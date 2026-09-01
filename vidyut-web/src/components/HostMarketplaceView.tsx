import { useCallback, useEffect, useState } from 'react';
import {
  ArrowRight, Building2, CheckCircle2, CircleAlert, Clock3, Handshake,
  MapPin, PencilLine, Plus, RefreshCw, Route, ShieldCheck, X, Zap,
} from 'lucide-react';
import {
  acceptInstallationProposal, cancelInstallationRequest, createInstallationRequest,
  getHostCompanyInterests, getHostInstallationRequests, getHostProperties,
  getMarketplaceCompanies, respondToCompanyInterest, saveHostProperty,
} from '../services/marketplace';
import type { ChargerProduct, HostProperty, InstallationRequest, MarketplaceCompany, PropertyInterest } from '../services/marketplace';
import { PropertyLocationPicker } from './MarketplaceMap';
import './Marketplace.css';

type HostMarketplaceTab = 'properties' | 'marketplace' | 'installations';
type ModalState = { kind: 'property'; property?: HostProperty } | { kind: 'request'; company: MarketplaceCompany; product: ChargerProduct } | null;

const emptyProperty = {
  title: '', address: '', city: 'Lucknow', state: 'Uttar Pradesh', pincode: '',
  latitude: 26.8467, longitude: 80.9462, propertyType: 'COMMERCIAL_PARKING',
  availableParkingBays: 4, powerPhase: 'THREE_PHASE', availableLoadKw: 35,
  operatingHours: '24 × 7', ownershipType: 'OWNED', preferredConnectorType: 'CCS2',
  preferredPowerKw: 60, pricePerKwh: 16, photoUrls: '', ownershipDocumentUrl: '',
  electricityDocumentUrl: '', videoVerificationUrl: '', discoverable: true,
};

const money = (value?: number) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(value ?? 0);
const readable = (value?: string) => value ? value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase()) : 'Not specified';
const shortDate = (value?: string) => value ? new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(value)) : 'Not scheduled';

