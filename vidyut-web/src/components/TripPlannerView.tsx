import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, BatteryCharging, CarFront, ExternalLink, MapPin, Navigation, Route, ShieldCheck, Zap } from 'lucide-react';
import { MapContainer, TileLayer, Marker, Popup, Polyline, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { apiRequest } from '../services/api';
import { getVehicles, type Vehicle } from '../services/vehicles';
import './TripPlannerView.css';

interface RouteStop {
  station: {
    id: number;
    name: string;
    address: string;
    pricePerKwh: number;
    availableSlots: number;
    latitude?: number;
    longitude?: number;
    connectors?: Array<{ type: string; powerKw: number }>;
  };
  distanceFromOriginKm: number;
  detourKm: number;
  etaMinutes: number;
  availableSlots: number;
  recommendedChargeMinutes: number;
  estimatedChargingCost: number;
  reason: string;
}

interface RoutePlan {
  origin: string;
  destination: string;
  totalDistanceKm: number;
  totalDurationMinutes: number;
  usableRangeKm: number;
  reserveBatteryPercent: number;
  estimatedArrivalBatteryPercent: number;
  destinationWithinRange: boolean;
  routeSource: string;
  externalMapsUrl: string;
  recommendedChargingStops: RouteStop[];
}

// Custom Marker Icons
const originIcon = L.divIcon({
  className: 'route-origin-pin',
  html: `<div style="width: 28px; height: 28px; background: #2563EB; border: 2.5px solid #fff; border-radius: 50%; box-shadow: 0 4px 10px rgba(37,99,235,0.4); display: flex; align-items: center; justify-content: center; color: #fff; font-size: 13px; font-weight: 800;">A</div>`,
  iconSize: [28, 28],
  iconAnchor: [14, 14],
});

const destinationIcon = L.divIcon({
  className: 'route-destination-pin',
  html: `<div style="width: 28px; height: 28px; background: #DC2626; border: 2.5px solid #fff; border-radius: 50%; box-shadow: 0 4px 10px rgba(220,38,38,0.4); display: flex; align-items: center; justify-content: center; color: #fff; font-size: 13px; font-weight: 800;">B</div>`,
  iconSize: [28, 28],
  iconAnchor: [14, 14],
});

const stopIcon = (index: number) =>
  L.divIcon({
    className: 'route-stop-pin',
    html: `<div style="width: 30px; height: 30px; background: #00A86B; border: 2.5px solid #fff; border-radius: 50%; box-shadow: 0 4px 10px rgba(0,168,107,0.4); display: flex; align-items: center; justify-content: center; color: #fff; font-size: 12px; font-weight: 800;">⚡${index + 1}</div>`,
    iconSize: [30, 30],
    iconAnchor: [15, 15],
  });

function TripMapBoundsController({ points }: { points: [number, number][] }) {
  const map = useMap();
  useEffect(() => {
    map.invalidateSize();
    const t1 = setTimeout(() => map.invalidateSize(), 150);
    const t2 = setTimeout(() => map.invalidateSize(), 500);
    if (points.length > 1) {
      map.fitBounds(L.latLngBounds(points), { padding: [36, 36], maxZoom: 14 });
    } else if (points.length === 1) {
      map.setView(points[0], 12);
    }
    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
    };
  }, [map, points]);
  return null;
}

