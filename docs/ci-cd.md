# CI/CD and Developer Experience for Weatherify API

This guide describes the recommended pipelines and local tooling to build, test, and deploy the
Weatherify API.

The project targets Google Cloud Run and provides:

- A GitHub Actions workflow for PR and push validation. Deploy is manual.
- A Dockerfile for containerized builds.
- A Makefile for common local tasks.

## Overview

Artifacts

- Fat JAR: build/libs/weatherify-api-all.jar
- Docker image: gcr.io/PROJECT_ID/weatherify-api
- Test reports: build/reports/tests/test
- Coverage report (JaCoCo): build/reports/jacoco/index.html

Key files

- Dockerfile — Multi-stage Docker build (Gradle build + JRE runtime)
- .github/workflows/build-and-test.yml — CI for PRs and pushes
- Makefile — Local convenience commands
- .editorconfig — Consistent formatting defaults

## Deployment

There is no GitHub deploy workflow. Deploy from a machine already logged into the GCP project:


```bash
# Build and push image
gcloud builds submit --tag gcr.io/PROJECT_ID/weatherify-api

# Deploy to Cloud Run
gcloud run deploy weatherify-api \
  --image=gcr.io/PROJECT_ID/weatherify-api \
  --platform=managed \
  --region=asia-southeast1 \
  --allow-unauthenticated
```

Prerequisites:

- gcloud CLI installed and authenticated (gcloud auth login)
- Appropriate IAM on your account or service account (Cloud Run Admin, Cloud Build Editor,
  Storage Writer)

## GitHub Actions

### 1) Build and Test (CI)

File: .github/workflows/build-and-test.yml

Triggers:

- Push to main/master
- Pull Requests to main/master

Steps:

- Checkout repo
- Setup JDK 17 (Temurin)
- Cache Gradle
- Run tests, coverage, and shadowJar
- Upload test and coverage reports and the fat JAR as artifacts

No deployment is performed in this CI workflow.

### 2) Deploy

GitHub Actions does not deploy. The old main-branch workflow depended on Workload Identity Federation and has been removed. Use the manual gcloud commands in the Deployment section. The Syncling deploy workflow used the same GitHub secrets and was removed with it.

## Makefile (local dev)

Common commands:

- make build — Clean, test, coverage, and build fat JAR
- make test — Run unit tests
- make coverage — Generate JaCoCo report (HTML at build/reports/jacoco/index.html)
- make shadow — Build fat JAR only
- make run — Run the app locally from the fat JAR on port 8080
- make docker-build — Build Docker image locally
- make docker-run — Build and run Docker image locally on port 8080

## Notes

- The Dockerfile copies the shadow JAR (weatherify-api-all.jar) into the container image.
- Ensure Secret Manager and env variables are configured in GCP as per Dockerfile and util/GCPUtil.kt
  behavior.
- CI does not require access to real secrets; unit tests rely on local defaults.

## Automation checklist (one-time setup)

Make sure these are done so the provided scripts and workflows run automatically:

1) Project prerequisites

- Billing enabled on the GCP project.
- gcloud initialized locally (for manual runs): gcloud auth login and gcloud config set project
  PROJECT_ID.

2) Enable required APIs

- Cloud Run Admin API: run.googleapis.com
- Cloud Build API: cloudbuild.googleapis.com
- IAM Service Account Credentials API: iamcredentials.googleapis.com
- Secret Manager API: secretmanager.googleapis.com
- Container Registry API: containerregistry.googleapis.com

3) Secret Manager secrets expected by the code

- jwt-secret — contents: a sufficiently long random string
- db-connection-string — contents: MongoDB connection URI
- weather-data-secret — contents: OpenWeatherMap API key

4) Runtime environment variables (configured in Dockerfile)

- Check the Dockerfile for GCP_PROJECT_ID, DB_NAME, WEATHER_URL, AIR_POLLUTION_URL,
  JWT_* values and adjust as needed.

5) Branch protection

- PRs and pushes run CI (.github/workflows/build-and-test.yml) automatically.
- A push to main does not deploy.

## Troubleshooting

- Gradle cache issues in Actions: rerun without cache by changing cache key or clearing caches.
- Permission denied deploying: the gcloud account running the manual deploy needs Cloud Build and Cloud Run access on the project.
- Container fails to start on Cloud Run: check logs with `gcloud run services logs read weatherify-api`.
