package io.github.noobcodergrowing.jregistryclient;

import io.github.noobcodergrowing.jregistrycore.KV;
import io.github.noobcodergrowing.jregistrycore.Pair;
import io.github.noobcodergrowing.jregistrycore.RPC.RaftRequest;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.alibaba.fastjson.JSON;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JRegistryClient {
    private long TASK_TIMEOUT_MS = 1000;

    private final NettyClient nettyClient;

    public JRegistryClient(String host, int port, long taskTimeoutMs, int connectTimeoutMillis) {
        this.nettyClient = new NettyClient(host, port, connectTimeoutMillis);
        this.TASK_TIMEOUT_MS = taskTimeoutMs;
    }

    /**
     * Connect synchronously; returns true only when the TCP channel is active.
     */
    public boolean connect() {
        return nettyClient.connectSync();
    }

    public boolean isConnected() {
        return nettyClient.isConnected();
    }

    public void shutdown() {
        nettyClient.shutdown();
    }

    private void requireConnected() {
        if (!nettyClient.isConnected()) {
            throw new IllegalStateException("JRegistryClient is not connected; call connect() first");
        }
    }

    public Pair<byte[], String> get(String key) {
        requireConnected();
        RaftRequest request = new RaftRequest();
        request.setType("get");
        request.setKey(key);
        request.setUuid(UuidCreator.getTimeOrderedEpoch());
        CompletableFuture<RaftRequest> future = new CompletableFuture<>();
        nettyClient.setMessageListener(message -> {
            RaftRequest reply = JSON.parseObject(message, RaftRequest.class);
            if (reply == null) {
                return;
            }
            if (reply.getUuid().equals(request.getUuid())) {
                future.complete(reply);
            }
        });
        nettyClient.sendRequest(request);

        try {
            RaftRequest reply = future.get(TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (reply.isSuccess()) {
                return new Pair<>(reply.getData(), reply.getDataType());
            }
            return null;
        } catch (Exception e) {
            log.error("get failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 递归列出 {@code path} 及其所有后代中带值的 key:value 对。
     * 返回的 key 是绝对完整路径（形如 {@code app/config/name}，可直接再传给 {@link #get}）。
     * 路径不存在或超时返回 {@code null}；路径存在但没有带值的后代时返回空列表。
     */
    public List<KV> listAll(String path) {
        requireConnected();
        RaftRequest request = new RaftRequest();
        request.setType("ls");
        request.setKey(path);
        request.setUuid(UuidCreator.getTimeOrderedEpoch());
        CompletableFuture<RaftRequest> future = new CompletableFuture<>();
        nettyClient.setMessageListener(message -> {
            RaftRequest reply = JSON.parseObject(message, RaftRequest.class);
            if (reply == null) {
                return;
            }
            if (reply.getUuid().equals(request.getUuid())) {
                future.complete(reply);
            }
        });
        nettyClient.sendRequest(request);

        try {
            RaftRequest reply = future.get(TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (reply.isSuccess()) {
                return reply.getKvs();
            }
            return null;
        } catch (Exception e) {
            log.error("listAll failed: {}", e.getMessage());
            return null;
        }
    }

    public void set(String key, byte[] data, String dataType) {
        requireConnected();
        RaftRequest request = new RaftRequest();
        request.setType("writeRequest");
        request.setCmd("set");
        request.setKey(key);
        request.setData(data);
        request.setDataType(dataType);
        request.setUuid(UuidCreator.getTimeOrderedEpoch());
        nettyClient.sendRequest(request);
    }

    public void delete(String key) {
        requireConnected();
        RaftRequest request = new RaftRequest();
        request.setType("writeRequest");
        request.setCmd("delete");
        request.setKey(key);
        request.setUuid(UuidCreator.getTimeOrderedEpoch());
        nettyClient.sendRequest(request);
    }
}
