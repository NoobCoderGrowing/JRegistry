import { useState } from 'react';
import type { TreeNode } from '../types';

interface TreeNodeItemProps {
  node: TreeNode;
  depth?: number;
}

function TreeNodeItem({ node, depth = 0 }: TreeNodeItemProps) {
  const hasChildren = node.children.length > 0;
  const [expanded, setExpanded] = useState(depth < 2);

  return (
    <li className="tree-node">
      <div className="tree-node-row" style={{ paddingLeft: `${depth * 1.25}rem` }}>
        {hasChildren ? (
          <button
            type="button"
            className="tree-toggle"
            onClick={() => setExpanded((v) => !v)}
            aria-expanded={expanded}
          >
            {expanded ? '▾' : '▸'}
          </button>
        ) : (
          <span className="tree-toggle-spacer" />
        )}
        <span className="tree-key">{node.key}</span>
        {node.leaf && node.type && (
          <span className="tree-meta">
            <span className="tree-type">{node.type}</span>
            {node.value != null && (
              <span className="tree-value mono">{node.value}</span>
            )}
          </span>
        )}
        {!node.leaf && (
          <span className="tree-path mono">{node.path}</span>
        )}
      </div>
      {hasChildren && expanded && (
        <ul className="tree-children">
          {node.children.map((child) => (
            <TreeNodeItem key={child.path} node={child} depth={depth + 1} />
          ))}
        </ul>
      )}
    </li>
  );
}

interface Props {
  nodeId: number;
  commitIndex: number;
  root: TreeNode;
  onClose: () => void;
}

export function StateMachineTreePanel({ nodeId, commitIndex, root, onClose }: Props) {
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="modal-panel state-machine-modal"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <div>
            <h3>StateMachine 树结构</h3>
            <p className="modal-subtitle">
              节点 #{nodeId} · commitIndex: {commitIndex}
            </p>
          </div>
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            关闭
          </button>
        </div>
        <div className="tree-container">
          <ul className="tree-root">
            <TreeNodeItem node={root} />
          </ul>
        </div>
      </div>
    </div>
  );
}
