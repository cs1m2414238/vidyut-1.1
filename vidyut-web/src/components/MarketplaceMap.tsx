import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import L from 'leaflet';
import { CircleMarker, MapContainer, Marker, Popup, TileLayer, useMap, useMapEvents } from 'react-leaflet';
import { Crosshair, LocateFixed, MapPin, Search } from 'lucide-react';
import { loadGoogleMaps, isGoogleMapsConfigured, onGoogleMapsAuthFailure, isGoogleMapsAuthFailed } from '../services/googleMapsLoader';
import type { MarketplaceStation, PropertyOpportunity } from '../services/marketplace';
import 'leaflet/dist/leaflet.css';

export interface PropertyMapSelection {
  latitude: number;
  longitude: number;
  address?: string;
  city?: string;
  state?: string;
  pincode?: string;
}

interface GeocodeAddress {
  road?: string;
  neighbourhood?: string;
  suburb?: string;
  city?: string;
  town?: string;
  village?: string;
  municipality?: string;
  county?: string;
  state?: string;
  postcode?: string;
}

interface GeocodeResult {
  lat: string;
  lon: string;
  display_name?: string;
  address?: GeocodeAddress;
}

const propertyPin = L.divIcon({
  className: 'marketplace-map-pin-shell',
  html: '<span class="marketplace-map-pin"><span></span></span>',
  iconSize: [34, 42],
  iconAnchor: [17, 42],
  popupAnchor: [0, -38],
});

const addressFields = (result: GeocodeResult): Omit<PropertyMapSelection, 'latitude' | 'longitude'> => {
  const address = result.address ?? {};
  const city = address.city ?? address.town ?? address.village ?? address.municipality ?? address.county;
  return {
    address: result.display_name,
    city,
    state: address.state,
    pincode: address.postcode,
  };
};

const locationSearchUrl = (query: string) =>
  `https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&countrycodes=in&limit=1&q=${encodeURIComponent(query)}`;

const reverseLookupUrl = (latitude: number, longitude: number) =>
  `https://nominatim.openstreetmap.org/reverse?format=jsonv2&addressdetails=1&zoom=18&lat=${latitude}&lon=${longitude}`;

