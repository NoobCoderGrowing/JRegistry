package hawk.JRegitstryCore.RPC.SSH;

import java.util.UUID;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.Data;

@Data
public class SSHRequest {

    private String type;
    private String key;
    private byte[] data;
    private String message = "";
    private boolean redirect = false;
    private UUID uuid;
    private String leaderHost;
    private int leaderPort;
    private String dataType;
    private boolean success = true;

    public SSHRequest(){
        this.uuid = UuidCreator.getTimeOrderedEpoch();
    }

}
