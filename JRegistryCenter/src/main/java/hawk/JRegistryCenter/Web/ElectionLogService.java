package hawk.JRegistryCenter.Web;

import hawk.JRegistryCenter.Web.dto.ElectionEventDTO;
import hawk.JRegistryCenter.Web.dto.ElectionTimelineDTO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ElectionLogService {

    private static final Pattern LINE_PREFIX = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}).*? - (.+)$");

    private static final Pattern RAFT_START = Pattern.compile("Raft Server (\\d+) started on port (\\d+)");
    private static final Pattern TURN_CANDIDATE = Pattern.compile("server (\\d+) turn to candidate term (\\d+)");
    private static final Pattern START_ELECTION = Pattern.compile("node (\\d+) timeout, start election term (\\d+)");
    private static final Pattern REQUEST_VOTE = Pattern.compile("Candidate (\\d+) send request vote to node (\\d+)");
    private static final Pattern GRANT_VOTE = Pattern.compile("server (\\d+) granted vote to node (\\d+)");
    private static final Pattern BECOME_LEADER = Pattern.compile(
            "term (\\d+) ,client node (\\d+) become leader, (\\d+) votes received");
    private static final Pattern TURN_FOLLOWER = Pattern.compile(
            "server (\\d+) turn to follower from higher term (\\d+) node (\\d+)");
    private static final Pattern ACCEPT_LEADER = Pattern.compile("server (\\d+) accept leader from leader node (\\d+)");

    @Value("${raft.count:3}")
    private int clusterSize;

    @Value("${admin.election-log-path:JRegistryCenter/logs/JRegistryCenter.log}")
    private String electionLogPath;

    public ElectionTimelineDTO parseStartupElectionTimeline() throws IOException {
        Path path = Path.of(electionLogPath);
        if (!Files.exists(path)) {
            throw new IOException("Log file not found: " + path.toAbsolutePath());
        }

        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<ElectionEventDTO> events = new ArrayList<>();
        long sequence = 0;
        int finalLeaderId = -1;
        long finalTerm = -1;

        for (String line : lines) {
            Matcher prefix = LINE_PREFIX.matcher(line);
            if (!prefix.find()) {
                continue;
            }
            String timestamp = prefix.group(1);
            String message = prefix.group(2);

            ElectionEventDTO event = parseLine(timestamp, message, ++sequence);
            if (event != null) {
                events.add(event);
            }
        }

        events.sort(Comparator.comparing(ElectionEventDTO::getTimestamp));

        int leaderIndex = -1;
        for (int i = 0; i < events.size(); i++) {
            if ("BECOME_LEADER".equals(events.get(i).getEventType())) {
                leaderIndex = i;
                finalLeaderId = events.get(i).getNodeId();
                finalTerm = events.get(i).getTerm();
                break;
            }
        }
        if (leaderIndex >= 0) {
            events = new ArrayList<>(events.subList(0, leaderIndex + 1));
        }

        for (int i = 0; i < events.size(); i++) {
            events.get(i).setSequence(i + 1);
        }

        return ElectionTimelineDTO.builder()
                .clusterSize(clusterSize)
                .finalLeaderId(finalLeaderId)
                .finalTerm(finalTerm)
                .logPath(path.toAbsolutePath().toString())
                .events(events)
                .build();
    }

    private ElectionEventDTO parseLine(String timestamp, String message, long sequence) {
        Matcher matcher;

        matcher = RAFT_START.matcher(message);
        if (matcher.find()) {
            int nodeId = Integer.parseInt(matcher.group(1));
            return ElectionEventDTO.builder()
                    .sequence(sequence)
                    .timestamp(timestamp)
                    .eventType("STARTUP")
                    .nodeId(nodeId)
                    .targetNodeId(-1)
                    .term(-1)
                    .votesReceived(-1)
                    .message("节点 " + nodeId + " Raft 服务启动 (port " + matcher.group(2) + ")")
                    .build();
        }

        matcher = TURN_CANDIDATE.matcher(message);
        if (matcher.find()) {
            int nodeId = Integer.parseInt(matcher.group(1));
            long term = Long.parseLong(matcher.group(2));
            return ElectionEventDTO.builder()
                    .sequence(sequence)
                    .timestamp(timestamp)
                    .eventType("CANDIDATE")
                    .nodeId(nodeId)
                    .targetNodeId(-1)
                    .term(term)
                    .votesReceived(-1)
                    .message("节点 " + nodeId + " 转为 Candidate (term " + term + ")")
                    .build();
        }

        matcher = START_ELECTION.matcher(message);
        if (matcher.find()) {
            int nodeId = Integer.parseInt(matcher.group(1));
            long term = Long.parseLong(matcher.group(2));
            return ElectionEventDTO.builder()
                    .sequence(sequence)
                    .timestamp(timestamp)
                    .eventType("ELECTION_START")
                    .nodeId(nodeId)
                    .targetNodeId(-1)
                    .term(term)
                    .votesReceived(-1)
                    .message("节点 " + nodeId + " 选举超时，发起选主 (term " + term + ")")
                    .build();
        }

        matcher = REQUEST_VOTE.matcher(message);
        if (matcher.find()) {
            int from = Integer.parseInt(matcher.group(1));
            int to = Integer.parseInt(matcher.group(2));
            return ElectionEventDTO.builder()
                    .sequence(sequence)
                    .timestamp(timestamp)
                    .eventType("REQUEST_VOTE")
                    .nodeId(from)
                    .targetNodeId(to)
                    .term(-1)
                    .votesReceived(-1)
                    .message("节点 " + from + " → 节点 " + to + " 请求投票")
                    .build();
        }

        matcher = GRANT_VOTE.matcher(message);
        if (matcher.find()) {
            int from = Integer.parseInt(matcher.group(1));
            int to = Integer.parseInt(matcher.group(2));
            return ElectionEventDTO.builder()
                    .sequence(sequence)
                    .timestamp(timestamp)
                    .eventType("GRANT_VOTE")
                    .nodeId(from)
                    .targetNodeId(to)
                    .term(-1)
                    .votesReceived(-1)
                    .message("节点 " + from + " 投票给节点 " + to)
                    .build();
        }

        matcher = BECOME_LEADER.matcher(message);
        if (matcher.find()) {
            long term = Long.parseLong(matcher.group(1));
            int nodeId = Integer.parseInt(matcher.group(2));
            int votes = Integer.parseInt(matcher.group(3));
            return ElectionEventDTO.builder()
                    .sequence(sequence)
                    .timestamp(timestamp)
                    .eventType("BECOME_LEADER")
                    .nodeId(nodeId)
                    .targetNodeId(-1)
                    .term(term)
                    .votesReceived(votes)
                    .message("节点 " + nodeId + " 成为 Leader (term " + term + "，获得 " + votes + " 票)")
                    .build();
        }

        matcher = TURN_FOLLOWER.matcher(message);
        if (matcher.find()) {
            int nodeId = Integer.parseInt(matcher.group(1));
            long term = Long.parseLong(matcher.group(2));
            int leaderId = Integer.parseInt(matcher.group(3));
            return ElectionEventDTO.builder()
                    .sequence(sequence)
                    .timestamp(timestamp)
                    .eventType("BECOME_FOLLOWER")
                    .nodeId(nodeId)
                    .targetNodeId(leaderId)
                    .term(term)
                    .votesReceived(-1)
                    .message("节点 " + nodeId + " 发现更高 term " + term + "，退为 Follower")
                    .build();
        }

        matcher = ACCEPT_LEADER.matcher(message);
        if (matcher.find()) {
            int nodeId = Integer.parseInt(matcher.group(1));
            int leaderId = Integer.parseInt(matcher.group(2));
            return ElectionEventDTO.builder()
                    .sequence(sequence)
                    .timestamp(timestamp)
                    .eventType("ACCEPT_LEADER")
                    .nodeId(nodeId)
                    .targetNodeId(leaderId)
                    .term(-1)
                    .votesReceived(-1)
                    .message("节点 " + nodeId + " 确认 Leader 为节点 " + leaderId)
                    .build();
        }

        return null;
    }
}
