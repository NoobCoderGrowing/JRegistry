import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { ElectionEvent, ElectionTimeline } from '../types';

type NodeRole = 'FOLLOWER' | 'CANDIDATE' | 'LEADER' | 'OFFLINE';

interface Point {
  x: number;
  y: number;
}

interface Props {
  timeline: ElectionTimeline;
  onClose: () => void;
}

function buildNodeRoles(
  events: ElectionEvent[],
  step: number,
  clusterSize: number,
): Map<number, NodeRole> {
  const roles = new Map<number, NodeRole>();
  for (let i = 1; i <= clusterSize; i++) {
    roles.set(i, 'OFFLINE');
  }
  const visible = events.slice(0, step + 1);
  for (const ev of visible) {
    switch (ev.eventType) {
      case 'STARTUP':
        roles.set(ev.nodeId, 'FOLLOWER');
        break;
      case 'CANDIDATE':
      case 'ELECTION_START':
        roles.set(ev.nodeId, 'CANDIDATE');
        break;
      case 'REQUEST_VOTE':
      case 'GRANT_VOTE':
        // 仅展示投票动画，不改变节点身份
        break;
      case 'BECOME_LEADER':
        roles.set(ev.nodeId, 'LEADER');
        break;
      case 'BECOME_FOLLOWER':
        if (roles.get(ev.nodeId) !== 'OFFLINE') {
          roles.set(ev.nodeId, 'FOLLOWER');
        }
        break;
      case 'ACCEPT_LEADER':
        if (roles.get(ev.nodeId) !== 'OFFLINE') {
          roles.set(ev.nodeId, 'FOLLOWER');
        }
        if (ev.targetNodeId > 0 && roles.get(ev.targetNodeId) !== 'OFFLINE') {
          roles.set(ev.targetNodeId, 'LEADER');
        }
        break;
      default:
        break;
    }
  }
  return roles;
}

function roleClass(role: NodeRole) {
  if (role === 'LEADER') return 'election-node leader';
  if (role === 'CANDIDATE') return 'election-node candidate';
  if (role === 'FOLLOWER') return 'election-node follower';
  return 'election-node offline';
}

function roleLabel(role: NodeRole) {
  if (role === 'LEADER') return 'Leader';
  if (role === 'CANDIDATE') return 'Candidate';
  if (role === 'FOLLOWER') return 'Follower';
  return '未启动';
}

function shortenLine(from: Point, to: Point, margin: number): { start: Point; end: Point } {
  const dx = to.x - from.x;
  const dy = to.y - from.y;
  const len = Math.hypot(dx, dy) || 1;
  const ux = dx / len;
  const uy = dy / len;
  return {
    start: { x: from.x + ux * margin, y: from.y + uy * margin },
    end: { x: to.x - ux * margin, y: to.y - uy * margin },
  };
}

