package hawk.JRegistryCenter.Raft.RPC.Client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;

import hawk.JRegistryCenter.Raft.RPC.RaftRequest;
import hawk.JRegistryCenter.Raft.RPC.RaftReply;
import hawk.JRegistryCenter.Raft.RPC.Server.Services.AppendEntriesService;
import hawk.JRegistryCenter.Raft.RPC.Server.Services.RequestVoteService;
import com.alibaba.fastjson.JSON;
import hawk.JRegistryCenter.Raft.RaftNode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RaftClientHandler extends SimpleChannelInboundHandler<String> {


    private final int peerNodeId;


   
    private AppendEntriesService appendEntriesService;

    
    private RequestVoteService requestVoteService;

    
    private RaftNode raftNode;

    
    private RaftClientManager raftClientManager;


    public RaftClientHandler(int peerNodeId, AppendEntriesService appendEntriesService, RequestVoteService requestVoteService, RaftNode raftNode, RaftClientManager raftClientManager) {
        this.peerNodeId = peerNodeId;
        this.appendEntriesService = appendEntriesService;
        this.requestVoteService = requestVoteService;
        this.raftNode = raftNode;
        this.raftClientManager = raftClientManager;
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        // 处理来自 peer 的 Raft RPC 响应
        // 解析并交给 RaftNode 处理
        try {
        RaftReply reply = JSON.parseObject(msg, RaftReply.class);
        RaftRequest request = null;
        switch (reply.getType()) {
            case "appendEntries":
                request = appendEntriesService.clientHandleAppendEntriesRequest(reply);
                break;
            case "heartbeat":
                //do nothing, because leader needn't respond to follower's heartbeat response
                // request = appendEntriesService.handleHeartbeatReply(reply);
                break;
            case "requestVote":
                request = requestVoteService.clientHandleRequestVoteRequest(reply, raftClientManager);
                break;
            default:
                    break;
            }
            if(request != null){
                ctx.writeAndFlush(JSON.toJSONString(request) + "\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
            // ctx.writeAndFlush("{\"error\":\"" + e.getMessage() + "\"}\n");
        }
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            if(raftNode.getLeaderId() == raftNode.getId()){ // 如果自己是leader，发送心跳
                appendEntriesService.sendHeartBeat(ctx.channel(), peerNodeId);
            }
        }
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        appendEntriesService.sendShakeHands(ctx.channel(), peerNodeId);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("node {} connection to peer {} lost, re-connect in 5 seconds" , raftClientManager.getId(), peerNodeId);
        raftClientManager.getPeerChannels().remove(peerNodeId);
        String[] address = raftClientManager.getPeerAddresses().get(peerNodeId).split(":");
        raftClientManager.scheduleReconnect(peerNodeId, address[0], Integer.parseInt(address[1]));
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}