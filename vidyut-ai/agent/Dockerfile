# Multi-stage Dockerfile for Vidyut Python ADK + Gemini AI Agent (Google Cloud Run ready)
FROM python:3.11-slim
WORKDIR /app

# Prevent Python from writing .pyc files & buffer stdout/stderr
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV PORT=8001

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY vidyut_agent ./vidyut_agent

EXPOSE 8001

CMD ["sh", "-c", "uvicorn vidyut_agent.service:app --host 0.0.0.0 --port ${PORT}"]