export function ElectionAnimationPanel({ timeline, onClose }: Props) {
  const { events, clusterSize, finalLeaderId, finalTerm } = timeline;
  const [step, setStep] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [nodeCenters, setNodeCenters] = useState<Map<number, Point>>(new Map());
  const [stageSize, setStageSize] = useState({ w: 0, h: 0 });

  const stageRef = useRef<HTMLDivElement>(null);
  const nodeRefs = useRef<Map<number, HTMLDivElement>>(new Map());

  const currentEvent = events[step] ?? null;
  const leaderRevealed = useMemo(
    () => events.slice(0, step + 1).some((e) => e.eventType === 'BECOME_LEADER'),
    [events, step],
  );
  const nodeRoles = useMemo(
    () => buildNodeRoles(events, step, clusterSize),
    [events, step, clusterSize],
  );

  const measureNodes = useCallback(() => {
    const stage = stageRef.current;
    if (!stage) return;
    const sr = stage.getBoundingClientRect();
    const centers = new Map<number, Point>();
    nodeRefs.current.forEach((el, id) => {
      const r = el.getBoundingClientRect();
      centers.set(id, {
        x: r.left + r.width / 2 - sr.left,
        y: r.top + r.height / 2 - sr.top,
      });
    });
    setNodeCenters(centers);
    setStageSize({ w: sr.width, h: sr.height });
  }, []);

  useLayoutEffect(() => {
    measureNodes();
    window.addEventListener('resize', measureNodes);
    return () => window.removeEventListener('resize', measureNodes);
  }, [measureNodes, step, clusterSize]);

  const flow = useMemo(() => {
    if (!currentEvent) return null;
    if (
      currentEvent.eventType !== 'REQUEST_VOTE' &&
      currentEvent.eventType !== 'GRANT_VOTE'
    ) {
      return null;
    }
    // 与日志一致：nodeId → targetNodeId
    // RequestVote: Candidate → peer；GrantVote: voter → candidate
    const fromId = currentEvent.nodeId;
    const toId = currentEvent.targetNodeId;
    const from = nodeCenters.get(fromId);
    const to = nodeCenters.get(toId);
    if (!from || !to) return null;
    return { fromId, toId, from, to, kind: currentEvent.eventType };
  }, [currentEvent, nodeCenters]);

  const requestArrow = useMemo(() => {
    if (!flow || flow.kind !== 'REQUEST_VOTE') return null;
    const line = shortenLine(flow.from, flow.to, 52);
    return { fromId: flow.fromId, toId: flow.toId, ...line };
  }, [flow]);

  const grantLetter = useMemo(() => {
    if (!flow || flow.kind !== 'GRANT_VOTE') return null;
    return {
      fromId: flow.fromId,
      toId: flow.toId,
      from: flow.from,
      to: flow.to,
      dx: flow.to.x - flow.from.x,
      dy: flow.to.y - flow.from.y,
    };
  }, [flow]);

  const goNext = useCallback(() => {
    setStep((s) => Math.min(s + 1, events.length - 1));
  }, [events.length]);

  useEffect(() => {
    if (!playing) return;
    if (step >= events.length - 1) {
      setPlaying(false);
      return;
    }
    const timer = window.setTimeout(goNext, 1200);
    return () => window.clearTimeout(timer);
  }, [playing, step, events.length, goNext]);

  const nodeIds = Array.from({ length: clusterSize }, (_, i) => i + 1);
  const animKey = currentEvent ? `${step}-${currentEvent.eventType}-${currentEvent.nodeId}` : 'none';

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-panel election-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div>
            <h3>选主流程</h3>
            <p className="modal-subtitle">
              基于开机日志 · 共 {events.length} 步
              {leaderRevealed && finalLeaderId > 0
                ? ` · Leader: 节点 ${finalLeaderId} (term ${finalTerm})`
                : ''}
            </p>
          </div>
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            关闭
          </button>
        </div>

        <div className="election-stage">
          <div className="election-diagram" ref={stageRef}>
            {stageSize.w > 0 && stageSize.h > 0 && (
              <svg
                className="election-svg"
                width={stageSize.w}
                height={stageSize.h}
                aria-hidden
              >
                <defs>
                  <marker
                    id="election-arrowhead"
                    markerWidth="8"
                    markerHeight="8"
                    refX="7"
                    refY="4"
                    orient="auto"
                  >
                    <path d="M0,0 L8,4 L0,8 Z" fill="var(--accent)" />
                  </marker>
                </defs>
                {requestArrow && (
                  <g key={`arrow-${animKey}`} className="election-request-arrow">
                    <line
                      x1={requestArrow.start.x}
                      y1={requestArrow.start.y}
                      x2={requestArrow.end.x}
                      y2={requestArrow.end.y}
                      markerEnd="url(#election-arrowhead)"
                    />
                  </g>
                )}
              </svg>
            )}

            {grantLetter && (
              <div
                key={`letter-${animKey}`}
                className="election-vote-letter"
                style={{
                  left: grantLetter.from.x,
                  top: grantLetter.from.y,
                  ['--dx' as string]: `${grantLetter.dx}px`,
                  ['--dy' as string]: `${grantLetter.dy}px`,
                }}
                title={`节点 ${grantLetter.fromId} → 节点 ${grantLetter.toId}`}
              >
                <span className="election-vote-letter-icon">✉</span>
              </div>
            )}

            <div className="election-nodes">
              {nodeIds.map((id) => (
                <div
                  key={id}
                  ref={(el) => {
                    if (el) nodeRefs.current.set(id, el);
                    else nodeRefs.current.delete(id);
                  }}
                  className={roleClass(nodeRoles.get(id) ?? 'OFFLINE')}
                >
                  <div className="election-node-id">#{id}</div>
                  <div className="election-node-role">{roleLabel(nodeRoles.get(id) ?? 'OFFLINE')}</div>
                </div>
              ))}
            </div>
          </div>

          {requestArrow && (
            <p className="election-vote-caption">
              RequestVote：节点 {requestArrow.fromId} → 节点 {requestArrow.toId}
            </p>
          )}
          {grantLetter && (
            <p className="election-vote-caption">
              投票：节点 {grantLetter.fromId} → 节点 {grantLetter.toId}
            </p>
          )}
        </div>

        <div className="election-event-box">
          {currentEvent ? (
            <>
              <div className="election-event-meta">
                <span className="badge badge-unknown">#{currentEvent.sequence}</span>
                <span className="mono">{currentEvent.timestamp}</span>
                <span className="badge badge-candidate">{currentEvent.eventType}</span>
              </div>
              <p className="election-event-msg">{currentEvent.message}</p>
            </>
          ) : (
            <p className="election-event-msg">暂无选主事件</p>
          )}
        </div>

        <div className="election-controls">
          <button
            type="button"
            className="btn btn-sm"
            disabled={step <= 0}
            onClick={() => setStep((s) => Math.max(0, s - 1))}
          >
            上一步
          </button>
          <button
            type="button"
            className="btn btn-sm"
            onClick={() => {
              if (playing) {
                setPlaying(false);
              } else {
                if (step >= events.length - 1) setStep(0);
                setPlaying(true);
              }
            }}
            disabled={events.length === 0}
          >
            {playing ? '暂停' : step >= events.length - 1 ? '重新播放' : '播放'}
          </button>
          <button
            type="button"
            className="btn btn-sm"
            disabled={step >= events.length - 1}
            onClick={goNext}
          >
            下一步
          </button>
          <span className="election-step-indicator">
            {events.length > 0 ? `${step + 1} / ${events.length}` : '0 / 0'}
          </span>
        </div>
      </div>
    </div>
  );
}
