import React, { useEffect, useMemo, useState } from 'react';
import { Alert, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useLocalSearchParams } from 'expo-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as Haptics from 'expo-haptics';
import { Colors } from '../../src/constants/colors';
import { SkeletonList } from '../../src/components/SkeletonList';
import { PrimaryButton } from '../../src/components/PrimaryButton';
import { controlSession, getSession, paySession, stopSession, updateSessionSoc } from '../../src/features/sessions/session.api';
import { getVehicle } from '../../src/features/vehicles/vehicle.api';
import { readVehicleSoc } from '../../src/features/bluetooth/bleAdapter';

export default function SessionScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const client = useQueryClient();
  const [btError, setBtError] = useState('');
  const query = useQuery({
    queryKey: ['session', id],
    queryFn: () => getSession(id!),
    enabled: !!id,
    refetchInterval: (state) => state.state.data?.status === 'ACTIVE' ? 10000 : false,
  });
  const vehicle = useQuery({
    queryKey: ['vehicle', query.data?.vehicleId],
    queryFn: () => getVehicle(query.data!.vehicleId!),
    enabled: !!query.data?.vehicleId,
  });
  const stop = useMutation({
    mutationFn: () => stopSession(id!),
    onSuccess: async (value) => { client.setQueryData(['session', id], value); await client.invalidateQueries({ queryKey: ['bookings'] }); await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success); },
    onError: (error: Error) => { void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error); Alert.alert('Could not stop charging', error.message); },
  });
  const pay = useMutation({
    mutationFn: () => paySession(id!),
    onSuccess: async (value) => { client.setQueryData(['session', id], value); await client.invalidateQueries({ queryKey: ['vehicle-wallets'] }); await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success); },
    onError: (error: Error) => Alert.alert('Payment not completed', error.message),
  });
  const control = useMutation({
    mutationFn: (action: 'START' | 'STOP') => controlSession(id!, action),
    onSuccess: (value) => client.setQueryData(['session', id], value),
    onError: (error: Error) => Alert.alert('Bluetooth control failed', error.message),
  });

  useEffect(() => {
    const currentVehicle = vehicle.data;
    if (!currentVehicle?.bluetoothDeviceId || query.data?.status !== 'ACTIVE') return;
    const sync = async () => {
      try {
        const soc = await readVehicleSoc(currentVehicle);
        const updated = await updateSessionSoc(id!, Math.max(query.data?.currentBatteryPercent ?? 0, Math.round(soc)), !!currentVehicle.btSimulatorEnabled);
        client.setQueryData(['session', id], updated);
        setBtError('');
      } catch (error) {
        setBtError(error instanceof Error ? error.message : 'Live Bluetooth data is unavailable.');
      }
    };
    void sync();
    const timer = setInterval(() => void sync(), 30000);
    return () => clearInterval(timer);
  }, [client, id, query.data?.status, vehicle.data?.bluetoothDeviceId, vehicle.data?.btSimulatorEnabled]);

  const timing = useMemo(() => {
    const startedAt = query.data?.startedAt ? new Date(query.data.startedAt).getTime() : Date.now();
    const elapsed = Math.max(0, Date.now() - startedAt);
    return { hours: Math.floor(elapsed / 3600000), minutes: Math.floor(elapsed / 60000) % 60, seconds: Math.floor(elapsed / 1000) % 60 };
  }, [query.data?.startedAt, query.data?.updatedAt]);

  if (query.isLoading || !query.data) return <SkeletonList rows={6} />;
  const session = query.data;
  const progress = Math.max(0, Math.min(100, (session.currentBatteryPercent - session.startBatteryPercent) / Math.max(1, session.targetBatteryPercent - session.startBatteryPercent) * 100));
  const bluetoothState = vehicle.data?.bluetoothDeviceId
    ? (btError ? 'weak' : 'connected') : 'fallback';
  const bluetoothColor = bluetoothState === 'connected' ? '#34D399' : bluetoothState === 'weak' ? '#F59E0B' : '#98A2B3';

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <View style={[styles.hero, session.status === 'COMPLETED' && styles.heroDone]}>
        <View style={styles.heroTop}>
          <View style={styles.live}><View style={[styles.pulse, session.status === 'COMPLETED' && styles.pulseDone]} /><Text style={styles.liveText}>{session.status === 'ACTIVE' ? 'LIVE CHARGING' : 'SESSION COMPLETE'}</Text></View>
          <View accessibilityLabel={`Bluetooth ${bluetoothState}`} style={styles.btBadge}><Ionicons name="bluetooth" size={13} color={bluetoothColor} /><Text style={[styles.btText, { color: bluetoothColor }]}>{bluetoothState}</Text></View>
        </View>
        <Text adjustsFontSizeToFit minimumFontScale={0.8} style={styles.percent}>{session.currentBatteryPercent}%</Text>
        <Text style={styles.target}>Target {session.targetBatteryPercent}% · {session.vehicleName || 'Your EV'}</Text>
        <View style={styles.track}><View style={[styles.fill, { width: `${progress}%` }]} /></View>
        <Text style={styles.station}>{session.stationName}</Text>
        <Text style={styles.completion}>Estimated complete {new Date(session.estimatedCompletionAt).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' })}</Text>
      </View>
      <View style={styles.grid}>
        <Metric icon="time-outline" label="Elapsed" value={`${String(timing.hours).padStart(2, '0')}:${String(timing.minutes).padStart(2, '0')}:${String(timing.seconds).padStart(2, '0')}`} />
        <Metric icon="flash-outline" label="Energy" value={`${session.energyKwh.toFixed(2)} kWh`} />
        <Metric icon="speedometer-outline" label="Power" value={`${session.powerKw.toFixed(1)} kW`} />
        <Metric icon="wallet-outline" label="Current cost" value={`₹${session.cost.toFixed(2)}`} />
      </View>
      {btError ? <View style={styles.btWarning}><Ionicons name="bluetooth-outline" size={18} color="#92400E" /><Text style={styles.btWarningText}>{btError} Manual session controls remain available.</Text></View> : null}
      {session.status === 'COMPLETED' ? (
        <View style={styles.summary}>
          <Text style={styles.summaryTitle}>Charging summary</Text>
          <Summary label="Energy delivered" value={`${session.energyKwh.toFixed(2)} kWh`} />
          <Summary label="Total cost" value={`₹${session.cost.toFixed(2)}`} />
          <Summary label="CO₂ avoided" value={`${session.co2SavedKg.toFixed(2)} kg`} />
          <Summary label="Telemetry" value={(session.telemetrySource || 'estimated').replace('_', ' ').toLowerCase()} />
          <Summary label="Payment" value={session.paymentStatus} />
          {session.paymentStatus === 'DUE' ? <PrimaryButton title="Pay from vehicle wallet" onPress={() => pay.mutate()} loading={pay.isPending} style={{ marginTop: 15 }} /> : <View style={styles.paid}><Ionicons name="checkmark-circle" size={18} color={Colors.success} /><Text style={styles.paidText}>Paid · receipt saved to Wallet</Text></View>}
        </View>
      ) : (
        <>
          <View style={styles.info}><Ionicons name="sync-outline" size={18} color={Colors.primary} /><Text style={styles.infoText}>{vehicle.data?.bluetoothDeviceId ? 'Live SoC is sent every 30 seconds and completion time updates from the paired device.' : 'Battery percentage is estimated. Pair an EV from Profile → Bluetooth for trusted live SoC.'}</Text></View>
          {vehicle.data?.btSessionControlEnabled ? <View style={styles.controlRow}><PrimaryButton title="BLE start" variant="outline" onPress={() => control.mutate('START')} loading={control.isPending} style={{ flex: 1 }} /><PrimaryButton title="BLE stop" variant="outline" onPress={() => control.mutate('STOP')} loading={control.isPending} style={{ flex: 1 }} /></View> : null}
          <PrimaryButton title="Stop charging" variant="outline" onPress={() => Alert.alert('Stop charging?', 'The session will finish and the metered amount becomes payable.', [{ text: 'Continue charging', style: 'cancel' }, { text: 'Stop', style: 'destructive', onPress: () => stop.mutate() }])} loading={stop.isPending} style={{ marginTop: 10 }} />
        </>
      )}
    </ScrollView>
  );
}

