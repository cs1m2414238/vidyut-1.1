export interface ChargingSession {
  id: number; bookingId: number; stationId: number; stationName: string; vehicleId?: number; vehicleName?: string;
  status: 'ACTIVE' | 'COMPLETED'; paymentStatus: 'DUE' | 'PAID'; powerKw: number; energyKwh: number; cost: number;
  co2SavedKg: number; startBatteryPercent: number; currentBatteryPercent: number; targetBatteryPercent: number;
  startedAt: string; estimatedCompletionAt: string; completedAt?: string; updatedAt: string;
  telemetrySource?: string;
}
