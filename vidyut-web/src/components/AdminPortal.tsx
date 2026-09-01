import { useCallback, useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import {
  Activity, BadgeIndianRupee, BellRing, BookOpenCheck, Bot, Building2, CalendarClock,
  Check, ChevronRight, CircleAlert, CreditCard, FileCheck2, Gauge, LandPlot, LogOut,
  Menu, Network, PackageCheck, RefreshCw, Search, ShieldCheck, ShieldEllipsis, Store,
  UserCog, Users, Wrench, X, AlertTriangle, BatteryCharging, Handshake, Leaf, MapPin,
  MessageSquare, Send,
} from 'lucide-react';
import { apiRequest } from '../services/api';
import './AdminPortal.css';

type Capability = 'OVERVIEW' | 'ACCOUNTS' | 'VERIFICATIONS' | 'OPERATIONS' | 'FINANCE' | 'SUPPORT' | 'AI_NETWORK' | 'AUDIT';
type AdminRole = 'SUPER_ADMIN' | 'VERIFICATION_ADMIN' | 'SUPPORT_ADMIN' | 'FINANCE_ADMIN' | 'OPERATIONS_ADMIN';
type Tab = 'overview' | 'accounts' | 'verifications' | 'operations' | 'marketplace' | 'finance' | 'revenue' | 'support' | 'assistance' | 'network' | 'audit';

interface AdminProfile { accountId: number; email: string; displayName: string; role: AdminRole; capabilities: Capability[]; lastLoginAt?: string }
interface AdminSession { token: string; admin: AdminProfile }
interface Verification {
  id: number; companyId: number; legalName?: string; cinLlpin?: string; gstin?: string; panLast4?: string;
  representativeName?: string; representativeWorkEmail?: string; representativePhone?: string;
  bankAccountHolder?: string; bankName?: string; bankAccountLast4?: string;
  businessIdentityVerified: boolean; representativeVerified: boolean; bankVerified: boolean;
  chargerDocumentsVerified: boolean; status: string; trustLevel: string; completedLayers: number;
  missingRequirements: string[]; submittedAt?: string; rejectionReason?: string;
  incorporationDocumentUrl?: string; gstCertificateUrl?: string; authorizationProofUrl?: string;
  cancelledChequeUrl?: string; chargerCatalogueUrl?: string; complianceDocumentUrl?: string;
}
interface Snapshot {
  metrics: Record<string, number>;
  accounts: Array<Record<string, unknown>>;
  companyVerifications: Verification[];
  companyVerificationHistory: Verification[];
  hostVerifications: Array<Record<string, unknown>>;
  properties: Array<Record<string, unknown>>;
  products: Array<Record<string, unknown>>;
  stations: Array<Record<string, unknown>>;
  connectors: Array<Record<string, unknown>>;
  activeSessions: Array<Record<string, unknown>>;
  incidents: Array<Record<string, unknown>>;
  maintenanceTickets: Array<Record<string, unknown>>;
  installations: Array<Record<string, unknown>>;
  settlements: Array<Record<string, unknown>>;
  supportCases: Array<Record<string, unknown>>;
  greenSchemes: Array<Record<string, unknown>>;
  bookings: Array<Record<string, unknown>>;
  payments: Array<Record<string, unknown>>;
  autopilotTrips: Array<Record<string, unknown>>;
  networkSuggestions: Array<Record<string, unknown> & { delayMinutes?: number }>;
  announcements: Array<Record<string, unknown>>;
}
interface Audit { id: number; adminAccountId: number; action: string; resourceType: string; resourceId?: string; summary: string; previousValue?: string; newValue?: string; reason?: string; createdAt: string }
interface AgentResponse { answer:string; findings:Array<Record<string,unknown>>; suggestedActions:Array<Record<string,unknown>>; sourceOfTruth:string; requiresApproval:boolean }

const emptySnapshot: Snapshot = { metrics: {}, accounts: [], companyVerifications: [], companyVerificationHistory: [], hostVerifications: [], properties: [], products: [], stations: [], connectors: [], activeSessions: [], incidents: [], maintenanceTickets: [], installations: [], settlements: [], supportCases: [], greenSchemes: [], bookings: [], payments: [], autopilotTrips: [], networkSuggestions: [], announcements: [] };
const nav: Array<{ tab: Tab; capability: Capability; label: string; icon: typeof Gauge }> = [
  { tab: 'overview', capability: 'OVERVIEW', label: 'Command center', icon: Gauge },
  { tab: 'accounts', capability: 'ACCOUNTS', label: 'Accounts & staff', icon: Users },
  { tab: 'verifications', capability: 'VERIFICATIONS', label: 'Trust & verification', icon: ShieldCheck },
  { tab: 'operations', capability: 'OPERATIONS', label: 'Network operations', icon: Wrench },
  { tab: 'marketplace', capability: 'OPERATIONS', label: 'Marketplace control', icon: Handshake },
  { tab: 'finance', capability: 'FINANCE', label: 'Finance & settlements', icon: BadgeIndianRupee },
  { tab: 'revenue', capability: 'FINANCE', label: 'Revenue intelligence', icon: Activity },
  { tab: 'support', capability: 'SUPPORT', label: 'Support & notices', icon: BellRing },
  { tab: 'assistance', capability: 'FINANCE', label: 'Green assistance', icon: Leaf },
  { tab: 'network', capability: 'AI_NETWORK', label: 'AI network control', icon: Network },
  { tab: 'audit', capability: 'AUDIT', label: 'Audit trail', icon: BookOpenCheck },
];

function loadAdminSession(): AdminSession | null {
  try {
    const token = localStorage.getItem('vidyut_admin_token');
    const profile = localStorage.getItem('vidyut_admin_profile');
    return token && profile ? { token, admin: JSON.parse(profile) as AdminProfile } : null;
  } catch { return null; }
}

function saveAdminSession(session: AdminSession) {
  localStorage.setItem('vidyut_admin_token', session.token);
  localStorage.setItem('vidyut_admin_profile', JSON.stringify(session.admin));
}

function clearAdminSession() {
  localStorage.removeItem('vidyut_admin_token');
  localStorage.removeItem('vidyut_admin_profile');
}

function tabFromHash(): Tab {
  const value = window.location.hash.replace('#/admin/', '');
  return (nav.some(item => item.tab === value) ? value : 'overview') as Tab;
}

export function AdminPortal() {
  const [session, setSession] = useState<AdminSession | null>(() => loadAdminSession());
  const [tab, setTab] = useState<Tab>(() => tabFromHash());
  const [snapshot, setSnapshot] = useState<Snapshot>(emptySnapshot);
  const [audits, setAudits] = useState<Audit[]>([]);
  const [loading, setLoading] = useState(Boolean(session));
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);
  const [search, setSearch] = useState('');

  const auth = useMemo(() => session ? { headers: { Authorization: `Bearer ${session.token}` } } : {}, [session]);
  const load = useCallback(async () => {
    if (!session) return;
    setLoading(true); setError('');
    try {
      const [data, log] = await Promise.all([
        apiRequest<Snapshot>('/admin/portal/snapshot', { method: 'GET', ...auth }),
        session.admin.capabilities.includes('AUDIT')
          ? apiRequest<Audit[]>('/admin/portal/audit', { method: 'GET', ...auth }) : Promise.resolve([]),
      ]);
      setSnapshot(data); setAudits(log);
    } catch (loadError) {
      const message = loadError instanceof Error ? loadError.message : 'Unable to load the Admin Portal.';
      setError(message);
      if (/unauthorized|token|administrator access/i.test(message)) { clearAdminSession(); setSession(null); }
    } finally { setLoading(false); }
  }, [auth, session]);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    const sync = () => setTab(tabFromHash());
    window.addEventListener('hashchange', sync);
    return () => window.removeEventListener('hashchange', sync);
  }, []);

  if (!session) return <AdminLogin onLogin={(next) => { saveAdminSession(next); setSession(next); window.location.hash = '#/admin/overview'; }} />;

  const visibleNav = nav.filter(item => session.admin.capabilities.includes(item.capability));
  const go = (next: Tab) => { setTab(next); setMenuOpen(false); window.location.hash = `#/admin/${next}`; };
  const action = async (key: string, path: string, init: RequestInit, success: string) => {
    try {
      setBusy(key); setError(''); setNotice('');
      await apiRequest(path, { ...init, ...auth, headers: { ...(auth as { headers?: Record<string,string> }).headers, ...(init.headers as Record<string,string> || {}) } });
      setNotice(success); await load();
    } catch (actionError) { setError(actionError instanceof Error ? actionError.message : 'Action failed.'); }
    finally { setBusy(''); }
  };
  const queryAgent = (question:string) => apiRequest<AgentResponse>('/admin/portal/agent/query', { method:'POST', body:JSON.stringify({question}), ...auth });

  return <div className="admin-shell">
    <aside className={menuOpen ? 'admin-sidebar open' : 'admin-sidebar'}>
      <div className="admin-brand"><img className="admin-brand-logo" src="/vidyut-logo.svg" alt=""/><div><strong>VIDYUT</strong><small>Control panel</small></div><button onClick={() => setMenuOpen(false)} aria-label="Close navigation"><X size={18}/></button></div>
      <div className="admin-scope"><ShieldEllipsis size={18}/><div><strong>{session.admin.role.replaceAll('_', ' ')}</strong><small>Least-privilege workspace</small></div></div>
      <nav>{visibleNav.map(item => <button key={item.tab} className={tab === item.tab ? 'active' : ''} onClick={() => go(item.tab)}><item.icon size={18}/><span>{item.label}</span><ChevronRight size={14}/></button>)}</nav>
      <div className="admin-user"><div>{initials(session.admin.displayName)}</div><span><strong>{session.admin.displayName}</strong><small title={session.admin.email}>{session.admin.email}</small></span><button aria-label="Log out" onClick={() => { clearAdminSession(); setSession(null); window.location.hash = '#/admin/login'; }}><LogOut size={18}/></button></div>
    </aside>
    {menuOpen && <button className="admin-scrim" onClick={() => setMenuOpen(false)} aria-label="Close navigation"/>}
    <main className="admin-main">
      <header className="admin-topbar"><button className="admin-menu" onClick={() => setMenuOpen(true)}><Menu size={19}/></button><div className="admin-search"><Search size={17}/><input value={search} onChange={event => setSearch(event.target.value)} placeholder="Search the current control view"/></div><span className="admin-live"><i/> LIVE CONTROL</span><button className="admin-refresh" onClick={() => void load()} disabled={loading}><RefreshCw size={16} className={loading ? 'spin' : ''}/>Sync</button></header>
      <div className="admin-content">
        <div className="admin-heading"><div><span>VIDYUT ADMIN · GOVERNANCE</span><h1>{nav.find(item => item.tab === tab)?.label}</h1><p>Review, intervene and audit without crossing account boundaries.</p></div><div><ShieldCheck size={18}/> Access scoped to {session.admin.role.replaceAll('_',' ').toLowerCase()}</div></div>
        {error && <div className="admin-alert error"><CircleAlert size={18}/>{error}</div>}
        {notice && <div className="admin-alert success"><Check size={18}/>{notice}</div>}
        {loading ? <AdminLoading/> : <AdminView tab={tab} snapshot={snapshot} audits={audits} search={search} admin={session.admin} busy={busy} action={action} queryAgent={queryAgent}/>}
      </div>
    </main>
  </div>;
}

