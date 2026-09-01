import React, { useEffect, useMemo, useState } from 'react';
import { FlatList, SafeAreaView, ScrollView, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import * as Location from 'expo-location';
import { useRouter } from 'expo-router';
import MapView, { Callout, Circle, Marker, PROVIDER_DEFAULT, Region } from 'react-native-maps';
import { useQuery } from '@tanstack/react-query';
import { Colors } from '../../src/constants/colors';
import { searchChargers } from '../../src/features/chargers/charger.api';
import type { Charger } from '../../src/features/chargers/charger.types';
import { getMyVehicles } from '../../src/features/vehicles/vehicle.api';
import { tokenStorage } from '../../src/services/tokenStorage';
import { SkeletonList } from '../../src/components/SkeletonList';

const lucknow: Region = { latitude: 26.8467, longitude: 80.9462, latitudeDelta: 0.08, longitudeDelta: 0.05 };
const statusColor = (status: Charger['status']) => status === 'AVAILABLE' ? Colors.primary : status === 'QUEUE' ? '#F59E0B' : status === 'FULL' ? Colors.error : '#98A2B3';

export default function MapScreen() {
  const router = useRouter();
  const [region, setRegion] = useState(lucknow);
  const [location, setLocation] = useState<{ latitude: number; longitude: number } | null>(null);
  const [search, setSearch] = useState('');
  const [recent, setRecent] = useState<string[]>([]);
  const [listMode, setListMode] = useState(false);
  const [compatibleOnly, setCompatibleOnly] = useState(true);
  const [availableOnly, setAvailableOnly] = useState(false);
  const [outletOnly, setOutletOnly] = useState(false);
  const vehicles = useQuery({ queryKey: ['vehicles'], queryFn: getMyVehicles });
  const vehicle = vehicles.data?.[0];

  useEffect(() => {
    void tokenStorage.getRecentSearches().then(setRecent);
    void Location.requestForegroundPermissionsAsync().then(async (permission) => {
      if (permission.status !== 'granted') return;
      const current = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      const point = { latitude: current.coords.latitude, longitude: current.coords.longitude };
      setLocation(point);
      setRegion({ ...point, latitudeDelta: 0.08, longitudeDelta: 0.05 });
    }).catch(() => undefined);
  }, []);

  const connector = compatibleOnly ? vehicle?.connectorType : undefined;
  const query = useQuery({
    queryKey: ['map-chargers', search, connector, availableOnly, location?.latitude, location?.longitude],
    queryFn: () => searchChargers({
      query: search || undefined,
      connectorType: connector,
      availableOnly,
      lat: location?.latitude,
      lng: location?.longitude,
      radius: 50,
    }),
    refetchInterval: 30000,
  });

  const chargers = useMemo(() => (query.data ?? []).filter((item) => !outletOnly || item.outletPartner), [outletOnly, query.data]);
  const fullRangeKm = useMemo(() => {
    if (!vehicle) return 0;
    if (vehicle.remainingRangeKm && vehicle.batteryPercent) return vehicle.remainingRangeKm * 100 / vehicle.batteryPercent;
    const capacity = Number((vehicle.batteryCapacity || '').replace(/[^0-9.]/g, ''));
    return capacity ? capacity * 6.2 : 0;
  }, [vehicle]);
  const rangeMeters = fullRangeKm * (vehicle?.batteryPercent ?? 70) / 100 * 1000;

  const saveSearch = () => void tokenStorage.saveRecentSearch(search).then(setRecent);
  const open = (item: Charger) => router.push(item.outletPartner ? `/outlet/${item.id}` : `/charger/${item.id}`);

  if (query.isLoading) return <SkeletonList rows={7} />;
  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.toolbar}>
        <View style={styles.search}>
          <Ionicons name="search" size={18} color={Colors.textMuted} />
          <TextInput accessibilityLabel="Search chargers" value={search} onChangeText={setSearch} onSubmitEditing={saveSearch} returnKeyType="search" placeholder="Station, area or city" placeholderTextColor={Colors.textMuted} style={styles.searchInput} />
          {search ? <TouchableOpacity accessibilityLabel="Clear search" onPress={() => setSearch('')}><Ionicons name="close-circle" size={18} color={Colors.textMuted} /></TouchableOpacity> : null}
        </View>
        <TouchableOpacity accessibilityLabel={listMode ? 'Show map' : 'Show list'} style={styles.mode} onPress={() => setListMode(!listMode)}><Ionicons name={listMode ? 'map-outline' : 'list-outline'} size={20} color={Colors.primary} /></TouchableOpacity>
      </View>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filters}>
        <Filter label={vehicle?.connectorType ? `Compatible ${vehicle.connectorType}` : 'Compatible'} active={compatibleOnly} onPress={() => setCompatibleOnly(!compatibleOnly)} />
        <Filter label="Available now" active={availableOnly} onPress={() => setAvailableOnly(!availableOnly)} />
        <Filter label="Outlet partners" active={outletOnly} purple onPress={() => setOutletOnly(!outletOnly)} />
        <TouchableOpacity style={styles.trip} onPress={() => router.push('/trip-planner')}><Ionicons name="navigate-outline" size={14} color={Colors.white} /><Text style={styles.tripText}>Plan trip</Text></TouchableOpacity>
      </ScrollView>
      {!search && recent.length ? <View style={styles.recent}><Text style={styles.recentLabel}>Recent</Text>{recent.slice(0, 3).map((value) => <TouchableOpacity key={value} style={styles.recentChip} onPress={() => setSearch(value)}><Text numberOfLines={1} style={styles.recentText}>{value}</Text></TouchableOpacity>)}</View> : null}
      {listMode ? (
        <FlatList
          data={chargers}
          keyExtractor={(item) => String(item.id)}
          contentContainerStyle={styles.list}
          ListEmptyComponent={<View style={styles.empty}><Ionicons name="map-outline" size={31} color={Colors.textMuted} /><Text style={styles.emptyTitle}>No matching chargers</Text><Text style={styles.emptyText}>Adjust the connector, availability or outlet filter.</Text></View>}
          renderItem={({ item }) => <TouchableOpacity style={styles.card} onPress={() => open(item)}><StationPin item={item} /><View style={styles.cardCopy}><View style={styles.nameRow}><Text style={styles.name}>{item.name}</Text>{item.outletPartner ? <Text style={styles.outletBadge}>OUTLET</Text> : null}</View><Text numberOfLines={1} style={styles.address}>{item.address}</Text><Text style={styles.meta}>{item.connectorType} · {item.powerKw} kW · ₹{item.pricePerKwh}/kWh</Text></View><View><Text style={[styles.live, { color: statusColor(item.status) }]}>{item.status}</Text><Text style={styles.slots}>{item.availableSlots}/{item.totalSlots} free</Text></View></TouchableOpacity>}
        />
      ) : (
        <MapView style={styles.map} provider={PROVIDER_DEFAULT} region={region} onRegionChangeComplete={setRegion} showsUserLocation={!!location} showsMyLocationButton={!!location}>
          {location && rangeMeters > 0 ? <Circle center={location} radius={rangeMeters} strokeColor="rgba(15,143,93,.55)" fillColor="rgba(15,143,93,.08)" strokeWidth={2} /> : null}
          {chargers.map((item) => (
            <Marker key={item.id} coordinate={{ latitude: item.latitude, longitude: item.longitude }}>
              <StationPin item={item} compact />
              <Callout onPress={() => open(item)}><View style={styles.callout}><View style={styles.nameRow}><Text style={styles.name}>{item.name}</Text>{item.outletPartner ? <Text style={styles.outletBadge}>OUTLET</Text> : null}</View><Text style={styles.meta}>{item.connectorType} · {item.powerKw} kW</Text><Text style={[styles.live, { color: statusColor(item.status) }]}>{item.status} · {item.availableSlots} free</Text><Text style={styles.action}>{item.outletPartner ? 'View member pricing →' : 'View and book →'}</Text></View></Callout>
            </Marker>
          ))}
        </MapView>
      )}
      {!listMode ? <View style={styles.legend}><View style={styles.legendItem}><View style={[styles.legendDot, { backgroundColor: Colors.primary }]} /><Text style={styles.legendText}>available</Text></View><View style={styles.legendItem}><View style={[styles.legendDot, { backgroundColor: Colors.purple }]} /><Text style={styles.legendText}>outlet</Text></View><View style={styles.legendItem}><View style={[styles.legendDot, { backgroundColor: Colors.error }]} /><Text style={styles.legendText}>full</Text></View></View> : null}
    </SafeAreaView>
  );
}

