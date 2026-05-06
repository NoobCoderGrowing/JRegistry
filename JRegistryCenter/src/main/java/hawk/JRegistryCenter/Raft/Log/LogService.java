package hawk.JRegistryCenter.Raft.Log;

import org.springframework.beans.factory.annotation.Autowired;
import hawk.JRegistryCenter.Raft.RaftNode;
import hawk.JRegistryCenter.Raft.RPC.Client.RaftClientManager;
import hawk.JRegitstryCore.RPC.CLIRequest;
import hawk.JRegitstryCore.Log.LogEntry;
import java.util.ArrayList;
import hawk.JRegitstryCore.RPC.RaftRequest;
import com.alibaba.fastjson.JSON;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import io.netty.channel.Channel;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import javax.annotation.PostConstruct;
import java.util.concurrent.locks.ReentrantLock;


public class LogService {

    @Autowired
    private RaftNode raftNode;

    private RaftClientManager raftClientManager;

    private ArrayList<LogEntry> logger = new ArrayList<>();
    private AtomicLong currentIndex = new AtomicLong(-1);
    private ReentrantReadWriteLock logLock = new ReentrantReadWriteLock();
    public ConcurrentHashMap<Integer, Long> matchIndexMap = new ConcurrentHashMap<>();
    private CommitWatcher commitWatcher;
    private ReentrantLock commitLock = new ReentrantLock();

    @Value("${raft.count}")
    private int nodeCount;

   public LogService(RaftClientManager raftClientManager){
        this.raftClientManager = raftClientManager;
        registerCommitWatcher();
   }

   @PostConstruct
   public void registerCommitWatcher(){
        this.commitWatcher = new CommitWatcher(this, raftNode, nodeCount);
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
        replicateLog2All(logEntry, prevLogIndex, prevLogTerm);
    }

    public LogEntry getLog(long logIndex){
        logLock.readLock().lock();
        try{
            long startIndex = logIndex - logger.get(0).getIndex();
            if(startIndex < 0){
                logLock.readLock().unlock();
                return null;
            }
            return logger.get((int) startIndex);
        }finally{
            logLock.readLock().unlock();
        }
    }

    public long getLogTerm(long logIndex){
        logLock.readLock().lock();
        try{
            long startIndex = logIndex - logger.get(0).getIndex();
            if(startIndex < 0){
                return -1;
            }
            return logger.get((int) startIndex).getTerm();
        }finally{
            logLock.readLock().unlock();
        }
    }

    public void deleteLogs(long startIndex){
        logLock.writeLock().lock();
        try{
            int i = logger.size() - 1;
            while(i >= 0 && logger.get(i).getIndex() > startIndex){
                i--;
            }
            logger.subList(i + 1, logger.size()).clear();
        }finally{
            logLock.writeLock().unlock();
        }
    }

