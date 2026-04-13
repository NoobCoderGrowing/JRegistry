package hawk.JRegitstryCore.RPC;

import java.util.UUID;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.Data;

@Data
public class CLIReuest {

    private String type;
    private String command;
    private String kye;
    private byte[] data;
    private UUID uuid;

    public CLIReuest(){
        this.uuid = UuidCreator.getTimeOrderedEpoch();
    }

}
