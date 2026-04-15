package hawk.JRegistryCenter.Raft.RPC.Server.Services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import io.netty.channel.Channel;
import hawk.JRegistryCenter.Raft.RaftNode;
import hawk.JRegistryCenter.Raft.RPC.Client.RaftClientManager;
import hawk.JRegistryCenter.Raft.RPC.Server.Timer;
import hawk.JRegitstryCore.RPC.RaftReply;
import hawk.JRegitstryCore.RPC.RaftRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;


@Slf4j
@Service
public class RequestVoteService {

    @Autowired
    private AppendEntriesService appendEntriesService;

    @Autowired
    private RaftNode raftNode;

    private ReentrantLock voteLock = new ReentrantLock();

    @Autowired
    private Timer serverTimer;


    @Value("${host}")
    private String CLIServerHost;
    @Value("${CLS.port}")
    private int CLIServerPort;


   
// voting logic
    public RaftReply serverHandleRequestVoteRequest1(RaftRequest request) {

        if(!serverTimer.isTimerUp()){
            //如果计时器没有超时，拒绝投票
            return rejectVote(request);
        }


        RaftReply reply = null;
        if(request.getTerm() < raftNode.getCurrentTerm()){// term比自己小，拒绝投票
            reply = rejectVote(request);
        }else if(request.getTerm() > raftNode.getCurrentTerm()){// term比自己大，接受投票
            reply = acceptVote(request);
        }else{ // term和自己一样，比较日志
            if(request.getLastLogTerm()<raftNode.getLastLogTerm()){// 日志的term比自己旧，拒绝投票
                reply = rejectVote(request);
            }else{ // 日志的term和自己一样或比自己新
                if(request.getLastLogTerm()>raftNode.getLastLogTerm()){// 日志的term比自己新，接受投票
                    reply = acceptVote(request);
                }else{ //日志的term和自己一样，比较index
                    if(request.getLastLogIndex() < raftNode.getLastLogIndex()){//日志的index比自己旧，拒绝投票
                        reply = rejectVote(request);
                    }else{ // 日志的index比自己新或一样新，接受投票
                        reply = acceptVote(request);
                    }
                }
            }
        }
        return reply;
    }

    // new voting logic(compare log term and index to determine whether to accept)
    public RaftReply serverHandleRequestVoteRequest(RaftRequest request) {

        // if(!serverTimer.isTimerUp() && raftNode.getLeaderId() != -1){ 
        //     //如果计时器没有超时，且有leader，拒绝投票
        //     return rejectVote(request);
        // }


        RaftReply reply = null;
        if(request.getTerm() < raftNode.getCurrentTerm()){// term比自己小，拒绝投票
            reply = rejectVote(request);
        }else{ // term比自己大，比较日志
            if(request.getLastLogTerm() < raftNode.getLastLogTerm()){
                // 日志的term比自己小，拒绝投票
                reply = rejectVote(request);
            }else{ // 日志的term和自己一样或比自己新
                if(request.getLastLogTerm() > raftNode.getLastLogTerm()){
                    // 日志的term比自己新，接受投票
                    reply = acceptVote(request);
                }else{ // 日志的term和自己一样，比较index
                    if(request.getLastLogIndex() < raftNode.getLastLogIndex()){
                        // 日志的index比自己旧，拒绝投票
                        reply = rejectVote(request);
                    }else{ // 日志的index比自己新或一样新，接受投票
                        reply = acceptVote(request);
                    }
                }   
            }
        }
        return reply;
    }





    public RaftReply rejectVote(RaftRequest request) {
        RaftReply reply = new RaftReply();
        reply.setType("requestVote");
        reply.setId(raftNode.getId());
        reply.setTerm(raftNode.getCurrentTerm());
        reply.setLastLogTerm(raftNode.getLastLogTerm());
        reply.setLastLogIndex(raftNode.getLastLogIndex());
        reply.setVoteGranted(false);    
        log.info("server {} reject vote for node {}， node info {}, request info {}", raftNode.getId(), request.getId(), JSON.toJSONString(raftNode), JSON.toJSONString(request));
        return reply;
    }

