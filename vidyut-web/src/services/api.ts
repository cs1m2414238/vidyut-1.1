export type AccessMode = 'EV_USER' | 'HOST' | 'COMPANY' | 'ADMIN';

export const AUTH_SESSION_EXPIRED_EVENT = 'vidyut:auth-session-expired';

export interface ApiUser {
  id: number | string;
  email: string;
  fullName: string;
  phone?: string;
  role: 'ROLE_EV_USER' | 'ROLE_HOST' | 'ROLE_COMPANY' | 'ROLE_ADMIN';
  roles: string[];
  accountType: 'INDIVIDUAL' | 'COMPANY' | 'ADMIN';
  allowedModes: AccessMode[];
  defaultMode: AccessMode;
  contactName?: string;
  companyName?: string;
  registrationNumber?: string;
  profileCompleted?: boolean;
  emailVerified?: boolean;
  hostStatus?: 'PENDING' | 'VERIFIED' | 'NOT_APPLIED';
}

export interface AuthData {
  token: string;
  activeMode: AccessMode;
  user: ApiUser;
}

export interface CompleteProfilePayload {
  mode: Exclude<AccessMode, 'ADMIN'>;
  fullName: string;
  phone: string;
  companyName?: string;
  registrationNumber?: string;
  hostDisplayName?: string;
}

interface ApiEnvelope<T> {
  success: boolean;
  message: string;
  data: T;
}

interface ApiErrorBody {
  message?: string;
  details?: string[];
}

function errorMessage(response: Response, body: unknown): string {
  const errorBody = typeof body === 'object' && body !== null ? body as ApiErrorBody : null;
  const apiMessage = errorBody?.details?.[0] || errorBody?.message;
  if (apiMessage) return apiMessage;

  if (response.status === 401) return 'Your login session has expired. Sign in again to continue.';
  if (response.status === 403) return 'This account is not authorized for the requested action.';
  if (response.status === 429) return 'The service is temporarily busy. Try again shortly.';
  if (response.status >= 500) return 'The Vidyut server could not complete the request. Try again shortly.';
  return `The request could not be completed (HTTP ${response.status}).`;
}

const rawBaseUrl = (import.meta.env.VITE_API_BASE_URL?.trim() || '/api').replace(/\/+$/, '');
const API_BASE_URL = rawBaseUrl.endsWith('/api') ? rawBaseUrl : `${rawBaseUrl}/api`;

export async function apiRequest<T>(path: string, init: RequestInit): Promise<T> {
  let response: Response;
  const token = localStorage.getItem('vidyut_token');
  const headers = new Headers(init.headers);
  if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  if (token && !headers.has('Authorization')) headers.set('Authorization', `Bearer ${token}`);

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers,
    });
  } catch {
    throw new Error('Unable to reach the Vidyut server. Check that the backend is running.');
  }

  const contentType = response.headers.get('content-type') ?? '';
  const body = contentType.includes('application/json') ? await response.json() : await response.text();
  if (!response.ok) {
    const message = errorMessage(response, body);
    const isSignInRequest = /^\/auth\/(?:login|google|register)/.test(path);
    if (response.status === 401 && !isSignInRequest && !path.startsWith('/admin/')) {
      clearAuthSession();
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent(AUTH_SESSION_EXPIRED_EVENT, { detail: { message } }));
      }
    }
    throw new Error(message);
  }

  const envelope = body as ApiEnvelope<T>;
  if (!envelope.success || envelope.data === undefined) {
    throw new Error(envelope.message || 'The server returned an invalid response.');
  }
  return envelope.data;
}

export async function apiDownload(path: string, token: string): Promise<Blob> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null) as ApiErrorBody | null;
    throw new Error(body?.details?.[0] || body?.message || 'Unable to download the report.');
  }
  return response.blob();
}

export async function switchAuthMode(mode: AccessMode, token: string): Promise<AuthData> {
  return apiRequest<AuthData>('/auth/switch-mode', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify({ mode }),
  });
}

export async function authenticateWithGoogle(
  accessToken: string,
  requestedMode?: Exclude<AccessMode, 'ADMIN'>,
): Promise<AuthData> {
  return apiRequest<AuthData>('/auth/google', {
    method: 'POST',
    body: JSON.stringify({ accessToken, requestedMode }),
  });
}

export async function completeProfile(
  payload: CompleteProfilePayload,
  token: string,
): Promise<AuthData> {
  return apiRequest<AuthData>('/auth/complete-profile', {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload),
  });
}

export function saveAuthSession(auth: AuthData): void {
  localStorage.setItem('vidyut_token', auth.token);
  localStorage.setItem('vidyut_user', JSON.stringify(auth.user));
  localStorage.setItem('vidyut_active_mode', auth.activeMode);
}

export function loadAuthSession(): AuthData | null {
  try {
    const token = localStorage.getItem('vidyut_token');
    const userJson = localStorage.getItem('vidyut_user');
    const activeMode = localStorage.getItem('vidyut_active_mode') as AccessMode | null;

    if (!token || !userJson || !activeMode) return null;
    if (token.startsWith('oauth_') || token.startsWith('session_')) {
      clearAuthSession();
      return null;
    }

    const user = JSON.parse(userJson) as ApiUser;
    if (!user.email || !Array.isArray(user.allowedModes) || !user.allowedModes.includes(activeMode)) {
      return null;
    }

    return { token, activeMode, user };
  } catch {
    return null;
  }
}

export function clearAuthSession(): void {
  localStorage.removeItem('vidyut_token');
  localStorage.removeItem('vidyut_user');
  localStorage.removeItem('vidyut_active_mode');
}

/* =========================
   BOOKING TYPES & SERVICES
========================= */

export type BookingStatus =
  | "PENDING"
  | "CONFIRMED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED"
  | "EXPIRED";

export interface BookingCreateRequest {
  chargerId: number;
  startTime: string;
  endTime: string;
}

export interface BookingResponse {
  id: number;
  chargerId: number;
  stationName: string;
  startTime: string;
  endTime: string;
  status: BookingStatus;
  createdAt: string;
}

/* =========================
   CREATE BOOKING
========================= */

export async function createBooking(
  booking: BookingCreateRequest
): Promise<BookingResponse> {
  return apiRequest<BookingResponse>("/bookings", {
    method: "POST",
    body: JSON.stringify(booking),
  });
}

/* =========================
   GET MY BOOKINGS
========================= */

export async function getMyBookings(): Promise<BookingResponse[]> {
  return apiRequest<BookingResponse[]>("/bookings/my", {
    method: "GET",
  });
}

/* =========================
   GET ONE BOOKING
========================= */

export async function getBooking(
  bookingId: number
): Promise<BookingResponse> {
  return apiRequest<BookingResponse>(`/bookings/${bookingId}`, {
    method: "GET",
  });
}

/* =========================
   CANCEL BOOKING
========================= */

export async function cancelBooking(
  bookingId: number
): Promise<BookingResponse> {
  return apiRequest<BookingResponse>(`/bookings/${bookingId}/cancel`, {
    method: "PATCH",
  });
}

/* =========================
   UNREAD ACTIVE COUNT
========================= */

export async function getUnreadBookingCount(): Promise<number> {
  const response = await apiRequest<{ count: number }>("/bookings/unread-count", {
    method: "GET",
  });
  return response.count;
}

/* =========================
   MARK BOOKINGS AS SEEN
========================= */

export async function markBookingsSeen(): Promise<void> {
  await apiRequest<void>("/bookings/mark-seen", {
    method: "PATCH",
  });
}
