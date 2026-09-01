import { useCallback, useEffect, useRef, useState } from 'react';
import type { CSSProperties } from 'react';
import {
  ArrowLeft,
  BatteryCharging,
  Bluetooth,
  Cable,
  CarFront,
  CheckCircle2,
  CircleAlert,
  Clock3,
  FlaskConical,
  Gauge,
  MapPin,
  PencilLine,
  Radio,
  RefreshCw,
  Route,
  Smartphone,
  Unplug,
  Wallet,
  X,
  Zap,
} from 'lucide-react';
import { getVehicle, updateVehicle } from '../services/vehicles';
import type { Vehicle, VehicleConnectionStatus, VehicleUpdatePayload } from '../services/vehicles';
import './VehicleDetailView.css';

interface VehicleDetailViewProps {
  token: string;
  vehicleId: number;
  onBack: () => void;
  onFindChargers: () => void;
  onOpenWallet: () => void;
  onVehicleUpdated?: (vehicle: Vehicle) => void;
}

interface BluetoothCharacteristicLike {
  value?: DataView | null;
  readValue(): Promise<DataView>;
  startNotifications?(): Promise<BluetoothCharacteristicLike>;
  stopNotifications?(): Promise<BluetoothCharacteristicLike>;
  addEventListener?(type: 'characteristicvaluechanged', listener: EventListener): void;
  removeEventListener?(type: 'characteristicvaluechanged', listener: EventListener): void;
}

interface BluetoothRemoteGattServerLike {
  getPrimaryService(service: string): Promise<{
    getCharacteristic(characteristic: string): Promise<BluetoothCharacteristicLike>;
  }>;
}

interface BluetoothDeviceLike {
  name?: string;
  addEventListener?(type: 'gattserverdisconnected', listener: EventListener): void;
  removeEventListener?(type: 'gattserverdisconnected', listener: EventListener): void;
  gatt?: {
    connected: boolean;
    connect(): Promise<BluetoothRemoteGattServerLike>;
    disconnect(): void;
  };
}

interface BluetoothApiLike {
  requestDevice(options: { acceptAllDevices: true; optionalServices: string[] }): Promise<BluetoothDeviceLike>;
}

type SupportChoice = 'UNKNOWN' | 'YES' | 'NO';
type ChargingChoice = 'UNKNOWN' | 'CHARGING' | 'NOT_CHARGING';
type NoticeTone = 'success' | 'info';

const DEMO_ENERGY_PER_KM_KWH = 0.12;
const DEFAULT_DEMO_BATTERY_CAPACITY_KWH = 40.5;

interface TelemetryForm {
  batteryPercent: string;
  remainingRangeKm: string;
  connectionStatus: VehicleConnectionStatus;
  charging: ChargingChoice;
  bluetoothSupported: SupportChoice;
  androidAutoSupported: SupportChoice;
  appleCarPlaySupported: SupportChoice;
  lastChargingStation: string;
  lastChargingAddress: string;
  lastChargedAt: string;
}

function toSupportChoice(value?: boolean | null): SupportChoice {
  return value == null ? 'UNKNOWN' : value ? 'YES' : 'NO';
}

