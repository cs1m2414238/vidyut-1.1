import React from 'react';
import type { Charger } from '../types';

interface NearbyChargersProps {
  chargers: Charger[];
  selectedId: number | null;
  onSelect: (charger: Charger) => void;
  onViewAll?: () => void;
}

export const NearbyChargers: React.FC<NearbyChargersProps> = ({
  chargers,
  selectedId,
  onSelect,
  onViewAll,
}) => {
  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <h3 style={styles.title}>Nearby Chargers</h3>
        <button style={styles.viewAllBtn} onClick={onViewAll}>
          View all
        </button>
      </div>

      <div style={styles.list}>
        {chargers.map((c) => {
          const isSelected = selectedId === c.id;
          return (
            <div
              key={c.id}
              style={{
                ...styles.card,
                ...(isSelected ? styles.cardSelected : {}),
              }}
              onClick={() => onSelect(c)}
            >
              <img
                src={c.imageUrl}
                alt={c.name}
                style={styles.image}
              />
              <div style={styles.info}>
                <div style={styles.row}>
                  <div style={styles.name}>{c.name}</div>
                  <span
                    style={{
                      ...styles.badge,
                      backgroundColor: c.available ? '#DCFCE7' : '#FFEDD5',
                      color: c.available ? '#15803D' : '#C2410C',
                    }}
                  >
                    {c.available ? 'Available' : 'Busy'}
                  </span>
                </div>
                <div style={styles.address}>📍 {c.address}</div>
                <div style={styles.specs}>
                  {c.powerKw} kW • {c.connectorType}
                </div>
                <div style={styles.footerRow}>
                  <div style={styles.price}>
                    ₹{c.pricePerKwh} <span style={styles.unit}>/kWh</span>
                  </div>
                  <div style={styles.rating}>
                    ⭐ {c.rating} ({c.reviewCount}) • {c.distance}
                  </div>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <button style={styles.viewAllBigBtn} onClick={onViewAll}>
        View All Chargers
      </button>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: 'flex',
    flexDirection: 'column',
    gap: 12,
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  title: {
    fontSize: 16,
    fontWeight: 800,
    color: '#1E293B',
  },
  viewAllBtn: {
    fontSize: 12,
    fontWeight: 700,
    color: '#00A86B',
    cursor: 'pointer',
  },
  list: {
    display: 'flex',
    flexDirection: 'column',
    gap: 10,
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 14,
    padding: 12,
    display: 'flex',
    gap: 12,
    border: '1px solid #E2E8F0',
    cursor: 'pointer',
    transition: 'all 0.15s',
  },
  cardSelected: {
    borderColor: '#00A86B',
    boxShadow: '0 0 0 2px rgba(0, 168, 107, 0.2)',
  },
  image: {
    width: 76,
    height: 76,
    borderRadius: 10,
    objectFit: 'cover',
    flexShrink: 0,
    backgroundColor: '#E6F7F0',
  },
  info: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'center',
    gap: 2,
  },
  row: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  name: {
    fontSize: 14,
    fontWeight: 700,
    color: '#1E293B',
  },
  badge: {
    fontSize: 10,
    fontWeight: 700,
    padding: '2px 8px',
    borderRadius: 10,
  },
  address: {
    fontSize: 11,
    color: '#64748B',
  },
  specs: {
    fontSize: 11,
    fontWeight: 600,
    color: '#64748B',
  },
  footerRow: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 2,
  },
  price: {
    fontSize: 13,
    fontWeight: 800,
    color: '#1E293B',
  },
  unit: {
    fontSize: 10,
    fontWeight: 400,
    color: '#64748B',
  },
  rating: {
    fontSize: 11,
    fontWeight: 600,
    color: '#1E293B',
  },
  viewAllBigBtn: {
    width: '100%',
    padding: '12px',
    backgroundColor: '#00A86B',
    color: '#fff',
    borderRadius: 12,
    fontSize: 14,
    fontWeight: 800,
    cursor: 'pointer',
    marginTop: 8,
    textAlign: 'center',
    transition: 'all 0.15s',
    border: 'none',
  },
};
