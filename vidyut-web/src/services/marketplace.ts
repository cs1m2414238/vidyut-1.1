import { apiRequest } from './api';

export type BusinessModel = 'PURCHASE' | 'LEASE' | 'REVENUE_SHARE' | 'COMPANY_OWNED';
export type InstallationStatus = 'REQUESTED' | 'UNDER_REVIEW' | 'SITE_SURVEY_REQUESTED' | 'SITE_SURVEY_SCHEDULED' | 'SURVEY_COMPLETED' | 'PROPOSAL_SENT' | 'ACCEPTED' | 'INSTALLATION_SCHEDULED' | 'INSTALLING' | 'INSTALLED' | 'COMMISSIONED' | 'LIVE' | 'DECLINED' | 'CANCELLED' | 'EXPIRED';

export interface HostProperty {
  id: number;
  title: string;
  address: string;
  city?: string;
  state?: string;
  pincode?: string;
  latitude: number;
  longitude: number;
  pricePerKwh: number;
  propertyType: string;
  availableParkingBays: number;
  powerPhase: string;
  availableLoadKw: number;
  operatingHours?: string;
  ownershipType: string;
  preferredConnectorType?: string;
  preferredPowerKw: number;
  photoUrls?: string;
  ownershipDocumentUrl?: string;
  electricityDocumentUrl?: string;
  videoVerificationUrl?: string;
  adminReviewNote?: string;
  verificationStage?: string;
  discoverable: boolean;
  status: string;
}

export interface ChargerProduct {
  id: number;
  companyId: number;
  companyName: string;
  modelName: string;
  manufacturer: string;
  currentType: 'AC' | 'DC';
  connectorType: string;
  powerKw: number;
  equipmentPrice: number;
  installationPrice: number;
  warrantyMonths: number;
  amcAvailable: boolean;
  certifications?: string;
  description?: string;
  imageUrl?: string;
  businessModels: BusinessModel[];
  active: boolean;
  complianceDocumentUrl?: string;
  approvalStatus: 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';
  adminReviewNote?: string;
}

export interface MarketplaceCompany {
  id: number;
  companyName: string;
  website?: string;
  supportEmail?: string;
  supportPhone?: string;
  verificationStatus: string;
  matchedBy: string;
  distanceKm?: number;
  products: ChargerProduct[];
}

export interface Proposal {
  id: number;
  equipmentTotal: number;
  installationTotal: number;
  monthlyLease?: number;
  hostRevenueSharePercent?: number;
  companyRevenueSharePercent?: number;
  validUntil: string;
  estimatedInstallationDays: number;
  terms?: string;
}

export interface InstallationHistory {
  id: number;
  status: InstallationStatus;
  actorAccountId: number;
  note?: string;
  createdAt: string;
  contactUnlocked: boolean;
  companyEmail?: string;
  companyPhone?: string;
  hostEmail?: string;
  hostPhone?: string;
}

export interface InstallationRequest {
  id: number;
  hostUserId: number;
  propertyId: number;
  propertyTitle: string;
  propertyAddress: string;
  propertyCity?: string;
  companyId: number;
  companyName: string;
  productId: number;
  productName: string;
  connectorType: string;
  powerKw: number;
  quantity: number;
  businessModel: BusinessModel;
  budget?: number;
  targetInstallationDate?: string;
  hostMessage?: string;
  companyNote?: string;
  scheduledSurveyAt?: string;
  scheduledInstallationAt?: string;
  stationId?: number;
  status: InstallationStatus;
  proposal?: Proposal;
  history: InstallationHistory[];
  createdAt: string;
  updatedAt: string;
}

export interface PropertyOpportunity {
  id: number;
  title: string;
  address: string;
  city?: string;
  state?: string;
  pincode?: string;
  latitude: number;
  longitude: number;
  propertyType: string;
  parkingBays: number;
  powerPhase: string;
  availableLoadKw: number;
  operatingHours?: string;
  ownershipType: string;
  preferredConnectorType?: string;
  preferredPowerKw: number;
  photoUrls?: string;
  siteVideoUrl?: string;
  matchedBy: string;
  distanceKm?: number;
  hostDisplayName: string;
  hostBio?: string;
  hostMemberSince?: string;
  hostRating: number;
  hostReviewCount?: number;
  hostTrustScore: number;
  verifiedProperties: number;
  successfulPartnerships: number;
  disputes: number;
  propertyScore: number;
  commercialScore: number;
  verificationRisk: 'LOW' | 'MEDIUM' | 'HIGH';
  verificationMethod: 'DOCUMENT_AND_VIDEO_REVIEW' | 'LIVE_VIDEO_SURVEY' | 'PHYSICAL_SITE_INSPECTION';
  identityVerified: boolean;
  ownershipVerified: boolean;
  electricityVerified: boolean;
  videoVerified: boolean;
  physicalInspectionRecommended: boolean;
  recentHostReviews?: Array<{
    rating: number;
    stationId: number;
    stationName: string;
    stationCity?: string;
    reviewerName: string;
    comment: string;
    hostReply?: string;
    createdAt: string;
  }>;
}

