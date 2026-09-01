# Vidyut EV Owner 

## Demo workflow

1. Start PostgreSQL, then run `npm run dev` from the repository root. Ports `8080`, `5173`, and `8001` must be free.
2. Open the EV Owner app and add/select the long-range demo vehicle. Use 80% SoC, Delhi → Mumbai, 15% arrival reserve, and the default 80% target.
3. Preview the agent plan. Confirm four timing-matched stops, open alternatives, swap one stop, and launch once to reserve every stop.
4. Start the trip, simulate a delay, confirm the replan/notification, complete each stop, then share the trip summary.
5. Open Map, enable `Outlet`, select the purple PSIT station, and verify Faculty ₹4/kWh, verified Student ₹6/kWh, and Visitor ₹9/kWh paths. The booking response must show the applied tier and rate.
6. Open Profile → Bluetooth & EV link. Enable simulator, pair the simulated Tata EV, enable session controls, start a charging session, and observe 30-second SoC updates.
7. Open Notifications to verify unread count, read/read-all, preferences, and deep-link navigation. Disable network access and confirm saved chargers remain visible while booking is blocked.

## Verification commands

```powershell
cd vidyut-backend
mvn test

cd ..\vidyut-mobile
npm run typecheck
npx expo config --type public
npx expo export --platform android --output-dir .expo-export-m5m10

cd ..\vidyut-ai\agent
.venv\Scripts\python -m unittest discover -s tests -v

cd ..\..\vidyut-web
npm run build
```


## Device and production setup still required

- Add `extra.eas.projectId` and configure Android/iOS push credentials before enabling `vidyut.notifications.push-enabled=true`.
- Use an Expo development build for real BLE; Expo Go does not include `react-native-ble-plx`. Validate permissions and the vehicle-specific service/characteristic contract on physical Android and iOS devices.
- Replace the development ID-document URI with authenticated object storage before production use.
- Run the PostgreSQL Flyway migrations through `V9` in staging and test real push delivery, background behavior, payment credentials, and Bluetooth hardware before release.
