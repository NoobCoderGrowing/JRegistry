package io.github.noobcodergrowing.jregistry.Raft.RPC.Server;

import com.alibaba.fastjson.JSON;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;

import io.github.noobcodergrowing.jregistrycore.RPC.RaftRequest;
import io.github.noobcodergrowing.jregistrycore.Raft.RaftNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import io.github.noobcodergrowing.jregistry.Raft.Log.LogService;
import io.github.noobcodergrowing.jregistry.Services.AppendEntriesService;
import io.github.noobcodergrowing.jregistry.Services.ClientSessionService;
import io.github.noobcodergrowing.jregistry.Services.RequestVoteService;
import io.github.noobcodergrowing.jregistry.Services.Persist.PersistService;

import java.util.concurrent.ThreadPoolExecutor;
import io.github.noobcodergrowing.jregistrycore.StateMachine;

@Slf4j
@Data
public class RaftServerHandler extends SimpleChannelInboundHandler<String> {

    private int peerNodeId;

   
    private RaftServerManager raftServer;


    private AppendEntriesService appendEntriesService;

    private RequestVoteService requestVoteService;

    private RaftNode raftNode;

    private LogService logService;

    private ThreadPoolExecutor writePool;

    private PersistService persistService;

    private StateMachine stateMachine;

    private ClientSessionService clientSessionService;

    public RaftServerHandler(RaftServerManager raftServer, AppendEntriesService appendEntriesService,
        RequestVoteService requestVoteService, RaftNode raftNode, LogService logService,
        ThreadPoolExecutor writePool, PersistService persistService, StateMachine stateMachine,
        ClientSessionService clientSessionService) {
        this.raftServer = raftServer;
        this.appendEntriesService = appendEntriesService;
        this.requestVoteService = requestVoteService;
        this.raftNode = raftNode;
        this.logService = logService;
        this.writePool = writePool;
        this.persistService = persistService;
        this.stateMachine = stateMachine;
        this.clientSessionService = clientSessionService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        try {
            RaftRequest request = JSON.parseObject(msg, RaftRequest.class);
            RaftRequest reply = null;
            // log.info("server {} handle request: {}", raftNode.getId(), JSON.toJSONString(request));
            switch (request.getType()) {
                case "appendEntries":
                    reply = appendEntriesService.handleAppendEntriesRequest(request);
                    break;
                case "heartbeat":
                    reply =appendEntriesService.serverHandleHeartbeatRequest(request);
                    break;
                case "requestVote":
                    reply = requestVoteService.serverHandleRequestVoteRequest(request);
                    break;
                case "shakeHand":
                    reply = appendEntriesService.handleShakeHandsRequest(request, this, ctx.channel());
                    break;
                case "installSnapshot":
                    reply = logService.handleInstallSnapshotRequest(request);
                    break;
                case "commitLogs":
                    reply = logService.followerCommitLogs(request);
                    break;
                case "clientHeartbeat":
                    // 客户端心跳：只是"这条连接还活着"的证据。绝不能复用 "heartbeat" 这个名字——
                    // 那个会被 serverHandleHeartbeatRequest 当成 leader 心跳、刷新选举超时。
                    clientSessionService.onHeartbeat(request.getClientId(), ctx.channel());
                    break;
                case "writeRequest":
                    // touch 必须在处理写之前：它决定 clientAttach 与首条 set 的日志先后。
                    clientSessionService.touch(request.getClientId(), ctx.channel());
                    reply = logService.handleWriteRequest(request);
                    break;
                case "persist":
                    persistService.handlePersistRequest();
                    break;
                case "compact":
                    persistService.handleCompactRequest();
                    break;
                case "get":
                    clientSessionService.touch(request.getClientId(), ctx.channel());
                    reply = stateMachine.handleGetRequest(request);
                    break;
                case "ls":
                    clientSessionService.touch(request.getClientId(), ctx.channel());
                    reply = stateMachine.handleLsRequest(request);
                    break;
                default:
                    break;
            }
            if(reply != null){
                final RaftRequest finalReply = reply;
                writePool.execute(() -> {
                    ctx.writeAndFlush(JSON.toJSONString(finalReply) + "\n");
                });
                // log.info("server {} send reply: {}", raftNode.getId(), JSON.toJSONString(reply));
            }
        } catch (Exception e) {
            log.error("server {} handle request error: {}, stack trace: {}", raftNode.getId(), e.getMessage(), e.getStackTrace());
        }
        
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            //如果对方是Leader超时没有发送心跳，发起选举
            //如果集群刚启动没有leader, 发起选举
            // if(raftNode.getLeaderId()==this.peerNodeId || raftNode.getLeaderId() == -1 ){ // 如果对方是Leader, 发起选举
            //     requestVoteService.startElection();
            // }

            //交给TimeoutLoop处理投票发起

            // 一次读超时 = 客户端少了一次心跳。只对确实注册过的 client 连接计数，
            // peer 连接在 ClientSessionService 里查不到会话，自然被忽略。
            clientSessionService.onMiss(ctx.channel());
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // 连接确定关闭（FIN/RST）→ 立即清理，不必等满三次超时。
        clientSessionService.onDisconnect(ctx.channel());
        raftServer.getPeerChannels().remove(peerNodeId);
        log.info("server {} disconnected from peer {}", raftNode.getId(), peerNodeId);
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}