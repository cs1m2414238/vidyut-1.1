import React, { useEffect, useState } from 'react';
import { Alert, Platform, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import * as WebBrowser from 'expo-web-browser';
import * as Google from 'expo-auth-session/providers/google';
import Svg, { Path } from 'react-native-svg';
import { Colors } from '../constants/colors';
import { CONFIG } from '../constants/config';

WebBrowser.maybeCompleteAuthSession();

interface GoogleSignInButtonProps {
  onAccessToken: (accessToken: string) => Promise<void>;
  label?: string;
  disabled?: boolean;
}

function GoogleIcon() {
  return (
    <View style={styles.icon}>
      <Svg width={24} height={24} viewBox="0 0 24 24" accessibilityLabel="Google">
        <Path fill="#4285F4" d="M21.35 12.2c0-.74-.06-1.28-.2-1.84H12v3.48h5.37c-.11.86-.69 2.17-1.99 3.04l-.02.12 2.89 2.24.2.02c1.84-1.7 2.9-4.2 2.9-7.06Z" />
        <Path fill="#34A853" d="M12 21.7c2.62 0 4.82-.86 6.45-2.35l-3.07-2.38c-.82.57-1.92.96-3.38.96-2.52 0-4.66-1.7-5.42-4.05l-.11.01-3 2.32-.04.1A9.74 9.74 0 0 0 12 21.7Z" />
        <Path fill="#FBBC05" d="M6.58 13.88A5.9 5.9 0 0 1 6.25 12c0-.65.12-1.28.32-1.88v-.12L3.53 7.65l-.1.05A9.7 9.7 0 0 0 2.3 12c0 1.55.37 3.02 1.13 4.3l3.15-2.42Z" />
        <Path fill="#EA4335" d="M12 6.07c1.82 0 3.05.79 3.75 1.43l2.76-2.7C16.82 3.23 14.62 2.3 12 2.3a9.74 9.74 0 0 0-8.57 5.4l3.14 2.42C7.34 7.77 9.48 6.07 12 6.07Z" />
      </Svg>
    </View>
  );
}

function GoogleButtonContent({ label }: { label: string }) {
  return (
    <>
      <GoogleIcon />
      <Text style={styles.label}>{label}</Text>
      <View style={styles.spacer} />
    </>
  );
}

function hasGoogleClientIdForPlatform() {
  if (Platform.OS === 'android') return Boolean(CONFIG.GOOGLE_ANDROID_CLIENT_ID);
  if (Platform.OS === 'ios') return Boolean(CONFIG.GOOGLE_IOS_CLIENT_ID);
  return Boolean(CONFIG.GOOGLE_WEB_CLIENT_ID);
}

export function GoogleSignInButton(props: GoogleSignInButtonProps) {
  const { label = 'Continue with Google', disabled = false } = props;

  if (!hasGoogleClientIdForPlatform()) {
    const explainSetup = () => Alert.alert(
      'Google setup required',
      `Add the ${Platform.OS} Google OAuth client ID to the mobile environment. Password sign-in is still available.`,
    );

    return (
      <TouchableOpacity
        style={[styles.button, styles.unconfigured, disabled && styles.disabled]}
        onPress={explainSetup}
        disabled={disabled}
        activeOpacity={0.82}
        accessibilityRole="button"
        accessibilityLabel={label}
        accessibilityHint="Google sign-in needs an OAuth client ID before it can be used"
      >
        <GoogleButtonContent label={label} />
      </TouchableOpacity>
    );
  }

  return <ConfiguredGoogleSignInButton {...props} />;
}

function ConfiguredGoogleSignInButton({ onAccessToken, label = 'Continue with Google', disabled = false }: GoogleSignInButtonProps) {
  const [submitting, setSubmitting] = useState(false);
  const [request, response, promptAsync] = Google.useAuthRequest({
    webClientId: CONFIG.GOOGLE_WEB_CLIENT_ID || undefined,
    iosClientId: CONFIG.GOOGLE_IOS_CLIENT_ID || undefined,
    androidClientId: CONFIG.GOOGLE_ANDROID_CLIENT_ID || undefined,
    scopes: ['openid', 'profile', 'email'],
    selectAccount: true,
  }, { scheme: 'vidyut', path: 'oauth' });

  useEffect(() => {
    if (response?.type !== 'success') return;
    const token = response.authentication?.accessToken || response.params.access_token;
    if (!token) {
      Alert.alert('Google sign-in', 'Google did not return an access token.');
      return;
    }
    setSubmitting(true);
    onAccessToken(token).catch((error) => {
      Alert.alert('Google sign-in failed', error instanceof Error ? error.message : 'Please try again.');
    }).finally(() => setSubmitting(false));
  }, [response, onAccessToken]);

  const start = async () => {
    await promptAsync();
  };

  return (
    <TouchableOpacity
      style={[styles.button, (disabled || submitting) && styles.disabled]}
      onPress={start}
      disabled={!request || disabled || submitting}
      activeOpacity={0.82}
      accessibilityRole="button"
      accessibilityLabel={label}
    >
      <GoogleButtonContent label={submitting ? 'Connecting securely…' : label} />
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  button: {
    width: '100%',
    height: 54,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: '#D0D5DD',
    borderRadius: 15,
    backgroundColor: Colors.white,
    shadowColor: '#101828',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: .06,
    shadowRadius: 10,
    elevation: 2,
  },
  disabled: { opacity: .55 },
  unconfigured: { backgroundColor: '#F8FAFC', shadowOpacity: 0, elevation: 0 },
  icon: { width: 24, height: 24, alignItems: 'center', justifyContent: 'center' },
  label: { flex: 1, color: Colors.textPrimary, fontSize: 14, fontWeight: '800', textAlign: 'center' },
  spacer: { width: 24 },
});
