import { useEffect, useMemo, useState } from 'react';
import { CalendarClock, CheckCircle2, CircleAlert, Gauge, MapPin, PlugZap, Star, X, Zap } from 'lucide-react';
import type { Charger } from '../types';
import { apiRequest } from '../services/api';
import './ChargerDetailModal.css';

interface ChargerDetailModalProps {
  charger: Charger | null;
  onClose: () => void;
  onConfirmBooking: (charger: Charger, durationMinutes: number, startTime?: string) => Promise<void>;
  token: string;
  vehicleId?: number;
}

interface BookingSlot { startTime: string; endTime: string; availableConnectors: number; available: boolean }

export function ChargerDetailModal({ charger, onClose, onConfirmBooking, token, vehicleId }: ChargerDetailModalProps) {
  const [duration, setDuration] = useState(60);
  const [isBooking, setIsBooking] = useState(false);
  const [bookingError, setBookingError] = useState('');
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [slots, setSlots] = useState<BookingSlot[]>([]);
  const [selectedStart, setSelectedStart] = useState('');
  const [slotsLoading, setSlotsLoading] = useState(false);
  const [waitlistNotice, setWaitlistNotice] = useState('');

  const durations = useMemo(() => {
    const slot = charger?.bookingSlotMinutes || 30;
    return [slot, slot * 2, slot * 3, slot * 4].filter((minutes) => minutes <= 720);
  }, [charger?.bookingSlotMinutes]);

  useEffect(() => {
    if (!charger) return;
    const preferredDuration = charger.bookingSlotMinutes && charger.bookingSlotMinutes >= 30
      ? charger.bookingSlotMinutes
      : 60;
    setDuration(preferredDuration);
    setBookingError('');
    setIsBooking(false);
    setSelectedStart(''); setWaitlistNotice('');
  }, [charger]);

  useEffect(() => {
    if (!charger || !token) return;
    let ignore = false;
    setSlotsLoading(true);
    void apiRequest<BookingSlot[]>(`/ev/bookings/availability?stationId=${charger.id}&date=${date}`, { method: 'GET', headers: { Authorization: `Bearer ${token}` } })
      .then((items) => { if (!ignore) { setSlots(items); setSelectedStart(items.find((slot) => slot.available)?.startTime || ''); } })
      .catch(() => { if (!ignore) setSlots([]); })
      .finally(() => { if (!ignore) setSlotsLoading(false); });
    return () => { ignore = true; };
  }, [charger, date, token]);

  useEffect(() => {
    if (!charger || isBooking) return undefined;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [charger, isBooking, onClose]);

  if (!charger) return null;

  const energy = charger.powerKw * (duration / 60);
  const totalCost = charger.pricePerKwh * energy;

  const handleBook = async () => {
    setIsBooking(true);
    setBookingError('');
    try {
      await onConfirmBooking(charger, duration, selectedStart || undefined);
      onClose();
    } catch (error) {
      setBookingError(error instanceof Error ? error.message : 'Unable to confirm this booking.');
    } finally {
      setIsBooking(false);
    }
  };

  const joinWaitlist = async () => {
    setIsBooking(true); setBookingError('');
    try {
      const result = await apiRequest<{ position: number }>('/ev/bookings/waitlist', { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: JSON.stringify({ stationId: charger.id, vehicleId, preferredStartTime: selectedStart || new Date(`${date}T09:00:00`).toISOString(), durationMinutes: duration }) });
      setWaitlistNotice(`Joined the waitlist at position ${result.position}. Vidyut will notify you when a slot opens.`);
    } catch (error) { setBookingError(error instanceof Error ? error.message : 'Unable to join the waitlist.'); }
    finally { setIsBooking(false); }
  };

  return (
    <div className="charger-dialog-backdrop" role="presentation" onMouseDown={() => !isBooking && onClose()}>
      <section className="charger-dialog" role="dialog" aria-modal="true" aria-labelledby="charger-dialog-title" onMouseDown={(event) => event.stopPropagation()}>
        <button className="charger-dialog-close" type="button" onClick={onClose} disabled={isBooking} aria-label="Close charger details"><X size={18} /></button>
        <div className="charger-dialog-image-wrap">
          <img src={charger.imageUrl} alt="" className="charger-dialog-image" />
          <span className={charger.available ? 'available' : 'busy'}>{charger.available ? 'Available now' : 'Currently busy'}</span>
        </div>

        <div className="charger-dialog-body">
          <div className="charger-dialog-heading">
            <div>
              <span className="charger-dialog-eyebrow">{charger.demoData ? 'SYNTHETIC DEMO STATION' : 'Verified charging station'}</span>
              <h2 id="charger-dialog-title">{charger.name}</h2>
              <p><MapPin size={13} />{charger.address}</p>
            </div>
            <span className="charger-rating"><Star size={14} fill="currentColor" />{charger.rating}</span>
          </div>

          <div className="charger-spec-grid">
            <article><span><Gauge size={16} /></span><small>Power</small><strong>{charger.powerKw} kW</strong></article>
            <article><span><PlugZap size={16} /></span><small>Connector</small><strong>{charger.connectorType}</strong></article>
            <article><span><Zap size={16} /></span><small>Rate</small><strong>₹{charger.pricePerKwh}/kWh</strong></article>
          </div>

          <div className="charger-duration-section">
            <div><h3>Select charging duration</h3><p>Starting now • saved to My bookings</p></div>
            <div className="charger-duration-options">
              {durations.map((minutes) => (
                <button key={minutes} type="button" className={duration === minutes ? 'active' : ''} onClick={() => setDuration(minutes)} disabled={isBooking}>
                  {minutes < 60 ? `${minutes} min` : minutes % 60 === 0 ? `${minutes / 60} hr` : `${Math.floor(minutes / 60)}h ${minutes % 60}m`}
                </button>
              ))}
            </div>
          </div>

          <div className="charger-slot-section"><div><h3>Choose an available start</h3><label>Date<input type="date" min={new Date().toISOString().slice(0, 10)} value={date} onChange={(event) => setDate(event.target.value)} /></label></div>{slotsLoading ? <p>Checking live slots…</p> : <div className="charger-slot-grid">{slots.slice(0, 12).map((slot) => <button type="button" key={slot.startTime} className={selectedStart === slot.startTime ? 'active' : ''} disabled={!slot.available || isBooking} onClick={() => setSelectedStart(slot.startTime)}><strong>{new Date(slot.startTime).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' })}</strong><small>{slot.available ? `${slot.availableConnectors} connector${slot.availableConnectors === 1 ? '' : 's'}` : 'Full'}</small></button>)}</div>}{!slotsLoading && !slots.length && <p>Live slot data is temporarily unavailable. You can still request the next opening.</p>}</div>

          <div className="charger-cost-summary">
            <span><CalendarClock size={18} /><div><small>Estimated session</small><strong>{energy.toFixed(1)} kWh • {duration} minutes</strong></div></span>
            <div><small>Estimated total</small><strong>₹{totalCost.toFixed(2)}</strong></div>
          </div>

          {bookingError && <div className="charger-booking-error" role="alert"><CircleAlert size={16} />{bookingError}</div>}
          {waitlistNotice && <div className="charger-waitlist-notice"><CheckCircle2 size={16} />{waitlistNotice}</div>}

          {charger.available && selectedStart ? <button className="charger-book-button" type="button" disabled={isBooking} onClick={() => void handleBook()}>{isBooking ? 'Confirming securely…' : 'Confirm selected slot'}</button> : <button className="charger-book-button waitlist" type="button" disabled={isBooking || Boolean(waitlistNotice)} onClick={() => void joinWaitlist()}>{isBooking ? 'Joining…' : 'Join waitlist for next opening'}</button>}
        </div>
      </section>
    </div>
  );
}
