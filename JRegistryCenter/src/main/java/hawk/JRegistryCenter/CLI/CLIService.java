package hawk.JRegistryCenter.CLI;

import io.netty.channel.Channel;
import hawk.JRegitstryCore.RPC.CLIRequest;
import org.springframework.stereotype.Service;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import hawk.JRegistryCenter.Raft.RaftNode;
import org.springframework.beans.factory.annotation.Autowired;


@Service
@Data
@Slf4j
public class CLIService {

    @Autowired
    private RaftNode raftNode;


    public void handleCLIRequest(Channel channel, CLIRequest cliRequest){
        switch (cliRequest.getType()) {
            case "get":
                channel.writeAndFlush("ACK: " + cliRequest.getKey() + "\n");
                break;
            case "set":
                chekcIsLeader(channel, cliRequest);
                break;
            case "delete":
                chekcIsLeader(channel, cliRequest);
                break;
            default:
                channel.writeAndFlush("invalid cmd\n");
                break;
        }
    }

    public void chekcIsLeader(Channel channel, CLIRequest cliRequest){
        if(raftNode.getIsLeader().get()){
            CLIRequest request = new CLIRequest();
            request.setType("redirect");
        }else{
            channel.writeAndFlush("You are not the leader\n");
        }

    }
}
