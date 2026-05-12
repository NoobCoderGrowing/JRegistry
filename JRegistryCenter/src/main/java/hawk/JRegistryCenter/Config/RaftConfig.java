package hawk.JRegistryCenter.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class RaftConfig {

    @Value("${raft.reconnect.threads:3}")
    private int reconnectThreads;

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

    // @Bean("reconnectGroup")
    // public EventLoopGroup reconnectGroup() {
    //     return new NioEventLoopGroup(reconnectThreads);
    // }

    @Bean("writePool")
    public ThreadPoolExecutor writePool() {
        return new ThreadPoolExecutor(3, 9, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
    }

}
