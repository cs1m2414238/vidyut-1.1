import axios from 'axios';

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

interface ApiErrorResponse {
  message?: string;
  details?: string[];
}

export function unwrapApiResponse<T>(response: ApiResponse<T>): T {
  if (!response.success || response.data === undefined) {
    throw new Error(response.message || 'The server returned an invalid response.');
  }
  return response.data;
}

export function getApiErrorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    const body = error.response?.data;
    return body?.details?.[0] || body?.message || (error.response
      ? fallback
      : 'Unable to reach the Vidyut server. Check the API address and network connection.');
  }
  return error instanceof Error ? error.message : fallback;
}

export function isNetworkError(error: unknown): boolean {
  return axios.isAxiosError(error) && !error.response;
}
