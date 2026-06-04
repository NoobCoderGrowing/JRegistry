import type { ElectionRoundSummary } from '../types';

interface Props {
  latestRound: ElectionRoundSummary | null;
  activeRoundIndex: number | null;
  loading: boolean;
  onReplay: () => void;
  onRefresh: () => void;
}

export function ElectionHistoryPanel({
  latestRound,
  activeRoundIndex,
  loading,
  onReplay,
  onRefresh,
}: Props) {
  const isActive = latestRound != null && activeRoundIndex === latestRound.roundIndex;
  const leaderLabel =
    latestRound && latestRound.finalLeaderId > 0
      ? `节点 ${latestRound.finalLeaderId}`
      : '未完成';

  return (
    <section className="panel election-history-panel">
      <div className="panel-header">
        <div>
          <h2>最新选主</h2>
          <p className="panel-subtitle">仅保留最近一次成功选主，重选主后会自动更新并播放动画</p>
        </div>
        <button type="button" className="btn btn-sm" disabled={loading} onClick={onRefresh}>
          {loading ? '刷新中…' : '刷新'}
        </button>
      </div>

      {!latestRound ? (
        <p className="election-history-empty">暂无成功选主记录，集群完成选主后会显示在这里。</p>
      ) : (
        <button
          type="button"
          className={`election-history-item${isActive ? ' active' : ''}`}
          onClick={onReplay}
        >
          <div className="election-history-item-title">
            最近成功选主
            {isActive && <span className="badge badge-leader">播放中</span>}
          </div>
          <div className="election-history-item-meta">
            <span>Leader: {leaderLabel}</span>
            <span>Term: {latestRound.finalTerm >= 0 ? latestRound.finalTerm : '-'}</span>
            <span>{latestRound.eventCount} 步</span>
          </div>
          <div className="election-history-item-time">
            {latestRound.startedAt || '-'}
            {latestRound.endedAt && latestRound.endedAt !== latestRound.startedAt
              ? ` → ${latestRound.endedAt}`
              : ''}
          </div>
        </button>
      )}
    </section>
  );
}
