import { useEffect, useMemo, useState } from 'react';
import {
  ArrowRight,
  Building2,
  CarFront,
  Check,
  HousePlug,
  LockKeyhole,
  LogOut,
  Mail,
  Phone,
  Sparkles,
  User as UserIcon,
  X,
} from 'lucide-react';
import type { User } from '../types';
import { completeProfile } from '../services/api';
import type { AccessMode, AuthData, CompleteProfilePayload } from '../services/api';
import './CompleteProfileModal.css';

type ProfileMode = Exclude<AccessMode, 'ADMIN'>;

interface CompleteProfileModalProps {
  user: User;
  activeMode: AccessMode;
  authToken: string;
  onComplete: (updatedAuth: AuthData) => void;
  onSkip?: () => void;
  onCancel?: () => void;
  onLogout?: () => void;
}

const profilePlans: Record<ProfileMode, {
  label: string;
  eyebrow: string;
  title: string;
  description: string;
  icon: typeof CarFront;
  steps: string[];
}> = {
  EV_USER: {
    label: 'EV Owner',
    eyebrow: 'Personal charging profile',
    title: 'Make every charge feel effortless.',
    description: 'Your mobile number helps with booking updates, receipts and charger support.',
    icon: CarFront,
    steps: ['Add your vehicle and connector', 'Save charging preferences', 'Book nearby stations'],
  },
  HOST: {
    label: 'Charger Host',
    eyebrow: 'Host workspace profile',
    title: 'Turn your charger into a trusted destination.',
    description: 'Add a public-facing host name and mobile number now. Verification and payouts stay as clear next steps.',
    icon: HousePlug,
    steps: ['List your charging location', 'Complete identity verification', 'Connect secure payouts'],
  },
  COMPANY: {
    label: 'Company Admin',
    eyebrow: 'Business workspace profile',
    title: 'Give your charging network a proper identity.',
    description: 'Business details keep stations, billing and operator access attached to the correct legal entity.',
    icon: Building2,
    steps: ['Verify business identity', 'Add stations and chargers', 'Invite operations staff'],
  },
};

