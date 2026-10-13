package io.github.noobcodergrowing.jregistryclient;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyClientHandler extends SimpleChannelInboundHandler<String> {

    private final NettyClient client;

    public NettyClientHandler(NettyClient client) {
        this.client = client;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        client.onMessage(msg);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("connected to registry server at {}:{}", client.getHost(), client.getPort());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("disconnected from registry server at {}:{}", client.getHost(), client.getPort());
        client.scheduleReconnect();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        // write-idle：一段时间没往服务端写东西就补一发心跳，避免服务端误判连接已死。
        if (evt instanceof IdleStateEvent) {
            client.sendHeartbeat();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("netty client error: {}", cause.getMessage());
        ctx.close();
    }
}
