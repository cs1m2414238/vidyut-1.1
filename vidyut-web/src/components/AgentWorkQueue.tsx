import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  Eye,
  LoaderCircle,
  RefreshCw,
  Route,
  ShieldCheck,
} from 'lucide-react';
import { apiRequest } from '../services/api';
import './AgentWorkQueue.css';

export type AgentWorkStatus = 'PENDING' | 'PREPARED' | 'IN_PROGRESS' | 'NEEDS_APPROVAL' | 'APPROVED' |
  'EXECUTING' | 'ATTENTION' | 'COMPLETED' | 'RETRYABLE_FAILURE' | 'BLOCKED' | 'STALE' |
  'CANCELLED' | 'DONE' | 'FAILED';

export interface AgentWorkItem {
  id: number;
  idempotencyKey: string;
  correlationId: string;
  objectiveId?: string;
  actionBundleId?: string;
  requestId?: string;
  category: string;
  status: AgentWorkStatus;
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  title: string;
  detail: string;
  whatHappened: string;
  whyItMatters: string;
  alreadyDone: string;
  proposedAction: string;
  approvalReason?: string;
  expectedImpact: string;
  actionType?: string;
  actionPayload: Record<string, unknown>;
  resourceType?: string;
  resourceId?: number;
  resultSummary?: string;
  failureReason?: string;
  retryCount: number;
  maxRetries: number;
  createdAt: string;
  updatedAt: string;
  completedAt?: string;
  preparedAt?: string;
  approvedAt?: string;
  executionStartedAt?: string;
  executedAt?: string;
  expiresAt?: string;
}

interface AgentWorkQueueResponse {
  workspace: 'EV' | 'HOST' | 'COMPANY';
  counts: Record<AgentWorkStatus, number>;
  items: AgentWorkItem[];
  generatedAt: string;
}

interface AgentWorkQueueProps {
  refreshKey?: string | number;
  onReview?: (item: AgentWorkItem) => void;
}

const terminal = new Set<AgentWorkStatus>(['COMPLETED', 'DONE', 'CANCELLED', 'FAILED', 'STALE']);
const priorityRank = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 } as const;
const workspaceLabel = { EV: 'EV OWNER AGENT', HOST: 'HOST AGENT', COMPANY: 'COMPANY AGENT' } as const;

const groups: Array<{
  key: string;
  statuses: AgentWorkStatus[];
  label: string;
  icon: typeof CheckCircle2;
  tone: string;
}> = [
  { key: 'progress', statuses: ['EXECUTING', 'APPROVED', 'PREPARED', 'IN_PROGRESS'], label: 'In progress', icon: LoaderCircle, tone: 'progress' },
  { key: 'approval', statuses: ['NEEDS_APPROVAL'], label: 'Needs approval', icon: ShieldCheck, tone: 'approval' },
  { key: 'monitoring', statuses: ['PENDING', 'ATTENTION'], label: 'Monitoring', icon: Eye, tone: 'monitoring' },
  { key: 'completed', statuses: ['COMPLETED', 'DONE', 'CANCELLED'], label: 'Completed', icon: CheckCircle2, tone: 'completed' },
  { key: 'blocked', statuses: ['RETRYABLE_FAILURE', 'BLOCKED', 'STALE', 'FAILED'], label: 'Failed / blocked', icon: AlertTriangle, tone: 'blocked' },
];

function displayStatus(status: AgentWorkStatus) {
  return status.replaceAll('_', ' ').toLowerCase();
}

function traceLabel(correlationId: string) {
  return correlationId.length > 24 ? `${correlationId.slice(0, 21)}…` : correlationId;
}

