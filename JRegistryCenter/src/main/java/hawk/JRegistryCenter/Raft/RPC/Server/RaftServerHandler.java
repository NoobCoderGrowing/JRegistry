package hawk.JRegistryCenter.Raft.RPC.Server;

import com.alibaba.fastjson.JSON;
import hawk.JRegistryCenter.Raft.RaftNode;
import hawk.JRegistryCenter.Raft.RPC.AppendEntriesRequest;
import hawk.JRegistryCenter.Raft.RPC.AppendEntriesReply;
import hawk.JRegistryCenter.Raft.RPC.RequestVoteRequest;
import hawk.JRegistryCenter.Raft.RPC.RequestVoteReply;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RaftServerHandler extends SimpleChannelInboundHandler<String> {
    
    @Autowired
    private RaftNode raftNode;
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        try {
            // 解析 JSON 消息
            if (msg.trim().equals("HEARTBEAT")) {
                // 心跳消息，直接返回
                ctx.writeAndFlush("HEARTBEAT_OK\n");
                return;
            }
            
            // 尝试解析为 AppendEntriesRequest
            if (msg.contains("\"leaderId\"") || msg.contains("\"prevLogIndex\"")) {
                AppendEntriesRequest request = JSON.parseObject(msg, AppendEntriesRequest.class);
                AppendEntriesReply reply = raftNode.appendEntries(
                    request.getTerm(),
                    request.getLeaderId(),
                    request.getPrevLogIndex(),
                    request.getPrevLogTerm(),
                    request.getEntries(),
                    request.getLeaderCommit()
                );
                ctx.writeAndFlush(JSON.toJSONString(reply) + "\n");
                return;
            }
            
            // 尝试解析为 RequestVoteRequest
            if (msg.contains("\"candidateId\"") || msg.contains("\"lastLogIndex\"")) {
                RequestVoteRequest request = JSON.parseObject(msg, RequestVoteRequest.class);
                RequestVoteReply reply = raftNode.requestVote(
                    request.getTerm(),
                    request.getCandidateId(),
                    request.getLastLogIndex(),
                    request.getLastLogTerm()
                );
                ctx.writeAndFlush(JSON.toJSONString(reply) + "\n");
                return;
            }
            
            // 未知消息类型
            ctx.writeAndFlush("{\"error\":\"Unknown message type\"}\n");
            
        } catch (Exception e) {
            e.printStackTrace();
            ctx.writeAndFlush("{\"error\":\"" + e.getMessage() + "\"}\n");
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