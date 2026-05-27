import type { ClusterStatus, StateMachineTree } from './types';

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
