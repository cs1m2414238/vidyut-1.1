export interface CreateBookingRequest {
  stationId: number | string;
  vehicleId?: number;
  startTime: string;
  durationMinutes: number;
  idempotencyKey?: string;
}

export interface BookingItem {
  id: string | number;
  stationId: string | number;
  vehicleId?: number;
  chargerName: string;
  address: string;
  startTime: string;
  durationMinutes: number;
  totalCost: number;
  endTime?: string;
  kwhDelivered?: number;
  cancellationFee?: number;
  refundAmount?: number;
  outletId?: number;
  outletTierName?: string;
  appliedRatePerKwh?: number;
  status: 'PENDING' | 'CONFIRMED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | 'EXPIRED';
  createdAt: string;
}

export interface BookingSlot { startTime: string; endTime: string; availableConnectors: number; available: boolean }

export interface WaitlistItem {
  id: number; stationId: number; stationName: string; vehicleId?: number; preferredStartTime?: string;
  durationMinutes: number; position: number; status: string; createdAt: string;
}
