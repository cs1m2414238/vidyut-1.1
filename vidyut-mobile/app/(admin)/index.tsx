import React from 'react';
import { Alert, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { AppHeader } from '../../src/components/AppHeader';
import { DashboardStyles, ListRow, MetricCard, SectionCard } from '../../src/components/DashboardUI';
import { Colors } from '../../src/constants/colors';
import { useAuthStore } from '../../src/features/auth/auth.store';

export default function AdminOverviewScreen() {
  const router = useRouter();
  const logout = useAuthStore((state) => state.logout);
  const signOut = () => Alert.alert('Sign out', 'Leave the protected admin workspace?', [{ text: 'Cancel', style: 'cancel' }, { text: 'Sign out', style: 'destructive', onPress: async () => { await logout(); router.replace('/(auth)/login'); } }]);
  return (
    <View style={DashboardStyles.screen}>
      <AppHeader title="Platform overview" subtitle="Governance and marketplace health" notificationCount={5} />
      <ScrollView contentContainerStyle={DashboardStyles.content}>
        <View style={styles.banner}><View style={styles.shield}><Ionicons name="shield-checkmark" size={28} color="#D1FADF" /></View><View><Text style={styles.bannerTitle}>Vidyut Admin</Text><Text style={styles.bannerSub}>Protected platform workspace</Text></View></View>
        <View style={DashboardStyles.metricsGrid}><MetricCard icon="business-outline" value="3" label="Company approvals" tone="blue" /><MetricCard icon="flash-outline" value="5" label="Station approvals" tone="amber" /><MetricCard icon="people-outline" value="25.4K" label="Active accounts" /><MetricCard icon="pulse-outline" value="99.8%" label="Platform uptime" tone="purple" /></View>
        <SectionCard title="Approval queue" actionLabel="Review all">
          <ListRow icon="business-outline" title="VoltGrid Mobility Pvt Ltd" subtitle="Company verification · 2 hours ago" status="Review" onPress={() => router.push('/(admin)/companies')} />
          <ListRow icon="flash-outline" title="Cyber Park Fast Charge" subtitle="Station safety review · 4 hours ago" status="Review" onPress={() => router.push('/(admin)/stations')} />
          <ListRow icon="home-outline" title="Host application · Rahul S." subtitle="Individual EV + Host upgrade" status="Pending" onPress={() => Alert.alert('Host application', 'Review identity, address and charger safety documents.')} />
        </SectionCard>
        <TouchableOpacity style={styles.logout} onPress={signOut}><Ionicons name="log-out-outline" size={17} color={Colors.error} /><Text style={styles.logoutText}>Sign out of admin</Text></TouchableOpacity>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: { padding: 17, flexDirection: 'row', alignItems: 'center', borderRadius: 19, backgroundColor: Colors.secondary }, shield: { width: 50, height: 50, marginRight: 13, borderRadius: 16, justifyContent: 'center', alignItems: 'center', backgroundColor: '#1D2939' }, bannerTitle: { color: Colors.white, fontSize: 18, fontWeight: '900' }, bannerSub: { marginTop: 3, color: Colors.textMuted, fontSize: 10 },
  logout: { minHeight: 48, marginTop: 15, flexDirection: 'row', gap: 7, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: '#FECDCA', borderRadius: 14, backgroundColor: '#FFFBFA' }, logoutText: { color: Colors.error, fontSize: 11, fontWeight: '800' },
});
