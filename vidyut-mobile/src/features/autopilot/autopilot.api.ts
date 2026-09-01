import { apiClient } from '../../services/apiClient';
import { ApiResponse, getApiErrorMessage, unwrapApiResponse } from '../../services/apiResponse';
import { AutopilotAlternative, AutopilotPlan, AutopilotTrip, AutopilotTripSummary, LaunchAutopilotTripRequest } from './autopilot.types';

export async function previewAutopilotTrip(request: LaunchAutopilotTripRequest): Promise<AutopilotPlan> {
  try { const response = await apiClient.post<ApiResponse<AutopilotPlan>>('/agent/plan', request); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to preview this journey.')); }
}

export async function getCurrentAutopilotTrip(): Promise<AutopilotTrip | null> {
  try {
    const response = await apiClient.get<ApiResponse<AutopilotTrip | null>>('/ev/autopilot/trips/current');
    return unwrapApiResponse(response.data);
  } catch (error) {
    throw new Error(getApiErrorMessage(error, 'Unable to load the Autopilot journey.'));
  }
}

export async function launchAutopilotTrip(request: LaunchAutopilotTripRequest): Promise<AutopilotTrip> {
  try {
    const response = await apiClient.post<ApiResponse<AutopilotTrip>>('/ev/autopilot/trips', request);
    return unwrapApiResponse(response.data);
  } catch (error) {
    throw new Error(getApiErrorMessage(error, 'Unable to plan this journey.'));
  }
}

export async function startAutopilotJourney(tripId: number): Promise<AutopilotTrip> {
  try {
    const response = await apiClient.post<ApiResponse<AutopilotTrip>>(
      `/ev/autopilot/trips/${tripId}/start`,
      { batteryDropPercent: 6 },
    );
    return unwrapApiResponse(response.data);
  } catch (error) {
    throw new Error(getApiErrorMessage(error, 'Unable to start journey monitoring.'));
  }
}

export async function simulateAutopilotFault(tripId: number): Promise<AutopilotTrip> {
  try {
    const response = await apiClient.post<ApiResponse<AutopilotTrip>>(
      `/ev/autopilot/trips/${tripId}/simulate-fault`,
      {},
    );
    return unwrapApiResponse(response.data);
  } catch (error) {
    throw new Error(getApiErrorMessage(error, 'Unable to replan around the charger fault.'));
  }
}

export async function completeAutopilotCharging(tripId: number): Promise<AutopilotTrip> {
  try {
    const response = await apiClient.post<ApiResponse<AutopilotTrip>>(
      `/ev/autopilot/trips/${tripId}/complete-charging`,
      {},
    );
    return unwrapApiResponse(response.data);
  } catch (error) {
    throw new Error(getApiErrorMessage(error, 'Unable to complete charging and AutoPay.'));
  }
}

export interface AutopilotAgentResponse {
  sessionId: string;
  requestId: string;
  reply: string;
  model: string;
  toolCalls: Array<{ name: string; status: string }>;
}

export async function sendAutopilotAgentMessage(
  message: string,
  sessionId?: string,
): Promise<AutopilotAgentResponse> {
  try {
    const response = await apiClient.post<ApiResponse<AutopilotAgentResponse>>('/ev/agent/chat', {
      message,
      sessionId,
      requestId: `mobile-agent-${Date.now()}`,
    });
    return unwrapApiResponse(response.data);
  } catch (error) {
    throw new Error(getApiErrorMessage(error, 'The Gemini agent is unavailable. Start the Python service and check its API key.'));
  }
}

export async function getAutopilotAlternatives(tripId: number, stopId: number): Promise<AutopilotAlternative[]> {
  try { const response = await apiClient.get<ApiResponse<AutopilotAlternative[]>>(`/agent/plans/${tripId}/legs/${stopId}/alternatives`); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to load timing-matched alternatives.')); }
}

export async function swapAutopilotStop(tripId: number, stopId: number, stationId: number): Promise<AutopilotTrip> {
  try { const response = await apiClient.post<ApiResponse<AutopilotTrip>>(`/agent/plans/${tripId}/legs/${stopId}/swap`, { stationId }); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to swap this charging stop.')); }
}

export async function simulateAutopilotDelay(tripId: number, delayMinutes = 25): Promise<AutopilotTrip> {
  try { const response = await apiClient.post<ApiResponse<AutopilotTrip>>(`/agent/plans/${tripId}/simulate-delay`, { delayMinutes }); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to simulate the route delay.')); }
}

export async function getAutopilotSummary(tripId: number): Promise<AutopilotTripSummary> {
  try { const response = await apiClient.get<ApiResponse<AutopilotTripSummary>>(`/agent/trips/${tripId}/summary`); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to build the trip summary.')); }
}
