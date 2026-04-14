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
        response.setRedirect(false);
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

    public void handleWriteRequest(Channel channel, CLIRequest cliRequest){
        String cmd = cliRequest.getType();
        String message = cmd + " completed";
        writeResponse(channel, cliRequest, message);
    }

    public void redirectToLeader(Channel channel, CLIRequest cliRequest){
        String message;
        String cmd = cliRequest.getType();
        if(raftNode.getLeaderHost()==null|| raftNode.getLeaderHost().isEmpty()){
            message = cmd + " failed, no leader found";
            writeResponse(channel, cliRequest, message);
            return;
        }
        CLIRequest response = new CLIRequest();
        response.setUuid(cliRequest.getUuid());
        response.setRedirect(true);
        response.setLeaderHost(raftNode.getLeaderHost());
        response.setLeaderPort(raftNode.getLeaderPort());
        message = cmd + " redirect to leader " + raftNode.getLeaderHost() + ":" + raftNode.getLeaderPort();
        response.setMessage(message);
        channel.writeAndFlush(JSON.toJSONString(response) + "\n");
    }

    public void chekcIsLeader(Channel channel, CLIRequest cliRequest){
        if(raftNode.getIsLeader().get()){
            handleWriteRequest(channel, cliRequest);
        }else{
            redirectToLeader(channel, cliRequest);
        }

    }
}
