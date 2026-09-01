import React, { useState } from "react";
import { Building2, CarFront, HousePlug, Leaf, ShieldCheck, Zap } from "lucide-react";
import { useGoogleLogin } from "@react-oauth/google";
import "./VidyutRegister.css";
import {
  apiRequest,
  authenticateWithGoogle,
  saveAuthSession,
} from "../services/api";
import type { AccessMode, AuthData } from "../services/api";

type AccountType = "EV_OWNER" | "LANDOWNER" | "COMPANY_ADMIN";

type RegisterForm = {
  name: string;
  email: string;
  password: string;
  accountType: AccountType;
};

interface RegisterPageProps {
  onRegistered: (auth: AuthData) => void;
  onLogin: () => void;
}

const roleOptions: Array<{
  id: AccountType;
  label: string;
  detail: string;
  icon: React.ComponentType<{ size?: number; strokeWidth?: number }>;
}> = [
  { id: "EV_OWNER", label: "EV Owner", detail: "Charge, book and manage vehicles", icon: CarFront },
  { id: "LANDOWNER", label: "Charger Host", detail: "List chargers and earn securely", icon: HousePlug },
  { id: "COMPANY_ADMIN", label: "Company Admin", detail: "Operate a charging network", icon: Building2 },
];

const GoogleIcon = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" className="google-btn-icon" aria-hidden="true">
    <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
    <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
    <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" />
    <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" />
  </svg>
);

const VidyutLogo: React.FC = () => (
  <div className="vidyut-logo-wrapper">
    <img src="/vidyut-logo.svg" className="brand-logo-img" alt="" />
    <span className="vidyut-logo-sub">Powering a Smarter Tomorrow</span>
  </div>
);

function modeForAccountType(accountType: AccountType): Exclude<AccessMode, "ADMIN"> {
  if (accountType === "LANDOWNER") return "HOST";
  if (accountType === "COMPANY_ADMIN") return "COMPANY";
  return "EV_USER";
}

