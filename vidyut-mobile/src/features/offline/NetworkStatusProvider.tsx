import React, { createContext, PropsWithChildren, useContext, useEffect, useMemo, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import NetInfo from '@react-native-community/netinfo';
import { Ionicons } from '@expo/vector-icons';
import { Colors } from '../../constants/colors';

interface NetworkState {
  isOffline: boolean;
  hasResolved: boolean;
}

const NetworkContext = createContext<NetworkState>({ isOffline: false, hasResolved: false });

export function NetworkStatusProvider({ children }: PropsWithChildren) {
  const [state, setState] = useState<NetworkState>({ isOffline: false, hasResolved: false });

  useEffect(() => NetInfo.addEventListener((status) => {
    const reachable = status.isConnected !== false && status.isInternetReachable !== false;
    setState({ isOffline: !reachable, hasResolved: true });
  }), []);

  const value = useMemo(() => state, [state]);
  return (
    <NetworkContext.Provider value={value}>
      <View style={styles.root}>
        {state.hasResolved && state.isOffline ? (
          <View accessibilityRole="alert" style={styles.banner}>
            <Ionicons name="cloud-offline-outline" size={15} color="#92400E" />
            <Text style={styles.text}>Offline — showing saved charger data. Booking is unavailable.</Text>
          </View>
        ) : null}
        <View style={styles.content}>{children}</View>
      </View>
    </NetworkContext.Provider>
  );
}

export const useNetworkStatus = () => useContext(NetworkContext);

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: Colors.background },
  content: { flex: 1 },
  banner: {
    paddingHorizontal: 13,
    paddingVertical: 8,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 7,
    backgroundColor: '#FEF3C7',
    borderBottomWidth: 1,
    borderBottomColor: '#FDE68A',
  },
  text: { color: '#92400E', fontSize: 10, fontWeight: '800' },
});
