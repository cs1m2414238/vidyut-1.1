import React from 'react';
import { Alert, ScrollView, StyleSheet, View } from 'react-native';
import { AppHeader } from '../../src/components/AppHeader';
import { ListRow, MetricCard, SectionCard } from '../../src/components/DashboardUI';
import { Colors } from '../../src/constants/colors';

export default function AdminUsersScreen() {
  return <View style={styles.screen}><AppHeader title="Accounts" subtitle="Individuals and protected roles" rightIcon="search-outline" /><ScrollView contentContainerStyle={styles.content}><View style={styles.metrics}><MetricCard icon="people-outline" value="25.4K" label="Individual accounts" /><MetricCard icon="home-outline" value="4.8K" label="Verified hosts" tone="blue" /></View><SectionCard title="Recent accounts" actionLabel="Export"><ListRow icon="person-outline" title="Priyanshu Sharma" subtitle="EV Owner · priyanshu@example.com" status="Active" onPress={() => Alert.alert('Account', 'EV Owner mode only · active · email verified')} /><ListRow icon="swap-horizontal-outline" title="Rahul Sharma" subtitle="EV Owner + Host · overlapping roles" status="Dual" onPress={() => Alert.alert('Dual-role account', 'Allowed modes: EV_USER and HOST. Company access is disallowed.')} /><ListRow icon="person-outline" title="Neha Verma" subtitle="EV Owner · neha@example.com" status="Active" /></SectionCard><SectionCard title="Host applications"><ListRow icon="home-outline" title="Amit Kapoor" subtitle="Identity and charger profile complete" status="Review" /><ListRow icon="time-outline" title="Sakshi Rao" subtitle="Awaiting electrical safety document" status="Pending" /></SectionCard></ScrollView></View>;
}
const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: Colors.background }, content: { padding: 16, paddingBottom: 32 }, metrics: { flexDirection: 'row', justifyContent: 'space-between' } });
