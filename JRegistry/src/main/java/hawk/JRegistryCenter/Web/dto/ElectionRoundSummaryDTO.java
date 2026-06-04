package hawk.JRegistryCenter.Web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ElectionRoundSummaryDTO {
    private int roundIndex;
    private int finalLeaderId;
    private long finalTerm;
    private int eventCount;
    private String startedAt;
    private String endedAt;
}
