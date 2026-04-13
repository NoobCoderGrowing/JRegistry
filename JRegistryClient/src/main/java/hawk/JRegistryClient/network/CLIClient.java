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
import java.nio.charset.StandardCharsets;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;

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
                 p.addLast(new CLIClientHandler());
             }
         });
         try {
            connect(host, port);
         } catch (InterruptedException e) {
            log.error("Failed to connect to JRegistry TCP server: " + host + ":" + port);
         }
    }


    // 启动客户端并建立长连接
    public void connect(String host, int port) throws InterruptedException {
        ChannelFuture f = b.connect(host, port).sync();
        channel = f.channel();
        log.info("Connected to JRegistry TCP server: " + host + ":" + port);
    }

    // 向 JRegistry 发送一行文本命令
    public void send(String msg) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(msg + "\n");
        } else {
            System.out.println("Channel is not active.");
        }
    }

    // 关闭连接
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

    // 处理服务端返回
    
}
