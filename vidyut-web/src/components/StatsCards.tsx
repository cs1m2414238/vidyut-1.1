import React from 'react';
import type { User } from '../types';

interface StatsCardsProps {
  user: User;
}

export const StatsCards: React.FC<StatsCardsProps> = ({ user }) => {
  const stats = [
    { icon: '📅', value: user.totalBookings.toString(), label: 'Bookings', color: '#0EA5E9' },
    { icon: '💳', value: `₹${user.walletBalance.toLocaleString()}`, label: 'Wallet Balance', color: '#8B5CF6' },
    { icon: '⚡', value: `${user.totalEnergyKwh} kWh`, label: 'This Month', color: '#F59E0B' },
    { icon: '🌿', value: `${user.co2SavedKg} kg`, label: 'CO₂ Saved', color: '#10B981' },
  ];

  return (
    <div style={styles.grid}>
      {stats.map((s) => (
        <div key={s.label} style={styles.card}>
          <div style={{ ...styles.iconBadge, backgroundColor: s.color + '20' }}>
            <span style={{ fontSize: 20 }}>{s.icon}</span>
          </div>
          <div>
            <div style={styles.value}>{s.value}</div>
            <div style={styles.label}>{s.label}</div>
          </div>
        </div>
      ))}
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  grid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(4, 1fr)',
    gap: 16,
    marginBottom: 20,
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 14,
    padding: '14px 18px',
    display: 'flex',
    alignItems: 'center',
    gap: 14,
    border: '1px solid #E2E8F0',
    boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
  },
  iconBadge: {
    width: 44,
    height: 44,
    borderRadius: 12,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  value: {
    fontSize: 20,
    fontWeight: 800,
    color: '#1E293B',
    lineHeight: 1.1,
  },
  label: {
    fontSize: 12,
    color: '#64748B',
    fontWeight: 500,
    marginTop: 2,
  },
};
