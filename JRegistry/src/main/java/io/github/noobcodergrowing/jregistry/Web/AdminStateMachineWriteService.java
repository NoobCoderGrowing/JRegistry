package io.github.noobcodergrowing.jregistry.Web;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.noobcodergrowing.jregistrycore.RPC.RaftRequest;
import io.github.noobcodergrowing.jregistrycore.Raft.RaftNode;
import io.github.noobcodergrowing.jregistry.Raft.Log.LogService;
import io.github.noobcodergrowing.jregistry.Web.dto.StateMachineWriteResultDTO;
import io.netty.channel.EventLoopGroup;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AdminStateMachineWriteService {

    private static final int WRITE_TIMEOUT_SECONDS = 3;

    @Autowired
    private RaftNode raftNode;

    @Autowired
    private LogService logService;

    @Autowired
    private EventLoopGroup singleGroup;

    public StateMachineWriteResultDTO set(String key, String value, String dataType) {
        validateKey(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("value is required for set");
        }
        return submitWrite("set", key, value, dataType);
    }

    public StateMachineWriteResultDTO delete(String key) {
        validateKey(key);
        return submitWrite("delete", key, null, null);
    }

    private StateMachineWriteResultDTO submitWrite(String type, String key, String value, String dataType) {
        if (raftNode.getLeaderId() == -1) {
            throw new IllegalStateException("No leader found, write failed");
        }

        RaftRequest request = new RaftRequest();
        request.setType("writeRequest");
        request.setCmd(type);
        request.setKey(key);
        if (value != null) {
            request.setData(value.getBytes(StandardCharsets.UTF_8));
            request.setDataType(dataType != null && !dataType.isEmpty() ? dataType : "string");
        }
        request.setUuid(UuidCreator.getTimeOrderedEpoch());

        try {
            CompletableFuture<Void> future = CompletableFuture.runAsync(
                    () -> logService.handleWriteRequest(request),
                    singleGroup
            );
            future.get(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String message = type + " accepted";
            log.info("node {} admin {} key={}", raftNode.getId(), type, key);
            return StateMachineWriteResultDTO.builder()
                    .success(true)
                    .message(message)
                    .build();
        } catch (TimeoutException e) {
            log.error("admin state machine write timeout: {}", e.getMessage());
            throw new IllegalStateException("Write request timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Write request interrupted");
        } catch (ExecutionException e) {
            log.error("admin state machine write failed: {}", e.getMessage());
            throw new IllegalStateException("Write request failed");
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key is required");
        }
        if (!key.matches("[A-Za-z0-9.]+")) {
            throw new IllegalArgumentException("invalid key format");
        }
    }
}
