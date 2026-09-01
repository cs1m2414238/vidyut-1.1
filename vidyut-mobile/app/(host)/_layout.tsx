import React from 'react';
import { Redirect, Tabs } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { Colors } from '../../src/constants/colors';
import { useAuthStore } from '../../src/features/auth/auth.store';
import { LoadingView } from '../../src/components/LoadingView';

export default function HostLayout() {
  const { user, isAuthenticated, isLoading } = useAuthStore();
  if (isLoading) return <LoadingView message="Loading your host session..." />;
  if (!isAuthenticated || user?.activeMode !== 'HOST') return <Redirect href="/" />;

  return (
    <Tabs screenOptions={{ headerShown: false, tabBarActiveTintColor: Colors.primary, tabBarInactiveTintColor: Colors.textSecondary, tabBarHideOnKeyboard: true, tabBarStyle: { height: 70, paddingTop: 8, paddingBottom: 10, backgroundColor: Colors.white, borderTopColor: Colors.borderSoft }, tabBarLabelStyle: { fontSize: 10.5, fontWeight: '700' } }}>
      <Tabs.Screen name="index" options={{ title: 'Dashboard', tabBarIcon: ({ color, size }) => <Ionicons name="grid-outline" size={size} color={color} /> }} />
      <Tabs.Screen name="stations" options={{ title: 'My Chargers', tabBarIcon: ({ color, size }) => <Ionicons name="flash-outline" size={size} color={color} /> }} />
      <Tabs.Screen name="bookings" options={{ title: 'Bookings', tabBarIcon: ({ color, size }) => <Ionicons name="calendar-outline" size={size} color={color} /> }} />
      <Tabs.Screen name="insights" options={{ title: 'Insights', tabBarIcon: ({ color, size }) => <Ionicons name="analytics-outline" size={size} color={color} /> }} />
      <Tabs.Screen name="profile" options={{ title: 'Profile', tabBarIcon: ({ color, size }) => <Ionicons name="person-outline" size={size} color={color} /> }} />
    </Tabs>
  );
}
