import { Building2, Zap, Clock, AlertCircle, Info, ChevronDown } from 'lucide-react';
import type { User } from '../../types';

interface EnterpriseDashboardProps {
  user: User;
  onNavigate?: (tab: string) => void;
}

export function EnterpriseDashboard({ user, onNavigate }: EnterpriseDashboardProps) {
  return (
    <div className="enterprise-dashboard-container">
      {/* Header Banner */}
      <div className="dashboard-header-row">
        <div className="welcome-text-group">
          <h1 className="welcome-heading">Welcome, {user.name || 'Tata Power EV'} ⚡</h1>
          <p className="welcome-subheading">Manage your network with real-time insights.</p>
        </div>
      </div>

      {/* Top 3 Stat Cards Grid */}
      <div className="enterprise-top-grid">
        {/* Card 1: Total Stations */}
        <div className="dashboard-card enterprise-stat-card">
          <div className="stat-icon-wrapper blue-light-bg">
            <Building2 size={20} className="text-blue-600" />
          </div>
          <div className="stat-content">
            <span className="stat-label-text">Total Stations</span>
            <div className="stat-number-val">56</div>
          </div>
        </div>

        {/* Card 2: Total Chargers */}
        <div className="dashboard-card enterprise-stat-card">
          <div className="stat-icon-wrapper green-light-bg">
            <Zap size={20} className="text-green-600" />
          </div>
          <div className="stat-content">
            <span className="stat-label-text">Total Chargers</span>
            <div className="stat-number-val">312</div>
          </div>
        </div>

        {/* Card 3: Utilization Rate */}
        <div className="dashboard-card enterprise-stat-card">
          <div className="stat-icon-wrapper purple-light-bg">
            <Clock size={20} className="text-purple-600" />
          </div>
          <div className="stat-content">
            <span className="stat-label-text">Utilization Rate</span>
            <div className="stat-number-val">62%</div>
          </div>
        </div>
      </div>

      {/* Middle Section: Network Overview (Left) & Station Status Donut (Right) */}
      <div className="enterprise-middle-grid">
        {/* Network Overview Line Chart */}
        <div className="dashboard-card chart-large-card">
          <div className="card-header-flex">
            <h3 className="card-heading">Network Overview</h3>
            <div className="dropdown-filter-btn">
              <span>This Week</span>
              <ChevronDown size={16} />
            </div>
          </div>

          <div className="chart-canvas-wrapper">
            <svg width="100%" height="240" viewBox="0 0 650 240" preserveAspectRatio="none">
              <defs>
                <linearGradient id="networkGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#3b82f6" stopOpacity="0.25" />
                  <stop offset="100%" stopColor="#3b82f6" stopOpacity="0.0" />
                </linearGradient>
              </defs>

              {/* Grid Lines */}
              <line x1="40" y1="20" x2="630" y2="20" stroke="#f1f5f9" strokeWidth="1" />
              <line x1="40" y1="65" x2="630" y2="65" stroke="#f1f5f9" strokeWidth="1" />
              <line x1="40" y1="110" x2="630" y2="110" stroke="#f1f5f9" strokeWidth="1" />
              <line x1="40" y1="155" x2="630" y2="155" stroke="#f1f5f9" strokeWidth="1" />
              <line x1="40" y1="200" x2="630" y2="200" stroke="#f1f5f9" strokeWidth="1" />

              {/* Y Axis Labels */}
              <text x="10" y="25" fill="#94a3b8" fontSize="11">100%</text>
              <text x="10" y="70" fill="#94a3b8" fontSize="11">75%</text>
              <text x="10" y="115" fill="#94a3b8" fontSize="11">50%</text>
              <text x="10" y="160" fill="#94a3b8" fontSize="11">25%</text>
              <text x="10" y="205" fill="#94a3b8" fontSize="11">0%</text>

              {/* Blue Wave Area Fill & Stroke */}
              <path
                d="M 50 110 Q 110 100 170 80 T 290 110 T 410 70 T 530 130 L 610 95 L 610 200 L 50 200 Z"
                fill="url(#networkGradient)"
              />
              <path
                d="M 50 110 Q 110 100 170 80 T 290 110 T 410 70 T 530 130 L 610 95"
                fill="none"
                stroke="#3b82f6"
                strokeWidth="3"
              />

              {/* Peak Circle */}
              <circle cx="610" cy="95" r="5" fill="#3b82f6" stroke="#ffffff" strokeWidth="3" />
            </svg>

            <div className="chart-tooltip-badge blue-tooltip">62%</div>

            <div className="chart-x-labels">
              <span>Mon</span>
              <span>Mon</span>
              <span>Tue</span>
              <span>Wed</span>
              <span>Thu</span>
              <span>Fri</span>
              <span>Sat</span>
              <span>Sun</span>
            </div>
          </div>
        </div>

        {/* Station Status Donut Card */}
        <div className="dashboard-card donut-card-wrapper">
          <div className="card-header-flex">
            <h3 className="card-heading">Station Status</h3>
            <button type="button" className="btn-text-link" onClick={() => onNavigate?.('stations')}>View All</button>
          </div>

          <div className="donut-content-layout">
            <div className="donut-chart-box">
              <svg width="150" height="150" viewBox="0 0 150 150" className="donut-svg">
                {/* Background Ring */}
                <circle cx="75" cy="75" r="54" stroke="#f1f5f9" strokeWidth="22" fill="none" />
                {/* Online - Green (71%) */}
                <circle
                  cx="75"
                  cy="75"
                  r="54"
                  stroke="#22c55e"
                  strokeWidth="22"
                  fill="none"
                  strokeDasharray="240 340"
                  strokeDashoffset="0"
                  strokeLinecap="round"
                />
                {/* Busy - Blue (18%) */}
                <circle
                  cx="75"
                  cy="75"
                  r="54"
                  stroke="#3b82f6"
                  strokeWidth="22"
                  fill="none"
                  strokeDasharray="60 340"
                  strokeDashoffset="-245"
                  strokeLinecap="round"
                />
                {/* Offline - Red (11%) */}
                <circle
                  cx="75"
                  cy="75"
                  r="54"
                  stroke="#ef4444"
                  strokeWidth="22"
                  fill="none"
                  strokeDasharray="35 340"
                  strokeDashoffset="-310"
                  strokeLinecap="round"
                />
              </svg>
            </div>

            <div className="donut-legend-list">
              <div className="legend-item">
                <span className="legend-dot green-dot" />
                <span className="legend-name">Online</span>
                <span className="legend-val">40 (71%)</span>
              </div>
              <div className="legend-item">
                <span className="legend-dot blue-dot" />
                <span className="legend-name">Busy</span>
                <span className="legend-val">10 (18%)</span>
              </div>
              <div className="legend-item">
                <span className="legend-dot red-dot" />
                <span className="legend-name">Offline</span>
                <span className="legend-val">6 (11%)</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Bottom Section: Duplicate Station Status Breakdown (Left) & Recent Alerts (Right) */}
      <div className="enterprise-bottom-grid">
        {/* Station Status Breakdown Card */}
        <div className="dashboard-card donut-card-wrapper">
          <div className="card-header-flex">
            <h3 className="card-heading">Station Status</h3>
            <button type="button" className="btn-text-link" onClick={() => onNavigate?.('stations')}>View All</button>
          </div>

          <div className="donut-content-layout">
            <div className="donut-chart-box">
              <svg width="150" height="150" viewBox="0 0 150 150" className="donut-svg">
                <circle cx="75" cy="75" r="54" stroke="#f1f5f9" strokeWidth="22" fill="none" />
                <circle
                  cx="75"
                  cy="75"
                  r="54"
                  stroke="#22c55e"
                  strokeWidth="22"
                  fill="none"
                  strokeDasharray="240 340"
                  strokeDashoffset="0"
                  strokeLinecap="round"
                />
                <circle
                  cx="75"
                  cy="75"
                  r="54"
                  stroke="#3b82f6"
                  strokeWidth="22"
                  fill="none"
                  strokeDasharray="60 340"
                  strokeDashoffset="-245"
                  strokeLinecap="round"
                />
                <circle
                  cx="75"
                  cy="75"
                  r="54"
                  stroke="#ef4444"
                  strokeWidth="22"
                  fill="none"
                  strokeDasharray="35 340"
                  strokeDashoffset="-310"
                  strokeLinecap="round"
                />
              </svg>
            </div>

            <div className="donut-legend-list">
              <div className="legend-item">
                <span className="legend-dot green-dot" />
                <span className="legend-name">Online</span>
                <span className="legend-val">40 (71%)</span>
              </div>
              <div className="legend-item">
                <span className="legend-dot blue-dot" />
                <span className="legend-name">Busy</span>
                <span className="legend-val">10 (18%)</span>
              </div>
              <div className="legend-item">
                <span className="legend-dot red-dot" />
                <span className="legend-name">Offline</span>
                <span className="legend-val">6 (11%)</span>
              </div>
            </div>
          </div>
        </div>

        {/* Recent Alerts Card */}
        <div className="dashboard-card enterprise-alerts-card">
          <div className="card-header-flex">
            <h3 className="card-heading">Recent Alerts</h3>
            <button type="button" className="btn-text-link" onClick={() => onNavigate?.('maintenance')}>View All</button>
          </div>

          <div className="alerts-stack-list">
            <div className="alert-item red-alert-item">
              <div className="alert-icon-box red-icon-bg">
                <AlertCircle size={18} />
              </div>
              <div className="alert-info-text">
                <div className="alert-title">DLF Cyber City Station</div>
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
