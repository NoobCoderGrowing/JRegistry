package hawk.JRegistryCenter.Raft.RPC.Server.Services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import hawk.JRegistryCenter.Raft.RPC.RPCReply;
import hawk.JRegistryCenter.Raft.RPC.RPCRequest;
import com.alibaba.fastjson.JSON;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import io.netty.channel.Channel;
import hawk.JRegistryCenter.Raft.RaftNode;
import hawk.JRegistryCenter.Raft.RPC.Server.RaftServerManager;
import hawk.JRegistryCenter.Raft.RPC.Client.RaftClientManager;
import hawk.JRegistryCenter.Raft.RPC.Server.Timer;


@Service
public class RequestVoteService {

    @Autowired
    private AppendEntriesService appendEntriesService;

    @Autowired
    private RaftServerManager raftServerManager;

    @Autowired
    private RaftClientManager raftClientManager;

    @Autowired
    private RaftNode raftNode;

    private ReentrantLock voteLock = new ReentrantLock();

    @Autowired
    private Timer serverTimer;


   
// voting logic
    public RPCReply serverHandleRequestVoteRequest(RPCRequest request) {

        if(!serverTimer.isTimerUp()){
            //如果计时器没有超时，拒绝投票
            return rejectVote();
        }


        RPCReply reply = null;
        if(request.getTerm() < raftNode.getCurrentTerm()){// term比自己小，拒绝投票
            reply = rejectVote();
        }else if(request.getTerm() > raftNode.getCurrentTerm()){// term比自己大，接受投票
            reply = acceptVote(request);
        }else{ // term和自己一样，比较日志
            if(request.getLastLogTerm()<raftNode.getLastLogTerm()){// 日志的term比自己旧，拒绝投票
                reply = rejectVote();
            }else{ // 日志的term和自己一样或比自己新
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
        
        // return to candidate
        raftNode.getIsCandidate().compareAndSet(true, false);

        voteLock.lock(); // 加锁，防止并发投票

        try{
            if(checkTermVoted(request)){ // 当前term已经投过票了，拒绝投票
                return rejectVote();
            }

            raftNode.setTermVoted(request.getTerm());
        }finally{
            voteLock.unlock(); // 解锁
        }

        raftNode.setCurrentTerm(request.getTerm());
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
        if(request.getVoteTerm() == raftNode.getCurrentTerm() && 
        raftNode.getIsCandidate().get()){
            int voteReceived = raftNode.getVoteReceived().incrementAndGet();
            int activePeers = raftServerManager.getActivePeers();
            if(voteReceived > activePeers/2){ // 获得多数票，成为leader
                raftNode.getIsLeader().compareAndSet(false, true);
                raftNode.getIsCandidate().compareAndSet(true, false);
                raftNode.setLeaderId(raftNode.getId());
                //异步发送心跳包给所有节点（netty发送消息本身就是异步的）
                appendEntriesService.sendHeartBeatToAll(raftClientManager.getPeerChannels());
            }
        }
        return null;
    }

    public void startElection(){
        raftNode.getVoteReceived().set(0);
        raftNode.setCurrentTerm(raftNode.getCurrentTerm() + 1);
        raftNode.getIsCandidate().compareAndSet(false, true);
        raftNode.setVotedFor(raftNode.getId());
        raftNode.getVoteReceived().incrementAndGet();//自己投给自己
        sendRequestVote();
    }

    public void sendRequestVote(){
        for (Map.Entry<Integer, Channel> entry : raftClientManager.getPeerChannels().entrySet()) {
            if(raftNode.getIsCandidate().get()){
                RPCRequest request = new RPCRequest();
                request.setType("requestVote");
                request.setTerm(raftNode.getCurrentTerm());
                request.setLastLogIndex(raftNode.getLastLogIndex());
                request.setLastLogTerm(raftNode.getLastLogTerm());
                raftClientManager.sendToPeer(entry.getKey(), JSON.toJSONString(request));
            }
            else{ // 如果candidate身份被取消，退出选举
                return;
            }
        }
    }


    public static void main(String[] args) {
        System.out.println(5/2);
    }

}
