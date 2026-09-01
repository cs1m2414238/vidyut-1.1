import React from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Colors } from '../constants/colors';

interface MetricCardProps {
  icon: keyof typeof Ionicons.glyphMap;
  value: string;
  label: string;
  tone?: 'green' | 'blue' | 'purple' | 'amber';
  trend?: string;
}

const tones = {
  green: { foreground: Colors.primary, background: Colors.primaryLight },
  blue: { foreground: Colors.blue, background: Colors.blueLight },
  purple: { foreground: Colors.purple, background: Colors.purpleLight },
  amber: { foreground: Colors.accent, background: '#FFF6E5' },
};

export function MetricCard({ icon, value, label, tone = 'green', trend }: MetricCardProps) {
  const palette = tones[tone];
  return (
    <View style={styles.metricCard}>
      <View style={[styles.metricIcon, { backgroundColor: palette.background }]}>
        <Ionicons name={icon} size={18} color={palette.foreground} />
      </View>
      <Text style={styles.metricValue}>{value}</Text>
      <Text style={styles.metricLabel}>{label}</Text>
      {trend ? <Text style={[styles.metricTrend, { color: palette.foreground }]}>{trend}</Text> : null}
    </View>
  );
}

interface SectionCardProps {
  title: string;
  actionLabel?: string;
  onAction?: () => void;
  children: React.ReactNode;
}

export function SectionCard({ title, actionLabel, onAction, children }: SectionCardProps) {
  return (
    <View style={styles.sectionCard}>
      <View style={styles.sectionHead}>
        <Text style={styles.sectionTitle}>{title}</Text>
        {actionLabel ? (
          <TouchableOpacity onPress={onAction}><Text style={styles.sectionAction}>{actionLabel}</Text></TouchableOpacity>
        ) : null}
      </View>
      {children}
    </View>
  );
}

interface ListRowProps {
  icon: keyof typeof Ionicons.glyphMap;
  title: string;
  subtitle: string;
  value?: string;
  status?: string;
  danger?: boolean;
  onPress?: () => void;
}

export function ListRow({ icon, title, subtitle, value, status, danger, onPress }: ListRowProps) {
  const Container = onPress ? TouchableOpacity : View;
  return (
    <Container style={styles.listRow} onPress={onPress} activeOpacity={0.75}>
      <View style={[styles.rowIcon, danger && styles.rowIconDanger]}>
        <Ionicons name={icon} size={17} color={danger ? Colors.error : Colors.primary} />
      </View>
      <View style={styles.rowCopy}>
        <Text style={styles.rowTitle} numberOfLines={1}>{title}</Text>
        <Text style={styles.rowSubtitle} numberOfLines={1}>{subtitle}</Text>
      </View>
      <View style={styles.rowEnd}>
        {value ? <Text style={styles.rowValue}>{value}</Text> : null}
        {status ? <Text style={[styles.rowStatus, danger && styles.rowStatusDanger]}>{status}</Text> : null}
        {onPress ? <Ionicons name="chevron-forward" size={16} color={Colors.textMuted} /> : null}
      </View>
    </Container>
  );
}

export const DashboardStyles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Colors.background },
  content: { padding: 16, paddingBottom: 32 },
  welcomeTitle: { fontSize: 24, fontWeight: '900', color: Colors.textPrimary, letterSpacing: -0.4 },
  welcomeSubtitle: { marginTop: 4, fontSize: 13, color: Colors.textSecondary },
  metricsGrid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', marginTop: 18 },
});

const styles = StyleSheet.create({
  metricCard: { width: '48.2%', minHeight: 132, marginBottom: 12, padding: 15, borderRadius: 17, borderWidth: 1, borderColor: Colors.borderSoft, backgroundColor: Colors.white, shadowColor: '#101828', shadowOffset: { width: 0, height: 3 }, shadowOpacity: 0.035, shadowRadius: 8, elevation: 1 },
  metricIcon: { width: 35, height: 35, borderRadius: 11, justifyContent: 'center', alignItems: 'center' },
  metricValue: { marginTop: 11, color: Colors.textPrimary, fontSize: 20, fontWeight: '900' },
  metricLabel: { marginTop: 2, color: Colors.textSecondary, fontSize: 11 },
  metricTrend: { marginTop: 5, fontSize: 10, fontWeight: '800' },
  sectionCard: { marginTop: 15, overflow: 'hidden', borderWidth: 1, borderColor: Colors.borderSoft, borderRadius: 18, backgroundColor: Colors.white },
  sectionHead: { minHeight: 50, paddingHorizontal: 16, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderBottomWidth: 1, borderBottomColor: Colors.borderSoft },
  sectionTitle: { color: Colors.textPrimary, fontSize: 15, fontWeight: '800' },
  sectionAction: { color: Colors.primary, fontSize: 11, fontWeight: '800' },
  listRow: { minHeight: 67, paddingHorizontal: 14, paddingVertical: 11, flexDirection: 'row', alignItems: 'center', borderBottomWidth: 1, borderBottomColor: Colors.borderSoft },
  rowIcon: { width: 36, height: 36, borderRadius: 11, justifyContent: 'center', alignItems: 'center', backgroundColor: Colors.primaryLight, marginRight: 11 },
  rowIconDanger: { backgroundColor: Colors.errorLight },
  rowCopy: { flex: 1, minWidth: 0 },
  rowTitle: { color: Colors.textPrimary, fontSize: 13, fontWeight: '800' },
  rowSubtitle: { color: Colors.textSecondary, fontSize: 10.5, marginTop: 3 },
  rowEnd: { alignItems: 'flex-end', marginLeft: 9, gap: 3 },
  rowValue: { color: Colors.textPrimary, fontSize: 12, fontWeight: '800' },
  rowStatus: { paddingHorizontal: 7, paddingVertical: 3, borderRadius: 8, overflow: 'hidden', color: Colors.primary, backgroundColor: Colors.primaryLight, fontSize: 8.5, fontWeight: '900', textTransform: 'uppercase' },
  rowStatusDanger: { color: Colors.error, backgroundColor: Colors.errorLight },
});
