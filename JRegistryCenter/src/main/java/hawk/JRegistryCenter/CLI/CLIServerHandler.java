package hawk.JRegistryCenter.CLI;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import com.alibaba.fastjson.JSON;
import hawk.JRegitstryCore.RPC.CLIRequest;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class CLIServerHandler extends SimpleChannelInboundHandler<String> {

    private final CLIService cliService;

    public CLIServerHandler(CLIService cliService) {
        this.cliService = cliService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        CLIRequest cliRequest = JSON.parseObject(msg, CLIRequest.class);
        cliService.handleCLIRequest(ctx.channel(), cliRequest);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
