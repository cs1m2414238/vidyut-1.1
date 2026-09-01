import { Children, useCallback, useEffect, useMemo, useState } from "react";
import {
  Activity,
  AlertTriangle,
  BadgeIndianRupee,
  Banknote,
  BatteryCharging,
  Bell,
  Bot,
  CalendarDays,
  CarFront,
  CheckCircle2,
  Clock3,
  Download,
  FileSpreadsheet,
  Gauge,
  HeartPulse,
  HousePlug,
  MapPin,
  MessageSquare,
  Plus,
  RefreshCw,
  Send,
  ShieldCheck,
  Star,
  Trash2,
  UserRound,
  WalletCards,
  X,
  Sun,
  Building2,
  TrendingUp,
  Wrench,
  ExternalLink,
} from "lucide-react";
import { apiDownload, apiRequest } from "../services/api";
import { HostMarketplaceView } from "./HostMarketplaceView";

type ModalKind = "charger" | "availability" | "profile" | "kyc" | "bank" | null;
type ReviewAction = {
  id: number;
  kind: "reply" | "report";
  title: string;
} | null;

interface HostProfile {
  accountId: number;
  email: string;
  emailVerified: boolean;
  displayName: string;
  phone?: string;
  address?: string;
  bio?: string;
  verificationStatus: "PENDING" | "VERIFIED" | "REJECTED";
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
interface HostDashboard {
  totalLocations: number;
  totalChargers: number;
  onlineChargers: number;
  occupiedChargers: number;
  activeSessions: number;
  upcomingBookings: number;
  monthlySessions: number;
  successfulSessionsPercent: number;
  energyDeliveredKwh: number;
  uptimePercent: number;
  todayEarnings: number;
  monthlyEarnings: number;
  pendingPayout: number;
  reputationScore: number;
  alerts: Monitor[];
}
interface Station {
  id: number;
  name: string;
  address: string;
  city: string;
  latitude: number;
  longitude: number;
  pricePerKwh: number;
  rating: number;
  reviewCount: number;
  imageUrl?: string;
  photoUrls?: string;
  amenities?: string;
  workingHours?: string;
  weeklySchedule?: string;
  holidaySchedule?: string;
  chargingInstructions?: string;
  autoAvailability: boolean;
  emergencyDisabled: boolean;
  demoData: boolean;
  propertyOwnerName?: string;
  operatorCompanyName?: string;
  equipmentOwnerName?: string;
  operatingModel?: string;
  solarProviderName?: string;
  bookingSlotMinutes: number;
  status: string;
  availability: string;
  connectors: Array<{
    id: number;
    chargerCode: string;
    type: string;
    powerKw: number;
    status?: string;
    available?: boolean;
    healthScore?: number;
  }>;
}
interface Booking {
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
  status: "PENDING" | "CONFIRMED" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
}
interface Earnings {
  daily: number;
  weekly: number;
  monthly: number;
  lifetime: number;
  availableBalance: number;
  pendingPayout: number;
  taxWithheld: number;
  financialYear: string;
  todayGrossRevenue?: number;
  todayElectricityCost?: number;
  todayPlatformShare?: number;
  todayHostNet?: number;
  todaySessions?: number;
  averageSessionValue?: number;
  utilizationPercent?: number;
  peakWindow?: string;
  payouts: Array<{
    id: number;
    amount: number;
    status: string;
    timestamp: string;
  }>;
  transactions: Array<{
    bookingId: number;
    station: string;
    amount: number;
    timestamp: string;
    status: string;
  }>;
}
interface Monitor {
  id: number;
  stationId: number;
  stationName: string;
  operatorCompanyName?: string;
  chargerCode: string;
  connectorType: string;
  powerKw: number;
  status: "ONLINE" | "OFFLINE" | "CHARGING" | "MAINTENANCE" | "FAULT";
  canControlOperationalStatus?: boolean;
  availabilityLabel?: "AVAILABLE" | "OCCUPIED" | "ONLINE" | "OFFLINE" | "MAINTENANCE" | "FAULT";
  available: boolean;
  currentPowerKw: number;
  sessionEnergyKwh: number;
  sessionDurationMinutes: number;
  healthScore: number;
  faultCode?: string;
  lastHeartbeat: string;
  sessionId?: number;
  bookingId?: number;
  vehicleId?: number;
  vehicleName?: string;
  vehicleRegistration?: string;
  startBatteryPercent?: number;
  currentBatteryPercent?: number;
  targetBatteryPercent?: number;
  estimatedCompletionAt?: string;
  remainingMinutes?: number;
  sessionCost?: number;
}
interface Review {
  id: number;
  customerName: string;
  rating: number;
  comment: string;
  hostReply?: string;
  reported: boolean;
  createdAt: string;
}
interface NotificationItem {
  id: number;
  title: string;
  message: string;
  type: string;
  timestamp: string;
  read: boolean;
}
interface MaintenanceAlternative {
  stationId: number;
  stationName: string;
  operatorCompanyName: string;
  connectorType: string;
  powerKw: number;
  extraDistanceKm: number;
  waitMinutes: number;
  chargingCost: number;
  chargingCostDifference: number;
  delayMinutes: number;
  extraBatteryPercent: number;
  score: number;
  demoScenario: boolean;
}
interface MaintenanceImpact {
  connectorId: number;
  chargerCode: string;
  stationId: number;
  stationName: string;
  operatorCompanyName: string;
  faultCode: string;
  estimatedRepairHours: number;
  repairEstimate: number;
  estimatedLostRevenueNext3Hours: number;
  estimatedRevenueLoss24Hours: number;
  repairRecommendation: string;
  activeJourneys: number;
  automaticReroutes: number;
  driverApprovals: number;
  upcomingReservations: number;
  affectedUsers: number;
  backupConnectorAvailable: boolean;
  recommendedAlternatives: MaintenanceAlternative[];
  modeledUserImpact: {
    extraDistanceKm: number;
    delayMinutes: number;
    chargingCostDifference: number;
    extraBatteryPercent: number;
    dataBasis: string;
  };
  message: string;
}
interface AgentMaintenanceRisk {
  connectorId: number;
  stationId: number;
  stationName: string;
  operatorCompanyName: string;
  chargerCode: string;
  connectorType: string;
  riskScore: number;
  maintenanceHealth: number;
  recentSessions: number;
  customerComplaints: number;
  signals: string[];
  repairEstimate: number;
  estimatedRevenueLoss24Hours: number;
  monthlyContribution: number;
  estimatedRepairHours: number;
  repeatedFailures90Days: number;
  assetAgeYears: number;
  financialRecommendation: string;
  operatorAction: string;
  recommendedWindow: string;
}
interface AgentAction {
  action:
    | "PUT_CONNECTOR_IN_MAINTENANCE"
    | "REQUEST_TATA_SERVICE"
    | "REQUEST_SERVICE"
    | "EXTEND_HOURS"
    | "OPEN_MARKETPLACE"
    | "CREATE_PROPERTY_DRAFT"
    | "PREPARE_GREEN_FINANCE"
    | (string & {});
  label: string;
  requiresConfirmation: boolean;
  stationId?: number;
  connectorId?: number;
  propertyId?: number;
  payload?: Record<string, unknown>;
  detail: string;
}
interface HostAgentInsight {
  answer: string;
  assistantModel?: string;
  assistantProvider?: string;
  assistantFallback?: boolean;
  revenue: Earnings;
  maintenanceRisks: AgentMaintenanceRisk[];
  operatingHours: {
    stationId?: number;
    peakWindow: string;
    recommendedHours: string;
    additionalSessionsLow: number;
    additionalSessionsHigh: number;
    estimatedAdditionalRevenue: number;
    estimatedAdditionalOperatingCost: number;
    recommendation: string;
    dataBasis: string;
  };
  companyDeals: Array<{
    company: string;
    chargerPowerKw: number;
    revenueModel: string;
    hostShareLabel: string;
    hostRevenueSharePercent: number;
    installationFunding: string;
    maintenanceResponsibility: string;
    expectedSessionsPerMonth: number;
    projectedMonthlyHostIncome: number;
    projectedAnnualHostRevenue: number;
    projectedThreeYearValue: number;
    riskLevel: string;
    recommendationTag: string;
    tradeoff: string;
    demoScenario: boolean;
  }>;
  solarOpportunity: {
    stationId?: number;
    stationName: string;
    propertyOwnerName: string;
    operatorCompanyName: string;
    solarProviderName: string;
    modeledMonthlyConsumptionKwh: number;
    solarCapacityKw: number;
    modeledMonthlyGenerationKwh: number;
    solarContributionPercent: number;
    monthlySavings: number;
    simplePaybackYears: number;
    dataBasis: string;
    eligibilityLeads: Array<{
      name: string;
      status: string;
      potentialAmount?: number;
      note?: string;
    }>;
    solarOptions: Array<{
      option: string;
      label: string;
      upfrontRequirement: string;
      modeledInvestment: number;
      monthlyPayment: number;
      ownership: string;
      tradeoff: string;
    }>;
    fundingPlan: {
      projectCost: number;
      princeBudget: number;
      princeContribution: number;
      operatorContribution: number;
      potentialGovernmentAssistance: number;
      solarProviderContribution: number;
      solarStructure: string;
      additionalUpfrontRequired: number;
      withinPrinceBudget: boolean;
      status: string;
    };
    legalNotice: string;
  };
  networkPortfolio: Array<{
    stationId: number;
    stationName: string;
    propertyOwnerName: string;
    operatorCompanyName: string;
    operatingModel: string;
    solarProviderName?: string;
    connectorCount: number;
    onlineConnectors: number;
    networkHealth: string;
    demoData: boolean;
  }>;
  outagePlaybook: { steps: string[]; approvalPolicy: string };
  liveSessions: Monitor[];
  proposedActions: AgentAction[];
  dataPolicy: string;
}
export interface HostCounts {
  bookings: number;
  notifications: number;
}

const emptyDashboard: HostDashboard = {
  totalLocations: 0,
  totalChargers: 0,
  onlineChargers: 0,
  occupiedChargers: 0,
  activeSessions: 0,
  upcomingBookings: 0,
  monthlySessions: 0,
  successfulSessionsPercent: 0,
  energyDeliveredKwh: 0,
  uptimePercent: 0,
  todayEarnings: 0,
  monthlyEarnings: 0,
  pendingPayout: 0,
  reputationScore: 5,
  alerts: [],
};
const emptyEarnings: Earnings = {
  daily: 0,
  weekly: 0,
  monthly: 0,
  lifetime: 0,
  availableBalance: 0,
  pendingPayout: 0,
  taxWithheld: 0,
  financialYear: "",
  todayGrossRevenue: 0,
  todayElectricityCost: 0,
  todayPlatformShare: 0,
  todayHostNet: 0,
  todaySessions: 0,
  averageSessionValue: 0,
  utilizationPercent: 0,
  peakWindow: "",
  payouts: [],
  transactions: [],
};
const money = (value: number) =>
  new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(value || 0);
const dateTime = (value?: string) =>
  value
    ? new Date(value).toLocaleString("en-IN", {
        day: "numeric",
        month: "short",
        hour: "numeric",
        minute: "2-digit",
      })
    : "—";

function validateHostForm(
  kind: Exclude<ModalKind, null>,
  form: Record<string, string | number | boolean>,
  editing: boolean,
): string {
  const text = (key: string) => String(form[key] ?? "").trim();
  const number = (key: string) => Number(form[key]);
  if (kind === "charger") {
    if (!text("name") || !text("address") || !text("city"))
      return "Charger name, address and city are required.";
    if (
      !Number.isFinite(number("latitude")) ||
      number("latitude") < -90 ||
      number("latitude") > 90
    )
      return "Latitude must be between -90 and 90.";
    if (
      !Number.isFinite(number("longitude")) ||
      number("longitude") < -180 ||
      number("longitude") > 180
    )
      return "Longitude must be between -180 and 180.";
    if (number("pricePerKwh") <= 0)
      return "Price per kWh must be greater than zero.";
    if (!editing && (!text("connectorType") || number("powerKw") <= 0))
      return "Connector type and charging speed are required.";
  }
  if (
    kind === "availability" &&
    (number("bookingSlotMinutes") < 15 || number("bookingSlotMinutes") > 480)
  )
    return "Booking slots must be between 15 and 480 minutes.";
  if (kind === "profile") {
    if (!text("displayName")) return "Display name is required.";
    if (
      text("phone") &&
      !/^(?:\+?91)?\d{10}$/.test(text("phone").replace(/[\s()-]/g, ""))
    )
      return "Enter a valid 10-digit Indian mobile number.";
  }
  if (
    kind === "kyc" &&
    (!/^[0-9a-z]{4}$/i.test(text("identityLast4")) || !text("kycDocumentUrl"))
  )
    return "Provide the last four identity characters and a KYC document URL.";
  if (kind === "bank") {
    if (!text("accountHolder") || !text("bankName"))
      return "Account holder and bank name are required.";
    if (!/^\d{4,18}$/.test(text("accountNumber")))
      return "Bank account number must contain 4 to 18 digits.";
    if (!/^[a-z]{4}0[a-z0-9]{6}$/i.test(text("ifscCode")))
      return "Enter a valid IFSC code.";
  }
  return "";
}

export function HostWorkspace({
  tab,
  token,
  hostName,
  onNavigate,
  onCountsChange,
}: {
  tab: string;
  token: string;
  hostName: string;
  onNavigate: (tab: string) => void;
  onCountsChange: (counts: HostCounts) => void;
}) {
  const [profile, setProfile] = useState<HostProfile | null>(null);
  const [dashboard, setDashboard] = useState<HostDashboard>(emptyDashboard);
  const [stations, setStations] = useState<Station[]>([]);
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [earnings, setEarnings] = useState<Earnings>(emptyEarnings);
  const [monitoring, setMonitoring] = useState<Monitor[]>([]);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [modal, setModal] = useState<ModalKind>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<Record<string, string | number | boolean>>(
    {},
  );
  const [emailOpen, setEmailOpen] = useState(false);
  const [emailSent, setEmailSent] = useState(false);
  const [emailCode, setEmailCode] = useState("");
  const [pendingDelete, setPendingDelete] = useState<Station | null>(null);
  const [reviewAction, setReviewAction] = useState<ReviewAction>(null);
  const [reviewMessage, setReviewMessage] = useState("");
  const [withdrawAmount, setWithdrawAmount] = useState("");
  const [question, setQuestion] = useState("Which property is best for expansion?");
  const [answer, setAnswer] = useState(
    "Ask which cars are charging, which connector needs service, or when a station should stay open.",
  );
  const [agentInsight, setAgentInsight] = useState<HostAgentInsight | null>(
    null,
  );
  const [pendingMaintenance, setPendingMaintenance] = useState<{
    item: Monitor;
    status: Monitor["status"];
    impact: MaintenanceImpact;
  } | null>(null);
  const [pendingAgentAction, setPendingAgentAction] =
    useState<AgentAction | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const auth = useMemo(
    () => ({ headers: { Authorization: `Bearer ${token}` } }),
    [token],
  );

  const loadAll = useCallback(async () => {
    setLoading(true);
    setError("");
    const requests = await Promise.allSettled([
      apiRequest<HostProfile>("/host/profile", { method: "GET", ...auth }),
      apiRequest<HostDashboard>("/host/dashboard", { method: "GET", ...auth }),
      apiRequest<Station[]>("/host/stations", { method: "GET", ...auth }),
      apiRequest<Booking[]>("/host/bookings", { method: "GET", ...auth }),
      apiRequest<Earnings>("/host/earnings", { method: "GET", ...auth }),
      apiRequest<Monitor[]>("/host/monitoring", { method: "GET", ...auth }),
      apiRequest<Review[]>("/host/reviews", { method: "GET", ...auth }),
      apiRequest<NotificationItem[]>("/host/notifications", {
        method: "GET",
        ...auth,
      }),
    ]);
    if (requests[0].status === "fulfilled") setProfile(requests[0].value);
    else setProfile(null);
    if (requests[1].status === "fulfilled") setDashboard(requests[1].value);
    if (requests[2].status === "fulfilled") setStations(requests[2].value);
    if (requests[3].status === "fulfilled") setBookings(requests[3].value);
    if (requests[4].status === "fulfilled") setEarnings(requests[4].value);
    if (requests[5].status === "fulfilled") setMonitoring(requests[5].value);
    if (requests[6].status === "fulfilled") setReviews(requests[6].value);
    if (requests[7].status === "fulfilled") setNotifications(requests[7].value);
    const resourceNames = [
      "profile",
      "dashboard",
      "chargers",
      "bookings",
      "earnings",
      "monitoring",
      "reviews",
      "notifications",
    ];
    const failedResources = requests.flatMap((request, index) =>
      request.status === "rejected" ? [resourceNames[index]] : [],
    );
    if (failedResources.length)
      setError(
        `Some Host data could not be loaded: ${failedResources.join(", ")}. Refresh to try again.`,
      );
    onCountsChange({
      bookings:
        requests[3].status === "fulfilled"
          ? requests[3].value.filter(
              (item) => !["COMPLETED", "CANCELLED"].includes(item.status),
            ).length
          : 0,
      notifications:
        requests[7].status === "fulfilled"
          ? requests[7].value.filter((item) => !item.read).length
          : 0,
    });
    setLoading(false);
  }, [auth, onCountsChange]);
  useEffect(() => {
    void loadAll();
  }, [loadAll]);

  const setupComplete = Boolean(
    profile?.emailVerified &&
      profile.verificationStatus === "VERIFIED" &&
      profile.bankVerified,
  );
  const openModal = (kind: Exclude<ModalKind, null>, station?: Station) => {
    setModal(kind);
    setEditingId(station?.id ?? null);
    setError("");
    if (kind === "charger")
      setForm(
        station
          ? {
              name: station.name,
              address: station.address,
              city: station.city,
              latitude: station.latitude,
              longitude: station.longitude,
              pricePerKwh: station.pricePerKwh,
              propertyOwnerName:
                station.propertyOwnerName ?? profile?.displayName ?? hostName,
              operatorCompanyName: station.operatorCompanyName ?? "",
              equipmentOwnerName: station.equipmentOwnerName ?? "",
              operatingModel:
                station.operatingModel ?? "HOST_PROPERTY_CPO_EQUIPMENT",
              solarProviderName: station.solarProviderName ?? "",
              imageUrl: station.imageUrl ?? "",
              photoUrls: station.photoUrls ?? "",
              amenities: station.amenities ?? "",
              workingHours: station.workingHours ?? "",
              chargingInstructions: station.chargingInstructions ?? "",
              status: station.status,
              availability: station.availability,
            }
          : {
              name: "",
              address: "",
              city: "Lucknow",
              latitude: 26.8467,
              longitude: 80.9462,
              pricePerKwh: 16,
              connectorType: "TYPE2",
              powerKw: 7.4,
              propertyOwnerName: profile?.displayName ?? hostName,
              operatorCompanyName: "",
              equipmentOwnerName: "",
              operatingModel: "HOST_PROPERTY_CPO_EQUIPMENT",
              solarProviderName: "",
              imageUrl: "",
              photoUrls: "",
              amenities: "Parking",
              workingHours: "06:00-23:00",
              weeklySchedule: "MON-SUN 06:00-23:00",
              bookingSlotMinutes: 60,
              chargingInstructions: "",
            },
      );
    if (kind === "availability" && station)
      setForm({
        availability: station.availability,
        weeklySchedule: station.weeklySchedule ?? "MON-SUN 06:00-23:00",
        holidaySchedule: station.holidaySchedule ?? "",
        chargingInstructions: station.chargingInstructions ?? "",
        bookingSlotMinutes: station.bookingSlotMinutes || 60,
        autoAvailability: station.autoAvailability,
        emergencyDisabled: station.emergencyDisabled,
      });
    if (kind === "profile")
      setForm({
        displayName: profile?.displayName ?? hostName,
        phone: profile?.phone ?? "",
        address: profile?.address ?? "",
        bio: profile?.bio ?? "",
      });
    if (kind === "kyc")
      setForm({
        identityType: profile?.identityType ?? "AADHAAR",
        identityLast4: profile?.identityLast4 ?? "",
        kycDocumentUrl: profile?.kycDocumentUrl ?? "",
      });
    if (kind === "bank")
      setForm({
        accountHolder: profile?.bankAccountHolder ?? profile?.displayName ?? "",
        bankName: profile?.bankName ?? "",
        accountNumber: "",
        ifscCode: profile?.ifscCode ?? "",
        payoutUpi: profile?.payoutUpi ?? "",
      });
  };

  const submit = async () => {
    if (!modal) return;
    const validationError = validateHostForm(modal, form, Boolean(editingId));
    if (validationError) {
      setError(validationError);
      return;
    }
    setSaving(true);
    setError("");
    setNotice("");
    try {
      let path = "";
      let method = "POST";
      if (modal === "charger") {
        path = editingId ? `/host/stations/${editingId}` : "/host/stations";
        method = editingId ? "PUT" : "POST";
      }
      if (modal === "availability") {
        path = `/host/stations/${editingId}/availability`;
        method = "PUT";
      }
      if (modal === "profile") {
        path = "/host/profile";
        method = "PUT";
      }
      if (modal === "kyc") path = "/host/verification";
      if (modal === "bank") {
        path = "/host/bank";
        method = "PUT";
      }
      const payload =
        modal === "bank"
          ? { ...form, ifscCode: String(form.ifscCode ?? "").toUpperCase() }
          : form;
      await apiRequest(path, {
        method,
        ...auth,
        body: JSON.stringify(payload),
      });
      setModal(null);
      setNotice("Host changes saved.");
      await loadAll();
    } catch (submitError) {
      setError(
        submitError instanceof Error
          ? submitError.message
          : "Unable to save changes.",
      );
    } finally {
      setSaving(false);
    }
  };

  const removeStation = async () => {
    if (!pendingDelete) return;
    try {
      setSaving(true);
      setError("");
      setNotice("");
      await apiRequest(`/host/stations/${pendingDelete.id}`, {
        method: "DELETE",
        ...auth,
      });
      setPendingDelete(null);
      setNotice("Charger listing deleted.");
      await loadAll();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to delete charger.");
    } finally {
      setSaving(false);
    }
  };
  const bookingStatus = async (booking: Booking, status: Booking["status"]) => {
    try {
      setError("");
      setNotice("");
      await apiRequest(`/host/bookings/${booking.id}/status`, {
        method: "PATCH",
        ...auth,
        body: JSON.stringify({ status }),
      });
      setNotice(`Booking marked ${status.toLowerCase()}.`);
      await loadAll();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to update booking.");
    }
  };
  const applyMonitorChange = async (
    item: Monitor,
    status: Monitor["status"],
    impactApproved: boolean,
  ) => {
    try {
      setSaving(true);
      setError("");
      setNotice("");
      const result = await apiRequest<{
        networkResult?: {
          affectedJourneys: number;
          automaticReroutes: number;
          driverApprovals: number;
          replanRequired: number;
          backupConnectorAvailable: boolean;
        };
      }>(`/host/connectors/${item.id}/status`, {
        method: "PUT",
        ...auth,
        body: JSON.stringify({
          status,
          currentPowerKw: status === "CHARGING" ? item.powerKw : 0,
          sessionEnergyKwh: item.sessionEnergyKwh,
          healthScore: item.healthScore,
          faultCode:
            status === "FAULT"
              ? item.faultCode || "HOST_REPORTED"
              : status === "MAINTENANCE"
                ? "HOST_APPROVED_SERVICE"
                : null,
          impactApproved,
        }),
      });
      const network = result.networkResult;
      setPendingMaintenance(null);
      setNotice(
        network && network.affectedJourneys > 0
          ? `${item.chargerCode} is ${status.toLowerCase()}. ${network.automaticReroutes} journey rerouted automatically; ${network.driverApprovals} driver approval requested.`
          : `${item.chargerCode} is ${status.toLowerCase()}.`,
      );
      await loadAll();
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Unable to update charger status.",
      );
    } finally {
      setSaving(false);
    }
  };
  const updateMonitor = async (item: Monitor, status: Monitor["status"]) => {
    const disruptive = ["OFFLINE", "MAINTENANCE", "FAULT"].includes(status);
    if (!disruptive) {
      await applyMonitorChange(item, status, true);
      return;
    }
    try {
      setError("");
      const impact = await apiRequest<MaintenanceImpact>(
        `/host/connectors/${item.id}/maintenance-impact`,
        { method: "GET", ...auth },
      );
      setPendingMaintenance({ item, status, impact });
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "Unable to calculate maintenance impact.",
      );
    }
  };
  const withdraw = async () => {
    try {
      setSaving(true);
      setError("");
      setNotice("");
      await apiRequest("/host/payouts/withdraw", {
        method: "POST",
        ...auth,
        body: JSON.stringify({ amount: Number(withdrawAmount) }),
      });
      setWithdrawAmount("");
      setNotice("Withdrawal request submitted.");
      await loadAll();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to withdraw.");
    } finally {
      setSaving(false);
    }
  };
  const saveReviewAction = async () => {
    if (!reviewAction) return;
    try {
      setSaving(true);
      await apiRequest(
        `/host/reviews/${reviewAction.id}/${reviewAction.kind}`,
        {
          method: "PATCH",
          ...auth,
          body: JSON.stringify({ message: reviewMessage }),
        },
      );
      setReviewAction(null);
      setReviewMessage("");
      setNotice(
        reviewAction.kind === "reply" ? "Reply published." : "Review reported.",
      );
      await loadAll();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to update review.");
    } finally {
      setSaving(false);
    }
  };
  const ask = useCallback(async () => {
    try {
      setSaving(true);
      const result = await apiRequest<HostAgentInsight>("/host/ai/ask", {
        method: "POST",
        ...auth,
        body: JSON.stringify({ question }),
      });
      setAnswer(result.answer);
      setAgentInsight(result);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Assistant unavailable.");
    } finally {
      setSaving(false);
    }
  }, [auth, question]);
  const chooseAgentAction = async (action: AgentAction) => {
    if (action.action === "OPEN_MARKETPLACE") {
      onNavigate("marketplace");
      return;
    }
    if (
      action.action === "PUT_CONNECTOR_IN_MAINTENANCE" &&
      action.connectorId
    ) {
      const item = monitoring.find(
        (connector) => connector.id === action.connectorId,
      );
      if (item) await updateMonitor(item, "MAINTENANCE");
      return;
    }
    setPendingAgentAction(action);
  };
  const executeAgentAction = async () => {
    if (!pendingAgentAction) return;
    const approvedAction = pendingAgentAction;
    try {
      setSaving(true);
      setError("");
      const result = await apiRequest<{ status: string; message?: string }>(
        "/host/ai/actions",
        {
          method: "POST",
          ...auth,
          body: JSON.stringify({
            action: pendingAgentAction.action,
            stationId: pendingAgentAction.stationId,
            connectorId: pendingAgentAction.connectorId,
            propertyId: pendingAgentAction.propertyId,
            payload: pendingAgentAction.payload,
            approved: true,
          }),
        },
      );
      setNotice(result.message || `${pendingAgentAction.label} completed.`);
      setPendingAgentAction(null);
      await loadAll();
      if (approvedAction.action === "CREATE_PROPERTY_DRAFT") {
        onNavigate("properties");
      } else {
        await ask();
      }
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "Unable to execute the approved Host action.",
      );
    } finally {
      setSaving(false);
    }
  };
  const sendCode = async () => {
    try {
      setSaving(true);
      await apiRequest("/host/email-verification/request", {
        method: "POST",
        ...auth,
      });
      setEmailSent(true);
      setEmailCode("");
      setNotice("Verification code sent.");
      await loadAll();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to send code.");
    } finally {
      setSaving(false);
    }
  };
  const confirmCode = async () => {
    try {
      setSaving(true);
      await apiRequest("/host/email-verification/confirm", {
        method: "POST",
        ...auth,
        body: JSON.stringify({ code: emailCode }),
      });
      setEmailOpen(false);
      setEmailSent(false);
      setNotice("Host email verified.");
      await loadAll();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to verify email.");
    } finally {
      setSaving(false);
    }
  };
  const updateSettings = async (
    changes: Partial<
      Pick<
        HostProfile,
        "emailNotifications" | "pushNotifications" | "autoAvailability"
      >
    >,
  ) => {
    if (!profile) return;
    try {
      await apiRequest("/host/settings", {
        method: "PUT",
        ...auth,
        body: JSON.stringify({
          emailNotifications:
            changes.emailNotifications ?? profile.emailNotifications,
          pushNotifications:
            changes.pushNotifications ?? profile.pushNotifications,
          autoAvailability:
            changes.autoAvailability ?? profile.autoAvailability,
        }),
      });
      await loadAll();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to update settings.");
    }
  };
  const markNotificationRead = async (id: number) => {
    try {
      setError("");
      await apiRequest(`/host/notifications/${id}/read`, {
        method: "PATCH",
        ...auth,
      });
      await loadAll();
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Unable to update notification.",
      );
    }
  };
  const markAllNotificationsRead = async () => {
    try {
      setError("");
      await apiRequest("/host/notifications/read-all", {
        method: "PATCH",
        ...auth,
      });
      setNotice("All Host notifications marked as read.");
      await loadAll();
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Unable to update notifications.",
      );
    }
  };
  const download = async (
    type: "EARNINGS" | "USAGE",
    format: "PDF" | "XLSX",
  ) => {
    try {
      const blob = await apiDownload(
        `/host/reports/export?type=${type}&format=${format}`,
        token,
      );
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `vidyut-host-${type.toLowerCase()}.${format.toLowerCase()}`;
      anchor.click();
      URL.revokeObjectURL(url);
      setNotice(`${format} report downloaded.`);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to download report.");
    }
  };

  useEffect(() => {
    if ((tab === "ai" || tab === "finance") && !agentInsight && !loading) void ask();
  }, [tab, agentInsight, loading, ask]);

  if (
    tab === "properties" ||
    tab === "marketplace" ||
    tab === "installations"
  ) {
    return (
      <HostMarketplaceView tab={tab} token={token} onNavigate={onNavigate} />
    );
  }

  const titles: Record<string, string> = {
    dashboard: "Prince’s charging network",
    chargers: "Properties & operator chargers",
    availability: "Availability schedule",
    bookings: "Booking management",
    earnings: "Earnings & payouts",
    monitoring: "Live charger monitoring",
    reviews: "Reviews & reputation",
    ai: "AI Host assistant",
    finance: "Offers & Green Finance",
    reports: "Host reports",
    notifications: "Host notifications",
    profile: "Host profile & verification",
  };
  return (
    <section className="host-workspace">
      <header className="host-page-head">
        <div>
          <div className="feature-eyebrow">
            HOST MODE · MULTI-OPERATOR PORTFOLIO
          </div>
          <h1>{titles[tab] ?? "Host workspace"}</h1>
          <p>
            {profile?.displayName ?? hostName} owns the properties; equipment
            operators run the chargers; Vidyut coordinates discovery, bookings
            and intelligence.
          </p>
        </div>
        <button
          className="wallet-refresh"
          onClick={() => void loadAll()}
          disabled={loading}
        >
          <RefreshCw size={15} className={loading ? "spinning" : ""} />
          Refresh
        </button>
      </header>
      {!loading && profile && !setupComplete && (
        <div className="host-verification-banner">
          <ShieldCheck size={20} />
          <div>
            <strong>Complete Host setup</strong>
            <span>
              {!profile.emailVerified
                ? "Verify your email to secure this workspace."
                : profile.verificationStatus !== "VERIFIED"
                  ? `Identity verification: ${profile.verificationStatus}`
                  : "Add a verified bank account to receive payouts."}
            </span>
          </div>
          <button
            onClick={() =>
              !profile.emailVerified
                ? setEmailOpen(true)
                : openModal(
                    profile.verificationStatus !== "VERIFIED" ? "kyc" : "bank",
                  )
            }
          >
            {!profile.emailVerified
              ? "Verify email"
              : profile.verificationStatus !== "VERIFIED"
                ? "Submit KYC"
                : "Add bank"}
          </button>
        </div>
      )}
      {error && (
        <div className="wallet-message error" role="alert">
          {error}
        </div>
      )}
      {notice && (
        <div className="wallet-message success" role="status">
          <CheckCircle2 size={15} />
          {notice}
        </div>
      )}

      {/* Real-time Incident Alert for Host Prince */}
      {monitoring.some((m) => m.status === 'FAULT' || m.status === 'MAINTENANCE' || (m as any).status === 'SUSPECTED_FAULT' || (m.faultCode && m.faultCode.includes('SUSPECTED_FAULT'))) && (
        <section className="host-urgent-incident-banner" role="alert" style={{
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
                }}>POTENTIAL CHARGER FAULT</span>
                <span style={{ fontSize: 11, color: '#fca5a5' }}>1 active trip rerouted</span>
              </div>
              <strong style={{ color: '#f8fafc', fontSize: 14 }}>Prince Highway Charging Hub (Dausa) — Tata CCS2 #1</strong>
              <p style={{ margin: '3px 0 0 0', fontSize: 12, color: '#cbd5e1' }}>
                Driver reported session could not start. Equipment operator <em>Tata Power — Demo Operator Data</em> has been notified.
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => {
              onNavigate("ai");
              setQuestion("Which charger needs servicing?");
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
            Ask Host Agent to Service
          </button>
        </section>
      )}

      {tab === "dashboard" && (
        <HostDashboardPanel
          dashboard={dashboard}
          stations={stations}
          bookings={bookings}
          onNavigate={onNavigate}
        />
      )}
      {tab === "chargers" && (
        <HostResource
          title="Your charger listings"
          action="Add charger"
          onAdd={() => openModal("charger")}
          empty="Register your first private charger after verification."
        >
          {stations.map((station) => (
            <HostStationRow
              key={station.id}
              station={station}
              onEdit={() => openModal("charger", station)}
              onSchedule={() => openModal("availability", station)}
              onDelete={() => setPendingDelete(station)}
            />
          ))}
        </HostResource>
      )}
      {tab === "availability" && (
        <AvailabilityPanel
          stations={stations}
          onEdit={(station) => openModal("availability", station)}
        />
      )}
      {tab === "bookings" && (
        <HostBookings bookings={bookings} onStatus={bookingStatus} />
      )}
      {tab === "earnings" && (
        <EarningsPanel
          earnings={earnings}
          profile={profile}
          amount={withdrawAmount}
          setAmount={setWithdrawAmount}
          saving={saving}
          onWithdraw={withdraw}
          onBank={() => openModal("bank")}
        />
      )}
      {tab === "monitoring" && (
        <MonitoringPanel items={monitoring} onStatus={updateMonitor} />
      )}
      {tab === "reviews" && (
        <ReviewsPanel
          items={reviews}
          score={profile?.reputationScore ?? dashboard.reputationScore}
          onAction={(item, kind) => {
            setReviewAction({ id: item.id, kind, title: item.customerName });
            setReviewMessage(
              kind === "reply"
                ? (item.hostReply ?? "")
                : "Abusive or irrelevant content",
            );
          }}
        />
      )}
      {tab === "ai" && (
        <HostAiPanel
          question={question}
          setQuestion={setQuestion}
          answer={answer}
          dashboard={dashboard}
          monitoring={monitoring}
          insight={agentInsight}
          busy={saving}
          onAsk={ask}
          onAction={chooseAgentAction}
        />
      )}
      {tab === "finance" && (
        <GreenFinancePanel
          insight={agentInsight}
          busy={saving}
          onAction={chooseAgentAction}
        />
      )}
      {tab === "reports" && <ReportsPanel onDownload={download} />}
      {tab === "notifications" && (
        <NotificationsPanel
          items={notifications}
          onRead={markNotificationRead}
          onReadAll={markAllNotificationsRead}
        />
      )}
      {tab === "profile" && (
        <HostProfilePanel
          profile={profile}
          onProfile={() => openModal("profile")}
          onEmail={() => setEmailOpen(true)}
          onKyc={() => openModal("kyc")}
          onBank={() => openModal("bank")}
          onSettings={updateSettings}
        />
      )}
      {modal && (
        <HostModal
          kind={modal}
          editing={Boolean(editingId)}
          form={form}
          setForm={setForm}
          saving={saving}
          onClose={() => setModal(null)}
          onSubmit={submit}
        />
      )}
      {emailOpen && (
        <EmailDialog
          sent={emailSent}
          code={emailCode}
          setCode={setEmailCode}
          saving={saving}
          onSend={sendCode}
          onConfirm={confirmCode}
          onClose={() => setEmailOpen(false)}
        />
      )}
      {pendingDelete && (
        <ConfirmDialog
          title={`Delete ${pendingDelete.name}?`}
          text="This removes the private charger listing and its connector records."
          saving={saving}
          onCancel={() => setPendingDelete(null)}
          onConfirm={removeStation}
        />
      )}
      {pendingMaintenance && (
        <MaintenanceApprovalDialog
          pending={pendingMaintenance}
          saving={saving}
          onCancel={() => setPendingMaintenance(null)}
          onConfirm={() =>
            void applyMonitorChange(
              pendingMaintenance.item,
              pendingMaintenance.status,
              true,
            )
          }
        />
      )}
      {pendingAgentAction && (
        <AgentActionApprovalDialog
          action={pendingAgentAction}
          saving={saving}
          onCancel={() => setPendingAgentAction(null)}
          onConfirm={executeAgentAction}
        />
      )}
      {reviewAction && (
        <TextActionDialog
          title={`${reviewAction.kind === "reply" ? "Reply to" : "Report review from"} ${reviewAction.title}`}
          value={reviewMessage}
          setValue={setReviewMessage}
          saving={saving}
          onCancel={() => setReviewAction(null)}
          onConfirm={saveReviewAction}
        />
      )}
    </section>
  );
}

