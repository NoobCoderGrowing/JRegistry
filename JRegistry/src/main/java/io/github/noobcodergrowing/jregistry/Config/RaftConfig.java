package io.github.noobcodergrowing.jregistry.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import io.github.noobcodergrowing.jregistrycore.Raft.RaftNode;
import io.github.noobcodergrowing.jregistrycore.StateMachine;

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


    @Bean("writePool")
    public ThreadPoolExecutor writePool() {
        return new ThreadPoolExecutor(3, 9, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
    }

    @Bean("persistThread")
    public ThreadPoolExecutor persistThread() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
    }

    @Bean("raftNode")
    public RaftNode raftNode() {
        return new RaftNode();
    }

    @Bean("stateMachine")
    public StateMachine stateMachine() {
        return new StateMachine();
    }

}
