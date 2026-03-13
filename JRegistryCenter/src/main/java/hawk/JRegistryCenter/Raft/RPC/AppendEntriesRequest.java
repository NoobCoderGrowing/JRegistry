package hawk.JRegistryCenter.Raft.RPC;

import lombok.Data;

@Data
public class AppendEntriesRequest {
    private long term;              // Leader 的 term
    private int leaderId;           // Leader 的 ID
    private long prevLogIndex;     // 前一条日志的索引
    private long prevLogTerm;       // 前一条日志的 term
    private String[] entries;       // 要追加的日志条目（空数组表示心跳）
    private long leaderCommit;      // Leader 的 commitIndex
}