function HostDashboardPanel({
  dashboard,
  stations,
  bookings,
  onNavigate,
}: {
  dashboard: HostDashboard;
  stations: Station[];
  bookings: Booking[];
  onNavigate: (tab: string) => void;
}) {
  return (
    <>
      <button
        type="button"
        className="host-agent-brief"
        onClick={() => onNavigate("ai")}
      >
        <span>
          <Bot size={22} />
        </span>
        <div>
          <small>VIDYUT HOST AGENT · APPROVAL CONTROLLED</small>
          <strong>
            {dashboard.alerts.length
              ? `${dashboard.alerts.length} connector signal${dashboard.alerts.length === 1 ? "" : "s"} need Prince's review`
              : "Revenue, demand and service briefing ready"}
          </strong>
          <p>
            Customer feedback, charger health, opening-hours opportunities,
            company comparisons and solar finance.
          </p>
        </div>
        <b>
          Open agent <ExternalLink size={14} />
        </b>
      </button>
      <div className="host-metric-grid host-network-metrics">
        <HostMetric icon={MapPin} label="Locations" value={dashboard.totalLocations} />
        <HostMetric icon={HousePlug} label="Chargers" value={dashboard.totalChargers} tone="blue" />
        <HostMetric icon={HeartPulse} label="Online" value={`${dashboard.onlineChargers} / ${dashboard.totalChargers}`} />
        <HostMetric icon={BadgeIndianRupee} label="Revenue this month" value={money(dashboard.monthlyEarnings)} tone="amber" />
        <HostMetric icon={Activity} label="Sessions this month" value={dashboard.monthlySessions} tone="blue" />
        <HostMetric icon={ShieldCheck} label="Successful sessions" value={`${dashboard.successfulSessionsPercent}%`} />
      </div>
      <div className="host-dashboard-grid">
        <article className="host-card">
          <div className="host-card-head">
            <div>
              <h2>Network health</h2>
              <p>Prince properties and their equipment operators</p>
            </div>
            <button onClick={() => onNavigate("monitoring")}>
              Monitor live
            </button>
          </div>
          <div className="host-status-strip">
            <SmallStat label="Chargers" value={dashboard.totalChargers} />
            <SmallStat label="Online" value={dashboard.onlineChargers} />
            <SmallStat label="Live sessions" value={dashboard.activeSessions} />
            <SmallStat
              label="Energy delivered"
              value={`${dashboard.energyDeliveredKwh} kWh`}
            />
          </div>
          <div className="host-list">
            {stations.map((station) => {
              const online = station.connectors?.filter((connector) => connector.status === "ONLINE").length ?? 0;
              const attention = station.connectors?.some((connector) =>
                (connector.healthScore ?? 100) < 60 || ["FAULT", "OFFLINE", "MAINTENANCE"].includes(connector.status ?? ""));
              return (
                <div key={station.id}>
                  <span className={`host-row-icon ${attention ? "amber" : ""}`}><HousePlug size={17} /></span>
                  <div>
                    <strong>{station.name}{station.demoData && <em className="host-demo-badge">DEMO DATA</em>}</strong>
                    <small>Prince property → {station.operatorCompanyName || "Host operated"} · {online}/{station.connectors?.length ?? 0} online</small>
                  </div>
                  <i className={attention ? "attention" : ""}>{attention ? "ATTENTION" : "HEALTHY"}</i>
                </div>
              );
            })}
            {!stations.length && (
              <HostEmpty
                icon={HousePlug}
                text="Add your first verified charger."
              />
            )}
          </div>
        </article>
        <article className="host-card">
          <div className="host-card-head">
            <div>
              <h2>Next bookings</h2>
              <p>Customers arriving soon</p>
            </div>
            <button onClick={() => onNavigate("bookings")}>View all</button>
          </div>
          <div className="host-list">
            {bookings
              .filter((b) => !["COMPLETED", "CANCELLED"].includes(b.status))
              .slice(0, 4)
              .map((booking) => (
                <div key={booking.id}>
                  <span className="host-row-icon blue">
                    <UserRound size={17} />
                  </span>
                  <div>
                    <strong>{booking.customerName}</strong>
                    <small>
                      {dateTime(booking.startTime)} · {booking.durationHours}h
                    </small>
                  </div>
                  <b>{money(booking.totalAmount)}</b>
                </div>
              ))}
            {!bookings.length && (
              <HostEmpty
                icon={CalendarDays}
                text="Upcoming reservations appear here."
              />
            )}
          </div>
        </article>
      </div>
    </>
  );
}
function HostResource({
  title,
  action,
  onAdd,
  empty,
  children,
}: {
  title: string;
  action: string;
  onAdd: () => void;
  empty: string;
  children: React.ReactNode;
}) {
  return (
    <article className="host-card">
      <div className="host-card-head">
        <div>
          <h2>{title}</h2>
          <p>Prince owns the properties; each charging operator may own and service its equipment</p>
        </div>
        <button className="feature-primary" onClick={onAdd}>
          <Plus size={15} />
          {action}
        </button>
      </div>
      {Children.count(children) ? (
        <div className="host-station-list">{children}</div>
      ) : (
        <HostEmpty
          icon={HousePlug}
          text={empty}
          action={action}
          onAction={onAdd}
        />
      )}
    </article>
  );
}
function HostStationRow({
  station,
  onEdit,
  onSchedule,
  onDelete,
}: {
  station: Station;
  onEdit: () => void;
  onSchedule: () => void;
  onDelete: () => void;
}) {
  const solar = station.amenities?.toLowerCase().includes("solar");
  return (
    <div className="host-station-row">
      <span className="host-row-icon">
        <BatteryCharging size={18} />
      </span>
      <div className="host-station-copy">
        <strong>
          {station.name}
          {station.demoData && <em className="host-demo-badge">DEMO DATA</em>}
          {solar && <em className="host-solar-badge">☀ SOLAR</em>}
        </strong>
        <small>
          <MapPin size={12} />
          {station.city} · {station.address}
        </small>
        <div className="host-ownership-chain">
          <span><UserRound size={12} /> Property: {station.propertyOwnerName || "Prince"}</span>
          <span><Building2 size={12} /> Operator: {station.operatorCompanyName || "Host operated"}</span>
          {station.solarProviderName && <span><Sun size={12} /> Solar: {station.solarProviderName}</span>}
        </div>
        <p>
          {station.connectors
            ?.map((connector) => `${connector.type} ${connector.powerKw} kW`)
            .join(" · ") || "Connector"}{" "}
          · {money(station.pricePerKwh)}/kWh
        </p>
      </div>
      <div className="host-station-state">
        <b>{station.availability}</b>
        <span>{station.workingHours}</span>
      </div>
      <button onClick={onSchedule}>Schedule</button>
      <button onClick={onEdit}>Edit</button>
      <button
        className="icon-button danger-icon"
        aria-label={`Delete ${station.name}`}
        onClick={onDelete}
      >
        <Trash2 size={15} />
      </button>
    </div>
  );
}
function AvailabilityPanel({
  stations,
  onEdit,
}: {
  stations: Station[];
  onEdit: (station: Station) => void;
}) {
  return (
    <article className="host-card">
      <div className="host-card-head">
        <div>
          <h2>Weekly & exception schedules</h2>
          <p>Control booking hours, holidays and emergency shutdowns</p>
        </div>
        <Clock3 size={20} />
      </div>
      <div className="availability-grid">
        {stations.map((station) => (
          <article key={station.id}>
            <div>
              <span className={station.emergencyDisabled ? "offline" : ""}>
                <HousePlug size={18} />
              </span>
              <i>
                {station.emergencyDisabled
                  ? "EMERGENCY OFF"
                  : station.availability}
              </i>
            </div>
            <h3>{station.name}</h3>
            <p>
              {station.weeklySchedule ||
                station.workingHours ||
                "No weekly schedule"}
            </p>
            <small>
              {station.holidaySchedule || "No holiday exceptions"} ·{" "}
              {station.bookingSlotMinutes || 60} min slots
            </small>
            <label>
              <input
                type="checkbox"
                checked={station.autoAvailability}
                readOnly
              />
              Auto availability
            </label>
            <button onClick={() => onEdit(station)}>Manage schedule</button>
          </article>
        ))}
        {!stations.length && (
          <HostEmpty
            icon={Clock3}
            text="Add a charger before creating availability."
          />
        )}
      </div>
    </article>
  );
}
function HostBookings({
  bookings,
  onStatus,
}: {
  bookings: Booking[];
  onStatus: (booking: Booking, status: Booking["status"]) => void;
}) {
  return (
    <article className="host-card">
      <div className="host-card-head">
        <div>
          <h2>Reservations & charging sessions</h2>
          <p>Accept, reject, start and complete owned bookings</p>
        </div>
        <CalendarDays size={20} />
      </div>
      <div className="host-booking-list">
        {bookings.map((booking) => (
          <div key={booking.id}>
            <span className="host-row-icon blue">
              <UserRound size={17} />
            </span>
            <div>
              <strong>{booking.customerName}</strong>
              <small>
                {booking.customerEmail} · {booking.stationName}
              </small>
              <p>
                {dateTime(booking.startTime)} · {booking.durationHours}h ·{" "}
                {booking.kwhDelivered} kWh
              </p>
            </div>
            <b>{money(booking.totalAmount)}</b>
            <i>{booking.status}</i>
            <section>
              {booking.status === "PENDING" && (
                <>
                  <button onClick={() => onStatus(booking, "CONFIRMED")}>
                    Accept
                  </button>
                  <button
                    className="danger"
                    onClick={() => onStatus(booking, "CANCELLED")}
                  >
                    Reject
                  </button>
                </>
              )}
              {booking.status === "CONFIRMED" && (
                <>
                  <button onClick={() => onStatus(booking, "IN_PROGRESS")}>
                    Start
                  </button>
                  <button
                    className="danger"
                    onClick={() => onStatus(booking, "CANCELLED")}
                  >
                    Cancel
                  </button>
                </>
              )}
              {booking.status === "IN_PROGRESS" && (
                <button onClick={() => onStatus(booking, "COMPLETED")}>
                  Complete
                </button>
              )}
            </section>
          </div>
        ))}
        {!bookings.length && (
          <HostEmpty icon={CalendarDays} text="No Host bookings yet." />
        )}
      </div>
    </article>
  );
}
function EarningsPanel({
  earnings,
  profile,
  amount,
  setAmount,
  saving,
  onWithdraw,
  onBank,
}: {
  earnings: Earnings;
  profile: HostProfile | null;
  amount: string;
  setAmount: (value: string) => void;
  saving: boolean;
  onWithdraw: () => void;
  onBank: () => void;
}) {
  const requestedAmount = Number(amount);
  const invalidAmount =
    !Number.isFinite(requestedAmount) ||
    requestedAmount < 100 ||
    requestedAmount > earnings.availableBalance;
  return (
    <>
      <div className="host-metric-grid">
        <HostMetric
          icon={Banknote}
          label="Today"
          value={money(earnings.daily)}
        />
        <HostMetric
          icon={Banknote}
          label="This week"
          value={money(earnings.weekly)}
        />
        <HostMetric
          icon={Banknote}
          label="This month"
          value={money(earnings.monthly)}
        />
        <HostMetric
          icon={WalletCards}
          label="Available"
          value={money(earnings.availableBalance)}
          tone="blue"
        />
      </div>
      <div className="host-dashboard-grid">
        <article className="host-card host-withdraw-card">
          <div className="host-card-head">
            <div>
              <h2>Withdraw earnings</h2>
              <p>
                {profile?.bankVerified
                  ? `Bank account ending ${profile.bankAccountLast4}`
                  : "Add and verify a bank account first"}
              </p>
            </div>
            <WalletCards size={20} />
          </div>
          <div>
            <strong>{money(earnings.availableBalance)}</strong>
            <span>available · {money(earnings.pendingPayout)} pending</span>
            <div>
              <input
                type="number"
                min="100"
                max={earnings.availableBalance}
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
                placeholder="Minimum ₹100"
              />
              <button
                onClick={() => void onWithdraw()}
                disabled={saving || !profile?.bankVerified || invalidAmount}
              >
                Withdraw
              </button>
            </div>
            {requestedAmount > earnings.availableBalance && (
              <p className="host-inline-error">
                Amount exceeds the available balance.
              </p>
            )}
            {!profile?.bankVerified && (
              <button className="secondary-action" onClick={onBank}>
                Set up bank account
              </button>
            )}
            <p>
              FY {earnings.financialYear} tax withheld:{" "}
              {money(earnings.taxWithheld)}
            </p>
          </div>
        </article>
        <article className="host-card">
          <div className="host-card-head">
            <div>
              <h2>Transaction history</h2>
              <p>Completed charging income</p>
            </div>
            <Activity size={20} />
          </div>
          <div className="host-list">
            {earnings.transactions.slice(0, 8).map((item) => (
              <div key={item.bookingId}>
                <span className="host-row-icon">
                  <Banknote size={16} />
                </span>
                <div>
                  <strong>{item.station}</strong>
                  <small>
                    Booking #{item.bookingId} · {dateTime(item.timestamp)}
                  </small>
                </div>
                <b>{money(item.amount)}</b>
              </div>
            ))}
            {!earnings.transactions.length && (
              <HostEmpty
                icon={Banknote}
                text="Completed session earnings appear here."
              />
            )}
          </div>
        </article>
      </div>
    </>
  );
}
function MonitoringPanel({
  items,
  onStatus,
}: {
  items: Monitor[];
  onStatus: (item: Monitor, status: Monitor["status"]) => void;
}) {
  const stationGroups = Array.from(
    items.reduce((groups, item) => {
      const current = groups.get(item.stationId) ?? { stationName: item.stationName, operator: item.operatorCompanyName, connectors: [] as Monitor[] };
      current.connectors.push(item);
      groups.set(item.stationId, current);
      return groups;
    }, new Map<number, { stationName: string; operator?: string; connectors: Monitor[] }>()).values(),
  );
  const occupied = items.filter((item) => item.status === "CHARGING").length;
  const available = items.filter((item) => item.status === "ONLINE" && item.available).length;
  const attention = items.filter((item) => item.status === "FAULT" || item.status === "MAINTENANCE" || item.healthScore < 70).length;
  return (
    <div className="host-monitoring-workspace">
      <div className="monitor-summary-strip">
        <SmallStat label="Stations" value={stationGroups.length} />
        <SmallStat label="Occupied now" value={occupied} />
        <SmallStat label="Available" value={available} />
        <SmallStat label="Needs attention" value={attention} />
      </div>
      <div className="monitor-station-grid">
        {stationGroups.map((station) => (
          <article className="host-card monitor-station" key={station.stationName}>
            <header><span><HousePlug size={19} /></span><div><strong>{station.stationName}</strong><small>{station.operator || "Host operated"} · {station.connectors.length} connectors</small></div><i>{station.connectors.filter((item) => item.status === "CHARGING").length} occupied</i></header>
            <div className="monitor-connector-list">
              {station.connectors.map((item) => (
                <section className={`monitor-connector ${item.status.toLowerCase()}`} key={item.id}>
                  <div className="monitor-connector-title"><span className={item.status === "FAULT" ? "fault" : ""}><HeartPulse size={17} /></span><div><strong>{item.chargerCode}</strong><small>{item.connectorType} · {item.powerKw} kW · health {item.healthScore}%</small></div><i>{item.status === "CHARGING" ? "OCCUPIED" : item.availabilityLabel || item.status}</i></div>
                  {item.status === "CHARGING" ? (
                    <div className="live-vehicle-session"><CarFront size={20} /><div><strong>{item.vehicleName || "Connected EV"}</strong><small>{item.vehicleRegistration || `Session #${item.sessionId}`} · {item.currentBatteryPercent ?? "—"}% → {item.targetBatteryPercent ?? "—"}%</small></div><span><strong>{item.currentPowerKw} kW</strong><small>{item.sessionEnergyKwh} kWh · {item.remainingMinutes ?? 0} min left</small></span></div>
                  ) : (
                    <div className="monitor-control"><span>{item.currentPowerKw} kW now · {item.sessionEnergyKwh} kWh session</span><select disabled={item.canControlOperationalStatus !== true} title={item.canControlOperationalStatus !== true ? "Company-operated equipment: ask the Host Agent to prepare a maintenance request" : undefined} aria-label={`Set operational state for ${item.chargerCode}`} value={item.status} onChange={(event) => void onStatus(item, event.target.value as Monitor["status"])}><option>ONLINE</option><option>OFFLINE</option><option>MAINTENANCE</option><option>FAULT</option></select></div>
                  )}
                  {item.faultCode && <p className="monitor-fault"><AlertTriangle size={14} />{item.faultCode}</p>}
                </section>
              ))}
            </div>
          </article>
        ))}
        {!items.length && <article className="host-card"><HostEmpty icon={HeartPulse} text="Register a connector to see live monitoring." /></article>}
      </div>
      <p className="monitor-source-note"><ShieldCheck size={14} /> Occupied state comes from the live charging session. The Host Assistant reads it; Vidyut Admin supervises network-wide exceptions.</p>
    </div>
  );
}
function ReviewsPanel({
  items,
  score,
  onAction,
}: {
  items: Review[];
  score: number;
  onAction: (item: Review, kind: "reply" | "report") => void;
}) {
  return (
    <div className="host-dashboard-grid">
      <article className="host-card reputation-card">
        <Star size={30} />
        <strong>{score.toFixed(1)}</strong>
        <span>Host reputation</span>
        <div>
          {[1, 2, 3, 4, 5].map((i) => (
            <Star
              key={i}
              size={17}
              fill={i <= Math.round(score) ? "currentColor" : "none"}
            />
          ))}
        </div>
        <p>{items.length} customer reviews</p>
      </article>
      <article className="host-card">
        <div className="host-card-head">
          <div>
            <h2>Customer reviews</h2>
            <p>Reply professionally or report abuse</p>
          </div>
          <MessageSquare size={20} />
        </div>
        <div className="review-list">
          {items.map((item) => (
            <div key={item.id}>
              <div>
                <strong>{item.customerName}</strong>
                <span>
                  {"★".repeat(item.rating)} · {dateTime(item.createdAt)}
                </span>
              </div>
              <p>{item.comment}</p>
              {item.hostReply && (
                <blockquote>
                  <b>Your reply</b>
                  {item.hostReply}
                </blockquote>
              )}
              <section>
                <button onClick={() => onAction(item, "reply")}>Reply</button>
                <button
                  className="danger"
                  onClick={() => onAction(item, "report")}
                >
                  {item.reported ? "Reported" : "Report abuse"}
                </button>
              </section>
            </div>
          ))}
          {!items.length && (
            <HostEmpty
              icon={MessageSquare}
              text="Reviews from completed sessions appear here."
            />
          )}
        </div>
      </article>
    </div>
  );
}
function HostAiPanel({
  question,
  setQuestion,
  answer,
  dashboard,
  monitoring,
  insight,
  busy,
  onAsk,
  onAction,
}: {
  question: string;
  setQuestion: (value: string) => void;
  answer: string;
  dashboard: HostDashboard;
  monitoring: Monitor[];
  insight: HostAgentInsight | null;
  busy: boolean;
  onAsk: () => void;
  onAction: (action: AgentAction) => void;
}) {
  const showExtendedAnalysis = false;
  const liveSessions = insight?.liveSessions ?? monitoring.filter((item) => item.status === "CHARGING");
  const serviceRisk = insight?.maintenanceRisks[0];
  const operationalActions = insight?.proposedActions ?? [];
  return (
    <div className="host-agent-workspace">
      <article className="host-card host-ai-card host-agent-conversation">
        <div className="host-ai-orb">
          <Bot size={29} />
        </div>
        <div className="feature-eyebrow">VIDYUT HOST AGENT · OPERATIONS & PARTNERSHIPS</div>
        <h2>Ask Prince’s host operations assistant</h2>
        <p>
          Evaluate property expansion, compare CPO offers, check hosted charger health,
          or prepare a new property draft. Vidyut checks backend records and asks before execution.
        </p>
        <div className="host-ai-answer">
          <Gauge size={25} />
          <div>
            <strong style={{ whiteSpace: "pre-wrap" }}>{answer}</strong>
            {insight?.assistantProvider && (
              <small className={`agent-provider ${insight.assistantFallback ? "fallback" : ""}`}>
                {insight.assistantFallback ? "Rules fallback" : insight.assistantProvider}
                {insight.assistantModel ? ` · ${insight.assistantModel}` : ""}
              </small>
            )}
          </div>
        </div>
        <div className="host-ai-input">
          <input
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") void onAsk();
            }}
            placeholder="Ask which property is best for expansion, compare company offers..."
          />
          <button
            onClick={() => void onAsk()}
            disabled={busy}
            aria-label="Ask Host assistant"
          >
            <Send size={17} />
          </button>
        </div>
        <div className="host-ai-prompts">
          {[
            "Which property is best for expansion?",
            "List a new 4-bay property near Faizabad Airport",
            "Compare company offers",
            "Show my charging partnerships",
            "Which hosted charger needs attention?",
          ].map((item) => (
            <button key={item} onClick={() => setQuestion(item)}>
              {item}
            </button>
          ))}
        </div>
        {operationalActions.length ? (
          <div className="host-agent-actions">
            <h3>Actions Vidyut can prepare</h3>
            {operationalActions.map((action) => (
              <button
                key={`${action.action}-${action.stationId ?? action.connectorId ?? action.label ?? "act"}`}
                onClick={() => void onAction(action)}
              >
                <span>
                  {action.action === "REQUEST_TATA_SERVICE" || action.action === "REQUEST_SERVICE" ? (
                    <Wrench size={16} />
                  ) : action.action === "PUT_CONNECTOR_IN_MAINTENANCE" ? (
                    <Wrench size={16} />
                  ) : action.action === "EXTEND_HOURS" ? (
                    <Clock3 size={16} />
                  ) : action.action === "CREATE_PROPERTY_DRAFT" ? (
                    <Building2 size={16} />
                  ) : action.action === "OPEN_MARKETPLACE" ? (
                    <Building2 size={16} />
                  ) : (
                    <Sun size={16} />
                  )}
                </span>
                <div>
                  <strong>{action.label}</strong>
                  <small>{action.detail}</small>
                </div>
                <i>{action.requiresConfirmation ? "ASK FIRST" : "OPEN"}</i>
              </button>
            ))}
          </div>
        ) : null}
      </article>
      <aside className="host-card host-agent-context">
        <div className="host-card-head">
          <div>
            <h2>Live Host context</h2>
            <p>Inputs behind every suggestion</p>
          </div>
          <Activity size={20} />
        </div>
        <div className="host-context">
          <SmallStat
            label="Monthly earnings"
            value={money(dashboard.monthlyEarnings)}
          />
          <SmallStat label="Uptime" value={`${dashboard.uptimePercent}%`} />
          <SmallStat label="Occupied" value={dashboard.occupiedChargers ?? liveSessions.length} />
          <SmallStat label="Rating" value={dashboard.reputationScore} />
        </div>
        <div className="host-agent-permissions">
          <strong>Vidyut may analyze automatically</strong>
          <p>Monitor · calculate · forecast · compare · prepare</p>
          <strong>Prince must approve execution</strong>
          <p>Property drafts · verification · publishing · maintenance · published hours</p>
          <strong>Always separately confirmed</strong>
          <p>Contracts · payments · finance submissions · purchases</p>
        </div>
      </aside>
      {insight && <section className="host-card host-agent-operations"><div className="host-card-head"><div><h2><CarFront size={18} /> Live charging sessions</h2><p>Fetched from session-backed connector occupancy</p></div><em>{liveSessions.length} ACTIVE</em></div><div className="host-live-session-list">{liveSessions.map((item) => <div key={item.sessionId ?? item.id}><span><BatteryCharging size={17} /></span><div><strong>{item.vehicleName || "Connected EV"}</strong><small>{item.stationName} · {item.chargerCode} · {item.vehicleRegistration || "Registration unavailable"}</small></div><b>{item.currentBatteryPercent ?? "—"}%<small>→ {item.targetBatteryPercent ?? "—"}% · {item.remainingMinutes ?? 0} min</small></b></div>)}{!liveSessions.length && <HostEmpty icon={CarFront} text="No cars are charging at Prince’s stations right now." />}</div>{serviceRisk && <div className="host-agent-simple-alert"><Wrench size={18} /><span><strong>{serviceRisk.chargerCode} is the highest service priority</strong><small>{serviceRisk.stationName} · risk {serviceRisk.riskScore}/100 · repair {money(serviceRisk.repairEstimate)} versus {money(serviceRisk.estimatedRevenueLoss24Hours)} modeled 24-hour loss</small></span></div>}<div className="host-agent-hours"><Clock3 size={18} /><span><strong>{insight.operatingHours.peakWindow} is the recorded peak</strong><small>{insight.operatingHours.recommendation}</small></span></div></section>}
      {showExtendedAnalysis && insight && (
        <>
          <section className="host-card host-agent-section portfolio">
            <div className="host-card-head"><div><h2><Building2 size={18} /> Prince’s multi-operator portfolio</h2><p>Property ownership and charger operations stay distinct</p></div><em>DEMO DATA</em></div>
            <div className="host-portfolio-grid">
              {insight.networkPortfolio.map((location) => <div key={location.stationId} className={location.networkHealth === "ATTENTION" ? "attention" : ""}>
                <span><HousePlug size={16} /></span>
                <div><strong>{location.stationName}</strong><small>{location.propertyOwnerName} property → {location.operatorCompanyName}</small></div>
                <b>{location.onlineConnectors}/{location.connectorCount}<small> online</small></b>
                <i>{location.networkHealth}</i>
              </div>)}
            </div>
            <div className="host-outage-playbook"><strong>When equipment suddenly fails</strong>{insight.outagePlaybook.steps.map((step, index) => <span key={step}><b>{index + 1}</b>{step}</span>)}<small>{insight.outagePlaybook.approvalPolicy}</small></div>
          </section>
          <section className="host-card host-agent-section revenue">
            <div className="host-card-head">
              <div>
                <h2>
                  <TrendingUp size={18} /> Today's revenue economics
                </h2>
                <p>Calculated from completed booking and energy records</p>
              </div>
              <em>DATABASE</em>
            </div>
            <div className="host-revenue-ledger">
              <span>
                <small>Gross charging revenue</small>
                <strong>{money(insight.revenue.todayGrossRevenue ?? 0)}</strong>
              </span>
              <span>
                <small>Electricity cost model</small>
                <strong>
                  -{money(insight.revenue.todayElectricityCost ?? 0)}
                </strong>
              </span>
              <span>
                <small>Platform share</small>
                <strong>
                  -{money(insight.revenue.todayPlatformShare ?? 0)}
                </strong>
              </span>
              <span className="net">
                <small>Estimated Host earnings</small>
                <strong>{money(insight.revenue.todayHostNet ?? 0)}</strong>
              </span>
            </div>
            <div className="host-context">
              <SmallStat
                label="Sessions"
                value={insight.revenue.todaySessions ?? 0}
              />
              <SmallStat
                label="Average session"
                value={money(insight.revenue.averageSessionValue ?? 0)}
              />
              <SmallStat
                label="Utilization"
                value={`${insight.revenue.utilizationPercent ?? 0}%`}
              />
              <SmallStat
                label="Peak"
                value={insight.revenue.peakWindow || "—"}
              />
            </div>
          </section>
          <section className="host-card host-agent-section maintenance">
            <div className="host-card-head">
              <div>
                <h2>
                  <Wrench size={18} /> Customer-driven servicing
                </h2>
                <p>
                  Hardware health combined with sessions and complaint language
                </p>
              </div>
              <em>LIVE SIGNALS</em>
            </div>
            <div className="host-risk-list">
              {insight.maintenanceRisks.slice(0, 4).map((item) => (
                <div
                  key={item.connectorId}
                  className={item.riskScore >= 35 ? "warning" : ""}
                >
                  <span>
                    <strong>{item.riskScore}</strong>
                    <small>risk</small>
                  </span>
                  <div>
                    <strong>{item.chargerCode}</strong>
                    <small>
                      {item.stationName} · {item.operatorCompanyName} · {item.connectorType}
                    </small>
                    <p>{item.signals.join(" · ")}</p>
                    <p className="host-repair-choice"><strong>{item.financialRecommendation.replaceAll("_", " ")}</strong> · repair {money(item.repairEstimate)} vs 24h loss {money(item.estimatedRevenueLoss24Hours)} · {item.operatorAction}</p>
                  </div>
                  <b>
                    {item.estimatedRepairHours}h repair
                    <br />
                    {item.repeatedFailures90Days} faults / 90d
                  </b>
                </div>
              ))}
            </div>
          </section>
          <section className="host-card host-agent-section hours">
            <div className="host-card-head">
              <div>
                <h2>
                  <Clock3 size={18} /> Opening-hours opportunity
                </h2>
                <p>
                  {insight.operatingHours.dataBasis
                    .replaceAll("_", " ")
                    .toLowerCase()}
                </p>
              </div>
              <em>{insight.operatingHours.peakWindow}</em>
            </div>
            <strong className="host-opportunity-value">
              {money(insight.operatingHours.estimatedAdditionalRevenue)}
            </strong>
            <p>
              Estimated additional gross revenue from{" "}
              {insight.operatingHours.additionalSessionsLow}–
              {insight.operatingHours.additionalSessionsHigh} sessions; modeled
              operating cost{" "}
              {money(insight.operatingHours.estimatedAdditionalOperatingCost)}.
            </p>
            <div className="host-opportunity-bar">
              <span style={{ width: "72%" }} />
            </div>
            <small>{insight.operatingHours.recommendation}</small>
          </section>
          <section className="host-card host-agent-section deals">
            <div className="host-card-head">
              <div>
                <h2>
                  <Building2 size={18} /> Company deal comparison
                </h2>
                <p>Scenario comparison only · no contract execution</p>
              </div>
              <em>DEMO DATA</em>
            </div>
            {insight.companyDeals.map((deal) => (
              <div className="host-deal-row" key={deal.company}>
                <div>
                  <strong>{deal.company} <em>{deal.recommendationTag.replaceAll("_", " ")}</em></strong>
                  <small>{deal.chargerPowerKw} kW · {deal.revenueModel.replaceAll("_", " ")} · Host {deal.hostShareLabel} · {deal.installationFunding.replaceAll("_", " ")}</small>
                  <small>{deal.tradeoff}</small>
                </div>
                <span>
                  <b>{money(deal.projectedMonthlyHostIncome)}/mo</b>
                  <small>{money(deal.projectedThreeYearValue)} / 3 years · {deal.riskLevel.replaceAll("_", " ")} risk</small>
                </span>
              </div>
            ))}
          </section>
          <section className="host-card host-agent-section solar">
            <div className="host-card-head">
              <div>
                <h2>
                  <Sun size={18} /> Vidyut Green Finance
                </h2>
                <p>
                  {insight.solarOpportunity.stationName} ·{" "}
                  {insight.solarOpportunity.dataBasis
                    .replaceAll("_", " ")
                    .toLowerCase()}
                </p>
              </div>
              <em>VERIFY ELIGIBILITY</em>
            </div>
            <div className="host-solar-metrics">
              <SmallStat
                label="Solar capacity"
                value={`${insight.solarOpportunity.solarCapacityKw} kW`}
              />
              <SmallStat
                label="Solar contribution"
                value={`${insight.solarOpportunity.solarContributionPercent}%`}
              />
              <SmallStat
                label="Monthly savings"
                value={money(insight.solarOpportunity.monthlySavings)}
              />
              <SmallStat
                label="Simple payback"
                value={`${insight.solarOpportunity.simplePaybackYears} yr`}
              />
            </div>
            <div className="host-funding-plan">
              <div><small>Total outlet project</small><strong>{money(insight.solarOpportunity.fundingPlan.projectCost)}</strong></div>
              <div><small>Prince contribution</small><strong>{money(insight.solarOpportunity.fundingPlan.princeContribution)}</strong></div>
              <div><small>TATA / CPO contribution</small><strong>{money(insight.solarOpportunity.fundingPlan.operatorContribution)}</strong></div>
              <div><small>Potential assistance*</small><strong>{money(insight.solarOpportunity.fundingPlan.potentialGovernmentAssistance)}</strong></div>
              <div><small>RESCO contribution</small><strong>{money(insight.solarOpportunity.fundingPlan.solarProviderContribution)}</strong></div>
              <div className="success"><small>Additional Prince capital</small><strong>{money(insight.solarOpportunity.fundingPlan.additionalUpfrontRequired)}</strong></div>
            </div>
            <div className="host-solar-options">
              {insight.solarOpportunity.solarOptions.map((option) => <div key={option.option} className={option.option === "RESCO_PPA" ? "recommended" : ""}>
                <strong>{option.label}{option.option === "RESCO_PPA" && <em>LOW-UPFRONT</em>}</strong>
                <small>{option.ownership.replaceAll("_", " ")} · {option.upfrontRequirement} upfront</small>
                <p>{option.tradeoff}</p>
              </div>)}
            </div>
            <div className="host-finance-leads">
              {insight.solarOpportunity.eligibilityLeads.map((lead) => (
                <span key={lead.name}>
                  <ShieldCheck size={14} />
                  <strong>{lead.name}<small>{lead.note}</small></strong>
                  <i>{lead.status.replaceAll("_", " ")}</i>
                </span>
              ))}
            </div>
            <p className="host-agent-policy">
              {insight.solarOpportunity.legalNotice}
            </p>
          </section>
        </>
      )}
    </div>
  );
}
function GreenFinancePanel({
  insight,
  busy,
  onAction,
}: {
  insight: HostAgentInsight | null;
  busy: boolean;
  onAction: (action: AgentAction) => void;
}) {
  if (!insight) {
    return <article className="host-card"><HostEmpty icon={BadgeIndianRupee} text="Loading company offers and Green Finance options…" /></article>;
  }
  const solar = insight.solarOpportunity;
  const prepareAction = insight.proposedActions.find((action) => action.action === "PREPARE_GREEN_FINANCE");
  const marketplaceAction = insight.proposedActions.find((action) => action.action === "OPEN_MARKETPLACE");
  return <div className="host-finance-workspace">
    <article className="host-card host-finance-hero"><span><Sun size={26} /></span><div><div className="feature-eyebrow">SEPARATE FINANCE WORKSPACE</div><h2>Lower Prince’s upfront investment</h2><p>Compare operator offers, solar ownership models and possible assistance. All figures are estimates until a company or government authority verifies them.</p></div><button disabled={busy || !marketplaceAction} onClick={() => marketplaceAction && onAction(marketplaceAction)}><Building2 size={16} /> Compare companies</button><button disabled={busy || !prepareAction} onClick={() => prepareAction && onAction(prepareAction)}><FileSpreadsheet size={16} /> Prepare eligibility checklist</button></article>
    <div className="host-finance-summary"><SmallStat label="Modeled project" value={money(solar.fundingPlan.projectCost)} /><SmallStat label="Prince contribution" value={money(solar.fundingPlan.princeContribution)} /><SmallStat label="Potential assistance" value={money(solar.fundingPlan.potentialGovernmentAssistance)} /><SmallStat label="Additional upfront" value={money(solar.fundingPlan.additionalUpfrontRequired)} /></div>
    <section className="host-card host-agent-section deals"><div className="host-card-head"><div><h2><Building2 size={18} /> Operator offer comparison</h2><p>Scenario comparison only · contracts require separate approval</p></div><em>DEMO ESTIMATES</em></div>{insight.companyDeals.map((deal) => <div className="host-deal-row" key={deal.company}><div><strong>{deal.company} <em>{deal.recommendationTag.replaceAll("_", " ")}</em></strong><small>{deal.chargerPowerKw} kW · {deal.revenueModel.replaceAll("_", " ")} · Host {deal.hostShareLabel}</small><small>{deal.tradeoff}</small></div><span><b>{money(deal.projectedMonthlyHostIncome)}/mo</b><small>{money(deal.projectedThreeYearValue)} modeled over 3 years</small></span></div>)}</section>
    <section className="host-card host-agent-section solar"><div className="host-card-head"><div><h2><Sun size={18} /> Solar options for {solar.stationName}</h2><p>Choose a structure before preparing documents</p></div><em>VERIFY ELIGIBILITY</em></div><div className="host-solar-metrics"><SmallStat label="Solar capacity" value={`${solar.solarCapacityKw} kW`} /><SmallStat label="Solar contribution" value={`${solar.solarContributionPercent}%`} /><SmallStat label="Monthly savings" value={money(solar.monthlySavings)} /><SmallStat label="Simple payback" value={`${solar.simplePaybackYears} yr`} /></div><div className="host-solar-options">{solar.solarOptions.map((option) => <div key={option.option} className={option.option === "RESCO_PPA" ? "recommended" : ""}><strong>{option.label}{option.option === "RESCO_PPA" && <em>LOW-UPFRONT</em>}</strong><small>{option.ownership.replaceAll("_", " ")} · {option.upfrontRequirement} upfront</small><p>{option.tradeoff}</p></div>)}</div><div className="host-finance-leads">{solar.eligibilityLeads.map((lead) => <span key={lead.name}><ShieldCheck size={14} /><strong>{lead.name}<small>{lead.note}</small></strong><i>{lead.status.replaceAll("_", " ")}</i></span>)}</div><p className="host-agent-policy">{solar.legalNotice}</p></section>
  </div>;
}
function ReportsPanel({
  onDownload,
}: {
  onDownload: (type: "EARNINGS" | "USAGE", format: "PDF" | "XLSX") => void;
}) {
  return (
    <article className="host-card">
      <div className="host-card-head">
        <div>
          <h2>Reports & exports</h2>
          <p>Tax-ready earnings, booking, usage and energy records</p>
        </div>
        <Download size={20} />
      </div>
      <div className="host-report-grid">
        <ReportCard
          title="Earnings report"
          text="Daily, weekly, monthly, payouts, transactions and tax summary."
          onPdf={() => onDownload("EARNINGS", "PDF")}
          onExcel={() => onDownload("EARNINGS", "XLSX")}
        />
        <ReportCard
          title="Charger usage report"
          text="Bookings, sessions, uptime and energy delivered."
          onPdf={() => onDownload("USAGE", "PDF")}
          onExcel={() => onDownload("USAGE", "XLSX")}
        />
      </div>
    </article>
  );
}
function NotificationsPanel({
  items,
  onRead,
  onReadAll,
}: {
  items: NotificationItem[];
  onRead: (id: number) => Promise<void>;
  onReadAll: () => Promise<void>;
}) {
  const unread = items.filter((item) => !item.read).length;
  return (
    <article className="host-card">
      <div className="host-card-head">
        <div>
          <h2>Notification inbox</h2>
          <p>
            {unread
              ? `${unread} unread Host update${unread === 1 ? "" : "s"}`
              : "Bookings, sessions, payments, faults and uptime alerts"}
          </p>
        </div>
        {unread > 0 ? (
          <button onClick={() => void onReadAll()}>Mark all read</button>
        ) : (
          <Bell size={20} />
        )}
      </div>
      <div className="host-notification-list">
        {items.map((item) => (
          <div className={item.read ? "" : "unread"} key={item.id}>
            <span>
              <Bell size={16} />
            </span>
            <div>
              <strong>{item.title}</strong>
              <p>{item.message}</p>
              <small>{item.type?.replaceAll("_", " ") || "HOST UPDATE"}</small>
            </div>
            <time>{dateTime(item.timestamp)}</time>
            {!item.read && (
              <button onClick={() => void onRead(item.id)}>Mark read</button>
            )}
          </div>
        ))}
        {!items.length && <HostEmpty icon={Bell} text="All caught up." />}
      </div>
    </article>
  );
}
function HostProfilePanel({
  profile,
  onProfile,
  onEmail,
  onKyc,
  onBank,
  onSettings,
}: {
  profile: HostProfile | null;
  onProfile: () => void;
  onEmail: () => void;
  onKyc: () => void;
  onBank: () => void;
  onSettings: (
    changes: Partial<
      Pick<
        HostProfile,
        "emailNotifications" | "pushNotifications" | "autoAvailability"
      >
    >,
  ) => void;
}) {
  return (
    <div className="host-profile-grid">
      <article className="host-card host-profile-card">
        <div className="host-avatar">
          {profile?.displayName
            ?.split(/\s+/)
            .slice(0, 2)
            .map((part) => part[0])
            .join("")}
        </div>
        <h2>{profile?.displayName}</h2>
        <p>{profile?.email}</p>
        <div>
          <i className={profile?.emailVerified ? "ok" : ""}>
            {profile?.emailVerified ? "Email verified" : "Email pending"}
          </i>
          <i className={profile?.verificationStatus === "VERIFIED" ? "ok" : ""}>
            KYC {profile?.verificationStatus?.toLowerCase()}
          </i>
          <i className={profile?.bankVerified ? "ok" : ""}>
            {profile?.bankVerified
              ? `Bank •••• ${profile.bankAccountLast4}`
              : "Bank pending"}
          </i>
        </div>
        <button onClick={onProfile}>Edit Host profile</button>
      </article>
      <article className="host-card">
        <div className="host-card-head">
          <div>
            <h2>Verification & payouts</h2>
            <p>Required for safe charger operations</p>
          </div>
          <ShieldCheck size={20} />
        </div>
        <div className="host-profile-actions">
          <button onClick={onEmail} disabled={profile?.emailVerified}>
            <ShieldCheck size={17} />
            <span>
              <strong>Account email</strong>
              <small>
                {profile?.emailVerified ? "Verified" : "Send verification code"}
              </small>
            </span>
          </button>
          <button onClick={onKyc}>
            <UserRound size={17} />
            <span>
              <strong>Identity KYC</strong>
              <small>
                {profile?.verificationStatus ?? "PENDING"} ·{" "}
                {profile?.identityType || "Add identity"}
              </small>
            </span>
          </button>
          <button onClick={onBank}>
            <Banknote size={17} />
            <span>
              <strong>Bank & payouts</strong>
              <small>
                {profile?.bankVerified
                  ? `${profile.bankName} •••• ${profile.bankAccountLast4}`
                  : "Set up withdrawals"}
              </small>
            </span>
          </button>
        </div>
      </article>
      <article className="host-card">
        <div className="host-card-head">
          <div>
            <h2>Automation & alerts</h2>
            <p>Control Host notifications and schedules</p>
          </div>
          <Bell size={20} />
        </div>
        <div className="host-settings">
          <SettingToggle
            label="Email updates"
            sub="Booking and earnings summaries"
            checked={profile?.emailNotifications ?? false}
            onChange={(value) => onSettings({ emailNotifications: value })}
          />
          <SettingToggle
            label="Push fault alerts"
            sub="Faults, sessions and low uptime"
            checked={profile?.pushNotifications ?? false}
            onChange={(value) => onSettings({ pushNotifications: value })}
          />
          <SettingToggle
            label="Auto availability"
            sub="Follow weekly charger schedules"
            checked={profile?.autoAvailability ?? false}
            onChange={(value) => onSettings({ autoAvailability: value })}
          />
        </div>
      </article>
    </div>
  );
}

