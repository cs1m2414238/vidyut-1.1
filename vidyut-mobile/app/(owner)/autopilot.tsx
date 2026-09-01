import React, { useCallback, useMemo, useState } from 'react';
import {
  Alert,
  Share,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect, useRouter } from 'expo-router';
import { AppHeader } from '../../src/components/AppHeader';
import { SkeletonList } from '../../src/components/SkeletonList';
import { Colors } from '../../src/constants/colors';
import {
  completeAutopilotCharging,
  getAutopilotAlternatives,
  getAutopilotSummary,
  getCurrentAutopilotTrip,
  launchAutopilotTrip,
  previewAutopilotTrip,
  sendAutopilotAgentMessage,
  simulateAutopilotDelay,
  simulateAutopilotFault,
  startAutopilotJourney,
  swapAutopilotStop,
} from '../../src/features/autopilot/autopilot.api';
import {
  AutopilotAlternative,
  AutopilotAutonomyMode,
  AutopilotOptimization,
  AutopilotPlan,
  AutopilotTrip,
} from '../../src/features/autopilot/autopilot.types';
import { addVehicle, getMyVehicles } from '../../src/features/vehicles/vehicle.api';
import { VehicleItem } from '../../src/features/vehicles/vehicle.types';
import { topUpWallet } from '../../src/features/wallet/wallet.api';

const defaultGoal = "Get me from Kanpur to Delhi by 6 PM. Keep charging under ₹900 and don't let my battery fall below 15%.";

type TripPurpose = 'DIRECT' | 'MALL_VISIT' | 'REST_BREAK';

const autonomyOptions: Array<{ value: AutopilotAutonomyMode; label: string; detail: string }> = [
  { value: 'RECOMMEND_ONLY', label: 'Recommend', detail: 'Plan only; no bookings' },
  { value: 'ASK_BEFORE_ACTIONS', label: 'Ask first', detail: 'Approve bookings and payments' },
  { value: 'FULL_AUTOPILOT', label: 'Full auto', detail: 'Act inside your limits' },
];

const purposeOptions: Array<{ value: TripPurpose; label: string; icon: keyof typeof Ionicons.glyphMap }> = [
  { value: 'DIRECT', label: 'Direct', icon: 'navigate' },
  { value: 'MALL_VISIT', label: 'Mall visit', icon: 'bag-handle' },
  { value: 'REST_BREAK', label: 'Rest break', icon: 'cafe' },
];

const optimizationOptions: Array<{ value: AutopilotOptimization; label: string }> = [
  { value: 'TIME', label: 'Fastest' },
  { value: 'COST', label: 'Lowest cost' },
  { value: 'BALANCED', label: 'Balanced' },
];