// Helper to ensure Leaflet container renders tiles fully on mount
function LeafletContainerResizer() {
  const map = useMap();
  useEffect(() => {
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
  return null;
}

export function PropertyLocationPicker({
  latitude,
  longitude,
  address,
  onChange,
}: {
  latitude: number;
  longitude: number;
  address?: string;
  onChange: (selection: PropertyMapSelection) => void;
}) {
  const [query, setQuery] = useState(address ?? '');
  const [lookupState, setLookupState] = useState<'idle' | 'searching' | 'located'>('idle');
  const [message, setMessage] = useState('Search an area or click/drag the pin on the map.');
  const [googleLoaded, setGoogleLoaded] = useState(false);

  const googleContainerRef = useRef<HTMLDivElement | null>(null);
  const googleMapInstanceRef = useRef<google.maps.Map | null>(null);
  const googleMarkerRef = useRef<google.maps.Marker | null>(null);

  const validLatitude = Number.isFinite(latitude) ? latitude : 26.8467;
  const validLongitude = Number.isFinite(longitude) ? longitude : 80.9462;

  // Listen to Google Maps auth failure
  useEffect(() => {
    const unsub = onGoogleMapsAuthFailure(() => {
      setGoogleLoaded(false);
    });
    return unsub;
  }, []);

  // Check Google Maps JS API availability
  useEffect(() => {
    let mounted = true;
    if (isGoogleMapsConfigured() && !isGoogleMapsAuthFailed()) {
      loadGoogleMaps(['places', 'geometry'])
        .then((maps) => {
          if (mounted && maps && !isGoogleMapsAuthFailed()) {
            setGoogleLoaded(true);
          } else if (mounted) {
            setGoogleLoaded(false);
          }
        })
        .catch(() => {
          if (mounted) setGoogleLoaded(false);
        });
    }
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    if (address && !query) setQuery(address);
  }, [address, query]);

  const selectPoint = useCallback(async (nextLatitude: number, nextLongitude: number, lookupAddress = true) => {
    const coordinates = {
      latitude: Number(nextLatitude.toFixed(6)),
      longitude: Number(nextLongitude.toFixed(6)),
    };
    onChange(coordinates);
    setLookupState('searching');
    setMessage('Pin placed. Looking up the mapped address…');
    if (!lookupAddress) return;

    // Use Google Geocoder if available, else reverse lookup fallback
    if (googleLoaded && window.google?.maps?.Geocoder && !isGoogleMapsAuthFailed()) {
      try {
        const geocoder = new window.google.maps.Geocoder();
        const response = await geocoder.geocode({
          location: { lat: coordinates.latitude, lng: coordinates.longitude },
        });
        const result = response.results[0];
        if (result) {
          let city = '';
          let state = '';
          let pincode = '';
          result.address_components.forEach((c: google.maps.GeocoderAddressComponent) => {
            if (c.types.includes('locality')) city = c.long_name;
            if (c.types.includes('administrative_area_level_1')) state = c.long_name;
            if (c.types.includes('postal_code')) pincode = c.long_name;
          });
          const resolved = {
            ...coordinates,
            address: result.formatted_address,
            city: city || undefined,
            state: state || undefined,
            pincode: pincode || undefined,
          };
          onChange(resolved);
          setQuery(result.formatted_address);
          setLookupState('located');
          setMessage('Mapped address found via Google Maps. Confirm land details before saving.');
          return;
        }
      } catch (err) {
        console.warn('Google reverse geocoding fallback to Nominatim:', err);
      }
    }

    try {
      const response = await fetch(reverseLookupUrl(coordinates.latitude, coordinates.longitude), {
        headers: { Accept: 'application/json', 'Accept-Language': 'en-IN,en' },
      });
      if (!response.ok) throw new Error('Address lookup unavailable');
      const result = (await response.json()) as GeocodeResult;
      const resolved = { ...coordinates, ...addressFields(result) };
      onChange(resolved);
      if (result.display_name) setQuery(result.display_name);
      setLookupState('located');
      setMessage('Mapped address found. Confirm the land details below before publishing.');
    } catch {
      setLookupState('located');
      setMessage('Pin saved. Address lookup was unavailable, so enter the address fields manually.');
    }
  }, [googleLoaded, onChange]);

  const searchLocation = async () => {
    const value = query.trim();
    if (!value) {
      setMessage('Enter an area, landmark, road, or PIN code first.');
      return;
    }
    setLookupState('searching');
    setMessage('Searching the map…');

    // If Google Places / Geocoder is available
    if (googleLoaded && window.google?.maps?.Geocoder && !isGoogleMapsAuthFailed()) {
      try {
        const geocoder = new window.google.maps.Geocoder();
        const response = await geocoder.geocode({ address: value, componentRestrictions: { country: 'in' } });
        const result = response.results[0];
        if (result && result.geometry?.location) {
          const nextLat = result.geometry.location.lat();
          const nextLng = result.geometry.location.lng();
          let city = '';
          let state = '';
          let pincode = '';
          result.address_components.forEach((c: google.maps.GeocoderAddressComponent) => {
            if (c.types.includes('locality')) city = c.long_name;
            if (c.types.includes('administrative_area_level_1')) state = c.long_name;
            if (c.types.includes('postal_code')) pincode = c.long_name;
          });
          onChange({
            latitude: nextLat,
            longitude: nextLng,
            address: result.formatted_address,
            city: city || undefined,
            state: state || undefined,
            pincode: pincode || undefined,
          });
          setQuery(result.formatted_address);
          setLookupState('located');
          setMessage('Area located. Drag or click the pin precisely on the land parcel.');
          if (googleMapInstanceRef.current) {
            googleMapInstanceRef.current.panTo({ lat: nextLat, lng: nextLng });
            googleMapInstanceRef.current.setZoom(15);
          }
          return;
        }
      } catch (err) {
        console.warn('Google geocoder search fallback to Nominatim:', err);
      }
    }

    try {
      const response = await fetch(locationSearchUrl(value), {
        headers: { Accept: 'application/json', 'Accept-Language': 'en-IN,en' },
      });
      if (!response.ok) throw new Error('Location search unavailable');
      const results = (await response.json()) as GeocodeResult[];
      const result = results[0];
      if (!result) {
        setLookupState('idle');
        setMessage('No matching area found. Try a nearby landmark or place the pin manually.');
        return;
      }
      const nextLatitude = Number(result.lat);
      const nextLongitude = Number(result.lon);
      onChange({ latitude: nextLatitude, longitude: nextLongitude, ...addressFields(result) });
      if (result.display_name) setQuery(result.display_name);
      setLookupState('located');
      setMessage('Area found. Drag or click to place the pin precisely on the land parcel.');
    } catch {
      setLookupState('idle');
      setMessage('Map search is temporarily unavailable. You can still place the pin manually.');
    }
  };

  const useCurrentLocation = () => {
    if (!navigator.geolocation) {
      setMessage('Current location is not supported by this browser.');
      return;
    }
    setLookupState('searching');
    setMessage('Finding your current location…');
    navigator.geolocation.getCurrentPosition(
      (position) => void selectPoint(position.coords.latitude, position.coords.longitude),
      () => {
        setLookupState('idle');
        setMessage('Location permission was unavailable. Search or place the pin manually.');
      },
      { enableHighAccuracy: true, timeout: 10000 }
    );
  };

  // Google Maps setup and synchronization
  useEffect(() => {
    if (!googleLoaded || !googleContainerRef.current || !window.google?.maps || isGoogleMapsAuthFailed()) return;

    if (!googleMapInstanceRef.current) {
      try {
        const mapOptions: google.maps.MapOptions = {
          center: { lat: validLatitude, lng: validLongitude },
          zoom: 14,
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
        }

        const map = new window.google.maps.Map(googleContainerRef.current, mapOptions);

        map.addListener('click', (event: google.maps.MapMouseEvent) => {
          if (event.latLng) {
            void selectPoint(event.latLng.lat(), event.latLng.lng());
          }
        });

        const marker = new window.google.maps.Marker({
          position: { lat: validLatitude, lng: validLongitude },
          map,
          draggable: true,
          title: 'Selected land parcel location',
        });

        marker.addListener('dragend', (event: google.maps.MapMouseEvent) => {
          if (event.latLng) {
            void selectPoint(event.latLng.lat(), event.latLng.lng());
          }
        });

        googleMapInstanceRef.current = map;
        googleMarkerRef.current = marker;
      } catch (err) {
        console.warn('Google Map creation failed:', err);
        setGoogleLoaded(false);
      }
    } else {
      googleMapInstanceRef.current.panTo({ lat: validLatitude, lng: validLongitude });
      if (googleMarkerRef.current) {
        googleMarkerRef.current.setPosition({ lat: validLatitude, lng: validLongitude });
      }
    }
  }, [googleLoaded, validLatitude, validLongitude, selectPoint]);

  return (
    <section className="property-location-picker wide" aria-label="Land location map">
      <div className="marketplace-map-heading">
        <div>
          <span>
            <MapPin size={17} />
          </span>
          <div>
            <strong>Pin the land location</strong>
            <small>
              {googleLoaded ? 'Google Maps JS platform active.' : 'Coordinates captured from map pin.'}
            </small>
          </div>
        </div>
        <button type="button" onClick={useCurrentLocation} disabled={lookupState === 'searching'}>
          <LocateFixed size={15} /> Use my location
        </button>
      </div>
      <div className="marketplace-location-search">
        <Search size={16} />
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault();
              void searchLocation();
            }
          }}
          placeholder="Search area, landmark, road or PIN code"
        />
        <button type="button" onClick={() => void searchLocation()} disabled={lookupState === 'searching'}>
          {lookupState === 'searching' ? 'Finding…' : 'Find area'}
        </button>
      </div>

      <div className="property-location-map">
        {googleLoaded ? (
          <div ref={googleContainerRef} style={{ width: '100%', height: 320, borderRadius: 12 }} />
        ) : (
          <MapContainer
            center={[validLatitude, validLongitude]}
            zoom={14}
            scrollWheelZoom
            className="marketplace-leaflet-map"
          >
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a> contributors'
              url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
              maxZoom={19}
            />
            <LeafletContainerResizer />
            <MapPositionSync latitude={validLatitude} longitude={validLongitude} />
            <MapClickPicker onSelect={(lat, lng) => void selectPoint(lat, lng)} />
            <Marker
              position={[validLatitude, validLongitude]}
              icon={propertyPin}
              draggable
              eventHandlers={{
                dragend: (event) => {
                  const point = (event.target as L.Marker).getLatLng();
                  void selectPoint(point.lat, point.lng);
                },
              }}
            >
              <Popup>
                Selected land location
                <br />
                {validLatitude.toFixed(6)}, {validLongitude.toFixed(6)}
              </Popup>
            </Marker>
          </MapContainer>
        )}
        <div className="map-pin-instruction">
          <Crosshair size={14} /> Click or drag the pin for parcel-level accuracy
        </div>
      </div>
      <div className="map-selection-status" role="status">
        <span>
          {validLatitude.toFixed(6)}, {validLongitude.toFixed(6)}
        </span>
        <p>{message}</p>
      </div>
    </section>
  );
}

