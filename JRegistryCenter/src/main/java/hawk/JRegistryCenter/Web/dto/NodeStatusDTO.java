package hawk.JRegistryCenter.Web.dto;

import lombok.Data;

@Data
public class NodeStatusDTO {
    private int nodeId;
    private String host;
    private int httpPort;
    private int raftPort;
    private String role;
    private long currentTerm;
    private long commitIndex;
    private long lastLogIndex;
    private long lastLogTerm;
    private int leaderId;
    private String leaderHost;
    private int leaderPort;
    private int voteReceived;
    private boolean leader;
    private boolean candidate;
    private boolean follower;
    private int activePeerConnections;
}
