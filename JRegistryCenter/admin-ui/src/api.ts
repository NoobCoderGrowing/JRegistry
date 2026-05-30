import type {
  ClusterStatus,
  StateMachineTree,
  StateMachineWriteRequest,
  StateMachineWriteResult,
  ElectionTimeline,
  ElectionRounds,
} from './types';

const API_BASE = import.meta.env.VITE_API_BASE ?? '';

export async function fetchClusterStatus(): Promise<ClusterStatus> {
  const res = await fetch(`${API_BASE}/api/admin/cluster`);
  if (!res.ok) {
    throw new Error(`请求失败: ${res.status} ${res.statusText}`);
  }
  return res.json();
}

export async function fetchStateMachineTree(nodeId: number): Promise<StateMachineTree> {
  const res = await fetch(`${API_BASE}/api/admin/state-machine?nodeId=${nodeId}`);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `请求失败: ${res.status} ${res.statusText}`);
  }
  return res.json();
}

async function postStateMachineWrite(
  path: 'set' | 'delete',
  body: StateMachineWriteRequest,
): Promise<StateMachineWriteResult> {
  const res = await fetch(`${API_BASE}/api/admin/state-machine/${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `请求失败: ${res.status} ${res.statusText}`);
  }
  return res.json();
}

export function setStateMachineKey(
  body: StateMachineWriteRequest,
): Promise<StateMachineWriteResult> {
  return postStateMachineWrite('set', body);
}

export function deleteStateMachineKey(
  key: string,
): Promise<StateMachineWriteResult> {
  return postStateMachineWrite('delete', { key });
}

export async function fetchElectionTimeline(round?: number): Promise<ElectionTimeline> {
  const query = round != null ? `?round=${round}` : '';
  const res = await fetch(`${API_BASE}/api/admin/election-timeline${query}`);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `请求失败: ${res.status} ${res.statusText}`);
  }
  return res.json();
}

export async function fetchElectionRounds(): Promise<ElectionRounds> {
  const res = await fetch(`${API_BASE}/api/admin/election-rounds`);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `请求失败: ${res.status} ${res.statusText}`);
  }
  return res.json();
}

export async function triggerPersist(): Promise<StateMachineWriteResult> {
  const res = await fetch(`${API_BASE}/api/admin/persist`, {
    method: 'POST',
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `请求失败: ${res.status} ${res.statusText}`);
  }
  return res.json();
}