function HostModal({
  kind,
  editing,
  form,
  setForm,
  saving,
  onClose,
  onSubmit,
}: {
  kind: Exclude<ModalKind, null>;
  editing: boolean;
  form: Record<string, string | number | boolean>;
  setForm: React.Dispatch<
    React.SetStateAction<Record<string, string | number | boolean>>
  >;
  saving: boolean;
  onClose: () => void;
  onSubmit: () => void;
}) {
  const field = (name: string, label: string, type = "text") => (
    <label>
      {label}
      <input
        type={type}
        value={String(form[name] ?? "")}
        onChange={(event) =>
          setForm((current) => ({
            ...current,
            [name]:
              type === "number"
                ? Number(event.target.value)
                : event.target.value,
          }))
        }
      />
    </label>
  );
  const select = (name: string, label: string, options: string[]) => (
    <label>
      {label}
      <select
        value={String(form[name] ?? "")}
        onChange={(event) =>
          setForm((current) => ({ ...current, [name]: event.target.value }))
        }
      >
        {options.map((option) => (
          <option key={option}>{option}</option>
        ))}
      </select>
    </label>
  );
  return (
    <div className="vehicle-form-backdrop" onMouseDown={onClose}>
      <section
        className="host-modal"
        role="dialog"
        aria-modal="true"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="host-card-head">
          <div>
            <h2>
              {editing ? "Edit" : "Set up"} {kind}
            </h2>
            <p>Changes apply only to this Host account.</p>
          </div>
          <button
            className="icon-button"
            aria-label="Close form"
            onClick={onClose}
          >
            <X size={17} />
          </button>
        </div>
        <div className="host-form-grid">
          {kind === "charger" && (
            <>
              {field("name", "Location name")}
              {field("city", "City")}
              {field("address", "Full address")}
              {field("latitude", "Latitude", "number")}
              {field("longitude", "Longitude", "number")}
              {field("pricePerKwh", "Price ₹/kWh", "number")}
              {field("propertyOwnerName", "Property owner")}
              {field("operatorCompanyName", "Charging operator / CPO")}
              {field("equipmentOwnerName", "Equipment owner")}
              {select("operatingModel", "Operating model", [
                "HOST_PROPERTY_CPO_EQUIPMENT",
                "HOST_OPERATED",
                "LEASED_TO_OPERATOR",
              ])}
              {field("solarProviderName", "Solar / RESCO provider")}
              {!editing &&
                select("connectorType", "Connector type", [
                  "TYPE2",
                  "CCS2",
                  "CHADEMO",
                  "GB_T",
                ])}
              {!editing && field("powerKw", "Charging speed kW", "number")}
              {field("imageUrl", "Primary photo URL")}
              {field("photoUrls", "Additional photo URLs")}
              {field("amenities", "Amenities")}
              {field("workingHours", "Working hours")}
              {field("chargingInstructions", "Customer instructions")}
              {editing &&
                select("status", "Listing status", [
                  "ACTIVE",
                  "MAINTENANCE",
                  "OFFLINE",
                ])}
              {editing &&
                select("availability", "Availability", [
                  "AVAILABLE",
                  "CHARGING",
                  "RESERVED",
                  "UNAVAILABLE",
                ])}
            </>
          )}
          {kind === "availability" && (
            <>
              {select("availability", "Availability", [
                "AVAILABLE",
                "CHARGING",
                "RESERVED",
                "UNAVAILABLE",
              ])}
              {field("bookingSlotMinutes", "Slot duration minutes", "number")}
              {field("weeklySchedule", "Weekly schedule")}
              {field("holidaySchedule", "Holiday exceptions")}
              {field("chargingInstructions", "Customer instructions")}
              <label className="host-check">
                <input
                  type="checkbox"
                  checked={Boolean(form.autoAvailability)}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      autoAvailability: event.target.checked,
                    }))
                  }
                />
                Auto availability
              </label>
              <label className="host-check danger">
                <input
                  type="checkbox"
                  checked={Boolean(form.emergencyDisabled)}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      emergencyDisabled: event.target.checked,
                    }))
                  }
                />
                Emergency disable
              </label>
            </>
          )}
          {kind === "profile" && (
            <>
              {field("displayName", "Display name")}
              {field("phone", "Phone")}
              {field("address", "Home / charger address")}
              {field("bio", "Host bio")}
            </>
          )}
          {kind === "kyc" && (
            <>
              {select("identityType", "Identity type", [
                "AADHAAR",
                "PAN",
                "DRIVING_LICENSE",
                "PASSPORT",
              ])}
              {field("identityLast4", "Last 4 identity characters")}
              {field("kycDocumentUrl", "KYC document URL")}
            </>
          )}
          {kind === "bank" && (
            <>
              {field("accountHolder", "Account holder")}
              {field("bankName", "Bank name")}
              {field("accountNumber", "Account number")}
              {field("ifscCode", "IFSC code")}
              {field("payoutUpi", "UPI ID")}
            </>
          )}
        </div>
        <div className="host-modal-actions">
          <button className="secondary-action" onClick={onClose}>
            Cancel
          </button>
          <button
            className="feature-primary"
            disabled={saving}
            onClick={() => void onSubmit()}
          >
            {saving ? "Saving…" : "Save changes"}
          </button>
        </div>
      </section>
    </div>
  );
}
function EmailDialog({
  sent,
  code,
  setCode,
  saving,
  onSend,
  onConfirm,
  onClose,
}: {
  sent: boolean;
  code: string;
  setCode: (value: string) => void;
  saving: boolean;
  onSend: () => void;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <div className="vehicle-form-backdrop" onMouseDown={onClose}>
      <section
        className="host-modal host-email-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="host-email-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="host-card-head">
          <div>
            <h2 id="host-email-title">Verify Host email</h2>
            <p>Single-use codes expire after 15 minutes.</p>
          </div>
          <button
            className="icon-button"
            aria-label="Close email verification"
            onClick={onClose}
            disabled={saving}
          >
            <X size={17} />
          </button>
        </div>
        <div className="host-email-body">
          {!sent ? (
            <button
              className="feature-primary"
              onClick={() => void onSend()}
              disabled={saving}
            >
              Send verification code
            </button>
          ) : (
            <>
              <label>
                6-digit code
                <input
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  maxLength={6}
                  value={code}
                  onChange={(event) =>
                    setCode(event.target.value.replace(/\D/g, "").slice(0, 6))
                  }
                  placeholder="000000"
                  autoFocus
                />
              </label>
              <p>Check your email inbox for the verification code.</p>
              <div>
                <button
                  className="secondary-action"
                  onClick={() => void onSend()}
                  disabled={saving}
                >
                  Resend
                </button>
                <button
                  className="feature-primary"
                  onClick={() => void onConfirm()}
                  disabled={saving || code.length !== 6}
                >
                  Verify email
                </button>
              </div>
            </>
          )}
        </div>
      </section>
    </div>
  );
}
function MaintenanceApprovalDialog({
  pending,
  saving,
  onCancel,
  onConfirm,
}: {
  pending: {
    item: Monitor;
    status: Monitor["status"];
    impact: MaintenanceImpact;
  };
  saving: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const { item, status, impact } = pending;
  return (
    <div className="vehicle-form-backdrop" onMouseDown={onCancel}>
      <section
        className="host-modal host-approval-dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="maintenance-approval-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="host-approval-icon">
          <Wrench size={24} />
        </div>
        <div>
          <div className="feature-eyebrow">HOST APPROVAL REQUIRED</div>
          <h2 id="maintenance-approval-title">
            Put {item.chargerCode} in {status.toLowerCase()}?
          </h2>
          <p>{impact.operatorCompanyName} · {impact.faultCode}</p>
          <small>{impact.message}</small>
        </div>
        <div className="host-impact-grid">
          <SmallStat label="Active journeys" value={impact.activeJourneys} />
          <SmallStat label="Auto reroutes" value={impact.automaticReroutes} />
          <SmallStat label="Driver approvals" value={impact.driverApprovals} />
          <SmallStat
            label="Upcoming bookings"
            value={impact.upcomingReservations}
          />
        </div>
        <div className="host-repair-economics">
          <span><small>Repair estimate</small><strong>{money(impact.repairEstimate)}</strong></span>
          <span><small>Lost revenue · next 3h</small><strong>{money(impact.estimatedLostRevenueNext3Hours)}</strong></span>
          <span><small>Modeled loss · 24h</small><strong>{money(impact.estimatedRevenueLoss24Hours)}</strong></span>
          <span className="decision"><small>Financial recommendation</small><strong>{impact.repairRecommendation.replaceAll("_", " ")}</strong></span>
        </div>
        <section className="host-user-impact-preview">
          <div><strong>Modeled customer impact</strong><small>Best currently compatible alternative · demo estimate</small></div>
          <div className="host-impact-grid">
            <SmallStat label="Extra driving" value={`+${impact.modeledUserImpact.extraDistanceKm} km`} />
            <SmallStat label="Journey delay" value={`+${impact.modeledUserImpact.delayMinutes} min`} />
            <SmallStat label="Charging difference" value={`${impact.modeledUserImpact.chargingCostDifference >= 0 ? "+" : ""}${money(impact.modeledUserImpact.chargingCostDifference)}`} />
            <SmallStat label="Extra battery" value={`-${impact.modeledUserImpact.extraBatteryPercent}%`} />
          </div>
          {impact.recommendedAlternatives.length > 0 && <div className="host-alternative-list">
            {impact.recommendedAlternatives.map((alternative, index) => <div key={alternative.stationId}>
              <b>{index === 0 ? "RECOMMENDED" : `OPTION ${index + 1}`}</b>
              <span><strong>{alternative.stationName}</strong><small>{alternative.operatorCompanyName} · {alternative.connectorType} {alternative.powerKw} kW</small></span>
              <span><strong>+{alternative.delayMinutes} min</strong><small>+{alternative.extraDistanceKm} km · {money(alternative.chargingCost)}</small></span>
            </div>)}
          </div>}
        </section>
        <div
          className={`host-impact-note ${impact.backupConnectorAvailable ? "safe" : "warning"}`}
        >
          <ShieldCheck size={17} />
          <span>
            <strong>
              {impact.backupConnectorAvailable
                ? "Compatible backup is online"
                : "No same-station backup"}
            </strong>
            <small>
              {impact.backupConnectorAvailable
                ? "Existing journeys can remain at this station."
                : "Vidyut will find another reachable charger, preserving the driver’s autonomy mode."}
            </small>
          </span>
        </div>
        <p className="host-agent-policy">
          Approval changes charger availability. Full Autopilot journeys reroute
          automatically; Ask before actions and Recommend only journeys wait for
          the driver.
        </p>
        <div className="host-modal-actions">
          <button
            className="secondary-action"
            onClick={onCancel}
            disabled={saving}
          >
            Keep online
          </button>
          <button
            className="feature-primary"
            onClick={() => void onConfirm()}
            disabled={saving}
          >
            {saving ? "Applying safely…" : "Approve maintenance"}
          </button>
        </div>
      </section>
    </div>
  );
}
function AgentActionApprovalDialog({
  action,
  saving,
  onCancel,
  onConfirm,
}: {
  action: AgentAction;
  saving: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="vehicle-form-backdrop" onMouseDown={onCancel}>
      <section
        className="host-modal host-approval-dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="agent-action-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="host-approval-icon">
          <Bot size={24} />
        </div>
        <div>
          <div className="feature-eyebrow">
            VIDYUT PREPARED THIS · PRINCE DECIDES
          </div>
          <h2 id="agent-action-title">{action.label}</h2>
          <p>{action.detail}</p>
        </div>
        <div className="host-impact-note safe">
          <ShieldCheck size={17} />
          <span>
            <strong>Bounded action only</strong>
            <small>
              This approval does not sign a contract, make a payment, buy
              equipment or submit a government application.
            </small>
          </span>
        </div>
        <p className="host-agent-policy">
          You can review any prepared output before a later external commitment.
          Those commitments always require a separate confirmation.
        </p>
        <div className="host-modal-actions">
          <button
            className="secondary-action"
            onClick={onCancel}
            disabled={saving}
          >
            Not now
          </button>
          <button
            className="feature-primary"
            onClick={() => void onConfirm()}
            disabled={saving}
          >
            {saving ? "Processing…" : "Approve this action"}
          </button>
        </div>
      </section>
    </div>
  );
}
function ConfirmDialog({
  title,
  text,
  saving,
  onCancel,
  onConfirm,
}: {
  title: string;
  text: string;
  saving: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="vehicle-form-backdrop">
      <section
        className="host-modal host-confirm"
        role="alertdialog"
        aria-modal="true"
      >
        <Trash2 size={24} />
        <h2>{title}</h2>
        <p>{text}</p>
        <div>
          <button className="secondary-action" onClick={onCancel}>
            Keep listing
          </button>
          <button
            className="danger-action"
            disabled={saving}
            onClick={() => void onConfirm()}
          >
            Delete permanently
          </button>
        </div>
      </section>
    </div>
  );
}
function TextActionDialog({
  title,
  value,
  setValue,
  saving,
  onCancel,
  onConfirm,
}: {
  title: string;
  value: string;
  setValue: (value: string) => void;
  saving: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="vehicle-form-backdrop">
      <section
        className="host-modal host-text-modal"
        role="dialog"
        aria-modal="true"
      >
        <h2>{title}</h2>
        <textarea
          value={value}
          onChange={(event) => setValue(event.target.value)}
          rows={5}
        />
        <div className="host-modal-actions">
          <button className="secondary-action" onClick={onCancel}>
            Cancel
          </button>
          <button
            className="feature-primary"
            disabled={saving || !value.trim()}
            onClick={() => void onConfirm()}
          >
            Submit
          </button>
        </div>
      </section>
    </div>
  );
}
function HostMetric({
  icon: Icon,
  label,
  value,
  tone = "green",
}: {
  icon: typeof HousePlug;
  label: string;
  value: string | number;
  tone?: "green" | "blue" | "amber";
}) {
  return (
    <article className={`host-metric ${tone}`}>
      <span>
        <Icon size={19} />
      </span>
      <strong>{value}</strong>
      <p>{label}</p>
    </article>
  );
}
function SmallStat({
  label,
  value,
}: {
  label: string;
  value: string | number;
}) {
  return (
    <div className="host-small-stat">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
function HostEmpty({
  icon: Icon,
  text,
  action,
  onAction,
}: {
  icon: typeof HousePlug;
  text: string;
  action?: string;
  onAction?: () => void;
}) {
  return (
    <div className="host-empty">
      <Icon size={25} />
      <p>{text}</p>
      {action && (
        <button className="feature-primary" onClick={onAction}>
          <Plus size={14} />
          {action}
        </button>
      )}
    </div>
  );
}
function ReportCard({
  title,
  text,
  onPdf,
  onExcel,
}: {
  title: string;
  text: string;
  onPdf: () => void;
  onExcel: () => void;
}) {
  return (
    <article>
      <span>
        <FileSpreadsheet size={21} />
      </span>
      <h3>{title}</h3>
      <p>{text}</p>
      <div>
        <button onClick={onPdf}>
          <Download size={14} />
          PDF
        </button>
        <button onClick={onExcel}>
          <FileSpreadsheet size={14} />
          Excel
        </button>
      </div>
    </article>
  );
}
function SettingToggle({
  label,
  sub,
  checked,
  onChange,
}: {
  label: string;
  sub: string;
  checked: boolean;
  onChange: (value: boolean) => void;
}) {
  return (
    <label>
      <span>
        <strong>{label}</strong>
        <small>{sub}</small>
      </span>
      <input
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
      />
    </label>
  );
}
