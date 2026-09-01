# Vidyut EV Platform - Safe Production Deployment Script
# Region: asia-south1
# Order: vidyut-backend -> V26 health gate -> vidyut-agent -> vidyut-web (Firebase)
# WARNING: Do NOT change service names. Live services are vidyut-backend and vidyut-agent.

param (
    [string]$ProjectId = "vidyut-autopilot",
    [string]$Region    = "asia-south1"
)

$ErrorActionPreference = "Stop"

$BACKEND_URL = "https://vidyut-backend-558967442483.asia-south1.run.app"
$AGENT_URL   = "https://vidyut-agent-558967442483.asia-south1.run.app"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Vidyut Production Deployment - asia-south1               " -ForegroundColor Cyan
Write-Host " Order: backend -> agent -> web                           " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# ----------------------------------------------------------------
# 0. Verify project and confirm live service names
# ----------------------------------------------------------------
Write-Host "`n[0/5] Setting active GCP project to $ProjectId..." -ForegroundColor Yellow
gcloud config set project $ProjectId

Write-Host "`n[0/5] Verifying live Cloud Run services in $Region..." -ForegroundColor Yellow
$liveServices = (gcloud run services list --region $Region --format="value(metadata.name)")
$liveServicesStr = ($liveServices -join " ")
Write-Host "Live services found: $liveServicesStr" -ForegroundColor Cyan

if ($liveServicesStr -notmatch "vidyut-backend") {
    Write-Host "[ERROR] vidyut-backend not found in $Region. Aborting to prevent accidental new service creation." -ForegroundColor Red
    exit 1
}
Write-Host "[OK] vidyut-backend confirmed in $Region." -ForegroundColor Green

# ----------------------------------------------------------------
# 1. Deploy vidyut-backend (contains Flyway V26)
# ----------------------------------------------------------------
Write-Host "`n[1/5] Deploying vidyut-backend to Cloud Run ($Region)..." -ForegroundColor Yellow
Push-Location vidyut-backend
try {
    $dbUser = if ($env:DB_USER) { $env:DB_USER } else { "vidyut_app" }
    $dbPass = $env:DB_PASSWORD
    $jwtSecret = $env:JWT_SECRET
    $googleApiKey = $env:GOOGLE_API_KEY
    $demoPass = $env:DEMO_ACCOUNT_PASSWORD
    $adminEmail = if ($env:VIDYUT_ADMIN_EMAIL) { $env:VIDYUT_ADMIN_EMAIL } else { "admin@vidyut.local" }
    $adminPass = $env:VIDYUT_ADMIN_PASSWORD

    $backendEnv = "SPRING_PROFILES_ACTIVE=cloud,DB_USER=$dbUser,DB_PASSWORD=$dbPass,JWT_SECRET=$jwtSecret,VIDYUT_AGENT_BASE_URL=$AGENT_URL,GOOGLE_API_KEY=$googleApiKey,DEMO_SEED_ENABLED=true,DEMO_ACCOUNT_PASSWORD=$demoPass,VIDYUT_ADMIN_BOOTSTRAP_ENABLED=false,VIDYUT_ADMIN_EMAIL=$adminEmail,VIDYUT_ADMIN_PASSWORD=$adminPass"

    gcloud run deploy vidyut-backend `
        --source . `
        --region $Region `
        --platform managed `
        --allow-unauthenticated `
        --set-env-vars "$backendEnv"
} finally {
    Pop-Location
}
Write-Host "[OK] vidyut-backend deployed." -ForegroundColor Green

# ----------------------------------------------------------------
# 2. Health gate: wait for backend to be healthy (V26 migration)
# ----------------------------------------------------------------
Write-Host "`n[2/5] Health gate: verifying vidyut-backend startup and V26 migration..." -ForegroundColor Yellow

$maxAttempts = 20
$attempt = 0
$healthy = $false

while ($attempt -lt $maxAttempts) {
    $attempt++
    Write-Host "  Attempt $attempt/$maxAttempts - checking $BACKEND_URL/actuator/health ..." -ForegroundColor Gray
    try {
        $response = Invoke-WebRequest -Uri "$BACKEND_URL/actuator/health" -TimeoutSec 10 -UseBasicParsing -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            Write-Host "  [OK] Backend health check passed (HTTP 200)." -ForegroundColor Green
            $healthy = $true
            break
        } else {
            Write-Host "  [WAIT] HTTP $($response.StatusCode) - not ready yet." -ForegroundColor Yellow
        }
    } catch {
        Write-Host "  [WAIT] Not reachable yet: $($_.Exception.Message)" -ForegroundColor Yellow
    }
    Start-Sleep -Seconds 15
}

