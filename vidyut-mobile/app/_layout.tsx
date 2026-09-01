import React, { useEffect } from 'react';
import { Stack } from 'expo-router';
import { QueryClientProvider } from '@tanstack/react-query';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { StatusBar } from 'expo-status-bar';
import { queryClient } from '../src/services/queryClient';
import { useAuthStore } from '../src/features/auth/auth.store';
import { NetworkStatusProvider } from '../src/features/offline/NetworkStatusProvider';
import { useNotificationRuntime } from '../src/features/notifications/notificationRuntime';

export default function RootLayout() {
  const loadPersistedAuth = useAuthStore((s) => s.loadPersistedAuth);
  useNotificationRuntime();

  useEffect(() => {
    loadPersistedAuth();
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      <SafeAreaProvider>
        <NetworkStatusProvider>
        <StatusBar style="dark" />
        <Stack screenOptions={{ headerShown: false }}>
          <Stack.Screen name="index" />
          <Stack.Screen name="onboarding" />
          <Stack.Screen name="complete-profile" options={{ presentation: 'modal' }} />
          <Stack.Screen name="(auth)" />
          <Stack.Screen name="(owner)" />
          <Stack.Screen name="(host)" />
          <Stack.Screen name="(company)" />
          <Stack.Screen name="(admin)" />
          <Stack.Screen
            name="charger/[id]"
            options={{
              headerShown: true,
              title: 'Station Details',
              headerBackTitle: 'Back',
            }}
          />
          <Stack.Screen name="booking/[id]" options={{ headerShown: true, title: 'Booking details' }} />
          <Stack.Screen name="booking/new" options={{ headerShown: true, title: 'Book charging slot' }} />
          <Stack.Screen name="session/[id]" options={{ headerShown: true, title: 'Charging session' }} />
          <Stack.Screen name="vehicle/index" options={{ headerShown: true, title: 'My vehicles' }} />
          <Stack.Screen name="wallet-tag" options={{ headerShown: true, title: 'Vehicle charging tag' }} />
          <Stack.Screen name="trip-planner" options={{ headerShown: true, title: 'Trip planner' }} />
          <Stack.Screen name="active-trip/[bookingId]" options={{ headerShown: true, title: 'Active trip' }} />
          <Stack.Screen name="notifications/index" options={{ headerShown: true, title: 'Notifications' }} />
          <Stack.Screen name="notifications/preferences" options={{ headerShown: true, title: 'Notification preferences' }} />
          <Stack.Screen name="outlet/[id]" options={{ headerShown: true, title: 'Outlet partner' }} />
          <Stack.Screen name="bluetooth/index" options={{ headerShown: true, title: 'Bluetooth & vehicle' }} />
        </Stack>
        </NetworkStatusProvider>
      </SafeAreaProvider>
    </QueryClientProvider>
  );
}
