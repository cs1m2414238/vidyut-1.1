# Vidyut Mobile

React Native 0.86 application for Android and iOS, managed with Expo SDK 57 and Expo Router.

## Implemented flows

- **EV Owner:** dashboard, map, charger details, connector-aware Autopilot, trip planner, bookings, active trip, charging session, wallet, vehicles, institutional outlet access, Bluetooth pairing, and notifications.
- **Host:** dashboard, properties/stations, live monitoring, booking actions, earnings, reviews, reports, profile/KYC/bank/email verification, and Host Assistant.
- **Company:** dashboard, stations, bookings, operational insights, and profile.
- **Admin:** protected overview, users, companies, and stations.
- Mode-scoped authentication and EV Owner ↔ Host switching for eligible individual accounts.
- Secure token storage, React Query caching, network-status handling, and explicit mock-data configuration.

The mobile Autopilot keeps action authority separate from optimization strategy, provides a read-only preview before launch, supports current-trip recovery and live rerouting, and explains when booking/payment actions require confirmation.

## Configuration

Copy `.env.example` to `.env.local` when local overrides are needed:

```env
EXPO_PUBLIC_API_BASE_URL=http://192.168.1.10:8080/api
EXPO_PUBLIC_USE_MOCK_DATA=false

EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID=
EXPO_PUBLIC_GOOGLE_ANDROID_CLIENT_ID=
EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID=
```

Backend addresses:

- Android emulator: `http://10.0.2.2:8080/api`
- iOS simulator: `http://localhost:8080/api`
- Physical device: use the development computer’s LAN address and ensure the firewall permits access

Authentication never falls back to mock credentials. Charger and booking mock data is used only when explicitly enabled by configuration or by the existing development fallback path.

## Install and run

```powershell
npm install
npm run android
```

On macOS with Xcode:

```powershell
npm run ios
```

`react-native-ble-plx` requires a native development build; Expo Go does not provide the required BLE native module.

## Verification

```powershell
npm run typecheck
npx expo config --type public
```

The resolved public Expo configuration must not expose API secrets or private credentials.
