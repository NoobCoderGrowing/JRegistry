package hawk.JRegistryClient;

import com.alibaba.fastjson.JSON;
import hawk.JRegitstryCore.RPC.RaftRequest;
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
    private static final int CONNECT_TIMEOUT_MILLIS = 5000;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 10;

    
    
    private String host;

    private int port;


    private final EventLoopGroup group = new NioEventLoopGroup(1);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    private volatile Channel channel;
    private volatile Consumer<String> messageListener;

    public NettyClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() {
        connect();
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    public void setMessageListener(Consumer<String> messageListener) {
        this.messageListener = messageListener;
    }

    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    public void connect() {
        if (isConnected()) {
            return;
        }
        if (!connecting.compareAndSet(false, true)) {
            return;
        }
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
        future.addListener(f -> {
            connecting.set(false);
            if (f.isSuccess()) {
                channel = future.channel();
                reconnectScheduled.set(false);
                log.info("netty client connected to {}:{}", host, port);
            } else {
                log.warn("netty client failed to connect to {}:{}, retry in {}s",
                        host, port, RECONNECT_DELAY_SECONDS);
                scheduleReconnect();
            }
        });
    }

    public void scheduleReconnect() {
        if (reconnectScheduled.compareAndSet(false, true)) {
            group.schedule(() -> {
                reconnectScheduled.set(false);
                if (!isConnected()) {
                    connect();
                }
            }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
        }
    }

    public void send(String message) {
        if (!isConnected()) {
            log.warn("netty client not connected, message dropped: {}", message);
            scheduleReconnect();
            return;
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
