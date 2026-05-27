package hawk.JRegistryCenter.Raft.Log;
import java.util.Collections;
import java.util.ArrayList;
import hawk.JRegitstryCore.StateMachine;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CommitWatcher{

    private int nodeCount;

    private LogService logService;

    private StateMachine stateMachine;

   
    public CommitWatcher(LogService logService, int nodeCount, StateMachine stateMachine){
        this.logService = logService;
        this.nodeCount = nodeCount;
        this.stateMachine = stateMachine;
    }


    public void update(){
        long commitIndex = stateMachine.getCommitIndex();
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
