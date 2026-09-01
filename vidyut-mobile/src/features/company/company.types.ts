export interface CompanyProfile {
  id: number;
  companyName: string;
  registrationNumber: string;
  contactName: string;
  supportEmail?: string;
  supportPhone?: string;
  gstNumber?: string;
  kycDocumentUrl?: string;
  businessAddress?: string;
  website?: string;
  emailVerified: boolean;
  verificationStatus: 'PENDING' | 'VERIFIED' | 'REJECTED';
  emailNotifications: boolean;
  pushNotifications: boolean;
  timezone: string;
}

export interface CompanyDashboard {
  totalStations: number;
  totalChargers: number;
  onlineChargers: number;
  busyChargers: number;
  faults: number;
  utilizationRate: number;
  activeSessions: number;
  queueCount: number;
  energyDeliveredKwh: number;
  revenue: number;
  occupancyPercent: number;
  alerts: CompanyAlert[];
}

export interface CompanyAlert {
  chargerId: number;
  chargerCode: string;
  station: string;
  status: string;
  healthScore: number;
  message: string;
}

export interface CompanyStation {
  id: number;
  name: string;
  address: string;
  city: string;
  latitude: number;
  longitude: number;
  pricePerKwh: number;
  status: 'ACTIVE' | 'MAINTENANCE' | 'OFFLINE';
  availability: string;
  amenities?: string;
  workingHours?: string;
  imageUrl?: string;
  queueCount: number;
  occupancyPercent: number;
  dynamicPricingEnabled: boolean;
  peakPricePerKwh?: number;
  peakHours?: string;
  studentDiscountPercent?: number;
  corporatePricePerKwh?: number;
  couponCode?: string;
  couponDiscountPercent?: number;
  connectors: unknown[];
}

export interface CompanyCharger {
  id: number;
  stationId: number;
  stationName: string;
  chargerCode: string;
  connectorType: string;
  powerKw: number;
  available: boolean;
  status: 'ONLINE' | 'OFFLINE' | 'CHARGING' | 'MAINTENANCE' | 'FAULT';
  maintenanceMode: boolean;
  firmwareVersion: string;
  healthScore: number;
  lastHeartbeat: string;
}

export interface CompanyBooking {
  id: number;
  stationName: string;
  stationAddress: string;
  startTime: string;
  durationHours: number;
  totalAmount: number;
  kwhDelivered: number;
  status: 'PENDING' | 'CONFIRMED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
}

export interface CompanyEmployee {
  id: number;
  name: string;
  email: string;
  phone?: string;
  role: 'MANAGER' | 'OPERATOR' | 'MAINTENANCE' | 'FINANCE' | 'ANALYST';
  active: boolean;
  permissions?: string;
  createdAt: string;
}

export interface CompanyActivityLog {
  id: number;
  actorAccountId: number;
  action: string;
  resourceType: string;
  resourceId?: number;
  description: string;
  createdAt: string;
}

export interface CompanyAnalytics {
  dailyRevenue: number;
  weeklyRevenue: number;
  monthlyRevenue: number;
  peakUsageHour: string;
  customerGrowthPercent: number;
  successfulSessions: number;
  topStations: Array<{ station: string; revenue: number }>;
}