function Filter({ label, active, onPress, purple = false }: { label: string; active: boolean; onPress: () => void; purple?: boolean }) {
  return <TouchableOpacity accessibilityRole="button" style={[styles.filter, active && (purple ? styles.filterPurple : styles.filterOn)]} onPress={onPress}><Text style={[styles.filterText, active && (purple ? styles.filterTextPurple : styles.filterTextOn)]}>{label}</Text></TouchableOpacity>;
}

function StationPin({ item, compact = false }: { item: Charger; compact?: boolean }) {
  const color = item.outletPartner ? Colors.purple : statusColor(item.status);
  return <View accessibilityLabel={`${item.outletPartner ? 'Outlet partner' : 'Charging station'} ${item.name}`} style={[compact ? styles.pinCompact : styles.pin, { backgroundColor: color }]}><Ionicons name={item.outletPartner ? 'school' : 'flash'} size={compact ? 16 : 18} color={Colors.white} /></View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Colors.background },
  toolbar: { padding: 12, paddingBottom: 8, flexDirection: 'row', gap: 8, backgroundColor: Colors.white },
  search: { flex: 1, minHeight: 44, paddingHorizontal: 12, flexDirection: 'row', alignItems: 'center', gap: 8, borderWidth: 1, borderColor: Colors.border, borderRadius: 13, backgroundColor: Colors.background },
  searchInput: { flex: 1, color: Colors.textPrimary, fontSize: 12 },
  mode: { width: 44, height: 44, borderWidth: 1, borderColor: Colors.border, borderRadius: 13, alignItems: 'center', justifyContent: 'center' },
  filters: { paddingHorizontal: 12, paddingBottom: 10, gap: 6, backgroundColor: Colors.white },
  filter: { paddingHorizontal: 10, paddingVertical: 8, borderWidth: 1, borderColor: Colors.border, borderRadius: 10 },
  filterOn: { borderColor: Colors.primary, backgroundColor: Colors.primaryLight },
  filterPurple: { borderColor: Colors.purple, backgroundColor: Colors.purpleLight },
  filterText: { color: Colors.textSecondary, fontSize: 9, fontWeight: '800' },
  filterTextOn: { color: Colors.primary },
  filterTextPurple: { color: Colors.purple },
  trip: { paddingHorizontal: 10, paddingVertical: 8, flexDirection: 'row', gap: 4, borderRadius: 10, backgroundColor: Colors.primary },
  tripText: { color: Colors.white, fontSize: 9, fontWeight: '900' },
  recent: { paddingHorizontal: 12, paddingBottom: 9, flexDirection: 'row', alignItems: 'center', gap: 6, backgroundColor: Colors.white },
  recentLabel: { color: Colors.textMuted, fontSize: 8, fontWeight: '800' },
  recentChip: { maxWidth: 100, paddingHorizontal: 8, paddingVertical: 6, borderRadius: 8, backgroundColor: Colors.borderSoft },
  recentText: { color: Colors.textSecondary, fontSize: 8.5, fontWeight: '700' },
  map: { flex: 1 },
  list: { padding: 12, paddingBottom: 80 },
  card: { marginBottom: 9, padding: 12, flexDirection: 'row', alignItems: 'center', gap: 10, borderWidth: 1, borderColor: Colors.border, borderRadius: 15, backgroundColor: Colors.white },
  cardCopy: { flex: 1 },
  pin: { width: 40, height: 40, borderRadius: 13, alignItems: 'center', justifyContent: 'center' },
  pinCompact: { width: 34, height: 34, borderRadius: 17, borderWidth: 3, borderColor: Colors.white, alignItems: 'center', justifyContent: 'center' },
  nameRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  name: { flexShrink: 1, color: Colors.textPrimary, fontSize: 11.5, fontWeight: '900' },
  outletBadge: { paddingHorizontal: 5, paddingVertical: 2, borderRadius: 5, overflow: 'hidden', backgroundColor: Colors.purpleLight, color: Colors.purple, fontSize: 6.5, fontWeight: '900' },
  address: { marginTop: 2, color: Colors.textSecondary, fontSize: 8.5 },
  meta: { marginTop: 4, color: Colors.textSecondary, fontSize: 8.5 },
  live: { fontSize: 8.5, fontWeight: '900' },
  slots: { marginTop: 3, color: Colors.textMuted, fontSize: 7.5, textAlign: 'right' },
  callout: { width: 205, padding: 10 },
  action: { marginTop: 7, color: Colors.primary, fontSize: 9, fontWeight: '900' },
  legend: { position: 'absolute', left: 14, bottom: 13, paddingHorizontal: 9, paddingVertical: 7, flexDirection: 'row', gap: 9, borderRadius: 11, backgroundColor: 'rgba(255,255,255,.95)' },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  legendDot: { width: 7, height: 7, borderRadius: 4 },
  legendText: { color: Colors.textSecondary, fontSize: 7.5 },
  empty: { padding: 40, alignItems: 'center' },
  emptyTitle: { marginTop: 10, color: Colors.textPrimary, fontSize: 14, fontWeight: '900' },
  emptyText: { marginTop: 4, color: Colors.textSecondary, fontSize: 10, textAlign: 'center' },
});
