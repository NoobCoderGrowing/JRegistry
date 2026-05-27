package hawk.JRegistryCenter;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Autowired;
import hawk.JRegistryCenter.Services.Persist.PersistService;
import org.springframework.stereotype.Component;
import hawk.JRegistryCenter.Raft.RPC.Server.RaftServerManager;
import hawk.JRegistryCenter.Raft.RPC.Client.RaftClientManager;
import hawk.JRegistryCenter.Services.Timer.TimeoutService;

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
