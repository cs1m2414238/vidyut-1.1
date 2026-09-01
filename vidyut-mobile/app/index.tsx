import React, { useEffect } from 'react';
import { View, ActivityIndicator, StyleSheet } from 'react-native';
import { useRouter } from 'expo-router';
import { useAuthStore } from '../src/features/auth/auth.store';
import { Colors } from '../src/constants/colors';
import { tokenStorage } from '../src/services/tokenStorage';

export default function IndexScreen() {
  const router = useRouter();
  const { user, isAuthenticated, isLoading } = useAuthStore();

  useEffect(() => {
    if (isLoading) return;
    let current = true;
    const route = async () => {
      if (!await tokenStorage.hasSeenOnboarding()) {
        if (current) router.replace('/onboarding');
        return;
      }

    if (!isAuthenticated || !user) {
      if (current) router.replace('/(auth)/login');
      return;
    }

    if (user.profileCompleted === false && !await tokenStorage.hasSkippedProfilePrompt(user.id)) {
      if (current) router.replace('/complete-profile');
      return;
    }

    switch (user.activeMode) {
      case 'EV_USER':
        if (current) router.replace('/(owner)');
        break;
      case 'HOST':
        if (current) router.replace('/(host)');
        break;
      case 'COMPANY':
        if (current) router.replace('/(company)');
        break;
      case 'ADMIN':
        if (current) router.replace('/(admin)');
        break;

      default:
        if (current) router.replace('/(owner)');
        break;
    }};
    route();
    return () => { current = false; };
  }, [user, isAuthenticated, isLoading]);

  return (
    <View style={styles.container}>
      <ActivityIndicator size="large" color={Colors.primary} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: Colors.background,
  },
});
