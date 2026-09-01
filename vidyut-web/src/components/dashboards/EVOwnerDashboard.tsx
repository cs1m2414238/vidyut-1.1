import { useEffect, useState } from 'react';
import { MapPin, Calendar, Wallet, AlertCircle, Info, Navigation, Sparkles, BatteryCharging, Bluetooth, Route, ChevronRight, ArrowRight, Clock3, IndianRupee, ShieldCheck, RefreshCw, Zap, XCircle } from 'lucide-react';
import { MapContainer, TileLayer, Marker as LeafletMarker, Popup as LeafletPopup, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import type { User, Charger } from '../../types';
import type { Vehicle } from '../../services/vehicles';
import { getCurrentAutopilotTrip, endAutopilotJourney, type AutopilotTrip, type AutopilotTripStatus } from '../../services/autopilot';

interface EVOwnerDashboardProps {
  token: string;
  user: User;
  chargers: Charger[];
  onSelectCharger?: (charger: Charger) => void;
  onExploreChargers?: () => void;
  onBookNow?: () => void;
  onOpenBookings?: () => void;
  onOpenWallet?: () => void;
  onOpenAutopilot?: () => void;
  vehicle?: Vehicle | null;
  onOpenVehicle?: (vehicleId: number) => void;
}

function MiniMapResizer() {
  const map = useMap();
  useEffect(() => {
    map.invalidateSize();
    const timer = setTimeout(() => map.invalidateSize(), 250);
    return () => clearTimeout(timer);
  }, [map]);
  return null;
}

export function EVOwnerDashboard({
  token,
  user,
  chargers,
  onSelectCharger,
  onExploreChargers,
  onBookNow,
  onOpenBookings,
  onOpenWallet,
  onOpenAutopilot,
  vehicle,
  onOpenVehicle,
}: EVOwnerDashboardProps) {
  const [activeJourney, setActiveJourney] = useState<AutopilotTrip | null>(null);
  const [journeyRefreshError, setJourneyRefreshError] = useState('');
  const [showEndModal, setShowEndModal] = useState(false);
  const [endingJourney, setEndingJourney] = useState(false);

  const handleConfirmEndJourney = async () => {
    if (!activeJourney) return;
    setEndingJourney(true);
    try {
      await endAutopilotJourney(token, activeJourney.id);
      setActiveJourney(null);
      setShowEndModal(false);
    } catch (err) {
      console.error('Failed to end journey:', err);
    } finally {
      setEndingJourney(false);
    }
  };

  useEffect(() => {
    let mounted = true;

    const refreshJourney = async () => {
      try {
        const current = await getCurrentAutopilotTrip(token);
        if (!mounted) return;
        setActiveJourney(current);
        setJourneyRefreshError('');
      } catch (error) {
        if (!mounted) return;
        // Keep the most recent snapshot on screen when a poll fails. A transient
        // API/network issue must not make an active journey appear to disappear.
        setJourneyRefreshError(error instanceof Error ? error.message : 'Live journey refresh is temporarily unavailable.');
      }
    };

    void refreshJourney();
    const intervalId = window.setInterval(() => {
      if (document.visibilityState === 'visible') void refreshJourney();
    }, 15_000);
    const refreshOnFocus = () => void refreshJourney();
    window.addEventListener('focus', refreshOnFocus);

    return () => {
      mounted = false;
      window.clearInterval(intervalId);
      window.removeEventListener('focus', refreshOnFocus);
    };
  }, [token]);

  const selectedStation = chargers[0] || {
    id: 1,
    name: 'Green Park Station',
    hostName: 'Vidyut Hub',
    address: '1.2 km away • Green Park Extension',
    latitude: 28.5588,
    longitude: 77.2028,
    pricePerKwh: 18,
    connectorType: 'CCS2',
    powerKw: 150,
    available: true,
    rating: 4.9,
    reviewCount: 128,
    distance: '1.2 km',
    imageUrl: '',
  };

  const batteryPercent = vehicle?.batteryPercent == null ? null : Math.max(0, Math.min(100, vehicle.batteryPercent));
  const vehicleStatus = vehicle?.charging == null ? 'Status unknown' : vehicle.charging ? 'Charging' : 'Not charging';
  const vehicleStatusClass = vehicle?.charging == null ? 'unknown' : vehicle.charging ? 'charging' : 'idle';

  return (
    <div className="ev-dashboard-container">
      {/* Top Welcome Banner */}
      <div className="dashboard-header-row">
        <div className="welcome-text-group">
          <h1 className="welcome-heading">Good Morning, {user.name || 'Priyanshu'} 👋</h1>
          <p className="welcome-subheading">Where do you want to charge today?</p>
        </div>

      </div>

      {activeJourney ? (
        <ActiveJourneyAnalytics
          trip={activeJourney}
          refreshWarning={journeyRefreshError}
          onContinue={onOpenAutopilot}
          onEndJourney={() => setShowEndModal(true)}
          ending={endingJourney}
        />
      ) : (
        <button type="button" className="ev-autopilot-banner" onClick={onOpenAutopilot}>
          <span className="ev-autopilot-banner-icon"><Navigation size={22} /></span>
          <span className="ev-autopilot-banner-copy">
            <small><Sparkles size={12} /> NEW · VIDYUT AUTOPILOT</small>
            <strong>Tell us where you’re going. We’ll plan, reserve, reroute and pay.</strong>
          </span>
          <span className="ev-autopilot-banner-action">Plan a journey →</span>
        </button>
      )}

      {showEndModal && activeJourney && (
        <div className="end-journey-modal-overlay" onClick={(e) => { if (e.target === e.currentTarget) setShowEndModal(false); }}>
          <div className="end-journey-modal">
            <div className="end-modal-header">
              <div className="end-modal-icon"><XCircle size={24} /></div>
              <div>
                <h3>End this journey?</h3>
                <p>Your active navigation will stop and future charging reservations will be released.</p>
              </div>
            </div>
            <div className="end-modal-actions">
              <button
                type="button"
                className="btn-cancel-keep"
                onClick={() => setShowEndModal(false)}
                disabled={endingJourney}
              >
                Keep journey
              </button>
              <button
                type="button"
                className="btn-confirm-end"
                onClick={() => void handleConfirmEndJourney()}
                disabled={endingJourney}
              >
                {endingJourney ? 'Ending…' : 'End journey'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Main Top 3 Cards Grid */}
      <div className="ev-top-grid">
        {/* Card 1: Interactive Map Card */}
        <div className="dashboard-card map-card-container">
          <div className="map-view-canvas" style={{ position: 'relative', height: '100%', minHeight: 260, borderRadius: 16, overflow: 'hidden' }}>
            <MapContainer
              center={[selectedStation.latitude || 26.8467, selectedStation.longitude || 80.9462]}
              zoom={13}
              scrollWheelZoom={false}
              style={{ height: '100%', width: '100%', borderRadius: 16 }}
            >
              <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
                maxZoom={19}
              />
              <MiniMapResizer />
              {chargers.slice(0, 10).map((c) => (
                <LeafletMarker
                  key={c.id}
                  position={[c.latitude, c.longitude]}
                  icon={L.divIcon({
                    className: 'dashboard-station-pin',
                    html: `<div style="width: 28px; height: 28px; background: ${c.available ? '#00A86B' : '#F97316'}; border: 2.5px solid #fff; border-radius: 50%; display: flex; align-items: center; justify-content: center; box-shadow: 0 2px 8px rgba(0,0,0,0.3); color: #fff; font-size: 11px; font-weight: 800; cursor: pointer;">⚡</div>`,
                    iconSize: [28, 28],
                    iconAnchor: [14, 14],
                  })}
                  eventHandlers={{
                    click: () => onSelectCharger?.(c),
                  }}
                >
                  <LeafletPopup>
                    <div style={{ padding: 4, fontFamily: "'Plus Jakarta Sans', sans-serif" }}>
                      <strong style={{ fontSize: 13, color: '#0F172A' }}>{c.name}</strong>
                      <div style={{ fontSize: 11, color: '#64748B', margin: '2px 0' }}>{c.address}</div>
                      <div style={{ fontSize: 11, fontWeight: 700, color: '#00A86B' }}>⚡ {c.powerKw} kW · ₹{c.pricePerKwh}/kWh</div>
                      <button
                        type="button"
                        style={{ marginTop: 6, width: '100%', background: '#00A86B', color: '#fff', border: 'none', borderRadius: 6, padding: '4px 8px', fontSize: 11, fontWeight: 700, cursor: 'pointer' }}
                        onClick={() => onSelectCharger?.(c)}
                      >
                        ⚡ Select Charger
                      </button>
                    </div>
                  </LeafletPopup>
                </LeafletMarker>
              ))}
            </MapContainer>

            {/* Selected Floating Station Card Overlay */}
            <div className="map-overlay-card">
              <div className="overlay-thumb">
                <div className="charger-icon-badge">⚡</div>
              </div>
              <div className="overlay-details">
                <div className="overlay-title-row">
                  <h4 className="overlay-title">{selectedStation.name}</h4>
                  <span className="available-tag">
                    <span className="dot-active" /> Available
                  </span>
                </div>
                <p className="overlay-sub">{selectedStation.distance || '1.2 km away'}</p>
                <p className="overlay-power">DC Fast • {selectedStation.powerKw || 150} kW</p>
                <div style={{ display: 'flex', gap: 6, marginTop: 4 }}>
                  <button
                    type="button"
                    className="btn-overlay-details"
                    onClick={() => onSelectCharger?.(selectedStation)}
                  >
                    View Details
                  </button>
                  <button
                    type="button"
                    style={{ background: '#0F172A', color: '#fff', border: 'none', borderRadius: 8, padding: '6px 10px', fontSize: 11, fontWeight: 700, cursor: 'pointer' }}
                    onClick={onExploreChargers}
                  >
                    Full Map →
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Card 2: Quick Actions */}
        <div className="dashboard-card quick-actions-card">
          <h3 className="card-heading">Quick Actions</h3>
          <div className="quick-actions-grid">
            <button
              type="button"
              className="action-tile green-tile"
              onClick={onExploreChargers}
            >
              <div className="action-icon-bg green-icon">
                <MapPin size={22} />
              </div>
              <span>Find Charger</span>
            </button>

            <button
              type="button"
              className="action-tile green-light-tile"
              onClick={onBookNow}
            >
              <div className="action-icon-bg green-soft-icon">
                <Calendar size={22} />
              </div>
              <span>Book Now</span>
            </button>

            <button type="button" className="action-tile blue-tile" onClick={onOpenBookings}>
              <div className="action-icon-bg blue-icon">
                <Calendar size={22} />
              </div>
              <span>My Bookings</span>
            </button>

            <button type="button" className="action-tile purple-tile" onClick={onOpenWallet}>
              <div className="action-icon-bg purple-icon">
                <Wallet size={22} />
              </div>
              <span>Wallet</span>
            </button>
          </div>
        </div>

        {/* Card 3: Persisted vehicle status */}
        <div className="dashboard-card current-session-card">
          <div className="session-header-row">
            <div>
              <h3 className="card-heading">Vehicle status</h3>
              <p className="session-station-name">{vehicle?.makeAndModel || 'No vehicle selected'}</p>
            </div>
            <span className={`charging-status-badge ${vehicleStatusClass}`}>
              <span className="pulse-green" /> {vehicleStatus}
            </span>
          </div>

          <div className="session-metrics-row">
            <div className="metric-box">
              <span className="metric-label"><BatteryCharging size={12} /> Battery</span>
              <span className="metric-val">{batteryPercent == null ? 'Not synced' : `${batteryPercent}%`}</span>
            </div>
            <div className="metric-box">
              <span className="metric-label"><Route size={12} /> Range</span>
              <span className="metric-val">{vehicle?.remainingRangeKm == null ? 'Not synced' : `${Math.round(vehicle.remainingRangeKm)} km`}</span>
            </div>
            <div className="metric-box">
              <span className="metric-label"><Bluetooth size={12} /> Connection</span>
              <span className="metric-val">{vehicle?.telemetrySource === 'BLUETOOTH_DEMO' || vehicle?.registrationNumber?.startsWith('DEMO-') || vehicle?.registrationNumber?.startsWith('PRI-') ? 'Demo telemetry' : vehicle?.connectionStatus === 'CONNECTED' ? 'Connected' : vehicle?.connectionStatus === 'DISCONNECTED' ? 'Offline' : 'Not synced'}</span>
            </div>
          </div>

          <div className="battery-progress-section">
            <div className="battery-label-row">
              <span className="battery-percent">{batteryPercent == null ? 'Battery reading unavailable' : `${batteryPercent}% remaining`}</span>
              <span className="vehicle-status-source">{vehicle?.telemetrySource === 'BLUETOOTH_DEMO' ? 'Bluetooth demo' : vehicle?.telemetrySource === 'BLUETOOTH' ? 'Bluetooth' : vehicle?.telemetrySource === 'CHARGING_SESSION' ? 'Charging session' : vehicle?.telemetrySource === 'MANUAL' ? 'Manual' : 'No source'}</span>
            </div>
            <div className="battery-bar-track">
              <div className={`battery-bar-fill ${batteryPercent == null ? 'unknown' : ''}`} style={{ width: `${batteryPercent ?? 0}%` }} />
            </div>
          </div>

          <div className="dashboard-vehicle-summary">
            <p>{vehicle
              ? vehicle.charging
                ? 'The dashboard shows charging because the latest saved vehicle reading reports active charging.'
                : vehicle.charging === false
                  ? 'The latest saved vehicle reading reports that it is not charging.'
                  : 'Connect by Bluetooth or add a manual reading to determine its charging state.'
              : 'Add a vehicle to see battery, range and charging information here.'}</p>
            {vehicle && onOpenVehicle && (
              <button type="button" onClick={() => onOpenVehicle(vehicle.id)}>View vehicle <ChevronRight size={14} /></button>
            )}
          </div>
        </div>
      </div>

      {/* Bottom Section: Recent Bookings Table */}
      <div className="dashboard-card recent-bookings-card">
        <div className="bookings-table-header">
          <h3 className="card-heading">Recent Bookings</h3>
          <button type="button" className="btn-text-link" onClick={onOpenBookings}>View All</button>
        </div>

        <div className="table-responsive-wrapper">
          <table className="bookings-custom-table">
            <thead>
              <tr>
                <th>Bookings</th>
                <th>Time Elapsed</th>
                <th>Energy Delivered</th>
                <th>Amount</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>
                  <div className="booking-title-cell">
                    <div className="icon-warn-badge">
                      <AlertCircle size={16} />
                    </div>
                    <div>
                      <div className="cell-main-title">Cyber Hub Station</div>
                      <div className="cell-sub-info">21 May 2024 • 6:30 PM</div>
                    </div>
                  </div>
                </td>
                <td>
                  <span className="mono-time">00:32:45 - 6:30 PM</span>
                </td>
                <td>
                  <span>12.45 kWh • AC Charger</span>
                </td>
                <td>
                  <span className="font-semibold">Amount ₹285.60</span>
                </td>
                <td>
                  <span className="amount-highlight">₹186.40</span>
                </td>
              </tr>

              <tr>
                <td>
                  <div className="booking-title-cell">
                    <div className="icon-info-badge">
                      <Info size={16} />
                    </div>
                    <div>
                      <div className="cell-main-title">MG Road Station</div>
                      <div className="cell-sub-info">Maintenance scheduled • 21 May 2024 • 6:30 PM</div>
                    </div>
                  </div>
                </td>
                <td>
                  <span className="mono-time">21 May 2024 • 6:30 PM</span>
                </td>
                <td>
                  <span>1h 45m • AC Charger</span>
                </td>
                <td>
                  <span className="font-semibold">Amount ₹255.00</span>
                </td>
                <td>
                  <span className="status-pill-completed">Completed</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

const journeyStatus: Record<AutopilotTripStatus, { label: string; detail: string }> = {
  RESERVED: { label: 'Confirmed', detail: 'Reservations are protected and ready to start.' },
  MONITORING: { label: 'Journey live', detail: 'Telemetry, charger availability, battery and budget are being monitored.' },
  REROUTED: { label: 'Route updated', detail: 'Vidyut recovered the plan and reserved a compatible replacement.' },
  REROUTE_APPROVAL_REQUIRED: { label: 'Approve reroute', detail: 'A compatible replacement is ready and waiting for your permission.' },
  REPLAN_REQUIRED: { label: 'Safe stop needed', detail: 'No compatible replacement currently satisfies every journey limit.' },
  PAYMENT_REQUIRED: { label: 'Action needed', detail: 'The journey remains active, but charging payment needs attention.' },
  COMPLETED: { label: 'Completed', detail: 'The journey is complete.' },
  CANCELLED: { label: 'Cancelled', detail: 'The journey was cancelled.' },
};

function ActiveJourneyAnalytics({
  trip,
  refreshWarning,
  onContinue,
  onEndJourney,
  ending,
}: {
  trip: AutopilotTrip;
  refreshWarning: string;
  onContinue?: () => void;
  onEndJourney?: () => void;
  ending?: boolean;
}) {
  const status = journeyStatus[trip.status];
  const usableStops = trip.stops.filter((stop) => stop.status !== 'CANCELLED');
  const completedStops = usableStops.filter((stop) => stop.status === 'COMPLETED').length;
  const nextStop = usableStops.find((stop) => stop.status === 'RESERVED' || stop.status === 'PLANNED');
  const progress = usableStops.length > 0 ? Math.round((completedStops / usableStops.length) * 100) : 0;
  const batteryPoints = journeyBatteryPoints(trip);
  const svgPoints = batteryPoints.map((value, index) => {
    const x = batteryPoints.length === 1 ? 250 : 28 + (index * 444) / (batteryPoints.length - 1);
    return `${x.toFixed(1)},${batteryY(value).toFixed(1)}`;
  }).join(' ');
  const timeParts = [
    { label: 'Driving', value: trip.estimatedDriveMinutes, className: 'drive' },
    { label: 'Charging', value: trip.estimatedChargingMinutes, className: 'charge' },
    { label: 'Queue', value: trip.estimatedQueueMinutes, className: 'queue' },
    { label: 'Setup', value: trip.connectionOverheadMinutes, className: 'setup' },
  ];
  const timeTotal = Math.max(1, timeParts.reduce((sum, part) => sum + Math.max(0, part.value), 0));

  return (
    <section className={`dashboard-card active-journey-card status-${trip.status.toLowerCase()}`} aria-label="Active Autopilot journey">
      <div className="active-journey-head">
        <div className="active-journey-heading">
          <div className="active-journey-kicker"><Sparkles size={13} /> ACTIVE AUTOPILOT JOURNEY</div>
          <h2><span>{trip.origin}</span><ArrowRight size={18} /><span>{trip.destination}</span></h2>
          <p>{trip.telemetry.vehicleName} · {trip.telemetry.registrationNumber} · {status.detail}</p>
        </div>
        <div className="active-journey-actions">
          <span className={`active-journey-status status-${trip.status.toLowerCase()}`}><i />{status.label}</span>
          <button type="button" onClick={onContinue}><Navigation size={16} /> Continue journey <ChevronRight size={15} /></button>
          {onEndJourney && (
            <button
              type="button"
              className="btn-end-journey"
              onClick={onEndJourney}
              disabled={ending}
              title="End active journey early"
            >
              <XCircle size={15} /> End journey
            </button>
          )}
        </div>
      </div>

      {refreshWarning && (
        <div className="active-journey-refresh-warning" role="status">
          <RefreshCw size={13} /> Showing the last saved journey snapshot while live refresh reconnects.
        </div>
      )}

      <div className="active-journey-metrics">
        <JourneyMetric icon={<Route />} label="Charging stops" value={`${completedStops}/${usableStops.length}`} detail={`${Math.max(0, usableStops.length - completedStops)} remaining on this route`} />
        <JourneyMetric icon={<BatteryCharging />} label="Live battery" value={`${trip.telemetry.batteryPercent}%`} detail={`${trip.estimatedArrivalBatteryPercent}% planned at arrival`} />
        <JourneyMetric icon={<Clock3 />} label="Planned duration" value={formatJourneyMinutes(trip.totalDurationMinutes)} detail={`${formatJourneyMinutes(trip.estimatedChargingMinutes)} charging`} />
        <JourneyMetric icon={<IndianRupee />} label="Charging budget" value={`₹${trip.estimatedChargingCost.toFixed(0)}`} detail={`of ₹${trip.maximumChargingBudget.toFixed(0)}`} />
      </div>

      <div className="active-journey-progress" aria-label={`${completedStops} of ${usableStops.length} charging stops complete`}>
        <span style={{ width: `${progress}%` }} />
      </div>

      <div className="active-journey-analysis">
        <div className="journey-battery-chart">
          <div className="journey-analysis-head">
            <div><strong>Battery plan</strong><span>Current SoC through remaining stops</span></div>
            <span><ShieldCheck size={13} /> {trip.minimumArrivalBatteryPercent}% reserve</span>
          </div>
          <svg viewBox="0 0 500 145" role="img" aria-label="Planned battery percentage through the remaining journey">
            <defs>
              <linearGradient id={`journey-battery-${trip.id}`} x1="0" x2="1">
                <stop offset="0%" stopColor="#10b981" />
                <stop offset="100%" stopColor="#4f46e5" />
              </linearGradient>
            </defs>
            <line x1="28" y1={batteryY(100)} x2="472" y2={batteryY(100)} className="chart-grid" />
            <line x1="28" y1={batteryY(50)} x2="472" y2={batteryY(50)} className="chart-grid" />
            <line x1="28" y1={batteryY(trip.minimumArrivalBatteryPercent)} x2="472" y2={batteryY(trip.minimumArrivalBatteryPercent)} className="reserve-line" />
            <text x="2" y={batteryY(100) + 4}>100</text>
            <text x="8" y={batteryY(50) + 4}>50</text>
            <text x="8" y={batteryY(trip.minimumArrivalBatteryPercent) - 5}>RESERVE</text>
            <polyline points={svgPoints} fill="none" stroke={`url(#journey-battery-${trip.id})`} strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
            {batteryPoints.map((value, index) => {
              const x = batteryPoints.length === 1 ? 250 : 28 + (index * 444) / (batteryPoints.length - 1);
              return <circle key={`${value}-${index}`} cx={x} cy={batteryY(value)} r="4" />;
            })}
          </svg>
          <div className="journey-chart-axis"><span>Now · {trip.telemetry.batteryPercent}%</span><span>{nextStop ? `${usableStops.length - completedStops} stop${usableStops.length - completedStops === 1 ? '' : 's'} left` : 'Final leg'}</span><span>Arrive · {trip.estimatedArrivalBatteryPercent}%</span></div>
        </div>

        <div className="journey-time-analysis">
          <div className="journey-analysis-head">
            <div><strong>Time composition</strong><span>What makes up the door-to-door plan</span></div>
            <span>{formatJourneyMinutes(trip.totalDurationMinutes)}</span>
          </div>
          <div className="journey-time-stack" aria-label="Journey time composition">
            {timeParts.map((part) => <i key={part.label} className={part.className} style={{ width: `${(Math.max(0, part.value) / timeTotal) * 100}%` }} />)}
          </div>
          <div className="journey-time-legend">
            {timeParts.map((part) => (
              <span key={part.label}><i className={part.className} /><small>{part.label}</small><strong>{formatJourneyMinutes(part.value)}</strong></span>
            ))}
          </div>
          <div className={`journey-next-stop ${trip.status === 'PAYMENT_REQUIRED' ? 'warning' : ''}`}>
            <span><Zap size={17} /></span>
            <div><small>{trip.status === 'PAYMENT_REQUIRED' ? 'ACTION NEEDED' : 'NEXT CHARGING STOP'}</small><strong>{nextStop?.stationName ?? 'Destination is the next milestone'}</strong><p>{nextStop ? `${nextStop.connectorType} · ${nextStop.chargingMinutes} min charge · ₹${nextStop.estimatedCost.toFixed(0)}` : 'No more charging stops are required.'}</p></div>
          </div>
        </div>
      </div>
    </section>
  );
}

function JourneyMetric({ icon, label, value, detail }: { icon: React.ReactNode; label: string; value: string; detail: string }) {
  return <div className="active-journey-metric"><span>{icon}</span><div><small>{label}</small><strong>{value}</strong><p>{detail}</p></div></div>;
}

function journeyBatteryPoints(trip: AutopilotTrip) {
  const points = [trip.telemetry.batteryPercent];
  trip.stops
    .filter((stop) => stop.status !== 'COMPLETED' && stop.status !== 'CANCELLED')
    .forEach((stop) => points.push(stop.arrivalBatteryPercent, stop.targetBatteryPercent));
  points.push(trip.estimatedArrivalBatteryPercent);
  return points.map((point) => Math.max(0, Math.min(100, point)));
}

function batteryY(percent: number) {
  return 12 + ((100 - Math.max(0, Math.min(100, percent))) / 100) * 108;
}

function formatJourneyMinutes(minutes: number) {
  const rounded = Math.max(0, Math.round(minutes));
  const hours = Math.floor(rounded / 60);
  const remaining = rounded % 60;
  if (!hours) return `${remaining}m`;
  if (!remaining) return `${hours}h`;
  return `${hours}h ${remaining}m`;
}
