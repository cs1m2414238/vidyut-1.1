import { apiRequest } from './api';

export type AutopilotTripStatus =
  | 'RESERVED'
  | 'MONITORING'
  | 'REROUTED'
  | 'REROUTE_APPROVAL_REQUIRED'
  | 'REPLAN_REQUIRED'
  | 'PAYMENT_REQUIRED'
  | 'COMPLETED'
  | 'CANCELLED';

export type AutopilotStopStatus = 'PLANNED' | 'RESERVED' | 'CANCELLED' | 'COMPLETED';
export type AutopilotActionState = 'SUCCESS' | 'INFO' | 'WARNING';
export type AutopilotMode = 'RECOMMEND_ONLY' | 'ASK_BEFORE_ACTIONS' | 'FULL_AUTOPILOT';
export type TripPurpose = 'GENERAL' | 'MALL_VISIT' | 'REST_STOP' | 'COMMUTE' | 'DESTINATION_CHARGING';

export interface AutopilotVehicle {
  id: number;
  userId: number;
  makeAndModel: string;
  registrationNumber: string;
  batteryCapacity: string;
  connectorType: string;
  supportedConnectors?: string[];
  efficiencyWhPerKm?: number;
  maxAcChargePowerKw?: number;
  maxDcChargePowerKw?: number;
  chargingEfficiency?: number;
  batteryPercent?: number;
  remainingRangeKm?: number;
}

export interface AutopilotTripRequest {
  vehicleId: number;
  origin: string;
  destination: string;
  goal: string;
  tripPurpose: TripPurpose;
  arrivalDeadline: string;
  optimizeFor: 'TIME' | 'COST' | 'BALANCED';
  autonomyMode: AutopilotMode;
  currentBatteryPercent: number;
  minimumArrivalBatteryPercent: number;
  maximumChargingBudget: number;
  idempotencyKey: string;
}

export interface AutopilotStop {
  id: number;
  sequenceNumber: number;
  stationId: number;
  bookingId?: number;
  connectorId?: number;
  chargerCode?: string;
  stationName: string;
  stationAddress: string;
  connectorType: string;
  powerKw: number;
  effectivePowerKw: number;
  distanceFromOriginKm: number;
  routeOffsetKm: number;
  arrivalBatteryPercent: number;
  targetBatteryPercent: number;
  estimatedWaitMinutes: number;
  chargingMinutes: number;
  connectionMinutes: number;
  estimatedCost: number;
  demoData: boolean;
  selectionReason?: string;
  status: AutopilotStopStatus;
  selectionType?: 'PRIMARY' | 'REROUTED_REPLACEMENT' | 'USER_SELECTED' | 'ALTERNATE' | string;
  replacesStationId?: number;
  replacesStationName?: string;
  rerouteReason?: string;
  additionalDistanceKm?: number;
  additionalMinutes?: number;
  additionalCost?: number;
  removalReason?: string;
  replacedByStationId?: number;
  replacedByStationName?: string;
  originalStopIndex?: number;
}

export interface VehicleRecommendationRequest {
  origin: string;
  destination: string;
  goal: string;
  tripPurpose: TripPurpose;
  arrivalDeadline: string;
  optimizeFor: AutopilotTripRequest['optimizeFor'];
  autonomyMode: AutopilotMode;
  fallbackBatteryPercent: number;
  minimumArrivalBatteryPercent: number;
  maximumChargingBudget: number;
}

export interface VehicleRecommendationOption {
  vehicleId: number;
  vehicleName: string;
  registrationNumber: string;
  supportedConnectors: string[];
  batteryCapacityKwh: number;
  currentBatteryPercent: number;
  efficiencyWhPerKm: number;
  maximumChargingPowerKw: number;
  feasible: boolean;
  reason: string;
  compatibleChargersEvaluated: number;
  chargingStops: number;
  journeyMinutes: number;
  chargingMinutes: number;
  estimatedCost: number;
  arrivalBatteryPercent: number;
  withinBudget: boolean;
  deadlineFeasible: boolean;
}

export interface VehicleRecommendation {
  recommendedVehicleId?: number | null;
  recommendedVehicleName?: string | null;
  reason: string;
  origin: string;
  destination: string;
  optimizeFor: AutopilotTripRequest['optimizeFor'];
  recommendedPlan?: AutopilotPlan | null;
  vehicles: VehicleRecommendationOption[];
}