function MapClickPicker({ onSelect }: { onSelect: (latitude: number, longitude: number) => void }) {
  useMapEvents({ click: (event) => onSelect(event.latlng.lat, event.latlng.lng) });
  return null;
}

function MapPositionSync({ latitude, longitude }: { latitude: number; longitude: number }) {
  const map = useMap();
  useEffect(() => {
    map.setView([latitude, longitude], Math.max(map.getZoom(), 14), { animate: true });
  }, [latitude, longitude, map]);
  return null;
}

const distanceKm = (first: { latitude: number; longitude: number }, second: { latitude: number; longitude: number }) => {
  const radians = (degrees: number) => (degrees * Math.PI) / 180;
  const latitudeDelta = radians(second.latitude - first.latitude);
  const longitudeDelta = radians(second.longitude - first.longitude);
  const a =
    Math.sin(latitudeDelta / 2) ** 2 +
    Math.cos(radians(first.latitude)) * Math.cos(radians(second.latitude)) * Math.sin(longitudeDelta / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
};

const densityTone = (count: number) =>
  count === 0
    ? { label: 'Underserved', color: '#07865d' }
    : count <= 2
    ? { label: 'Low density', color: '#d68a05' }
    : { label: 'Higher density', color: '#dc4c3f' };

export function ChargerDensityMap({
  opportunities,
  stations,
  onContact,
}: {
  opportunities: PropertyOpportunity[];
  stations: MarketplaceStation[];
  onContact: (property: PropertyOpportunity) => void;
}) {
  const [radiusKm, setRadiusKm] = useState(5);
  const [googleLoaded, setGoogleLoaded] = useState(false);

  const googleContainerRef = useRef<HTMLDivElement | null>(null);
  const googleMapInstanceRef = useRef<google.maps.Map | null>(null);
  const googleOverlaysRef = useRef<Array<google.maps.Circle | google.maps.Marker>>([]);

  const activeStations = useMemo(() => stations.filter((station) => station.status === 'ACTIVE'), [stations]);
  const density = useMemo(
    () =>
      opportunities.map((property) => ({
        property,
        stationCount: activeStations.filter((station) => distanceKm(property, station) <= radiusKm).length,
      })),
    [activeStations, opportunities, radiusKm]
  );
  const underserved = density.filter((item) => item.stationCount === 0).length;
  const points = useMemo(() => [
    ...opportunities.map((item) => [item.latitude, item.longitude] as [number, number]),
    ...activeStations.map((item) => [item.latitude, item.longitude] as [number, number]),
  ], [opportunities, activeStations]);

  const center = useMemo<[number, number]>(() => points[0] ?? [26.8467, 80.9462], [points]);

  // Listen to Google Maps auth failure
  useEffect(() => {
    const unsub = onGoogleMapsAuthFailure(() => {
      setGoogleLoaded(false);
    });
    return unsub;
  }, []);

  // Check Google Maps JS API availability
  useEffect(() => {
    let mounted = true;
    if (isGoogleMapsConfigured() && !isGoogleMapsAuthFailed()) {
      loadGoogleMaps(['geometry'])
        .then((maps) => {
          if (mounted && maps && !isGoogleMapsAuthFailed()) {
            setGoogleLoaded(true);
          } else if (mounted) {
            setGoogleLoaded(false);
          }
        })
        .catch(() => {
          if (mounted) setGoogleLoaded(false);
        });
    }
    return () => {
      mounted = false;
    };
  }, []);

  // Google Maps Density circles & markers
  useEffect(() => {
    if (!googleLoaded || !googleContainerRef.current || !window.google?.maps || isGoogleMapsAuthFailed()) return;

    if (!googleMapInstanceRef.current) {
      try {
        const mapOptions: google.maps.MapOptions = {
          center: { lat: center[0], lng: center[1] },
          zoom: 10,
          mapTypeControl: true,
          zoomControl: true,
          fullscreenControl: true,
        };

        if ('MapTypeControlStyle' in window.google.maps && 'ControlPosition' in window.google.maps) {
          mapOptions.mapTypeControlOptions = {
            style: window.google.maps.MapTypeControlStyle.HORIZONTAL_BAR,
            position: window.google.maps.ControlPosition.TOP_LEFT,
          };
        }

        googleMapInstanceRef.current = new window.google.maps.Map(googleContainerRef.current, mapOptions);
      } catch (err) {
        console.warn('Google Map creation failed in Density Map:', err);
        setGoogleLoaded(false);
        return;
      }
    }

    const map = googleMapInstanceRef.current;
    googleOverlaysRef.current.forEach((item) => {
      if ('setMap' in item) item.setMap(null);
    });
    googleOverlaysRef.current = [];

    const bounds = new window.google.maps.LatLngBounds();
    const circleSymbolPath = window.google.maps.SymbolPath?.CIRCLE ?? 0;

    // Active stations
    activeStations.forEach((station) => {
      const pos = { lat: station.latitude, lng: station.longitude };
      bounds.extend(pos);

      const marker = new window.google.maps.Marker({
        position: pos,
        map,
        title: station.name,
        icon: {
          path: circleSymbolPath,
          scale: 6,
          fillColor: '#64748b',
          fillOpacity: 0.85,
          strokeColor: '#334155',
          strokeWeight: 2,
        },
      });
      googleOverlaysRef.current.push(marker);
    });

    // Opportunities with density tone circles
    density.forEach(({ property, stationCount }) => {
      const pos = { lat: property.latitude, lng: property.longitude };
      bounds.extend(pos);
      const tone = densityTone(stationCount);

      const circle = new window.google.maps.Circle({
        strokeColor: tone.color,
        strokeOpacity: 0.8,
        strokeWeight: 2,
        fillColor: tone.color,
        fillOpacity: 0.35,
        map,
        center: pos,
        radius: radiusKm * 1000,
      });

      const marker = new window.google.maps.Marker({
        position: pos,
        map,
        title: property.title,
        icon: {
          path: circleSymbolPath,
          scale: 10,
          fillColor: tone.color,
          fillOpacity: 1,
          strokeColor: '#ffffff',
          strokeWeight: 3,
        },
      });

      marker.addListener('click', () => {
        onContact(property);
      });

      googleOverlaysRef.current.push(circle);
      googleOverlaysRef.current.push(marker);
    });

    if (points.length > 1) {
      map.fitBounds(bounds);
    } else if (points.length === 1) {
      map.panTo({ lat: center[0], lng: center[1] });
      map.setZoom(12);
    }
  }, [googleLoaded, density, activeStations, points, center, radiusKm, onContact]);

  return (
    <section className="charger-density-panel">
      <div className="density-map-head">
        <div>
          <span>
            <MapPin size={20} />
          </span>
          <div>
            <small>NETWORK PLANNING MAP</small>
            <h2>Find low-density charger areas</h2>
            <p>Green Host sites have no active charger within the selected radius.</p>
          </div>
        </div>
        <aside>
          <strong>{underserved}</strong>
          <span>underserved sites</span>
        </aside>
      </div>
      <div className="density-map-toolbar">
        <div className="density-legend">
          <span className="underserved">Underserved</span>
          <span className="low">Low density</span>
          <span className="dense">Higher density</span>
          <span className="station">Existing charger</span>
        </div>
        <label>
          Density radius
          <select value={radiusKm} onChange={(event) => setRadiusKm(Number(event.target.value))}>
            <option value={3}>3 km</option>
            <option value={5}>5 km</option>
            <option value={10}>10 km</option>
            <option value={20}>20 km</option>
          </select>
        </label>
      </div>
      <div className="charger-density-map">
        {googleLoaded ? (
          <div ref={googleContainerRef} style={{ width: '100%', height: 380, borderRadius: 12 }} />
        ) : (
          <MapContainer center={center} zoom={10} scrollWheelZoom className="marketplace-leaflet-map">
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a> contributors'
              url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
              maxZoom={19}
            />
            <LeafletContainerResizer />
            <MapBoundsSync points={points} />
            {activeStations.map((station) => (
              <CircleMarker
                key={`station-${station.id}`}
                center={[station.latitude, station.longitude]}
                radius={6}
                pathOptions={{ color: '#334155', fillColor: '#64748b', fillOpacity: 0.85, weight: 2 }}
              >
                <Popup>
                  <strong>{station.name}</strong>
                  <br />
                  {station.city || station.address}
                  <br />
                  {station.availableSlots}/{station.totalSlots} slots available
                </Popup>
              </CircleMarker>
            ))}
            {density.map(({ property, stationCount }) => {
              const tone = densityTone(stationCount);
              return (
                <CircleMarker
                  key={`property-${property.id}`}
                  center={[property.latitude, property.longitude]}
                  radius={13}
                  pathOptions={{ color: '#fff', fillColor: tone.color, fillOpacity: 0.9, weight: 3 }}
                >
                  <Popup>
                    <div className="density-map-popup">
                      <strong>{property.title}</strong>
                      <span>{property.city || property.address}</span>
                      <b style={{ color: tone.color }}>
                        {tone.label} · {stationCount} active within {radiusKm} km
                      </b>
                      <small>
                        {property.parkingBays} bays · {property.availableLoadKw} kW grid load
                      </small>
                      <button type="button" onClick={() => onContact(property)}>
                        Review Host site
                      </button>
                    </div>
                  </Popup>
                </CircleMarker>
              );
            })}
          </MapContainer>
        )}
      </div>
      {!opportunities.length && (
        <p className="density-map-empty">No verified Host land is available for density analysis yet.</p>
      )}
    </section>
  );
}

function MapBoundsSync({ points }: { points: [number, number][] }) {
  const map = useMap();
  const pointsKey = points.map((point) => point.join(',')).join('|');
  useEffect(() => {
    if (!points.length) return;
    if (points.length === 1) map.setView(points[0], 12);
    else map.fitBounds(L.latLngBounds(points), { padding: [28, 28], maxZoom: 12 });
  }, [map, points, pointsKey]);
  return null;
}
