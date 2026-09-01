import { ArrowRight, Building2, CarFront, HousePlug, LogOut, ShieldCheck, Zap } from 'lucide-react';
import type { AccessMode } from '../services/api';

interface ModeSelectionProps {
  name: string;
  modes: AccessMode[];
  loadingMode?: AccessMode | null;
  error?: string;
  onSelect: (mode: AccessMode) => void;
  onLogout: () => void;
}

const modeMeta: Record<AccessMode, { title: string; description: string; icon: typeof CarFront }> = {
  EV_USER: {
    title: 'EV Owner',
    description: 'Find chargers, manage vehicles, bookings and wallet.',
    icon: CarFront,
  },
  HOST: {
    title: 'Charger Host',
    description: 'Manage your chargers, reservations, earnings and payouts.',
    icon: HousePlug,
  },
  COMPANY: {
    title: 'Company Network',
    description: 'Operate stations, chargers, teams and network analytics.',
    icon: Building2,
  },
  ADMIN: {
    title: 'Platform Admin',
    description: 'Review platform operations and protected approvals.',
    icon: ShieldCheck,
  },
};

export function ModeSelection({ name, modes, loadingMode, error, onSelect, onLogout }: ModeSelectionProps) {
  return (
    <main className="mode-selection-page">
      <div className="mode-selection-top"><Zap size={25} fill="#0f8f5d" color="#0f8f5d" /> VIDYUT</div>
      <section className="mode-selection-card" aria-labelledby="mode-title">
        <div className="mode-selection-kicker">Secure workspace</div>
        <h1 id="mode-title">Welcome, {name}. How would you like to continue?</h1>
        <p>
          Each workspace uses a mode-scoped access token, so information and actions stay inside the role you select.
        </p>

        <div className="mode-choice-grid">
          {modes.map((mode) => {
            const meta = modeMeta[mode];
            const Icon = meta.icon;
            const loading = loadingMode === mode;
            return (
              <button key={mode} className="mode-choice" onClick={() => onSelect(mode)} disabled={Boolean(loadingMode)}>
                <span className="mode-choice-icon"><Icon size={23} /></span>
                <span className="mode-choice-copy">
                  <span className="mode-choice-title">{loading ? 'Opening workspace…' : meta.title}</span>
                  <span className="mode-choice-desc">{meta.description}</span>
                </span>
                <ArrowRight size={18} color="#98a2b3" />
              </button>
            );
          })}
        </div>

        {error && <p className="mode-error" role="alert">{error}</p>}
        <button className="mode-choice" style={{ marginTop: 18, padding: 12 }} onClick={onLogout}>
          <span className="mode-choice-icon" style={{ width: 38, height: 38 }}><LogOut size={18} /></span>
          <span className="mode-choice-copy"><span className="mode-choice-title">Sign out</span></span>
        </button>
      </section>
    </main>
  );
}
