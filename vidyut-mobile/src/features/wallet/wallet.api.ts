import { apiClient } from '../../services/apiClient';
import { ApiResponse, getApiErrorMessage, unwrapApiResponse } from '../../services/apiResponse';
import { AutoRechargeRule, SaveAutoRechargeRuleRequest, VehicleWalletData, WalletData } from './wallet.types';

export async function getWallet(): Promise<WalletData> {
  try {
    const response = await apiClient.get<ApiResponse<WalletData>>('/ev/wallet');
    return unwrapApiResponse(response.data);
  } catch (error) {
    throw new Error(getApiErrorMessage(error, 'Unable to load your wallet.'));
  }
}

export async function getVehicleWallets(): Promise<VehicleWalletData[]> {
  try { const response = await apiClient.get<ApiResponse<VehicleWalletData[]>>('/ev/wallet/vehicles'); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to load vehicle wallets.')); }
}

export async function getVehicleWallet(vehicleId: number): Promise<VehicleWalletData> {
  try { const response = await apiClient.get<ApiResponse<VehicleWalletData>>(`/ev/wallet/vehicles/${vehicleId}`); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to load this vehicle wallet.')); }
}

export async function topUpVehicleWallet({ vehicleId, amount }: { vehicleId: number; amount: number }): Promise<VehicleWalletData> {
  try {
    const response = await apiClient.post<ApiResponse<VehicleWalletData>>(`/ev/wallet/vehicles/${vehicleId}/topup`, {
      amount, paymentMethod: 'UPI_TOKEN', paymentReference: `MOBILE-${Date.now()}`,
    });
    return unwrapApiResponse(response.data);
  } catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to top up this vehicle wallet.')); }
}

export async function topUpWallet(amount: number): Promise<WalletData> {
  try {
    const response = await apiClient.post<ApiResponse<WalletData>>('/ev/wallet/topup', { amount });
    return unwrapApiResponse(response.data);
  } catch (error) {
    throw new Error(getApiErrorMessage(error, 'Unable to top up your wallet.'));
  }
}

export async function getAutoRechargeRules(): Promise<AutoRechargeRule[]> {
  try {
    const response = await apiClient.get<ApiResponse<AutoRechargeRule[]>>('/ev/wallet/auto-recharge');
    return unwrapApiResponse(response.data);
  } catch (error) {
    throw new Error(getApiErrorMessage(error, 'Unable to load auto-recharge settings.'));
  }
}

export async function saveAutoRechargeRule(request: SaveAutoRechargeRuleRequest): Promise<AutoRechargeRule> {
  try {
    const response = await apiClient.put<ApiResponse<AutoRechargeRule>>(
      `/ev/wallet/auto-recharge/${request.vehicleId}`,
      request,
    );
    return unwrapApiResponse(response.data);
  } catch (error) {
    throw new Error(getApiErrorMessage(error, 'Unable to update auto-recharge.'));
  }
}
