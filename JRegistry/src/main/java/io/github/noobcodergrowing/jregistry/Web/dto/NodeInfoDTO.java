package io.github.noobcodergrowing.jregistry.Web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NodeInfoDTO {
    private int nodeId;
    private String host;
    private int raftPort;
    private int httpPort;
    private int sshPort;
    private int clsPort;
    private String role;
    private boolean connected;
    private boolean self;
    private Long currentTerm;
    private Long commitIndex;
    private Long lastLogIndex;
    private Long lastLogTerm;
    private Integer logCount;
    private Integer leaderId;
    private String leaderHost;
    private Integer leaderPort;
    private Integer voteReceived;
    private Integer activePeerConnections;
}
