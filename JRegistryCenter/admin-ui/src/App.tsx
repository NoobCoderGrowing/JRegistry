import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchClusterStatus, fetchElectionRounds, fetchElectionTimeline, triggerPersist } from './api';
import { ClusterSummary } from './components/ClusterSummary';
import { NodeTable } from './components/NodeTable';
import { ElectionAnimationPanel } from './components/ElectionAnimationPanel';
import type { ClusterStatus, ElectionRoundSummary, ElectionTimeline } from './types';

const REFRESH_MS = 5000;

function buildElectionSignature(round: ElectionRoundSummary | null): string {
  if (!round || round.finalLeaderId <= 0) {
    return '';
  }
  return `${round.finalTerm}:${round.finalLeaderId}:${round.endedAt}`;
}

export default function App() {
  const [data, setData] = useState<ClusterStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [electionTimeline, setElectionTimeline] = useState<ElectionTimeline | null>(null);
  const [latestElectionRound, setLatestElectionRound] = useState<ElectionRoundSummary | null>(null);
  const [electionLoading, setElectionLoading] = useState(false);
  const [electionError, setElectionError] = useState<string | null>(null);
  const [animationSessionKey, setAnimationSessionKey] = useState(0);
  const [autoPlayElection, setAutoPlayElection] = useState(false);
  const [persistLoading, setPersistLoading] = useState(false);
  const [persistMessage, setPersistMessage] = useState<string | null>(null);
  const [persistIsError, setPersistIsError] = useState(false);

  const electionInitializedRef = useRef(false);
  const lastElectionSignatureRef = useRef('');

  const openLatestElectionTimeline = useCallback(async (options?: { autoPlay?: boolean }) => {
    setElectionLoading(true);
    setElectionError(null);
    try {
      const timeline = await fetchElectionTimeline(1);
      if (timeline.events.length === 0 || timeline.finalLeaderId <= 0) {
        setElectionError('暂无成功选主记录');
        setElectionTimeline(null);
        return;
      }
      setElectionTimeline(timeline);
      setAutoPlayElection(options?.autoPlay ?? false);
      setAnimationSessionKey((key) => key + 1);
    } catch (e) {
      setElectionError(e instanceof Error ? e.message : '加载选主日志失败');
      setElectionTimeline(null);
    } finally {
      setElectionLoading(false);
    }
  }, []);

  const refreshLatestElection = useCallback(async (options?: { silent?: boolean }) => {
    if (!options?.silent) {
      setElectionLoading(true);
    }
    setElectionError(null);
    try {
      const roundsData = await fetchElectionRounds();
      const latestRound = roundsData.rounds[0] ?? null;
      setLatestElectionRound(latestRound);

      const signature = buildElectionSignature(latestRound);
      if (!electionInitializedRef.current) {
        lastElectionSignatureRef.current = signature;
        electionInitializedRef.current = true;
        return;
      }

      if (signature && signature !== lastElectionSignatureRef.current) {
        lastElectionSignatureRef.current = signature;
        await openLatestElectionTimeline({ autoPlay: true });
      }
    } catch (e) {
      setElectionError(e instanceof Error ? e.message : '加载选主记录失败');
    } finally {
      if (!options?.silent) {
        setElectionLoading(false);
      }
    }
  }, [openLatestElectionTimeline]);

  async function handlePersist() {
    setPersistLoading(true);
    setPersistMessage(null);
    setPersistIsError(false);
    try {
      const result = await triggerPersist();
      setPersistMessage(result.message || 'Persist 已触发');
    } catch (e) {
      setPersistIsError(true);
      setPersistMessage(e instanceof Error ? e.message : 'Persist 失败');
    } finally {
      setPersistLoading(false);
    }
  }

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
    refreshLatestElection({ silent: true });
    const timer = window.setInterval(() => {
      load();
      refreshLatestElection({ silent: true });
    }, REFRESH_MS);
    return () => window.clearInterval(timer);
  }, [load, refreshLatestElection]);

  return (
    <>
      <header>
        <div className="header-row">
          <div>
            <h1>JRegistry Center</h1>
            <p>Raft 集群节点管理后台 · 每 {REFRESH_MS / 1000}s 自动刷新 · 重选主后自动播放动画</p>
          </div>
          <div className="header-actions">
            <button
              type="button"
              className="btn"
              disabled={persistLoading}
              onClick={() => {
                void handlePersist();
              }}
            >
              {persistLoading ? 'Persist 中…' : 'Persist'}
            </button>
            <button
              type="button"
              className="btn"
              disabled={electionLoading || !latestElectionRound}
              onClick={() => {
                void openLatestElectionTimeline({ autoPlay: true });
              }}
            >
              {electionLoading ? '加载中…' : '最新选主流程'}
            </button>
          </div>
        </div>
      </header>

      {persistMessage && (
        <div className={persistIsError ? 'error-banner' : 'info-banner'}>{persistMessage}</div>
      )}

      {electionError && (
        <div className="error-banner">{electionError}</div>
      )}

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
          <NodeTable
            nodes={data.nodes}
            leaderId={data.leaderId}
            isLeader={data.localRole === 'LEADER'}
          />
        </>
      )}

      {electionTimeline && (
        <ElectionAnimationPanel
          key={animationSessionKey}
          timeline={electionTimeline}
          sessionKey={animationSessionKey}
          autoPlay={autoPlayElection}
          onClose={() => {
            setElectionTimeline(null);
            setAutoPlayElection(false);
          }}
        />
      )}
    </>
  );
}
