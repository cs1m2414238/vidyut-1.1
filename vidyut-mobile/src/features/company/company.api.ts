import { apiClient } from '../../services/apiClient';
import { ApiResponse, getApiErrorMessage, unwrapApiResponse } from '../../services/apiResponse';
import {
  CompanyActivityLog,
  CompanyAnalytics,
  CompanyBooking,
  CompanyCharger,
  CompanyDashboard,
  CompanyEmployee,
  CompanyProfile,
  CompanyStation,
} from './company.types';

async function get<T>(path: string, fallback: string): Promise<T> {
  try {
    const response = await apiClient.get<ApiResponse<T>>(path);
    return unwrapApiResponse(response.data);
  } catch (error) { throw new Error(getApiErrorMessage(error, fallback)); }
}

async function send<T>(method: 'post' | 'put' | 'patch' | 'delete', path: string, body: unknown, fallback: string): Promise<T> {
  try {
    const response = await apiClient.request<ApiResponse<T>>({ method, url: path, data: body });
    return unwrapApiResponse(response.data);
  } catch (error) { throw new Error(getApiErrorMessage(error, fallback)); }
}

export const getCompanyProfile = () => get<CompanyProfile>('/company/profile', 'Unable to load the company profile.');
export const getCompanyDashboard = () => get<CompanyDashboard>('/company/dashboard', 'Unable to load company operations.');
export const getCompanyAnalytics = () => get<CompanyAnalytics>('/company/analytics', 'Unable to load company analytics.');
export const getCompanyStations = () => get<CompanyStation[]>('/company/stations', 'Unable to load company stations.');
export const getCompanyChargers = () => get<CompanyCharger[]>('/company/chargers', 'Unable to load company chargers.');
export const getCompanyBookings = () => get<CompanyBooking[]>('/company/bookings', 'Unable to load company bookings.');
export const getCompanyEmployees = () => get<CompanyEmployee[]>('/company/employees', 'Unable to load company employees.');
export const getCompanyActivityLogs = () => get<CompanyActivityLog[]>('/company/activity-logs', 'Unable to load company activity logs.');
export const getCompanyNotifications = () => get<Array<{ id: number; title: string; message: string; type: string; timestamp: string }>>('/company/notifications', 'Unable to load company notifications.');

export const createCompanyStation = (body: Record<string, unknown>) => send<CompanyStation>('post', '/company/stations', body, 'Unable to create station.');
export const updateCompanyStation = (id: number, body: Record<string, unknown>) => send<CompanyStation>('put', `/company/stations/${id}`, body, 'Unable to update station.');
export const deleteCompanyStation = (id: number) => send<void>('delete', `/company/stations/${id}`, undefined, 'Unable to delete station.');
export const createCompanyCharger = (body: Record<string, unknown>) => send<CompanyCharger>('post', '/company/chargers', body, 'Unable to add charger.');
export const updateCompanyCharger = (id: number, body: Record<string, unknown>) => send<CompanyCharger>('put', `/company/chargers/${id}`, body, 'Unable to update charger.');
export const updateCompanyBooking = (id: number, status: CompanyBooking['status']) => send<CompanyBooking>('patch', `/company/bookings/${id}/status`, { status }, 'Unable to update booking.');
export const updateCompanyPricing = (id: number, body: Record<string, unknown>) => send<CompanyStation>('put', `/company/stations/${id}/pricing`, body, 'Unable to update pricing.');
export const createCompanyEmployee = (body: Record<string, unknown>) => send<CompanyEmployee>('post', '/company/employees', body, 'Unable to add employee.');
export const deleteCompanyEmployee = (id: number) => send<void>('delete', `/company/employees/${id}`, undefined, 'Unable to remove employee.');
export const askCompanyAssistant = (question: string) => send<{ answer: string; recommendations: string[] }>('post', '/company/ai/ask', { question }, 'The assistant is unavailable.');
export const updateCompanyProfile = (body: Record<string, unknown>) => send<CompanyProfile>('put', '/company/profile', body, 'Unable to update company profile.');
export const updateCompanySettings = (body: Record<string, unknown>) => send<CompanyProfile>('put', '/company/settings', body, 'Unable to update company settings.');
export const submitCompanyVerification = (body: Record<string, unknown>) => send<CompanyProfile>('post', '/company/verification', body, 'Unable to submit business verification.');
export const requestCompanyEmailCode = () => send<string>('post', '/company/email-verification/request', undefined, 'Unable to send verification code.');
export const confirmCompanyEmail = (code: string) => send<CompanyProfile>('post', '/company/email-verification/confirm', { code }, 'Unable to verify email.');

export async function downloadCompanyReport(type: 'ANALYTICS' | 'REVENUE', format: 'PDF' | 'XLSX'): Promise<ArrayBuffer> {
  try {
    const response = await apiClient.get(`/company/reports/export?type=${type}&format=${format}`, { responseType: 'arraybuffer' });
    return response.data;
  } catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to generate report.')); }
}
