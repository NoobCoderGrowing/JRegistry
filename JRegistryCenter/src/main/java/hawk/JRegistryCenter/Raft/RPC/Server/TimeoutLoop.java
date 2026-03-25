package hawk.JRegistryCenter.Raft.RPC.Server;

import org.springframework.beans.factory.annotation.Autowired;
import javax.annotation.PostConstruct;
import hawk.JRegistryCenter.Raft.RPC.Server.Services.RequestVoteService;

import org.springframework.stereotype.Component;
import hawk.JRegistryCenter.Raft.RaftNode;


@Component
public class TimeoutLoop {

  

    @Autowired
    private RequestVoteService requestVoteService;

    @Autowired
    private Timer timer;

    @Autowired
    private RaftNode raftNode;

    @PostConstruct
    public void start(){ // 程序逻辑入口

        timer.start();

        while(true){
            try{
                timer.awaitTimerUp();
                // 到点后：重置计时并发起选举
                timer.resetTimer();
                if(!raftNode.getIsLeader().get()
                ){
                    requestVoteService.startElection();
                }
                
            }catch(InterruptedException e){
                e.printStackTrace();
            }
        }
    }
    
}
