import { useState } from 'react';
import { Wallet, Zap, Percent, Star, AlertCircle, Info, ChevronDown } from 'lucide-react';
import type { User } from '../../types';

interface LandownerDashboardProps {
  user: User;
  onNavigate?: (tab: string) => void;
}

export function LandownerDashboard({ user, onNavigate }: LandownerDashboardProps) {
  const [isChargerOnline, setIsChargerOnline] = useState(true);

  return (
    <div className="landowner-dashboard-container">
      {/* Welcome Banner */}
      <div className="dashboard-header-row">
        <div className="welcome-text-group">
          <h1 className="welcome-heading">Welcome Back, {user.name || 'Amit'} 👋</h1>
          <p className="welcome-subheading">Here&apos;s what&apos;s happening with your charger.</p>
        </div>
      </div>

      {/* Top 4 Metric Cards Row */}
      <div className="landowner-top-cards-grid">
        {/* Card 1: Today's Earnings */}
        <div className="dashboard-card stat-summary-card">
          <div className="stat-icon-wrapper green-light-bg">
            <Wallet size={20} className="text-green-600" />
          </div>
          <div className="stat-content">
            <span className="stat-label-text">Today&apos;s Earnings</span>
            <div className="stat-number-val">₹1,250</div>
            <span className="stat-trend-badge green-badge">+12% from yesterday</span>
          </div>
        </div>

        {/* Card 2: Total Bookings */}
        <div className="dashboard-card stat-summary-card">
          <div className="stat-icon-wrapper amber-light-bg">
            <Zap size={20} className="text-amber-600" />
          </div>
          <div className="stat-content">
            <span className="stat-label-text">Total Bookings</span>
            <div className="stat-number-val">08</div>
            <span className="stat-trend-badge green-badge">+2 from yesterday</span>
          </div>
        </div>

        {/* Card 3: Total Earnings */}
        <div className="dashboard-card stat-summary-card">
          <div className="stat-icon-wrapper purple-light-bg">
            <Percent size={20} className="text-purple-600" />
          </div>
          <div className="stat-content">
            <span className="stat-label-text">Total Earnings</span>
            <div className="stat-number-val">₹28,540</div>
            <span className="stat-sub-text">This Month</span>
          </div>
        </div>

        {/* Card 4: Charger Status Toggle Card */}
        <div className="dashboard-card charger-status-toggle-card">
          <h3 className="card-heading">Charger Status</h3>
          <div className="charger-info-row">
            <div className="charger-thumb-box">⚡</div>
            <div className="charger-text-details">
              <div className="charger-name-title">Home Charger</div>
              <div className="charger-specs">AC • 7.4 kW</div>
            </div>
            <div className="toggle-badge-group">
              <span className={`status-tag ${isChargerOnline ? 'online' : 'offline'}`}>
                {isChargerOnline ? 'Online' : 'Offline'}
              </span>
              <label className="switch-toggle-label">
                <input
                  type="checkbox"
                  checked={isChargerOnline}
                  onChange={(e) => setIsChargerOnline(e.target.checked)}
                />
                <span className="slider-round" />
              </label>
            </div>
          </div>
        </div>
      </div>

      {/* Middle Section: Earnings Overview Chart (Left) & Upcoming Bookings (Right) */}
      <div className="landowner-middle-grid">
        {/* Earnings Overview Card */}
        <div className="dashboard-card chart-large-card">
          <div className="card-header-flex">
            <h3 className="card-heading">Earnings Overview</h3>
            <div className="dropdown-filter-btn">
              <span>This Month</span>
              <ChevronDown size={16} />
            </div>
          </div>

          <div className="chart-canvas-wrapper">
            <svg width="100%" height="240" viewBox="0 0 650 240" preserveAspectRatio="none">
              <defs>
                <linearGradient id="earningsGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#22c55e" stopOpacity="0.25" />
                  <stop offset="100%" stopColor="#22c55e" stopOpacity="0.0" />
                </linearGradient>
              </defs>

              {/* Grid Lines */}
              <line x1="40" y1="20" x2="630" y2="20" stroke="#f1f5f9" strokeWidth="1" />
              <line x1="40" y1="75" x2="630" y2="75" stroke="#f1f5f9" strokeWidth="1" />
              <line x1="40" y1="130" x2="630" y2="130" stroke="#f1f5f9" strokeWidth="1" />
              <line x1="40" y1="185" x2="630" y2="185" stroke="#f1f5f9" strokeWidth="1" />

              {/* Y Axis Labels */}
              <text x="10" y="25" fill="#94a3b8" fontSize="11">₹3K</text>
              <text x="10" y="80" fill="#94a3b8" fontSize="11">₹2K</text>
              <text x="10" y="135" fill="#94a3b8" fontSize="11">₹1K</text>
              <text x="10" y="190" fill="#94a3b8" fontSize="11">₹0</text>

              {/* Curve Area Fill & Stroke */}
              <path
                d="M 50 160 Q 120 180 180 100 T 310 140 T 440 90 T 570 120 L 610 80 L 610 185 L 50 185 Z"
                fill="url(#earningsGradient)"
              />
              <path
                d="M 50 160 Q 120 180 180 100 T 310 140 T 440 90 T 570 120 L 610 80"
                fill="none"
                stroke="#22c55e"
                strokeWidth="3"
              />

              {/* Peak Point */}
              <circle cx="610" cy="80" r="5" fill="#22c55e" stroke="#ffffff" strokeWidth="3" />
            </svg>

            <div className="chart-tooltip-badge landowner-tooltip">₹1,250</div>

            <div className="chart-x-labels">
              <span>1 May</span>
              <span>8 May</span>
              <span>15 May</span>
              <span>22 May</span>
              <span>29 May</span>
            </div>
          </div>
        </div>

        {/* Upcoming Bookings Card */}
        <div className="dashboard-card upcoming-bookings-card">
          <div className="card-header-flex">
            <h3 className="card-heading">Upcoming Bookings</h3>
            <button type="button" className="btn-text-link" onClick={() => onNavigate?.('bookings')}>View All</button>
          </div>

          <div className="upcoming-list">
            <div className="upcoming-item-row">
              <div className="icon-rounded-bg green-soft">
                <Zap size={18} className="text-green-600" />
              </div>
              <div className="upcoming-text-info">
                <div className="upcoming-time-title">Today, 11:00 AM</div>
                <div className="upcoming-sub-specs">2h 30m • AC Charger</div>
              </div>
              <span className="status-confirmed-pill">Confirmed</span>
            </div>

            <div className="upcoming-item-row">
              <div className="icon-rounded-bg green-soft">
                <Zap size={18} className="text-green-600" />
              </div>
              <div className="upcoming-text-info">
                <div className="upcoming-time-title">Today, 04:00 PM</div>
                <div className="upcoming-sub-specs">1h 45m • AC Charger</div>
              </div>
              <span className="status-confirmed-pill">Confirmed</span>
            </div>
          </div>
        </div>
      </div>

      {/* Bottom Section: Recent Reviews (Left) & Recent Alerts (Right) */}
      <div className="landowner-bottom-grid">
        {/* Recent Reviews Card */}
        <div className="dashboard-card reviews-card">
          <div className="card-header-flex">
            <h3 className="card-heading">Recent Reviews</h3>
            <button type="button" className="btn-text-link" onClick={() => onNavigate?.('reviews')}>View All</button>
          </div>

          <div className="review-single-item">
            <div className="reviewer-info-row">
              <div className="avatar-circle">
                <img src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=100&q=80" alt="Rahul" className="avatar-img" />
              </div>
              <div className="reviewer-details">
                <div className="reviewer-name">Rahul Sharma</div>
                <div className="star-rating-row">
                  {[1, 2, 3, 4, 5].map((s) => (
                    <Star key={s} size={14} fill="#f59e0b" color="#f59e0b" />
                  ))}
                </div>
              </div>
              <span className="review-date-text">20 May 2024</span>
            </div>
            <p className="review-comment-text">
              Great experience. Charger was available and worked perfectly.
            </p>
          </div>
        </div>

        {/* Recent Alerts Card */}
        <div className="dashboard-card landowner-alerts-card">
          <div className="alerts-stack-list">
            <div className="alert-item red-alert-item">
              <div className="alert-icon-box red-icon-bg">
                <AlertCircle size={18} />
              </div>
              <div className="alert-info-text">
                <div className="alert-title">Cyber Hub Station</div>
                <div className="alert-sub">Charger #12 not responding</div>
              </div>
              <span className="alert-time-badge red-time">10 min ago</span>
            </div>

            <div className="alert-item blue-alert-item">
              <div className="alert-icon-box blue-icon-bg">
                <Info size={18} />
              </div>
              <div className="alert-info-text">
                <div className="alert-title">MG Road Station</div>
                <div className="alert-sub">Maintenance scheduled</div>
              </div>
              <span className="alert-time-badge blue-time">1 hr ago</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
