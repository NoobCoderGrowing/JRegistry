package hawk.JRegistryCenter.Raft.RPC.Server;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.context.annotation.Configuration;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import io.netty.channel.EventLoopGroup;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import hawk.JRegistryCenter.Raft.RaftNode;
import hawk.JRegistryCenter.Raft.RPC.Client.RaftClientManager;
import hawk.JRegistryCenter.Raft.RPC.Server.Services.RequestVoteService;

@Configuration
@Data
@Slf4j
public class TimeoutLoop {

    private static final long ELECTION_TIMEOUT_MIN_MS = 20_000L;
    private static final long ELECTION_TIMEOUT_MAX_MS = 30_000L;

    @Autowired
    private EventLoopGroup singleGroup;

    @Autowired
    private RaftNode raftNode;

    @Autowired
    private RaftClientManager raftClientManager;

    @Autowired
    private RequestVoteService requestVoteService;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong timeoutVersion = new AtomicLong(0);
    private volatile ScheduledFuture<?> timeoutFuture;

    @PostConstruct
    public void timeout(){
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

        long delayMs = ThreadLocalRandom.current().nextLong(ELECTION_TIMEOUT_MIN_MS, ELECTION_TIMEOUT_MAX_MS + 1);
        timeoutFuture = singleGroup.schedule(() -> {
            if (!running.get() || currentVersion != timeoutVersion.get()) {
                return;
            }
            try {
                if (!raftNode.getIsLeader().get()) {
                    requestVoteService.startElection(raftClientManager);
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
        log.info("TimeoutLoop {} shutdown gracefully", raftNode.getId());
    }
}
