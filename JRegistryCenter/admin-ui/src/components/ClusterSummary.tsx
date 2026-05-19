import type { ClusterStatus } from '../types';

interface Props {
  data: ClusterStatus;
}

export function ClusterSummary({ data }: Props) {
  const leaderLabel =
    data.leaderId > 0
      ? `节点 ${data.leaderId}${data.leaderHost ? ` (${data.leaderHost})` : ''}`
      : '未选举';

  return (
    <section className="summary-grid">
      <div className="summary-card">
        <div className="label">当前节点</div>
        <div className="value">#{data.localNodeId}</div>
      </div>
      <div className="summary-card">
        <div className="label">本机角色</div>
        <div className="value">{data.localRole}</div>
      </div>
      <div className="summary-card">
        <div className="label">Leader</div>
        <div className="value leader">{leaderLabel}</div>
      </div>
      <div className="summary-card">
        <div className="label">任期 (Term)</div>
        <div className="value">{data.currentTerm}</div>
      </div>
      <div className="summary-card">
        <div className="label">集群规模</div>
        <div className="value">{data.clusterSize} 节点</div>
      </div>
      <div className="summary-card">
        <div className="label">已连接 Peer</div>
        <div className="value">
          {data.connectedPeers} / {data.clusterSize - 1}
        </div>
      </div>
      <div className="summary-card">
        <div className="label">法定人数</div>
        <div className="value">{data.quorum}</div>
      </div>
    </section>
  );
}
