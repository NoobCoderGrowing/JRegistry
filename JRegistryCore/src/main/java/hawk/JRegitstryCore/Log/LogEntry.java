package hawk.JRegitstryCore.Log;

import lombok.Data;

@Data
public class LogEntry implements Comparable<LogEntry>{
    
    private long term;
    private long index;
    private String command;
    private String key;
    private byte[] data;
    private String dataType;

    @Override
    public int compareTo(LogEntry other) {
        return Long.compare(this.index, other.index);
    }
    
}