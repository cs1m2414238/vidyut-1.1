import { useEffect } from 'react';
import { Platform } from 'react-native';
import Constants from 'expo-constants';
import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';
import { router } from 'expo-router';
import { useAuthStore } from '../auth/auth.store';
import { registerPushDevice } from './notification.api';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldPlaySound: true,
    shouldSetBadge: true,
    shouldShowBanner: true,
    shouldShowList: true,
  }),
});

function openNotificationUrl(url: unknown) {
  if (typeof url !== 'string') return;
  const localPath = url.replace(/^vidyut:\/\//, '/');
  router.push(localPath as never);
}

export function useNotificationRuntime() {
  const authenticated = useAuthStore((state) => state.isAuthenticated);

  useEffect(() => {
    const last = Notifications.getLastNotificationResponse();
    if (last?.notification) openNotificationUrl(last.notification.request.content.data?.url);
    const subscription = Notifications.addNotificationResponseReceivedListener((response) => {
      openNotificationUrl(response.notification.request.content.data?.url);
    });
    return () => subscription.remove();
  }, []);

  useEffect(() => {
    if (!authenticated || !Device.isDevice) return;
    void (async () => {
      if (Platform.OS === 'android') {
        await Notifications.setNotificationChannelAsync('vidyut-alerts', {
          name: 'Vidyut alerts',
          importance: Notifications.AndroidImportance.HIGH,
          vibrationPattern: [0, 180, 120, 180],
          lightColor: '#0F8F5D',
        });
      }
      const current = await Notifications.getPermissionsAsync();
      const permission = current.status === 'granted' ? current : await Notifications.requestPermissionsAsync();
      if (permission.status !== 'granted') return;
      const projectId = Constants.expoConfig?.extra?.eas?.projectId ?? Constants.easConfig?.projectId;
      if (!projectId) return;
      const token = await Notifications.getExpoPushTokenAsync({ projectId });
      await registerPushDevice(token.data);
    })().catch(() => undefined);
  }, [authenticated]);
}
