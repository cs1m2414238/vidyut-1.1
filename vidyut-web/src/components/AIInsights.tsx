import React from 'react';

export const AIInsights: React.FC = () => {
  return (
    <div style={styles.card}>
      <div style={styles.header}>
        <div>
          <div style={styles.title}>Vidyut AI Insights</div>
          <div style={styles.subtitle}>AI is optimizing the network for you</div>
        </div>
        <button style={styles.viewBtn}>View AI Agent</button>
      </div>

      <div style={styles.grid}>
        <div style={styles.insightItem}>
          <span style={styles.icon}>⚡</span>
          <div>
            <div style={styles.itemTitle}>High Demand</div>
            <div style={styles.itemText}>Gomti Nagar</div>
            <div style={styles.itemSub}>Congestion expected 5 PM – 8 PM</div>
          </div>
        </div>

        <div style={styles.insightItem}>
          <span style={styles.icon}>🟢</span>
          <div>
            <div style={styles.itemTitle}>Best Time to Charge</div>
            <div style={styles.itemText}>10:00 PM – 6:00 AM</div>
            <div style={styles.itemSub}>Lower prices &amp; less congestion</div>
          </div>
        </div>

        <div style={styles.insightItem}>
          <span style={styles.icon}>🌱</span>
          <div>
            <div style={styles.itemTitle}>CO₂ Impact</div>
            <div style={styles.itemText}>You saved 23 kg</div>
            <div style={styles.itemSub}>of CO₂ this month. Keep it up! 🌿</div>
          </div>
        </div>

        <div style={styles.insightItem}>
          <span style={styles.icon}>💡</span>
          <div>
            <div style={styles.itemTitle}>Smart Recommendation</div>
            <div style={styles.itemText}>We found 5 better</div>
            <div style={styles.itemSub}>charging options near your route.</div>
          </div>
        </div>
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  card: {
    backgroundColor: '#fff',
    borderRadius: 16,
    padding: 16,
    border: '1px solid #E2E8F0',
    boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  title: {
    fontSize: 14,
    fontWeight: 800,
    color: '#1E293B',
  },
  subtitle: {
    fontSize: 11,
    color: '#64748B',
  },
  viewBtn: {
    padding: '6px 14px',
    backgroundColor: '#E6F7F0',
    color: '#00A86B',
    border: 'none',
    borderRadius: 8,
    fontSize: 11,
    fontWeight: 700,
    cursor: 'pointer',
  },
  grid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(4, 1fr)',
    gap: 10,
  },
  insightItem: {
    backgroundColor: '#F8FAFC',
    borderRadius: 12,
    padding: 10,
    display: 'flex',
    gap: 8,
    border: '1px solid #F1F5F9',
  },
  icon: {
    fontSize: 16,
  },
  itemTitle: {
    fontSize: 11,
    fontWeight: 700,
    color: '#1E293B',
  },
  itemText: {
    fontSize: 11,
    fontWeight: 600,
    color: '#00A86B',
  },
  itemSub: {
    fontSize: 10,
    color: '#64748B',
    marginTop: 2,
  },
};
