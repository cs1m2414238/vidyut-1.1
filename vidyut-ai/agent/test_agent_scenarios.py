"""Test script to verify Vidyut Autopilot Agent across all 3 autonomy modes and multiple India routes."""
import asyncio
import json
import httpx

BASE_URL = "http://127.0.0.1:8001"
HEADERS = {"Authorization": "Bearer test-jwt-token-vidyut"}

SCENARIOS = [
    {
        "name": "Scenario 1: Delhi to Bhopal (Recommend Only)",
        "payload": {
            "message": "Plan a road trip from Delhi to Bhopal starting with 76% battery and ₹1000 budget.",
            "tripContext": {
                "vehicleId": 1,
                "origin": "Delhi",
                "destination": "Bhopal",
                "goal": "Fastest arrival with ₹1000 budget",
                "tripPurpose": "GENERAL",
                "optimizeFor": "TIME",
                "autonomyMode": "RECOMMEND_ONLY",
                "currentBatteryPercent": 76.0,
                "minimumArrivalBatteryPercent": 15.0,
                "maximumChargingBudget": 1000.0,
            },
            "workspace": "EV_OWNER",
        },
    },
    {
        "name": "Scenario 2: Kanpur to Delhi (Ask Before Actions)",
        "payload": {
            "message": "Trip from Kanpur to Delhi with 50% battery in Nexon EV. Ask me before booking.",
            "tripContext": {
                "vehicleId": 1,
                "origin": "Kanpur",
                "destination": "Delhi",
                "goal": "Reach Delhi safely",
                "tripPurpose": "COMMUTE",
                "optimizeFor": "BALANCED",
                "autonomyMode": "ASK_BEFORE_ACTIONS",
                "currentBatteryPercent": 50.0,
                "minimumArrivalBatteryPercent": 20.0,
                "maximumChargingBudget": 800.0,
            },
            "workspace": "EV_OWNER",
        },
    },
    {
        "name": "Scenario 3: Jammu to Bangalore (Full Autopilot)",
        "payload": {
            "message": "Cross-country trip from Jammu to Bangalore. Full autopilot with ₹5000 budget.",
            "tripContext": {
                "vehicleId": 1,
                "origin": "Jammu",
                "destination": "Bangalore",
                "goal": "Long distance highway journey with automatic reroute",
                "tripPurpose": "GENERAL",
                "optimizeFor": "TIME",
                "autonomyMode": "FULL_AUTOPILOT",
                "currentBatteryPercent": 90.0,
                "minimumArrivalBatteryPercent": 15.0,
                "maximumChargingBudget": 5000.0,
            },
            "workspace": "EV_OWNER",
        },
    },
]

async def run_all_tests():
    print("=" * 70)
    print("[VIDYUT] AUTONOMOUS AGENT - ALL-INDIA MULTI-MODE TEST SUITE")
    print("=" * 70)

    async with httpx.AsyncClient(timeout=30.0) as client:
        # Check health
        try:
            health = await client.get(f"{BASE_URL}/health")
            print(f"Health status: {health.status_code} -> {health.json()}\n")
        except Exception as e:
            print(f"[ERROR] Agent server is not reachable at {BASE_URL}: {e}")
            return

        for idx, scenario in enumerate(SCENARIOS, start=1):
            print(f"--- Test {idx}: {scenario['name']} ---")
            try:
                resp = await client.post(
                    f"{BASE_URL}/v1/chat",
                    headers=HEADERS,
                    json=scenario["payload"],
                )
                print(f"Status Code: {resp.status_code}")
                if resp.status_code == 200:
                    data = resp.json()
                    print(f"Provider: {data.get('provider')} | Model: {data.get('model')}")
                    reply = str(data.get("reply", "")).encode('ascii', 'ignore').decode('ascii')
                    print(f"Reply: {reply[:250]}...")
                    tool_calls = data.get("toolCalls", [])
                    print(f"Tool calls made: {tool_calls}")
                    print(f"[SUCCESS]\n")
                else:
                    print(f"[ERROR]: {resp.text}\n")
            except Exception as ex:
                print(f"[EXCEPTION]: {ex}\n")

    print("=" * 70)
    print("All test scenarios completed.")
    print("=" * 70)

if __name__ == "__main__":
    asyncio.run(run_all_tests())
