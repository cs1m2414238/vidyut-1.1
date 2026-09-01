import { useEffect, useState } from 'react';
import { Bell, CheckCheck, RefreshCw, Settings } from 'lucide-react';
import { getEvNotificationPreferences, markAllEvNotificationsRead, markEvNotificationRead, updateEvNotificationPreference } from '../services/notifications';
import type { EvNotification, EvNotificationPreference } from '../services/notifications';

interface OwnerNotificationsViewProps {
  notifications: EvNotification[];
  loading: boolean;
  error?: string;
  onRefresh: () => void;
  token: string;
}

function formatNotificationTime(timestamp: string): string {
  const value = new Date(timestamp);
  if (Number.isNaN(value.getTime())) return timestamp;
  return new Intl.DateTimeFormat('en-IN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(value);
}

export function OwnerNotificationsView({
  notifications,
  loading,
  error,
  onRefresh,
  token,
}: OwnerNotificationsViewProps) {
  const [preferences, setPreferences] = useState<EvNotificationPreference[]>([]);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [working, setWorking] = useState(false);
  const unreadCount = notifications.filter((notification) => !notification.read).length;

  useEffect(() => { void getEvNotificationPreferences(token).then(setPreferences).catch(() => setPreferences([])); }, [token]);

  const openNotification = async (notification: EvNotification) => {
    if (!notification.read) await markEvNotificationRead(token, notification.id);
    if (notification.deepLink?.startsWith('vidyut://session/')) window.location.hash = '#/dashboard/charging';
    else if (notification.deepLink?.includes('booking')) window.location.hash = '#/dashboard/bookings';
    else if (notification.deepLink?.includes('outlet')) window.location.hash = '#/dashboard/outlets';
    else if (notification.deepLink?.includes('trip') || notification.deepLink?.includes('route')) window.location.hash = '#/dashboard/autopilot';
    onRefresh();
  };

  const markAll = async () => { setWorking(true); try { await markAllEvNotificationsRead(token); onRefresh(); } finally { setWorking(false); } };
  const togglePreference = async (preference: EvNotificationPreference) => {
    if (preference.critical) return;
    const updated = await updateEvNotificationPreference(token, preference.type, !preference.enabled);
    setPreferences((items) => items.map((item) => item.type === updated.type ? updated : item));
  };

  return (
    <section className="owner-notifications-view" aria-labelledby="owner-notifications-title">
      <header className="owner-notifications-head">
        <div>
          <span className="section-eyebrow">ACCOUNT ACTIVITY</span>
          <h1 id="owner-notifications-title">Notifications</h1>
          <p>{unreadCount > 0 ? `${unreadCount} unread update${unreadCount === 1 ? '' : 's'}` : 'You are all caught up.'}</p>
        </div>
        <div className="owner-notification-actions"><button type="button" onClick={() => setSettingsOpen((value) => !value)}><Settings size={15} /> Preferences</button>{unreadCount > 0 && <button type="button" onClick={() => void markAll()} disabled={working}><CheckCheck size={15} /> Mark all read</button>}<button type="button" onClick={onRefresh} disabled={loading}>
          <RefreshCw size={15} className={loading ? 'spinning' : ''} /> Refresh
        </button></div>
      </header>

      {settingsOpen && <section className="owner-notification-preferences"><header><div><strong>Notification preferences</strong><span>Safety-critical alerts always stay enabled.</span></div><Bell size={18} /></header><div>{preferences.map((preference) => <label key={preference.type}><span><strong>{preference.type.replaceAll('_', ' ')}</strong><small>{preference.critical ? 'Safety critical · always on' : 'Push and in-app updates'}</small></span><input type="checkbox" checked={preference.enabled || preference.critical} disabled={preference.critical} onChange={() => void togglePreference(preference)} /></label>)}</div></section>}

      {error && <div className="owner-notifications-error" role="alert">{error}</div>}

      <div className="owner-notifications-card">
        {loading && notifications.length === 0 && (
          <div className="owner-notifications-empty">Loading notifications…</div>
        )}

        {!loading && notifications.length === 0 && !error && (
          <div className="owner-notifications-empty">
            <Bell size={25} />
            <strong>No notifications yet</strong>
            <p>Booking, charging and account updates will appear here.</p>
          </div>
        )}

        {notifications.map((notification) => (
          <button
            key={notification.id}
            className={`owner-notification-row ${notification.read ? '' : 'unread'}`}
            onClick={() => void openNotification(notification)}
          >
            <span className="owner-notification-icon"><Bell size={17} /></span>
            <div>
              <small>{notification.type.replaceAll('_', ' ')}</small>
              <strong>{notification.title}</strong>
              <p>{notification.message}</p>
            </div>
            <time dateTime={notification.timestamp}>{formatNotificationTime(notification.timestamp)}</time>
          </button>
        ))}
      </div>
    </section>
  );
}
