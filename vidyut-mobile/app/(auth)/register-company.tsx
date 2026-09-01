import React, { useCallback, useState } from 'react';
import { Alert, KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { InputField } from '../../src/components/InputField';
import { PrimaryButton } from '../../src/components/PrimaryButton';
import { Colors } from '../../src/constants/colors';
import { googleAuthApi, registerCompanyApi } from '../../src/features/auth/auth.api';
import { useAuthStore } from '../../src/features/auth/auth.store';
import { GoogleSignInButton } from '../../src/components/GoogleSignInButton';

export default function RegisterCompanyScreen() {
  const router = useRouter();
  const login = useAuthStore((state) => state.login);
  const [companyName, setCompanyName] = useState('');
  const [contactName, setContactName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [registrationNumber, setRegistrationNumber] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const handleGoogle = useCallback(async (accessToken: string) => {
    setIsLoading(true);
    try { const response = await googleAuthApi(accessToken, 'COMPANY'); await login(response.user, response.token); router.replace('/'); }
    finally { setIsLoading(false); }
  }, [login, router]);

  const submit = async () => {
    if (![contactName, email, password].every((value) => value.trim())) {
      Alert.alert('Complete your account', 'Administrator name, email and password are required.');
      return;
    }
    if (password.length < 8) {
      Alert.alert('Choose a stronger password', 'Use at least 8 characters.');
      return;
    }

    setIsLoading(true);
    try {
      const response = await registerCompanyApi({ companyName: companyName.trim(), contactName: contactName.trim(), email: email.trim().toLowerCase(), phone: phone.trim(), password, registrationNumber: registrationNumber.trim() });
      await login(response.user, response.token);
      router.replace('/(company)');
    } catch (error) {
      Alert.alert('Unable to register company', error instanceof Error ? error.message : 'Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView style={styles.screen} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <TouchableOpacity style={styles.back} onPress={() => router.back()}><Ionicons name="arrow-back" size={20} color={Colors.textPrimary} /></TouchableOpacity>
        <View style={styles.companyIcon}><Ionicons name="business-outline" size={24} color={Colors.blue} /></View>
        <Text style={styles.eyebrow}>COMPANY ACCOUNT</Text>
        <Text style={styles.title}>Build your network workspace</Text>
        <Text style={styles.subtitle}>Company data is isolated from personal EV Owner and Host modes.</Text>

        <View style={styles.card}>
          <InputField label="Company name" placeholder="Tata Power EV" value={companyName} onChangeText={setCompanyName} />
          <InputField label="Registration number / CIN" placeholder="U12345XX2026PLC000000" value={registrationNumber} onChangeText={setRegistrationNumber} autoCapitalize="characters" />
          <InputField label="Administrator name" placeholder="Priyanshu Sharma" value={contactName} onChangeText={setContactName} autoComplete="name" />
          <InputField label="Business email" placeholder="admin@company.com" value={email} onChangeText={setEmail} keyboardType="email-address" autoCapitalize="none" autoComplete="email" />
          <InputField label="Support phone" placeholder="+91 98765 43210" value={phone} onChangeText={setPhone} keyboardType="phone-pad" autoComplete="tel" />
          <InputField label="Password" placeholder="At least 6 characters" value={password} onChangeText={setPassword} secureTextEntry autoComplete="new-password" />
          <PrimaryButton title="Create company workspace" onPress={submit} loading={isLoading} />
          <View style={styles.orRow}><View style={styles.orLine} /><Text style={styles.orText}>OR</Text><View style={styles.orLine} /></View>
          <GoogleSignInButton onAccessToken={handleGoogle} disabled={isLoading} />
        </View>

        <View style={styles.note}><Ionicons name="shield-checkmark-outline" size={18} color={Colors.blue} /><Text style={styles.noteText}>This login will only access Company routes and company-owned resources.</Text></View>
        <TouchableOpacity onPress={() => router.replace('/(auth)/login')}><Text style={styles.loginLink}>Already registered? <Text style={styles.loginStrong}>Sign in</Text></Text></TouchableOpacity>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Colors.background },
  content: { flexGrow: 1, padding: 20, paddingTop: 34, paddingBottom: 38 },
  back: { width: 42, height: 42, borderRadius: 14, borderWidth: 1, borderColor: Colors.border, backgroundColor: Colors.white, justifyContent: 'center', alignItems: 'center', marginBottom: 22 },
  companyIcon: { width: 50, height: 50, borderRadius: 16, justifyContent: 'center', alignItems: 'center', backgroundColor: Colors.blueLight, marginBottom: 16 },
  eyebrow: { color: Colors.blue, fontSize: 10, fontWeight: '900', letterSpacing: 1.4 },
  title: { marginTop: 7, color: Colors.textPrimary, fontSize: 28, fontWeight: '900', letterSpacing: -.4 },
  subtitle: { marginTop: 7, color: Colors.textSecondary, fontSize: 12.5, lineHeight: 19 },
  card: { marginTop: 20, padding: 18, paddingBottom: 20, borderRadius: 21, borderWidth: 1, borderColor: Colors.borderSoft, backgroundColor: Colors.white },
  note: { marginTop: 14, padding: 12, flexDirection: 'row', alignItems: 'center', gap: 8, borderRadius: 13, backgroundColor: Colors.blueLight },
  noteText: { flex: 1, color: Colors.textSecondary, fontSize: 10.5, lineHeight: 15 },
  loginLink: { marginTop: 20, color: Colors.textSecondary, fontSize: 12, textAlign: 'center' },
  loginStrong: { color: Colors.primary, fontWeight: '800' },
  orRow: { marginVertical: 16, flexDirection: 'row', alignItems: 'center', gap: 10 },
  orLine: { flex: 1, height: 1, backgroundColor: Colors.border },
  orText: { color: Colors.textMuted, fontSize: 9, fontWeight: '900' },
});
