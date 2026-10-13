package io.github.noobcodergrowing.jregistrycore;

import lombok.Data;

/**
 * 集群里一个节点的可连接地址。服务端用它把选点结果（按连接数排序的候选节点）下发给客户端，
 * 使客户端只需配置一个种子地址、不必知道全量拓扑。
 */
@Data
public class NodeAddress {

    private int nodeId;

    private String host;

    private int port;

    public NodeAddress() {
    }

    public NodeAddress(int nodeId, String host, int port) {
        this.nodeId = nodeId;
        this.host = host;
        this.port = port;
    }
}
