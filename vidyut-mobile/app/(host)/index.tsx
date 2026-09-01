import React from 'react';
import { RefreshControl, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { AppHeader } from '../../src/components/AppHeader';
import { DashboardStyles, ListRow, MetricCard, SectionCard } from '../../src/components/DashboardUI';
import { LoadingView } from '../../src/components/LoadingView';
import { Colors } from '../../src/constants/colors';
import { getHostBookings, getHostDashboard, getHostMonitoring, getHostNotifications, getHostProfile } from '../../src/features/host/host.api';

const money = (value = 0) => `₹${Math.round(value).toLocaleString('en-IN')}`;

export default function HostDashboardScreen() {
  const router = useRouter();
  const dashboard = useQuery({ queryKey: ['host-dashboard'], queryFn: getHostDashboard });
  const profile = useQuery({ queryKey: ['host-profile'], queryFn: getHostProfile });
  const bookings = useQuery({ queryKey: ['host-bookings'], queryFn: getHostBookings });
  const monitoring = useQuery({ queryKey: ['host-monitoring'], queryFn: getHostMonitoring });
  const notifications = useQuery({ queryKey: ['host-notifications'], queryFn: getHostNotifications });
  if (dashboard.isLoading || profile.isLoading) return <LoadingView message="Loading your Host business..." />;
  const data = dashboard.data;
  const host = profile.data;
  const upcoming = (bookings.data ?? []).filter(item => item.status === 'PENDING' || item.status === 'CONFIRMED').slice(0, 3);
  const chargers = monitoring.data ?? [];
  const refreshing = dashboard.isRefetching || profile.isRefetching || bookings.isRefetching || monitoring.isRefetching;
  const refresh = () => { void dashboard.refetch(); void profile.refetch(); void bookings.refetch(); void monitoring.refetch(); void notifications.refetch(); };
  return (
    <View style={DashboardStyles.screen}>
      <AppHeader showBrand notificationCount={(notifications.data ?? []).filter(item => !item.read).length} />
      <ScrollView contentContainerStyle={DashboardStyles.content} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} colors={[Colors.primary]} />}>
        <Text style={DashboardStyles.welcomeTitle}>Welcome back, {data?.displayName?.split(' ')[0] || 'Host'}</Text>
        <Text style={DashboardStyles.welcomeSubtitle}>Earn safely from your private charging points.</Text>

        {(!host?.emailVerified || host.verificationStatus !== 'VERIFIED') && <TouchableOpacity style={styles.verify} onPress={() => router.push('/(host)/profile')}><Ionicons name="shield-checkmark-outline" size={21} color={Colors.warning} /><View><Text style={styles.verifyTitle}>Complete Host verification</Text><Text style={styles.verifyText}>{!host?.emailVerified ? 'Verify your email' : `KYC is ${host.verificationStatus.toLowerCase()}`} before accepting bookings.</Text></View><Ionicons name="chevron-forward" size={18} color={Colors.warning} /></TouchableOpacity>}

        <View style={DashboardStyles.metricsGrid}>
          <MetricCard icon="wallet-outline" value={money(data?.todayEarnings)} label="Today's earnings" />
          <MetricCard icon="calendar-outline" value={String(data?.upcomingBookings ?? 0)} label="Upcoming bookings" tone="amber" />
          <MetricCard icon="cash-outline" value={money(data?.monthlyEarnings)} label="This month" tone="purple" />
          <MetricCard icon="star-outline" value={(data?.reputationScore ?? 5).toFixed(1)} label="Host reputation" tone="blue" />
        </View>

        <View style={styles.healthCard}>
          <View style={styles.healthHead}><View><Text style={styles.healthTitle}>Private charger health</Text><Text style={styles.healthSub}>{data?.totalChargers ?? 0} chargers · {data?.onlineChargers ?? 0} online</Text></View><View style={styles.uptime}><Text>{data?.uptimePercent ?? 0}%</Text><Text>UPTIME</Text></View></View>
          <View style={styles.healthStats}><MiniStat value={String(data?.activeSessions ?? 0)} label="Live sessions" /><MiniStat value={`${data?.energyDeliveredKwh ?? 0} kWh`} label="Energy delivered" /><MiniStat value={money(data?.pendingPayout)} label="Pending payout" /></View>
          <TouchableOpacity style={styles.monitorButton} onPress={() => router.push('/(host)/insights')}><Ionicons name="pulse-outline" size={16} color={Colors.primary} /><Text>Open live monitoring</Text></TouchableOpacity>
        </View>

        <SectionCard title="Upcoming bookings" actionLabel="View all" onAction={() => router.push('/(host)/bookings')}>
          {upcoming.map(item => <ListRow key={item.id} icon="person-outline" title={item.customerName} subtitle={`${new Date(item.startTime).toLocaleString('en-IN')} · ${item.stationName}`} value={money(item.totalAmount)} status={item.status} />)}
          {!upcoming.length && <EmptyRow text="No upcoming bookings." />}
        </SectionCard>
        <SectionCard title="Charger monitoring" actionLabel="Manage" onAction={() => router.push('/(host)/stations')}>
          {chargers.slice(0, 3).map(item => <ListRow key={item.id} icon={item.status === 'FAULT' ? 'warning-outline' : 'flash-outline'} title={item.chargerCode} subtitle={`${item.stationName} · ${item.currentPowerKw} kW · ${item.healthScore}% health`} status={item.status} danger={item.status === 'FAULT'} />)}
          {!chargers.length && <EmptyRow text="Add a charger after verification." />}
        </SectionCard>
      </ScrollView>
    </View>
  );
}

function MiniStat({ value, label }: { value: string; label: string }) { return <View style={styles.miniStat}><Text style={styles.miniValue}>{value}</Text><Text style={styles.miniLabel}>{label}</Text></View>; }
function EmptyRow({ text }: { text: string }) { return <View style={styles.empty}><Ionicons name="leaf-outline" size={20} color={Colors.textMuted} /><Text>{text}</Text></View>; }
const styles = StyleSheet.create({
  verify: { marginTop: 15, padding: 13, flexDirection: 'row', alignItems: 'center', gap: 10, borderWidth: 1, borderColor: '#FEDF89', borderRadius: 15, backgroundColor: '#FFFAEB' }, verifyTitle: { color: '#7A2E0E', fontSize: 11.5, fontWeight: '900' }, verifyText: { marginTop: 2, color: '#93370D', fontSize: 9 },
  healthCard: { marginTop: 3, padding: 16, borderWidth: 1, borderColor: Colors.borderSoft, borderRadius: 18, backgroundColor: Colors.white }, healthHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, healthTitle: { color: Colors.textPrimary, fontSize: 15, fontWeight: '900' }, healthSub: { marginTop: 3, color: Colors.textSecondary, fontSize: 10 }, uptime: { alignItems: 'flex-end' }, healthStats: { marginTop: 14, flexDirection: 'row', gap: 7 }, miniStat: { flex: 1, padding: 10, borderRadius: 12, backgroundColor: Colors.primarySoft }, miniValue: { color: Colors.textPrimary, fontSize: 11, fontWeight: '900' }, miniLabel: { marginTop: 3, color: Colors.textMuted, fontSize: 7.5 }, monitorButton: { height: 40, marginTop: 12, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, borderRadius: 11, backgroundColor: Colors.primaryLight },
  empty: { minHeight: 74, alignItems: 'center', justifyContent: 'center', gap: 5 },
});
