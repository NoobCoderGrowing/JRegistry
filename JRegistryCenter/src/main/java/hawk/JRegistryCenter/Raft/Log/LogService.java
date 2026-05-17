package hawk.JRegistryCenter.Raft.Log;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import hawk.JRegistryCenter.Raft.RaftNode;
import hawk.JRegistryCenter.Raft.RPC.Client.RaftClientManager;
import hawk.JRegitstryCore.Log.LogEntry;
import java.util.ArrayList;
import hawk.JRegitstryCore.RPC.RaftRequest;
import hawk.JRegitstryCore.RPC.SSH.SSHRequest;

import com.alibaba.fastjson.JSON;
import io.netty.channel.Channel;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import javax.annotation.PostConstruct;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.stereotype.Service;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;



@Service
@Data
@Slf4j
public class LogService {

    @Autowired
    private RaftNode raftNode;

    /** 延迟解析，避免 LogService → RaftClientManager → AppendEntries → LogService 环 */
    @Autowired
    private ObjectProvider<RaftClientManager> raftClientManagerProvider;

    private ArrayList<LogEntry> logger = new ArrayList<>();
    private AtomicLong currentIndex = new AtomicLong(-1);
    public ConcurrentHashMap<Integer, Long> matchIndexMap = new ConcurrentHashMap<>();
    public ConcurrentHashMap<Integer, Long> nextIndexMap = new ConcurrentHashMap<>();
    private CommitWatcher commitWatcher;

     /** 防止父子容器各刷新一次时重复初始化 */
     private final AtomicBoolean indexMapInitialized = new AtomicBoolean(false);

    @Autowired
    private ThreadPoolExecutor writePool;

    @Value("${raft.count}")
    private int nodeCount;

    @Value("#{${raft.peers:{}}}")
    private Map<Integer, String> peers;

    @PostConstruct
    public void initIndexMap(){
        peers.forEach((k,v)->{
            nextIndexMap.put(k, 0L);
            matchIndexMap.put(k, -1L);
        });
    }


    // @EventListener(ContextRefreshedEvent.class)
    // public void initIndexMap(ContextRefreshedEvent event){

    //     if(event.getApplicationContext().getParent() != null){
    //         return;
    //     }

    //     if(!indexMapInitialized.compareAndSet(false, true)){ // 防止重复初始化
    //         return;
    //     }
    //     raftClientManagerProvider.getObject().getPeerChannels().forEach((k,v)->{
    //         nextIndexMap.put(k, 0L);
    //         matchIndexMap.put(k, -1L);
    //     });
    // }




   @PostConstruct
   public void registerCommitWatcher(){
        this.commitWatcher = new CommitWatcher(this, raftNode, nodeCount);
   }

   public RaftRequest handleWriteRequest(RaftRequest request){
        log.info("node {} handle write request: {}", raftNode.getId(), JSON.toJSONString(request));
        generateLogEntry(request);
        return null;
   }

   

    public void generateLogEntry(RaftRequest request){
        long prevLogIndex = -1;
        long prevLogTerm = -1;
        
        if(logger.size() != 0){ // always keep last log in logger
            prevLogIndex = logger.get(logger.size() - 1).getIndex();
            prevLogTerm = logger.get(logger.size() - 1).getTerm();    
        }
        LogEntry logEntry = new LogEntry();
        logEntry.setTerm(raftNode.getCurrentTerm());
        logEntry.setIndex(currentIndex.incrementAndGet());
        logEntry.setCommand(request.getCmd());
        logEntry.setKey(request.getKey());
        logEntry.setData(request.getData());
        logEntry.setDataType(request.getDataType());
        logger.add(logEntry);
        raftNode.setLastLogIndex(logEntry.getIndex());
        raftNode.setLastLogTerm(logEntry.getTerm());
        log.info("node {} replicate log to all nodes", raftNode.getId());
        replicateLog2All();
    }

    public void generateNoOpLog(){
        long prevLogIndex = -1;
        long prevLogTerm = -1;
        
        if(logger.size() != 0){ // always keep last log in logger
            prevLogIndex = logger.get(logger.size() - 1).getIndex();
            prevLogTerm = logger.get(logger.size() - 1).getTerm();    
        }
        LogEntry logEntry = new LogEntry();
        logEntry.setTerm(raftNode.getCurrentTerm());
        logEntry.setIndex(currentIndex.incrementAndGet());
        logEntry.setCommand("noOp");
        logger.add(logEntry);
        raftNode.setLastLogIndex(logEntry.getIndex());
        raftNode.setLastLogTerm(logEntry.getTerm());
        log.info("term {} node {} generate no op log", raftNode.getCurrentTerm(), raftNode.getId());
        replicateLog2All();
    }

    public void generateLogEntry(SSHRequest cliRequest){
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
        replicateLog2All();
    }

    public LogEntry getLog(long logIndex){
        
        long startIndex = logIndex - logger.get(0).getIndex();
        if(startIndex < 0){
            return null;
        }
        return logger.get((int) startIndex);
        
    }

    public long getLogTerm(long logIndex){
        long startIndex = logIndex - logger.get(0).getIndex();
        if(startIndex < 0){
            return -1;
        }
        return logger.get((int) startIndex).getTerm();
    }

    public void deleteLogs(long startIndex){
        int i = logger.size() - 1;
        while(i >= 0 && logger.get(i).getIndex() > startIndex){
            i--;
        }
        logger.subList(i + 1, logger.size()).clear();
        
    }

