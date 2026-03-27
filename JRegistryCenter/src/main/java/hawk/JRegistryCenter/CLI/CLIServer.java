package hawk.JRegistryCenter.CLI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import java.nio.charset.StandardCharsets;
import io.netty.channel.ChannelFuture;
import io.netty.channel.Channel;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CLIServer {

    @Value("${host}")
    private String host;
    @Value("${CLS.port}")
    private int port;
    @Value("${raft.node-id}")
    private int id;

    private EventLoopGroup boss;
    private EventLoopGroup worker;
    private Channel channel;

    @PostConstruct
    public void startListen() throws InterruptedException {
        System.out.println("Connecting to " + host + ":" + port);
        boss = new NioEventLoopGroup(1);
        worker = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(boss, worker)
             .channel(io.netty.channel.socket.nio.NioServerSocketChannel.class)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ChannelPipeline p = ch.pipeline();
                     p.addLast(new LineBasedFrameDecoder(8192));
                     p.addLast(new StringDecoder(StandardCharsets.UTF_8));
                     p.addLast(new StringEncoder(StandardCharsets.UTF_8));
                     p.addLast(new CLIServerHandler());
                 }
             });

            ChannelFuture f = b.bind(port).sync();
            channel = f.channel();
            log.info("CLI server {} started on port {}", id, port);
            
            // 注册 JVM 关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                shutdown();
            }));
            
            // f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // shutdown();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            channel.close();
        }
        if (boss != null) {
            boss.shutdownGracefully();
        }
        if (worker != null) {
            worker.shutdownGracefully();
        }
        log.info("CLI server {} shutdown gracefully", id);
    }
}