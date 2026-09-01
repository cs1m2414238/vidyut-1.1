import { useState } from 'react';
import type { LucideIcon } from 'lucide-react';
import {
  Activity,
  ArrowUpRight,
  BatteryCharging,
  Building2,
  CalendarClock,
  CarFront,
  CheckCircle2,
  CircleDollarSign,
  Clock3,
  CreditCard,
  FileBarChart,
  Gift,
  Headphones,
  HousePlug,
  MapPin,
  Plus,
  Settings,
  ShieldCheck,
  Star,
  TrendingUp,
  Users,
  WalletCards,
  Wrench,
  Zap,
} from 'lucide-react';

type UserRole = 'EV_OWNER' | 'LANDOWNER' | 'COMPANY_ADMIN';

interface FeatureViewProps {
  role: UserRole;
  tab: string;
}

interface FeatureConfig {
  title: string;
  subtitle: string;
  action: string;
  icon: LucideIcon;
  metrics: Array<{ value: string; label: string; icon: LucideIcon }>;
  rows: Array<{ title: string; subtitle: string; status: string; icon: LucideIcon }>;
  tip: string;
}

const configs: Record<string, FeatureConfig> = {
  'EV_OWNER:history': feature('Charging history', 'Review energy use, cost and receipts from completed sessions.', 'Download statement', Clock3, [
    ['42', 'Sessions this year', BatteryCharging], ['318 kWh', 'Energy delivered', Zap], ['₹5,840', 'Total charging cost', WalletCards],
  ], [['Green Park Station', 'Today · 12.45 kWh · 42 min', 'Completed', CheckCircle2], ['Cyber Hub Station', '22 May · 18.1 kWh · 58 min', 'Completed', CheckCircle2], ['MG Road Station', '18 May · 8.7 kWh · 31 min', 'Receipt ready', FileBarChart]], 'Your overnight sessions cost 18% less than daytime charging.'),
  'EV_OWNER:wallet': feature('Wallet & payments', 'Manage balance, payment methods and charging transactions.', 'Add money', WalletCards, [
    ['₹2,840', 'Available balance', WalletCards], ['₹1,250', 'Spent this month', CircleDollarSign], ['₹180', 'Rewards earned', Gift],
  ], [['Wallet top-up', 'UPI · Today, 9:42 AM', '+ ₹1,000', CreditCard], ['Green Park Station', 'Charging payment · Today', '₹285.60', Zap], ['Auto-pay method', 'Visa ending 4242', 'Active', ShieldCheck]], 'Turn on auto top-up so a low balance never interrupts a charging session.'),
  'EV_OWNER:vehicles': feature('My vehicles', 'Keep battery and connector information ready for smarter recommendations.', 'Add vehicle', CarFront, [
    ['1', 'Connected vehicle', CarFront], ['68%', 'Current battery', BatteryCharging], ['246 km', 'Estimated range', Activity],
  ], [['Tata Nexon EV Max', 'CCS2 · 40.5 kWh battery', 'Primary', CarFront], ['Battery health', 'Last diagnostic completed today', '96% good', Activity], ['Charging preference', 'Stop charging automatically', 'At 85%', Settings]], 'Add your vehicle model to filter stations by connector and charging speed.'),
  'EV_OWNER:host': feature('Become a charger host', 'Add a second approved mode without creating another account.', 'Start application', HousePlug, [
    ['3 steps', 'Application process', ShieldCheck], ['24–48 h', 'Typical review', Clock3], ['₹8–14K', 'Est. monthly potential', TrendingUp],
  ], [['Identity & address', 'Confirm the account owner and installation address', 'Ready', ShieldCheck], ['Charger details', 'Power, connector and availability schedule', 'Next', HousePlug], ['Admin verification', 'Safety and listing review by Vidyut', 'Pending', Clock3]], 'Your EV Owner workspace remains separate. Host access appears only after approval.'),
  'EV_OWNER:rewards': feature('Rewards', 'Track charging streaks, referrals and available benefits.', 'Invite a friend', Gift, [
    ['1,840', 'Vidyut points', Gift], ['Silver', 'Current tier', Star], ['₹180', 'Redeemable value', WalletCards],
  ], [['Off-peak explorer', 'Charge 3 times after 10 PM', '2 of 3', Zap], ['Green driver', 'Save 100 kg of CO₂', 'Completed', CheckCircle2], ['Refer & earn', 'Share Vidyut with another EV owner', '₹150', Users]], 'One more off-peak session unlocks a 15% charging voucher.'),
  'EV_OWNER:support': feature('Help & support', 'Get fast help for bookings, chargers, payments and safety.', 'Start a chat', Headphones, [
    ['< 2 min', 'Average reply', Clock3], ['24 × 7', 'Safety assistance', ShieldCheck], ['4.8/5', 'Support rating', Star],
  ], [['Active charging help', 'Stop, restart or report a charging session', 'Priority', Zap], ['Booking & refund', 'Change a slot or review refund status', 'Self-service', CalendarClock], ['Report a charger', 'Send location and fault details', 'Open', Wrench]], 'For an electrical or safety issue, stop charging and use priority support.'),
  'EV_OWNER:settings': settingsFeature('Personal settings'),

  'LANDOWNER:bookings': feature('Charger bookings', 'Manage upcoming guests and the reservation schedule.', 'Block time', CalendarClock, [
    ['8', 'Bookings today', CalendarClock], ['6.4 h', 'Reserved time', Clock3], ['₹1,250', 'Expected earnings', CircleDollarSign],
  ], [['Rahul Sharma', 'Today, 11:00 AM · 2h 30m', 'Confirmed', Users], ['Neha Verma', 'Today, 4:00 PM · 1h 45m', 'Confirmed', Users], ['Arjun Mehta', 'Tomorrow, 9:30 AM · 1h', 'Pending', Clock3]], 'Keep a 15-minute buffer between bookings for a smoother handover.'),
  'LANDOWNER:my_charger': feature('My chargers', 'Control availability, pricing and operational status.', 'Add charger', HousePlug, [
    ['1', 'Listed charger', HousePlug], ['Online', 'Network status', Activity], ['7.4 kW', 'Power output', Zap],
  ], [['Home Charger', 'AC Type 2 · ₹16/kWh', 'Online', BatteryCharging], ['Availability', 'Daily · 6:00 AM to 11:00 PM', 'Open', Clock3], ['Safety inspection', 'Next review due 12 September', 'Valid', ShieldCheck]], 'Updating availability immediately prevents new reservations during private use.'),
  'LANDOWNER:earnings': feature('Earnings', 'Understand revenue across completed charging sessions.', 'Export earnings', CircleDollarSign, [
    ['₹28,540', 'This month', CircleDollarSign], ['₹1,250', 'Today', TrendingUp], ['₹17.80', 'Average per kWh', Zap],
  ], [['This week', '32 completed charging sessions', '₹8,420', TrendingUp], ['Last week', '29 completed charging sessions', '₹7,680', TrendingUp], ['Platform fee', 'Current statement period', '₹1,142', FileBarChart]], 'Extending evening availability could add an estimated ₹2,100 monthly.'),
  'LANDOWNER:analytics': analyticsFeature('Charger analytics', 'Review utilization, booking patterns and energy delivery.'),
  'LANDOWNER:payouts': feature('Payouts', 'Track settlement cycles and bank transfers.', 'Manage bank', CreditCard, [
    ['₹7,860', 'Next payout', WalletCards], ['12 Jun', 'Settlement date', CalendarClock], ['₹20,680', 'Paid this month', CheckCircle2],
  ], [['Settlement VY-8821', '1–7 June · 28 sessions', 'Processing', Clock3], ['Settlement VY-8740', '25–31 May · 31 sessions', 'Paid', CheckCircle2], ['Bank account', 'HDFC Bank ending 2840', 'Verified', ShieldCheck]], 'Payouts are initiated every Monday after completed-session reconciliation.'),
  'LANDOWNER:reviews': feature('Guest reviews', 'Learn what EV drivers say about your charging experience.', 'Reply to reviews', Star, [
    ['4.8', 'Average rating', Star], ['86', 'Total reviews', Users], ['96%', 'Positive feedback', TrendingUp],
  ], [['Rahul Sharma', 'Easy to find and the charger worked perfectly.', '5.0', Star], ['Neha Verma', 'Clean parking and quick host response.', '4.8', Star], ['Amit Patel', 'Clear instructions and reliable charging.', '4.6', Star]], 'Hosts who reply within a day receive 12% more repeat bookings.'),
  'LANDOWNER:settings': settingsFeature('Host settings'),

  'COMPANY_ADMIN:stations': feature('Stations', 'Monitor every location in your company network.', 'Add station', Building2, [
    ['56', 'Total stations', Building2], ['40', 'Online', CheckCircle2], ['6', 'Needs attention', Wrench],
  ], [['DLF Cyber City', 'Gurugram · 12 chargers', 'Online', Building2], ['MG Road Hub', 'Bengaluru · 8 chargers', 'Online', Building2], ['Airport Terminal 3', 'New Delhi · 16 chargers', 'Maintenance', Wrench]], 'Six offline stations account for most of this week’s lost availability.'),
  'COMPANY_ADMIN:chargers': feature('Chargers', 'Track connector health, firmware and utilization.', 'Provision charger', BatteryCharging, [
    ['312', 'Total chargers', BatteryCharging], ['249', 'Available', CheckCircle2], ['10', 'Busy now', Activity],
  ], [['DC-CCS2-1024', 'DLF Cyber City · 150 kW', 'Charging', Zap], ['AC-T2-0844', 'MG Road Hub · 22 kW', 'Available', CheckCircle2], ['DC-CCS2-0711', 'Airport T3 · 120 kW', 'Offline', Wrench]], 'Schedule firmware updates during the 2–4 AM low-utilization window.'),
  'COMPANY_ADMIN:analytics': analyticsFeature('Network analytics', 'Compare utilization, uptime and charging demand across regions.'),
  'COMPANY_ADMIN:revenue': feature('Revenue', 'Review network earnings, tariffs and settlement performance.', 'Export report', CircleDollarSign, [
    ['₹18.4L', 'Monthly revenue', CircleDollarSign], ['+12.8%', 'Month-on-month', TrendingUp], ['₹286', 'Avg. session value', Zap],
  ], [['North region', '18 stations · 41% of revenue', '₹7.54L', MapPin], ['South region', '22 stations · 36% of revenue', '₹6.62L', MapPin], ['West region', '16 stations · 23% of revenue', '₹4.24L', MapPin]], 'Dynamic tariffs at high-demand stations may improve margin by 4–6%.'),
  'COMPANY_ADMIN:maintenance': feature('Maintenance', 'Prioritize faults, inspections and field work.', 'Create work order', Wrench, [
    ['6', 'Open incidents', Wrench], ['2', 'Critical', Activity], ['94%', 'Network uptime', CheckCircle2],
  ], [['Charger #12 not responding', 'DLF Cyber City · opened 10 min ago', 'Critical', Wrench], ['Cooling inspection', 'Airport Terminal 3 · due today', 'Scheduled', CalendarClock], ['Connector replacement', 'MG Road Hub · technician assigned', 'In progress', Activity]], 'Two critical incidents need field acknowledgement within the next 20 minutes.'),
  'COMPANY_ADMIN:users': feature('Network users', 'Manage team access and operational responsibilities.', 'Invite user', Users, [
    ['84', 'Active users', Users], ['12', 'Operations managers', ShieldCheck], ['5', 'Pending invites', Clock3],
  ], [['Riya Khanna', 'Regional operations · North', 'Active', Users], ['Arvind Rao', 'Maintenance lead · South', 'Active', Users], ['Meera Shah', 'Finance analyst · All regions', 'Invited', Clock3]], 'Use least-privilege team roles so each person sees only the operations they manage.'),
  'COMPANY_ADMIN:reports': feature('Reports', 'Generate operational, revenue and compliance reports.', 'Create report', FileBarChart, [
    ['18', 'Saved reports', FileBarChart], ['6', 'Scheduled', CalendarClock], ['Today', 'Last generated', CheckCircle2],
  ], [['Network performance', 'Weekly · PDF and CSV', 'Scheduled', FileBarChart], ['Revenue summary', 'Monthly · Finance team', 'Ready', CircleDollarSign], ['Uptime compliance', 'Quarterly · Operations', 'Draft', ShieldCheck]], 'Schedule reports once and deliver them automatically to approved stakeholders.'),
  'COMPANY_ADMIN:settings': settingsFeature('Company settings'),
};

