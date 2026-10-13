package io.github.noobcodergrowing.jregistry.Services.Timer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import io.netty.channel.EventLoopGroup;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import io.github.noobcodergrowing.jregistrycore.Raft.RaftNode;
import io.github.noobcodergrowing.jregistry.Raft.RPC.Client.RaftClientManager;
import io.github.noobcodergrowing.jregistry.Services.RequestVoteService;

import org.springframework.context.annotation.Lazy;


@Component
@Data
@Slf4j
// public class TimeoutService implements FollowerElectionTimer {
public class TimeoutService{

    /** 选举超时下限（ms）：follower 在 [min, max] 的随机区间内没收到 leader 消息就发起选举。 */
    @Value("${raft.timing.election-timeout-min-ms:15000}")
    private long electionTimeoutMinMs;

    @Value("${raft.timing.election-timeout-max-ms:30000}")
    private long electionTimeoutMaxMs;

    /** 只用于启动时校验选举超时是否合理，本类不直接使用该值。 */
    @Value("${raft.timing.heartbeat-interval-ms:5000}")
    private long heartbeatIntervalMs;

    @Autowired
    private EventLoopGroup singleGroup;

    @Autowired
    private RaftNode raftNode;

    /** 延迟解析，打破与 RaftClientManager 的构造期环 */
    @Autowired
    private ObjectProvider<RaftClientManager> raftClientManagerProvider;

    @Lazy
    @Autowired
    private RequestVoteService requestVoteService;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong timeoutVersion = new AtomicLong(0);
    private volatile ScheduledFuture<?> timeoutFuture;

    
    /**
     * 启动时把明显不合理的配置喊出来。Raft 对这两个值的关系很敏感：选举超时若没有显著大于
     * 心跳间隔，follower 会抢在收到 leader 心跳之前就发起选举，集群陷入无休止的重新选主。
     */
    @PostConstruct
    public void validateTimings(){
        if(electionTimeoutMaxMs < electionTimeoutMinMs){
            log.warn("node {} election-timeout-max-ms ({}) < election-timeout-min-ms ({}), will be treated as swapped",
                    raftNode.getId(), electionTimeoutMaxMs, electionTimeoutMinMs);
        }
        if(Math.min(electionTimeoutMinMs, electionTimeoutMaxMs) <= heartbeatIntervalMs){
            log.warn("node {} election-timeout-min-ms ({}) <= heartbeat-interval-ms ({}): "
                            + "followers may start elections before hearing from the leader",
                    raftNode.getId(), electionTimeoutMinMs, heartbeatIntervalMs);
        }
    }

    public void timeoutStart(){
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        scheduleNextTimeout();
    }

    public void resetTimeout() {
        singleGroup.execute(this::scheduleNextTimeout);
    }

    private void scheduleNextTimeout() {
        if (!running.get()) {
            return;
        }

        long currentVersion = timeoutVersion.incrementAndGet();
        cancelTimeoutFuture();

        long minMs = Math.min(electionTimeoutMinMs, electionTimeoutMaxMs);
        long maxMs = Math.max(electionTimeoutMinMs, electionTimeoutMaxMs);
        long delayMs = ThreadLocalRandom.current().nextLong(minMs, maxMs + 1);
        timeoutFuture = singleGroup.schedule(() -> {
            if (!running.get() || currentVersion != timeoutVersion.get()) {
                return;
            }
            try {
                if (!raftNode.getIsLeader().get()) {
                    requestVoteService.startElection(raftClientManagerProvider.getObject());
                }
            } catch (Exception e) {
                log.error("node {} election timeout handler error", raftNode.getId(), e);
            } finally {
                scheduleNextTimeout();
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void cancelTimeoutFuture() {
        ScheduledFuture<?> future = timeoutFuture;
        if (future != null && !future.isDone()) {
            future.cancel(false);
        }
        timeoutFuture = null;
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        timeoutVersion.incrementAndGet();
        cancelTimeoutFuture();
        log.info("TimeoutService {} shutdown gracefully", raftNode.getId());
    }
}
