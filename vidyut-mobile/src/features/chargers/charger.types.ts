export interface Charger {
  id: number | string;
  name: string;
  hostName: string;
  address: string;
  latitude: number;
  longitude: number;
  pricePerKwh: number;
  connectorType: 'TYPE_2' | 'CCS2' | 'CHAdeMO' | string;
  powerKw: number;
  available: boolean;
  rating: number;
  reviewCount?: number;
  distance?: string;
  imageUrl?: string;
  description?: string;
  city?: string;
  status: 'AVAILABLE' | 'QUEUE' | 'FULL' | 'OFFLINE';
  totalSlots: number;
  availableSlots: number;
  queueCount: number;
  workingHours?: string;
  amenities?: string;
  chargingInstructions?: string;
  photoUrls?: string;
  connectors: Array<{ type: string; powerKw: number; available: boolean; status?: string }>;
  distanceKm?: number;
  bookingSlotMinutes?: number;
  outletPartner?: boolean;
  outletInstitutionName?: string;
  outletIdVerificationRequired?: boolean;
}

export interface ChargerSearchFilters {
  query?: string;
  connectorType?: string;
  lat?: number;
  lng?: number;
  radius?: number;
  minAvailableSlots?: number;
  maxPricePerKwh?: number;
  minPowerKw?: number;
  availableOnly?: boolean;
}