    public boolean checkTermVoted(long requestTerm){
        if(raftNode.getTermVoted() >= requestTerm){//当前term已经投过票了
            return true;
        }
        return false;
    }

    
    
    public RaftReply acceptVote(RaftRequest request){
        
        // return to candidate
        raftNode.getIsCandidate().compareAndSet(true, false);

        voteLock.lock(); // 加锁，防止并发投票

        try{
            if(checkTermVoted(request.getTerm())){ // 当前term已经投过票了，拒绝投票
                return rejectVote(request);
            }

            raftNode.setTermVoted(request.getTerm());
        }finally{
            voteLock.unlock(); // 解锁
        }

        raftNode.setCurrentTerm(request.getTerm());
        

        RaftReply reply = new RaftReply();
        reply.setType("requestVote");
        reply.setId(raftNode.getId());
        reply.setVoteTerm(request.getTerm());
        reply.setTerm(raftNode.getCurrentTerm());
        reply.setLastLogTerm(raftNode.getLastLogTerm());
        reply.setLastLogIndex(raftNode.getLastLogIndex());
        reply.setVoteGranted(true);
        log.info("server {} granted vote for node {}", raftNode.getId(), request.getId());
        return reply;
    }

    //client to server
    public RaftRequest clientHandleRequestVoteRequest(RaftReply reply, RaftClientManager raftClientManager) {
        // log.info("client node{} handle request vote request: {}", raftNode.getId(), JSON.toJSONString(request));
        if(reply.getVoteTerm() == raftNode.getCurrentTerm() && 
        raftNode.getIsCandidate().get()){
            int voteReceived = raftNode.getVoteReceived().get();
            if(reply.isVoteGranted()){
                voteReceived = raftNode.getVoteReceived().incrementAndGet();
            }
            int activeNodes = raftClientManager.getActivePeers() + 1;
            if(voteReceived > activeNodes/2){ // 获得多数票，成为leader
                raftNode.getIsLeader().compareAndSet(false, true);
                raftNode.getIsCandidate().compareAndSet(true, false);
                raftNode.setLeaderId(raftNode.getId());
                raftNode.setLeaderHost(CLIServerHost);
                raftNode.setLeaderPort(CLIServerPort);
                log.info("term {} ,client node {} become leader, {} votes received, active nodes: {}", raftNode.getCurrentTerm(), raftNode.getId(), voteReceived, activeNodes);
                //异步发送心跳包给所有节点（netty发送消息本身就是异步的）
                appendEntriesService.sendHeartBeatToAll(raftClientManager.getPeerChannels());
            }
        }
        return null;
    }

    public void startElection(RaftClientManager raftClientManager){
        raftNode.getVoteReceived().set(0);
        raftNode.setCurrentTerm(raftNode.getCurrentTerm() + 1);
        voteLock.lock();
        try{
            if(checkTermVoted(raftNode.getCurrentTerm())){ // 当前term已经投过票了，拒绝投票
                return;
            }
            raftNode.setTermVoted(raftNode.getCurrentTerm());
            raftNode.getIsCandidate().compareAndSet(false, true);
            raftNode.getVoteReceived().incrementAndGet();//自己投给自己
        }finally{
            voteLock.unlock(); // 解锁
        }
        log.info("node {} timeout, start election term {}", raftNode.getId(), raftNode.getCurrentTerm());
        sendRequestVote(raftClientManager);
    }

    public void sendRequestVote(RaftClientManager raftClientManager){
        for (Map.Entry<Integer, Channel> entry : raftClientManager.getPeerChannels().entrySet()) {
            if(raftNode.getIsCandidate().get()){
                RaftRequest request = new RaftRequest();
                request.setId(raftNode.getId());
                request.setType("requestVote");
                request.setTerm(raftNode.getCurrentTerm());
                request.setLastLogIndex(raftNode.getLastLogIndex());
                request.setLastLogTerm(raftNode.getLastLogTerm());
                raftClientManager.sendToPeer(entry.getKey(), JSON.toJSONString(request));
                log.info("Candidate {} send request vote to node {}", raftNode.getId(), entry.getKey());
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
