import React from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Colors } from '../constants/colors';
import { useRouter } from 'expo-router';

interface AppHeaderProps {
  title?: string;
  subtitle?: string;
  showBrand?: boolean;
  notificationCount?: number;
  rightIcon?: keyof typeof Ionicons.glyphMap;
  onRightPress?: () => void;
}

export function AppHeader({
  title,
  subtitle,
  showBrand = false,
  notificationCount = 0,
  rightIcon = 'notifications-outline',
  onRightPress,
}: AppHeaderProps) {
  const router = useRouter();
  return (
    <View style={styles.header}>
      <View style={styles.titleRow}>
        {showBrand ? (
          <>
            <View style={styles.logo}><Ionicons name="flash" size={17} color={Colors.white} /></View>
            <Text style={styles.brand}>VIDYUT</Text>
          </>
        ) : (
          <View style={styles.copy}>
            <Text style={styles.title}>{title}</Text>
            {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
          </View>
        )}
      </View>

      <TouchableOpacity
        accessibilityRole="button"
        accessibilityLabel={rightIcon.includes('notification') ? 'Open notifications' : 'Open action'}
        style={styles.action}
        onPress={onRightPress ?? (() => router.push('/notifications'))}
        activeOpacity={0.7}
      >
        <Ionicons name={rightIcon} size={21} color={Colors.textPrimary} />
        {notificationCount > 0 ? (
          <View style={styles.dot}><Text style={styles.dotText}>{notificationCount > 9 ? '9+' : notificationCount}</Text></View>
        ) : null}
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    minHeight: 64,
    paddingHorizontal: 18,
    paddingVertical: 10,
    backgroundColor: Colors.white,
    borderBottomWidth: 1,
    borderBottomColor: Colors.borderSoft,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  titleRow: { flexDirection: 'row', alignItems: 'center', flex: 1 },
  logo: { width: 32, height: 32, borderRadius: 10, backgroundColor: Colors.primary, justifyContent: 'center', alignItems: 'center', marginRight: 9 },
  brand: { color: Colors.textPrimary, fontSize: 20, fontWeight: '900', letterSpacing: 1 },
  copy: { flex: 1 },
  title: { color: Colors.textPrimary, fontSize: 18, fontWeight: '800' },
  subtitle: { color: Colors.textSecondary, fontSize: 11, marginTop: 2 },
  action: { width: 40, height: 40, borderRadius: 13, borderWidth: 1, borderColor: Colors.border, justifyContent: 'center', alignItems: 'center', backgroundColor: Colors.white },
  dot: { position: 'absolute', right: -2, top: -3, minWidth: 17, height: 17, paddingHorizontal: 3, borderRadius: 9, backgroundColor: Colors.error, borderWidth: 2, borderColor: Colors.white, justifyContent: 'center', alignItems: 'center' },
  dotText: { color: Colors.white, fontSize: 8, fontWeight: '900' },
});
