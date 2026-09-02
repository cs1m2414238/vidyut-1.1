import { useCallback, useEffect, useState } from 'react';
import { Activity, CheckCircle2, LoaderCircle, RefreshCw } from 'lucide-react';
import { apiRequest } from '../services/api';
import './AgentWorkQueue.css';

interface AgentActivityItem {
  id: number;
  workItemId?: number;
  correlationId: string;
  activityType: string;
  summary: string;
  detail?: string;
  metadata: Record<string, unknown>;
  occurredAt: string;
}

interface AgentActivityResponse {
  workspace: 'EV' | 'HOST' | 'COMPANY';
  activities: AgentActivityItem[];
  generatedAt: string;
}

export function AgentActivityTimeline({ refreshKey }: { refreshKey?: string | number }) {
  const [timeline, setTimeline] = useState<AgentActivityResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      setTimeline(await apiRequest<AgentActivityResponse>('/agent/work-queue/activity', { method: 'GET' }));
      setError('');
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unable to load agent activity.');
    } finally {
      if (!quiet) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(true), 5_000);
    return () => window.clearInterval(timer);
  }, [load, refreshKey]);

  return (
    <section className="agent-activity" aria-label="Agent activity timeline">
      <header>
        <div><Activity size={17} /><span><b>Agent activity</b><small>Verified execution timeline</small></span></div>
        <button type="button" onClick={() => void load()} aria-label="Refresh agent activity"><RefreshCw size={14} /></button>
      </header>
      {loading && !timeline && <p className="agent-activity-empty"><LoaderCircle className="agent-work-spin" size={17} /> Loading activity…</p>}
      {error && !timeline && <p className="agent-activity-empty"><RefreshCw size={17} /> {error}</p>}
      {timeline && timeline.activities.length === 0 && <p className="agent-activity-empty"><CheckCircle2 size={17} /> No activity recorded yet.</p>}
      {timeline && timeline.activities.length > 0 && (
        <ol>
          {timeline.activities.slice(0, 12).map((item) => (
            <li key={item.id}>
              <time>{new Date(item.occurredAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}</time>
              <i />
              <div><strong>{item.summary}</strong>{item.detail && <p>{item.detail}</p>}
                <small>{item.activityType.replaceAll('_', ' ').toLowerCase()} · {item.correlationId}</small>
              </div>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
