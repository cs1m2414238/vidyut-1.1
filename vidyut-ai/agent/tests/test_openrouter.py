from __future__ import annotations

import json
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from vidyut_agent.config import Settings
from vidyut_agent.openrouter import (
    AVAILABLE_TOOLS,
    OPENROUTER_TOOL_DEFINITIONS,
    _execute_openrouter_tool,
    run_openrouter_agent,
)
from vidyut_agent.agent import COMPANY_AGENT_INSTRUCTION
from vidyut_agent.service import (
    ChatRequest,
    chat,
    health,
)


class OpenRouterToolTests(unittest.IsolatedAsyncioTestCase):
    def test_all_tools_are_defined(self) -> None:
        tool_names = {t["function"]["name"] for t in OPENROUTER_TOOL_DEFINITIONS}
        self.assertEqual(len(tool_names), len(AVAILABLE_TOOLS))
        self.assertEqual(len(tool_names), 31)
        for name in tool_names:
            self.assertIn(name, AVAILABLE_TOOLS)

    async def test_execute_tool_captures_plan_artifact(self) -> None:
        mock_plan = {
            "origin": "Delhi",
            "destination": "Agra",
            "estimatedChargingCost": 500,
            "withinBudget": True,
        }
        tool_states: dict[str, str] = {}
        artifacts: dict[str, dict] = {}

        with patch("vidyut_agent.tools.backend.request", new_callable=AsyncMock) as backend_mock:
            backend_mock.return_value = mock_plan
            res = await _execute_openrouter_tool(
                name="preview_autopilot_trip",
                args={
                    "vehicle_id": 1,
                    "origin": "Delhi",
                    "destination": "Agra",
                    "current_battery_percent": 50,
                },
                tool_states=tool_states,
                artifacts=artifacts,
            )

        self.assertTrue(res["ok"])
        self.assertEqual(tool_states["preview_autopilot_trip"], "completed")
        self.assertEqual(artifacts["plan"], mock_plan)

    async def test_run_openrouter_agent_multi_turn(self) -> None:
        tool_states: dict[str, str] = {}
        artifacts: dict[str, dict] = {}

        # Step 1 response: calls preview_autopilot_trip
        step1_json = {
            "choices": [
                {
                    "message": {
                        "role": "assistant",
                        "content": None,
                        "tool_calls": [
                            {
                                "id": "call_abc123",
                                "type": "function",
                                "function": {
                                    "name": "preview_autopilot_trip",
                                    "arguments": json.dumps({
                                        "vehicle_id": 1,
                                        "origin": "Delhi",
                                        "destination": "Jaipur",
                                        "current_battery_percent": 60,
                                    }),
                                },
                            }
                        ],
                    }
                }
            ]
        }
        # Step 2 response: returns final answer
        step2_json = {
            "choices": [
                {
                    "message": {
                        "role": "assistant",
                        "content": "Route to Jaipur planned with 1 charging stop.",
                    }
                }
            ]
        }

        mock_post = AsyncMock()
        resp1 = MagicMock(status_code=200, json=lambda: step1_json)
        resp2 = MagicMock(status_code=200, json=lambda: step2_json)
        mock_post.side_effect = [resp1, resp2]

        mock_client = MagicMock()
        mock_client.post = mock_post
        mock_client.__aenter__.return_value = mock_client
        mock_client.__aexit__.return_value = False

        dummy_settings = Settings(
            model="gemini-3.5-flash",
            fallback_models=("gemini-3.5-flash-lite",),
            openrouter_api_key="sk-or-testkey",
            openrouter_model="openai/gpt-4o-mini",
            openrouter_fallback_models=("meta-llama/llama-3.3-70b-instruct",),
            openrouter_base_url="https://openrouter.ai/api/v1",
            backend_base_url="http://localhost:8080",
            backend_timeout_seconds=15.0,
        )

        with patch("vidyut_agent.openrouter.settings", dummy_settings), \
             patch("vidyut_agent.openrouter.httpx.AsyncClient", return_value=mock_client), \
             patch("vidyut_agent.tools.backend.request", new_callable=AsyncMock) as backend_mock:
            backend_mock.return_value = {
                "origin": "Delhi",
                "destination": "Jaipur",
                "totalDistanceKm": 270,
            }
            reply, model = await run_openrouter_agent(
                message="Plan Delhi to Jaipur",
                tool_states=tool_states,
                artifacts=artifacts,
            )

        self.assertEqual(reply, "Route to Jaipur planned with 1 charging stop.")
        self.assertEqual(model, "openai/gpt-4o-mini")
        self.assertEqual(tool_states["preview_autopilot_trip"], "completed")
        self.assertEqual(artifacts["plan"]["totalDistanceKm"], 270)

    async def test_company_agent_uses_only_company_role_tool(self) -> None:
        response_json = {
            "choices": [{"message": {"role": "assistant", "content": "Network is healthy."}}]
        }
        mock_post = AsyncMock(return_value=MagicMock(status_code=200, json=lambda: response_json))
        mock_client = MagicMock()
        mock_client.post = mock_post
        mock_client.__aenter__.return_value = mock_client
        mock_client.__aexit__.return_value = False
        dummy_settings = Settings(
            model="gemini-3.5-flash",
            fallback_models=(),
            openrouter_api_key="sk-or-testkey",
            openrouter_model="openai/gpt-4o-mini",
            openrouter_fallback_models=(),
            openrouter_base_url="https://openrouter.ai/api/v1",
            backend_base_url="http://localhost:8080",
            backend_timeout_seconds=15.0,
        )

        with patch("vidyut_agent.openrouter.settings", dummy_settings), \
             patch("vidyut_agent.openrouter.httpx.AsyncClient", return_value=mock_client):
            reply, _ = await run_openrouter_agent(
                message='Question with {"network":{"faults":0}}',
                tool_states={},
                artifacts={},
                system_instruction=COMPANY_AGENT_INSTRUCTION,
                tools_enabled=True,
                allowed_tool_names={"get_company_operations_context"},
            )

        self.assertEqual(reply, "Network is healthy.")
        payload = mock_post.await_args.kwargs["json"]
        self.assertEqual(payload["messages"][0]["content"], COMPANY_AGENT_INSTRUCTION)
        self.assertEqual(
            [tool["function"]["name"] for tool in payload["tools"]],
            ["get_company_operations_context"],
        )
        self.assertEqual(payload["tool_choice"], "auto")
        self.assertEqual(payload["max_tokens"], 2048)


