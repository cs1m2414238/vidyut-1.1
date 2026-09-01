import { apiRequest } from './api';

export type BookingStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'EXPIRED';

export interface BookingCreateRequest {
  stationId: number;
  vehicleId?: number;
  startTime?: string;
  durationMinutes: number;
  idempotencyKey?: string;
}

export interface BookingResponse {
  id: number;
  userId: number;
  stationId: number;
  vehicleId?: number | null;
  stationName: string;
  stationAddress: string;
  startTime: string;
  durationHours: number;
  durationMinutes: number;
  totalAmount: number;
  kwhDelivered: number;
  status: BookingStatus;
  seen: boolean;
  createdAt: string;
}

function authorized(token: string): HeadersInit {
  return { Authorization: `Bearer ${token}` };
}

export function createBooking(
  token: string,
  booking: BookingCreateRequest,
): Promise<BookingResponse> {
  return apiRequest<BookingResponse>('/ev/bookings', {
    method: 'POST',
    headers: authorized(token),
    body: JSON.stringify({ ...booking, idempotencyKey: booking.idempotencyKey || `web-${Date.now()}-${crypto.randomUUID()}` }),
  });
}

export function getMyBookings(token: string): Promise<BookingResponse[]> {
  return apiRequest<BookingResponse[]>('/ev/bookings', {
    method: 'GET',
    headers: authorized(token),
  });
}

export function getBooking(token: string, bookingId: number): Promise<BookingResponse> {
  return apiRequest<BookingResponse>(`/ev/bookings/${bookingId}`, {
    method: 'GET',
    headers: authorized(token),
  });
}

export async function cancelBooking(token: string, bookingId: number): Promise<void> {
  await apiRequest<null>(`/ev/bookings/${bookingId}/cancel`, {
    method: 'POST',
    headers: authorized(token),
  });
}

export async function getUnreadBookingCount(token: string): Promise<number> {
  const response = await apiRequest<{ count: number }>('/ev/bookings/unread-count', {
    method: 'GET',
    headers: authorized(token),
  });
  return response.count;
}

export async function markBookingsSeen(token: string): Promise<void> {
  await apiRequest<null>('/ev/bookings/mark-seen', {
    method: 'PATCH',
    headers: authorized(token),
  });
}
