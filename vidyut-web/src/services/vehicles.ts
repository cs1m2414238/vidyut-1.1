import { apiRequest } from './api';

export type VehicleConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'UNKNOWN';
export type VehicleTelemetrySource = 'BLUETOOTH' | 'BLUETOOTH_DEMO' | 'MANUAL' | 'CHARGING_SESSION' | 'NOT_AVAILABLE';

export interface Vehicle {
  id: number;
  userId: number;
  makeAndModel: string;
  registrationNumber: string;
  batteryCapacity?: string | null;
  connectorType?: string | null;
  supportedConnectors?: string[];
  efficiencyWhPerKm?: number | null;
  maxAcChargePowerKw?: number | null;
  maxDcChargePowerKw?: number | null;
  connectionStatus: VehicleConnectionStatus;
  batteryPercent?: number | null;
  remainingRangeKm?: number | null;
  charging?: boolean | null;
  bluetoothSupported?: boolean | null;
  androidAutoSupported?: boolean | null;
  appleCarPlaySupported?: boolean | null;
  bluetoothDeviceName?: string | null;
  lastChargingStation?: string | null;
  lastChargingAddress?: string | null;
  lastChargedAt?: string | null;
  telemetrySource: VehicleTelemetrySource;
  telemetryUpdatedAt?: string | null;
}

export interface VehicleCreatePayload {
  makeAndModel: string;
  registrationNumber: string;
  batteryCapacity?: string;
  connectorType: string;
  supportedConnectors?: string[];
  efficiencyWhPerKm?: number;
  maxAcChargePowerKw?: number;
  maxDcChargePowerKw?: number;
}

export interface VehicleUpdatePayload {
  batteryPercent?: number;
  remainingRangeKm?: number;
  connectionStatus?: VehicleConnectionStatus;
  charging?: boolean;
  bluetoothSupported?: boolean;
  androidAutoSupported?: boolean;
  appleCarPlaySupported?: boolean;
  bluetoothDeviceName?: string;
  lastChargingStation?: string;
  lastChargingAddress?: string;
  lastChargedAt?: string;
  telemetrySource?: VehicleTelemetrySource;
}

function authorized(token: string): HeadersInit {
  return { Authorization: `Bearer ${token}` };
}

export function getVehicles(token: string): Promise<Vehicle[]> {
  return apiRequest<Vehicle[]>('/ev/vehicles', { method: 'GET', headers: authorized(token) });
}

export function getVehicle(token: string, vehicleId: number): Promise<Vehicle> {
  return apiRequest<Vehicle>(`/ev/vehicles/${vehicleId}`, { method: 'GET', headers: authorized(token) });
}

export function addVehicle(token: string, payload: VehicleCreatePayload): Promise<Vehicle> {
  return apiRequest<Vehicle>('/ev/vehicles', {
    method: 'POST',
    headers: authorized(token),
    body: JSON.stringify(payload),
  });
}

export function updateVehicle(token: string, vehicleId: number, payload: VehicleUpdatePayload): Promise<Vehicle> {
  return apiRequest<Vehicle>(`/ev/vehicles/${vehicleId}`, {
    method: 'PATCH',
    headers: authorized(token),
    body: JSON.stringify(payload),
  });
}

export async function deleteVehicle(token: string, vehicleId: number): Promise<void> {
  await apiRequest<null>(`/ev/vehicles/${vehicleId}`, {
    method: 'DELETE',
    headers: authorized(token),
  });
}
