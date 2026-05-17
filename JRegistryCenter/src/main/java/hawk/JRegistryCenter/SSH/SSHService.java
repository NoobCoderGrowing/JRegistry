package hawk.JRegistryCenter.SSH;

import org.springframework.stereotype.Service;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import hawk.JRegistryCenter.Raft.RaftNode;
import org.springframework.beans.factory.annotation.Autowired;
import com.alibaba.fastjson.JSON;
import hawk.JRegistryCenter.Raft.Log.LogService;

import hawk.JRegitstryCore.BPlusNode;
import hawk.JRegitstryCore.Pair;
import io.netty.channel.EventLoopGroup;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import hawk.JRegistryCenter.Raft.RPC.Client.RaftClientManager;
import hawk.JRegitstryCore.RPC.RaftRequest;
import hawk.JRegitstryCore.RPC.SSH.SSHRequest;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Data
@Slf4j
public class SSHService {

    @Autowired
    private RaftNode raftNode;

    @Autowired
    private LogService logService;

    @Autowired
    private EventLoopGroup singleGroup;

    @Autowired
    private RaftClientManager raftClientManager;

    

    // public void handleGetRequest(Channel channel, CLIRequest cliRequest){
    //     String key = cliRequest.getKey();
    //     Pair<String, byte[]> value = raftNode.getLsmTree().get(key);
    //     CLIRequest reply = new CLIRequest();         
    //     reply.setUuid(cliRequest.getUuid());
    //     if(value != null){
    //         reply.setDataType(value.getLeft());
    //         reply.setData(value.getRight());
    //     }else{
    //         reply.setSuccess(false);
    //     }
    //     writePool.execute(() -> {
    //         channel.writeAndFlush(JSON.toJSONString(reply) + "\n");
    //     });

    // }

    public String handleGetRequest( SSHRequest cliRequest, AtomicReference<BPlusNode> sessionCurrent){
        String key = cliRequest.getKey();
        if(key == null || key.isEmpty()){ // if the key is empty, return the current node's kv pair
            BPlusNode current = sessionCurrent.get();
            if(current.getKey().equals("root")){
                return "root contains no kv pair";
            }else{
                Pair<String, byte[]> result = current.get();
                return JSON.toJSONString(result);
            }
        }

        Pair<String, byte[]> result = raftNode.getLsmTree().get(key);
        if(result != null){
            return JSON.toJSONString(result);
        }else{
            return "invalid key";
        }
    }



    public String handleSSHRequest( SSHRequest cliRequest, AtomicReference<BPlusNode> sessionCurrent){
        String response = null;
        log.info("node {} handle CLI request: {}", raftNode.getId(), JSON.toJSONString(cliRequest));
        switch (cliRequest.getType()) {
            case "get":
                response = handleGetRequest(cliRequest, sessionCurrent);
                break;
            case "set":
                response = chekcIsLeader(cliRequest);
                break;
            case "delete":
                response = chekcIsLeader(cliRequest);
                break;
            case "cd":
                response = handleCD(cliRequest, sessionCurrent);
                break;
            case "pwd":
                response = handlePwd(cliRequest, sessionCurrent);
                break;
            case "ls":
                response = handleLs(cliRequest, sessionCurrent);
                break;
            default:
                response = "invalid cmd";
                break;
        }
        return response;
    }

    public String handleLs(SSHRequest cliRequest, AtomicReference<BPlusNode> sessionCurrent){
        Set<String> keys = sessionCurrent.get().ls();
        String nodeStrings = String.join("  ", keys);
        return nodeStrings;
    }

    public String handlePwd(SSHRequest cliRequest, AtomicReference<BPlusNode> sessionCurrent){
        return sessionCurrent.get().pwd();
    }

    public String handleCD(SSHRequest cliRequest, AtomicReference<BPlusNode> sessionCurrent){
        BPlusNode temp = raftNode.getLsmTree().cd(cliRequest.getKey(), sessionCurrent.get());
        if(temp != null){
            sessionCurrent.set(temp);
            // return ">"+sessionCurrent.get().pwd();
            return "";
        }else{
            return "invalid path";
        }
    }

