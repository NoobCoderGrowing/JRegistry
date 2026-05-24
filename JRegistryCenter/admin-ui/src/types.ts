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
