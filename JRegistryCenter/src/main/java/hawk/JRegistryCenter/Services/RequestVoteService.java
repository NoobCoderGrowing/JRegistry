package hawk.JRegistryCenter.Services;

// import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import java.util.Map;
import io.netty.channel.Channel;
import hawk.JRegitstryCore.Raft.RaftNode;
import hawk.JRegistryCenter.Raft.RPC.Client.RaftClientManager;
import hawk.JRegistryCenter.Services.Timer.TimeoutService;
import hawk.JRegitstryCore.RPC.RaftRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import hawk.JRegistryCenter.Raft.Log.LogService;
import hawk.JRegistryCenter.Services.Persist.PersistService;


@Slf4j
@Service
public class RequestVoteService {

    @Autowired
    private RaftNode raftNode;

    @Autowired
    private LogService logService;


    @Value("${host}")
    private String CLIServerHost;
    @Value("${CLS.port}")
    private int CLIServerPort;

    @Autowired
    private TimeoutService timeoutService;

    @Value("${raft.count}")
    private int nodeCount;

    @Value("${raft.auto-persist}")
    private boolean autoPersist;

    @Autowired
    private PersistService persistService;



    // new voting logic(compare log term and index to determine whether to accept)
    public RaftRequest serverHandleRequestVoteRequest(RaftRequest request) {
        if(request.getTerm() < raftNode.getCurrentTerm()){// term比自己小，拒绝投票
            return rejectVoteRequest(request);
        }else{ // term>=自己的，比较日志

            if(request.getTerm() > raftNode.getCurrentTerm()){
                raftNode.turn2Follower(request);
            }

            if(request.getLastLogTerm() < logService.getLastLogTerm()){ // 日志的term比自己旧，拒绝投票
                return rejectVoteRequest(request);
            }else{ // 日志的term>=自己的
                if(request.getLastLogTerm() == logService.getLastLogTerm()){  // 日志的term=自己，比较index
                    if(request.getLastLogIndex() < logService.getLastLogIndex()){ // 日志的index比自己旧，拒绝投票
                        return rejectVoteRequest(request);
                    }else{ // 日志的index>=自己的，接受投票
                        return acceptVoteRequest(request);
                    }
                }else{ // 日志的term比自己新，接受投票
                    return acceptVoteRequest(request);
                }
            }
        }
    }





    public RaftRequest rejectVoteRequest(RaftRequest request) {
        RaftRequest reply = new RaftRequest();
        reply.setType("requestVote");
        reply.setId(raftNode.getId());
        reply.setTerm(raftNode.getCurrentTerm());
        reply.setLastLogTerm(logService.getLastLogTerm());
        reply.setLastLogIndex(logService.getLastLogIndex());
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

    
    
    public RaftRequest acceptVoteRequest(RaftRequest request){  
        if(checkTermVoted(request.getTerm())){ // 当前term已经投过票了，拒绝投票
            return rejectVoteRequest(request);
        }
        timeoutService.resetTimeout();
        raftNode.setTermVoted(request.getTerm());
        RaftRequest reply = new RaftRequest();
        reply.setType("requestVote");
        reply.setId(raftNode.getId());
        reply.setTerm(raftNode.getCurrentTerm());
        reply.setVoteGranted(true);
        log.info("server {} granted vote to node {}", raftNode.getId(), request.getId());
        if(autoPersist){ // must persist before voting
            persistService.persistNode();
        }
        return reply;
    }

    public RaftRequest clientHandleRequestVoteRequest(RaftRequest reply, RaftClientManager raftClientManager) {
        // log.info("client node{} handle request vote request: {}", raftNode.getId(), JSON.toJSONString(request));
        if(reply.getTerm() > raftNode.getCurrentTerm()){ // 收到更高term的回复，放弃candidate身份，成为follower
            timeoutService.resetTimeout();
            raftNode.turn2Follower(reply);
            return null;
        }

        if(raftNode.getIsLeader().get()){ //already becom leader, no duplicate noOP
            return null;
        }

        if(reply.getTerm() == raftNode.getCurrentTerm() && 
        raftNode.getIsCandidate().get()){
            if(reply.isVoteGranted()){
                int voteReceived = raftNode.getVoteReceived().incrementAndGet();
                if(voteReceived > nodeCount/2){ // 获得多数票，成为leader
                    timeoutService.resetTimeout();
                    raftNode.turn2Leader();
                    log.info("term {} ,client node {} become leader, {} votes received, active nodes: {}", raftNode.getCurrentTerm(), raftNode.getId(), voteReceived, 
                    nodeCount);
                    //异步发送心跳包给所有节点（netty发送消息本身就是异步的）
                    // appendEntriesServiceProvider.getObject().sendHeartBeatToAll(raftClientManager.getPeerChannels());
                    logService.generateNoOpLog();
                }
            }
        }
        return null;
    }

    public void startElection(RaftClientManager raftClientManager){
        raftNode.turn2Candidate();
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
                request.setLastLogIndex(logService.getLastLogIndex());
                request.setLastLogTerm(logService.getLastLogTerm());
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