    // public void replicateLog2All(LogEntry logEntry, long prevLogIndex, long prevLogTerm){
    //     RaftRequest raftRequest = new RaftRequest();
    //     raftRequest.setType("appendEntries");
    //     raftRequest.setId(raftNode.getId());
    //     raftRequest.setTerm(raftNode.getCurrentTerm());
    //     raftRequest.setId(raftNode.getId());
    //     raftRequest.setLeaderCommit(raftNode.getLeaderCommit());
    //     raftRequest.setPrevLogIndex(prevLogIndex);
    //     raftRequest.setPrevLogTerm(prevLogTerm);
    //     raftRequest.setLog(logEntry);
    //     raftClientManagerProvider.getObject().sendToAllPeers(JSON.toJSONString(raftRequest));
    // }

    public void replicateLog2All(){
        raftClientManagerProvider.getObject().getPeerChannels().forEach((k,v)->{
            replicateLog(k, v);
        });
    }

    // public void replicateLog(long prevLogIndex, long prevLogTerm, Channel channel, LogEntry currentLog){
    //     RaftRequest raftRequest = new RaftRequest();
    //     raftRequest.setType("appendEntries");
    //     raftRequest.setId(raftNode.getId());
    //     raftRequest.setTerm(raftNode.getCurrentTerm());
    //     raftRequest.setLeaderCommit(raftNode.getLeaderCommit());
    //     raftRequest.setPrevLogIndex(prevLogIndex);
    //     raftRequest.setPrevLogTerm(prevLogTerm);
    //     raftRequest.setLog(currentLog);
    //     writePool.execute(() -> {
    //         channel.writeAndFlush(JSON.toJSONString(raftRequest) + "\n");
    //     });
    // }


    public void replicateLog( int id , Channel channel){
        long nextIndex = nextIndexMap.get(id);
        if(nextIndex > raftNode.getLastLogIndex()){
            return;
        }
        
        if(nextIndex == 0){
            LogEntry currentLog = getLog(nextIndex);
            RaftRequest raftRequest = new RaftRequest();
            raftRequest.setType("appendEntries");
            raftRequest.setId(raftNode.getId());
            raftRequest.setTerm(raftNode.getCurrentTerm());
            raftRequest.setLeaderCommit(raftNode.getLeaderCommit());
            raftRequest.setPrevLogIndex(-1);
            raftRequest.setPrevLogTerm(-1);
            raftRequest.setLog(currentLog);
            writePool.execute(() -> {
                channel.writeAndFlush(JSON.toJSONString(raftRequest) + "\n");
            });
            return;
        }

        LogEntry currentLog = getLog(nextIndex);
        LogEntry prevLog = getLog(nextIndex - 1);
        if(currentLog == null || prevLog == null){
            sendSnapshot(channel);
            return;
        }
        RaftRequest raftRequest = new RaftRequest();
        raftRequest.setType("appendEntries");
        raftRequest.setId(raftNode.getId());
        raftRequest.setTerm(raftNode.getCurrentTerm());
        raftRequest.setLeaderCommit(raftNode.getLeaderCommit());
        raftRequest.setPrevLogIndex(prevLog.getIndex());
        raftRequest.setPrevLogTerm(prevLog.getTerm());
        raftRequest.setLog(currentLog);
        writePool.execute(() -> {
            channel.writeAndFlush(JSON.toJSONString(raftRequest) + "\n");
        });
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
        logger.add(logEntry);
        raftNode.setLastLogIndex(logger.get(logger.size() - 1).getIndex());
        raftNode.setLastLogTerm(logger.get(logger.size() - 1).getTerm());
        
    }

    public boolean containLog(long logTerm, long logIndex){
        if(logIndex == -1){
            return true;
        }
        if(logger.size() == 0){
            return false;
        }
            
        long index = logIndex - logger.get(0).getIndex();
        if(logger.size() < index + 1){
            return false;
        }

        LogEntry logEntry = logger.get((int) index);
        if(logEntry.getTerm() == logTerm && logEntry.getIndex() == logIndex){
            return true;
        }
        return false;
        
    }

    public LogEntry containAndGetNextLog(long logIndex, long logTerm){
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
    }

    public void sendSnapshot(Channel channel){
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
        raftRequest.setLogs(logger);
        writePool.execute(() -> {
            channel.writeAndFlush(JSON.toJSONString(raftRequest) + "\n");
        });
    }

    

    public void installLogger(RaftRequest request){
        logger.clear();
        logger.addAll(request.getLogs());
        currentIndex.set(logger.get(logger.size() - 1).getIndex());
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
        log.info("Node {} matchIndexMap updated to {}", raftNode.getId(), matchIndexMap);
        commitWatcher.update();
    }

    public RaftRequest followerCommitLogs(RaftRequest request){
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
        log.info("follower node {} commit logs to {}", raftNode.getId(), commitableIndex);
        return null;
    }

    public void commitLogs(long commitableIndex){
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

        log.info("leader node {} require commit logs to {}", raftNode.getId(), commitableIndex);
        raftClientManagerProvider.getObject().sendToAllPeers(JSON.toJSONString(raftRequest));

    }

    public static void main(String[] args) {
        ArrayList<LogEntry> log = new ArrayList<>();
        System.out.println(log.get(0).getIndex());
    }


    
}