class ServiceOpenRouterFallbackTests(unittest.IsolatedAsyncioTestCase):
    async def test_health_reports_openrouter_status(self) -> None:
        dummy_settings = Settings(
            model="gemini-3.5-flash",
            fallback_models=("gemini-3.5-flash-lite",),
            openrouter_api_key="sk-or-testkey",
            openrouter_model="openai/gpt-4o-mini",
            openrouter_fallback_models=("meta-llama/llama-3.3-70b-instruct",),
            openrouter_base_url="https://openrouter.ai/api/v1",
            backend_base_url="http://localhost:8080",
            backend_timeout_seconds=15.0,
        )
        with patch("vidyut_agent.service.settings", dummy_settings):
            res = await health()
            self.assertEqual(res["status"], "ready")
            self.assertEqual(res["openrouterModel"], "openai/gpt-4o-mini")
            self.assertTrue(res["openrouterAuthenticationConfigured"])

    async def test_falls_back_to_openrouter_when_gemini_quota_exhausted(self) -> None:
        dummy_settings = Settings(
            model="gemini-3.5-flash",
            fallback_models=(),
            openrouter_api_key="sk-or-testkey",
            openrouter_model="openai/gpt-4o-mini",
            openrouter_fallback_models=(),
            openrouter_base_url="https://openrouter.ai/api/v1",
            backend_base_url="http://localhost:8080",
            backend_timeout_seconds=15.0,
        )

        async def failing_runner(*args, **kwargs):
            class QuotaError(Exception):
                code = 429
            raise QuotaError("RESOURCE_EXHAUSTED")
            yield  # pragma: no cover

        mock_gemini_runner = MagicMock()
        mock_gemini_runner.run_async = failing_runner

        request = ChatRequest(
            message="Plan journey to Agra",
            sessionId="test-session-1234",
            requestId="test-request-1234",
        )

        with patch("vidyut_agent.service.settings", dummy_settings), \
             patch("vidyut_agent.service.model_runners", [("gemini-3.5-flash", mock_gemini_runner)]), \
             patch("vidyut_agent.service.run_openrouter_agent", new_callable=AsyncMock) as or_mock:
            or_mock.return_value = ("OpenRouter planned your trip to Agra.", "openai/gpt-4o-mini")

            response = await chat(
                request=request,
                authorization="Bearer valid-token-for-test",
            )

            self.assertEqual(response.reply, "OpenRouter planned your trip to Agra.")
            self.assertEqual(response.model, "openai/gpt-4o-mini")
            self.assertEqual(response.provider, "OPENROUTER")
            or_mock.assert_awaited_once()


if __name__ == "__main__":
    unittest.main()
