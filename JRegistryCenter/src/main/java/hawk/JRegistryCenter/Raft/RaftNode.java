package hawk.JRegistryCenter.Raft;



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

   
}
