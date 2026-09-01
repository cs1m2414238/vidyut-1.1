import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Image,
  SafeAreaView,
  Linking,
  TouchableOpacity,
} from 'react-native';
import { Redirect, useLocalSearchParams, useRouter } from 'expo-router';
import { useQuery } from '@tanstack/react-query';
import { Ionicons } from '@expo/vector-icons';
import { PrimaryButton } from '../../src/components/PrimaryButton';
import { LoadingView } from '../../src/components/LoadingView';
import { Colors } from '../../src/constants/colors';
import { getChargerById } from '../../src/features/chargers/charger.api';
import { useAuthStore } from '../../src/features/auth/auth.store';
import { getMyVehicles } from '../../src/features/vehicles/vehicle.api';

export default function ChargerDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const currentUser = useAuthStore((state) => state.user);
  const [selectedVehicleId, setSelectedVehicleId] = useState<number | null>(null);

  const { data: charger, isLoading } = useQuery({
    queryKey: ['charger', id],
    queryFn: () => getChargerById(id || '1'),
    enabled: !!id && currentUser?.activeMode === 'EV_USER',
  });

  const { data: vehicles = [], isLoading: vehiclesLoading } = useQuery({
    queryKey: ['vehicles'],
    queryFn: getMyVehicles,
    enabled: currentUser?.activeMode === 'EV_USER',
  });

  useEffect(() => {
    if (!selectedVehicleId && vehicles.length) setSelectedVehicleId(vehicles[0].id);
  }, [selectedVehicleId, vehicles]);

  if (currentUser?.activeMode !== 'EV_USER') {
    return <Redirect href="/" />;
  }

  if (isLoading || vehiclesLoading || !charger) {
    return <LoadingView message="Loading charger details..." />;
  }

  const isAvailable = charger.available;

  const handleBookNow = () => router.push({ pathname: '/booking/new', params: {
    stationId: String(charger.id), vehicleId: selectedVehicleId ? String(selectedVehicleId) : undefined,
  } });
  const openDirections = () => Linking.openURL(`https://www.google.com/maps/dir/?api=1&destination=${charger.latitude},${charger.longitude}`);

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.content}>
        {/* Charger Banner / Image */}
        <View style={styles.imageCard}>
          <Image
            source={{
              uri:
                charger.imageUrl && charger.imageUrl.length > 0
                  ? charger.imageUrl
                  : 'https://images.unsplash.com/photo-1563720223185-11003d516935?auto=format&fit=crop&w=800&q=80',
            }}
            style={styles.bannerImage}
            resizeMode="cover"
          />
        </View>

        {/* Title & Host info */}
        <Text style={styles.name}>{charger.name}</Text>
        <Text style={styles.hostName}>Hosted by {charger.hostName}</Text>

        <View style={styles.statusRow}>
          <View style={styles.ratingBadge}>
            <Ionicons name="star" size={16} color={Colors.accent} />
            <Text style={styles.ratingText}>
              {charger.rating} {charger.reviewCount ? `(${charger.reviewCount} reviews)` : ''}
            </Text>
          </View>

          <View
            style={[
              styles.availabilityBadge,
              { backgroundColor: isAvailable ? Colors.successLight : Colors.warningLight },
            ]}
          >
            <Ionicons
              name={isAvailable ? 'checkmark-circle' : 'close-circle'}
              size={16}
              color={isAvailable ? Colors.success : Colors.warning}
            />
            <Text
              style={[
                styles.availabilityText,
                { color: isAvailable ? Colors.success : Colors.warning },
              ]}
            >
              {isAvailable ? ' Available' : ' In use'}
            </Text>
          </View>
        </View>

        <View style={styles.divider} />

        {/* Details Grid */}
        <View style={styles.detailsContainer}>
          <DetailRow label="Connector Type" value={charger.connectorType} />
          <DetailRow label="Power Rating" value={`${charger.powerKw} kW`} />
          <DetailRow label="Tariff Price" value={`₹${charger.pricePerKwh} / kWh`} />
          <DetailRow label="Address" value={charger.address} />
          <DetailRow label="Live slots" value={`${charger.availableSlots} of ${charger.totalSlots} available`} />
          <DetailRow label="Hours" value={charger.workingHours || 'Hours not reported'} />
          {charger.distance ? <DetailRow label="Distance" value={charger.distance} /> : null}
        </View>

        <TouchableOpacity style={styles.directions} onPress={openDirections}>
          <Ionicons name="navigate-outline" size={19} color={Colors.primary} />
          <View style={{ flex: 1 }}><Text style={styles.directionsTitle}>Get directions</Text><Text style={styles.directionsText}>Open turn-by-turn navigation in Google Maps</Text></View>
          <Ionicons name="open-outline" size={17} color={Colors.textMuted} />
        </TouchableOpacity>

        <View style={styles.descBox}><Text style={styles.descTitle}>Connectors</Text>{charger.connectors.map((connector, index) => <View key={`${connector.type}-${index}`} style={styles.connectorRow}><Ionicons name="flash-outline" size={17} color={connector.available ? Colors.primary : Colors.textMuted} /><Text style={styles.connectorName}>{connector.type}</Text><Text style={styles.connectorMeta}>{connector.powerKw} kW • {connector.available ? 'Available' : 'In use'}</Text></View>)}</View>

        <View style={styles.vehicleSection}>
          <View style={styles.vehicleSectionTop}>
            <View><Text style={styles.vehicleSectionTitle}>Charging vehicle</Text><Text style={styles.vehicleSectionHint}>Links this payment to the correct auto-recharge rule</Text></View>
            <Ionicons name="shield-checkmark-outline" size={20} color={Colors.primary} />
          </View>
          {vehicles.length ? (
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.vehicleOptions}>
              {vehicles.map((vehicle) => {
                const active = selectedVehicleId === vehicle.id;
                return (
                  <TouchableOpacity key={vehicle.id} style={[styles.vehicleOption, active && styles.vehicleOptionActive]} onPress={() => setSelectedVehicleId(vehicle.id)}>
                    <Ionicons name="car-sport" size={17} color={active ? Colors.primary : Colors.textSecondary} />
                    <View><Text style={styles.vehicleOptionName}>{vehicle.makeAndModel}</Text><Text style={styles.vehicleOptionReg}>{vehicle.registrationNumber}</Text></View>
                    <Ionicons name={active ? 'radio-button-on' : 'radio-button-off'} size={16} color={active ? Colors.primary : Colors.textMuted} />
                  </TouchableOpacity>
                );
              })}
            </ScrollView>
          ) : (
            <TouchableOpacity style={styles.addVehiclePrompt} onPress={() => router.push('/(owner)/wallet')}>
              <View style={styles.addVehicleIcon}><Ionicons name="add" size={19} color={Colors.primary} /></View>
              <View style={styles.addVehicleCopy}><Text style={styles.addVehicleTitle}>Add a vehicle to book</Text><Text style={styles.addVehicleHint}>It also unlocks vehicle-linked auto-recharge.</Text></View>
              <Ionicons name="chevron-forward" size={18} color={Colors.textMuted} />
            </TouchableOpacity>
          )}
        </View>

        {charger.description ? (
          <View style={styles.descBox}>
            <Text style={styles.descTitle}>About this station</Text>
            <Text style={styles.descText}>{charger.description}</Text>
          </View>
        ) : null}
      </ScrollView>

      {/* Footer CTA */}
      <View style={styles.footer}>
        <PrimaryButton
          title={!vehicles.length ? 'Add vehicle to continue' : isAvailable ? 'Book Now' : 'Currently Unavailable'}
          onPress={!vehicles.length ? () => router.push('/(owner)/wallet') : handleBookNow}
          disabled={!isAvailable && vehicles.length > 0}
        />
      </View>
    </SafeAreaView>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.detailRow}>
      <Text style={styles.detailLabel}>{label}</Text>
      <Text style={styles.detailValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.background,
  },
  content: {
    padding: 16,
    paddingBottom: 30,
  },
  imageCard: {
    height: 200,
    width: '100%',
    borderRadius: 16,
    overflow: 'hidden',
    marginBottom: 16,
    backgroundColor: Colors.primaryLight,
  },
  bannerImage: {
    width: '100%',
    height: '100%',
  },
  name: {
    fontSize: 22,
    fontWeight: '800',
    color: Colors.textPrimary,
  },
  hostName: {
    fontSize: 14,
    fontWeight: '600',
    color: Colors.textSecondary,
    marginTop: 4,
  },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 12,
  },
  ratingBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    marginRight: 16,
  },
  ratingText: {
    fontSize: 14,
    fontWeight: '700',
    color: Colors.textPrimary,
    marginLeft: 4,
  },
  availabilityBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
  },
  availabilityText: {
    fontSize: 12,
    fontWeight: '700',
  },
  divider: {
    height: 1,
    backgroundColor: Colors.border,
    marginVertical: 18,
  },
  detailsContainer: {
    backgroundColor: Colors.white,
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  detailRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#F1F5F9',
  },
  detailLabel: {
    fontSize: 14,
    color: Colors.textSecondary,
  },
  detailValue: {
    fontSize: 14,
    fontWeight: '600',
    color: Colors.textPrimary,
    maxWidth: '60%',
    textAlign: 'right',
  },
  descBox: {
    marginTop: 16,
    backgroundColor: Colors.white,
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  descTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: Colors.textPrimary,
    marginBottom: 6,
  },
  descText: {
    fontSize: 13,
    color: Colors.textSecondary,
    lineHeight: 18,
  },
  directions: { marginTop: 14, minHeight: 58, padding: 13, flexDirection: 'row', alignItems: 'center', gap: 10, borderWidth: 1, borderColor: '#B6E2CF', borderRadius: 14, backgroundColor: Colors.primarySoft },
  directionsTitle: { color: Colors.primaryDark, fontSize: 11, fontWeight: '900' },
  directionsText: { marginTop: 2, color: Colors.textSecondary, fontSize: 8.5 },
  connectorRow: { paddingVertical: 9, flexDirection: 'row', alignItems: 'center', gap: 8, borderBottomWidth: 1, borderBottomColor: Colors.borderSoft },
  connectorName: { flex: 1, color: Colors.textPrimary, fontSize: 11, fontWeight: '800' },
  connectorMeta: { color: Colors.textSecondary, fontSize: 9 },
  vehicleSection: {
    marginTop: 16,
    padding: 16,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: Colors.border,
    backgroundColor: Colors.white,
  },
  vehicleSectionTop: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  vehicleSectionTitle: {
    color: Colors.textPrimary,
    fontSize: 14,
    fontWeight: '800',
  },
  vehicleSectionHint: {
    maxWidth: 260,
    marginTop: 3,
    color: Colors.textSecondary,
    fontSize: 9,
    lineHeight: 13,
  },
  vehicleOptions: {
    paddingTop: 12,
    gap: 8,
  },
  vehicleOption: {
    minWidth: 205,
    padding: 10,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    borderWidth: 1,
    borderColor: Colors.border,
    borderRadius: 12,
  },
  vehicleOptionActive: {
    borderColor: Colors.primary,
    backgroundColor: Colors.primarySoft,
  },
  vehicleOptionName: {
    maxWidth: 125,
    color: Colors.textPrimary,
    fontSize: 10,
    fontWeight: '800',
  },
  vehicleOptionReg: {
    marginTop: 2,
    color: Colors.textSecondary,
    fontSize: 8,
  },
  addVehiclePrompt: {
    marginTop: 12,
    padding: 11,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 9,
    borderWidth: 1,
    borderStyle: 'dashed',
    borderColor: '#86CDB0',
    borderRadius: 12,
    backgroundColor: Colors.primarySoft,
  },
  addVehicleIcon: {
    width: 34,
    height: 34,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 10,
    backgroundColor: Colors.primaryLight,
  },
  addVehicleCopy: { flex: 1 },
  addVehicleTitle: { color: Colors.primaryDark, fontSize: 10.5, fontWeight: '800' },
  addVehicleHint: { marginTop: 2, color: Colors.textSecondary, fontSize: 8.5 },
  footer: {
    padding: 16,
    backgroundColor: Colors.white,
    borderTopWidth: 1,
    borderTopColor: Colors.border,
  },
});
