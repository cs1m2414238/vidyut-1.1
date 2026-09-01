import React, { useState } from 'react';
import { Dimensions, FlatList, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Colors } from '../src/constants/colors';
import { tokenStorage } from '../src/services/tokenStorage';

const width = Dimensions.get('window').width;
const slides = [
  { icon: 'location-outline' as const, title: 'Find the right charger', text: 'See live availability, connector compatibility, price and distance before you drive.' },
  { icon: 'calendar-outline' as const, title: 'Book and charge confidently', text: 'Reserve a time, monitor live energy and cost, then keep every receipt in one place.' },
  { icon: 'navigate-outline' as const, title: 'Plan beyond your range', text: 'Build trips around your current battery and divert to a better station when plans change.' },
];

export default function OnboardingScreen() {
  const router = useRouter();
  const [index, setIndex] = useState(0);
  const finish = async () => { await tokenStorage.markOnboardingSeen(); router.replace('/'); };
  return <View style={styles.screen}>
    <View style={styles.top}><Text style={styles.brand}>VIDYUT</Text><TouchableOpacity onPress={finish}><Text style={styles.skip}>Skip</Text></TouchableOpacity></View>
    <FlatList horizontal pagingEnabled scrollEnabled={false} data={slides} keyExtractor={(item) => item.title}
      renderItem={({ item }) => <View style={styles.slide}><View style={styles.visual}><View style={styles.ring}><Ionicons name={item.icon} size={66} color={Colors.primary} /></View></View><Text style={styles.title}>{item.title}</Text><Text style={styles.text}>{item.text}</Text></View>} />
    <View style={styles.footer}><View style={styles.dots}>{slides.map((_, dot) => <View key={dot} style={[styles.dot, dot === index && styles.dotActive]} />)}</View>
      <TouchableOpacity style={styles.next} onPress={() => index === slides.length - 1 ? finish() : setIndex(index + 1)}><Text style={styles.nextText}>{index === slides.length - 1 ? 'Get started' : 'Continue'}</Text><Ionicons name="arrow-forward" size={18} color={Colors.white} /></TouchableOpacity></View>
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Colors.background }, top: { paddingTop: 54, paddingHorizontal: 22, flexDirection: 'row', justifyContent: 'space-between' },
  brand: { color: Colors.textPrimary, fontSize: 20, fontWeight: '900', letterSpacing: 2 }, skip: { color: Colors.textSecondary, fontWeight: '800' },
  slide: { width, paddingHorizontal: 30, alignItems: 'center', justifyContent: 'center' }, visual: { width: 230, height: 230, borderRadius: 70, backgroundColor: Colors.primarySoft, alignItems: 'center', justifyContent: 'center' },
  ring: { width: 150, height: 150, borderRadius: 48, backgroundColor: Colors.white, borderWidth: 1, borderColor: '#C8EBDD', alignItems: 'center', justifyContent: 'center', shadowColor: Colors.primary, shadowOpacity: .12, shadowRadius: 22, elevation: 5 },
  title: { marginTop: 38, color: Colors.textPrimary, fontSize: 27, fontWeight: '900', textAlign: 'center' }, text: { marginTop: 13, maxWidth: 330, color: Colors.textSecondary, fontSize: 14, lineHeight: 22, textAlign: 'center' },
  footer: { paddingHorizontal: 22, paddingBottom: 38 }, dots: { flexDirection: 'row', justifyContent: 'center', gap: 7, marginBottom: 22 }, dot: { width: 7, height: 7, borderRadius: 4, backgroundColor: '#D0D5DD' }, dotActive: { width: 24, backgroundColor: Colors.primary },
  next: { height: 54, borderRadius: 16, backgroundColor: Colors.primary, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 }, nextText: { color: Colors.white, fontSize: 14, fontWeight: '900' },
});