export default function VidyutRegisterPage({ onRegistered, onLogin }: RegisterPageProps) {
  const googleEnabled = Boolean(import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim());
  const [formData, setFormData] = useState<RegisterForm>({
    name: "",
    email: "",
    password: "",
    accountType: "EV_OWNER",
  });
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const finishAuthentication = (auth: AuthData) => {
    saveAuthSession(auth);
    onRegistered(auth);
  };

  const googleLogin = useGoogleLogin({
    onSuccess: async (tokenResponse) => {
      try {
        setIsSubmitting(true);
        setError("");
        const auth = await authenticateWithGoogle(
          tokenResponse.access_token,
          modeForAccountType(formData.accountType),
        );
        finishAuthentication(auth);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Google registration failed.");
      } finally {
        setIsSubmitting(false);
      }
    },
    onError: () => setError("Google registration was cancelled or could not be completed."),
  });

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = event.target;
    setFormData((current) => ({ ...current, [name]: value }));
    setError("");
  };

  const getRegistrationRequest = (): { endpoint: string; payload: object } => {
    if (formData.accountType === "LANDOWNER") {
      return {
        endpoint: "/auth/register/host",
        payload: { fullName: formData.name, email: formData.email, password: formData.password },
      };
    }
    if (formData.accountType === "COMPANY_ADMIN") {
      return {
        endpoint: "/auth/register/company",
        payload: {
          adminEmail: formData.email,
          adminPassword: formData.password,
          adminFullName: formData.name,
        },
      };
    }
    return {
      endpoint: "/auth/register/user",
      payload: { fullName: formData.name, email: formData.email, password: formData.password },
    };
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!formData.name.trim() || !formData.email.trim() || !formData.password) {
      setError("Name, email and password are required.");
      return;
    }
    if (formData.password.length < 8) {
      setError("Password must be at least 8 characters.");
      return;
    }

    try {
      setIsSubmitting(true);
      setError("");
      const { endpoint, payload } = getRegistrationRequest();
      const auth = await apiRequest<AuthData>(endpoint, {
        method: "POST",
        body: JSON.stringify(payload),
      });
      finishAuthentication(auth);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to create your account. Please try again.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="margdarshi-register-page">
      <div className="split-bg-left" />
      <div className="split-bg-right" />

      <div className="margdarshi-card">
        <section className="form-column">
          <div className="form-header">
            <span className="signup-kicker">Create your workspace</span>
            <h1 className="signup-title">Join Vidyut</h1>
            <p className="signup-subtitle">Start with the essentials. You can finish profile details after sign-in.</p>
          </div>

          <div className="role-selector" role="radiogroup" aria-label="Choose account role">
            {roleOptions.map(({ id, label, detail, icon: Icon }) => (
              <button
                type="button"
                role="radio"
                aria-checked={formData.accountType === id}
                className={`role-btn ${formData.accountType === id ? "active" : ""}`}
                onClick={() => {
                  setFormData((current) => ({ ...current, accountType: id }));
                  setError("");
                }}
                key={id}
              >
                <span className="role-btn-icon"><Icon size={18} strokeWidth={2} /></span>
                <span><strong>{label}</strong><small>{detail}</small></span>
              </button>
            ))}
          </div>

          <form onSubmit={handleSubmit} className="signup-form">
            <label className="input-field-group" htmlFor="name">
              <span>Full name</span>
              <input id="name" name="name" type="text" placeholder="Your name" value={formData.name} onChange={handleChange} autoComplete="name" required />
            </label>
            <label className="input-field-group" htmlFor="register-email">
              <span>Email address</span>
              <input id="register-email" name="email" type="email" placeholder="you@example.com" value={formData.email} onChange={handleChange} autoComplete="email" required />
            </label>
            <label className="input-field-group" htmlFor="register-password">
              <span>Password</span>
              <input id="register-password" name="password" type="password" placeholder="Minimum 8 characters" value={formData.password} onChange={handleChange} autoComplete="new-password" minLength={8} required />
            </label>

            {error && <p className="signup-error-msg" role="alert">{error}</p>}

            <button type="submit" className="submit-signup-btn" disabled={isSubmitting}>
              {isSubmitting ? "Creating account…" : "Create account"}
            </button>

            <div className="divider"><hr /><span>or</span><hr /></div>
            <button
              type="button"
              className="google-signin-btn"
              title={googleEnabled ? "Continue with Google" : "Google sign-in is not configured"}
              onClick={() => googleLogin()}
              disabled={isSubmitting || !googleEnabled}
              aria-label="Continue with Google"
            >
              <GoogleIcon />
              <span>Continue with Google</span>
            </button>

            <p className="already-account">
              Already have an account?{" "}
              <a href="#/login" className="login-link-btn" onClick={(event) => { event.preventDefault(); onLogin(); }}>Log in</a>
            </p>
          </form>
        </section>

        <section className="brand-column" aria-label="Vidyut account benefits">
          <div className="top-brand-title"><span className="brand-badge-pill">VIDYUT</span></div>
          <div className="brand-logo-section">
            <VidyutLogo />
            <h2>One account. The right workspace.</h2>
            <p>Your selected role shapes the dashboard, profile steps and next actions—without slowing down sign-up.</p>
            <div className="registration-principles">
              <p><Zap size={17} /><span><strong>Fast start</strong>Only name, email and password are required now.</span></p>
              <p><ShieldCheck size={17} /><span><strong>Secure by default</strong>Credentials and Google identity are verified by the server.</span></p>
              <p><Leaf size={17} /><span><strong>Complete later</strong>Phone and business details can be added inside your workspace.</span></p>
            </div>
          </div>
          <p className="registration-footnote">By continuing, you agree to use Vidyut responsibly and keep account information accurate.</p>
        </section>
      </div>
    </main>
  );
}
