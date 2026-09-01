import React from "react";
import { Zap, ArrowRight, MapPin, BatteryCharging, Building2, ChevronRight } from "lucide-react";
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
  const scrollToEcosystem = () => {
    const el = document.getElementById("ecosystem-section");
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
          <a className="nav-brand" href="#home">
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
          </a>

          <nav>
            <ul className="nav-links">
              <li>
                <button className="nav-link-btn" onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}>
                  Home
                </button>
              </li>
              <li>
                <button className="nav-link-btn" onClick={onExploreChargers}>
                  Find Chargers
                </button>
              </li>
              <li>
                <button className="nav-link-btn" onClick={scrollToEcosystem}>
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
          {/* 0.3s VIDYUT Heading */}
          <h1 className="hero-heading">VIDYUT</h1>

          {/* 0.5s Tagline */}
          <p className="hero-tagline">Powering a Smarter Tomorrow</p>

          {/* 0.7s Description */}
          <p className="hero-description">
            India&apos;s intelligent EV charging ecosystem connecting EV owners,
            landowners and charging providers.
          </p>

          {/* 0.9s CTA Buttons */}
          <div className="hero-cta-group">
            <button className="cta-primary" onClick={onExploreChargers}>
              <span>⚡ Find a Charger</span>
            </button>

            <button className="cta-secondary" onClick={onRegister}>
              <span>Get Started</span>
              <ArrowRight size={18} />
            </button>
          </div>
        </div>

        {/* STATS ROW (1.1s entrance) */}
        <div className="hero-stats-bar">
          <div className="stat-item">
            <span className="stat-number">10K+</span>
            <span className="stat-label">Chargers</span>
          </div>

          <div className="stat-divider" />

          <div className="stat-item">
            <span className="stat-number">25K+</span>
            <span className="stat-label">Users</span>
          </div>

          <div className="stat-divider" />

          <div className="stat-item">
            <span className="stat-number">500+</span>
            <span className="stat-label">Locations</span>
          </div>
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
          <div className="ecosystem-card">
            <div>
              <div className="card-icon-box">
                <BatteryCharging size={28} />
              </div>
              <h3 className="card-title">EV Drivers</h3>
              <p className="card-desc">
                Locate high-speed chargers, reserve slots in advance, and pay automatically
                with real-time AI battery diagnostics.
              </p>
            </div>
            <button className="card-link-btn" onClick={onExploreChargers}>
              <span>Explore Chargers</span>
              <ChevronRight size={16} />
            </button>
          </div>

          {/* Card 2: Landowners */}
          <div className="ecosystem-card">
            <div>
              <div className="card-icon-box">
                <MapPin size={28} />
              </div>
              <h3 className="card-title">Landowners & Hosts</h3>
              <p className="card-desc">
                Turn your parking spaces or real estate into passive revenue streams by hosting
                Vidyut ultra-fast charging hardware.
              </p>
            </div>
            <button className="card-link-btn" onClick={onRegister}>
              <span>Become a Host</span>
              <ChevronRight size={16} />
            </button>
          </div>

          {/* Card 3: Businesses */}
          <div className="ecosystem-card">
            <div>
              <div className="card-icon-box">
                <Building2 size={28} />
              </div>
              <h3 className="card-title">Companies & Fleets</h3>
              <p className="card-desc">
                Manage commercial EV fleets, set corporate charging policies, and scale your charging
                network with central analytics.
              </p>
            </div>
            <button className="card-link-btn" onClick={onRegister}>
              <span>Enterprise Solutions</span>
              <ChevronRight size={16} />
            </button>
          </div>
        </div>
      </section>

      {/* FOOTER */}
      <footer className="landing-footer">
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <Zap size={18} color="#22c55e" />
          <span style={{ color: "#ffffff", fontWeight: 700 }}>VIDYUT</span>
          <span>© 2026 Vidyut EV Ecosystem. All rights reserved.</span>
        </div>
        <div style={{ display: "flex", gap: 24 }}>
          <button className="nav-link-btn" style={{ fontSize: "0.85rem" }}>Privacy Policy</button>
          <button className="nav-link-btn" style={{ fontSize: "0.85rem" }}>Terms of Service</button>
          <button className="nav-link-btn" style={{ fontSize: "0.85rem" }}>Contact Support</button>
        </div>
      </footer>
    </div>
  );
};

export default LandingPage;
