package hawk.JRegitstryCore.RPC;

import java.util.UUID;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.Data;

@Data
public class CLIRequest {

    private String type;
    private String key;
    private byte[] data;
    private String message;
    private boolean redirect;
    private UUID uuid;
    private String leaderHost;
    private int leaderPort;
    private String dataType;

    public CLIRequest(){
        this.uuid = UuidCreator.getTimeOrderedEpoch();
    }

}
