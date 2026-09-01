import React from 'react';
import { RefreshControl, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useRouter } from 'expo-router';
import { useQuery } from '@tanstack/react-query';
import { Ionicons } from '@expo/vector-icons';
import { AppHeader } from '../../src/components/AppHeader';
import { DashboardStyles, MetricCard } from '../../src/components/DashboardUI';
import { LoadingView } from '../../src/components/LoadingView';
import { Colors } from '../../src/constants/colors';
import { getCompanyDashboard, getCompanyProfile } from '../../src/features/company/company.api';

function money(value: number) { return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(value || 0); }

export default function CompanyDashboardScreen() {
  const router = useRouter();
  const dashboard = useQuery({ queryKey: ['company-dashboard'], queryFn: getCompanyDashboard });
  const profile = useQuery({ queryKey: ['company-profile'], queryFn: getCompanyProfile });
  const refreshing = dashboard.isRefetching || profile.isRefetching;
  if (dashboard.isLoading || profile.isLoading) return <LoadingView message="Opening company operations..." />;
  const data = dashboard.data;
  return (
    <View style={DashboardStyles.screen}>
      <AppHeader showBrand notificationCount={data?.alerts.length ?? 0} />
      <ScrollView contentContainerStyle={DashboardStyles.content} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { void dashboard.refetch(); void profile.refetch(); }} colors={[Colors.blue]} />}>
        <Text style={DashboardStyles.welcomeTitle}>Welcome, {profile.data?.companyName || 'Company Network'}</Text>
        <Text style={DashboardStyles.welcomeSubtitle}>Live, company-scoped operations and revenue.</Text>

        {(!profile.data?.emailVerified || profile.data.verificationStatus !== 'VERIFIED') ? <TouchableOpacity style={styles.verifyBanner} onPress={() => router.push('/(company)/profile')}><Ionicons name="shield-checkmark-outline" size={20} color={Colors.warning} /><View style={styles.verifyCopy}><Text style={styles.verifyTitle}>Complete company verification</Text><Text style={styles.verifyText}>Email: {profile.data?.emailVerified ? 'verified' : 'pending'} · GST/KYC: {profile.data?.verificationStatus}</Text></View><Ionicons name="chevron-forward" size={17} color={Colors.textMuted} /></TouchableOpacity> : null}

        <View style={DashboardStyles.metricsGrid}>
          <MetricCard icon="business-outline" value={String(data?.totalStations ?? 0)} label="Total stations" tone="blue" />
          <MetricCard icon="flash-outline" value={String(data?.totalChargers ?? 0)} label="Total chargers" />
          <MetricCard icon="pie-chart-outline" value={`${data?.utilizationRate ?? 0}%`} label="Utilization" tone="purple" />
          <MetricCard icon="cash-outline" value={money(data?.revenue ?? 0)} label="Network revenue" tone="amber" />
        </View>

        <View style={styles.liveCard}>
          <View style={styles.cardHead}><View><Text style={styles.cardTitle}>Live operations</Text><Text style={styles.cardSub}>Sessions, queues and delivered energy</Text></View><View style={styles.liveBadge}><View style={styles.liveDot} /><Text style={styles.liveText}>LIVE</Text></View></View>
          <View style={styles.liveGrid}><LiveStat label="Active sessions" value={data?.activeSessions ?? 0} /><LiveStat label="Queue" value={data?.queueCount ?? 0} /><LiveStat label="Energy" value={`${data?.energyDeliveredKwh ?? 0} kWh`} /><LiveStat label="Occupancy" value={`${data?.occupancyPercent ?? 0}%`} /></View>
          <Health label="Online chargers" value={data?.onlineChargers ?? 0} total={data?.totalChargers ?? 0} color={Colors.primary} />
          <Health label="Charging now" value={data?.busyChargers ?? 0} total={data?.totalChargers ?? 0} color={Colors.blue} />
          <Health label="Fault / offline" value={data?.faults ?? 0} total={data?.totalChargers ?? 0} color={Colors.error} />
        </View>

        <View style={styles.alertCard}>
          <View style={styles.cardHead}><View><Text style={styles.cardTitle}>Fault alerts</Text><Text style={styles.cardSub}>Prioritized by charger health</Text></View><TouchableOpacity onPress={() => router.push('/(company)/bookings')}><Text style={styles.link}>Operations</Text></TouchableOpacity></View>
          {(data?.alerts ?? []).map((alert) => <View key={alert.chargerId} style={styles.alertRow}><View style={styles.alertIcon}><Ionicons name="warning-outline" size={18} color={Colors.error} /></View><View style={styles.alertCopy}><Text style={styles.alertTitle}>{alert.chargerCode}</Text><Text style={styles.alertSub}>{alert.station} · {alert.message}</Text></View><Text style={styles.healthScore}>{alert.healthScore}%</Text></View>)}
          {!data?.alerts.length ? <View style={styles.empty}><Ionicons name="checkmark-circle-outline" size={25} color={Colors.success} /><Text style={styles.emptyText}>No critical charger alerts.</Text></View> : null}
        </View>
      </ScrollView>
    </View>
  );
}

