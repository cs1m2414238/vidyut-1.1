import { useCallback, useState, useEffect } from 'react';
import { ArrowRight, Building2, CarFront, CircleAlert, HousePlug, UserRound } from 'lucide-react';
import { Sidebar } from './components/Sidebar';
import { TopBar } from './components/TopBar';
import type { TopBarLocation } from './components/TopBar';
import { MapView } from './components/MapView';
import { ChargerDetailModal } from './components/ChargerDetailModal';
import { BookingsView } from './components/BookingsView';
import { FeatureView } from './components/FeatureView';
import { WalletView } from './components/WalletView';
import { VehiclesView } from './components/VehiclesView';
import { VehicleDetailView } from './components/VehicleDetailView';
import { CompanyWorkspace } from './components/CompanyWorkspace';
import { HostWorkspace } from './components/HostWorkspace';
import { AutopilotView } from './components/AutopilotView';
import { TripPlannerView } from './components/TripPlannerView';
import { OwnerNotificationsView } from './components/OwnerNotificationsView';
import { ChargingSessionView } from './components/ChargingSessionView';
import { OutletAccessView } from './components/OutletAccessView';
import { ModeSelection } from './components/ModeSelection';
import { mockUser, mockChargers } from './data/mockData';
import type { Charger, User } from './types';
import LoginPage from './components/LoginPage';
import SplashScreen from './components/SplashScreen';
import LandingPage from './components/LandingPage';
import VidyutRegisterPage from './components/RegisterPage';
import { CompleteProfileModal } from './components/CompleteProfileModal';
import { AdminPortal } from './components/AdminPortal';
import {
  AUTH_SESSION_EXPIRED_EVENT,
  clearAuthSession,
  loadAuthSession,
  saveAuthSession,
  switchAuthMode,
} from './services/api';
import type { AccessMode, AuthData } from './services/api';
import { createBooking, getUnreadBookingCount } from './services/bookings';
import { getStations, stationToCharger } from './services/stations';
import type { StationViewportBounds } from './services/stations';
import { getVehicles } from './services/vehicles';
import type { Vehicle } from './services/vehicles';
import { getEvNotifications } from './services/notifications';
import type { EvNotification } from './services/notifications';
import { EVOwnerDashboard } from './components/dashboards/EVOwnerDashboard';
import './components/dashboards/Dashboards.css';
import './components/DashboardShell.css';

type AppView = 'splash' | 'landing' | 'login' | 'register' | 'mode-select' | 'dashboard';
type UserRole = 'EV_OWNER' | 'LANDOWNER' | 'COMPANY_ADMIN';

function roleForMode(mode: AccessMode): UserRole {
  if (mode === 'HOST') return 'LANDOWNER';
  if (mode === 'COMPANY') return 'COMPANY_ADMIN';
  return 'EV_OWNER';
}

function userFromAuth(auth: AuthData, fallback: User = mockUser): User {
  return {
    ...fallback,
    id: String(auth.user.id),
    name: auth.user.fullName,
    email: auth.user.email,
    phone: auth.user.phone || '',
    contactName: auth.user.contactName,
    companyName: auth.user.companyName,
    registrationNumber: auth.user.registrationNumber,
    profileCompleted: auth.user.profileCompleted,
    accountType: auth.user.accountType,
    emailVerified: auth.user.emailVerified,
    hostStatus: auth.user.hostStatus,
  };
}

function isProfileIncomplete(user: User, activeMode: AccessMode): boolean {
  if (activeMode === 'ADMIN') return false;
  if (user.profileCompleted === false) return true;
  if (!/^(?:91)?\d{10}$/.test((user.phone || '').replace(/\D/g, ''))) return true;
  if (activeMode === 'COMPANY' && (!user.companyName?.trim() || !user.registrationNumber?.trim())) return true;
  return false;
}

function getHashForState(view: AppView, tab: string): string {
  if (view === 'login') return '#/login';
  if (view === 'register') return '#/register';
  if (view === 'mode-select') return '#/mode-select';
  if (view === 'landing' || view === 'splash') return '#/landing';
  if (view === 'dashboard') {
    return tab === 'dashboard' ? '#/dashboard' : `#/dashboard/${tab}`;
  }
  return '#/';
}

