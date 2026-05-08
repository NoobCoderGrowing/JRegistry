package hawk.JRegistryCenter.CLI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.netty.channel.EventLoopGroup;
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
import java.util.concurrent.ThreadPoolExecutor;


@Slf4j
@Component
public class CLIServer {

    @Value("${host}")
    private String host;
    @Value("${CLS.port}")
    private int port;
    @Value("${raft.node-id}")
    private int id;
    @Autowired
    private CLIService cliService;

    @Autowired
    private ThreadPoolExecutor writePool;

    @Autowired
    private EventLoopGroup singleGroup;
    private Channel channel;

    @PostConstruct
    public void startListen() throws InterruptedException {
        System.out.println("Connecting to " + host + ":" + port);
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(singleGroup)
             .channel(io.netty.channel.socket.nio.NioServerSocketChannel.class)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ChannelPipeline p = ch.pipeline();
                     p.addLast(new LineBasedFrameDecoder(8192));
                     p.addLast(new StringDecoder(StandardCharsets.UTF_8));
                     p.addLast(new StringEncoder(StandardCharsets.UTF_8));
                     p.addLast(new CLIServerHandler(cliService, writePool));
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
        if (singleGroup != null) {
            singleGroup.shutdownGracefully();
        }
        log.info("CLI server {} shutdown gracefully", id);
    }
}