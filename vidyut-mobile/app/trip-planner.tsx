import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Linking, ScrollView, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import * as Location from 'expo-location';
import { useRouter } from 'expo-router';
import MapView, { Circle, Marker, Polyline } from 'react-native-maps';
import { useQuery } from '@tanstack/react-query';
import * as Haptics from 'expo-haptics';
import { Colors } from '../src/constants/colors';
import { PrimaryButton } from '../src/components/PrimaryButton';
import { SkeletonList } from '../src/components/SkeletonList';
import { getMyVehicles } from '../src/features/vehicles/vehicle.api';
import { getRouteAlternatives, planRoute } from '../src/features/routing/routing.api';
import type { RoutePlan, RouteStop } from '../src/features/routing/routing.types';

const citySuggestions = ['Delhi', 'Jaipur', 'Udaipur', 'Ahmedabad', 'Vadodara', 'Surat', 'Mumbai', 'Kanpur', 'Agra'];
const lucknow = { latitude: 26.8467, longitude: 80.9462 };

export default function TripPlannerScreen() {
  const router = useRouter();
  const vehicles = useQuery({ queryKey: ['vehicles'], queryFn: getMyVehicles });
  const [selectedVehicle, setSelectedVehicle] = useState(0);
  const [destination, setDestination] = useState('');
  const [soc, setSoc] = useState('70');
  const [loading, setLoading] = useState(false);
  const [plan, setPlan] = useState<RoutePlan | null>(null);
  const [originPoint, setOriginPoint] = useState(lucknow);
  const [destinationPoint, setDestinationPoint] = useState<{ latitude: number; longitude: number } | null>(null);
  const [stops, setStops] = useState<RouteStop[]>([]);
  const [alternatives, setAlternatives] = useState<Record<string, RouteStop[]>>({});
  const [alternativeLoading, setAlternativeLoading] = useState<string>();

  useEffect(() => { if (!selectedVehicle && vehicles.data?.length) setSelectedVehicle(vehicles.data[0].id); }, [vehicles.data, selectedVehicle]);
  const selected = vehicles.data?.find((vehicle) => vehicle.id === selectedVehicle);
  const filteredCities = destination.trim().length > 0
    ? citySuggestions.filter((city) => city.toLowerCase().startsWith(destination.trim().toLowerCase())).slice(0, 4)
    : citySuggestions.slice(0, 5);
  const usableRangeMeters = useMemo(() => {
    if (plan) return plan.usableRangeKm * 1000;
    const capacity = Number((selected?.batteryCapacity || '').replace(/[^0-9.]/g, ''));
    return (selected?.remainingRangeKm ?? capacity * 6.2 * (Number(soc) || 70) / 100) * 1000;
  }, [plan, selected, soc]);
  const points = destinationPoint ? [originPoint, ...stops.map((stop) => ({ latitude: stop.station.latitude, longitude: stop.station.longitude })), destinationPoint] : [];

  const submit = async () => {
    if (!destination.trim() || !selectedVehicle) { Alert.alert('Trip details', 'Choose a vehicle and enter a destination.'); return; }
    setLoading(true); setPlan(null); setAlternatives({});
    try {
      const permission = await Location.requestForegroundPermissionsAsync();
      let current = lucknow;
      let origin = 'Lucknow (location fallback)';
      if (permission.status === 'granted') {
        const position = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
        current = { latitude: position.coords.latitude, longitude: position.coords.longitude };
        origin = 'Current location';
      }
      const geocoded = await Location.geocodeAsync(destination.trim());
      if (!geocoded.length) throw new Error('Destination could not be located. Add the city or state.');
      const target = { latitude: geocoded[0].latitude, longitude: geocoded[0].longitude };
      const result = await planRoute({
        origin, destination: destination.trim(), vehicleId: selectedVehicle,
        currentBatteryPercent: Number(soc) || 70,
        originLatitude: current.latitude, originLongitude: current.longitude,
        destinationLatitude: target.latitude, destinationLongitude: target.longitude,
        reserveBatteryPercent: 10,
      });
      setOriginPoint(current); setDestinationPoint(target); setPlan(result); setStops(result.recommendedChargingStops);
      await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    } catch (error) { Alert.alert('Trip could not be planned', error instanceof Error ? error.message : 'Please try again.'); }
    finally { setLoading(false); }
  };

  const loadAlternatives = async (stop: RouteStop) => {
    const key = String(stop.station.id);
    if (alternatives[key]) { setAlternatives((current) => ({ ...current, [key]: [] })); return; }
    setAlternativeLoading(key);
    try {
      const nextAlternatives = await getRouteAlternatives(stop.station.id, selectedVehicle);
      setAlternatives((current) => ({ ...current, [key]: nextAlternatives }));
    }
    catch (error) { Alert.alert('Alternatives unavailable', error instanceof Error ? error.message : 'Please try again.'); }
    finally { setAlternativeLoading(undefined); }
  };

  const swap = (index: number, alternative: RouteStop) => {
    setStops((current) => current.map((stop, itemIndex) => itemIndex === index ? alternative : stop));
    setAlternatives({});
    void Haptics.selectionAsync();
  };

  if (vehicles.isLoading) return <SkeletonList rows={6} />;
  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <Text style={styles.eyebrow}>M5 · RANGE-AWARE ROUTING</Text>
      <Text adjustsFontSizeToFit minimumFontScale={0.8} style={styles.title}>Plan the whole trip, not just the next charger</Text>
      <Text style={styles.subtitle}>Connector compatibility, reserve battery, live slots, detour, ETA and price are scored together.</Text>
      <Text style={styles.label}>Vehicle</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.vehicleRow}>{(vehicles.data ?? []).map((vehicle) => <TouchableOpacity key={vehicle.id} onPress={() => setSelectedVehicle(vehicle.id)} style={[styles.vehicle, selectedVehicle === vehicle.id && styles.vehicleOn]}><Ionicons name="car-sport" size={19} color={selectedVehicle === vehicle.id ? Colors.white : Colors.primary} /><View><Text style={[styles.vehicleName, selectedVehicle === vehicle.id && styles.onText]}>{vehicle.makeAndModel}</Text><Text style={[styles.vehicleMeta, selectedVehicle === vehicle.id && styles.onMeta]}>{vehicle.remainingRangeKm ? `${Math.round(vehicle.remainingRangeKm)} km reported` : 'Estimated from battery'}</Text></View></TouchableOpacity>)}</ScrollView>
      <Text style={styles.label}>Destination</Text>
      <View style={styles.inputRow}><Ionicons name="location-outline" size={19} color={Colors.primary} /><TextInput accessibilityLabel="Trip destination" value={destination} onChangeText={setDestination} placeholder="Mumbai, Maharashtra" placeholderTextColor={Colors.textMuted} style={styles.input} /></View>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.suggestions}>{filteredCities.map((city) => <TouchableOpacity key={city} style={styles.suggestion} onPress={() => setDestination(city)}><Text style={styles.suggestionText}>{city}</Text></TouchableOpacity>)}</ScrollView>
      <Text style={styles.label}>Current battery</Text>
      <View style={styles.socRow}>{['30', '50', '70', '90'].map((value) => <TouchableOpacity key={value} onPress={() => setSoc(value)} style={[styles.soc, soc === value && styles.socOn]}><Text style={[styles.socText, soc === value && styles.socTextOn]}>{value}%</Text></TouchableOpacity>)}</View>
      <PrimaryButton title="Plan trip" onPress={() => void submit()} loading={loading} style={{ marginTop: 20 }} />

      {plan ? <>
        <View style={styles.summary}><View style={styles.summaryCopy}><Text style={styles.summaryKicker}>{plan.destinationWithinRange ? 'DIRECT TRIP' : `${stops.length} CHARGING STOP${stops.length === 1 ? '' : 'S'}`}</Text><Text adjustsFontSizeToFit minimumFontScale={0.75} style={styles.summaryTitle}>{plan.totalDistanceKm.toFixed(0)} km · {Math.floor(plan.totalDurationMinutes / 60)}h {plan.totalDurationMinutes % 60}m</Text><Text style={styles.summaryMeta}>Usable range {plan.usableRangeKm.toFixed(0)} km · arrive near {plan.estimatedArrivalBatteryPercent.toFixed(0)}%</Text></View><Ionicons name={plan.destinationWithinRange ? 'checkmark-circle' : 'flash'} size={31} color={plan.destinationWithinRange ? Colors.success : Colors.warning} /></View>
        {points.length > 1 ? <MapView style={styles.map} initialRegion={{ latitude: originPoint.latitude, longitude: originPoint.longitude, latitudeDelta: 3.5, longitudeDelta: 3.5 }}><Circle center={originPoint} radius={usableRangeMeters} strokeColor="rgba(15,143,93,.65)" fillColor="rgba(15,143,93,.08)" strokeWidth={2} /><Polyline coordinates={points} strokeColor={Colors.primary} strokeWidth={4} />{points.map((point, index) => <Marker key={`${point.latitude}-${point.longitude}-${index}`} coordinate={point} pinColor={index === 0 ? Colors.blue : index === points.length - 1 ? Colors.error : Colors.primary} />)}</MapView> : null}
        <View style={styles.rangeNote}><Ionicons name="radio-button-on-outline" size={17} color={Colors.primary} /><Text style={styles.rangeText}>The green range ring reflects the selected EV, {soc}% charge and a 10% reserve.</Text></View>
        <Text style={styles.section}>Recommended charging stops</Text>
        {stops.map((stop, index) => {
          const key = String(stop.station.id); const options = alternatives[key] ?? [];
          return <View key={`${stop.station.id}-${index}`} style={styles.stopWrap}><View style={styles.stop}><View style={styles.stopNumber}><Text style={styles.stopNumberText}>{index + 1}</Text></View><View style={styles.stopCopy}><Text style={styles.stopName}>{stop.station.name}</Text><Text style={styles.stopMeta}>{stop.station.address}</Text><View style={styles.badges}><Badge icon="git-compare-outline" text={`${stop.detourKm.toFixed(1)} km detour`} /><Badge icon="flash-outline" text={`${stop.availableSlots} slots`} /><Badge icon="checkmark-circle-outline" text={stop.connectorMatched ? 'Connector match' : 'Mismatch'} /></View><Text style={styles.stopDetail}>{stop.recommendedChargeMinutes} min charge · ETA {stop.etaMinutes} min · est. ₹{stop.estimatedChargingCost.toFixed(0)}</Text><View style={styles.stopActions}><TouchableOpacity style={styles.altButton} onPress={() => void loadAlternatives(stop)}><Text style={styles.altText}>{alternativeLoading === key ? 'Loading…' : options.length ? 'Hide alternatives' : 'Alternatives'}</Text></TouchableOpacity><TouchableOpacity style={styles.bookButton} onPress={() => router.push({ pathname: '/booking/new', params: { stationId: String(stop.station.id), vehicleId: String(selectedVehicle) } })}><Text style={styles.bookText}>Book stop</Text></TouchableOpacity></View></View></View>{options.slice(0, 3).map((option) => <TouchableOpacity key={String(option.station.id)} style={styles.alternative} onPress={() => swap(index, option)}><View style={styles.altCopy}><Text style={styles.altName}>{option.station.name}</Text><Text style={styles.altMeta}>{option.detourKm.toFixed(1)} km · {option.availableSlots} slots · ₹{option.station.pricePerKwh}/kWh</Text></View><Text style={styles.swap}>Swap</Text></TouchableOpacity>)}</View>;
        })}
        {!stops.length ? <View style={styles.direct}><Ionicons name="leaf-outline" size={20} color={Colors.success} /><Text style={styles.directText}>Destination is inside your reserve-adjusted range. No charging stop is required.</Text></View> : null}
        <PrimaryButton title="Open turn-by-turn directions" variant="outline" onPress={() => Linking.openURL(plan.externalMapsUrl)} style={{ marginTop: 14 }} />
      </> : null}
    </ScrollView>
  );
}

