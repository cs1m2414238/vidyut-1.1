import { useCallback, useEffect, useMemo, useState } from 'react';
import type { LucideIcon } from 'lucide-react';
import {
  ArrowRight,
  BookmarkCheck,
  Building2,
  CheckCircle2,
  CircleAlert,
  ClipboardCheck,
  Clock3,
  Handshake,
  Mail,
  MapPin,
  PackagePlus,
  PencilLine,
  Phone,
  Plus,
  RefreshCw,
  Route,
  Search,
  ShieldCheck,
  Star,
  Trash2,
  X,
  Video,
  Zap,
} from 'lucide-react';
import {
  archiveCompanyProduct,
  expressPropertyInterest,
  getCompanyInstallationRequests,
  getCompanyInterests,
  getCompanyOpportunities,
  getCompanyProducts,
  getMarketplaceStations,
  saveCompanyProduct,
  savePropertyOpportunity,
  sendInstallationProposal,
  updateInstallationStatus,
} from '../services/marketplace';
import type {
  ChargerProduct,
  InstallationRequest,
  InstallationStatus,
  MarketplaceStation,
  PropertyInterest,
  PropertyOpportunity,
} from '../services/marketplace';
import { ChargerDensityMap } from './MarketplaceMap';
import './Marketplace.css';

export type CompanyMarketplaceTab = 'catalog' | 'host_opportunities' | 'installation_pipeline';

type ModalState =
  | { kind: 'product'; product?: ChargerProduct }
  | { kind: 'archive'; product: ChargerProduct }
  | { kind: 'interest'; property: PropertyOpportunity }
  | { kind: 'review'; property: PropertyOpportunity }
  | { kind: 'proposal'; request: InstallationRequest }
  | { kind: 'status'; request: InstallationRequest; status: InstallationStatus }
  | null;

const blankProduct = {
  modelName: '',
  manufacturer: '',
  currentType: 'DC',
  connectorType: 'CCS2',
  powerKw: 60,
  equipmentPrice: 450000,
  installationPrice: 90000,
  warrantyMonths: 36,
  amcAvailable: true,
  certifications: 'BIS',
  description: '',
  imageUrl: '',
  complianceDocumentUrl: '',
  businessModels: 'PURCHASE,REVENUE_SHARE',
  active: true,
};