    public String handleWriteRequest(SSHRequest cliRequest){
        String cmd = cliRequest.getType();
        String message = cmd + " received";
        logService.generateLogEntry(cliRequest);
        return message;
    }

    public String redirectCMD2Leader(SSHRequest cliRequest){
        String cmd = cliRequest.getType();
        String message = cmd + " received";
        if(raftNode.getLeaderHost()==null|| raftNode.getLeaderHost().isEmpty()){
            message = cmd + " failed, no leader found";
            return message;
        }

        RaftRequest raftRequest = new RaftRequest();
        raftRequest.setType("writeRequest");
        raftRequest.setCmd(cmd);
        raftRequest.setKey(cliRequest.getKey());
        raftRequest.setData(cliRequest.getData());
        raftRequest.setDataType(cliRequest.getDataType());
        raftRequest.setUuid(cliRequest.getUuid());
        log.info("node {} redirect write requestt leader {}", raftNode.getId(), cmd, raftNode.getLeaderId());
        raftClientManager.sendToPeer(raftNode.getLeaderId(), JSON.toJSONString(raftRequest));
        return message;
    }

    // public void chekcIsLeader(Channel channel, CLIRequest cliRequest){
    //     if(raftNode.getIsLeader().get()){
    //         log.info("node {} is leader, handle write request", raftNode.getId());
    //         handleWriteRequest(channel, cliRequest);
    //     }else{
    //         log.info("node {} is not leader, redirect to leader", raftNode.getId());
    //         redirectToLeader(channel, cliRequest);
    //     }
    // }

    public String chekcIsLeader(SSHRequest cliRequest){
        if(raftNode.getIsLeader().get()){
            log.info("node {} is leader, handle write request", raftNode.getId());
            return handleWriteRequest(cliRequest);
        }else{
            log.info("node {} is not leader, redirect to leader", raftNode.getId());
            return redirectCMD2Leader(cliRequest);
        }
    }


    public String userInputCheck(String input, AtomicReference<BPlusNode> sessionCurrent){
        if (input.isEmpty()) {
            log.info("user input is empty");
            return "invalid cmd";
        }
        if(!input.matches("[A-Za-z0-9.~/ ]+")){
            log.info("user input is empty");
            return "invalid cmd";
        }

        String[] cmd = input.split(" ");
        if(cmd.length != 3 && cmd.length != 2 && cmd.length != 1){
            log.info("user input is empty");
            return "invalid cmd";
        }
        if(!cmd[0].equals("show")&&!cmd[0].equals("set")&&!cmd[0].equals("delete")&&!cmd[0].equals("ls")
            &&!cmd[0].equals("pwd")&&!cmd[0].equals("cd")&&!cmd[0].equals("get")){
            log.info("invalid cmd");
            return "invalid cmd";
        }
        SSHRequest cliRequest = new SSHRequest();
        if(cmd.length == 3){ // set 
            cliRequest.setType(cmd[0]);
            cliRequest.setKey(cmd[1]);
            cliRequest.setData(cmd[2].getBytes());
            cliRequest.setDataType("string");
        }
        if(cmd.length == 2){ //  delete / cd / get
            cliRequest.setType(cmd[0]);
            cliRequest.setKey(cmd[1]);
        }

        if(cmd.length == 1){ // ls / pwd / show
            cliRequest.setType(cmd[0]);
        }
        
        
        log.info("userInputCheck before sendRequest: requestId={}", cliRequest.getUuid());
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeAsync(() -> {
            return handleSSHRequest(cliRequest, sessionCurrent);
        },singleGroup);
        // String response = handleCLIRequest(cliRequest, sessionCurrent);
        String response = null;
        try {
            response = future.get(3, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            log.error("userInputCheck error: {}", e.getMessage());
            return "ssh server error";
        }catch (TimeoutException e) {
            log.error("userInputCheck timeout: {}", e.getMessage());
            return "ssh server timeout";
        }
        
        log.info("userInputCheck after sendRequest: requestId={}, response={}", cliRequest.getUuid(), response);

        return response;
    }
}
