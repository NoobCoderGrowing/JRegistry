package hawk.JRegistryCenter.Raft;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import lombok.Data;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import hawk.JRegitstryCore.LSMTree;
import hawk.JRegitstryCore.BPlusTree;
import lombok.extern.slf4j.Slf4j;
import hawk.JRegitstryCore.RPC.RaftRequest;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import com.alibaba.fastjson.JSON;
import java.io.FileOutputStream;
import java.io.IOException;
import com.alibaba.fastjson.annotation.JSONField;

@Slf4j
@Component
@Data
public class RaftNode {

    @Autowired
    @JSONField(serialize = false)
    private ThreadPoolExecutor writePool;

    @Value("${host}")
    @JSONField(serialize = false)
    private String CLIServerHost;
    @Value("${CLS.port}")
    @JSONField(serialize = false)
    private int CLIServerPort;

    @Value("${raft.node-id}")
    @JSONField(serialize = false)
    private int id;

    @JSONField(serialize = false)
    private volatile AtomicBoolean isLeader;

    @JSONField(serialize = false)
    private volatile AtomicBoolean isCandidate;
    @JSONField(serialize = false)
    private String leaderHost;  
    @JSONField(serialize = false)
    private int leaderPort;

    private volatile long termVoted;
    @JSONField(serialize = false)
    private AtomicInteger voteReceived;


    //State part in raft paper
    private volatile long currentTerm;

    private long commitIndex;
    // private long lastApplied;
    // private long[] nextIndex;
    // private long[] matchIndex;

    //Append Entries part in raft paper
    @JSONField(serialize = false)
    private long leaderTerm;
    @JSONField(serialize = false)
    private volatile int leaderId;
    // private String[] entries;
    @JSONField(serialize = false)
    private long leaderCommit;

    //Request Vote part in raft paper
    private long lastLogIndex;
    private long lastLogTerm;
    private LSMTree lsmTree;

    public RaftNode(){
        this.isLeader = new AtomicBoolean(false);
        this.isCandidate = new AtomicBoolean(true); // candidate by default
        this.currentTerm = -1;
        this.commitIndex = -1;
        // this.nextIndex = new long[10];
        // this.matchIndex = new long[10];
        this.leaderTerm = -1;
        this.termVoted = -1;
        this.voteReceived = new AtomicInteger(0);
        this.lastLogIndex = -1;
        this.lastLogTerm = -1;
        this.leaderId = -1;
        this.lsmTree = new BPlusTree();
    }

    public void setLsmTree(LSMTree lsmTree){
        this.lsmTree = lsmTree;
    }

    public LSMTree getLsmTree(){
        return this.lsmTree;
        
    }

    public void turn2CandidateTimeout(){
        log.info("server {} turn to candidate term {}", this.getId(), this.getCurrentTerm() + 1);
        this.getIsCandidate().compareAndSet(false, true);
        this.getIsLeader().compareAndSet(true, false); // 放弃leader身份
        this.setCurrentTerm(this.getCurrentTerm() + 1);
        this.setLeaderId(-1);   
        this.setLeaderHost(null);
        this.setLeaderPort(-1);
        this.setTermVoted(this.getCurrentTerm());
        this.getVoteReceived().set(1);
    }

    public void turn2Follower(RaftRequest request){
        log.info("server {} turn to follower from higher term {} node {}", this.getId(), request.getTerm(), request.getId());
        this.getIsCandidate().compareAndSet(true, false);
        this.getIsLeader().compareAndSet(true, false); // 放弃leader身份
        this.setCurrentTerm(request.getTerm());
        this.setLeaderId(-1);   
        this.setLeaderHost(null);
        this.setLeaderPort(-1);
        this.setTermVoted(-1);
        this.getVoteReceived().set(0);
    }

    public void turn2Leader(){
        this.getIsLeader().compareAndSet(false, true);
        this.getIsCandidate().compareAndSet(true, false);
        this.setLeaderId(this.getId());
        this.setLeaderHost(CLIServerHost);
        this.setLeaderPort(CLIServerPort);
    }

    public void acceptLeader(RaftRequest request){
        log.info("server {} accept leader from leader node {}", this.getId(), request.getId());
        this.getIsCandidate().compareAndSet(true, false); // 放弃candidate身份
        this.getIsLeader().compareAndSet(true, false); // 放弃leader身份
        this.setCurrentTerm(request.getTerm());
        this.setLeaderId(request.getId());   
        this.setLeaderHost(request.getLeaderHost());
        this.setLeaderPort(request.getLeaderPort());
    }

    public boolean persist(){
        String serializedNode = JSON.toJSONString(this);

        writePool.execute(() -> {
            try {
                FileOutputStream fileOutputStream = new FileOutputStream("raftNode"+this.getId()+".json");
                fileOutputStream.write(serializedNode.getBytes());
                fileOutputStream.close();
            } catch (IOException e) {
                log.error("node {} persist failed", this.getId());
            } 
        });    
        return true;
    }

    public void recoverFromImage(RaftNode nodeImage){
        this.setCurrentTerm(nodeImage.getCurrentTerm());
        this.setCommitIndex(nodeImage.getCommitIndex());
        this.setLsmTree(nodeImage.getLsmTree());
        this.setLastLogTerm(nodeImage.getLastLogTerm());
        this.setLastLogIndex(nodeImage.getLastLogIndex());

        this.setLeaderId(-1);
        this.setLeaderHost(null);
        this.setLeaderPort(-1);
        this.setTermVoted(nodeImage.getTermVoted());
        this.getVoteReceived().set(0);
        this.getIsLeader().compareAndSet(true, false);
        this.getIsCandidate().compareAndSet(true, false);
    }
}
