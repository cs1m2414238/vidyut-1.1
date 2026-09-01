import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Linking, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useLocalSearchParams, useRouter } from 'expo-router';
import * as Location from 'expo-location';
import MapView, { Marker, Polyline } from 'react-native-maps';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as Haptics from 'expo-haptics';
import { Colors } from '../../src/constants/colors';
import { SkeletonList } from '../../src/components/SkeletonList';
import { PrimaryButton } from '../../src/components/PrimaryButton';
import { getBooking } from '../../src/features/bookings/booking.api';
import { getChargerById } from '../../src/features/chargers/charger.api';
import { divertBooking, getRouteStatus } from '../../src/features/routing/routing.api';

const haversine = (a: { latitude: number; longitude: number }, b: { latitude: number; longitude: number }) => {
  const radius = 6371; const toRad = (value: number) => value * Math.PI / 180;
  const lat = toRad(b.latitude - a.latitude); const lng = toRad(b.longitude - a.longitude);
  const value = Math.sin(lat / 2) ** 2 + Math.cos(toRad(a.latitude)) * Math.cos(toRad(b.latitude)) * Math.sin(lng / 2) ** 2;
  return radius * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
};

export default function ActiveTripScreen() {
  const { bookingId } = useLocalSearchParams<{ bookingId: string }>();
  const router = useRouter(); const client = useQueryClient();
  const [location, setLocation] = useState<{ latitude: number; longitude: number } | null>(null);
  const booking = useQuery({ queryKey: ['booking', bookingId], queryFn: () => getBooking(bookingId!), enabled: !!bookingId });
  const station = useQuery({ queryKey: ['charger', booking.data?.stationId], queryFn: () => getChargerById(booking.data!.stationId), enabled: !!booking.data?.stationId });
  const status = useQuery({ queryKey: ['route-status', bookingId], queryFn: () => getRouteStatus(Number(bookingId)), enabled: !!bookingId, refetchInterval: 10000 });
  const divert = useMutation({
    mutationFn: (stationId: number) => divertBooking(Number(bookingId), stationId),
    onSuccess: async () => { await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success); await client.invalidateQueries({ queryKey: ['bookings'] }); Alert.alert('Route updated', 'The old booking was released without a fee and the compatible replacement is ready.', [{ text: 'View booking', onPress: () => router.replace('/(owner)/bookings') }]); },
    onError: (error: Error) => { void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error); Alert.alert('Diversion failed', error.message); },
  });

  useEffect(() => {
    void Location.requestForegroundPermissionsAsync().then(async (permission) => {
      if (permission.status !== 'granted') return;
      const current = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      setLocation({ latitude: current.coords.latitude, longitude: current.coords.longitude });
    }).catch(() => undefined);
  }, []);

  const destination = station.data ? { latitude: station.data.latitude, longitude: station.data.longitude } : null;
  const distance = useMemo(() => location && destination ? haversine(location, destination) : undefined, [destination?.latitude, destination?.longitude, location?.latitude, location?.longitude]);
  const eta = distance === undefined ? undefined : Math.max(1, Math.round(distance / 42 * 60));
  if (booking.isLoading || station.isLoading || !booking.data || !station.data) return <SkeletonList rows={6} />;

  const openMaps = () => Linking.openURL(`https://www.google.com/maps/dir/?api=1&destination=${station.data!.latitude},${station.data!.longitude}`);
  return (
    <View style={styles.screen}>
      <MapView style={styles.map} initialRegion={{ latitude: station.data.latitude, longitude: station.data.longitude, latitudeDelta: 0.15, longitudeDelta: 0.15 }} showsUserLocation={!!location}>
        {location && destination ? <Polyline coordinates={[location, destination]} strokeColor={Colors.primary} strokeWidth={5} /> : null}
        <Marker coordinate={destination!} title={station.data.name} description={station.data.address} pinColor={status.data?.diversionRecommended ? Colors.error : Colors.primary} />
      </MapView>
      <ScrollView style={styles.sheet} contentContainerStyle={styles.content}>
        <View style={styles.handle} />
        <View style={styles.topRow}><View style={styles.copy}><Text style={styles.kicker}>NEXT CHARGING STOP</Text><Text style={styles.title}>{station.data.name}</Text><Text style={styles.address}>{station.data.address}</Text></View><View style={styles.eta}><Text style={styles.etaValue}>{eta ?? '—'}</Text><Text style={styles.etaLabel}>MIN ETA</Text></View></View>
        <View style={styles.metrics}><Metric label="Distance" value={distance === undefined ? 'Waiting for GPS' : `${distance.toFixed(1)} km`} /><Metric label="Free slots" value={`${station.data.availableSlots}/${station.data.totalSlots}`} /><Metric label="Connector" value={station.data.connectorType} /></View>
        {status.data?.diversionRecommended ? <View accessibilityRole="alert" style={styles.alert}><View style={styles.alertHead}><Ionicons name="warning" size={21} color={Colors.error} /><View style={styles.alertCopy}><Text style={styles.alertTitle}>Station full — reroute now</Text><Text style={styles.alertText}>{status.data.reason}</Text></View></View>{status.data.alternatives.slice(0, 3).map((alternative) => <TouchableOpacity key={String(alternative.station.id)} disabled={divert.isPending} style={styles.alternative} onPress={() => divert.mutate(Number(alternative.station.id))}><View style={styles.altCopy}><Text style={styles.altName}>{alternative.station.name}</Text><Text style={styles.altMeta}>{alternative.detourKm.toFixed(1)} km detour · {alternative.availableSlots} free · {alternative.station.connectorType}</Text></View><View style={styles.reroute}><Text style={styles.rerouteText}>{divert.isPending ? 'Moving…' : 'Reroute'}</Text><Ionicons name="arrow-forward" size={14} color={Colors.white} /></View></TouchableOpacity>)}</View> : <View style={styles.healthy}><Ionicons name="shield-checkmark" size={19} color={Colors.success} /><Text style={styles.healthyText}>Reservation protected. Vidyut checks station capacity every 10 seconds.</Text></View>}
        <PrimaryButton title="Open turn-by-turn directions" onPress={() => void openMaps()} style={{ marginTop: 13 }} />
      </ScrollView>
    </View>
  );
}

