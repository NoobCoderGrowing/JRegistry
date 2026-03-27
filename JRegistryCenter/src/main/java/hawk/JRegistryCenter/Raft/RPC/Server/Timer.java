package hawk.JRegistryCenter.Raft.RPC.Server;

import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import hawk.JRegistryCenter.Raft.RaftNode;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.PostConstruct;

@Slf4j
@Component
public class Timer {

    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean timerIsUP;
    private Random random;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    @Autowired
    private RaftNode raftNode;



    // 当前这次计时任务句柄（用于取消）
    private volatile ScheduledFuture<?> timeoutFuture;

    public Timer() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {

            Thread thread = new Thread(r, "Timer-Thread");
            thread.setDaemon(true);
            return thread;
        });
        this.timerIsUP = new AtomicBoolean(false);
        
    }

    @PostConstruct
    public void init(){
        this.random = new Random(raftNode.getId() * 42);
    }


    public void start() {
        resetTimer();
    }

    public void resetTimer() {
        log.info("node {} reset timer", raftNode.getId());
        lock.lock();
        try{
            timerIsUP.set(false);
            cancelCurrentTimeout();
            timeoutFuture = scheduler.schedule(() -> {
                lock.lock();
                try{
                    timerIsUP.set(true);
                    condition.signalAll();
                }catch(Exception e){
                    log.error("node {} reset timer error", raftNode.getId());
                }finally{
                    lock.unlock();
                }
            }, 20000L + random.nextInt(10000), TimeUnit.MILLISECONDS);
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
                lock.lock();
                try{
                    timerIsUP.set(true);
                    condition.signalAll();
                }catch(Exception e){
                    e.printStackTrace();
                }finally{
                    lock.unlock();
                }
            }, timeout, TimeUnit.MILLISECONDS);
        }finally{
            lock.unlock();
        }
    }

    public void awaitTimerUp() throws InterruptedException {
        lock.lock();
        try {
            while (!timerIsUP.get()) { // 阻塞等待计时器超时
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