    public void replicateLog2All(LogEntry logEntry, long prevLogIndex, long prevLogTerm){
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

    public void replicateLog(long prevLogIndex, long prevLogTerm, Channel channel, LogEntry currentLog){
        RaftRequest raftRequest = new RaftRequest();
        raftRequest.setType("AppendEntries");
        raftRequest.setId(raftNode.getId());
        raftRequest.setTerm(raftNode.getCurrentTerm());
        raftRequest.setLeaderCommit(raftNode.getLeaderCommit());
        raftRequest.setPrevLogIndex(prevLogIndex);
        raftRequest.setPrevLogTerm(prevLogTerm);
        raftRequest.setLog(currentLog);
        channel.writeAndFlush(JSON.toJSONString(raftRequest) + "\n");
    }

    // insertion sort version
    // public void appendLog(LogEntry logEntry){
    //     // insertion sort
    //     logLock.writeLock().lock();
    //     try{
    //         int i = logger.size() - 1;
    //         while(i >= 0 && logger.get(i).getIndex() > logEntry.getIndex()){
    //             i--;
    //         }
    //         logger.add(i + 1, logEntry);
    //         raftNode.setLastLogIndex(logger.get(logger.size() - 1).getIndex());
    //         raftNode.setLastLogTerm(logger.get(logger.size() - 1).getTerm());
    //     }finally{
    //         logLock.writeLock().unlock();
    //     }
    // }

    public void appendLog(LogEntry logEntry){
        // insertion sort
        logLock.writeLock().lock();
        try{
            logger.add(logEntry);
            raftNode.setLastLogIndex(logger.get(logger.size() - 1).getIndex());
            raftNode.setLastLogTerm(logger.get(logger.size() - 1).getTerm());
        }finally{
            logLock.writeLock().unlock();
        }
    }

    public boolean containLog(long logTerm, long logIndex){
        if(logIndex == -1){
            return true;
        }
        if(logger.size() == 0){
            return false;
        }
        logLock.readLock().lock();
        try {
            
            long index = logIndex - logger.get(0).getIndex();
            if(logger.size() < index + 1){
                return false;
            }
    
            LogEntry logEntry = logger.get((int) index);
            if(logEntry.getTerm() == logTerm && logEntry.getIndex() == logIndex){
                logLock.readLock().unlock();
                return true;
            }
            return false;
        } finally{
            logLock.readLock().unlock();
        }
    }

    public LogEntry containAndGetNextLog(long logIndex, long logTerm){
        logLock.readLock().lock();
        try{
            if(logger.size() <= 1){
                return null;
            }
            long index = logIndex - logger.get(0).getIndex();
            if(index < 0){
                return null;
            }
            LogEntry logEntry = logger.get((int) index);
            if(logEntry.getTerm() == logTerm && logEntry.getIndex() == logIndex){
                logEntry = logger.get((int) index + 1);
                return logEntry;
            }
            return null;
        }finally{
            logLock.readLock().unlock();
        }
    }

    public void sendSnapshot(RaftRequest request, Channel channel){
        logLock.readLock().lock();
        try{
            RaftRequest raftRequest = new RaftRequest();
            raftRequest.setType("installSnapshot");
            raftRequest.setId(raftNode.getId());
            raftRequest.setTerm(raftNode.getCurrentTerm());
            raftRequest.setLeaderCommit(raftNode.getCommitIndex());
            raftRequest.setLastLogIndex(raftNode.getLastLogIndex());
            raftRequest.setLastLogTerm(raftNode.getLastLogTerm());
            raftRequest.setLeaderHost(raftNode.getLeaderHost());
            raftRequest.setLeaderPort(raftNode.getLeaderPort());
            raftRequest.setSnapshot(raftNode.getLsmTree());
            channel.writeAndFlush(JSON.toJSONString(raftRequest) + "\n");
        }finally{
            logLock.readLock().unlock();
        }
    }

    public void installLogger(RaftRequest request){
        logLock.writeLock().lock();
        try{
            logger.clear();
            logger.addAll(request.getLogs());
            currentIndex.set(logger.get(logger.size() - 1).getIndex());
        }finally{
            logLock.writeLock().unlock();
        }
    }

    public void updateMatchIndex(RaftRequest reply){
        int id = reply.getId();
        long matchIndex = reply.getLastLogIndex();
        matchIndexMap.compute(id, (k,v)->{
            if(v == null){
                return matchIndex;
            }
            if(matchIndex > v){
                return matchIndex;
            }
            return v;
        });
        commitWatcher.update();
    }

    public RaftRequest followerCommitLogs(RaftRequest request){
        commitLock.lock();
        try{
            long commitableIndex = request.getLeaderCommit();
            long currentCommitIndex = raftNode.getCommitIndex();
            int startIndex = 0;
            if(logger.size() > 0){
                startIndex = (int) (currentCommitIndex - logger.get(0).getIndex()) + 1;
            }
            while (currentCommitIndex < commitableIndex) {
                if((startIndex < logger.size()) && startIndex >= 0){
                    LogEntry logEntry = logger.get(startIndex);
                    raftNode.getLsmTree().applyLog(logEntry);
                    raftNode.setCommitIndex(logEntry.getIndex());
                    startIndex++;
                    currentCommitIndex++;
                }
            }
            return null;
        }finally{
            commitLock.unlock();
        }
    }

    public void leaderCommitLogs(long commitableIndex){
        commitLock.lock();
        try{
            long commitableTerm = getLogTerm(commitableIndex);
            if(commitableTerm != raftNode.getCurrentTerm()){
                return; // leader only commit logs of current term
            }

            long currentCommitIndex = raftNode.getCommitIndex();
            int startIndex = 0;
            if(logger.size() > 0){
                startIndex = (int) (currentCommitIndex - logger.get(0).getIndex()) + 1;
            }
            while (currentCommitIndex < commitableIndex) {
                LogEntry logEntry = logger.get(startIndex);
                raftNode.getLsmTree().applyLog(logEntry);
                raftNode.setCommitIndex(logEntry.getIndex());
                startIndex++;
                currentCommitIndex++;
            }
            
            RaftRequest raftRequest = new RaftRequest();
            raftRequest.setType("commitLogs");
            raftRequest.setId(raftNode.getId());
            raftRequest.setTerm(raftNode.getCurrentTerm());
            raftRequest.setLeaderCommit(raftNode.getCommitIndex());
            raftClientManager.sendToAllPeers(JSON.toJSONString(raftRequest));
        }finally{
            commitLock.unlock();
        }
        
    }

    public static void main(String[] args) {
        ArrayList<LogEntry> log = new ArrayList<>();
        System.out.println(log.get(0).getIndex());
    }


    
}
