import { useCallback, useEffect, useMemo, useState } from 'react';
import { BatteryCharging, Bluetooth, CalendarClock, CheckCircle2, CircleAlert, Clock3, IndianRupee, Play, RefreshCw, Square, Zap } from 'lucide-react';
import { apiRequest } from '../services/api';
import './ChargingSessionView.css';

interface SessionBooking {
  id: number;
  stationName: string;
  stationAddress: string;
  startTime: string;
  status: string;
  vehicleId?: number;
}
interface ChargingSession {
  id: number;
  bookingId: number;
  stationId: number;
  connectorId?: number;
  stationName: string;
  vehicleId?: number;
  vehicleName?: string;
  status: 'ACTIVE' | 'COMPLETED';
  paymentStatus: string;
  powerKw: number;
  energyKwh: number;
  cost: number;
  co2SavedKg: number;
  startBatteryPercent: number;
  currentBatteryPercent: number;
  targetBatteryPercent: number;
  telemetrySource: string;
  startedAt: string;
  estimatedCompletionAt: string;
  completedAt?: string;
}

function time(value?: string) {
  if (!value) return 'Not available';
  return new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

export function ChargingSessionView({ token }: { token: string }) {
  const [sessions, setSessions] = useState<ChargingSession[]>([]);
  const [completed, setCompleted] = useState<ChargingSession | null>(null);
  const [bookings, setBookings] = useState<SessionBooking[]>([]);
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const auth = useMemo(() => ({ headers: { Authorization: `Bearer ${token}` } }), [token]);

  const load = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      const [active, ownerBookings] = await Promise.all([
        apiRequest<ChargingSession[]>('/ev/sessions/active', { method: 'GET', ...auth }),
        apiRequest<SessionBooking[]>('/ev/bookings', { method: 'GET', ...auth }),
      ]);
      setSessions(active); setBookings(ownerBookings); setError('');
    } catch (loadError) { setError(loadError instanceof Error ? loadError.message : 'Unable to load charging sessions.'); }
    finally { if (!quiet) setLoading(false); }
  }, [auth]);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    if (!sessions.length) return undefined;
    const timer = window.setInterval(() => void load(true), 10_000);
    return () => window.clearInterval(timer);
  }, [load, sessions.length]);

  const run = async (path: string, message: string, body?: Record<string, unknown>) => {
    try {
      setWorking(true); setError('');
      const session = await apiRequest<ChargingSession>(path, { method: path.includes('/soc') ? 'PATCH' : 'POST', ...auth, body: body ? JSON.stringify(body) : undefined });
      if (session.status === 'COMPLETED') setCompleted(session);
      setNotice(message); await load(true);
    } catch (actionError) { setError(actionError instanceof Error ? actionError.message : 'Unable to update the charging session.'); }
    finally { setWorking(false); }
  };

  const ready = bookings.filter((booking) => booking.status === 'CONFIRMED' || booking.status === 'PENDING');
  const session = sessions[0] ?? completed;
  const batteryProgress = session ? Math.max(0, Math.min(100, (session.currentBatteryPercent - session.startBatteryPercent) / Math.max(1, session.targetBatteryPercent - session.startBatteryPercent) * 100)) : 0;

  return <section className="charging-session-page" aria-labelledby="charging-session-title">
    <header><div><span className="section-eyebrow">LIVE CHARGING</span><h1 id="charging-session-title">Charging session</h1><p>Battery, delivered energy, cost and completion estimate refresh every 10 seconds.</p></div><button onClick={() => void load()} disabled={loading || working}><RefreshCw size={15} className={loading ? 'spinning' : ''} /> Refresh</button></header>
    {error && <div className="charging-session-message error"><CircleAlert size={17} />{error}</div>}
    {notice && <div className="charging-session-message"><CheckCircle2 size={17} />{notice}</div>}
    {loading && !session && <div className="charging-session-skeleton"><i /><i /><i /></div>}
    {!loading && !session && <div className="charging-ready-layout"><article><span><BatteryCharging size={29} /></span><h2>No active charging session</h2><p>Start one of your confirmed reservations. Vidyut will allocate a healthy compatible connector and keep this page live.</p></article><div>{ready.map((booking) => <button key={booking.id} onClick={() => void run(`/ev/sessions/booking/${booking.id}/start`, `Charging started at ${booking.stationName}.`)} disabled={working}><span><strong>{booking.stationName}</strong><small>{booking.stationAddress} · {time(booking.startTime)}</small></span><Play size={17} /> Start charging</button>)}{!ready.length && <p>No confirmed booking is ready. Book a charger first.</p>}</div></div>}
    {session && <div className="charging-live-layout">
      <article className={`charging-live-hero ${session.status.toLowerCase()}`}><header><div><span>{session.status === 'ACTIVE' ? 'CHARGING NOW' : 'SESSION COMPLETE'}</span><h2>{session.stationName}</h2><p>{session.vehicleName || 'Linked EV'} · Connector #{session.connectorId || 'auto'}</p></div><i><Zap size={18} />{session.powerKw.toFixed(1)} kW</i></header><div className="charging-battery"><div><strong>{session.currentBatteryPercent}%</strong><span>Target {session.targetBatteryPercent}%</span></div><aside><i style={{ width: `${batteryProgress}%` }} /></aside></div><div className="charging-live-metrics"><span><BatteryCharging /><small>Energy delivered</small><strong>{session.energyKwh.toFixed(2)} kWh</strong></span><span><IndianRupee /><small>Cost so far</small><strong>₹{session.cost.toFixed(2)}</strong></span><span><Clock3 /><small>Estimated completion</small><strong>{time(session.estimatedCompletionAt)}</strong></span><span><Bluetooth /><small>Telemetry</small><strong>{session.telemetrySource.replaceAll('_', ' ')}</strong></span></div><footer>{session.status === 'ACTIVE' && <><button className="secondary" onClick={() => void run(`/ev/sessions/${session.id}/soc`, 'Bluetooth simulator reading applied.', { batteryPercent: Math.min(session.targetBatteryPercent, session.currentBatteryPercent + 10), simulated: true })} disabled={working}><Bluetooth size={16} /> Simulate +10% SoC</button><button className="stop" onClick={() => void run(`/ev/sessions/${session.id}/stop`, 'Charging stopped safely.')} disabled={working}><Square size={15} /> Stop session</button></>}{session.status === 'COMPLETED' && session.paymentStatus !== 'PAID' && <button onClick={() => void run(`/ev/sessions/${session.id}/pay`, 'Vehicle wallet payment completed.')} disabled={working}><IndianRupee size={16} /> Pay from vehicle wallet</button>}</footer></article>
      <aside className="charging-receipt"><span><CalendarClock size={22} /></span><h2>{session.status === 'ACTIVE' ? 'Session timeline' : 'Charging receipt'}</h2><dl><div><dt>Started</dt><dd>{time(session.startedAt)}</dd></div><div><dt>Completed</dt><dd>{time(session.completedAt)}</dd></div><div><dt>CO₂ saved</dt><dd>{session.co2SavedKg.toFixed(2)} kg</dd></div><div><dt>Payment</dt><dd>{session.paymentStatus}</dd></div></dl><p>Session #{session.id} · Booking #{session.bookingId}</p></aside>
    </div>}
  </section>;
}
