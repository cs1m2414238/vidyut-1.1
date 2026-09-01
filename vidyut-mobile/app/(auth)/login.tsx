import React, { useCallback, useState } from 'react';
import {
  Alert,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { InputField } from '../../src/components/InputField';
import { PrimaryButton } from '../../src/components/PrimaryButton';
import { Colors } from '../../src/constants/colors';
import { googleAuthApi, loginApi } from '../../src/features/auth/auth.api';
import { useAuthStore } from '../../src/features/auth/auth.store';
import { GoogleSignInButton } from '../../src/components/GoogleSignInButton';

export default function LoginScreen() {
  const router = useRouter();
  const setAuthLogin = useAuthStore((state) => state.login);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const finishAuth = useCallback(async (response: Awaited<ReturnType<typeof loginApi>>) => {
    await setAuthLogin(response.user, response.token);
    router.replace('/');
  }, [router, setAuthLogin]);

  const handleGoogle = useCallback(async (accessToken: string) => {
    setIsLoading(true);
    try { await finishAuth(await googleAuthApi(accessToken, 'EV_USER')); }
    finally { setIsLoading(false); }
  }, [finishAuth]);

  const handleLogin = async () => {
    if (!email.trim() || !password) {
      Alert.alert('Enter your details', 'Email and password are required to continue.');
      return;
    }

    setIsLoading(true);
    try {
      const response = await loginApi({ email: email.trim().toLowerCase(), password });
      await setAuthLogin(response.user, response.token);

      if (response.user.allowedModes.length > 1) {
        router.replace('/(auth)/select-mode');
        return;
      }

      const routes = {
        EV_USER: '/(owner)',
        HOST: '/(host)',
        COMPANY: '/(company)',
        ADMIN: '/(admin)',
      } as const;
      router.replace(routes[response.activeMode]);
    } catch (error) {
      Alert.alert('Unable to sign in', error instanceof Error ? error.message : 'Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView style={styles.screen} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <View style={styles.brandBlock}>
          <View style={styles.logo}><Ionicons name="flash" size={35} color={Colors.white} /></View>
          <Text style={styles.brand}>VIDYUT</Text>
          <Text style={styles.tagline}>Powering a smarter tomorrow</Text>
        </View>

        <View style={styles.card}>
          <Text style={styles.title}>Welcome back</Text>
          <Text style={styles.subtitle}>Sign in once, then enter your authorized workspace.</Text>

          <View style={styles.form}>
            <InputField
              label="Email address"
              placeholder="you@example.com"
              value={email}
              onChangeText={setEmail}
              keyboardType="email-address"
              autoCapitalize="none"
              autoComplete="email"
              leftIcon={<Ionicons name="mail-outline" size={19} color={Colors.textSecondary} />}
            />
            <InputField
              label="Password"
              placeholder="Enter your password"
              value={password}
              onChangeText={setPassword}
              secureTextEntry={!showPassword}
              autoComplete="current-password"
              leftIcon={<Ionicons name="lock-closed-outline" size={19} color={Colors.textSecondary} />}
            />
            <TouchableOpacity style={styles.passwordToggle} onPress={() => setShowPassword((current) => !current)}>
              <Ionicons name={showPassword ? 'eye-off-outline' : 'eye-outline'} size={16} color={Colors.primary} />
              <Text style={styles.passwordToggleText}>{showPassword ? 'Hide password' : 'Show password'}</Text>
            </TouchableOpacity>

            <PrimaryButton title="Sign in securely" onPress={handleLogin} loading={isLoading} style={styles.signIn} />
          </View>

          <View style={styles.orRow}><View style={styles.orLine} /><Text style={styles.orText}>OR</Text><View style={styles.orLine} /></View>
          <GoogleSignInButton onAccessToken={handleGoogle} disabled={isLoading} />

          <View style={styles.securityNote}>
            <Ionicons name="shield-checkmark-outline" size={18} color={Colors.primary} />
            <Text style={styles.securityText}>Your active token is limited to the selected account mode.</Text>
          </View>

          <View style={styles.divider} />
          <Text style={styles.newText}>New to Vidyut?</Text>
          <View style={styles.registerRow}>
            <TouchableOpacity style={styles.registerChoice} onPress={() => router.push('/(auth)/register-user')}>
              <Ionicons name="person-outline" size={19} color={Colors.primary} />
              <Text style={styles.registerChoiceText}>Individual</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.registerChoice} onPress={() => router.push('/(auth)/register-company')}>
              <Ionicons name="business-outline" size={19} color={Colors.blue} />
              <Text style={styles.registerChoiceText}>Company</Text>
            </TouchableOpacity>
          </View>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Colors.background },
  content: { flexGrow: 1, justifyContent: 'center', paddingHorizontal: 20, paddingVertical: 36 },
  brandBlock: { alignItems: 'center', marginBottom: 24 },
  logo: { width: 66, height: 66, borderRadius: 21, justifyContent: 'center', alignItems: 'center', backgroundColor: Colors.primary, shadowColor: Colors.primary, shadowOffset: { width: 0, height: 8 }, shadowOpacity: .26, shadowRadius: 18, elevation: 6 },
  brand: { marginTop: 14, color: Colors.textPrimary, fontSize: 27, fontWeight: '900', letterSpacing: 2 },
  tagline: { marginTop: 3, color: Colors.textSecondary, fontSize: 12 },
  card: { padding: 20, borderWidth: 1, borderColor: Colors.borderSoft, borderRadius: 24, backgroundColor: Colors.white, shadowColor: '#101828', shadowOffset: { width: 0, height: 8 }, shadowOpacity: .06, shadowRadius: 20, elevation: 3 },
  title: { color: Colors.textPrimary, fontSize: 25, fontWeight: '900' },
  subtitle: { marginTop: 5, color: Colors.textSecondary, fontSize: 12.5, lineHeight: 18 },
  form: { marginTop: 22 },
  passwordToggle: { alignSelf: 'flex-end', flexDirection: 'row', alignItems: 'center', gap: 5, marginTop: -7 },
  passwordToggleText: { color: Colors.primary, fontSize: 11, fontWeight: '700' },
  signIn: { marginTop: 17 },
  orRow: { marginVertical: 17, flexDirection: 'row', alignItems: 'center', gap: 10 },
  orLine: { flex: 1, height: 1, backgroundColor: Colors.border },
  orText: { color: Colors.textMuted, fontSize: 9, fontWeight: '900' },
  securityNote: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 15, padding: 11, borderRadius: 12, backgroundColor: Colors.primarySoft },
  securityText: { flex: 1, color: Colors.textSecondary, fontSize: 10.5, lineHeight: 15 },
  divider: { height: 1, marginVertical: 18, backgroundColor: Colors.borderSoft },
  newText: { color: Colors.textSecondary, fontSize: 11, textAlign: 'center', marginBottom: 10 },
  registerRow: { flexDirection: 'row', gap: 10 },
  registerChoice: { flex: 1, minHeight: 46, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7, borderWidth: 1, borderColor: Colors.border, borderRadius: 13, backgroundColor: Colors.white },
  registerChoiceText: { color: Colors.textPrimary, fontSize: 12, fontWeight: '800' },
});
