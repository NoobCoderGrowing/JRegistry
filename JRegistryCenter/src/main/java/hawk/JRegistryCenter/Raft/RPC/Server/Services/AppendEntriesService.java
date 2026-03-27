package hawk.JRegistryCenter.Raft.RPC.Server.Services;


import org.springframework.stereotype.Service;
import hawk.JRegistryCenter.Raft.RPC.RPCReply;
import hawk.JRegistryCenter.Raft.RPC.RPCRequest;
import com.alibaba.fastjson.JSON;
import hawk.JRegistryCenter.Raft.RaftNode;
import org.springframework.beans.factory.annotation.Autowired;
import io.netty.channel.Channel;
import java.util.Map;
import hawk.JRegistryCenter.Raft.RPC.Server.Timer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AppendEntriesService {

    @Autowired
    private RaftNode raftNode;

    @Autowired
    private Timer serverTimer;

    //leader to follower (active)
    public void sendHeartBeat(Channel channel, int id){
        RPCRequest request = new RPCRequest();
        request.setType("heartbeat");
        request.setId(id);
        request.setTerm(raftNode.getCurrentTerm());
        channel.writeAndFlush(JSON.toJSONString(request) + "\n");
        log.info("leader node {} send heartbeat to node {}", raftNode.getId(), id);
    }

    public void sendHeartBeatToAll(Map<Integer, Channel> peerChannels){
        log.info("leader node {} send heartbeat to all nodes", raftNode.getId());
        for (Map.Entry<Integer, Channel> entry : peerChannels.entrySet()) {
            sendHeartBeat(entry.getValue(), entry.getKey());
        }
    }

    //client to server（passive）
    public RPCReply clientHandleAppendEntriesRequest(RPCRequest request) {

        return null;

    }


    public RPCReply handleAppendEntriesRequest(RPCRequest request) {
        log.info("server {} handle append entries request: {}", raftNode.getId(), JSON.toJSONString(request));
        if(request.getId() == raftNode.getLeaderId()){
            serverTimer.resetTimer();
        }

        return null;

    }

    //follower to leader (passive)
    // for now leader needn't respond to follower's heartbeat response
    public RPCRequest handleHeartbeatReply(RPCReply reply) {

        return null;

    }


    public void acceptHeartbeat(RPCRequest request){
        raftNode.getIsCandidate().compareAndSet(true, false);
        raftNode.setCurrentTerm(request.getTerm());
        raftNode.setLeaderId(request.getId());   
    }

    public RPCReply serverHandleHeartbeatRequest(RPCRequest request) {
        log.info("server {} handle heartbeat request: {}", raftNode.getId(), JSON.toJSONString(request));
        if(request.getId() == raftNode.getLeaderId()){
            serverTimer.resetTimer();
            acceptHeartbeat(request);
        }else{
            if(serverTimer.isTimerUp() && 
            request.getTerm() > raftNode.getCurrentTerm()){ 
            //收到更高term的心跳包，承认对方leader，放弃选举，更新自己term
                serverTimer.resetTimer();
                acceptHeartbeat(request);
            }
        }
        return null;
    }

}