export interface JourneyIntent {
  origin?: string | null;
  destination?: string | null;
  currentBatteryPercent?: number | null;
  minimumArrivalBatteryPercent?: number | null;
  maximumChargingBudget?: number | null;
  arrivalDeadline?: string | null;
  optimizeFor?: AutopilotTripRequest['optimizeFor'] | null;
  autonomyMode?: AutopilotMode | null;
  tripPurpose?: TripPurpose | null;
  recognizedFields: string[];
}

export interface AutopilotPlanStop {
  sequenceNumber: number;
  stationId: number;
  stationName: string;
  stationAddress: string;
  connectorType: string;
  powerKw: number;
  effectivePowerKw: number;
  distanceFromOriginKm: number;
  routeOffsetKm: number;
  estimatedArrivalTime: string;
  arrivalBatteryPercent: number;
  targetBatteryPercent: number;
  estimatedWaitMinutes: number;
  chargingMinutes: number;
  connectionMinutes: number;
  estimatedCost: number;
  demoData: boolean;
  availableConnectors: number;
  queueCount: number;
  rating: number;
  selectionReason?: string;
}

export interface AutopilotPlan {
  vehicleId: number;
  vehicleName: string;
  registrationNumber: string;
  connectorType: string;
  origin: string;
  destination: string;
  arrivalDeadline?: string;
  estimatedArrivalTime: string;
  optimizeFor: AutopilotTripRequest['optimizeFor'];
  tripPurpose: TripPurpose;
  purposeSummary: string;
  pastExperiencesUsed: number;
  memorySummary: string;
  autonomyMode: AutopilotMode;
  currentBatteryPercent: number;
  minimumArrivalBatteryPercent: number;
  maximumChargingBudget: number;
  totalDistanceKm: number;
  baseRouteDistanceKm: number;
  chargingDetourDistanceKm: number;
  estimatedDriveMinutes: number;
  baseDriveMinutes: number;
  chargingDetourMinutes: number;
  estimatedChargingMinutes: number;
  estimatedQueueMinutes: number;
  connectionOverheadMinutes: number;
  totalDurationMinutes: number;
  estimatedChargingCost: number;
  budgetRemaining: number;
  estimatedArrivalBatteryPercent: number;
  batteryCapacityKwh: number;
  availableEnergyKwh: number;
  energyConsumptionKwhPer100Km: number;
  vehicleMaxChargingPowerKw: number;
  chargingEfficiencyPercent: number;
  compatibleChargersEvaluated: number;
  feasibleAlternativesCompared: number;
  optimizationSummary: string;
  routeEngine: string;
  withinBudget: boolean;
  safeArrivalReserve: boolean;
  deadlineFeasible: boolean;
  overallFeasible: boolean;
  deadlineMinutesLate: number;
  liveAvailabilityChecked: boolean;
  confirmationRequired: boolean;
  stops: AutopilotPlanStop[];
}

export interface AutopilotAction {
  sequenceNumber: number;
  state: AutopilotActionState;
  title: string;
  detail: string;
  timestamp: string;
}

export interface AutopilotTrip {
  recovery?: AutopilotRecovery | null;
  routeCoordinates?: number[][];
  id: number;
  idempotencyKey: string;
  goal: string;
  tripPurpose: TripPurpose;
  memorySummary?: string;
  origin: string;
  destination: string;
  arrivalDeadline?: string;
  optimizeFor: 'TIME' | 'COST' | 'BALANCED';
  autonomyMode: AutopilotMode;
  minimumArrivalBatteryPercent: number;
  maximumChargingBudget: number;
  totalDistanceKm: number;
  baseRouteDistanceKm: number;
  chargingDetourDistanceKm: number;
  estimatedDriveMinutes: number;
  baseDriveMinutes: number;
  chargingDetourMinutes: number;
  estimatedChargingMinutes: number;
  estimatedQueueMinutes: number;
  connectionOverheadMinutes: number;
  totalDurationMinutes: number;
  estimatedChargingCost: number;
  estimatedArrivalBatteryPercent: number;
  feasibleAlternativesCompared: number;
  optimizationSummary: string;
  routeEngine: string;
  activeStationId?: number;
  activeBookingId?: number;
  status: AutopilotTripStatus;
  paymentMessage?: string;
  walletBalance: number;
  telemetry: {
    latitude?: number;
    longitude?: number;
    positionRecordedAt?: string;
    positionSource?: string;
    distanceTravelledKm?: number;
    safeReachableDistanceKm?: number;
    vehicleId: number;
    vehicleName: string;
    registrationNumber: string;
    connectorType: string;
    batteryCapacityKwh: number;
    batteryPercent: number;
    remainingRangeKm: number;
    state: string;
  };
  stops: AutopilotStop[];
  timeline: AutopilotAction[];
  createdAt: string;
  updatedAt: string;
}

