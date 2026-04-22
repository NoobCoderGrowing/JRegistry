package hawk.JRegitstryCore.RPC;

import lombok.Data;

@Data
public class RaftRequest {

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

    private String leaderHost;
    private int leaderPort;


    private String cmd;
    private String key;
    private byte[] data;
    private String dataType;
}
