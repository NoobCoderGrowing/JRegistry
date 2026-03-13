package hawk.JRegistryCenter.Raft.RPC;

import lombok.Data;

@Data
public class AppendEntriesReply {

    private long term;
    private boolean success;
    private long nextIndex;

    public AppendEntriesReply(long term, boolean success, long nextIndex) {
        this.term = term;
        this.success = success;
        this.nextIndex = nextIndex;
    }
}
