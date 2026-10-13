package io.github.noobcodergrowing.jregistrycore.RPC;

import lombok.Data;
import io.github.noobcodergrowing.jregistrycore.KV;
import io.github.noobcodergrowing.jregistrycore.NodeAddress;
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
    private List<KV> kvs;

    // client session part: identifies the JRegistryClient connection behind a request
    private String clientId;
    private long clientEpoch;
    private int ownerNodeId;

    /** 读回复里带出的二值状态：false = 生效，true = 该 key 所属客户端已离线。 */
    private boolean offline;

    /** pickNode 回复：按当前连接数升序排好的候选节点，客户端从头开始试。 */
    private List<NodeAddress> nodes;
}
