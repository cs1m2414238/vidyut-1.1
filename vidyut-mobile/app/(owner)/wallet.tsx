import React, { useEffect, useState } from 'react';
import {
  Alert,
  Modal,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { AppHeader } from '../../src/components/AppHeader';
import { LoadingView } from '../../src/components/LoadingView';
import { PrimaryButton } from '../../src/components/PrimaryButton';
import { Colors } from '../../src/constants/colors';
import { addVehicle, getMyVehicles } from '../../src/features/vehicles/vehicle.api';
import { VehicleItem } from '../../src/features/vehicles/vehicle.types';
import {
  getAutoRechargeRules,
  getVehicleWallets,
  saveAutoRechargeRule,
  topUpVehicleWallet,
} from '../../src/features/wallet/wallet.api';
import { AutoRechargeRule } from '../../src/features/wallet/wallet.types';

const initialRule = {
  enabled: true,
  balanceThreshold: '500',
  rechargeAmount: '1000',
  paymentMethod: 'UPI mandate',
};

function money(value: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  }).format(value);
}

function formatDate(value?: string | null): string {
  if (!value) return 'Not triggered yet';
  return new Date(value).toLocaleString('en-IN', {
    day: 'numeric',
    month: 'short',
    hour: 'numeric',
    minute: '2-digit',
  });
}

