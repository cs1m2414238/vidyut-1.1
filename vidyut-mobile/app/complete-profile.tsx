import React, { useState } from 'react';
import { Alert, KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Colors } from '../src/constants/colors';
import { InputField } from '../src/components/InputField';
import { PrimaryButton } from '../src/components/PrimaryButton';
import { completeProfileApi } from '../src/features/auth/auth.api';
import { useAuthStore } from '../src/features/auth/auth.store';
import { tokenStorage } from '../src/services/tokenStorage';

export default function CompleteProfileScreen() {
  const router = useRouter();
  const user = useAuthStore((state) => state.user);
  const login = useAuthStore((state) => state.login);
  const [name, setName] = useState(user?.name || '');
  const [phone, setPhone] = useState(user?.phone || '');
  const [companyName, setCompanyName] = useState(user?.companyName || '');
  const [registrationNumber, setRegistrationNumber] = useState(user?.registrationNumber || '');
  const [loading, setLoading] = useState(false);
  const mode = user?.activeMode || 'EV_USER';

  const goWorkspace = () => {
    const routes = { EV_USER: '/(owner)', HOST: '/(host)', COMPANY: '/(company)', ADMIN: '/(admin)' } as const;
    router.replace(routes[mode]);
  };
  const skip = async () => { if (user) await tokenStorage.skipProfilePrompt(user.id); goWorkspace(); };
  const save = async () => {
    const digits = phone.replace(/\D/g, '');
    if (!name.trim() || digits.length !== 10) {
      Alert.alert('Check your details', 'Enter your name and a 10-digit mobile number.'); return;
    }
    if (mode === 'COMPANY' && (!companyName.trim() || !registrationNumber.trim())) {
      Alert.alert('Company details required', 'Enter the Company Name and Registration Number/CIN.'); return;
    }
    setLoading(true);
    try {
      const response = await completeProfileApi({ mode, fullName: name.trim(), phone: digits,
        companyName: companyName.trim() || undefined, registrationNumber: registrationNumber.trim().toUpperCase() || undefined,
        hostDisplayName: mode === 'HOST' ? name.trim() : undefined });
      await login(response.user, response.token);
      await tokenStorage.clearProfilePromptSkip(response.user.id);
      goWorkspace();
    } catch (error) { Alert.alert('Could not save profile', error instanceof Error ? error.message : 'Please try again.'); }
    finally { setLoading(false); }
  };

  return <KeyboardAvoidingView style={styles.screen} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
    <View style={styles.modal}>
      <TouchableOpacity accessibilityLabel="Close profile completion" style={styles.close} onPress={skip}><Ionicons name="close" size={22} color={Colors.textSecondary} /></TouchableOpacity>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <View style={styles.icon}><Ionicons name="person-add-outline" size={29} color={Colors.primary} /></View>
        <Text style={styles.eyebrow}>PROFILE COMPLETION</Text><Text style={styles.title}>Make Vidyut work better for you</Text>
        <Text style={styles.copy}>These details improve booking support and billing. You can skip now and finish later from Profile.</Text>
        <View style={styles.role}><Ionicons name={mode === 'COMPANY' ? 'business-outline' : mode === 'HOST' ? 'home-outline' : 'car-sport-outline'} size={18} color={Colors.primary} /><Text style={styles.roleText}>{mode.replace('_', ' ')}</Text></View>
        <View style={styles.form}>
          <InputField label={mode === 'COMPANY' ? 'Contact name' : 'Full name'} value={name} onChangeText={setName} placeholder="Your name" />
          <InputField label="10-digit mobile number" value={phone} onChangeText={setPhone} keyboardType="phone-pad" placeholder="9876543210" />
          {mode === 'COMPANY' ? <><InputField label="Company name" value={companyName} onChangeText={setCompanyName} placeholder="Vidyut Mobility Pvt Ltd" /><InputField label="Registration Number / CIN" value={registrationNumber} onChangeText={setRegistrationNumber} autoCapitalize="characters" placeholder="U12345UP2026PTC000001" /></> : null}
          <PrimaryButton title="Save and continue" onPress={save} loading={loading} />
          <TouchableOpacity style={styles.skipButton} onPress={skip}><Text style={styles.skipText}>Skip for now</Text></TouchableOpacity>
        </View>
      </ScrollView>
    </View>
  </KeyboardAvoidingView>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#EEF3F1', justifyContent: 'flex-end' }, modal: { maxHeight: '94%', borderTopLeftRadius: 30, borderTopRightRadius: 30, backgroundColor: Colors.white, overflow: 'hidden' },
  close: { position: 'absolute', zIndex: 2, top: 18, right: 18, width: 39, height: 39, borderRadius: 13, borderWidth: 1, borderColor: Colors.border, alignItems: 'center', justifyContent: 'center', backgroundColor: Colors.white },
  content: { padding: 24, paddingTop: 30, paddingBottom: 38 }, icon: { width: 58, height: 58, borderRadius: 18, backgroundColor: Colors.primaryLight, alignItems: 'center', justifyContent: 'center' },
  eyebrow: { marginTop: 20, color: Colors.primary, fontSize: 10, fontWeight: '900', letterSpacing: 1.4 }, title: { marginTop: 7, maxWidth: 320, color: Colors.textPrimary, fontSize: 27, fontWeight: '900', lineHeight: 33 },
  copy: { marginTop: 9, maxWidth: 360, color: Colors.textSecondary, fontSize: 13, lineHeight: 20 }, role: { alignSelf: 'flex-start', marginTop: 17, paddingHorizontal: 12, paddingVertical: 9, borderRadius: 12, backgroundColor: Colors.primarySoft, flexDirection: 'row', gap: 7, alignItems: 'center' },
  roleText: { color: Colors.primaryDark, fontSize: 11, fontWeight: '900' }, form: { marginTop: 19 }, skipButton: { height: 48, marginTop: 8, alignItems: 'center', justifyContent: 'center' }, skipText: { color: Colors.textSecondary, fontSize: 13, fontWeight: '800' },
});
