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
import java.io.IOException;
import java.util.List;
import hawk.JRegitstryCore.Log.LogEntry;

@Service
@Data
@Slf4j
public class PersistService {

    @Autowired
    private RaftNode raftNode;

    @Autowired
    private LogService logService;

    public boolean persist(){
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
        this.persist();
    }

    public void recoverFromLocalImage(){
        File nodeFile = new File("raftNode" + raftNode.getId() + ".json");
        File logFile = new File("log" + raftNode.getId() + ".json");
        if(nodeFile.exists() && logFile.exists()){
            try {
                String nodejson = new String(java.nio.file.Files.readAllBytes(nodeFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                RaftNode nodeImage = JSON.parseObject( nodejson,RaftNode.class);
                raftNode.recoverFromImage(nodeImage);
                String logjson = new String(java.nio.file.Files.readAllBytes(logFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                List<LogEntry> logEntries = JSON.parseArray(logjson, LogEntry.class);
                if(logEntries != null){
                    logService.setLogger(logEntries);
                }else{
                    logService.getLogger().clear();
                }
                log.info("node {} recover from local image success", raftNode.getId());
            } catch (IOException e) {
                e.printStackTrace();
                log.error("node {} recover from local image failed", raftNode.getId());
            }
            
        }
    }
    
}
