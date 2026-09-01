import json
import urllib.request
import urllib.error

BASE_URL = "http://localhost:8080/api"

def make_req(endpoint, method="GET", data=None, token=None):
    url = f"{BASE_URL}{endpoint}"
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    body = json.dumps(data).encode("utf-8") if data else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            raw = json.loads(resp.read().decode("utf-8"))
            if isinstance(raw, dict) and "data" in raw:
                return raw["data"]
            return raw
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        print(f"HTTP Error {e.code} on {endpoint}: {err_body}")
        raise

def main():
    # 1. Login or Register
    try:
        auth = make_req("/auth/login", method="POST", data={"email": "testev1@vidyut.com", "password": "password123"})
    except Exception:
        auth = make_req("/auth/register", method="POST", data={
            "name": "Test Driver",
            "email": "testev1@vidyut.com",
            "password": "password123",
            "accountType": "EV_OWNER"
        })
    token = auth.get("token") or auth.get("accessToken")
    print(f"Auth successful! Token obtained.")

    # 2. Get/Add Vehicle
    vehicles = make_req("/ev/vehicles", method="GET", token=token)
    if not vehicles:
        veh = make_req("/ev/vehicles", method="POST", data={
            "make": "Tata",
            "model": "Nexon EV Max",
            "registrationNumber": "DL01EV1234",
            "batteryCapacityKwh": 40.5,
            "fastChargingPowerKw": 50.0,
            "supportedConnectors": ["CCS2", "TYPE2"]
        }, token=token)
        vehicle_id = veh["id"]
    else:
        vehicle_id = vehicles[0]["id"]
    print(f"Vehicle ID: {vehicle_id}")

    # Top up wallet
    try:
        make_req("/ev/wallet/topup", method="POST", data={"amount": 10000.0, "paymentMethod": "UPI"}, token=token)
        print("Wallet topped up with Rs.10,000.")
    except Exception as e:
        print("Topup info:", e)

    # Reset demo stations to ensure fresh state
    try:
        make_req("/ev/autopilot/stations/reset-demo", method="POST", token=token)
        print("Demo stations restored to AVAILABLE.")
    except Exception as e:
        print("Reset demo error:", e)

    # 3. Plan Trip
    plan_payload = {
        "vehicleId": vehicle_id,
        "origin": "Delhi",
        "destination": "Bhopal",
        "goal": "Fastest route to Bhopal",
        "tripPurpose": "GENERAL",
        "optimizeFor": "TIME",
        "autonomyMode": "ASK_BEFORE_ACTIONS",
        "currentBatteryPercent": 76.0,
        "minimumArrivalBatteryPercent": 15.0,
        "maximumChargingBudget": 3000.0
    }
    print(f"\nPlanning trip Delhi -> Bhopal...")
    plan = make_req("/ev/autopilot/trips/preview", method="POST", data=plan_payload, token=token)
    print(f"Plan distance: {plan.get('totalDistanceKm')} km, duration: {plan.get('totalDurationMinutes')} min, cost: Rs.{plan.get('estimatedChargingCost')}")
    print("Initial stops planned:")
    for s in plan.get("stops", []):
        print(f"  - [{s.get('sequenceNumber')}] {s.get('stationName')} | Arrive: {s.get('arrivalBatteryPercent')}% | Target: {s.get('targetBatteryPercent')}% | Dist: {s.get('distanceFromOriginKm')} km")

    # 4. Confirm Trip
    print(f"\nConfirming Trip...")
    trip = make_req("/ev/autopilot/trips", method="POST", data=plan_payload, token=token)
    trip_id = trip["id"]
    print(f"Trip created with ID: {trip_id}, Status: {trip.get('status')}")

    # 5. Complete first stop (Nuh) so vehicle charges to 80% and moves forward
    print(f"\nCompleting Stop #1 (Vidyut Nuh District Demo Hub)...")
    trip_after_stop1 = make_req(f"/ev/autopilot/trips/{trip_id}/complete-charging", method="POST", token=token)
    print(f"Stop #1 completed! Trip status: {trip_after_stop1.get('status')}")
    print(f"Next active reserved stop: {[s['stationName'] for s in trip_after_stop1.get('stops', []) if s.get('status') == 'RESERVED']}")

    # 6. Simulate Station Failure on Dausa!
    print(f"\nSIMULATING CHARGER OUTAGE on Dausa (Stop #2)...")
    reroute = make_req(f"/ev/autopilot/trips/{trip_id}/simulate-fault", method="POST", token=token)
    
    print(f"\n=======================================================")
    print(f"REROUTE COMPLETE! Trip Status: {reroute.get('status')}")
    print(f"New Total Distance: {reroute.get('totalDistanceKm')} km")
    print(f"New Total Duration: {reroute.get('totalDurationMinutes')} min")
    print(f"New Estimated Charging Cost: Rs.{reroute.get('estimatedChargingCost')}")
    print(f"New Arrival Battery: {reroute.get('estimatedArrivalBatteryPercent')}%")
    print(f"Optimization Summary: {reroute.get('optimizationSummary')}")
    print(f"\nUpdated Stops:")
    for s in reroute.get("stops", []):
        st = s.get("status")
        sel_type = s.get("selectionType") or "STANDARD"
        replaces = s.get("replacesStationName") or "N/A"
        replaced_by = s.get("replacedByStationName") or "N/A"
        print(f"  - [{s.get('sequenceNumber')}] {s.get('stationName')}")
        print(f"      Status: {st} | Type: {sel_type}")
        print(f"      Arrive SOC: {s.get('arrivalBatteryPercent')}% | Target SOC: {s.get('targetBatteryPercent')}% | Power: {s.get('powerKw')} kW")
        print(f"      Dist from Origin: {s.get('distanceFromOriginKm')} km | Cost: Rs.{s.get('estimatedCost')}")
        if st == "CANCELLED":
            print(f"      [CANCELLED] REPLACED BY: {replaced_by} (Reason: {s.get('removalReason')})")
        if sel_type == "REROUTED_REPLACEMENT":
            print(f"      [REPLACEMENT] REPLACES: {replaces} | Impact: +{s.get('additionalMinutes')} min, +{s.get('additionalDistanceKm')} km, +Rs.{s.get('additionalCost')}")
    print(f"=======================================================\n")

if __name__ == "__main__":
    main()
