import { apiClient } from '../../services/apiClient';
import { Charger, ChargerSearchFilters } from './charger.types';
import { mockChargers } from './charger.mock';
import { CONFIG } from '../../constants/config';
import { ApiResponse, getApiErrorMessage, isNetworkError, unwrapApiResponse } from '../../services/apiResponse';
import { getCachedStations, saveCachedStations } from '../offline/offlineCache';

interface BackendConnector {
  type: string;
  powerKw: number;
  available: boolean;
  status?: string;
}

interface BackendStation {
  id: number;
  name: string;
  address: string;
  city?: string;
  latitude: number;
  longitude: number;
  pricePerKwh: number;
  rating: number;
  reviewCount: number;
  imageUrl?: string;
  status: 'ACTIVE' | 'MAINTENANCE' | 'OFFLINE';
  availability: 'AVAILABLE' | 'CHARGING' | 'RESERVED' | 'UNAVAILABLE';
  hostUserId?: number;
  connectors?: BackendConnector[];
  totalSlots?: number;
  availableSlots?: number;
  queueCount?: number;
  liveStatus?: Charger['status'];
  workingHours?: string;
  amenities?: string;
  chargingInstructions?: string;
  photoUrls?: string;
  distanceKm?: number;
  bookingSlotMinutes?: number;
  outletPartner?: boolean;
  outletInstitutionName?: string;
  outletIdVerificationRequired?: boolean;
}

function normalizeStation(station: BackendStation): Charger {
  const connector = [...(station.connectors || [])]
    .sort((left, right) => right.powerKw - left.powerKw)[0];

  return {
    id: station.id,
    name: station.name,
    hostName: station.hostUserId ? `Vidyut Host #${station.hostUserId}` : station.name,
    address: [station.address, station.city].filter(Boolean).join(', '),
    latitude: station.latitude,
    longitude: station.longitude,
    pricePerKwh: station.pricePerKwh,
    connectorType: connector?.type || 'TYPE2',
    powerKw: connector?.powerKw || 0,
    available: station.status === 'ACTIVE'
      && station.availability === 'AVAILABLE'
      && (connector?.available ?? true),
    rating: station.rating,
    reviewCount: station.reviewCount,
    imageUrl: station.imageUrl,
    city: station.city,
    status: station.liveStatus || (station.status !== 'ACTIVE' ? 'OFFLINE' : station.availability === 'AVAILABLE' ? 'AVAILABLE' : 'FULL'),
    totalSlots: station.totalSlots ?? station.connectors?.length ?? 0,
    availableSlots: station.availableSlots ?? (station.connectors || []).filter((item) => item.available).length,
    queueCount: station.queueCount ?? 0,
    workingHours: station.workingHours,
    amenities: station.amenities,
    chargingInstructions: station.chargingInstructions,
    photoUrls: station.photoUrls,
    connectors: station.connectors ?? [],
    distanceKm: station.distanceKm,
    bookingSlotMinutes: station.bookingSlotMinutes,
    outletPartner: station.outletPartner,
    outletInstitutionName: station.outletInstitutionName,
    outletIdVerificationRequired: station.outletIdVerificationRequired,
  };
}

export async function searchChargers(filters: ChargerSearchFilters): Promise<Charger[]> {
  try {
    const response = await apiClient.get<ApiResponse<BackendStation[]>>('/stations/search', { params: filters });
    const stations = unwrapApiResponse(response.data).map(normalizeStation);
    if (!filters.query && !filters.connectorType) await saveCachedStations(stations);
    return stations;
  } catch (error) {
    if (isNetworkError(error)) {
      const cached = await getCachedStations<Charger[]>();
      if (cached?.data.length) return cached.data;
    }
    if (CONFIG.USE_MOCK_DATA && isNetworkError(error)) return mockChargers;
    throw new Error(getApiErrorMessage(error, 'Unable to search charging stations.'));
  }
}

export async function getChargers(): Promise<Charger[]> {
  try {
    const response = await apiClient.get<ApiResponse<BackendStation[]>>('/stations');
    const stations = unwrapApiResponse(response.data).map(normalizeStation);
    await saveCachedStations(stations);
    return stations;
  } catch (error) {
    if (isNetworkError(error)) {
      const cached = await getCachedStations<Charger[]>();
      if (cached?.data.length) return cached.data;
    }
    if (CONFIG.USE_MOCK_DATA && isNetworkError(error)) return mockChargers;
    throw new Error(getApiErrorMessage(error, 'Unable to load charging stations.'));
  }
}

export async function getChargerById(id: number | string): Promise<Charger> {
  try {
    const response = await apiClient.get<ApiResponse<BackendStation>>(`/stations/${id}`);
    return normalizeStation(unwrapApiResponse(response.data));
  } catch (error) {
    if (CONFIG.USE_MOCK_DATA && isNetworkError(error)) {
      const found = mockChargers.find((charger) => charger.id.toString() === id.toString());
      if (found) return found;
    }
    throw new Error(getApiErrorMessage(error, 'Unable to load the charging station.'));
  }
}
