export interface VehicleItem {
  id: number;
  userId: number;
  makeAndModel: string;
  registrationNumber: string;
  batteryCapacity?: string;
  connectorType?: string;
  connectionStatus?: 'CONNECTED' | 'DISCONNECTED' | 'UNKNOWN';
  batteryPercent?: number;
  remainingRangeKm?: number;
  charging?: boolean;
  bluetoothSupported?: boolean;
  androidAutoSupported?: boolean;
  appleCarPlaySupported?: boolean;
  bluetoothDeviceName?: string;
  bluetoothDeviceId?: string;
  bluetoothServiceUuid?: string;
  btSessionControlEnabled?: boolean;
  btSimulatorEnabled?: boolean;
  lastChargingStation?: string;
  lastChargingAddress?: string;
  lastChargedAt?: string;
  telemetrySource?: string;
  telemetryUpdatedAt?: string;
}

export interface CreateVehicleRequest {
  makeAndModel: string;
  registrationNumber: string;
  batteryCapacity?: string;
  connectorType?: string;
}

export type UpdateVehicleRequest = Partial<Omit<VehicleItem, 'id' | 'userId'>>;
