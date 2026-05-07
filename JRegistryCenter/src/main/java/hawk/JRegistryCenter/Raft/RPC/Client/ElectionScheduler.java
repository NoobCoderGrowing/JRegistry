package hawk.JRegistryCenter.Raft.RPC.Client;

import io.netty.channel.EventLoop;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class ElectionScheduler {
    private final EventLoop raftLoop;
    private final AtomicLong epoch = new AtomicLong(0);
    private volatile ScheduledFuture<?> electionFuture;

    // 你可以按需要调整
    private final long electionMinMs;
    private final long electionMaxMs;

    public ElectionScheduler(EventLoop raftLoop, long electionMinMs, long electionMaxMs) {
        this.raftLoop = raftLoop;
        this.electionMinMs = electionMinMs;
        this.electionMaxMs = electionMaxMs;
    }

    public void resetTimeout(Runnable onTimeout) {
        if (!raftLoop.inEventLoop()) {
            raftLoop.execute(() -> resetTimeout(onTimeout));
            return;
        }

        long myEpoch = epoch.incrementAndGet();

        if (electionFuture != null) {
            electionFuture.cancel(false);
        }

        long delay = ThreadLocalRandom.current().nextLong(electionMinMs, electionMaxMs + 1);
        electionFuture = raftLoop.schedule(() -> {
            // 防止旧任务误触发
            if (myEpoch != epoch.get()) return;
            onTimeout.run();
        }, delay, TimeUnit.MILLISECONDS);
    }

    public void cancel() {
        if (!raftLoop.inEventLoop()) {
            raftLoop.execute(this::cancel);
            return;
        }
        epoch.incrementAndGet();
        if (electionFuture != null) {
            electionFuture.cancel(false);
            electionFuture = null;
        }
    }
}
