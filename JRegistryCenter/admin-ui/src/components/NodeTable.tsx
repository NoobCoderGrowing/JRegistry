import type { NodeInfo } from '../types';

interface Props {
  nodes: NodeInfo[];
  leaderId: number;
}

function roleBadge(role: string) {
  const r = (role || 'UNKNOWN').toUpperCase();
  if (r === 'LEADER') return <span className="badge badge-leader">Leader</span>;
  if (r === 'FOLLOWER') return <span className="badge badge-follower">Follower</span>;
  if (r === 'CANDIDATE') return <span className="badge badge-candidate">Candidate</span>;
  return <span className="badge badge-unknown">{role || 'Unknown'}</span>;
}

function fmt(v: number | null | undefined, fallback = '—') {
  if (v === null || v === undefined || v < 0) return fallback;
  return String(v);
}

export function NodeTable({ nodes, leaderId }: Props) {
  return (
    <section className="panel">
      <div className="panel-header">
        <h2>Raft 集群节点</h2>
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>节点 ID</th>
              <th>角色</th>
              <th>连接</th>
              <th>主机</th>
              <th>Raft port</th>
              <th>HTTP port</th>
              <th>SSH port</th>
              <th>Term</th>
              <th>Commit index</th>
              <th>Last log index</th>
              <th>活跃连接数</th>
            </tr>
          </thead>
          <tbody>
            {nodes.map((node) => (
              <tr
                key={node.nodeId}
                className={node.self ? 'self-row' : undefined}
              >
                <td>
                  <strong>#{node.nodeId}</strong>
                  {node.self && (
                    <span className="badge badge-follower" style={{ marginLeft: 6 }}>
                      本机
                    </span>
                  )}
                  {node.nodeId === leaderId && leaderId > 0 && (
                    <span className="badge badge-leader" style={{ marginLeft: 6 }}>
                      Leader
                    </span>
                  )}
                </td>
                <td>{roleBadge(node.role)}</td>
                <td>
                  <span className={`status-dot ${node.connected ? 'online' : ''}`}>
                    {node.connected ? '在线' : '离线'}
                  </span>
                </td>
                <td className="mono">{node.host}</td>
                <td className="mono">{fmt(node.raftPort)}</td>
                <td className="mono">{fmt(node.httpPort)}</td>
                <td className="mono">{fmt(node.sshPort)}</td>
                <td>{fmt(node.currentTerm)}</td>
                <td>{fmt(node.commitIndex)}</td>
                <td>{fmt(node.lastLogIndex)}</td>
                <td>{fmt(node.activePeerConnections)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
