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
    private UUID uuid;
    private String leaderHost;
    private int leaderPort;

    public CLIRequest(){
        this.uuid = UuidCreator.getTimeOrderedEpoch();
    }

}
