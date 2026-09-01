export type AccessMode = 'EV_USER' | 'HOST' | 'COMPANY' | 'ADMIN';

export type UserRole = 'EV_OWNER' | 'HOST' | 'COMPANY' | 'PLATFORM_ADMIN';

export interface User {
  id: string | number;
  email: string;
  name: string;
  role: UserRole;
  activeMode: AccessMode;
  allowedModes: AccessMode[];
  accountType: 'INDIVIDUAL' | 'COMPANY' | 'ADMIN';
  phone?: string;
  avatarUrl?: string;
  profileCompleted?: boolean;
  companyName?: string;
  registrationNumber?: string;
}

export interface AuthResponse {
  token: string;
  activeMode: AccessMode;
  user: User;
}

export interface LoginCredentials {
  email: string;
  password: string;
}

export interface RegisterUserRequest {
  name: string;
  email: string;
  password: string;
  phone?: string;
}

export interface RegisterHostRequest extends RegisterUserRequest {}

export interface RegisterCompanyRequest {
  companyName: string;
  contactName: string;
  email: string;
  password: string;
  phone: string;
  registrationNumber: string;
}

export interface CompleteProfileRequest {
  mode: AccessMode;
  fullName: string;
  phone: string;
  companyName?: string;
  registrationNumber?: string;
  hostDisplayName?: string;
}