export function CompleteProfileModal({
  user,
  activeMode,
  authToken,
  onComplete,
  onSkip,
  onCancel,
  onLogout,
}: CompleteProfileModalProps) {
  const mode: ProfileMode = activeMode === 'ADMIN' ? 'EV_USER' : activeMode;
  const plan = profilePlans[mode];
  const close = onSkip ?? onCancel;
  const [fullName, setFullName] = useState(user.contactName || user.name || '');
  const [phone, setPhone] = useState((user.phone || '').replace(/\D/g, '').slice(-10));
  const [hostDisplayName, setHostDisplayName] = useState(user.name || '');
  const [companyName, setCompanyName] = useState(user.companyName || '');
  const [registrationNumber, setRegistrationNumber] = useState(user.registrationNumber || '');
  const [error, setError] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  const completedCount = useMemo(() => {
    const fields = [fullName.trim(), phone.length === 10 ? phone : ''];
    if (mode === 'HOST') fields.push(hostDisplayName.trim());
    if (mode === 'COMPANY') fields.push(companyName.trim(), registrationNumber.trim());
    return fields.filter(Boolean).length;
  }, [companyName, fullName, hostDisplayName, mode, phone, registrationNumber]);
  const totalCount = mode === 'COMPANY' ? 4 : mode === 'HOST' ? 3 : 2;

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') close?.();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [close]);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');

    if (!fullName.trim()) {
      setError(mode === 'COMPANY' ? 'Enter the primary contact name.' : 'Enter your full name.');
      return;
    }
    if (!/^[0-9]{10}$/.test(phone)) {
      setError('Enter a valid 10-digit mobile phone number.');
      return;
    }
    if (mode === 'HOST' && !hostDisplayName.trim()) {
      setError('Enter the name customers should see for this Host profile.');
      return;
    }
    if (mode === 'COMPANY' && (!companyName.trim() || !registrationNumber.trim())) {
      setError('Company name and Registration Number / CIN are required.');
      return;
    }

    const payload: CompleteProfilePayload = {
      mode,
      fullName: fullName.trim(),
      phone,
      ...(mode === 'HOST' && { hostDisplayName: hostDisplayName.trim() }),
      ...(mode === 'COMPANY' && {
        companyName: companyName.trim(),
        registrationNumber: registrationNumber.trim().toUpperCase(),
      }),
    };

    try {
      setIsSaving(true);
      const auth = await completeProfile(payload, authToken);
      onComplete(auth);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to save your profile. Please try again.');
    } finally {
      setIsSaving(false);
    }
  };

  const PlanIcon = plan.icon;

  return (
    <div className="profile-modal-backdrop" onMouseDown={() => close?.()}>
      <section
        className="profile-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="profile-modal-title"
        aria-describedby="profile-modal-description"
        onMouseDown={(event) => event.stopPropagation()}
      >
        {close && (
          <button type="button" className="profile-modal-close" onClick={close} aria-label="Close and skip for now" title="Skip for now">
            <X size={19} />
          </button>
        )}

        <aside className="profile-modal-story">
          <div>
            <span className="profile-modal-brand"><Sparkles size={15} /> VIDYUT</span>
            <div className="profile-mode-icon"><PlanIcon size={27} /></div>
            <p className="profile-modal-eyebrow">{plan.eyebrow}</p>
            <h2>{plan.title}</h2>
            <p>{plan.description}</p>
          </div>

          <div className="profile-next-actions">
            <span>What opens next</span>
            {plan.steps.map((step, index) => (
              <div key={step}><i>{index + 1}</i><strong>{step}</strong><Check size={15} /></div>
            ))}
          </div>

          {onLogout && (
            <button type="button" className="profile-modal-logout" onClick={onLogout}>
              <LogOut size={15} /> Sign out instead
            </button>
          )}
        </aside>

        <div className="profile-modal-form-side">
          <header className="profile-modal-header">
            <span className="profile-role-chip"><PlanIcon size={15} /> {plan.label}</span>
            <h1 id="profile-modal-title">Complete your profile</h1>
            <p id="profile-modal-description">A few details make your workspace personal. You can skip this and return from Settings at any time.</p>
            <div className="profile-progress" aria-label={`${completedCount} of ${totalCount} required details complete`}>
              <span style={{ width: `${Math.round((completedCount / totalCount) * 100)}%` }} />
            </div>
          </header>

          <form className="profile-completion-form" onSubmit={handleSubmit}>
            <label className="profile-field">
              <span>Registered email</span>
              <div className="profile-input is-readonly"><Mail size={18} /><input type="email" value={user.email} disabled /></div>
              <small><LockKeyhole size={12} /> Secured by your sign-in method</small>
            </label>

            <div className="profile-form-grid">
              <label className="profile-field">
                <span>{mode === 'COMPANY' ? 'Primary contact name' : 'Full name'} <b>*</b></span>
                <div className="profile-input"><UserIcon size={18} /><input type="text" value={fullName} onChange={(event) => { setFullName(event.target.value); setError(''); }} placeholder="Enter your name" autoComplete="name" autoFocus /></div>
              </label>
              <label className="profile-field">
                <span>Mobile number <b>*</b></span>
                <div className="profile-input profile-phone-input"><Phone size={18} /><em>+91</em><input type="tel" inputMode="numeric" value={phone} onChange={(event) => { setPhone(event.target.value.replace(/\D/g, '').slice(0, 10)); setError(''); }} placeholder="10-digit number" autoComplete="tel-national" /></div>
              </label>
            </div>

            {mode === 'HOST' && (
              <label className="profile-field">
                <span>Public Host / property name <b>*</b></span>
                <div className="profile-input"><HousePlug size={18} /><input type="text" value={hostDisplayName} onChange={(event) => { setHostDisplayName(event.target.value); setError(''); }} placeholder="e.g. Green Park Charging Hub" /></div>
                <small>This is the name EV owners will see while booking.</small>
              </label>
            )}

            {mode === 'COMPANY' && (
              <div className="profile-form-grid">
                <label className="profile-field">
                  <span>Company name <b>*</b></span>
                  <div className="profile-input"><Building2 size={18} /><input type="text" value={companyName} onChange={(event) => { setCompanyName(event.target.value); setError(''); }} placeholder="Registered business name" /></div>
                </label>
                <label className="profile-field">
                  <span>Registration Number / CIN <b>*</b></span>
                  <div className="profile-input"><Building2 size={18} /><input type="text" value={registrationNumber} onChange={(event) => { setRegistrationNumber(event.target.value.toUpperCase()); setError(''); }} placeholder="e.g. U74999DL2024PTC123456" autoCapitalize="characters" /></div>
                </label>
              </div>
            )}

            {error && <p className="profile-form-error" role="alert">{error}</p>}

            <div className="profile-modal-actions">
              {close && <button type="button" className="profile-skip-button" onClick={close}>Skip for now</button>}
              <button type="submit" className="profile-save-button" disabled={isSaving}>
                <span>{isSaving ? 'Saving profile…' : 'Save and continue'}</span>
                <ArrowRight size={17} />
              </button>
            </div>
            <p className="profile-privacy-note"><LockKeyhole size={13} /> Your details are saved securely to your Vidyut account.</p>
          </form>
        </div>
      </section>
    </div>
  );
}
