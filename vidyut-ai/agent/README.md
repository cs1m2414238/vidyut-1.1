# Vidyut role-scoped agents

FastAPI service built on Google ADK. It exposes isolated EV Owner, Host, and Company agents while Spring Boot remains the source of truth for authentication, authorization, vehicles, chargers, routes, bookings, trips, revenue, network operations, and wallet state.

The user bearer token is forwarded only to Vidyut backend tools. It is not included in Gemini or OpenRouter prompts.

## Workspace responsibilities

| Workspace | Model responsibility | Execution boundary |
| --- | --- | --- |
| `EV_OWNER` | Conversational trip planning, charging guidance, journey recovery, and approved tool orchestration | Uses authenticated EV tools; existing autonomy and mutation guards apply |
| `HOST` | Uses a role-scoped read-only backend tool for properties, occupancy, maintenance, revenue, partnerships, and scored recommendations | Mutations remain under `/api/host/ai/actions` and require backend approval |
| `COMPANY` | Uses a role-scoped read-only backend tool for managed network, faults, maintenance, expansion sites, and performance evidence | Mutations remain under `/api/company/ai/actions` and require backend approval |

The EV Owner agent can:

- Read vehicle status and supported connectors.
- Find compatible chargers and request road-aware trip plans.
- Produce read-only Autopilot previews from natural-language requests.
- Launch a confirmed trip according to its autonomy mode.
- Recover and monitor the current ongoing trip.
- Handle charger-unavailable events, release failed reservations, locate compatible replacements, and return the updated route timeline.
- List and swap charging-stop alternatives.
- Simulate delay, complete charging, summarize a trip, reroute, or cancel a booking.
- Read wallet status and perform an explicitly requested top-up.

Spring Boot results override model assumptions. The agent must not claim that an action succeeded unless the corresponding tool returns `ok=true`.

## Action safety

- Planning and preview requests are read-only.
- Recommend-only mode must never invoke booking, payment, cancellation, or rerouting mutations.
- Ask-before-actions requires explicit approval before mutation.
- Full Autopilot may act only inside the trip’s reserve, budget, connector, deadline, and configured execution limits.
- A provider fallback is never allowed to replay a state-changing tool call whose outcome may already have been applied.

## Provider order and degradation

1. Gemini primary model.
2. Configured Gemini fallback models.
3. OpenRouter model and fallback models, when configured and no state mutation has already been attempted.
4. Deterministic Spring fallback when both LLM providers are unavailable:
   - EV Owner requests use the backend trip-preview engine.
   - Host and Company requests return the already-calculated role-scoped answer.

Host and Company prompts receive only the authenticated Spring context for that account and each has one workspace-specific read-only tool. They cannot directly change a station, connector, price, contract, payment, payout, finance application, or solar-scheme submission. This means an LLM outage does not break their operational pages, and a provider fallback cannot bypass approval controls.

## Configuration

Copy `vidyut_agent/.env.example` to the ignored `vidyut_agent/.env` file when needed:

```env
GOOGLE_API_KEY=your-private-key
VIDYUT_AGENT_MODEL=gemini-3.6-flash
VIDYUT_AGENT_FALLBACK_MODELS=gemini-3.5-flash,gemini-3.5-pro
VIDYUT_AGENT_DISABLE_GEMINI=false

OPENROUTER_API_KEY=
OPENROUTER_MODEL=meta-llama/llama-3.3-70b-instruct
OPENROUTER_FALLBACK_MODELS=
OPENROUTER_BASE_URL=https://openrouter.ai/api/v1

VIDYUT_BACKEND_BASE_URL=http://localhost:8080
VIDYUT_BACKEND_TIMEOUT_SECONDS=15
```

Do not commit `.env`, API keys, bearer tokens, or model-provider credentials.

## Run locally

```powershell
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python -m vidyut_agent
```

The service listens on `http://127.0.0.1:8001`.

- `GET /health` reports provider configuration, fallback models, and supported workspaces.
- `POST /v1/chat` is the internal role-scoped agent endpoint. `workspace` accepts `EV_OWNER`, `HOST`, or `COMPANY`; Host and Company calls include a backend-generated `groundingContext`.
- Client applications should normally call Spring Boot at `POST /api/ev/agent/chat`; the backend verifies the Vidyut JWT before forwarding the request.

For the ADK development UI, run `adk web` from this directory. The exported `root_agent` uses the same instructions and tools.

## Tests

The current suite covers backend tools, OpenRouter tool execution/fallback, Gemini quota fallback, role-scoped Host/Company tools, deterministic role fallbacks, deterministic planning fallback, and mutation replay protection.

```powershell
.\.venv\Scripts\python -m unittest discover -s tests -v
```

## Vertex AI migration

To use Vertex AI, remove `GOOGLE_API_KEY` and set Google’s standard variables:

```env
GOOGLE_GENAI_USE_VERTEXAI=TRUE
GOOGLE_CLOUD_PROJECT=your-project-id
GOOGLE_CLOUD_LOCATION=us-central1
VIDYUT_AGENT_MODEL=your-Vertex-supported-model-id
```

No tool workflow change is required. Confirm that the selected model is available in the configured region.
