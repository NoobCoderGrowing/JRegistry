package hawk.JRegistryClient;
import hawk.JRegitstryCore.Pair;
import hawk.JRegitstryCore.RPC.RaftRequest;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.concurrent.CompletableFuture;
import com.alibaba.fastjson.JSON;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JRegistryClient {
    private NettyClient nettyClient;

    public JRegistryClient() {
        this.nettyClient = new NettyClient();
        this.nettyClient.connect();
    }

    public void connect(){
        nettyClient.connect();
    }

    public Pair<byte[], String> get(String key){
        RaftRequest request = new RaftRequest();
        request.setType("get");
        request.setKey(key);
        request.setUuid(UuidCreator.getTimeOrderedEpoch());
        CompletableFuture<RaftRequest> future = new CompletableFuture<>();
        nettyClient.setMessageListener(message -> {
            RaftRequest reply = JSON.parseObject(message, RaftRequest.class);
            if(reply == null){
                return;
            }
            if(reply.getUuid().equals(request.getUuid())){
                future.complete(reply);
            }
        });
        nettyClient.sendRequest(request);
        
        try {
            RaftRequest reply = future.get(100,TimeUnit.MILLISECONDS);
            if(reply.isSuccess()){
                return new Pair<byte[], String>(reply.getData(), reply.getDataType());
            }
            return null;
        } catch (Exception e) {
            log.error("get failed: {}", e.getMessage());
            return null;
        }
    }

    public void set(String key, byte[] data, String dataType){
        RaftRequest request = new RaftRequest();
        request.setType("set");
        request.setKey(key);
        request.setData(data);
        request.setDataType(dataType);
        request.setUuid(UuidCreator.getTimeOrderedEpoch());
        nettyClient.sendRequest(request);
    }

    public void delete(String key){
        RaftRequest request = new RaftRequest();
        request.setType("delete");
        request.setKey(key);
        request.setUuid(UuidCreator.getTimeOrderedEpoch());
        nettyClient.sendRequest(request);
    }

    

 
}
