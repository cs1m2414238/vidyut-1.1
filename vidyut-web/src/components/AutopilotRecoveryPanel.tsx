import { useEffect, useState } from 'react';
import type { AutopilotTrip } from '../services/autopilot';
import { getX402Payment, requestX402PaymentRequirements, settleSignedX402Payment,
  type PaymentPayload, type PaymentRequired, type X402PaymentRecord } from '../services/x402';
import './AutopilotRecoveryPanel.css';

const number = (value: number | undefined | null, digits = 1) => value == null ? 'Not available' : value.toFixed(digits);
const delta = (value: number | undefined | null, unit: string) => value == null ? 'Comparison unavailable' : `${value > 0 ? '+' : ''}${value.toFixed(1)} ${unit}`;

export function AutopilotRecoveryPanel({ trip, token, busy, onApprove, onRetry, onPosition }: {
  trip: AutopilotTrip; token: string; busy: boolean; onApprove: () => void; onRetry: () => void;
  onPosition: (soc: number) => void;
}) {
  const [soc, setSoc] = useState(trip.telemetry.batteryPercent);
  const [review, setReview] = useState(false);
  const [paymentRecord, setPaymentRecord] = useState<X402PaymentRecord | null>(null);
  const [paymentRequired, setPaymentRequired] = useState<PaymentRequired | null>(null);
  const [signedPayload, setSignedPayload] = useState('');
  const [paymentBusy, setPaymentBusy] = useState(false);
  const [paymentError, setPaymentError] = useState('');
  const [copied, setCopied] = useState(false);
  const r = trip.recovery;

  useEffect(() => {
    if (!r?.paymentId) { setPaymentRecord(null); return; }
    let active = true;
    void getX402Payment(r.paymentId).then(record => { if (active) setPaymentRecord(record); }).catch(() => undefined);
    return () => { active = false; };
  }, [r?.paymentId, r?.paymentStatus]);

  if (!r) return null;
  const paymentStatus = paymentRecord?.status ?? r.paymentStatus;
  const paymentConfirmed = paymentStatus === 'BOOKING_CONFIRMED';

  const openPaymentReview = async () => {
    setReview(true); setPaymentError(''); setPaymentBusy(true);
    try {
      if (!r.paymentId) return;
      const record = await getX402Payment(r.paymentId);
      setPaymentRecord(record);
      if (record.status !== 'BOOKING_CONFIRMED') setPaymentRequired(await requestX402PaymentRequirements(record));
    } catch (error) {
      setPaymentError(error instanceof Error ? error.message : 'Unable to prepare the x402 wallet handoff.');
    } finally { setPaymentBusy(false); }
  };

  const settleDriverPayment = async () => {
    if (!paymentRecord) return;
    setPaymentBusy(true); setPaymentError('');
    try {
      const parsed = JSON.parse(signedPayload) as PaymentPayload;
      const next = await settleSignedX402Payment(token, paymentRecord, parsed);
      setPaymentRecord(next);
      if (next.status === 'BOOKING_CONFIRMED') { setReview(false); onApprove(); }
    } catch (error) {
      setPaymentError(error instanceof Error ? error.message : 'Unable to settle the signed x402 payment.');
    } finally { setPaymentBusy(false); }
  };

  const copyRequirements = async () => {
    if (!paymentRequired) return;
    try { await navigator.clipboard.writeText(JSON.stringify(paymentRequired, null, 2)); setCopied(true); }
    catch { setPaymentError('Clipboard access was blocked. Select and copy the payment request manually.'); }
  };
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
    {r.paymentId && <div className={`x402-payment-strip x402-${(paymentStatus ?? 'PREPARED').toLowerCase()}`}>
      <div><span className="x402-protocol-badge">HTTP 402 · Algorand Testnet</span>
        <strong>{Number(paymentRecord?.displayAmount ?? r.paymentDisplayAmount ?? 0.05).toFixed(6)} ALGO</strong>
        <small>{paymentRecord?.amountAtomic ?? r.paymentAmountAtomic ?? 50000} microAlgos · asset 0</small></div>
      <div><span>Payment state</span><strong>{(paymentStatus ?? 'PREPARED').replaceAll('_', ' ')}</strong>
        {(paymentRecord?.explorerUrl ?? r.paymentExplorerUrl) && <a href={paymentRecord?.explorerUrl ?? r.paymentExplorerUrl}
          target="_blank" rel="noreferrer">View confirmed transaction on Lora ↗</a>}</div>
      {(paymentRecord?.failureReason ?? r.paymentFailureReason) && <p role="alert">{paymentRecord?.failureReason ?? r.paymentFailureReason}</p>}
    </div>}
    {r.state === 'AWAITING_APPROVAL' && <div className="agent-recovery-actions">
      {!review ? <button disabled={busy || paymentBusy} onClick={() => void openPaymentReview()}>Review reroute & x402 payment</button> : <>
        {r.paymentId && !paymentConfirmed ? <div className="x402-wallet-handoff">
          <strong>Driver wallet signature required</strong>
          <p>Vidyut does not hold your driver-wallet mnemonic. Copy the issued v2 PaymentRequired object, sign it in your Algorand wallet tooling, then paste the resulting PaymentPayload.</p>
          {paymentRequired && <><button type="button" disabled={paymentBusy} onClick={() => void copyRequirements()}>{copied ? 'Payment request copied' : 'Copy PaymentRequired JSON'}</button>
            <details><summary>Inspect exact payment request</summary><pre>{JSON.stringify(paymentRequired, null, 2)}</pre></details></>}
          <label>Signed PaymentPayload JSON<textarea value={signedPayload} rows={6}
            placeholder='{"x402Version":2,"accepted":{...},"payload":{"paymentGroup":[...],"paymentIndex":0}}'
            onChange={event => setSignedPayload(event.target.value)} /></label>
          <button disabled={busy || paymentBusy || !signedPayload.trim()} onClick={() => void settleDriverPayment()}>
            {paymentBusy ? 'Verifying on Testnet…' : 'Settle payment & apply reroute'}
          </button>
        </div> : <>
          <span>{paymentConfirmed ? 'Payment and connector booking are confirmed. Apply the complete recovery route?' : 'Replace remaining reservations and apply this complete route?'}</span>
          <button disabled={busy || paymentBusy} onClick={() => { setReview(false); onApprove(); }}>Approve Reroute</button>
        </>}
        {paymentError && <span className="x402-payment-error" role="alert">{paymentError}</span>}
        <button disabled={busy || paymentBusy} onClick={() => setReview(false)}>Cancel</button>
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
