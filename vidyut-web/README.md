# Vidyut Web

React 19 + TypeScript + Vite client for the Vidyut EV charging platform.

## Workspaces

The client keeps four authorities visually and technically distinct:

- **EV Owner:** dashboard, charger map, Autopilot, trip planner, bookings, active charging, history, wallet, vehicles, institutional outlet access, notifications, and Host application.
- **Host:** properties, company marketplace, installation projects, chargers, availability, bookings, earnings, monitoring, reviews, reports, Host Assistant, and a separate green-finance workspace.
- **Company:** network operations, maintenance, Host-property review, product catalogue, pricing, analytics, settlements, staff, Company Assistant, and a separate Expansion Intelligence dashboard.
- **Admin:** isolated login and capability-scoped control plane at `#/admin/login`.

EV Owner and Host modes can belong to one individual account, but switching mode requests a new mode-scoped JWT. Company and Admin identities remain isolated.

## Autopilot interface

The journey form accepts both natural-language intent and explicit constraints. Parsed text can fill origin, destination, battery, reserve, budget, arrival deadline, trip purpose, autonomy, and optimization fields.

Two controls are independent:

| Authority | Execution behavior |
| --- | --- |
| Recommend only | Plans and explains; the user performs every action |
| Ask before actions | Plans automatically and asks before booking, paying, cancelling, or rerouting |
| Full Autopilot | Acts automatically within the selected safety and budget limits |

| Strategy | Optimization behavior |
| --- | --- |
| Fastest | Minimize total trip time |
| Balanced | Balance time, cost, and convenience |
| Lowest cost | Minimize charging expense without violating hard constraints |

Preview and active-journey views show road geometry, selected stops, charging time, effective power, cost, reserve/budget/deadline feasibility, requested versus expected arrival, lateness, and whether routing values are measured or estimated. Confirmed ongoing journeys are recovered from the backend and remain visible after page refresh.

## Host and Company assistants

- The **Host Assistant** reads session-backed charger occupancy, service risk, bookings, revenue, operating hours, operator offers, and solar scenarios. Actions remain approval-controlled.
- **Offers & Green Finance** is a separate decision workspace; modeled assistance is never presented as approved eligibility.
- The **Company Assistant** handles operational questions and permission-controlled actions for one company only.
- **Expansion Intelligence** is not a second assistant. It is a read-only site-ranking dashboard using grid capacity, parking readiness, and charging-network gaps.

## Admin Portal

The Admin Portal uses a separate token and route family. Its sidebar scrolls independently on short viewports while the identity and logout controls remain reachable. Routine enforcement uses the smallest applicable scope—booking, payment, property, station, charger, marketplace, or settlement—before emergency identity restriction.

## Branding

`public/vidyut-logo.svg` is the canonical web logo and favicon. Login, registration, splash, main navigation, and Admin surfaces reference the same asset.

## Configuration

Copy `.env.example` only when local overrides are required:

```env
VITE_API_BASE_URL=/api
VITE_GOOGLE_CLIENT_ID=your-google-oauth-web-client-id.apps.googleusercontent.com
```

Vite proxies `/api` to `http://127.0.0.1:8080` by default. Override the proxy target before startup when needed:

```powershell
$env:VIDYUT_BACKEND_PROXY = "http://127.0.0.1:8080"
```

## Development

```powershell
npm install
npm run dev
```

The development server normally listens on `http://localhost:5173`.

## Verification

```powershell
npm run lint
npm run build
```

`npm run build` runs the TypeScript project build before producing the Vite bundle. Vite currently reports a non-failing warning for the main JavaScript chunk exceeding 500 kB; future route-level code splitting can address it.
