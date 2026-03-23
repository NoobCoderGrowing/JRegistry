package hawk.JRegistryCenter.Raft.RPC.Server.Services;


import org.springframework.stereotype.Service;
import hawk.JRegistryCenter.Raft.RPC.RPCReply;
import hawk.JRegistryCenter.Raft.RPC.RPCRequest;
import com.alibaba.fastjson.JSON;
import hawk.JRegistryCenter.Raft.RaftNode;
import org.springframework.beans.factory.annotation.Autowired;
import io.netty.channel.Channel;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AppendEntriesService {

    @Autowired
    private RaftNode raftNode;

    //leader to follower (active)
    public void sendHeartBeat(Channel channel, int id){
        RPCRequest request = new RPCRequest();
        request.setType("heartbeat");
        request.setId(id);
        request.setTerm(raftNode.getCurrentTerm());
        channel.writeAndFlush(JSON.toJSONString(request) + "\n");
    }

    public void sendHeartBeatToAll(Map<Integer, Channel> peerChannels){
        for (Map.Entry<Integer, Channel> entry : peerChannels.entrySet()) {
            sendHeartBeat(entry.getValue(), entry.getKey());
        }
    }

    //client to server（passive）
    public RPCReply clientHandleAppendEntriesRequest(RPCRequest request) {

        return null;

    }
    //client to server (passive)  
    public RPCReply handleAppendEntriesRequest(RPCRequest request) {

        return null;

    }

    //follower to leader (passive)
    // for now leader needn't respond to follower's heartbeat response
    public RPCRequest handleHeartbeatReply(RPCReply reply) {

        return null;

    }

    public RPCReply serverHandleHeartbeatRequest(RPCRequest request) {
        if(request.getTerm() >= raftNode.getCurrentTerm()){
            //收到更高term的心跳包，承认对方leader，放弃选举，更新自己term
            raftNode.getIsCandidate().compareAndSet(true, false);
            raftNode.setCurrentTerm(request.getTerm());
            raftNode.setLeaderId(request.getId());   
        }

        return null;
    }

}
