import React, { useEffect, useState } from "react";
import { Eye, EyeOff, LockKeyhole, Mail, Zap, Sun, Moon } from "lucide-react";
import { apiRequest, authenticateWithGoogle, saveAuthSession } from "../services/api";
import type { AuthData } from "../services/api";
import "../css/login.css";
import { useGoogleLogin } from "@react-oauth/google";


type LoginForm = {
  email: string;
  password: string;
};

interface LoginPageProps {
  onLogin: (auth: AuthData) => void;
  onBack?: () => void;
  onRegister?: () => void;
}

const VidyutLogoSvg: React.FC = () => (
  <img src="/vidyut-logo.svg" className="brand-logo-img" alt="" />
);

export default function LoginPage({ onLogin, onBack, onRegister }: LoginPageProps) {
  const googleEnabled = Boolean(import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim());
  const [theme, setTheme] = useState<'dark' | 'light'>(() => {
    const saved = localStorage.getItem('vidyut_theme');
    if (saved === 'dark' || saved === 'light') return saved;
    return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
  });

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    document.documentElement.classList.remove('theme-dark', 'theme-light');
    document.documentElement.classList.add(`theme-${theme}`);
    localStorage.setItem('vidyut_theme', theme);
  }, [theme]);

  const toggleTheme = () => setTheme((current) => current === 'dark' ? 'light' : 'dark');

  const [form, setForm] = useState<LoginForm>({
    email: "",
    password: "",
  });

  const [showPassword, setShowPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState("");

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = event.target;
    setForm((current) => ({
      ...current,
      [name]: value,
    }));
    setError("");
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!form.email.trim() || !form.password.trim()) {
      setError("Please enter your email and password.");
      return;
    }

    try {
      setIsSubmitting(true);
      setError("");

      const auth = await apiRequest<AuthData>("/auth/login", {
        method: "POST",
        body: JSON.stringify({ email: form.email, password: form.password }),
      });

      saveAuthSession(auth);
      onLogin(auth);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to log in. Please try again.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDemoLogin = async (demoEmail: string, demoPass: string) => {
    try {
      setIsSubmitting(true);
      setError("");
      setForm({ email: demoEmail, password: demoPass });

      const auth = await apiRequest<AuthData>("/auth/login", {
        method: "POST",
        body: JSON.stringify({ email: demoEmail, password: demoPass }),
      });

      saveAuthSession(auth);
      onLogin(auth);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to log in with demo account.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const googleLogin = useGoogleLogin({
    onSuccess: async (tokenResponse) => {
      try {
        setIsSubmitting(true);
        setError("");
        const auth = await authenticateWithGoogle(tokenResponse.access_token);
        saveAuthSession(auth);
        onLogin(auth);
      } catch (err) {
        setError(
          err instanceof Error
            ? err.message
            : "Google login failed."
        );
      } finally {
        setIsSubmitting(false);
      }
    },

    onError: () => {
      setError("Google login failed. Please try again.");
    },
  });

  return (
    <main className="login-page">
      {/* Background Overlay for text legibility */}
      <div className="bg-overlay" />

      {/* Floating Theme Toggle Switcher Button */}
      <button
        type="button"
        className="theme-toggle-btn"
        onClick={toggleTheme}
        aria-label="Toggle Theme Mode"
        title={`Switch to ${theme === 'dark' ? 'Day Mode' : 'Night Mode'}`}
      >
        {theme === 'dark' ? (
          <>
            <Sun size={18} className="theme-btn-icon sun-icon" />

          </>
        ) : (
          <>
            <Moon size={18} className="theme-btn-icon moon-icon" />

          </>
        )}
      </button>

      {/* Main Container */}
      <div className="login-container">
        {/* LEFT SIDE: BRANDING WITH LOGO CENTERED ABOVE VIDYUT (AT 30-35% HEIGHT) */}
        <section className="brand-side">
          <div
            className="top-left-brand"
            onClick={onBack}
            style={{ cursor: onBack ? "pointer" : "default" }}
            title={onBack ? "Return to Landing Page" : undefined}
          >
            <div className="brand-logo-centered">
              <VidyutLogoSvg />
            </div>

            <h1 className="brand-title">VIDYUT</h1>
            <p className="brand-subtitle">Powering a Smarter Tomorrow</p>
            <span className="brand-divider-line" />

            <p className="brand-description">
              India&apos;s intelligent EV charging ecosystem connecting EV owners, landowners, and charging providers.
            </p>
          </div>
        </section>

        {/* RIGHT SIDE: PREMIUM FLOATING GLASS CARD (580px WIDTH, 35% OPACITY GLASS) */}
        <section className="glass-card-side">
          <div className="glass-card">
            <header className="card-header">
              <h2>Welcome Back</h2>
              <p>Sign in to your Vidyut account</p>
            </header>

            <form className="login-form" onSubmit={handleSubmit}>
              <div className="form-group">
                <label htmlFor="email">Email Address</label>
                <div className="input-wrapper">
                  <Mail className="input-icon" size={35} />
                  <input
                    id="email"
                    name="email"
                    type="email"
                    value={form.email}
                    onChange={handleChange}
                    placeholder="Enter your email"
                    autoComplete="email"
                  />
                </div>
              </div>

              <div className="form-group">
                <label htmlFor="password">Password</label>
                <div className="input-wrapper">
                  <LockKeyhole className="input-icon" size={35} />
                  <input
                    id="password"
                    name="password"
                    type={showPassword ? "text" : "password"}
                    value={form.password}
                    onChange={handleChange}
                    placeholder="Enter your password"
                    autoComplete="current-password"
                  />
                  <button
                    type="button"
                    className="password-toggle"
                    onClick={() => setShowPassword((current) => !current)}
                    aria-label={showPassword ? "Hide password" : "Show password"}
                  >
                    {showPassword ? <EyeOff size={19} /> : <Eye size={19} />}
                  </button>
                </div>
              </div>

              {error && <p className="form-error">{error}</p>}

              <button
                type="submit"
                className="login-button"
                disabled={isSubmitting}
              >
                <Zap size={19} fill="currentColor" />
                <span>{isSubmitting ? "Authenticating..." : "Login"}</span>
              </button>
            </form>

            {/* Quick Demo Access Buttons for Hackathon Judges */}
            <div className="demo-accounts-box" style={{ marginTop: "16px", marginBottom: "8px" }}>
              <div className="divider" style={{ margin: "10px 0 12px" }}>
                <hr />
                <span style={{ fontSize: "11px", letterSpacing: "0.5px", textTransform: "uppercase", color: "var(--text-muted, #94a3b8)" }}>
                  ⚡ Quick Demo Access
                </span>
                <hr />
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "8px" }}>
                <button
                  type="button"
                  id="btn-demo-driver"
                  onClick={() => handleDemoLogin("demo.driver@vidyut.com", "VidyutDemo@2026")}
                  disabled={isSubmitting}
                  style={{
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    gap: "4px",
                    background: "rgba(0, 212, 255, 0.08)",
                    border: "1px solid rgba(0, 212, 255, 0.35)",
                    borderRadius: "8px",
                    padding: "8px 4px",
                    color: "var(--color-primary, #00d4ff)",
                    fontSize: "11px",
                    fontWeight: 600,
                    cursor: "pointer",
                    transition: "all 0.2s ease"
                  }}
                  title="Log in as Vidyut Demo Driver (EV Owner)"
                >
                  <span style={{ fontSize: "14px" }}>🚗</span>
                  <span>Try as EV Owner</span>
                </button>
                <button
                  type="button"
                  id="btn-demo-host"
                  onClick={() => handleDemoLogin("demo.host@vidyut.com", "VidyutDemo@2026")}
                  disabled={isSubmitting}
                  style={{
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    gap: "4px",
                    background: "rgba(16, 185, 129, 0.08)",
                    border: "1px solid rgba(16, 185, 129, 0.35)",
                    borderRadius: "8px",
                    padding: "8px 4px",
                    color: "#10b981",
                    fontSize: "11px",
                    fontWeight: 600,
                    cursor: "pointer",
                    transition: "all 0.2s ease"
                  }}
                  title="Log in as Vidyut Demo Host"
                >
                  <span style={{ fontSize: "14px" }}>🏢</span>
                  <span>Try as Host</span>
                </button>
                <button
                  type="button"
                  id="btn-demo-company"
                  onClick={() => handleDemoLogin("demo.company@vidyut.com", "VidyutDemo@2026")}
                  disabled={isSubmitting}
                  style={{
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    gap: "4px",
                    background: "rgba(245, 158, 11, 0.08)",
                    border: "1px solid rgba(245, 158, 11, 0.35)",
                    borderRadius: "8px",
                    padding: "8px 4px",
                    color: "#f59e0b",
                    fontSize: "11px",
                    fontWeight: 600,
                    cursor: "pointer",
                    transition: "all 0.2s ease"
                  }}
                  title="Log in as Tata EV Charging Demo (Company)"
                >
                  <span style={{ fontSize: "14px" }}>⚡</span>
                  <span>Try as Company</span>
                </button>
              </div>
            </div>

            <p className="signup-text">
              Don&apos;t have an account?{" "}
              <a
                href="#register"
                onClick={(e) => {
                  e.preventDefault();
                  if (onRegister) onRegister();
                }}
              >
                Sign up
              </a>
            </p>

            <div className="divider" style={{ margin: "20px 0 14px" }}>
              <hr />
              <span>or continue with</span>
              <hr />
            </div>

            <div className="socialconnect">
              <button
                type="button"
                className="google-signin-btn"
                title="Continue with Google"
                onClick={() => googleEnabled ? googleLogin() : setError("Google sign-in is not configured for this environment.")}
                disabled={isSubmitting || !googleEnabled}
                aria-label="Continue with Google"
              >
                <svg width="24" height="24" viewBox="0 0 24 24" className="google-btn-icon" aria-hidden="true">
                  <path
                    fill="#4285F4"
                    d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                  />
                  <path
                    fill="#34A853"
                    d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                  />
                  <path
                    fill="#FBBC05"
                    d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
                  />
                  <path
                    fill="#EA4335"
                    d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
                  />
                </svg>
                <span>Login with Google</span>
              </button>
            </div>
          </div>
        </section>
      </div>
    </main>
  );
}
