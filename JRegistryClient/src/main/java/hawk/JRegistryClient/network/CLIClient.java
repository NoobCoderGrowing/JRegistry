package hawk.JRegistryClient.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.springframework.stereotype.Component;

import hawk.JRegitstryCore.RPC.CLIRequest;

import java.nio.charset.StandardCharsets;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import com.alibaba.fastjson.JSON;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class CLIClient {
    @Value("${netty.host}")
    private String host;
    @Value("${netty.port}")
    private int port;

    private EventLoopGroup group;
    private Channel channel;
    private Bootstrap b;

    private final int MAX_REDIRECT_RETRIES = 3;

    // 支持并发请求：requestId -> pending request context
    private final ConcurrentHashMap<UUID, PendingRequest> pendingResponses = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        group = new NioEventLoopGroup();
        b = new Bootstrap();
        b.group(group)
         .channel(NioSocketChannel.class)
         .option(ChannelOption.TCP_NODELAY, true)
         .handler(new ChannelInitializer<SocketChannel>() {
             @Override
             protected void initChannel(SocketChannel ch) {
                 ChannelPipeline p = ch.pipeline();
                 p.addLast(new LineBasedFrameDecoder(8192));
                 p.addLast(new StringDecoder(StandardCharsets.UTF_8));
                 p.addLast(new StringEncoder(StandardCharsets.UTF_8));
                 p.addLast(new CLIClientHandler(CLIClient.this)); // 关键：把 client 传给 handler
             }
         });

        connect(host, port);
    }

    public synchronized void connect(String host, int port) {
        int maxAttempts = 2;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ChannelFuture future = b.connect(host, port).sync();
                if (future.isSuccess()) {
                    channel = future.channel();
                    log.info("Connected to JRegistry TCP server: {}:{}", host, port);
                    return;
                }
                log.error("Failed to connect to JRegistry TCP server: {}:{} (attempt {}/{})", host, port, attempt, maxAttempts);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Connect interrupted to JRegistry TCP server: {}:{}", host, port, e);
                return;
            } catch (Exception e) {
                log.error("Failed to connect to JRegistry TCP server: {}:{} (attempt {}/{})", host, port, attempt, maxAttempts, e);
            }

            if (attempt < maxAttempts) {
                log.warn("Retrying to connect in 5 seconds: {}:{}", host, port);
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Retry sleep interrupted for {}:{}", host, port, e);
                    return;
                }
            }
        }
    }

    public String sendRequest(CLIRequest cliRequest) {
        if (channel == null || !channel.isActive()) {
            log.info("Channel is not active.");
            return "Connection to server is not active.";
        }

        UUID requestId = cliRequest.getUuid();
        if (requestId == null) {
            return "request id is missing";
        }

        CompletableFuture<String> responseFuture = new CompletableFuture<>();
        PendingRequest pendingRequest = new PendingRequest(cliRequest, responseFuture);
        PendingRequest existingPending = pendingResponses.putIfAbsent(requestId, pendingRequest);
        if (existingPending != null) {
            return "duplicate request id: " + requestId;
        }

        channel.writeAndFlush(JSON.toJSONString(cliRequest) + "\n").addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                PendingRequest pending = pendingResponses.remove(requestId);
                if (pending != null) {
                    pending.getFuture().completeExceptionally(future.cause());
                }
            }
        });

        try {
            return responseFuture.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pendingResponses.remove(requestId);
            return "timeout";
        } catch (Exception e) {
            pendingResponses.remove(requestId);
            log.error("sendRequest failed", e);
            return "request failed: " + e.getMessage();
        }
    }

    // 由 handler 在收到服务端消息时调用，根据 requestId 匹配对应 future
    public void completeResponse(String msg) {
        try {
            CLIRequest response = JSON.parseObject(msg, CLIRequest.class);
            UUID requestId = response.getUuid();

            PendingRequest pending = pendingResponses.get(requestId);
            if (pending == null) {
                log.warn("No pending request for requestId {}. Raw response: {}", requestId, msg);
                return;
            }

            if (response.isRedirect()) {
                String newHost = response.getLeaderHost();
                int newPort = response.getLeaderPort();
                handleRedirect(requestId, pending, response.getMessage(), newHost, newPort);
                return;
            }

            PendingRequest removed = pendingResponses.remove(requestId);
            if (removed != null && !removed.getFuture().isDone()) {
                removed.getFuture().complete(response.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to parse server response: {}", msg, e);
        }
    }

    private void handleRedirect(UUID requestId, PendingRequest pending, String redirectMessage, String newHost, int newPort) {
        if (pending.getRedirectRetries() >= MAX_REDIRECT_RETRIES) {
            PendingRequest removed = pendingResponses.remove(requestId);
            if (removed != null && !removed.getFuture().isDone()) {
                removed.getFuture().complete("redirect exceeded max retries");
            }
            return;
        }

        pending.incrementRedirectRetries();
        CompletableFuture.runAsync(() -> {
            reconnectTo(newHost, newPort);
            resendPendingRequest(requestId, pending, redirectMessage);
        });
    }

    private void resendPendingRequest(UUID requestId, PendingRequest pending, String redirectMessage) {
        if (channel == null || !channel.isActive()) {
            PendingRequest removed = pendingResponses.remove(requestId);
            if (removed != null && !removed.getFuture().isDone()) {
                removed.getFuture().complete("redirect reconnect failed");
            }
            return;
        }

        channel.writeAndFlush(JSON.toJSONString(pending.getOriginalRequest()) + "\n").addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                PendingRequest removed = pendingResponses.remove(requestId);
                if (removed != null && !removed.getFuture().isDone()) {
                    removed.getFuture().completeExceptionally(future.cause());
                }
            } else {
                
                log.info("Resent request {} after redirect: {}", requestId, redirectMessage);
            }
        });
    }

    private synchronized void reconnectTo(String newHost, int newPort) {
        Channel oldChannel = this.channel;
        this.host = newHost;
        this.port = newPort;

        try {
            if (oldChannel != null && oldChannel.isActive()) {
                oldChannel.close().syncUninterruptibly();
            }
        } catch (Exception e) {
            log.warn("Failed to close old channel before reconnect", e);
        }

        connect(newHost, newPort);
    }

    public void stop() {
        try {
            if (channel != null) {
                channel.close().syncUninterruptibly();
            }
        } finally {
            if (group != null) {
                group.shutdownGracefully();
            }
        }
        System.out.println("Client connection closed.");
    }
}