export function HostMarketplaceView({ tab, token, onNavigate }: { tab: HostMarketplaceTab; token: string; onNavigate: (tab: string) => void }) {
  const [properties, setProperties] = useState<HostProperty[]>([]);
  const [companies, setCompanies] = useState<MarketplaceCompany[]>([]);
  const [requests, setRequests] = useState<InstallationRequest[]>([]);
  const [interests, setInterests] = useState<PropertyInterest[]>([]);
  const [propertyId, setPropertyId] = useState<number | null>(null);
  const [modal, setModal] = useState<ModalState>(null);
  const [form, setForm] = useState<Record<string, string | number | boolean>>({});
  const [loading, setLoading] = useState(true);
  const [companiesLoading, setCompaniesLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const loadBase = useCallback(async () => {
    setLoading(true); setError('');
    const results = await Promise.allSettled([
      getHostProperties(token), getHostInstallationRequests(token), getHostCompanyInterests(token),
    ]);
    const propertiesResult = results[0];
    if (propertiesResult.status === 'fulfilled') {
      setProperties(propertiesResult.value);
      const firstPropertyId = propertiesResult.value[0]?.id ?? null;
      setPropertyId(current => propertiesResult.value.some(property => property.id === current) ? current : firstPropertyId);
    }
    if (results[1].status === 'fulfilled') setRequests(results[1].value);
    if (results[2].status === 'fulfilled') setInterests(results[2].value);
    const failed = results.find(result => result.status === 'rejected');
    if (failed?.status === 'rejected') setError(failed.reason instanceof Error ? failed.reason.message : 'Unable to load marketplace data.');
    setLoading(false);
  }, [token]);

  useEffect(() => { void loadBase(); }, [loadBase]);

  useEffect(() => {
    if (tab !== 'marketplace' || propertyId == null) return;
    setCompaniesLoading(true); setError('');
    void getMarketplaceCompanies(token, propertyId)
      .then(setCompanies)
      .catch(loadError => setError(loadError instanceof Error ? loadError.message : 'Unable to match charger companies.'))
      .finally(() => setCompaniesLoading(false));
  }, [propertyId, tab, token]);

  const pendingInterests = interests.filter(interest => interest.status === 'PENDING');
  const activeRequests = requests.filter(request => !['LIVE', 'DECLINED', 'CANCELLED', 'EXPIRED'].includes(request.status));

  const openProperty = (property?: HostProperty) => {
    setForm(property ? { ...emptyProperty, ...property } : emptyProperty);
    setModal({ kind: 'property', property }); setError(''); setNotice('');
  };

  const openRequest = (company: MarketplaceCompany, product: ChargerProduct) => {
    setForm({ propertyId: propertyId ?? '', companyId: company.id, productId: product.id, quantity: 1, businessModel: product.businessModels[0] ?? 'PURCHASE', budget: product.equipmentPrice + product.installationPrice, targetInstallationDate: '', message: '' });
    setModal({ kind: 'request', company, product }); setError(''); setNotice('');
  };

  const submitModal = async () => {
    if (!modal) return;
    const text = (key: string) => String(form[key] ?? '').trim();
    const number = (key: string) => Number(form[key]);
    if (modal.kind === 'property') {
      if (!text('title') || !text('address') || !text('city') || !text('state') || !/^\d{6}$/.test(text('pincode'))) {
        setError('Property title, address, city, state and a 6-digit PIN code are required.'); return;
      }
      if (!text('ownershipDocumentUrl')) {
        setError('Add an ownership, lease or authorization document URL before submitting the property.'); return;
      }
      if (!/^https?:\/\/\S+$/i.test(text('ownershipDocumentUrl'))) {
        setError('Ownership evidence must be an accessible http:// or https:// document URL.'); return;
      }
      if (!text('electricityDocumentUrl') || !/^https?:\/\/\S+$/i.test(text('electricityDocumentUrl'))) {
        setError('Add an accessible electricity connection or sanctioned-load document URL.'); return;
      }
      if (!text('videoVerificationUrl') || !/^https?:\/\/\S+$/i.test(text('videoVerificationUrl'))) {
        setError('Add an accessible site walkthrough video URL before publishing.'); return;
      }
      if (number('availableParkingBays') < 1 || number('availableLoadKw') < 0 || number('preferredPowerKw') <= 0) {
        setError('Enter at least one parking bay, a non-negative load and a preferred charger power.'); return;
      }
      if (number('latitude') < -90 || number('latitude') > 90 || number('longitude') < -180 || number('longitude') > 180) {
        setError('Enter valid latitude and longitude coordinates.'); return;
      }
    } else if (!number('propertyId') || !number('companyId') || !number('productId') || number('quantity') < 1 || !text('businessModel')) {
      setError('Choose a property and business model with a quantity of at least one.'); return;
    }
    setSaving(true); setError('');
    try {
      if (modal.kind === 'property') {
        await saveHostProperty(token, {
          ...form,
          connectorType: form.preferredConnectorType,
          powerKw: Number(form.preferredPowerKw),
          latitude: Number(form.latitude), longitude: Number(form.longitude),
          availableParkingBays: Number(form.availableParkingBays), availableLoadKw: Number(form.availableLoadKw),
          preferredPowerKw: Number(form.preferredPowerKw), pricePerKwh: Number(form.pricePerKwh),
          ownershipDocumentUrl: form.ownershipDocumentUrl,
          electricityDocumentUrl: form.electricityDocumentUrl,
          videoVerificationUrl: form.videoVerificationUrl,
        }, modal.property?.id);
        setNotice(modal.property ? 'Property updated and returned for review.' : 'Property submitted for Host identity and ownership review.');
      } else {
        await createInstallationRequest(token, {
          ...form, propertyId: Number(form.propertyId), companyId: Number(form.companyId), productId: Number(form.productId),
          quantity: Number(form.quantity), budget: form.budget === '' ? null : Number(form.budget),
          targetInstallationDate: form.targetInstallationDate || null,
        });
        setNotice(`Proposal requested from ${modal.company.companyName}.`);
        onNavigate('installations');
      }
      setModal(null); await loadBase();
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : 'Unable to save marketplace changes.');
    } finally { setSaving(false); }
  };

  const requestAction = async (request: InstallationRequest, action: 'accept' | 'cancel') => {
    setSaving(true); setError('');
    try {
      if (action === 'accept') await acceptInstallationProposal(token, request.id);
      else await cancelInstallationRequest(token, request.id);
      setNotice(action === 'accept' ? 'Proposal accepted. The company can now schedule installation.' : 'Installation request cancelled.');
      await loadBase();
    } catch (actionError) { setError(actionError instanceof Error ? actionError.message : 'Unable to update the request.'); }
    finally { setSaving(false); }
  };

  const interestAction = async (interest: PropertyInterest, response: 'accept' | 'decline') => {
    setSaving(true); setError('');
    try {
      await respondToCompanyInterest(token, interest.id, response);
      setNotice(`Company interest ${response === 'accept' ? 'accepted' : 'declined'}.`); await loadBase();
    } catch (actionError) { setError(actionError instanceof Error ? actionError.message : 'Unable to respond to the company.'); }
    finally { setSaving(false); }
  };

  return (
    <section className="marketplace-page">
      <header className="marketplace-head">
        <div><span>HOST–COMPANY MARKETPLACE</span><h1>{tab === 'properties' ? 'Installation-ready properties' : tab === 'marketplace' ? 'Find charger companies' : 'Installation requests'}</h1><p>Turn suitable land into charging infrastructure without already owning a charger.</p></div>
        <button className="marketplace-refresh" onClick={() => void loadBase()} disabled={loading}><RefreshCw className={loading ? 'spinning' : ''} size={16} /> Refresh</button>
      </header>
      {error && <div className="marketplace-message error"><CircleAlert size={17} />{error}</div>}
      {notice && <div className="marketplace-message success"><CheckCircle2 size={17} />{notice}</div>}

      {tab === 'properties' && <PropertiesPanel properties={properties} onAdd={() => openProperty()} onEdit={openProperty} onFind={(id) => { setPropertyId(id); onNavigate('marketplace'); }} />}
      {tab === 'marketplace' && <CompanyDiscovery properties={properties} selectedId={propertyId} onProperty={setPropertyId} companies={companies} loading={companiesLoading} onAddProperty={() => openProperty()} onRequest={openRequest} />}
      {tab === 'installations' && <HostPipeline requests={requests} interests={interests} saving={saving} onAccept={request => void requestAction(request, 'accept')} onCancel={request => void requestAction(request, 'cancel')} onInterest={(interest, response) => void interestAction(interest, response)} />}

      {tab === 'properties' && properties.length > 0 && <div className="marketplace-summary-strip"><span><MapPin size={17} /><strong>{properties.length}</strong> properties</span><span><Handshake size={17} /><strong>{pendingInterests.length}</strong> new company interests</span><span><Route size={17} /><strong>{activeRequests.length}</strong> active installations</span></div>}
      {modal && <HostMarketplaceModal modal={modal} form={form} setForm={setForm} properties={properties} saving={saving} error={error} onClose={() => setModal(null)} onSubmit={() => void submitModal()} />}
    </section>
  );
}