function AdminLogin({ onLogin }: { onLogin: (session: AdminSession) => void }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [show, setShow] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setLoading(true); setError('');
    try { onLogin(await apiRequest<AdminSession>('/admin/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) })); }
    catch (loginError) { setError(loginError instanceof Error ? loginError.message : 'Unable to sign in.'); }
    finally { setLoading(false); }
  };
  return <div className="admin-login-page"><section className="admin-login-story"><div className="admin-login-brand"><img src="/vidyut-logo.svg" alt=""/><strong>VIDYUT</strong></div><div><span className="admin-kicker">NATIONAL EV NETWORK CONTROL</span><h1>One trusted view.<br/>Every critical decision.</h1><p>A separate, auditable workspace for verification, operations, support, finance and AI network governance.</p><div className="admin-story-grid"><article><ShieldCheck/><strong>Layered trust</strong><span>Verify businesses, representatives, banks, chargers, Hosts and land.</span></article><article><Network/><strong>Network intelligence</strong><span>Turn route experience into safer placement and intervention decisions.</span></article></div></div><small>Authorized Vidyut personnel only · Actions are recorded</small></section><section className="admin-login-panel"><form onSubmit={submit}><div className="admin-lock"><ShieldEllipsis size={25}/></div><span className="admin-kicker">SECURE ADMIN ACCESS</span><h2>Sign in to Control Plane</h2><p>This login is isolated from EV Owner, Host and Company accounts.</p>{error && <div className="admin-alert error"><CircleAlert size={17}/>{error}</div>}<label>Administrator email<input autoFocus type="email" value={email} onChange={event => setEmail(event.target.value)} required/></label><label>Password<div className="admin-password"><input type={show ? 'text' : 'password'} value={password} onChange={event => setPassword(event.target.value)} required/><button type="button" onClick={() => setShow(value => !value)}>{show ? 'Hide' : 'Show'}</button></div></label><button className="admin-signin" disabled={loading}>{loading ? 'Checking secure access…' : 'Enter Admin Portal'}<ChevronRight size={18}/></button><a href="#/login">Return to Vidyut app login</a><small>Local bootstrap credentials are configured server-side through VIDYUT_ADMIN_* environment variables.</small></form></section></div>;
}

function AdminView({ tab, snapshot, audits, search, admin, busy, action, queryAgent }: { tab: Tab; snapshot: Snapshot; audits: Audit[]; search: string; admin: AdminProfile; busy: string; action: AdminAction; queryAgent:(question:string)=>Promise<AgentResponse> }) {
  if (tab === 'overview') return <Overview snapshot={snapshot}/>;
  if (tab === 'verifications') return <VerificationCenter snapshot={snapshot} busy={busy} action={action}/>;
  if (tab === 'accounts') return <AccountsView items={snapshot.accounts} search={search} admin={admin} busy={busy} action={action}/>;
  if (tab === 'operations') return <OperationsView snapshot={snapshot} search={search} busy={busy} action={action}/>;
  if (tab === 'marketplace') return <MarketplaceControl snapshot={snapshot} search={search} busy={busy} action={action}/>;
  if (tab === 'finance') return <FinanceView snapshot={snapshot} busy={busy} action={action}/>;
  if (tab === 'revenue') return <RevenueView snapshot={snapshot} search={search}/>;
  if (tab === 'support') return <SupportView snapshot={snapshot} busy={busy} action={action}/>;
  if (tab === 'assistance') return <GreenAssistanceView snapshot={snapshot} busy={busy} action={action}/>;
  if (tab === 'network') return <NetworkView snapshot={snapshot} queryAgent={queryAgent}/>;
  return <AuditView items={audits} search={search}/>;
}

function Overview({ snapshot }: { snapshot: Snapshot }) {
  const m = snapshot.metrics;
  const activeIncidents = snapshot.incidents.filter(item => str(item.status) !== 'RESOLVED');
  return <div className="admin-stack">
    <div className="admin-metrics"><Metric icon={Users} label="Active accounts" value={m.accounts}/><Metric icon={Building2} label="Companies" value={m.companies}/><Metric icon={UserCog} label="Hosts" value={m.hosts}/><Metric icon={Store} label="Stations" value={m.stations}/><Metric icon={BatteryCharging} label="Chargers" value={m.connectors}/></div>
    <div className="admin-network-state"><Attention label="Available" value={m.availableChargers||0} tone="green"/><Attention label="Occupied" value={m.occupiedChargers||0} tone="blue"/><Attention label="Reserved sites" value={m.reservedChargers||0} tone="violet"/><Attention label="Offline" value={m.offlineChargers||0} tone="amber"/><Attention label="Fault" value={m.faultChargers||0} tone="red"/></div>
    <div className="admin-overview-grid"><Panel title="Today at a glance" subtitle="Backend-derived operational and commercial activity" icon={Activity}><div className="admin-coverage"><span><b>{m.activeSessions||0}</b> active sessions</span><span><b>{m.todayBookings||0}</b> bookings today</span><span><b>{m.todayTransactions||0}</b> transactions today</span><span><b>{money(m.platformRevenue||0)}</b> platform revenue</span></div></Panel><Panel title="Trust & critical attention" subtitle="Only current work appears in the main queues" icon={ShieldCheck}><div className="admin-attention"><Attention label="Pending verification" value={m.pendingVerifications||0} tone="amber"/><Attention label="Critical alerts" value={m.criticalAlerts||0} tone="red"/><Attention label="Support cases" value={snapshot.supportCases.filter(item=>!['RESOLVED','CLOSED'].includes(str(item.status))).length} tone="violet"/><Attention label="Open incidents" value={activeIncidents.length} tone="blue"/></div></Panel></div>
    <Panel title="Critical network alerts" subtitle="Faults, rerouting outcomes and required intervention" icon={AlertTriangle}><DataTable headers={['Incident','Station','Severity','Rerouted','Manual','Status']} rows={activeIncidents.slice(0,8).map(item=>[str(item.incidentCode),str(item.stationName),<Status key={`severity-${item.id}`} value={str(item.severity)}/>,str(item.usersRerouted),str(item.manualInterventions),<Status key={`incident-${item.id}`} value={str(item.status)}/>])}/></Panel>
  </div>;
}

function VerificationCenter({ snapshot, busy, action }: { snapshot: Snapshot; busy: string; action: AdminAction }) {
  const pendingProperties = snapshot.properties.filter(item => !['PUBLISHED','REJECTED'].includes(str(item.verificationStage)));
  const pendingStations = snapshot.stations.filter(item => !['LIVE','SUSPENDED'].includes(str(item.verificationStage)));
  return <div className="admin-stack">
    <Panel title="Pending company reviews" subtitle="Business, representative, bank and charger evidence" icon={Building2}>{snapshot.companyVerifications.length ? snapshot.companyVerifications.map(item => <CompanyVerificationCard key={item.id} item={item} busy={busy} action={action}/>) : <Empty text="No company submissions are waiting."/>}</Panel>
    <Panel title="Approved and closed reviews" subtitle="Final decisions stay out of the pending queue" icon={FileCheck2}><DataTable headers={['Company','Decision','Trust','Reviewed','Reason']} rows={snapshot.companyVerificationHistory.map(item=>[item.legalName||`Company #${item.companyId}`,<Status key={`history-${item.id}`} value={item.status}/>,item.trustLevel,date(item.submittedAt||''),item.rejectionReason||'All required layers verified'])}/></Panel>
    <div className="admin-dual"><ReviewList title="Host identity & KYC" icon={UserCog} items={snapshot.hostVerifications} nameKey="name" statusKey="status" detailKey="email" evidenceKey="kycDocumentUrl" busy={busy} onReview={(item, approved) => action(`host-${item.accountId}`, `/admin/portal/hosts/${item.accountId}/review`, { method:'PATCH', body:JSON.stringify({approved,note:approved?'Identity evidence approved.':'Identity evidence requires correction.'}) }, `Host ${approved?'approved':'rejected'}.`)}/><Panel title="Property verification" subtitle="Document → video → physical inspection when risk requires" icon={LandPlot}>{pendingProperties.length ? <div className="admin-workflow-list">{pendingProperties.map(item=><PropertyVerificationCard key={str(item.id)} item={item} busy={busy} action={action}/>)}</div>:<Empty text="No properties are waiting."/>}</Panel></div>
    <Panel title="Company-owned and partnered station verification" subtitle="A Company can publish its own station without becoming a Host" icon={Store}>{pendingStations.length ? <div className="admin-workflow-list">{pendingStations.map(item=><StationVerificationCard key={str(item.id)} item={item} busy={busy} action={action}/>)}</div>:<Empty text="No station submissions are waiting."/>}</Panel>
    <ReviewList title="Charger catalogue compliance" icon={PackageCheck} items={snapshot.products.filter(item => item.approvalStatus !== 'APPROVED')} nameKey="model" statusKey="approvalStatus" detailKey="company" busy={busy} onReview={(item,approved)=>action(`product-${item.id}`,`/admin/portal/products/${item.id}/review`,{method:'PATCH',body:JSON.stringify({approved,note:approved?'Compliance document approved.':'Compliance evidence requires correction.'})},`Product ${approved?'published':'rejected'}.`)}/>
  </div>;
}

function CompanyVerificationCard({item,busy,action}:{item:Verification;busy:string;action:AdminAction}) {
  const review = (status:string,note:string) => action(`company-${item.id}-${status}`,`/admin/portal/companies/${item.companyId}/review`,{method:'PATCH',body:JSON.stringify({status,businessIdentityVerified:status==='VERIFIED'||item.businessIdentityVerified,representativeVerified:status==='VERIFIED'||item.representativeVerified,bankVerified:status==='VERIFIED'||item.bankVerified,chargerDocumentsVerified:status==='VERIFIED'||item.chargerDocumentsVerified,trustLevel:status==='VERIFIED'?'VIDYUT_VERIFIED':'UNVERIFIED',note,rejectionReason:status==='REJECTED'?note:null})},`Company moved to ${status.replaceAll('_',' ').toLowerCase()}.`);
  return <article className="admin-review-card"><div className="admin-review-title"><div className="admin-entity-icon"><Building2/></div><span><strong>{item.legalName||`Company #${item.companyId}`}</strong><small>{item.cinLlpin} · GST {item.gstin} · PAN •••• {item.panLast4}</small></span><Status value={item.status}/></div><div className="admin-layer-grid"><Layer label="Business identity" ok={item.businessIdentityVerified} detail={item.incorporationDocumentUrl}/><Layer label="Representative" ok={item.representativeVerified} detail={item.representativeName}/><Layer label="Bank match" ok={item.bankVerified} detail={`${item.bankName||'Bank'} •••• ${item.bankAccountLast4||'—'}`}/><Layer label="Charger compliance" ok={item.chargerDocumentsVerified} detail={item.complianceDocumentUrl}/></div><div className="admin-review-actions wrap"><span>{item.completedLayers}/4 approved · {item.trustLevel.replaceAll('_',' ')}</span><button disabled={Boolean(busy)} onClick={()=>void review('NEEDS_INFORMATION','Additional or corrected evidence is required.')}>Request information</button><button disabled={Boolean(busy)} onClick={()=>void review('ESCALATED','Verification escalated for specialist review.')}>Escalate</button><button className="reject" disabled={Boolean(busy)} onClick={()=>void review('REJECTED','Submitted evidence could not be validated.')}>Reject</button><button disabled={Boolean(busy)} onClick={()=>void review('VERIFIED','All four mandatory layers reviewed and approved.')}>Approve</button></div></article>;
}

function PropertyVerificationCard({item,busy,action}:{item:Record<string,unknown>;busy:string;action:AdminAction}) {
  const run=(workflow:string,note:string,scheduledAt?:string)=>action(`property-${item.id}-${workflow}`,`/admin/portal/properties/${item.id}/workflow`,{method:'PATCH',body:JSON.stringify({action:workflow,note,scheduledAt})},`Property moved to ${workflow.replaceAll('_',' ').toLowerCase()}.`);
  const tomorrow=new Date(Date.now()+86_400_000).toISOString().slice(0,19);
  return <article><div className="admin-workflow-head"><span><strong>{str(item.title)}</strong><small><MapPin size={13}/>{str(item.address)} · {str(item.parking)} bays · {str(item.availableLoadKw)} kW</small></span><Status value={str(item.verificationStage)||str(item.status)}/></div><div className="admin-evidence-row"><EvidenceLink label="Ownership" url={str(item.ownershipDocumentUrl)}/><EvidenceLink label="Electricity" url={str(item.electricityDocumentUrl)}/><EvidenceLink label="Site video" url={str(item.videoVerificationUrl)}/></div><div className="admin-workflow-actions"><button disabled={Boolean(busy)} onClick={()=>void run('START_REVIEW','Document review started.')}>Start review</button><button disabled={Boolean(busy)} onClick={()=>void run('REQUEST_INFORMATION','Host must supply missing or corrected information.')}>Need information</button><button disabled={Boolean(busy)} onClick={()=>void run('REQUEST_VIDEO','A live site-video verification is required.')}>Request video</button><button disabled={Boolean(busy)} onClick={()=>void run('SCHEDULE_INSPECTION','Physical inspection scheduled by verification Admin.',tomorrow)}>Schedule inspection</button><button disabled={Boolean(busy)} onClick={()=>void run('ESCALATE','High-risk property escalated.')}>Escalate</button><button className="reject" disabled={Boolean(busy)} onClick={()=>void run('REJECT','Verification requirements not met.')}>Reject</button><button disabled={Boolean(busy)} onClick={()=>void run('APPROVE','Ownership, electricity and site evidence verified.')}>Publish</button></div></article>;
}

function StationVerificationCard({item,busy,action}:{item:Record<string,unknown>;busy:string;action:AdminAction}) {
  const run=(reviewAction:string)=>action(`station-${item.id}-${reviewAction}`,`/admin/portal/stations/${item.id}/review`,{method:'PATCH',body:JSON.stringify({action:reviewAction,note:`${reviewAction.replaceAll('_',' ')} by verification Admin.`})},`Station moved to ${reviewAction.toLowerCase()}.`);
  return <article><div className="admin-workflow-head"><span><strong>{str(item.name)} · {str(item.city)}</strong><small>{str(item.ownershipType).replaceAll('_',' ')} · owner {str(item.propertyOwner)||'unverified'} · operator {str(item.operatorCompany)||'unassigned'}</small></span><Status value={str(item.verificationStage)}/></div><div className="admin-check-strip"><span>{item.companyVerified?'✓':'?'} company</span><span>{item.siteEvidence?'✓':'?'} site</span><span>{item.chargersVerified?'✓':'?'} chargers</span><span>{item.propertyOwnerVerified?'✓':'?'} owner</span></div><div className="admin-workflow-actions"><button onClick={()=>void run('START_REVIEW')} disabled={Boolean(busy)}>Review</button><button onClick={()=>void run('REQUEST_INFORMATION')} disabled={Boolean(busy)}>Need information</button><button onClick={()=>void run('ESCALATE')} disabled={Boolean(busy)}>Escalate</button><button onClick={()=>void run('VERIFY')} disabled={Boolean(busy)}>Verify</button><button onClick={()=>void run('PUBLISH')} disabled={Boolean(busy)}>Publish live</button></div></article>;
}

type AdminAction = (key:string,path:string,init:RequestInit,success:string)=>Promise<void>;

function AccountsView({ items, search, admin, busy, action }: { items:Array<Record<string,unknown>>;search:string;admin:AdminProfile;busy:string;action:AdminAction }) {
  const [form,setForm]=useState({email:'',password:'',displayName:'',role:'SUPPORT_ADMIN'});
  const filtered=filterRows(items,search);
  return <div className="admin-stack">
    {admin.role==='SUPER_ADMIN'&&<Panel title="Create scoped staff account" subtitle="Admins are separate identities with fixed module access" icon={UserCog}><form className="admin-inline-form" onSubmit={event=>{event.preventDefault();void action('create-admin','/admin/portal/admins',{method:'POST',body:JSON.stringify(form)},'Administrator account created.')}}><input placeholder="Full name" value={form.displayName} onChange={e=>setForm({...form,displayName:e.target.value})} required/><input placeholder="Work email" type="email" value={form.email} onChange={e=>setForm({...form,email:e.target.value})} required/><input placeholder="Temporary password (12+ chars)" type="password" minLength={12} value={form.password} onChange={e=>setForm({...form,password:e.target.value})} required/><select value={form.role} onChange={e=>setForm({...form,role:e.target.value})}><option>VERIFICATION_ADMIN</option><option>SUPPORT_ADMIN</option><option>FINANCE_ADMIN</option><option>OPERATIONS_ADMIN</option><option>SUPER_ADMIN</option></select><button disabled={Boolean(busy)}>Create staff account</button></form></Panel>}
    <Panel title="Access & operational controls" subtitle="Intervene at the smallest useful scope; identity suspension is an emergency exception" icon={Users}>
      <div className="admin-account-control-list">
        {filtered.map(item=><AccountControlCard key={str(item.id)} item={item} admin={admin} busy={busy} action={action}/>)}
      </div>
    </Panel>
  </div>;
}

type ControlDefinition = { control:string; key:string; label:string; activeLabel:string };

function AccountControlCard({item,admin,busy,action}:{item:Record<string,unknown>;admin:AdminProfile;busy:string;action:AdminAction}) {
  const accountId=Number(item.id);
  const controls=(typeof item.controls==='object'&&item.controls!==null?item.controls:{}) as Record<string,unknown>;
  const isCompany=str(item.accountType)==='COMPANY';
  const isHost=!isCompany&&str(item.roles).includes('ROLE_HOST');
  const kind=isCompany?'Company':isHost?'Host':'EV user';
  const definitions:ControlDefinition[]=isCompany?[
    {control:'PAUSE_COMPANY_BOOKINGS',key:'pauseCompanyBookings',label:'Pause new bookings',activeLabel:'Resume new bookings'},
    {control:'DISABLE_STATION_PUBLISHING',key:'disableStationPublishing',label:'Disable station publishing',activeLabel:'Allow station publishing'},
    {control:'FREEZE_SETTLEMENTS',key:'freezeSettlements',label:'Freeze settlements',activeLabel:'Unfreeze settlements'},
    {control:'SUSPEND_MARKETPLACE_ACCESS',key:'suspendMarketplaceAccess',label:'Pause marketplace access',activeLabel:'Restore marketplace access'},
    {control:'REQUIRE_COMPLIANCE_REVIEW',key:'requireComplianceReview',label:'Require compliance review',activeLabel:'Clear compliance review'},
  ]:isHost?[
    {control:'PAUSE_NEW_LISTINGS',key:'pauseNewListings',label:'Pause new listings',activeLabel:'Resume new listings'},
    {control:'FREEZE_PAYOUTS',key:'freezePayouts',label:'Freeze payouts',activeLabel:'Unfreeze payouts'},
    {control:'SUSPEND_NEW_PARTNERSHIPS',key:'suspendNewPartnerships',label:'Pause new partnerships',activeLabel:'Resume partnerships'},
    {control:'REQUIRE_SITE_REVERIFICATION',key:'requireSiteReverification',label:'Require site re-verification',activeLabel:'Clear re-verification'},
  ]:[
    {control:'RESTRICT_NEW_BOOKINGS',key:'restrictNewBookings',label:'Restrict new bookings',activeLabel:'Allow new bookings'},
    {control:'FREEZE_PAYMENTS',key:'freezePayments',label:'Freeze payments',activeLabel:'Unfreeze payments'},
    {control:'REQUIRE_USER_VERIFICATION',key:'requireUserVerification',label:'Require verification',activeLabel:'Clear verification requirement'},
  ];
  const toggle=(definition:ControlDefinition)=>{
    const enabled=controls[definition.key]!==true;
    return action(`control-${accountId}-${definition.control}`,`/admin/portal/accounts/${accountId}/controls`,{method:'PATCH',body:JSON.stringify({control:definition.control,enabled,reason:`Least-disruptive ${kind.toLowerCase()} intervention selected by Admin.`})},`${enabled?definition.label:definition.activeLabel} applied.`);
  };
  const warn=()=>{
    const message=window.prompt(`Warning message for ${str(item.displayName)||str(item.email)}`,'Please review the recent activity on your Vidyut account. No account access has been removed.');
    if(message?.trim()) return action(`warning-${accountId}`,`/admin/portal/accounts/${accountId}/warning`,{method:'POST',body:JSON.stringify({message:message.trim()})},'Warning delivered without restricting account access.');
    return Promise.resolve();
  };
  const temporaryRestriction=()=>action(`temporary-${accountId}`,`/admin/portal/accounts/${accountId}/controls`,{method:'PATCH',body:JSON.stringify({control:'TEMPORARILY_RESTRICT_ACCESS',enabled:controls.accessRestrictedUntil==null,durationHours:24,reason:'Time-limited Super Admin restriction while a serious security or fraud concern is reviewed.'})},controls.accessRestrictedUntil?'Temporary restriction cleared.':'New actions restricted for 24 hours; read access remains available.');
  const identityAccess=()=>{
    const restoring=item.enabled!==true;
    const reason=restoring?'Restoring identity after Admin review.':window.prompt('Emergency reason (fraud, security compromise, or repeated serious misuse only)');
    if(!reason?.trim()) return Promise.resolve();
    if(!restoring&&!window.confirm('Disable the entire identity? Prefer a scoped control whenever possible.')) return Promise.resolve();
    return action(`identity-${accountId}`,`/admin/portal/accounts/${accountId}/identity-access`,{method:'PATCH',body:JSON.stringify({enabled:restoring,reason:reason.trim(),confirmed:true})},restoring?'Identity access restored.':'Identity access disabled as an emergency action.');
  };
  const profile=isCompany?`${str(item.stations)} stations · ${str(item.chargers)} chargers · ${str(item.employees)} staff`:isHost?`${str(item.properties)} properties · ${str(item.partnerships)} partnerships · trust ${str(item.trustScore)}`:`${str(item.vehicles)} vehicles · ${str(item.bookings)} bookings`;
  return <article className="admin-account-control-card">
    <header><div><strong>{str(item.displayName)||str(item.email)}</strong><small>#{accountId} · {kind} · {profile}</small></div><Status value={item.enabled?'ACTIVE':'IDENTITY DISABLED'}/></header>
    <p>{isCompany?'Existing stations remain independent; use Network operations to isolate one unsafe station or charger.':isHost?'Existing verified properties remain available; property-specific controls stay in Marketplace control.':'The user keeps profile, vehicle, booking-history and support access unless an emergency identity action is justified.'}</p>
    <div className="admin-account-control-actions">
      {definitions.map(definition=>{const active=controls[definition.key]===true;return <button className={active?'active':''} key={definition.control} disabled={busy.length>0||item.enabled!==true} onClick={()=>void toggle(definition)}>{active?definition.activeLabel:definition.label}</button>;})}
      {!isCompany&&!isHost&&<button disabled={busy.length>0||item.enabled!==true} onClick={()=>void warn()}>Send warning</button>}
    </div>
    {admin.role==='SUPER_ADMIN'&&<details className="admin-advanced-control"><summary>Advanced security enforcement</summary><div><span>Use only for serious, documented risk.</span>{!isCompany&&!isHost&&item.enabled===true&&<button disabled={busy.length>0} onClick={()=>void temporaryRestriction()}>{controls.accessRestrictedUntil?'Clear temporary restriction':'Restrict new actions for 24h'}</button>}<button className={!item.enabled?'':'danger'} disabled={busy.length>0} onClick={()=>void identityAccess()}>{item.enabled?'Emergency disable identity':'Restore identity access'}</button></div></details>}
  </article>;
}

function OperationsView({ snapshot, search, busy, action }: { snapshot:Snapshot;search:string;busy:string;action:AdminAction }) {
  const [status,setStatus]=useState('ALL');
  const [type,setType]=useState('ALL');
  const [city,setCity]=useState('ALL');
  const cities=Array.from(new Set(snapshot.connectors.map(item=>str(item.city)).filter(Boolean))).sort();
  const connectors=filterRows(snapshot.connectors,search).filter(item=>(status==='ALL'||str(item.status)===status)&&(type==='ALL'||str(item.connectorType)===type)&&(city==='ALL'||str(item.city)===city));
  const activeIncidents=snapshot.incidents.filter(item=>str(item.status)!=='RESOLVED');
  const tracked=new Set(activeIncidents.map(item=>Number(item.connectorId)));
  const createIncident=(item:Record<string,unknown>)=>action(`incident-new-${item.id}`,'/admin/portal/incidents',{method:'POST',body:JSON.stringify({connectorId:Number(item.id),severity:str(item.status)==='FAULT'?'CRITICAL':'HIGH',reason:str(item.faultCode)||`${str(item.chargerCode)} stopped reporting safely`,estimatedDowntimeMinutes:180})},'Incident created; affected Autopilot journeys were processed.');
  const updateIncident=(item:Record<string,unknown>,next:string)=>action(`incident-${item.id}-${next}`,`/admin/portal/incidents/${item.id}`,{method:'PATCH',body:JSON.stringify({status:next,note:next==='RESOLVED'?'Company repair confirmed and network monitoring closed.':'Operations escalation requires manual intervention.'})},`Incident moved to ${next.toLowerCase()}.`);
  return <div className="admin-stack">
    <div className="admin-metrics"><Metric icon={BatteryCharging} label="Chargers" value={snapshot.connectors.length}/><Metric icon={Activity} label="Live sessions" value={snapshot.activeSessions.length}/><Metric icon={AlertTriangle} label="Open incidents" value={activeIncidents.length}/><Metric icon={Wrench} label="Open work orders" value={snapshot.maintenanceTickets.filter(item=>!['RESOLVED','CANCELLED'].includes(str(item.status))).length}/></div>
    <Panel title="Live charger monitoring" subtitle="Backend status is authoritative; filter by city, connector and state" icon={Network}><div className="admin-filter-row"><select value={city} onChange={e=>setCity(e.target.value)}><option>ALL</option>{cities.map(value=><option key={value}>{value}</option>)}</select><select value={type} onChange={e=>setType(e.target.value)}><option>ALL</option><option>CCS2</option><option>TYPE2</option><option>CHADEMO</option><option>GB_T</option><option>TYPE1</option></select><select value={status} onChange={e=>setStatus(e.target.value)}><option>ALL</option><option>ONLINE</option><option>CHARGING</option><option>OFFLINE</option><option>FAULT</option><option>MAINTENANCE</option></select></div><DataTable headers={['Charger','Station','Connector','State','Health / load','Live vehicle','Action']} rows={connectors.map(item=>[<div key={`charger-${item.id}`}><strong>{str(item.chargerCode)}</strong><small>heartbeat {date(str(item.lastHeartbeat))}</small></div>,`${str(item.stationName)} · ${str(item.city)}`,`${str(item.connectorType)} · ${str(item.powerKw)} kW`,<Status key={`charger-status-${item.id}`} value={str(item.status)}/>,`${str(item.healthScore)}% · ${str(item.currentPowerKw)} kW`,item.sessionId?`Vehicle #${str(item.vehicleId)} · session #${str(item.sessionId)}`:'—',['FAULT','OFFLINE','MAINTENANCE'].includes(str(item.status))&&!tracked.has(Number(item.id))?<button key={`incident-${item.id}`} disabled={Boolean(busy)} onClick={()=>void createIncident(item)}>Create incident</button>:tracked.has(Number(item.id))?'Tracked':'—'])}/></Panel>
    <Panel title="Incident management" subtitle="Disable intake → reroute users → notify Host/Company → track repair" icon={AlertTriangle}><DataTable headers={['Incident','Station','Fault','Impact','Downtime','Status','Control']} rows={snapshot.incidents.map(item=>[<div key={`inc-${item.id}`}><strong>{str(item.incidentCode)}</strong><small>{str(item.severity)}</small></div>,str(item.stationName),str(item.description),`${str(item.affectedBookings)} affected · ${str(item.usersRerouted)} rerouted · ${str(item.manualInterventions)} manual`,`${str(item.estimatedDowntimeMinutes)} min`,<Status key={`inc-status-${item.id}`} value={str(item.status)}/>,str(item.status)==='RESOLVED'?'Closed':<span key={`inc-action-${item.id}`} className="admin-table-actions"><button disabled={Boolean(busy)} onClick={()=>void updateIncident(item,'ESCALATED')}>Escalate</button><button disabled={Boolean(busy)} onClick={()=>void updateIncident(item,'RESOLVED')}>Resolve</button></span>])}/></Panel>
    <Panel title="Maintenance compliance" subtitle="Companies repair equipment; Admin monitors response and downtime" icon={Wrench}><DataTable headers={['Work order','Company','Charger','Issue','Open time','Owner','Status']} rows={snapshot.maintenanceTickets.map(item=>[`#${str(item.id)}`,`#${str(item.companyId)}`,`${str(item.chargerCode)} · ${str(item.stationName)}`,str(item.issue),`${str(item.openMinutes)} min`,str(item.assignedTo)||'Unassigned',<Status key={`maintenance-${item.id}`} value={str(item.status)}/>])}/></Panel>
    <Panel title="Live charging sessions" subtitle="Investigate stuck charging, billing mismatch and physical occupancy" icon={Activity}><DataTable headers={['Session','Station / charger','Vehicle','Battery','Power / energy','Cost / payment','ETA']} rows={filterRows(snapshot.activeSessions,search).map(i=>[`#${str(i.id)}`,`${str(i.station)} · ${str(i.chargerCode)||`#${str(i.connectorId)}`}`,`Vehicle #${str(i.vehicleId)}`,str(i.battery),`${str(i.powerKw)} kW · ${str(i.energyKwh)} kWh`,`${money(Number(i.cost))} · ${str(i.paymentStatus)}`,date(str(i.estimatedCompletionAt))])}/></Panel>
    <Panel title="Stations & ownership" subtitle="Property ownership, operator and charger verification stay distinct" icon={Store}><DataTable headers={['Station','Site type','Property owner','Operator','Verification','Load']} rows={filterRows(snapshot.stations,search).map(i=>[`${str(i.name)} · ${str(i.city)}`,str(i.ownershipType).replaceAll('_',' '),str(i.propertyOwner)||`Account #${str(i.propertyOwnerAccountId)}`,str(i.operatorCompany)||'Independent operator',`${str(i.verificationStage)} · ${i.siteEvidence?'Site ✓':'Site ?'} · ${i.chargersVerified?'Chargers ✓':'Chargers ?'}`,`${str(i.occupancy)}% · queue ${str(i.queue)}`])}/></Panel>
  </div>;
}

function MarketplaceControl({snapshot,search}:{snapshot:Snapshot;search:string;busy:string;action:AdminAction}) {
  const active=snapshot.installations.filter(item=>!['DECLINED','CANCELLED','EXPIRED'].includes(str(item.status)));
  return <div className="admin-stack"><div className="admin-metrics"><Metric icon={LandPlot} label="Listed properties" value={snapshot.properties.filter(item=>item.discoverable).length}/><Metric icon={Building2} label="Companies interested" value={new Set(snapshot.installations.map(item=>item.companyId)).size}/><Metric icon={Handshake} label="Active partnerships" value={active.length}/><Metric icon={Wrench} label="Installing" value={snapshot.installations.filter(item=>['INSTALLATION_SCHEDULED','INSTALLING','INSTALLED','COMMISSIONED'].includes(str(item.status))).length}/></div><Panel title="Property marketplace monitoring" subtitle="Admin governs safety; Hosts and Companies negotiate terms" icon={LandPlot}><DataTable headers={['Property','Host','Location','Score','Verification','Marketplace']} rows={filterRows(snapshot.properties,search).map(item=>[str(item.title),`Host #${str(item.hostAccountId)}`,`${str(item.city)}, ${str(item.state)}`,str(item.propertyScore)||'—',<Status key={`property-${item.id}`} value={str(item.verificationStage)}/>,item.discoverable?'Discoverable':'Hidden'])}/></Panel><Panel title="Host–Company partnerships and offers" subtitle="Commercial decisions remain with the Host and Company" icon={Handshake}><DataTable headers={['Property / Host','Company','Offer','Revenue split','Dates','Stage']} rows={filterRows(snapshot.installations,search).map(item=>[<div key={`partner-${item.id}`}><strong>{str(item.property)}</strong><small>{str(item.host)}</small></div>,str(item.company),`${str(item.quantity)} × ${str(item.product)} · ${str(item.businessModel)} · ${money(Number(item.equipmentTotal)+Number(item.installationTotal))}`,`${str(item.hostRevenueShare)||'—'}% Host · ${str(item.companyRevenueShare)||'—'}% Company`,`${str(item.surveyAt)||'Survey TBD'} · ${str(item.installationAt)||'Install TBD'}`,<Status key={`installation-${item.id}`} value={str(item.status)}/>])}/></Panel></div>;
}

function FinanceView({ snapshot,busy,action }: {snapshot:Snapshot;busy:string;action:AdminAction}) {
  const update=(item:Record<string,unknown>,status:string)=>action(`settlement-${item.paymentId}-${status}`,`/admin/portal/settlements/${item.paymentId}`,{method:'PATCH',body:JSON.stringify({status,note:status==='DISPUTED'?'Settlement held for evidence review.':'Settlement shares reviewed and processed.'})},`Settlement moved to ${status.toLowerCase()}.`);
  return <div className="admin-stack"><Panel title="Payments & refunds" subtitle="Refunds are consequential and recorded in the audit trail" icon={CreditCard}><DataTable headers={['Payment','Booking','Station','Amount','Transaction','Status','Action']} rows={snapshot.payments.map(i=>[`#${str(i.id)}`,`#${str(i.bookingId)}`,str(i.station),money(Number(i.amount)),str(i.transaction),<Status key={`payment-status-${i.id}`} value={str(i.status)}/>,i.status==='SUCCESS'?<button key={`refund-${i.id}`} disabled={Boolean(busy)} onClick={()=>void action(`refund-${i.id}`,`/admin/portal/payments/${i.id}/refund`,{method:'PATCH',body:JSON.stringify({note:'Admin-approved refund'})},'Payment refunded.')}>Refund</button>:'—'])}/></Panel><Panel title="Settlement ledger" subtitle="Company-owned sites never receive a Host share" icon={BadgeIndianRupee}><DataTable headers={['Payment / station','Ownership','Gross','Platform','Company','Host','Status','Control']} rows={snapshot.settlements.map(item=>[<div key={`settlement-${item.paymentId}`}><strong>Payment #{str(item.paymentId)}</strong><small>{str(item.stationName)}</small></div>,str(item.ownershipType).replaceAll('_',' '),money(Number(item.grossAmount)),money(Number(item.platformAmount)),money(Number(item.companyAmount)),Number(item.hostAmount)>0?money(Number(item.hostAmount)):'Not applicable',<Status key={`settle-${item.paymentId}`} value={str(item.status)}/>,<span key={`settle-actions-${item.paymentId}`} className="admin-table-actions"><button disabled={Boolean(busy)} onClick={()=>void update(item,'DISPUTED')}>Hold/dispute</button><button disabled={Boolean(busy)||str(item.status)==='PAID'} onClick={()=>void update(item,'PAID')}>Mark paid</button></span>])}/></Panel></div>;
}

function RevenueView({ snapshot, search }: { snapshot: Snapshot; search: string }) {
  const payments = snapshot.payments;
  const paymentStatus = (item: Record<string, unknown>) => str(item.status).toUpperCase();
  const sum = (items: Array<Record<string, unknown>>) => items.reduce((total, item) => total + (Number(item.amount) || 0), 0);
  const successful = payments.filter(item => paymentStatus(item) === 'SUCCESS');
  const refunded = payments.filter(item => paymentStatus(item) === 'REFUNDED');
  const captured = payments.filter(item => ['SUCCESS', 'REFUNDED'].includes(paymentStatus(item)));
  const decided = payments.filter(item => ['SUCCESS', 'REFUNDED', 'FAILED'].includes(paymentStatus(item)));
  const activeBookings = snapshot.bookings.filter(item => ['PENDING', 'CONFIRMED', 'IN_PROGRESS'].includes(str(item.status).toUpperCase()));
  const grossCollections = sum(captured);
  const refunds = sum(refunded);
  const netCollections = sum(successful);
  const confirmedPipeline = sum(activeBookings);
  const pendingPayments = sum(payments.filter(item => paymentStatus(item) === 'PENDING'));
  const averageTransaction = successful.length ? netCollections / successful.length : 0;
  const captureRate = decided.length ? (captured.length / decided.length) * 100 : 0;

  const today = new Date();
  const days = Array.from({ length: 7 }, (_, index) => {
    const value = new Date(today);
    value.setHours(0, 0, 0, 0);
    value.setDate(today.getDate() - (6 - index));
    const key = dayKey(value);
    const amount = sum(successful.filter(item => dayKey(new Date(str(item.timestamp))) === key));
    return { key, label: value.toLocaleDateString('en-IN', { weekday: 'short' }), amount };
  });
  const dailyPeak = Math.max(0, ...days.map(item => item.amount));
  const dailyScale = Math.max(1, dailyPeak);

  const outcomes = ['SUCCESS', 'REFUNDED', 'PENDING', 'FAILED'].map(status => {
    const count = payments.filter(item => paymentStatus(item) === status).length;
    return { status, count, share: payments.length ? (count / payments.length) * 100 : 0 };
  });

  const stationRevenue = new Map<string, { amount: number; transactions: number }>();
  successful.forEach(item => {
    const station = str(item.station) || 'Unlinked payment';
    const current = stationRevenue.get(station) || { amount: 0, transactions: 0 };
    stationRevenue.set(station, { amount: current.amount + (Number(item.amount) || 0), transactions: current.transactions + 1 });
  });
  const stationRankings = Array.from(stationRevenue.entries())
    .map(([station, value]) => ({ station, ...value }))
    .sort((a, b) => b.amount - a.amount);
  const topStations = stationRankings.slice(0, 5);
  const topStationAmount = Math.max(1, ...topStations.map(item => item.amount));
  const chartColors = ['#0a9169', '#35c994', '#3d83df', '#8b69d6', '#e3a13a'];
  const leadingSources = stationRankings.slice(0, 4);
  const otherRevenue = stationRankings.slice(4).reduce((total, item) => total + item.amount, 0);
  const revenueSources = [
    ...leadingSources.map((item, index) => ({ label: item.station, value: item.amount, color: chartColors[index] })),
    ...(otherRevenue ? [{ label: 'Other stations', value: otherRevenue, color: chartColors[4] }] : []),
  ];
  const outcomeSegments = outcomes.map((item, index) => ({ ...item, label: item.status.replaceAll('_', ' '), value: item.count, color: chartColors[index] }));

  const pipelineByStation = new Map<string, { amount: number; bookings: number }>();
  activeBookings.forEach(item => {
    const station = str(item.station) || 'Unassigned station';
    const current = pipelineByStation.get(station) || { amount: 0, bookings: 0 };
    pipelineByStation.set(station, { amount: current.amount + (Number(item.amount) || 0), bookings: current.bookings + 1 });
  });
  const opportunities = Array.from(pipelineByStation.entries())
    .map(([station, value]) => ({ station, ...value, collected: stationRevenue.get(station)?.amount || 0 }))
    .sort((a, b) => b.amount - a.amount)
    .slice(0, 5);
  const topOpportunity = Math.max(1, ...opportunities.map(item => item.amount));
  const stationMeta=new Map(snapshot.stations.map(item=>[str(item.name),item]));
  const aggregate=(key:'city'|'operatorCompany')=>Array.from(stationRankings.reduce((map,item)=>{const meta=stationMeta.get(item.station);const label=str(meta?.[key])||'Unassigned';map.set(label,(map.get(label)||0)+item.amount);return map;},new Map<string,number>()).entries()).sort((a,b)=>b[1]-a[1]);
  const cityRevenue=aggregate('city');
  const companyRevenue=aggregate('operatorCompany');
  const averageOccupancy=snapshot.stations.length?snapshot.stations.reduce((sum,item)=>sum+Number(item.occupancy||0),0)/snapshot.stations.length:0;
  const mostUsed=snapshot.connectors.slice().sort((a,b)=>Number(b.sessionEnergyKwh||0)-Number(a.sessionEnergyKwh||0))[0];

  const ledger = filterRows(payments, search)
    .slice()
    .sort((a, b) => new Date(str(b.timestamp)).getTime() - new Date(str(a.timestamp)).getTime())
    .slice(0, 20);

  return <div className="admin-stack admin-revenue">
    <section className="admin-revenue-hero">
      <div className="admin-revenue-hero-copy">
        <span><Activity size={14}/> LIVE COLLECTION SIGNAL</span>
        <h2>Know what the network earned, retained and expects next.</h2>
        <p>Collections are calculated directly from payment and booking records. No assumed commission or synthetic forecast is included.</p>
      </div>
      <div className="admin-revenue-hero-facts">
        <div><small>Captured transactions</small><strong>{captured.length}</strong><span>{payments.length} total payment attempts</span></div>
        <div><small>Confirmed pipeline</small><strong>{money(confirmedPipeline)}</strong><span>{activeBookings.length} active bookings</span></div>
      </div>
    </section>

    <div className="admin-revenue-kpis">
      <RevenueKpi icon={BadgeIndianRupee} label="Gross collections" value={money(grossCollections)} detail="Successful + later-refunded captures" tone="green"/>
      <RevenueKpi icon={Activity} label="Net collections" value={money(netCollections)} detail="Successful payments retained" tone="blue"/>
      <RevenueKpi icon={RefreshCw} label="Refunded value" value={money(refunds)} detail={`${refunded.length} refunded transaction${refunded.length === 1 ? '' : 's'}`} tone="amber"/>
      <RevenueKpi icon={CreditCard} label="Capture rate" value={`${captureRate.toFixed(1)}%`} detail={`${captured.length} of ${decided.length} decided attempts`} tone="violet"/>
    </div>

    <div className="admin-revenue-grid">
      <Panel title="Net collections · last 7 days" subtitle="Daily value from successful payments" icon={Activity}>
        <div className="admin-revenue-chart">
          <div className="admin-revenue-axis"><span>{money(dailyPeak)}</span><span>{money(dailyPeak / 2)}</span><span>{money(0)}</span></div>
          <div className="admin-revenue-bars">{days.map(item => <div className="admin-revenue-bar" key={item.key} title={`${item.label}: ${money(item.amount)}`}><strong>{item.amount ? money(item.amount) : ''}</strong><div><i style={{ height: `${item.amount ? Math.max(8, (item.amount / dailyScale) * 100) : 2}%` }}/></div><span>{item.label}</span></div>)}</div>
        </div>
      </Panel>
      <Panel title="Where revenue comes from" subtitle="Share of retained collections by station" icon={Store}>
        <DonutChart segments={revenueSources} centerValue={money(netCollections)} centerLabel="net collected" formatValue={money} emptyText="Successful payments will reveal the station revenue mix."/>
      </Panel>
    </div>

    <div className="admin-revenue-grid secondary">
      <Panel title="Where revenue can grow" subtitle="Opportunity ranked by real active-booking value" icon={LandPlot}>
        {opportunities.length ? <div className="admin-opportunity-list">{opportunities.map((item, index) => {
          const signal = item.collected === 0 ? 'Untapped demand' : item.amount > item.collected * .5 ? 'High near-term demand' : 'Repeat demand';
          return <article key={item.station}><div className="admin-opportunity-rank">0{index + 1}</div><div className="admin-opportunity-copy"><div><span><strong>{item.station}</strong><small>{item.bookings} active booking{item.bookings === 1 ? '' : 's'} · {money(item.amount)} pipeline</small></span><em>{signal}</em></div><i><b style={{ width: `${(item.amount / topOpportunity) * 100}%` }}/></i><p>{item.collected ? `${money(item.collected)} already collected here; convert the active demand and protect availability.` : 'Active demand exists without recorded successful collections; prioritize payment conversion and charger availability.'}</p></div></article>;
        })}</div> : <Empty text="Active bookings will identify the next revenue opportunities."/>}
      </Panel>
      <Panel title="Payment outcome mix" subtitle="Conversion and collection risk by attempt" icon={CreditCard}>
        <DonutChart segments={outcomeSegments} centerValue={`${payments.length}`} centerLabel="payment attempts" formatValue={value => `${value}`} emptyText="Payment outcomes will appear after the first attempt."/>
      </Panel>
    </div>

    <div className="admin-revenue-grid secondary">
      <Panel title="Top earning stations" subtitle="Ranked by retained successful collections" icon={Store}>
        {topStations.length ? <div className="admin-station-revenue">{topStations.map((item, index) => <article key={item.station}><b>{index + 1}</b><div><span><strong>{item.station}</strong><small>{item.transactions} transaction{item.transactions === 1 ? '' : 's'}</small></span><em>{money(item.amount)}</em><i><u style={{ width: `${(item.amount / topStationAmount) * 100}%` }}/></i></div></article>)}</div> : <Empty text="Station earnings will appear after the first successful payment."/>}
      </Panel>
      <Panel title="Collection health" subtitle="Operational indicators behind the headline value" icon={Gauge}>
        <div className="admin-revenue-brief">
          <article><span>Active booking pipeline<small>Pending, confirmed and in-progress bookings</small></span><strong>{money(confirmedPipeline)}</strong></article>
          <article><span>Pending payment value<small>Started attempts not yet resolved</small></span><strong>{money(pendingPayments)}</strong></article>
          <article><span>Average successful transaction<small>Net collections per successful payment</small></span><strong>{money(averageTransaction)}</strong></article>
          <article><span>Refund exposure<small>Share of gross captured value returned</small></span><strong>{grossCollections ? `${((refunds / grossCollections) * 100).toFixed(1)}%` : '0.0%'}</strong></article>
        </div>
      </Panel>
    </div>

    <div className="admin-revenue-grid secondary">
      <Panel title="Revenue by company" subtitle="Retained collections attributed through station operator" icon={Building2}><DataTable headers={['Company','Revenue']} rows={companyRevenue.slice(0,8).map(([label,value])=>[label,money(value)])}/></Panel>
      <Panel title="Revenue by city" subtitle="Geographic collection performance" icon={MapPin}><DataTable headers={['City','Revenue']} rows={cityRevenue.slice(0,8).map(([label,value])=>[label,money(value)])}/></Panel>
    </div>
    <div className="admin-revenue-brief"><article><span>Average station occupancy<small>Current network-wide mean</small></span><strong>{averageOccupancy.toFixed(1)}%</strong></article><article><span>Most used charger<small>Based on current session energy telemetry</small></span><strong>{mostUsed?str(mostUsed.chargerCode):'—'}</strong></article><article><span>Active charging sessions<small>Live backend session records</small></span><strong>{snapshot.activeSessions.length}</strong></article><article><span>Average transaction value<small>Successful payments</small></span><strong>{money(averageTransaction)}</strong></article></div>

    <Panel title="Revenue ledger" subtitle="Latest payment records; use the global search to filter this table" icon={BookOpenCheck}>
      <DataTable headers={['Time','Payment','Station','Booking','Amount','Status']} rows={ledger.map(item => [date(str(item.timestamp)), `#${str(item.id)}`, str(item.station) || 'Unlinked payment', `#${str(item.bookingId)}`, money(Number(item.amount)), <Status key={`ledger-status-${item.id}`} value={str(item.status)}/>])}/>
    </Panel>
  </div>;
}

function DonutChart({ segments, centerValue, centerLabel, formatValue, emptyText }: { segments: Array<{ label: string; value: number; color: string }>; centerValue: string; centerLabel: string; formatValue: (value: number) => string; emptyText: string }) {
  const visible = segments.filter(item => item.value > 0);
  const total = visible.reduce((sum, item) => sum + item.value, 0);
  let offset = 0;
  const arcs = visible.map(item => {
    const share = total ? (item.value / total) * 100 : 0;
    const arc = { ...item, share, offset };
    offset += share;
    return arc;
  });
  return <div className="admin-donut-layout">
    <div className="admin-donut">
      <svg viewBox="0 0 120 120" role="img" aria-label={`${centerLabel}: ${centerValue}`}>
        <circle className="admin-donut-track" cx="60" cy="60" r="45" pathLength="100"/>
        {arcs.map(item => <circle key={item.label} className="admin-donut-arc" cx="60" cy="60" r="45" pathLength="100" stroke={item.color} strokeDasharray={`${item.share} ${100 - item.share}`} strokeDashoffset={-item.offset}/>) }
      </svg>
      <div><strong>{centerValue}</strong><span>{centerLabel}</span></div>
    </div>
    <div className="admin-donut-legend">{arcs.length ? arcs.map(item => <article key={item.label}><i style={{ background: item.color }}/><span><strong>{item.label}</strong><small>{formatValue(item.value)}</small></span><em>{item.share.toFixed(0)}%</em></article>) : <p><CircleAlert size={17}/>{emptyText}</p>}</div>
  </div>;
}

function RevenueKpi({ icon: Icon, label, value, detail, tone }: { icon: typeof Gauge; label: string; value: string; detail: string; tone: string }) {
  return <article className={`admin-revenue-kpi ${tone}`}><span><Icon size={19}/></span><div><small>{label}</small><strong>{value}</strong><p>{detail}</p></div></article>;
}

function SupportView({ snapshot,busy,action }: {snapshot:Snapshot;busy:string;action:AdminAction}) {
  const [form,setForm]=useState({title:'',message:'',audience:'ALL',severity:'INFO',targetState:'',targetCity:'',targetAccountId:''});
  const updateCase=(item:Record<string,unknown>,status:string)=>action(`case-${item.id}-${status}`,`/admin/portal/support-cases/${item.id}`,{method:'PATCH',body:JSON.stringify({status,note:status==='EVIDENCE_REQUESTED'?'Please attach evidence to the case.':status==='ESCALATED'?'Escalated to the responsible specialist.':'Case resolved after review.'})},`Support case moved to ${status.toLowerCase()}.`);
  return <div className="admin-stack"><Panel title="Central support & disputes" subtitle="User, Host and Company cases with evidence and escalation controls" icon={MessageSquare}><DataTable headers={['Case','Account','Issue','Priority','Status','Control']} rows={snapshot.supportCases.map(item=>[<div key={`case-${item.id}`}><strong>#{str(item.id)} · {str(item.subject)}</strong><small>{str(item.category)}</small></div>,`${str(item.accountType)} #${str(item.accountId)}`,str(item.description),<Status key={`case-priority-${item.id}`} value={str(item.priority)}/>,<Status key={`case-status-${item.id}`} value={str(item.status)}/>,['RESOLVED','CLOSED'].includes(str(item.status))?'Closed':<span key={`case-actions-${item.id}`} className="admin-table-actions"><button disabled={Boolean(busy)} onClick={()=>void updateCase(item,'EVIDENCE_REQUESTED')}>Evidence</button><button disabled={Boolean(busy)} onClick={()=>void updateCase(item,'ESCALATED')}>Escalate</button><button disabled={Boolean(busy)} onClick={()=>void updateCase(item,'RESOLVED')}>Resolve</button></span>])}/></Panel><Panel title="Publish targeted notice" subtitle="Target an account partition, geography or one account" icon={BellRing}><form className="admin-inline-form notice expanded" onSubmit={e=>{e.preventDefault();void action('announcement','/admin/portal/announcements',{method:'POST',body:JSON.stringify({...form,targetAccountId:form.targetAccountId?Number(form.targetAccountId):null})},'Announcement published.')}}><input placeholder="Notice title" value={form.title} onChange={e=>setForm({...form,title:e.target.value})} required/><input placeholder="Message" value={form.message} onChange={e=>setForm({...form,message:e.target.value})} required/><select value={form.audience} onChange={e=>setForm({...form,audience:e.target.value})}><option>ALL</option><option>EV_OWNER</option><option>HOST</option><option>COMPANY</option></select><select value={form.severity} onChange={e=>setForm({...form,severity:e.target.value})}><option>INFO</option><option>WARNING</option><option>CRITICAL</option></select><input placeholder="State (optional)" value={form.targetState} onChange={e=>setForm({...form,targetState:e.target.value})}/><input placeholder="City (optional)" value={form.targetCity} onChange={e=>setForm({...form,targetCity:e.target.value})}/><input placeholder="Account ID (optional)" inputMode="numeric" value={form.targetAccountId} onChange={e=>setForm({...form,targetAccountId:e.target.value})}/><button disabled={Boolean(busy)}>Publish</button></form></Panel><Panel title="Booking control" subtitle="Upcoming, active, completed, cancelled, rerouted and failed records remain auditable" icon={CalendarClock}><DataTable headers={['Booking','Station','User','Start','Amount','Status','Action']} rows={snapshot.bookings.map(i=>[`#${str(i.id)}`,str(i.station),`#${str(i.userId)}`,date(str(i.startTime)),money(Number(i.amount)),<Status key={`booking-status-${i.id}`} value={str(i.status)}/>,!['COMPLETED','CANCELLED'].includes(str(i.status))?<button key={`cancel-booking-${i.id}`} className="table-danger" disabled={Boolean(busy)} onClick={()=>void action(`booking-${i.id}`,`/admin/portal/bookings/${i.id}/cancel`,{method:'PATCH',body:JSON.stringify({note:'Cancelled by support after intervention review'})},'Booking cancelled.')}>Cancel</button>:'—'])}/></Panel></div>;
}

function GreenAssistanceView({snapshot,busy,action}:{snapshot:Snapshot;busy:string;action:AdminAction}) {
  const [form,setForm]=useState({name:'',authority:'',schemeType:'EV_CHARGING_INCENTIVE',states:'',sourceUrl:'',summary:'',status:'DRAFT',validFrom:'',validUntil:''});
  return <div className="admin-stack"><Panel title="Approved scheme and finance sources" subtitle="Host Assistant reads only ACTIVE records maintained here" icon={Leaf}><form className="admin-scheme-form" onSubmit={event=>{event.preventDefault();void action('green-scheme','/admin/portal/green-schemes',{method:'POST',body:JSON.stringify({...form,validFrom:form.validFrom||null,validUntil:form.validUntil||null})},'Green-finance source saved.')}}><input placeholder="Scheme or finance source" value={form.name} onChange={e=>setForm({...form,name:e.target.value})} required/><input placeholder="Authority / provider" value={form.authority} onChange={e=>setForm({...form,authority:e.target.value})} required/><select value={form.schemeType} onChange={e=>setForm({...form,schemeType:e.target.value})}><option>EV_CHARGING_INCENTIVE</option><option>STATE_POLICY</option><option>SOLAR</option><option>RESCO_PPA</option><option>LOAN</option><option>INFRASTRUCTURE_SUPPORT</option></select><input placeholder="Applicable states" value={form.states} onChange={e=>setForm({...form,states:e.target.value})}/><input placeholder="Official source URL" type="url" value={form.sourceUrl} onChange={e=>setForm({...form,sourceUrl:e.target.value})} required/><input placeholder="Eligibility summary" value={form.summary} onChange={e=>setForm({...form,summary:e.target.value})} required/><select value={form.status} onChange={e=>setForm({...form,status:e.target.value})}><option>DRAFT</option><option>ACTIVE</option><option>SUSPENDED</option><option>EXPIRED</option></select><input type="date" value={form.validFrom} onChange={e=>setForm({...form,validFrom:e.target.value})}/><input type="date" value={form.validUntil} onChange={e=>setForm({...form,validUntil:e.target.value})}/><button disabled={Boolean(busy)}>Save source</button></form><DataTable headers={['Source','Type / states','Validity','Status','Host Assistant']} rows={snapshot.greenSchemes.map(item=>[<div key={`scheme-${item.id}`}><strong>{str(item.name)}</strong><small>{str(item.authority)} · <a href={str(item.sourceUrl)} target="_blank" rel="noreferrer">official source</a></small></div>,`${str(item.schemeType)} · ${str(item.states)||'All configured areas'}`,`${str(item.validFrom)||'—'} → ${str(item.validUntil)||'—'}`,<Status key={`scheme-status-${item.id}`} value={str(item.status)}/>,str(item.status)==='ACTIVE'?'Available for eligibility analysis':'Not used'])}/></Panel><p className="admin-policy-note"><ShieldCheck size={16}/> Admin maintains verified sources. The Host Assistant analyzes eligibility but never guarantees or submits a subsidy without Host approval.</p></div>;
}

function NetworkView({ snapshot,queryAgent }: {snapshot:Snapshot;queryAgent:(question:string)=>Promise<AgentResponse>}) {
  const [question,setQuestion]=useState("What's wrong in the network?");
  const [answer,setAnswer]=useState<AgentResponse|null>(null);
  const [loading,setLoading]=useState(false);
  const [error,setError]=useState('');
  const ask=async()=>{if(!question.trim())return;setLoading(true);setError('');try{setAnswer(await queryAgent(question));}catch(reason){setError(reason instanceof Error?reason.message:'Admin Agent could not answer.');}finally{setLoading(false);}};
  return <div className="admin-stack"><div className="admin-metrics"><Metric icon={Bot} label="Trips monitored" value={snapshot.autopilotTrips.length}/><Metric icon={Network} label="Route memories" value={snapshot.networkSuggestions.length}/><Metric icon={AlertTriangle} label="Active incidents" value={snapshot.incidents.filter(item=>str(item.status)!=='RESOLVED').length}/><Metric icon={CircleAlert} label="Manual intervention" value={snapshot.incidents.reduce((sum,item)=>sum+Number(item.manualInterventions||0),0)}/></div><Panel title="Admin Agent" subtitle="Reads backend truth, explains network state and proposes approval-gated actions" icon={Bot}><form className="admin-agent-form" onSubmit={event=>{event.preventDefault();void ask();}}><textarea value={question} onChange={event=>setQuestion(event.target.value)} placeholder="Ask about faults, occupancy, downtime, payments or settlements"/><button disabled={loading}><Send size={16}/>{loading?'Checking backend…':'Ask Admin Agent'}</button></form><div className="admin-agent-prompts"><button onClick={()=>setQuestion("What's wrong in the network?")}>Network problems</button><button onClick={()=>setQuestion('Show stations with >90% occupancy')}>Peak occupancy</button><button onClick={()=>setQuestion('Which company has the most maintenance downtime?')}>Company downtime</button><button onClick={()=>setQuestion('Show failed payments and settlement disputes')}>Finance exceptions</button></div>{error&&<div className="admin-alert error"><CircleAlert size={16}/>{error}</div>}{answer&&<div className="admin-agent-answer"><header><Bot size={20}/><div><strong>{answer.answer}</strong><small>Source of truth: {answer.sourceOfTruth.replaceAll('_',' ')}</small></div></header><div>{answer.findings.map((item,index)=><article key={index}><Status value={str(item.severity)||'INFO'}/><span><strong>{str(item.label)}</strong><small>{str(item.detail)}</small></span></article>)}</div>{answer.suggestedActions.length>0&&<p><ShieldCheck size={15}/> Suggested actions require an authorized Admin confirmation in the relevant control view.</p>}</div>}</Panel><Panel title="User rerouting supervision" subtitle="User Agent reroutes; Admin records automatic success or manual intervention" icon={Network}><DataTable headers={['Trip','Route','Autonomy','Status','Cost','Supervision']} rows={snapshot.autopilotTrips.map(item=>[`#${str(item.id)}`,`${str(item.origin)} → ${str(item.destination)}`,str(item.mode),<Status key={`trip-${item.id}`} value={str(item.status)}/>,money(Number(item.cost)),str(item.status)==='REROUTED'?'Automatic rerouting successful ✓':str(item.status)==='REPLAN_REQUIRED'?'Manual intervention required ⚠':'Monitored'])}/></Panel><Panel title="Route experience memory" subtitle="Past outcomes guide placement and future trip decisions" icon={Network}><div className="admin-memory-list">{snapshot.networkSuggestions.map(i=><article key={str(i.id)}><span><Network size={17}/></span><div><strong>{str(i.route)}</strong><p>{str(i.detail)||'No narrative supplied.'}</p><small>{str(i.outcome)} · station {str(i.stationId)||'route-wide'} · {date(str(i.createdAt))}</small></div>{i.delayMinutes&&<b>+{str(i.delayMinutes)} min</b>}</article>)}{!snapshot.networkSuggestions.length&&<Empty text="Route experiences will appear after Autopilot trips."/>}</div></Panel></div>;
}

function AuditView({items,search}:{items:Audit[];search:string}) { const filtered=items.filter(i=>JSON.stringify(i).toLowerCase().includes(search.toLowerCase()));return <Panel title="Immutable operational activity" subtitle="Human and agent actions with previous state, new state, timestamp and reason" icon={FileCheck2}><DataTable headers={['Time','Actor','Action','Resource','Previous → new','Reason']} rows={filtered.map(i=>[date(i.createdAt),i.adminAccountId===0?'SYSTEM AGENT':`Admin #${i.adminAccountId}`,i.action.replaceAll('_',' '),`${i.resourceType} ${i.resourceId||''}`,i.previousValue||i.newValue?`${i.previousValue||'—'} → ${i.newValue||'—'}`:'Recorded',i.reason||i.summary])}/></Panel> }

function ReviewList({title,icon:Icon,items,nameKey,statusKey,detailKey,evidenceKey,evidenceKeys,busy,onReview}:{title:string;icon:typeof Gauge;items:Array<Record<string,unknown>>;nameKey:string;statusKey:string;detailKey:string;evidenceKey?:string;evidenceKeys?:Array<{key:string;label:string}>;busy:string;onReview:(item:Record<string,unknown>,approved:boolean)=>Promise<void>}) { return <Panel title={title} subtitle="Evidence remains private until a trust decision" icon={Icon}>{items.length?<div className="admin-simple-list">{items.map((item,index)=><article key={str(item.id??item.accountId??index)}><div><strong>{str(item[nameKey])}</strong><span>{str(item[detailKey])}</span>{evidenceKey&&<EvidenceLink url={str(item[evidenceKey])}/>} {evidenceKeys?.map(evidence=><EvidenceLink key={evidence.key} label={evidence.label} url={str(item[evidence.key])}/>)}</div><Status value={str(item[statusKey])}/><section><button className="reject" disabled={Boolean(busy)} onClick={()=>void onReview(item,false)}>Reject</button><button disabled={Boolean(busy)} onClick={()=>void onReview(item,true)}>Approve</button></section></article>)}</div>:<Empty text="No records are waiting."/>}</Panel> }
function EvidenceLink({url,label="View submitted evidence"}:{url:string;label?:string}) { return /^https?:\/\//i.test(url)?<a className="admin-evidence-link" href={url} target="_blank" rel="noreferrer">{label}</a>:<em className="admin-evidence-missing">{label} missing</em> }
function Panel({title,subtitle,icon:Icon,children}:{title:string;subtitle:string;icon:typeof Gauge;children:React.ReactNode}) { return <section className="admin-panel"><header><span><Icon size={19}/></span><div><h2>{title}</h2><p>{subtitle}</p></div></header>{children}</section> }
function DataTable({headers,rows}:{headers:string[];rows:React.ReactNode[][]}) { return rows.length?<div className="admin-table-wrap"><table><thead><tr>{headers.map(h=><th key={h}>{h}</th>)}</tr></thead><tbody>{rows.map((row,i)=><tr key={i}>{row.map((cell,j)=><td key={j}>{cell}</td>)}</tr>)}</tbody></table></div>:<Empty text="No records in this view."/> }
function Metric({icon:Icon,label,value}:{icon:typeof Gauge;label:string;value?:number}) { return <article><span><Icon size={20}/></span><div><strong>{value??0}</strong><small>{label}</small></div></article> }
function Attention({label,value,tone}:{label:string;value:number;tone:string}) { return <div className={tone}><span>{label}</span><strong>{value}</strong></div> }
function Layer({label,ok,detail}:{label:string;ok:boolean;detail?:string}) { return <div className={ok?'ok':''}><span>{ok?<Check size={15}/>:<CircleAlert size={15}/>}</span><div><strong>{label}</strong><small>{detail?'Evidence attached':'Awaiting review'}</small></div></div> }
function Status({value}:{value:string}) { return <i className={`admin-status ${statusTone(value)}`}>{value.replaceAll('_',' ')}</i> }
function Empty({text}:{text:string}) { return <div className="admin-empty"><FileCheck2 size={24}/><span>{text}</span></div> }
function AdminLoading() { return <div className="admin-loading"><span/><span/><span/><p>Synchronizing protected control data…</p></div> }
function statusTone(value:string){if(/VERIFIED|APPROVED|ACTIVE|SUCCESS|COMPLETED|ONLINE|LIVE|RESOLVED|PAID|LOW/.test(value))return'ok';if(/REJECTED|SUSPENDED|FAILED|CANCELLED|FAULT|CRITICAL|HIGH|ESCALATED|MANUAL_INTERVENTION/.test(value))return'bad';return'pending'}
function initials(value:string){return value.split(/\s+/).slice(0,2).map(v=>v[0]).join('').toUpperCase()}
function str(value:unknown){if(value==null)return'';if(Array.isArray(value))return value.join(', ');return String(value)}
function date(value:string){return value?new Date(value).toLocaleString('en-IN',{day:'2-digit',month:'short',hour:'2-digit',minute:'2-digit'}):'—'}
function money(value:number){return new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:0}).format(value||0)}
function dayKey(value:Date){return Number.isNaN(value.getTime())?'':`${value.getFullYear()}-${String(value.getMonth()+1).padStart(2,'0')}-${String(value.getDate()).padStart(2,'0')}`}
function filterRows(items:Array<Record<string,unknown>>,search:string){const q=search.trim().toLowerCase();return q?items.filter(i=>JSON.stringify(i).toLowerCase().includes(q)):items}
