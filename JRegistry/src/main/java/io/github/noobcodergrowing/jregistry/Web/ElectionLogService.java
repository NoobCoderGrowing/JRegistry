package io.github.noobcodergrowing.jregistry.Web;

import io.github.noobcodergrowing.jregistry.Web.dto.ElectionEventDTO;
import io.github.noobcodergrowing.jregistry.Web.dto.ElectionRoundSummaryDTO;
import io.github.noobcodergrowing.jregistry.Web.dto.ElectionRoundsDTO;
import io.github.noobcodergrowing.jregistry.Web.dto.ElectionTimelineDTO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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

    @Value("${admin.election-log-path:logs/JRegistry.log}")
    private String electionLogPath;

    public ElectionTimelineDTO parseStartupElectionTimeline() throws IOException {
        return parseLatestSuccessfulElectionTimeline();
    }

    public ElectionTimelineDTO parseLatestElectionTimeline() throws IOException {
        return parseLatestSuccessfulElectionTimeline();
    }

    public ElectionTimelineDTO parseElectionTimeline(int roundIndex) throws IOException {
        if (roundIndex != 1) {
            throw new IOException("Only the latest successful election is available");
        }
        return parseLatestSuccessfulElectionTimeline();
    }

    public ElectionRoundsDTO parseElectionRounds() throws IOException {
        Path path = resolveLogPath();
        ElectionTimelineDTO latest = parseLatestSuccessfulElectionTimeline();
        List<ElectionRoundSummaryDTO> summaries = new ArrayList<>();
        if (latest.getFinalLeaderId() > 0 && !latest.getEvents().isEmpty()) {
            summaries.add(ElectionRoundSummaryDTO.builder()
                    .roundIndex(1)
                    .finalLeaderId(latest.getFinalLeaderId())
                    .finalTerm(latest.getFinalTerm())
                    .eventCount(latest.getEvents().size())
                    .startedAt(latest.getStartedAt())
                    .endedAt(latest.getEndedAt())
                    .build());
        }
        return ElectionRoundsDTO.builder()
                .clusterSize(clusterSize)
                .logPath(path.toAbsolutePath().toString())
                .totalRounds(summaries.size())
                .rounds(summaries)
                .build();
    }

    public ElectionTimelineDTO parseLatestSuccessfulElectionTimeline() throws IOException {
        List<ElectionTimelineDTO> successfulRounds = parseSuccessfulElectionTimelines();
        if (successfulRounds.isEmpty()) {
            return emptyTimeline(resolveLogPath());
        }
        ElectionTimelineDTO latest = successfulRounds.get(successfulRounds.size() - 1);
        latest.setRoundIndex(1);
        return latest;
    }

    private List<ElectionTimelineDTO> parseSuccessfulElectionTimelines() throws IOException {
        List<ElectionTimelineDTO> timelines = parseAllElectionTimelines();
        List<ElectionTimelineDTO> successful = new ArrayList<>();
        for (ElectionTimelineDTO timeline : timelines) {
            if (timeline.getFinalLeaderId() > 0) {
                successful.add(timeline);
            }
        }
        return successful;
    }

    public List<ElectionTimelineDTO> parseAllElectionTimelines() throws IOException {
        Path path = resolveLogPath();
        if (!Files.exists(path)) {
            throw new IOException("Log file not found: " + path.toAbsolutePath());
        }

        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<ElectionEventDTO> events = new ArrayList<>();
        long sequence = 0;

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
        List<List<ElectionEventDTO>> groupedRounds = splitIntoRounds(events);

        List<ElectionTimelineDTO> timelines = new ArrayList<>();
        for (int i = 0; i < groupedRounds.size(); i++) {
            List<ElectionEventDTO> roundEvents = new ArrayList<>(groupedRounds.get(i));
            resequence(roundEvents);

            int finalLeaderId = -1;
            long finalTerm = -1;
            for (ElectionEventDTO event : roundEvents) {
                if ("BECOME_LEADER".equals(event.getEventType())) {
                    finalLeaderId = event.getNodeId();
                    finalTerm = event.getTerm();
                }
            }

            String startedAt = roundEvents.isEmpty() ? "" : roundEvents.get(0).getTimestamp();
            String endedAt = roundEvents.isEmpty() ? "" : roundEvents.get(roundEvents.size() - 1).getTimestamp();

            timelines.add(ElectionTimelineDTO.builder()
                    .roundIndex(i + 1)
                    .clusterSize(clusterSize)
                    .finalLeaderId(finalLeaderId)
                    .finalTerm(finalTerm)
                    .startedAt(startedAt)
                    .endedAt(endedAt)
                    .logPath(path.toAbsolutePath().toString())
                    .events(roundEvents)
                    .build());
        }
        return timelines;
    }

    private Path resolveLogPath() {
        return Path.of(electionLogPath);
    }

    private List<List<ElectionEventDTO>> splitIntoRounds(List<ElectionEventDTO> events) {
        List<List<ElectionEventDTO>> rounds = new ArrayList<>();
        List<ElectionEventDTO> pendingStartups = new ArrayList<>();
        List<ElectionEventDTO> current = null;

        for (ElectionEventDTO event : events) {
            if ("STARTUP".equals(event.getEventType())) {
                if (current == null) {
                    pendingStartups.add(event);
                }
                continue;
            }

            if ("CANDIDATE".equals(event.getEventType()) || "ELECTION_START".equals(event.getEventType())) {
                current = new ArrayList<>(pendingStartups);
                pendingStartups.clear();
                current.add(event);
                continue;
            }

            if (current != null) {
                current.add(event);
                if ("BECOME_LEADER".equals(event.getEventType())) {
                    rounds.add(current);
                    current = null;
                }
            }
        }
        return rounds;
    }

    private void resequence(List<ElectionEventDTO> events) {
        for (int i = 0; i < events.size(); i++) {
            events.get(i).setSequence(i + 1);
        }
    }

    private ElectionTimelineDTO emptyTimeline(Path path) {
        return ElectionTimelineDTO.builder()
                .roundIndex(0)
                .clusterSize(clusterSize)
                .finalLeaderId(-1)
                .finalTerm(-1)
                .startedAt("")
                .endedAt("")
                .logPath(path.toAbsolutePath().toString())
                .events(Collections.emptyList())
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
                    .message("节点 " + nodeId + " 退为 Follower（发现 term " + term + "，来自节点 " + leaderId + "）")
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
