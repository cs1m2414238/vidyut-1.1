import React from 'react';
import { Alert, ScrollView, StyleSheet, Switch, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Colors } from '../../src/constants/colors';
import { SkeletonList } from '../../src/components/SkeletonList';
import { getNotificationPreferences, updateNotificationPreference } from '../../src/features/notifications/notification.api';
import type { NotificationPreference } from '../../src/features/notifications/notification.types';

const label = (value: string) => value.toLowerCase().split('_').map((part) => part[0].toUpperCase() + part.slice(1)).join(' ');

export default function NotificationPreferencesScreen() {
  const client = useQueryClient();
  const query = useQuery({ queryKey: ['notification-preferences'], queryFn: getNotificationPreferences });
  const update = useMutation({
    mutationFn: ({ item, enabled }: { item: NotificationPreference; enabled: boolean }) => updateNotificationPreference(item.type, enabled),
    onSuccess: () => client.invalidateQueries({ queryKey: ['notification-preferences'] }),
    onError: (error: Error) => Alert.alert('Preference not saved', error.message),
  });
  if (query.isLoading) return <SkeletonList rows={7} />;
  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <View style={styles.notice}>
        <Ionicons name="shield-checkmark-outline" size={21} color={Colors.primary} />
        <Text style={styles.noticeText}>Safety-critical diversion and fault alerts always stay on.</Text>
      </View>
      {(query.data ?? []).map((item) => (
        <View key={item.type} style={styles.row}>
          <View style={styles.copy}>
            <Text style={styles.title}>{label(item.type)}</Text>
            <Text style={styles.meta}>{item.critical ? 'Required for charging safety' : 'Push and in-app alerts'}</Text>
          </View>
          <Switch
            accessibilityLabel={`${label(item.type)} notifications`}
            value={item.enabled}
            disabled={item.critical || update.isPending}
            onValueChange={(enabled) => update.mutate({ item, enabled })}
            trackColor={{ false: Colors.border, true: '#86EFAC' }}
            thumbColor={item.enabled ? Colors.primary : Colors.textMuted}
          />
        </View>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Colors.background },
  content: { padding: 16, paddingBottom: 40, gap: 9 },
  notice: { padding: 14, flexDirection: 'row', gap: 9, borderRadius: 15, backgroundColor: Colors.primaryLight },
  noticeText: { flex: 1, color: Colors.primaryDark, fontSize: 10, lineHeight: 15, fontWeight: '800' },
  row: { padding: 14, flexDirection: 'row', alignItems: 'center', borderRadius: 15, backgroundColor: Colors.white, borderWidth: 1, borderColor: Colors.border },
  copy: { flex: 1 },
  title: { color: Colors.textPrimary, fontSize: 11.5, fontWeight: '900' },
  meta: { marginTop: 3, color: Colors.textSecondary, fontSize: 8.5 },
});
