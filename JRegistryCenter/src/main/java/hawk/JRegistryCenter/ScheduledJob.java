package hawk.JRegistryCenter;

import org.springframework.beans.factory.annotation.Autowired;
import io.netty.channel.EventLoopGroup;
import hawk.JRegistryCenter.Services.Persist.PersistService;
import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;


@Configuration
@Slf4j
public class ScheduledJob {

    @Autowired
    private EventLoopGroup singleGroup;

    @Autowired
    private PersistService persistService;

    @Value("${raft.log-compaction-interval:14400}")
    private int logCompactionInterval;


    @PostConstruct
    public void init(){
        singleGroup.scheduleAtFixedRate(() -> {
            log.info("log compaction start");
            persistService.logCompaction();
        }, 0, logCompactionInterval, TimeUnit.MINUTES);
    }
}
