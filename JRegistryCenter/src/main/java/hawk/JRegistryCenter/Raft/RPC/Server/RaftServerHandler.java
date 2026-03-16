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

@Component
public class RaftServerHandler extends SimpleChannelInboundHandler<String> {

    @Autowired
    private AppendEntriesService appendEntriesService;

    @Autowired
    private RequestVoteService requestVoteService;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        try {
            RPCRequest request = JSON.parseObject(msg, RPCRequest.class);
            RPCReply reply = null;
            switch (request.getType()) {
                case "appendEntries":
                    reply = appendEntriesService.handleAppendEntriesRequest(request);
                    ctx.writeAndFlush(JSON.toJSONString(reply) + "\n");
                    break;
                case "heartbeat":
                    reply =appendEntriesService.handleHeartbeatRequest();
                    ctx.writeAndFlush(JSON.toJSONString(reply) + "\n");
                    break;
                case "requestVote":
                    reply = requestVoteService.handleRequestVoteRequest(request);
                    ctx.writeAndFlush(JSON.toJSONString(reply) + "\n");
                    break;
                default:
                    break;
            }

        } catch (Exception e) {
            e.printStackTrace();
            // ctx.writeAndFlush("{\"error\":\"" + e.getMessage() + "\"}\n");
        }
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            // 30秒无活动，关闭连接
            System.out.println("Closing idle connection from " + ctx.channel().remoteAddress());
            ctx.close();
        }
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("Raft peer connected: " + ctx.channel().remoteAddress());
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        System.out.println("Raft peer disconnected: " + ctx.channel().remoteAddress());
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}