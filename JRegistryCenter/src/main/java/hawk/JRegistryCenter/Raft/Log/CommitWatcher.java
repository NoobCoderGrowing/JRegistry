package hawk.JRegistryCenter.Raft.Log;

import hawk.JRegistryCenter.Raft.RaftNode;
import java.util.Collections;
import java.util.ArrayList;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CommitWatcher{

    private int nodeCount;

    private LogService logService;

    private RaftNode raftNode;

   
    public CommitWatcher(LogService logService, RaftNode raftNode, int nodeCount){
        this.logService = logService;
        this.raftNode = raftNode;
        this.nodeCount = nodeCount;
    }


    public void update(){
        long commitIndex = raftNode.getCommitIndex();
        ArrayList<Long> matchIndexes = new ArrayList<>();
        logService.matchIndexMap.forEach((k,v)->{
            matchIndexes.add(v);
        });
        Collections.sort(matchIndexes);
        long commitableIndex  = matchIndexes.get(nodeCount/2);
        log.info("commitableIndex: {}, commitIndex: {}", commitableIndex, commitIndex);
        if(commitableIndex > commitIndex){
            logService.commitLogs(commitableIndex);
        }
    }

    
}
