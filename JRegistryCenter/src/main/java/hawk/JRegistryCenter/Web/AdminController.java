package hawk.JRegistryCenter.Web;

import hawk.JRegistryCenter.Raft.Log.LogService;
import hawk.JRegitstryCore.Raft.RaftNode;
import hawk.JRegistryCenter.Web.dto.ClusterStatusDTO;
import hawk.JRegistryCenter.Web.dto.NodeInfoDTO;
import hawk.JRegistryCenter.Web.dto.NodeStatusDTO;
import hawk.JRegistryCenter.Web.dto.StateMachineTreeDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import hawk.JRegitstryCore.StateMachine;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private RaftNode raftNode;

    @Autowired
    private LogService logService;

    @Autowired
    private StateMachine stateMachine;

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
        dto.setCommitIndex(stateMachine.getCommitIndex());
        dto.setLastLogIndex(logService.getLastLogIndex());
        dto.setLastLogTerm(logService.getLastLogTerm());
        dto.setLogCount(logService.getLogCount());
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

    @GetMapping("/state-machine")
    public ResponseEntity<?> stateMachine(@RequestParam(required = false) Integer nodeId) {
        int targetNodeId = nodeId != null ? nodeId : localNodeId;
        try {
            return ResponseEntity.ok(adminService.getStateMachineTree(targetNodeId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Failed to fetch state machine tree for node " + targetNodeId);
        }
    }
}
