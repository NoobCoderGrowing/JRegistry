package hawk.JRegistryCenter.Raft.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;

public class LogReplicator {
    
    private LinkedBlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();

    ExecutorService workerPool = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, taskQueue);    
}