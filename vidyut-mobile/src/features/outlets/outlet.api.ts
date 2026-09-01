import { apiClient } from '../../services/apiClient';
import { ApiResponse, getApiErrorMessage, unwrapApiResponse } from '../../services/apiResponse';
import { OutletStats, OutletTier } from './outlet.types';

export async function getMyOutletTier(outletId: number | string): Promise<OutletTier> {
  try {
    const response = await apiClient.get<ApiResponse<OutletTier>>(`/outlets/${outletId}/my-tier`);
    return unwrapApiResponse(response.data);
  } catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to load outlet access.')); }
}

export async function getMyOutletStats(outletId: number | string): Promise<OutletStats> {
  try {
    const response = await apiClient.get<ApiResponse<OutletStats>>(`/outlets/${outletId}/my-stats`);
    return unwrapApiResponse(response.data);
  } catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to load outlet stats.')); }
}

export async function submitInstitutionId(outletId: number | string, documentUri: string): Promise<void> {
  try { await apiClient.post('/users/verify-institution', { outletId: Number(outletId), documentUri }); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to submit the institution ID.')); }
}
