package hawk.JRegistryCenter.Raft.RPC.Server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import lombok.Data;

@Component
@Data
public class RaftServerManager {
    
    @Value("${raft.port}")
    private int raftPort;
    
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    @Value("#{${raft.peers}}")
    private Map<Integer, String> peers;
    private final ConcurrentHashMap<Integer, Channel> peerChannels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> addressToPeer= new ConcurrentHashMap<>();
    
    public void initPeers(Map<Integer, String> peers) {
        for (Map.Entry<Integer, String> entry : peers.entrySet()) {
            addressToPeer.put(entry.getValue(), entry.getKey());
        }
    }


    @PostConstruct
    public void start() throws InterruptedException {
        initPeers(peers);

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .option(ChannelOption.SO_BACKLOG, 128)
             .childOption(ChannelOption.SO_KEEPALIVE, true)
             .childOption(ChannelOption.TCP_NODELAY, true)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ChannelPipeline p = ch.pipeline();
                     // 心跳检测：30秒无读写发送选举
                     p.addLast(new IdleStateHandler(0, 0, 30, TimeUnit.SECONDS));
                     p.addLast(new LineBasedFrameDecoder(8192));
                     p.addLast(new StringDecoder(StandardCharsets.UTF_8));
                     p.addLast(new StringEncoder(StandardCharsets.UTF_8));
                     p.addLast(new RaftServerHandler());
                 }
             });
            
            ChannelFuture f = b.bind(raftPort).sync();
            channel = f.channel();
            System.out.println("Raft Server started on port " + raftPort);
            
            // 注册关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
            
            // 阻塞直到服务器关闭
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            shutdown();
        }
    }
    
    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            channel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        System.out.println("Raft Server shutdown gracefully");
    }

    // 发送消息到指定节点
    public void sendToPeer(int nodeId, String message) {
        Channel channel = peerChannels.get(nodeId);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(message + "\n");
        } 
    }

    public int getActivePeers() {
        int activePeers = 0;
        for (Channel channel : peerChannels.values()) {
            if(channel.isActive()) {
                activePeers++;
            }
        }
        return activePeers;
    }
}