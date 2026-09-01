import React from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Charger } from '../features/chargers/charger.types';
import { Colors } from '../constants/colors';

interface ChargerCardProps { charger: Charger; onTap: () => void; }

export function ChargerCard({ charger, onTap }: ChargerCardProps) {
  return (
    <TouchableOpacity style={styles.card} onPress={onTap} activeOpacity={.82}>
      <View style={[styles.visual, charger.outletPartner && styles.outletVisual]}>
        <View style={[styles.halo, charger.outletPartner && styles.outletHalo]}><Ionicons name={charger.outletPartner ? 'school' : 'flash'} size={25} color={charger.outletPartner ? Colors.purple : Colors.primary} /></View>
        <Text style={[styles.power, charger.outletPartner && styles.outletText]}>{charger.outletPartner ? 'OUTLET' : `${charger.powerKw} kW`}</Text>
      </View>
      <View style={styles.content}>
        <View style={styles.topRow}><Text style={styles.title} numberOfLines={1}>{charger.name || charger.hostName}</Text><View style={[styles.badge, !charger.available && styles.badgeBusy]}><View style={[styles.statusDot, !charger.available && styles.statusDotBusy]} /><Text style={[styles.badgeText, !charger.available && styles.badgeTextBusy]}>{charger.available ? 'Available' : 'Busy'}</Text></View></View>
        <View style={styles.location}><Ionicons name="location-outline" size={13} color={Colors.textMuted} /><Text style={styles.address} numberOfLines={1}>{charger.address}</Text></View>
        <Text style={styles.connector}>{charger.connectorType} · {charger.distance || 'Nearby'}{charger.outletInstitutionName ? ` · ${charger.outletInstitutionName}` : ''}</Text>
        <View style={styles.footer}><Text style={styles.price}>₹{charger.pricePerKwh}<Text style={styles.unit}> / kWh</Text></Text><View style={styles.rating}><Ionicons name="star" size={13} color={Colors.accent} /><Text style={styles.ratingText}>{charger.rating}</Text><Text style={styles.reviews}>({charger.reviewCount || 0})</Text></View><View style={styles.arrow}><Ionicons name="arrow-forward" size={14} color={Colors.primary} /></View></View>
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: { minHeight: 126, marginHorizontal: 16, marginVertical: 6, padding: 12, flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderColor: Colors.borderSoft, borderRadius: 18, backgroundColor: Colors.white, shadowColor: '#101828', shadowOffset: { width: 0, height: 4 }, shadowOpacity: .04, shadowRadius: 10, elevation: 2 },
  visual: { width: 82, height: 98, alignItems: 'center', justifyContent: 'center', borderRadius: 15, backgroundColor: Colors.primarySoft, marginRight: 12 }, halo: { width: 47, height: 47, alignItems: 'center', justifyContent: 'center', borderRadius: 16, backgroundColor: Colors.primaryLight }, power: { marginTop: 7, color: Colors.primary, fontSize: 9.5, fontWeight: '900' },
  outletVisual: { backgroundColor: Colors.purpleLight }, outletHalo: { backgroundColor: '#E9D5FF' }, outletText: { color: Colors.purple },
  content: { flex: 1, minWidth: 0 }, topRow: { flexDirection: 'row', alignItems: 'center' }, title: { flex: 1, marginRight: 6, color: Colors.textPrimary, fontSize: 14, fontWeight: '900' }, badge: { paddingHorizontal: 7, paddingVertical: 4, flexDirection: 'row', alignItems: 'center', gap: 4, borderRadius: 8, backgroundColor: Colors.successLight }, badgeBusy: { backgroundColor: Colors.warningLight }, statusDot: { width: 5, height: 5, borderRadius: 3, backgroundColor: Colors.success }, statusDotBusy: { backgroundColor: Colors.warning }, badgeText: { color: Colors.success, fontSize: 7.5, fontWeight: '900', textTransform: 'uppercase' }, badgeTextBusy: { color: Colors.warning },
  location: { marginTop: 7, flexDirection: 'row', alignItems: 'center', gap: 3 }, address: { flex: 1, color: Colors.textSecondary, fontSize: 10 }, connector: { marginTop: 4, color: Colors.textSecondary, fontSize: 9.5 }, footer: { marginTop: 11, flexDirection: 'row', alignItems: 'center' }, price: { color: Colors.textPrimary, fontSize: 13, fontWeight: '900' }, unit: { color: Colors.textSecondary, fontSize: 8.5, fontWeight: '500' }, rating: { marginLeft: 11, flexDirection: 'row', alignItems: 'center', gap: 3 }, ratingText: { color: Colors.textPrimary, fontSize: 10, fontWeight: '800' }, reviews: { color: Colors.textMuted, fontSize: 8.5 }, arrow: { marginLeft: 'auto', width: 26, height: 26, justifyContent: 'center', alignItems: 'center', borderRadius: 9, backgroundColor: Colors.primaryLight },
});
