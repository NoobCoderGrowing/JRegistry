package hawk.JRegistryCenter.Raft;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import lombok.Data;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.Channel;

@Component
@Data
public class RaftNode {

    @Value("${raft.node-id}")
    private int id;
    private AtomicBoolean isLeader;
    private AtomicBoolean isCandidate;

    //peer nodes
    private Map<Integer, Channel> peerChannels;

    private volatile long termVoted;

    private AtomicInteger voteReceived;


    //State part in raft paper
    private long currentTerm;
    private long votedFor;
    private long commitIndex;
    private long lastApplied;
    private long[] nextIndex;
    private long[] matchIndex;

    //Append Entries part in raft paper
    private long leaderTerm;
    private int leaderId;
    private long prevLogIndex;
    private long prevLogTerm;
    private String[] entries;
    private long leaderCommit;

    //Request Vote part in raft paper
    private long lastLogIndex;
    private long lastLogTerm;

    public RaftNode(){
        this.isLeader = new AtomicBoolean(false);
        this.isCandidate = new AtomicBoolean(false);
        this.currentTerm = -1;
        this.votedFor = -1;
        this.commitIndex = -1;
        this.lastApplied = -1;
        this.nextIndex = new long[10];
        this.matchIndex = new long[10];
        this.leaderTerm = -1;
        this.termVoted = -1;
        this.voteReceived = new AtomicInteger(0);
       
    }
}
