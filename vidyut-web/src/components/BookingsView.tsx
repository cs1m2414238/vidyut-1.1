import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  BatteryCharging,
  CalendarDays,
  CheckCircle2,
  CircleAlert,
  Clock3,
  IndianRupee,
  MapPin,
  RefreshCw,
  Search,
  Trash2,
  Zap,
} from 'lucide-react';
import {
  cancelBooking,
  getMyBookings,
  markBookingsSeen,
} from '../services/bookings';
import type { BookingResponse, BookingStatus } from '../services/bookings';
import './BookingsView.css';

type BookingFilter = 'ALL' | 'UPCOMING' | 'COMPLETED' | 'CANCELLED';

function money(value: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  }).format(value);
}

function dateTime(value: string): string {
  return new Intl.DateTimeFormat('en-IN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}

function durationLabel(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  if (!hours) return `${remainder} min`;
  if (!remainder) return `${hours} hr`;
  return `${hours} hr ${remainder} min`;
}

function statusLabel(status: BookingStatus): string {
  return status.replaceAll('_', ' ').toLowerCase().replace(/^./, (value) => value.toUpperCase());
}

function matchesFilter(booking: BookingResponse, filter: BookingFilter): boolean {
  if (filter === 'ALL') return true;
  if (filter === 'UPCOMING') return ['PENDING', 'CONFIRMED', 'IN_PROGRESS'].includes(booking.status);
  return booking.status === filter;
}

export function BookingsView({
  token,
  refreshKey,
  onUnreadCountChange,
  onFindChargers,
}: {
  token: string;
  refreshKey: number;
  onUnreadCountChange: (count: number) => void;
  onFindChargers: () => void;
}) {
  const [bookings, setBookings] = useState<BookingResponse[]>([]);
  const [filter, setFilter] = useState<BookingFilter>('ALL');
  const [loading, setLoading] = useState(true);
  const [cancelling, setCancelling] = useState(false);
  const [pendingCancel, setPendingCancel] = useState<BookingResponse | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const loadBookings = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await getMyBookings(token);
      setBookings(data);
      try {
        await markBookingsSeen(token);
        onUnreadCountChange(0);
      } catch {
        // The list remains usable if acknowledging the badge fails temporarily.
      }
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Unable to load your bookings.');
    } finally {
      setLoading(false);
    }
  }, [token, onUnreadCountChange]);

  useEffect(() => {
    void loadBookings();
  }, [loadBookings, refreshKey]);

  useEffect(() => {
    if (!pendingCancel || cancelling) return undefined;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setPendingCancel(null);
    };
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [pendingCancel, cancelling]);

  const totals = useMemo(() => ({
    upcoming: bookings.filter((booking) => ['PENDING', 'CONFIRMED', 'IN_PROGRESS'].includes(booking.status)).length,
    completed: bookings.filter((booking) => booking.status === 'COMPLETED').length,
    energy: bookings.filter((booking) => booking.status === 'COMPLETED').reduce((sum, booking) => sum + booking.kwhDelivered, 0),
  }), [bookings]);
  const visibleBookings = bookings.filter((booking) => matchesFilter(booking, filter));

  const confirmCancellation = async () => {
    if (!pendingCancel) return;
    setCancelling(true);
    setError('');
    try {
      await cancelBooking(token, pendingCancel.id);
      setBookings((current) => current.map((booking) => booking.id === pendingCancel.id
        ? { ...booking, status: 'CANCELLED' }
        : booking));
      setNotice(`Booking VY-${pendingCancel.id} was cancelled.`);
      setPendingCancel(null);
    } catch (cancelError) {
      setError(cancelError instanceof Error ? cancelError.message : 'Unable to cancel this booking.');
      setPendingCancel(null);
    } finally {
      setCancelling(false);
    }
  };

  return (
    <section className="bookings-page" aria-labelledby="bookings-title">
      <header className="bookings-heading">
        <div>
          <div className="feature-eyebrow">EV Owner workspace</div>
          <h1 id="bookings-title">My bookings</h1>
          <p>Track upcoming reservations, charging sessions and completed visits.</p>
        </div>
        <div className="bookings-heading-actions">
          <button className="bookings-refresh" type="button" onClick={() => void loadBookings()} disabled={loading}>
            <RefreshCw size={15} className={loading ? 'spinning' : ''} /> Refresh
          </button>
          <button className="feature-primary" type="button" onClick={onFindChargers}><Search size={15} /> Find a charger</button>
        </div>
      </header>

      {error && <div className="bookings-message error" role="alert"><CircleAlert size={16} />{error}</div>}
      {notice && <div className="bookings-message success" role="status"><CheckCircle2 size={16} />{notice}</div>}

      <div className="bookings-metrics">
        <article><span><CalendarDays size={18} /></span><strong>{loading ? '—' : totals.upcoming}</strong><small>Upcoming bookings</small></article>
        <article><span><CheckCircle2 size={18} /></span><strong>{loading ? '—' : totals.completed}</strong><small>Completed sessions</small></article>
        <article><span><Zap size={18} /></span><strong>{loading ? '—' : `${totals.energy.toFixed(1)} kWh`}</strong><small>Energy delivered</small></article>
      </div>

      <article className="bookings-panel">
        <div className="bookings-panel-head">
          <div><h2>Booking activity</h2><p>Live records from your Vidyut account</p></div>
          <div className="bookings-filters" role="tablist" aria-label="Filter bookings">
            {(['ALL', 'UPCOMING', 'COMPLETED', 'CANCELLED'] as BookingFilter[]).map((option) => (
              <button key={option} type="button" role="tab" aria-selected={filter === option} className={filter === option ? 'active' : ''} onClick={() => setFilter(option)}>
                {option.charAt(0) + option.slice(1).toLowerCase()}
              </button>
            ))}
          </div>
        </div>

        {loading ? (
          <div className="bookings-loading" aria-live="polite"><span /><span /><span /><p>Loading your bookings…</p></div>
        ) : visibleBookings.length === 0 ? (
          <div className="bookings-empty">
            <span><BatteryCharging size={28} /></span>
            <h3>{bookings.length ? 'No bookings match this filter' : 'No bookings yet'}</h3>
            <p>{bookings.length ? 'Choose another status to review your charging activity.' : 'Reserve a verified charger and your booking will appear here immediately.'}</p>
            {!bookings.length && <button className="feature-primary" type="button" onClick={onFindChargers}><MapPin size={15} /> Explore chargers</button>}
          </div>
        ) : (
          <div className="bookings-list">
            {visibleBookings.map((booking) => (
              <article className="booking-record" key={booking.id}>
                <span className="booking-record-icon"><BatteryCharging size={19} /></span>
                <div className="booking-record-main">
                  <div className="booking-record-title">
                    <div><span>VY-{booking.id}</span><h3>{booking.stationName}</h3></div>
                    <i className={`status-${booking.status.toLowerCase()}`}>{statusLabel(booking.status)}</i>
                  </div>
                  <p><MapPin size={13} />{booking.stationAddress}</p>
                  <div className="booking-record-meta">
                    <span><CalendarDays size={13} />{dateTime(booking.startTime)}</span>
                    <span><Clock3 size={13} />{durationLabel(booking.durationMinutes)}</span>
                    <span><Zap size={13} />{booking.kwhDelivered.toFixed(1)} kWh</span>
                    <span><IndianRupee size={13} />{money(booking.totalAmount)}</span>
                  </div>
                </div>
                {['PENDING', 'CONFIRMED'].includes(booking.status) && (
                  <button className="booking-cancel-button" type="button" onClick={() => setPendingCancel(booking)}><Trash2 size={14} /> Cancel</button>
                )}
              </article>
            ))}
          </div>
        )}
      </article>

      {pendingCancel && (
        <div className="vehicle-form-backdrop" role="presentation" onMouseDown={() => !cancelling && setPendingCancel(null)}>
          <section className="booking-cancel-dialog" role="alertdialog" aria-modal="true" aria-labelledby="cancel-booking-title" onMouseDown={(event) => event.stopPropagation()}>
            <span><Trash2 size={23} /></span>
            <h2 id="cancel-booking-title">Cancel this booking?</h2>
            <p>Your reservation at <strong>{pendingCancel.stationName}</strong> will be released. This action cannot be reversed.</p>
            <div>
              <button className="secondary-action" type="button" onClick={() => setPendingCancel(null)} disabled={cancelling}>Keep booking</button>
              <button className="booking-danger-button" type="button" onClick={() => void confirmCancellation()} disabled={cancelling}>{cancelling ? 'Cancelling…' : 'Cancel booking'}</button>
            </div>
          </section>
        </div>
      )}
    </section>
  );
}
