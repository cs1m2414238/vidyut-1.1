export interface WalletTransactionItem {
  id: number;
  walletId: number;
  vehicleId?: number | null;
  amount: number;
  type: 'TOP_UP' | 'AUTO_RECHARGE' | 'CHARGING_PAYMENT' | string;
  description: string;
  timestamp: string;
}

export interface WalletData {
  walletId: number;
  userId: number;
  balance: number;
  recentTransactions: WalletTransactionItem[];
}

export interface AutoRechargeRule {
  id: number;
  vehicleId: number;
  vehicleName: string;
  registrationNumber: string;
  enabled: boolean;
  balanceThreshold: number;
  rechargeAmount: number;
  paymentMethod: string;
  lastTriggeredAt?: string | null;
  updatedAt: string;
}

export interface SaveAutoRechargeRuleRequest {
  vehicleId: number;
  enabled: boolean;
  balanceThreshold: number;
  rechargeAmount: number;
  paymentMethod: string;
}

export interface VehicleWalletTransaction {
  id: number; bookingId?: number; amount: number; balanceBefore: number; balanceAfter: number;
  type: 'TOP_UP' | 'AUTO_RECHARGE' | 'CHARGING_PAYMENT' | 'REFUND' | string;
  description: string; paymentMethod?: string; paymentReference?: string; timestamp: string;
}

export interface VehicleWalletData {
  walletId: number; vehicleId: number; vehicleName: string; registrationNumber: string; tagUid: string;
  balance: number; active: boolean; lowBalance: boolean; recentTransactions: VehicleWalletTransaction[];
}
