import { apiClient } from '../../services/apiClient';
import { ApiResponse, getApiErrorMessage, unwrapApiResponse } from '../../services/apiResponse';
import { ChargingSession } from './session.types';

async function post(path: string, fallback: string): Promise<ChargingSession> {
  try { const response = await apiClient.post<ApiResponse<ChargingSession>>(path); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, fallback)); }
}
export const startSession = (bookingId: number | string) => post(`/ev/sessions/booking/${bookingId}/start`, 'Unable to start charging.');
export const stopSession = (id: number | string) => post(`/ev/sessions/${id}/stop`, 'Unable to stop charging.');
export const paySession = (id: number | string) => post(`/ev/sessions/${id}/pay`, 'Unable to pay for this session.');
export async function getSession(id: number | string): Promise<ChargingSession> {
  try { const response = await apiClient.get<ApiResponse<ChargingSession>>(`/ev/sessions/${id}`); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to load the charging session.')); }
}
export async function getActiveSessions(): Promise<ChargingSession[]> {
  try { const response = await apiClient.get<ApiResponse<ChargingSession[]>>('/ev/sessions/active'); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to load active charging.')); }
}
export async function updateSessionSoc(id: number | string, batteryPercent: number, simulated: boolean): Promise<ChargingSession> {
  try { const response = await apiClient.patch<ApiResponse<ChargingSession>>(`/ev/sessions/${id}/soc`, { batteryPercent, simulated }); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to update live battery status.')); }
}
export async function controlSession(id: number | string, action: 'START' | 'STOP'): Promise<ChargingSession> {
  try { const response = await apiClient.post<ApiResponse<ChargingSession>>(`/ev/sessions/${id}/control`, { action }); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Bluetooth session control failed.')); }
}
