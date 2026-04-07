package hawk.JRegitstry.Log;

import lombok.Data;

@Data
public class LogEntry {
    
    private long term;
    private String commandType;
    private int index;
    private String key;
    private byte[] data;
}
