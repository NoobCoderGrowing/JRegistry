package io.github.noobcodergrowing.jregistry.Web.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ClusterStatusDTO {
    private int localNodeId;
    private int clusterSize;
    private int leaderId;
    private String leaderHost;
    private int leaderPort;
    private long currentTerm;
    private String localRole;
    private int connectedPeers;
    private int quorum;
    private List<NodeInfoDTO> nodes;
}
