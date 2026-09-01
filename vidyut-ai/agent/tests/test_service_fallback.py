from __future__ import annotations

import unittest
import os
from unittest.mock import AsyncMock, patch

from vidyut_agent.config import Settings

from vidyut_agent.service import (
    ChatRequest,
    _message_for_agent,
    _planning_fallback,
    _planning_reply,
    _looks_like_tool_protocol,
    _state_change_attempted,
    chat,
)


class PlanningFallbackTests(unittest.IsolatedAsyncioTestCase):
    async def test_uses_structured_trip_context_without_location_defaults(self) -> None:
        request = ChatRequest(
            message="Build the plan",
            tripContext={
                "vehicleId": 17,
                "origin": "Satna Railway Station",
                "destination": "Bhopal Junction",
                "currentBatteryPercent": 43,
                "minimumArrivalBatteryPercent": 14,
                "maximumChargingBudget": 875,
                "optimizeFor": "COST",
            },
        )
        plan = {
            "origin": "Satna Railway Station",
            "destination": "Bhopal Junction",
            "estimatedChargingCost": 920,
            "maximumChargingBudget": 875,
            "withinBudget": False,
        }
        artifacts: dict[str, dict] = {}
        tool_states: dict[str, str] = {}

        with patch("vidyut_agent.service.backend.request", new_callable=AsyncMock) as call:
            call.return_value = plan
            result = await _planning_fallback(request, artifacts, tool_states)

        self.assertEqual(result, plan)
        payload = call.await_args.kwargs["json"]
        self.assertEqual(payload["vehicleId"], 17)
        self.assertEqual(payload["origin"], "Satna Railway Station")
        self.assertEqual(payload["destination"], "Bhopal Junction")
        self.assertEqual(payload["maximumChargingBudget"], 875)
        self.assertEqual(tool_states["preview_autopilot_trip"], "completed")

    async def test_does_not_invent_a_trip_when_context_is_missing(self) -> None:
        request = ChatRequest(message="Hello Vidyut")
        with patch("vidyut_agent.service.backend.request", new_callable=AsyncMock) as call:
            result = await _planning_fallback(request, {}, {})
        self.assertIsNone(result)
        call.assert_not_awaited()

    def test_reply_explains_budget_shortfall_inline(self) -> None:
        reply = _planning_reply({
            "origin": "Satna",
            "destination": "Bhopal",
            "estimatedChargingCost": 920,
            "maximumChargingBudget": 875,
            "withinBudget": False,
        })
        self.assertIn("needs INR 45 more budget", reply)

    def test_reply_reports_deadline_failure_separately_from_battery_and_budget(self) -> None:
        reply = _planning_reply({
            "origin": "Kanpur",
            "destination": "Bhopal",
            "estimatedChargingCost": 631,
            "maximumChargingBudget": 10_000,
            "withinBudget": True,
            "safeArrivalReserve": True,
            "deadlineFeasible": False,
            "overallFeasible": False,
            "arrivalDeadline": "05:44",
            "estimatedArrivalTime": "09:58",
            "deadlineMinutesLate": 254,
        })
        self.assertIn("Arrival deadline missed", reply)
        self.assertIn("ETA 09:58, requested 05:44, late by 254 minutes", reply)

    def test_normal_agent_turn_receives_the_same_structured_context(self) -> None:
        request = ChatRequest(
            message="Build the supplied trip",
            tripContext={
                "vehicleId": 17,
                "origin": "  Satna Railway Station  ",
                "destination": "Bhopal Junction",
                "currentBatteryPercent": 43,
                "minimumArrivalBatteryPercent": 14,
                "maximumChargingBudget": 875,
            },
        )

        message = _message_for_agent(request)

        self.assertIn('"vehicleId":17', message)
        self.assertIn('"origin":"Satna Railway Station"', message)
        self.assertIn('"destination":"Bhopal Junction"', message)
        self.assertIn('"maximumChargingBudget":875.0', message)
        self.assertNotIn("Kanpur", message)

    def test_host_agent_receives_only_grounded_workspace_context(self) -> None:
        request = ChatRequest(
            message="Which charger needs service?",
            workspace="HOST",
            groundingContext={"maintenanceRisks": [{"chargerCode": "KNP-03", "riskScore": 72}]},
        )

        message = _message_for_agent(request)

        self.assertIn("Authoritative HOST workspace context", message)
        self.assertIn('"chargerCode":"KNP-03"', message)
        self.assertNotIn("Application trip context", message)

    async def test_host_uses_deterministic_answer_when_both_providers_are_unavailable(self) -> None:
        request = ChatRequest(
            message="How are my stations?",
            sessionId="host-session-test",
            requestId="host-request-test",
            workspace="HOST",
            groundingContext={"deterministicAnswer": "Two stations are healthy."},
        )
        dummy_settings = Settings(
            model="gemini-3.5-flash",
            fallback_models=(),
            openrouter_api_key="",
            openrouter_model="openai/gpt-4o-mini",
            openrouter_fallback_models=(),
            openrouter_base_url="https://openrouter.ai/api/v1",
            backend_base_url="http://localhost:8080",
            backend_timeout_seconds=15.0,
        )

        with patch.dict(os.environ, {"VIDYUT_AGENT_DISABLE_GEMINI": "true"}), \
             patch("vidyut_agent.service.settings", dummy_settings):
            response = await chat(request, authorization="Bearer host-test-token")

        self.assertEqual(response.reply, "Two stations are healthy.")
        self.assertEqual(response.provider, "DETERMINISTIC")
        self.assertEqual(response.model, "deterministic-host-fallback")
        self.assertEqual(response.toolCalls, [])

    def test_read_only_tools_do_not_block_provider_fallback(self) -> None:
        self.assertFalse(_state_change_attempted({
            "get_vehicle_status": "completed",
            "preview_autopilot_trip": "completed",
        }))
        self.assertTrue(_state_change_attempted({"launch_autopilot_trip": "requested"}))

    def test_tool_protocol_echo_is_detected(self) -> None:
        self.assertTrue(_looks_like_tool_protocol(
            'The function call is {"name":"preview_autopilot_trip"}'
        ))
        self.assertFalse(_looks_like_tool_protocol("Your 455 km route is ready."))


if __name__ == "__main__":
    unittest.main()
