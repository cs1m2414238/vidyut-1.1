import React, { useEffect, useMemo, useState } from 'react';
import { Alert, ScrollView, StyleSheet, Switch, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import * as Haptics from 'expo-haptics';
import { Colors } from '../../src/constants/colors';
import { PrimaryButton } from '../../src/components/PrimaryButton';
import { SkeletonList } from '../../src/components/SkeletonList';
import { createBleAdapter, BleAdapter, BleDeviceSummary, VIDYUT_SERVICE_UUID } from '../../src/features/bluetooth/bleAdapter';
import { getMyVehicles, updateVehicle } from '../../src/features/vehicles/vehicle.api';

export default function BluetoothSettingsScreen() {
  const client = useQueryClient();
  const vehicles = useQuery({ queryKey: ['vehicles'], queryFn: getMyVehicles });
  const [vehicleId, setVehicleId] = useState<number | null>(null);
  const vehicle = vehicles.data?.find((item) => item.id === vehicleId) ?? vehicles.data?.[0];
  const [simulator, setSimulator] = useState(false);
  const [sessionControl, setSessionControl] = useState(false);
  const [devices, setDevices] = useState<BleDeviceSummary[]>([]);
  const [busy, setBusy] = useState('');
  const adapter = useMemo<BleAdapter>(() => createBleAdapter(simulator), [simulator]);

  useEffect(() => {
    if (vehicles.data?.length && vehicleId === null) setVehicleId(vehicles.data[0].id);
  }, [vehicleId, vehicles.data]);
  useEffect(() => {
    setSimulator(!!vehicle?.btSimulatorEnabled);
    setSessionControl(!!vehicle?.btSessionControlEnabled);
  }, [vehicle?.id, vehicle?.btSessionControlEnabled, vehicle?.btSimulatorEnabled]);
  useEffect(() => () => adapter.destroy(), [adapter]);

  if (vehicles.isLoading) return <SkeletonList rows={6} />;
  if (!vehicle) return <View style={styles.empty}><Ionicons name="car-sport-outline" size={44} color={Colors.textMuted} /><Text style={styles.emptyTitle}>Add an EV first</Text><Text style={styles.emptyText}>Bluetooth pairing is saved against a vehicle profile.</Text></View>;

  const saveMode = async (nextSimulator: boolean, nextControl: boolean) => {
    setSimulator(nextSimulator); setSessionControl(nextControl);
    try {
      await updateVehicle(vehicle.id, {
        btSimulatorEnabled: nextSimulator,
        btSessionControlEnabled: nextControl,
        bluetoothSupported: true,
        telemetrySource: nextSimulator ? 'BLUETOOTH_DEMO' : vehicle.telemetrySource,
      });
      await client.invalidateQueries({ queryKey: ['vehicles'] });
    } catch (error) { Alert.alert('Setting not saved', error instanceof Error ? error.message : 'Please try again.'); }
  };

  const scan = async () => {
    setBusy('scan'); setDevices([]);
    try {
      const found = await adapter.scan();
      setDevices(found);
      if (!found.length) Alert.alert('No compatible devices', 'Only devices advertising the Vidyut service UUID are shown.');
    } catch (error) { Alert.alert('Bluetooth scan stopped', error instanceof Error ? error.message : 'Bluetooth is unavailable.'); }
    finally { setBusy(''); }
  };

  const pair = async (device: BleDeviceSummary) => {
    setBusy(device.id);
    try {
      const paired = await adapter.connect(device);
      await updateVehicle(vehicle.id, {
        bluetoothDeviceId: paired.id,
        bluetoothDeviceName: paired.name,
        bluetoothServiceUuid: paired.serviceUuid || VIDYUT_SERVICE_UUID,
        bluetoothSupported: true,
        btSimulatorEnabled: paired.simulated,
        connectionStatus: 'CONNECTED',
        telemetrySource: paired.simulated ? 'BLUETOOTH_DEMO' : 'BLUETOOTH',
      });
      await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      await client.invalidateQueries({ queryKey: ['vehicles'] });
      Alert.alert('Vehicle paired', `${paired.name} will provide live battery status when available.`);
    } catch (error) { Alert.alert('Pairing failed', error instanceof Error ? error.message : 'Please try again.'); }
    finally { setBusy(''); }
  };

  const forget = async () => {
    if (vehicle.bluetoothDeviceId) await adapter.disconnect(vehicle.bluetoothDeviceId);
    await updateVehicle(vehicle.id, {
      bluetoothDeviceId: '', bluetoothDeviceName: '', bluetoothServiceUuid: '',
      connectionStatus: 'DISCONNECTED', telemetrySource: 'NOT_AVAILABLE',
    });
    setDevices([]);
    await client.invalidateQueries({ queryKey: ['vehicles'] });
  };

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <Text style={styles.label}>Vehicle</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.vehicles}>
        {(vehicles.data ?? []).map((item) => <TouchableOpacity key={item.id} onPress={() => setVehicleId(item.id)} style={[styles.vehicle, item.id === vehicle.id && styles.vehicleActive]}><Ionicons name="car-sport" size={17} color={item.id === vehicle.id ? Colors.white : Colors.primary} /><View><Text style={[styles.vehicleName, item.id === vehicle.id && styles.vehicleTextActive]}>{item.makeAndModel}</Text><Text style={[styles.vehicleReg, item.id === vehicle.id && styles.vehicleTextActive]}>{item.registrationNumber}</Text></View></TouchableOpacity>)}
      </ScrollView>

      <View style={styles.statusCard}>
        <View style={[styles.statusIcon, { backgroundColor: vehicle.bluetoothDeviceId ? Colors.successLight : Colors.borderSoft }]}><Ionicons name="bluetooth" size={24} color={vehicle.bluetoothDeviceId ? Colors.success : Colors.textMuted} /></View>
        <View style={styles.statusCopy}><Text style={styles.statusTitle}>{vehicle.bluetoothDeviceName || 'No paired Bluetooth device'}</Text><Text style={styles.statusText}>{vehicle.bluetoothDeviceId ? `${vehicle.connectionStatus?.toLowerCase()} · ${vehicle.telemetrySource?.toLowerCase().replace('_', ' ')}` : 'Scan a compatible EV or charger beacon'}</Text></View>
        {vehicle.bluetoothDeviceId ? <TouchableOpacity onPress={() => void forget()}><Text style={styles.forget}>Forget</Text></TouchableOpacity> : null}
      </View>

      <View style={styles.setting}>
        <View style={styles.settingCopy}><Text style={styles.settingTitle}>BLE simulator</Text><Text style={styles.settingText}>Generate compatible devices and battery readings without physical hardware.</Text></View>
        <Switch value={simulator} onValueChange={(value) => void saveMode(value, sessionControl)} trackColor={{ false: Colors.border, true: '#86EFAC' }} thumbColor={simulator ? Colors.primary : Colors.textMuted} />
      </View>
      <View style={styles.setting}>
        <View style={styles.settingCopy}><Text style={styles.settingTitle}>Bluetooth session control</Text><Text style={styles.settingText}>Allow start and stop commands only for this paired vehicle.</Text></View>
        <Switch value={sessionControl} onValueChange={(value) => void saveMode(simulator, value)} trackColor={{ false: Colors.border, true: '#86EFAC' }} thumbColor={sessionControl ? Colors.primary : Colors.textMuted} />
      </View>

      <PrimaryButton title={busy === 'scan' ? 'Scanning for 8 seconds…' : `Scan ${simulator ? 'simulator' : 'nearby BLE'} devices`} loading={busy === 'scan'} onPress={() => void scan()} style={{ marginTop: 16 }} />
      <Text style={styles.helper}>Compatibility filter: {VIDYUT_SERVICE_UUID}</Text>
      {devices.map((device) => (
        <TouchableOpacity key={device.id} disabled={!!busy} style={styles.device} onPress={() => void pair(device)}>
          <View style={styles.deviceIcon}><Ionicons name={device.simulated ? 'flask-outline' : 'bluetooth'} size={20} color={Colors.primary} /></View>
          <View style={styles.deviceCopy}><Text style={styles.deviceName}>{device.name}</Text><Text style={styles.deviceMeta}>{device.id} · signal {device.signal} dBm</Text></View>
          <Text style={styles.pair}>{busy === device.id ? 'Pairing…' : 'Pair'}</Text>
        </TouchableOpacity>
      ))}
      <View style={styles.fallback}><Ionicons name="shield-checkmark-outline" size={19} color={Colors.primary} /><Text style={styles.fallbackText}>If Bluetooth is off or denied, manual charging and session controls continue to work normally.</Text></View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Colors.background }, content: { padding: 16, paddingBottom: 40 },
  label: { marginBottom: 8, color: Colors.textPrimary, fontSize: 11, fontWeight: '900' }, vehicles: { gap: 8 },
  vehicle: { minWidth: 180, padding: 12, flexDirection: 'row', gap: 9, alignItems: 'center', borderRadius: 13, backgroundColor: Colors.white, borderWidth: 1, borderColor: Colors.border },
  vehicleActive: { backgroundColor: Colors.primary, borderColor: Colors.primary }, vehicleName: { color: Colors.textPrimary, fontSize: 10.5, fontWeight: '900' }, vehicleReg: { marginTop: 2, color: Colors.textSecondary, fontSize: 8 }, vehicleTextActive: { color: Colors.white },
  statusCard: { marginTop: 16, padding: 14, flexDirection: 'row', alignItems: 'center', gap: 10, borderRadius: 17, backgroundColor: Colors.white, borderWidth: 1, borderColor: Colors.border },
  statusIcon: { width: 46, height: 46, alignItems: 'center', justifyContent: 'center', borderRadius: 14 }, statusCopy: { flex: 1 }, statusTitle: { color: Colors.textPrimary, fontSize: 11.5, fontWeight: '900' }, statusText: { marginTop: 3, color: Colors.textSecondary, fontSize: 8.5 }, forget: { color: Colors.error, fontSize: 9, fontWeight: '900' },
  setting: { marginTop: 10, padding: 14, flexDirection: 'row', alignItems: 'center', borderRadius: 15, backgroundColor: Colors.white, borderWidth: 1, borderColor: Colors.border }, settingCopy: { flex: 1, paddingRight: 10 }, settingTitle: { color: Colors.textPrimary, fontSize: 11, fontWeight: '900' }, settingText: { marginTop: 3, color: Colors.textSecondary, fontSize: 8.5, lineHeight: 13 },
  helper: { marginTop: 7, color: Colors.textMuted, fontSize: 7.5, textAlign: 'center' }, device: { marginTop: 9, padding: 12, flexDirection: 'row', alignItems: 'center', gap: 9, borderRadius: 14, backgroundColor: Colors.white, borderWidth: 1, borderColor: Colors.border }, deviceIcon: { width: 38, height: 38, alignItems: 'center', justifyContent: 'center', borderRadius: 12, backgroundColor: Colors.primaryLight }, deviceCopy: { flex: 1 }, deviceName: { color: Colors.textPrimary, fontSize: 10.5, fontWeight: '900' }, deviceMeta: { marginTop: 2, color: Colors.textSecondary, fontSize: 7.5 }, pair: { color: Colors.primary, fontSize: 9, fontWeight: '900' },
  fallback: { marginTop: 18, padding: 13, flexDirection: 'row', gap: 8, borderRadius: 13, backgroundColor: Colors.primaryLight }, fallbackText: { flex: 1, color: Colors.primaryDark, fontSize: 9, lineHeight: 14, fontWeight: '700' },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 30, backgroundColor: Colors.background }, emptyTitle: { marginTop: 10, color: Colors.textPrimary, fontSize: 17, fontWeight: '900' }, emptyText: { marginTop: 5, color: Colors.textSecondary, fontSize: 10, textAlign: 'center' },
});
