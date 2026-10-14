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

    /**
     * 关联 id，<b>只有需要回包的写（如 CAS）才填</b>。apply 之后由持有该客户端连接的节点
     * 按它把结果回给客户端；普通 set/delete 是 fire-and-forget，这里保持 null。
     */
    private String uuid;

    /** {@code cas} 专用：为 true 表示「期望该 key 当前不存在」，为 false 则与 {@link #expectedData} 比较。 */
    private boolean casExpectAbsent;

    /** {@code cas} 专用：期望的前值。{@link #casExpectAbsent} 为 true 时忽略。 */
    private byte[] expectedData;

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
        this.uuid = other.uuid;
        this.casExpectAbsent = other.casExpectAbsent;
        this.expectedData = other.expectedData;
    }

    public LogEntry(){}

    
    @Override
    public int compareTo(LogEntry other) {
        return Long.compare(this.index, other.index);
    }
    
}