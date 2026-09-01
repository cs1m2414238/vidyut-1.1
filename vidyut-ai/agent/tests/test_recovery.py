import unittest
from unittest.mock import AsyncMock, patch
from vidyut_agent import recovery


def journey(mode="ASK_BEFORE_ACTIONS", state="INCIDENT_DETECTED"):
    return {"autonomyMode": mode, "recovery": {"state": state, "planId": "safe-1"}}


class RecoveryTests(unittest.IsolatedAsyncioTestCase):
    async def exercise(self, mode):
        prepared_state = {"ASK_BEFORE_ACTIONS": "AWAITING_APPROVAL", "RECOMMEND_ONLY": "SUGGESTED", "FULL_AUTOPILOT": "PREPARED"}[mode]
        calls = [
            {"journey": journey(mode)},
            {"candidates": [{"planId": "safe-1", "plan": {"strategy": "BRIDGE_RECOVERY", "distanceToBridgeKm": 22, "newRemainingMinutes": 400, "remainingCost": 500}}]},
            journey(mode, prepared_state),
            journey(mode, "EXECUTED"),
        ]
        with patch.object(recovery.backend, "request", new_callable=AsyncMock, side_effect=calls) as request, \
             patch.object(recovery, "_model_selection", new_callable=AsyncMock, return_value="safe-1"):
            result = await recovery.run_recovery(1, "incident-1")
        return result, request

    async def test_ask_prepares_but_never_executes(self):
        result, request = await self.exercise("ASK_BEFORE_ACTIONS")
        self.assertEqual(result["state"], "AWAITING_APPROVAL")
        self.assertEqual(request.await_count, 3)
        self.assertNotIn("execute_reroute", result["tools"])

    async def test_recommend_only_suggests_without_execution(self):
        result, request = await self.exercise("RECOMMEND_ONLY")
        self.assertEqual(result["state"], "SUGGESTED")
        self.assertEqual(request.await_count, 3)

    async def test_full_autopilot_calls_actual_execution_tool(self):
        result, request = await self.exercise("FULL_AUTOPILOT")
        self.assertEqual(result["state"], "EXECUTED")
        self.assertTrue(request.await_args_list[-1].args[1].endswith("/execute"))
        self.assertNotIn("approved", request.await_args_list[-1].kwargs["json"])

    async def test_no_safe_route_never_prepares_or_executes(self):
        with patch.object(recovery.backend,"request",new_callable=AsyncMock,side_effect=[
            {"journey":journey()}, {"state":"NO_SAFE_RECOVERY_ROUTE","candidates":[]},
            {"journey":journey(state="NO_SAFE_RECOVERY_ROUTE")},
        ]) as request:
            result=await recovery.run_recovery(1,"incident-1")
        self.assertEqual(result["state"],"NO_SAFE_RECOVERY_ROUTE")
        self.assertTrue(all(c.args[1].endswith(("/context","/candidates")) for c in request.await_args_list))

    async def test_unoffered_model_plan_cannot_execute_and_direct_is_preferred(self):
        candidates=[{"planId":"bridge", "plan":{"strategy":"BRIDGE_RECOVERY","distanceToBridgeKm":10,"newRemainingMinutes":200,"remainingCost":100}},
                    {"planId":"direct", "plan":{"strategy":"DIRECT_NEXT_STOP","distanceToBridgeKm":60,"newRemainingMinutes":250,"remainingCost":120}}]
        with patch.object(recovery,"_model_selection",new_callable=AsyncMock,return_value="invented-route"):
            selected,provider=await recovery.select_safe_plan({},candidates)
        self.assertEqual(selected["planId"],"direct")
        self.assertEqual(provider,"AGENT_POLICY")

    async def test_replayed_preparation_does_not_repeat_mutations(self):
        with patch.object(recovery.backend,"request",new_callable=AsyncMock,return_value={"journey":journey(state="AWAITING_APPROVAL")}) as request:
            await recovery.run_recovery(1,"incident-1")
        self.assertEqual(request.await_count,1)
