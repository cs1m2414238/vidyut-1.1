import { Charger } from '../chargers/charger.types';

export interface RouteStop {
  station: Charger; distanceFromOriginKm: number; recommendedChargeMinutes: number; detourKm: number;
  etaMinutes: number; availableSlots: number; connectorMatched: boolean; estimatedChargingCost: number; reason: string;
}
export interface RoutePlan {
  origin: string; destination: string; totalDistanceKm: number; totalDurationMinutes: number; vehicleId: number;
  usableRangeKm: number; reserveBatteryPercent: number; estimatedArrivalBatteryPercent: number;
  destinationWithinRange: boolean; routeSource: string; externalMapsUrl: string; recommendedChargingStops: RouteStop[];
}
export interface RouteStatus {
  bookingId: number; stationId: number; stationStatus: string; diversionRecommended: boolean; reason: string; alternatives: RouteStop[];
}
