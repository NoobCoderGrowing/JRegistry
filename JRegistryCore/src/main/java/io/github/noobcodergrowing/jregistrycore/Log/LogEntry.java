package io.github.noobcodergrowing.jregistrycore.Log;

import lombok.Data;

@Data
public class LogEntry implements Comparable<LogEntry>{
    
    private long term;
    private long index;
    private String command;
    private String key;
    private byte[] data;
    private String dataType;

    // client session part; null/0 on entries written before this feature (backwards compatible)
    private String clientId;
    private long clientEpoch;
    private int ownerNodeId;

    /**
     * {@code clientOffline} 专用：标记离线的时刻（墙钟毫秒）。由 leader 在追加日志时写入一次，
     * 之后随日志一起复制与重放——所以它是确定性的「数据」，判过期时再拿去和本地时钟比。
     */
    private long offlineSinceMillis;

    public LogEntry(LogEntry other){
        this.term = other.term;
        this.index = other.index;
        this.command = other.command;
        this.key = other.key;
        this.data = other.data;
        this.dataType = other.dataType;
        this.clientId = other.clientId;
        this.clientEpoch = other.clientEpoch;
        this.ownerNodeId = other.ownerNodeId;
        this.offlineSinceMillis = other.offlineSinceMillis;
    }

    public LogEntry(){}

    
    @Override
    public int compareTo(LogEntry other) {
        return Long.compare(this.index, other.index);
    }
    
}