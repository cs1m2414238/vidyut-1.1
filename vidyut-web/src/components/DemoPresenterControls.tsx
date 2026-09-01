import { useState } from 'react';
import {
  CarFront,
  HousePlug,
  Building2,
  AlertTriangle,
  ChevronDown,
  ChevronUp,
  Sparkles,
  CheckCircle2,
  Navigation,
  Wrench,
  TrendingUp,
} from 'lucide-react';
import type { AccessMode } from '../services/api';
import './DemoPresenterControls.css';

interface DemoPresenterControlsProps {
  activeMode: AccessMode;
  activeTab?: string;
  onSwitchPersona: (mode: AccessMode, targetTab: string) => Promise<void> | void;
  hasIncident?: boolean;
}

export function DemoPresenterControls({
  activeMode,
  activeTab: _activeTab,
  onSwitchPersona,
  hasIncident = false,
}: DemoPresenterControlsProps) {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <aside
      className={`demo-presenter-bar ${collapsed ? 'collapsed' : ''}`}
      aria-label="Demo Presenter Controls"
    >
      <div className="demo-presenter-inner">
        <div className="demo-presenter-brand">
          <div className="presenter-tag">
            <Sparkles size={13} className="sparkle-icon" />
            <span>DEMO PRESENTER CONTROLS</span>
          </div>
          <span className="presenter-title">Multi-Agent Presentation Mode</span>
          <span className="corridor-tag">Delhi → Bhopal Corridor (780 km)</span>
          {hasIncident ? (
            <span className="incident-status-pill incident-active">
              <AlertTriangle size={12} />
              <span>Incident Synchronized (3 Agents)</span>
            </span>
          ) : (
            <span className="incident-status-pill incident-idle">
              <CheckCircle2 size={12} />
              <span>All Systems Nominal</span>
            </span>
          )}
        </div>

        {!collapsed && (
          <div className="demo-persona-switchers">
            <span className="switchers-label">Switch Persona:</span>
            <button
              type="button"
              className={`persona-btn driver ${activeMode === 'EV_USER' ? 'active' : ''}`}
              onClick={() => void onSwitchPersona('EV_USER', 'autopilot')}
              title="Priyanshu Sharma · EV Driver (Delhi → Bhopal)"
            >
              <CarFront size={15} />
              <div className="persona-info">
                <strong>Driver (Priyanshu)</strong>
                <small>Delhi → Bhopal · Autopilot</small>
              </div>
              {activeMode === 'EV_USER' && <span className="active-dot" />}
            </button>

            <button
              type="button"
              className={`persona-btn host ${activeMode === 'HOST' ? 'active' : ''}`}
              onClick={() => void onSwitchPersona('HOST', 'ai')}
              title="Prince · Highway Property Owner (Dausa Station)"
            >
              <HousePlug size={15} />
              <div className="persona-info">
                <strong>Host (Prince)</strong>
                <small>Dausa Property · Host Copilot</small>
              </div>
              {activeMode === 'HOST' && <span className="active-dot" />}
            </button>

            <button
              type="button"
              className={`persona-btn company ${activeMode === 'COMPANY' ? 'active' : ''}`}
              onClick={() => void onSwitchPersona('COMPANY', 'ai')}
              title="Tata Power · Charging Network Operator (Demo)"
            >
              <Building2 size={15} />
              <div className="persona-info">
                <strong>Company (Tata Demo)</strong>
                <small>CPO Operations & Expansion</small>
              </div>
              {activeMode === 'COMPANY' && <span className="active-dot" />}
            </button>
          </div>
        )}

        <div className="demo-presenter-actions">
          <button
            type="button"
            className="presenter-toggle-btn"
            onClick={() => setCollapsed(!collapsed)}
            aria-label={collapsed ? 'Expand Presenter Controls' : 'Collapse Presenter Controls'}
          >
            {collapsed ? <ChevronDown size={16} /> : <ChevronUp size={16} />}
            <span>{collapsed ? 'Show Presenter Bar' : 'Hide'}</span>
          </button>
        </div>
      </div>

      {!collapsed && (
        <div className="demo-story-steps">
          <span className="story-step-item">
            <span className="step-num">1</span>
            <Navigation size={12} />
            <span>Driver approaches Dausa (39% SOC)</span>
          </span>
          <span className="story-arrow">→</span>
          <span className="story-step-item">
            <span className="step-num">2</span>
            <AlertTriangle size={12} />
            <span>Charger issue reported · Suspected fault</span>
          </span>
          <span className="story-arrow">→</span>
          <span className="story-step-item">
            <span className="step-num">3</span>
            <CarFront size={12} />
            <span>Driver dynamic bridge recovery & replan</span>
          </span>
          <span className="story-arrow">→</span>
          <span className="story-step-item">
            <span className="step-num">4</span>
            <Wrench size={12} />
            <span>Host requests service from Tata</span>
          </span>
          <span className="story-arrow">→</span>
          <span className="story-step-item">
            <span className="step-num">5</span>
            <TrendingUp size={12} />
            <span>Tata assigns tech + expands to Rahul's site</span>
          </span>
        </div>
      )}
    </aside>
  );
}
