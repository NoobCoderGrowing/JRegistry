package io.github.noobcodergrowing.jregistrycore.RPC;

import lombok.Data;
import io.github.noobcodergrowing.jregistrycore.Log.LogEntry;
import io.github.noobcodergrowing.jregistrycore.StateMachine;
import java.util.UUID;
import java.util.List;

@Data
public class RaftRequest {

    //coomon part
    private String type;
    private int id;
    private boolean isLeader;
    //Append Entries part in raft paper
    
    private long term;
    private long prevLogIndex;
    private long prevLogTerm;
    private String[] entries;
    private long leaderCommit;
    private boolean success;
    private long nextIndex;
    //Request Vote part in raft paper
    private long lastLogIndex;     // Candidate 最后一条日志的索引
    private long lastLogTerm; 
    private boolean voteGranted;

    private String leaderHost;
    private int leaderPort;
    
    private LogEntry log;
    private List<LogEntry> logs;
    private StateMachine stateMachine;

    private String cmd;
    private String key;
    private byte[] data;
    private String dataType;
    private UUID uuid;
}
