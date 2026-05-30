import { useState } from 'react';
import type { TreeNode } from '../types';
import { deleteStateMachineKey, setStateMachineKey } from '../api';

interface TreeNodeItemProps {
  node: TreeNode;
  depth?: number;
  onDelete: (dotKey: string) => void;
  deletingKey: string | null;
}

function TreeNodeItem({ node, depth = 0, onDelete, deletingKey }: TreeNodeItemProps) {
  const hasChildren = node.children.length > 0;
  const [expanded, setExpanded] = useState(depth < 2);
  const canDelete = node.leaf && node.dotKey;

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
        {canDelete && (
          <button
            type="button"
            className="btn btn-sm btn-danger"
            disabled={deletingKey === node.dotKey}
            onClick={() => onDelete(node.dotKey)}
          >
            {deletingKey === node.dotKey ? '删除中…' : '删除'}
          </button>
        )}
      </div>
      {hasChildren && expanded && (
        <ul className="tree-children">
          {node.children.map((child) => (
            <TreeNodeItem
              key={child.path}
              node={child}
              depth={depth + 1}
              onDelete={onDelete}
              deletingKey={deletingKey}
            />
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
  onRefresh: () => Promise<void>;
}

export function StateMachineTreePanel({
  nodeId,
  commitIndex,
  root,
  onClose,
  onRefresh,
}: Props) {
  const [setKey, setSetKey] = useState('');
  const [setValue, setSetValue] = useState('');
  const [setType, setSetType] = useState('string');
  const [deleteKey, setDeleteKey] = useState('');
  const [busy, setBusy] = useState(false);
  const [deletingKey, setDeletingKey] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  async function runWrite(action: () => Promise<unknown>) {
    setBusy(true);
    setErr(null);
    setMsg(null);
    try {
      await action();
      await onRefresh();
      setMsg('操作已提交，树已刷新');
    } catch (e) {
      setErr(e instanceof Error ? e.message : '操作失败');
    } finally {
      setBusy(false);
      setDeletingKey(null);
    }
  }

  async function handleSet(e: React.FormEvent) {
    e.preventDefault();
    if (!setKey.trim() || !setValue.trim()) return;
    await runWrite(() =>
      setStateMachineKey({
        key: setKey.trim(),
        value: setValue,
        dataType: setType,
      }),
    );
    setSetKey('');
    setSetValue('');
  }

  async function handleDelete(e: React.FormEvent) {
    e.preventDefault();
    if (!deleteKey.trim()) return;
    await runWrite(() => deleteStateMachineKey(deleteKey.trim()));
    setDeleteKey('');
  }

  async function handleTreeDelete(dotKey: string) {
    if (!window.confirm(`确认删除键 "${dotKey}"？`)) return;
    setDeletingKey(dotKey);
    await runWrite(() => deleteStateMachineKey(dotKey));
  }

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

        <div className="sm-write-panel">
          <h4>增删</h4>
          <form className="sm-form" onSubmit={handleSet}>
            <label>
              <span>新增 / 更新 (set)</span>
              <input
                className="sm-input mono"
                placeholder="键，如 app.config.timeout"
                value={setKey}
                onChange={(e) => setSetKey(e.target.value)}
                disabled={busy}
              />
            </label>
            <label>
              <span>值</span>
              <input
                className="sm-input mono"
                placeholder="值"
                value={setValue}
                onChange={(e) => setSetValue(e.target.value)}
                disabled={busy}
              />
            </label>
            <label>
              <span>类型</span>
              <select
                className="sm-input"
                value={setType}
                onChange={(e) => setSetType(e.target.value)}
                disabled={busy}
              >
                <option value="string">string</option>
              </select>
            </label>
            <button type="submit" className="btn btn-sm" disabled={busy}>
              {busy ? '提交中…' : 'Set'}
            </button>
          </form>
          <form className="sm-form sm-form-delete" onSubmit={handleDelete}>
            <label>
              <span>删除 (delete)</span>
              <input
                className="sm-input mono"
                placeholder="键，如 app.config.timeout"
                value={deleteKey}
                onChange={(e) => setDeleteKey(e.target.value)}
                disabled={busy}
              />
            </label>
            <button type="submit" className="btn btn-sm btn-danger" disabled={busy}>
              Delete
            </button>
          </form>
        </div>

        {msg && <p className="sm-msg success">{msg}</p>}
        {err && <p className="sm-msg error">{err}</p>}

        <div className="tree-container">
          <ul className="tree-root">
            <TreeNodeItem
              node={root}
              onDelete={handleTreeDelete}
              deletingKey={deletingKey}
            />
          </ul>
        </div>
      </div>
    </div>
  );
}
