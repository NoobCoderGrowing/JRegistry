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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
public class RaftServer {
    
    @Value("${raft.port:6605}")
    private int raftPort;
    
    @Autowired
    private RaftServerHandler raftServerHandler;
    
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;
    
    @PostConstruct
    public void start() throws InterruptedException {
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
                     // 心跳检测：30秒无读写则关闭连接
                     p.addLast(new IdleStateHandler(0, 0, 30, TimeUnit.SECONDS));
                     p.addLast(new LineBasedFrameDecoder(8192));
                     p.addLast(new StringDecoder(StandardCharsets.UTF_8));
                     p.addLast(new StringEncoder(StandardCharsets.UTF_8));
                     p.addLast(raftServerHandler);
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
}