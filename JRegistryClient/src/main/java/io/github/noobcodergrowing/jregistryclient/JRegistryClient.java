package io.github.noobcodergrowing.jregistryclient;

import io.github.noobcodergrowing.jregistrycore.KV;
import io.github.noobcodergrowing.jregistrycore.NodeAddress;
import io.github.noobcodergrowing.jregistrycore.Pair;
import io.github.noobcodergrowing.jregistrycore.RPC.RaftRequest;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.alibaba.fastjson.JSON;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JRegistryClient {

    /** 心跳间隔默认值（ms）：连接写空闲这么久就补发一次心跳。 */
    public static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 10_000;

    /** 断线后重连间隔的默认值（ms）。 */
    public static final long DEFAULT_RECONNECT_DELAY_MS = 5000;

    /** 连续重连失败多少次后故障转移到下一个候选节点的默认值。 */
    public static final int DEFAULT_FAILOVER_AFTER_ATTEMPTS = 3;

    /**
     * {@link #cas} 等待答复的超时默认值（ms）。
     *
     * <p>比普通请求的 {@code taskTimeoutMs} 宽得多：CAS 要走完「追加日志 → 多数派复制 → 提交 →
     * apply」一整趟共识，不是一次 RPC 往返。给太紧只会换来一堆 {@link CasResult#UNKNOWN}。
     */
    public static final long DEFAULT_CAS_TIMEOUT_MS = 5000;

    private long TASK_TIMEOUT_MS = 1000;

    /** {@link #cas} 的等待上限；与普通请求分开，因为要覆盖一次共识往返。 */
    private final long casTimeoutMs;

    private final NettyClient nettyClient;

    /** 是否在连接时向服务端查询选点结果（连接负载均衡）。 */
    private final boolean serverSideBalancing;

    /** 上一次拿到的候选节点（按连接数升序）。运行期故障转移就在这份列表上轮转。 */
    private volatile List<NodeAddress> candidates = List.of();

    /** 当前落在候选列表的第几位，故障转移时从它往后走。 */
    private final AtomicInteger candidateIndex = new AtomicInteger(-1);

    /**
     * 心跳间隔、重连间隔、故障转移阈值、CAS 超时一律取 {@code DEFAULT_*} 默认值。
     *
     * @param taskTimeoutMs        单次请求（get / listAll / pickNode）等待回复的超时
     * @param connectTimeoutMillis TCP 连接超时
     */
    public JRegistryClient(String host, int port, long taskTimeoutMs, int connectTimeoutMillis) {
        this(host, port, taskTimeoutMs, connectTimeoutMillis, false,
                DEFAULT_HEARTBEAT_INTERVAL_MS, DEFAULT_RECONNECT_DELAY_MS, DEFAULT_FAILOVER_AFTER_ATTEMPTS,
                DEFAULT_CAS_TIMEOUT_MS);
    }

    /**
     * 心跳间隔、重连间隔、故障转移阈值、CAS 超时一律取 {@code DEFAULT_*} 默认值。
     *
     * @param serverSideBalancing 为 true 时把 {@code host:port} 当作<b>种子地址</b>：连上后向服务端
     *        查询当前连接数最少的节点并迁过去。客户端因此只需配置一个地址、不必知道全量拓扑，
     *        也不会所有客户端都挤在同一个节点上。
     */
    public JRegistryClient(String host, int port, long taskTimeoutMs, int connectTimeoutMillis,
                           boolean serverSideBalancing) {
        this(host, port, taskTimeoutMs, connectTimeoutMillis, serverSideBalancing,
                DEFAULT_HEARTBEAT_INTERVAL_MS, DEFAULT_RECONNECT_DELAY_MS, DEFAULT_FAILOVER_AFTER_ATTEMPTS,
                DEFAULT_CAS_TIMEOUT_MS);
    }

    /**
     * 全参构造：时间参数与行为开关全部显式给出。上面两个短构造最终都委托到这里。
     *
     * <p>位置参数较多，其中几个单位相同，传错顺序编译器不会报错——只改一两项时建议用上面的短构造。
     *
     * @param taskTimeoutMs         单次请求（get / listAll / pickNode）等待回复的超时
     * @param connectTimeoutMillis  TCP 连接超时
     * @param serverSideBalancing   是否向服务端查询选点结果（连接负载均衡）
     * @param heartbeatIntervalMs   心跳间隔：连接写空闲这么久就补发一次心跳。
     *                              <b>必须小于服务端的 {@code registry.client-heartbeat.read-timeout-seconds}</b>，
     *                              否则服务端会把正常连接误判为掉线（默认 10000 &lt; 20000）
     * @param reconnectDelayMs      断线后重连的间隔
     * @param failoverAfterAttempts 连续重连失败多少次后故障转移到下一个候选节点
     * @param casTimeoutMs          {@link #cas} 的等待上限，要覆盖一次共识往返（默认 5000）
     */
    public JRegistryClient(String host, int port, long taskTimeoutMs, int connectTimeoutMillis,
                           boolean serverSideBalancing, long heartbeatIntervalMs,
                           long reconnectDelayMs, int failoverAfterAttempts, long casTimeoutMs) {
        this.nettyClient = new NettyClient(host, port, connectTimeoutMillis,
                heartbeatIntervalMs, reconnectDelayMs, failoverAfterAttempts);
        this.TASK_TIMEOUT_MS = taskTimeoutMs;
        this.casTimeoutMs = casTimeoutMs;
        this.serverSideBalancing = serverSideBalancing;
    }

    /**
     * Connect synchronously; returns true only when the TCP channel is active.
     *
     * <p>开启选点时会先连种子地址、问一次选点结果，再迁到候选首位；全部候选都连不上时退回种子。
     */
    public boolean connect() {
        if (!nettyClient.connectSync()) {
            return false;
        }
        if (serverSideBalancing) {
            relocateByServerHint();
        }
        return nettyClient.isConnected();
    }

    public boolean isConnected() {
        return nettyClient.isConnected();
    }

    /**
     * 问一次种子节点要候选列表，迁到首位；首位连不上就依次往下试，全都不通时退回种子地址。
     *
     * <p>候选列表是服务端按各节点当前在线连接数排好序的，所以客户端的分散度直接来自服务端
     * 对复制状态机的计数——客户端本地不做任何负载判断。
     */
    private void relocateByServerHint() {
        String seedHost = nettyClient.getHost();
        int seedPort = nettyClient.getPort();

        List<NodeAddress> ranked = requestNodeRanking();
        if (ranked.isEmpty()) {
            log.warn("pickNode returned no candidate, staying on seed {}:{}", seedHost, seedPort);
            return;
        }
        candidates = ranked;
        // 把候选列表交给传输层：连续重连失败时它会来问下一个节点。
        nettyClient.setFailoverTargetSupplier(this::nextCandidate);

        for (int i = 0; i < ranked.size(); i++) {
            NodeAddress node = ranked.get(i);
            if (nettyClient.reconnectTo(node.getHost(), node.getPort())) {
                candidateIndex.set(i);
                log.info("client {} placed on node {} ({}:{})",
                        nettyClient.getClientId(), node.getNodeId(), node.getHost(), node.getPort());
                return;
            }
            log.warn("candidate node {} ({}:{}) unreachable, trying next",
                    node.getNodeId(), node.getHost(), node.getPort());
        }
        // 遍历过程中可能把原本好好的种子连接断掉了，兜回来。
        log.error("no candidate node reachable, falling back to seed {}:{}", seedHost, seedPort);
        nettyClient.reconnectTo(seedHost, seedPort);
    }

    /**
     * 运行期故障转移的目标：在候选列表上轮转，跳过当前地址。
     *
     * <p>只做存活意义上的转移，<b>不重新选点</b>——重新按连接数选点会让连接在节点间来回搬家。
     * 列表只有一项、或整份列表都指向当前地址时返回 {@code null}，表示原地继续重试。
     */
    private NodeAddress nextCandidate() {
        List<NodeAddress> list = candidates;
        if (list.isEmpty()) {
            return null;
        }
        for (int i = 0; i < list.size(); i++) {
            NodeAddress node = list.get(Math.floorMod(candidateIndex.incrementAndGet(), list.size()));
            if (!node.getHost().equals(nettyClient.getHost())
                    || node.getPort() != nettyClient.getPort()) {
                return node;
            }
        }
        return null;
    }

    /** 向当前连接索要一份按连接数升序的候选节点列表；失败返回空列表。 */
    private List<NodeAddress> requestNodeRanking() {
        RaftRequest request = new RaftRequest();
        request.setType("pickNode");
        request.setUuid(UuidCreator.getTimeOrderedEpoch());
        CompletableFuture<RaftRequest> future = new CompletableFuture<>();
        nettyClient.setMessageListener(message -> {
            RaftRequest reply = JSON.parseObject(message, RaftRequest.class);
            if (reply == null || reply.getUuid() == null) {
                return;
            }
            if (reply.getUuid().equals(request.getUuid())) {
                future.complete(reply);
            }
        });
        nettyClient.sendRequest(request);

        try {
            RaftRequest reply = future.get(TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return reply.getNodes() == null ? List.of() : reply.getNodes();
        } catch (Exception e) {
            log.warn("pickNode request failed: {}", e.getMessage());
            return List.of();
        }
    }

    public void shutdown() {
        nettyClient.shutdown();
    }

    private void requireConnected() {
        if (!nettyClient.isConnected()) {
            throw new IllegalStateException("JRegistryClient is not connected; call connect() first");
        }
    }

    public Pair<byte[], String> get(String key) {
        KV kv = getWithStatus(key);
        return kv == null ? null : new Pair<>(kv.getData(), kv.getDataType());
    }

    /**
     * 与 {@link #get} 相同，但额外带出该 key 所属客户端的二值状态
     * （{@link KV#isOffline()}：{@code false} = 生效，{@code true} = 离线）。
     *
     * <p>服务端在客户端心跳连续超时 / 连接断开 / 其 owner 节点失效时只把该客户端标成离线，
     * 不再删除它 set 过的 key —— 所以数据仍在，只是状态变了。
     * 由 SSH / Admin 写入、没有归属客户端的 key 恒为 {@code false}。
     *
     * <p>key 不存在或超时返回 {@code null}。
     */
    public KV getWithStatus(String key) {
        requireConnected();
        RaftRequest request = new RaftRequest();
        request.setType("get");
        request.setKey(key);
        request.setUuid(UuidCreator.getTimeOrderedEpoch());
        CompletableFuture<RaftRequest> future = new CompletableFuture<>();
        nettyClient.setMessageListener(message -> {
            RaftRequest reply = JSON.parseObject(message, RaftRequest.class);
            if (reply == null) {
                return;
            }
            if (reply.getUuid().equals(request.getUuid())) {
                future.complete(reply);
            }
        });
        nettyClient.sendRequest(request);

        try {
            RaftRequest reply = future.get(TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (reply.isSuccess()) {
                return new KV(reply.getKey(), reply.getData(), reply.getDataType(), reply.isOffline());
            }
            return null;
        } catch (Exception e) {
            log.error("getWithStatus failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 递归列出 {@code path} 及其所有后代中带值的 key:value 对。
     * 返回的 key 是绝对完整路径（形如 {@code app/config/name}，可直接再传给 {@link #get}）。
     * 路径不存在或超时返回 {@code null}；路径存在但没有带值的后代时返回空列表。
     */
    public List<KV> listAll(String path) {
        requireConnected();
        RaftRequest request = new RaftRequest();
        request.setType("ls");
        request.setKey(path);
        request.setUuid(UuidCreator.getTimeOrderedEpoch());
        CompletableFuture<RaftRequest> future = new CompletableFuture<>();
        nettyClient.setMessageListener(message -> {
            RaftRequest reply = JSON.parseObject(message, RaftRequest.class);
            if (reply == null) {
                return;
            }
            if (reply.getUuid().equals(request.getUuid())) {
                future.complete(reply);
            }
        });
        nettyClient.sendRequest(request);

        try {
            RaftRequest reply = future.get(TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (reply.isSuccess()) {
                return reply.getKvs();
            }
            return null;
        } catch (Exception e) {
            log.error("listAll failed: {}", e.getMessage());
            return null;
        }
    }

    public void set(String key, byte[] data, String dataType) {
        requireConnected();
        RaftRequest request = new RaftRequest();
        request.setType("writeRequest");
        request.setCmd("set");
        request.setKey(key);
        request.setData(data);
        request.setDataType(dataType);
        request.setUuid(UuidCreator.getTimeOrderedEpoch());
        nettyClient.sendRequest(request);
    }

    public void delete(String key) {
        requireConnected();
        RaftRequest request = new RaftRequest();
        request.setType("writeRequest");
        request.setCmd("delete");
        request.setKey(key);
        request.setUuid(UuidCreator.getTimeOrderedEpoch());
        nettyClient.sendRequest(request);
    }

    /**
     * 条件写：前值与 {@code expected} 一致才写入 {@code newValue}，否则什么都不做。
     *
     * <p>比较是在状态机的 apply 阶段做的——所有副本按同样的日志顺序看到同样的前值，
     * 判定必然一致，所以这是**原子**的：并发调用里只有一个能拿到 {@link CasResult#APPLIED}，
     * 这正是做分布式锁、选主、乐观并发控制需要的东西。
     *
     * <p>与 {@link #set} / {@link #delete} 不同，本方法<b>要等回包</b>：它返回的是"到底写没写进去"，
     * 所以耗时是<b>一次共识往返</b>（追加日志 → 多数派复制 → 提交 → apply），比读操作慢得多。
     * 等待上限是构造时的 {@code casTimeoutMs}（默认 {@value #DEFAULT_CAS_TIMEOUT_MS}ms），
     * 它和普通请求的 {@code taskTimeoutMs} 分开，就是为了别拿读的超时去卡共识。
     *
     * @param expected 期望的当前值；<b>传 {@code null} 表示"期望该 key 当前不存在"</b>（创建即抢锁）
     * @return 见 {@link CasResult}；超时返回 {@code UNKNOWN}，重试安全
     */
    public CasResult cas(String key, byte[] expected, byte[] newValue, String dataType) {
        requireConnected();
        RaftRequest request = new RaftRequest();
        request.setType("writeRequest");
        request.setCmd("cas");
        request.setKey(key);
        request.setExpectedData(expected);
        request.setCasExpectAbsent(expected == null);
        request.setData(newValue);
        request.setDataType(dataType);
        request.setUuid(UuidCreator.getTimeOrderedEpoch());

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        nettyClient.setMessageListener(message -> {
            RaftRequest reply = JSON.parseObject(message, RaftRequest.class);
            if (reply == null || reply.getUuid() == null) {
                return;
            }
            if (reply.getUuid().equals(request.getUuid())) {
                future.complete(reply.isSuccess());
            }
        });
        nettyClient.sendRequest(request);

        try {
            boolean applied = future.get(casTimeoutMs, TimeUnit.MILLISECONDS);
            return applied ? CasResult.APPLIED : CasResult.NOT_APPLIED;
        } catch (Exception e) {
            log.warn("cas on {} got no ack within {}ms ({}), outcome unknown",
                    key, casTimeoutMs, e.getMessage());
            return CasResult.UNKNOWN;
        }
    }
}
