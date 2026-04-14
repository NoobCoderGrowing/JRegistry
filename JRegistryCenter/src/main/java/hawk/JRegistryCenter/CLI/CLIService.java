package hawk.JRegistryCenter.CLI;

import io.netty.channel.Channel;
import hawk.JRegitstryCore.RPC.CLIRequest;
import org.springframework.stereotype.Service;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import hawk.JRegistryCenter.Raft.RaftNode;
import org.springframework.beans.factory.annotation.Autowired;
import com.alibaba.fastjson.JSON;


@Service
@Data
@Slf4j
public class CLIService {

    @Autowired
    private RaftNode raftNode;

    private void writeResponse(Channel channel, CLIRequest request, String message) {
        CLIRequest response = new CLIRequest();
        response.setUuid(request.getUuid());
        response.setMessage(message);
        channel.writeAndFlush(JSON.toJSONString(response) + "\n");
    }


    public void handleCLIRequest(Channel channel, CLIRequest cliRequest){
        switch (cliRequest.getType()) {
            case "get":
                writeResponse(channel, cliRequest, "ACK: " + cliRequest.getKey());
                break;
            case "set":
                chekcIsLeader(channel, cliRequest);
                break;
            case "delete":
                chekcIsLeader(channel, cliRequest);
                break;
            default:
                writeResponse(channel, cliRequest, "invalid cmd");
                break;
        }
    }

    public void chekcIsLeader(Channel channel, CLIRequest cliRequest){
        if(raftNode.getIsLeader().get()){
            CLIRequest request = new CLIRequest();
            request.setType("redirect");
            writeResponse(channel, cliRequest, "leader accepted");
        }else{
            writeResponse(channel, cliRequest, "You are not the leader");
        }

    }
}
