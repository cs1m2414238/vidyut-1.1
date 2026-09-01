import type { LucideIcon } from 'lucide-react';
import {
  Activity,
  BatteryCharging,
  ArrowRight,
  Building2,
  CalendarDays,
  Clock3,
  CarFront,
  CircleDollarSign,
  CircleAlert,
  FileBarChart,
  Gauge,
  Bot,
  Bell,
  BadgeIndianRupee,
  BookmarkCheck,
  Gift,
  GraduationCap,
  Headphones,
  History,
  HousePlug,
  HeartPulse,
  LayoutDashboard,
  LogOut,
  MapPinned,
  MessageSquare,
  Navigation,
  Route,
  Settings,
  ShieldCheck,
  Users,
  WalletCards,
  Wrench,
  Zap,
  X,
} from 'lucide-react';
import type { User } from '../types';

type UserRole = 'EV_OWNER' | 'LANDOWNER' | 'COMPANY_ADMIN';

interface SidebarItem {
  icon: LucideIcon;
  label: string;
  id: string;
}

interface SidebarProps {
  active: string;
  onNav: (id: string) => void;
  user: Pick<User, 'name' | 'email'>;
  bookingCount: number;
  onLogout: () => void;
  role?: UserRole;
  open?: boolean;
  onClose?: () => void;
  profileIncomplete?: boolean;
  onCompleteProfile?: () => void;
}

const roleItems: Record<UserRole, SidebarItem[]> = {
  EV_OWNER: [
    { icon: LayoutDashboard, label: 'Dashboard', id: 'dashboard' },
    { icon: Navigation, label: 'Vidyut Autopilot', id: 'autopilot' },
    { icon: MapPinned, label: 'Find Charger', id: 'find' },
    { icon: Route, label: 'Trip Planner', id: 'trip' },
    { icon: CalendarDays, label: 'My Bookings', id: 'bookings' },
    { icon: Zap, label: 'Active Charging', id: 'charging' },
    { icon: History, label: 'Charging History', id: 'history' },
    { icon: WalletCards, label: 'Wallet', id: 'wallet' },
    { icon: CarFront, label: 'My Vehicles', id: 'vehicles' },
    { icon: GraduationCap, label: 'Outlet Access', id: 'outlets' },
    { icon: HousePlug, label: 'Become a Host', id: 'host' },
    { icon: Gift, label: 'Rewards', id: 'rewards' },
    { icon: Bell, label: 'Notifications', id: 'notifications' },
    { icon: Headphones, label: 'Support', id: 'support' },
    { icon: Settings, label: 'Settings', id: 'settings' },
  ],
  LANDOWNER: [
    { icon: LayoutDashboard, label: 'Dashboard', id: 'dashboard' },
    { icon: Building2, label: 'My Properties', id: 'properties' },
    { icon: MapPinned, label: 'Find Companies', id: 'marketplace' },
    { icon: Route, label: 'Installations', id: 'installations' },
    { icon: HousePlug, label: 'My Chargers', id: 'chargers' },
    { icon: Bot, label: 'AI Assistant', id: 'ai' },
    { icon: BadgeIndianRupee, label: 'Offers & Green Finance', id: 'finance' },
    { icon: Clock3, label: 'Availability', id: 'availability' },
    { icon: CalendarDays, label: 'Bookings', id: 'bookings' },
    { icon: CircleDollarSign, label: 'Earnings', id: 'earnings' },
    { icon: HeartPulse, label: 'Monitoring', id: 'monitoring' },
    { icon: MessageSquare, label: 'Reviews', id: 'reviews' },
    { icon: FileBarChart, label: 'Reports', id: 'reports' },
    { icon: Bell, label: 'Notifications', id: 'notifications' },
    { icon: Settings, label: 'Host Profile', id: 'profile' },
  ],
  COMPANY_ADMIN: [
    { icon: LayoutDashboard, label: 'Overview', id: 'dashboard' },
    { icon: Building2, label: 'Stations', id: 'stations' },
    { icon: BatteryCharging, label: 'Chargers', id: 'chargers' },
    { icon: Activity, label: 'Live Monitoring', id: 'monitoring' },
    { icon: CalendarDays, label: 'Bookings & Sessions', id: 'bookings' },
    { icon: Wrench, label: 'Maintenance', id: 'maintenance' },
    { icon: Building2, label: 'Property Marketplace', id: 'host_opportunities' },
    { icon: BookmarkCheck, label: 'Saved Properties', id: 'saved_properties' },
    { icon: Route, label: 'Partnership Projects', id: 'installation_pipeline' },
    { icon: BatteryCharging, label: 'Product Catalogue', id: 'catalog' },
    { icon: BadgeIndianRupee, label: 'Pricing', id: 'pricing' },
    { icon: Gauge, label: 'Analytics', id: 'analytics' },
    { icon: CircleDollarSign, label: 'Revenue & Settlements', id: 'revenue' },
    { icon: MapPinned, label: 'Expansion Intelligence', id: 'expansion' },
    { icon: Bot, label: 'Company Assistant', id: 'ai' },
    { icon: Users, label: 'Staff', id: 'users' },
    { icon: FileBarChart, label: 'Reports', id: 'reports' },
    { icon: Bell, label: 'Notifications', id: 'notifications' },
    { icon: Settings, label: 'Settings', id: 'settings' },
  ],
};

