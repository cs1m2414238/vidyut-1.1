import React, { useCallback, useState } from 'react';
import { Alert, KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { InputField } from '../../src/components/InputField';
import { PrimaryButton } from '../../src/components/PrimaryButton';
import { Colors } from '../../src/constants/colors';
import { googleAuthApi, registerHostApi, registerUserApi } from '../../src/features/auth/auth.api';
import { useAuthStore } from '../../src/features/auth/auth.store';
import { GoogleSignInButton } from '../../src/components/GoogleSignInButton';

type IndividualRole = 'EV_USER' | 'HOST';

export default function RegisterUserScreen() {
  const router = useRouter();
  const login = useAuthStore((state) => state.login);
  const [role, setRole] = useState<IndividualRole>('EV_USER');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleGoogle = useCallback(async (accessToken: string) => {
    setIsLoading(true);
    try {
      const response = await googleAuthApi(accessToken, role);
      await login(response.user, response.token);
      router.replace('/');
    } finally { setIsLoading(false); }
  }, [login, role, router]);

  const submit = async () => {
    if (!name.trim() || !email.trim() || !password) {
      Alert.alert('Complete your account', 'Name, email and password are required.');
      return;
    }
    if (password.length < 8) {
      Alert.alert('Choose a stronger password', 'Use at least 8 characters.');
      return;
    }
    const phoneDigits = phone.replace(/\D/g, '');
    if (phoneDigits && phoneDigits.length !== 10) { Alert.alert('Check phone number', 'Use exactly 10 digits, or leave it for profile completion.'); return; }

    setIsLoading(true);
    try {
      const request = { name: name.trim(), email: email.trim().toLowerCase(), phone: phoneDigits || undefined, password };
      const response = role === 'EV_USER' ? await registerUserApi(request) : await registerHostApi(request);
      await login(response.user, response.token);
      router.replace(role === 'EV_USER' ? '/(owner)' : '/(host)');
    } catch (error) {
      Alert.alert('Unable to create account', error instanceof Error ? error.message : 'Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView style={styles.screen} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <TouchableOpacity style={styles.back} onPress={() => router.back()}><Ionicons name="arrow-back" size={20} color={Colors.textPrimary} /></TouchableOpacity>
        <Text style={styles.eyebrow}>INDIVIDUAL ACCOUNT</Text>
        <Text style={styles.title}>Create your Vidyut account</Text>
        <Text style={styles.subtitle}>Choose your first workspace. You can add Host mode later without another login.</Text>

        <View style={styles.segment}>
          <RoleButton active={role === 'EV_USER'} icon="car-sport-outline" label="EV Owner" onPress={() => setRole('EV_USER')} />
          <RoleButton active={role === 'HOST'} icon="home-outline" label="Charger Host" onPress={() => setRole('HOST')} />
        </View>

        <View style={styles.card}>
          <InputField label="Full name" placeholder="Priyanshu Sharma" value={name} onChangeText={setName} autoComplete="name" />
          <InputField label="Email address" placeholder="you@example.com" value={email} onChangeText={setEmail} keyboardType="email-address" autoCapitalize="none" autoComplete="email" />
          <InputField label="Phone number" placeholder="+91 98765 43210" value={phone} onChangeText={setPhone} keyboardType="phone-pad" autoComplete="tel" />
          <InputField label="Password" placeholder="At least 6 characters" value={password} onChangeText={setPassword} secureTextEntry autoComplete="new-password" />
          <PrimaryButton title={role === 'EV_USER' ? 'Create EV Owner account' : 'Create Host account'} onPress={submit} loading={isLoading} />
          <View style={styles.orRow}><View style={styles.orLine} /><Text style={styles.orText}>OR</Text><View style={styles.orLine} /></View>
          <GoogleSignInButton onAccessToken={handleGoogle} disabled={isLoading} />
        </View>

        <TouchableOpacity onPress={() => router.replace('/(auth)/login')}><Text style={styles.loginLink}>Already registered? <Text style={styles.loginStrong}>Sign in</Text></Text></TouchableOpacity>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

function RoleButton({ active, icon, label, onPress }: { active: boolean; icon: keyof typeof Ionicons.glyphMap; label: string; onPress: () => void }) {
  return (
    <TouchableOpacity style={[styles.roleButton, active && styles.roleButtonActive]} onPress={onPress}>
      <Ionicons name={icon} size={18} color={active ? Colors.primary : Colors.textSecondary} />
      <Text style={[styles.roleLabel, active && styles.roleLabelActive]}>{label}</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Colors.background },
  content: { flexGrow: 1, padding: 20, paddingTop: 34, paddingBottom: 36 },
  back: { width: 42, height: 42, borderRadius: 14, borderWidth: 1, borderColor: Colors.border, backgroundColor: Colors.white, justifyContent: 'center', alignItems: 'center', marginBottom: 28 },
  eyebrow: { color: Colors.primary, fontSize: 10, fontWeight: '900', letterSpacing: 1.4 },
  title: { marginTop: 7, color: Colors.textPrimary, fontSize: 28, fontWeight: '900', letterSpacing: -.4 },
  subtitle: { marginTop: 7, color: Colors.textSecondary, fontSize: 12.5, lineHeight: 19 },
  segment: { marginTop: 22, padding: 4, flexDirection: 'row', gap: 5, borderRadius: 15, backgroundColor: '#EDEFF3' },
  roleButton: { flex: 1, minHeight: 45, flexDirection: 'row', gap: 7, justifyContent: 'center', alignItems: 'center', borderRadius: 12 },
  roleButtonActive: { backgroundColor: Colors.white, shadowColor: '#101828', shadowOffset: { width: 0, height: 2 }, shadowOpacity: .07, shadowRadius: 5, elevation: 1 },
  roleLabel: { color: Colors.textSecondary, fontSize: 12, fontWeight: '800' },
  roleLabelActive: { color: Colors.primary },
  card: { marginTop: 16, padding: 18, paddingBottom: 20, borderRadius: 21, borderWidth: 1, borderColor: Colors.borderSoft, backgroundColor: Colors.white },
  loginLink: { marginTop: 20, color: Colors.textSecondary, fontSize: 12, textAlign: 'center' },
  loginStrong: { color: Colors.primary, fontWeight: '800' },
  orRow: { marginVertical: 16, flexDirection: 'row', alignItems: 'center', gap: 10 },
  orLine: { flex: 1, height: 1, backgroundColor: Colors.border },
  orText: { color: Colors.textMuted, fontSize: 9, fontWeight: '900' },
});
