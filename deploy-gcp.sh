#!/usr/bin/env bash
# Google Cloud Run Deployment Script for Vidyut EV Platform
# Project: vidyut-autopilot
# Target Region: us-central1

set -euo pipefail

PROJECT_ID="vidyut-autopilot"
REGION="us-central1"

echo "=========================================================="
echo " 🚀 Vidyut Cloud Run Deployment - All Things Agentic     "
echo "=========================================================="

echo "[1/4] Setting active GCP project to $PROJECT_ID..."
gcloud config set project "$PROJECT_ID"

echo "[2/4] Enabling required Google Cloud APIs..."
gcloud services enable \
    run.googleapis.com \
    artifactregistry.googleapis.com \
    aiplatform.googleapis.com \
    firestore.googleapis.com \
    pubsub.googleapis.com \
    routes.googleapis.com \
    secretmanager.googleapis.com

echo "[3/4] Building & Deploying vidyut-agent to Cloud Run..."
cd vidyut-ai/agent
gcloud run deploy vidyut-agent \
    --source . \
    --region "$REGION" \
    --platform managed \
    --allow-unauthenticated \
    --set-env-vars="VIDYUT_AGENT_MODEL=gemini-3.6-flash,GOOGLE_GENAI_USE_VERTEXAI=1,GOOGLE_CLOUD_PROJECT=$PROJECT_ID,GOOGLE_CLOUD_LOCATION=$REGION"

AGENT_URL=$(gcloud run services describe vidyut-agent --region "$REGION" --format="value(status.url)")
echo "✅ vidyut-agent deployed at: $AGENT_URL"
cd ../..

echo "[4/4] Building & Deploying vidyut-backend to Cloud Run..."
cd vidyut-backend

SECRETS_ARGS=""
if gcloud secrets describe google-routes-api-key &>/dev/null; then
    echo "🔒 Attaching Secret Manager secret for Google Routes API..."
    SECRETS_ARGS="--set-secrets=GOOGLE_ROUTES_API_KEY=google-routes-api-key:latest"
fi

gcloud run deploy vidyut-api \
    --source . \
    --region "$REGION" \
    --platform managed \
    --allow-unauthenticated \
    --set-env-vars="SPRING_PROFILES_ACTIVE=prod,VIDYUT_AGENT_BASE_URL=$AGENT_URL,GOOGLE_CLOUD_PROJECT=$PROJECT_ID,GOOGLE_CLOUD_LOCATION=$REGION,VIDYUT_GOOGLE_ROUTES_ENABLED=true" \
    $SECRETS_ARGS

API_URL=$(gcloud run services describe vidyut-api --region "$REGION" --format="value(status.url)")
echo "✅ vidyut-api deployed at: $API_URL"
cd ..

echo "=========================================================="
echo " 🎉 Full Deployment Complete!"
echo " 🤖 AI Agent URL: $AGENT_URL"
echo " ⚡ API Core URL: $API_URL"
echo "=========================================================="
