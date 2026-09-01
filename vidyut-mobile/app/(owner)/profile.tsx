import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  SafeAreaView,
  Alert,
} from 'react-native';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { Colors } from '../../src/constants/colors';
import { useAuthStore } from '../../src/features/auth/auth.store';
import { applyForHostApi, deleteAccountApi } from '../../src/features/auth/auth.api';
import { AppHeader } from '../../src/components/AppHeader';

export default function ProfileScreen() {
  const router = useRouter();
  const { user, logout } = useAuthStore();

  const handleLogout = async () => {
    Alert.alert('Log Out', 'Are you sure you want to log out?', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Log Out',
        style: 'destructive',
        onPress: async () => {
          await logout();
          router.replace('/(auth)/login');
        },
      },
    ]);
  };

  const applyForHost = async () => {
    try {
      await applyForHostApi(user?.name || 'Vidyut Host');
      Alert.alert('Application submitted', 'Host mode will appear after an administrator verifies your charger profile.');
    } catch (error) {
      Alert.alert('Unable to apply', error instanceof Error ? error.message : 'Please try again.');
    }
  };

  const deleteAccount = () => Alert.alert('Delete account permanently?', 'This disables login immediately and removes personal profile, company and vehicle identifiers. Financial records remain anonymized where legally required.', [
    { text: 'Keep account', style: 'cancel' },
    { text: 'Delete account', style: 'destructive', onPress: async () => {
      try { await deleteAccountApi(); await logout(); router.replace('/(auth)/login'); }
      catch (error) { Alert.alert('Unable to delete account', error instanceof Error ? error.message : 'Please try again.'); }
    } },
  ]);

  return (
    <SafeAreaView style={styles.container}>
      <AppHeader title="Profile" subtitle="Account, vehicles and payments" rightIcon="settings-outline" />

      <ScrollView contentContainerStyle={styles.content}>
        {/* User Card */}
        <View style={styles.userCard}>
          <View style={styles.avatarContainer}>
            <Ionicons name="person" size={40} color={Colors.primary} />
          </View>
          <Text style={styles.userName}>{user?.name || 'EV Owner'}</Text>
          <Text numberOfLines={1} ellipsizeMode="middle" style={styles.userEmail}>{user?.email || 'owner@vidyut.com'}</Text>
          <View style={styles.roleTag}>
            <Text style={styles.roleText}>{user?.role || 'EV OWNER'}</Text>
          </View>
        </View>

        {/* Menu Items */}
        <View style={styles.menuSection}>
          {user?.profileCompleted === false ? <TouchableOpacity style={styles.menuItem} onPress={() => router.push('/complete-profile')}>
            <View style={styles.menuIconWrapper}><Ionicons name="person-add-outline" size={22} color={Colors.warning} /></View>
            <View style={styles.menuTextContainer}><Text style={styles.menuTitle}>Complete profile</Text><Text style={styles.menuSubtitle}>Add mobile and mandatory workspace details</Text></View><Ionicons name="chevron-forward" size={20} color={Colors.textMuted} />
          </TouchableOpacity> : null}
          <TouchableOpacity
            style={styles.menuItem}
            onPress={user?.allowedModes.includes('HOST')
              ? () => router.push('/(auth)/select-mode')
              : applyForHost}
          >
            <View style={styles.menuIconWrapper}>
              <Ionicons name="flash-outline" size={22} color={Colors.primary} />
            </View>
            <View style={styles.menuTextContainer}>
              <Text style={styles.menuTitle}>{user?.allowedModes.includes('HOST') ? 'Switch account mode' : 'List my charger'}</Text>
              <Text style={styles.menuSubtitle}>{user?.allowedModes.includes('HOST') ? 'Continue as EV owner or charger host' : 'Apply to become a verified charger host'}</Text>
            </View>
            <Ionicons name="chevron-forward" size={20} color={Colors.textMuted} />
          </TouchableOpacity>

          <TouchableOpacity style={styles.menuItem} onPress={() => router.push('/vehicle')}>
            <View style={styles.menuIconWrapper}>
              <Ionicons name="car-outline" size={22} color={Colors.primary} />
            </View>
            <View style={styles.menuTextContainer}>
              <Text style={styles.menuTitle}>My vehicles</Text>
              <Text style={styles.menuSubtitle}>Manage registered electric vehicles</Text>
            </View>
            <Ionicons name="chevron-forward" size={20} color={Colors.textMuted} />
          </TouchableOpacity>

          <TouchableOpacity style={styles.menuItem} onPress={() => router.push('/bluetooth')}>
            <View style={styles.menuIconWrapper}>
              <Ionicons name="bluetooth" size={22} color={Colors.primary} />
            </View>
            <View style={styles.menuTextContainer}>
              <Text style={styles.menuTitle}>Bluetooth & live battery</Text>
              <Text style={styles.menuSubtitle}>Pair an EV, use simulator mode and control sessions</Text>
            </View>
            <Ionicons name="chevron-forward" size={20} color={Colors.textMuted} />
          </TouchableOpacity>

          <TouchableOpacity style={styles.menuItem} onPress={() => router.push('/(owner)/wallet')}>
            <View style={styles.menuIconWrapper}>
              <Ionicons name="card-outline" size={22} color={Colors.primary} />
            </View>
            <View style={styles.menuTextContainer}>
              <Text style={styles.menuTitle}>Payment methods</Text>
              <Text style={styles.menuSubtitle}>UPI, Cards and Vidyut Wallet balance</Text>
            </View>
            <Ionicons name="chevron-forward" size={20} color={Colors.textMuted} />
          </TouchableOpacity>

          <TouchableOpacity style={[styles.menuItem, styles.logoutItem]} onPress={handleLogout}>
            <View style={[styles.menuIconWrapper, styles.logoutIconWrapper]}>
              <Ionicons name="log-out-outline" size={22} color={Colors.error} />
            </View>
            <View style={styles.menuTextContainer}>
              <Text style={[styles.menuTitle, { color: Colors.error }]}>Log out</Text>
              <Text style={styles.menuSubtitle}>Sign out of your Vidyut account</Text>
            </View>
          </TouchableOpacity>
          <TouchableOpacity style={[styles.menuItem, styles.logoutItem]} onPress={deleteAccount}>
            <View style={[styles.menuIconWrapper, styles.logoutIconWrapper]}><Ionicons name="trash-outline" size={22} color={Colors.error} /></View>
            <View style={styles.menuTextContainer}><Text style={[styles.menuTitle, { color: Colors.error }]}>Delete account</Text><Text style={styles.menuSubtitle}>Disable login and remove personal identifiers</Text></View>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.background,
  },
  header: {
    height: 56,
    backgroundColor: Colors.white,
    justifyContent: 'center',
    alignItems: 'center',
    borderBottomWidth: 1,
    borderBottomColor: Colors.border,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '800',
    color: Colors.textPrimary,
  },
  content: {
    padding: 16,
  },
  userCard: {
    backgroundColor: Colors.white,
    borderRadius: 20,
    padding: 20,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.border,
    marginBottom: 20,
  },
  avatarContainer: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: Colors.primaryLight,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 12,
  },
  userName: {
    fontSize: 20,
    fontWeight: '800',
    color: Colors.textPrimary,
  },
  userEmail: {
    fontSize: 14,
    color: Colors.textSecondary,
    marginTop: 2,
    maxWidth: '88%',
  },
  roleTag: {
    marginTop: 10,
    backgroundColor: Colors.primaryLight,
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 12,
  },
  roleText: {
    color: Colors.primary,
    fontSize: 11,
    fontWeight: '700',
  },
  menuSection: {
    backgroundColor: Colors.white,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: Colors.border,
    overflow: 'hidden',
  },
  menuItem: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: Colors.border,
  },
  menuIconWrapper: {
    width: 40,
    height: 40,
    borderRadius: 12,
    backgroundColor: Colors.primaryLight,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 14,
  },
  logoutItem: {
    borderBottomWidth: 0,
  },
  logoutIconWrapper: {
    backgroundColor: Colors.errorLight,
  },
  menuTextContainer: {
    flex: 1,
  },
  menuTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: Colors.textPrimary,
  },
  menuSubtitle: {
    fontSize: 12,
    color: Colors.textSecondary,
    marginTop: 2,
  },
});
