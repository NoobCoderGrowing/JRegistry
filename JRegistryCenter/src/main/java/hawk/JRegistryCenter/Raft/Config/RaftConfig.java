package hawk.JRegistryCenter.Raft.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

@Configuration
public class RaftConfig {

    @Bean("electionLock")
    public ReentrantReadWriteLock electionLock() {
        return new ReentrantReadWriteLock();
    }

    @Bean("logLock")
    public ReentrantReadWriteLock logLock() {
        return new ReentrantReadWriteLock();
    }

    @Bean("singleGroup")
    public EventLoopGroup singleGroup() {
        return new NioEventLoopGroup(1);
    }
}
