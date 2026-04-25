package hawk.JRegistryCenter.Raft.RPC.Server.Services;


import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import hawk.JRegistryCenter.Raft.RaftNode;
import org.springframework.beans.factory.annotation.Autowired;
import io.netty.channel.Channel;
import java.util.Map;
import hawk.JRegistryCenter.Raft.RPC.Server.Timer;
import hawk.JRegitstryCore.RPC.RaftRequest;
import lombok.extern.slf4j.Slf4j;
import hawk.JRegistryCenter.Raft.RPC.Server.RaftServerHandler;
import org.springframework.beans.factory.annotation.Value;
import hawk.JRegitstryCore.Log.LogEntry;
import hawk.JRegistryCenter.Raft.Log.LogService;


@Slf4j
@Service
public class AppendEntriesService {

    @Autowired
    private RaftNode raftNode;

    @Autowired
    private Timer serverTimer;

    @Autowired
    private LogService logService;

    @Value("${host}")
    private String CLIServerHost;
    @Value("${CLS.port}")
    private int CLIServerPort;


    public void sendShakeHands(Channel channel, int peerNodeId){
        RaftRequest request = new RaftRequest();
        request.setType("shakeHand");
        request.setId(raftNode.getId());
        channel.writeAndFlush(JSON.toJSONString(request) + "\n");
        log.info("node {} send shake hand request to node {}", raftNode.getId(), peerNodeId);
        
    }

    public RaftRequest handleShakeHandsRequest(RaftRequest request, RaftServerHandler raftServerHandler, Channel channel){
        //client tell server its id
        raftServerHandler.setPeerNodeId(request.getId());
        raftServerHandler.getRaftServer().getPeerChannels().put(request.getId(), channel);
        return null;
    }

    public RaftRequest handleInstallSnapshotRequest(RaftRequest request){
        if(request.getTerm() >= raftNode.getCurrentTerm()){
            serverTimer.resetTimer();
            acceptLeader(request);
            raftNode.setLsmTree(request.getSnapshot());
            raftNode.setLastLogIndex(request.getLastLogIndex());
            raftNode.setLastLogTerm(request.getLastLogTerm());
            raftNode.setCommitIndex(request.getLeaderCommit());
            logService.cleanLogger();
        }
        return null;
    }

    //leader to follower (active)
    public void sendHeartBeat(Channel channel, int peerNodeId){
        RaftRequest request = new RaftRequest();
        request.setType("heartbeat");
        request.setId(raftNode.getId());
        request.setTerm(raftNode.getCurrentTerm());
        request.setLeaderHost(CLIServerHost);
        request.setLeaderPort(CLIServerPort);
        channel.writeAndFlush(JSON.toJSONString(request) + "\n");
        log.info("term {}, leader node {} send heartbeat to node {}", raftNode.getCurrentTerm(), raftNode.getId(), peerNodeId);
    }

    public void sendHeartBeatToAll(Map<Integer, Channel> peerChannels){
        log.info("term {}, leader node {} send heartbeat to all nodes", raftNode.getCurrentTerm(), raftNode.getId());
        for (Map.Entry<Integer, Channel> entry : peerChannels.entrySet()) {
            if(entry.getKey() != raftNode.getId()){ // 不发送心跳包给自己
                sendHeartBeat(entry.getValue(), entry.getKey());
            }
        }
    }

    //client to server（passive）
    public RaftRequest clientHandleAppendEntriesRequest(RaftRequest reply, Channel channel, int peerNodeId) {
        if(!reply.isSuccess()){
            if(reply.getTerm() > raftNode.getCurrentTerm()){
                raftNode.setCurrentTerm(reply.getTerm()+1);
            }
        }else{
            // handle commitable log
        }

        if(reply.getLastLogIndex() < raftNode.getLastLogIndex()){
            LogEntry nextLog = null;
            if(( nextLog = logService.containAndGetNextLog(reply.getLastLogTerm(), reply.getLastLogIndex())) != null){
                logService.replicateLog(reply, channel, nextLog);
            }else{
                logService.sendSnapshot(reply, channel);
            }
        }

        return null;

    }


    public RaftRequest handleAppendEntriesRequest(RaftRequest request) {
        RaftRequest reply = new RaftRequest();
        reply.setType("AppendEntries");
        reply.setId(raftNode.getId());
        reply.setTerm(raftNode.getCurrentTerm());
        log.info("server {} handle append entries request: {}", raftNode.getId(), JSON.toJSONString(request));
        if(request.getTerm() < raftNode.getCurrentTerm()){
            reply.setSuccess(false);
        }else{
            serverTimer.resetTimer();
            acceptLeader(request);
            if(logService.containLog(request.getPrevLogIndex(), request.getPrevLogTerm())){
                //prevLogIndex and prevLogTerm are correct, append log
                reply.setSuccess(true);
                logService.appendLog(request.getLog());
            }else{// does not contain prevlog, reject append entries request
                reply.setSuccess(false);
            }
        }
        reply.setLastLogIndex(raftNode.getLastLogIndex());
        reply.setLastLogTerm(raftNode.getLastLogTerm());
        return reply;

    }

    //follower to leader (passive)
    // for now leader needn't respond to follower's heartbeat response
    public RaftRequest handleHeartbeatReply(RaftRequest reply) {

        return null;

    }


    public void acceptHeartbeat(RaftRequest request){
        log.info("server {} accept heartbeat from leader node {}", raftNode.getId(), request.getId());
        raftNode.getIsCandidate().compareAndSet(true, false);
        raftNode.getIsLeader().compareAndSet(true, false); // 放弃leader身份
        raftNode.setCurrentTerm(request.getTerm());
        raftNode.setLeaderId(request.getId());   
        raftNode.setLeaderHost(request.getLeaderHost());
        raftNode.setLeaderPort(request.getLeaderPort());
    }

    public void acceptLeader(RaftRequest request){
        log.info("server {} accept leader from leader node {}", raftNode.getId(), request.getId());
        raftNode.getIsCandidate().compareAndSet(true, false);
        raftNode.getIsLeader().compareAndSet(true, false); // 放弃leader身份
        raftNode.setCurrentTerm(request.getTerm());
        raftNode.setLeaderId(request.getId());   
        raftNode.setLeaderHost(request.getLeaderHost());
        raftNode.setLeaderPort(request.getLeaderPort());
    }

    public RaftRequest serverHandleHeartbeatRequest(RaftRequest request) {
        // log.info("server {} handle heartbeat request: {}", raftNode.getId(), JSON.toJSONString(request));
        // if(request.getId() == raftNode.getLeaderId()){
        //     serverTimer.resetTimer();
        //     acceptHeartbeat(request);
        // }else{
        //     if(request.getTerm() >= raftNode.getCurrentTerm()){ 
        //     //收到更高term的心跳包，承认对方leader，放弃选举，更新自己term
        //         serverTimer.resetTimer();
        //         acceptHeartbeat(request);
        //     }
        // }

        if(request.getTerm() >= raftNode.getCurrentTerm()){ 
            //收到更高term或一样term的心跳包，承认对方leader，放弃选举，更新自己term
            serverTimer.resetTimer();
            acceptHeartbeat(request);
        }
        return null;
    }

}
