import React from "react";
import {
  Zap,
  ArrowRight,
  MapPin,
  BatteryCharging,
  Building2,
  ChevronRight,
  Route,
  ShieldCheck,
  LayoutDashboard,
} from "lucide-react";
import landingBgImg from "../assets/homepage.png";
import "../css/landing.css";

interface LandingPageProps {
  onLogin: () => void;
  onRegister: () => void;
  onExploreChargers: () => void;
}

export const LandingPage: React.FC<LandingPageProps> = ({
  onLogin,
  onRegister,
  onExploreChargers,
}) => {
  const scrollToHowItWorks = () => {
    const el = document.getElementById("how-it-works-section");
    if (el) {
      el.scrollIntoView({ behavior: "smooth" });
    }
  };

  return (
    <div className="landing-page">
      {/* HERO SECTION */}
      <section className="hero">
        {/* Background Image with 12s zoom */}
        <div className="hero-background-wrapper">
          <img
            src={landingBgImg}
            alt="Smart City EV Charging Road"
            className="hero-background"
          />
        </div>

        {/* TOP NAVBAR (0.1s entrance) */}
        <header className="navbar">
          <button
            type="button"
            className="nav-brand"
            aria-label="Back to the top"
            onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}
          >
            <svg
              className="nav-brand-svg"
              viewBox="0 0 200 200"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
            >
              <defs>
                <linearGradient id="navVGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stopColor="#d9f99d" />
                  <stop offset="50%" stopColor="#22c55e" />
                  <stop offset="100%" stopColor="#15803d" />
                </linearGradient>
              </defs>
              <path
                d="M 38,30 L 65,36 L 100,165 L 128,75 L 114,80 L 165,15 L 142,58 L 158,58 L 100,185 Z"
                fill="url(#navVGrad)"
              />
            </svg>
            <span className="nav-brand-title">VIDYUT</span>
          </button>

          <nav>
            <ul className="nav-links">
              <li>
                <button
                  className="nav-link-btn"
                  aria-current="page"
                  onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
                >
                  Home
                </button>
              </li>
              <li>
                <button className="nav-link-btn" onClick={onExploreChargers}>
                  Find Chargers
                </button>
              </li>
              <li>
                <button className="nav-link-btn" onClick={scrollToHowItWorks}>
                  How It Works
                </button>
              </li>
              <li>
                <button className="nav-link-btn" onClick={onRegister}>
                  For Hosts
                </button>
              </li>
              <li>
                <button className="nav-link-btn" onClick={onRegister}>
                  For Businesses
                </button>
              </li>
            </ul>
          </nav>

          <div className="nav-actions">
            <button className="btn-login" onClick={onLogin}>
              Login
            </button>
            <button className="btn-get-started" onClick={onRegister}>
              Get Started
            </button>
          </div>
        </header>

        {/* HERO MAIN BODY CONTENT */}
        <div className="hero-content">
          <div className="hero-eyebrow">
            <span className="hero-eyebrow-dot" />
            One platform · Three role workspaces
          </div>

          {/* 0.3s VIDYUT Heading */}
          <h1 className="hero-heading">VIDYUT</h1>

          {/* 0.5s Tagline */}
          <p className="hero-tagline">Powering a Smarter Tomorrow</p>

          {/* 0.7s Description */}
          <p className="hero-description">
            India&apos;s intelligent EV charging ecosystem connecting EV owners,
            landowners and charging providers.
          </p>

          <div className="hero-capabilities" aria-label="Vidyut capabilities">
            <span><Route size={15} aria-hidden="true" /> Plan EV journeys</span>
            <span><MapPin size={15} aria-hidden="true" /> Host charging sites</span>
            <span><Building2 size={15} aria-hidden="true" /> Operate the network</span>
          </div>

          {/* 0.9s CTA Buttons */}
          <div className="hero-cta-group">
            <button className="cta-primary" onClick={onExploreChargers}>
              <Zap size={18} aria-hidden="true" />
              <span>Find a Charger</span>
            </button>

            <button className="cta-secondary" onClick={onRegister}>
              <span>Get Started</span>
              <ArrowRight size={18} aria-hidden="true" />
            </button>
          </div>
        </div>

        {/* STATS ROW (1.1s entrance) */}
        <div className="hero-stats-bar">
          <div className="stat-item">
            <span className="stat-icon"><MapPin size={16} aria-hidden="true" /></span>
            <span>
              <span className="stat-number">9</span>
              <span className="stat-label">Demo corridor stations</span>
            </span>
          </div>

          <div className="stat-divider" />

          <div className="stat-item">
            <span className="stat-icon"><LayoutDashboard size={16} aria-hidden="true" /></span>
            <span>
              <span className="stat-number">3</span>
              <span className="stat-label">Role workspaces</span>
            </span>
          </div>

          <div className="stat-divider" />

          <div className="stat-item">
            <span className="stat-icon"><BatteryCharging size={16} aria-hidden="true" /></span>
            <span>
              <span className="stat-number">24/7</span>
              <span className="stat-label">Journey monitoring</span>
            </span>
          </div>
        </div>
      </section>

      <section className="landing-process" id="how-it-works-section">
        <div className="section-header process-header">
          <span className="section-badge">HOW VIDYUT WORKS</span>
          <h2 className="section-title">From the next charge to the whole network</h2>
          <p className="section-subtitle">
            Start in the workspace built for your role, take the next clear action,
            and keep every update in one place.
          </p>
        </div>

        <div className="process-rail">
          <article className="process-step">
            <span className="process-number">01</span>
            <div className="process-icon"><ShieldCheck size={22} aria-hidden="true" /></div>
            <div>
              <h3>Choose your workspace</h3>
              <p>EV owner, property host, or charging company—each role gets a focused flow.</p>
            </div>
          </article>

          <article className="process-step">
            <span className="process-number">02</span>
            <div className="process-icon"><Route size={22} aria-hidden="true" /></div>
            <div>
              <h3>Take the next action</h3>
              <p>Find a charger, list a suitable site, or manage station operations.</p>
            </div>
          </article>

          <article className="process-step">
            <span className="process-number">03</span>
            <div className="process-icon"><LayoutDashboard size={22} aria-hidden="true" /></div>
            <div>
              <h3>Stay in control</h3>
              <p>Track journeys, properties, connectors, bookings, and incidents from one dashboard.</p>
            </div>
          </article>
        </div>
      </section>

      {/* ECOSYSTEM / FEATURES SECTION */}
      <section className="landing-section" id="ecosystem-section">
        <div className="section-header">
          <span className="section-badge">INTELLIGENT ECOSYSTEM</span>
          <h2 className="section-title">Built for Everyone in the EV Revolution</h2>
          <p className="section-subtitle">
            Whether you drive an electric vehicle, own commercial space, or operate a fleet,
            Vidyut brings seamless smart charging to your fingertips.
          </p>
        </div>

        <div className="ecosystem-grid">
          {/* Card 1: EV Owners */}
          <div className="ecosystem-card ecosystem-card-driver">
            <div>
              <span className="card-role-label">DRIVER WORKSPACE</span>
              <div className="card-icon-box">
                <BatteryCharging size={28} />
              </div>
              <h3 className="card-title">EV Drivers</h3>
              <p className="card-desc">
                Find compatible chargers, reserve a slot, and keep trips on track with
                battery-aware route planning.
              </p>
            </div>
            <button className="card-link-btn" onClick={onExploreChargers}>
              <span>Explore Chargers</span>
              <ChevronRight size={16} />
            </button>
          </div>

          {/* Card 2: Landowners */}
          <div className="ecosystem-card ecosystem-card-host">
            <div>
              <span className="card-role-label">HOST WORKSPACE</span>
              <div className="card-icon-box">
                <MapPin size={28} />
              </div>
              <h3 className="card-title">Landowners & Hosts</h3>
              <p className="card-desc">
                List suitable properties, review operator opportunities, and monitor hosted
                charging locations in one workspace.
              </p>
            </div>
            <button className="card-link-btn" onClick={onRegister}>
              <span>Become a Host</span>
              <ChevronRight size={16} />
            </button>
          </div>

          {/* Card 3: Businesses */}
          <div className="ecosystem-card ecosystem-card-company">
            <div>
              <span className="card-role-label">COMPANY WORKSPACE</span>
              <div className="card-icon-box">
                <Building2 size={28} />
              </div>
              <h3 className="card-title">Companies & Fleets</h3>
              <p className="card-desc">
                Monitor stations, connectors, bookings, and incidents from a role-scoped
                operations workspace.
              </p>
            </div>
            <button className="card-link-btn" onClick={onRegister}>
              <span>Enterprise Solutions</span>
              <ChevronRight size={16} />
            </button>
          </div>
        </div>
      </section>

      <section className="landing-final-cta">
        <div>
          <span className="final-cta-kicker">YOUR NEXT MOVE, SIMPLIFIED</span>
          <h2>Start with the Vidyut workspace built for you.</h2>
          <p>Explore charging now or create an account to manage your role-specific workflow.</p>
        </div>
        <div className="final-cta-actions">
          <button className="cta-secondary final-cta-secondary" onClick={onExploreChargers}>
            <MapPin size={18} aria-hidden="true" />
            <span>Explore Chargers</span>
          </button>
          <button className="cta-primary" onClick={onRegister}>
            <span>Create Account</span>
            <ArrowRight size={18} aria-hidden="true" />
          </button>
        </div>
      </section>

      {/* FOOTER */}
      <footer className="landing-footer">
        <div className="landing-footer-brand">
          <Zap size={18} color="#22c55e" />
          <strong>VIDYUT</strong>
          <span>© 2026 Vidyut EV Ecosystem. All rights reserved.</span>
        </div>
        <p className="landing-footer-note">Built for EV owners, property hosts, and charging operators.</p>
      </footer>
    </div>
  );
};

export default LandingPage;
