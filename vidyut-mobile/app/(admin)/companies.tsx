import React from 'react';
import { Alert, ScrollView, StyleSheet, View } from 'react-native';
import { AppHeader } from '../../src/components/AppHeader';
import { ListRow, SectionCard } from '../../src/components/DashboardUI';
import { Colors } from '../../src/constants/colors';

export default function AdminCompaniesScreen() {
  const review = (name: string) => Alert.alert(name, 'Verify company registration, administrator identity, and support details.', [{ text: 'Close', style: 'cancel' }, { text: 'Approve', onPress: () => Alert.alert('Approved', `${name} is now active.`) }]);
  return <View style={styles.screen}><AppHeader title="Company approvals" subtitle="3 awaiting verification" rightIcon="filter-outline" /><ScrollView contentContainerStyle={styles.content}><SectionCard title="Pending companies" actionLabel="Newest first"><ListRow icon="business-outline" title="VoltGrid Mobility Pvt Ltd" subtitle="CIN U40106DL2026PLC184021" status="Review" onPress={() => review('VoltGrid Mobility Pvt Ltd')} /><ListRow icon="business-outline" title="EcoCharge Networks Ltd" subtitle="CIN U31909MH2026PLC210445" status="Review" onPress={() => review('EcoCharge Networks Ltd')} /><ListRow icon="business-outline" title="Rapid Route Energy" subtitle="CIN U40108KA2026PLC118904" status="Review" onPress={() => review('Rapid Route Energy')} /></SectionCard><SectionCard title="Recently approved"><ListRow icon="checkmark-circle-outline" title="Tata Power EV" subtitle="Approved yesterday · 56 stations" status="Active" /><ListRow icon="checkmark-circle-outline" title="Green Miles Charging" subtitle="Approved 3 days ago · 12 stations" status="Active" /></SectionCard></ScrollView></View>;
}
const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: Colors.background }, content: { padding: 16, paddingBottom: 32 } });