function PropertiesPanel({ properties, onAdd, onEdit, onFind }: { properties: HostProperty[]; onAdd: () => void; onEdit: (property: HostProperty) => void; onFind: (id: number) => void }) {
  return <div className="marketplace-stack"><div className="marketplace-section-head"><div><h2>My properties</h2><p>Compare verified charger companies nationwide by hardware, price and commercial model.</p></div><button className="marketplace-primary" onClick={onAdd}><Plus size={16} /> Add property</button></div><div className="property-grid">{properties.map(property => <article className="property-card" key={property.id}><div className="property-card-top"><span><Building2 size={21} /></span><i className={property.discoverable ? 'published' : ''}>{property.discoverable ? 'PUBLISHED' : property.verificationStage === 'DRAFT' ? 'DRAFT' : 'PRIVATE'}</i></div><h3>{property.title}</h3><p><MapPin size={13} />{property.city || property.address}{property.state ? `, ${property.state}` : ''}</p><div className="property-specs"><span><strong>{property.availableParkingBays}</strong> bays</span><span><strong>{property.availableLoadKw} kW</strong> load</span><span><strong>{readable(property.powerPhase)}</strong> power</span></div><div className="property-preference"><Zap size={15} /><span><small>Preferred setup</small><strong>{property.preferredConnectorType || 'Flexible'} · {property.preferredPowerKw || 'Any'} kW</strong></span></div><footer><button onClick={() => onEdit(property)}><PencilLine size={14} /> Edit</button><button className="marketplace-primary" onClick={() => onFind(property.id)}>Find companies <ArrowRight size={14} /></button></footer></article>)}{!properties.length && <MarketplaceEmpty icon={Building2} title="List your first installation site" text="Add parking, power and ownership details so verified companies across India can contact you." action="Add property" onAction={onAdd} />}</div></div>;
}

