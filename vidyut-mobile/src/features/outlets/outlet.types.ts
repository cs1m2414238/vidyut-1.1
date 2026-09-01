export interface OutletPricingTier {
  id: number;
  name: string;
  ratePerKwh: number;
  eligibility: 'EMAIL_DOMAIN' | 'VERIFIED_ID' | 'VISITOR';
  eligibilityNote: string;
}

export interface OutletTier {
  outletId: number;
  institutionName: string;
  tierName: string;
  ratePerKwh: number;
  reason: string;
  verificationStatus: 'NOT_REQUIRED' | 'NOT_SUBMITTED' | 'PENDING' | 'APPROVED' | 'REJECTED';
  idUploadRequired: boolean;
  pricing: OutletPricingTier[];
}

export interface OutletStats {
  outletId: number;
  institutionName: string;
  sessions: number;
  totalSpend: number;
  savedVsVisitor: number;
}
