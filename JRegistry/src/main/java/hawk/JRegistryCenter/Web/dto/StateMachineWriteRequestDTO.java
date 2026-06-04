package hawk.JRegistryCenter.Web.dto;

import lombok.Data;

@Data
public class StateMachineWriteRequestDTO {
    private String key;
    private String value;
    private String dataType;
}
