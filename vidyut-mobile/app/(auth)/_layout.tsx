import React from 'react';
import { Redirect, Stack } from 'expo-router';
import { useAuthStore } from '../../src/features/auth/auth.store';
import { LoadingView } from '../../src/components/LoadingView';

export default function AuthLayout() {
  const { isAuthenticated, isLoading } = useAuthStore();

  if (isLoading) return <LoadingView message="Loading your session..." />;
  if (isAuthenticated) return <Redirect href="/" />;

  return (
    <Stack screenOptions={{ headerShown: false }}>
      <Stack.Screen name="login" />
      <Stack.Screen name="register-user" />
      <Stack.Screen name="register-company" />
    </Stack>
  );
}
