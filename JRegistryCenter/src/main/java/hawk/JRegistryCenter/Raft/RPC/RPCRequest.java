package hawk.JRegistryCenter.Raft.RPC;

import lombok.Data;

@Data
public class RPCRequest {

    //coomon part
    private String type;
    private int id;
    //Append Entries part in raft paper
    
    private long term;
    private long prevLogIndex;
    private long prevLogTerm;
    private String[] entries;
    private long leaderCommit;
    //Request Vote part in raft paper
    private long lastLogIndex;     // Candidate 最后一条日志的索引
    private long lastLogTerm; 
    private int voteTerm;

}
