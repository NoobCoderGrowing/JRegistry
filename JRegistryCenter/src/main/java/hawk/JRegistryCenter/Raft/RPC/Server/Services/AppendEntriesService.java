package hawk.JRegistryCenter.Raft.RPC.Server.Services;


import org.springframework.stereotype.Service;
import hawk.JRegistryCenter.Raft.RPC.RPCReply;
import hawk.JRegistryCenter.Raft.RPC.RPCRequest;
import com.alibaba.fastjson.JSON;
import hawk.JRegistryCenter.Raft.RaftNode;
import org.springframework.beans.factory.annotation.Autowired;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.Channel;

@Service
public class AppendEntriesService {

    @Autowired
    private RaftNode raftNode;

    //leader to follower (active)
    public void sendHeartBeat(Channel channel, int id){
        RPCRequest request = new RPCRequest();
        request.setType("heartbeat");
        request.setId(id);
        channel.writeAndFlush(JSON.toJSONString(request) + "\n");
    }

    //server to client （passive）
    public RPCRequest handleAppendEntriesReply(RPCReply reply) {

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

    //follower to leader (passive)
    public RPCReply handleHeartbeatRequest() {
        RPCReply reply = new RPCReply();
        reply.setType("heartbeat");
        reply.setId(raftNode.getId());
        reply.setLeader(raftNode.isLeader());
        return reply;
    }

}
