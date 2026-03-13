package hawk.JRegistryCenter.Raft;

import hawk.JRegistryCenter.Raft.RPC.AppendEntriesReply;
import hawk.JRegistryCenter.Raft.RPC.RequestVoteReply;

public class RaftNode {

    int nodeId;
    //State part in raft paper
    long currentTerm;
    long votedFor;
    long commitIndex;
    long lastApplied;
    long[] nextIndex;
    long[] matchIndex;

    //Append Entries part in raft paper
    long leaderTerm;
    int leaderId;
    long prevLogIndex;
    long prevLogTerm;
    String[] entries;
    long leaderCommit;


    public RaftNode(int nodeId) {
        this.nodeId = nodeId;
    }

   public AppendEntriesReply appendEntries(long leaderTerm, int leaderId, long prevLogIndex, long prevLogTerm, String[] entries, long leaderCommit) {
    
        return null;
   }

   public RequestVoteReply requestVote(long candidateTerm, int candidateId, long lastLogIndex, long lastLogTerm) {

        return null;

   }
}
