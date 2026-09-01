import { PermissionsAndroid, Platform } from 'react-native';
import { BleManager, Device, State } from 'react-native-ble-plx';
import type { VehicleItem } from '../vehicles/vehicle.types';

export const VIDYUT_SERVICE_UUID = '0000feea-0000-1000-8000-00805f9b34fb';
export const VIDYUT_SOC_CHARACTERISTIC_UUID = '0000feeb-0000-1000-8000-00805f9b34fb';
let simulatedSoc = 42;

export interface BleDeviceSummary {
  id: string;
  name: string;
  signal: number;
  simulated: boolean;
  serviceUuid: string;
}

export interface BleAdapter {
  mode: 'hardware' | 'simulator' | 'unavailable';
  scan(): Promise<BleDeviceSummary[]>;
  connect(device: BleDeviceSummary): Promise<BleDeviceSummary>;
  readSoc(deviceId: string, serviceUuid?: string): Promise<number>;
  disconnect(deviceId: string): Promise<void>;
  destroy(): void;
}

class SimulatorBleAdapter implements BleAdapter {
  mode = 'simulator' as const;
  async scan() {
    return [
      { id: 'VIDYUT-SIM-EV-01', name: 'Tata Nexon EV · simulator', signal: -42, simulated: true, serviceUuid: VIDYUT_SERVICE_UUID },
      { id: 'VIDYUT-SIM-CHARGER-02', name: 'Vidyut charger beacon · simulator', signal: -58, simulated: true, serviceUuid: VIDYUT_SERVICE_UUID },
    ];
  }
  async connect(device: BleDeviceSummary) { return device; }
  async readSoc() { simulatedSoc = Math.min(100, simulatedSoc + 2); return simulatedSoc; }
  async disconnect() { return; }
  destroy() { return; }
}

class HardwareBleAdapter implements BleAdapter {
  mode = 'hardware' as const;
  private manager = new BleManager();

  private async permissions() {
    if (Platform.OS !== 'android') return true;
    if (Number(Platform.Version) >= 31) {
      const result = await PermissionsAndroid.requestMultiple([
        PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
        PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
      ]);
      return Object.values(result).every((value) => value === PermissionsAndroid.RESULTS.GRANTED);
    }
    return (await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION)) === PermissionsAndroid.RESULTS.GRANTED;
  }

  async scan(): Promise<BleDeviceSummary[]> {
    if (!(await this.permissions())) throw new Error('Bluetooth permission was denied. Simulator mode is still available.');
    const state = await this.manager.state();
    if (state !== State.PoweredOn) throw new Error('Turn on Bluetooth and try again.');
    return new Promise((resolve, reject) => {
      const devices = new Map<string, BleDeviceSummary>();
      const timeout = setTimeout(() => { this.manager.stopDeviceScan(); resolve([...devices.values()].sort((a, b) => b.signal - a.signal)); }, 8000);
      this.manager.startDeviceScan([VIDYUT_SERVICE_UUID], { allowDuplicates: false }, (error, device) => {
        if (error) { clearTimeout(timeout); this.manager.stopDeviceScan(); reject(error); return; }
        if (!device) return;
        devices.set(device.id, this.mapDevice(device));
      });
    });
  }

  async connect(device: BleDeviceSummary) {
    const connected = await this.manager.connectToDevice(device.id, { timeout: 10000 });
    await connected.discoverAllServicesAndCharacteristics();
    return this.mapDevice(connected);
  }

  async readSoc(deviceId: string, serviceUuid = VIDYUT_SERVICE_UUID) {
    let device = await this.manager.devices([deviceId]).then((items) => items[0]);
    if (!device) device = await this.manager.connectToDevice(deviceId, { timeout: 10000 });
    await device.discoverAllServicesAndCharacteristics();
    const characteristic = await device.readCharacteristicForService(serviceUuid, VIDYUT_SOC_CHARACTERISTIC_UUID);
    if (!characteristic.value) throw new Error('The EV did not publish a battery reading.');
    const decoded = globalThis.atob(characteristic.value).replace(/[^0-9.]/g, '');
    const value = Number(decoded);
    if (!Number.isFinite(value)) throw new Error('The EV battery reading was invalid.');
    return Math.max(0, Math.min(100, value));
  }

  async disconnect(deviceId: string) { await this.manager.cancelDeviceConnection(deviceId).catch(() => undefined); }
  destroy() { void this.manager.destroy(); }
  private mapDevice(device: Device): BleDeviceSummary {
    return { id: device.id, name: device.name || device.localName || 'Compatible Vidyut BLE device', signal: device.rssi ?? -100, simulated: false, serviceUuid: VIDYUT_SERVICE_UUID };
  }
}

class UnavailableBleAdapter implements BleAdapter {
  mode = 'unavailable' as const;
  async scan(): Promise<BleDeviceSummary[]> { throw new Error('Hardware Bluetooth needs a Vidyut development build. Turn on simulator mode for this demo.'); }
  async connect(): Promise<BleDeviceSummary> { throw new Error('Bluetooth hardware is unavailable.'); }
  async readSoc(): Promise<number> { throw new Error('Bluetooth hardware is unavailable.'); }
  async disconnect() { return; }
  destroy() { return; }
}

export function createBleAdapter(simulator: boolean): BleAdapter {
  if (simulator) return new SimulatorBleAdapter();
  try { return new HardwareBleAdapter(); } catch { return new UnavailableBleAdapter(); }
}

export async function readVehicleSoc(vehicle: VehicleItem): Promise<number> {
  const adapter = createBleAdapter(!!vehicle.btSimulatorEnabled);
  try {
    if (!vehicle.bluetoothDeviceId) throw new Error('Pair this vehicle before reading battery status.');
    return await adapter.readSoc(vehicle.bluetoothDeviceId, vehicle.bluetoothServiceUuid);
  } finally { adapter.destroy(); }
}
