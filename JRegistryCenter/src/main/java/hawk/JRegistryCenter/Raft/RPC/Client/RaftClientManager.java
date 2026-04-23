package hawk.JRegistryCenter.Raft.RPC.Client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import javax.annotation.PostConstruct;
import lombok.Data;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.PreDestroy;
import java.lang.Thread;
import org.springframework.beans.factory.annotation.Autowired;
import hawk.JRegistryCenter.Raft.RPC.Server.Services.AppendEntriesService;
import hawk.JRegistryCenter.Raft.RPC.Server.Services.RequestVoteService;
import hawk.JRegistryCenter.Raft.RaftNode;
import hawk.JRegistryCenter.Raft.Log.LogService;

@Slf4j
@Component
@Data
public class RaftClientManager {
    
    private final ConcurrentHashMap<Integer, Channel> peerChannels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> peerAddresses = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, AtomicBoolean> reconnectLock = new ConcurrentHashMap<>();
    private EventLoopGroup group;

    @Value("#{${raft.peers:{}}}")
    private Map<Integer, String> peers;

    @Value("${raft.node-id}")
    private int id;

    @Autowired
    private AppendEntriesService appendEntriesService;

    @Autowired
    private RequestVoteService requestVoteService;

    @Autowired
    private RaftNode raftNode;

    private LogService logService;

    @PostConstruct
    public void init(){
        group = new NioEventLoopGroup();
        initPeers(peers);
        connectAllPeers();
        try{
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        }catch(Exception e){
            log.error("node {} add shutdown hook error", id);
        }
   }

   @Bean
   public LogService initLogService(){
        LogService logService = new LogService(this);
        this.logService = logService;
        return logService;
   }
    // 初始化：配置所有 peer 节点的地址
    public void initPeers(Map<Integer, String> peers) {
        peerAddresses.putAll(peers);
        for (Map.Entry<Integer, String> entry : peers.entrySet()) {
            reconnectLock.put(entry.getKey(), new AtomicBoolean(false));
        }
    }
    
    // 连接到指定节点
    public void connectToPeer(int nodeId, String host, int port) {
        
        if (peerChannels.containsKey(nodeId) && peerChannels.get(nodeId).isActive()) {
            return; // 已连接
        }

        //如果正在重连，则不进行重连
        if(!reconnectLock.get(nodeId).compareAndSet(false, true)){
            return; //正在重连，不进行重连
        }

        Bootstrap b = new Bootstrap();
        b.group(group)
         .channel(NioSocketChannel.class) //使用NIO Socket通道
         .option(ChannelOption.TCP_NODELAY, true)  // 禁用 Nagle 算法，降低延迟
         .option(ChannelOption.SO_KEEPALIVE, true)  // 开启 TCP keepalive
         .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
         .handler(new ChannelInitializer<SocketChannel>() {
             @Override
             protected void initChannel(SocketChannel ch) {
                 ChannelPipeline p = ch.pipeline();
                 // 30秒无读写则触发ideleStateEvent
                 p.addLast(new IdleStateHandler(0, 10000, 0, TimeUnit.MILLISECONDS)); 
                 p.addLast(new LineBasedFrameDecoder(8192)); //使用行分隔符解码器，每行一个消息
                 p.addLast(new StringDecoder(StandardCharsets.UTF_8)); //使用字符串解码器，将字符串解码为消息
                 p.addLast(new StringEncoder(StandardCharsets.UTF_8)); //使用字符串编码器，将消息编码为字符串
                 p.addLast(new RaftClientHandler(nodeId, appendEntriesService, requestVoteService, raftNode, RaftClientManager.this)); //使用RaftClientHandler处理消息
             }
         });
        
        //异步连接并处理结果
        b.connect(host, port).addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {

                peerChannels.compute(nodeId, (k, oldCh) -> {
                    if (oldCh != null && oldCh != future.channel() && oldCh.isOpen()) {
                        oldCh.close(); // 关闭旧连接，避免同一 peer 多个活跃 channel
                    }
                    return future.channel(); //compute返回什么就把这个key更新为这个值
                });

                // peerChannels.put(nodeId, future.channel());  //保存连接
                log.info("node {} connected to Raft peer {} at {}", id, nodeId, host + ":" + port);
            } else {
                log.info("node {} failed to connect to Raft peer {} at {}. Reconnect in 5 seconds", id, nodeId, host + ":" + port);
                // 延迟重连     
                scheduleReconnect(nodeId, host, port); //失败延迟重连
            }
            reconnectLock.get(nodeId).set(false);
        });
    }
    
    // 发送消息到指定节点
    public void sendToPeer(int nodeId, String message) {
        Channel channel = peerChannels.get(nodeId);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(message + "\n");
        } else {
            // 连接断开，触发重连
            String addr = peerAddresses.get(nodeId);
            if (addr != null) {
                String[] parts = addr.split(":");
                scheduleReconnect(nodeId, parts[0], Integer.parseInt(parts[1]));
            }
        }
    }

    public void sendToAllPeers(String message) {
        for(int nodeId : peerChannels.keySet()) {
            sendToPeer(nodeId, message);
        }
    }
    
    // 延迟重连
    public void scheduleReconnect(int nodeId, String host, int port) {
        group.schedule(() -> {
            if (!peerChannels.containsKey(nodeId) || 
                !peerChannels.get(nodeId).isActive()) {
                connectToPeer(nodeId, host, port);
            }
        }, 5, TimeUnit.SECONDS);
    }
    
    // 启动时连接所有 peer
    public void connectAllPeers() {
        peerAddresses.forEach((nodeId, addr) -> {
            String[] parts = addr.split(":");
            connectToPeer(nodeId, parts[0], Integer.parseInt(parts[1]));
        });
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

    @PreDestroy
    public void shutdown() {
     // 关闭所有已建立连接
        peerChannels.forEach((id, ch) -> {
            if (ch != null && ch.isOpen()) {
                ch.close();
            }
        });
        peerChannels.clear();

        // 关闭 Netty 线程组
        if (group != null) {
            group.shutdownGracefully();
        }

        log.info("RaftClientManager {} shutdown gracefully", id);
    }
}
