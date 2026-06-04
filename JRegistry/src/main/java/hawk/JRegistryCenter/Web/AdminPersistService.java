package hawk.JRegistryCenter.Web;

import com.alibaba.fastjson.JSON;
import hawk.JRegitstryCore.RPC.RaftRequest;
import hawk.JRegitstryCore.Raft.RaftNode;
import hawk.JRegistryCenter.Raft.RPC.Client.RaftClientManager;
import hawk.JRegistryCenter.Services.Persist.PersistService;
import hawk.JRegistryCenter.Web.dto.StateMachineWriteResultDTO;
import io.netty.channel.EventLoopGroup;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AdminPersistService {

    private static final int PERSIST_TIMEOUT_SECONDS = 5;

    @Autowired
    private RaftNode raftNode;

    @Autowired
    private EventLoopGroup singleGroup;

    @Autowired
    private PersistService persistService;

    @Autowired
    private RaftClientManager raftClientManager;

    public StateMachineWriteResultDTO triggerPersist() {
        if (raftNode.getLeaderId() <= 0 && !raftNode.getIsLeader().get()) {
            throw new IllegalStateException("No leader found, persist failed");
        }

        try {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                if (raftNode.getIsLeader().get()) {
                    persistService.sendPersistRequest2All(raftClientManager);
                } else {
                    redirectPersistToLeader();
                }
            }, singleGroup);
            future.get(PERSIST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("node {} admin persist triggered", raftNode.getId());
            return StateMachineWriteResultDTO.builder()
                    .success(true)
                    .message("persist accepted")
                    .build();
        } catch (TimeoutException e) {
            log.error("admin persist timeout: {}", e.getMessage());
            throw new IllegalStateException("Persist request timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Persist request interrupted");
        } catch (ExecutionException e) {
            log.error("admin persist failed: {}", e.getMessage());
            throw new IllegalStateException("Persist request failed");
        }
    }

    private void redirectPersistToLeader() {
        String leaderHost = raftNode.getLeaderHost();
        if (leaderHost == null || leaderHost.isEmpty()) {
            throw new IllegalStateException("No leader found, persist failed");
        }

        RaftRequest raftRequest = new RaftRequest();
        raftRequest.setType("persist");
        raftRequest.setId(raftNode.getId());
        log.info("node {} redirect persist request to leader {}", raftNode.getId(), raftNode.getLeaderId());
        raftClientManager.sendToPeer(raftNode.getLeaderId(), JSON.toJSONString(raftRequest));
    }
}
