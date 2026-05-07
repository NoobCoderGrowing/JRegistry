package hawk.JRegistryCenter.Raft.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Configuration
public class LockConfiguration {

    @Bean("electionLock")
    public ReentrantReadWriteLock electionLock() {
        return new ReentrantReadWriteLock();
    }

    @Bean("logLock")
    public ReentrantReadWriteLock logLock() {
        return new ReentrantReadWriteLock();
    }
    
}
