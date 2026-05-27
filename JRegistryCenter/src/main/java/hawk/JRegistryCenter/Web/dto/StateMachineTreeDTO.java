package hawk.JRegistryCenter.Web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StateMachineTreeDTO {
    private int nodeId;
    private long commitIndex;
    private TreeNodeDTO root;
}
