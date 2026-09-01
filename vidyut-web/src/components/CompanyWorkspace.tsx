import CompanyChargerSearch from './CompanyChargerSearch';
import { Children, useCallback, useEffect, useMemo, useState } from 'react';
import {
  Activity,
  AlertTriangle,
  BadgeIndianRupee,
  BatteryCharging,
  Bell,
  Bot,
  Building2,
  CalendarDays,
  CheckCircle2,
  CircleDollarSign,
  Download,
  FileSpreadsheet,
  Gauge,
  MapPin,
  Plus,
  RefreshCw,
  Send,
  Settings,
  ShieldCheck,
  Trash2,
  Users,
  Wrench,
  X,
} from 'lucide-react';
import { apiDownload, apiRequest } from '../services/api';
import { CompanyMarketplaceView } from './CompanyMarketplaceView';
import { CompanyVerificationFlow } from './CompanyVerificationFlow';

type ModalKind = 'station' | 'charger' | 'employee' | 'pricing' | 'profile' | 'verification' | null;

interface CompanyProfile {
  id: number;
  companyName: string;
  registrationNumber: string;
  contactName: string;
  supportEmail: string;
  supportPhone: string;
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

interface CompanyVerificationSummary {
  status: 'NOT_STARTED' | 'DOCUMENTS_SUBMITTED' | 'UNDER_REVIEW' | 'VERIFIED' | 'REJECTED' | 'SUSPENDED';
  trustLevel: 'UNVERIFIED' | 'BUSINESS_VERIFIED' | 'VIDYUT_VERIFIED' | 'TRUSTED_PARTNER';
  marketplaceEnabled: boolean;
  completedLayers: number;
  missingRequirements: string[];
  rejectionReason?: string;
}

interface Station {
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
  photoUrls?: string;
  queueCount: number;
  occupancyPercent: number;
  dynamicPricingEnabled: boolean;
  timeBasedPricePerHour?: number;
  peakPricePerKwh?: number;
  peakHours?: string;
  studentDiscountPercent?: number;
  corporatePricePerKwh?: number;
  couponCode?: string;
  couponDiscountPercent?: number;
  connectors: unknown[];
  propertyOwnerName?: string;
  operatorCompanyName?: string;
  ownershipType: 'COMPANY_OWNED' | 'HOST_PARTNERED';
  hostPartnershipId?: number;
  siteEvidenceComplete: boolean;
}

interface Charger {
  id: number;
  stationId: number;
  stationName: string;
  chargerCode: string;
  connectorType: string;
  powerKw: number;
  available: boolean;
  status: 'ONLINE' | 'OFFLINE' | 'CHARGING' | 'MAINTENANCE' | 'FAULT' | 'SUSPECTED_FAULT';
  demoData?: boolean;
  faultCode?: string;
  faultReason?: string;
  maintenanceMode: boolean;
  firmwareVersion: string;
  healthScore: number;
  lastHeartbeat: string;
}

interface Booking {
  id: number;
  stationName: string;
  stationAddress: string;
  startTime: string;
  durationHours: number;
  totalAmount: number;
  kwhDelivered: number;
  status: string;
}

interface Employee {
  id: number;
  name: string;
  email: string;
  phone?: string;
  role: string;
  active: boolean;
  permissions?: string;
  createdAt: string;
}

interface CompanyDashboard {
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
  alerts: Array<{ chargerId: number; chargerCode: string; station: string; status: string; healthScore: number; message: string }>;
}

interface Analytics {
  dailyRevenue: number;
  weeklyRevenue: number;
  monthlyRevenue: number;
  peakUsageHour: string;
  customerGrowthPercent: number;
  successfulSessions: number;
  topStations: Array<{ station: string; revenue: number }>;
}

interface SiteRecommendation {
  propertyId: number;
  title: string;
  location: string;
  parkingBays: number;
  availableLoadKw: number;
  nearestActiveStationKm: number | null;
  expansionScore: number;
  recommendedChargerCount?: number;
  recommendedPowerKw?: number;
  recommendedConnector?: string;
  reason: string;
}

type CompanyAgentMode = 'RECOMMEND_ONLY' | 'ASK_BEFORE_ACTIONS' | 'AUTOPILOT';
type CompanyAgentActionType = 'SIMULATE_DEMO_FAULT' | 'RESTORE_DEMO_CHARGER' | 'PUT_DEMO_CHARGER_IN_MAINTENANCE' | 'DISABLE_NEW_BOOKINGS' | 'CREATE_MAINTENANCE_TICKET' | 'NOTIFY_STATION_MANAGER' | 'APPLY_PRICE_RECOMMENDATION';

interface CompanyAgentSettings {
  mode: CompanyAgentMode;
  maxPriceChangePercent: number;
  autoDisableFaultyChargers: boolean;
  autoCreateMaintenanceTickets: boolean;
}

interface CompanyAgentResponse {
  intent: string;
  mode: CompanyAgentMode;
  answer: string;
  network: { stations: number; chargers: number; occupied: number; available: number; reserved: number; offline: number; faults: number; activeSessions: number; highestLoadStation: string; highestLoadPercent: number; longestSessionCharger: string; longestSessionMinutes: number; nextAvailableCharger: string; nextAvailableMinutes: number };
  fault?: { chargerId: number; chargerCode: string; stationName: string; issue: string; affectedBookings: number; estimatedDowntimeMinutes: number | null; estimatedRevenueAtRisk: number; compatibleBackups: string[] };
  revenue: { sessions: number; energySoldKwh: number; chargingRevenue: number; estimatedHostPayouts: number; estimatedVidyutFees: number; refunds: number; estimatedCompanyRevenue: number; bestPerformingStation: string; lowestPerformingStation: string };
  pricing?: { stationId: number; stationName: string; currentPricePerKwh: number; nearbyAveragePricePerKwh: number; recommendedPricePerKwh: number; currentUtilizationPercent: number; expectedUtilizationPercent: number; timeWindow: string };
  siteRecommendations: SiteRecommendation[];
  actions: Array<{ action: CompanyAgentActionType; label: string; risk: string; requiresApproval: boolean; chargerId?: number; stationId?: number; proposedPricePerKwh?: number; reason: string; expectedStatus?: Charger['status'] }>;
  offerDraft?: Record<string, string | number>;
  operations?: {
    stations: Array<{ stationId: number; stationName: string; city?: string; ownershipType: string; hostName?: string; propertyTitle?: string; issueCount: number; unavailableConnectors: number; affectedJourneyIds: number[]; activeBookingIds: number[]; downtimeMinutes?: number; availableCcs2: number; ccs2Connectors: number; acOnly: boolean }>;
    rankingMethod: string;
  };
  assistantModel?: string;
  assistantProvider?: string;
  assistantFallback?: boolean;
  generatedAt: string;
}

interface ManagedStation {
  id: number;
  name: string;
  city?: string;
  address: string;
  hostUserId: number;
  propertyOwnerName?: string;
  operatorCompanyName?: string;
  ownershipType: 'COMPANY_OWNED' | 'HOST_PARTNERED';
  hostPartnershipId?: number;
  relationship: 'COMPANY_OWNED' | 'HOST_PARTNERED';
  status: string;
  chargerCount: number;
  onlineChargers: number;
  faultedChargers: number;
}

interface ManagedCharger {
  id: number;
  stationId: number;
  stationName: string;
  city?: string;
  chargerCode: string;
  connectorType: string;
  powerKw: number;
  status: Charger['status'];
  available: boolean;
  maintenanceMode: boolean;
  healthScore: number;
  faultCode?: string;
  lastHeartbeat?: string;
  relationship: 'COMPANY_OWNED' | 'HOST_PARTNERED';
}

interface CompanyNetwork {
  totalStations: number;
  totalChargers: number;
  onlineChargers: number;
  chargingChargers: number;
  faultedChargers: number;
  openMaintenanceTickets: number;
  stations: ManagedStation[];
  chargers: ManagedCharger[];
}

interface MaintenanceTicket {
  id: number;
  chargerId: number;
  chargerCode: string;
  stationId: number;
  stationName: string;
  city?: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CANCELLED';
  issue: string;
  assignedTo?: string;
  resolutionNote?: string;
  createdAt: string;
  updatedAt: string;
  resolvedAt?: string;
}

interface SettlementTransaction {
  paymentId: number;
  bookingId?: number;
  stationName: string;
  amount: number;
  status: 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUNDED';
  gatewayTransactionId?: string;
  timestamp: string;
}

interface CompanySettlement {
  collected: number;
  pending: number;
  refunded: number;
  netRevenue: number;
  successfulTransactions: number;
  recentTransactions: SettlementTransaction[];
}

interface NotificationItem { id: number; title: string; message: string; type: string; timestamp: string; read: boolean }
interface ActivityLog { id: number; actorAccountId: number; action: string; resourceType: string; resourceId?: number; description: string; createdAt: string }
export interface CompanyCounts { bookings: number; notifications: number }

const emptyDashboard: CompanyDashboard = { totalStations: 0, totalChargers: 0, onlineChargers: 0, busyChargers: 0, faults: 0, utilizationRate: 0, activeSessions: 0, queueCount: 0, energyDeliveredKwh: 0, revenue: 0, occupancyPercent: 0, alerts: [] };
const emptyNetwork: CompanyNetwork = { totalStations: 0, totalChargers: 0, onlineChargers: 0, chargingChargers: 0, faultedChargers: 0, openMaintenanceTickets: 0, stations: [], chargers: [] };
const emptySettlement: CompanySettlement = { collected: 0, pending: 0, refunded: 0, netRevenue: 0, successfulTransactions: 0, recentTransactions: [] };
const defaultAgentSettings: CompanyAgentSettings = { mode: 'ASK_BEFORE_ACTIONS', maxPriceChangePercent: 10, autoDisableFaultyChargers: true, autoCreateMaintenanceTickets: true };

function money(value: number): string {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(value || 0);
}

function dateTime(value?: string): string {
  return value ? new Date(value).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }) : '—';
}

