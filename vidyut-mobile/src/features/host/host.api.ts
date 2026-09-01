import { apiClient } from '../../services/apiClient';
import { ApiResponse, getApiErrorMessage, unwrapApiResponse } from '../../services/apiResponse';
import { HostBooking, HostDashboard, HostEarnings, HostMonitor, HostNotification, HostProfile, HostReview, HostStation } from './host.types';

async function get<T>(path: string, fallback: string): Promise<T> {
  try { const response = await apiClient.get<ApiResponse<T>>(path); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, fallback)); }
}
async function send<T>(method: 'post' | 'put' | 'patch' | 'delete', path: string, body: unknown, fallback: string): Promise<T> {
  try { const response = await apiClient.request<ApiResponse<T>>({ method, url: path, data: body }); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, fallback)); }
}

export const getHostProfile = () => get<HostProfile>('/host/profile', 'Unable to load Host profile.');
export const getHostDashboard = () => get<HostDashboard>('/host/dashboard', 'Unable to load Host dashboard.');
export const getHostStations = () => get<HostStation[]>('/host/stations', 'Unable to load private chargers.');
export const getHostBookings = () => get<HostBooking[]>('/host/bookings', 'Unable to load Host bookings.');
export const getHostEarnings = () => get<HostEarnings>('/host/earnings', 'Unable to load Host earnings.');
export const getHostMonitoring = () => get<HostMonitor[]>('/host/monitoring', 'Unable to load charger monitoring.');
export const getHostReviews = () => get<HostReview[]>('/host/reviews', 'Unable to load reviews.');
export const getHostNotifications = () => get<HostNotification[]>('/host/notifications', 'Unable to load notifications.');
export const createHostStation = (body: Record<string, unknown>) => send<HostStation>('post', '/host/stations', body, 'Unable to register charger.');
export const updateHostStation = (id: number, body: Record<string, unknown>) => send<HostStation>('put', `/host/stations/${id}`, body, 'Unable to update charger.');
export const deleteHostStation = (id: number) => send<void>('delete', `/host/stations/${id}`, undefined, 'Unable to delete charger.');
export const updateHostAvailability = (id: number, body: Record<string, unknown>) => send<HostStation>('put', `/host/stations/${id}/availability`, body, 'Unable to update availability.');
export const updateHostBooking = (id: number, status: HostBooking['status']) => send<HostBooking>('patch', `/host/bookings/${id}/status`, { status }, 'Unable to update booking.');
export const updateHostConnector = (id: number, body: Record<string, unknown>) => send<HostMonitor>('put', `/host/connectors/${id}/status`, body, 'Unable to update charger status.');
export const withdrawHostEarnings = (amount: number) => send<unknown>('post', '/host/payouts/withdraw', { amount }, 'Unable to request payout.');
export const replyHostReview = (id: number, message: string) => send<HostReview>('patch', `/host/reviews/${id}/reply`, { message }, 'Unable to reply to review.');
export const reportHostReview = (id: number, message: string) => send<HostReview>('patch', `/host/reviews/${id}/report`, { message }, 'Unable to report review.');
export const askHostAssistant = (question: string) => send<{ answer: string; recommendations?: string[] }>('post', '/host/ai/ask', { question }, 'Host assistant is unavailable.');
export const updateHostProfile = (body: Record<string, unknown>) => send<HostProfile>('put', '/host/profile', body, 'Unable to update Host profile.');
export const updateHostSettings = (body: Record<string, unknown>) => send<HostProfile>('put', '/host/settings', body, 'Unable to update Host settings.');
export const submitHostVerification = (body: Record<string, unknown>) => send<HostProfile>('post', '/host/verification', body, 'Unable to submit KYC.');
export const setupHostBank = (body: Record<string, unknown>) => send<HostProfile>('put', '/host/bank', body, 'Unable to set up bank account.');
export const requestHostEmailCode = () => send<string>('post', '/host/email-verification/request', undefined, 'Unable to send verification code.');
export const confirmHostEmail = (code: string) => send<HostProfile>('post', '/host/email-verification/confirm', { code }, 'Unable to verify email.');

export async function downloadHostReport(type: 'EARNINGS' | 'USAGE', format: 'PDF' | 'XLSX'): Promise<ArrayBuffer> {
  try { const response = await apiClient.get(`/host/reports/export?type=${type}&format=${format}`, { responseType: 'arraybuffer' }); return response.data; }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to generate Host report.')); }
}
