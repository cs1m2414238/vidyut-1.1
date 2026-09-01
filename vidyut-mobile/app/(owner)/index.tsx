import React, { useState } from 'react';
import { FlatList, RefreshControl, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';
import { useRouter } from 'expo-router';
import { useQuery } from '@tanstack/react-query';
import { Ionicons } from '@expo/vector-icons';
import { ChargerCard } from '../../src/components/ChargerCard';
import { SkeletonList } from '../../src/components/SkeletonList';
import { AppHeader } from '../../src/components/AppHeader';
import { Colors } from '../../src/constants/colors';
import { getChargers } from '../../src/features/chargers/charger.api';
import { useAuthStore } from '../../src/features/auth/auth.store';
import { getActiveSessions } from '../../src/features/sessions/session.api';
import { getVehicleWallets } from '../../src/features/wallet/wallet.api';
import { getUnreadNotificationCount } from '../../src/features/notifications/notification.api';

export default function DiscoverScreen() {
  const router = useRouter();
  const user = useAuthStore((state) => state.user);
  const [search, setSearch] = useState('');
  const [availableOnly, setAvailableOnly] = useState(false);
  const { data: chargers = [], isLoading, refetch, isRefetching } = useQuery({ queryKey: ['chargers'], queryFn: getChargers });
  const { data: activeSessions = [] } = useQuery({ queryKey: ['active-sessions'], queryFn: getActiveSessions, refetchInterval: 10000 });
  const { data: vehicleWallets = [] } = useQuery({ queryKey: ['vehicle-wallets'], queryFn: getVehicleWallets });
  const { data: notificationCount = 0 } = useQuery({ queryKey: ['notification-count'], queryFn: getUnreadNotificationCount, refetchInterval: 15000 });
  const activeSession = activeSessions[0];
  const lowWallet = vehicleWallets.find((wallet) => wallet.lowBalance);

  const filtered = chargers.filter((charger) => {
    const matches = `${charger.name} ${charger.hostName} ${charger.address}`.toLowerCase().includes(search.trim().toLowerCase());
    return matches && (!availableOnly || charger.available);
  });

  if (isLoading) return <SkeletonList rows={7} />;

  return (
    <View style={styles.screen}>
      <AppHeader showBrand notificationCount={notificationCount} />
      <FlatList
        data={filtered}
        keyExtractor={(item) => item.id.toString()}
        renderItem={({ item }) => <ChargerCard charger={item} onTap={() => router.push(item.outletPartner ? `/outlet/${item.id}` : `/charger/${item.id}`)} />}
        refreshControl={<RefreshControl refreshing={isRefetching} onRefresh={refetch} colors={[Colors.primary]} tintColor={Colors.primary} />}
        contentContainerStyle={styles.list}
        ListHeaderComponent={
          <View>
            <View style={styles.welcomeRow}>
              <View>
                <Text style={styles.welcome}>Good morning, {user?.name?.split(' ')[0] || 'Driver'}</Text>
                <Text style={styles.welcomeSub}>Where do you want to charge today?</Text>
              </View>
              <TouchableOpacity style={styles.location} onPress={() => router.push('/(owner)/map')}>
                <Ionicons name="location" size={16} color={Colors.primary} />
                <Text style={styles.locationText}>Lucknow</Text>
              </TouchableOpacity>
            </View>

            <View style={styles.searchBar}>
              <Ionicons name="search" size={18} color={Colors.textMuted} />
              <TextInput value={search} onChangeText={setSearch} placeholder="Search location or charger" placeholderTextColor={Colors.textMuted} style={styles.searchInput} />
              {search ? <TouchableOpacity onPress={() => setSearch('')}><Ionicons name="close-circle" size={18} color={Colors.textMuted} /></TouchableOpacity> : null}
            </View>

            <View style={styles.quickGrid}>
              <QuickAction icon="map-outline" label="Find charger" onPress={() => router.push('/(owner)/map')} />
              <QuickAction icon="calendar-outline" label="My bookings" tone="blue" onPress={() => router.push('/(owner)/bookings')} />
              <QuickAction icon="wallet-outline" label="Wallet & auto-recharge" tone="purple" onPress={() => router.push('/(owner)/wallet')} />
              <QuickAction icon="navigate-circle-outline" label="Vidyut Autopilot" tone="purple" onPress={() => router.push('/(owner)/autopilot')} />
              <QuickAction icon="navigate-outline" label="Plan a road trip" tone="blue" onPress={() => router.push('/trip-planner')} />
              <QuickAction icon="car-sport-outline" label="My vehicles" onPress={() => router.push('/vehicle')} />
            </View>

            {user?.profileCompleted === false ? <TouchableOpacity style={styles.profileAlert} onPress={() => router.push('/complete-profile')}><Ionicons name="person-add-outline" size={19} color={Colors.primary} /><View style={{ flex: 1 }}><Text style={styles.alertTitle}>Finish profile setup</Text><Text style={styles.alertText}>Add your 10-digit mobile number for booking support.</Text></View><Ionicons name="chevron-forward" size={17} color={Colors.textMuted} /></TouchableOpacity> : null}
            {lowWallet ? <TouchableOpacity style={styles.walletAlert} onPress={() => router.push('/(owner)/wallet')}><Ionicons name="wallet-outline" size={19} color={Colors.warning} /><Text style={styles.walletAlertText}>{lowWallet.vehicleName} wallet is low at ₹{lowWallet.balance.toFixed(0)}</Text><Text style={styles.topUp}>Top up</Text></TouchableOpacity> : null}

            {activeSession ? <TouchableOpacity style={styles.sessionCard} onPress={() => router.push(`/session/${activeSession.id}`)}>
              <View style={styles.sessionTop}>
                <View><Text style={styles.sessionKicker}>CURRENT SESSION</Text><Text style={styles.sessionStation}>{activeSession.stationName}</Text></View>
                <View style={styles.chargingBadge}><View style={styles.pulse} /><Text style={styles.chargingText}>Charging</Text></View>
              </View>
              <View style={styles.sessionMetrics}>
                <SessionMetric label="Time" value={formatElapsed(activeSession.startedAt)} />
                <SessionMetric label="Energy" value={`${activeSession.energyKwh.toFixed(2)} kWh`} />
                <SessionMetric label="Amount" value={`₹${activeSession.cost.toFixed(2)}`} />
              </View>
              <View style={styles.progressLabel}><Text style={styles.progressValue}>{activeSession.currentBatteryPercent}% estimated</Text><Text style={styles.progressRange}>Tap for live details</Text></View>
              <View style={styles.progressTrack}><View style={[styles.progressFill, { width: `${Math.min(100, activeSession.currentBatteryPercent)}%` }]} /></View>
            </TouchableOpacity> : null}

            <View style={styles.sectionRow}>
              <Text style={styles.sectionTitle}>Nearby chargers</Text>
              <TouchableOpacity style={[styles.filter, availableOnly && styles.filterActive]} onPress={() => setAvailableOnly((current) => !current)}>
                <Ionicons name="options-outline" size={14} color={availableOnly ? Colors.white : Colors.primary} />
                <Text style={[styles.filterText, availableOnly && styles.filterTextActive]}>Available</Text>
              </TouchableOpacity>
            </View>
          </View>
        }
        ListEmptyComponent={<View style={styles.empty}><Ionicons name="search-outline" size={36} color={Colors.textMuted} /><Text style={styles.emptyTitle}>No chargers match</Text><Text style={styles.emptySub}>Try another location or remove the availability filter.</Text></View>}
      />
    </View>
  );
}

function formatElapsed(startedAt: string) {
  const elapsed = Math.max(0, Date.now() - new Date(startedAt).getTime());
  const hours = Math.floor(elapsed / 3600000);
  const minutes = Math.floor(elapsed / 60000) % 60;
  const seconds = Math.floor(elapsed / 1000) % 60;
  return [hours, minutes, seconds].map((value) => String(value).padStart(2, '0')).join(':');
}

function QuickAction({ icon, label, tone = 'green', onPress }: { icon: keyof typeof Ionicons.glyphMap; label: string; tone?: 'green' | 'blue' | 'purple'; onPress: () => void }) {
  const palette = tone === 'blue' ? { color: Colors.blue, background: Colors.blueLight } : tone === 'purple' ? { color: Colors.purple, background: Colors.purpleLight } : { color: Colors.primary, background: Colors.primaryLight };
  return <TouchableOpacity style={styles.quickAction} onPress={onPress}><View style={[styles.quickIcon, { backgroundColor: palette.background }]}><Ionicons name={icon} size={20} color={palette.color} /></View><Text style={styles.quickLabel}>{label}</Text></TouchableOpacity>;
}

function SessionMetric({ label, value }: { label: string; value: string }) {
  return <View><Text style={styles.metricLabel}>{label}</Text><Text style={styles.metricValue}>{value}</Text></View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Colors.background },
  list: { paddingBottom: 24 },
  welcomeRow: { paddingHorizontal: 16, paddingTop: 20, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  welcome: { color: Colors.textPrimary, fontSize: 22, fontWeight: '900', letterSpacing: -.35 },
  welcomeSub: { marginTop: 4, color: Colors.textSecondary, fontSize: 12 },
  location: { flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 9, paddingVertical: 7, borderRadius: 10, backgroundColor: Colors.primaryLight },
  locationText: { color: Colors.primary, fontSize: 10.5, fontWeight: '800' },
  searchBar: { height: 50, marginHorizontal: 16, marginTop: 17, paddingHorizontal: 13, flexDirection: 'row', alignItems: 'center', gap: 8, borderRadius: 14, borderWidth: 1, borderColor: Colors.border, backgroundColor: Colors.white },
  searchInput: { flex: 1, color: Colors.textPrimary, fontSize: 13 },
  quickGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 9, paddingHorizontal: 16, marginTop: 13 },
  quickAction: { width: '48%', flexGrow: 1, minHeight: 82, padding: 10, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: Colors.borderSoft, borderRadius: 16, backgroundColor: Colors.white },
  quickIcon: { width: 38, height: 38, borderRadius: 12, justifyContent: 'center', alignItems: 'center' },
  quickLabel: { marginTop: 7, color: Colors.textPrimary, fontSize: 9.5, fontWeight: '800', textAlign: 'center' },
  sessionCard: { marginHorizontal: 16, marginTop: 14, padding: 16, borderRadius: 19, backgroundColor: Colors.secondary },
  profileAlert: { marginHorizontal: 16, marginTop: 13, padding: 12, flexDirection: 'row', alignItems: 'center', gap: 9, borderWidth: 1, borderColor: '#B6E2CF', borderRadius: 13, backgroundColor: Colors.primarySoft },
  alertTitle: { color: Colors.primaryDark, fontSize: 10.5, fontWeight: '900' },
  alertText: { marginTop: 2, color: Colors.textSecondary, fontSize: 8.5 },
  walletAlert: { marginHorizontal: 16, marginTop: 10, padding: 11, flexDirection: 'row', alignItems: 'center', gap: 8, borderRadius: 12, backgroundColor: Colors.warningLight },
  walletAlertText: { flex: 1, color: '#9A3412', fontSize: 9.5, fontWeight: '700' },
  topUp: { color: '#9A3412', fontSize: 9.5, fontWeight: '900' },
  sessionTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  sessionKicker: { color: '#A7F3D0', fontSize: 8.5, fontWeight: '900', letterSpacing: 1 },
  sessionStation: { marginTop: 4, color: Colors.white, fontSize: 16, fontWeight: '900' },
  chargingBadge: { flexDirection: 'row', alignItems: 'center', gap: 5, paddingHorizontal: 8, paddingVertical: 5, borderRadius: 9, backgroundColor: 'rgba(16,185,129,.15)' },
  pulse: { width: 7, height: 7, borderRadius: 4, backgroundColor: '#34D399' },
  chargingText: { color: '#6EE7B7', fontSize: 9, fontWeight: '900' },
  sessionMetrics: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 18 },
  metricLabel: { color: '#98A2B3', fontSize: 9 },
  metricValue: { marginTop: 4, color: Colors.white, fontSize: 12, fontWeight: '800' },
  progressLabel: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 18 },
  progressValue: { color: '#6EE7B7', fontSize: 10, fontWeight: '800' },
  progressRange: { color: '#98A2B3', fontSize: 9 },
  progressTrack: { height: 6, marginTop: 7, borderRadius: 4, overflow: 'hidden', backgroundColor: '#344054' },
  progressFill: { width: '68%', height: '100%', borderRadius: 4, backgroundColor: '#34D399' },
  sectionRow: { marginTop: 23, marginBottom: 7, paddingHorizontal: 16, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  sectionTitle: { color: Colors.textPrimary, fontSize: 16, fontWeight: '900' },
  filter: { flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 9, paddingVertical: 6, borderRadius: 9, borderWidth: 1, borderColor: Colors.primary, backgroundColor: Colors.white },
  filterActive: { backgroundColor: Colors.primary },
  filterText: { color: Colors.primary, fontSize: 9.5, fontWeight: '800' },
  filterTextActive: { color: Colors.white },
  empty: { margin: 16, padding: 28, alignItems: 'center', borderRadius: 18, backgroundColor: Colors.white },
  emptyTitle: { marginTop: 10, color: Colors.textPrimary, fontSize: 15, fontWeight: '800' },
  emptySub: { marginTop: 4, color: Colors.textSecondary, fontSize: 11, textAlign: 'center' },
});
