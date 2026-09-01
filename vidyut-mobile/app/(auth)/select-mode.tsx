import React, { useState } from 'react';
import { ActivityIndicator, Alert, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { Colors } from '../../src/constants/colors';
import { switchModeApi } from '../../src/features/auth/auth.api';
import { AccessMode } from '../../src/features/auth/auth.types';
import { useAuthStore } from '../../src/features/auth/auth.store';

const modeMeta: Record<AccessMode, { icon: keyof typeof Ionicons.glyphMap; title: string; subtitle: string }> = {
  EV_USER: { icon: 'car-sport-outline', title: 'EV Owner', subtitle: 'Find chargers, bookings, wallet and vehicles' },
  HOST: { icon: 'home-outline', title: 'Charger Host', subtitle: 'Chargers, reservations, earnings and payouts' },
  COMPANY: { icon: 'business-outline', title: 'Company Network', subtitle: 'Stations, operations, people and analytics' },
  ADMIN: { icon: 'shield-checkmark-outline', title: 'Platform Admin', subtitle: 'Approvals, governance and platform health' },
};

export default function SelectModeScreen() {
  const router = useRouter();
  const { user, login, logout } = useAuthStore();
  const [loadingMode, setLoadingMode] = useState<AccessMode | null>(null);

  const chooseMode = async (mode: AccessMode) => {
    try {
      setLoadingMode(mode);
      const auth = await switchModeApi(mode);
      await login(auth.user, auth.token);
      const routes = { EV_USER: '/(owner)', HOST: '/(host)', COMPANY: '/(company)', ADMIN: '/(admin)' } as const;
      router.replace(routes[mode]);
    } catch (error) {
      Alert.alert('Workspace unavailable', error instanceof Error ? error.message : 'Unable to switch mode.');
    } finally {
      setLoadingMode(null);
    }
  };

  const signOut = async () => {
    await logout();
    router.replace('/(auth)/login');
  };

  return (
    <View style={styles.screen}>
      <View style={styles.brand}><View style={styles.logo}><Ionicons name="flash" size={20} color={Colors.white} /></View><Text style={styles.brandText}>VIDYUT</Text></View>
      <View style={styles.content}>
        <Text style={styles.eyebrow}>SECURE WORKSPACE</Text>
        <Text style={styles.title}>Continue as {user?.name?.split(' ')[0] || 'yourself'}</Text>
        <Text style={styles.subtitle}>Your access token will be limited to the workspace you choose.</Text>

        <View style={styles.choices}>
          {user?.allowedModes.map((mode) => {
            const meta = modeMeta[mode];
            const loading = loadingMode === mode;
            return (
              <TouchableOpacity key={mode} style={styles.choice} onPress={() => chooseMode(mode)} disabled={Boolean(loadingMode)} activeOpacity={.78}>
                <View style={styles.choiceIcon}><Ionicons name={meta.icon} size={23} color={Colors.primary} /></View>
                <View style={styles.choiceCopy}><Text style={styles.choiceTitle}>{loading ? 'Opening workspace…' : meta.title}</Text><Text style={styles.choiceSubtitle}>{meta.subtitle}</Text></View>
                {loading ? <ActivityIndicator color={Colors.primary} /> : <Ionicons name="arrow-forward" size={19} color={Colors.textMuted} />}
              </TouchableOpacity>
            );
          })}
        </View>

        <View style={styles.scopeNote}><Ionicons name="shield-checkmark-outline" size={19} color={Colors.primary} /><Text style={styles.scopeText}>Mode isolation is enforced by the backend, not only by the navigation shown here.</Text></View>
      </View>

      <TouchableOpacity style={styles.signOut} onPress={signOut}><Ionicons name="log-out-outline" size={17} color={Colors.textSecondary} /><Text style={styles.signOutText}>Sign out</Text></TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, padding: 22, paddingTop: 42, paddingBottom: 28, backgroundColor: Colors.background },
  brand: { flexDirection: 'row', alignItems: 'center', gap: 9 },
  logo: { width: 36, height: 36, borderRadius: 11, justifyContent: 'center', alignItems: 'center', backgroundColor: Colors.primary },
  brandText: { color: Colors.textPrimary, fontSize: 19, fontWeight: '900', letterSpacing: 1.5 },
  content: { flex: 1, justifyContent: 'center' },
  eyebrow: { color: Colors.primary, fontSize: 10, fontWeight: '900', letterSpacing: 1.4 },
  title: { marginTop: 8, color: Colors.textPrimary, fontSize: 29, fontWeight: '900', letterSpacing: -.5 },
  subtitle: { marginTop: 7, color: Colors.textSecondary, fontSize: 12.5, lineHeight: 19 },
  choices: { marginTop: 24, gap: 12 },
  choice: { minHeight: 82, flexDirection: 'row', alignItems: 'center', padding: 15, borderWidth: 1, borderColor: Colors.borderSoft, borderRadius: 19, backgroundColor: Colors.white, shadowColor: '#101828', shadowOffset: { width: 0, height: 5 }, shadowOpacity: .045, shadowRadius: 12, elevation: 2 },
  choiceIcon: { width: 48, height: 48, borderRadius: 15, justifyContent: 'center', alignItems: 'center', backgroundColor: Colors.primaryLight, marginRight: 13 },
  choiceCopy: { flex: 1 },
  choiceTitle: { color: Colors.textPrimary, fontSize: 15, fontWeight: '900' },
  choiceSubtitle: { marginTop: 4, color: Colors.textSecondary, fontSize: 10.5, lineHeight: 15 },
  scopeNote: { marginTop: 20, padding: 13, flexDirection: 'row', alignItems: 'center', gap: 9, borderRadius: 14, backgroundColor: Colors.primarySoft },
  scopeText: { flex: 1, color: Colors.textSecondary, fontSize: 10.5, lineHeight: 15 },
  signOut: { alignSelf: 'center', flexDirection: 'row', alignItems: 'center', gap: 6, padding: 10 },
  signOutText: { color: Colors.textSecondary, fontSize: 12, fontWeight: '700' },
});
