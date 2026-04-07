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
import org.springframework.beans.factory.annotation.Autowired;
import hawk.JRegistryClient.network.Config.LoadBalancer;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class NettyClient {
    private EventLoopGroup group;
    private Channel channel;

    @Autowired
    private LoadBalancer loadBalancer;

    private Bootstrap bootstrap;

   @PostConstruct
    public void nettyInit() {
        group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
         .channel(NioSocketChannel.class)
         .option(ChannelOption.TCP_NODELAY, true)
         .handler(new ChannelInitializer<SocketChannel>() {
             @Override
             protected void initChannel(SocketChannel ch) {
                 ChannelPipeline p = ch.pipeline();
                 p.addLast(new LineBasedFrameDecoder(8192));
                 p.addLast(new StringDecoder(StandardCharsets.UTF_8));
                 p.addLast(new StringEncoder(StandardCharsets.UTF_8));
                 p.addLast(new ClientHandler());
             }
         });
         String raftNode = loadBalancer.getRandomRaftNode();
         String[] parts = raftNode.split(":");
         String host = parts[0];
         int port = Integer.parseInt(parts[1]);
         connect(host, port);
    }

    // 启动客户端并建立长连接
    public void connect(String host, int port){
        try {
        
            ChannelFuture f = bootstrap.connect(host, port).sync();
            channel = f.channel();
            log.info("Connected to JRegistry TCP server: " + host + ":" + port);
        } catch (InterruptedException e) {
            log.error("Failed to connect to JRegistry TCP server: " + host + ":" + port, e);
        }
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
