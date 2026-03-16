package hawk.JRegistryCenter.Raft.RPC.Server.Services;

import org.springframework.stereotype.Service;

import hawk.JRegistryCenter.Raft.RPC.RPCReply;
import hawk.JRegistryCenter.Raft.RPC.RPCRequest;

@Service
public class AppendEntriesService {

    //leader to follower
    public RPCRequest handleAppendEntriesReply(RPCReply reply) {

        return null;

    }
    //follower to leader
    public RPCReply handleAppendEntriesRequest(RPCRequest request) {

        return null;

    }

    //leader to follower
    public RPCRequest handleHeartbeatReply(RPCReply reply) {

        return null;

    }

    //follower to leader
    public RPCReply handleHeartbeatRequest(RPCRequest request) {

        return null;
    }
}
