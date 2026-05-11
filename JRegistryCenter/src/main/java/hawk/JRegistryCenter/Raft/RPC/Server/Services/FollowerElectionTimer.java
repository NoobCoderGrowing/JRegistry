package hawk.JRegistryCenter.Raft.RPC.Server.Services;

/**
 * Follower 收到 AppendEntries / 心跳 / 快照时重置选举超时；由 {@link TimeoutService} 实现。
 * Append 侧只依赖此接口，避免与 RaftClientManager、Timeout 实现形成环。
 */
public interface FollowerElectionTimer {

    void resetTimeout();
}