function feature(
  title: string,
  subtitle: string,
  action: string,
  icon: LucideIcon,
  metrics: Array<[string, string, LucideIcon]>,
  rows: Array<[string, string, string, LucideIcon]>,
  tip: string,
): FeatureConfig {
  return {
    title, subtitle, action, icon,
    metrics: metrics.map(([value, label, metricIcon]) => ({ value, label, icon: metricIcon })),
    rows: rows.map(([rowTitle, rowSubtitle, status, rowIcon]) => ({ title: rowTitle, subtitle: rowSubtitle, status, icon: rowIcon })),
    tip,
  };
}

function settingsFeature(title: string) {
  return feature(title, 'Control notifications, security and workspace preferences.', 'Save preferences', Settings, [
    ['Enabled', 'Login alerts', ShieldCheck], ['Instant', 'Push notifications', Activity], ['English', 'Language', Settings],
  ], [['Security', 'Password, sessions and login alerts', 'Protected', ShieldCheck], ['Notifications', 'Bookings, charging and account updates', 'On', Activity], ['Privacy', 'Data sharing and communication preferences', 'Review', Settings]], 'You can review or sign out active sessions at any time from security settings.');
}

function analyticsFeature(title: string, subtitle: string) {
  return feature(title, subtitle, 'Download analytics', Activity, [
    ['62%', 'Utilization', Activity], ['+8.4%', 'This period', TrendingUp], ['98.2%', 'Successful sessions', CheckCircle2],
  ], [['Peak demand', 'Weekdays · 6:00 PM to 9:00 PM', '62%', TrendingUp], ['Session success', 'Completed without interruption', '98.2%', CheckCircle2], ['Average duration', 'Across selected period', '46 min', Clock3]], 'Demand forecasting is strongest when chargers report at least 30 days of uptime data.');
}