export interface MarketplaceStation {
  id: number;
  name: string;
  address: string;
  city?: string;
  latitude: number;
  longitude: number;
  status: string;
  totalSlots: number;
  availableSlots: number;
}

export interface PropertyInterest {
  id: number;
  companyId: number;
  companyName: string;
  propertyId: number;
  propertyTitle: string;
  propertyCity?: string;
  message?: string;
  status: 'SAVED' | 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'WITHDRAWN';
  createdAt: string;
  contactUnlocked: boolean;
  companyEmail?: string;
  companyPhone?: string;
  hostEmail?: string;
  hostPhone?: string;
}

const auth = (token: string) => ({ headers: { Authorization: `Bearer ${token}` } });

export const getHostProperties = (token: string) => apiRequest<HostProperty[]>('/host/land-listings', { method: 'GET', ...auth(token) });
export const saveHostProperty = (token: string, payload: Record<string, unknown>, id?: number) => apiRequest<HostProperty>(id ? `/host/land-listings/${id}` : '/host/land-listings', { method: id ? 'PUT' : 'POST', ...auth(token), body: JSON.stringify(payload) });
export const getMarketplaceCompanies = (token: string, propertyId: number) => apiRequest<MarketplaceCompany[]>(`/host/marketplace/companies?propertyId=${propertyId}`, { method: 'GET', ...auth(token) });
export const getHostInstallationRequests = (token: string) => apiRequest<InstallationRequest[]>('/host/marketplace/installation-requests', { method: 'GET', ...auth(token) });
export const createInstallationRequest = (token: string, payload: Record<string, unknown>) => apiRequest<InstallationRequest>('/host/marketplace/installation-requests', { method: 'POST', ...auth(token), body: JSON.stringify(payload) });
export const acceptInstallationProposal = (token: string, id: number) => apiRequest<InstallationRequest>(`/host/marketplace/installation-requests/${id}/accept-proposal`, { method: 'POST', ...auth(token) });
export const cancelInstallationRequest = (token: string, id: number) => apiRequest<InstallationRequest>(`/host/marketplace/installation-requests/${id}/cancel`, { method: 'POST', ...auth(token) });
export const getHostCompanyInterests = (token: string) => apiRequest<PropertyInterest[]>('/host/marketplace/company-interests', { method: 'GET', ...auth(token) });
export const respondToCompanyInterest = (token: string, id: number, response: 'accept' | 'decline') => apiRequest<PropertyInterest>(`/host/marketplace/company-interests/${id}/${response}`, { method: 'POST', ...auth(token) });

export const getCompanyProducts = (token: string) => apiRequest<ChargerProduct[]>('/company/marketplace/products', { method: 'GET', ...auth(token) });
export const saveCompanyProduct = (token: string, payload: Record<string, unknown>, id?: number) => apiRequest<ChargerProduct>(id ? `/company/marketplace/products/${id}` : '/company/marketplace/products', { method: id ? 'PUT' : 'POST', ...auth(token), body: JSON.stringify(payload) });
export const archiveCompanyProduct = (token: string, id: number) => apiRequest<void>(`/company/marketplace/products/${id}`, { method: 'DELETE', ...auth(token) });
export const getCompanyOpportunities = (token: string) => apiRequest<PropertyOpportunity[]>('/company/marketplace/opportunities', { method: 'GET', ...auth(token) });
export const getMarketplaceStations = (token: string) => apiRequest<MarketplaceStation[]>('/stations', { method: 'GET', ...auth(token) });
export const expressPropertyInterest = (token: string, propertyId: number, message: string) => apiRequest<PropertyInterest>(`/company/marketplace/opportunities/${propertyId}/interest`, { method: 'POST', ...auth(token), body: JSON.stringify({ message }) });
export const savePropertyOpportunity = (token: string, propertyId: number) => apiRequest<PropertyInterest>(`/company/marketplace/opportunities/${propertyId}/save`, { method: 'POST', ...auth(token) });
export const getCompanyInterests = (token: string) => apiRequest<PropertyInterest[]>('/company/marketplace/interests', { method: 'GET', ...auth(token) });
export const getCompanyInstallationRequests = (token: string) => apiRequest<InstallationRequest[]>('/company/marketplace/installation-requests', { method: 'GET', ...auth(token) });
export const sendInstallationProposal = (token: string, id: number, payload: Record<string, unknown>) => apiRequest<InstallationRequest>(`/company/marketplace/installation-requests/${id}/proposal`, { method: 'POST', ...auth(token), body: JSON.stringify(payload) });
export const updateInstallationStatus = (token: string, id: number, payload: Record<string, unknown>) => apiRequest<InstallationRequest>(`/company/marketplace/installation-requests/${id}/status`, { method: 'PATCH', ...auth(token), body: JSON.stringify(payload) });
