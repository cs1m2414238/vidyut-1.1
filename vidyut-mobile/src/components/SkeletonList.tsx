import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Colors } from '../constants/colors';

export function SkeletonList({ rows = 5 }: { rows?: number }) {
  return (
    <View accessibilityLabel="Loading content" style={styles.container}>
      {Array.from({ length: rows }, (_, index) => (
        <View key={index} style={styles.row}>
          <View style={styles.icon} />
          <View style={styles.copy}>
            <View style={styles.title} />
            <View style={styles.line} />
            <View style={styles.shortLine} />
          </View>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16, gap: 10 },
  row: { padding: 14, flexDirection: 'row', gap: 11, borderRadius: 16, backgroundColor: Colors.white },
  icon: { width: 38, height: 38, borderRadius: 12, backgroundColor: Colors.borderSoft },
  copy: { flex: 1, gap: 7 },
  title: { width: '58%', height: 11, borderRadius: 6, backgroundColor: Colors.border },
  line: { width: '94%', height: 8, borderRadius: 5, backgroundColor: Colors.borderSoft },
  shortLine: { width: '38%', height: 8, borderRadius: 5, backgroundColor: Colors.borderSoft },
});
