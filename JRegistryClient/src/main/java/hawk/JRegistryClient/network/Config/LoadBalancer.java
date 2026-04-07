package hawk.JRegistryClient.network.Config;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import javax.annotation.PostConstruct;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import lombok.Data;

@Configuration
@Data
public class ClientConfig {


    @Value("#{${raft.peers}}")
    private Map<Integer, String> raftNodes;

    private List<Map.Entry<Integer, String>> raftNodesList;

    private Random random = new Random();

    @PostConstruct
    public void init() {
        raftNodesList = new ArrayList<>(raftNodes.entrySet());
    }

    public String getRandomRaftNode() {
        return raftNodesList.get(random.nextInt(raftNodesList.size())).getValue();
    }

    
}
