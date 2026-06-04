package hawk.JRegistryCenter.Web.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ElectionRoundsDTO {
    private int clusterSize;
    private String logPath;
    private int totalRounds;
    private List<ElectionRoundSummaryDTO> rounds;
}
