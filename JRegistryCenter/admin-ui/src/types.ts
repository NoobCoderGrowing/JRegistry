export interface TreeNode {
  key: string;
  path: string;
  dotKey: string;
  type: string | null;
  value: string | null;
  leaf: boolean;
  children: TreeNode[];
}

export interface StateMachineWriteRequest {
  key: string;
  value?: string;
  dataType?: string;
}

export interface StateMachineWriteResult {
  success: boolean;
  message: string;
}

export interface StateMachineTree {
  nodeId: number;
  commitIndex: number;
  root: TreeNode;
}

export interface NodeInfo {
  nodeId: number;
  host: string;
  raftPort: number;
  httpPort: number;
  sshPort: number;
  clsPort: number;
  role: string;
  connected: boolean;
  self: boolean;
  currentTerm: number | null;
  commitIndex: number | null;
  lastLogIndex: number | null;
  lastLogTerm: number | null;
  logCount: number | null;
  leaderId: number | null;
  leaderHost: string | null;
  leaderPort: number | null;
  voteReceived: number | null;
  activePeerConnections: number | null;
}

export interface ClusterStatus {
  localNodeId: number;
  clusterSize: number;
  leaderId: number;
  leaderHost: string | null;
  leaderPort: number;
  currentTerm: number;
  localRole: string;
  connectedPeers: number;
  quorum: number;
  nodes: NodeInfo[];
}

export interface ElectionEvent {
  sequence: number;
  timestamp: string;
  eventType: string;
  nodeId: number;
  targetNodeId: number;
  term: number;
  votesReceived: number;
  message: string;
}

export interface ElectionTimeline {
  clusterSize: number;
  finalLeaderId: number;
  finalTerm: number;
  logPath: string;
  events: ElectionEvent[];
}