function Metric({ label, value }: { label: string; value: string }) { return <View style={styles.metric}><Text adjustsFontSizeToFit minimumFontScale={0.75} style={styles.metricValue}>{value}</Text><Text style={styles.metricLabel}>{label}</Text></View>; }

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Colors.background }, map: { flex: 1, minHeight: 300 }, sheet: { maxHeight: '58%', marginTop: -18, borderTopLeftRadius: 24, borderTopRightRadius: 24, backgroundColor: Colors.background }, content: { padding: 17, paddingBottom: 35 }, handle: { width: 42, height: 4, alignSelf: 'center', marginBottom: 15, borderRadius: 2, backgroundColor: Colors.border },
  topRow: { flexDirection: 'row', alignItems: 'center', gap: 12 }, copy: { flex: 1 }, kicker: { color: Colors.primary, fontSize: 8, fontWeight: '900', letterSpacing: 1 }, title: { marginTop: 4, color: Colors.textPrimary, fontSize: 20, fontWeight: '900' }, address: { marginTop: 3, color: Colors.textSecondary, fontSize: 9.5 }, eta: { width: 64, height: 64, alignItems: 'center', justifyContent: 'center', borderRadius: 18, backgroundColor: Colors.primaryLight }, etaValue: { color: Colors.primary, fontSize: 20, fontWeight: '900' }, etaLabel: { color: Colors.primaryDark, fontSize: 6.5, fontWeight: '900' },
  metrics: { marginTop: 14, flexDirection: 'row', gap: 8 }, metric: { flex: 1, padding: 11, alignItems: 'center', borderRadius: 13, backgroundColor: Colors.white, borderWidth: 1, borderColor: Colors.border }, metricValue: { color: Colors.textPrimary, fontSize: 11, fontWeight: '900' }, metricLabel: { marginTop: 3, color: Colors.textSecondary, fontSize: 7.5 },
  healthy: { marginTop: 13, padding: 12, flexDirection: 'row', gap: 8, borderRadius: 13, backgroundColor: Colors.successLight }, healthyText: { flex: 1, color: Colors.success, fontSize: 9, lineHeight: 14, fontWeight: '800' }, alert: { marginTop: 13, padding: 13, borderRadius: 16, backgroundColor: Colors.errorLight, borderWidth: 1, borderColor: '#FECACA' }, alertHead: { flexDirection: 'row', gap: 8 }, alertCopy: { flex: 1 }, alertTitle: { color: '#991B1B', fontSize: 12, fontWeight: '900' }, alertText: { marginTop: 2, color: '#991B1B', fontSize: 9 }, alternative: { marginTop: 9, padding: 10, flexDirection: 'row', alignItems: 'center', gap: 8, borderRadius: 11, backgroundColor: Colors.white }, altCopy: { flex: 1 }, altName: { color: Colors.textPrimary, fontSize: 10, fontWeight: '900' }, altMeta: { marginTop: 3, color: Colors.textSecondary, fontSize: 7.5 }, reroute: { paddingHorizontal: 9, paddingVertical: 7, flexDirection: 'row', gap: 3, borderRadius: 8, backgroundColor: Colors.primary }, rerouteText: { color: Colors.white, fontSize: 8, fontWeight: '900' },
});
