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

        connectOnStartup(host, port);
    }

    public void connectOnStartup(String host, int port) {
        b.connect(host, port).addListener((ChannelFuture future) -> {
            if (!future.isSuccess()) {
                scheduleReconnect(host, port); //失败延迟重连
            }else{
                this.channel = future.channel();
                log.info("Connected to JRegistry TCP server: {}:{}", host, port);
            }
        });
    }



    public void scheduleReconnect(String host, int port) {
        group.schedule(() -> {
            connectOnStartup(host, port);
        }, 5, TimeUnit.SECONDS);
    }
    

    public synchronized void reconnect(String host, int port) {
        //close old channel
        Channel oldChannel = this.channel;
        try {
            if (oldChannel != null && oldChannel.isActive()) {
                oldChannel.close().syncUninterruptibly();
            }
        } catch (Exception e) {
            log.warn("Failed to close old channel before connect", e);
        }

        //connect new channel
        try {
            ChannelFuture future = b.connect(host, port).sync();
            if (future.isSuccess()) {
                channel = future.channel();
                log.info("Redirected to JRegistry TCP server: {}:{}", host, port);
                return;
            }
            log.error("Failed to connect to JRegistry TCP server: {}:{}", host, port);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Reconnect interrupted to JRegistry TCP server: {}:{}", host, port, e);
        } catch (Exception e) {
            log.error("Failed to reconnect to JRegistry TCP server: {}:{}", host, port, e);
        }
    }

    public String sendRequest(CLIRequest cliRequest) {
        log.info("sendRequest start: requestId={}, type={}, key={}, channelActive={}",
                cliRequest.getUuid(), cliRequest.getType(), cliRequest.getKey(), channel != null && channel.isActive());
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
                log.error("sendRequest write failed: requestId={}", requestId, future.cause());
            } else {
                log.info("sendRequest write success: requestId={}", requestId);
            }
        });

        try {
            log.info("sendRequest waiting response: requestId={}, pendingSize={}", requestId, pendingResponses.size());
            String response = responseFuture.get(5, TimeUnit.SECONDS);
            return response;
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
            log.info("completeResponse raw: {}", msg);
            CLIRequest response = JSON.parseObject(msg, CLIRequest.class);
            UUID requestId = response.getUuid();
            log.info("completeResponse parsed: requestId={}, redirect={}, message={}",
                    requestId, response.isRedirect(), response.getMessage());

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
            this.host = newHost;
            this.port = newPort;
            reconnect(newHost, newPort);
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