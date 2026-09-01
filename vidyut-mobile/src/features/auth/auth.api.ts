import { apiClient } from '../../services/apiClient';
import { AccessMode, AuthResponse, CompleteProfileRequest, LoginCredentials, RegisterCompanyRequest, RegisterHostRequest, RegisterUserRequest } from './auth.types';
import { ApiResponse, getApiErrorMessage, unwrapApiResponse } from '../../services/apiResponse';

interface BackendUser {
  id: string | number;
  email: string;
  fullName: string;
  phone?: string;
  role: 'ROLE_EV_USER' | 'ROLE_HOST' | 'ROLE_COMPANY' | 'ROLE_ADMIN';
  accountType: 'INDIVIDUAL' | 'COMPANY' | 'ADMIN';
  allowedModes: AccessMode[];
  profileCompleted?: boolean;
  companyName?: string;
  registrationNumber?: string;
}

interface BackendAuthResponse {
  token: string;
  activeMode: AccessMode;
  user: BackendUser;
}

function normalizeAuthResponse(response: BackendAuthResponse): AuthResponse {
  const roles = {
    ROLE_EV_USER: 'EV_OWNER',
    ROLE_HOST: 'HOST',
    ROLE_COMPANY: 'COMPANY',
    ROLE_ADMIN: 'PLATFORM_ADMIN',
  } as const;

  return {
    token: response.token,
    activeMode: response.activeMode,
    user: {
      id: response.user.id,
      email: response.user.email,
      name: response.user.fullName,
      phone: response.user.phone,
      role: roles[response.user.role],
      activeMode: response.activeMode,
      allowedModes: response.user.allowedModes,
      accountType: response.user.accountType,
      profileCompleted: response.user.profileCompleted,
      companyName: response.user.companyName,
      registrationNumber: response.user.registrationNumber,
    },
  };
}

async function authPost(path: string, body: object, fallback: string): Promise<AuthResponse> {
  try {
    const response = await apiClient.post<ApiResponse<BackendAuthResponse>>(path, body);
    return normalizeAuthResponse(unwrapApiResponse(response.data));
  } catch (error) {
    throw new Error(getApiErrorMessage(error, fallback));
  }
}

export function loginApi(credentials: LoginCredentials): Promise<AuthResponse> {
  return authPost('/auth/login', credentials, 'Invalid email or password.');
}

export function googleAuthApi(accessToken: string, requestedMode: AccessMode = 'EV_USER'): Promise<AuthResponse> {
  return authPost('/auth/google', { accessToken, requestedMode }, 'Google sign-in could not be completed.');
}

export function completeProfileApi(data: CompleteProfileRequest): Promise<AuthResponse> {
  return authPost('/auth/complete-profile', data, 'Unable to save the profile details.');
}

export function registerUserApi(data: RegisterUserRequest): Promise<AuthResponse> {
  return authPost('/auth/register/user', {
    fullName: data.name, email: data.email, password: data.password, phone: data.phone,
  }, 'Failed to create the account.');
}

export function registerHostApi(data: RegisterHostRequest): Promise<AuthResponse> {
  return authPost('/auth/register/host', {
    fullName: data.name, email: data.email, password: data.password, phone: data.phone,
  }, 'Failed to create the host account.');
}

export function registerCompanyApi(data: RegisterCompanyRequest): Promise<AuthResponse> {
  return authPost('/auth/register/company', {
    companyName: data.companyName,
    registrationNumber: data.registrationNumber,
    adminEmail: data.email,
    adminPassword: data.password,
    adminFullName: data.contactName,
    supportPhone: data.phone,
  }, 'Failed to register the company.');
}

export function switchModeApi(mode: AccessMode): Promise<AuthResponse> {
  return authPost('/auth/switch-mode', { mode }, 'You are not authorized for that mode.');
}

export async function applyForHostApi(displayName: string): Promise<void> {
  try {
    await apiClient.post('/auth/host/apply', { displayName });
  } catch (error) {
    throw new Error(getApiErrorMessage(error, 'Unable to submit the host application.'));
  }
}

export async function deleteAccountApi(): Promise<void> {
  try { await apiClient.delete('/account'); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to delete this account.')); }
}
