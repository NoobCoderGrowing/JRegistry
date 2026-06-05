package io.github.noobcodergrowing.jregistryclient;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
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
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("netty client error: {}", cause.getMessage());
        ctx.close();
    }
}
