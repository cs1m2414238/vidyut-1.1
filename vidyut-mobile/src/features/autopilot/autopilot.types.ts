export type AutopilotTripStatus =
  | 'RESERVED'
  | 'MONITORING'
  | 'REROUTED'
  | 'PAYMENT_REQUIRED'
  | 'COMPLETED'
  | 'CANCELLED';

export type AutopilotAutonomyMode =
  | 'RECOMMEND_ONLY'
  | 'ASK_BEFORE_ACTIONS'
  | 'FULL_AUTOPILOT';

export type AutopilotOptimization = 'TIME' | 'COST' | 'BALANCED';

export interface AutopilotStop {
  id: number;
  sequenceNumber: number;
  stationId: number;
  bookingId?: number;
  stationName: string;
  stationAddress: string;
  connectorType: string;
  powerKw: number;
  distanceFromOriginKm: number;
  arrivalBatteryPercent: number;
  targetBatteryPercent: number;
  estimatedWaitMinutes: number;
  chargingMinutes: number;
  estimatedCost: number;
  status: 'PLANNED' | 'RESERVED' | 'CANCELLED' | 'COMPLETED';
  timingScore?: 'HIGH' | 'MEDIUM' | 'LOW';
  timingLabel?: string;
}

export interface AutopilotPlanStop {
  sequenceNumber: number; stationId: number; stationName: string; stationAddress: string;
  connectorType: string; powerKw: number; distanceFromOriginKm: number;
  estimatedArrivalTime: string; predictedSlotFreeAt: string;
  timingScore: 'HIGH' | 'MEDIUM' | 'LOW'; timingLabel: string;
  arrivalBatteryPercent: number; targetBatteryPercent: number; estimatedWaitMinutes: number;
  chargingMinutes: number; estimatedCost: number; availableConnectors: number; queueCount: number;
  rating: number; selectionReason: string;
}

export interface AutopilotPlan {
  vehicleId: number; vehicleName: string; registrationNumber: string; connectorType: string;
  origin: string; destination: string; arrivalDeadline?: string; estimatedArrivalTime: string;
  optimizeFor: AutopilotOptimization; autonomyMode: AutopilotAutonomyMode; currentBatteryPercent: number;
  minimumArrivalBatteryPercent: number; maximumChargingBudget: number; totalDistanceKm: number;
  estimatedDriveMinutes: number; totalDurationMinutes: number; estimatedChargingCost: number;
  budgetRemaining: number; estimatedArrivalBatteryPercent: number; compatibleChargersEvaluated: number;
  withinBudget: boolean; safeArrivalReserve: boolean; liveAvailabilityChecked: boolean;
  confirmationRequired: boolean; stops: AutopilotPlanStop[];
}

export interface AutopilotAlternative {
  station: { id: number; name: string; address: string; pricePerKwh: number; connectorType?: string };
  distanceFromOriginKm: number; recommendedChargeMinutes: number; detourKm: number;
  etaMinutes: number; availableSlots: number; connectorMatched: boolean; estimatedChargingCost: number; reason: string;
}

export interface AutopilotTripSummary {
  tripId: number; origin: string; destination: string; totalKm: number; totalMinutes: number;
  chargingMinutes: number; stopsTaken: number; totalCost: number; co2SavedKg: number; shareText: string;
}

export interface AutopilotAction {
  sequenceNumber: number;
  state: 'SUCCESS' | 'INFO' | 'WARNING';
  title: string;
  detail: string;
  timestamp: string;
}

export interface AutopilotTrip {
  id: number;
  goal: string;
  origin: string;
  destination: string;
  arrivalDeadline?: string;
  optimizeFor: AutopilotOptimization;
  autonomyMode?: AutopilotAutonomyMode;
  minimumArrivalBatteryPercent: number;
  maximumChargingBudget: number;
  totalDistanceKm: number;
  estimatedDriveMinutes: number;
  totalDurationMinutes: number;
  estimatedChargingCost: number;
  estimatedArrivalBatteryPercent: number;
  activeBookingId?: number;
  status: AutopilotTripStatus;
  paymentMessage?: string;
  walletBalance: number;
  telemetry: {
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
}

export interface LaunchAutopilotTripRequest {
  vehicleId: number;
  origin: string;
  destination: string;
  goal: string;
  arrivalDeadline: string;
  optimizeFor: AutopilotOptimization;
  currentBatteryPercent: number;
  minimumArrivalBatteryPercent: number;
  maximumChargingBudget: number;
  idempotencyKey: string;
  autonomyMode?: AutopilotAutonomyMode;
}