export function CompanyWorkspace({ tab, token, companyName, onNavigate, onCountsChange, searchQuery, onSearchChange }: { tab: string; token: string; companyName: string; onNavigate: (tab: string) => void; onCountsChange: (counts: CompanyCounts) => void; searchQuery: string; onSearchChange: (query: string) => void }) {
  const [profile, setProfile] = useState<CompanyProfile | null>(null);
  const [verification, setVerification] = useState<CompanyVerificationSummary | null>(null);
  const [dashboard, setDashboard] = useState<CompanyDashboard>(emptyDashboard);
  const [analytics, setAnalytics] = useState<Analytics | null>(null);
  const [stations, setStations] = useState<Station[]>([]);
  const [chargers, setChargers] = useState<Charger[]>([]);
  const [networkVersion, setNetworkVersion] = useState(0);
  const [networkSearch, setNetworkSearch] = useState<{ key: string; stations: Station[]; chargers: Charger[]; error: string }>({ key: '', stations: [], chargers: [], error: '' });
  const searchKey = `${token}:${tab}:${searchQuery.trim()}:${networkVersion}`;
  const searchingNetwork = networkSearch.key !== searchKey;
  const searchError = searchingNetwork ? '' : networkSearch.error;
  const filteredStations = searchingNetwork || searchError ? [] : networkSearch.stations;
  const filteredChargers = searchingNetwork || searchError ? [] : networkSearch.chargers;
  const matchingCities = [...new Set((tab === 'stations' ? filteredStations : stations.filter(station => filteredChargers.some(charger => charger.stationId === station.id))).map(station => station.city).filter(Boolean))];
  const operationalAlerts = chargers.filter(c => ['FAULT', 'SUSPECTED_FAULT', 'MAINTENANCE', 'OFFLINE'].includes(c.status));
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [activityLogs, setActivityLogs] = useState<ActivityLog[]>([]);
  const [network, setNetwork] = useState<CompanyNetwork>(emptyNetwork);
  const [maintenanceTickets, setMaintenanceTickets] = useState<MaintenanceTicket[]>([]);
  const [settlement, setSettlement] = useState<CompanySettlement>(emptySettlement);
  const [modal, setModal] = useState<ModalKind>(null);
  const [pendingChargerSave, setPendingChargerSave] = useState(false);
  const [pendingAgentAction, setPendingAgentAction] = useState<CompanyAgentResponse['actions'][number] | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<Record<string, string | number | boolean>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [question, setQuestion] = useState("Show today's revenue");
  const [assistantAnswer, setAssistantAnswer] = useState('Ask about revenue, demand, faults, pricing, energy or expansion.');
  const [siteRecommendations, setSiteRecommendations] = useState<SiteRecommendation[]>([]);
  const [agentResponse, setAgentResponse] = useState<CompanyAgentResponse | null>(null);
  const [agentSettings, setAgentSettings] = useState<CompanyAgentSettings>(defaultAgentSettings);
  const [assistantLoading, setAssistantLoading] = useState(false);
  const [expansionLoaded, setExpansionLoaded] = useState(false);
  const [emailVerificationOpen, setEmailVerificationOpen] = useState(false);
  const [verificationCode, setVerificationCode] = useState('');
  const [verificationSent, setVerificationSent] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<{ kind: 'stations' | 'chargers' | 'employees'; id: number; label: string } | null>(null);

  const auth = useMemo(() => ({ headers: { Authorization: `Bearer ${token}` } }), [token]);
  const loadAll = useCallback(async () => {
    setLoading(true);
    setError('');
    const requests = await Promise.allSettled([
      apiRequest<CompanyProfile>('/company/profile', { method: 'GET', ...auth }),
      apiRequest<CompanyDashboard>('/company/dashboard', { method: 'GET', ...auth }),
      apiRequest<Analytics>('/company/analytics', { method: 'GET', ...auth }),
      apiRequest<Station[]>('/company/stations', { method: 'GET', ...auth }),
      apiRequest<Charger[]>('/company/chargers', { method: 'GET', ...auth }),
      apiRequest<Booking[]>('/company/bookings', { method: 'GET', ...auth }),
      apiRequest<Employee[]>('/company/employees', { method: 'GET', ...auth }),
      apiRequest<NotificationItem[]>('/company/notifications', { method: 'GET', ...auth }),
      apiRequest<ActivityLog[]>('/company/activity-logs', { method: 'GET', ...auth }),
      apiRequest<CompanyVerificationSummary>('/company/verification', { method: 'GET', ...auth }),
      apiRequest<CompanyNetwork>('/company/network', { method: 'GET', ...auth }),
      apiRequest<MaintenanceTicket[]>('/company/maintenance-tickets', { method: 'GET', ...auth }),
      apiRequest<CompanySettlement>('/company/settlements', { method: 'GET', ...auth }),
      apiRequest<CompanyAgentSettings>('/company/ai/settings', { method: 'GET', ...auth }),
    ]);
    if (requests[0].status === 'fulfilled') setProfile(requests[0].value); else setError(requests[0].reason instanceof Error ? requests[0].reason.message : 'Unable to load company profile.');
    if (requests[1].status === 'fulfilled') setDashboard(requests[1].value);
    if (requests[2].status === 'fulfilled') setAnalytics(requests[2].value);
    if (requests[3].status === 'fulfilled') setStations(requests[3].value);
    if (requests[4].status === 'fulfilled') setChargers(requests[4].value);
    if (requests[5].status === 'fulfilled') setBookings(requests[5].value);
    if (requests[6].status === 'fulfilled') setEmployees(requests[6].value);
    if (requests[7].status === 'fulfilled') setNotifications(requests[7].value);
    if (requests[8].status === 'fulfilled') setActivityLogs(requests[8].value);
    if (requests[9].status === 'fulfilled') setVerification(requests[9].value);
    if (requests[10].status === 'fulfilled') setNetwork(requests[10].value);
    if (requests[11].status === 'fulfilled') setMaintenanceTickets(requests[11].value);
    if (requests[12].status === 'fulfilled') setSettlement(requests[12].value);
    if (requests[13].status === 'fulfilled') setAgentSettings(requests[13].value);
    onCountsChange({
      bookings: requests[5].status === 'fulfilled' ? requests[5].value.length : 0,
      notifications: requests[7].status === 'fulfilled' ? requests[7].value.filter((item) => !item.read).length : 0,
    });
    setLoading(false);
    setNetworkVersion(version => version + 1);
  }, [auth, onCountsChange]);

  useEffect(() => { void loadAll(); }, [loadAll]);

  useEffect(() => {
    if (tab !== 'stations' && tab !== 'chargers') return;
    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      try {
        const rows = await apiRequest<Station[] | Charger[]>(`/company/${tab}?q=${encodeURIComponent(searchQuery.trim())}`, { method: 'GET', ...auth, signal: controller.signal });
        if (!controller.signal.aborted) setNetworkSearch({ key: searchKey, stations: tab === 'stations' ? rows as Station[] : [], chargers: tab === 'chargers' ? rows as Charger[] : [], error: '' });
      } catch (err) {
        if (!controller.signal.aborted) setNetworkSearch({ key: searchKey, stations: [], chargers: [], error: err instanceof Error ? err.message : 'Network search failed. Try again.' });
      }
    }, 300);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [auth, tab, searchQuery, searchKey]);

  const verified = Boolean(verification?.marketplaceEnabled);

  const openModal = (kind: Exclude<ModalKind, null>, item?: Station | Charger | Employee) => {
    setEditingId(item?.id ?? null);
    if (kind === 'station') {
      const station = item as Station | undefined;
      setForm(station ? { name: station.name, address: station.address, city: station.city, latitude: station.latitude, longitude: station.longitude, pricePerKwh: station.pricePerKwh, amenities: station.amenities ?? '', workingHours: station.workingHours ?? '', imageUrl: station.imageUrl ?? '', photoUrls: station.photoUrls ?? '', status: station.status, availability: station.availability, queueCount: station.queueCount, occupancyPercent: station.occupancyPercent } : { stationSource: 'COMPANY_OWNED', propertyOwnerName: profile?.companyName ?? companyName, name: '', address: '', city: '', latitude: 26.8467, longitude: 80.9462, pricePerKwh: 18, connectorType: 'CCS2', powerKw: 60, amenities: 'Parking, Restroom', workingHours: 'Open 24 hours', imageUrl: '', photoUrls: '', siteOwnershipDocumentUrl: '', electricityConnectionDocumentUrl: '' });
    }
    if (kind === 'charger') {
      const charger = item as Charger | undefined;
      setForm(charger ? { stationId: charger.stationId, chargerCode: charger.chargerCode, connectorType: charger.connectorType, powerKw: charger.powerKw, status: charger.status, maintenanceMode: charger.maintenanceMode, firmwareVersion: charger.firmwareVersion, healthScore: charger.healthScore, expectedStatus: charger.status, syntheticDemo: Boolean(charger.demoData), faultReason: charger.faultReason || 'Simulated charger communication failure' } : { stationId: stations[0]?.id ?? '', chargerCode: '', connectorType: 'CCS2', powerKw: 60, status: 'ONLINE', maintenanceMode: false, firmwareVersion: '1.0.0', healthScore: 100 });
    }
    if (kind === 'employee') {
      const employee = item as Employee | undefined;
      setForm(employee ? { name: employee.name, email: employee.email, phone: employee.phone ?? '', role: employee.role, active: employee.active, permissions: employee.permissions ?? '' } : { name: '', email: '', phone: '', role: 'OPERATOR', active: true, permissions: 'stations:read,bookings:read' });
    }
    if (kind === 'pricing') {
      const station = item as Station;
      setEditingId(station.id);
      setForm({ pricePerKwh: station.pricePerKwh, timeBasedPricePerHour: station.timeBasedPricePerHour ?? 0, peakPricePerKwh: station.peakPricePerKwh ?? station.pricePerKwh, peakHours: station.peakHours ?? '18:00-22:00', studentDiscountPercent: station.studentDiscountPercent ?? 10, corporatePricePerKwh: station.corporatePricePerKwh ?? station.pricePerKwh, dynamicPricingEnabled: station.dynamicPricingEnabled, couponCode: station.couponCode ?? '', couponDiscountPercent: station.couponDiscountPercent ?? 0 });
    }
    if (kind === 'profile') setForm(profile ? { companyName: profile.companyName, contactName: profile.contactName, supportEmail: profile.supportEmail ?? '', supportPhone: profile.supportPhone ?? '', businessAddress: profile.businessAddress ?? '', website: profile.website ?? '' } : {});
    if (kind === 'verification') setForm({});
    setModal(kind);
    setError('');
  };

  const submit = async (approved = false) => {
    if (!modal) return;
    if (modal === 'station' && !editingId && form.stationSource === 'HOST_PARTNERED') {
      setModal(null);
      onNavigate('host_opportunities');
      setNotice('Choose a verified Host property, then continue through review, agreement and commissioning.');
      return;
    }
    const editingCharger = modal === 'charger' && editingId ? [...filteredChargers, ...chargers].find(charger => charger.id === editingId) : undefined;
    const changingStatus = editingCharger && (form.status !== editingCharger.status || Boolean(form.maintenanceMode) !== editingCharger.maintenanceMode);
    if (changingStatus && !approved) { setPendingChargerSave(true); return; }
    setSaving(true); setError('');
    try {
      let path = ''; let method = 'POST';
      if (modal === 'station') { path = editingId ? `/company/stations/${editingId}` : '/company/stations'; method = editingId ? 'PUT' : 'POST'; }
      if (modal === 'charger') { path = editingId ? `/company/chargers/${editingId}` : '/company/chargers'; method = editingId ? 'PUT' : 'POST'; }
      if (modal === 'employee') { path = editingId ? `/company/employees/${editingId}` : '/company/employees'; method = editingId ? 'PUT' : 'POST'; }
      if (modal === 'pricing') { path = `/company/stations/${editingId}/pricing`; method = 'PUT'; }
      if (modal === 'profile') { path = '/company/profile'; method = 'PUT'; }
      if (modal === 'verification') { path = '/company/verification'; method = 'POST'; }
      const payload: Record<string, string | number | boolean> = { ...form, ...(modal === 'charger' ? { impactApproved: approved, maintenanceMode: form.status === 'MAINTENANCE', ...(form.status === 'ONLINE' && form.syntheticDemo ? { faultReason: 'Operator restored synthetic demo connector' } : {}) } : {}) };
      delete payload.stationSource;
      await apiRequest(path, { method, ...auth, body: JSON.stringify(payload) });
      setPendingChargerSave(false); setModal(null); setNotice('Changes saved successfully.'); await loadAll();
    } catch (submitError) { setError(submitError instanceof Error ? submitError.message : 'Unable to save changes.'); }
    finally { setSaving(false); }
  };

  const remove = async () => {
    if (!pendingDelete) return;
    setSaving(true);
    try { await apiRequest(`/company/${pendingDelete.kind}/${pendingDelete.id}`, { method: 'DELETE', ...auth }); setPendingDelete(null); setNotice('Item deleted.'); await loadAll(); }
    catch (removeError) { setError(removeError instanceof Error ? removeError.message : 'Unable to delete item.'); }
    finally { setSaving(false); }
  };

  const updateBooking = async (id: number, status: 'CONFIRMED' | 'CANCELLED' | 'IN_PROGRESS' | 'COMPLETED') => {
    try { await apiRequest(`/company/bookings/${id}/status`, { method: 'PATCH', ...auth, body: JSON.stringify({ status }) }); setNotice(`Booking marked ${status.toLowerCase()}.`); await loadAll(); }
    catch (bookingError) { setError(bookingError instanceof Error ? bookingError.message : 'Unable to update booking.'); }
  };

  const createMaintenanceTicket = async (charger: ManagedCharger, priority: MaintenanceTicket['priority'], issue: string, assignedTo: string) => {
    try {
      setSaving(true); setError('');
      await apiRequest('/company/maintenance-tickets', {
        method: 'POST', ...auth,
        body: JSON.stringify({ chargerId: charger.id, priority, issue, assignedTo: assignedTo || null }),
      });
      setNotice(`Maintenance work order opened for ${charger.chargerCode}.`);
      await loadAll();
    } catch (ticketError) { setError(ticketError instanceof Error ? ticketError.message : 'Unable to create maintenance work order.'); }
    finally { setSaving(false); }
  };

  const updateMaintenanceTicket = async (ticket: MaintenanceTicket, status: MaintenanceTicket['status'], assignedTo: string, resolutionNote: string, restoreChargerOnline: boolean) => {
    try {
      setSaving(true); setError('');
      await apiRequest(`/company/maintenance-tickets/${ticket.id}`, {
        method: 'PATCH', ...auth,
        body: JSON.stringify({ status, priority: ticket.priority, assignedTo: assignedTo || null, resolutionNote: resolutionNote || null, restoreChargerOnline }),
      });
      setNotice(`Work order #${ticket.id} moved to ${status.toLowerCase().replaceAll('_', ' ')}.`);
      await loadAll();
    } catch (ticketError) { setError(ticketError instanceof Error ? ticketError.message : 'Unable to update maintenance work order.'); }
    finally { setSaving(false); }
  };

  const markNotificationRead = async (id: number) => {
    try {
      await apiRequest(`/company/notifications/${id}/read`, { method: 'PATCH', ...auth });
      await loadAll();
    } catch (notificationError) { setError(notificationError instanceof Error ? notificationError.message : 'Unable to update notification.'); }
  };

  const markAllNotificationsRead = async () => {
    try {
      await apiRequest('/company/notifications/read-all', { method: 'PATCH', ...auth });
      setNotice('All company notifications marked as read.');
      await loadAll();
    } catch (notificationError) { setError(notificationError instanceof Error ? notificationError.message : 'Unable to update notifications.'); }
  };

  const sendVerificationCode = async () => {
    try {
      setSaving(true); setError('');
      await apiRequest('/company/email-verification/request', { method: 'POST', ...auth });
      setVerificationCode(''); setVerificationSent(true); setNotice('Verification code sent.'); await loadAll();
    } catch (verifyError) { setError(verifyError instanceof Error ? verifyError.message : 'Unable to send verification code.'); }
    finally { setSaving(false); }
  };

  const confirmVerificationCode = async () => {
    if (!/^\d{6}$/.test(verificationCode)) { setError('Enter the complete 6-digit verification code.'); return; }
    try {
      setSaving(true); setError('');
      await apiRequest('/company/email-verification/confirm', { method: 'POST', ...auth, body: JSON.stringify({ code: verificationCode }) });
      setEmailVerificationOpen(false); setVerificationSent(false); setVerificationCode('');
      setNotice('Company email verified.'); await loadAll();
    } catch (verifyError) { setError(verifyError instanceof Error ? verifyError.message : 'Unable to verify email.'); }
    finally { setSaving(false); }
  };

  const updateCompanySettings = async (changes: Partial<Pick<CompanyProfile, 'emailNotifications' | 'pushNotifications' | 'timezone'>>) => {
    if (!profile) return;
    try {
      setSaving(true); setError('');
      await apiRequest('/company/settings', { method: 'PUT', ...auth, body: JSON.stringify({
        emailNotifications: changes.emailNotifications ?? profile.emailNotifications,
        pushNotifications: changes.pushNotifications ?? profile.pushNotifications,
        timezone: changes.timezone ?? profile.timezone ?? 'Asia/Kolkata',
      }) });
      setNotice('Company notification settings updated.'); await loadAll();
    } catch (settingsError) { setError(settingsError instanceof Error ? settingsError.message : 'Unable to update company settings.'); }
    finally { setSaving(false); }
  };

  const askCompany = useCallback(async (prompt: string) => {
    try {
      setAssistantLoading(true); setError('');
      const response = await apiRequest<CompanyAgentResponse>('/company/ai/ask', { method: 'POST', ...auth, body: JSON.stringify({ question: prompt }) });
      setAssistantAnswer(response.answer);
      setSiteRecommendations(response.siteRecommendations ?? []);
      setAgentResponse(response);
    } catch (aiError) { setError(aiError instanceof Error ? aiError.message : 'Unable to answer right now.'); }
    finally { setAssistantLoading(false); }
  }, [auth]);

  const askAi = async () => askCompany(question);
  const analyzeExpansion = useCallback(async () => {
    setExpansionLoaded(true);
    await askCompany('Rank the best verified properties for our next charging station installation.');
  }, [askCompany]);

  useEffect(() => {
    if (tab === 'expansion' && !expansionLoaded) void analyzeExpansion();
  }, [analyzeExpansion, expansionLoaded, tab]);

  const updateAgentSettings = async (changes: Partial<CompanyAgentSettings>) => {
    try {
      setSaving(true); setError('');
      const next = { ...agentSettings, ...changes };
      const saved = await apiRequest<CompanyAgentSettings>('/company/ai/settings', { method: 'PUT', ...auth, body: JSON.stringify(next) });
      setAgentSettings(saved); setNotice('Vidyut action permission updated.');
    } catch (settingsError) { setError(settingsError instanceof Error ? settingsError.message : 'Unable to update Company Assistant settings.'); }
    finally { setSaving(false); }
  };

  const executeAgentAction = async (action: CompanyAgentResponse['actions'][number]) => {
    try {
      setSaving(true); setError('');
      const response = await apiRequest<{ state: string; message: string }>('/company/ai/actions', {
        method: 'POST', ...auth,
        body: JSON.stringify({ ...action, priority: 'HIGH', approved: true }),
      });
      setPendingAgentAction(null); setNotice(response.message); await loadAll(); await askAi();
    } catch (actionError) { setError(actionError instanceof Error ? actionError.message : 'Unable to process the Company Assistant action.'); }
    finally { setSaving(false); }
  };

  const download = async (type: 'ANALYTICS' | 'REVENUE', format: 'PDF' | 'XLSX') => {
    try {
      const blob = await apiDownload(`/company/reports/export?type=${type}&format=${format}`, token);
      const url = URL.createObjectURL(blob); const anchor = document.createElement('a');
      anchor.href = url; anchor.download = `vidyut-${type.toLowerCase()}.${format.toLowerCase()}`; anchor.click(); URL.revokeObjectURL(url);
      setNotice(`${format} report downloaded.`);
    } catch (downloadError) { setError(downloadError instanceof Error ? downloadError.message : 'Unable to download report.'); }
  };

  if (tab === 'catalog' || tab === 'host_opportunities' || tab === 'saved_properties' || tab === 'installation_pipeline') {
    return <CompanyMarketplaceView tab={tab === 'saved_properties' ? 'host_opportunities' : tab} token={token} savedOnly={tab === 'saved_properties'} marketplaceEnabled={verified}
      verificationStatus={verification?.status} onOpenVerification={() => onNavigate('settings')} />;
  }

  const title = ({ dashboard: 'Live operations dashboard', monitoring: 'Live network monitoring', stations: 'Charging stations', chargers: 'Charger network', bookings: 'Bookings & charging sessions', pricing: 'Pricing & revenue rules', analytics: 'Network analytics', revenue: 'Revenue & settlements', maintenance: 'Faults & maintenance', users: 'Employees & permissions', ai: 'Company Assistant', expansion: 'Expansion intelligence', reports: 'Reports & exports', notifications: 'Company notifications', settings: 'Company profile & settings' } as Record<string, string>)[tab] ?? 'Company workspace';

  return (
    <section className="company-workspace">
      <header className="company-page-head">
        <div><div className="feature-eyebrow">PRIVATE COMPANY WORKSPACE</div><h1>{title}</h1><p>{companyName} · Only your company’s authorized network and business data are shown.</p></div>
        <button className="wallet-refresh" onClick={() => void loadAll()} disabled={loading}><RefreshCw size={15} className={loading ? 'spinning' : ''} /> Refresh</button>
      </header>

      {!verified && <div className="company-verification-banner"><ShieldCheck size={20} /><div><strong>{verification?.status === 'UNDER_REVIEW' ? 'Verification under Vidyut review' : verification?.status === 'REJECTED' ? 'Verification needs correction' : 'Finish company verification'}</strong><span>{!profile?.emailVerified ? 'Verify your company email first. Business, representative, bank and charger evidence follow.' : verification?.status === 'UNDER_REVIEW' ? `${verification.completedLayers}/4 layers approved · Contacts, products and proposals stay protected until final approval.` : verification?.rejectionReason || `${verification?.completedLayers ?? 0}/4 layers approved · Complete all trust layers.`}</span></div><button onClick={() => !profile?.emailVerified ? setEmailVerificationOpen(true) : openModal('verification')}>{!profile?.emailVerified ? 'Verify email' : verification?.status === 'UNDER_REVIEW' ? 'Review submission' : 'Complete verification'}</button></div>}
      {error && <div className="wallet-message error" role="alert">{error}</div>}
      {notice && <div className="wallet-message success" role="status"><CheckCircle2 size={15} />{notice}</div>}

      {operationalAlerts.length > 0 && (
        <section className="company-urgent-incident-banner" role="alert" style={{
          background: 'linear-gradient(135deg, rgba(239, 68, 68, 0.12) 0%, rgba(220, 38, 38, 0.18) 100%)',
          border: '1px solid rgba(239, 68, 68, 0.4)',
          borderRadius: 14,
          padding: '14px 18px',
          marginBottom: 20,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 16,
          flexWrap: 'wrap'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <div style={{
              width: 42,
              height: 42,
              borderRadius: 10,
              background: 'rgba(239, 68, 68, 0.2)',
              color: '#ef4444',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0
            }}>
              <AlertTriangle size={22} />
            </div>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 3 }}>
                <span style={{
                  background: '#ef4444',
                  color: '#fff',
                  fontSize: 10,
                  fontWeight: 800,
                  padding: '2px 6px',
                  borderRadius: 4,
                  textTransform: 'uppercase'
                }}>OPERATIONAL SERVICE ALERT</span>
                <span style={{ fontSize: 11, color: '#fca5a5' }}>{operationalAlerts.length} connector(s) need attention</span>
              </div>
              <strong style={{ color: '#f8fafc', fontSize: 14 }}>{operationalAlerts[0].stationName} — {operationalAlerts[0].chargerCode}</strong>
              <p style={{ margin: '3px 0 0 0', fontSize: 12, color: '#cbd5e1' }}>
                {operationalAlerts[0].status} · {operationalAlerts[0].faultReason || 'Operator attention required. Review current journey impact with the Company Agent.'}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => {
              setQuestion("What needs attention today?");
              onNavigate("ai");
            }}
            style={{
              background: '#ef4444',
              color: '#fff',
              border: 'none',
              borderRadius: 8,
              padding: '8px 16px',
              fontSize: 13,
              fontWeight: 700,
              cursor: 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6
            }}
          >
            <Wrench size={14} />
            Ask Company Agent to Handle
          </button>
        </section>
      )}

      {tab === 'dashboard' && <CompanyDashboardPanel dashboard={dashboard} network={network} onNavigate={onNavigate} />}
      {tab === 'monitoring' && <CompanyLiveMonitoringPanel network={network} bookings={bookings} onNavigate={onNavigate} />}
      {(tab === 'stations' || tab === 'chargers') && <CompanyChargerSearch query={searchQuery} kind={tab} count={tab === 'stations' ? filteredStations.length : filteredChargers.length} city={matchingCities.length === 1 ? matchingCities[0] : undefined} loading={searchingNetwork} error={searchError} onClear={() => onSearchChange('')} onRetry={() => setNetworkVersion(version => version + 1)} />}
      {tab === 'stations' && <ResourceList title="My network" description="Company-owned and Host-partnered stations, without mixing ownership roles" action="Add station" icon={Building2} onAdd={() => openModal('station')} emptyTitle={searchingNetwork ? "Searching stations…" : searchError ? "Search unavailable" : searchQuery.trim() ? `No stations found for “${searchQuery.trim()}”` : "Nothing here yet"} empty={searchingNetwork ? "Checking your authorized network." : searchError || (searchQuery.trim() ? "Try a city, address or station name." : "Add a controlled site or partner with a verified Host property.")}>{filteredStations.map((station) => <ResourceRow key={station.id} icon={Building2} title={station.name} subtitle={`${station.ownershipType === 'HOST_PARTNERED' ? `Host property${station.propertyOwnerName ? ` · ${station.propertyOwnerName}` : ''}` : 'Company-owned / controlled'} · ${station.city} · ${station.connectors?.length ?? 0} chargers`} status={station.status} meta={`${money(station.pricePerKwh)}/kWh`} onEdit={() => openModal('station', station)} onDelete={station.ownershipType === 'COMPANY_OWNED' ? () => setPendingDelete({ kind: 'stations', id: station.id, label: station.name }) : undefined} />)}</ResourceList>}
      {tab === 'chargers' && <ResourceList title="Provisioned chargers" action="Add charger" icon={BatteryCharging} onAdd={() => openModal('charger')} emptyTitle={searchingNetwork ? "Searching chargers…" : searchError ? "Search unavailable" : searchQuery.trim() ? `No chargers found for “${searchQuery.trim()}”` : "Nothing here yet"} empty={searchingNetwork ? "Checking your authorized network." : searchError || (searchQuery.trim() ? "Try a city, station name or exact charger code." : "Add a station before provisioning chargers.")}>{filteredChargers.map((charger) => <ResourceRow key={charger.id} icon={BatteryCharging} title={charger.chargerCode} subtitle={`${charger.stationName} · ${charger.connectorType} · ${charger.powerKw} kW · Firmware ${charger.firmwareVersion}`} status={charger.status} meta={`${charger.healthScore}% health`} onEdit={() => openModal('charger', charger)} onDelete={charger.demoData ? undefined : () => setPendingDelete({ kind: 'chargers', id: charger.id, label: charger.chargerCode })} />)}</ResourceList>}
      {tab === 'bookings' && <BookingsPanel bookings={bookings} onUpdate={updateBooking} />}
      {tab === 'pricing' && <ResourceList title="Station pricing" action="Add station" icon={BadgeIndianRupee} onAdd={() => openModal('station')} empty="Create a station to configure tariffs.">{stations.map((station) => <ResourceRow key={station.id} icon={BadgeIndianRupee} title={station.name} subtitle={`Base ${money(station.pricePerKwh)}/kWh · Peak ${money(station.peakPricePerKwh ?? station.pricePerKwh)} · Student ${station.studentDiscountPercent ?? 0}% off`} status={station.dynamicPricingEnabled ? 'DYNAMIC' : 'FIXED'} meta={station.couponCode || 'No coupon'} onEdit={() => openModal('pricing', station)} />)}</ResourceList>}
      {tab === 'analytics' && <AnalyticsPanel analytics={analytics} dashboard={dashboard} />}
      {tab === 'revenue' && <CompanyRevenuePanel dashboard={dashboard} analytics={analytics} settlement={settlement} onDownload={download} />}
      {tab === 'maintenance' && <CompanyMaintenancePanel network={network} tickets={maintenanceTickets} employees={employees} saving={saving} onCreate={createMaintenanceTicket} onUpdate={updateMaintenanceTicket} />}
      {tab === 'users' && <div className="company-team-grid"><ResourceList title="Company employees" action="Add employee" icon={Users} onAdd={() => openModal('employee')} empty="Invite managers, operators and maintenance staff.">{employees.map((employee) => <ResourceRow key={employee.id} icon={Users} title={employee.name} subtitle={`${employee.email} · ${employee.permissions || 'Default permissions'}`} status={employee.active ? employee.role : 'INACTIVE'} meta={dateTime(employee.createdAt)} onEdit={() => openModal('employee', employee)} onDelete={() => setPendingDelete({ kind: 'employees', id: employee.id, label: employee.name })} />)}</ResourceList><ActivityLogPanel items={activityLogs} /></div>}
      {tab === 'ai' && <AiPanel companyName={companyName} question={question} setQuestion={setQuestion} answer={assistantAnswer} sites={siteRecommendations} response={agentResponse} settings={agentSettings} loading={assistantLoading} saving={saving} onAsk={askAi} onSettings={updateAgentSettings} onAction={async action => { setPendingAgentAction(action); }} onOpenOpportunities={() => onNavigate('host_opportunities')} dashboard={dashboard} />}
      {tab === 'expansion' && <ExpansionIntelligencePanel companyName={companyName} sites={siteRecommendations} answer={assistantAnswer} loading={assistantLoading} onRefresh={analyzeExpansion} onOpenOpportunities={() => onNavigate('host_opportunities')} onAskAssistant={() => { setQuestion('Compare the top expansion sites and explain the investment trade-offs.'); onNavigate('ai'); }} />}
      {tab === 'reports' && <ReportsPanel onDownload={download} />}
      {tab === 'notifications' && <CompanyNotificationsPanel items={notifications} onRead={markNotificationRead} onReadAll={markAllNotificationsRead} />}
      {tab === 'settings' && <SettingsPanel profile={profile} verification={verification} saving={saving} onProfile={() => openModal('profile')} onVerification={() => openModal('verification')} onVerifyEmail={() => setEmailVerificationOpen(true)} onSettings={updateCompanySettings} />}

      {pendingAgentAction && <OperatorApprovalDialog title={pendingAgentAction.action.includes('DEMO') ? 'DEMO ACTION' : 'Approve operational action'}
        description={pendingAgentAction.label} detail={pendingAgentAction.reason} saving={saving} error={error}
        onCancel={() => setPendingAgentAction(null)} onConfirm={() => void executeAgentAction(pendingAgentAction)} />}
      {pendingChargerSave && <OperatorApprovalDialog title={form.syntheticDemo ? 'Synthetic demo event' : 'Confirm operational change'}
        description={`${form.chargerCode}: ${form.expectedStatus} → ${form.status}`}
        detail={form.status === 'ONLINE' ? 'Restore this connector to service and clear its fault. Existing driver plans will not be changed by restoration.' : `${form.faultReason || 'Operator status update'}. Active journeys using this connector may need a replacement plan. Other healthy connectors remain usable.`}
        saving={saving} error={error} onCancel={() => setPendingChargerSave(false)} onConfirm={() => void submit(true)} />}
      {modal && !pendingChargerSave && modal !== 'verification' && <CompanyModal kind={modal} form={form} setForm={setForm} stations={stations} editing={Boolean(editingId)} saving={saving} onClose={() => { if (!saving) { setModal(null); setPendingChargerSave(false); } }} onSubmit={() => submit()} />}
      {modal === 'verification' && <CompanyVerificationFlow token={token} company={profile} onClose={() => setModal(null)} onSubmitted={() => { setModal(null); setNotice('Verification submitted for independent review.'); void loadAll(); }} />}
      {emailVerificationOpen && <EmailVerificationDialog code={verificationCode} sent={verificationSent} saving={saving} onCode={setVerificationCode} onSend={sendVerificationCode} onConfirm={confirmVerificationCode} onClose={() => setEmailVerificationOpen(false)} />}
      {pendingDelete && <DeleteConfirmationDialog label={pendingDelete.label} saving={saving} onCancel={() => setPendingDelete(null)} onConfirm={remove} />}
    </section>
  );
}

