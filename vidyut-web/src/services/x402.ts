import { apiRequest } from './api';

export type X402PaymentStatus =
  | 'PREPARED' | 'PAYMENT_PENDING' | 'SETTLED' | 'BOOKING_CONFIRMED'
  | 'SETTLED_BOOKING_FAILED' | 'COMPENSATION_PENDING' | 'COMPENSATED'
  | 'REFUNDED' | 'FAILED';

export interface PaymentRequirements {
  scheme: string;
  network: string;
  asset: string;
  amount: string;
  payTo: string;
  maxTimeoutSeconds: number;
  extra: Record<string, unknown>;
}

export interface PaymentRequired {
  x402Version: number;
  resource: { url: string; description?: string; mimeType?: string };
  accepts: PaymentRequirements[];
  error?: string;
  extensions?: Record<string, unknown>;
}

export interface PaymentPayload {
  x402Version: number;
  resource?: PaymentRequired['resource'];
  accepted: PaymentRequirements;
  payload: Record<string, unknown>;
  extensions?: Record<string, unknown>;
}

export interface X402PaymentRecord {
  paymentId: string;
  idempotencyKey: string;
  tripId: number;
  stationId: number;
  connectorId: number;
  bookingId?: number;
  network: string;
  asset: string;
  scheme: string;
  paymentPurpose: string;
  payerAddress?: string;
  recipientAddress: string;
  amountAtomic: number;
  displayAmount: number;
  txId?: string;
  status: X402PaymentStatus;
  failureReason?: string;
  actionBundleId: string;
  paymentRequired: PaymentRequired;
  explorerUrl?: string;
}

const gatewayBase = (import.meta.env.VITE_X402_GATEWAY_URL?.trim() || '/x402').replace(/\/+$/, '');

export function getX402Payment(paymentId: string): Promise<X402PaymentRecord> {
  return apiRequest<X402PaymentRecord>(`/ev/x402/records/${encodeURIComponent(paymentId)}`, { method: 'GET' });
}

export async function requestX402PaymentRequirements(record: X402PaymentRecord): Promise<PaymentRequired> {
  const response = await fetch(`${gatewayBase}/payment-requirements`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      paymentId: record.paymentId,
      idempotencyKey: record.idempotencyKey,
      purpose: record.paymentPurpose,
      paymentRequired: record.paymentRequired,
    }),
  });
  const body = await response.json().catch(() => null) as PaymentRequired | { error?: string } | null;
  if (response.status !== 402) {
    throw new Error(body && 'error' in body && body.error ? body.error : `x402 gateway returned HTTP ${response.status}`);
  }
  if (!body || !('x402Version' in body) || body.x402Version !== 2) {
    throw new Error('x402 gateway returned an invalid PaymentRequired response');
  }
  return body as PaymentRequired;
}

export async function settleSignedX402Payment(
  token: string,
  record: X402PaymentRecord,
  paymentPayload: PaymentPayload,
): Promise<X402PaymentRecord> {
  const response = await fetch(`${gatewayBase}/settle`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({
      paymentId: record.paymentId,
      idempotencyKey: record.idempotencyKey,
      purpose: record.paymentPurpose,
      paymentRequired: record.paymentRequired,
      paymentPayload,
    }),
  });
  const body = await response.json().catch(() => null) as { error?: string } | null;
  if (!response.ok) throw new Error(body?.error || `x402 settlement failed (HTTP ${response.status})`);
  return getX402Payment(record.paymentId);
}
