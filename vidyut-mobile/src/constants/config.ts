import Constants from 'expo-constants';
import { Platform } from 'react-native';

const expoDevelopmentHost = Constants.expoConfig?.hostUri?.split(':')[0];
const developmentHost =
  expoDevelopmentHost || (Platform.OS === 'android' ? '10.0.2.2' : 'localhost');

export const CONFIG = {
  API_BASE_URL: (
    process.env.EXPO_PUBLIC_API_BASE_URL || `http://${developmentHost}:8080/api`
  ).replace(/\/+$/, ''),
  TIMEOUT: 10000,
  USE_MOCK_DATA: __DEV__ && process.env.EXPO_PUBLIC_USE_MOCK_DATA === 'true',
  GOOGLE_WEB_CLIENT_ID: process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID || '',
  GOOGLE_IOS_CLIENT_ID: process.env.EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID || '',
  GOOGLE_ANDROID_CLIENT_ID: process.env.EXPO_PUBLIC_GOOGLE_ANDROID_CLIENT_ID || '',
};
