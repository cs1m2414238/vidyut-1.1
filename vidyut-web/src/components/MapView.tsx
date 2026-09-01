import React, { useEffect, useMemo, useRef, useState } from 'react';
import { MapContainer, TileLayer, Marker as LeafletMarker, Popup as LeafletPopup, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { LocateFixed } from 'lucide-react';
import { loadGoogleMaps, isGoogleMapsConfigured, onGoogleMapsAuthFailure, isGoogleMapsAuthFailed } from '../services/googleMapsLoader';
import type { Charger } from '../types';
import type { StationViewportBounds } from '../services/stations';

interface MapViewProps {
  chargers: Charger[];
  selectedId: number | null;
  onSelect: (charger: Charger) => void;
  filter: 'all' | 'available' | 'fast';
  onFilterChange: (filter: 'all' | 'available' | 'fast') => void;
  searchQuery?: string;
  center?: [number, number];
  compatibleConnector?: string;
  offline?: boolean;
  onBoundsChange?: (bounds: StationViewportBounds) => void;
}

type MapProviderMode = 'GOOGLE_MAPS' | 'LEAFLET_OSM';
type TileStyle = 'osm' | 'satellite';

// Custom SVG icon generator for Leaflet
const createLeafletCustomIcon = (charger: Charger, isSelected: boolean) => {
  const unavailable = charger.status === 'OFFLINE' || charger.status === 'MAINTENANCE' || charger.availability === 'UNAVAILABLE';
  const color = unavailable ? '#94A3B8' : charger.available ? '#00A86B' : '#F97316';
  const label = unavailable ? 'OFF' : charger.available ? `${charger.powerKw}k` : 'BUSY';
  const stroke = isSelected ? '#FFFFFF' : '#0F172A';
  const strokeWidth = isSelected ? 3.5 : 1.5;
  const scale = isSelected ? 'transform: scale(1.12); transform-origin: bottom center;' : '';

  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 44 54" width="44" height="54" style="${scale}">
      <defs>
        <filter id="shadow-${charger.id}" x="-20%" y="-20%" width="140%" height="140%">
          <feDropShadow dx="0" dy="3" stdDeviation="3" flood-color="#000000" flood-opacity="0.35"/>
        </filter>
      </defs>
      <path d="M22 2C10.95 2 2 10.95 2 22c0 15.5 20 30 20 30s20-14.5 20-30C42 10.95 33.05 2 22 2z" fill="${color}" stroke="${stroke}" stroke-width="${strokeWidth}" filter="url(#shadow-${charger.id})"/>
      <circle cx="22" cy="21" r="14" fill="#ffffff"/>
      <path d="M21 11l-6 11h6v9l7-11h-7V11z" fill="${color}"/>
      <rect x="6" y="38" width="32" height="12" rx="6" fill="#1E293B"/>
      <text x="22" y="47" font-size="8.5" font-family="system-ui, -apple-system, sans-serif" font-weight="900" fill="#ffffff" text-anchor="middle">${label}</text>
    </svg>
  `;

  return L.divIcon({
    className: 'custom-leaflet-pin',
    html: svg,
    iconSize: [44, 54],
    iconAnchor: [22, 54],
    popupAnchor: [0, -50],
  });
};

const normalizeConnector = (value?: string) => (value || '').toUpperCase().replace(/[^A-Z0-9]/g, '');

// Internal Leaflet Helper for resizing and animated centering
function LeafletMapController({
  center,
  selectedLocation,
}: {
  center: [number, number];
  selectedLocation?: [number, number] | null;
}) {
  const map = useMap();

  useEffect(() => {
    // Invalidate size on initial mount and when layout transitions complete
    map.invalidateSize();
    const t1 = setTimeout(() => map.invalidateSize(), 150);
    const t2 = setTimeout(() => map.invalidateSize(), 500);

    const onResize = () => map.invalidateSize();
    window.addEventListener('resize', onResize);

    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
      window.removeEventListener('resize', onResize);
    };
  }, [map]);

  useEffect(() => {
    if (selectedLocation) {
      map.flyTo(selectedLocation, 15, { duration: 0.8 });
    } else if (center) {
      map.panTo(center);
    }
  }, [map, center, selectedLocation]);

  return null;
}

function LeafletViewportReporter({ onBoundsChange }: { onBoundsChange?: (bounds: StationViewportBounds) => void }) {
  const map = useMap();

  useEffect(() => {
    if (!onBoundsChange) return undefined;
    let timer: number | undefined;
    const report = () => {
      window.clearTimeout(timer);
      timer = window.setTimeout(() => {
        const bounds = map.getBounds();
        onBoundsChange({
          minLat: bounds.getSouth(),
          maxLat: bounds.getNorth(),
          minLng: bounds.getWest(),
          maxLng: bounds.getEast(),
        });
      }, 300);
    };
    report();
    map.on('moveend zoomend', report);
    return () => {
      window.clearTimeout(timer);
      map.off('moveend zoomend', report);
    };
  }, [map, onBoundsChange]);

  return null;
}

export const MapView: React.FC<MapViewProps> = ({
  chargers,
  selectedId,
  onSelect,
  filter,
  onFilterChange,
  searchQuery = '',
  center = [26.8467, 80.9462],
  compatibleConnector,
  offline = false,
  onBoundsChange,
}) => {
  const [showMore, setShowMore] = useState(false);
  const [connector, setConnector] = useState('COMPATIBLE');
  const [maxPrice, setMaxPrice] = useState(50);
  const [minimumPower, setMinimumPower] = useState(0);
  const [tileStyle, setTileStyle] = useState<TileStyle>('osm');
  const [userLocation, setUserLocation] = useState<[number, number] | null>(null);
  const [locating, setLocating] = useState(false);

  const [preferredMode, setPreferredMode] = useState<MapProviderMode>('LEAFLET_OSM');
  const [googleMapsLoaded, setGoogleMapsLoaded] = useState(false);
  const [googleMapsError, setGoogleMapsError] = useState(isGoogleMapsAuthFailed());

  const googleMapRef = useRef<HTMLDivElement | null>(null);
  const googleMapInstanceRef = useRef<google.maps.Map | null>(null);
  const markersRef = useRef<google.maps.Marker[]>([]);
  const infoWindowRef = useRef<google.maps.InfoWindow | null>(null);
  const onBoundsChangeRef = useRef(onBoundsChange);
  const googleBoundsTimerRef = useRef<number | undefined>(undefined);

  useEffect(() => {
    onBoundsChangeRef.current = onBoundsChange;
  }, [onBoundsChange]);

  useEffect(() => () => window.clearTimeout(googleBoundsTimerRef.current), []);

  // Subscribe to Google Maps Auth Failure event (e.g. invalid key or unauth domain)
  useEffect(() => {
    const unsubscribe = onGoogleMapsAuthFailure(() => {
      setGoogleMapsError(true);
      setGoogleMapsLoaded(false);
      setPreferredMode('LEAFLET_OSM');
    });
    return unsubscribe;
  }, []);

  const normalizedQuery = searchQuery.trim().toLocaleLowerCase();
  const connectorOptions = useMemo(
    () => Array.from(new Set(chargers.map((c) => c.connectorType).filter(Boolean))).sort(),
    [chargers]
  );

  const filteredChargers = useMemo(() => {
    return chargers.filter((c) => {
      if (normalizedQuery && !`${c.name} ${c.address}`.toLocaleLowerCase().includes(normalizedQuery)) return false;
      if (filter === 'available' && !c.available) return false;
      if (filter === 'fast' && c.powerKw < 11) return false;
      if (c.pricePerKwh > maxPrice || c.powerKw < minimumPower) return false;
      const requiredConnector = connector === 'COMPATIBLE' ? compatibleConnector : connector === 'ALL' ? '' : connector;
      if (requiredConnector && normalizeConnector(c.connectorType) !== normalizeConnector(requiredConnector)) return false;
      return true;
    });
  }, [chargers, normalizedQuery, filter, maxPrice, minimumPower, connector, compatibleConnector]);

  const selectedCharger = useMemo(
    () => chargers.find((c) => c.id === selectedId) || null,
    [chargers, selectedId]
  );

  const activeCenter: [number, number] = useMemo(() => {
    if (userLocation) return userLocation;
    if (selectedCharger) return [selectedCharger.latitude, selectedCharger.longitude];
    return center;
  }, [userLocation, selectedCharger, center]);

  // Load Google Maps JS API when needed
  useEffect(() => {
    let isMounted = true;
    if (preferredMode === 'GOOGLE_MAPS' && isGoogleMapsConfigured() && !googleMapsError) {
      loadGoogleMaps()
        .then((maps) => {
          if (isMounted) {
            if (maps && !isGoogleMapsAuthFailed()) {
              setGoogleMapsLoaded(true);
              setGoogleMapsError(false);
            } else {
              setGoogleMapsLoaded(false);
              setGoogleMapsError(true);
              setPreferredMode('LEAFLET_OSM');
            }
          }
        })
        .catch(() => {
          if (isMounted) {
            setGoogleMapsLoaded(false);
            setGoogleMapsError(true);
            setPreferredMode('LEAFLET_OSM');
          }
        });
    }
    return () => {
      isMounted = false;
    };
  }, [preferredMode, googleMapsError]);

  // Active rendering mode
  const activeMode: MapProviderMode =
    preferredMode === 'GOOGLE_MAPS' && isGoogleMapsConfigured() && googleMapsLoaded && !googleMapsError
      ? 'GOOGLE_MAPS'
      : 'LEAFLET_OSM';

  // Locate User GPS
  const handleLocateMe = () => {
    if (!navigator.geolocation) {
      alert('Geolocation is not supported by your browser.');
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLocating(false);
        setUserLocation([pos.coords.latitude, pos.coords.longitude]);
      },
      (err) => {
        setLocating(false);
        console.warn('Geolocation error:', err);
        alert('Could not access current location. Please check location permissions.');
      },
      { enableHighAccuracy: true, timeout: 8000 }
    );
  };

  // Initialize and update Google Maps instance & markers if Google mode is active
  useEffect(() => {
    if (activeMode !== 'GOOGLE_MAPS' || !googleMapRef.current || !window.google?.maps) {
      return;
    }

    if (!googleMapInstanceRef.current) {
      try {
        const mapOptions: google.maps.MapOptions = {
          center: { lat: activeCenter[0], lng: activeCenter[1] },
          zoom: 13,
          mapTypeControl: true,
          zoomControl: true,
          streetViewControl: true,
          fullscreenControl: true,
        };

        if ('MapTypeControlStyle' in window.google.maps && 'ControlPosition' in window.google.maps) {
          mapOptions.mapTypeControlOptions = {
            style: window.google.maps.MapTypeControlStyle.HORIZONTAL_BAR,
            position: window.google.maps.ControlPosition.TOP_LEFT,
          };
          mapOptions.zoomControlOptions = {
            position: window.google.maps.ControlPosition.RIGHT_BOTTOM,
          };
          mapOptions.streetViewControlOptions = {
            position: window.google.maps.ControlPosition.RIGHT_BOTTOM,
          };
        }

        googleMapInstanceRef.current = new window.google.maps.Map(googleMapRef.current, mapOptions);
        infoWindowRef.current = new window.google.maps.InfoWindow();
        googleMapInstanceRef.current.addListener('idle', () => {
          window.clearTimeout(googleBoundsTimerRef.current);
          googleBoundsTimerRef.current = window.setTimeout(() => {
            const bounds = googleMapInstanceRef.current?.getBounds();
            const southWest = bounds?.getSouthWest();
            const northEast = bounds?.getNorthEast();
            if (southWest && northEast) {
              onBoundsChangeRef.current?.({
                minLat: southWest.lat(),
                maxLat: northEast.lat(),
                minLng: southWest.lng(),
                maxLng: northEast.lng(),
              });
            }
          }, 300);
        });
      } catch (err) {
        console.warn('Google Maps instantiation failed, falling back:', err);
        setGoogleMapsError(true);
        setPreferredMode('LEAFLET_OSM');
        return;
      }
    } else {
      googleMapInstanceRef.current.panTo({ lat: activeCenter[0], lng: activeCenter[1] });
    }

    const map = googleMapInstanceRef.current;
    const infoWindow = infoWindowRef.current;

    // Clear old markers
    markersRef.current.forEach((m) => m.setMap(null));
    markersRef.current = [];

    // Add new markers
    filteredChargers.forEach((c) => {
      const isSelected = c.id === selectedId;
      const unavailable = c.status === 'OFFLINE' || c.status === 'MAINTENANCE' || c.availability === 'UNAVAILABLE';
      const color = unavailable ? '#94A3B8' : c.available ? '#00A86B' : '#F97316';
      const badgeText = unavailable ? 'OFF' : c.available ? `${c.powerKw} kW` : 'BUSY';
      const stroke = isSelected ? '#FFFFFF' : '#1E293B';
      const strokeWidth = isSelected ? 3 : 1;

      const markerSvg = `
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 44 54" width="44" height="54">
          <defs>
            <filter id="gshadow-${c.id}" x="-20%" y="-20%" width="140%" height="140%">
              <feDropShadow dx="0" dy="2" stdDeviation="2.5" flood-color="#000000" flood-opacity="0.35"/>
            </filter>
          </defs>
          <path d="M22 2C10.95 2 2 10.95 2 22c0 15.5 20 30 20 30s20-14.5 20-30C42 10.95 33.05 2 22 2z" fill="${color}" stroke="${stroke}" stroke-width="${strokeWidth}" filter="url(#gshadow-${c.id})"/>
          <circle cx="22" cy="21" r="15" fill="#ffffff"/>
          <path d="M21 11l-6 11h6v9l7-11h-7V11z" fill="${color}"/>
          <rect x="7" y="38" width="30" height="12" rx="6" fill="#1E293B"/>
          <text x="22" y="47" font-size="8" font-family="system-ui, sans-serif" font-weight="bold" fill="#ffffff" text-anchor="middle">${badgeText}</text>
        </svg>
      `;

      const marker = new window.google.maps.Marker({
        position: { lat: c.latitude, lng: c.longitude },
        map,
        title: c.name,
        icon: {
          url: `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(markerSvg)}`,
          scaledSize: new window.google.maps.Size(44, 54),
          anchor: new window.google.maps.Point(22, 54),
        },
      });

      marker.addListener('click', () => {
        onSelect(c);
        if (infoWindow) {
          const contentString = `
            <div style="padding: 6px; font-family: 'Plus Jakarta Sans', system-ui, sans-serif; min-width: 190px;">
              <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 4px;">
                <strong style="font-size: 14px; color: #0F172A; font-weight: 800;">${c.name}</strong>
                <span style="font-size: 10px; font-weight: 800; padding: 2px 7px; border-radius: 999px; background: ${unavailable ? '#F1F5F9' : c.available ? '#EAF8F2' : '#FFF4E5'}; color: ${unavailable ? '#64748B' : c.available ? '#00A86B' : '#EA580C'};">
                  ${unavailable ? 'OFFLINE' : c.available ? 'AVAILABLE' : 'IN USE'}
                </span>
              </div>
              ${c.outletPartner ? `<div style="display: inline-block; margin-bottom: 4px; padding: 2px 6px; border-radius: 999px; color: #6D28D9; background: #F0E9FF; font-size: 9px; font-weight: 800;">${c.outletInstitutionName || 'Outlet Partner'}</div>` : ''}
              <div style="font-size: 11px; color: #64748B; margin-bottom: 6px;">${c.address}</div>
              <div style="display: flex; gap: 8px; font-size: 11px; font-weight: 700; color: #00A86B; margin-bottom: 6px;">
                <span>⚡ ${c.powerKw} kW</span>
                <span>🔌 ${c.connectorType}</span>
              </div>
              <div style="font-size: 13px; font-weight: 800; color: #0F172A; margin-bottom: 8px;">₹${c.pricePerKwh} / kWh</div>
              <button id="gmap-book-btn-${c.id}" style="width: 100%; background: #00A86B; color: #ffffff; border: none; border-radius: 8px; padding: 7px 10px; font-size: 12px; font-weight: 700; cursor: pointer;">
                ⚡ Book Charger Now
              </button>
            </div>
          `;
          infoWindow.setContent(contentString);
          infoWindow.open(map, marker);

          window.setTimeout(() => {
            const btn = document.getElementById(`gmap-book-btn-${c.id}`);
            if (btn) {
              btn.onclick = () => onSelect(c);
            }
          }, 50);
        }
      });

      markersRef.current.push(marker);
    });
  }, [activeMode, filteredChargers, activeCenter, selectedId, onSelect]);

  return (
    <div style={styles.container}>
      {/* Map Header & Provenance Bar */}
      <div style={styles.provenanceBar}>
        <div style={styles.provenanceTag}>
          {activeMode === 'GOOGLE_MAPS' ? (
            <span style={styles.googleActiveBadge}>
              <span style={styles.liveDot} />
              ROUTING & MAP ● Google Live Traffic Engine
            </span>
          ) : (
            <span style={styles.osmActiveBadge}>
              <span style={styles.resilientDot} />
              ROUTING & MAP ● Vidyut Resilient Engine (OpenStreetMap / OSRM)
            </span>
          )}
        </div>

        {/* Action Controls & Provider Switcher */}
        <div style={styles.modeSwitcher}>
          <button
            type="button"
            style={styles.actionToolBtn}
            onClick={handleLocateMe}
            title="Locate my position"
          >
            <LocateFixed size={14} color="#00A86B" />
            <span>{locating ? 'Locating…' : 'My Location'}</span>
          </button>

          {activeMode === 'LEAFLET_OSM' && (
            <div style={styles.tileStyleGroup}>
              <button
                type="button"
                style={{
                  ...styles.tileBtn,
                  ...(tileStyle === 'osm' ? styles.tileBtnActive : {}),
                }}
                onClick={() => setTileStyle('osm')}
                title="Standard OpenStreetMap"
              >
                OSM
              </button>
              <button
                type="button"
                style={{
                  ...styles.tileBtn,
                  ...(tileStyle === 'satellite' ? styles.tileBtnActive : {}),
                }}
                onClick={() => setTileStyle('satellite')}
                title="Satellite Imagery"
              >
                Satellite
              </button>
            </div>
          )}

          <button
            type="button"
            style={{
              ...styles.switchBtn,
              ...(preferredMode === 'GOOGLE_MAPS' ? styles.switchBtnActive : {}),
              ...(googleMapsError ? { opacity: 0.6, cursor: 'not-allowed' } : {}),
            }}
            onClick={() => {
              if (googleMapsError) {
                alert('Google Maps JS API key is restricted or unauthenticated. Using OpenStreetMap / OSRM resilient fallback.');
                return;
              }
              setPreferredMode('GOOGLE_MAPS');
            }}
            title={googleMapsError ? 'Google Maps unavailable (falling back to OSM)' : 'Use Google Maps'}
          >
            🗺️ Google Maps
          </button>
          <button
            type="button"
            style={{
              ...styles.switchBtn,
              ...(preferredMode === 'LEAFLET_OSM' ? styles.switchBtnActive : {}),
            }}
            onClick={() => setPreferredMode('LEAFLET_OSM')}
            title="Use OpenStreetMap / OSRM Resilience Fallback"
          >
            🧭 OpenStreetMap (OSRM)
          </button>
        </div>
      </div>

      {/* Filter Controls Bar */}
      <div style={styles.filterBar}>
        <button
          style={{
            ...styles.filterBtn,
            ...(filter === 'all' ? styles.filterBtnActive : {}),
          }}
          onClick={() => onFilterChange('all')}
        >
          ⚡ All ({chargers.length})
        </button>

        <button
          style={{
            ...styles.filterBtn,
            ...(filter === 'available' ? styles.filterBtnActive : {}),
          }}
          onClick={() => onFilterChange('available')}
        >
          🟢 Available ({chargers.filter((c) => c.available).length})
        </button>

        <button
          style={{
            ...styles.filterBtn,
            ...(filter === 'fast' ? styles.filterBtnActive : {}),
          }}
          onClick={() => onFilterChange('fast')}
        >
          🚀 Fast Charger (≥11kW)
        </button>

        <button
          style={{ ...styles.filterBtn, ...(showMore ? styles.filterBtnActive : {}) }}
          onClick={() => setShowMore((value) => !value)}
        >
          🎛️ More Filters
        </button>

        <div style={styles.resultsBadge}>
          {filteredChargers.length} stations in view
        </div>
      </div>

      {showMore && (
        <div style={styles.moreFilters}>
          <label style={styles.filterLabel}>
            Connector
            <select
              style={styles.select}
              value={connector}
              onChange={(event) => setConnector(event.target.value)}
            >
              <option value="COMPATIBLE">Compatible with {compatibleConnector || 'my vehicle'}</option>
              <option value="ALL">All connectors</option>
              {connectorOptions.map((item) => (
                <option key={item}>{item}</option>
              ))}
            </select>
          </label>
          <label style={styles.filterLabel}>
            Maximum ₹/kWh
            <input
              type="number"
              min="0"
              max="100"
              style={styles.input}
              value={maxPrice}
              onChange={(event) => setMaxPrice(Number(event.target.value))}
            />
          </label>
          <label style={styles.filterLabel}>
            Minimum speed
            <select
              style={styles.select}
              value={minimumPower}
              onChange={(event) => setMinimumPower(Number(event.target.value))}
            >
              <option value={0}>Any speed</option>
              <option value={7}>7+ kW</option>
              <option value={30}>30+ kW</option>
              <option value={60}>60+ kW</option>
              <option value={120}>120+ kW</option>
            </select>
          </label>
        </div>
      )}

      {offline && (
        <div style={styles.offlineBanner}>
          Offline — showing cached charging network. Booking actions will queue and synchronize when connection returns.
        </div>
      )}

      {/* Interactive Map View */}
      <div style={styles.mapContainer}>
        {activeMode === 'GOOGLE_MAPS' ? (
          <div ref={googleMapRef} style={{ width: '100%', height: '100%', borderRadius: 16 }} />
        ) : (
          <MapContainer
            center={activeCenter}
            zoom={13}
            scrollWheelZoom={true}
            style={{ height: '100%', width: '100%', borderRadius: 16 }}
          >
            <LeafletMapController
              center={activeCenter}
              selectedLocation={selectedCharger ? [selectedCharger.latitude, selectedCharger.longitude] : null}
            />
            <LeafletViewportReporter onBoundsChange={onBoundsChange} />

            {tileStyle === 'osm' && (
              <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a> contributors'
                url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
                maxZoom={19}
              />
            )}

            {tileStyle === 'satellite' && (
              <TileLayer
                attribution='Tiles &copy; Esri &mdash; Source: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and the GIS User Community'
                url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
                maxZoom={19}
              />
            )}

            {userLocation && (
              <LeafletMarker
                position={userLocation}
                icon={L.divIcon({
                  className: 'user-gps-pin',
                  html: `
                    <div style="position: relative; width: 24px; height: 24px;">
                      <div style="position: absolute; inset: 0; background: rgba(59, 130, 246, 0.35); border-radius: 50%; animation: pulse-ring 1.8s cubic-bezier(0.215, 0.61, 0.355, 1) infinite;"></div>
                      <div style="position: absolute; inset: 4px; background: #2563EB; border: 2.5px solid #FFFFFF; border-radius: 50%; box-shadow: 0 2px 6px rgba(0,0,0,0.3);"></div>
                    </div>
                  `,
                  iconSize: [24, 24],
                  iconAnchor: [12, 12],
                })}
              >
                <LeafletPopup>
                  <strong>Your Current Location</strong>
                </LeafletPopup>
              </LeafletMarker>
            )}

            {filteredChargers.map((c) => {
              const unavailable = c.status === 'OFFLINE' || c.status === 'MAINTENANCE' || c.availability === 'UNAVAILABLE';
              return (
                <LeafletMarker
                  key={c.id}
                  position={[c.latitude, c.longitude]}
                  icon={createLeafletCustomIcon(c, c.id === selectedId)}
                  eventHandlers={{
                    click: () => onSelect(c),
                  }}
                >
                  <LeafletPopup>
                    <div style={styles.popup}>
                      <div style={styles.popupHead}>
                        <div style={styles.popupTitle}>{c.name}</div>
                        <span
                          style={{
                            ...styles.popupBadge,
                            backgroundColor: unavailable ? '#F1F5F9' : c.available ? '#DCFCE7' : '#FFEDD5',
                            color: unavailable ? '#64748B' : c.available ? '#15803D' : '#C2410C',
                          }}
                        >
                          {unavailable ? 'OFFLINE' : c.available ? 'AVAILABLE' : 'BUSY'}
                        </span>
                      </div>

                      {c.outletPartner && (
                        <div style={styles.outletBadge}>{c.outletInstitutionName || 'Outlet partner'}</div>
                      )}
                      {c.demoData && <div style={styles.demoBadge}>SYNTHETIC DEMO STATION</div>}
                      <div style={styles.popupAddress}>{c.address}</div>
                      <div style={styles.popupMeta}>
                        <span>⚡ {c.powerKw} kW</span> • <span>🔌 {c.connectorType}</span>
                      </div>
                      <div style={styles.popupPriceRow}>
                        <span style={styles.popupPrice}>₹{c.pricePerKwh} / kWh</span>
                        <span style={styles.popupDistance}>⭐ {c.rating} ({c.reviewCount})</span>
                      </div>
                      <button
                        style={styles.popupBtn}
                        onClick={(e) => {
                          e.stopPropagation();
                          onSelect(c);
                        }}
                      >
                        ⚡ Book Charger Now
                      </button>
                    </div>
                  </LeafletPopup>
                </LeafletMarker>
              );
            })}
          </MapContainer>
        )}
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    height: '100%',
    minHeight: 520,
    display: 'flex',
    flexDirection: 'column',
    gap: 10,
  },
  provenanceBar: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 8,
    padding: '8px 12px',
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    border: '1px solid #E2E8F0',
    boxShadow: '0 1px 3px rgba(0,0,0,0.03)',
  },
  provenanceTag: {
    fontSize: 12,
    fontWeight: 700,
    display: 'flex',
    alignItems: 'center',
    gap: 6,
  },
  googleActiveBadge: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 6,
    color: '#15803D',
    fontWeight: 800,
  },
  osmActiveBadge: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 6,
    color: '#0369A1',
    fontWeight: 800,
  },
  liveDot: {
    width: 8,
    height: 8,
    borderRadius: '50%',
    backgroundColor: '#22C55E',
    boxShadow: '0 0 6px #22C55E',
    display: 'inline-block',
  },
  resilientDot: {
    width: 8,
    height: 8,
    borderRadius: '50%',
    backgroundColor: '#0284C7',
    boxShadow: '0 0 6px #0284C7',
    display: 'inline-block',
  },
  modeSwitcher: {
    display: 'flex',
    alignItems: 'center',
    gap: 6,
    flexWrap: 'wrap',
  },
  actionToolBtn: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 5,
    padding: '5px 10px',
    borderRadius: 8,
    border: '1px solid #D1FAE5',
    backgroundColor: '#ECFDF5',
    color: '#065F46',
    fontSize: 11,
    fontWeight: 700,
    cursor: 'pointer',
    transition: 'all 0.15s',
  },
  tileStyleGroup: {
    display: 'flex',
    gap: 2,
    backgroundColor: '#F1F5F9',
    padding: 2,
    borderRadius: 8,
  },
  tileBtn: {
    padding: '4px 8px',
    borderRadius: 6,
    fontSize: 10,
    fontWeight: 700,
    color: '#64748B',
    cursor: 'pointer',
    border: 'none',
    backgroundColor: 'transparent',
    transition: 'all 0.15s',
  },
  tileBtnActive: {
    backgroundColor: '#FFFFFF',
    color: '#0F172A',
    boxShadow: '0 1px 2px rgba(0,0,0,0.06)',
  },
  switchBtn: {
    padding: '5px 11px',
    borderRadius: 8,
    border: '1px solid #CBD5E1',
    backgroundColor: '#FFFFFF',
    color: '#475569',
    fontSize: 11,
    fontWeight: 700,
    cursor: 'pointer',
    transition: 'all 0.15s',
  },
  switchBtnActive: {
    backgroundColor: '#0F172A',
    color: '#FFFFFF',
    borderColor: '#0F172A',
  },
  filterBar: {
    display: 'flex',
    gap: 8,
    alignItems: 'center',
    flexWrap: 'wrap',
  },
  filterBtn: {
    padding: '8px 14px',
    borderRadius: 10,
    backgroundColor: '#fff',
    border: '1px solid #E2E8F0',
    fontSize: 12,
    fontWeight: 700,
    color: '#64748B',
    cursor: 'pointer',
    transition: 'all 0.15s',
  },
  filterBtnActive: {
    backgroundColor: '#00A86B',
    color: '#fff',
    borderColor: '#00A86B',
    boxShadow: '0 2px 6px rgba(0, 168, 107, 0.25)',
  },
  resultsBadge: {
    marginLeft: 'auto',
    fontSize: 11,
    fontWeight: 700,
    color: '#64748B',
    padding: '4px 10px',
    backgroundColor: '#F8FAFC',
    borderRadius: 999,
    border: '1px solid #E2E8F0',
  },
  mapContainer: {
    flex: 1,
    minHeight: 480,
    height: '100%',
    borderRadius: 16,
    overflow: 'hidden',
    border: '1px solid #E2E8F0',
    boxShadow: '0 4px 16px rgba(0,0,0,0.06)',
    position: 'relative',
    backgroundColor: '#E2E8F0',
  },
  moreFilters: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: 12,
    alignItems: 'center',
    padding: '10px 14px',
    border: '1px solid #E2E8F0',
    borderRadius: 12,
    background: '#fff',
  },
  filterLabel: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    color: '#475569',
    fontSize: 11,
    fontWeight: 700,
  },
  select: {
    minWidth: 160,
    padding: '6px 10px',
    border: '1px solid #CBD5E1',
    borderRadius: 8,
    color: '#1E293B',
    background: '#fff',
    fontSize: 12,
    fontWeight: 600,
  },
  input: {
    width: 90,
    padding: '6px 10px',
    border: '1px solid #CBD5E1',
    borderRadius: 8,
    color: '#1E293B',
    fontSize: 12,
    fontWeight: 600,
  },
  offlineBanner: {
    padding: '9px 14px',
    border: '1px solid #FDE68A',
    borderRadius: 10,
    color: '#92400E',
    background: '#FEF3C7',
    fontSize: 11,
    fontWeight: 700,
  },
  popup: {
    padding: 6,
    minWidth: 200,
    fontFamily: "'Plus Jakarta Sans', system-ui, sans-serif",
  },
  popupHead: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 6,
    marginBottom: 4,
  },
  popupTitle: {
    fontSize: 13,
    fontWeight: 800,
    color: '#0F172A',
  },
  popupBadge: {
    fontSize: 9,
    fontWeight: 800,
    padding: '2px 7px',
    borderRadius: 999,
  },
  outletBadge: {
    display: 'inline-block',
    margin: '2px 0 4px',
    padding: '2px 6px',
    borderRadius: 999,
    color: '#6D28D9',
    background: '#F0E9FF',
    fontSize: 9,
    fontWeight: 800,
  },
  demoBadge: {
    display: 'inline-block',
    margin: '2px 0 4px',
    padding: '2px 6px',
    borderRadius: 999,
    color: '#92400E',
    background: '#FEF3C7',
    fontSize: 9,
    fontWeight: 800,
  },
  popupAddress: {
    fontSize: 11,
    color: '#64748B',
    margin: '2px 0 6px',
  },
  popupMeta: {
    fontSize: 11,
    fontWeight: 700,
    color: '#00A86B',
    marginBottom: 4,
  },
  popupPriceRow: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    margin: '4px 0 8px',
  },
  popupPrice: {
    fontSize: 13,
    fontWeight: 800,
    color: '#0F172A',
  },
  popupDistance: {
    fontSize: 10,
    fontWeight: 700,
    color: '#64748B',
  },
  popupBtn: {
    width: '100%',
    backgroundColor: '#00A86B',
    color: '#ffffff',
    border: 'none',
    borderRadius: 8,
    padding: '8px 10px',
    fontSize: 11,
    fontWeight: 800,
    cursor: 'pointer',
    boxShadow: '0 2px 4px rgba(0, 168, 107, 0.2)',
  },
};