export default function AutopilotScreen() {
  const router = useRouter();
  const [trip, setTrip] = useState<AutopilotTrip | null>(null);
  const [preview, setPreview] = useState<AutopilotPlan | null>(null);
  const [alternatives, setAlternatives] = useState<Record<number, AutopilotAlternative[]>>({});
  const [vehicles, setVehicles] = useState<VehicleItem[]>([]);
  const [vehicleId, setVehicleId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [busy, setBusy] = useState('');
  const [goal, setGoal] = useState(defaultGoal);
  const [agentReply, setAgentReply] = useState('');
  const [agentSessionId, setAgentSessionId] = useState<string>();
  const [agentModel, setAgentModel] = useState('');
  const [agentTools, setAgentTools] = useState<Array<{ name: string; status: string }>>([]);
  const [origin, setOrigin] = useState('Kanpur');
  const [destination, setDestination] = useState('Delhi');
  const [battery, setBattery] = useState('42');
  const [reserve, setReserve] = useState('15');
  const [budget, setBudget] = useState('900');
  const [deadline, setDeadline] = useState('18:00');
  const [departure, setDeparture] = useState('Now');
  const [targetCharge, setTargetCharge] = useState('80');
  const [autonomyMode, setAutonomyMode] = useState<AutopilotAutonomyMode>('ASK_BEFORE_ACTIONS');
  const [optimizeFor, setOptimizeFor] = useState<AutopilotOptimization>('TIME');
  const [tripPurpose, setTripPurpose] = useState<TripPurpose>('DIRECT');
  const [vehicleForm, setVehicleForm] = useState({
    makeAndModel: 'Tata Nexon EV',
    registrationNumber: '',
    batteryCapacity: '40.5 kWh',
    connectorType: 'CCS2',
  });

  const load = useCallback(async (showLoader = false) => {
    if (showLoader) setLoading(true);
    try {
      const [availableVehicles, currentTrip] = await Promise.all([
        getMyVehicles(),
        getCurrentAutopilotTrip(),
      ]);
      setVehicles(availableVehicles);
      setVehicleId((current) => current ?? availableVehicles[0]?.id ?? null);
      setTrip(currentTrip);
    } catch (error) {
      Alert.alert('Autopilot unavailable', messageFor(error));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useFocusEffect(useCallback(() => {
    void load(true);
  }, [load]));

  const activeStop = useMemo(
    () => trip?.stops.find((stop) => stop.status === 'RESERVED') ?? null,
    [trip],
  );

  const purposeInstruction = ({
    DIRECT: 'Prioritize a direct journey.',
    MALL_VISIT: 'Choose a charger close to the destination mall so charging happens during the visit.',
    REST_BREAK: 'Place a safe charging stop beside a comfortable rest point.',
  } satisfies Record<TripPurpose, string>)[tripPurpose];

  const enrichedGoal = `${goal.trim()} ${purposeInstruction} Depart ${departure}; charge each stop to ${targetCharge}%.`;

  const runTripAction = async (name: string, operation: () => Promise<AutopilotTrip>) => {
    setBusy(name);
    try {
      setTrip(await operation());
    } catch (error) {
      Alert.alert('Action failed', messageFor(error));
    } finally {
      setBusy('');
    }
  };

  const launch = async () => {
    if (!vehicleId) {
      Alert.alert('Connect an EV', 'Add your EV before starting an autonomous journey.');
      return;
    }
    if (autonomyMode === 'RECOMMEND_ONLY') {
      Alert.alert(
        'Recommendation kept',
        'Recommend-only mode never creates bookings or payment holds. Switch to Ask first or Full auto when you want Vidyut to act.',
      );
      return;
    }
    await runTripAction('launch', () => launchAutopilotTrip({
      vehicleId,
      origin: origin.trim(),
      destination: destination.trim(),
      goal: enrichedGoal,
      arrivalDeadline: deadline,
      optimizeFor,
      currentBatteryPercent: Number(battery),
      minimumArrivalBatteryPercent: Number(reserve),
      maximumChargingBudget: Number(budget),
      idempotencyKey: `MOBILE-${Date.now()}`,
      autonomyMode,
    }));
    setPreview(null);
  };

  const previewJourney = async () => {
    if (!vehicleId) { Alert.alert('Connect an EV', 'Add your EV before planning the journey.'); return; }
    setBusy('preview');
    try {
      setPreview(await previewAutopilotTrip({
        vehicleId, origin: origin.trim(), destination: destination.trim(),
        goal: enrichedGoal,
        arrivalDeadline: deadline, optimizeFor, currentBatteryPercent: Number(battery),
        minimumArrivalBatteryPercent: Number(reserve), maximumChargingBudget: Number(budget),
        idempotencyKey: `PREVIEW-${Date.now()}`, autonomyMode,
      }));
    } catch (error) { Alert.alert('Plan unavailable', messageFor(error)); }
    finally { setBusy(''); }
  };

  const loadAlternatives = async (stopId: number) => {
    if (!trip) return;
    setBusy(`alternatives-${stopId}`);
    try {
      const nextAlternatives = await getAutopilotAlternatives(trip.id, stopId);
      setAlternatives((current) => ({ ...current, [stopId]: nextAlternatives }));
    }
    catch (error) { Alert.alert('Alternatives unavailable', messageFor(error)); }
    finally { setBusy(''); }
  };

  const swapStop = async (stopId: number, stationId: number) => {
    if (!trip) return;
    await runTripAction(`swap-${stopId}`, () => swapAutopilotStop(trip.id, stopId, stationId));
    setAlternatives({});
  };

  const shareSummary = async () => {
    if (!trip) return;
    setBusy('summary');
    try { const summary = await getAutopilotSummary(trip.id); await Share.share({ message: summary.shareText, title: 'My Vidyut trip' }); }
    catch (error) { Alert.alert('Summary unavailable', messageFor(error)); }
    finally { setBusy(''); }
  };

  const planWithGemini = async () => {
    if (!vehicleId) {
      Alert.alert('Connect an EV', 'Add your EV before asking Gemini to plan the journey.');
      return;
    }
    setBusy('agent');
    setAgentReply('');
    setAgentTools([]);
    try {
      const response = await sendAutopilotAgentMessage(
        `Analyze and propose a Vidyut Autopilot trip for vehicle ID ${vehicleId}. `
          + `Travel from ${origin} to ${destination}, current battery ${battery}%, `
          + `arrive by ${deadline}, keep at least ${reserve}% battery, `
          + `spend at most ₹${budget}, optimize for ${optimizeFor}. ${purposeInstruction} `
          + `Use past route outcomes when available. This is read-only planning: do not reserve, cancel, pay, or mutate any trip. `
          + `User goal: ${goal}`,
        agentSessionId,
      );
      setAgentSessionId(response.sessionId);
      setAgentReply(response.reply);
      setAgentModel(response.model);
      setAgentTools(response.toolCalls);
      await previewJourney();
    } catch (error) {
      Alert.alert('Gemini agent unavailable', messageFor(error));
    } finally {
      setBusy('');
    }
  };

  const createVehicle = async () => {
    if (!vehicleForm.registrationNumber.trim()) {
      Alert.alert('Registration required', 'Enter the EV registration number.');
      return;
    }
    setBusy('vehicle');
    try {
      const vehicle = await addVehicle({
        ...vehicleForm,
        registrationNumber: vehicleForm.registrationNumber.trim().toUpperCase(),
      });
      setVehicles((current) => [...current, vehicle]);
      setVehicleId(vehicle.id);
    } catch (error) {
      Alert.alert('Unable to add EV', messageFor(error));
    } finally {
      setBusy('');
    }
  };

  const fundWallet = async () => {
    setBusy('topup');
    try {
      await topUpWallet(1000);
      setTrip(await getCurrentAutopilotTrip());
      Alert.alert('Wallet ready', '₹1,000 was added using the simulated UPI provider.');
    } catch (error) {
      Alert.alert('Top-up failed', messageFor(error));
    } finally {
      setBusy('');
    }
  };

  if (loading) return <SkeletonList rows={7} />;

  return (
    <View style={styles.screen}>
      <AppHeader showBrand notificationCount={trip?.timeline.filter((item) => item.state === 'WARNING').length ?? 0} />
      <ScrollView
        contentContainerStyle={styles.content}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); void load(); }} colors={[Colors.primary]} />}
      >
        <View style={styles.hero}>
          <View style={styles.heroGlow} />
          <View style={styles.kicker}><Ionicons name="sparkles" size={13} color="#6EE7B7" /><Text style={styles.kickerText}>VIDYUT AUTOPILOT</Text></View>
          <Text style={styles.heroTitle}>Give it your destination.{`\n`}<Text style={styles.heroAccent}>Vidyut handles charging.</Text></Text>
          <Text style={styles.heroCopy}>Plan · reserve · monitor · reroute · charge · pay</Text>
          <View style={styles.trustRow}><TrustChip icon="shield-checkmark" label="EV protected" /><TrustChip icon="navigate" label="Live rerouting" /><TrustChip icon="wallet" label="AutoPay" /></View>
        </View>

        <View style={styles.card}>
          <SectionHead step="01" title="Journey goal" subtitle="Intent with enforceable safety limits" />
          {vehicles.length === 0 ? (
            <View style={styles.vehicleSetup}>
              <View style={styles.vehicleSetupHead}><View style={styles.setupIcon}><Ionicons name="car-sport" size={22} color={Colors.primary} /></View><View><Text style={styles.setupTitle}>Connect your EV</Text><Text style={styles.setupCopy}>Autopilot uses its range and connector.</Text></View></View>
              <Field label="Make & model" value={vehicleForm.makeAndModel} onChangeText={(value) => setVehicleForm((current) => ({ ...current, makeAndModel: value }))} />
              <Field label="Registration" value={vehicleForm.registrationNumber} placeholder="UP78 AB 1234" onChangeText={(value) => setVehicleForm((current) => ({ ...current, registrationNumber: value }))} autoCapitalize="characters" />
              <View style={styles.fieldRow}><View style={styles.flexField}><Field label="Battery" value={vehicleForm.batteryCapacity} onChangeText={(value) => setVehicleForm((current) => ({ ...current, batteryCapacity: value }))} /></View><View style={styles.flexField}><Field label="Connector" value={vehicleForm.connectorType} onChangeText={(value) => setVehicleForm((current) => ({ ...current, connectorType: value }))} autoCapitalize="characters" /></View></View>
              <PrimaryAction label="Add EV" icon="car-sport" loading={busy === 'vehicle'} onPress={() => void createVehicle()} />
            </View>
          ) : (
            <>
              <Text style={styles.inputLabel}>SELECTED VEHICLE</Text>
              <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.vehicleChips}>
                {vehicles.map((vehicle) => <TouchableOpacity key={vehicle.id} style={[styles.vehicleChip, vehicle.id === vehicleId && styles.vehicleChipActive]} onPress={() => setVehicleId(vehicle.id)}><Ionicons name="car-sport" size={15} color={vehicle.id === vehicleId ? Colors.white : Colors.primary} /><View><Text style={[styles.vehicleChipTitle, vehicle.id === vehicleId && styles.vehicleChipTextActive]}>{vehicle.makeAndModel}</Text><Text style={[styles.vehicleChipSub, vehicle.id === vehicleId && styles.vehicleChipTextActive]}>{vehicle.registrationNumber}</Text></View></TouchableOpacity>)}
              </ScrollView>
              <Text style={styles.inputLabel}>TELL VIDYUT WHAT MATTERS</Text>
              <TextInput style={styles.goalInput} multiline value={goal} onChangeText={setGoal} placeholderTextColor={Colors.textMuted} />
              <Text style={styles.choiceLabel}>TRIP PURPOSE</Text>
              <View style={styles.choiceRow}>
                {purposeOptions.map((option) => <TouchableOpacity key={option.value} style={[styles.choiceChip, tripPurpose === option.value && styles.choiceChipActive]} onPress={() => setTripPurpose(option.value)}><Ionicons name={option.icon} size={13} color={tripPurpose === option.value ? Colors.white : Colors.primary} /><Text style={[styles.choiceChipText, tripPurpose === option.value && styles.choiceChipTextActive]}>{option.label}</Text></TouchableOpacity>)}
              </View>
              <Text style={styles.choiceLabel}>OPTIMIZE FOR</Text>
              <View style={styles.choiceRow}>
                {optimizationOptions.map((option) => <TouchableOpacity key={option.value} style={[styles.choiceChip, optimizeFor === option.value && styles.choiceChipActive]} onPress={() => setOptimizeFor(option.value)}><Text style={[styles.choiceChipText, optimizeFor === option.value && styles.choiceChipTextActive]}>{option.label}</Text></TouchableOpacity>)}
              </View>
              <View style={styles.fieldRow}><View style={styles.flexField}><Field label="From" value={origin} onChangeText={setOrigin} /></View><View style={styles.routeArrow}><Ionicons name="arrow-forward" size={16} color={Colors.textMuted} /></View><View style={styles.flexField}><Field label="To" value={destination} onChangeText={setDestination} /></View></View>
              <View style={styles.constraintGrid}>
                <CompactField label="Battery" value={battery} suffix="%" onChangeText={setBattery} />
                <CompactField label="Reserve" value={reserve} suffix="%" onChangeText={setReserve} />
                <CompactField label="Budget" value={budget} prefix="₹" onChangeText={setBudget} />
                <CompactField label="Arrive" value={deadline} onChangeText={setDeadline} keyboardType="default" />
                <CompactField label="Depart" value={departure} onChangeText={setDeparture} keyboardType="default" />
                <CompactField label="Charge target" value={targetCharge} suffix="%" onChangeText={setTargetCharge} />
              </View>
              <Text style={styles.choiceLabel}>AUTONOMY MODE</Text>
              <View style={styles.modeList}>
                {autonomyOptions.map((option) => <TouchableOpacity key={option.value} style={[styles.modeOption, autonomyMode === option.value && styles.modeOptionActive]} onPress={() => setAutonomyMode(option.value)}><View style={[styles.modeRadio, autonomyMode === option.value && styles.modeRadioActive]}>{autonomyMode === option.value ? <View style={styles.modeRadioDot} /> : null}</View><View style={styles.modeCopy}><Text style={styles.modeTitle}>{option.label}</Text><Text style={styles.modeDetail}>{option.detail}</Text></View>{option.value === 'ASK_BEFORE_ACTIONS' ? <View style={styles.recommendedPill}><Text style={styles.recommendedText}>DEFAULT</Text></View> : null}</TouchableOpacity>)}
              </View>
              <View style={styles.memoryNote}><Ionicons name="time" size={15} color={Colors.primary} /><View style={styles.memoryCopy}><Text style={styles.memoryTitle}>Route memory ready</Text><Text style={styles.memoryText}>Past booking, queue and charger outcomes can improve the next plan when the agent has matching history.</Text></View></View>
              <PrimaryAction label="Preview timing-matched plan" icon="sparkles" loading={busy === 'preview'} onPress={() => void previewJourney()} />
              <TouchableOpacity style={styles.fallbackButton} onPress={() => void planWithGemini()} disabled={Boolean(busy)}><Ionicons name="chatbubble-ellipses" size={15} color={Colors.primary} /><Text style={styles.fallbackButtonText}>{busy === 'agent' ? 'Analyzing…' : 'Ask Gemini to analyze'}</Text></TouchableOpacity>
              {agentReply ? <View style={styles.agentReply}><Ionicons name="sparkles" size={17} color="#6848D9" /><View style={styles.agentReplyCopy}><Text style={styles.agentReplyTitle}>Gemini agent{agentModel ? ` · ${agentModel}` : ''}</Text><Text style={styles.agentReplyText}>{agentReply}</Text>{agentTools.length ? <View style={styles.toolProof}><Text style={styles.toolProofLabel}>TOOLS CALLED</Text><View style={styles.toolChips}>{agentTools.map((tool, index) => <View key={`${tool.name}-${index}`} style={styles.toolChip}><Ionicons name={tool.status === 'SUCCESS' ? 'checkmark-circle' : 'ellipse'} size={11} color={tool.status === 'SUCCESS' ? Colors.success : Colors.blue} /><Text style={styles.toolChipText}>{tool.name}</Text></View>)}</View></View> : null}</View></View> : null}
            </>
          )}
        </View>

        {preview ? (
          <View style={styles.card}>
            <View style={styles.planHead}><SectionHead step="02" title="Review before booking" subtitle={`${preview.origin} → ${preview.destination}`} /><View style={styles.statusPill}><Text style={styles.statusText}>PREVIEW</Text></View></View>
            <View style={styles.metricGrid}><Metric icon="map" value={`${preview.totalDistanceKm} km`} label="Distance" /><Metric icon="time" value={formatMinutes(preview.totalDurationMinutes)} label={`Arrive ${preview.estimatedArrivalTime}`} /><Metric icon="cash" value={`₹${preview.estimatedChargingCost.toFixed(0)}`} label={`₹${preview.budgetRemaining.toFixed(0)} left`} /><Metric icon="battery-half" value={`${preview.estimatedArrivalBatteryPercent}%`} label="Arrival reserve" /></View>
            <Text style={styles.previewLabel}>{preview.compatibleChargersEvaluated} compatible chargers checked live</Text>
            <View style={styles.previewStops}>{preview.stops.map((stop) => <View key={`${stop.stationId}-${stop.sequenceNumber}`} style={styles.previewStop}><View style={[styles.timingDot, { backgroundColor: timingColor(stop.timingScore) }]} /><View style={styles.previewStopCopy}><Text style={styles.previewStopTitle}>{stop.stationName}</Text><Text style={styles.previewStopMeta}>ETA {stop.estimatedArrivalTime} · slot free {stop.predictedSlotFreeAt} · {stop.chargingMinutes} min · ₹{stop.estimatedCost.toFixed(0)}</Text><Text style={[styles.timingText, { color: timingColor(stop.timingScore) }]}>{stop.timingLabel}</Text></View></View>)}</View>
            <View style={styles.actionBoundary}><Ionicons name="shield-checkmark" size={16} color={Colors.primary} /><Text style={styles.actionBoundaryText}>{autonomyMode === 'FULL_AUTOPILOT' ? `Confirmation authorizes reservations and rerouting only inside ₹${budget} and ${reserve}% reserve limits.` : autonomyMode === 'ASK_BEFORE_ACTIONS' ? 'No booking or payment happens until you confirm this plan.' : 'Recommend-only mode will not create a booking or payment hold.'}</Text></View>
            <PrimaryAction label={autonomyMode === 'FULL_AUTOPILOT' ? 'Authorize full Autopilot' : autonomyMode === 'ASK_BEFORE_ACTIONS' ? 'Confirm tentative bookings' : 'Keep recommendation (no booking)'} icon={autonomyMode === 'RECOMMEND_ONLY' ? 'bookmark' : 'checkmark-circle'} loading={busy === 'launch'} onPress={() => void launch()} />
            <TouchableOpacity style={styles.fallbackButton} onPress={() => setPreview(null)}><Text style={styles.fallbackButtonText}>Edit trip details</Text></TouchableOpacity>
          </View>
        ) : null}

        {trip ? (
          <>
            <View style={styles.card}>
              <View style={styles.planHead}><SectionHead step="02" title="Autonomous plan" subtitle={`${trip.origin} → ${trip.destination}`} /><View style={[styles.statusPill, trip.status === 'PAYMENT_REQUIRED' && styles.statusWarning, trip.status === 'COMPLETED' && styles.statusComplete]}><Text style={[styles.statusText, trip.status === 'PAYMENT_REQUIRED' && styles.statusWarningText, trip.status === 'COMPLETED' && styles.statusCompleteText]}>{statusLabel(trip.status)}</Text></View></View>
              <View style={styles.metricGrid}><Metric icon="map" value={`${trip.totalDistanceKm} km`} label="Distance" /><Metric icon="time" value={formatMinutes(trip.totalDurationMinutes)} label="Total time" /><Metric icon="cash" value={`₹${trip.estimatedChargingCost.toFixed(0)}`} label={`of ₹${trip.maximumChargingBudget.toFixed(0)}`} /><Metric icon="battery-half" value={`${trip.estimatedArrivalBatteryPercent}%`} label="Arrival" /></View>
              <View style={styles.routeStrip}><RoutePoint title={trip.origin} subtitle={`${trip.telemetry.batteryPercent}% now`} /><View style={styles.routeLine} />{trip.stops.filter((stop) => stop.status !== 'CANCELLED').map((stop) => <React.Fragment key={stop.id}><RoutePoint charge title={stop.stationName} subtitle={`${stop.arrivalBatteryPercent}% → ${stop.targetBatteryPercent}%`} /><View style={styles.routeLine} /></React.Fragment>)}<RoutePoint title={trip.destination} subtitle={`${trip.estimatedArrivalBatteryPercent}% safe`} /></View>
            </View>

            <View style={styles.telemetryCard}>
              <View style={styles.telemetryHead}><View><Text style={styles.telemetryKicker}>LIVE VEHICLE</Text><Text style={styles.telemetryTitle}>{trip.telemetry.vehicleName}</Text><Text style={styles.telemetrySub}>{trip.telemetry.registrationNumber} · {trip.telemetry.connectorType}</Text></View><View style={styles.livePill}><View style={styles.liveDot} /><Text style={styles.liveText}>LIVE</Text></View></View>
              <View style={styles.batteryRow}><View style={styles.batteryIcon}><Ionicons name="battery-charging" size={27} color="#6EE7B7" /></View><View><Text style={styles.batteryValue}>{trip.telemetry.batteryPercent}%</Text><Text style={styles.batteryRange}>{trip.telemetry.remainingRangeKm} km estimated range</Text></View><View style={styles.walletMetric}><Text style={styles.walletLabel}>WALLET</Text><Text style={styles.walletValue}>₹{trip.walletBalance.toFixed(0)}</Text></View></View>
              <View style={styles.progressTrack}><View style={[styles.progressFill, { width: `${trip.telemetry.batteryPercent}%` }]} /></View>
              {trip.paymentMessage ? <View style={[styles.paymentNote, trip.status === 'PAYMENT_REQUIRED' && styles.paymentWarning]}><Ionicons name="wallet" size={15} color={trip.status === 'PAYMENT_REQUIRED' ? '#FEC84B' : '#A7F3D0'} /><Text style={styles.paymentText}>{trip.paymentMessage}</Text></View> : null}
            </View>

            <View style={styles.card}>
              <SectionHead step="03" title="Charging stops" subtitle="Optimized for total journey impact" />
              <View style={styles.stopList}>{trip.stops.map((stop) => <View key={stop.id}><View style={[styles.stopRow, stop.status === 'CANCELLED' && styles.stopCancelled]}><View style={[styles.stopIndex, stop.status === 'CANCELLED' && styles.stopIndexDanger]}>{stop.status === 'CANCELLED' ? <Ionicons name="warning" size={14} color={Colors.error} /> : <Text style={styles.stopIndexText}>{stop.sequenceNumber}</Text>}</View><View style={styles.stopCopy}><View style={styles.stopTitleRow}><Text style={styles.stopTitle}>{stop.stationName}</Text><Text style={styles.stopStatus}>{stop.status}</Text></View><Text style={styles.stopAddress} numberOfLines={1}>{stop.stationAddress}</Text><Text style={styles.stopMeta}>{stop.connectorType} · {stop.powerKw} kW · {stop.estimatedWaitMinutes + stop.chargingMinutes} min · ₹{stop.estimatedCost.toFixed(0)}</Text><View style={styles.stopTimingRow}><Text style={[styles.stopTiming, { color: timingColor(stop.timingScore) }]}>{stop.timingLabel ?? 'Timing monitored'}</Text>{stop.status !== 'CANCELLED' ? <TouchableOpacity onPress={() => void loadAlternatives(stop.id)}><Text style={styles.alternativesLink}>{busy === `alternatives-${stop.id}` ? 'Checking…' : 'Alternatives'}</Text></TouchableOpacity> : null}</View></View><View style={styles.stopBattery}><Text style={styles.stopBatteryText}>{stop.arrivalBatteryPercent}%</Text><Ionicons name="arrow-forward" size={12} color={Colors.primary} /><Text style={styles.stopBatteryText}>{stop.targetBatteryPercent}%</Text></View></View>{(alternatives[stop.id] ?? []).slice(0, 3).map((alternative) => <TouchableOpacity key={alternative.station.id} style={styles.alternativeRow} onPress={() => void swapStop(stop.id, alternative.station.id)}><View style={styles.alternativeCopy}><Text style={styles.alternativeTitle}>{alternative.station.name}</Text><Text style={styles.alternativeMeta}>{alternative.detourKm.toFixed(1)} km detour · {alternative.availableSlots} free · ₹{alternative.estimatedChargingCost.toFixed(0)}</Text></View><Text style={styles.swapText}>{busy === `swap-${stop.id}` ? 'Swapping…' : 'Swap'}</Text></TouchableOpacity>)}</View>)}</View>
            </View>

            <View style={styles.card}>
              <SectionHead step="04" title="Demo controls" subtitle="Each control calls the secured backend" />
              <View style={styles.actionList}>
                {trip.status === 'RESERVED' ? <ActionRow icon="navigate" title="Start monitored journey" subtitle="Activate telemetry and live checks" loading={busy === 'start'} onPress={() => void runTripAction('start', () => startAutopilotJourney(trip.id))} /> : null}
                {['RESERVED', 'MONITORING'].includes(trip.status) ? <ActionRow icon="time" title="Simulate 25-minute delay" subtitle="Trigger timing-match replan and stop rebooking" danger loading={busy === 'delay'} onPress={() => void runTripAction('delay', () => simulateAutopilotDelay(trip.id, 25))} /> : null}
                {['RESERVED', 'MONITORING'].includes(trip.status) ? <ActionRow icon="warning" title="Simulate charger fault" subtitle="Cancel, replan and reserve replacement" danger loading={busy === 'fault'} onPress={() => void runTripAction('fault', () => simulateAutopilotFault(trip.id))} /> : null}
                {['RESERVED', 'MONITORING', 'REROUTED', 'PAYMENT_REQUIRED'].includes(trip.status) ? <ActionRow icon="flash" title="Complete charging + AutoPay" subtitle={activeStop ? `Pay ₹${activeStop.estimatedCost.toFixed(0)} from wallet` : 'Finish active charging'} loading={busy === 'complete'} onPress={() => void runTripAction('complete', () => completeAutopilotCharging(trip.id))} /> : null}
                {(trip.status === 'PAYMENT_REQUIRED' || trip.walletBalance < (activeStop?.estimatedCost ?? 0)) ? <ActionRow icon="add-circle" title="Top up ₹1,000" subtitle="Simulated UPI payment provider" loading={busy === 'topup'} onPress={() => void fundWallet()} /> : null}
                <ActionRow icon="wallet" title="Wallet & auto-recharge" subtitle="Manage the linked vehicle payment rule" onPress={() => router.push('/(owner)/wallet')} />
                <ActionRow icon="share-social" title="Share trip summary" subtitle="Distance, time, charging cost and CO₂ saved" loading={busy === 'summary'} onPress={() => void shareSummary()} />
              </View>
            </View>

            <View style={styles.card}>
              <SectionHead step="05" title="Action timeline" subtitle="Visible proof that the agent took action" />
              <View style={styles.timeline}>{[...trip.timeline].reverse().map((item) => <View style={styles.timelineItem} key={item.sequenceNumber}><View style={[styles.timelineIcon, item.state === 'WARNING' && styles.timelineWarning, item.state === 'INFO' && styles.timelineInfo]}><Ionicons name={item.state === 'WARNING' ? 'warning' : item.state === 'INFO' ? 'sparkles' : 'checkmark'} size={13} color={item.state === 'WARNING' ? Colors.error : item.state === 'INFO' ? Colors.blue : Colors.primary} /></View><View style={styles.timelineCopy}><View style={styles.timelineTitleRow}><Text style={styles.timelineTitle}>{item.title}</Text><Text style={styles.timelineTime}>{new Date(item.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</Text></View><Text style={styles.timelineDetail}>{item.detail}</Text></View></View>)}</View>
            </View>
          </>
        ) : (
          <View style={styles.emptyCard}><View style={styles.emptyIcon}><Ionicons name="navigate-circle" size={38} color={Colors.primary} /></View><Text style={styles.emptyTitle}>No active journey</Text><Text style={styles.emptyText}>Connect an EV and give Vidyut a destination. Planning and autonomous actions will appear here.</Text></View>
        )}
      </ScrollView>
    </View>
  );
}

function SectionHead({ step, title, subtitle }: { step: string; title: string; subtitle: string }) {
  return <View style={styles.sectionHead}><View style={styles.step}><Text style={styles.stepText}>{step}</Text></View><View><Text style={styles.sectionTitle}>{title}</Text><Text style={styles.sectionSubtitle}>{subtitle}</Text></View></View>;
}

function Field(props: React.ComponentProps<typeof TextInput> & { label: string }) {
  const { label, ...inputProps } = props;
  return <View style={styles.field}><Text style={styles.inputLabel}>{label.toUpperCase()}</Text><TextInput {...inputProps} style={styles.input} placeholderTextColor={Colors.textMuted} /></View>;
}

function CompactField({ label, prefix, suffix, keyboardType = 'numeric', ...props }: React.ComponentProps<typeof TextInput> & { label: string; prefix?: string; suffix?: string }) {
  return <View style={styles.compactField}><Text style={styles.inputLabel}>{label.toUpperCase()}</Text><View style={styles.compactInput}>{prefix ? <Text style={styles.affix}>{prefix}</Text> : null}<TextInput {...props} keyboardType={keyboardType} style={styles.compactTextInput} /><Text style={styles.affix}>{suffix}</Text></View></View>;
}

function PrimaryAction({ label, icon, loading, onPress }: { label: string; icon: keyof typeof Ionicons.glyphMap; loading: boolean; onPress: () => void }) {
  return <TouchableOpacity style={styles.primaryButton} onPress={onPress} disabled={loading}>{loading ? <Ionicons name="sync" size={17} color={Colors.white} /> : <Ionicons name={icon} size={17} color={Colors.white} />}<Text style={styles.primaryButtonText}>{loading ? 'Working…' : label}</Text></TouchableOpacity>;
}

function TrustChip({ icon, label }: { icon: keyof typeof Ionicons.glyphMap; label: string }) {
  return <View style={styles.trustChip}><Ionicons name={icon} size={12} color="#A7F3D0" /><Text style={styles.trustText}>{label}</Text></View>;
}

function Metric({ icon, value, label }: { icon: keyof typeof Ionicons.glyphMap; value: string; label: string }) {
  return <View style={styles.metric}><View style={styles.metricIcon}><Ionicons name={icon} size={15} color={Colors.primary} /></View><Text style={styles.metricValue}>{value}</Text><Text style={styles.metricLabel}>{label}</Text></View>;
}

function RoutePoint({ title, subtitle, charge }: { title: string; subtitle: string; charge?: boolean }) {
  return <View style={styles.routePoint}><View style={[styles.routeDot, charge && styles.routeCharge]}><Ionicons name={charge ? 'flash' : 'ellipse'} size={charge ? 11 : 7} color={Colors.white} /></View><Text style={styles.routeTitle} numberOfLines={1}>{title}</Text><Text style={styles.routeSubtitle}>{subtitle}</Text></View>;
}

function ActionRow({ icon, title, subtitle, danger, loading, onPress }: { icon: keyof typeof Ionicons.glyphMap; title: string; subtitle: string; danger?: boolean; loading?: boolean; onPress: () => void }) {
  return <TouchableOpacity style={styles.actionRow} onPress={onPress} disabled={loading}><View style={[styles.actionIcon, danger && styles.actionIconDanger]}><Ionicons name={loading ? 'sync' : icon} size={17} color={danger ? Colors.error : Colors.primary} /></View><View style={styles.actionCopy}><Text style={styles.actionTitle}>{title}</Text><Text style={styles.actionSubtitle}>{subtitle}</Text></View><Ionicons name="chevron-forward" size={16} color={Colors.textMuted} /></TouchableOpacity>;
}

function formatMinutes(minutes: number) {
  const hours = Math.floor(minutes / 60);
  return hours ? `${hours}h ${minutes % 60}m` : `${minutes}m`;
}

function timingColor(score?: 'HIGH' | 'MEDIUM' | 'LOW') {
  return score === 'LOW' ? Colors.error : score === 'MEDIUM' ? '#D97706' : Colors.success;
}

function statusLabel(status: AutopilotTrip['status']) {
  return ({ RESERVED: 'RESERVED', MONITORING: 'MONITORING', REROUTED: 'REROUTED', PAYMENT_REQUIRED: 'PAYMENT NEEDED', COMPLETED: 'COMPLETE', CANCELLED: 'CANCELLED' })[status];
}

function messageFor(error: unknown) {
  return error instanceof Error ? error.message : 'The requested action could not be completed.';
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Colors.background },
  content: { padding: 14, paddingBottom: 32, gap: 13 },
  hero: { position: 'relative', overflow: 'hidden', padding: 21, borderRadius: 22, backgroundColor: '#092A22' },
  heroGlow: { position: 'absolute', width: 180, height: 180, top: -90, right: -35, borderRadius: 90, backgroundColor: 'rgba(52,211,153,.13)' },
  kicker: { flexDirection: 'row', alignItems: 'center', gap: 6 }, kickerText: { color: '#6EE7B7', fontSize: 9, fontWeight: '900', letterSpacing: 1.1 },
  heroTitle: { marginTop: 13, color: Colors.white, fontSize: 27, lineHeight: 29, fontWeight: '900', letterSpacing: -.7 }, heroAccent: { color: '#6EE7B7' },
  heroCopy: { marginTop: 10, color: '#A8C9BF', fontSize: 10.5, fontWeight: '600' }, trustRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginTop: 17 }, trustChip: { flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 8, paddingVertical: 6, borderWidth: 1, borderColor: 'rgba(255,255,255,.1)', borderRadius: 9, backgroundColor: 'rgba(255,255,255,.04)' }, trustText: { color: '#D5EDE6', fontSize: 8, fontWeight: '800' },
  card: { padding: 17, borderWidth: 1, borderColor: Colors.borderSoft, borderRadius: 19, backgroundColor: Colors.white }, sectionHead: { flexDirection: 'row', alignItems: 'center', gap: 10 }, step: { width: 32, height: 32, alignItems: 'center', justifyContent: 'center', borderRadius: 10, backgroundColor: Colors.primaryLight }, stepText: { color: Colors.primary, fontSize: 9, fontWeight: '900' }, sectionTitle: { color: Colors.textPrimary, fontSize: 15, fontWeight: '900' }, sectionSubtitle: { marginTop: 2, color: Colors.textSecondary, fontSize: 9.5 },
  inputLabel: { marginBottom: 6, color: Colors.textSecondary, fontSize: 8, fontWeight: '900', letterSpacing: .65 }, goalInput: { minHeight: 84, marginTop: 5, padding: 12, borderWidth: 1, borderColor: '#CFE8DF', borderRadius: 13, color: Colors.textPrimary, backgroundColor: Colors.primarySoft, fontSize: 11.5, lineHeight: 17, textAlignVertical: 'top' }, field: { marginTop: 12 }, input: { height: 43, paddingHorizontal: 12, borderWidth: 1, borderColor: Colors.border, borderRadius: 11, color: Colors.textPrimary, backgroundColor: Colors.white, fontSize: 11, fontWeight: '700' }, fieldRow: { flexDirection: 'row', alignItems: 'flex-end', gap: 8 }, flexField: { flex: 1 }, routeArrow: { paddingBottom: 13 }, constraintGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 10 }, compactField: { width: '47%', flexGrow: 1 }, compactInput: { height: 42, paddingHorizontal: 10, flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderColor: Colors.border, borderRadius: 11, backgroundColor: Colors.white }, compactTextInput: { flex: 1, color: Colors.textPrimary, fontSize: 11.5, fontWeight: '800' }, affix: { color: Colors.textSecondary, fontSize: 10, fontWeight: '800' },
  choiceLabel: { marginTop: 13, marginBottom: 7, color: Colors.textSecondary, fontSize: 8, fontWeight: '900', letterSpacing: .65 }, choiceRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 7 }, choiceChip: { minHeight: 34, paddingHorizontal: 10, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 5, borderWidth: 1, borderColor: Colors.border, borderRadius: 10, backgroundColor: Colors.white, flexGrow: 1 }, choiceChipActive: { borderColor: Colors.primary, backgroundColor: Colors.primary }, choiceChipText: { color: Colors.textSecondary, fontSize: 8.5, fontWeight: '900' }, choiceChipTextActive: { color: Colors.white },
  modeList: { gap: 7 }, modeOption: { minHeight: 52, padding: 10, flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderColor: Colors.borderSoft, borderRadius: 12, backgroundColor: '#FBFDFC' }, modeOptionActive: { borderColor: '#88CDB6', backgroundColor: Colors.primarySoft }, modeRadio: { width: 18, height: 18, alignItems: 'center', justifyContent: 'center', marginRight: 9, borderWidth: 1.5, borderColor: Colors.textMuted, borderRadius: 9 }, modeRadioActive: { borderColor: Colors.primary }, modeRadioDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: Colors.primary }, modeCopy: { flex: 1 }, modeTitle: { color: Colors.textPrimary, fontSize: 10, fontWeight: '900' }, modeDetail: { marginTop: 2, color: Colors.textSecondary, fontSize: 8 }, recommendedPill: { paddingHorizontal: 6, paddingVertical: 4, borderRadius: 6, backgroundColor: Colors.primaryLight }, recommendedText: { color: Colors.primary, fontSize: 6.5, fontWeight: '900' },
  memoryNote: { marginTop: 10, padding: 10, flexDirection: 'row', alignItems: 'flex-start', gap: 8, borderRadius: 11, backgroundColor: '#F3FAF7' }, memoryCopy: { flex: 1 }, memoryTitle: { color: Colors.primaryDark, fontSize: 9, fontWeight: '900' }, memoryText: { marginTop: 2, color: Colors.textSecondary, fontSize: 7.5, lineHeight: 11 },
  primaryButton: { height: 45, marginTop: 16, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7, borderRadius: 12, backgroundColor: Colors.primary }, primaryButtonText: { color: Colors.white, fontSize: 11, fontWeight: '900' }, fallbackButton: { height: 40, marginTop: 8, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, borderWidth: 1, borderColor: '#B7DFD2', borderRadius: 11, backgroundColor: '#F2FBF7' }, fallbackButtonText: { color: Colors.primary, fontSize: 10, fontWeight: '900' }, agentReply: { marginTop: 12, padding: 12, flexDirection: 'row', alignItems: 'flex-start', gap: 9, borderWidth: 1, borderColor: '#DED5FA', borderRadius: 12, backgroundColor: '#F8F6FF' }, agentReplyCopy: { flex: 1 }, agentReplyTitle: { color: '#4F36A5', fontSize: 9, fontWeight: '900' }, agentReplyText: { marginTop: 3, color: '#635B80', fontSize: 10, lineHeight: 15 }, toolProof: { marginTop: 10 }, toolProofLabel: { color: '#7C69B2', fontSize: 6.5, fontWeight: '900', letterSpacing: .7 }, toolChips: { marginTop: 5, flexDirection: 'row', flexWrap: 'wrap', gap: 5 }, toolChip: { paddingHorizontal: 7, paddingVertical: 5, flexDirection: 'row', alignItems: 'center', gap: 4, borderRadius: 7, backgroundColor: Colors.white }, toolChipText: { maxWidth: 145, color: '#51456F', fontSize: 7, fontWeight: '800' },
  vehicleSetup: { marginTop: 14, padding: 13, borderWidth: 1, borderStyle: 'dashed', borderColor: '#A8D9C7', borderRadius: 14, backgroundColor: Colors.primarySoft }, vehicleSetupHead: { flexDirection: 'row', alignItems: 'center', gap: 10 }, setupIcon: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center', borderRadius: 12, backgroundColor: Colors.primaryLight }, setupTitle: { color: Colors.textPrimary, fontSize: 12, fontWeight: '900' }, setupCopy: { marginTop: 2, color: Colors.textSecondary, fontSize: 9 }, vehicleChips: { gap: 8, paddingBottom: 5 }, vehicleChip: { minWidth: 145, padding: 10, flexDirection: 'row', alignItems: 'center', gap: 8, borderWidth: 1, borderColor: Colors.border, borderRadius: 11, backgroundColor: Colors.white }, vehicleChipActive: { borderColor: Colors.primary, backgroundColor: Colors.primary }, vehicleChipTitle: { color: Colors.textPrimary, fontSize: 9.5, fontWeight: '900' }, vehicleChipSub: { marginTop: 2, color: Colors.textSecondary, fontSize: 7.5 }, vehicleChipTextActive: { color: Colors.white },
  planHead: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between' }, statusPill: { paddingHorizontal: 8, paddingVertical: 5, borderRadius: 8, backgroundColor: Colors.primaryLight }, statusText: { color: Colors.primary, fontSize: 7.5, fontWeight: '900' }, statusWarning: { backgroundColor: '#FFFAEB' }, statusWarningText: { color: '#B54708' }, statusComplete: { backgroundColor: Colors.primary }, statusCompleteText: { color: Colors.white }, metricGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 7, marginTop: 15 }, metric: { width: '47%', flexGrow: 1, padding: 10, borderRadius: 12, backgroundColor: '#F8FAF9' }, metricIcon: { width: 26, height: 26, alignItems: 'center', justifyContent: 'center', borderRadius: 8, backgroundColor: Colors.primaryLight }, metricValue: { marginTop: 7, color: Colors.textPrimary, fontSize: 12, fontWeight: '900' }, metricLabel: { marginTop: 2, color: Colors.textSecondary, fontSize: 8 },
  previewLabel: { marginTop: 13, color: Colors.textSecondary, fontSize: 9, fontWeight: '800' }, previewStops: { marginTop: 9, gap: 7 }, previewStop: { padding: 10, flexDirection: 'row', gap: 8, borderRadius: 12, backgroundColor: '#F8FAF9' }, timingDot: { width: 8, height: 8, marginTop: 3, borderRadius: 4 }, previewStopCopy: { flex: 1 }, previewStopTitle: { color: Colors.textPrimary, fontSize: 10, fontWeight: '900' }, previewStopMeta: { marginTop: 3, color: Colors.textSecondary, fontSize: 7.5 }, timingText: { marginTop: 4, fontSize: 8, fontWeight: '900' }, actionBoundary: { marginTop: 12, padding: 10, flexDirection: 'row', alignItems: 'flex-start', gap: 7, borderRadius: 10, backgroundColor: Colors.primarySoft }, actionBoundaryText: { flex: 1, color: Colors.primaryDark, fontSize: 8, lineHeight: 12, fontWeight: '700' },
  routeStrip: { flexDirection: 'row', alignItems: 'flex-start', marginTop: 18 }, routePoint: { width: 68, alignItems: 'center' }, routeDot: { width: 24, height: 24, alignItems: 'center', justifyContent: 'center', borderRadius: 12, backgroundColor: Colors.primary }, routeCharge: { backgroundColor: Colors.primaryDark }, routeLine: { flex: 1, height: 2, marginTop: 11, backgroundColor: '#A7E4CF' }, routeTitle: { width: 74, marginTop: 5, color: Colors.textPrimary, fontSize: 7.5, fontWeight: '900', textAlign: 'center' }, routeSubtitle: { marginTop: 2, color: Colors.textSecondary, fontSize: 6.5, textAlign: 'center' },
  telemetryCard: { padding: 17, borderRadius: 19, backgroundColor: '#0B3027' }, telemetryHead: { flexDirection: 'row', justifyContent: 'space-between' }, telemetryKicker: { color: '#6EE7B7', fontSize: 7.5, fontWeight: '900', letterSpacing: .9 }, telemetryTitle: { marginTop: 5, color: Colors.white, fontSize: 14, fontWeight: '900' }, telemetrySub: { marginTop: 2, color: '#9FC0B7', fontSize: 8.5 }, livePill: { height: 25, paddingHorizontal: 8, flexDirection: 'row', alignItems: 'center', gap: 5, borderRadius: 8, backgroundColor: 'rgba(52,211,153,.12)' }, liveDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: '#34D399' }, liveText: { color: '#6EE7B7', fontSize: 7.5, fontWeight: '900' }, batteryRow: { marginTop: 17, flexDirection: 'row', alignItems: 'center' }, batteryIcon: { width: 47, height: 47, alignItems: 'center', justifyContent: 'center', marginRight: 11, borderRadius: 15, backgroundColor: 'rgba(52,211,153,.11)' }, batteryValue: { color: Colors.white, fontSize: 23, fontWeight: '900' }, batteryRange: { marginTop: 2, color: '#9FC0B7', fontSize: 8 }, walletMetric: { marginLeft: 'auto', alignItems: 'flex-end' }, walletLabel: { color: '#6E9E91', fontSize: 7 }, walletValue: { marginTop: 4, color: Colors.white, fontSize: 14, fontWeight: '900' }, progressTrack: { height: 5, marginTop: 14, overflow: 'hidden', borderRadius: 4, backgroundColor: '#274A41' }, progressFill: { height: '100%', borderRadius: 4, backgroundColor: '#34D399' }, paymentNote: { marginTop: 11, padding: 9, flexDirection: 'row', alignItems: 'flex-start', gap: 7, borderRadius: 10, backgroundColor: 'rgba(255,255,255,.055)' }, paymentWarning: { backgroundColor: 'rgba(247,144,9,.12)' }, paymentText: { flex: 1, color: '#C5DDD6', fontSize: 8.5, lineHeight: 12 },
  stopList: { marginTop: 13, gap: 8 }, stopRow: { padding: 10, flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderColor: Colors.borderSoft, borderRadius: 12, backgroundColor: '#FCFDFD' }, stopCancelled: { opacity: .55, backgroundColor: '#FFF8F7' }, stopIndex: { width: 29, height: 29, alignItems: 'center', justifyContent: 'center', marginRight: 9, borderRadius: 9, backgroundColor: Colors.primaryLight }, stopIndexDanger: { backgroundColor: Colors.errorLight }, stopIndexText: { color: Colors.primary, fontSize: 9, fontWeight: '900' }, stopCopy: { flex: 1, minWidth: 0 }, stopTitleRow: { flexDirection: 'row', alignItems: 'center', gap: 5 }, stopTitle: { flexShrink: 1, color: Colors.textPrimary, fontSize: 10, fontWeight: '900' }, stopStatus: { paddingHorizontal: 5, paddingVertical: 2, overflow: 'hidden', borderRadius: 5, color: Colors.primary, backgroundColor: Colors.primaryLight, fontSize: 6, fontWeight: '900' }, stopAddress: { marginTop: 2, color: Colors.textSecondary, fontSize: 7.5 }, stopMeta: { marginTop: 5, color: Colors.textSecondary, fontSize: 7.5, fontWeight: '700' }, stopBattery: { alignItems: 'center', gap: 2, marginLeft: 7 }, stopBatteryText: { color: Colors.textPrimary, fontSize: 8, fontWeight: '900' },
  stopTimingRow: { marginTop: 5, flexDirection: 'row', justifyContent: 'space-between', gap: 7 }, stopTiming: { flex: 1, fontSize: 7.5, fontWeight: '900' }, alternativesLink: { color: Colors.primary, fontSize: 7.5, fontWeight: '900' }, alternativeRow: { marginTop: 5, marginLeft: 38, padding: 9, flexDirection: 'row', alignItems: 'center', gap: 8, borderRadius: 10, backgroundColor: Colors.primarySoft, borderWidth: 1, borderColor: '#CFE8DF' }, alternativeCopy: { flex: 1 }, alternativeTitle: { color: Colors.textPrimary, fontSize: 9, fontWeight: '900' }, alternativeMeta: { marginTop: 2, color: Colors.textSecondary, fontSize: 7 }, swapText: { color: Colors.primary, fontSize: 8, fontWeight: '900' },
  actionList: { marginTop: 12, gap: 7 }, actionRow: { minHeight: 56, padding: 9, flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderColor: Colors.borderSoft, borderRadius: 12, backgroundColor: '#FBFDFC' }, actionIcon: { width: 33, height: 33, alignItems: 'center', justifyContent: 'center', marginRight: 9, borderRadius: 10, backgroundColor: Colors.primaryLight }, actionIconDanger: { backgroundColor: Colors.errorLight }, actionCopy: { flex: 1 }, actionTitle: { color: Colors.textPrimary, fontSize: 9.5, fontWeight: '900' }, actionSubtitle: { marginTop: 2, color: Colors.textSecondary, fontSize: 7.5 },
  timeline: { marginTop: 14 }, timelineItem: { paddingBottom: 13, flexDirection: 'row' }, timelineIcon: { width: 25, height: 25, alignItems: 'center', justifyContent: 'center', marginRight: 9, borderRadius: 8, backgroundColor: Colors.primaryLight }, timelineWarning: { backgroundColor: Colors.errorLight }, timelineInfo: { backgroundColor: Colors.blueLight }, timelineCopy: { flex: 1 }, timelineTitleRow: { flexDirection: 'row', justifyContent: 'space-between', gap: 7 }, timelineTitle: { color: Colors.textPrimary, fontSize: 9.5, fontWeight: '900' }, timelineTime: { color: Colors.textMuted, fontSize: 7 }, timelineDetail: { marginTop: 3, color: Colors.textSecondary, fontSize: 8, lineHeight: 11 },
  emptyCard: { padding: 28, alignItems: 'center', borderWidth: 1, borderColor: Colors.borderSoft, borderRadius: 19, backgroundColor: Colors.white }, emptyIcon: { width: 64, height: 64, alignItems: 'center', justifyContent: 'center', borderRadius: 20, backgroundColor: Colors.primaryLight }, emptyTitle: { marginTop: 13, color: Colors.textPrimary, fontSize: 15, fontWeight: '900' }, emptyText: { marginTop: 6, color: Colors.textSecondary, fontSize: 10, lineHeight: 15, textAlign: 'center' },
});
