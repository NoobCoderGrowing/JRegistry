import { useState } from 'react';
import type { NodeInfo } from '../types';
import { fetchStateMachineTree } from '../api';
import { StateMachineTreePanel } from './StateMachineTreePanel';

interface Props {
  nodes: NodeInfo[];
  leaderId: number;
  isLeader: boolean;
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

function fmtCount(v: number | null | undefined, fallback = '—') {
  if (v === null || v === undefined) return fallback;
  return String(v);
}

export function NodeTable({ nodes, leaderId, isLeader }: Props) {
  const [loadingNodeId, setLoadingNodeId] = useState<number | null>(null);
  const [treeData, setTreeData] = useState<Awaited<ReturnType<typeof fetchStateMachineTree>> | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function loadTree(nodeId: number) {
    const data = await fetchStateMachineTree(nodeId);
    setTreeData(data);
    return data;
  }

  async function handleShowTree(nodeId: number) {
    setLoadingNodeId(nodeId);
    setError(null);
    try {
      await loadTree(nodeId);
    } catch (e) {
      setError(e instanceof Error ? e.message : '获取 StateMachine 失败');
    } finally {
      setLoadingNodeId(null);
    }
  }

  async function refreshTree() {
    if (!treeData) return;
    await loadTree(treeData.nodeId);
  }

  return (
    <>
      <section className="panel">
        <div className="panel-header">
          <h2>Raft 集群节点</h2>
        </div>
        {error && <div className="error-banner" style={{ margin: '0 1rem 1rem' }}>{error}</div>}
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
                <th>Last log term</th>
                <th>总 Log 数</th>
                <th>活跃连接数</th>
                <th>操作</th>
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
                  <td>{fmt(node.lastLogTerm)}</td>
                  <td>{fmtCount(node.logCount)}</td>
                  <td>{fmt(node.activePeerConnections)}</td>
                  <td>
                    <button
                      type="button"
                      className="btn btn-sm"
                      disabled={loadingNodeId === node.nodeId}
                      onClick={() => handleShowTree(node.nodeId)}
                    >
                      {loadingNodeId === node.nodeId ? '加载中…' : 'StateMachine'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
      {treeData && (
        <StateMachineTreePanel
          nodeId={treeData.nodeId}
          commitIndex={treeData.commitIndex}
          root={treeData.root}
          isLeader={isLeader}
          onClose={() => setTreeData(null)}
          onRefresh={refreshTree}
        />
      )}
    </>
  );
}