export interface AutopilotAgentResponse {
  sessionId: string;
  requestId: string;
  reply: string;
  model: string;
  toolCalls: Array<{ name: string; status: string }>;
  plan?: AutopilotPlan;
  actionResult?: AutopilotTrip;
}

function authorized(token: string): HeadersInit {
  return { Authorization: `Bearer ${token}` };
}

export function getAutopilotVehicles(token: string): Promise<AutopilotVehicle[]> {
  return apiRequest<AutopilotVehicle[]>('/ev/vehicles', {
    method: 'GET',
    headers: authorized(token),
  });
}

export function addAutopilotVehicle(
  token: string,
  vehicle: Omit<AutopilotVehicle, 'id' | 'userId'>,
): Promise<AutopilotVehicle> {
  return apiRequest<AutopilotVehicle>('/ev/vehicles', {
    method: 'POST',
    headers: authorized(token),
    body: JSON.stringify(vehicle),
  });
}

export function getCurrentAutopilotTrip(token: string): Promise<AutopilotTrip | null> {
  return apiRequest<AutopilotTrip | null>('/ev/autopilot/trips/current', {
    method: 'GET',
    headers: authorized(token),
  });
}

export function launchAutopilotTrip(token: string, request: AutopilotTripRequest): Promise<AutopilotTrip> {
  return apiRequest<AutopilotTrip>('/ev/autopilot/trips', {
    method: 'POST',
    headers: authorized(token),
    body: JSON.stringify(request),
  });
}

export function previewAutopilotTrip(token: string, request: AutopilotTripRequest): Promise<AutopilotPlan> {
  return apiRequest<AutopilotPlan>('/ev/autopilot/trips/preview', {
    method: 'POST',
    headers: authorized(token),
    body: JSON.stringify(request),
  });
}

export function recommendAutopilotVehicle(
  token: string,
  request: VehicleRecommendationRequest,
): Promise<VehicleRecommendation> {
  return apiRequest<VehicleRecommendation>('/ev/autopilot/vehicles/recommend', {
    method: 'POST',
    headers: authorized(token),
    body: JSON.stringify(request),
  });
}

export function parseAutopilotJourneyIntent(token: string, text: string): Promise<JourneyIntent> {
  return apiRequest<JourneyIntent>('/ev/autopilot/intent/parse', {
    method: 'POST',
    headers: authorized(token),
    body: JSON.stringify({ text }),
  });
}

export function startAutopilotTrip(token: string, tripId: number): Promise<AutopilotTrip> {
  return apiRequest<AutopilotTrip>(`/ev/autopilot/trips/${tripId}/start`, {
    method: 'POST',
    headers: authorized(token),
    body: JSON.stringify({ batteryDropPercent: 6 }),
  });
}

export function reportChargerIssue(
  token: string,
  tripId: number,
  reason: string = 'CHARGER_NOT_STARTING',
  comment?: string
): Promise<AutopilotTrip> {
  return apiRequest<AutopilotTrip>(`/ev/autopilot/trips/${tripId}/report-issue`, {
    method: 'POST',
    headers: authorized(token),
    body: JSON.stringify({ reason, comment }),
  });
}

export function simulateAutopilotFault(
  token: string,
  tripId: number,
  reason: string = 'CHARGER_NOT_STARTING',
  comment?: string
): Promise<AutopilotTrip> {
  return reportChargerIssue(token, tripId, reason, comment);
}

export function completeAutopilotCharging(token: string, tripId: number): Promise<AutopilotTrip> {
  return apiRequest<AutopilotTrip>(`/ev/autopilot/trips/${tripId}/complete-charging`, {
    method: 'POST',
    headers: authorized(token),
    body: '{}',
  });
}

export function approveAutopilotReroute(token: string, tripId: number, recovery: AutopilotRecovery): Promise<AutopilotTrip> {
  return apiRequest<AutopilotTrip>(`/ev/autopilot/trips/${tripId}/approve-reroute`, {
    method: 'POST',
    headers: authorized(token),
    body: JSON.stringify({ incidentId: recovery.incidentId, planId: recovery.planId }),
  });
}

