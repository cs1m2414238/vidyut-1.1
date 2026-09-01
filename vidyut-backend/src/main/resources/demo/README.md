# Demo station data

This directory contains synthetic charging-station fixtures used for Vidyut development, tests, and route demonstrations.

## Files

### `chargers-india.json`

- 112 manually curated road-corridor hubs.
- Includes the Delhi/Noida/Agra corridor and Central India coverage such as Lucknow, Kanpur, Kanpur Dehat, Hamirpur, Mahoba, Chhatarpur, Jhansi, Sagar, Bhopal, Sehore, and Vidisha.
- Used for real-road Autopilot examples including Lucknow → Kanpur → Bhopal and Kanpur → Bhopal.

### `district-chargers-india.json`

- 777 district hubs across 36 Indian states and union territories.
- District names and boundaries originate from the Survey of India Administrative Boundary Database.
- Includes only named districts with numeric Local Government Directory codes.
- Each coordinate is an interior representative point and is snapped to a road only when the configured local OSRM graph has a road within 25 km.

Source: <https://surveyofindia.gov.in/pages/administrative-boundary-data-base-abdb->

Source archive: `State_District_Subdistrict_PAN INDIA.rar`, published by the Office of the Surveyor General of India and retrieved 2026-08-16.

## Connector inventory

Every seeded hub is synchronized with all five connector standards supported by the demo compatibility engine:

| Connector | Demo power |
| --- | --- |
| CCS2 DC | 60–180 kW variants |
| Type 2 AC | 22 kW |
| CHAdeMO DC | 50 kW |
| GB/T DC | 60 kW |
| Type 1 AC | 7.2 kW |

The station initializer uses stable `demoSeedKey` values, so startup updates are idempotent and remain separate from Host- and Company-owned stations.

## Enable or disable

The seeders run only when `vidyut.demo-data.enabled=true`, exposed as `VIDYUT_DEMO_DATA_ENABLED` in development configuration.

## Data warning

Only the cited administrative geography is source data. Vidyut station names, exact placement, connector inventory, availability, prices, power, queue, amenities, ratings, companies, and operating claims are synthetic. These records must not be shown as verified public charging installations.

The public OSRM demo server and public Nominatim service are development aids, not guarantees that a seeded charger exists or is reachable. A route marked as estimated must be confirmed with live navigation before travel.
