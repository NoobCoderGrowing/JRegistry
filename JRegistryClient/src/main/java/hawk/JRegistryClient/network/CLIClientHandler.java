package hawk.JRegistryClient.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class CLIClientHandler extends SimpleChannelInboundHandler<String> {

    private final CLIClient cliClient;

    public CLIClientHandler(CLIClient cliClient) {
        this.cliClient = cliClient;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        // 不再只打印，而是交给 CLIClient 处理
        cliClient.completeResponse(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}