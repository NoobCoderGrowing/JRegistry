package hawk.JRegistryCenter.Raft.RPC;

import lombok.Data;

@Data
public class RequestVoteRequest {
    private long term;              // Candidate 的 term
    private int candidateId;       // Candidate 的 ID
    private long lastLogIndex;     // Candidate 最后一条日志的索引
    private long lastLogTerm;      // Candidate 最后一条日志的 term
}
