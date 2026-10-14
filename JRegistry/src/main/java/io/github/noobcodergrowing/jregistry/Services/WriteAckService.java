package io.github.noobcodergrowing.jregistry.Services;

import com.alibaba.fastjson.JSON;
import io.github.noobcodergrowing.jregistrycore.RPC.RaftRequest;
import io.github.noobcodergrowing.jregistrycore.Raft.RaftNode;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 「需要回包的写」（目前只有 CAS）的待回包表。
 *
 * <p>客户端把带 uuid 的条件写发到某个节点，那个节点记下 {@code uuid -> 客户端 channel}。
 * 日志被 apply 之后，<b>谁 apply 到、谁手里正好有这个 channel，就由谁回话</b>：
 * 客户端连在 follower 上时，follower 自己 apply 那条日志时就结清了，不需要任何回程路由；
 * leader 只在客户端恰好连在自己身上时才会命中。
 *
 * <p>本类只保存本地状态，不参与共识——结果对错由状态机的 apply 决定。
 */
@Slf4j
@Service
public class WriteAckService {

    private static final class PendingAck {
        final Channel channel;
        final long deadlineMillis;

        PendingAck(Channel channel, long deadlineMillis) {
            this.channel = channel;
            this.deadlineMillis = deadlineMillis;
        }
    }

    private final Map<String, PendingAck> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);

    private volatile ScheduledFuture<?> pruneFuture;

    @Autowired
    private EventLoopGroup singleGroup;

    @Autowired
    private RaftNode raftNode;

    /** 超过这么久还没等到 apply 回包就丢弃——客户端侧更早就会超时，这里只是别让表无限涨。 */
    @Value("${registry.write-ack.timeout-seconds:30}")
    private int ackTimeoutSeconds;

    @Value("${registry.write-ack.prune-interval-seconds:10}")
    private int pruneIntervalSeconds;

    public void start() {
        pruneFuture = singleGroup.scheduleWithFixedDelay(this::prune,
                pruneIntervalSeconds, pruneIntervalSeconds, TimeUnit.SECONDS);
        log.info("node {} write-ack service started: timeout {}s, prune interval {}s",
                raftNode.getId(), ackTimeoutSeconds, pruneIntervalSeconds);
    }

    /**
     * 客户端把需要回包的写发到本节点时登记。
     * <b>只在真正持有该客户端连接的那一侧调用</b>——peer 连接上转发来的写请求不能登记，
     * 否则会拿别人的 peer channel 当作客户端连接去回包。
     */
    public void register(String uuid, Channel channel) {
        if (uuid == null || channel == null) {
            return;
        }
        pending.put(uuid, new PendingAck(channel, System.currentTimeMillis() + ackTimeoutSeconds * 1000L));
    }

    /** 日志 apply 完成：如果这个 uuid 的客户端恰好连在本节点，就把结果回过去。 */
    public void complete(String uuid, boolean applied) {
        PendingAck ack = pending.remove(uuid);
        if (ack == null) {
            return; // 绝大多数情况：客户端连在别的节点上，那条连接由那个节点回
        }
        if (!ack.channel.isActive()) {
            log.warn("node {} dropping write ack {}, client channel already closed", raftNode.getId(), uuid);
            return;
        }
        RaftRequest reply = new RaftRequest();
        reply.setType("writeAck");
        reply.setSuccess(applied);
        try {
            reply.setUuid(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            log.error("node {} malformed uuid in write ack: {}", raftNode.getId(), uuid);
            return;
        }
        ack.channel.writeAndFlush(JSON.toJSONString(reply) + "\n");
    }

    private void prune() {
        if (!running.get()) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            pending.entrySet().removeIf(entry -> {
                boolean expired = entry.getValue().deadlineMillis < now;
                if (expired) {
                    log.warn("node {} giving up on write ack {} after {}s with no apply",
                            raftNode.getId(), entry.getKey(), ackTimeoutSeconds);
                }
                return expired;
            });
        } catch (Exception e) {
            log.error("node {} write ack prune error", raftNode.getId(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        ScheduledFuture<?> future = pruneFuture;
        if (future != null) {
            future.cancel(false);
        }
        pending.clear();
        log.info("WriteAckService {} shutdown gracefully", raftNode.getId());
    }
}
