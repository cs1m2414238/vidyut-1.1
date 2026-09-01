import { useCallback, useEffect, useMemo, useState } from 'react';
import { BadgeIndianRupee, Building2, CheckCircle2, CircleAlert, FileCheck2, GraduationCap, RefreshCw, ShieldCheck, Upload } from 'lucide-react';
import { apiRequest } from '../services/api';
import { getStations } from '../services/stations';
import type { StationResponse } from '../services/stations';
import './OutletAccessView.css';

interface PricingTier { id: number; name: string; ratePerKwh: number; eligibility: string; eligibilityNote: string }
interface OutletTier { outletId: number; institutionName: string; tierName: string; ratePerKwh: number; reason: string; verificationStatus: string; idUploadRequired: boolean; pricing: PricingTier[] }
interface OutletStats { outletId: number; institutionName: string; sessions: number; totalSpend: number; savedVsVisitor: number }

export function OutletAccessView({ token, onFindChargers }: { token: string; onFindChargers: () => void }) {
  const [outlets, setOutlets] = useState<StationResponse[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [tier, setTier] = useState<OutletTier | null>(null);
  const [stats, setStats] = useState<OutletStats | null>(null);
  const [documentUri, setDocumentUri] = useState('');
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const auth = useMemo(() => ({ headers: { Authorization: `Bearer ${token}` } }), [token]);

  const loadOutlet = useCallback(async (id: number) => {
    try {
      const [tierData, statsData] = await Promise.all([
        apiRequest<OutletTier>(`/outlets/${id}/my-tier`, { method: 'GET', ...auth }),
        apiRequest<OutletStats>(`/outlets/${id}/my-stats`, { method: 'GET', ...auth }),
      ]);
      setTier(tierData); setStats(statsData); setSelectedId(id); setError('');
    } catch (loadError) { setError(loadError instanceof Error ? loadError.message : 'Unable to load outlet access.'); }
  }, [auth]);

  useEffect(() => {
    void getStations().then((items) => {
      const partnerOutlets = items.filter((station) => station.outletPartner);
      setOutlets(partnerOutlets);
      if (partnerOutlets[0]) return loadOutlet(partnerOutlets[0].id);
      return undefined;
    }).catch((loadError) => setError(loadError instanceof Error ? loadError.message : 'Unable to load outlet stations.')).finally(() => setLoading(false));
  }, [loadOutlet]);

  const submit = async () => {
    if (!selectedId || !documentUri.trim()) return;
    try {
      setWorking(true); setError('');
      await apiRequest('/users/verify-institution', { method: 'POST', ...auth, body: JSON.stringify({ outletId: selectedId, documentUri: documentUri.trim() }) });
      setNotice('Institution ID submitted for Vidyut review.'); setDocumentUri(''); await loadOutlet(selectedId);
    } catch (submitError) { setError(submitError instanceof Error ? submitError.message : 'Unable to submit institution verification.'); }
    finally { setWorking(false); }
  };

  const station = outlets.find((item) => item.id === selectedId);
  return <section className="outlet-access-page">
    <header><div><span className="section-eyebrow">INSTITUTIONAL CHARGING</span><h1>Outlet partner access</h1><p>Your email or verified institution ID automatically selects the correct campus, mall or workplace charging rate.</p></div><button onClick={onFindChargers}><RefreshCw size={15} /> Find chargers</button></header>
    {error && <div className="outlet-message error"><CircleAlert size={17} />{error}</div>}{notice && <div className="outlet-message"><CheckCircle2 size={17} />{notice}</div>}
    {loading && <div className="outlet-loading">Loading institutional access…</div>}
    {!loading && !outlets.length && <div className="outlet-empty"><Building2 size={31} /><h2>No outlet partner is published yet</h2><p>Verified institutional chargers appear here automatically.</p></div>}
    {outlets.length > 0 && <div className="outlet-layout"><aside>{outlets.map((outlet) => <button key={outlet.id} className={selectedId === outlet.id ? 'active' : ''} onClick={() => void loadOutlet(outlet.id)}><span><GraduationCap size={18} /></span><div><strong>{outlet.outletInstitutionName || outlet.name}</strong><small>{outlet.name} · {outlet.city || outlet.address}</small></div></button>)}</aside>{tier && <main>
      <article className="outlet-tier-hero"><div><span><ShieldCheck size={21} /></span><div><small>YOUR AUTOMATIC RATE</small><h2>{tier.tierName}</h2><p>{tier.reason}</p></div></div><strong>₹{tier.ratePerKwh.toFixed(2)}<small>/kWh</small></strong></article>
      <div className="outlet-stats"><span><strong>{stats?.sessions ?? 0}</strong><small>Completed sessions</small></span><span><strong>₹{(stats?.totalSpend ?? 0).toFixed(0)}</strong><small>Total spend here</small></span><span><strong>₹{(stats?.savedVsVisitor ?? 0).toFixed(0)}</strong><small>Saved vs visitor</small></span><span><strong>{station?.workingHours || 'Open hours vary'}</strong><small>Access window</small></span></div>
      <section className="outlet-pricing"><header><div><h2>Member pricing</h2><p>Your eligible tier is highlighted; booking applies it automatically.</p></div><BadgeIndianRupee size={21} /></header><div>{tier.pricing.map((item) => <article className={item.name === tier.tierName ? 'active' : ''} key={item.id}><span><strong>{item.name}</strong><small>{item.eligibilityNote}</small></span><b>₹{item.ratePerKwh.toFixed(2)}/kWh</b>{item.name === tier.tierName && <i>YOUR RATE</i>}</article>)}</div></section>
      {tier.idUploadRequired && <section className="outlet-verification"><span><FileCheck2 size={22} /></span><div><h2>Unlock an institution member rate</h2><p>Provide a secure document URL for the current demo. Admin approval activates the matched tier once.</p><label>Institution ID document URL<input value={documentUri} onChange={(event) => setDocumentUri(event.target.value)} placeholder="https://secure.example/id-document" /></label><button disabled={working || !documentUri.trim()} onClick={() => void submit()}><Upload size={15} />{working ? 'Submitting…' : 'Submit for verification'}</button></div></section>}
    </main>}</div>}
  </section>;
}
