import * as SecureStore from 'expo-secure-store';
import type { User } from '../features/auth/auth.types';

const TOKEN_KEY = 'accessToken';
const USER_KEY = 'userData';
const ONBOARDING_KEY = 'onboardingSeen';
const PROFILE_SKIP_PREFIX = 'profilePromptSkipped:';
const RECENT_SEARCHES_KEY = 'recentChargerSearches';

export const tokenStorage = {
  saveToken: async (token: string): Promise<void> => {
    await SecureStore.setItemAsync(TOKEN_KEY, token);
  },

  getToken: async (): Promise<string | null> => {
    try {
      return await SecureStore.getItemAsync(TOKEN_KEY);
    } catch {
      return null;
    }
  },

  removeToken: async (): Promise<void> => {
    await SecureStore.deleteItemAsync(TOKEN_KEY);
  },

  saveUser: async (user: User): Promise<void> => {
    await SecureStore.setItemAsync(USER_KEY, JSON.stringify(user));
  },

  getUser: async (): Promise<User | null> => {
    try {
      const data = await SecureStore.getItemAsync(USER_KEY);
      return data ? JSON.parse(data) as User : null;
    } catch {
      return null;
    }
  },

  hasSeenOnboarding: async (): Promise<boolean> => {
    try { return (await SecureStore.getItemAsync(ONBOARDING_KEY)) === 'true'; }
    catch { return false; }
  },

  markOnboardingSeen: async (): Promise<void> => {
    await SecureStore.setItemAsync(ONBOARDING_KEY, 'true');
  },

  hasSkippedProfilePrompt: async (userId: string | number): Promise<boolean> => {
    try { return (await SecureStore.getItemAsync(`${PROFILE_SKIP_PREFIX}${userId}`)) === 'true'; }
    catch { return false; }
  },

  skipProfilePrompt: async (userId: string | number): Promise<void> => {
    await SecureStore.setItemAsync(`${PROFILE_SKIP_PREFIX}${userId}`, 'true');
  },

  clearProfilePromptSkip: async (userId: string | number): Promise<void> => {
    await SecureStore.deleteItemAsync(`${PROFILE_SKIP_PREFIX}${userId}`);
  },

  getRecentSearches: async (): Promise<string[]> => {
    try { const value = await SecureStore.getItemAsync(RECENT_SEARCHES_KEY); return value ? JSON.parse(value) : []; }
    catch { return []; }
  },

  saveRecentSearch: async (search: string): Promise<string[]> => {
    const cleaned = search.trim();
    if (!cleaned) return tokenStorage.getRecentSearches();
    const current = await tokenStorage.getRecentSearches();
    const next = [cleaned, ...current.filter((value) => value.toLowerCase() !== cleaned.toLowerCase())].slice(0, 5);
    await SecureStore.setItemAsync(RECENT_SEARCHES_KEY, JSON.stringify(next));
    return next;
  },

  clearAll: async (): Promise<void> => {
    await Promise.all([
      SecureStore.deleteItemAsync(TOKEN_KEY),
      SecureStore.deleteItemAsync(USER_KEY),
    ]);
  },
};