export default function WalletScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [selectedVehicleId, setSelectedVehicleId] = useState<number | null>(null);
  const [ruleDraft, setRuleDraft] = useState(initialRule);
  const [topUpAmount, setTopUpAmount] = useState('1000');
  const [showVehicleModal, setShowVehicleModal] = useState(false);
  const [vehicleForm, setVehicleForm] = useState({
    makeAndModel: '',
    registrationNumber: '',
    batteryCapacity: '',
    connectorType: 'CCS2',
  });

  const walletQuery = useQuery({ queryKey: ['vehicle-wallets'], queryFn: getVehicleWallets });
  const vehiclesQuery = useQuery({ queryKey: ['vehicles'], queryFn: getMyVehicles });
  const rulesQuery = useQuery({ queryKey: ['auto-recharge-rules'], queryFn: getAutoRechargeRules });

  const vehicles = vehiclesQuery.data ?? [];
  const rules = rulesQuery.data ?? [];
  const selectedWallet = walletQuery.data?.find((wallet) => wallet.vehicleId === selectedVehicleId);

  useEffect(() => {
    if (!selectedVehicleId && vehicles.length) setSelectedVehicleId(vehicles[0].id);
    if (selectedVehicleId && !vehicles.some((vehicle) => vehicle.id === selectedVehicleId)) {
      setSelectedVehicleId(vehicles[0]?.id ?? null);
    }
  }, [selectedVehicleId, vehicles]);

  useEffect(() => {
    const rule = rules.find((item) => item.vehicleId === selectedVehicleId);
    setRuleDraft(rule ? {
      enabled: rule.enabled,
      balanceThreshold: String(rule.balanceThreshold),
      rechargeAmount: String(rule.rechargeAmount),
      paymentMethod: rule.paymentMethod,
    } : initialRule);
  }, [selectedVehicleId, rules]);

  const topUpMutation = useMutation({
    mutationFn: topUpVehicleWallet,
    onSuccess: (wallet) => {
      queryClient.setQueryData(['vehicle-wallets'], (current: typeof walletQuery.data = []) => [
        wallet,
        ...(current ?? []).filter((item) => item.vehicleId !== wallet.vehicleId),
      ]);
      Alert.alert('Wallet topped up', `${money(Number(topUpAmount))} was added successfully.`);
    },
    onError: (error: Error) => Alert.alert('Top-up failed', error.message),
  });

  const ruleMutation = useMutation({
    mutationFn: saveAutoRechargeRule,
    onSuccess: (rule) => {
      queryClient.setQueryData<AutoRechargeRule[]>(['auto-recharge-rules'], (current = []) => [
        rule,
        ...current.filter((item) => item.vehicleId !== rule.vehicleId),
      ]);
      Alert.alert(
        rule.enabled ? 'Auto-recharge enabled' : 'Auto-recharge paused',
        `${rule.vehicleName} now uses its own wallet rule.`,
      );
    },
    onError: (error: Error) => Alert.alert('Could not save', error.message),
  });

  const vehicleMutation = useMutation({
    mutationFn: addVehicle,
    onSuccess: (vehicle) => {
      queryClient.setQueryData<VehicleItem[]>(['vehicles'], (current = []) => [...current, vehicle]);
      setSelectedVehicleId(vehicle.id);
      setShowVehicleModal(false);
      setVehicleForm({ makeAndModel: '', registrationNumber: '', batteryCapacity: '', connectorType: 'CCS2' });
      Alert.alert('Vehicle added', 'You can now turn on auto-recharge for this EV.');
    },
    onError: (error: Error) => Alert.alert('Could not add vehicle', error.message),
  });

  const refresh = async () => {
    await Promise.all([walletQuery.refetch(), vehiclesQuery.refetch(), rulesQuery.refetch()]);
  };

  const handleTopUp = () => {
    const amount = Number(topUpAmount);
    if (!Number.isFinite(amount) || amount < 100) {
      Alert.alert('Check amount', 'Enter an amount of at least ₹100.');
      return;
    }
    if (!selectedVehicleId) {
      Alert.alert('Choose a vehicle', 'Each EV has its own wallet balance.');
      return;
    }
    topUpMutation.mutate({ vehicleId: selectedVehicleId, amount });
  };

  const handleSaveRule = () => {
    if (!selectedVehicleId) return;
    const balanceThreshold = Number(ruleDraft.balanceThreshold);
    const rechargeAmount = Number(ruleDraft.rechargeAmount);
    if (balanceThreshold < 100 || balanceThreshold > 10000) {
      Alert.alert('Check threshold', 'Choose a threshold between ₹100 and ₹10,000.');
      return;
    }
    if (rechargeAmount < 100 || rechargeAmount > 25000) {
      Alert.alert('Check amount', 'Choose a recharge amount between ₹100 and ₹25,000.');
      return;
    }
    ruleMutation.mutate({
      vehicleId: selectedVehicleId,
      enabled: ruleDraft.enabled,
      balanceThreshold,
      rechargeAmount,
      paymentMethod: ruleDraft.paymentMethod,
    });
  };

  const handleAddVehicle = () => {
    if (!vehicleForm.makeAndModel.trim() || !vehicleForm.registrationNumber.trim()) {
      Alert.alert('Missing details', 'Make/model and registration number are required.');
      return;
    }
    vehicleMutation.mutate(vehicleForm);
  };

  if (walletQuery.isLoading || vehiclesQuery.isLoading || rulesQuery.isLoading) {
    return <LoadingView message="Opening your wallet..." />;
  }

  const selectedVehicle = vehicles.find((vehicle) => vehicle.id === selectedVehicleId);
  const selectedRule = rules.find((rule) => rule.vehicleId === selectedVehicleId);
  const activeRules = rules.filter((rule) => rule.enabled).length;
  const isRefreshing = walletQuery.isRefetching || vehiclesQuery.isRefetching || rulesQuery.isRefetching;
  const queryError = walletQuery.error || vehiclesQuery.error || rulesQuery.error;

  return (
    <View style={styles.screen}>
      <AppHeader title="Wallet" subtitle="Payments linked to your EV" rightIcon="shield-checkmark-outline" />
      <ScrollView
        contentContainerStyle={styles.content}
        refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={refresh} colors={[Colors.primary]} />}
      >
        {queryError ? <View style={styles.errorBox}><Ionicons name="alert-circle" size={17} color={Colors.error} /><Text style={styles.errorText}>{queryError.message}</Text></View> : null}

        <View style={styles.balanceCard}>
          <View style={styles.balanceTop}>
            <View><Text style={styles.balanceKicker}>{selectedWallet?.vehicleName?.toUpperCase() || 'VEHICLE'} BALANCE</Text><Text style={styles.balance}>{money(selectedWallet?.balance ?? 0)}</Text></View>
            <View style={styles.walletIcon}><Ionicons name="wallet" size={22} color={Colors.white} /></View>
          </View>
          <Text style={styles.balanceHint}>Independent balance • protected for this vehicle only</Text>
          {selectedWallet ? <TouchableOpacity style={styles.tagButton} onPress={() => router.push({ pathname: '/wallet-tag', params: { vehicleId: String(selectedWallet.vehicleId) } })}><Ionicons name="qr-code-outline" size={15} color="#D1FAE5" /><Text style={styles.tagButtonText}>View charging tag</Text></TouchableOpacity> : null}
          <View style={styles.topUpRow}>
            <View style={styles.topUpInput}><Text style={styles.rupee}>₹</Text><TextInput value={topUpAmount} onChangeText={setTopUpAmount} keyboardType="number-pad" style={styles.amountInput} placeholderTextColor="#A7F3D0" /></View>
            <TouchableOpacity style={styles.topUpButton} onPress={handleTopUp} disabled={topUpMutation.isPending}>
              <Ionicons name="add" size={17} color={Colors.primaryDark} />
              <Text style={styles.topUpText}>{topUpMutation.isPending ? 'Adding...' : 'Add money'}</Text>
            </TouchableOpacity>
          </View>
        </View>

        <View style={styles.miniStats}>
          <MiniStat icon="car-sport-outline" value={String(vehicles.length)} label="Linked EVs" />
          <MiniStat icon="sparkles-outline" value={String(activeRules)} label="Active rules" tone="purple" />
        </View>

        <View style={styles.sectionTitleRow}>
          <View><Text style={styles.sectionTitle}>Vehicle auto-recharge</Text><Text style={styles.sectionSubtitle}>A separate safety net for every EV</Text></View>
          <TouchableOpacity style={styles.addVehicle} onPress={() => setShowVehicleModal(true)}><Ionicons name="add" size={16} color={Colors.primary} /><Text style={styles.addVehicleText}>Vehicle</Text></TouchableOpacity>
        </View>

        {vehicles.length ? (
          <>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.vehicleStrip}>
              {vehicles.map((vehicle) => {
                const active = vehicle.id === selectedVehicleId;
                const rule = rules.find((item) => item.vehicleId === vehicle.id);
                return (
                  <TouchableOpacity key={vehicle.id} style={[styles.vehicleChip, active && styles.vehicleChipActive]} onPress={() => setSelectedVehicleId(vehicle.id)}>
                    <View style={[styles.vehicleChipIcon, active && styles.vehicleChipIconActive]}><Ionicons name="car-sport" size={18} color={active ? Colors.white : Colors.primary} /></View>
                    <View style={styles.vehicleChipCopy}><Text numberOfLines={1} style={styles.vehicleName}>{vehicle.makeAndModel}</Text><Text style={styles.vehicleReg}>{vehicle.registrationNumber}</Text></View>
                    <View style={[styles.ruleDot, rule?.enabled && styles.ruleDotOn]} />
                  </TouchableOpacity>
                );
              })}
            </ScrollView>

            {selectedVehicle ? (
              <View style={styles.ruleCard}>
                <View style={styles.ruleTop}>
                  <View style={styles.ruleTitleCopy}><Text style={styles.ruleReg}>{selectedVehicle.registrationNumber}</Text><Text style={styles.ruleVehicle}>{selectedVehicle.makeAndModel}</Text></View>
                  <View style={styles.switchRow}><Text style={styles.switchText}>{ruleDraft.enabled ? 'Enabled' : 'Paused'}</Text><Switch value={ruleDraft.enabled} onValueChange={(enabled) => setRuleDraft((current) => ({ ...current, enabled }))} trackColor={{ false: Colors.border, true: '#74C7A5' }} thumbColor={ruleDraft.enabled ? Colors.primary : Colors.white} /></View>
                </View>

                <View style={styles.explainer}>
                  <FlowStep number="1" title="Charge ends" caption="Payment is tied to this EV" />
                  <FlowStep number="2" title="Balance checked" caption={`Below ₹${ruleDraft.balanceThreshold || '0'}`} />
                  <FlowStep number="3" title="Wallet refilled" caption={`Add ₹${ruleDraft.rechargeAmount || '0'}`} />
                </View>

                <Text style={styles.fieldLabel}>Recharge when wallet goes below</Text>
                <View style={styles.choiceRow}>
                  {[300, 500, 1000].map((amount) => <ChoiceChip key={amount} label={`₹${amount}`} active={ruleDraft.balanceThreshold === String(amount)} onPress={() => setRuleDraft((current) => ({ ...current, balanceThreshold: String(amount) }))} />)}
                </View>
                <TextInput value={ruleDraft.balanceThreshold} onChangeText={(balanceThreshold) => setRuleDraft((current) => ({ ...current, balanceThreshold }))} keyboardType="number-pad" style={styles.customInput} placeholder="Custom threshold" placeholderTextColor={Colors.textMuted} />

                <Text style={styles.fieldLabel}>Auto-recharge amount</Text>
                <View style={styles.choiceRow}>
                  {[500, 1000, 2000].map((amount) => <ChoiceChip key={amount} label={`₹${amount}`} active={ruleDraft.rechargeAmount === String(amount)} onPress={() => setRuleDraft((current) => ({ ...current, rechargeAmount: String(amount) }))} />)}
                </View>

                <Text style={styles.fieldLabel}>Payment method</Text>
                <View style={styles.paymentMethods}>
                  {['UPI mandate', 'Visa •••• 4242'].map((method) => (
                    <TouchableOpacity key={method} style={[styles.paymentMethod, ruleDraft.paymentMethod === method && styles.paymentMethodActive]} onPress={() => setRuleDraft((current) => ({ ...current, paymentMethod: method }))}>
                      <Ionicons name={method.startsWith('UPI') ? 'phone-portrait-outline' : 'card-outline'} size={17} color={ruleDraft.paymentMethod === method ? Colors.primary : Colors.textSecondary} />
                      <Text style={styles.paymentText}>{method}</Text>
                      <Ionicons name={ruleDraft.paymentMethod === method ? 'radio-button-on' : 'radio-button-off'} size={17} color={ruleDraft.paymentMethod === method ? Colors.primary : Colors.textMuted} />
                    </TouchableOpacity>
                  ))}
                </View>

                <View style={styles.lastRun}><Ionicons name="time-outline" size={14} color={Colors.textMuted} /><Text style={styles.lastRunText}>Last auto-recharge: {formatDate(selectedRule?.lastTriggeredAt)}</Text></View>
                <PrimaryButton title={ruleMutation.isPending ? 'Saving...' : 'Save auto-recharge'} onPress={handleSaveRule} loading={ruleMutation.isPending} />
              </View>
            ) : null}
          </>
        ) : (
          <View style={styles.emptyCard}>
            <View style={styles.emptyIcon}><Ionicons name="car-sport-outline" size={27} color={Colors.primary} /></View>
            <Text style={styles.emptyTitle}>Add your EV first</Text>
            <Text style={styles.emptyText}>Auto-recharge activates only for charging payments linked to a saved vehicle.</Text>
            <PrimaryButton title="Add vehicle" onPress={() => setShowVehicleModal(true)} style={styles.emptyButton} />
          </View>
        )}

        <Text style={[styles.sectionTitle, styles.activityTitle]}>Recent activity</Text>
        <View style={styles.activityCard}>
          {(selectedWallet?.recentTransactions ?? []).map((transaction) => {
            const credit = transaction.type === 'TOP_UP' || transaction.type === 'AUTO_RECHARGE' || transaction.type === 'REFUND';
            const vehicle = vehicles.find((item) => item.id === selectedVehicleId);
            return (
              <View key={transaction.id} style={styles.activityRow}>
                <View style={[styles.activityIcon, credit ? styles.creditIcon : styles.debitIcon]}><Ionicons name={credit ? 'arrow-down' : 'arrow-up'} size={16} color={credit ? Colors.primary : Colors.error} /></View>
                <View style={styles.activityCopy}><Text numberOfLines={1} style={styles.activityName}>{transaction.type === 'AUTO_RECHARGE' ? 'Vehicle auto-recharge' : transaction.description}</Text><Text style={styles.activityMeta}>{vehicle ? `${vehicle.makeAndModel} • ` : ''}{formatDate(transaction.timestamp)}</Text></View>
                <Text style={[styles.activityAmount, { color: credit ? Colors.primary : Colors.error }]}>{credit ? '+' : '−'}{money(Math.abs(transaction.amount))}</Text>
              </View>
            );
          })}
          {!selectedWallet?.recentTransactions?.length ? <View style={styles.noActivity}><Ionicons name="receipt-outline" size={23} color={Colors.textMuted} /><Text style={styles.noActivityText}>This vehicle's wallet activity will appear here.</Text></View> : null}
        </View>
      </ScrollView>

      <Modal visible={showVehicleModal} transparent animationType="slide" onRequestClose={() => setShowVehicleModal(false)}>
        <View style={styles.modalBackdrop}>
          <View style={styles.modalCard}>
            <View style={styles.modalTop}><View><Text style={styles.modalTitle}>Add your EV</Text><Text style={styles.modalSubtitle}>Link it to bookings and auto-recharge.</Text></View><TouchableOpacity style={styles.closeButton} onPress={() => setShowVehicleModal(false)}><Ionicons name="close" size={20} color={Colors.textSecondary} /></TouchableOpacity></View>
            <VehicleField label="Make and model" value={vehicleForm.makeAndModel} placeholder="Tata Nexon EV" onChangeText={(makeAndModel) => setVehicleForm((current) => ({ ...current, makeAndModel }))} />
            <VehicleField label="Registration number" value={vehicleForm.registrationNumber} placeholder="UP32 AB 1234" autoCapitalize="characters" onChangeText={(registrationNumber) => setVehicleForm((current) => ({ ...current, registrationNumber: registrationNumber.toUpperCase() }))} />
            <VehicleField label="Battery capacity" value={vehicleForm.batteryCapacity} placeholder="40.5 kWh" onChangeText={(batteryCapacity) => setVehicleForm((current) => ({ ...current, batteryCapacity }))} />
            <Text style={styles.modalFieldLabel}>Connector type</Text>
            <View style={styles.choiceRow}>{['CCS2', 'Type 2', 'CHAdeMO'].map((connectorType) => <ChoiceChip key={connectorType} label={connectorType} active={vehicleForm.connectorType === connectorType} onPress={() => setVehicleForm((current) => ({ ...current, connectorType }))} />)}</View>
            <View style={styles.modalActions}><PrimaryButton title="Cancel" variant="outline" onPress={() => setShowVehicleModal(false)} style={styles.modalButton} /><PrimaryButton title="Add vehicle" onPress={handleAddVehicle} loading={vehicleMutation.isPending} style={styles.modalButton} /></View>
          </View>
        </View>
      </Modal>
    </View>
  );
}

