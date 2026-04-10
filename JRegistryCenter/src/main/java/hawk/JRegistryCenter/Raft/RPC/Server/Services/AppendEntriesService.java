package hawk.JRegistryCenter.Raft.RPC.Server.Services;


import org.springframework.stereotype.Service;
import hawk.JRegistryCenter.Raft.RPC.RaftReply;
import hawk.JRegistryCenter.Raft.RPC.RaftRequest;
import com.alibaba.fastjson.JSON;
import hawk.JRegistryCenter.Raft.RaftNode;
import org.springframework.beans.factory.annotation.Autowired;
import io.netty.channel.Channel;
import java.util.Map;
import hawk.JRegistryCenter.Raft.RPC.Server.Timer;
import lombok.extern.slf4j.Slf4j;
import hawk.JRegistryCenter.Raft.RPC.Server.RaftServerHandler;


@Slf4j
@Service
public class AppendEntriesService {

    @Autowired
    private RaftNode raftNode;

    @Autowired
    private Timer serverTimer;


    public void sendShakeHands(Channel channel, int peerNodeId){
        RaftRequest request = new RaftRequest();
        request.setType("shakeHand");
        request.setId(raftNode.getId());
        channel.writeAndFlush(JSON.toJSONString(request) + "\n");
        log.info("node {} send shake hand request to node {}", raftNode.getId(), peerNodeId);
        
    }

    public RaftReply handleShakeHandsRequest(RaftRequest request, RaftServerHandler raftServerHandler, Channel channel){
        raftServerHandler.setPeerNodeId(request.getId());
        raftServerHandler.getRaftServer().getPeerChannels().put(request.getId(), channel);
        return null;
    }

    //leader to follower (active)
    public void sendHeartBeat(Channel channel, int peerNodeId){
        RaftRequest request = new RaftRequest();
        request.setType("heartbeat");
        request.setId(raftNode.getId());
        request.setTerm(raftNode.getCurrentTerm());
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
    public RaftRequest clientHandleAppendEntriesRequest(RaftReply reply) {

        return null;

    }


    public RaftReply handleAppendEntriesRequest(RaftRequest request) {
        log.info("server {} handle append entries request: {}", raftNode.getId(), JSON.toJSONString(request));
        if(request.getId() == raftNode.getLeaderId()){
            serverTimer.resetTimer();
        }

        return null;

    }

    //follower to leader (passive)
    // for now leader needn't respond to follower's heartbeat response
    public RaftRequest handleHeartbeatReply(RaftReply reply) {

        return null;

    }


    public void acceptHeartbeat(RaftRequest request){
        log.info("server {} accept heartbeat from leader node {}", raftNode.getId(), request.getId());
        raftNode.getIsCandidate().compareAndSet(true, false);
        raftNode.getIsLeader().compareAndSet(true, false); // 放弃leader身份
        raftNode.setCurrentTerm(request.getTerm());
        raftNode.setLeaderId(request.getId());   
    }

    public RaftReply serverHandleHeartbeatRequest(RaftRequest request) {
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