const modeSummary: Record<UserRole, string> = {
  EV_OWNER: 'Personal charging, bookings and vehicle activity',
  LANDOWNER: 'Charger availability, earnings and guest bookings',
  COMPANY_ADMIN: 'Only your company’s network and business data are shown',
};

const workspaceBoundary: Record<UserRole, string> = {
  EV_OWNER: 'Private EV-owner workspace',
  LANDOWNER: 'Private Host workspace',
  COMPANY_ADMIN: 'Private company workspace',
};

export function Sidebar({
  active,
  onNav,
  user,
  bookingCount,
  onLogout,
  role = 'EV_OWNER',
  open = false,
  onClose,
  profileIncomplete = false,
  onCompleteProfile,
}: SidebarProps) {
  const initials = user.name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('');

  const navigate = (id: string) => {
    onNav(id);
    onClose?.();
  };

  return (
    <>
      {open && <button className="drawer-scrim" aria-label="Close navigation" onClick={onClose} />}
      <aside className={`sidebar ${open ? 'open' : ''}`} aria-label="Primary navigation">
        <div className="sidebar-brand">
          <span className="brand-mark">
            <img src="/vidyut-logo.svg" width="44" height="44" alt="" style={{ filter: 'drop-shadow(0 4px 14px rgba(34, 197, 94, 0.65))' }} />
          </span>
          <div>
            <div className="brand-name">VIDYUT</div>
            <div className="brand-tagline">Powering a smarter tomorrow</div>
          </div>
          <button className="icon-button sidebar-close" onClick={onClose} aria-label="Close navigation">
            <X size={18} />
          </button>
        </div>

        <nav className="sidebar-nav">
          {roleItems[role].map(({ icon: Icon, label, id }) => (
            <button
              key={id}
              className={`sidebar-item ${active === id ? 'active' : ''}`}
              onClick={() => navigate(id)}
              aria-current={active === id ? 'page' : undefined}
            >
              <Icon size={18} strokeWidth={1.9} />
              <span className="sidebar-item-label">{label}</span>
              {id === 'bookings' && bookingCount > 0 && (
                <span className="sidebar-badge">{bookingCount}</span>
              )}
            </button>
          ))}
        </nav>

        {profileIncomplete && onCompleteProfile && (
          <button
            type="button"
            className="sidebar-profile-prompt"
            onClick={() => {
              onCompleteProfile();
              onClose?.();
            }}
          >
            <span><CircleAlert size={16} /></span>
            <span><strong>Finish your profile</strong><small>Complete required details</small></span>
            <ArrowRight size={14} />
          </button>
        )}

        <div className="sidebar-context">
          <div className="sidebar-context-top">
            <span className="sidebar-context-dot" />
            <ShieldCheck size={14} />
            {workspaceBoundary[role]}
          </div>
          <p>{modeSummary[role]}</p>
        </div>

        <div className="sidebar-user">
          <div className="sidebar-avatar">{initials || 'V'}</div>
          <div className="sidebar-user-copy">
            <div className="sidebar-user-name" title={user.name}>{user.name}</div>
            <div className="sidebar-user-email" title={user.email}>{user.email}</div>
          </div>
          <button
            className="icon-button logout-btn"
            title="Log out"
            aria-label="Log out"
            onClick={onLogout}
          >
            <LogOut size={17} />
          </button>
        </div>
      </aside>
    </>
  );
}