function Badge({ icon, text }: { icon: keyof typeof Ionicons.glyphMap; text: string }) { return <View style={styles.badge}><Ionicons name={icon} size={11} color={Colors.primary} /><Text style={styles.badgeText}>{text}</Text></View>; }

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Colors.background }, content: { padding: 18, paddingBottom: 40 },
  eyebrow: { color: Colors.primary, fontSize: 9, fontWeight: '900', letterSpacing: 1.2 }, title: { marginTop: 6, color: Colors.textPrimary, fontSize: 26, fontWeight: '900' }, subtitle: { marginTop: 7, color: Colors.textSecondary, fontSize: 11, lineHeight: 17 },
  label: { marginTop: 18, marginBottom: 8, color: Colors.textPrimary, fontSize: 10.5, fontWeight: '900' }, vehicleRow: { gap: 8 }, vehicle: { minWidth: 190, padding: 12, flexDirection: 'row', gap: 9, alignItems: 'center', borderWidth: 1, borderColor: Colors.border, borderRadius: 13, backgroundColor: Colors.white }, vehicleOn: { borderColor: Colors.primary, backgroundColor: Colors.primary }, vehicleName: { color: Colors.textPrimary, fontSize: 10.5, fontWeight: '900' }, vehicleMeta: { marginTop: 2, color: Colors.textSecondary, fontSize: 8.5 }, onText: { color: Colors.white }, onMeta: { color: '#D1FAE5' },
  inputRow: { minHeight: 48, paddingHorizontal: 12, flexDirection: 'row', alignItems: 'center', gap: 8, borderWidth: 1, borderColor: Colors.border, borderRadius: 13, backgroundColor: Colors.white }, input: { flex: 1, color: Colors.textPrimary, fontSize: 12 }, suggestions: { paddingTop: 7, gap: 6 }, suggestion: { paddingHorizontal: 9, paddingVertical: 6, borderRadius: 9, backgroundColor: Colors.borderSoft }, suggestionText: { color: Colors.textSecondary, fontSize: 8.5, fontWeight: '800' },
  socRow: { flexDirection: 'row', gap: 8 }, soc: { flex: 1, paddingVertical: 11, alignItems: 'center', borderWidth: 1, borderColor: Colors.border, borderRadius: 11, backgroundColor: Colors.white }, socOn: { borderColor: Colors.primary, backgroundColor: Colors.primaryLight }, socText: { color: Colors.textSecondary, fontSize: 10, fontWeight: '800' }, socTextOn: { color: Colors.primary },
  summary: { marginTop: 22, padding: 17, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderRadius: 17, backgroundColor: Colors.white, borderWidth: 1, borderColor: Colors.border }, summaryCopy: { flex: 1, paddingRight: 8 }, summaryKicker: { color: Colors.primary, fontSize: 8.5, fontWeight: '900', letterSpacing: 0.9 }, summaryTitle: { marginTop: 5, color: Colors.textPrimary, fontSize: 18, fontWeight: '900' }, summaryMeta: { marginTop: 4, color: Colors.textSecondary, fontSize: 9 },
  map: { height: 260, marginTop: 12, borderRadius: 17 }, rangeNote: { marginTop: 9, padding: 10, flexDirection: 'row', gap: 7, borderRadius: 11, backgroundColor: Colors.primaryLight }, rangeText: { flex: 1, color: Colors.primaryDark, fontSize: 8.5, lineHeight: 13, fontWeight: '700' }, section: { marginTop: 20, marginBottom: 9, color: Colors.textPrimary, fontSize: 16, fontWeight: '900' },
  stopWrap: { marginBottom: 10 }, stop: { padding: 13, flexDirection: 'row', gap: 10, borderRadius: 15, backgroundColor: Colors.white, borderWidth: 1, borderColor: Colors.border }, stopNumber: { width: 30, height: 30, borderRadius: 10, backgroundColor: Colors.primaryLight, alignItems: 'center', justifyContent: 'center' }, stopNumberText: { color: Colors.primary, fontSize: 10, fontWeight: '900' }, stopCopy: { flex: 1 }, stopName: { color: Colors.textPrimary, fontSize: 12, fontWeight: '900' }, stopMeta: { marginTop: 3, color: Colors.textSecondary, fontSize: 8.5 }, badges: { marginTop: 7, flexDirection: 'row', flexWrap: 'wrap', gap: 5 }, badge: { paddingHorizontal: 6, paddingVertical: 4, flexDirection: 'row', alignItems: 'center', gap: 3, borderRadius: 7, backgroundColor: Colors.primaryLight }, badgeText: { color: Colors.primaryDark, fontSize: 7.5, fontWeight: '800' }, stopDetail: { marginTop: 7, color: Colors.primary, fontSize: 8.5, fontWeight: '800' }, stopActions: { marginTop: 9, flexDirection: 'row', gap: 7 }, altButton: { flex: 1, paddingVertical: 8, alignItems: 'center', borderRadius: 9, borderWidth: 1, borderColor: Colors.primary }, altText: { color: Colors.primary, fontSize: 8.5, fontWeight: '900' }, bookButton: { flex: 1, paddingVertical: 8, alignItems: 'center', borderRadius: 9, backgroundColor: Colors.primary }, bookText: { color: Colors.white, fontSize: 8.5, fontWeight: '900' },
  alternative: { marginTop: 5, marginLeft: 40, padding: 10, flexDirection: 'row', alignItems: 'center', borderRadius: 11, backgroundColor: Colors.primarySoft, borderWidth: 1, borderColor: '#A7F3D0' }, altCopy: { flex: 1 }, altName: { color: Colors.textPrimary, fontSize: 9.5, fontWeight: '900' }, altMeta: { marginTop: 2, color: Colors.textSecondary, fontSize: 7.5 }, swap: { color: Colors.primary, fontSize: 8.5, fontWeight: '900' }, direct: { padding: 13, flexDirection: 'row', gap: 8, borderRadius: 13, backgroundColor: Colors.successLight }, directText: { flex: 1, color: Colors.success, fontSize: 10, lineHeight: 15, fontWeight: '700' },
});
