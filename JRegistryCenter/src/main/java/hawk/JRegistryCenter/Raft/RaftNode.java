package hawk.JRegistryCenter.Raft;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import lombok.Data;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import hawk.JRegitstryCore.LSMTree;
import hawk.JRegitstryCore.BPlusTree;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import hawk.JRegitstryCore.RPC.RaftRequest;

@Slf4j
@Component
@Data
public class RaftNode {

    @Value("${raft.node-id}")
    private int id;
    private AtomicBoolean isLeader;
    private AtomicBoolean isCandidate;

    private String leaderHost;
    private int leaderPort;

    //peer nodes
    private Map<Integer, Channel> peerChannels;

    private volatile long termVoted;

    private AtomicInteger voteReceived;


    //State part in raft paper
    private volatile long currentTerm;

    private long commitIndex;
    private long lastApplied;
    // private long[] nextIndex;
    // private long[] matchIndex;

    //Append Entries part in raft paper
    private long leaderTerm;
    private volatile int leaderId;
    // private String[] entries;
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
        this.lastApplied = -1;
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

    public void turn2Candidate(RaftRequest request){
        log.info("server {} turn to candidate from higher term {} node {}", this.getId(), request.getTerm(), request.getId());
        this.getIsCandidate().compareAndSet(false, true);
        this.getIsLeader().compareAndSet(true, false); // 放弃leader身份
        this.setCurrentTerm(request.getTerm());
        this.setLeaderId(-1);   
        this.setLeaderHost(null);
        this.setLeaderPort(-1);
        this.setTermVoted(-1);
        this.getVoteReceived().set(0);
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
        log.info("server {} turn to candidate from higher term {} node {}", this.getId(), request.getTerm(), request.getId());
        this.getIsCandidate().compareAndSet(true, false);
        this.getIsLeader().compareAndSet(true, false); // 放弃leader身份
        this.setCurrentTerm(request.getTerm());
        this.setLeaderId(request.getId());   
        this.setLeaderHost(request.getLeaderHost());
        this.setLeaderPort(request.getLeaderPort());
        this.setTermVoted(request.getTerm());
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
}
