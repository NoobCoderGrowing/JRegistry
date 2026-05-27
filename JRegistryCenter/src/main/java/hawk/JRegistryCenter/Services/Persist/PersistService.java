package hawk.JRegistryCenter.Services.Persist;

import org.springframework.beans.factory.annotation.Autowired;
import hawk.JRegitstryCore.Raft.RaftNode;
import hawk.JRegistryCenter.Raft.Log.LogService;
import hawk.JRegistryCenter.Raft.RPC.Client.RaftClientManager;
import org.springframework.stereotype.Service;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import hawk.JRegitstryCore.RPC.RaftRequest;
import com.alibaba.fastjson.JSON;
import java.io.File;
import org.springframework.beans.factory.annotation.Value;
import hawk.JRegitstryCore.StateMachine;
import hawk.JRegitstryCore.Log.LogEntry;


@Service
@Data
@Slf4j
public class PersistService {

    @Autowired
    private RaftNode raftNode;

    @Autowired
    private LogService logService;

    @Autowired
    private StateMachine stateMachine;

    @Value("${raft.image-path}")
    private String imagePath;


    public void autoRecovery(){
        recoverFromLocalImage();
    }

    public boolean manualPersist(){
        if(raftNode.persist() && logService.persist() && stateMachine.persist()){
            return true;
        }else{
            return false;
        }
    }

    public void persistNode(){
        raftNode.persist();
    }

    public void persistLog(){
        logService.persist();
    }

    public void sendPersistRequest2All(RaftClientManager raftClientManager){
        RaftRequest raftRequest = new RaftRequest();
        raftRequest.setType("persist");
        raftRequest.setId(raftNode.getId());
        log.info("node {} send persist request to all nodes", raftNode.getId());
        raftClientManager.sendToAllPeers(JSON.toJSONString(raftRequest));
        this.manualPersist();
    }

    public void recoverFromLocalImage(){
        File nodeFile = new File(imagePath + "raftNode" + raftNode.getId() + ".json");
        File logFile = new File(imagePath + "log" + raftNode.getId() + ".json");
        if(nodeFile.exists() && logFile.exists()){
            raftNode.recoverFromImage();
            logService.recoverFromLocalImage();
        }
        File stateMachineFile = new File(imagePath + "stateMachine" + raftNode.getId() + ".json");
        if(stateMachineFile.exists()){
            stateMachine.recoverFromLocalImage();
        }
    }

    public void logCompaction(){
        long commitIndex = stateMachine.getCommitIndex();
        LogEntry prevEntry = logService.getLog(commitIndex-1);
        if(prevEntry == null){
            return;
        }
        int firstLogIndex = logService.getLogger().indexOf(prevEntry);
        logService.getLogger().subList(0, firstLogIndex).clear();
        raftNode.persist();
        logService.persist();
        stateMachine.persist();
    }

    public void sendCompactRequest2All(RaftClientManager raftClientManager){
        RaftRequest raftRequest = new RaftRequest();
        raftRequest.setType("compact");
        raftRequest.setId(raftNode.getId());
        log.info("node {} send compact request to all nodes", raftNode.getId());
        raftClientManager.sendToAllPeers(JSON.toJSONString(raftRequest));
        logCompaction();
    }
    
}