function parseStateFromHash(hash: string): { view: AppView; tab: string } {
  const clean = hash.replace(/^#\/?/, '').trim();
  if (clean === 'login') return { view: 'login', tab: 'dashboard' };
  if (clean === 'register') return { view: 'register', tab: 'dashboard' };
  if (clean === 'mode-select') return { view: 'mode-select', tab: 'dashboard' };
  if (clean === 'landing' || clean === '') return { view: 'landing', tab: 'dashboard' };
  if (clean.startsWith('dashboard')) {
    const parts = clean.split('/');
    const tab = parts.slice(1).join('/') || 'dashboard';
    return { view: 'dashboard', tab };
  }
  return { view: 'landing', tab: 'dashboard' };
}

function MainApplication() {
  const [restoredSession] = useState<AuthData | null>(() => loadAuthSession());
  const [currentView, setCurrentView] = useState<AppView>(() => {
    if (window.location.hash) {
      return parseStateFromHash(window.location.hash).view;
    }
    if (restoredSession) return 'dashboard';
    return sessionStorage.getItem('vidyut_splash_shown') === 'true' ? 'landing' : 'splash';
  });
  const [activeTab, setActiveTab] = useState(() => {
    if (window.location.hash) {
      return parseStateFromHash(window.location.hash).tab;
    }
    return 'dashboard';
  });
  const [selectedCharger, setSelectedCharger] = useState<Charger | null>(null);
  const [mapFilter, setMapFilter] = useState<'all' | 'available' | 'fast'>('available');
  const [chargers, setChargers] = useState<Charger[]>(mockChargers);
  const [user, setUser] = useState<User>(() => restoredSession ? userFromAuth(restoredSession) : mockUser);
  const [isLoggedIn, setIsLoggedIn] = useState(Boolean(restoredSession));
  const [bookingUnreadCount, setBookingUnreadCount] = useState(0);
  const [bookingRefreshKey, setBookingRefreshKey] = useState(0);
  const [companyCounts, setCompanyCounts] = useState({ bookings: 0, notifications: 0 });
  const [hostCounts, setHostCounts] = useState({ bookings: 0, notifications: 0 });
  const [modalCharger, setModalCharger] = useState<Charger | null>(null);
  const [activeMode, setActiveMode] = useState<AccessMode>(() => restoredSession?.activeMode || 'EV_USER');
  const [activeDashboardRole, setActiveDashboardRole] = useState<UserRole>(() => roleForMode(restoredSession?.activeMode || 'EV_USER'));
  const [authToken, setAuthToken] = useState(restoredSession?.token || '');
  const [allowedModes, setAllowedModes] = useState<AccessMode[]>(() => restoredSession?.user.allowedModes || []);
  const [modeError, setModeError] = useState('');
  const [loadingMode, setLoadingMode] = useState<AccessMode | null>(null);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [showProfileModal, setShowProfileModal] = useState(false);
  const [profileModalDismissed, setProfileModalDismissed] = useState(false);
  const [primaryVehicle, setPrimaryVehicle] = useState<Vehicle | null>(null);
  const [ownerNotifications, setOwnerNotifications] = useState<EvNotification[]>([]);
  const [ownerNotificationsLoading, setOwnerNotificationsLoading] = useState(false);
  const [ownerNotificationsError, setOwnerNotificationsError] = useState('');
  const [chargerSearchQuery, setChargerSearchQuery] = useState('');
  const [chargerMapCenter, setChargerMapCenter] = useState<[number, number]>([26.8467, 80.9462]);
  const [stationViewport, setStationViewport] = useState<StationViewportBounds>({
    minLat: 24.5,
    maxLat: 29.2,
    minLng: 77.5,
    maxLng: 84.4,
  });
  const [chargerDataOffline, setChargerDataOffline] = useState(false);

  const refreshOwnerNotifications = useCallback(async () => {
    if (!authToken) return;
    setOwnerNotificationsLoading(true);
    setOwnerNotificationsError('');
    try {
      setOwnerNotifications(await getEvNotifications(authToken));
    } catch (error) {
      setOwnerNotificationsError(error instanceof Error ? error.message : 'Unable to load notifications.');
    } finally {
      setOwnerNotificationsLoading(false);
    }
  }, [authToken]);

  // Sync state with browser URL history stack
  const navigateToState = (newView: AppView, newTab: string = activeTab, replace: boolean = false) => {
    const newHash = getHashForState(newView, newTab);
    if (window.location.hash !== newHash) {
      if (replace) {
        window.history.replaceState(null, '', newHash);
      } else {
        window.history.pushState(null, '', newHash);
      }
    }
    setCurrentView(newView);
    setActiveTab(newTab);
  };

  useEffect(() => {
    const returnToLogin = () => {
      clearAuthSession();
      setAuthToken('');
      setAllowedModes([]);
      setIsLoggedIn(false);
      setActiveMode('EV_USER');
      setActiveDashboardRole('EV_OWNER');
      setUser(mockUser);
      setBookingUnreadCount(0);
      setBookingRefreshKey(0);
      setChargers(mockChargers);
      setPrimaryVehicle(null);
      setOwnerNotifications([]);
      setOwnerNotificationsError('');
      setChargerSearchQuery('');
      setChargerDataOffline(false);
      setSidebarOpen(false);
      setShowProfileModal(false);
      setCurrentView('login');
      setActiveTab('dashboard');
      window.history.replaceState(null, '', '#/login');
    };

    window.addEventListener(AUTH_SESSION_EXPIRED_EVENT, returnToLogin);
    return () => window.removeEventListener(AUTH_SESSION_EXPIRED_EVENT, returnToLogin);
  }, []);

  // Browser Back/Forward button listener
  useEffect(() => {
    const handlePopState = () => {
      const parsed = parseStateFromHash(window.location.hash);
      setCurrentView(parsed.view);
      setActiveTab(parsed.tab);
    };

    window.addEventListener('popstate', handlePopState);
    window.addEventListener('hashchange', handlePopState);

    // Ensure URL hash is synced on initial mount
    const initialHash = getHashForState(currentView, activeTab);
    if (!window.location.hash && initialHash !== '#/') {
      window.history.replaceState(null, '', initialHash);
    }

    return () => {
      window.removeEventListener('popstate', handlePopState);
      window.removeEventListener('hashchange', handlePopState);
    };
  }, [activeTab, currentView]);

  useEffect(() => {
    if (!isLoggedIn || activeMode !== 'EV_USER' || !authToken) return undefined;
    let ignore = false;

    void refreshOwnerNotifications();

    const refreshStations = async () => {
      try {
        const mapped = (await getStations(stationViewport, 250)).map(stationToCharger);
        if (ignore) return;
        setChargers(mapped); setChargerDataOffline(false);
        localStorage.setItem('vidyut:last-known-chargers', JSON.stringify({ savedAt: Date.now(), chargers: mapped }));
      } catch {
        if (ignore) return;
        setChargerDataOffline(true);
        try {
          const cached = JSON.parse(localStorage.getItem('vidyut:last-known-chargers') || 'null') as { chargers?: Charger[] } | null;
          if (cached?.chargers?.length) setChargers(cached.chargers);
        } catch { /* A damaged cache should never break discovery. */ }
      }
    };

    void refreshStations();
    const stationPoll = window.setInterval(() => void refreshStations(), 30_000);
    void Promise.allSettled([
      getUnreadBookingCount(authToken),
      getVehicles(authToken),
    ]).then(([bookingCountResult, vehicleResult]) => {
      if (ignore) return;
      if (bookingCountResult.status === 'fulfilled') {
        setBookingUnreadCount(bookingCountResult.value);
      }
      if (vehicleResult.status === 'fulfilled') {
        const preferred = vehicleResult.value.find((vehicle) => vehicle.charging)
          || vehicleResult.value.find((vehicle) => vehicle.connectionStatus === 'CONNECTED')
          || vehicleResult.value[0]
          || null;
        setPrimaryVehicle(preferred);
      }
    });

    return () => {
      ignore = true;
      window.clearInterval(stationPoll);
    };
  }, [isLoggedIn, activeMode, authToken, refreshOwnerNotifications, stationViewport]);

  const handleStationBoundsChange = useCallback((bounds: StationViewportBounds) => {
    const rounded: StationViewportBounds = {
      minLat: Number(bounds.minLat.toFixed(5)),
      maxLat: Number(bounds.maxLat.toFixed(5)),
      minLng: Number(bounds.minLng.toFixed(5)),
      maxLng: Number(bounds.maxLng.toFixed(5)),
    };
    setStationViewport((current) =>
      current.minLat === rounded.minLat && current.maxLat === rounded.maxLat
        && current.minLng === rounded.minLng && current.maxLng === rounded.maxLng
        ? current : rounded);
  }, []);

  const applyAuthenticatedSession = (auth: AuthData, promptForMode: boolean) => {
    const modes = auth.user.allowedModes?.length ? auth.user.allowedModes : [auth.user.defaultMode || auth.activeMode];
    saveAuthSession(auth);
    setUser((current) => userFromAuth(auth, current));
    setAllowedModes(modes);
    setAuthToken(auth.token);
    setActiveMode(auth.activeMode);
    setActiveDashboardRole(roleForMode(auth.activeMode));
    setModeError('');
    setIsLoggedIn(true);
    setProfileModalDismissed(false);
    setShowProfileModal(false);
    const targetView: AppView = promptForMode && modes.length > 1 ? 'mode-select' : 'dashboard';
    navigateToState(targetView, 'dashboard');
  };

  const handleSplashComplete = () => {
    sessionStorage.setItem('vidyut_splash_shown', 'true');
    navigateToState('landing', 'dashboard', true);
  };

  const handleSelectCharger = (charger: Charger) => {
    setSelectedCharger(charger);
    setModalCharger(charger);
  };


  const handleConfirmBooking = async (charger: Charger, durationMinutes: number, startTime?: string) => {
    if (!authToken) throw new Error('Please sign in again before booking a charger.');
    if (!primaryVehicle) throw new Error('Add a vehicle before booking so connector compatibility and payment use the correct EV.');
    await createBooking(authToken, {
      stationId: charger.id,
      vehicleId: primaryVehicle.id,
      startTime: startTime || new Date().toISOString(),
      durationMinutes,
    });
    setBookingUnreadCount((current) => current + 1);
    setBookingRefreshKey((current) => current + 1);
    setUser((current) => ({
      ...current,
      totalBookings: current.totalBookings + 1,
    }));
  };

  const switchMode = async (mode: AccessMode, closeChooser = false) => {
    if (!authToken || loadingMode) return;
    if (mode === activeMode) {
      if (closeChooser) navigateToState('dashboard', activeTab);
      return;
    }

    try {
      setLoadingMode(mode);
      setModeError('');
      const auth = await switchAuthMode(mode, authToken);
      applyAuthenticatedSession(auth, false);
    } catch (error) {
      setModeError(error instanceof Error ? error.message : 'Unable to switch workspace.');
    } finally {
      setLoadingMode(null);
    }
  };

  const openLogin = (destination: 'dashboard' | 'find') => {
    navigateToState('login', destination);
  };

  const handleLogout = () => {
    clearAuthSession();
    setAuthToken('');
    setAllowedModes([]);
    setIsLoggedIn(false);
    setBookingUnreadCount(0);
    setBookingRefreshKey(0);
    setChargers(mockChargers);
    setPrimaryVehicle(null);
    setOwnerNotifications([]);
    setOwnerNotificationsError('');
    setChargerSearchQuery('');
    setSidebarOpen(false);
    navigateToState('landing', 'dashboard', true);
  };

  if (currentView === 'splash') {
    return <SplashScreen onComplete={handleSplashComplete} />;
  }

  if (!isLoggedIn) {
    if (currentView === 'landing') {
      return (
        <LandingPage
          onLogin={() => openLogin('dashboard')}
          onRegister={() => navigateToState('register')}
          onExploreChargers={() => openLogin('find')}
        />
      );
    }

    if (currentView === 'register') {
      return (
        <div>
          <button className="auth-back-button" onClick={() => navigateToState('landing')}>← Back to home</button>
          <VidyutRegisterPage
            onRegistered={(auth) => applyAuthenticatedSession(auth, false)}
            onLogin={() => openLogin('dashboard')}
          />
        </div>
      );
    }

    return (
      <LoginPage
        onLogin={(auth) => applyAuthenticatedSession(auth, true)}
        onBack={() => navigateToState('landing')}
        onRegister={() => navigateToState('register')}
      />
    );
  }

  if (currentView === 'mode-select') {
    return (
      <ModeSelection
        name={user.name.split(' ')[0] || user.name}
        modes={allowedModes}
        loadingMode={loadingMode}
        error={modeError}
        onSelect={(mode) => void switchMode(mode, true)}
        onLogout={handleLogout}
      />
    );
  }

  const showOwnerBookings = activeDashboardRole === 'EV_OWNER' && activeTab === 'bookings';
  const showOwnerWallet = activeDashboardRole === 'EV_OWNER' && activeTab === 'wallet';
  const showOwnerVehicles = activeDashboardRole === 'EV_OWNER' && activeTab === 'vehicles';
  const vehicleDetailMatch = activeDashboardRole === 'EV_OWNER' ? activeTab.match(/^vehicles\/(\d+)$/) : null;
  const selectedVehicleId = vehicleDetailMatch ? Number(vehicleDetailMatch[1]) : null;

  const showOwnerVehicleDetail = selectedVehicleId != null && Number.isInteger(selectedVehicleId) && selectedVehicleId > 0;
  const showOwnerAutopilot = activeDashboardRole === 'EV_OWNER' && activeTab === 'autopilot';
  const showOwnerTripPlanner = activeDashboardRole === 'EV_OWNER' && activeTab === 'trip';
  const showOwnerNotifications = activeDashboardRole === 'EV_OWNER' && activeTab === 'notifications';
  const showOwnerCharging = activeDashboardRole === 'EV_OWNER' && activeTab === 'charging';
  const showOwnerOutlets = activeDashboardRole === 'EV_OWNER' && activeTab === 'outlets';
  const showCompanyWorkspace = activeDashboardRole === 'COMPANY_ADMIN';
  const showHostWorkspace = activeDashboardRole === 'LANDOWNER';
  const showModeFeature = activeTab !== 'dashboard' && activeTab !== 'find' && !showOwnerBookings && !showOwnerWallet && !showOwnerVehicles && !showOwnerVehicleDetail && !showOwnerAutopilot && !showOwnerTripPlanner && !showOwnerNotifications && !showOwnerCharging && !showOwnerOutlets && !showCompanyWorkspace && !showHostWorkspace;
  const profileIncomplete = isProfileIncomplete(user, activeMode);

  return (
    <div className="app-shell">
      <Sidebar
        active={activeTab.split('/')[0]}
        onNav={(tab) => navigateToState('dashboard', tab)}
        user={user}
        bookingCount={activeDashboardRole === 'COMPANY_ADMIN' ? companyCounts.bookings : activeDashboardRole === 'LANDOWNER' ? hostCounts.bookings : bookingUnreadCount}
        onLogout={handleLogout}
        role={activeDashboardRole}
        open={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
        profileIncomplete={profileIncomplete}
        onCompleteProfile={() => {
          setShowProfileModal(true);
          setSidebarOpen(false);
        }}
      />

      <main className="app-main">
        <TopBar
          notificationCount={activeDashboardRole === 'COMPANY_ADMIN'
            ? companyCounts.notifications
            : activeDashboardRole === 'LANDOWNER'
              ? hostCounts.notifications
              : ownerNotifications.filter((notification) => !notification.read).length}
          onOpenMenu={() => setSidebarOpen(true)}
          searchValue={showCompanyWorkspace ? chargerSearchQuery : undefined}
          searchPlaceholder={showCompanyWorkspace ? `Search ${activeTab === 'chargers' ? 'chargers' : 'stations'} by location, name or code` : undefined}
          onSearchChange={showCompanyWorkspace ? (query) => {
            setChargerSearchQuery(query);
            if (activeTab !== 'stations' && activeTab !== 'chargers') navigateToState('dashboard', 'stations');
          } : undefined}
          onSearch={(query) => {
            setChargerSearchQuery(query);
            navigateToState(
              'dashboard',
              activeDashboardRole === 'EV_OWNER' ? 'find' : activeDashboardRole === 'LANDOWNER' ? 'chargers' : activeTab === 'chargers' ? 'chargers' : 'stations',
            );
          }}
          onLocationChange={(location: TopBarLocation) => {
            setChargerMapCenter([location.latitude, location.longitude]);
            setStationViewport({
              minLat: location.latitude - 1.5,
              maxLat: location.latitude + 1.5,
              minLng: location.longitude - 1.5,
              maxLng: location.longitude + 1.5,
            });
            setChargerSearchQuery('');
            if (activeDashboardRole === 'EV_OWNER') navigateToState('dashboard', 'find');
          }}
          onOpenNotifications={() => navigateToState('dashboard', 'notifications')}
        />

        <div className="mode-bar">
          <span className="mode-label">{allowedModes.length > 1 ? 'Current workspace' : 'Account workspace'}</span>
          <div className="mode-tabs" aria-label="Authorized workspaces">
            {allowedModes.includes('EV_USER') && (
              <button className={`mode-tab ${activeMode === 'EV_USER' ? 'active' : ''}`} onClick={() => void switchMode('EV_USER')} disabled={Boolean(loadingMode)}>
                <CarFront size={14} /> EV Owner
              </button>
            )}
            {allowedModes.includes('HOST') && (
              <button className={`mode-tab ${activeMode === 'HOST' ? 'active' : ''}`} onClick={() => void switchMode('HOST')} disabled={Boolean(loadingMode)}>
                <HousePlug size={14} /> Charger Host
              </button>
            )}
            {allowedModes.includes('COMPANY') && (
              <button className={`mode-tab ${activeMode === 'COMPANY' ? 'active' : ''}`} onClick={() => void switchMode('COMPANY')} disabled={Boolean(loadingMode)}>
                <Building2 size={14} /> Company
              </button>
            )}
          </div>
          {profileIncomplete && (
            <button
              className="profile-mode-alert"
              onClick={() => setShowProfileModal(true)}
            >
              <CircleAlert size={15} /> Finish profile <ArrowRight size={14} />
            </button>
          )}
          {modeError && <span className="mode-error" role="alert">{modeError}</span>}
        </div>

        <div className="app-body">
          {profileIncomplete && (activeTab === 'settings' || activeTab === 'profile') && (
            <section className="account-profile-notice" aria-label="Profile completion status">
              <span><UserRound size={20} /></span>
              <div>
                <strong>Your {activeMode === 'COMPANY' ? 'Company' : activeMode === 'HOST' ? 'Host' : 'EV Owner'} profile needs a few details</strong>
                <p>Add the missing account information now, or continue using the workspace and return later.</p>
              </div>
              <button type="button" onClick={() => setShowProfileModal(true)}>Complete profile <ArrowRight size={14} /></button>
            </section>
          )}
          {activeTab === 'dashboard' && activeDashboardRole === 'EV_OWNER' && (
            <EVOwnerDashboard
              token={authToken}
              user={user}
              chargers={chargers}
              onSelectCharger={handleSelectCharger}
              onExploreChargers={() => setActiveTab('find')}
              onBookNow={() => setActiveTab('find')}
              onOpenBookings={() => setActiveTab('bookings')}
              onOpenWallet={() => setActiveTab('wallet')}
              onOpenAutopilot={() => navigateToState('dashboard', 'autopilot')}
              vehicle={primaryVehicle}
              onOpenVehicle={(vehicleId) => navigateToState('dashboard', `vehicles/${vehicleId}`)}
            />
          )}
          {showHostWorkspace && <HostWorkspace tab={activeTab} token={authToken} hostName={user.name} onNavigate={setActiveTab} onCountsChange={setHostCounts} />}
          {showCompanyWorkspace && <CompanyWorkspace tab={activeTab} token={authToken} companyName={user.name} onNavigate={setActiveTab} onCountsChange={setCompanyCounts} searchQuery={chargerSearchQuery} onSearchChange={setChargerSearchQuery} />}

          {showOwnerBookings && (
            <BookingsView
              token={authToken}
              refreshKey={bookingRefreshKey}
              onUnreadCountChange={setBookingUnreadCount}
              onFindChargers={() => setActiveTab('find')}
            />
          )}

          {showOwnerWallet && <WalletView token={authToken} onOpenVehicles={() => setActiveTab('vehicles')} />}

          {showOwnerVehicles && (
            <VehiclesView
              token={authToken}
              onFindChargers={() => setActiveTab('find')}
              onOpenWallet={() => setActiveTab('wallet')}
              onOpenVehicle={(vehicleId) => navigateToState('dashboard', `vehicles/${vehicleId}`)}
            />
          )}

          {showOwnerVehicleDetail && selectedVehicleId != null && (
            <VehicleDetailView
              token={authToken}
              vehicleId={selectedVehicleId}
              onBack={() => navigateToState('dashboard', 'vehicles')}
              onFindChargers={() => navigateToState('dashboard', 'find')}
              onOpenWallet={() => navigateToState('dashboard', 'wallet')}
              onVehicleUpdated={(updated) => setPrimaryVehicle((current) => !current || current.id === updated.id ? updated : current)}
            />
          )}

          {showOwnerAutopilot && (
            <AutopilotView
              token={authToken}
              userName={user.name}
              onOpenWallet={() => setActiveTab('wallet')}
            />
          )}

          {showOwnerTripPlanner && <TripPlannerView token={authToken} />}

          {showOwnerCharging && <ChargingSessionView token={authToken} />}

          {showOwnerOutlets && <OutletAccessView token={authToken} onFindChargers={() => navigateToState('dashboard', 'find')} />}

          {showOwnerNotifications && (
            <OwnerNotificationsView
              notifications={ownerNotifications}
              loading={ownerNotificationsLoading}
              error={ownerNotificationsError}
              onRefresh={() => void refreshOwnerNotifications()}
              token={authToken}
            />
          )}

          {activeTab === 'find' && activeDashboardRole === 'EV_OWNER' && (
            <div style={{ height: 'calc(100dvh - 180px)', minHeight: 520 }}>
              <MapView
                chargers={chargers}
                selectedId={selectedCharger?.id ?? null}
                onSelect={handleSelectCharger}
                filter={mapFilter}
                onFilterChange={setMapFilter}
                searchQuery={chargerSearchQuery}
                center={chargerMapCenter}
                compatibleConnector={primaryVehicle?.connectorType || undefined}
                offline={chargerDataOffline}
                onBoundsChange={handleStationBoundsChange}
              />
            </div>
          )}

          {showModeFeature && <FeatureView role={activeDashboardRole} tab={activeTab} />}
        </div>
      </main>

      <ChargerDetailModal
        charger={modalCharger}
        onClose={() => setModalCharger(null)}
        onConfirmBooking={handleConfirmBooking}
        token={authToken}
        vehicleId={primaryVehicle?.id}
      />

      {(showProfileModal || (profileIncomplete && !profileModalDismissed)) && (
        <CompleteProfileModal
          user={user}
          activeMode={activeMode}
          authToken={authToken}
          onComplete={(auth) => {
            setShowProfileModal(false);
            setProfileModalDismissed(true);
            applyAuthenticatedSession(auth, false);
          }}
          onSkip={() => {
            setShowProfileModal(false);
            setProfileModalDismissed(true);
          }}
          onCancel={() => {
            setShowProfileModal(false);
            setProfileModalDismissed(true);
          }}
          onLogout={handleLogout}
        />
      )}
    </div>
  );
}

export function App() {
  const [adminRoute, setAdminRoute] = useState(() => window.location.hash.startsWith('#/admin'));
  useEffect(() => {
    const sync = () => setAdminRoute(window.location.hash.startsWith('#/admin'));
    window.addEventListener('hashchange', sync);
    return () => window.removeEventListener('hashchange', sync);
  }, []);
  return adminRoute ? <AdminPortal /> : <MainApplication />;
}

export default App;