if (-not $healthy) {
    Write-Host "[ERROR] Backend did not become healthy after $($maxAttempts * 15) seconds. Check Cloud Run logs for V26 migration errors. Aborting agent deploy." -ForegroundColor Red
    Write-Host "Run: gcloud run services logs read vidyut-backend --region $Region --limit 100" -ForegroundColor Yellow
    exit 1
}

Write-Host "`n[2/5] Backend is healthy. Checking recent Cloud Run logs for Flyway V26..." -ForegroundColor Yellow
gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=vidyut-backend AND textPayload=~\"V26\"" `
    --project $ProjectId --limit 20 --format="value(textPayload)"

# ----------------------------------------------------------------
# 3. Deploy vidyut-agent (now backend + V26 schema exists)
# ----------------------------------------------------------------
Write-Host "`n[3/5] Deploying vidyut-agent to Cloud Run ($Region)..." -ForegroundColor Yellow
Push-Location vidyut-ai\agent
try {
    $openRouterKey = $env:OPENROUTER_API_KEY
    $googleApiKey = $env:GOOGLE_API_KEY
    $agentModel = if ($env:VIDYUT_AGENT_MODEL) { $env:VIDYUT_AGENT_MODEL } else { "gemini-2.5-flash" }

    $agentEnv = "VIDYUT_BACKEND_BASE_URL=$BACKEND_URL,OPENROUTER_API_KEY=$openRouterKey,GOOGLE_API_KEY=$googleApiKey,VIDYUT_AGENT_MODEL=$agentModel"

    gcloud run deploy vidyut-agent `
        --source . `
        --region $Region `
        --platform managed `
        --allow-unauthenticated `
        --set-env-vars "$agentEnv"
} finally {
    Pop-Location
}
Write-Host "[OK] vidyut-agent deployed." -ForegroundColor Green

# ----------------------------------------------------------------
# 4. Agent health gate
# ----------------------------------------------------------------
Write-Host "`n[4/5] Health gate: verifying vidyut-agent..." -ForegroundColor Yellow
$attempt = 0
$healthy = $false

while ($attempt -lt $maxAttempts) {
    $attempt++
    Write-Host "  Attempt $attempt/$maxAttempts - checking $AGENT_URL/health ..." -ForegroundColor Gray
    try {
        $response = Invoke-WebRequest -Uri "$AGENT_URL/health" -TimeoutSec 10 -UseBasicParsing -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            Write-Host "  [OK] Agent health check passed (HTTP 200)." -ForegroundColor Green
            $healthy = $true
            break
        }
    } catch {
        Write-Host "  [WAIT] $($_.Exception.Message)" -ForegroundColor Yellow
    }
    Start-Sleep -Seconds 15
}

if (-not $healthy) {
    Write-Host "[WARN] Agent did not respond on /health in time. Check logs before deploying frontend." -ForegroundColor Red
    Write-Host "Run: gcloud run services logs read vidyut-agent --region $Region --limit 50" -ForegroundColor Yellow
    $confirm = Read-Host "Continue to Firebase deploy anyway? (yes/no)"
    if ($confirm -ne "yes") { exit 1 }
}

# ----------------------------------------------------------------
# 5. Build and deploy vidyut-web to Firebase Hosting
# ----------------------------------------------------------------
Write-Host "`n[5/5] Building vidyut-web..." -ForegroundColor Yellow
Push-Location vidyut-web
try {
    npm run build
    Write-Host "[OK] Build complete. Deploying to Firebase Hosting..." -ForegroundColor Green
    firebase deploy --only hosting
} finally {
    Pop-Location
}

# ----------------------------------------------------------------
# Summary
# ----------------------------------------------------------------
Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " [DONE] Full Deployment Complete                          " -ForegroundColor Cyan
Write-Host " [BACKEND] $BACKEND_URL                                  " -ForegroundColor Green
Write-Host " [AGENT]   $AGENT_URL                                    " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "`nNEXT: Run the production end-to-end test:" -ForegroundColor Yellow
Write-Host "  Company: Chargers -> Search 'agar' -> Open DEMO-AGRA-CCS2-01" -ForegroundColor Gray
Write-Host "  EV Owner: Delhi -> Bhopal journey, verify connector_id stored" -ForegroundColor Gray
Write-Host "  Fault flow: ONLINE->FAULT (cancel stays ONLINE, approve->FAULT)" -ForegroundColor Gray
Write-Host "  Verify: connector failure detected, reroute calculated, approval required" -ForegroundColor Gray
