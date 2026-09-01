import { X } from 'lucide-react';
import './CompanyChargerSearch.css';

export default function CompanyChargerSearch({ query, kind, count, city, loading, error, onClear, onRetry }: {
  query: string; kind: 'stations' | 'chargers'; count: number; city?: string;
  loading: boolean; error: string; onClear: () => void; onRetry: () => void;
}) {
  return <section className="company-network-search-status" aria-label="Company network search results" aria-busy={loading}>
    <span role="status">{loading ? `Searching your authorized ${kind}…` : error ? 'Search could not be completed' : `${count} ${count === 1 ? kind.slice(0, -1) : kind} found${query.trim() && city ? ` in ${city}` : ''}${query.trim() ? ` for “${query.trim()}”` : ' in your network'}`}</span>
    {query && <button type="button" onClick={onClear}><X size={14} /> Clear search</button>}
    {error && <><span role="alert">{error}</span><button type="button" onClick={onRetry}>Retry search</button></>}
  </section>;
}
