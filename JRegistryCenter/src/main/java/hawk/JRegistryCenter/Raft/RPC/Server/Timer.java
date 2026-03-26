package hawk.JRegistryCenter.Raft.RPC.Server;

import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

public class Timer {

    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean timerIsUP;
    private final Random random;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();



    // 当前这次计时任务句柄（用于取消）
    private volatile ScheduledFuture<?> timeoutFuture;

    public Timer() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {

            Thread thread = new Thread(r, "Timer-Thread");
            thread.setDaemon(true);
            return thread;
        });
        this.timerIsUP = new AtomicBoolean(false);
        this.random = new Random();
    }


    public void start() {
        resetTimer();
    }

    public void resetTimer() {
        lock.lock();
        try{
            timerIsUP.set(false);
            cancelCurrentTimeout();
            long timeout = 20000L + random.nextInt(10000);
            timeoutFuture = scheduler.schedule(() -> {
                try{
                    timerIsUP.set(true);
                    condition.signalAll();
                }catch(Exception e){
                    e.printStackTrace();
                }
            }, timeout, TimeUnit.MILLISECONDS);
        }finally{
            lock.unlock();
        }
    }

    public void resetTimerWithDelay(int delay) {

        lock.lock();
        try{
            timerIsUP.set(false);
            cancelCurrentTimeout();
            long timeout = 20000L + random.nextInt(Math.max(1, delay));
            timeoutFuture = scheduler.schedule(() -> {
                try{
                    timerIsUP.set(true);
                    condition.signalAll();
                }catch(Exception e){
                    e.printStackTrace();
                }
            }, timeout, TimeUnit.MILLISECONDS);
        }finally{
            lock.unlock();
        }
    }

    public void awaitTimerUp() throws InterruptedException {
        lock.lock();
        try {
            while (!timerIsUP.get()) {
                condition.await(); // 会释放 lock，直到 signal 后重新竞争获取 lock
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean isTimerUp() {
        return timerIsUP.get();
    }

    public void stop() {
        lock.lock();
        try{
            cancelCurrentTimeout();
            scheduler.shutdownNow();
        }finally{
            lock.unlock();
        }
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