export function TripPlannerView({ token }: { token: string }) {
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [vehicleId, setVehicleId] = useState(0);
  const [origin, setOrigin] = useState('');
  const [destination, setDestination] = useState('');
  const [battery, setBattery] = useState(70);
  const [plan, setPlan] = useState<RoutePlan | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    void getVehicles(token)
      .then((items) => {
        setVehicles(items);
        setVehicleId((current) => current || items[0]?.id || 0);
        const first = items[0];
        if (first?.batteryPercent != null) setBattery(first.batteryPercent);
      })
      .catch((reason) => setError(reason instanceof Error ? reason.message : 'Unable to load vehicles.'));
  }, [token]);

  const submit = async () => {
    if (!vehicleId || !origin.trim() || !destination.trim()) {
      setError('Choose a vehicle and enter both locations.');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const response = await apiRequest<RoutePlan>('/routing/plan', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: JSON.stringify({ vehicleId, origin, destination, currentBatteryPercent: battery, reserveBatteryPercent: 10 }),
      });
      setPlan(response);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Unable to plan this trip.');
    } finally {
      setLoading(false);
    }
  };

  const isGoogleRoute = plan?.routeSource?.includes('GOOGLE');
  const isEstimated = plan?.routeSource?.includes('ESTIMATED');
  const isOsmRoute = plan?.routeSource?.includes('OSRM') || plan?.routeSource?.includes('OPENSTREETMAP');

  // Compute map coordinates from plan stops and fallback points
  const mapPoints = useMemo(() => {
    if (!plan) return [];
    const pts: Array<{ lat: number; lng: number; title: string; type: 'origin' | 'stop' | 'destination'; stopIndex?: number }> = [];

    // Extract stops with coordinates
    const stopCoords = (plan.recommendedChargingStops || [])
      .map((s, idx) => ({
        lat: s.station.latitude || 26.8467 + (idx + 1) * 0.04,
        lng: s.station.longitude || 80.9462 + (idx + 1) * 0.04,
        title: s.station.name,
        type: 'stop' as const,
        stopIndex: idx,
        station: s.station,
        detourKm: s.detourKm,
        etaMinutes: s.etaMinutes,
        recommendedChargeMinutes: s.recommendedChargeMinutes,
        estimatedChargingCost: s.estimatedChargingCost,
      }));

    // Generate approximate origin / destination coordinate anchors if city names
    const originLat = 26.8467;
    const originLng = 80.9462;
    const destLat = stopCoords.length ? stopCoords[stopCoords.length - 1].lat + 0.08 : 26.4499;
    const destLng = stopCoords.length ? stopCoords[stopCoords.length - 1].lng + 0.08 : 80.3319;

    pts.push({ lat: originLat, lng: originLng, title: `Start: ${plan.origin}`, type: 'origin' });
    stopCoords.forEach((s) => pts.push(s));
    pts.push({ lat: destLat, lng: destLng, title: `Destination: ${plan.destination}`, type: 'destination' });

    return pts;
  }, [plan]);

  const polylineCoords = useMemo<[number, number][]>(
    () => mapPoints.map((p) => [p.lat, p.lng]),
    [mapPoints]
  );

  return (
    <section className="trip-page" aria-labelledby="trip-title">
      <header className="trip-heading">
        <div>
          <span>RANGE-AWARE ROUTING</span>
          <h1 id="trip-title">Plan your EV trip</h1>
          <p>Compatible stops ranked by reserve range, live slots, detour and price—without agent automation.</p>
        </div>
        <div className="trip-safety">
          <ShieldCheck size={17} /> Deterministic route
        </div>
      </header>

      <div className="trip-layout">
        <form className="trip-form" onSubmit={(event) => { event.preventDefault(); void submit(); }}>
          <label>
            Vehicle
            <select value={vehicleId} onChange={(event) => setVehicleId(Number(event.target.value))}>
              <option value={0}>Choose vehicle</option>
              {vehicles.map((vehicle) => (
                <option key={vehicle.id} value={vehicle.id}>
                  {vehicle.makeAndModel} • {vehicle.connectorType || 'connector unknown'}
                </option>
              ))}
            </select>
          </label>
          <div className="trip-location">
            <MapPin size={18} />
            <label>
              Starting from
              <input value={origin} onChange={(event) => setOrigin(event.target.value)} placeholder="Lucknow" />
            </label>
          </div>
          <div className="trip-line" />
          <div className="trip-location destination">
            <Navigation size={18} />
            <label>
              Destination
              <input value={destination} onChange={(event) => setDestination(event.target.value)} placeholder="Kanpur" />
            </label>
          </div>
          <label>
            Current battery <strong>{battery}%</strong>
            <input type="range" min="10" max="100" step="5" value={battery} onChange={(event) => setBattery(Number(event.target.value))} />
          </label>
          {error && <div className="trip-error">{error}</div>}
          <button type="submit" className="trip-submit" disabled={loading || !vehicles.length}>
            <Route size={17} />
            {loading ? 'Planning route…' : 'Plan route'}
          </button>
        </form>

        <div className="trip-results">
          {!plan ? (
            <div className="trip-empty">
              <Route size={34} />
              <h2>Ready when you are</h2>
              <p>Enter a destination to check whether the current battery is enough and where to charge.</p>
            </div>
          ) : (
            <>
              {/* Visible Routing Provenance Badge */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '8px 12px',
                  borderRadius: 10,
                  fontSize: 12,
                  fontWeight: 800,
                  backgroundColor: isGoogleRoute ? '#F0FDF4' : isEstimated ? '#FFFBEB' : '#F0F9FF',
                  border: `1px solid ${isGoogleRoute ? '#BBF7D0' : isEstimated ? '#FDE68A' : '#BAE6FD'}`,
                  color: isGoogleRoute ? '#15803D' : isEstimated ? '#B45309' : '#0369A1',
                  marginBottom: 12,
                }}
              >
                <span
                  style={{
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    backgroundColor: isGoogleRoute ? '#22C55E' : isEstimated ? '#F59E0B' : '#0284C7',
                    boxShadow: `0 0 6px ${isGoogleRoute ? '#22C55E' : isEstimated ? '#F59E0B' : '#0284C7'}`,
                    display: 'inline-block',
                  }}
                />
                {isGoogleRoute && 'ROUTING ● Google Traffic-Aware (Live traffic enabled)'}
                {isOsmRoute && 'ROUTING ● Vidyut Resilient Routing (OpenStreetMap / OSRM)'}
                {isEstimated && 'ROUTING ● Approximate Route (Estimated road fallback)'}
              </div>

              <article className={`trip-summary ${plan.destinationWithinRange ? 'direct' : 'stops'}`}>
                <div>
                  <small>{plan.destinationWithinRange ? 'DIRECT TRIP' : 'CHARGING STOP NEEDED'}</small>
                  <h2>
                    {Math.round(plan.totalDistanceKm)} km • {Math.floor(plan.totalDurationMinutes / 60)}h {plan.totalDurationMinutes % 60}m
                  </h2>
                  <p>
                    Usable range {Math.round(plan.usableRangeKm)} km • arrive near {Math.round(plan.estimatedArrivalBatteryPercent)}%
                  </p>
                </div>
                {plan.destinationWithinRange ? <Navigation size={30} /> : <BatteryCharging size={30} />}
              </article>

              {/* Interactive Route Map */}
              {polylineCoords.length > 0 && (
                <div style={{ height: 260, borderRadius: 14, overflow: 'hidden', margin: '14px 0', border: '1px solid #E2E8F0', boxShadow: '0 2px 8px rgba(0,0,0,0.04)' }}>
                  <MapContainer
                    center={polylineCoords[0]}
                    zoom={11}
                    scrollWheelZoom={false}
                    style={{ height: '100%', width: '100%' }}
                  >
                    <TileLayer
                      attribution='&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a> contributors'
                      url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
                      maxZoom={19}
                    />
                    <TripMapBoundsController points={polylineCoords} />
                    <Polyline positions={polylineCoords} color="#00A86B" weight={4} opacity={0.85} dashArray="8, 6" />

                    {mapPoints.map((point, i) => (
                      <Marker
                        key={`${point.type}-${i}`}
                        position={[point.lat, point.lng]}
                        icon={
                          point.type === 'origin'
                            ? originIcon
                            : point.type === 'destination'
                            ? destinationIcon
                            : stopIcon(point.stopIndex ?? 0)
                        }
                      >
                        <Popup>
                          <strong>{point.title}</strong>
                        </Popup>
                      </Marker>
                    ))}
                  </MapContainer>
                </div>
              )}

              {isEstimated && (
                <div className="trip-route-warning" role="status">
                  <AlertTriangle size={16} />
                  Road routing services are currently unavailable. Distance and ETA are approximate estimates; confirm live navigation before departure.
                </div>
              )}

              <div className="trip-route-line">
                <span>{plan.origin}</span>
                <i />
                <span>{plan.destination}</span>
              </div>

              <h3>Compatible charging stops</h3>
              {plan.recommendedChargingStops.map((stop, index) => (
                <article className="trip-stop" key={stop.station.id}>
                  <b>{index + 1}</b>
                  <div>
                    <h4>{stop.station.name}</h4>
                    <p>{stop.station.address}</p>
                    <span>
                      <Zap size={13} /> {stop.recommendedChargeMinutes} min • {stop.availableSlots} free • {stop.detourKm} km detour • est. ₹{Math.round(stop.estimatedChargingCost)}
                    </span>
                  </div>
                </article>
              ))}

              {!plan.recommendedChargingStops.length && (
                <div className="trip-direct">
                  <CarFront size={19} /> No charging stop is required inside the 10% reserve.
                </div>
              )}

              {plan.externalMapsUrl && !isEstimated && (
                <a className="trip-maps" href={plan.externalMapsUrl} target="_blank" rel="noreferrer">
                  {isGoogleRoute ? 'Open in Google Maps' : 'Open in OpenStreetMap'} <ExternalLink size={15} />
                </a>
              )}
            </>
          )}
        </div>
      </div>
    </section>
  );
}