function toDateTimeInput(value?: string | null): string {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.slice(0, 16);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

function formFromVehicle(vehicle: Vehicle): TelemetryForm {
  return {
    batteryPercent: vehicle.batteryPercent == null ? '' : String(vehicle.batteryPercent),
    remainingRangeKm: vehicle.remainingRangeKm == null ? '' : String(vehicle.remainingRangeKm),
    connectionStatus: vehicle.connectionStatus,
    charging: vehicle.charging == null ? 'UNKNOWN' : vehicle.charging ? 'CHARGING' : 'NOT_CHARGING',
    bluetoothSupported: toSupportChoice(vehicle.bluetoothSupported),
    androidAutoSupported: toSupportChoice(vehicle.androidAutoSupported),
    appleCarPlaySupported: toSupportChoice(vehicle.appleCarPlaySupported),
    lastChargingStation: vehicle.lastChargingStation || '',
    lastChargingAddress: vehicle.lastChargingAddress || '',
    lastChargedAt: toDateTimeInput(vehicle.lastChargedAt),
  };
}

function formatDateTime(value?: string | null): string {
  if (!value) return 'Not reported yet';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(date);
}

function sourceLabel(vehicle: Vehicle): string {
  if (vehicle.telemetrySource === 'BLUETOOTH_DEMO') return 'Bluetooth demo telemetry';
  if (vehicle.telemetrySource === 'BLUETOOTH') return 'Bluetooth reading';
  if (vehicle.telemetrySource === 'CHARGING_SESSION') return 'Charging session';
  if (vehicle.telemetrySource === 'MANUAL') return 'Manual update';
  return 'No live source';
}

function capabilityLabel(value?: boolean | null): string {
  return value == null ? 'Not specified' : value ? 'Supported' : 'Not supported';
}

function supportValue(value: SupportChoice): boolean | undefined {
  return value === 'UNKNOWN' ? undefined : value === 'YES';
}

function batteryCapacityKwh(value?: string | null): number {
  const parsed = Number(value?.replace(',', '.').match(/\d+(?:\.\d+)?/)?.[0]);
  return Number.isFinite(parsed) && parsed > 10 && parsed < 250
    ? parsed
    : DEFAULT_DEMO_BATTERY_CAPACITY_KWH;
}

function demoRangeKm(vehicle: Vehicle, batteryPercent: number): number {
  const capacity = batteryCapacityKwh(vehicle.batteryCapacity);
  return Math.round((capacity * batteryPercent / 100 / DEMO_ENERGY_PER_KM_KWH) * 10) / 10;
}

function normalizedBattery(value: DataView): number {
  if (value.byteLength < 1) throw new Error('The Bluetooth battery response was empty.');
  return Math.max(0, Math.min(100, value.getUint8(0)));
}

function bluetoothErrorMessage(error: Error): string {
  if (error.name === 'SecurityError') return 'Bluetooth permission was blocked. Allow Bluetooth access for this site and try again.';
  if (error.name === 'NetworkError') return 'The device was found, but its Bluetooth connection failed. Disconnect it from another app and retry.';
  if (error.name === 'NotSupportedError') return 'The selected device does not provide a compatible Bluetooth GATT connection.';
  return error.message || 'Unable to connect to the selected Bluetooth device.';
}

export function VehicleDetailView({
  token,
  vehicleId,
  onBack,
  onFindChargers,
  onOpenWallet,
  onVehicleUpdated,
}: VehicleDetailViewProps) {
  const [vehicle, setVehicle] = useState<Vehicle | null>(null);
  const [form, setForm] = useState<TelemetryForm | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [liveBluetooth, setLiveBluetooth] = useState(false);
  const [showEditor, setShowEditor] = useState(false);
  const [showBluetoothConsent, setShowBluetoothConsent] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [noticeTone, setNoticeTone] = useState<NoticeTone>('success');
  const bluetoothDevice = useRef<BluetoothDeviceLike | null>(null);
  const bluetoothCharacteristic = useRef<BluetoothCharacteristicLike | null>(null);
  const bluetoothValueHandler = useRef<EventListener | null>(null);
  const bluetoothDisconnectHandler = useRef<EventListener | null>(null);

  const loadVehicle = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await getVehicle(token, vehicleId);
      setVehicle(data);
      setForm(formFromVehicle(data));
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Unable to load this vehicle.');
    } finally {
      setLoading(false);
    }
  }, [token, vehicleId]);

  useEffect(() => {
    void loadVehicle();
  }, [loadVehicle]);

  useEffect(() => {
    if (!showEditor && !showBluetoothConsent) return undefined;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      if (showEditor && !saving) setShowEditor(false);
      if (showBluetoothConsent && !syncing) setShowBluetoothConsent(false);
    };
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [showBluetoothConsent, showEditor, saving, syncing]);

  useEffect(() => () => {
    const characteristic = bluetoothCharacteristic.current;
    const valueHandler = bluetoothValueHandler.current;
    if (characteristic && valueHandler) {
      characteristic.removeEventListener?.('characteristicvaluechanged', valueHandler);
      void characteristic.stopNotifications?.().catch(() => undefined);
    }

    const device = bluetoothDevice.current;
    const disconnectHandler = bluetoothDisconnectHandler.current;
    if (device && disconnectHandler) {
      device.removeEventListener?.('gattserverdisconnected', disconnectHandler);
    }
    if (device?.gatt?.connected) {
      device.gatt.disconnect();
      void updateVehicle(token, vehicleId, {
        connectionStatus: 'DISCONNECTED',
        telemetrySource: 'BLUETOOTH_DEMO',
      }).catch(() => undefined);
    }
  }, [token, vehicleId]);

  const applyUpdate = (updated: Vehicle) => {
    setVehicle(updated);
    setForm(formFromVehicle(updated));
    onVehicleUpdated?.(updated);
  };

  const openEditor = () => {
    if (!vehicle) return;
    setForm(formFromVehicle(vehicle));
    setError('');
    setNotice('');
    setShowEditor(true);
  };

  const showNotice = (message: string, tone: NoticeTone = 'success') => {
    setNoticeTone(tone);
    setNotice(message);
  };

  const saveTelemetry = async () => {
    if (!form) return;
    const batteryPercent = form.batteryPercent === '' ? undefined : Number(form.batteryPercent);
    const remainingRangeKm = form.remainingRangeKm === '' ? undefined : Number(form.remainingRangeKm);
    if (batteryPercent != null && (!Number.isFinite(batteryPercent) || batteryPercent < 0 || batteryPercent > 100)) {
      setError('Battery level must be between 0 and 100.');
      return;
    }
    if (remainingRangeKm != null && (!Number.isFinite(remainingRangeKm) || remainingRangeKm < 0)) {
      setError('Remaining range cannot be negative.');
      return;
    }

    const payload: VehicleUpdatePayload = {
      batteryPercent,
      remainingRangeKm,
      connectionStatus: form.connectionStatus,
      charging: form.charging === 'UNKNOWN' ? undefined : form.charging === 'CHARGING',
      bluetoothSupported: supportValue(form.bluetoothSupported),
      androidAutoSupported: supportValue(form.androidAutoSupported),
      appleCarPlaySupported: supportValue(form.appleCarPlaySupported),
      lastChargingStation: form.lastChargingStation.trim() || undefined,
      lastChargingAddress: form.lastChargingAddress.trim() || undefined,
      lastChargedAt: form.lastChargedAt ? `${form.lastChargedAt}:00` : undefined,
      telemetrySource: 'MANUAL',
    };

    setSaving(true);
    setError('');
    try {
      const updated = await updateVehicle(token, vehicleId, payload);
      applyUpdate(updated);
      setShowEditor(false);
      showNotice('Vehicle status and capabilities were updated.');
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Unable to update this vehicle.');
    } finally {
      setSaving(false);
    }
  };

  const saveBluetoothReading = async (deviceName: string, batteryPercent: number) => {
    if (!vehicle) return;
    const updated = await updateVehicle(token, vehicleId, {
      batteryPercent,
      remainingRangeKm: demoRangeKm(vehicle, batteryPercent),
      connectionStatus: 'CONNECTED',
      bluetoothSupported: true,
      bluetoothDeviceName: deviceName,
      telemetrySource: 'BLUETOOTH_DEMO',
    });
    applyUpdate(updated);
  };

  const syncBluetooth = async () => {
    if (!vehicle) return;
    const bluetooth = (navigator as Navigator & { bluetooth?: BluetoothApiLike }).bluetooth;
    setError('');
    setNotice('');
    if (!window.isSecureContext) {
      setError('Bluetooth sync requires a secure HTTPS connection or localhost.');
      return;
    }
    if (!bluetooth) {
      setError('Web Bluetooth is not available in this browser. Use a compatible Android/desktop browser or update the data manually.');
      return;
    }

    setSyncing(true);
    try {
      const device = await bluetooth.requestDevice({
        acceptAllDevices: true,
        optionalServices: ['battery_service'],
      });
      bluetoothDevice.current = device;
      if (!device.gatt) throw new Error('The selected device does not expose a Bluetooth data connection.');

      const disconnectHandler: EventListener = () => {
        if (bluetoothDevice.current !== device) return;
        bluetoothDevice.current = null;
        bluetoothCharacteristic.current = null;
        bluetoothValueHandler.current = null;
        bluetoothDisconnectHandler.current = null;
        setLiveBluetooth(false);
        void updateVehicle(token, vehicleId, {
          connectionStatus: 'DISCONNECTED',
          telemetrySource: 'BLUETOOTH_DEMO',
        }).then((updated) => {
          applyUpdate(updated);
          showNotice(`${device.name || 'The demo device'} disconnected. The last battery reading is still saved.`, 'info');
        }).catch(() => {
          setError('The Bluetooth device disconnected, but Vidyut could not save the offline status.');
        });
      };
      device.addEventListener?.('gattserverdisconnected', disconnectHandler);
      bluetoothDisconnectHandler.current = disconnectHandler;

      const server = await device.gatt.connect();
      const deviceName = device.name || 'Bluetooth demo device';
      const payload: VehicleUpdatePayload = {
        connectionStatus: 'CONNECTED',
        bluetoothSupported: true,
        bluetoothDeviceName: deviceName,
        telemetrySource: 'BLUETOOTH_DEMO',
      };

      let batteryRead = false;
      let notificationsStarted = false;
      try {
        const service = await server.getPrimaryService('battery_service');
        const characteristic = await service.getCharacteristic('battery_level');
        bluetoothCharacteristic.current = characteristic;
        const value = await characteristic.readValue();
        payload.batteryPercent = normalizedBattery(value);
        payload.remainingRangeKm = demoRangeKm(vehicle, payload.batteryPercent);
        batteryRead = true;

        if (characteristic.startNotifications && characteristic.addEventListener) {
          const valueHandler: EventListener = (event) => {
            const changedCharacteristic = event.target as BluetoothCharacteristicLike | null;
            if (!changedCharacteristic?.value) return;
            try {
              const nextBattery = normalizedBattery(changedCharacteristic.value);
              void saveBluetoothReading(deviceName, nextBattery).then(() => {
                showNotice(`Live demo telemetry updated to ${nextBattery}% (${demoRangeKm(vehicle, nextBattery)} km estimated range).`);
              }).catch(() => {
                setError('A new Bluetooth reading arrived, but it could not be saved.');
              });
            } catch (notificationError) {
              setError(notificationError instanceof Error ? notificationError.message : 'The live Bluetooth reading was invalid.');
            }
          };
          characteristic.addEventListener('characteristicvaluechanged', valueHandler);
          bluetoothValueHandler.current = valueHandler;
          try {
            await characteristic.startNotifications();
            notificationsStarted = true;
            setLiveBluetooth(true);
          } catch {
            characteristic.removeEventListener?.('characteristicvaluechanged', valueHandler);
            bluetoothValueHandler.current = null;
          }
        }
      } catch {
        // Windows may know a vendor-specific battery value that Web Bluetooth cannot expose.
      }

      const updated = await updateVehicle(token, vehicleId, payload);
      applyUpdate(updated);
      showNotice(batteryRead
        ? `${deviceName} is supplying demo EV telemetry${notificationsStarted ? ' with live updates' : ''}. Battery ${payload.batteryPercent}% maps to ${payload.remainingRangeKm} km estimated range.`
        : `${deviceName} connected, but it does not expose the standard Bluetooth Battery Service to this browser. Windows can still show a vendor-specific value that Chrome cannot read.`,
      batteryRead ? 'success' : 'info');
    } catch (syncError) {
      const bluetoothError = syncError as Error;
      if (bluetoothError.name === 'NotFoundError') {
        showNotice('Bluetooth selection was cancelled. No vehicle data was changed.', 'info');
      } else {
        const characteristic = bluetoothCharacteristic.current;
        const valueHandler = bluetoothValueHandler.current;
        if (characteristic && valueHandler) {
          characteristic.removeEventListener?.('characteristicvaluechanged', valueHandler);
          void characteristic.stopNotifications?.().catch(() => undefined);
        }
        const device = bluetoothDevice.current;
        const disconnectHandler = bluetoothDisconnectHandler.current;
        if (device && disconnectHandler) {
          device.removeEventListener?.('gattserverdisconnected', disconnectHandler);
        }
        device?.gatt?.disconnect();
        bluetoothDevice.current = null;
        bluetoothCharacteristic.current = null;
        bluetoothValueHandler.current = null;
        bluetoothDisconnectHandler.current = null;
        setLiveBluetooth(false);
        setError(bluetoothErrorMessage(bluetoothError));
      }
    } finally {
      setSyncing(false);
    }
  };

  const disconnectBluetooth = async () => {
    setSyncing(true);
    setError('');
    try {
      const characteristic = bluetoothCharacteristic.current;
      const valueHandler = bluetoothValueHandler.current;
      if (characteristic && valueHandler) {
        characteristic.removeEventListener?.('characteristicvaluechanged', valueHandler);
        await characteristic.stopNotifications?.().catch(() => undefined);
      }

      const device = bluetoothDevice.current;
      const disconnectHandler = bluetoothDisconnectHandler.current;
      if (device && disconnectHandler) {
        device.removeEventListener?.('gattserverdisconnected', disconnectHandler);
      }
      device?.gatt?.disconnect();
      bluetoothDevice.current = null;
      bluetoothCharacteristic.current = null;
      bluetoothValueHandler.current = null;
      bluetoothDisconnectHandler.current = null;
      setLiveBluetooth(false);
      const updated = await updateVehicle(token, vehicleId, {
        connectionStatus: 'DISCONNECTED',
        telemetrySource: 'BLUETOOTH_DEMO',
      });
      applyUpdate(updated);
      showNotice('Bluetooth demo telemetry was disconnected. Your last reading remains saved.', 'info');
    } catch (disconnectError) {
      setError(disconnectError instanceof Error ? disconnectError.message : 'Unable to update the connection status.');
    } finally {
      setSyncing(false);
    }
  };

  const refreshBluetoothReading = async () => {
    if (!vehicle) return;
    const characteristic = bluetoothCharacteristic.current;
    const device = bluetoothDevice.current;
    if (!characteristic || !device?.gatt?.connected) {
      setShowBluetoothConsent(true);
      return;
    }

    setSyncing(true);
    setError('');
    setNotice('');
    try {
      const batteryPercent = normalizedBattery(await characteristic.readValue());
      await saveBluetoothReading(device.name || 'Bluetooth demo device', batteryPercent);
      showNotice(`Demo telemetry refreshed: ${batteryPercent}% battery and ${demoRangeKm(vehicle, batteryPercent)} km estimated range.`);
    } catch (refreshError) {
      setError(refreshError instanceof Error ? refreshError.message : 'Unable to refresh the Bluetooth battery reading.');
    } finally {
      setSyncing(false);
    }
  };

  if (loading) {
    return (
      <section className="vehicle-detail-page vehicle-detail-loading" aria-live="polite">
        <RefreshCw className="spinning" size={25} />


        <strong>Loading vehicle data…</strong>
      </section>
    );
  }

  if (!vehicle) {
    return (
      <section className="vehicle-detail-page vehicle-detail-missing">
        <CircleAlert size={35} />
        <h1>Vehicle unavailable</h1>
        <p>{error || 'This vehicle could not be found in your account.'}</p>
        <button type="button" className="vehicle-secondary-button" onClick={onBack}><ArrowLeft size={16} /> Back to my vehicles</button>
      </section>
    );
  }

  const battery = vehicle.batteryPercent == null ? null : Math.max(0, Math.min(100, vehicle.batteryPercent));
  const connectionClass = vehicle.connectionStatus.toLowerCase();
  const chargeLabel = vehicle.charging == null ? 'Not reported' : vehicle.charging ? 'Charging' : 'Not charging';
  const ringStyle = { '--vehicle-battery-level': `${battery ?? 0}%` } as CSSProperties;
  const isDemoTelemetry = vehicle.telemetrySource === 'BLUETOOTH_DEMO' || vehicle.registrationNumber?.startsWith('DEMO-') || vehicle.registrationNumber?.startsWith('PRI-');
  const canRefreshBluetooth = Boolean(bluetoothCharacteristic.current && bluetoothDevice.current?.gatt?.connected);

  return (
    <section className="vehicle-detail-page" aria-labelledby="vehicle-detail-title">
      <button type="button" className="vehicle-detail-back" onClick={onBack}><ArrowLeft size={16} /> My vehicles</button>

      <header className="vehicle-detail-header">
        <div className="vehicle-detail-title-wrap">
          <span className="vehicle-detail-title-icon"><CarFront size={26} /></span>
          <div>
            <span>{vehicle.registrationNumber}</span>
            <h1 id="vehicle-detail-title">{vehicle.makeAndModel}</h1>
            <p>{sourceLabel(vehicle)} · Updated {formatDateTime(vehicle.telemetryUpdatedAt)}</p>
            {isDemoTelemetry && <span className="vehicle-demo-source"><FlaskConical size={12} /> Demo source—not vehicle hardware</span>}
          </div>
        </div>
        <div className="vehicle-detail-header-actions">
          <span className={`vehicle-detail-status ${connectionClass}`}><i />{isDemoTelemetry ? 'Demo telemetry' : vehicle.connectionStatus === 'CONNECTED' ? 'Connected' : vehicle.connectionStatus === 'DISCONNECTED' ? 'Offline' : 'Not synced'}</span>
          <button type="button" className="vehicle-secondary-button" onClick={openEditor}><PencilLine size={15} /> Update data</button>
        </div>
      </header>

      {error && <div className="vehicle-detail-message error" role="alert"><CircleAlert size={17} />{error}</div>}
      {notice && <div className={`vehicle-detail-message ${noticeTone}`} role="status">{noticeTone === 'success' ? <CheckCircle2 size={17} /> : <CircleAlert size={17} />}{notice}</div>}

      <div className="vehicle-detail-metrics">
        <article>
          <span><Bluetooth size={19} /></span>
          <small>Connection</small>
          <strong>{isDemoTelemetry ? 'Demo telemetry' : vehicle.connectionStatus === 'CONNECTED' ? 'Connected' : vehicle.connectionStatus === 'DISCONNECTED' ? 'Offline' : 'Not synced'}</strong>
          <p>{vehicle.bluetoothDeviceName || 'No device name saved'}{isDemoTelemetry ? ' · Demo' : ''}</p>
        </article>
        <article>
          <span><BatteryCharging size={19} /></span>
          <small>Battery remaining</small>
          <strong>{battery == null ? 'Not synced' : `${battery}%`}</strong>
          <p>{sourceLabel(vehicle)}</p>
        </article>
        <article>
          <span><Route size={19} /></span>
          <small>Estimated range</small>
          <strong>{vehicle.remainingRangeKm == null ? 'Not synced' : `${Math.round(vehicle.remainingRangeKm)} km`}</strong>
          <p>{isDemoTelemetry ? 'Demo estimate at 0.12 kWh/km' : 'Remaining driving estimate'}</p>
        </article>
        <article className={vehicle.charging ? 'is-charging' : ''}>
          <span><Zap size={19} /></span>
          <small>Charge status</small>
          <strong>{chargeLabel}</strong>
          <p>{vehicle.charging ? 'Energy is being received' : vehicle.charging === false ? 'Vehicle is not drawing power' : 'Awaiting a reading'}</p>
        </article>
      </div>

      <div className="vehicle-detail-grid">
        <article className={`vehicle-live-card ${vehicle.charging ? 'charging' : ''}`}>
          <div className="vehicle-live-copy">
            <span className="vehicle-live-eyebrow">{isDemoTelemetry ? 'BLUETOOTH DEMO ADAPTER' : 'CURRENT VEHICLE STATE'}</span>
            <h2>{isDemoTelemetry ? 'Demo telemetry active' : vehicle.charging ? 'Charging now' : vehicle.connectionStatus === 'CONNECTED' ? 'Vehicle connected' : 'Ready when you are'}</h2>
            <p>{isDemoTelemetry
              ? `${vehicle.bluetoothDeviceName || 'The selected Bluetooth device'} supplies a demonstration battery value. It is not direct EV hardware telemetry.`
              : vehicle.charging
              ? 'This status is based on the latest saved vehicle reading.'
              : 'Connect by Bluetooth or update the status to keep the dashboard accurate.'}</p>
            {isDemoTelemetry && liveBluetooth && <span className="vehicle-live-indicator"><Radio size={13} /> Listening for live battery changes</span>}
            <div className="vehicle-live-actions">
              <button type="button" onClick={() => canRefreshBluetooth ? void refreshBluetoothReading() : setShowBluetoothConsent(true)} disabled={syncing}><Bluetooth size={16} />{syncing ? 'Connecting…' : canRefreshBluetooth ? 'Refresh demo reading' : 'Connect demo device'}</button>
              {vehicle.connectionStatus === 'CONNECTED' && (
                <button type="button" className="ghost" onClick={() => void disconnectBluetooth()} disabled={syncing}><Unplug size={16} /> Disconnect</button>
              )}
            </div>
          </div>
          <div className="vehicle-battery-ring" style={ringStyle}>
            <div>
              <BatteryCharging size={25} />
              <strong>{battery == null ? '—' : `${battery}%`}</strong>
              <span>{battery == null ? 'Not synced' : isDemoTelemetry ? 'Demo battery' : 'Remaining'}</span>
            </div>
          </div>
        </article>

        <article className="vehicle-info-card vehicle-last-charge-card">
          <div className="vehicle-info-card-head">
            <span><MapPin size={18} /></span>
            <div><h2>Last charge</h2><p>The most recent refill saved for this vehicle</p></div>
          </div>
          {vehicle.lastChargingStation || vehicle.lastChargedAt ? (
            <div className="vehicle-last-charge-content">
              <strong>{vehicle.lastChargingStation || 'Charging location not named'}</strong>
              <p><MapPin size={14} />{vehicle.lastChargingAddress || 'Address not recorded'}</p>
              <p><Clock3 size={14} />{formatDateTime(vehicle.lastChargedAt)}</p>
            </div>
          ) : (
            <div className="vehicle-info-empty">
              <Zap size={24} />
              <strong>No charge recorded yet</strong>
              <p>Add the last station manually; a linked charging session can supply it later.</p>
            </div>
          )}
        </article>

        <article className="vehicle-info-card vehicle-capabilities-card">
          <div className="vehicle-info-card-head">
            <span><Smartphone size={18} /></span>
            <div><h2>Connectivity & infotainment</h2><p>Capabilities saved for this model</p></div>
          </div>
          <ul className="vehicle-capability-list">
            <li><span><Bluetooth size={17} /> Bluetooth telemetry</span><strong className={vehicle.bluetoothSupported ? 'supported' : ''}>{capabilityLabel(vehicle.bluetoothSupported)}</strong></li>
            <li><span><Smartphone size={17} /> Android Auto</span><strong className={vehicle.androidAutoSupported ? 'supported' : ''}>{capabilityLabel(vehicle.androidAutoSupported)}</strong></li>
            <li><span><Smartphone size={17} /> Apple CarPlay</span><strong className={vehicle.appleCarPlaySupported ? 'supported' : ''}>{capabilityLabel(vehicle.appleCarPlaySupported)}</strong></li>
          </ul>
          <p className="vehicle-capability-note"><CircleAlert size={14} /> Android Auto and Apple CarPlay cannot be detected by a web page; these are owner-confirmed capabilities.</p>
        </article>

        <article className="vehicle-info-card vehicle-spec-card">
          <div className="vehicle-info-card-head">
            <span><Gauge size={18} /></span>
            <div><h2>Vehicle specification</h2><p>Used for compatible charger recommendations</p></div>
          </div>
          <dl className="vehicle-spec-list">
            <div><dt><Cable size={15} /> Connector</dt><dd>{vehicle.connectorType || 'Not set'}</dd></div>
            <div><dt><BatteryCharging size={15} /> Battery capacity</dt><dd>{vehicle.batteryCapacity || 'Not set'}</dd></div>
            <div><dt><CarFront size={15} /> Registration</dt><dd>{vehicle.registrationNumber}</dd></div>
          </dl>
        </article>
      </div>

      <aside className="vehicle-data-explainer">
        <div>
          <CircleAlert size={19} />
          <p><strong>How Vidyut decides “charging” or “connected”</strong><br />The dashboard uses this vehicle’s latest saved reading. The Bluetooth demo maps a nearby device’s standard Battery Service to EV battery percentage and estimates range; it does not prove the vehicle is charging. Real manufacturers may require protected apps or proprietary services.</p>
        </div>
        <button type="button" onClick={openEditor}>Review data</button>
      </aside>

      <div className="vehicle-detail-bottom-actions">
        <button type="button" onClick={onFindChargers}><MapPin size={17} /><span><strong>Find a compatible charger</strong><small>Filtered using {vehicle.connectorType || 'your connector'}</small></span></button>
        <button type="button" onClick={onOpenWallet}><Wallet size={17} /><span><strong>Open wallet</strong><small>Manage charging payments</small></span></button>
      </div>

      {showBluetoothConsent && (
        <div className="vehicle-detail-modal-backdrop" role="presentation" onMouseDown={() => !syncing && setShowBluetoothConsent(false)}>
          <section className="vehicle-detail-modal vehicle-bluetooth-modal" role="dialog" aria-modal="true" aria-labelledby="bluetooth-demo-title" onMouseDown={(event) => event.stopPropagation()}>
            <header>
              <div><span>DEMO TELEMETRY ADAPTER</span><h2 id="bluetooth-demo-title">Use a Bluetooth battery as EV data?</h2><p>Useful for demonstrating Vidyut before real manufacturer telemetry is connected.</p></div>
              <button type="button" onClick={() => setShowBluetoothConsent(false)} disabled={syncing} aria-label="Close Bluetooth demo"><X size={19} /></button>
            </header>
            <div className="vehicle-bluetooth-consent">
              <div className="vehicle-bluetooth-hero"><span><FlaskConical size={24} /></span><div><strong>Safe, clearly labeled simulation</strong><p>The selected device’s battery percentage will be stored against {vehicle.makeAndModel} as demo data—not as verified vehicle telemetry.</p></div></div>
              <ul>
                <li><CheckCircle2 size={16} /><span><strong>Battery mapping</strong>The Bluetooth value becomes the demo EV state of charge.</span></li>
                <li><Route size={16} /><span><strong>Range calculation</strong>Vidyut estimates range using {batteryCapacityKwh(vehicle.batteryCapacity)} kWh and 0.12 kWh/km.</span></li>
                <li><Radio size={16} /><span><strong>Live when available</strong>Battery changes update automatically if the device supports notifications.</span></li>
              </ul>
              <p className="vehicle-bluetooth-warning"><CircleAlert size={16} /> This does not detect real EV charging, location, speed, diagnostics, Android Auto, or Apple CarPlay.</p>
            </div>
            <footer>
              <button type="button" className="vehicle-secondary-button" onClick={() => setShowBluetoothConsent(false)} disabled={syncing}>Cancel</button>
              <button type="button" className="vehicle-primary-button" onClick={() => { setShowBluetoothConsent(false); void syncBluetooth(); }} disabled={syncing}><Bluetooth size={16} />{syncing ? 'Opening picker…' : 'Choose Bluetooth device'}</button>
            </footer>
          </section>
        </div>
      )}

      {showEditor && form && (
        <div className="vehicle-detail-modal-backdrop" role="presentation" onMouseDown={() => !saving && setShowEditor(false)}>
          <section className="vehicle-detail-modal" role="dialog" aria-modal="true" aria-labelledby="vehicle-update-title" onMouseDown={(event) => event.stopPropagation()}>
            <header>
              <div><span>VEHICLE TELEMETRY</span><h2 id="vehicle-update-title">Update {vehicle.makeAndModel}</h2><p>Record only what you know. Unknown values remain clearly marked.</p></div>
              <button type="button" onClick={() => setShowEditor(false)} disabled={saving} aria-label="Close vehicle update"><X size={19} /></button>
            </header>
            <form onSubmit={(event) => { event.preventDefault(); void saveTelemetry(); }}>
              {error && <div className="vehicle-detail-message error" role="alert"><CircleAlert size={16} />{error}</div>}
              <div className="vehicle-telemetry-fields">
                <label>Battery remaining (%)<input type="number" min="0" max="100" step="1" value={form.batteryPercent} onChange={(event) => setForm({ ...form, batteryPercent: event.target.value })} placeholder="68" /></label>
                <label>Estimated range (km)<input type="number" min="0" step="0.1" value={form.remainingRangeKm} onChange={(event) => setForm({ ...form, remainingRangeKm: event.target.value })} placeholder="246" /></label>
                <label>Connection status<select value={form.connectionStatus} onChange={(event) => setForm({ ...form, connectionStatus: event.target.value as VehicleConnectionStatus })}><option value="UNKNOWN">Not synced</option><option value="CONNECTED">Connected</option><option value="DISCONNECTED">Disconnected</option></select></label>
                <label>Charging status<select value={form.charging} onChange={(event) => setForm({ ...form, charging: event.target.value as ChargingChoice })}><option value="UNKNOWN">Not reported</option><option value="CHARGING">Charging</option><option value="NOT_CHARGING">Not charging</option></select></label>
                <label>Bluetooth telemetry<select value={form.bluetoothSupported} onChange={(event) => setForm({ ...form, bluetoothSupported: event.target.value as SupportChoice })}><option value="UNKNOWN">Not specified</option><option value="YES">Supported</option><option value="NO">Not supported</option></select></label>
                <label>Android Auto<select value={form.androidAutoSupported} onChange={(event) => setForm({ ...form, androidAutoSupported: event.target.value as SupportChoice })}><option value="UNKNOWN">Not specified</option><option value="YES">Supported</option><option value="NO">Not supported</option></select></label>
                <label>Apple CarPlay<select value={form.appleCarPlaySupported} onChange={(event) => setForm({ ...form, appleCarPlaySupported: event.target.value as SupportChoice })}><option value="UNKNOWN">Not specified</option><option value="YES">Supported</option><option value="NO">Not supported</option></select></label>
                <label>Last charged at<input type="datetime-local" value={form.lastChargedAt} onChange={(event) => setForm({ ...form, lastChargedAt: event.target.value })} /></label>
                <label className="wide">Last charging station<input value={form.lastChargingStation} onChange={(event) => setForm({ ...form, lastChargingStation: event.target.value })} placeholder="Green Park Station" maxLength={160} /></label>
                <label className="wide">Charging address<input value={form.lastChargingAddress} onChange={(event) => setForm({ ...form, lastChargingAddress: event.target.value })} placeholder="Green Park Extension, New Delhi" maxLength={255} /></label>
              </div>
              <footer>
                <button type="button" className="vehicle-secondary-button" onClick={() => setShowEditor(false)} disabled={saving}>Cancel</button>
                <button type="submit" className="vehicle-primary-button" disabled={saving}>{saving ? 'Saving…' : 'Save vehicle data'}</button>
              </footer>
            </form>
          </section>
        </div>
      )}
    </section>
  );
}
