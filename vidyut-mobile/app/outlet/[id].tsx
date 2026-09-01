import React, { useState } from 'react';
import { Alert, Image, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as ImagePicker from 'expo-image-picker';
import * as Haptics from 'expo-haptics';
import { Colors } from '../../src/constants/colors';
import { PrimaryButton } from '../../src/components/PrimaryButton';
import { SkeletonList } from '../../src/components/SkeletonList';
import { getMyOutletStats, getMyOutletTier, submitInstitutionId } from '../../src/features/outlets/outlet.api';

export default function OutletPartnerScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const client = useQueryClient();
  const [documentUri, setDocumentUri] = useState('');
  const tier = useQuery({ queryKey: ['outlet-tier', id], queryFn: () => getMyOutletTier(id!), enabled: !!id });
  const stats = useQuery({ queryKey: ['outlet-stats', id], queryFn: () => getMyOutletStats(id!), enabled: !!id });
  const submit = useMutation({
    mutationFn: () => submitInstitutionId(id!, documentUri),
    onSuccess: async () => {
      await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      await client.invalidateQueries({ queryKey: ['outlet-tier', id] });
      Alert.alert('ID submitted', 'An administrator can now review your institution access.');
    },
    onError: (error: Error) => Alert.alert('Upload failed', error.message),
  });

  const pickId = async () => {
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 });
    if (!result.canceled) setDocumentUri(result.assets[0].uri);
  };

  if (tier.isLoading || !tier.data) return <SkeletonList rows={6} />;
  const data = tier.data;
  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <View style={styles.hero}>
        <View style={styles.badge}><Ionicons name="school-outline" size={16} color={Colors.white} /><Text style={styles.badgeText}>OUTLET PARTNER</Text></View>
        <Text style={styles.institution}>{data.institutionName}</Text>
        <Text style={styles.rate}>₹{data.ratePerKwh.toFixed(2)}<Text style={styles.rateUnit}> / kWh</Text></Text>
        <View style={styles.tierPill}><Text style={styles.tierText}>{data.tierName} rate</Text></View>
        <Text style={styles.reason}>{data.reason}</Text>
      </View>

      <Text style={styles.section}>Pricing tiers</Text>
      <View style={styles.table}>
        {data.pricing.map((item) => (
          <View key={item.id} style={[styles.tierRow, item.name === data.tierName && styles.tierRowActive]}>
            <View style={styles.tierCopy}><Text style={styles.tierName}>{item.name}{item.name === data.tierName ? ' · you' : ''}</Text><Text style={styles.tierNote}>{item.eligibilityNote}</Text></View>
            <Text style={styles.tierRate}>₹{item.ratePerKwh.toFixed(2)}</Text>
          </View>
        ))}
      </View>

      {data.idUploadRequired ? (
        <View style={styles.verifyCard}>
          <View style={styles.verifyHead}><Ionicons name="id-card-outline" size={23} color={Colors.purple} /><View style={styles.verifyCopy}><Text style={styles.verifyTitle}>Verify institution ID</Text><Text style={styles.verifyText}>Status: {data.verificationStatus.replace('_', ' ').toLowerCase()}</Text></View></View>
          {documentUri ? <Image source={{ uri: documentUri }} style={styles.preview} /> : null}
          <TouchableOpacity accessibilityRole="button" style={styles.picker} onPress={() => void pickId()}><Ionicons name="image-outline" size={17} color={Colors.purple} /><Text style={styles.pickerText}>{documentUri ? 'Choose another photo' : 'Choose ID photo'}</Text></TouchableOpacity>
          <PrimaryButton title="Submit for review" disabled={!documentUri} loading={submit.isPending} onPress={() => submit.mutate()} style={{ marginTop: 10 }} />
        </View>
      ) : null}

      <Text style={styles.section}>My outlet activity</Text>
      <View style={styles.stats}>
        <Stat label="Sessions" value={String(stats.data?.sessions ?? 0)} />
        <Stat label="Spent" value={`₹${(stats.data?.totalSpend ?? 0).toFixed(0)}`} />
        <Stat label="Saved" value={`₹${(stats.data?.savedVsVisitor ?? 0).toFixed(0)}`} />
      </View>
      <PrimaryButton title="Book this outlet" onPress={() => router.push({ pathname: '/booking/new', params: { stationId: id } })} style={{ marginTop: 17 }} />
    </ScrollView>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return <View style={styles.stat}><Text style={styles.statValue}>{value}</Text><Text style={styles.statLabel}>{label}</Text></View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Colors.background },
  content: { padding: 16, paddingBottom: 40 },
  hero: { padding: 21, borderRadius: 22, backgroundColor: '#4C1D95' },
  badge: { alignSelf: 'flex-start', paddingHorizontal: 9, paddingVertical: 6, flexDirection: 'row', alignItems: 'center', gap: 5, borderRadius: 10, backgroundColor: 'rgba(255,255,255,.14)' },
  badgeText: { color: Colors.white, fontSize: 8, fontWeight: '900', letterSpacing: 1 },
  institution: { marginTop: 16, color: Colors.white, fontSize: 22, fontWeight: '900' },
  rate: { marginTop: 11, color: '#DDD6FE', fontSize: 32, fontWeight: '900' },
  rateUnit: { fontSize: 12 },
  tierPill: { marginTop: 8, alignSelf: 'flex-start', paddingHorizontal: 9, paddingVertical: 5, borderRadius: 9, backgroundColor: '#DDD6FE' },
  tierText: { color: '#4C1D95', fontSize: 9, fontWeight: '900' },
  reason: { marginTop: 11, color: '#EDE9FE', fontSize: 10, lineHeight: 15 },
  section: { marginTop: 21, marginBottom: 9, color: Colors.textPrimary, fontSize: 16, fontWeight: '900' },
  table: { overflow: 'hidden', borderRadius: 16, borderWidth: 1, borderColor: Colors.border, backgroundColor: Colors.white },
  tierRow: { padding: 13, flexDirection: 'row', alignItems: 'center', borderBottomWidth: 1, borderBottomColor: Colors.borderSoft },
  tierRowActive: { backgroundColor: Colors.purpleLight },
  tierCopy: { flex: 1 },
  tierName: { color: Colors.textPrimary, fontSize: 11, fontWeight: '900' },
  tierNote: { marginTop: 3, color: Colors.textSecondary, fontSize: 8.5 },
  tierRate: { color: Colors.purple, fontSize: 13, fontWeight: '900' },
  verifyCard: { marginTop: 15, padding: 15, borderRadius: 17, backgroundColor: Colors.white, borderWidth: 1, borderColor: '#DDD6FE' },
  verifyHead: { flexDirection: 'row', alignItems: 'center', gap: 9 },
  verifyCopy: { flex: 1 },
  verifyTitle: { color: Colors.textPrimary, fontSize: 12, fontWeight: '900' },
  verifyText: { marginTop: 2, color: Colors.textSecondary, fontSize: 9 },
  preview: { width: '100%', height: 145, marginTop: 12, borderRadius: 12 },
  picker: { marginTop: 11, padding: 11, flexDirection: 'row', justifyContent: 'center', gap: 7, borderRadius: 11, backgroundColor: Colors.purpleLight },
  pickerText: { color: Colors.purple, fontSize: 10, fontWeight: '900' },
  stats: { flexDirection: 'row', gap: 8 },
  stat: { flex: 1, padding: 13, alignItems: 'center', borderRadius: 14, backgroundColor: Colors.white, borderWidth: 1, borderColor: Colors.border },
  statValue: { color: Colors.textPrimary, fontSize: 17, fontWeight: '900' },
  statLabel: { marginTop: 3, color: Colors.textSecondary, fontSize: 8.5 },
});
