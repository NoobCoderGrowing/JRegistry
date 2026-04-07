package hawk.JRegistryCenter.Raft.RPC.Server;

import org.springframework.beans.factory.annotation.Autowired;
import javax.annotation.PostConstruct;
import hawk.JRegistryCenter.Raft.RPC.Server.Services.RequestVoteService;

import org.springframework.stereotype.Component;
import hawk.JRegistryCenter.Raft.RaftNode;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.PreDestroy;
import hawk.JRegistryCenter.Raft.RPC.Client.RaftClientManager;

@Slf4j
@Component
public class TimeoutLoop {

    @Autowired
    private RaftClientManager raftClientManager;

    @Autowired
    private RequestVoteService requestVoteService;

    @Autowired
    private Timer timer;

    @Autowired
    private RaftNode raftNode;

    private final AtomicBoolean running = new AtomicBoolean(true);

    private volatile Thread loopThread;

    @PostConstruct
    public void start(){ // 程序逻辑入口

        timer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                shutdown();
            } catch (Exception e) {
                log.error("timeout loop {} shutdown hook error", raftNode.getId(), e);
            }
        }, "timeout-loop-shutdown-hook"));

        loopThread = new Thread(this::runLoop, "timeout-loop-" + raftNode.getId());
        loopThread.setDaemon(true);
        loopThread.start();
    }


    private void runLoop() {
        while (running.get()) {
            try {
                timer.awaitTimerUp();
                if (!raftNode.getIsLeader().get()) {
                    requestVoteService.startElection(raftClientManager);
                }
                timer.resetTimer();
            } catch (InterruptedException e) {
                log.error("timeout loop {} interrupted", raftNode.getId(), e);
                break;
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        timer.stop();
        log.info("TimeoutLoop {} shutdown gracefully", raftNode.getId());
    }
}
