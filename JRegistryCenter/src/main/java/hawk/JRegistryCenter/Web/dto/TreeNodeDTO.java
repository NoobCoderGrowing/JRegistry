package hawk.JRegistryCenter.Web.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class TreeNodeDTO {
    private String key;
    private String path;
    private String type;
    private String value;
    private boolean leaf;
    private List<TreeNodeDTO> children;
}
