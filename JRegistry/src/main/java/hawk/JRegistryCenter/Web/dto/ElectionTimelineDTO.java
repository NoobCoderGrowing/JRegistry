package hawk.JRegistryCenter.Web.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ElectionTimelineDTO {
    private int roundIndex;
    private int clusterSize;
    private int finalLeaderId;
    private long finalTerm;
    private String startedAt;
    private String endedAt;
    private String logPath;
    private List<ElectionEventDTO> events;
}
