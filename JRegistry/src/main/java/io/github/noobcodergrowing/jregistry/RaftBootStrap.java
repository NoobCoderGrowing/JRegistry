package io.github.noobcodergrowing.jregistry;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Autowired;
import io.github.noobcodergrowing.jregistry.Services.Persist.PersistService;
import org.springframework.stereotype.Component;
import io.github.noobcodergrowing.jregistry.Raft.RPC.Server.RaftServerManager;
import io.github.noobcodergrowing.jregistry.Raft.RPC.Client.RaftClientManager;
import io.github.noobcodergrowing.jregistry.Services.ClientSessionService;
import io.github.noobcodergrowing.jregistry.Services.WriteAckService;
import io.github.noobcodergrowing.jregistry.Services.Timer.TimeoutService;

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

    @Autowired
    private ClientSessionService clientSessionService;

    @Autowired
    private WriteAckService writeAckService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        persistService.autoRecovery();
        raftServerManager.start();
        raftClientManager.start();
        clientSessionService.start();
        writeAckService.start();
        timeoutService.timeoutStart();
    }
}