function CompanyDashboardPanel({ dashboard, network, onNavigate }: { dashboard: CompanyDashboard; network: CompanyNetwork; onNavigate: (tab: string) => void }) {
  const partneredStations = network.stations.filter(station => station.relationship === 'HOST_PARTNERED').length;
  return <>
    <div className="company-metric-grid"><Metric icon={Building2} label="Company-operated stations" value={dashboard.totalStations} /><Metric icon={BatteryCharging} label="Nationwide supplied chargers" value={network.totalChargers} /><Metric icon={Gauge} label="Live utilization" value={`${dashboard.utilizationRate}%`} tone="blue" /><Metric icon={CircleDollarSign} label="Recorded revenue" value={money(dashboard.revenue)} tone="purple" /></div>
    <div className="company-dashboard-grid">
      <article className="company-card company-operations-card"><div className="company-card-head"><div><h2>Live company operations</h2><p>Sessions, queue and energy at stations you operate</p></div><Activity size={20} /></div><div className="operations-strip"><SmallMetric label="Active sessions" value={dashboard.activeSessions} /><SmallMetric label="Queue" value={dashboard.queueCount} /><SmallMetric label="Energy" value={`${dashboard.energyDeliveredKwh} kWh`} /><SmallMetric label="Occupancy" value={`${dashboard.occupancyPercent}%`} /></div><div className="health-bars"><HealthBar label="Online" value={dashboard.onlineChargers} total={dashboard.totalChargers} color="#10b981" /><HealthBar label="Charging" value={dashboard.busyChargers} total={dashboard.totalChargers} color="#3478f6" /><HealthBar label="Fault/offline" value={dashboard.faults} total={dashboard.totalChargers} color="#ef4444" /></div></article>
      <article className="company-card company-national-card"><div className="company-card-head"><div><h2>Unified operating network</h2><p>Company sites and Host partnerships, with ownership kept explicit</p></div><button onClick={() => onNavigate('maintenance')}>Open network</button></div><div className="national-stat-grid"><SmallMetric label="Host partnerships" value={partneredStations} /><SmallMetric label="All chargers" value={network.totalChargers} /><SmallMetric label="Needs attention" value={network.faultedChargers} /><SmallMetric label="Open work orders" value={network.openMaintenanceTickets} /></div><div className="managed-station-preview">{network.stations.slice(0, 4).map(station => <div key={station.id}><span><Building2 size={15} /></span><div><strong>{station.name}</strong><small>{station.city || station.address} · {station.relationship === 'COMPANY_OWNED' ? 'Company-owned' : 'Host partnership'}</small></div><i className={station.faultedChargers ? 'danger' : ''}>{station.faultedChargers ? `${station.faultedChargers} alert` : 'Healthy'}</i></div>)}{!network.stations.length && <div className="company-empty compact"><Building2 size={24} /><p>Completed installations will appear here.</p></div>}</div></article>
    </div>
  </>;
}