const supportedBusinessModels = ['PURCHASE', 'LEASE', 'REVENUE_SHARE', 'COMPANY_OWNED'];
const money = (value?: number) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(value ?? 0);
const readable = (value?: string) => value ? value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase()) : 'Not specified';
const shortDate = (value?: string) => value ? new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(value)) : 'Not scheduled';
const mediaUrls = (value?: string) => {
  if (!value) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    if (Array.isArray(parsed)) return parsed.filter((item): item is string => typeof item === 'string' && /^https?:\/\//i.test(item));
  } catch { /* Hosts may save a comma/newline separated list instead of JSON. */ }
  return value.split(/[\n,]+/).map(item => item.trim().replace(/^[\s'"]+|[\s'"]+$/g, '').replace(/^\[|\]$/g, '')).filter(item => /^https?:\/\//i.test(item));
};

const nextAction: Partial<Record<InstallationStatus, { status: InstallationStatus; label: string }>> = {
  REQUESTED: { status: 'UNDER_REVIEW', label: 'Start review' },
  UNDER_REVIEW: { status: 'SITE_SURVEY_REQUESTED', label: 'Request survey' },
  SITE_SURVEY_REQUESTED: { status: 'SITE_SURVEY_SCHEDULED', label: 'Schedule survey' },
  SITE_SURVEY_SCHEDULED: { status: 'SURVEY_COMPLETED', label: 'Complete survey' },
  ACCEPTED: { status: 'INSTALLATION_SCHEDULED', label: 'Schedule installation' },
  INSTALLATION_SCHEDULED: { status: 'INSTALLING', label: 'Start installation' },
  INSTALLING: { status: 'INSTALLED', label: 'Mark installed' },
  INSTALLED: { status: 'COMMISSIONED', label: 'Commission charger' },
  COMMISSIONED: { status: 'LIVE', label: 'Set live' },
};

interface CompanyMarketplaceViewProps {
  tab: CompanyMarketplaceTab;
  token: string;
  savedOnly?: boolean;
  marketplaceEnabled?: boolean;
  verificationStatus?: string;
  onOpenVerification?: () => void;
}

export function CompanyMarketplaceView({
  tab,
  token,
  savedOnly = false,
  marketplaceEnabled = true,
  verificationStatus,
  onOpenVerification,
}: CompanyMarketplaceViewProps) {
  const [products, setProducts] = useState<ChargerProduct[]>([]);
  const [opportunities, setOpportunities] = useState<PropertyOpportunity[]>([]);
  const [interests, setInterests] = useState<PropertyInterest[]>([]);
  const [requests, setRequests] = useState<InstallationRequest[]>([]);
  const [stations, setStations] = useState<MarketplaceStation[]>([]);
  const [modal, setModal] = useState<ModalState>(null);
  const [form, setForm] = useState<Record<string, string | number | boolean>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const loadAll = useCallback(async () => {
    if (!marketplaceEnabled) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError('');
    const results = await Promise.allSettled([
      getCompanyProducts(token),
      getCompanyOpportunities(token),
      getCompanyInterests(token),
      getCompanyInstallationRequests(token),
      getMarketplaceStations(token),
    ]);
    if (results[0].status === 'fulfilled') setProducts(results[0].value);
    if (results[1].status === 'fulfilled') setOpportunities(results[1].value);
    if (results[2].status === 'fulfilled') setInterests(results[2].value);
    if (results[3].status === 'fulfilled') setRequests(results[3].value);
    if (results[4].status === 'fulfilled') setStations(results[4].value);
    const relevantIndexes = tab === 'catalog' ? [0] : tab === 'host_opportunities' ? [1, 2, 4] : [3];
    const failed = relevantIndexes.map(index => results[index]).find(result => result.status === 'rejected');
    if (failed?.status === 'rejected') setError(failed.reason instanceof Error ? failed.reason.message : 'Unable to load marketplace data.');
    setLoading(false);
  }, [marketplaceEnabled, tab, token]);

  useEffect(() => { void loadAll(); }, [loadAll]);

  const openProduct = (product?: ChargerProduct) => {
    setForm(product ? { ...blankProduct, ...product, businessModels: product.businessModels.join(',') } : blankProduct);
    setModal({ kind: 'product', product });
    setError('');
  };

  const openProposal = (request: InstallationRequest) => {
    const product = products.find(item => item.id === request.productId);
    const validUntil = new Date(Date.now() + 14 * 86400000).toISOString().slice(0, 10);
    setForm({
      equipmentTotal: (product?.equipmentPrice ?? 0) * request.quantity,
      installationTotal: (product?.installationPrice ?? 0) * request.quantity,
      monthlyLease: '',
      hostRevenueSharePercent: request.businessModel === 'REVENUE_SHARE' ? 70 : '',
      companyRevenueSharePercent: request.businessModel === 'REVENUE_SHARE' ? 30 : '',
      validUntil,
      estimatedInstallationDays: 14,
      terms: 'Includes standard installation, commissioning, warranty handover and operator training.',
    });
    setModal({ kind: 'proposal', request });
    setError('');
  };

  const submitModal = async () => {
    if (!modal) return;
    if (modal.kind === 'review') {
      setForm({ message: `We reviewed ${modal.property.title}. Please arrange the recommended ${readable(modal.property.verificationMethod).toLowerCase()} so we can confirm charger and commercial terms.` });
      setModal({ kind: 'interest', property: modal.property });
      setError('');
      return;
    }
    setSaving(true);
    setError('');
    try {
      if (modal.kind === 'archive') {
        await archiveCompanyProduct(token, modal.product.id);
        setNotice(`${modal.product.modelName} was archived. Existing requests remain available.`);
      } else if (modal.kind === 'product') {
        const businessModels = String(form.businessModels).split(',').map(item => item.trim().toUpperCase()).filter(Boolean);
        if (!businessModels.length || businessModels.some(model => !supportedBusinessModels.includes(model))) {
          throw new Error(`Commercial models must use: ${supportedBusinessModels.join(', ')}.`);
        }
        if (!String(form.complianceDocumentUrl ?? '').trim()) {
          throw new Error('A compliance or test document URL is required for Admin approval.');
        }
        await saveCompanyProduct(token, {
          ...form,
          modelName: String(form.modelName).trim(),
          manufacturer: String(form.manufacturer).trim(),
          powerKw: Number(form.powerKw),
          equipmentPrice: Number(form.equipmentPrice),
          installationPrice: Number(form.installationPrice),
          warrantyMonths: Number(form.warrantyMonths),
          businessModels,
          complianceDocumentUrl: String(form.complianceDocumentUrl).trim(),
        }, modal.product?.id);
        setNotice(modal.product ? 'Product updated and returned for compliance review.' : 'Product submitted for Admin compliance review.');
      } else if (modal.kind === 'interest') {
        const message = String(form.message ?? '').trim();
        if (!message) throw new Error('Add a short introduction for the Host.');
        await expressPropertyInterest(token, modal.property.id, message);
        setNotice(`Interest sent for ${modal.property.title}. Contact unlocks after the Host accepts.`);
      } else if (modal.kind === 'proposal') {
        const hostShare = numericOrNull(form.hostRevenueSharePercent);
        const companyShare = numericOrNull(form.companyRevenueSharePercent);
        if (modal.request.businessModel === 'REVENUE_SHARE' && (hostShare ?? 0) + (companyShare ?? 0) !== 100) {
          throw new Error('Host and company revenue shares must total 100%.');
        }
        await sendInstallationProposal(token, modal.request.id, {
          equipmentTotal: Number(form.equipmentTotal),
          installationTotal: Number(form.installationTotal),
          monthlyLease: numericOrNull(form.monthlyLease),
          hostRevenueSharePercent: hostShare,
          companyRevenueSharePercent: companyShare,
          validUntil: form.validUntil,
          estimatedInstallationDays: Number(form.estimatedInstallationDays),
          terms: form.terms,
        });
        setNotice('Commercial proposal sent to the Host with a complete audit trail.');
      } else {
        await updateInstallationStatus(token, modal.request.id, {
          status: modal.status,
          note: form.note || null,
          scheduledDate: form.scheduledDate || null,
        });
        setNotice(`Request moved to ${readable(modal.status)}.`);
      }
      setModal(null);
      await loadAll();
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : 'Unable to save marketplace changes.');
    } finally {
      setSaving(false);
    }
  };

  const startStatus = (request: InstallationRequest, status: InstallationStatus) => {
    if (status === 'SURVEY_COMPLETED') {
      setForm({ note: 'Site survey completed. Grid, parking and access requirements were recorded.' });
    } else {
      setForm({ note: '', scheduledDate: '' });
    }
    setModal({ kind: 'status', request, status });
    setError('');
  };

  const saveProperty = async (property: PropertyOpportunity) => {
    setSaving(true);
    setError('');
    try {
      await savePropertyOpportunity(token, property.id);
      setNotice(`${property.title} was saved privately. The Host was not notified.`);
      setModal(null);
      await loadAll();
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Unable to save this property.');
    } finally {
      setSaving(false);
    }
  };

  const title = tab === 'catalog' ? 'Charger product catalogue' : tab === 'host_opportunities' ? (savedOnly ? 'Saved Host properties' : 'Nationwide Host opportunities') : 'Installation pipeline';
  const description = tab === 'catalog'
    ? 'Publish approved AC and DC equipment for purchase, lease or revenue share.'
    : tab === 'host_opportunities'
      ? savedOnly ? 'Revisit shortlisted properties privately before requesting a survey.' : 'Discover verified installation-ready land anywhere in India.'
      : 'Move every Host request from survey through commissioning and go-live.';

  return <section className="marketplace-page company-marketplace-page">
    <header className="marketplace-head"><div><span>HOST–COMPANY MARKETPLACE</span><h1>{title}</h1><p>{description}</p></div><button className="marketplace-refresh" onClick={() => void loadAll()} disabled={loading || !marketplaceEnabled}><RefreshCw className={loading ? 'spinning' : ''} size={16} /> Refresh</button></header>
    {!marketplaceEnabled && <MarketplaceAccessGate status={verificationStatus} onOpenVerification={onOpenVerification} />}
    {marketplaceEnabled && <>
      {error && <div className="marketplace-message error"><CircleAlert size={17} />{error}</div>}
      {notice && <div className="marketplace-message success"><CheckCircle2 size={17} />{notice}</div>}
      {tab === 'catalog' && <ProductCatalogue products={products} onAdd={() => openProduct()} onEdit={openProduct} onRemove={product => setModal({ kind: 'archive', product })} />}
      {tab === 'host_opportunities' && <HostOpportunities opportunities={opportunities} stations={stations} interests={interests} loading={loading} savedOnly={savedOnly} onReview={property => { setForm({}); setModal({ kind: 'review', property }); }} onInterest={property => { setForm({ message: `We would like to survey ${property.title} and discuss a suitable charger and commercial model.` }); setModal({ kind: 'interest', property }); }} />}
      {tab === 'installation_pipeline' && <CompanyPipeline requests={requests} opportunities={opportunities} onReview={property => { setForm({}); setModal({ kind: 'review', property }); }} onProposal={openProposal} onStatus={startStatus} onDecline={request => startStatus(request, 'DECLINED')} />}
      {modal && <CompanyMarketplaceModal modal={modal} form={form} setForm={setForm} saving={saving} error={error} onClose={() => setModal(null)} onSave={property => void saveProperty(property)} onSubmit={() => void submitModal()} />}
    </>}
  </section>;
}

function MarketplaceAccessGate({ status, onOpenVerification }: { status?: string; onOpenVerification?: () => void }) {
  return <article className="marketplace-access-gate"><span><ShieldCheck size={30} /></span><div><small>TRUST GATE</small><h2>Complete company verification</h2><p>Business identity, authorized representative, bank and charger-compliance evidence must pass Vidyut review before products, Host contact or proposals are enabled.</p><div><i>Email verified</i><i>Four trust layers</i><i>Admin approval</i></div></div><aside><strong>{readable(status || 'NOT_STARTED')}</strong>{onOpenVerification && <button className="marketplace-primary" onClick={onOpenVerification}>Open verification <ArrowRight size={14} /></button>}</aside></article>;
}

function ProductCatalogue({ products, onAdd, onEdit, onRemove }: { products: ChargerProduct[]; onAdd: () => void; onEdit: (product: ChargerProduct) => void; onRemove: (product: ChargerProduct) => void }) {
  return <div className="marketplace-stack">
    <div className="marketplace-section-head"><div><h2>Products available to Hosts</h2><p>Equipment, installation and compliance approval are tracked independently.</p></div><button className="marketplace-primary" onClick={onAdd}><Plus size={16} /> Add charger product</button></div>
    <div className="catalog-grid">{products.map(product => <article className="catalog-card" key={product.id}>
      <header><span className={product.currentType.toLowerCase()}><Zap size={21} /></span><div><i className={`product-approval ${product.approvalStatus.toLowerCase()}`}>{readable(product.approvalStatus)}</i><h3>{product.modelName}</h3><p>{product.manufacturer}</p></div></header>
      <div className="catalog-power"><strong>{product.powerKw}<small> kW</small></strong><span>{product.currentType}<small>{product.connectorType}</small></span></div>
      <p>{product.description || 'Commercial EV charger with company-backed installation and commissioning.'}</p>
      <div className="catalog-price"><span><small>Equipment</small><strong>{money(product.equipmentPrice)}</strong></span><span><small>Installation</small><strong>{money(product.installationPrice)}</strong></span></div>
      <div className="marketplace-tags">{product.businessModels.map(model => <i key={model}>{readable(model)}</i>)}</div>
      <div className="product-compliance"><ShieldCheck size={15} /><span><strong>{product.complianceDocumentUrl ? 'Compliance evidence attached' : 'Compliance evidence missing'}</strong><small>{product.adminReviewNote || (product.approvalStatus === 'APPROVED' ? 'Approved for Host discovery' : 'Awaiting Vidyut Admin review')}</small></span></div>
      <footer><span>{product.active && product.approvalStatus === 'APPROVED' ? 'Published to Hosts' : `${product.warrantyMonths} month warranty`}</span><div><button onClick={() => onEdit(product)}><PencilLine size={14} /> Edit</button><button className="danger-icon" onClick={() => onRemove(product)} aria-label={`Archive ${product.modelName}`}><Trash2 size={14} /></button></div></footer>
    </article>)}{!products.length && <MarketplaceEmpty icon={PackagePlus} title="Publish your first charger" text="Add power, connector, pricing and compliance evidence for Vidyut approval." action="Add product" onAction={onAdd} />}</div>
  </div>;
}

function HostOpportunities({ opportunities, stations, interests, loading, savedOnly, onReview, onInterest }: { opportunities: PropertyOpportunity[]; stations: MarketplaceStation[]; interests: PropertyInterest[]; loading: boolean; savedOnly: boolean; onReview: (property: PropertyOpportunity) => void; onInterest: (property: PropertyOpportunity) => void }) {
  const [query, setQuery] = useState('');
  const [minimumLoad, setMinimumLoad] = useState(0);
  const savedPropertyIds = useMemo(() => new Set(interests.filter(item => item.status === 'SAVED').map(item => item.propertyId)), [interests]);
  const filtered = useMemo(() => opportunities.filter(property => {
    const haystack = `${property.title} ${property.address} ${property.city ?? ''} ${property.state ?? ''}`.toLowerCase();
    return (!savedOnly || savedPropertyIds.has(property.id)) && haystack.includes(query.trim().toLowerCase()) && property.availableLoadKw >= minimumLoad;
  }), [minimumLoad, opportunities, query, savedOnly, savedPropertyIds]);
  const unlocked = interests.filter(interest => interest.contactUnlocked);
  if (loading) return <div className="marketplace-loading"><RefreshCw className="spinning" /> Matching Host properties…</div>;
  return <div className="marketplace-stack">
    <div className="marketplace-section-head"><div><h2>{savedOnly ? 'Your private shortlist' : 'Installation sites across India'}</h2><p>{savedOnly ? 'Saving does not reveal company interest or unlock Host contact.' : 'Search verified Host properties without artificial service-area restrictions.'}</p></div>{savedOnly ? <BookmarkCheck size={21} /> : <Handshake size={21} />}</div>
    {unlocked.length > 0 && <section className="company-contact-leads"><header><div><strong>Accepted Host conversations</strong><span>Contact details unlock only after Host approval.</span></div><CheckCircle2 size={19} /></header><div>{unlocked.map(interest => <article key={interest.id}><span><Building2 size={18} /></span><div><strong>{interest.propertyTitle}</strong><small>{interest.propertyCity || 'India'} · {interest.hostEmail || 'Email unavailable'}</small></div><aside>{interest.hostEmail && <a href={`mailto:${interest.hostEmail}`}><Mail size={14} /> Email</a>}{interest.hostPhone && <a href={`tel:${interest.hostPhone}`}><Phone size={14} /> Call</a>}</aside></article>)}</div></section>}
    <div className="opportunity-toolbar"><label><Search size={16} /><input value={query} onChange={event => setQuery(event.target.value)} placeholder="Search city, state, route or property" /></label><select value={minimumLoad} onChange={event => setMinimumLoad(Number(event.target.value))}><option value={0}>Any grid load</option><option value={30}>30+ kW</option><option value={60}>60+ kW</option><option value={120}>120+ kW</option></select><span>{filtered.length} verified sites</span></div>
    <ChargerDensityMap opportunities={filtered} stations={stations} onContact={onReview} />
    <div className="opportunity-grid">{filtered.map(property => { const interest = interests.find(item => item.propertyId === property.id); return <article className="opportunity-card" key={property.id}><header><span><Building2 size={21} /></span><i>{property.matchedBy}{property.distanceKm != null ? ` · ${property.distanceKm} km` : ''}</i></header><h3>{property.title}</h3><p><MapPin size={13} /> {property.address}, {property.city}</p><div className="opportunity-host"><span>{property.hostDisplayName.slice(0, 1).toUpperCase()}</span><div><strong>{property.hostDisplayName}</strong><small>Verified Host · {property.hostReviewCount ?? 0} reviews</small></div></div><div className="opportunity-trust"><span><ShieldCheck size={14} /> Host trust {property.hostTrustScore}/100</span><span><Star size={14} /> {property.hostRating.toFixed(1)}</span><i className={`risk-${property.verificationRisk.toLowerCase()}`}>{property.verificationRisk} RISK</i></div><div className="opportunity-specs"><span><strong>{property.parkingBays}</strong><small>parking bays</small></span><span><strong>{property.availableLoadKw} kW</strong><small>available load</small></span><span><strong>{readable(property.powerPhase)}</strong><small>power supply</small></span></div><section><Zap size={17} /><span><small>Host preference</small><strong>{property.preferredConnectorType || 'Flexible'} · {property.preferredPowerKw || 'Any'} kW</strong></span></section><footer><button onClick={() => onReview(property)}><ClipboardCheck size={14} /> View property &amp; Host</button>{interest && <i className={`interest-${interest.status.toLowerCase()}`}>{interest.contactUnlocked ? 'CONTACT UNLOCKED' : readable(interest.status)}</i>}{(!interest || interest.status === 'SAVED') && <button className="marketplace-primary" onClick={() => onInterest(property)}>Request site visit <ArrowRight size={14} /></button>}</footer></article>; })}{!filtered.length && <MarketplaceEmpty icon={savedOnly ? BookmarkCheck : Building2} title={savedOnly ? 'No saved properties yet' : 'No matching Host sites'} text={savedOnly ? 'Save a property from the marketplace to build a private shortlist.' : 'Try a broader search or lower the required grid load.'} />}</div>
  </div>;
}

function CompanyPipeline({ requests, opportunities, onReview, onProposal, onStatus, onDecline }: { requests: InstallationRequest[]; opportunities: PropertyOpportunity[]; onReview: (property: PropertyOpportunity) => void; onProposal: (request: InstallationRequest) => void; onStatus: (request: InstallationRequest, status: InstallationStatus) => void; onDecline: (request: InstallationRequest) => void }) {
  return <section className="marketplace-panel"><div className="marketplace-section-head"><div><h2>Host installation requests</h2><p>Open the property and Host profile before starting review or requesting a survey.</p></div><Route size={21} /></div><div className="pipeline-list company-pipeline-list">{requests.map(request => { const next = nextAction[request.status]; const property = opportunities.find(item => item.id === request.propertyId); return <article key={request.id}><header><div><span>{readable(request.status)}</span><h3>{request.propertyTitle}</h3><p>{request.productName} × {request.quantity} · {request.connectorType} · {request.powerKw} kW</p></div><strong>{readable(request.businessModel)}</strong></header><div className="request-detail-grid"><span><small>Location</small><strong>{request.propertyCity || request.propertyAddress}</strong></span><span><small>Host budget</small><strong>{request.budget ? money(request.budget) : 'Open'}</strong></span><span><small>Target date</small><strong>{shortDate(request.targetInstallationDate)}</strong></span><span><small>Last update</small><strong>{shortDate(request.updatedAt)}</strong></span></div>{request.hostMessage && <blockquote>“{request.hostMessage}”</blockquote>}<div className="pipeline-progress">{request.history.map((item, index) => <span className="done" key={item.id}><i>{index + 1}</i><small>{readable(item.status)}</small></span>)}</div>{request.proposal && <div className="proposal-summary"><div><small>Total commercial value</small><strong>{money(request.proposal.equipmentTotal + request.proposal.installationTotal)}</strong></div><div><small>Valid until</small><strong>{shortDate(request.proposal.validUntil)}</strong></div><p>{request.proposal.terms}</p></div>}<footer><span><Clock3 size={14} /> Request #{request.id}</span><div>{property && <button onClick={() => onReview(property)}><ClipboardCheck size={14} /> View property &amp; Host</button>}{['REQUESTED', 'UNDER_REVIEW', 'SITE_SURVEY_REQUESTED'].includes(request.status) && <button onClick={() => onDecline(request)}>Decline</button>}{request.status === 'SURVEY_COMPLETED' && <button className="marketplace-primary" onClick={() => onProposal(request)}>Send proposal</button>}{next && <button className="marketplace-primary" onClick={() => onStatus(request, next.status)}>{next.label} <ArrowRight size={14} /></button>}{request.status === 'PROPOSAL_SENT' && <i>Awaiting Host approval</i>}{request.status === 'LIVE' && <i className="live"><CheckCircle2 size={14} /> Live station created</i>}</div></footer></article>; })}{!requests.length && <MarketplaceEmpty icon={Route} title="No installation requests yet" text="Verified Hosts can request any Admin-approved charger from your catalogue." />}</div></section>;
}

function CompanyMarketplaceModal({ modal, form, setForm, saving, error, onClose, onSave, onSubmit }: { modal: Exclude<ModalState, null>; form: Record<string, string | number | boolean>; setForm: React.Dispatch<React.SetStateAction<Record<string, string | number | boolean>>>; saving: boolean; error: string; onClose: () => void; onSave: (property: PropertyOpportunity) => void; onSubmit: () => void }) {
  if (modal.kind === 'archive') return <div className="marketplace-modal-backdrop" onMouseDown={onClose}><section className="marketplace-modal marketplace-confirm" role="alertdialog" aria-modal="true" onMouseDown={event => event.stopPropagation()}><span><Trash2 size={24} /></span><h2>Archive {modal.product.modelName}?</h2><p>Hosts will no longer see this charger, while existing proposals and installations remain in the audit trail.</p><footer><button onClick={onClose}>Keep product</button><button className="marketplace-danger" onClick={onSubmit} disabled={saving}>{saving ? 'Archiving…' : 'Archive product'}</button></footer></section></div>;
  if (modal.kind === 'review') {
    const property = modal.property;
    const checks = [
      ['Host identity', property.identityVerified],
      ['Ownership evidence', property.ownershipVerified],
      ['Electricity / sanctioned load', property.electricityVerified],
      ['Site walkthrough video', property.videoVerified],
    ] as const;
    const photos = mediaUrls(property.photoUrls);
    const recentReviews = property.recentHostReviews ?? [];
    return <div className="marketplace-modal-backdrop" onMouseDown={onClose}>
      <section className="marketplace-modal property-review-modal" role="dialog" aria-modal="true" onMouseDown={event => event.stopPropagation()}>
        <header><div><span>PRE-SURVEY PROPERTY &amp; HOST REVIEW</span><h2>{property.title}</h2><p>{property.address}, {property.city}{property.state ? `, ${property.state}` : ''}</p></div><button onClick={onClose} aria-label="Close property review"><X size={18} /></button></header>
        <div className="property-score-grid"><article><small>PROPERTY SCORE</small><strong>{property.propertyScore}<i>/100</i></strong><span>Site readiness</span></article><article><small>HOST TRUST</small><strong>{property.hostTrustScore}<i>/100</i></strong><span><Star size={13} /> {property.hostRating.toFixed(1)} from {property.hostReviewCount ?? 0} reviews</span></article><article><small>COMMERCIAL FIT</small><strong>{property.commercialScore}<i>/100</i></strong><span>{property.availableLoadKw} kW · {property.parkingBays} bays</span></article></div>
        {photos.length > 0 && <section className="property-photo-gallery" aria-label="Property photos">{photos.slice(0, 5).map((url, index) => <a href={url} target="_blank" rel="noreferrer" key={url}><img src={url} alt={`${property.title} property view ${index + 1}`} /></a>)}</section>}
        <div className="property-profile-grid">
          <section className="property-fact-card"><h3>Property details</h3><div><span><small>Property type</small><strong>{readable(property.propertyType)}</strong></span><span><small>Ownership</small><strong>{readable(property.ownershipType)}</strong></span><span><small>Parking</small><strong>{property.parkingBays} bays</strong></span><span><small>Electrical capacity</small><strong>{property.availableLoadKw} kW · {readable(property.powerPhase)}</strong></span><span><small>Operating hours</small><strong>{property.operatingHours || 'Confirm with Host'}</strong></span><span><small>Preferred charger</small><strong>{property.preferredConnectorType || 'Flexible'} · {property.preferredPowerKw || 'Any'} kW</strong></span></div>{property.siteVideoUrl && <a className="property-video-link" href={property.siteVideoUrl} target="_blank" rel="noreferrer"><Video size={16} /> Watch verified site walkthrough</a>}</section>
          <section className="host-public-profile"><div className="host-public-head"><span>{property.hostDisplayName.slice(0, 1).toUpperCase()}</span><div><small>VERIFIED MARKETPLACE HOST</small><h3>{property.hostDisplayName}</h3><p>{property.hostMemberSince ? `Member since ${shortDate(property.hostMemberSince)}` : 'Identity verified by Vidyut'}</p></div><ShieldCheck size={21} /></div><p>{property.hostBio || 'This Host has completed Vidyut identity and property verification. Contact details unlock only after the Host accepts company interest.'}</p><div className="host-public-stats"><span><strong>{property.verifiedProperties}</strong><small>verified properties</small></span><span><strong>{property.successfulPartnerships}</strong><small>commissioned sites</small></span><span><strong>{property.disputes}</strong><small>reported disputes</small></span></div></section>
        </div>
        <div className="property-review-columns"><section><h3>Site verification</h3>{checks.map(([label, passed]) => <div className={passed ? 'passed' : 'missing'} key={label}>{passed ? <CheckCircle2 size={17} /> : <CircleAlert size={17} />}<span><strong>{label}</strong><small>{passed ? 'Evidence recorded by Vidyut' : 'Required before commitment'}</small></span></div>)}</section><section><h3>Ratings from previous chargers</h3>{recentReviews.length ? recentReviews.map((review, index) => <article className="host-review-summary" key={`${review.createdAt}-${index}`}><div><span><strong>{review.stationName || 'Previous charger'}</strong><small><MapPin size={10} /> {review.stationCity || 'Location unavailable'} · by {review.reviewerName}</small></span><i>{'★'.repeat(Math.max(0, Math.min(5, review.rating)))}</i></div><p>{review.comment}</p>{review.hostReply && <small>Host reply: {review.hostReply}</small>}</article>) : <div><Star size={17} /><span><strong>No public charger ratings yet</strong><small>Use verification evidence and a survey before commitment.</small></span></div>}</section></div>
        <div className={`verification-recommendation risk-${property.verificationRisk.toLowerCase()}`}><Video size={21} /><span><small>{property.verificationRisk} VERIFICATION RISK</small><strong>{readable(property.verificationMethod)}</strong><p>{property.physicalInspectionRecommended ? 'A field verifier should confirm access, electrical infrastructure and the charger bay before a commercial offer.' : 'Documents and recorded video are sufficient for initial review; request a live video if conditions change.'}</p></span></div>
        <footer><button onClick={onClose}>Close</button><button onClick={() => onSave(property)} disabled={saving}><BookmarkCheck size={14} /> {saving ? 'Saving…' : 'Save property'}</button><button className="marketplace-primary" onClick={onSubmit}>Request site visit / contact <ArrowRight size={14} /></button></footer>
      </section>
    </div>;
  }
  const field = (name: string, label: string, type = 'text') => <label>{label}<input type={type} value={String(form[name] ?? '')} onChange={event => setForm(current => ({ ...current, [name]: event.target.value }))} /></label>;
  const select = (name: string, label: string, options: string[]) => <label>{label}<select value={String(form[name] ?? '')} onChange={event => setForm(current => ({ ...current, [name]: event.target.value }))}>{options.map(option => <option key={option} value={option}>{readable(option)}</option>)}</select></label>;
  const check = (name: string, label: string) => <label className="marketplace-check"><input type="checkbox" checked={Boolean(form[name])} onChange={event => setForm(current => ({ ...current, [name]: event.target.checked }))} /> {label}</label>;
  const modalTitle = modal.kind === 'product' ? `${modal.product ? 'Update' : 'Add'} charger product` : modal.kind === 'interest' ? `Contact Host for ${modal.property.title}` : modal.kind === 'proposal' ? `Proposal for ${modal.request.propertyTitle}` : `${readable(modal.status)} request`;
  const eyebrow = modal.kind === 'product' ? 'PRODUCT CATALOGUE' : modal.kind === 'interest' ? 'HOST OPPORTUNITY' : 'INSTALLATION PIPELINE';
  return <div className="marketplace-modal-backdrop" onMouseDown={onClose}><section className="marketplace-modal" role="dialog" aria-modal="true" onMouseDown={event => event.stopPropagation()}><header><div><span>{eyebrow}</span><h2>{modalTitle}</h2><p>Changes are stored in Vidyut and shared with the relevant marketplace participant.</p></div><button onClick={onClose} aria-label="Close marketplace form"><X size={18} /></button></header>{error && <div className="marketplace-message error"><CircleAlert size={16} />{error}</div>}<div className="marketplace-form-grid">
    {modal.kind === 'product' && <>{field('modelName', 'Model name')}{field('manufacturer', 'Manufacturer')}{select('currentType', 'Current type', ['AC', 'DC'])}{select('connectorType', 'Connector type', ['CCS2', 'TYPE2', 'CHADEMO', 'GB_T', 'TYPE1'])}{field('powerKw', 'Rated power (kW)', 'number')}{field('equipmentPrice', 'Equipment price (₹)', 'number')}{field('installationPrice', 'Installation price (₹)', 'number')}{field('warrantyMonths', 'Warranty (months)', 'number')}{field('certifications', 'Certifications')}<label className="wide">Compliance / test document URL<input value={String(form.complianceDocumentUrl ?? '')} onChange={event => setForm(current => ({ ...current, complianceDocumentUrl: event.target.value }))} placeholder="https://…/BIS-or-test-report.pdf" /></label><label className="wide">Commercial models<input value={String(form.businessModels ?? '')} onChange={event => setForm(current => ({ ...current, businessModels: event.target.value }))} placeholder="PURCHASE, LEASE, REVENUE_SHARE" /></label><label className="wide">Description<textarea value={String(form.description ?? '')} onChange={event => setForm(current => ({ ...current, description: event.target.value }))} /></label>{check('amcAvailable', 'Annual maintenance contract available')}{check('active', 'Request publication after approval')}</>}
    {modal.kind === 'interest' && <label className="wide">Message to Host<textarea value={String(form.message ?? '')} onChange={event => setForm(current => ({ ...current, message: event.target.value }))} /></label>}
    {modal.kind === 'proposal' && <>{field('equipmentTotal', 'Equipment total (₹)', 'number')}{field('installationTotal', 'Installation total (₹)', 'number')}{field('monthlyLease', 'Monthly lease (₹)', 'number')}{field('hostRevenueSharePercent', 'Host revenue share (%)', 'number')}{field('companyRevenueSharePercent', 'Company revenue share (%)', 'number')}{field('validUntil', 'Proposal valid until', 'date')}{field('estimatedInstallationDays', 'Estimated installation days', 'number')}<label className="wide">Commercial terms<textarea value={String(form.terms ?? '')} onChange={event => setForm(current => ({ ...current, terms: event.target.value }))} /></label></>}
    {modal.kind === 'status' && <>{(modal.status === 'SITE_SURVEY_SCHEDULED' || modal.status === 'INSTALLATION_SCHEDULED') && field('scheduledDate', modal.status === 'SITE_SURVEY_SCHEDULED' ? 'Survey date' : 'Installation date', 'date')}<label className="wide">Update note<textarea value={String(form.note ?? '')} onChange={event => setForm(current => ({ ...current, note: event.target.value }))} placeholder="Share access instructions, work completed or next steps." /></label></>}
  </div><footer><button onClick={onClose}>Cancel</button><button className="marketplace-primary" onClick={onSubmit} disabled={saving}>{saving ? 'Saving…' : modal.kind === 'interest' ? 'Send interest' : modal.kind === 'proposal' ? 'Send proposal' : 'Save changes'}</button></footer></section></div>;
}

function MarketplaceEmpty({ icon: Icon, title, text, action, onAction }: { icon: LucideIcon; title: string; text: string; action?: string; onAction?: () => void }) {
  return <div className="marketplace-empty"><span><Icon size={25} /></span><h3>{title}</h3><p>{text}</p>{action && onAction && <button className="marketplace-primary" onClick={onAction}>{action}</button>}</div>;
}

function numericOrNull(value: string | number | boolean | undefined): number | null {
  return value === '' || value == null ? null : Number(value);
}
