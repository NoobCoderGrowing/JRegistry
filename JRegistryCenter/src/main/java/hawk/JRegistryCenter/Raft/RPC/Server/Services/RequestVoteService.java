package hawk.JRegistryCenter.Raft.RPC.Server.Services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import hawk.JRegistryCenter.Raft.RPC.RPCReply;
import hawk.JRegistryCenter.Raft.RPC.RPCRequest;
import com.alibaba.fastjson.JSON;
import java.util.Map;
import io.netty.channel.Channel;
import hawk.JRegistryCenter.Raft.RaftNode;
import hawk.JRegistryCenter.Raft.RPC.Server.RaftServerManager;


@Service
public class RequestVoteService {

    @Autowired
    private AppendEntriesService appendEntriesService;

    @Autowired
    private RaftServerManager raftServer;

    @Autowired
    private RaftNode raftNode;

    //server to client, dertermine whether become leader here
    public RPCReply serverHandleRequestVoteRequest(RPCRequest request) {
        if(request.getVoteTerm() == raftNode.getCurrentTerm() && 
        raftNode.getVoting().get()){
            int voteReceived = raftNode.getVoteReceived().incrementAndGet();
            int activePeers = raftServer.getActivePeers();
            if(voteReceived > activePeers/2){ // 获得多数票，成为leader
                raftNode.setLeader(true);
                raftNode.setCandidate(false);
                raftNode.setLeaderId(raftNode.getId());
                raftNode.getVoting().set(false);
                //异步发送心跳包给所有节点（netty发送消息本身就是异步的）
                appendEntriesService.sendHeartBeatToAll(raftServer.getPeerChannels());
            }
        }
        return null;
    }

    public RPCReply rejectVote() {
        RPCReply reply = new RPCReply();
        reply.setType("requestVote");
        reply.setId(raftNode.getId());
        reply.setTerm(raftNode.getCurrentTerm());
        reply.setLastLogTerm(raftNode.getLastLogTerm());
        reply.setLastLogIndex(raftNode.getLastLogIndex());
        reply.setVoteGranted(false);    
        return reply;
    }

    public boolean checkTermVoted(RPCRequest request){
        if(raftNode.getTermVoted() == request.getTerm()){//当前term已经投过票了
            return true;
        }
        return false;
    }
    
    public RPCReply acceptVote(RPCRequest request){
        
        if(checkTermVoted(request)){ // 当前term已经投过票了，拒绝投票
            return rejectVote();
        }
        
        raftNode.setCurrentTerm(request.getTerm());
        raftNode.setTermVoted(request.getTerm());
        raftNode.setLeader(false);
        raftNode.setCandidate(false);
        raftNode.setVotedFor(request.getId());
        

        RPCReply reply = new RPCReply();
        reply.setType("requestVote");
        reply.setId(raftNode.getId());
        reply.setTerm(raftNode.getCurrentTerm());
        reply.setLastLogTerm(raftNode.getLastLogTerm());
        reply.setLastLogIndex(raftNode.getLastLogIndex());
        reply.setVoteGranted(true);
        return reply;
    }

    //client to server
    public RPCReply clientHandleRequestVoteRequest(RPCRequest request) {
        RPCReply reply = null;
        if(request.getTerm() < raftNode.getCurrentTerm()){// term比自己小，拒绝投票
            reply = rejectVote();
        }else if(request.getTerm() > raftNode.getCurrentTerm()){// term比自己大，接受投票
            reply = acceptVote(request);
        }else{ // term和自己一样，比较日志
            if(request.getLastLogTerm()<raftNode.getCurrentTerm()){// 日志的term比自己旧，拒绝投票
                reply = rejectVote();
            }else{ // 日志的term和自己一样或比自己大
                if(request.getLastLogTerm()>raftNode.getLastLogTerm()){// 日志的term比自己新，接受投票
                    reply = acceptVote(request);
                }else{ //日志的term和自己一样，比较index
                    if(request.getLastLogIndex() < raftNode.getLastLogIndex()){//日志的index比自己旧，拒绝投票
                        reply = rejectVote();
                    }else{ // 日志的index比自己新或一样新，接受投票
                        reply = acceptVote(request);
                    }
                }
            }
        }
        return reply;
    }

    public void startElection(){
        raftNode.getVoteReceived().set(0);
        raftNode.setCurrentTerm(raftNode.getCurrentTerm() + 1);
        raftNode.setLeader(false);
        raftNode.setCandidate(true);
        raftNode.setVotedFor(raftNode.getId());
        raftNode.getVoting().set(true);
        sendRequestVote();
    }

    public void sendRequestVote(){
        for (Map.Entry<Integer, Channel> entry : raftServer.getPeerChannels().entrySet()) {
            RPCRequest request = new RPCRequest();
            request.setType("requestVote");
            request.setTerm(raftNode.getCurrentTerm());
            request.setLastLogIndex(raftNode.getLastLogIndex());
            request.setLastLogTerm(raftNode.getLastLogTerm());
            raftServer.sendToPeer(entry.getKey(), JSON.toJSONString(request));
        }
    }


    public static void main(String[] args) {
        System.out.println(5/2);
    }

}
