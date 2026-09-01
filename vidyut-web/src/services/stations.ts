import type { Charger } from '../types';
import { apiRequest } from './api';

interface StationConnector {
  id: number;
  type: string;
  powerKw: number;
  available: boolean;
  status: string;
  maintenanceMode: boolean;
}

export interface StationResponse {
  id: number;
  name: string;
  address: string;
  city?: string | null;
  latitude: number;
  longitude: number;
  pricePerKwh: number;
  rating: number;
  reviewCount: number;
  imageUrl?: string | null;
  bookingSlotMinutes?: number;
  status?: string;
  availability: string;
  emergencyDisabled: boolean;
  demoData?: boolean;
  outletPartner?: boolean;
  outletInstitutionName?: string | null;
  outletIdVerificationRequired?: boolean;
  amenities?: string | null;
  workingHours?: string | null;
  connectors?: StationConnector[];
}

export interface StationViewportBounds {
  minLat: number;
  maxLat: number;
  minLng: number;
  maxLng: number;
}

const INDIA_VIEWPORT_BOUNDS: StationViewportBounds = {
  minLat: 6,
  maxLat: 38,
  minLng: 68,
  maxLng: 98,
};

const fallbackImage = 'https://images.unsplash.com/photo-1563720223185-11003d516935?auto=format&fit=crop&w=800&q=80';

function connectorLabel(value?: string): string {
  if (!value) return 'Connector pending';
  return value === 'TYPE2' ? 'Type 2' : value === 'CHADEMO' ? 'CHAdeMO' : value.replaceAll('_', ' ');
}

export function getStations(bounds: StationViewportBounds = INDIA_VIEWPORT_BOUNDS, limit = 250): Promise<StationResponse[]> {
  const params = new URLSearchParams({
    minLat: String(bounds.minLat),
    maxLat: String(bounds.maxLat),
    minLng: String(bounds.minLng),
    maxLng: String(bounds.maxLng),
    limit: String(Math.max(1, Math.min(limit, 500))),
  });
  return apiRequest<StationResponse[]>(`/stations?${params}`, { method: 'GET' });
}

export function stationToCharger(station: StationResponse): Charger {
  const connectors = station.connectors ?? [];
  const usableConnectors = connectors.filter(
    (connector) => connector.available && connector.status !== 'OFFLINE' && !connector.maintenanceMode,
  );
  const primaryConnector = [...(usableConnectors.length ? usableConnectors : connectors)]
    .sort((left, right) => right.powerKw - left.powerKw)[0];

  return {
    id: station.id,
    name: station.name,
    hostName: 'Vidyut verified host',
    address: station.address,
    latitude: station.latitude,
    longitude: station.longitude,
    pricePerKwh: station.pricePerKwh,
    connectorType: connectorLabel(primaryConnector?.type),
    powerKw: primaryConnector?.powerKw || 7.4,
    available: station.availability === 'AVAILABLE' && !station.emergencyDisabled && usableConnectors.length > 0,
    rating: station.rating,
    reviewCount: station.reviewCount,
    distance: station.city || 'Vidyut network',
    imageUrl: station.imageUrl || fallbackImage,
    bookingSlotMinutes: station.bookingSlotMinutes || 30,
    status: station.status,
    availability: station.availability,
    outletPartner: Boolean(station.outletPartner),
    outletInstitutionName: station.outletInstitutionName || undefined,
    demoData: Boolean(station.demoData),
  };
}
