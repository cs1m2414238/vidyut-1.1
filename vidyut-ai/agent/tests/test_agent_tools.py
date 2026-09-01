from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, patch

from vidyut_agent import tools as agent_tools
from vidyut_agent.backend import redact_sensitive
from vidyut_agent.runtime_context import reset_request_context, set_request_context


class ToolTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self) -> None:
        self.context = set_request_context(
            authorization="Bearer test-user-token",
            request_id="request-12345678",
            user_message="show chargers near Lucknow",
        )

    def tearDown(self) -> None:
        reset_request_context(self.context)

    async def test_find_chargers_maps_search_filters(self) -> None:
        with patch.object(
            agent_tools.backend, "request", new_callable=AsyncMock
        ) as request:
            request.return_value = [{"id": 7, "name": "Vidyut Central"}]

            result = await agent_tools.find_chargers(
                query="Lucknow",
                latitude=26.85,
                longitude=80.95,
                connector_type="CCS2",
            )

        self.assertTrue(result["ok"])
        request.assert_awaited_once_with(
            "GET",
            "/api/stations/search",
            params={
                "query": "Lucknow",
                "lat": 26.85,
                "lng": 80.95,
                "radius": 25,
                "connectorType": "CCS2",
                "availableOnly": True,
                "maxPricePerKwh": None,
            },
            authenticated=False,
        )

    async def test_host_context_uses_only_host_role_endpoint(self) -> None:
        with patch.object(agent_tools.backend, "request", new_callable=AsyncMock) as request:
            request.return_value = {"networkPortfolio": [{"stationName": "Agra Demo Charging Hub"}]}
            result = await agent_tools.get_host_operations_context("Show my properties")

        self.assertTrue(result["ok"])
        request.assert_awaited_once_with(
            "POST", "/api/host/ai/context", json={"question": "Show my properties"}
        )

    async def test_company_context_uses_only_company_role_endpoint(self) -> None:
        with patch.object(agent_tools.backend, "request", new_callable=AsyncMock) as request:
            request.return_value = {"network": {"faults": 1}}
            result = await agent_tools.get_company_operations_context("Which connector needs service?")

        self.assertTrue(result["ok"])
        request.assert_awaited_once_with(
            "POST", "/api/company/ai/context",
            json={"question": "Which connector needs service?"},
        )

    async def test_booking_needs_explicit_user_intent(self) -> None:
        with patch.object(
            agent_tools.backend, "request", new_callable=AsyncMock
        ) as request:
            result = await agent_tools.book_charger(station_id=7)

        self.assertFalse(result["ok"])
        self.assertTrue(result["confirmationRequired"])
        request.assert_not_awaited()

    async def test_negated_booking_never_executes(self) -> None:
        reset_request_context(self.context)
        self.context = set_request_context(
            authorization="Bearer test-user-token",
            request_id="request-12345678",
            user_message="Do not book this charger yet",
        )
        with patch.object(
            agent_tools.backend, "request", new_callable=AsyncMock
        ) as request:
            result = await agent_tools.book_charger(station_id=7)

        self.assertFalse(result["ok"])
        self.assertTrue(result["confirmationRequired"])
        request.assert_not_awaited()

    async def test_autopilot_preview_is_read_only(self) -> None:
        with patch.object(
            agent_tools.backend, "request", new_callable=AsyncMock
        ) as request:
            request.return_value = {"origin": "Kanpur", "destination": "Delhi"}
            result = await agent_tools.preview_autopilot_trip(
                vehicle_id=2,
                origin="Kanpur",
                destination="Delhi",
                current_battery_percent=42,
                maximum_charging_budget=900,
            )

        self.assertTrue(result["ok"])
        request.assert_awaited_once()
        method, path = request.await_args.args
        self.assertEqual(method, "POST")
        self.assertEqual(path, "/api/ev/autopilot/trips/preview")

    async def test_web_confirmation_copy_authorizes_autopilot_launch(self) -> None:
        reset_request_context(self.context)
        self.context = set_request_context(
            authorization="Bearer test-user-token",
            request_id="request-12345678",
            user_message=(
                "I explicitly confirm launching this Autopilot trip and reserving "
                "its charging stops. Launch vehicle ID 2 from Kanpur to Delhi, "
                "battery 42%, minimum reserve 15%, maximum charging budget INR 900, "
                "deadline 18:00, trip purpose REST_STOP, optimize for TIME, autonomy "
                "mode ASK_BEFORE_ACTIONS. Call launch_autopilot_trip now. Goal: "
                "Get me to Delhi by 6 PM and don't let my battery fall below 15%."
            ),
        )
        with patch.object(
            agent_tools.backend, "request", new_callable=AsyncMock
        ) as request:
            request.return_value = {"id": 12, "status": "RESERVED"}
            result = await agent_tools.launch_autopilot_trip(
                vehicle_id=2,
                origin="Kanpur",
                destination="Delhi",
                current_battery_percent=42,
                maximum_charging_budget=900,
            )

        self.assertTrue(result["ok"])
        request.assert_awaited_once()
        method, path = request.await_args.args
        self.assertEqual(method, "POST")
        self.assertEqual(path, "/api/ev/autopilot/trips")

    async def test_mobile_confirmation_labels_authorize_autopilot_launch(self) -> None:
        for copy in ("Confirm tentative bookings", "Authorize full Autopilot"):
            with self.subTest(copy=copy):
                reset_request_context(self.context)
                self.context = set_request_context(
                    authorization="Bearer test-user-token",
                    request_id="request-12345678",
                    user_message=copy,
                )
                with patch.object(
                    agent_tools.backend, "request", new_callable=AsyncMock
                ) as request:
                    request.return_value = {"id": 12, "status": "RESERVED"}
                    result = await agent_tools.launch_autopilot_trip(
                        vehicle_id=2,
                        origin="Kanpur",
                        destination="Delhi",
                        current_battery_percent=42,
                        maximum_charging_budget=900,
                    )

                self.assertTrue(result["ok"])
                request.assert_awaited_once()

    async def test_action_negated_autopilot_confirmations_do_not_launch(self) -> None:
        negative_copies = (
            "Do not launch this trip. I explicitly confirm launching this Autopilot trip as an example.",
            "Don't reserve this trip. I explicitly confirm launching this Autopilot trip.",
            "Never book these charging stops. I explicitly confirm launching this Autopilot trip.",
            "Preview only; I explicitly confirm launching this Autopilot trip for comparison.",
        )
        for negative_copy in negative_copies:
            with self.subTest(copy=negative_copy):
                reset_request_context(self.context)
                self.context = set_request_context(
                    authorization="Bearer test-user-token",
                    request_id="request-12345678",
                    user_message=negative_copy,
                )
                with patch.object(
                    agent_tools.backend, "request", new_callable=AsyncMock
                ) as request:
                    result = await agent_tools.launch_autopilot_trip(
                        vehicle_id=2,
                        origin="Kanpur",
                        destination="Delhi",
                        current_battery_percent=42,
                        maximum_charging_budget=900,
                    )

                self.assertFalse(result["ok"])
                self.assertTrue(result["confirmationRequired"])
                request.assert_not_awaited()

    async def test_mobile_read_only_copy_does_not_launch(self) -> None:
        reset_request_context(self.context)
        self.context = set_request_context(
            authorization="Bearer test-user-token",
            request_id="request-12345678",
            user_message="No booking or payment happens until you confirm this plan.",
        )
        with patch.object(
            agent_tools.backend, "request", new_callable=AsyncMock
        ) as request:
            result = await agent_tools.launch_autopilot_trip(
                vehicle_id=2,
                origin="Kanpur",
                destination="Delhi",
                current_battery_percent=42,
                maximum_charging_budget=900,
            )

        self.assertFalse(result["ok"])
        self.assertTrue(result["confirmationRequired"])
        request.assert_not_awaited()

    async def test_stop_swap_needs_explicit_user_intent(self) -> None:
        with patch.object(
            agent_tools.backend, "request", new_callable=AsyncMock
        ) as request:
            result = await agent_tools.swap_autopilot_stop(12, 4, 27)

        self.assertFalse(result["ok"])
        self.assertTrue(result["confirmationRequired"])
        request.assert_not_awaited()

    async def test_charger_unavailable_event_runs_recovery_tool(self) -> None:
        reset_request_context(self.context)
        self.context = set_request_context(
            authorization="Bearer test-user-token", request_id="request-12345678",
            user_message='{"type":"CHARGER_UNAVAILABLE","tripId":12}',
        )
        with patch.object(agent_tools.backend, "request", new_callable=AsyncMock) as request, \
             patch("vidyut_agent.recovery.run_recovery", new_callable=AsyncMock) as orchestrate:
            request.return_value = {"id": 12, "recovery": {"incidentId": "incident-12", "state": "INCIDENT_DETECTED"}}
            orchestrate.return_value = {"journey": {"id": 12, "recovery": {"state": "AWAITING_APPROVAL"}}}
            result = await agent_tools.handle_charger_unavailable(12)
        self.assertTrue(result["ok"])
        self.assertEqual(result["data"]["recovery"]["state"], "AWAITING_APPROVAL")
        request.assert_awaited_once_with("GET", "/api/ev/autopilot/trips/12")
        orchestrate.assert_awaited_once_with(12, "incident-12")

    async def test_complete_charging_requires_explicit_intent(self) -> None:
        with patch.object(
            agent_tools.backend, "request", new_callable=AsyncMock
        ) as request:
            result = await agent_tools.complete_autopilot_charging(12)

        self.assertFalse(result["ok"])
        self.assertTrue(result["confirmationRequired"])
        request.assert_not_awaited()

    async def test_cancel_booking_maps_to_authenticated_backend(self) -> None:
        reset_request_context(self.context)
        self.context = set_request_context(
            authorization="Bearer test-user-token",
            request_id="request-12345678",
            user_message="Cancel reservation 44",
        )
        with patch.object(
            agent_tools.backend, "request", new_callable=AsyncMock
        ) as request:
            request.return_value = {"id": 44, "status": "CANCELLED"}
            result = await agent_tools.cancel_booking(44)

        self.assertTrue(result["ok"])
        request.assert_awaited_once_with("POST", "/api/ev/bookings/44/cancel")

    async def test_trip_summary_is_read_only(self) -> None:
        with patch.object(
            agent_tools.backend, "request", new_callable=AsyncMock
        ) as request:
            request.return_value = {"distanceKm": 1420, "chargingStops": 4}
            result = await agent_tools.get_autopilot_trip_summary(12)

        self.assertTrue(result["ok"])
        request.assert_awaited_once_with("GET", "/api/agent/trips/12/summary")

    def test_sensitive_backend_fields_are_removed_recursively(self) -> None:
        value = {
            "id": 1,
            "userId": 99,
            "nested": [{"accessToken": "secret", "batteryPercent": 68}],
        }

        self.assertEqual(
            redact_sensitive(value),
            {"id": 1, "nested": [{"batteryPercent": 68}]},
        )

    async def test_get_host_properties_calls_land_listings(self) -> None:
        with patch.object(agent_tools.backend, "request", new_callable=AsyncMock) as request:
            request.return_value = [{"id": 1, "title": "Agra Highway Hub"}]
            result = await agent_tools.get_host_properties()

        self.assertTrue(result["ok"])
        request.assert_awaited_once_with("GET", "/api/host/land-listings")

    async def test_prepare_property_listing_detects_duplicates(self) -> None:
        with patch.object(agent_tools.backend, "request", new_callable=AsyncMock) as request:
            request.return_value = {"status": "DUPLICATE_FOUND", "existingPropertyId": 1}
            result = await agent_tools.prepare_property_listing(
                title="Faizabad Airport EV Hub",
                address="Near Terminal",
                city="Faizabad",
                available_parking_bays=4,
                available_load_kw=80.0,
                property_type="COMMERCIAL_PARKING",
                operating_hours="06:00-23:00",
            )

        self.assertTrue(result["ok"])
        self.assertEqual(result["data"]["status"], "DUPLICATE_FOUND")
        request.assert_awaited_once_with(
            "POST",
            "/api/host/ai/prepare-property-draft",
            json={
                "title": "Faizabad Airport EV Hub",
                "address": "Near Terminal",
                "city": "Faizabad",
                "state": "Uttar Pradesh",
                "availableParkingBays": 4,
                "availableLoadKw": 80.0,
                "propertyType": "COMMERCIAL_PARKING",
                "operatingHours": "06:00-23:00",
                "powerPhase": "NOT_SURE",
                "discoverable": False,
            },
        )

    async def test_create_property_draft_persists_via_backend(self) -> None:
        reset_request_context(self.context)
        self.context = set_request_context(
            authorization="Bearer test-user-token",
            request_id="request-create-draft",
            user_message="Yes, create the draft",
        )
        with patch.object(agent_tools.backend, "request", new_callable=AsyncMock) as request:
            request.return_value = {"status": "CREATED", "propertyId": 99}
            result = await agent_tools.create_property_draft(
                title="Faizabad Hub",
                address="Highway 27",
                city="Faizabad",
                available_parking_bays=4,
                available_load_kw=100.0,
                property_type="COMMERCIAL_PARKING",
                operating_hours="Open 24 hours",
            )

        self.assertTrue(result["ok"])
        request.assert_awaited_once_with(
            "POST",
            "/api/host/ai/actions",
            json={
                "action": "CREATE_PROPERTY_DRAFT",
                "approved": True,
                "payload": {
                    "title": "Faizabad Hub",
                    "address": "Highway 27",
                    "city": "Faizabad",
                    "state": "Uttar Pradesh",
                    "availableParkingBays": 4,
                    "availableLoadKw": 100.0,
                    "propertyType": "COMMERCIAL_PARKING",
                    "operatingHours": "Open 24 hours",
                    "powerPhase": "NOT_SURE",
                    "discoverable": False,
                },
            },
        )

    async def test_create_property_draft_requires_explicit_approval(self) -> None:
        with patch.object(agent_tools.backend, "request", new_callable=AsyncMock) as request:
            result = await agent_tools.create_property_draft(
                title="Faizabad Hub",
                address="Highway 27",
                city="Faizabad",
                available_parking_bays=4,
                available_load_kw=100.0,
                property_type="COMMERCIAL_PARKING",
                operating_hours="Open 24 hours",
            )

        self.assertFalse(result["ok"])
        self.assertTrue(result["confirmationRequired"])
        request.assert_not_awaited()

    async def test_get_property_readiness_calls_endpoint(self) -> None:
        with patch.object(agent_tools.backend, "request", new_callable=AsyncMock) as request:
            request.return_value = {"rankedProperties": []}
            result = await agent_tools.get_property_readiness()

        self.assertTrue(result["ok"])
        request.assert_awaited_once_with("GET", "/api/host/ai/readiness")

    async def test_compare_company_offers_calls_endpoint(self) -> None:
        with patch.object(agent_tools.backend, "request", new_callable=AsyncMock) as request:
            request.return_value = {"offers": []}
            result = await agent_tools.compare_company_offers("Agra")

        self.assertTrue(result["ok"])
        request.assert_awaited_once_with("GET", "/api/host/ai/offers", params={"property": "Agra"})

    async def test_get_hosted_charger_health_calls_endpoint(self) -> None:
        with patch.object(agent_tools.backend, "request", new_callable=AsyncMock) as request:
            request.return_value = {"totalHostedChargers": 4}
            result = await agent_tools.get_hosted_charger_health()

        self.assertTrue(result["ok"])
        request.assert_awaited_once_with("GET", "/api/host/ai/charger-health")


if __name__ == "__main__":
    unittest.main()