export function FeatureView({ role, tab }: FeatureViewProps) {
  const [notice, setNotice] = useState('');
  const config = configs[`${role}:${tab}`] ?? feature(
    'Workspace',
    'This feature belongs to your currently selected workspace.',
    'Create new',
    Plus,
    [['Active', 'Workspace status', CheckCircle2], ['Protected', 'Mode scope', ShieldCheck], ['Live', 'Data connection', Activity]],
    [['Workspace access', 'Your account is authorized for this mode', 'Active', ShieldCheck]],
    'Switch modes only when you need to work in another authorized workspace.',
  );
  const HeaderIcon = config.icon;

  return (
    <section className="feature-page">
      <header className="feature-header">
        <div>
          <div className="feature-eyebrow">Vidyut workspace</div>
          <h1>{config.title}</h1>
          <p>{config.subtitle}</p>
        </div>
        <button className="feature-primary" type="button" onClick={() => setNotice(`${config.action} flow opened for ${config.title.toLowerCase()}.`)}><HeaderIcon size={16} /> {config.action}</button>
      </header>

      {notice && <div className="feature-notice" role="status">{notice}</div>}

      <div className="feature-metrics">
        {config.metrics.map(({ value, label, icon: Icon }) => (
          <article className="feature-metric" key={label}>
            <span className="feature-metric-icon"><Icon size={18} /></span>
            <div className="feature-metric-value">{value}</div>
            <div className="feature-metric-label">{label}</div>
          </article>
        ))}
      </div>

      <div className="feature-layout">
        <article className="feature-panel">
          <div className="feature-panel-head"><h2>Overview</h2><span>Updated just now</span></div>
          <ul className="feature-list">
            {config.rows.map(({ title, subtitle, status, icon: Icon }) => (
              <li className="feature-row" key={title}>
                <span className="feature-row-icon"><Icon size={17} /></span>
                <div className="feature-row-copy">
                  <div className="feature-row-title">{title}</div>
                  <div className="feature-row-sub">{subtitle}</div>
                </div>
                <span className="feature-status">{status}</span>
              </li>
            ))}
          </ul>
        </article>

        <aside className="feature-tip">
          <Zap size={22} />
          <h3>Vidyut insight</h3>
          <p>{config.tip}</p>
          <button type="button" onClick={() => setNotice('Recommendation saved to your workspace activity.')}>View recommendation <ArrowUpRight size={13} /></button>
        </aside>
      </div>
    </section>
  );
}
