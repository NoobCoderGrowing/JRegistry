package hawk.JRegistryCenter.Raft.RPC.Server.Services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import hawk.JRegistryCenter.Raft.RPC.RPCReply;
import hawk.JRegistryCenter.Raft.RPC.RPCRequest;
import hawk.JRegistryCenter.Raft.RPC.Client.RaftClientManager;
import com.alibaba.fastjson.JSON;
import java.util.Map;
import io.netty.channel.Channel;
import hawk.JRegistryCenter.Raft.RaftNode;


@Service
public class RequestVoteService {

    @Autowired
    private RaftClientManager raftClientManager;

    @Autowired
    private RaftNode raftNode;
    //server to client
    public RPCRequest handleRequestVoteReply(RPCReply reply) {

        return null;

    }

    //client to server
    public RPCReply handleRequestVoteRequest(RPCRequest request) {

        return null;

    }

    public void sendRequestVote(){
        for (Map.Entry<Integer, Channel> entry : raftClientManager.getPeerChannels().entrySet()) {
            RPCRequest request = new RPCRequest();
            request.setType("requestVote");
            request.setTerm(raftNode.getCurrentTerm());
            request.setLastLogIndex(raftNode.getLastLogIndex());
            request.setLastLogTerm(raftNode.getLastLogTerm());
            raftClientManager.sendToPeer(entry.getKey(), JSON.toJSONString(request));
        }
    }

}
