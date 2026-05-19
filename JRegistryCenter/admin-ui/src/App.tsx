import { useCallback, useEffect, useState } from 'react';
import { fetchClusterStatus } from './api';
import { ClusterSummary } from './components/ClusterSummary';
import { NodeTable } from './components/NodeTable';
import type { ClusterStatus } from './types';

const REFRESH_MS = 5000;

export default function App() {
  const [data, setData] = useState<ClusterStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  const load = useCallback(async () => {
    try {
      const status = await fetchClusterStatus();
      setData(status);
      setError(null);
      setLastUpdated(new Date());
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
    const timer = window.setInterval(load, REFRESH_MS);
    return () => window.clearInterval(timer);
  }, [load]);

  return (
    <>
      <header>
        <h1>JRegistry Center</h1>
        <p>Raft 集群节点管理后台 · 每 {REFRESH_MS / 1000}s 自动刷新</p>
      </header>

      {error && (
        <div className="error-banner">
          无法连接后端 API：{error}。请确认 JRegistryCenter 已启动（默认 http://127.0.0.1:6101）。
        </div>
      )}

      {loading && !data && <p className="loading">正在加载集群状态…</p>}

      {data && (
        <>
          <ClusterSummary data={data} />
          <section className="panel" style={{ marginBottom: '1rem', border: 'none', background: 'transparent' }}>
            <p className="refresh-meta" style={{ padding: 0 }}>
              {lastUpdated
                ? `上次更新：${lastUpdated.toLocaleTimeString('zh-CN')}`
                : ''}
            </p>
          </section>
          <NodeTable nodes={data.nodes} leaderId={data.leaderId} />
        </>
      )}
    </>
  );
}
