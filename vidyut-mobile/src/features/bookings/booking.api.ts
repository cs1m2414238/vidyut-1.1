import { apiClient } from '../../services/apiClient';
import { BookingItem, BookingSlot, CreateBookingRequest, WaitlistItem } from './booking.types';
import { CONFIG } from '../../constants/config';
import { ApiResponse, getApiErrorMessage, isNetworkError, unwrapApiResponse } from '../../services/apiResponse';

interface BackendBooking {
  id: number;
  stationId: number;
  vehicleId?: number;
  stationName: string;
  stationAddress: string;
  startTime: string;
  durationHours: number;
  totalAmount: number;
  status: BookingItem['status'];
  createdAt: string;
  durationMinutes?: number;
  endTime?: string;
  kwhDelivered?: number;
  cancellationFee?: number;
  refundAmount?: number;
  outletId?: number;
  outletTierName?: string;
  appliedRatePerKwh?: number;
}

function normalizeBooking(booking: BackendBooking): BookingItem {
  return {
    id: booking.id,
    stationId: booking.stationId,
    vehicleId: booking.vehicleId,
    chargerName: booking.stationName,
    address: booking.stationAddress,
    startTime: booking.startTime,
    durationMinutes: booking.durationMinutes || booking.durationHours * 60,
    totalCost: booking.totalAmount,
    status: booking.status,
    createdAt: booking.createdAt,
    endTime: booking.endTime,
    kwhDelivered: booking.kwhDelivered,
    cancellationFee: booking.cancellationFee,
    refundAmount: booking.refundAmount,
    outletId: booking.outletId,
    outletTierName: booking.outletTierName,
    appliedRatePerKwh: booking.appliedRatePerKwh,
  };
}

const mockBookings: BookingItem[] = [
  {
    id: 'BK-9041',
    stationId: 1,
    chargerName: 'Vidyut Green Hub',
    address: 'Gomti Nagar, Lucknow',
    startTime: new Date(Date.now() - 3600 * 1000 * 24).toISOString(),
    durationMinutes: 60,
    totalCost: 187.5,
    status: 'COMPLETED',
    createdAt: new Date().toISOString(),
  },
  {
    id: 'BK-9082',
    stationId: 2,
    chargerName: 'EcoVolt Station',
    address: 'Vibhuti Khand, Lucknow',
    startTime: new Date(Date.now() + 3600 * 1000 * 2).toISOString(),
    durationMinutes: 45,
    totalCost: 154.0,
    status: 'CONFIRMED',
    createdAt: new Date().toISOString(),
  },
];

export async function createBooking(
  request: CreateBookingRequest,
  _userId: number | string,
): Promise<BookingItem> {
  try {
    const response = await apiClient.post<ApiResponse<BackendBooking>>('/ev/bookings', {
      stationId: request.stationId,
      vehicleId: request.vehicleId,
      startTime: request.startTime,
      durationMinutes: request.durationMinutes,
      idempotencyKey: request.idempotencyKey || `mobile-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
    });
    return normalizeBooking(unwrapApiResponse(response.data));
  } catch (error) {
    if (isNetworkError(error)) {
      throw new Error('No connection — reconnect before booking so your slot cannot be double-booked.');
    }
    if (!(CONFIG.USE_MOCK_DATA && isNetworkError(error))) {
      throw new Error(getApiErrorMessage(error, 'Unable to create the booking.'));
    }
    const newBooking: BookingItem = {
      id: `BK-${Math.floor(1000 + Math.random() * 9000)}`,
      stationId: request.stationId,
      vehicleId: request.vehicleId,
      chargerName: `Station #${request.stationId}`,
      address: 'Lucknow, UP',
      startTime: request.startTime,
      durationMinutes: request.durationMinutes,
      totalCost: 150.0,
      status: 'CONFIRMED',
      createdAt: new Date().toISOString(),
    };
    mockBookings.unshift(newBooking);
    return newBooking;
  }
}

export async function getBooking(id: number | string): Promise<BookingItem> {
  try { const response = await apiClient.get<ApiResponse<BackendBooking>>(`/ev/bookings/${id}`); return normalizeBooking(unwrapApiResponse(response.data)); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to load the booking.')); }
}

export async function cancelBooking(id: number | string): Promise<BookingItem> {
  try { const response = await apiClient.patch<ApiResponse<BackendBooking>>(`/ev/bookings/${id}/cancel`); return normalizeBooking(unwrapApiResponse(response.data)); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to cancel the booking.')); }
}

export async function getBookingSlots(stationId: number | string, date: string): Promise<BookingSlot[]> {
  try { const response = await apiClient.get<ApiResponse<BookingSlot[]>>('/ev/bookings/availability', { params: { stationId, date } }); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to load charging slots.')); }
}

export async function joinWaitlist(request: { stationId: number | string; vehicleId?: number; preferredStartTime?: string; durationMinutes: number }): Promise<WaitlistItem> {
  try { const response = await apiClient.post<ApiResponse<WaitlistItem>>('/ev/bookings/waitlist', request); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to join the waitlist.')); }
}

export async function getMyBookings(_userId: number | string): Promise<BookingItem[]> {
  try {
    const response = await apiClient.get<ApiResponse<BackendBooking[]>>('/ev/bookings');
    return unwrapApiResponse(response.data).map(normalizeBooking);
  } catch (error) {
    if (CONFIG.USE_MOCK_DATA && isNetworkError(error)) return mockBookings;
    throw new Error(getApiErrorMessage(error, 'Unable to load your bookings.'));
  }
}
