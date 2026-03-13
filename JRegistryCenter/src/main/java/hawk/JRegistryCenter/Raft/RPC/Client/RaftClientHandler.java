package hawk.JRegistryCenter.Raft.RPC.Client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;

public class RaftClientHandler extends SimpleChannelInboundHandler<String> {
    private final int peerNodeId;
    
    public RaftClientHandler(int peerNodeId) {
        this.peerNodeId = peerNodeId;
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        // 处理来自 peer 的 Raft RPC 响应
        // 解析并交给 RaftNode 处理
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