import React from 'react';
import { Alert, ScrollView, StyleSheet, View } from 'react-native';
import { AppHeader } from '../../src/components/AppHeader';
import { ListRow, SectionCard } from '../../src/components/DashboardUI';
import { Colors } from '../../src/constants/colors';

export default function AdminStationsScreen() {
  const review = (name: string) => Alert.alert(name, 'Review ownership, electrical inspection, photos, tariff and connector details.', [{ text: 'Close', style: 'cancel' }, { text: 'Approve', onPress: () => Alert.alert('Station approved', `${name} can now accept bookings.`) }]);
  return <View style={styles.screen}><AppHeader title="Station moderation" subtitle="Safety and listing approvals" rightIcon="filter-outline" /><ScrollView contentContainerStyle={styles.content}><SectionCard title="Pending safety review"><ListRow icon="flash-outline" title="Cyber Park Fast Charge" subtitle="Gurugram · 4 × CCS2 · 150 kW" status="New" onPress={() => review('Cyber Park Fast Charge')} /><ListRow icon="flash-outline" title="Hazratganj Charge Hub" subtitle="Lucknow · 2 × Type 2 · 22 kW" status="Review" onPress={() => review('Hazratganj Charge Hub')} /><ListRow icon="home-outline" title="Rahul Home Charger" subtitle="Noida · Type 2 · 7.4 kW" status="Host" onPress={() => review('Rahul Home Charger')} /></SectionCard><SectionCard title="Flagged listings"><ListRow icon="warning-outline" title="Airport Terminal 3" subtitle="Repeated offline heartbeat" status="Inspect" danger /><ListRow icon="warning-outline" title="Sector 62 Community Hub" subtitle="Tariff discrepancy reported" status="Review" danger /></SectionCard></ScrollView></View>;
}
const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: Colors.background }, content: { padding: 16, paddingBottom: 32 } });
