package hawk.JRegistryCenter.Raft.RPC.Client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.springframework.beans.factory.annotation.Autowired;

import hawk.JRegistryCenter.Raft.RPC.RPCRequest;
import hawk.JRegistryCenter.Raft.RPC.RPCReply;
import hawk.JRegistryCenter.Raft.RPC.Server.Services.AppendEntriesService;
import hawk.JRegistryCenter.Raft.RPC.Server.Services.RequestVoteService;
import com.alibaba.fastjson.JSON;

public class RaftClientHandler extends SimpleChannelInboundHandler<String> {
    private final int peerNodeId;


    @Autowired
    private AppendEntriesService appendEntriesService;

    @Autowired
    private RequestVoteService requestVoteService;

    public RaftClientHandler(int peerNodeId) {
        this.peerNodeId = peerNodeId;
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        // 处理来自 peer 的 Raft RPC 响应
        // 解析并交给 RaftNode 处理
        try {
        RPCRequest request = JSON.parseObject(msg, RPCRequest.class);
        RPCReply reply = null;
        switch (request.getType()) {
            case "appendEntries":
                reply = appendEntriesService.handleAppendEntriesRequest(request);
                break;
            case "heartbeat":
                reply = appendEntriesService.handleHeartbeatRequest(request);
                break;
            case "requestVote":
                reply = requestVoteService.handleRequestVoteRequest(request);
                break;
            default:
                    break;
            }
            ctx.writeAndFlush(JSON.toJSONString(reply) + "\n");
        } catch (Exception e) {
            e.printStackTrace();
            ctx.writeAndFlush("{\"error\":\"" + e.getMessage() + "\"}\n");
        }
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            // 发送心跳
            ctx.writeAndFlush("HEARTBEAT\n");
        }
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        System.out.println("Connection to peer " + peerNodeId + " lost");
        // 触发重连逻辑
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}