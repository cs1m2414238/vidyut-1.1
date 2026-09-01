import { apiClient } from '../../services/apiClient';
import { ApiResponse, getApiErrorMessage, unwrapApiResponse } from '../../services/apiResponse';
import { CreateVehicleRequest, UpdateVehicleRequest, VehicleItem } from './vehicle.types';

export async function getMyVehicles(): Promise<VehicleItem[]> {
  try {
    const response = await apiClient.get<ApiResponse<VehicleItem[]>>('/ev/vehicles');
    return unwrapApiResponse(response.data);
  } catch (error) {
    throw new Error(getApiErrorMessage(error, 'Unable to load your vehicles.'));
  }
}

export async function getVehicle(id: number): Promise<VehicleItem> {
  try {
    const response = await apiClient.get<ApiResponse<VehicleItem>>(`/ev/vehicles/${id}`);
    return unwrapApiResponse(response.data);
  } catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to load the vehicle.')); }
}

export async function updateVehicle(id: number, request: UpdateVehicleRequest): Promise<VehicleItem> {
  try {
    const response = await apiClient.patch<ApiResponse<VehicleItem>>(`/ev/vehicles/${id}`, request);
    return unwrapApiResponse(response.data);
  } catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to update the vehicle.')); }
}

export async function deleteVehicle(id: number): Promise<void> {
  try { await apiClient.delete(`/ev/vehicles/${id}`); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to remove the vehicle.')); }
}

export async function addVehicle(request: CreateVehicleRequest): Promise<VehicleItem> {
  try {
    const response = await apiClient.post<ApiResponse<VehicleItem>>('/ev/vehicles', request);
    return unwrapApiResponse(response.data);
  } catch (error) {
    throw new Error(getApiErrorMessage(error, 'Unable to add your vehicle.'));
  }
}
