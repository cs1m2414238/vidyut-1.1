import { useState } from 'react';
import type { AutopilotTrip } from '../services/autopilot';
import './AutopilotRecoveryPanel.css';

const number = (value: number | undefined | null, digits = 1) => value == null ? 'Not available' : value.toFixed(digits);
const delta = (value: number | undefined | null, unit: string) => value == null ? 'Comparison unavailable' : `${value > 0 ? '+' : ''}${value.toFixed(1)} ${unit}`;

export function AutopilotRecoveryPanel({ trip, busy, onApprove, onRetry, onPosition }: {
  trip: AutopilotTrip; busy: boolean; onApprove: () => void; onRetry: () => void;
  onPosition: (soc: number) => void;
}) {
  const [soc, setSoc] = useState(trip.telemetry.batteryPercent);
  const [review, setReview] = useState(false);
  const r = trip.recovery;
  if (!r) return null;
  const bridge = r.proposedStops?.[0];
  const next = r.proposedStops?.[1];
  const prepared = Boolean(r.planId);
  const executed = r.state === 'EXECUTED';
  const failed = trip.stops.find(s => r.failedConnectorId ? s.connectorId === r.failedConnectorId : s.stationId === r.failedStationId);
  const title = executed ? (trip.autonomyMode === 'FULL_AUTOPILOT' ? 'Vidyut automatically rerouted your journey' : 'Approved recovery route applied')
    : prepared ? 'Vidyut found a safe recovery route' : r.state === 'NO_SAFE_RECOVERY_ROUTE' ? 'No safe recovery route verified' : 'Vidyut is evaluating recovery';
  return <section className="agent-recovery-panel" aria-live="polite">
    <span className="agent-recovery-eyebrow">EV OWNER AGENT · {r.state.replaceAll('_', ' ')}</span>
    <h2>{title}</h2>
    <p>{failed?.stationName ?? 'Planned charging stop'} · {failed?.chargerCode ?? `connector ${r.failedConnectorId ?? 'unknown'}`} is unavailable.</p>
    {!prepared && <p>{busy ? 'The agent is asking the backend for complete routes from the current vehicle position, then selecting a safe option.' : r.reason}</p>}
    {prepared && <>
      <h3>{r.strategy === 'BRIDGE_RECOVERY' ? 'Bridge recovery charger' : r.strategy === 'DIRECT_NEXT_STOP' ? 'Continue to next planned charger' : 'Continue to destination'}: {bridge?.stationName ?? trip.destination}</h3>
      {bridge && <p>{bridge.chargerCode} · {bridge.connectorType} · {bridge.powerKw} kW · {number(r.distanceToBridgeKm)} road km from captured position</p>}
      <dl className="agent-recovery-metrics">
        <div><dt>Battery at evaluation</dt><dd>{number(r.currentSoc)}%</dd></div>
        {bridge && <><div><dt>Arrival battery</dt><dd>{number(r.predictedArrivalSoc)}%</dd></div><div><dt>Charge only to</dt><dd>{number(r.departureTargetSoc)}%</dd></div></>}
        <div><dt>Minimum reserve, every leg</dt><dd>{number(r.reserveSoc)}%</dd></div>
        <div><dt>Next leg after recovery stop</dt><dd>{next?.stationName ?? trip.destination}</dd></div>
        <div><dt>Remaining route</dt><dd>{number(r.newRemainingDistanceKm)} km · {number(r.newRemainingMinutes, 0)} min</dd></div>
        <div><dt>Remaining charging cost</dt><dd>₹{number(r.remainingCost, 2)}</dd></div>
        <div><dt>Change versus original remaining route</dt><dd>{delta(r.additionalDistanceKm, 'km')} · {delta(r.additionalMinutes, 'min')} · {delta(r.additionalCost, '₹')}</dd></div>
        <div><dt>Estimated arrival (UTC)</dt><dd>{r.estimatedArrivalTime?.replace('T', ' ') ?? 'Not available'}</dd></div>
      </dl>
      <p>Position source: {r.positionSource === 'DEMO_ROUTE_PROGRESS' ? 'Explicit demo road simulation' : r.positionSource ?? 'Unavailable'} · Road engine: {r.routeEngine} · Selection: {r.agentProvider === 'GEMINI' ? 'Gemini agent' : 'EV Agent policy fallback'}</p>
      <ol>{r.proposedStops?.map((s, i) => <li key={s.connectorId ?? i}>{s.stationName} · {s.chargerCode} · arrive {number(s.arrivalBatteryPercent)}% → {number(s.targetBatteryPercent)}% · {s.chargingMinutes} min · ₹{number(s.estimatedCost, 2)}</li>)}</ol>
    </>}
    <p><strong>{executed ? 'Recovery reservations and navigation updated.' : r.state === 'SUGGESTED' ? 'Recommend Only: this is a suggestion. No reservations or navigation changed.' : 'Existing reservations and navigation remain unchanged until execution is permitted.'}</strong></p>
    {r.state === 'AWAITING_APPROVAL' && <div className="agent-recovery-actions">
      {!review ? <button disabled={busy} onClick={() => setReview(true)}>Review reroute approval</button> : <>
        <span>Replace remaining reservations and apply this complete route?</span>
        <button disabled={busy} onClick={() => { setReview(false); onApprove(); }}>Approve Reroute</button>
        <button disabled={busy} onClick={() => setReview(false)}>Cancel</button>
      </>}
    </div>}
    {!executed && <div className="agent-recovery-actions"><button disabled={busy} onClick={onRetry}>{busy ? 'Agent working…' : 'Re-evaluate with Vidyut'}</button></div>}
    {!executed && trip.telemetry.positionSource !== 'DEMO_ROUTE_PROGRESS' && <div className="agent-recovery-actions">
      <label>Current vehicle battery (%) <input type="number" min="0" max="100" value={soc} onChange={e => setSoc(Number(e.target.value))} /></label>
      <button disabled={busy || !Number.isFinite(soc) || soc < 0 || soc > 100} onClick={() => onPosition(soc)}>Update from my current GPS</button>
      <small>Use only while this device is in the vehicle. You supply the current vehicle SoC.</small>
    </div>}
  </section>;
}
