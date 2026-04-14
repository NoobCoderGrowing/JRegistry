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

    // 支持并发请求：requestId -> pending response
    private final ConcurrentHashMap<UUID, CompletableFuture<String>> pendingResponses = new ConcurrentHashMap<>();

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

    public void connect(String host, int port) {
        try {
            b.connect(host, port).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    channel = future.channel();
                    log.info("Connected to JRegistry TCP server: {}:{}", host, port);
                } else {
                    log.error("Failed to connect to JRegistry TCP server: {}:{}", host, port, future.cause());
                }
            });
        } catch (Exception e) {
            log.error("Failed to connect to JRegistry TCP server: {}:{}", host, port, e);
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
        CompletableFuture<String> existingFuture = pendingResponses.putIfAbsent(requestId, responseFuture);
        if (existingFuture != null) {
            return "duplicate request id: " + requestId;
        }

        channel.writeAndFlush(JSON.toJSONString(cliRequest) + "\n").addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                CompletableFuture<String> pending = pendingResponses.remove(requestId);
                if (pending != null) {
                    pending.completeExceptionally(future.cause());
                }
            }
        });

        try {
            return responseFuture.get(10, TimeUnit.SECONDS);
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
            if (requestId == null) {
                log.warn("Received response without requestId: {}", msg);
                return;
            }
            CompletableFuture<String> pending = pendingResponses.remove(requestId);

            // redirect and close old channel
            if (response.isRedirect()) {
                String newHost = response.getLeaderHost();
                int newPort = response.getLeaderPort();
                reconnectTo(newHost, newPort);
                if (pending != null && !pending.isDone()) {
                    pending.complete(response.getMessage());
                }
                return;
            }
            // send server message to terminal
            if (pending != null && !pending.isDone()) {
                pending.complete(response.getMessage());
            } else {
                log.warn("No pending request for requestId {}. Raw response: {}", requestId, msg);
            }
        } catch (Exception e) {
            log.error("Failed to parse server response: {}", msg, e);
        }
    }

    private synchronized void reconnectTo(String newHost, int newPort) {
        Channel oldChannel = this.channel;
        this.host = newHost;
        this.port = newPort;
       

        if (oldChannel != null && oldChannel.isActive()) {
            oldChannel.close().addListener((ChannelFutureListener) closeFuture -> {
                if (!closeFuture.isSuccess()) {
                    log.warn("Failed to close old channel， ", closeFuture.cause());
                }
            });
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