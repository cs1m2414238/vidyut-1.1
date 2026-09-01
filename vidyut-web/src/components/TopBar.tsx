import { useState } from 'react';
import { Bell, ChevronDown, LocateFixed, MapPin, Menu, Search, X } from 'lucide-react';

export interface TopBarLocation {
  label: string;
  latitude: number;
  longitude: number;
}

interface TopBarProps {
  notificationCount?: number;
  onOpenMenu?: () => void;
  onSearch?: (query: string) => void;
  searchValue?: string;
  onSearchChange?: (query: string) => void;
  searchPlaceholder?: string;
  onLocationChange?: (location: TopBarLocation) => void;
  onOpenNotifications?: () => void;
}

const knownLocations: TopBarLocation[] = [
  { label: 'Lucknow, India', latitude: 26.8467, longitude: 80.9462 },
  { label: 'Kanpur, India', latitude: 26.4499, longitude: 80.3319 },
  { label: 'Delhi, India', latitude: 28.6139, longitude: 77.209 },
];

export function TopBar({
  notificationCount = 0,
  onOpenMenu,
  onSearch,
  searchValue: controlledSearchValue,
  onSearchChange,
  searchPlaceholder = 'Search location, charger or booking',
  onLocationChange,
  onOpenNotifications,
}: TopBarProps) {
  const [localSearchValue, setLocalSearchValue] = useState('');
  const searchValue = controlledSearchValue ?? localSearchValue;
  const setSearchValue = (value: string) => {
    setLocalSearchValue(value);
    onSearchChange?.(value);
  };
  const [selectedLocation, setSelectedLocation] = useState(knownLocations[0]);
  const [locationMenuOpen, setLocationMenuOpen] = useState(false);
  const [locationError, setLocationError] = useState('');

  const chooseLocation = (location: TopBarLocation) => {
    setSelectedLocation(location);
    setSearchValue('');
    setLocationMenuOpen(false);
    setLocationError('');
    onLocationChange?.(location);
  };

  const useCurrentLocation = () => {
    if (!navigator.geolocation) {
      setLocationError('Location is not supported by this browser.');
      return;
    }

    setLocationError('');
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => chooseLocation({
        label: 'Current location',
        latitude: coords.latitude,
        longitude: coords.longitude,
      }),
      () => setLocationError('Allow location access, then try again.'),
      { enableHighAccuracy: true, timeout: 10_000 },
    );
  };

  return (
    <header className="topbar">
      <button className="icon-button topbar-menu" onClick={onOpenMenu} aria-label="Open navigation">
        <Menu size={20} />
      </button>

      <form
        className="topbar-search"
        role="search"
        onSubmit={(event) => {
          event.preventDefault();
          onSearch?.(searchValue.trim());
        }}
      >
        <Search size={17} color="#98a2b3" />
        <input
          aria-label="Search Vidyut"
          placeholder={searchPlaceholder}
          value={searchValue}
          onChange={(event) => setSearchValue(event.target.value)}
        />
        {searchValue && <button className="topbar-search-clear" type="button" aria-label="Clear search" onClick={() => {
          setSearchValue('');
          if (!onSearchChange) onSearch?.('');
        }}><X size={16} /></button>}
        <button className="topbar-search-submit" type="submit" aria-label="Search">
          <Search size={16} />
        </button>
      </form>

      <div className="topbar-spacer" />

      <div className="topbar-location">
        <button
          className="location-button"
          type="button"
          aria-haspopup="menu"
          aria-expanded={locationMenuOpen}
          onClick={() => setLocationMenuOpen((open) => !open)}
        >
          <MapPin size={16} color="#0f8f5d" />
          {selectedLocation.label}
          <ChevronDown size={14} color="#98a2b3" />
        </button>

        {locationMenuOpen && (
          <div className="location-menu" role="menu" aria-label="Choose map location">
            <button type="button" role="menuitem" onClick={useCurrentLocation}>
              <LocateFixed size={15} /> Use current location
            </button>
            {knownLocations.map((location) => (
              <button
                key={location.label}
                type="button"
                role="menuitem"
                className={selectedLocation.label === location.label ? 'active' : ''}
                onClick={() => chooseLocation(location)}
              >
                <MapPin size={15} /> {location.label}
              </button>
            ))}
            {locationError && <p role="alert">{locationError}</p>}
          </div>
        )}
      </div>

      <button
        className="icon-button notification-button"
        type="button"
        aria-label={notificationCount > 0 ? `Open ${notificationCount} notifications` : 'Open notifications'}
        onClick={onOpenNotifications}
      >
        <Bell size={19} />
        {notificationCount > 0 && <span className="notification-dot" />}
      </button>
    </header>
  );
}