function MiniStat({ icon, value, label, tone = 'green' }: { icon: keyof typeof Ionicons.glyphMap; value: string; label: string; tone?: 'green' | 'purple' }) {
  const purple = tone === 'purple';
  return <View style={styles.miniStat}><View style={[styles.miniIcon, purple && styles.miniIconPurple]}><Ionicons name={icon} size={19} color={purple ? Colors.purple : Colors.primary} /></View><View><Text style={styles.miniValue}>{value}</Text><Text style={styles.miniLabel}>{label}</Text></View></View>;
}

function FlowStep({ number, title, caption }: { number: string; title: string; caption: string }) {
  return <View style={styles.flowStep}><View style={styles.flowNumber}><Text style={styles.flowNumberText}>{number}</Text></View><Text style={styles.flowTitle}>{title}</Text><Text style={styles.flowCaption}>{caption}</Text></View>;
}

function ChoiceChip({ label, active, onPress }: { label: string; active: boolean; onPress: () => void }) {
  return <TouchableOpacity style={[styles.choiceChip, active && styles.choiceChipActive]} onPress={onPress}><Text style={[styles.choiceText, active && styles.choiceTextActive]}>{label}</Text></TouchableOpacity>;
}

function VehicleField(props: React.ComponentProps<typeof TextInput> & { label: string }) {
  const { label, ...inputProps } = props;
  return <View><Text style={styles.modalFieldLabel}>{label}</Text><TextInput {...inputProps} style={styles.modalInput} placeholderTextColor={Colors.textMuted} /></View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Colors.background },
  content: { padding: 16, paddingBottom: 32 },
  errorBox: { marginBottom: 12, padding: 12, flexDirection: 'row', gap: 7, borderRadius: 12, backgroundColor: Colors.errorLight },
  errorText: { flex: 1, color: Colors.error, fontSize: 11, fontWeight: '700' },
  balanceCard: { padding: 19, borderRadius: 21, backgroundColor: '#096340' },
  balanceTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  balanceKicker: { color: '#A7F3D0', fontSize: 8.5, fontWeight: '900', letterSpacing: 1 },
  balance: { marginTop: 7, color: Colors.white, fontSize: 29, fontWeight: '900', letterSpacing: -.7 },
  walletIcon: { width: 43, height: 43, borderRadius: 14, justifyContent: 'center', alignItems: 'center', backgroundColor: 'rgba(255,255,255,.12)' },
  balanceHint: { marginTop: 2, color: 'rgba(255,255,255,.62)', fontSize: 9.5 },
  tagButton: { alignSelf: 'flex-start', marginTop: 10, paddingHorizontal: 9, paddingVertical: 7, flexDirection: 'row', alignItems: 'center', gap: 5, borderRadius: 9, backgroundColor: 'rgba(255,255,255,.10)' },
  tagButtonText: { color: '#D1FAE5', fontSize: 8.5, fontWeight: '800' },
  topUpRow: { marginTop: 18, flexDirection: 'row', gap: 8 },
  topUpInput: { flex: 1, height: 42, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 11, borderWidth: 1, borderColor: 'rgba(255,255,255,.18)', borderRadius: 11, backgroundColor: 'rgba(255,255,255,.09)' },
  rupee: { color: Colors.white, fontSize: 12, fontWeight: '800' },
  amountInput: { flex: 1, height: '100%', paddingHorizontal: 6, color: Colors.white, fontSize: 12, fontWeight: '800' },
  topUpButton: { minWidth: 112, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 4, borderRadius: 11, backgroundColor: Colors.white },
  topUpText: { color: Colors.primaryDark, fontSize: 10.5, fontWeight: '900' },
  miniStats: { marginTop: 10, flexDirection: 'row', gap: 10 },
  miniStat: { flex: 1, padding: 13, flexDirection: 'row', alignItems: 'center', gap: 10, borderWidth: 1, borderColor: Colors.borderSoft, borderRadius: 15, backgroundColor: Colors.white },
  miniIcon: { width: 36, height: 36, borderRadius: 11, justifyContent: 'center', alignItems: 'center', backgroundColor: Colors.primaryLight },
  miniIconPurple: { backgroundColor: Colors.purpleLight },
  miniValue: { color: Colors.textPrimary, fontSize: 16, fontWeight: '900' },
  miniLabel: { marginTop: 1, color: Colors.textSecondary, fontSize: 8.5 },
  sectionTitleRow: { marginTop: 23, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  sectionTitle: { color: Colors.textPrimary, fontSize: 16, fontWeight: '900' },
  sectionSubtitle: { marginTop: 3, color: Colors.textSecondary, fontSize: 9.5 },
  addVehicle: { flexDirection: 'row', alignItems: 'center', gap: 3, paddingHorizontal: 9, paddingVertical: 7, borderRadius: 9, backgroundColor: Colors.primaryLight },
  addVehicleText: { color: Colors.primary, fontSize: 9.5, fontWeight: '800' },
  vehicleStrip: { paddingTop: 12, paddingBottom: 2, gap: 9 },
  vehicleChip: { width: 220, padding: 10, flexDirection: 'row', alignItems: 'center', gap: 9, borderWidth: 1, borderColor: Colors.border, borderRadius: 14, backgroundColor: Colors.white },
  vehicleChipActive: { borderColor: '#74C7A5', backgroundColor: Colors.primarySoft },
  vehicleChipIcon: { width: 34, height: 34, borderRadius: 10, justifyContent: 'center', alignItems: 'center', backgroundColor: Colors.primaryLight },
  vehicleChipIconActive: { backgroundColor: Colors.primary },
  vehicleChipCopy: { flex: 1 },
  vehicleName: { color: Colors.textPrimary, fontSize: 10.5, fontWeight: '800' },
  vehicleReg: { marginTop: 2, color: Colors.textSecondary, fontSize: 8.5 },
  ruleDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: Colors.border },
  ruleDotOn: { backgroundColor: Colors.success },
  ruleCard: { marginTop: 11, padding: 16, borderWidth: 1, borderColor: Colors.borderSoft, borderRadius: 18, backgroundColor: Colors.white },
  ruleTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  ruleTitleCopy: { flex: 1 },
  ruleReg: { color: Colors.primary, fontSize: 8.5, fontWeight: '900', letterSpacing: .8 },
  ruleVehicle: { marginTop: 3, color: Colors.textPrimary, fontSize: 15, fontWeight: '900' },
  switchRow: { flexDirection: 'row', alignItems: 'center' },
  switchText: { color: Colors.textSecondary, fontSize: 9.5, fontWeight: '800' },
  explainer: { marginTop: 18, paddingVertical: 13, flexDirection: 'row', borderRadius: 14, backgroundColor: Colors.primarySoft },
  flowStep: { flex: 1, alignItems: 'center', paddingHorizontal: 3 },
  flowNumber: { width: 28, height: 28, borderRadius: 14, justifyContent: 'center', alignItems: 'center', backgroundColor: Colors.primaryLight },
  flowNumberText: { color: Colors.primary, fontSize: 9, fontWeight: '900' },
  flowTitle: { marginTop: 5, color: Colors.textPrimary, fontSize: 8.5, fontWeight: '800', textAlign: 'center' },
  flowCaption: { marginTop: 2, color: Colors.textSecondary, fontSize: 7.5, lineHeight: 10, textAlign: 'center' },
  fieldLabel: { marginTop: 17, marginBottom: 8, color: Colors.textPrimary, fontSize: 10.5, fontWeight: '800' },
  choiceRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 7 },
  choiceChip: { minWidth: 70, paddingHorizontal: 12, paddingVertical: 9, alignItems: 'center', borderWidth: 1, borderColor: Colors.border, borderRadius: 10, backgroundColor: Colors.white },
  choiceChipActive: { borderColor: Colors.primary, backgroundColor: Colors.primaryLight },
  choiceText: { color: Colors.textSecondary, fontSize: 10, fontWeight: '800' },
  choiceTextActive: { color: Colors.primary },
  customInput: { height: 41, marginTop: 8, paddingHorizontal: 12, borderWidth: 1, borderColor: Colors.border, borderRadius: 10, color: Colors.textPrimary, fontSize: 11, backgroundColor: Colors.white },
  paymentMethods: { gap: 7 },
  paymentMethod: { minHeight: 44, paddingHorizontal: 12, flexDirection: 'row', alignItems: 'center', gap: 8, borderWidth: 1, borderColor: Colors.border, borderRadius: 11 },
  paymentMethodActive: { borderColor: Colors.primary, backgroundColor: Colors.primarySoft },
  paymentText: { flex: 1, color: Colors.textPrimary, fontSize: 10.5, fontWeight: '700' },
  lastRun: { marginVertical: 14, flexDirection: 'row', alignItems: 'center', gap: 5 },
  lastRunText: { color: Colors.textMuted, fontSize: 8.5 },
  emptyCard: { marginTop: 12, padding: 28, alignItems: 'center', borderWidth: 1, borderColor: Colors.borderSoft, borderRadius: 18, backgroundColor: Colors.white },
  emptyIcon: { width: 52, height: 52, borderRadius: 16, justifyContent: 'center', alignItems: 'center', backgroundColor: Colors.primaryLight },
  emptyTitle: { marginTop: 12, color: Colors.textPrimary, fontSize: 15, fontWeight: '900' },
  emptyText: { maxWidth: 280, marginTop: 5, color: Colors.textSecondary, fontSize: 10.5, lineHeight: 15, textAlign: 'center' },
  emptyButton: { minWidth: 180, marginTop: 16 },
  activityTitle: { marginTop: 23, marginBottom: 10 },
  activityCard: { overflow: 'hidden', borderWidth: 1, borderColor: Colors.borderSoft, borderRadius: 17, backgroundColor: Colors.white },
  activityRow: { minHeight: 65, paddingHorizontal: 13, flexDirection: 'row', alignItems: 'center', gap: 9, borderBottomWidth: 1, borderBottomColor: Colors.borderSoft },
  activityIcon: { width: 35, height: 35, borderRadius: 11, justifyContent: 'center', alignItems: 'center' },
  creditIcon: { backgroundColor: Colors.primaryLight },
  debitIcon: { backgroundColor: Colors.errorLight },
  activityCopy: { flex: 1 },
  activityName: { color: Colors.textPrimary, fontSize: 10.5, fontWeight: '800' },
  activityMeta: { marginTop: 3, color: Colors.textMuted, fontSize: 8 },
  activityAmount: { fontSize: 10.5, fontWeight: '900' },
  noActivity: { minHeight: 120, alignItems: 'center', justifyContent: 'center' },
  noActivityText: { marginTop: 7, color: Colors.textMuted, fontSize: 9.5 },
  modalBackdrop: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(16,24,40,.48)' },
  modalCard: { padding: 19, paddingBottom: 28, borderTopLeftRadius: 24, borderTopRightRadius: 24, backgroundColor: Colors.white },
  modalTop: { marginBottom: 7, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  modalTitle: { color: Colors.textPrimary, fontSize: 18, fontWeight: '900' },
  modalSubtitle: { marginTop: 3, color: Colors.textSecondary, fontSize: 10 },
  closeButton: { width: 34, height: 34, borderWidth: 1, borderColor: Colors.border, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  modalFieldLabel: { marginTop: 13, marginBottom: 6, color: Colors.textPrimary, fontSize: 10, fontWeight: '800' },
  modalInput: { height: 44, paddingHorizontal: 12, borderWidth: 1, borderColor: Colors.border, borderRadius: 11, color: Colors.textPrimary, fontSize: 11.5, backgroundColor: Colors.background },
  modalActions: { marginTop: 22, flexDirection: 'row', gap: 9 },
  modalButton: { flex: 1 },
});
