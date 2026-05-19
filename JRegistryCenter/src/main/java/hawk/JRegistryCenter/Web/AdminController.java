package hawk.JRegistryCenter.Web;

import hawk.JRegistryCenter.Raft.RaftNode;
import hawk.JRegistryCenter.Web.dto.ClusterStatusDTO;
import hawk.JRegistryCenter.Web.dto.NodeInfoDTO;
import hawk.JRegistryCenter.Web.dto.NodeStatusDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private RaftNode raftNode;

    @Value("${raft.node-id}")
    private int localNodeId;

    @Value("${host}")
    private String host;

    @Value("${server.port}")
    private int httpPort;

    @Value("${raft.port}")
    private int raftPort;

    @GetMapping("/cluster")
    public ClusterStatusDTO cluster() {
        return adminService.getClusterStatus();
    }

    @GetMapping("/node")
    public NodeStatusDTO node() {
        NodeStatusDTO dto = new NodeStatusDTO();
        dto.setNodeId(localNodeId);
        dto.setHost(host);
        dto.setHttpPort(httpPort);
        dto.setRaftPort(raftPort);
        dto.setCurrentTerm(raftNode.getCurrentTerm());
        dto.setCommitIndex(raftNode.getCommitIndex());
        dto.setLastLogIndex(raftNode.getLastLogIndex());
        dto.setLeaderId(raftNode.getLeaderId());
        dto.setLeaderHost(raftNode.getLeaderHost());
        dto.setLeaderPort(raftNode.getLeaderPort());
        dto.setVoteReceived(raftNode.getVoteReceived().get());
        dto.setLeader(raftNode.getIsLeader().get());
        dto.setCandidate(raftNode.getIsCandidate().get());
        dto.setFollower(!dto.isLeader() && !dto.isCandidate());
        if (dto.isLeader()) {
            dto.setRole("LEADER");
        } else if (dto.isCandidate()) {
            dto.setRole("CANDIDATE");
        } else {
            dto.setRole("FOLLOWER");
        }
        dto.setActivePeerConnections(adminService.getActivePeerConnections());
        return dto;
    }

    @GetMapping("/self")
    public NodeInfoDTO self() {
        return adminService.getSelfStatus();
    }
}
