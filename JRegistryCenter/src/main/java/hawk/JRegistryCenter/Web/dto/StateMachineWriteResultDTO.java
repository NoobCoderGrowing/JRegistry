package hawk.JRegistryCenter.Web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StateMachineWriteResultDTO {
    private boolean success;
    private String message;
}
