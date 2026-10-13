package io.github.noobcodergrowing.jregistry.Services;

import io.github.noobcodergrowing.jregistry.Raft.Log.LogService;
import io.github.noobcodergrowing.jregistry.Raft.RPC.Client.RaftClientManager;
import io.github.noobcodergrowing.jregistrycore.NodeAddress;
import io.github.noobcodergrowing.jregistrycore.RPC.RaftRequest;
import io.github.noobcodergrowing.jregistrycore.Raft.RaftNode;
import io.github.noobcodergrowing.jregistrycore.SessionInfo;
import io.github.noobcodergrowing.jregistrycore.StateMachine;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 每个节点各自维持「连到自己」的那些 JRegistryClient 连接的活性。
 *
 * <p>分工：
 * <ul>
 *   <li><b>检测（本地）</b>：只有持有某条 TCP 连接的节点能看见它的死活，所以计超时在这里做。</li>
 *   <li><b>归属（复制）</b>：key -&gt; clientId 与 clientId -&gt; 租约都存在 {@link StateMachine} 里，
 *       随 Raft 日志/快照复制，节点宕机不会丢。</li>
 *   <li><b>动作（走 Raft）</b>：过期判决签发一条 {@code clientOffline}（只把客户端标成离线，
 *       不删它 set 过的 key），非 leader 会自动转发，真正是否执行由 apply 阶段的守卫决定。</li>
 * </ul>
 *
 * <p>本类只保存<b>本地活性状态</b>，不保存归属——归属的权威副本在状态机里，避免节点重启/lag 时
 * 本地视图与集群视图打架。
 */
@Slf4j
@Service
public class ClientSessionService {

    /** 一个 client 连接的本地活性状态；归属（owner/epoch）不在这里，在状态机里。 */
    private static final class ClientSession {
        final String clientId;
        volatile Channel channel;
        final AtomicInteger missCount = new AtomicInteger();
        final AtomicBoolean closed = new AtomicBoolean(false);

        ClientSession(String clientId, Channel channel) {
            this.clientId = clientId;
            this.channel = channel;
        }
    }

