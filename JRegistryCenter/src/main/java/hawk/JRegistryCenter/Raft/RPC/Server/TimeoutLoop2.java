package hawk.JRegistryCenter.Raft.RPC.Server;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.context.annotation.Configuration;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import io.netty.channel.EventLoopGroup;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import hawk.JRegistryCenter.Raft.RaftNode;
import hawk.JRegistryCenter.Raft.RPC.Client.RaftClientManager;
import hawk.JRegistryCenter.Raft.RPC.Server.Services.RequestVoteService;

@Configuration
@Data
@Slf4j
public class TimeoutLoop2 {


    @Autowired
    private EventLoopGroup singleGroup;

    @Autowired
    private RaftNode raftNode;

    @Autowired
    private RaftClientManager raftClientManager;

    @Autowired
    private RequestVoteService requestVoteService;

    @Autowired
    private hawk.JRegistryCenter.Raft.RPC.Server.Timer timer;

    private volatile ScheduledFuture<?> timeoutChecker;

    @PostConstruct
    public void timeout(){
        timer.start();
        timeoutChecker = singleGroup.scheduleWithFixedDelay(() -> {
            try {
                if (!timer.isTimerUp()) {
                    return;
                }
                if (!raftNode.getIsLeader().get()) {
                    requestVoteService.startElection(raftClientManager);
                }
                timer.resetTimer();
            } catch (Exception e) {
                log.error("timeout loop {} run error", raftNode.getId(), e);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        ScheduledFuture<?> checker = timeoutChecker;
        if (checker != null) {
            checker.cancel(false);
        }
        timer.stop();
        log.info("TimeoutLoop2 {} shutdown gracefully", raftNode.getId());
    }
}
