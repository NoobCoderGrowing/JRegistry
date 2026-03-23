package hawk.JRegistryCenter.Raft.RPC.Server;

import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Timer {

    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean timerIsUP;
    private final Random random;

    // 当前这次计时任务句柄（用于取消）
    private volatile ScheduledFuture<?> timeoutFuture;

    public Timer() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.timerIsUP = new AtomicBoolean(false);
        this.random = new Random();
    }

    public synchronized void resetTimer() {
        timerIsUP.set(false);
        cancelCurrentTimeout();
        long timeout = 20000L + random.nextInt(10000);
        timeoutFuture = scheduler.schedule(() -> {
            timerIsUP.set(true);
        }, timeout, TimeUnit.MILLISECONDS);
    }

    public synchronized void resetTimerWithDelay(int delay) {
        timerIsUP.set(false);
        cancelCurrentTimeout();
        long timeout = 20000L + random.nextInt(Math.max(1, delay));
        timeoutFuture = scheduler.schedule(() -> {
            timerIsUP.set(true);
        }, timeout, TimeUnit.MILLISECONDS);
    }

    public boolean isTimerUp() {
        return timerIsUP.get();
    }

    public synchronized void stop() {
        cancelCurrentTimeout();
        scheduler.shutdownNow();
    }

    private void cancelCurrentTimeout() {
        ScheduledFuture<?> f = timeoutFuture;
        if (f != null && !f.isDone()) {
            f.cancel(false);
        }
        timeoutFuture = null;
    }

    public static void main(String[] args) {
        Timer timer = new Timer();
        timer.resetTimer();
        try{
            Thread.sleep(1000);
            timer.resetTimer();
        }catch(InterruptedException e){
            e.printStackTrace();
        }
    }
}