    private final ConcurrentHashMap<Channel, ClientSession> byChannel = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ClientSession> byClient = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);

    /** 本节点成为 leader 的时刻；-1 表示当前不是 leader。用于给新上任的 leader 一段宽限期。 */
    private volatile long leaderSinceMillis = -1;

    /** 已签发释放、但还没在状态机里落地的 clientId -> 它失效时的 owner，避免每轮重复签发。 */
    private final Map<String, Integer> pendingRelease = new ConcurrentHashMap<>();

    private volatile ScheduledFuture<?> sweepFuture;

    @Autowired
    private LogService logService;

    @Autowired
    private StateMachine stateMachine;

    @Autowired
    private RaftNode raftNode;

    @Autowired
    private EventLoopGroup singleGroup;

    @Autowired
    private RaftClientManager raftClientManager;

    /** 集群里其余节点的地址，用于给客户端下发完整候选列表（自己的地址取自 raftNode）。 */
    @Value("#{${raft.peers:{}}}")
    private Map<Integer, String> peers;

    /** 连续超时多少次后判定连接已死。 */
    @Value("${registry.client-heartbeat.max-misses:3}")
    private int maxMisses;

    /** 归属尚未可见 / 当前无 leader 时，隔多久重试一次判决。 */
    @Value("${registry.client-heartbeat.retry-delay-seconds:5}")
    private int retryDelaySeconds;

    /** 重试上限，避免永久空转。 */
    private static final int MAX_EXPIRE_ATTEMPTS = 12;

    @Value("${registry.session-sweep.enabled:true}")
    private boolean sweepEnabled;

    @Value("${registry.session-sweep.sweep-interval-seconds:10}")
    private int sweepIntervalSeconds;

    /** peer 多久没有任何响应就判它失效，回收它名下的会话。要比心跳间隔（5s）大得多。 */
    @Value("${registry.session-sweep.peer-dead-threshold-seconds:60}")
    private int peerDeadThresholdSeconds;

    /** 新 leader 上任后的宽限期：peer 连接可能还没建起来，这段时间内不判任何 peer 失效。 */
    @Value("${registry.session-sweep.leader-grace-seconds:30}")
    private int leaderGraceSeconds;

    // ------------------------------------------------------------------ 入站

    /**
     * 记录一次来自该 client 的入站流量（任何消息都算活着），必要时接管连接。
     */
    public void touch(String clientId, Channel channel) {
        if (clientId == null || clientId.isEmpty() || channel == null) {
            return;
        }
        ClientSession session = bind(clientId, channel);
        if (session != null) {
            session.missCount.set(0);
        }
    }

    /** 收到客户端心跳；与普通入站流量同义，都只是"还活着"的证据。 */
    public void onHeartbeat(String clientId, Channel channel) {
        touch(clientId, channel);
    }

    /**
     * 把 clientId 绑定到这条连接，并在连接发生变化时执行<b>接管</b>。
     *
     * <p>接管只换绑定、不删任何 key——客户端只是换了个连接回来，它的数据仍然属于它。
     * 真正的"是否还属于本节点"由状态机里的 epoch 守卫在 apply 时裁决。
     */
    private ClientSession bind(String clientId, Channel channel) {
        final ClientSession[] replaced = new ClientSession[1];
        final boolean[] needAttach = new boolean[1];

        ClientSession session = byClient.compute(clientId, (cid, old) -> {
            if (old != null && old.channel == channel) {
                return old;
            }
            replaced[0] = old;
            needAttach[0] = true;
            return new ClientSession(cid, channel);
        });

        if (replaced[0] != null) {
            replaced[0].closed.set(true);
            byChannel.remove(replaced[0].channel, replaced[0]);
            log.info("node {} client {} switched to a new connection, old one superseded",
                    raftNode.getId(), clientId);
        }
        if (needAttach[0]) {
            byChannel.put(channel, session);
            issueAttach(clientId);
        }
        return session;
    }

    // ------------------------------------------------------------------ 超时

    /** 一次读超时（IdleStateEvent）——只对确实注册为 client 的连接计数。 */
    public void onMiss(Channel channel) {
        ClientSession session = byChannel.get(channel);
        if (session == null || session.closed.get() || session.channel != channel) {
            return;
        }
        int misses = session.missCount.incrementAndGet();
        log.info("node {} client {} missed heartbeat {}/{}",
                raftNode.getId(), session.clientId, misses, maxMisses);
        if (misses >= maxMisses) {
            expire(session, channel);
        }
    }

    /**
     * 连接确定关闭（收到 FIN/RST）：立即清理，不必等满三次。
     * 三次超时真正要覆盖的是客户端掉电/断网这类<b>半开连接</b>——那种情况不会走到这里。
     */
    public void onDisconnect(Channel channel) {
        ClientSession session = byChannel.get(channel);
        if (session == null || session.closed.get() || session.channel != channel) {
            return;
        }
        log.info("node {} client {} disconnected, clean up immediately",
                raftNode.getId(), session.clientId);
        expire(session, channel);
    }

    // ------------------------------------------------------------------ 判决

    private void expire(ClientSession session, Channel channel) {
        if (!session.closed.compareAndSet(false, true)) {
            return; // 已被接管或已清理
        }
        String clientId = session.clientId;
        SessionInfo lease = stateMachine.getSession(clientId);

        if (lease == null) {
            // attach 还没 apply 到本节点，归属不可见 → 现在判不了，稍后重试。
            log.info("node {} client {} expiry deferred, lease not visible yet",
                    raftNode.getId(), clientId);
            retryExpire(clientId, channel, 1);
            return;
        }
        if (lease.getOwnerNodeId() != raftNode.getId()) {
            // 归属已经在别的节点上（客户端重连走了）→ 只摘本地，绝不删。
            log.info("node {} client {} now owned by node {}, abort expiry",
                    raftNode.getId(), clientId, lease.getOwnerNodeId());
            dropLocal(clientId, channel);
            channel.close();
            return;
        }

        issueClientOffline(clientId, lease.getEpoch(), lease.getOwnerNodeId());
        if (raftNode.getLeaderId() == -1) {
            log.warn("node {} has no leader yet, will retry clientOffline for client {}",
                    raftNode.getId(), clientId);
            retryExpire(clientId, channel, 1);
            return;
        }

        log.info("node {} mark client {} offline at epoch {}, its keys stay: {}",
                raftNode.getId(), clientId, lease.getEpoch(), stateMachine.keysOwnedBy(clientId));
        dropLocal(clientId, channel);
        channel.close();
    }

    /**
     * 归属尚不可见或当前无 leader 时的退避重试。每次都重新读一遍租约——因为重试期间
     * 客户端可能已经重连到别处，那样这次判决就该作废。
     */
    private void retryExpire(String clientId, Channel channel, int attempt) {
        if (!running.get()) {
            return;
        }
        if (attempt > MAX_EXPIRE_ATTEMPTS) {
            log.error("node {} gave up expiring client {} after {} attempts; {} keys may leak",
                    raftNode.getId(), clientId, attempt, stateMachine.keysOwnedBy(clientId).size());
            dropLocal(clientId, channel);
            return;
        }
        singleGroup.schedule(() -> {
            SessionInfo lease = stateMachine.getSession(clientId);
            if (lease == null) {
                retryExpire(clientId, channel, attempt + 1);
                return;
            }
            if (lease.getOwnerNodeId() != raftNode.getId()) {
                log.info("node {} client {} was taken over by node {} during retry, abort expiry",
                        raftNode.getId(), clientId, lease.getOwnerNodeId());
                dropLocal(clientId, channel);
                return;
            }
            if (raftNode.getLeaderId() == -1) {
                retryExpire(clientId, channel, attempt + 1);
                return;
            }
            issueClientOffline(clientId, lease.getEpoch(), lease.getOwnerNodeId());
            log.info("node {} mark client {} offline at epoch {} (attempt {}), its keys stay: {}",
                    raftNode.getId(), clientId, lease.getEpoch(), attempt,
                    stateMachine.keysOwnedBy(clientId));
            dropLocal(clientId, channel);
            channel.close();
        }, retryDelaySeconds, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------------ 签发

    /** 声明本节点接管该 client；epoch 由状态机在 apply 时自增，不在这里算。 */
    private void issueAttach(String clientId) {
        RaftRequest request = new RaftRequest();
        request.setType("writeRequest");
        request.setCmd("clientAttach");
        request.setClientId(clientId);
        request.setOwnerNodeId(raftNode.getId());
        log.info("node {} attach client {}", raftNode.getId(), clientId);
        logService.handleWriteRequest(request);
    }

    /**
     * 签发一次过期判决。{@code ownerNodeId} 必须是<b>该会话当前的 owner</b>，不是签发者自己 ——
     * 正常路径两者恰好相同（检测者就是 owner），但 leader 侧回收孤儿会话时，
     * 签发者是 leader，owner 却是那个失效的节点。守卫比的正是这个字段。
     */
    private void issueClientOffline(String clientId, long epoch, int ownerNodeId) {
        RaftRequest request = new RaftRequest();
        request.setType("writeRequest");
        request.setCmd("clientOffline");
        request.setClientId(clientId);
        request.setClientEpoch(epoch);
        request.setOwnerNodeId(ownerNodeId);
        logService.handleWriteRequest(request);
    }

    /** 摘掉本地活性状态；若客户端已经重连（绑定到了别的 channel），不动新连接的状态。 */
    private void dropLocal(String clientId, Channel channel) {
        if (channel != null) {
            byChannel.remove(channel);
        }
        ClientSession session = byClient.get(clientId);
        if (session != null && session.channel == channel) {
            byClient.remove(clientId, session);
        }
    }

    // ------------------------------------------------------------ 连接负载均衡：选点

    /**
     * 回答「我该连哪个节点」，返回一份按当前连接数升序排好的候选列表。
     *
     * <p>负载视图<b>直接取自复制的状态机</b>：每个会话都带 {@code ownerNodeId} 和 {@code offline}，
     * 按 owner 数一遍在线会话就是各节点持有的连接数——不需要任何额外的上报或计数通道。
     * 因此任何节点都能给出同一个答案。
     *
     * <p>这是一次纯查询：<b>不建会话、不改变任何状态</b>。客户端连上种子地址问一次，
     * 再迁到列表首位（连不上就依次往下试）。
     */
    public RaftRequest handlePickNodeRequest(RaftRequest request) {
        RaftRequest reply = new RaftRequest();
        reply.setType("pickNode");
        reply.setUuid(request.getUuid());
        reply.setNodes(rankNodes(request.getClientId()));
        reply.setSuccess(true);
        return reply;
    }

    /**
     * 节点按（在线连接数升序，同数内按 clientId 打散）排序。
     *
     * <p>为什么要按 clientId 打散：集群空闲时所有节点连接数都是 0，若同数就按固定顺序排，
     * 那么所有客户端会拿到同一个首选、全挤到同一台上去。用 clientId 作种子把同数节点的顺序
     * 伪随机化，不同客户端自然分到不同节点，而且同一个客户端每次问到的顺序是稳定的。
     */
    private List<NodeAddress> rankNodes(String clientId) {
        Map<Integer, NodeAddress> all = new LinkedHashMap<>();
        all.put(raftNode.getId(),
                new NodeAddress(raftNode.getId(), raftNode.getHost(), raftNode.getPort()));
        for (Map.Entry<Integer, String> peer : peers.entrySet()) {
            String[] hostPort = peer.getValue().split(":");
            if (hostPort.length == 2) {
                all.put(peer.getKey(),
                        new NodeAddress(peer.getKey(), hostPort[0], Integer.parseInt(hostPort[1])));
            }
        }

        Map<Integer, Integer> connections = new HashMap<>();
        for (SessionInfo session : stateMachine.getSessions().values()) {
            if (!session.isOffline()) {
                connections.merge(session.getOwnerNodeId(), 1, Integer::sum);
            }
        }

        int clientSeed = clientId == null ? 0 : clientId.hashCode();
        List<NodeAddress> ranked = new ArrayList<>(all.values());
        ranked.sort(Comparator
                .comparingInt((NodeAddress node) -> connections.getOrDefault(node.getNodeId(), 0))
                .thenComparingInt(node -> Math.floorMod(clientSeed * 31 + node.getNodeId(), 1000003)));
        return ranked;
    }

    // ------------------------------------------------------------ leader 侧孤儿会话回收

    /**
     * 启动 leader 侧的孤儿会话扫描。
     *
     * <p>覆盖的是本地检测够不到的那一档：<b>客户端彻底消失，而持有它连接的那个节点也崩了</b>
     * —— 此时没有任何节点还持有连接，本地超时无从触发，但归属表还留在复制状态里。
     *
     * <p>判据是「peer 是否还在响应」。必须在 leader 上跑：只有 leader 能保证自己的 applied 状态
     * 不滞后，落后的节点可能还没 apply 到最近的日志就误判租约过期。
     */
    public void start() {
        if (!sweepEnabled) {
            log.info("node {} session sweep disabled", raftNode.getId());
            return;
        }
        sweepFuture = singleGroup.scheduleWithFixedDelay(this::sweepTick,
                sweepIntervalSeconds, sweepIntervalSeconds, TimeUnit.SECONDS);
        log.info("node {} session sweep started: interval {}s, peer-dead {}s, leader-grace {}s",
                raftNode.getId(), sweepIntervalSeconds, peerDeadThresholdSeconds, leaderGraceSeconds);
    }

    private void sweepTick() {
        if (!running.get() || !sweepEnabled) {
            return;
        }
        try {
            if (!raftNode.getIsLeader().get()) {
                leaderSinceMillis = -1; // 掉下 leader 就重置宽限，下次上任重新计时
                return;
            }
            long now = System.currentTimeMillis();
            if (leaderSinceMillis < 0) {
                // 刚上任：到各 peer 的连接可能还没建立、也还没收到过任何响应，先给一段宽限。
                leaderSinceMillis = now;
                log.info("node {} became leader, session sweep in grace period for {}s",
                        raftNode.getId(), leaderGraceSeconds);
                return;
            }
            if (now - leaderSinceMillis < leaderGraceSeconds * 1000L) {
                return;
            }

            prunePendingRelease();

            for (Map.Entry<Integer, Long> peer : raftClientManager.getPeerLastSeen().entrySet()) {
                if (now - peer.getValue() <= peerDeadThresholdSeconds * 1000L) {
                    continue;
                }
                releaseSessionsOf(peer.getKey());
            }
        } catch (Exception e) {
            log.error("node {} session sweep error", raftNode.getId(), e);
        }
    }

    /** 已释放的会话一旦从状态机消失（或已被别人接管），去重标记就没必要留着了。 */
    private void prunePendingRelease() {
        pendingRelease.entrySet().removeIf(entry -> {
            SessionInfo session = stateMachine.getSession(entry.getKey());
            return session == null || session.getOwnerNodeId() != entry.getValue();
        });
    }

    /**
     * 把某个失效 peer 名下的全部会话标成离线（<b>不删它们的 key</b>）。展开成一条条现成的
     * {@code clientOffline}，因此每条都要过 apply 阶段的 epoch 守卫 —— 若那个节点其实只是被网络
     * 隔开、并未崩溃，且它在这期间重新接管过某个客户端（epoch 涨了），这里带的是陈旧 epoch，
     * 会被守卫拒绝，那个客户端的 key 也不会被误标离线。
     */
    private void releaseSessionsOf(int peerId) {
        List<String> orphaned = new ArrayList<>();
        for (Map.Entry<String, SessionInfo> entry : stateMachine.getSessions().entrySet()) {
            if (entry.getValue().getOwnerNodeId() == peerId && !pendingRelease.containsKey(entry.getKey())) {
                orphaned.add(entry.getKey());
            }
        }
        if (orphaned.isEmpty()) {
            return;
        }
        log.warn("node {} peer {} is unreachable, marking {} orphaned client session(s) offline: {}",
                raftNode.getId(), peerId, orphaned.size(), orphaned);
        for (String clientId : orphaned) {
            SessionInfo session = stateMachine.getSession(clientId);
            if (session == null || session.getOwnerNodeId() != peerId) {
                continue; // 刚被接管或已经释放掉了
            }
            pendingRelease.put(clientId, peerId);
            issueClientOffline(clientId, session.getEpoch(), session.getOwnerNodeId());
        }
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        ScheduledFuture<?> future = sweepFuture;
        if (future != null) {
            future.cancel(false);
        }
        byClient.clear();
        byChannel.clear();
        log.info("ClientSessionService {} shutdown gracefully", raftNode.getId());
    }
}
