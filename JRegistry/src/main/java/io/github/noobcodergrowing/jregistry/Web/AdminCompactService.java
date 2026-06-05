package io.github.noobcodergrowing.jregistry.Web;

import com.alibaba.fastjson.JSON;
import io.github.noobcodergrowing.jregistrycore.RPC.RaftRequest;
import io.github.noobcodergrowing.jregistrycore.Raft.RaftNode;
import io.github.noobcodergrowing.jregistry.Raft.RPC.Client.RaftClientManager;
import io.github.noobcodergrowing.jregistry.Services.Persist.PersistService;
import io.github.noobcodergrowing.jregistry.Web.dto.StateMachineWriteResultDTO;
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
public class AdminCompactService {

    private static final int COMPACT_TIMEOUT_SECONDS = 5;

    @Autowired
    private RaftNode raftNode;

    @Autowired
    private EventLoopGroup singleGroup;

    @Autowired
    private PersistService persistService;

    @Autowired
    private RaftClientManager raftClientManager;

    public StateMachineWriteResultDTO triggerCompact() {
        if (raftNode.getLeaderId() <= 0 && !raftNode.getIsLeader().get()) {
            throw new IllegalStateException("No leader found, compact failed");
        }

        try {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                if (raftNode.getIsLeader().get()) {
                    persistService.sendCompactRequest2All(raftClientManager);
                } else {
                    redirectCompactToLeader();
                }
            }, singleGroup);
            future.get(COMPACT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("node {} admin compact triggered", raftNode.getId());
            return StateMachineWriteResultDTO.builder()
                    .success(true)
                    .message("compact accepted")
                    .build();
        } catch (TimeoutException e) {
            log.error("admin compact timeout: {}", e.getMessage());
            throw new IllegalStateException("Compact request timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Compact request interrupted");
        } catch (ExecutionException e) {
            log.error("admin compact failed: {}", e.getMessage());
            throw new IllegalStateException("Compact request failed");
        }
    }

    private void redirectCompactToLeader() {
        String leaderHost = raftNode.getLeaderHost();
        if (leaderHost == null || leaderHost.isEmpty()) {
            throw new IllegalStateException("No leader found, compact failed");
        }

        RaftRequest raftRequest = new RaftRequest();
        raftRequest.setType("compact");
        raftRequest.setId(raftNode.getId());
        log.info("node {} redirect compact request to leader {}", raftNode.getId(), raftNode.getLeaderId());
        raftClientManager.sendToPeer(raftNode.getLeaderId(), JSON.toJSONString(raftRequest));
    }
}