function expiryLabel(value?: string) {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return `Approval expires ${date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
}

export function AgentWorkQueue({ refreshKey, onReview }: AgentWorkQueueProps) {
  const [queue, setQueue] = useState<AgentWorkQueueResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async (quiet = false) => {
    try {
      if (!quiet) setLoading(true);
      const response = await apiRequest<AgentWorkQueueResponse>('/agent/work-queue', { method: 'GET' });
      setQueue(response);
      setError('');
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unable to load the agent work queue.');
    } finally {
      if (!quiet) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(true), 5_000);
    return () => window.clearInterval(timer);
  }, [load, refreshKey]);

  const currentObjective = useMemo(() => (queue?.items ?? [])
    .filter((item) => !terminal.has(item.status))
    .sort((left, right) => priorityRank[right.priority] - priorityRank[left.priority]
      || new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())[0], [queue]);

  const visibleGroups = useMemo(() => groups.map((group) => ({
    ...group,
    items: (queue?.items ?? []).filter((item) => group.statuses.includes(item.status))
      .slice(0, group.key === 'completed' ? 4 : 6),
  })).filter((group) => group.items.length > 0), [queue]);

  const groupCount = (statuses: AgentWorkStatus[]) => statuses
    .reduce((total, status) => total + (queue?.counts[status] ?? 0), 0);

  return (
    <section className="agent-work-queue" aria-label="Agent work queue">
      <header className="agent-work-queue-head">
        <div>
          <span>LIVE OPERATOR STATE · {queue ? workspaceLabel[queue.workspace] : 'AGENT'}</span>
          <h2>Agent work queue</h2>
          <p>One durable execution view—not a chat transcript.</p>
        </div>
        <button type="button" onClick={() => void load()} disabled={loading} aria-label="Refresh agent work queue">
          <RefreshCw size={15} className={loading ? 'agent-work-spin' : ''} />
          Refresh
        </button>
      </header>

      {queue && (
        <div className="agent-work-queue-counts">
          <span><LoaderCircle size={14} />{groupCount(groups[0].statuses)}<small>in progress</small></span>
          <span><ShieldCheck size={14} />{groupCount(groups[1].statuses)}<small>approval</small></span>
          <span><Eye size={14} />{groupCount(groups[2].statuses)}<small>monitoring</small></span>
          <span><AlertTriangle size={14} />{groupCount(groups[4].statuses)}<small>blocked</small></span>
        </div>
      )}

      {currentObjective && (
        <article className={`agent-current-objective priority-${currentObjective.priority.toLowerCase()}`}>
          <header>
            <div><Route size={17} /><span>Current objective</span></div>
            <b>{displayStatus(currentObjective.status)}</b>
          </header>
          <h3>{currentObjective.title}</h3>
          <div className="agent-reason-chain">
            <p><strong>What happened</strong>{currentObjective.whatHappened}</p>
            <p><strong>Why it matters</strong>{currentObjective.whyItMatters}</p>
            <p><strong>What Vidyut did</strong>{currentObjective.alreadyDone}</p>
            <p><strong>{currentObjective.approvalReason ? 'What needs approval' : 'Next action'}</strong>
              {currentObjective.approvalReason ?? currentObjective.proposedAction}</p>
          </div>
          <footer>
            <span title={currentObjective.correlationId}>Trace {traceLabel(currentObjective.correlationId)}</span>
            {expiryLabel(currentObjective.expiresAt) && <span>{expiryLabel(currentObjective.expiresAt)}</span>}
            {currentObjective.status === 'NEEDS_APPROVAL' && onReview && currentObjective.actionType && (
              <button type="button" onClick={() => onReview(currentObjective)}>Review action</button>
            )}
          </footer>
        </article>
      )}

      {loading && !queue && <div className="agent-work-queue-state"><LoaderCircle className="agent-work-spin" size={20} />Reading operational state…</div>}
      {error && !queue && <div className="agent-work-queue-state error"><AlertTriangle size={18} />{error}<button onClick={() => void load()}>Retry</button></div>}
      {!loading && queue && queue.items.length === 0 && (
        <div className="agent-work-queue-state"><ShieldCheck size={20} />No active work yet. Start the agent’s primary operator workflow.</div>
      )}

      {visibleGroups.length > 0 && <div className="agent-work-groups">
        {visibleGroups.map(({ key, statuses, label, icon: Icon, tone, items }) => (
          <section className={`agent-work-group status-${tone}`} key={key}>
            <header><Icon size={15} className={key === 'progress' ? 'agent-work-spin' : ''} /><strong>{label}</strong><span>{groupCount(statuses)}</span></header>
            <div>
              {items.map((item) => (
                <article key={item.id} className={`priority-${item.priority.toLowerCase()}`}>
                  <i />
                  <div>
                    <strong>{item.title}</strong>
                    <p>{item.failureReason ?? item.detail}</p>
                    <small>{displayStatus(item.status)} · {item.category.replaceAll('_', ' ')} · trace {traceLabel(item.correlationId)}</small>
                  </div>
                  {item.status === 'NEEDS_APPROVAL' && onReview && item.actionType && (
                    <button type="button" onClick={() => onReview(item)}>Review</button>
                  )}
                </article>
              ))}
            </div>
          </section>
        ))}
      </div>}
    </section>
  );
}
