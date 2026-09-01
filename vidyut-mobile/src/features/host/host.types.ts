export interface HostProfile {
  accountId: number;
  email: string;
  emailVerified: boolean;
  displayName: string;
  phone?: string;
  address?: string;
  bio?: string;
  verificationStatus: 'PENDING' | 'VERIFIED' | 'REJECTED';
  kycDocumentUrl?: string;
  identityType?: string;
  identityLast4?: string;
  bankAccountHolder?: string;
  bankName?: string;
  bankAccountLast4?: string;
  ifscCode?: string;
  payoutUpi?: string;
  bankVerified: boolean;
  emailNotifications: boolean;
  pushNotifications: boolean;
  autoAvailability: boolean;
  reputationScore: number;
}

export interface HostDashboard {
  displayName: string;
  verified: boolean;
  totalChargers: number;
  onlineChargers: number;
  activeSessions: number;
  upcomingBookings: number;
  energyDeliveredKwh: number;
  uptimePercent: number;
  todayEarnings: number;
  monthlyEarnings: number;
  pendingPayout: number;
  reputationScore: number;
  alerts: HostMonitor[];
}

export interface HostConnector {
  id: number;
  chargerCode: string;
  type: string;
  powerKw: number;
  status?: string;
  available?: boolean;
}

export interface HostStation {
  id: number;
  name: string;
  address: string;
  city: string;
  latitude: number;
  longitude: number;
  pricePerKwh: number;
  imageUrl?: string;
  photoUrls?: string;
  amenities?: string;
  workingHours?: string;
  weeklySchedule?: string;
  holidaySchedule?: string;
  chargingInstructions?: string;
  autoAvailability: boolean;
  emergencyDisabled: boolean;
  bookingSlotMinutes: number;
  status: string;
  availability: string;
  connectors: HostConnector[];
}

export interface HostBooking {
  id: number;
  stationId: number;
  stationName: string;
  customerAccountId: number;
  customerName: string;
  customerEmail: string;
  startTime: string;
  durationHours: number;
  totalAmount: number;
  kwhDelivered: number;
  status: 'PENDING' | 'CONFIRMED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
}

export interface HostEarnings {
  daily: number;
  weekly: number;
  monthly: number;
  lifetime: number;
  availableBalance: number;
  pendingPayout: number;
  taxWithheld: number;
  financialYear: string;
  transactions: Array<{ bookingId: number; station: string; amount: number; timestamp: string; status: string }>;
  payouts: Array<{ id: number; amount: number; status: string; timestamp: string }>;
}

export interface HostMonitor {
  id: number;
  stationId: number;
  stationName: string;
  chargerCode: string;
  connectorType: string;
  powerKw: number;
  status: 'ONLINE' | 'OFFLINE' | 'CHARGING' | 'MAINTENANCE' | 'FAULT';
  currentPowerKw: number;
  sessionEnergyKwh: number;
  sessionDurationMinutes: number;
  healthScore: number;
  faultCode?: string;
  lastHeartbeat: string;
}

export interface HostReview {
  id: number;
  customerName: string;
  rating: number;
  comment: string;
  hostReply?: string;
  reported: boolean;
  createdAt: string;
}

export interface HostNotification { id: number; title: string; message: string; type: string; timestamp: string; read: boolean }
