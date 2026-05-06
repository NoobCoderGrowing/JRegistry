package hawk.JRegistryCenter.Raft.Log;

import hawk.JRegistryCenter.Raft.RaftNode;
import java.util.Collections;
import java.util.ArrayList;


public class CommitWatcher{

    private int nodeCount;

    private LogService logService;

    private RaftNode raftNode;

   
    public CommitWatcher(LogService logService, RaftNode raftNode, int nodeCount){
        this.logService = logService;
        this.raftNode = raftNode;
        this.nodeCount = nodeCount;
    }


    public synchronized void update(){
        long commitIndex = raftNode.getCommitIndex();
        ArrayList<Long> matchIndexes = new ArrayList<>();
        logService.matchIndexMap.forEach((k,v)->{
            matchIndexes.add(v);
        });
        while (matchIndexes.size() < nodeCount) {
            matchIndexes.add(-1L);
        }
        Collections.sort(matchIndexes);
        long commitableIndex  = matchIndexes.get(nodeCount/2);
        if(commitableIndex > commitIndex){
            logService.leaderCommitLogs(commitableIndex);
        }
    }

    
}
