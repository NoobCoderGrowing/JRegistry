package hawk.JRegistryCenter.CLI;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import com.alibaba.fastjson.JSON;
import hawk.JRegitstryCore.RPC.CLIRequest;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
public class CLIServerHandler extends SimpleChannelInboundHandler<String> {

    private final CLIService cliService;

    private ThreadPoolExecutor writePool;

    public CLIServerHandler(CLIService cliService, ThreadPoolExecutor writePool) {
        this.cliService = cliService;
        this.writePool = writePool;
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
