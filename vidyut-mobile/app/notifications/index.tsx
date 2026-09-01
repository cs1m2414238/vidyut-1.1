import React from 'react';
import { FlatList, RefreshControl, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as Haptics from 'expo-haptics';
import { Colors } from '../../src/constants/colors';
import { EmptyState } from '../../src/components/EmptyState';
import { SkeletonList } from '../../src/components/SkeletonList';
import {
  getNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '../../src/features/notifications/notification.api';
import type { VidyutNotification } from '../../src/features/notifications/notification.types';

const iconFor = (type: string): keyof typeof Ionicons.glyphMap => {
  if (type.includes('BOOKING')) return 'calendar-outline';
  if (type.includes('CHARG')) return 'flash-outline';
  if (type.includes('WALLET') || type.includes('PAYMENT')) return 'wallet-outline';
  if (type.includes('REPLAN') || type.includes('DIVERSION') || type.includes('FAULT')) return 'navigate-outline';
  return 'notifications-outline';
};

export default function NotificationsScreen() {
  const router = useRouter();
  const client = useQueryClient();
  const query = useQuery({ queryKey: ['notifications'], queryFn: getNotifications, refetchInterval: 15000 });
  const readAll = useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: () => client.invalidateQueries({ queryKey: ['notifications'] }),
  });

  const open = async (item: VidyutNotification) => {
    if (!item.read) await markNotificationRead(item.id);
    await Haptics.selectionAsync();
    await client.invalidateQueries({ queryKey: ['notifications'] });
    if (item.deepLink) router.push(item.deepLink.replace(/^vidyut:\/\//, '/') as never);
  };

  if (query.isLoading) return <SkeletonList rows={6} />;

  return (
    <View style={styles.screen}>
      <View style={styles.toolbar}>
        <View>
          <Text style={styles.title}>Notification center</Text>
          <Text style={styles.subtitle}>Bookings, charging, wallet and route changes</Text>
        </View>
        <TouchableOpacity accessibilityRole="button" style={styles.settings} onPress={() => router.push('/notifications/preferences')}>
          <Ionicons name="options-outline" size={19} color={Colors.primary} />
        </TouchableOpacity>
      </View>
      <TouchableOpacity disabled={readAll.isPending} style={styles.readAll} onPress={() => readAll.mutate()}>
        <Text style={styles.readAllText}>Mark all as read</Text>
      </TouchableOpacity>
      <FlatList
        data={query.data ?? []}
        keyExtractor={(item) => String(item.id)}
        contentContainerStyle={(query.data?.length ?? 0) ? styles.list : styles.emptyList}
        refreshControl={<RefreshControl refreshing={query.isFetching} onRefresh={() => query.refetch()} colors={[Colors.primary]} />}
        ListEmptyComponent={<EmptyState iconName="notifications-off-outline" title="You're all caught up" subtitle="Booking, charging and route alerts will appear here." />}
        renderItem={({ item }) => (
          <TouchableOpacity accessibilityRole="button" onPress={() => void open(item)} style={[styles.card, !item.read && styles.unread]}>
            <View style={[styles.icon, item.critical && styles.criticalIcon]}>
              <Ionicons name={iconFor(item.type)} size={20} color={item.critical ? Colors.error : Colors.primary} />
            </View>
            <View style={styles.copy}>
              <View style={styles.headingRow}>
                <Text style={styles.cardTitle}>{item.title}</Text>
                {!item.read ? <View accessibilityLabel="Unread" style={styles.dot} /> : null}
              </View>
              <Text style={styles.message}>{item.message}</Text>
              <Text style={styles.time}>{new Date(item.timestamp).toLocaleString('en-IN')}</Text>
            </View>
          </TouchableOpacity>
        )}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Colors.background },
  toolbar: { padding: 16, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  title: { color: Colors.textPrimary, fontSize: 22, fontWeight: '900' },
  subtitle: { marginTop: 3, color: Colors.textSecondary, fontSize: 10 },
  settings: { width: 42, height: 42, alignItems: 'center', justifyContent: 'center', borderRadius: 13, backgroundColor: Colors.white, borderWidth: 1, borderColor: Colors.border },
  readAll: { marginHorizontal: 16, marginBottom: 7, alignSelf: 'flex-end', paddingVertical: 5 },
  readAllText: { color: Colors.primary, fontSize: 10, fontWeight: '900' },
  list: { paddingHorizontal: 16, paddingBottom: 35, gap: 9 },
  emptyList: { flexGrow: 1, justifyContent: 'center', padding: 16 },
  card: { padding: 13, flexDirection: 'row', gap: 11, borderRadius: 16, backgroundColor: Colors.white, borderWidth: 1, borderColor: Colors.border },
  unread: { borderColor: '#A7F3D0', backgroundColor: '#F3FBF7' },
  icon: { width: 42, height: 42, alignItems: 'center', justifyContent: 'center', borderRadius: 13, backgroundColor: Colors.primaryLight },
  criticalIcon: { backgroundColor: Colors.errorLight },
  copy: { flex: 1 },
  headingRow: { flexDirection: 'row', alignItems: 'center', gap: 7 },
  cardTitle: { flex: 1, color: Colors.textPrimary, fontSize: 12, fontWeight: '900' },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: Colors.primary },
  message: { marginTop: 4, color: Colors.textSecondary, fontSize: 10, lineHeight: 15 },
  time: { marginTop: 7, color: Colors.textMuted, fontSize: 8 },
});
