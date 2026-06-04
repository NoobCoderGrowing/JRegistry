package hawk.JRegistry;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Autowired;
import hawk.JRegistry.Services.Persist.PersistService;
import org.springframework.stereotype.Component;
import hawk.JRegistry.Raft.RPC.Server.RaftServerManager;
import hawk.JRegistry.Raft.RPC.Client.RaftClientManager;
import hawk.JRegistry.Services.Timer.TimeoutService;

@Component
public class RaftBootStrap implements ApplicationRunner{

    @Autowired
    private PersistService persistService;

    @Autowired
    private RaftServerManager raftServerManager;

    @Autowired
    private RaftClientManager raftClientManager;

    @Autowired
    private TimeoutService timeoutService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        persistService.autoRecovery();
        raftServerManager.start();
        raftClientManager.start();
        timeoutService.timeoutStart();
    }
}
