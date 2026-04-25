package hawk.JRegitstryCore.Log;

import lombok.Data;

@Data
public class LogEntry {
    
    private long term;
    private long index;
    private String command;
    private String key;
    private byte[] data;
    private String dataType;
    
}