function CompanyDiscovery({ properties, selectedId, onProperty, companies, loading, onAddProperty, onRequest }: { properties: HostProperty[]; selectedId: number | null; onProperty: (id: number) => void; companies: MarketplaceCompany[]; loading: boolean; onAddProperty: () => void; onRequest: (company: MarketplaceCompany, product: ChargerProduct) => void }) {
  if (!properties.length) return <MarketplaceEmpty icon={MapPin} title="A property is required" text="Publish the site where you want a charger before comparing installation companies." action="Add property" onAction={onAddProperty} />;
  return <div className="marketplace-stack"><div className="marketplace-property-selector"><div><MapPin size={18} /><span><small>Nationwide companies for</small><strong>{properties.find(property => property.id === selectedId)?.title}</strong></span></div><select value={selectedId ?? ''} onChange={event => onProperty(Number(event.target.value))}>{properties.map(property => <option key={property.id} value={property.id}>{property.title} · {property.city}</option>)}</select></div>{loading ? <div className="marketplace-loading"><RefreshCw className="spinning" /> Loading verified charger companies…</div> : <div className="company-market-grid">{companies.map(company => <article className="supplier-card" key={company.id}><header><span><Building2 size={22} /></span><div><h2>{company.companyName}</h2><p><ShieldCheck size={13} /> Verified supplier · {company.matchedBy}</p></div>{company.distanceKm != null && <i>{company.distanceKm} km</i>}</header><div className="supplier-services"><span>Site survey</span><span>Installation</span><span>Maintenance support</span></div><div className="product-stack">{company.products.map(product => <div className="product-row" key={product.id}><span className={product.currentType.toLowerCase()}><Zap size={18} /></span><div><strong>{product.modelName}</strong><small>{product.currentType} · {product.connectorType} · {product.powerKw} kW</small><p>{money(product.equipmentPrice)} + {money(product.installationPrice)} installation</p><div>{product.businessModels.map(model => <i key={model}>{readable(model)}</i>)}</div></div><button onClick={() => onRequest(company, product)}>Request proposal</button></div>)}</div></article>)}{!companies.length && <MarketplaceEmpty icon={Building2} title="No charger products published yet" text="Verified companies appear nationwide after publishing at least one active charger product." />}</div>}</div>;
}

