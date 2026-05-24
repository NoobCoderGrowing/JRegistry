package hawk.JRegistryCenter.Services.Persist;

import org.springframework.beans.factory.annotation.Autowired;
import hawk.JRegistryCenter.Raft.RaftNode;
import hawk.JRegistryCenter.Raft.Log.LogService;
import hawk.JRegistryCenter.Raft.RPC.Client.RaftClientManager;
import org.springframework.stereotype.Service;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import hawk.JRegitstryCore.RPC.RaftRequest;
import com.alibaba.fastjson.JSON;
import java.io.File;
import io.netty.channel.EventLoopGroup;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;

@Service
@Data
@Slf4j
public class PersistService {

    @Autowired
    private RaftNode raftNode;

    @Autowired
    private LogService logService;

  

    @Value("${raft.image-path}")
    private String imagePath;


    public void autoRecovery(){
        recoverFromLocalImage();
    }

    public boolean manualPersist(){
        if(raftNode.persist() && logService.persist()){
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
            log.info("node {} recover from local image success", raftNode.getId());
        }
    }
    
}