function Metric({ icon, label, value }: { icon: keyof typeof Ionicons.glyphMap; label: string; value: string }) { return <View style={styles.metric}><Ionicons name={icon} size={20} color={Colors.primary} /><Text adjustsFontSizeToFit minimumFontScale={0.75} style={styles.metricValue}>{value}</Text><Text style={styles.metricLabel}>{label}</Text></View>; }
function Summary({ label, value }: { label: string; value: string }) { return <View style={styles.summaryRow}><Text style={styles.summaryLabel}>{label}</Text><Text style={styles.summaryValue}>{value}</Text></View>; }

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Colors.background }, content: { padding: 17, paddingBottom: 35 },
  hero: { padding: 23, borderRadius: 22, backgroundColor: '#075B3B' }, heroDone: { backgroundColor: '#164E3A' }, heroTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  live: { flexDirection: 'row', alignItems: 'center', gap: 7 }, pulse: { width: 9, height: 9, borderRadius: 5, backgroundColor: '#4ADE80' }, pulseDone: { backgroundColor: '#BBF7D0' }, liveText: { color: '#D1FAE5', fontSize: 9, fontWeight: '900', letterSpacing: 1.2 },
  btBadge: { paddingHorizontal: 8, paddingVertical: 5, flexDirection: 'row', alignItems: 'center', gap: 4, borderRadius: 9, backgroundColor: 'rgba(255,255,255,.1)' }, btText: { fontSize: 8, fontWeight: '900' },
  percent: { marginTop: 18, color: Colors.white, fontSize: 48, fontWeight: '900' }, target: { color: '#A7F3D0', fontSize: 10 }, track: { height: 8, marginTop: 16, borderRadius: 4, backgroundColor: 'rgba(255,255,255,.15)', overflow: 'hidden' }, fill: { height: '100%', borderRadius: 4, backgroundColor: '#4ADE80' }, station: { marginTop: 15, color: Colors.white, fontSize: 14, fontWeight: '800' }, completion: { marginTop: 4, color: '#A7F3D0', fontSize: 9 },
  grid: { marginTop: 12, flexDirection: 'row', flexWrap: 'wrap', gap: 9 }, metric: { width: '48%', padding: 15, borderRadius: 15, backgroundColor: Colors.white, borderWidth: 1, borderColor: Colors.border }, metricValue: { marginTop: 8, color: Colors.textPrimary, fontSize: 16, fontWeight: '900' }, metricLabel: { marginTop: 2, color: Colors.textSecondary, fontSize: 9 },
  info: { marginTop: 13, padding: 12, flexDirection: 'row', gap: 8, borderRadius: 12, backgroundColor: Colors.primarySoft }, infoText: { flex: 1, color: Colors.textSecondary, fontSize: 9.5, lineHeight: 14 }, btWarning: { marginTop: 12, padding: 12, flexDirection: 'row', gap: 8, borderRadius: 12, backgroundColor: '#FEF3C7' }, btWarningText: { flex: 1, color: '#92400E', fontSize: 9, lineHeight: 14 }, controlRow: { marginTop: 12, flexDirection: 'row', gap: 8 },
  summary: { marginTop: 14, padding: 17, borderRadius: 17, backgroundColor: Colors.white, borderWidth: 1, borderColor: Colors.border }, summaryTitle: { marginBottom: 8, color: Colors.textPrimary, fontSize: 17, fontWeight: '900' }, summaryRow: { paddingVertical: 11, flexDirection: 'row', justifyContent: 'space-between', borderBottomWidth: 1, borderBottomColor: Colors.borderSoft }, summaryLabel: { color: Colors.textSecondary, fontSize: 11 }, summaryValue: { maxWidth: '58%', color: Colors.textPrimary, fontSize: 11, fontWeight: '900', textAlign: 'right' }, paid: { marginTop: 14, padding: 11, flexDirection: 'row', gap: 7, justifyContent: 'center', borderRadius: 11, backgroundColor: Colors.successLight }, paidText: { color: Colors.success, fontSize: 10, fontWeight: '900' },
});