function HostPipeline({ requests, interests, saving, onAccept, onCancel, onInterest }: { requests: InstallationRequest[]; interests: PropertyInterest[]; saving: boolean; onAccept: (request: InstallationRequest) => void; onCancel: (request: InstallationRequest) => void; onInterest: (interest: PropertyInterest, response: 'accept' | 'decline') => void }) {
  return <div className="marketplace-stack">{interests.length > 0 && <section className="marketplace-panel"><div className="marketplace-section-head"><div><h2>Companies interested in your land</h2><p>Accepting interest allows the company to continue the conversation.</p></div><Handshake size={21} /></div><div className="interest-list">{interests.map(interest => <div key={interest.id}><span><Building2 size={18} /></span><div><strong>{interest.companyName}</strong><small>{interest.propertyTitle} · {interest.propertyCity}</small><p>{interest.message || 'The company wants to discuss this location.'}</p></div><i>{interest.status}</i>{interest.status === 'PENDING' && <section><button onClick={() => onInterest(interest, 'decline')} disabled={saving}>Decline</button><button className="marketplace-primary" onClick={() => onInterest(interest, 'accept')} disabled={saving}>Accept</button></section>}</div>)}</div></section>}<section className="marketplace-panel"><div className="marketplace-section-head"><div><h2>Installation pipeline</h2><p>Survey, proposal, installation and activation progress.</p></div><Route size={21} /></div><div className="pipeline-list">{requests.map(request => <article key={request.id}><header><div><span>{request.status}</span><h3>{request.propertyTitle}</h3><p>{request.companyName} · {request.productName} × {request.quantity}</p></div><strong>{request.businessModel === 'PURCHASE' ? money((request.proposal?.equipmentTotal ?? 0) + (request.proposal?.installationTotal ?? 0)) : readable(request.businessModel)}</strong></header><div className="pipeline-progress">{request.history.map((item, index) => <span className="done" key={item.id}><i>{index + 1}</i><small>{readable(item.status)}</small></span>)}</div>{request.proposal && <div className="proposal-summary"><div><small>Equipment</small><strong>{money(request.proposal.equipmentTotal)}</strong></div><div><small>Installation</small><strong>{money(request.proposal.installationTotal)}</strong></div><div><small>Valid until</small><strong>{shortDate(request.proposal.validUntil)}</strong></div><p>{request.proposal.terms}</p></div>}<footer><span><Clock3 size={14} /> Updated {shortDate(request.updatedAt)}</span><div>{request.status === 'PROPOSAL_SENT' && <button className="marketplace-primary" onClick={() => onAccept(request)} disabled={saving}>Accept proposal</button>}{!['INSTALLING','INSTALLED','COMMISSIONED','LIVE','DECLINED','CANCELLED','EXPIRED'].includes(request.status) && <button onClick={() => onCancel(request)} disabled={saving}>Cancel</button>}{request.status === 'LIVE' && <i className="live"><CheckCircle2 size={14} /> Live on Vidyut</i>}</div></footer></article>)}{!requests.length && <MarketplaceEmpty icon={Route} title="No installation requests" text="Choose a company and charger product to start the proposal process." />}</div></section></div>;
}

