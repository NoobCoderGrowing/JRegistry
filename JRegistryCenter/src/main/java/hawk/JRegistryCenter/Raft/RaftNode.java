package hawk.JRegistryCenter.Raft;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import lombok.Data;

@Component
@Data
public class RaftNode {

    @Value("${raft.node-id}")
    private int id;
    private boolean isLeader;


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

}
