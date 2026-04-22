package hawk.JRegistryCenter.Raft.Log;

import org.springframework.beans.factory.annotation.Autowired;
import hawk.JRegistryCenter.Raft.RaftNode;
import org.springframework.stereotype.Service;
import hawk.JRegitstryCore.RPC.CLIRequest;
import hawk.JRegitstryCore.Log.LogEntry;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;


@Service
public class LogService {


    @Autowired
    private RaftNode raftNode;

    private ArrayList<LogEntry> log = new ArrayList<>();
    private AtomicInteger currentIndex = new AtomicInteger(-1);
    private ReentrantLock logLock = new ReentrantLock();
    private  ExecutorService workerPool = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());    


    public void appendLog(CLIRequest cliRequest){
        logLock.lock();
        LogEntry logEntry = new LogEntry();
        logEntry.setTerm(raftNode.getCurrentTerm());
        logEntry.setIndex(currentIndex.incrementAndGet());
        logEntry.setCommand(cliRequest.getType());
        logEntry.setKey(cliRequest.getKey());
        logEntry.setData(cliRequest.getData());
        logEntry.setDataType(cliRequest.getDataType());
        log.add(logEntry);
        logLock.unlock();
    }

    public static void main(String[] args) {
        ArrayList<LogEntry> log = new ArrayList<>();
        System.out.println(log.size());
    }


    
}