function HostMarketplaceModal({ modal, form, setForm, properties, saving, error, onClose, onSubmit }: { modal: Exclude<ModalState, null>; form: Record<string, string | number | boolean>; setForm: React.Dispatch<React.SetStateAction<Record<string, string | number | boolean>>>; properties: HostProperty[]; saving: boolean; error: string; onClose: () => void; onSubmit: () => void }) {
  const field = (name: string, label: string, type = 'text') => <label>{label}<input type={type} value={String(form[name] ?? '')} onChange={event => setForm(current => ({ ...current, [name]: type === 'number' ? Number(event.target.value) : event.target.value }))} /></label>;
  const select = (name: string, label: string, options: Array<string | { value: string | number; label: string }>) => <label>{label}<select value={String(form[name] ?? '')} onChange={event => setForm(current => ({ ...current, [name]: event.target.value }))}>{options.map(option => typeof option === 'string' ? <option key={option} value={option}>{readable(option)}</option> : <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>;
  return <div className="marketplace-modal-backdrop" onMouseDown={onClose}><section className="marketplace-modal" role="dialog" aria-modal="true" onMouseDown={event => event.stopPropagation()}><header><div><span>{modal.kind === 'property' ? 'HOST PROPERTY' : 'INSTALLATION REQUEST'}</span><h2>{modal.kind === 'property' ? `${modal.property ? 'Update' : 'Add'} installation site` : `Request ${modal.product.modelName}`}</h2><p>{modal.kind === 'property' ? 'Pin the exact land location, then confirm its address, power, parking and verification evidence.' : `${modal.company.companyName} will review your property and respond with next steps.`}</p></div><button onClick={onClose} aria-label="Close marketplace form"><X size={18} /></button></header>{error && <div className="marketplace-message error"><CircleAlert size={16} />{error}</div>}<div className="marketplace-form-grid">{modal.kind === 'property' ? <>{field('title','Property title')}{select('propertyType','Property type',['COMMERCIAL_PARKING','RESIDENTIAL','OFFICE','MALL','HOTEL','HIGHWAY','FUEL_STATION','OTHER'])}<PropertyLocationPicker latitude={Number(form.latitude)} longitude={Number(form.longitude)} address={String(form.address ?? '')} onChange={selection => setForm(current => ({ ...current, ...selection }))} /><label className="wide">Full address<input value={String(form.address ?? '')} onChange={event => setForm(current => ({ ...current, address: event.target.value }))} /></label>{field('city','City')}{field('state','State')}{field('pincode','PIN code')}{select('ownershipType','Ownership',['OWNED','LEASED','AUTHORIZED'])}{field('availableParkingBays','Available parking bays','number')}{select('powerPhase','Power supply',['THREE_PHASE','SINGLE_PHASE','NOT_SURE'])}{field('availableLoadKw','Available load (kW)','number')}{field('operatingHours','Operating hours')}{select('preferredConnectorType','Preferred connector',['CCS2','TYPE2','CHADEMO','GB_T','TYPE1'])}{field('preferredPowerKw','Preferred charger power (kW)','number')}{field('pricePerKwh','Expected customer price ₹/kWh','number')}<label className="wide">Ownership document URL<input type="url" required placeholder="https://…/ownership-deed.pdf" value={String(form.ownershipDocumentUrl ?? '')} onChange={event => setForm(current => ({ ...current, ownershipDocumentUrl: event.target.value }))} /><small>Ownership deed, lease agreement or signed authorization.</small></label><label className="wide">Electricity / sanctioned-load document URL<input type="url" required placeholder="https://…/electricity-bill.pdf" value={String(form.electricityDocumentUrl ?? '')} onChange={event => setForm(current => ({ ...current, electricityDocumentUrl: event.target.value }))} /><small>Used to validate the meter, connection and available electrical load.</small></label><label className="wide">Site walkthrough video URL<input type="url" required placeholder="https://…/site-walkthrough" value={String(form.videoVerificationUrl ?? '')} onChange={event => setForm(current => ({ ...current, videoVerificationUrl: event.target.value }))} /><small>Show the entrance, road access, parking bays, meter/electrical room and proposed charger location.</small></label><label className="wide">Property photo URLs <small>(optional)</small><input value={String(form.photoUrls ?? '')} onChange={event => setForm(current => ({ ...current, photoUrls: event.target.value }))} /></label><label className="marketplace-check wide"><input type="checkbox" checked={Boolean(form.discoverable)} onChange={event => setForm(current => ({ ...current, discoverable: event.target.checked }))} /> Let verified companies serving this area discover the opportunity after Vidyut review</label></> : <>{select('propertyId','Installation property',properties.map(property => ({ value: property.id, label: `${property.title} · ${property.city}` })))}{select('businessModel','Business model',modal.product.businessModels)}{field('quantity','Quantity','number')}{field('budget','Maximum budget (₹)','number')}{field('targetInstallationDate','Target installation date','date')}<label className="wide">Message to company<textarea value={String(form.message ?? '')} onChange={event => setForm(current => ({ ...current, message: event.target.value }))} placeholder="Describe access, transformer availability or preferred commercial terms." /></label></>}</div><footer><button onClick={onClose}>Cancel</button><button className="marketplace-primary" onClick={onSubmit} disabled={saving}>{saving ? 'Saving…' : modal.kind === 'property' ? 'Submit for verification' : 'Send request'}</button></footer></section></div>;
}

function MarketplaceEmpty({ icon: Icon, title, text, action, onAction }: { icon: typeof Building2; title: string; text: string; action?: string; onAction?: () => void }) {
  return <div className="marketplace-empty"><span><Icon size={25} /></span><h3>{title}</h3><p>{text}</p>{action && onAction && <button className="marketplace-primary" onClick={onAction}>{action}</button>}</div>;
}
