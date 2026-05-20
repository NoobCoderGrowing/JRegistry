package hawk.JRegistryCenter.Raft.RPC.Server;

import com.alibaba.fastjson.JSON;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.springframework.stereotype.Component;
import hawk.JRegistryCenter.Raft.RPC.Server.Services.AppendEntriesService;
import hawk.JRegistryCenter.Raft.RPC.Server.Services.RequestVoteService;
import hawk.JRegitstryCore.RPC.RaftRequest;
import hawk.JRegistryCenter.Raft.RaftNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import hawk.JRegistryCenter.Raft.Log.LogService;
import java.util.concurrent.ThreadPoolExecutor;
import hawk.JRegistryCenter.Raft.RPC.Server.Services.Persist.PersistService;

@Slf4j
@Component
@Data
public class RaftServerHandler extends SimpleChannelInboundHandler<String> {

    private int peerNodeId;

   
    private RaftServerManager raftServer;


    private AppendEntriesService appendEntriesService;

    private RequestVoteService requestVoteService;

    private RaftNode raftNode;

    private LogService logService;

    private ThreadPoolExecutor writePool;

    private PersistService persistService;

    public RaftServerHandler(RaftServerManager raftServer, AppendEntriesService appendEntriesService, 
        RequestVoteService requestVoteService, RaftNode raftNode, LogService logService, 
        ThreadPoolExecutor writePool, PersistService persistService) {
        this.raftServer = raftServer;
        this.appendEntriesService = appendEntriesService;
        this.requestVoteService = requestVoteService;
        this.raftNode = raftNode;
        this.logService = logService;
        this.writePool = writePool;
        this.persistService = persistService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        try {
            RaftRequest request = JSON.parseObject(msg, RaftRequest.class);
            RaftRequest reply = null;
            // log.info("server {} handle request: {}", raftNode.getId(), JSON.toJSONString(request));
            switch (request.getType()) {
                case "appendEntries":
                    reply = appendEntriesService.handleAppendEntriesRequest(request);
                    break;
                case "heartbeat":
                    reply =appendEntriesService.serverHandleHeartbeatRequest(request);
                    break;
                case "requestVote":
                    reply = requestVoteService.serverHandleRequestVoteRequest(request);
                    break;
                case "shakeHand":
                    reply = appendEntriesService.handleShakeHandsRequest(request, this, ctx.channel());
                    break;
                case "installSnapshot":
                    reply = appendEntriesService.handleInstallSnapshotRequest(request);
                    break;
                case "commitLogs":
                    reply = logService.followerCommitLogs(request);
                    break;
                case "writeRequest":
                    reply = logService.handleWriteRequest(request);
                    break;
                case "persist":
                    persistService.persist();
                    break;
                default:
                    break;
            }
            if(reply != null){
                final RaftRequest finalReply = reply;
                writePool.execute(() -> {
                    ctx.writeAndFlush(JSON.toJSONString(finalReply) + "\n");
                });
                // log.info("server {} send reply: {}", raftNode.getId(), JSON.toJSONString(reply));
            }
        } catch (Exception e) {
            log.error("server {} handle request error: {}", raftNode.getId(), e.getMessage());
        }
        
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            //如果对方是Leader超时没有发送心跳，发起选举
            //如果集群刚启动没有leader, 发起选举
            // if(raftNode.getLeaderId()==this.peerNodeId || raftNode.getLeaderId() == -1 ){ // 如果对方是Leader, 发起选举
            //     requestVoteService.startElection();
            // }

            //交给TimeoutLoop处理投票发起
    
        }
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        raftServer.getPeerChannels().remove(peerNodeId);
        log.info("server {} disconnected from peer {}", raftNode.getId(), peerNodeId);
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}