function CompanyRevenuePanel({ dashboard, analytics, settlement, onDownload }: { dashboard: CompanyDashboard; analytics: Analytics | null; settlement: CompanySettlement; onDownload: (type: 'ANALYTICS' | 'REVENUE', format: 'PDF' | 'XLSX') => void }) {
  return <div className="company-revenue-layout">
    <div className="company-metric-grid"><Metric icon={CircleDollarSign} label="Collected" value={money(settlement.collected)} /><Metric icon={BadgeIndianRupee} label="Net recorded revenue" value={money(settlement.netRevenue)} tone="blue" /><Metric icon={CalendarDays} label="Pending settlement" value={money(settlement.pending)} tone="purple" /><Metric icon={RefreshCw} label="Refunded" value={money(settlement.refunded)} /></div>
    <div className="company-dashboard-grid"><article className="company-card revenue-hero"><CircleDollarSign size={24} /><span>Operating revenue</span><strong>{money(dashboard.revenue)}</strong><p>{money(analytics?.monthlyRevenue ?? 0)} this month · {settlement.successfulTransactions} successful payment records</p><div className="report-actions"><button onClick={() => onDownload('REVENUE', 'PDF')}><Download size={16} />Revenue PDF</button><button onClick={() => onDownload('REVENUE', 'XLSX')}><FileSpreadsheet size={16} />Revenue Excel</button></div></article><article className="company-card"><div className="company-card-head"><div><h2>Reconciliation summary</h2><p>Derived from real booking payment records</p></div><ShieldCheck size={20} /></div><div className="insight-list"><SmallMetric label="This week" value={money(analytics?.weeklyRevenue ?? 0)} /><SmallMetric label="This month" value={money(analytics?.monthlyRevenue ?? 0)} /><SmallMetric label="Refund exposure" value={money(settlement.refunded)} /></div></article></div>
    <article className="company-card resource-card"><div className="company-card-head"><div><h2>Recent payment ledger</h2><p>Gateway references and refund state remain auditable</p></div><BadgeIndianRupee size={20} /></div><div className="company-transaction-list">{settlement.recentTransactions.map(transaction => <div key={transaction.paymentId}><span><CircleDollarSign size={16} /></span><div><strong>{transaction.stationName}</strong><small>Payment #{transaction.paymentId}{transaction.gatewayTransactionId ? ` · ${transaction.gatewayTransactionId}` : ''}</small></div><b>{money(transaction.amount)}</b><i className={transaction.status === 'REFUNDED' || transaction.status === 'FAILED' ? 'danger' : ''}>{transaction.status}</i><time>{dateTime(transaction.timestamp)}</time></div>)}{!settlement.recentTransactions.length && <div className="company-empty"><CircleDollarSign size={27} /><h3>No payment records yet</h3><p>Completed paid sessions will appear here for reconciliation.</p></div>}</div></article>
  </div>;
}

function CompanyLiveMonitoringPanel({ network, bookings, onNavigate }: { network: CompanyNetwork; bookings: Booking[]; onNavigate: (tab: string) => void }) {
  const occupied = network.chargers.filter((charger) => charger.status === 'CHARGING').length;
  const available = network.chargers.filter((charger) => charger.status === 'ONLINE' && charger.available && !charger.maintenanceMode).length;
  const reserved = bookings.filter((booking) => booking.status === 'CONFIRMED' || booking.status === 'PENDING').length;
  const offline = network.chargers.filter((charger) => charger.status === 'OFFLINE' || charger.status === 'MAINTENANCE' || charger.maintenanceMode).length;
  const faults = network.chargers.filter((charger) => charger.status === 'FAULT' || charger.healthScore < 70).length;
  return <div className="company-monitoring-layout">
    <div className="company-monitoring-summary">
      <article><span className="monitoring-dot occupied" /><strong>{occupied}</strong><small>Occupied</small></article>
      <article><span className="monitoring-dot available" /><strong>{available}</strong><small>Available</small></article>
      <article><span className="monitoring-dot reserved" /><strong>{reserved}</strong><small>Reserved</small></article>
      <article><span className="monitoring-dot offline" /><strong>{offline}</strong><small>Offline / service</small></article>
      <article><span className="monitoring-dot fault" /><strong>{faults}</strong><small>Fault</small></article>
    </div>
    <article className="company-card resource-card">
      <div className="company-card-head"><div><h2>Live charger grid</h2><p>Company-operated infrastructure only · charger status is never inferred from the Host account</p></div><button onClick={() => onNavigate('maintenance')}>Open maintenance</button></div>
      <div className="company-live-grid"><header><span>Charger</span><span>Station</span><span>Connector</span><span>Health</span><span>Availability</span></header>{network.chargers.map((charger) => <div key={charger.id}><span><strong>{charger.chargerCode}</strong><small>{charger.relationship === 'COMPANY_OWNED' ? 'Company-owned' : 'Host-partnered'}</small></span><span><strong>{charger.stationName}</strong><small>{charger.city || 'India'}</small></span><span>{charger.connectorType}<small>{charger.powerKw} kW</small></span><span>{charger.healthScore}%<small>{dateTime(charger.lastHeartbeat)}</small></span><i className={`charger-state-${charger.status.toLowerCase()}`}>{charger.status === 'CHARGING' ? 'OCCUPIED' : charger.status === 'ONLINE' && charger.available ? 'AVAILABLE' : charger.status}</i></div>)}</div>
      {!network.chargers.length && <div className="company-empty"><BatteryCharging size={27} /><h3>No managed chargers yet</h3><p>Company-owned or commissioned Host chargers appear here once provisioned.</p></div>}
    </article>
  </div>;
}

function CompanyMaintenancePanel({ network, tickets, employees, saving, onCreate, onUpdate }: {
  network: CompanyNetwork;
  tickets: MaintenanceTicket[];
  employees: Employee[];
  saving: boolean;
  onCreate: (charger: ManagedCharger, priority: MaintenanceTicket['priority'], issue: string, assignedTo: string) => Promise<void>;
  onUpdate: (ticket: MaintenanceTicket, status: MaintenanceTicket['status'], assignedTo: string, resolutionNote: string, restoreChargerOnline: boolean) => Promise<void>;
}) {
  const [action, setAction] = useState<{ charger?: ManagedCharger; ticket?: MaintenanceTicket } | null>(null);
  const [priority, setPriority] = useState<MaintenanceTicket['priority']>('HIGH');
  const [ticketStatus, setTicketStatus] = useState<MaintenanceTicket['status']>('IN_PROGRESS');
  const [issue, setIssue] = useState('');
  const [assignedTo, setAssignedTo] = useState('');
  const [resolutionNote, setResolutionNote] = useState('');
  const [restoreOnline, setRestoreOnline] = useState(true);
  const activeTickets = tickets.filter(ticket => ticket.status === 'OPEN' || ticket.status === 'IN_PROGRESS');
  const faulted = network.chargers.filter(charger => charger.status === 'FAULT' || charger.status === 'OFFLINE' || charger.healthScore < 70 || charger.maintenanceMode);
  const openCreate = (charger: ManagedCharger) => {
    setPriority(charger.status === 'FAULT' || charger.healthScore < 40 ? 'CRITICAL' : 'HIGH');
    setIssue(charger.faultCode ? `${charger.faultCode}: charger requires diagnosis.` : `${charger.status} status with ${charger.healthScore}% health requires diagnosis.`);
    setAssignedTo(''); setResolutionNote(''); setAction({ charger });
  };
  const openUpdate = (ticket: MaintenanceTicket) => {
    setPriority(ticket.priority); setTicketStatus(ticket.status === 'OPEN' ? 'IN_PROGRESS' : ticket.status);
    setAssignedTo(ticket.assignedTo ?? ''); setResolutionNote(ticket.resolutionNote ?? ''); setRestoreOnline(true); setAction({ ticket });
  };
  const submitAction = async () => {
    if (action?.charger) await onCreate(action.charger, priority, issue.trim(), assignedTo);
    if (action?.ticket) await onUpdate(action.ticket, ticketStatus, assignedTo, resolutionNote.trim(), ticketStatus === 'RESOLVED' && restoreOnline);
    setAction(null);
  };
  return <div className="company-maintenance-layout">
    <div className="company-metric-grid"><Metric icon={Building2} label="Managed stations" value={network.totalStations} /><Metric icon={BatteryCharging} label="Managed chargers" value={network.totalChargers} /><Metric icon={AlertTriangle} label="Needs attention" value={network.faultedChargers} tone="purple" /><Metric icon={Wrench} label="Open work orders" value={network.openMaintenanceTickets} tone="blue" /></div>
    <div className="company-dashboard-grid"><article className="company-card resource-card"><div className="company-card-head"><div><h2>National failure queue</h2><p>All chargers this company operates, across both site types</p></div><AlertTriangle size={20} /></div><div className="company-failure-list">{faulted.map(charger => { const existing = activeTickets.find(ticket => ticket.chargerId === charger.id); return <div key={charger.id}><span className={charger.status === 'FAULT' ? 'critical' : ''}><AlertTriangle size={17} /></span><div><strong>{charger.chargerCode}</strong><small>{charger.stationName} · {charger.city || 'India'} · {charger.relationship === 'COMPANY_OWNED' ? 'Company-owned site' : 'Host partnership'}</small></div><b>{charger.healthScore}%</b>{existing ? <button onClick={() => openUpdate(existing)}>Work order #{existing.id}</button> : <button className="feature-primary" onClick={() => openCreate(charger)}>Create work order</button>}</div>; })}{!faulted.length && <div className="company-empty compact"><CheckCircle2 size={25} /><p>Every managed charger is healthy.</p></div>}</div></article><article className="company-card resource-card"><div className="company-card-head"><div><h2>Maintenance work orders</h2><p>Assign, resolve and restore equipment safely</p></div><Wrench size={20} /></div><div className="maintenance-ticket-list">{tickets.map(ticket => <button key={ticket.id} onClick={() => openUpdate(ticket)}><i className={`priority-${ticket.priority.toLowerCase()}`}>{ticket.priority}</i><span><strong>#{ticket.id} · {ticket.chargerCode}</strong><small>{ticket.issue}</small></span><b>{ticket.assignedTo || 'Unassigned'}</b><em>{ticket.status.replaceAll('_', ' ')}</em></button>)}{!tickets.length && <div className="company-empty compact"><Wrench size={25} /><p>No maintenance work orders yet.</p></div>}</div></article></div>
    <article className="company-card resource-card"><div className="company-card-head"><div><h2>Operating network inventory</h2><p>Live health and heartbeat with ownership type shown separately</p></div><Activity size={20} /></div><div className="managed-network-table"><header><span>Charger</span><span>Location</span><span>Site type</span><span>Health</span><span>Status</span></header>{network.chargers.map(charger => <div key={charger.id}><span><strong>{charger.chargerCode}</strong><small>{charger.connectorType} · {charger.powerKw} kW</small></span><span>{charger.stationName}<small>{charger.city || 'India'}</small></span><span>{charger.relationship === 'COMPANY_OWNED' ? 'Company-owned' : 'Host partnered'}</span><span>{charger.healthScore}%<small>{dateTime(charger.lastHeartbeat)}</small></span><i className={charger.status === 'FAULT' || charger.status === 'OFFLINE' ? 'danger' : ''}>{charger.status}</i></div>)}</div></article>
    {action && <div className="vehicle-form-backdrop" onMouseDown={() => setAction(null)}><section className="company-modal maintenance-work-modal" role="dialog" aria-modal="true" onMouseDown={event => event.stopPropagation()}><div className="company-card-head"><div><h2>{action.charger ? 'Create maintenance work order' : `Manage work order #${action.ticket?.id}`}</h2><p>{action.charger?.chargerCode || action.ticket?.chargerCode} · {action.charger?.stationName || action.ticket?.stationName}</p></div><button className="icon-button" onClick={() => setAction(null)}><X size={17} /></button></div><div className="company-form-grid"><label>Priority<select value={priority} disabled={Boolean(action.ticket)} onChange={event => setPriority(event.target.value as MaintenanceTicket['priority'])}><option>LOW</option><option>MEDIUM</option><option>HIGH</option><option>CRITICAL</option></select></label>{action.ticket && <label>Status<select value={ticketStatus} onChange={event => setTicketStatus(event.target.value as MaintenanceTicket['status'])}><option>OPEN</option><option>IN_PROGRESS</option><option>RESOLVED</option><option>CANCELLED</option></select></label>}<label>Assign to<select value={assignedTo} onChange={event => setAssignedTo(event.target.value)}><option value="">Unassigned</option>{employees.filter(employee => employee.active).map(employee => <option key={employee.id} value={employee.name}>{employee.name} · {employee.role}</option>)}</select></label>{action.charger && <label className="wide">Issue<textarea value={issue} onChange={event => setIssue(event.target.value)} /></label>}{action.ticket && <label className="wide">Resolution / progress note<textarea value={resolutionNote} onChange={event => setResolutionNote(event.target.value)} placeholder="Diagnosis, replacement or test result" /></label>}{action.ticket && ticketStatus === 'RESOLVED' && <label className="company-checkbox"><input type="checkbox" checked={restoreOnline} onChange={event => setRestoreOnline(event.target.checked)} />Restore charger online and clear fault</label>}</div><div className="company-modal-actions"><button className="secondary-action" onClick={() => setAction(null)}>Cancel</button><button className="feature-primary" disabled={saving || (Boolean(action.charger) && !issue.trim())} onClick={() => void submitAction()}>{saving ? 'Saving…' : action.charger ? 'Open work order' : 'Update work order'}</button></div></section></div>}
  </div>;
}

function CompanyNotificationsPanel({ items, onRead, onReadAll }: { items: NotificationItem[]; onRead: (id: number) => Promise<void>; onReadAll: () => Promise<void> }) {
  const unread = items.filter(item => !item.read).length;
  return <article className="company-card resource-card"><div className="company-card-head"><div><h2>Notification inbox</h2><p>{unread ? `${unread} unread company updates` : 'Email verification, alerts and scheduled updates'}</p></div>{unread > 0 ? <button onClick={() => void onReadAll()}>Mark all read</button> : <Bell size={20} />}</div><div className="notification-list company-notification-list">{items.map(item => <div className={item.read ? '' : 'unread'} key={item.id}><span><Bell size={17} /></span><div><strong>{item.title}</strong><p>{item.message}</p><small>{item.type.replaceAll('_', ' ')}</small></div><time>{dateTime(item.timestamp)}</time>{!item.read && <button onClick={() => void onRead(item.id)}>Mark read</button>}</div>)}{!items.length && <div className="company-empty"><Bell size={27} /><h3>All caught up</h3><p>Company notifications will appear here.</p></div>}</div></article>;
}

/* Superseded by CompanyDashboardPanel.
function Dashboard({ dashboard, onNavigate }: { dashboard: CompanyDashboard; onNavigate: (tab: string) => void }) {
  return <><div className="company-metric-grid"><Metric icon={Building2} label="Stations" value={dashboard.totalStations} /><Metric icon={BatteryCharging} label="Chargers" value={dashboard.totalChargers} /><Metric icon={Gauge} label="Utilization" value={`${dashboard.utilizationRate}%`} tone="blue" /><Metric icon={CircleDollarSign} label="Network revenue" value={money(dashboard.revenue)} tone="purple" /></div><div className="company-dashboard-grid"><article className="company-card company-operations-card"><div className="company-card-head"><div><h2>Live operations</h2><p>Sessions, queue and energy across your network</p></div><Activity size={20} /></div><div className="operations-strip"><SmallMetric label="Active sessions" value={dashboard.activeSessions} /><SmallMetric label="Queue" value={dashboard.queueCount} /><SmallMetric label="Energy" value={`${dashboard.energyDeliveredKwh} kWh`} /><SmallMetric label="Occupancy" value={`${dashboard.occupancyPercent}%`} /></div><div className="health-bars"><HealthBar label="Online" value={dashboard.onlineChargers} total={dashboard.totalChargers} color="#10b981" /><HealthBar label="Charging" value={dashboard.busyChargers} total={dashboard.totalChargers} color="#3478f6" /><HealthBar label="Fault/offline" value={dashboard.faults} total={dashboard.totalChargers} color="#ef4444" /></div></article><article className="company-card"><div className="company-card-head"><div><h2>Fault alerts</h2><p>Prioritized by charger health</p></div><button onClick={() => onNavigate('maintenance')}>View all</button></div><AlertRows alerts={dashboard.alerts} /></article></div></>;
}

*/
function ResourceList({ title, description = 'Protected company workspace records', action, icon: Icon, onAdd, empty, emptyTitle = 'Nothing here yet', children }: { title: string; description?: string; action: string; icon: typeof Building2; onAdd: () => void; empty: string; emptyTitle?: string; children: React.ReactNode }) {
  return <article className="company-card resource-card"><div className="company-card-head"><div><h2>{title}</h2><p>{description}</p></div><button className="feature-primary" onClick={onAdd}><Plus size={15} />{action}</button></div><div className="resource-list">{Children.count(children) > 0 ? children : <div className="company-empty"><Icon size={27} /><h3>{emptyTitle}</h3><p>{empty}</p><button className="feature-primary" onClick={onAdd}><Plus size={15} />{action}</button></div>}</div></article>;
}

function ResourceRow({ icon: Icon, title, subtitle, status, meta, onEdit, onDelete }: { icon: typeof Building2; title: string; subtitle: string; status: string; meta: string; onEdit: () => void; onDelete?: () => void }) {
  return <div className="resource-row"><span className="resource-icon"><Icon size={18} /></span><div className="resource-copy"><strong>{title}</strong><span>{subtitle}</span></div><div className="resource-meta"><b>{meta}</b><i className={/FAULT|OFFLINE|REJECTED/.test(status) ? 'danger' : ''}>{status}</i></div><button className="secondary-action" onClick={onEdit}>Edit</button>{onDelete && <button className="icon-button danger-icon" onClick={onDelete} aria-label={`Delete ${title}`}><Trash2 size={15} /></button>}</div>;
}

function BookingsPanel({ bookings, onUpdate }: { bookings: Booking[]; onUpdate: (id: number, status: 'CONFIRMED' | 'CANCELLED' | 'IN_PROGRESS' | 'COMPLETED') => void }) {
  return <article className="company-card resource-card"><div className="company-card-head"><div><h2>Network bookings & sessions</h2><p>Approve, cancel and advance charging sessions</p></div><CalendarDays size={20} /></div><div className="resource-list">{bookings.map((booking) => <div className="booking-operation-row" key={booking.id}><div><strong>{booking.stationName}</strong><span>{dateTime(booking.startTime)} · {booking.durationHours}h · {booking.kwhDelivered} kWh</span></div><b>{money(booking.totalAmount)}</b><i>{booking.status}</i><div>{booking.status === 'PENDING' && <button onClick={() => onUpdate(booking.id, 'CONFIRMED')}>Approve</button>}{booking.status === 'CONFIRMED' && <button onClick={() => onUpdate(booking.id, 'IN_PROGRESS')}>Start</button>}{booking.status === 'IN_PROGRESS' && <button onClick={() => onUpdate(booking.id, 'COMPLETED')}>Complete</button>}{!['COMPLETED','CANCELLED'].includes(booking.status) && <button className="danger" onClick={() => onUpdate(booking.id, 'CANCELLED')}>Cancel</button>}</div></div>)}{!bookings.length && <div className="company-empty"><CalendarDays size={27} /><h3>No bookings yet</h3><p>Reservations at your stations appear here automatically.</p></div>}</div></article>;
}

function AnalyticsPanel({ analytics, dashboard }: { analytics: Analytics | null; dashboard: CompanyDashboard }) { return <><div className="company-metric-grid"><Metric icon={CircleDollarSign} label="Today" value={money(analytics?.dailyRevenue ?? 0)} /><Metric icon={CircleDollarSign} label="This week" value={money(analytics?.weeklyRevenue ?? 0)} /><Metric icon={CircleDollarSign} label="This month" value={money(analytics?.monthlyRevenue ?? 0)} /><Metric icon={Users} label="Customer growth" value={`${analytics?.customerGrowthPercent ?? 0}%`} tone="purple" /></div><div className="company-dashboard-grid"><article className="company-card"><div className="company-card-head"><div><h2>Top performing stations</h2><p>Completed-session revenue</p></div><Gauge size={20} /></div><div className="rank-list">{(analytics?.topStations ?? []).map((item, index) => <div key={item.station}><b>#{index + 1}</b><span>{item.station}</span><strong>{money(item.revenue)}</strong></div>)}{!analytics?.topStations?.length && <div className="company-empty compact"><Gauge size={24} /><p>Complete sessions to populate analytics.</p></div>}</div></article><article className="company-card"><div className="company-card-head"><div><h2>Usage intelligence</h2><p>Demand and service performance</p></div><Activity size={20} /></div><div className="insight-list"><SmallMetric label="Peak usage" value={analytics?.peakUsageHour ?? '18:00'} /><SmallMetric label="Successful sessions" value={analytics?.successfulSessions ?? 0} /><SmallMetric label="Energy delivered" value={`${dashboard.energyDeliveredKwh} kWh`} /></div></article></div></> }

function RevenuePanel({ dashboard, analytics, onDownload }: { dashboard: CompanyDashboard; analytics: Analytics | null; onDownload: (type: 'ANALYTICS' | 'REVENUE', format: 'PDF' | 'XLSX') => void }) { return <div className="company-dashboard-grid"><article className="company-card revenue-hero"><CircleDollarSign size={24} /><span>Total recorded revenue</span><strong>{money(dashboard.revenue)}</strong><p>{money(analytics?.monthlyRevenue ?? 0)} this month · {money(analytics?.weeklyRevenue ?? 0)} this week</p></article><article className="company-card"><div className="company-card-head"><div><h2>Finance exports</h2><p>Ready for reconciliation and audit</p></div><Download size={20} /></div><div className="report-actions"><button onClick={() => onDownload('REVENUE','PDF')}><Download size={16} />Revenue PDF</button><button onClick={() => onDownload('REVENUE','XLSX')}><FileSpreadsheet size={16} />Revenue Excel</button></div></article></div> }

function MaintenancePanel({ chargers, alerts, onStatus }: { chargers: Charger[]; alerts: CompanyDashboard['alerts']; onStatus: (charger: Charger, status: Charger['status']) => void }) { return <div className="company-dashboard-grid"><article className="company-card"><div className="company-card-head"><div><h2>Fault alerts</h2><p>Health-based priority queue</p></div><AlertTriangle size={20} /></div><AlertRows alerts={alerts} /></article><article className="company-card resource-card"><div className="company-card-head"><div><h2>Maintenance controls</h2><p>Safely isolate or restore chargers</p></div><Wrench size={20} /></div><div className="maintenance-list">{chargers.map(charger => <div key={charger.id}><span><strong>{charger.chargerCode}</strong><small>{charger.stationName} · {charger.healthScore}% health</small></span><select value={charger.status} onChange={event => void onStatus(charger, event.target.value as Charger['status'])}><option>ONLINE</option><option>OFFLINE</option><option>CHARGING</option><option>MAINTENANCE</option><option>FAULT</option></select></div>)}</div></article></div> }

function AiPanel({ companyName, question, setQuestion, answer, sites, response, settings, loading, saving, onAsk, onSettings, onAction, onOpenOpportunities, dashboard }: {
  companyName: string;
  question: string;
  setQuestion: (value: string) => void;
  answer: string;
  sites: SiteRecommendation[];
  response: CompanyAgentResponse | null;
  settings: CompanyAgentSettings;
  loading: boolean;
  saving: boolean;
  onAsk: () => void;
  onSettings: (changes: Partial<CompanyAgentSettings>) => Promise<void>;
  onAction: (action: CompanyAgentResponse['actions'][number]) => Promise<void>;
  onOpenOpportunities: () => void;
  dashboard: CompanyDashboard;
}) {
  const modes: Array<{ id: CompanyAgentMode; label: string; detail: string }> = [
    { id: 'RECOMMEND_ONLY', label: 'Recommend only', detail: 'Vidyut recommends; your team acts' },
    { id: 'ASK_BEFORE_ACTIONS', label: 'Ask before actions', detail: 'Vidyut waits for team approval' },
    { id: 'AUTOPILOT', label: 'Monitor and prepare', detail: 'Every write still requires approval' },
  ];
  return <div className="company-agent-page">
    <section className="company-agent-authority company-card">
      <div><span className="feature-eyebrow">ACTION PERMISSION</span><h2>Choose how Vidyut may act</h2><p>Vidyut always monitors and analyzes your network. You decide whether it may carry out operational actions.</p></div>
      <div className="company-agent-modes">{modes.map((mode) => <button key={mode.id} className={settings.mode === mode.id ? 'active' : ''} disabled={saving} onClick={() => void onSettings({ mode: mode.id })}><span>{mode.label}</span><small>{mode.detail}</small>{settings.mode === mode.id && <CheckCircle2 size={16} />}</button>)}</div>
      <div className="company-agent-limit-heading"><strong>Operational safety limits</strong><span>Every write requires approval in every mode.</span></div>
      <div className="company-agent-limits"><label>Maximum approved price change <strong>{settings.maxPriceChangePercent}%</strong><input type="range" min="0" max="25" step="1" value={settings.maxPriceChangePercent} disabled={saving} onChange={event => void onSettings({ maxPriceChangePercent: Number(event.target.value) })} /></label><p>Host partners report issues. Company operators control equipment. EV Owners approve replacement journeys.</p></div>
    </section>
    <div className="ai-company-layout">
    <article className="company-card ai-company-card">
      <div className="ai-orb"><Bot size={30} /></div>
      <div><div className="feature-eyebrow">ASK VIDYUT</div><h2>Charging network operator assistant</h2><p>Responses use only {companyName}’s stations, chargers, sessions, partnerships, maintenance and revenue.</p></div>
      <div className="ai-answer"><Sparkline /><div><strong>{loading ? 'Analyzing live company data…' : answer}</strong>{response?.assistantProvider && <small className={`agent-provider ${response.assistantFallback ? 'fallback' : ''}`}>{response.assistantFallback ? 'Rules fallback' : response.assistantProvider}{response.assistantModel ? ` · ${response.assistantModel}` : ''}</small>}</div></div>
      {response?.operations && ['FAULT','OWNERSHIP','CONNECTOR_AVAILABILITY'].includes(response.intent) && <div className="company-operator-evidence">
        {response.operations.stations.filter(station => response.intent !== 'FAULT' || station.issueCount > 0).slice(0, 6).map(station => <article key={station.stationId}>
          <strong>{station.stationName} · {station.city}</strong><small>{station.ownershipType}{station.propertyTitle ? ` · ${station.propertyTitle}` : ''}{station.hostName ? ` · Host: ${station.hostName}` : ''}</small>
          <small>{station.unavailableConnectors} unavailable connectors · {station.affectedJourneyIds.length} affected journeys · {station.activeBookingIds.length} active station bookings</small>
          <small>{station.availableCcs2}/{station.ccs2Connectors} CCS2 available · Recorded downtime: {station.downtimeMinutes == null ? 'unknown' : `${station.downtimeMinutes} min`}</small>
        </article>)}
      </div>}
      {response?.network && <div className="company-agent-network"><span><strong>{response.network.occupied}</strong><small>Occupied</small></span><span><strong>{response.network.available}</strong><small>Available</small></span><span><strong>{response.network.reserved}</strong><small>Reserved</small></span><span><strong>{response.network.offline}</strong><small>Offline</small></span><span><strong>{response.network.faults}</strong><small>Faults</small></span></div>}
      {response?.fault && <section className="company-agent-fault"><header><AlertTriangle size={19} /><div><strong>{response.fault.chargerCode} fault · {response.fault.stationName}</strong><span>{response.fault.issue}</span></div></header><div><span><small>Affected bookings</small><strong>{response.fault.affectedBookings}</strong></span><span><small>Estimated downtime</small><strong>{response.fault.estimatedDowntimeMinutes == null ? 'Not recorded' : `${response.fault.estimatedDowntimeMinutes} min`}</strong></span><span><small>Station booking value</small><strong>{money(response.fault.estimatedRevenueAtRisk)}</strong></span></div>{response.fault.compatibleBackups.length > 0 && <p>Compatible backup: {response.fault.compatibleBackups.join(' · ')}</p>}</section>}
      {response?.revenue && response.intent === 'REVENUE' && <section className="company-agent-revenue"><span><small>Sessions</small><strong>{response.revenue.sessions}</strong></span><span><small>Energy sold</small><strong>{response.revenue.energySoldKwh} kWh</strong></span><span><small>Charging revenue</small><strong>{money(response.revenue.chargingRevenue)}</strong></span><span><small>Host payouts</small><strong>− {response.revenue.estimatedHostPayouts == null ? 'Not recorded' : money(response.revenue.estimatedHostPayouts)}</strong></span><span><small>Vidyut fees</small><strong>− {response.revenue.estimatedVidyutFees == null ? 'Not recorded' : money(response.revenue.estimatedVidyutFees)}</strong></span><span><small>Estimated company revenue</small><strong>{response.revenue.estimatedCompanyRevenue == null ? 'Not available' : money(response.revenue.estimatedCompanyRevenue)}</strong></span></section>}
      {response?.pricing && response.intent === 'PRICING' && <section className="company-agent-pricing"><div><small>{response.pricing.stationName}</small><strong>{money(response.pricing.currentPricePerKwh)}/kWh → {money(response.pricing.recommendedPricePerKwh)}/kWh</strong><p>Nearby average {money(response.pricing.nearbyAveragePricePerKwh)} · test {response.pricing.timeWindow} · utilization {response.pricing.currentUtilizationPercent}% · demand change unknown</p></div></section>}
      {response?.offerDraft && Object.keys(response.offerDraft).length > 0 && <section className="company-agent-offer"><header><strong>Reviewable offer draft</strong><span>Nothing has been sent</span></header><div><span><small>Property</small><strong>{String(response.offerDraft.property)}</strong></span><span><small>Installation</small><strong>{String(response.offerDraft.installation)}</strong></span><span><small>Company investment</small><strong>{response.offerDraft.companyInvestment == null ? 'Terms needed' : money(Number(response.offerDraft.companyInvestment))}</strong></span><span><small>Host revenue share</small><strong>{response.offerDraft.hostRevenueSharePercent == null ? 'Terms needed' : `${response.offerDraft.hostRevenueSharePercent}%`}</strong></span></div><button onClick={onOpenOpportunities}>Open property workflow</button></section>}
      {response?.actions && response.actions.length > 0 && <div className="company-agent-actions"><header><strong>Recommended actions</strong><span>{settings.mode === 'RECOMMEND_ONLY' ? 'Manual only' : 'Approval required'}</span></header>{response.actions.map((action) => <article key={action.action}><div><strong>{action.label}</strong><small>{action.reason} · {action.risk.toLowerCase()} risk</small></div><button disabled={saving || settings.mode === 'RECOMMEND_ONLY'} onClick={() => void onAction(action)}>Review action</button></article>)}</div>}
      {sites.length > 0 && response?.intent === 'EXPANSION' && <div className="company-ai-sites">
        <header><div><strong>Expansion shortlist</strong><span>Grid, parking and network-gap score</span></div><button onClick={onOpenOpportunities}>Open all Host land</button></header>
        {sites.slice(0, 3).map((site, index) => <article key={site.propertyId}>
          <b>#{index + 1}</b><div><strong>{site.title}</strong><span><MapPin size={12} /> {site.location}</span><small>{site.reason}</small></div>
          <aside><strong>{site.expansionScore.toFixed(0)}</strong><span>score</span><small>{site.recommendedChargerCount ?? 1} × {site.recommendedPowerKw ?? 60} kW {site.recommendedConnector ?? 'CCS2'}</small></aside>
        </article>)}
      </div>}
      <div className="ai-input"><input value={question} onChange={event => setQuestion(event.target.value)} onKeyDown={event => { if (event.key === 'Enter') void onAsk(); }} placeholder="Ask about occupied chargers, a fault, revenue, pricing, properties or an offer…" /><button onClick={() => void onAsk()} disabled={loading}><Send size={17} /></button></div>
      <div className="ai-prompts">{['Which station needs the most attention right now?', 'Show me all Host-partnered stations with operational issues.', 'Which Host property is the best candidate for our next 120 kW CCS2 charger?', 'Compare the partnership offers for the Agra property.', 'Simulate a fault on the Agra demo CCS2 connector.', 'Restore DEMO-AGRA-CCS2-01', 'Which stations have only AC charging?'].map(item => <button key={item} onClick={() => setQuestion(item)}>{item}</button>)}</div>
    </article>
    <aside className="company-card"><div className="company-card-head"><div><h2>Live company data</h2><p>Visible only inside {companyName}’s workspace</p></div><Activity size={20} /></div><div className="insight-list"><SmallMetric label="Stations" value={dashboard.totalStations} /><SmallMetric label="Faults" value={dashboard.faults} /><SmallMetric label="Utilization" value={`${dashboard.utilizationRate}%`} /><SmallMetric label="Revenue" value={money(dashboard.revenue)} /></div><div className="company-agent-boundary"><ShieldCheck size={18} /><p><strong>One assistant, company scope only</strong>Vidyut can analyze and operate {companyName}’s infrastructure according to the action permission selected above. Platform-wide control remains with Admin.</p></div></aside>
  </div></div>;
}

function ExpansionIntelligencePanel({ companyName, sites, answer, loading, onRefresh, onOpenOpportunities, onAskAssistant }: {
  companyName: string;
  sites: SiteRecommendation[];
  answer: string;
  loading: boolean;
  onRefresh: () => Promise<void>;
  onOpenOpportunities: () => void;
  onAskAssistant: () => void;
}) {
  const topSite = sites[0];
  const plannedChargers = sites.reduce((total, site) => total + (site.recommendedChargerCount ?? 1), 0);
  const averageScore = sites.length ? sites.reduce((total, site) => total + site.expansionScore, 0) / sites.length : 0;
  return <div className="company-expansion-page">
    <section className="company-card expansion-intelligence-hero">
      <div><span className="feature-eyebrow">NETWORK GROWTH MODEL</span><h2>Rank verified sites before committing capital</h2><p>Vidyut compares Host properties using grid capacity, parking readiness and distance from the existing charging network.</p></div>
      <div className="expansion-hero-actions"><button onClick={() => void onRefresh()} disabled={loading}><RefreshCw className={loading ? 'spinning' : ''} size={15} />{loading ? 'Analyzing…' : 'Refresh analysis'}</button><button className="feature-primary" onClick={onOpenOpportunities}>Review Host properties</button></div>
    </section>

    <div className="expansion-kpi-grid">
      <article><Building2 size={19} /><span><strong>{sites.length}</strong><small>verified candidates</small></span></article>
      <article><Gauge size={19} /><span><strong>{topSite ? topSite.expansionScore.toFixed(0) : '—'}</strong><small>highest expansion score</small></span></article>
      <article><BatteryCharging size={19} /><span><strong>{plannedChargers}</strong><small>chargers across shortlist</small></span></article>
      <article><MapPin size={19} /><span><strong>{averageScore ? averageScore.toFixed(0) : '—'}</strong><small>average site score</small></span></article>
    </div>

    <div className="expansion-intelligence-layout">
      <section className="company-card expansion-ranking-card">
        <header><div><span className="feature-eyebrow">PRIORITY RANKING</span><h2>Recommended expansion sites</h2><p>Higher scores indicate stronger site readiness and a more useful network-coverage gap.</p></div><strong>{companyName}</strong></header>
        {loading && !sites.length && <div className="company-empty"><RefreshCw className="spinning" size={27} /><h3>Analyzing verified properties</h3><p>Comparing electrical load, parking capacity and nearby active stations.</p></div>}
        {!loading && !sites.length && <div className="company-empty"><MapPin size={27} /><h3>No verified candidates yet</h3><p>Open the property marketplace when Hosts publish verified installation sites.</p><button onClick={onOpenOpportunities}>Open property marketplace</button></div>}
        <div className="expansion-site-ranking">{sites.map((site, index) => <article key={site.propertyId}>
          <b>#{index + 1}</b>
          <div className="expansion-site-copy"><strong>{site.title}</strong><span><MapPin size={12} />{site.location}</span><p>{site.reason}</p></div>
          <div className="expansion-site-evidence"><span><small>Grid capacity</small><strong>{site.availableLoadKw} kW</strong></span><span><small>Parking</small><strong>{site.parkingBays} bays</strong></span><span><small>Network gap</small><strong>{site.nearestActiveStationKm == null ? 'Unknown' : `${site.nearestActiveStationKm} km`}</strong></span><span><small>Recommended setup</small><strong>{site.recommendedChargerCount ?? 1} × {site.recommendedPowerKw ?? 60} kW {site.recommendedConnector ?? 'CCS2'}</strong></span></div>
          <div className="expansion-site-score"><strong>{site.expansionScore.toFixed(0)}</strong><small>/100</small><button onClick={onOpenOpportunities}>Review site</button></div>
        </article>)}</div>
      </section>

      <aside className="company-expansion-aside">
        <section className="company-card expansion-method-card"><span className="feature-eyebrow">HOW SITES ARE SCORED</span><h2>Decision evidence</h2><div><span><strong>Electrical capacity</strong><small>Available sanctioned load contributes up to 45 points.</small></span><span><strong>Parking readiness</strong><small>Each usable parking bay strengthens installation capacity.</small></span><span><strong>Network coverage gap</strong><small>Underserved areas contribute up to 35 points.</small></span></div><p>Final investment still requires a site survey, grid confirmation and commercial approval.</p></section>
        <section className="company-card expansion-assistant-bridge"><Bot size={23} /><span><small>LATEST MODEL FINDING</small><strong>{loading ? 'Recalculating the shortlist…' : answer}</strong></span><button onClick={onAskAssistant}>Discuss trade-offs with Company Assistant</button></section>
      </aside>
    </div>
  </div>;
}

function ReportsPanel({ onDownload }: { onDownload: (type: 'ANALYTICS' | 'REVENUE', format: 'PDF' | 'XLSX') => void }) { return <article className="company-card"><div className="company-card-head"><div><h2>Reports & notifications</h2><p>Generate real PDF and Excel workbooks from company data</p></div><Download size={20} /></div><div className="reports-grid"><ReportCard icon={Gauge} title="Analytics report" text="Utilization, peak usage, station performance, growth and energy." onPdf={() => onDownload('ANALYTICS','PDF')} onExcel={() => onDownload('ANALYTICS','XLSX')} /><ReportCard icon={CircleDollarSign} title="Revenue report" text="Daily, weekly and monthly revenue with station totals." onPdf={() => onDownload('REVENUE','PDF')} onExcel={() => onDownload('REVENUE','XLSX')} /></div></article> }

function NotificationsPanel({ items }: { items: NotificationItem[] }) { return <article className="company-card resource-card"><div className="company-card-head"><div><h2>Notification inbox</h2><p>Email verification, alerts and scheduled updates</p></div><Bell size={20} /></div><div className="notification-list">{items.map(item => <div key={item.id}><span><Bell size={17} /></span><div><strong>{item.title}</strong><p>{item.message}</p></div><time>{dateTime(item.timestamp)}</time></div>)}{!items.length && <div className="company-empty"><Bell size={27} /><h3>All caught up</h3><p>Company notifications will appear here.</p></div>}</div></article> }

function ActivityLogPanel({ items }: { items: ActivityLog[] }) { return <article className="company-card activity-log-card"><div className="company-card-head"><div><h2>Activity logs</h2><p>Latest protected company changes</p></div><ShieldCheck size={20} /></div><div className="activity-log-list">{items.slice(0, 30).map(item => <div key={item.id}><span><ShieldCheck size={15} /></span><div><strong>{item.description}</strong><small>{item.action} · {item.resourceType} · Account #{item.actorAccountId}</small></div><time>{dateTime(item.createdAt)}</time></div>)}{!items.length && <div className="company-empty compact"><ShieldCheck size={25} /><p>Protected changes will appear here.</p></div>}</div></article> }

function SettingsPanel({ profile, verification, saving, onProfile, onVerification, onVerifyEmail, onSettings }: { profile: CompanyProfile | null; verification: CompanyVerificationSummary | null; saving: boolean; onProfile: () => void; onVerification: () => void; onVerifyEmail: () => void; onSettings: (changes: Partial<Pick<CompanyProfile, 'emailNotifications' | 'pushNotifications' | 'timezone'>>) => void }) { return <div className="company-settings-grid"><article className="company-card profile-company-card"><div className="company-profile-mark"><Building2 size={28} /></div><h2>{profile?.companyName}</h2><p>{profile?.registrationNumber}</p><div className="verification-pills"><i className={profile?.emailVerified ? 'verified' : ''}>{profile?.emailVerified ? 'Email verified' : 'Email pending'}</i><i className={verification?.marketplaceEnabled ? 'verified' : ''}>{verification?.trustLevel?.replaceAll('_',' ') ?? 'UNVERIFIED'}</i></div><button className="feature-primary" onClick={onProfile}><Settings size={15} />Edit profile</button></article><article className="company-card"><div className="company-card-head"><div><h2>Verification & security</h2><p>Required before products, contact and proposals</p></div><ShieldCheck size={20} /></div><div className="settings-actions"><button onClick={() => void onVerifyEmail()} disabled={profile?.emailVerified}><CheckCircle2 size={17} /><span><strong>Company email</strong><small>{profile?.emailVerified ? 'Verified' : 'Send a 6-digit code'}</small></span></button><button onClick={onVerification}><ShieldCheck size={17} /><span><strong>Four-layer verification</strong><small>{verification?.status ?? 'NOT STARTED'} · {verification?.completedLayers ?? 0}/4 approved</small></span></button></div></article><article className="company-card"><div className="company-card-head"><div><h2>Reports & alerts</h2><p>Choose how this company receives updates</p></div><Bell size={20} /></div><div className="company-preferences"><label><span><strong>Email reports</strong><small>Revenue and operational summaries</small></span><input type="checkbox" checked={profile?.emailNotifications ?? false} disabled={saving} onChange={event => void onSettings({ emailNotifications: event.target.checked })} /></label><label><span><strong>Push fault alerts</strong><small>Offline and low-health charger warnings</small></span><input type="checkbox" checked={profile?.pushNotifications ?? false} disabled={saving} onChange={event => void onSettings({ pushNotifications: event.target.checked })} /></label><label className="timezone-setting"><span><strong>Reporting timezone</strong><small>Used for dashboards and exports</small></span><select value={profile?.timezone ?? 'Asia/Kolkata'} disabled={saving} onChange={event => void onSettings({ timezone: event.target.value })}><option value="Asia/Kolkata">India (IST)</option><option value="Asia/Dubai">Dubai (GST)</option><option value="Europe/London">London (GMT)</option><option value="America/New_York">New York (ET)</option></select></label></div></article></div> }

function CompanyModal({ kind, form, setForm, stations, editing, saving, onClose, onSubmit }: { kind: Exclude<ModalKind, null>; form: Record<string, string | number | boolean>; setForm: React.Dispatch<React.SetStateAction<Record<string, string | number | boolean>>>; stations: Station[]; editing: boolean; saving: boolean; onClose: () => void; onSubmit: () => void }) {
  const field = (name: string, label: string, type = 'text') => <label>{label}<input type={type} value={String(form[name] ?? '')} onChange={event => setForm(current => ({ ...current, [name]: type === 'number' ? Number(event.target.value) : event.target.value }))} /></label>;
  const select = (name: string, label: string, options: Array<string | { value: number; label: string }>) => <label>{label}<select value={String(form[name] ?? '')} onChange={event => setForm(current => ({ ...current, [name]: name === 'stationId' ? Number(event.target.value) : event.target.value }))}>{options.map(option => typeof option === 'string' ? <option key={option}>{option}</option> : <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>;
  return <div className="vehicle-form-backdrop" onMouseDown={onClose}>
    <section className="company-modal" role="dialog" aria-modal="true" onMouseDown={event => event.stopPropagation()}>
      <div className="company-card-head"><div><h2>{editing ? 'Edit' : 'Add'} {kind}</h2><p>Changes stay inside this company workspace.</p></div><button className="icon-button" onClick={onClose} aria-label="Close form"><X size={17} /></button></div>
      <div className="company-form-grid">
        {kind === 'station' && <>
          {!editing && <div className="company-site-choice wide">
            <strong>Who owns or controls this property?</strong>
            <p>This choice changes the workflow; it does not create a Host identity for your company.</p>
            <div><button className={form.stationSource === 'COMPANY_OWNED' ? 'selected' : ''} onClick={() => setForm(current => ({ ...current, stationSource: 'COMPANY_OWNED' }))}><Building2 size={18} /><span><b>Our company</b><small>Direct station setup after company and site evidence</small></span></button><button className={form.stationSource === 'HOST_PARTNERED' ? 'selected' : ''} onClick={() => setForm(current => ({ ...current, stationSource: 'HOST_PARTNERED' }))}><Users size={18} /><span><b>Host partner</b><small>Choose property, review, offer, agreement and commissioning</small></span></button></div>
          </div>}
          {(!editing && form.stationSource === 'HOST_PARTNERED') ? <div className="company-partner-route wide"><ShieldCheck size={21} /><div><strong>Use the verified Host property marketplace</strong><p>Vidyut will retain the Host as property owner and your company as station operator. The partnership is created only after the agreement workflow.</p></div></div> : <>
            {!editing && field('propertyOwnerName','Registered property owner')}{field('name','Station name')}{field('city','City')}{field('address','Full address')}
            {field('latitude','Latitude','number')}{field('longitude','Longitude','number')}{field('pricePerKwh','Base ₹/kWh','number')}
            {!editing && select('connectorType','Initial connector',['CCS2','TYPE2','CHADEMO','GB_T'])}{!editing && field('powerKw','Power kW','number')}
            {field('amenities','Amenities')}{field('workingHours','Working hours')}{field('imageUrl','Primary photo URL')}{field('photoUrls','Additional photo URLs')}
            {!editing && field('siteOwnershipDocumentUrl','Property ownership / control evidence URL')}{!editing && field('electricityConnectionDocumentUrl','Electricity connection evidence URL')}
            {editing && select('status','Station status',['ACTIVE','MAINTENANCE','OFFLINE'])}{editing && select('availability','Availability',['AVAILABLE','CHARGING','RESERVED','UNAVAILABLE'])}
            {editing && field('queueCount','Queue count','number')}{editing && field('occupancyPercent','Occupancy %','number')}
          </>}
        </>}
        {kind === 'charger' && <>
          {select('stationId','Station',stations.map(station => ({ value: station.id, label: station.name })))}{field('chargerCode','Charger code')}
          {select('connectorType','Connector',['CCS2','TYPE2','CHADEMO','GB_T'])}{field('powerKw','Power kW','number')}
          {select('status','Operational status',['ONLINE','OFFLINE','CHARGING','MAINTENANCE','FAULT','SUSPECTED_FAULT'])}{field('firmwareVersion','Firmware version')}{field('healthScore','Health score','number')}
          <label className="company-checkbox"><input type="checkbox" checked={form.status === 'MAINTENANCE'} disabled />Maintenance follows operational status</label>
          {form.status === 'FAULT' && field('faultReason','Fault reason')}
          {form.syntheticDemo && <div className="company-demo-review wide"><strong>DEMO CONTROLS · Synthetic telemetry</strong><p>{String(form.chargerCode)} · Change ONLINE → FAULT to prepare an incident. Change FAULT → ONLINE to restore this connector. Saving requires review and confirmation.</p></div>}
        </>}
        {kind === 'employee' && <>{field('name','Full name')}{field('email','Work email','email')}{field('phone','Phone')}{select('role','Role',['MANAGER','OPERATOR','MAINTENANCE','FINANCE','ANALYST'])}{field('permissions','Permissions')}<label className="company-checkbox"><input type="checkbox" checked={Boolean(form.active)} onChange={event => setForm(current => ({ ...current, active: event.target.checked }))} />Active employee</label></>}
        {kind === 'pricing' && <>{field('pricePerKwh','Base ₹/kWh','number')}{field('timeBasedPricePerHour','Time-based ₹/hour','number')}{field('peakPricePerKwh','Peak ₹/kWh','number')}{field('peakHours','Peak hours')}{field('studentDiscountPercent','Student discount %','number')}{field('corporatePricePerKwh','Corporate ₹/kWh','number')}{field('couponCode','Coupon code')}{field('couponDiscountPercent','Coupon discount %','number')}<label className="company-checkbox"><input type="checkbox" checked={Boolean(form.dynamicPricingEnabled)} onChange={event => setForm(current => ({ ...current, dynamicPricingEnabled: event.target.checked }))} />Dynamic pricing</label></>}
        {kind === 'profile' && <>{field('companyName','Company name')}{field('contactName','Primary contact')}{field('supportEmail','Support email','email')}{field('supportPhone','Support phone')}{field('businessAddress','Business address')}{field('website','Website')}</>}
        {kind === 'verification' && <>{field('gstNumber','GST number')}{field('kycDocumentUrl','KYC document URL')}{field('businessAddress','Registered business address')}</>}
      </div>
      <div className="company-modal-actions"><button className="secondary-action" onClick={onClose}>Cancel</button><button className="feature-primary" onClick={() => void onSubmit()} disabled={saving}>{saving ? 'Saving…' : kind === 'station' && !editing && form.stationSource === 'HOST_PARTNERED' ? 'Open Host property marketplace' : 'Save changes'}</button></div>
    </section>
  </div>;
}

function EmailVerificationDialog({ code, sent, saving, onCode, onSend, onConfirm, onClose }: { code: string; sent: boolean; saving: boolean; onCode: (value: string) => void; onSend: () => void; onConfirm: () => void; onClose: () => void }) {
  return <div className="vehicle-form-backdrop" onMouseDown={onClose}><section className="company-modal company-verification-dialog" role="dialog" aria-modal="true" aria-labelledby="company-email-title" onMouseDown={event => event.stopPropagation()}><div className="company-card-head"><div><span className="company-dialog-icon"><ShieldCheck size={21} /></span><h2 id="company-email-title">Verify company email</h2><p>Secure this workspace before submitting business documents.</p></div><button className="icon-button" onClick={onClose} aria-label="Close email verification"><X size={17} /></button></div>{!sent ? <div className="company-verification-step"><p>We will create a single-use, 6-digit code that expires in 15 minutes.</p><button className="feature-primary" onClick={() => void onSend()} disabled={saving}>{saving ? 'Sending…' : 'Send verification code'}</button></div> : <div className="company-verification-step"><label htmlFor="company-verification-code">6-digit verification code</label><input id="company-verification-code" className="company-code-input" inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={code} onChange={event => onCode(event.target.value.replace(/\D/g, '').slice(0, 6))} placeholder="000000" autoFocus /><p>Check your company email inbox. You can return here without requesting a new code.</p><div className="company-verification-links"><button className="secondary-action" onClick={() => void onSend()} disabled={saving}>Resend code</button></div><div className="company-modal-actions"><button className="secondary-action" onClick={onClose}>Do this later</button><button className="feature-primary" onClick={() => void onConfirm()} disabled={saving || code.length !== 6}>{saving ? 'Verifying…' : 'Verify email'}</button></div></div>}</section></div>;
}

function DeleteConfirmationDialog({ label, saving, onCancel, onConfirm }: { label: string; saving: boolean; onCancel: () => void; onConfirm: () => void }) {
  return <div className="vehicle-form-backdrop" onMouseDown={onCancel}><section className="company-modal company-delete-dialog" role="alertdialog" aria-modal="true" aria-labelledby="company-delete-title" onMouseDown={event => event.stopPropagation()}><span className="company-delete-icon"><Trash2 size={22} /></span><h2 id="company-delete-title">Delete {label}?</h2><p>This permanently removes the record from this company workspace. This action cannot be undone.</p><div className="company-modal-actions"><button className="secondary-action" onClick={onCancel} disabled={saving}>Keep item</button><button className="feature-primary company-danger-button" onClick={() => void onConfirm()} disabled={saving}>{saving ? 'Deleting…' : 'Delete permanently'}</button></div></section></div>;
}

function Metric({ icon: Icon, label, value, tone = 'green' }: { icon: typeof Building2; label: string; value: string | number; tone?: 'green' | 'blue' | 'purple' }) { return <article className={`company-metric ${tone}`}><span><Icon size={20} /></span><strong>{value}</strong><p>{label}</p></article> }
function SmallMetric({ label, value }: { label: string; value: string | number }) { return <div className="small-metric"><span>{label}</span><strong>{value}</strong></div> }
function HealthBar({ label, value, total, color }: { label: string; value: number; total: number; color: string }) { const width = total ? Math.round(value * 100 / total) : 0; return <div className="health-bar"><div><span>{label}</span><b>{value}</b></div><i><em style={{ width: `${width}%`, background: color }} /></i></div> }
function AlertRows({ alerts }: { alerts: CompanyDashboard['alerts'] }) { return <div className="alert-rows">{alerts.map(alert => <div key={alert.chargerId}><span><AlertTriangle size={17} /></span><div><strong>{alert.chargerCode}</strong><small>{alert.station} · {alert.message}</small></div><b>{alert.healthScore}%</b></div>)}{!alerts.length && <div className="company-empty compact"><CheckCircle2 size={24} /><p>No critical charger alerts.</p></div>}</div> }
function ReportCard({ icon: Icon, title, text, onPdf, onExcel }: { icon: typeof Gauge; title: string; text: string; onPdf: () => void; onExcel: () => void }) { return <article><span><Icon size={22} /></span><h3>{title}</h3><p>{text}</p><div><button onClick={onPdf}><Download size={14} />PDF</button><button onClick={onExcel}><FileSpreadsheet size={14} />Excel</button></div></article> }
function Sparkline() { return <svg viewBox="0 0 200 44" aria-hidden="true"><path d="M2 35 Q22 6 42 27 T82 17 T122 29 T162 8 T198 18" fill="none" stroke="currentColor" strokeWidth="3" /></svg> }

// Keep the original compact panels available while persisted replacements above own the live routes.
void RevenuePanel;
void MaintenancePanel;
void NotificationsPanel;

function OperatorApprovalDialog({ title, description, detail, saving, error, onCancel, onConfirm }: {
  title: string; description: string; detail: string; saving: boolean; error: string; onCancel: () => void; onConfirm: () => void;
}) {
  return <div className="vehicle-form-backdrop" style={{ zIndex: 3000 }}><section className="company-modal" role="dialog" aria-modal="true" aria-labelledby="operator-approval-title">
    <div className="company-card-head"><div><h2 id="operator-approval-title">{title}</h2><p>Review before the backend changes network state.</p></div></div>
    <div style={{ padding: 24 }}><h3>{description}</h3><p>{detail}</p>{error && <p role="alert">{error}</p>}</div>
    <div className="company-modal-actions"><button autoFocus className="secondary-action" disabled={saving} onClick={onCancel}>Cancel</button><button className="feature-primary" disabled={saving} onClick={onConfirm}>{saving ? 'Applying…' : 'Approve'}</button></div>
  </section></div>;
}
