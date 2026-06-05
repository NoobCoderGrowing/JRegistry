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

    public LogEntry(LogEntry other){
        this.term = other.term;
        this.index = other.index;
        this.command = other.command;
        this.key = other.key;
        this.data = other.data;
        this.dataType = other.dataType;
    }

    public LogEntry(){}

    
    @Override
    public int compareTo(LogEntry other) {
        return Long.compare(this.index, other.index);
    }
    
}