function LiveStat({ label, value }: { label: string; value: string | number }) { return <View style={styles.liveStat}><Text style={styles.liveLabel}>{label}</Text><Text style={styles.liveValue}>{value}</Text></View>; }
function Health({ label, value, total, color }: { label: string; value: number; total: number; color: string }) { const width = total ? `${Math.round(value * 100 / total)}%` as `${number}%` : '0%'; return <View style={styles.health}><View style={styles.healthTop}><Text style={styles.healthLabel}>{label}</Text><Text style={styles.healthValue}>{value}</Text></View><View style={styles.healthTrack}><View style={[styles.healthFill, { width, backgroundColor: color }]} /></View></View>; }

const styles = StyleSheet.create({
  verifyBanner: { marginBottom: 13, padding: 13, flexDirection: 'row', alignItems: 'center', gap: 9, borderWidth: 1, borderColor: '#FEDF89', borderRadius: 15, backgroundColor: '#FFFAEB' }, verifyCopy: { flex: 1 }, verifyTitle: { color: '#93370D', fontSize: 11, fontWeight: '800' }, verifyText: { marginTop: 3, color: '#B54708', fontSize: 8.5 },
  liveCard: { padding: 16, borderWidth: 1, borderColor: Colors.borderSoft, borderRadius: 18, backgroundColor: Colors.white }, cardHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, cardTitle: { color: Colors.textPrimary, fontSize: 15, fontWeight: '900' }, cardSub: { marginTop: 3, color: Colors.textSecondary, fontSize: 9 }, liveBadge: { paddingHorizontal: 8, paddingVertical: 5, flexDirection: 'row', alignItems: 'center', gap: 4, borderRadius: 9, backgroundColor: Colors.primaryLight }, liveDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: Colors.success }, liveText: { color: Colors.primary, fontSize: 8, fontWeight: '900' },
  liveGrid: { marginTop: 14, flexDirection: 'row', flexWrap: 'wrap', gap: 8 }, liveStat: { width: '48%', flexGrow: 1, padding: 11, borderRadius: 12, backgroundColor: Colors.background }, liveLabel: { color: Colors.textSecondary, fontSize: 8.5 }, liveValue: { marginTop: 4, color: Colors.textPrimary, fontSize: 13, fontWeight: '900' }, health: { marginTop: 12 }, healthTop: { flexDirection: 'row', justifyContent: 'space-between' }, healthLabel: { color: Colors.textSecondary, fontSize: 9 }, healthValue: { color: Colors.textPrimary, fontSize: 9, fontWeight: '800' }, healthTrack: { height: 6, marginTop: 5, borderRadius: 4, overflow: 'hidden', backgroundColor: Colors.borderSoft }, healthFill: { height: '100%', borderRadius: 4 },
  alertCard: { marginTop: 14, padding: 16, borderWidth: 1, borderColor: Colors.borderSoft, borderRadius: 18, backgroundColor: Colors.white }, link: { color: Colors.blue, fontSize: 9.5, fontWeight: '800' }, alertRow: { minHeight: 59, flexDirection: 'row', alignItems: 'center', gap: 9, borderBottomWidth: 1, borderBottomColor: Colors.borderSoft }, alertIcon: { width: 34, height: 34, borderRadius: 10, alignItems: 'center', justifyContent: 'center', backgroundColor: Colors.errorLight }, alertCopy: { flex: 1 }, alertTitle: { color: Colors.textPrimary, fontSize: 10.5, fontWeight: '800' }, alertSub: { marginTop: 3, color: Colors.textSecondary, fontSize: 8.5 }, healthScore: { color: Colors.error, fontSize: 9.5, fontWeight: '900' }, empty: { minHeight: 100, alignItems: 'center', justifyContent: 'center' }, emptyText: { marginTop: 6, color: Colors.textSecondary, fontSize: 9.5 },
});
