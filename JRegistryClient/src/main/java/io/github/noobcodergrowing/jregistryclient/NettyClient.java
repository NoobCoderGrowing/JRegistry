package io.github.noobcodergrowing.jregistryclient;

import com.alibaba.fastjson.JSON;
import com.github.f4b6a3.uuid.UuidCreator;
import io.github.noobcodergrowing.jregistrycore.NodeAddress;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.PreDestroy;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class NettyClient {

    private static final int RECONNECT_DELAY_SECONDS = 5;
    private  int CONNECT_TIMEOUT_MILLIS = 5000;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 10;
    /** 连续重连失败多少次后换下一个候选节点（配合 5s 重连间隔 ≈ 20s 才搬家）。 */
    private static final int FAILOVER_AFTER_FAILED_ATTEMPTS = 3;

    private volatile String host;

    private volatile int port;

    private final EventLoopGroup group = new NioEventLoopGroup(1);

    /**
     * 重连专用线程。<b>不能在 Netty 的 event loop 上跑 {@link #connectSync()}</b>——它内部
     * {@code future.await(...)} 会阻塞，而 connect 的 future 正好由同一个 event loop 完成，
     * 于是 Netty 直接抛 BlockingOperationException（这就是原来「断线后重连」从来没生效过的原因）。
     */
    private final ExecutorService reconnectExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "jregistry-reconnect");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    /** 连续重连失败后由它给出下一个候选地址；为 null 表示不做故障转移（单地址模式）。 */
    private volatile Supplier<NodeAddress> failoverTargetSupplier;

    /**
     * 本客户端实例的稳定身份，随实例生命周期不变、跨重连不变。
     * 服务端靠它把「换了一条连接回来的同一个客户端」认成同一个 client，
     * 从而只做接管、不误删它此前 set 的 key。
     */
    private final String clientId = UuidCreator.getTimeOrderedEpoch().toString();

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
        if (closed.get() || isConnected() || connecting.get()) {
            return;
        }
        reconnectExecutor.execute(this::tryReconnect);
    }

    /**
     * 重连循环：试一次，失败就把下一次排上。
     *
     * <p>这一步是必要的——原来的实现只在 {@code channelInactive} 时排一次重试，而失败的
     * {@code connectSync} 既不会产生 channelInactive 也不会自己再排一次，所以目标一旦连不上，
     * 循环当场就断了（注释里写的 "reconnect forever" 其实只重试一次）。
     * 连续失败到阈值后就换下一个候选节点，这就是运行期的故障转移。
     */
    private void tryReconnect() {
        if (closed.get() || isConnected()) {
            return;
        }
        if (consecutiveFailures.incrementAndGet() > FAILOVER_AFTER_FAILED_ATTEMPTS) {
            moveToNextCandidate();
        }
        if (connectSync()) {
            consecutiveFailures.set(0);
        } else if (!closed.get()) {
            scheduleReconnect();
        }
    }

    /** 走到候选列表的下一个节点；没有候选（单地址模式）时原地不动。 */
    private void moveToNextCandidate() {
        Supplier<NodeAddress> supplier = failoverTargetSupplier;
        if (supplier == null) {
            return;
        }
        NodeAddress next = supplier.get();
        if (next == null) {
            return;
        }
        log.warn("netty client found {}:{} unreachable after {} attempts, failing over to {}:{}",
                host, port, consecutiveFailures.get(), next.getHost(), next.getPort());
        host = next.getHost();
        port = next.getPort();
        consecutiveFailures.set(0);
    }

    /** 设置候选地址来源；传 null 关闭故障转移。 */
    public void setFailoverTargetSupplier(Supplier<NodeAddress> supplier) {
        this.failoverTargetSupplier = supplier;
    }

    public void scheduleReconnect() {
        if (closed.get() || !reconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        group.schedule(() -> {
            reconnectScheduled.set(false);
            if (!isConnected()) {
                connectAsync();
            }
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 换一个目标地址并重连（用于服务端下发的选点结果）。目标没变且已连接时直接返回，
     * 所以「候选首位的恰好就是当前节点」这种情况不会白白断一次连接。
     *
     * <p>目标一旦切换，后续的自动重连（{@link #scheduleReconnect}）也会跟着走新地址。
     */
    public boolean reconnectTo(String host, int port) {
        if (isConnected() && this.host.equals(host) && this.port == port) {
            return true;
        }
        this.host = host;
        this.port = port;
        Channel current = channel;
        if (current != null && current.isOpen()) {
            channel = null; // 先摘引用，避免断开回调里的重连把旧连接又拉起来
            current.close();
        }
        return connectSync();
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
        // 单点注入：所有出站请求都带上本客户端的身份，服务端据此建立/接管租约。
        if (request.getClientId() == null) {
            request.setClientId(clientId);
        }
        send(JSON.toJSONString(request));
    }

    /**
     * 发一次客户端心跳。由 write-idle 事件驱动：只要一段时间没写，就补一发，
     * 让服务端知道这条连接活着。断开时静默丢弃，不抛异常。
     */
    public void sendHeartbeat() {
        if (!isConnected()) {
            return;
        }
        RaftRequest heartbeat = new RaftRequest();
        heartbeat.setType("clientHeartbeat");
        heartbeat.setClientId(clientId);
        try {
            send(JSON.toJSONString(heartbeat));
        } catch (RuntimeException e) {
            log.debug("client heartbeat skipped: {}", e.getMessage());
        }
    }

    void onMessage(String message) {
        log.info("netty client received: {}", message);
        if (messageListener != null) {
            messageListener.accept(message);
        }
    }

    @PreDestroy
    public void shutdown() {
        closed.set(true); // 先停掉重连循环，否则关掉线程组之后它还会继续排重试
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        reconnectExecutor.shutdownNow();
        group.shutdownGracefully();
        log.info("netty client shutdown");
    }
}
