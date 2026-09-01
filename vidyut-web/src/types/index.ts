export interface Charger {
  id: number;
  name: string;
  hostName: string;
  address: string;
  latitude: number;
  longitude: number;
  pricePerKwh: number;
  connectorType: string;
  powerKw: number;
  available: boolean;
  rating: number;
  reviewCount: number;
  distance: string;
  imageUrl: string;
  description?: string;
  bookingSlotMinutes?: number;
  status?: string;
  availability?: string;
  outletPartner?: boolean;
  outletInstitutionName?: string;
  demoData?: boolean;
}

export interface User {
  id: string;
  name: string;
  email: string;
  phone: string;
  walletBalance: number;
  totalBookings: number;
  totalEnergyKwh: number;
  co2SavedKg: number;
  contactName?: string;
  companyName?: string;
  registrationNumber?: string;
  profileCompleted?: boolean;
  emailVerified?: boolean;
  accountType?: 'INDIVIDUAL' | 'COMPANY' | 'ADMIN';
  hostStatus?: 'PENDING' | 'VERIFIED' | 'NOT_APPLIED';
}

export type NavItem = {
  icon: string;
  label: string;
  id: string;
  badge?: number;
};
