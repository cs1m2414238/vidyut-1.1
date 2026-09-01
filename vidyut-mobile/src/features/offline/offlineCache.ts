import AsyncStorage from '@react-native-async-storage/async-storage';

const STATIONS_KEY = '@vidyut/cached-stations/v1';

interface CachedPayload<T> {
  savedAt: string;
  data: T;
}

export async function saveCachedStations<T>(stations: T): Promise<void> {
  const payload: CachedPayload<T> = { savedAt: new Date().toISOString(), data: stations };
  await AsyncStorage.setItem(STATIONS_KEY, JSON.stringify(payload));
}

export async function getCachedStations<T>(): Promise<CachedPayload<T> | null> {
  const value = await AsyncStorage.getItem(STATIONS_KEY);
  if (!value) return null;
  try {
    return JSON.parse(value) as CachedPayload<T>;
  } catch {
    await AsyncStorage.removeItem(STATIONS_KEY);
    return null;
  }
}
