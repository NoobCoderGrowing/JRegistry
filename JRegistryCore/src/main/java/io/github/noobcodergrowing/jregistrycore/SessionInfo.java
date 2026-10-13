package io.github.noobcodergrowing.jregistrycore;

import lombok.Data;

/**
 * 一个 clientId 当前归属的租约。随 {@link StateMachine} 一起被 JSON 序列化，
 * 因此会随 Raft 日志、快照和本地持久化在全集群传播。
 *
 * <p>{@code ownerNodeId} 是当前持有该 client 连接的节点；{@code epoch} 是该归属的任期号，
 * 每次 {@code clientAttach} 自增。epoch 是守卫的承重字段：同一个节点可以在不同任期里
 * 两次成为 owner，只有 epoch 能区分「上一任的我」和「这一任的我」。
 */
@Data
public class SessionInfo {

    private int ownerNodeId;

    private long epoch;

    /**
     * 二值状态：{@code true} = 离线，{@code false} = 生效。
     *
     * <p>客户端心跳超时 / 连接断开 / owner 节点失效时只翻这个标志，<b>不再删除</b>它 set 过的 key；
     * 客户端重新连上（{@code clientAttach}）会把它翻回生效。
     */
    private boolean offline;
}
