package hawk.JRegistryCenter.Raft.Log;

import org.springframework.beans.factory.annotation.Autowired;
import hawk.JRegistryCenter.Raft.RaftNode;
import hawk.JRegistryCenter.Raft.RPC.Client.RaftClientManager;
import hawk.JRegitstryCore.RPC.CLIRequest;
import hawk.JRegitstryCore.Log.LogEntry;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import hawk.JRegitstryCore.RPC.RaftRequest;
import com.alibaba.fastjson.JSON;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LogService {


    @Autowired
    private RaftNode raftNode;

    private RaftClientManager raftClientManager;

    private ArrayList<LogEntry> logger = new ArrayList<>();
    private AtomicInteger currentIndex = new AtomicInteger(-1);
    private ReentrantReadWriteLock logLock = new ReentrantReadWriteLock();
    private  ExecutorService workerPool = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());    

   public LogService(RaftClientManager raftClientManager){
        this.raftClientManager = raftClientManager;
   }

    public void generateLogEntry(CLIRequest cliRequest){
        logLock.writeLock().lock();
        long prevLogIndex = -1;
        long prevLogTerm = -1;
        
        if(logger.size() != 0){ // always keep last log in logger
            prevLogIndex = logger.get(logger.size() - 1).getIndex();
            prevLogTerm = logger.get(logger.size() - 1).getTerm();    
        }
        LogEntry logEntry = new LogEntry();
        logEntry.setTerm(raftNode.getCurrentTerm());
        logEntry.setIndex(currentIndex.incrementAndGet());
        logEntry.setCommand(cliRequest.getType());
        logEntry.setKey(cliRequest.getKey());
        logEntry.setData(cliRequest.getData());
        logEntry.setDataType(cliRequest.getDataType());
        logger.add(logEntry);
        raftNode.setLastLogIndex(logEntry.getIndex());
        raftNode.setLastLogTerm(logEntry.getTerm());
        logLock.writeLock().unlock();
        replicateLog(logEntry, prevLogIndex, prevLogTerm);
    }

    public void replicateLog(LogEntry logEntry, long prevLogIndex, long prevLogTerm){
        RaftRequest raftRequest = new RaftRequest();
        raftRequest.setType("AppendEntries");
        raftRequest.setId(raftNode.getId());
        raftRequest.setTerm(raftNode.getCurrentTerm());
        raftRequest.setId(raftNode.getId());
        raftRequest.setLeaderCommit(raftNode.getLeaderCommit());
        raftRequest.setPrevLogIndex(prevLogIndex);
        raftRequest.setPrevLogTerm(prevLogTerm);
        raftRequest.setLog(logEntry);
        raftClientManager.sendToAllPeers(JSON.toJSONString(raftRequest));
    }

    public void appendLog(LogEntry logEntry){
        // insertion sort
        logLock.writeLock().lock();
        int i = logger.size() - 1;
        while(i >= 0 && logger.get(i).getIndex() > logEntry.getIndex()){
            i--;
        }
        logger.add(i + 1, logEntry);
        raftNode.setLastLogIndex(logger.get(logger.size() - 1).getIndex());
        raftNode.setLastLogTerm(logger.get(logger.size() - 1).getTerm());
        logLock.writeLock().unlock();
    }

    public boolean containLog(long logIndex, long logTerm){
        logLock.readLock().lock();
        if(logger.size() == 0){
            return false;
        }
        long index = logIndex - logger.get(0).getIndex();
        LogEntry logEntry = logger.get((int) index);
        if(logEntry.getTerm() == logTerm && logEntry.getIndex() == logIndex){
            logLock.readLock().unlock();
            return true;
        }
        logLock.readLock().unlock();
        return false;
    }

    public static void main(String[] args) {
        ArrayList<LogEntry> log = new ArrayList<>();
        System.out.println(log.size());
    }


    
}
