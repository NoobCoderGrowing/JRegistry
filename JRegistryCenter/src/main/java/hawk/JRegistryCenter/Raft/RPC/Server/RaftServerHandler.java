package hawk.JRegistryCenter.Raft.RPC.Server;

import com.alibaba.fastjson.JSON;
import hawk.JRegistryCenter.Raft.RPC.RPCReply;
import hawk.JRegistryCenter.Raft.RPC.RPCRequest;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import hawk.JRegistryCenter.Raft.RPC.Server.Services.AppendEntriesService;
import hawk.JRegistryCenter.Raft.RPC.Server.Services.RequestVoteService;
import hawk.JRegistryCenter.Raft.RaftNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Data
public class RaftServerHandler extends SimpleChannelInboundHandler<String> {

    private int peerNodeId;

    @Autowired
    private RaftServerManager raftServer;

    @Autowired
    private AppendEntriesService appendEntriesService;

    @Autowired
    private RequestVoteService requestVoteService;

    @Autowired
    private RaftNode raftNode;
;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        try {
            RPCRequest request = JSON.parseObject(msg, RPCRequest.class);
            RPCReply reply = null;
            log.info("server {} handle request: {}", raftNode.getId(), JSON.toJSONString(request));
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
                default:
                    break;
            }
            if(reply != null){
                ctx.writeAndFlush(JSON.toJSONString(reply) + "\n");
                log.info("server {} send reply: {}", raftNode.getId(), JSON.toJSONString(reply));
            }
        } catch (Exception e) {
            log.error("server {} handle request error: {}", raftNode.getId(), e.getMessage());
            // ctx.writeAndFlush("{\"error\":\"" + e.getMessage() + "\"}\n");
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
        Integer peerNodeId = raftServer.getAddressToPeer().get(ctx.channel().remoteAddress().toString());
        this.peerNodeId = peerNodeId;
        if(peerNodeId != null){
            raftServer.getPeerChannels().put(peerNodeId, ctx.channel());
        }
        log.info("server {} connected to peer {}", raftNode.getId(), peerNodeId);
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