export interface AutopilotRecovery {
  incidentId: string;
  planId?: string;
  state: 'INCIDENT_DETECTED' | 'CANDIDATES_READY' | 'NO_SAFE_RECOVERY_ROUTE' | 'PREPARED' | 'AWAITING_APPROVAL' | 'SUGGESTED' | 'EXECUTED';
  strategy?: 'DIRECT_NEXT_STOP' | 'DIRECT_DESTINATION' | 'BRIDGE_RECOVERY';
  reason?: string;
  agentProvider?: string;
  positionSource?: string;
  positionRecordedAt?: string;
  currentLatitude?: number;
  currentLongitude?: number;
  currentSoc: number;
  reserveSoc: number;
  batteryCapacityKwh?: number;
  efficiencyWhPerKm?: number;
  safeReachableDistanceKm?: number;
  failedConnectorId?: number;
  failedStationId: number;
  proposedStops?: AutopilotStop[];
  distanceToBridgeKm?: number;
  predictedArrivalSoc?: number;
  departureTargetSoc?: number;
  additionalDistanceKm?: number | null;
  additionalMinutes?: number | null;
  additionalCost?: number | null;
  newRemainingDistanceKm?: number;
  newRemainingMinutes?: number;
  remainingCost?: number;
  estimatedArrivalTime?: string;
  routeEngine?: string;
}

export async function runAutopilotRecovery(token: string, tripId: number, incidentId: string): Promise<AutopilotTrip> {
  const result = await apiRequest<{ journey: AutopilotTrip }>(`/ev/autopilot/trips/${tripId}/recovery/run`, {
    method: 'POST', headers: authorized(token), body: JSON.stringify({ incidentId }),
  });
  return result.journey;
}

export function refreshAutopilotRecovery(token: string, tripId: number, incidentId: string): Promise<AutopilotTrip> {
  return apiRequest(`/ev/autopilot/trips/${tripId}/recovery/refresh`, {
    method: 'POST', headers: authorized(token), body: JSON.stringify({ incidentId }),
  });
}

export function updateAutopilotPosition(token: string, tripId: number, position: { latitude: number; longitude: number; batteryPercent: number; recordedAt: string }): Promise<AutopilotTrip> {
  return apiRequest(`/ev/autopilot/trips/${tripId}/position`, {
    method: 'POST', headers: authorized(token), body: JSON.stringify(position),
  });
}

export function endAutopilotJourney(token: string, tripId: number, completed?: boolean): Promise<AutopilotTrip> {
  return apiRequest<AutopilotTrip>(`/ev/autopilot/trips/${tripId}/end`, {
    method: 'POST',
    headers: authorized(token),
    body: JSON.stringify({ completed: Boolean(completed) }),
  });
}

export function arriveAutopilotJourney(token: string, tripId: number): Promise<AutopilotTrip> {
  return apiRequest<AutopilotTrip>(`/ev/autopilot/trips/${tripId}/simulation/arrive`, {
    method: 'POST',
    headers: authorized(token),
  });
}

export function recordAutopilotExperience(
  token: string,
  tripId: number,
  payload: {
    stationId?: number;
    outcome: 'SUCCESS' | 'CHARGER_FAULT' | 'EXCESS_WAIT' | 'ACCESS_ISSUE' | 'PAYMENT_ISSUE' | 'USER_REPORTED';
    detail?: string;
    rating?: number;
    delayMinutes?: number;
  },
): Promise<unknown> {
  return apiRequest(`/ev/autopilot/trips/${tripId}/experience`, {
    method: 'POST',
    headers: authorized(token),
    body: JSON.stringify(payload),
  });
}

export async function topUpAutopilotWallet(token: string, amount: number): Promise<void> {
  await apiRequest('/ev/wallet/topup', {
    method: 'POST',
    headers: authorized(token),
    body: JSON.stringify({ amount, paymentMethod: 'DEMO_UPI' }),
  });
}

export function sendAutopilotAgentMessage(
  token: string,
  message: string,
  sessionId?: string,
  tripContext?: AutopilotTripRequest,
): Promise<AutopilotAgentResponse> {
  return apiRequest<AutopilotAgentResponse>('/ev/agent/chat', {
    method: 'POST',
    headers: authorized(token),
    body: JSON.stringify({
      message,
      sessionId: sessionId && sessionId.trim().length >= 8 ? sessionId.trim() : undefined,
      workspace: 'EV_OWNER',
      tripContext,
      requestId: `web-agent-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`,
    }),
  });
}
