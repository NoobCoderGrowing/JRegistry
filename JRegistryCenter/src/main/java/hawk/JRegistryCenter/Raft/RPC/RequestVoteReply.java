package hawk.JRegistryCenter.Raft.RPC;

import lombok.Data;

@Data
public class RequestVoteReply {

    public long term;
    public boolean voteGranted;
    
    public RequestVoteReply(long term, boolean voteGranted) {
        this.term = term;
        this.voteGranted = voteGranted;
    }


}
