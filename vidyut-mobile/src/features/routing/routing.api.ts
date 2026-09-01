import { apiClient } from '../../services/apiClient';
import { ApiResponse, getApiErrorMessage, unwrapApiResponse } from '../../services/apiResponse';
import { RoutePlan, RouteStatus } from './routing.types';

export async function planRoute(request: { origin: string; destination: string; vehicleId: number; currentBatteryPercent: number;
  originLatitude?: number; originLongitude?: number; destinationLatitude?: number; destinationLongitude?: number; reserveBatteryPercent?: number }): Promise<RoutePlan> {
  try { const response = await apiClient.post<ApiResponse<RoutePlan>>('/routing/plan', request); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to plan this trip.')); }
}
export async function getRouteStatus(bookingId: number): Promise<RouteStatus> {
  try { const response = await apiClient.get<ApiResponse<RouteStatus>>(`/routing/status/${bookingId}`); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to refresh route status.')); }
}
export async function divertBooking(bookingId: number, alternativeStationId: number): Promise<void> {
  try { await apiClient.post(`/routing/divert/${bookingId}`, { alternativeStationId }); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to divert the booking.')); }
}
export async function getRouteAlternatives(stationId: number | string, vehicleId?: number): Promise<RoutePlan['recommendedChargingStops']> {
  try { const response = await apiClient.get<ApiResponse<RoutePlan['recommendedChargingStops']>>('/routing/alternatives', { params: { stationId, vehicleId } }); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to load route alternatives.')); }
}
