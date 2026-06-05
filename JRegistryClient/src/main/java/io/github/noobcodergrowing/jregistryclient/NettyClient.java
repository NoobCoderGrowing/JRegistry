package io.github.noobcodergrowing.jregistryclient;

import com.alibaba.fastjson.JSON;
import io.github.noobcodergrowing.jregistrycore.RPC.RaftRequest;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.PreDestroy;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class NettyClient {

    private static final int RECONNECT_DELAY_SECONDS = 5;
    private  int CONNECT_TIMEOUT_MILLIS = 5000;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 10;

    private String host;

    private int port;

    private final EventLoopGroup group = new NioEventLoopGroup(1);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);

    private volatile Channel channel;
    private volatile Consumer<String> messageListener;

    public NettyClient(String host, int port, int connectTimeoutMillis) {
        this.host = host;
        this.port = port;
        this.CONNECT_TIMEOUT_MILLIS = connectTimeoutMillis;
    }

    public void setMessageListener(Consumer<String> messageListener) {
        this.messageListener = messageListener;
    }

    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    /**
     * Block until connected or connect attempt fails/times out.
     */
    public boolean connectSync() {
        if (isConnected()) {
            return true;
        }
        if (!connecting.compareAndSet(false, true)) {
            return waitUntilConnected(CONNECT_TIMEOUT_MILLIS);
        }
        try {
            NettyClientHandler handler = new NettyClientHandler(NettyClient.this);
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new IdleStateHandler(0, HEARTBEAT_INTERVAL_SECONDS, 0, TimeUnit.SECONDS))
                                    .addLast(new LineBasedFrameDecoder(8192))
                                    .addLast(new StringDecoder(StandardCharsets.UTF_8))
                                    .addLast(new StringEncoder(StandardCharsets.UTF_8))
                                    .addLast(handler);
                        }
                    });

            ChannelFuture future = bootstrap.connect(host, port);
            if (!future.await(CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                log.warn("netty client connect to {}:{} timed out", host, port);
                return false;
            }
            if (future.isSuccess()) {
                channel = future.channel();
                reconnectScheduled.set(false);
                registerShutdownHookOnce();
                log.info("netty client connected to {}:{}", host, port);
                return true;
            }
            log.warn("netty client failed to connect to {}:{}: {}",
                    host, port, future.cause() != null ? future.cause().getMessage() : "unknown");
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("netty client connect to {}:{} interrupted", host, port);
            return false;
        } finally {
            connecting.set(false);
        }
    }

    private boolean waitUntilConnected(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isConnected()) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return isConnected();
    }

    private void registerShutdownHookOnce() {
        if (shutdownHookRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        }
    }

    /** Background reconnect after disconnect; does not block. */
    void connectAsync() {
        if (isConnected() || connecting.get()) {
            return;
        }
        group.execute(this::connectSync);
    }

    public void scheduleReconnect() {
        if (reconnectScheduled.compareAndSet(false, true)) {
            group.schedule(() -> {
                reconnectScheduled.set(false);
                if (!isConnected()) {
                    connectAsync();
                }
            }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
        }
    }

    public void send(String message) {
        if (!isConnected()) {
            throw new IllegalStateException(
                    "netty client not connected to " + host + ":" + port);
        }
        String payload = message.endsWith("\n") ? message : message + "\n";
        channel.writeAndFlush(payload);
    }

    public void sendRequest(RaftRequest request) {
        send(JSON.toJSONString(request));
    }

    void onMessage(String message) {
        log.info("netty client received: {}", message);
        if (messageListener != null) {
            messageListener.accept(message);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        group.shutdownGracefully();
        log.info("netty client shutdown");
    }
}
