package hawk.JRegistryCenter.Services;


import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import hawk.JRegistryCenter.Raft.RaftNode;
import org.springframework.beans.factory.annotation.Autowired;
import io.netty.channel.Channel;
import java.util.Map;
import hawk.JRegitstryCore.RPC.RaftRequest;
import lombok.extern.slf4j.Slf4j;
import hawk.JRegistryCenter.Raft.RPC.Server.RaftServerHandler;
import hawk.JRegistryCenter.Services.Timer.TimeoutService;

import org.springframework.beans.factory.annotation.Value;
import hawk.JRegitstryCore.Log.LogEntry;
import hawk.JRegistryCenter.Raft.Log.LogService;
import java.util.concurrent.ThreadPoolExecutor;
import hawk.JRegistryCenter.Services.Persist.PersistService;



@Slf4j
@Service
public class AppendEntriesService {

    @Autowired
    private RaftNode raftNode;

    // @Autowired
    // private FollowerElectionTimer followerElectionTimer;

    @Autowired
    private TimeoutService timeoutService;

    @Autowired
    private LogService logService;


    @Autowired
    private ThreadPoolExecutor writePool;

    @Value("${host}")
    private String CLIServerHost;
    @Value("${CLS.port}")
    private int CLIServerPort;

    @Value("${raft.auto-persist}")
    private boolean autoPersist;

    @Autowired
    private PersistService persistService;


    public void sendShakeHands(Channel channel, int peerNodeId){
        RaftRequest request = new RaftRequest();
        request.setType("shakeHand");
        request.setId(raftNode.getId());
        writePool.execute(() -> {
            channel.writeAndFlush(JSON.toJSONString(request) + "\n");
        });
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
            // followerElectionTimer.resetTimeout();
            timeoutService.resetTimeout();
            long oldTerm = raftNode.getCurrentTerm();
            raftNode.acceptLeader(request);
            if(request.getTerm() > oldTerm){
                // if(autoPersist){
                //     raftNode.persist();
                // }
            }
            // raftNode.setLsmTree(request.getSnapshot());
            // raftNode.setLastLogIndex(request.getLastLogIndex());
            // raftNode.setLastLogTerm(request.getLastLogTerm());
            raftNode.setCommitIndex(request.getLeaderCommit());
            logService.installLogger(request);
            persistService.manualPersist();
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
        writePool.execute(() -> {
            channel.writeAndFlush(JSON.toJSONString(request) + "\n");
        });
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



    public RaftRequest clientHandleAppendEntriesRequest(RaftRequest reply, Channel channel, int peerNodeId) {
        if (!raftNode.getIsLeader().get()) return null; // 如果当前节点退位，不处理请求
        if(!reply.isSuccess()){
            if(reply.getTerm() > raftNode.getCurrentTerm()){ // 收到更高term的回复，放弃leader身份，成为follower
                timeoutService.resetTimeout();
                raftNode.turn2Follower(reply);
                return null;
            }else{ //log miss match
                long prevlogIndex = reply.getPrevLogIndex();
                logService.nextIndexMap.put(peerNodeId, prevlogIndex);
                logService.replicateLog(peerNodeId, channel);      
            }
        }else{ // if success
            // handle commitable log
            logService.updateMatchIndex(reply);
            Long nextIndex = reply.getLastLogIndex() + 1;
            logService.nextIndexMap.put(peerNodeId, nextIndex);
            if(nextIndex <= logService.getLastLogIndex()){ // if last log not match
                logService.replicateLog(peerNodeId, channel);
            }
        }
        return null;
    }

    public RaftRequest handleAppendEntriesRequest(RaftRequest request) {
        RaftRequest reply = new RaftRequest();
        reply.setType("appendEntries");
        reply.setId(raftNode.getId());
        reply.setTerm(raftNode.getCurrentTerm());
        log.info("server {} handle append entries request: {}", raftNode.getId(), JSON.toJSONString(request));
        if(request.getTerm() < raftNode.getCurrentTerm()){
            reply.setSuccess(false);
        }else{
            timeoutService.resetTimeout();
            long oldTerm = raftNode.getCurrentTerm();
            raftNode.acceptLeader(request);
            if(request.getTerm() > oldTerm){
                if(autoPersist){
                    raftNode.persist();
                }
            }
            if(logService.containLog(request.getPrevLogTerm(), request.getPrevLogIndex())){
                //containLog already handle -1 case
                LogEntry currentLog = request.getLog();
                if(logService.containLog(currentLog.getTerm(), currentLog.getIndex())){
                    //log exist, do not append but return success
                    reply.setSuccess(true);
                    reply.setLastLogIndex(currentLog.getIndex());
                    reply.setLastLogTerm(currentLog.getTerm());
                    return reply;
                }
                //prevLogIndex and prevLogTerm are correct, append log
                reply.setSuccess(true);
                logService.deleteLogs(request.getPrevLogIndex());
                logService.appendLog(currentLog);
                if(autoPersist){
                    logService.persistEntry(currentLog);
                }
                reply.setLastLogIndex(currentLog.getIndex());
                reply.setLastLogTerm(currentLog.getTerm());
            }else{// does not contain prevlog, reject append entries request
                reply.setPrevLogIndex(request.getPrevLogIndex());
                // reply.setPrevLogIndex(request.getPrevLogIndex() - 1);
                reply.setSuccess(false);
            }
        }
        return reply;
    }


    public RaftRequest serverHandleHeartbeatRequest(RaftRequest request) {
        

        if(request.getTerm() >= raftNode.getCurrentTerm()){ 
            //收到更高term或一样term的心跳包，承认对方leader，更新自己term
            // followerElectionTimer.resetTimeout();
            timeoutService.resetTimeout();
            long oldTerm = raftNode.getCurrentTerm();
            raftNode.acceptHeartbeat(request);
            if(request.getTerm() > oldTerm){
                if(autoPersist){
                    raftNode.persist();
                }
            }
        }
        return null;
    }

}
