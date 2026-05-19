package hawk.JRegistryCenter.Web;

import hawk.JRegistryCenter.Raft.RPC.Client.RaftClientManager;
import hawk.JRegistryCenter.Raft.RPC.Server.RaftServerManager;
import hawk.JRegistryCenter.Raft.RaftNode;
import hawk.JRegistryCenter.Web.dto.ClusterStatusDTO;
import hawk.JRegistryCenter.Web.dto.NodeInfoDTO;
import hawk.JRegistryCenter.Web.dto.NodeStatusDTO;
import io.netty.channel.Channel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class AdminService {

    @Autowired
    private RaftNode raftNode;

    @Autowired
    private RaftServerManager raftServerManager;

    @Autowired
    private RaftClientManager raftClientManager;

    @Value("${raft.node-id}")
    private int localNodeId;

    @Value("${host}")
    private String host;

    @Value("${server.port}")
    private int httpPort;

    @Value("${raft.port}")
    private int raftPort;

    @Value("${ssh.port}")
    private int sshPort;

    @Value("${CLS.port}")
    private int clsPort;

    @Value("${raft.count:3}")
    private int clusterSize;

    @Value("#{${raft.peers:{}}}")
    private Map<Integer, String> raftPeers;

    @Value("#{${admin.peer-http-ports:{}}}")
    private Map<Integer, Integer> peerHttpPorts;

    private final RestTemplate restTemplate;

    public AdminService(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(800))
                .setReadTimeout(Duration.ofMillis(800))
                .build();
    }

    public ClusterStatusDTO getClusterStatus() {
        Map<Integer, NodeStatusDTO> remoteStatus = fetchPeerStatuses();
        List<NodeInfoDTO> nodes = buildNodeList(remoteStatus);
        int leaderId = raftNode.getLeaderId();
        int connectedPeers = countConnectedPeers(nodes, localNodeId);

        return ClusterStatusDTO.builder()
                .localNodeId(localNodeId)
                .clusterSize(clusterSize)
                .leaderId(leaderId)
                .leaderHost(raftNode.getLeaderHost())
                .leaderPort(raftNode.getLeaderPort())
                .currentTerm(raftNode.getCurrentTerm())
                .localRole(resolveRole(raftNode))
                .connectedPeers(connectedPeers)
                .quorum(clusterSize / 2 + 1)
                .nodes(nodes)
                .build();
    }

    public NodeInfoDTO getSelfStatus() {
        return buildSelfNode();
    }

    private Map<Integer, NodeStatusDTO> fetchPeerStatuses() {
        if (peerHttpPorts == null || peerHttpPorts.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, NodeStatusDTO> result = new TreeMap<>();
        for (Map.Entry<Integer, Integer> entry : peerHttpPorts.entrySet()) {
            int nodeId = entry.getKey();
            if (nodeId == localNodeId) {
                continue;
            }
            try {
                String url = "http://127.0.0.1:" + entry.getValue() + "/api/admin/node";
                NodeStatusDTO status = restTemplate.getForObject(url, NodeStatusDTO.class);
                if (status != null) {
                    result.put(nodeId, status);
                }
            } catch (Exception ignored) {
                // peer HTTP admin may be down
            }
        }
        return result;
    }

    private List<NodeInfoDTO> buildNodeList(Map<Integer, NodeStatusDTO> remoteStatus) {
        List<NodeInfoDTO> nodes = new ArrayList<>();
        nodes.add(buildSelfNode());

        Map<Integer, String> allPeers = new TreeMap<>(raftPeers);
        for (Map.Entry<Integer, String> entry : allPeers.entrySet()) {
            int peerId = entry.getKey();
            NodeStatusDTO remote = remoteStatus.get(peerId);
            nodes.add(buildPeerNode(peerId, entry.getValue(), remote));
        }
        nodes.sort((a, b) -> Integer.compare(a.getNodeId(), b.getNodeId()));
        return nodes;
    }

    private NodeInfoDTO buildSelfNode() {
        return NodeInfoDTO.builder()
                .nodeId(localNodeId)
                .host(host)
                .raftPort(raftPort)
                .httpPort(httpPort)
                .sshPort(sshPort)
                .clsPort(clsPort)
                .role(resolveRole(raftNode))
                .connected(true)
                .self(true)
                .currentTerm(raftNode.getCurrentTerm())
                .commitIndex(raftNode.getCommitIndex())
                .lastLogIndex(raftNode.getLastLogIndex())
                .leaderId(raftNode.getLeaderId())
                .leaderHost(raftNode.getLeaderHost())
                .leaderPort(raftNode.getLeaderPort())
                .voteReceived(raftNode.getVoteReceived().get())
                .activePeerConnections(countActiveChannels())
                .build();
    }

    private NodeInfoDTO buildPeerNode(int peerId, String raftAddress, NodeStatusDTO remote) {
        String peerHost = raftAddress;
        int peerRaftPort = -1;
        String[] parts = raftAddress.split(":");
        if (parts.length == 2) {
            peerHost = parts[0];
            peerRaftPort = Integer.parseInt(parts[1]);
        }

        int peerHttp = peerHttpPorts != null && peerHttpPorts.containsKey(peerId)
                ? peerHttpPorts.get(peerId)
                : -1;

        boolean connected = isPeerConnected(peerId);
        String role = "UNKNOWN";
        Long term = null;
        Long commitIndex = null;
        Long lastLogIndex = null;
        Integer leaderId = null;
        String leaderHost = null;
        Integer leaderPort = null;
        Integer voteReceived = null;
        Integer activePeerConnections = null;

        if (remote != null) {
            role = remote.getRole() != null ? remote.getRole() : role;
            term = remote.getCurrentTerm();
            commitIndex = remote.getCommitIndex();
            lastLogIndex = remote.getLastLogIndex();
            leaderId = remote.getLeaderId();
            leaderHost = remote.getLeaderHost();
            leaderPort = remote.getLeaderPort();
            voteReceived = remote.getVoteReceived();
            activePeerConnections = remote.getActivePeerConnections();
        }

        Integer activeConnections = activePeerConnections;

        return NodeInfoDTO.builder()
                .nodeId(peerId)
                .host(peerHost)
                .raftPort(peerRaftPort)
                .httpPort(peerHttp)
                .sshPort(-1)
                .clsPort(-1)
                .role(role)
                .connected(connected)
                .self(false)
                .currentTerm(term)
                .commitIndex(commitIndex)
                .lastLogIndex(lastLogIndex)
                .leaderId(leaderId)
                .leaderHost(leaderHost)
                .leaderPort(leaderPort)
                .voteReceived(voteReceived)
                .activePeerConnections(activeConnections)
                .build();
    }

    private boolean isPeerConnected(int peerId) {
        Channel outbound = raftClientManager.getPeerChannels().get(peerId);
        if (outbound != null && outbound.isActive()) {
            return true;
        }
        Channel inbound = raftServerManager.getPeerChannels().get(peerId);
        return inbound != null && inbound.isActive();
    }

    public int getActivePeerConnections() {
        return raftClientManager.getActivePeers() + raftServerManager.getActivePeers();
    }

    private int countActiveChannels() {
        return getActivePeerConnections();
    }

    private int countConnectedPeers(List<NodeInfoDTO> nodes, int selfId) {
        int count = 0;
        for (NodeInfoDTO node : nodes) {
            if (node.getNodeId() != selfId && node.isConnected()) {
                count++;
            }
        }
        return count;
    }

    private String resolveRole(RaftNode node) {
        if (node.getIsLeader() != null && node.getIsLeader().get()) {
            return "LEADER";
        }
        if (node.getIsCandidate() != null && node.getIsCandidate().get()) {
            return "CANDIDATE";
        }
        return "FOLLOWER";
    }
}
