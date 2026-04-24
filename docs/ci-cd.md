# CI/CD and Developer Experience for Weatherify API

This guide describes the recommended pipelines and local tooling to build, test, and deploy the
Weatherify API.

The project targets Google Cloud Run and provides:

- GitHub Actions workflows for PR validation and automated deployment.
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
- .github/workflows/auto-deploy-on-main.yml — Auto deploy to Cloud Run on push to main
- Makefile — Local convenience commands
- .editorconfig — Consistent formatting defaults

## Deployment

Pushes to main trigger the auto-deploy workflow which:

1. Builds a Docker image via Cloud Build
2. Deploys to Cloud Run

Manual deployment:

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

### 2) Auto Deploy on Main (CD)

File: .github/workflows/auto-deploy-on-main.yml

Trigger:

- Push to main

Behavior:

- Authenticates to GCP using Workload Identity Federation (no JSON key)
- Builds Docker image via Cloud Build
- Deploys to Cloud Run

Required GitHub Actions secrets:

- GCP_WORKLOAD_IDENTITY_PROVIDER — Resource name of your WIF provider
- GCP_SERVICE_ACCOUNT_EMAIL — Email of the GCP service account used by GitHub Actions
- GCP_PROJECT_ID — Target project ID

#### Setting up Workload Identity Federation (one-time)

1. Create a service account, e.g. github-deployer@PROJECT_ID.iam.gserviceaccount.com
2. Grant minimal roles to this service account:
    - roles/run.admin
    - roles/cloudbuild.builds.editor
    - roles/iam.serviceAccountUser
    - roles/storage.admin or roles/storage.objectAdmin (to write artifacts if needed)
3. Create a workload identity pool and provider for GitHub in your project (via gcloud or Console).
   Example with gcloud:

   gcloud iam workload-identity-pools create github-pool \
   --project=PROJECT_ID --location=global --display-name="GitHub Pool"

   gcloud iam workload-identity-pools providers create-oidc github-provider \
   --project=PROJECT_ID --location=global --workload-identity-pool=github-pool \
   --display-name="GitHub Provider" --issuer-uri="https://token.actions.githubusercontent.com" \
   --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository"

4. Allow the GitHub repo to impersonate the service account:

   gcloud iam service-accounts add-iam-policy-binding \
   github-deployer@PROJECT_ID.iam.gserviceaccount.com \
   --project=PROJECT_ID \
   --role=roles/iam.workloadIdentityUser \
   --member="principalSet:
   //iam.googleapis.com/projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/attribute.repository/OWNER/REPO"

5. Set the GitHub repository secrets with:
    - GCP_WORKLOAD_IDENTITY_PROVIDER:
      projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/providers/github-provider
    - GCP_SERVICE_ACCOUNT_EMAIL: github-deployer@PROJECT_ID.iam.gserviceaccount.com
    - GCP_PROJECT_ID: your-project-id

6. Run the Deploy workflow from the Actions tab.

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

5) GitHub Actions → Workload Identity Federation

- Create a service account (e.g., github-deployer@PROJECT_ID.iam.gserviceaccount.com)
- Grant minimal roles:
    - roles/run.admin
    - roles/cloudbuild.builds.editor
    - roles/iam.serviceAccountUser
    - roles/secretmanager.secretAccessor (only if you plan to access secrets in CI; not required for
      build-only)
- Configure a Workload Identity Pool/Provider and bind your repo as described above.
- Add repository secrets:
    - GCP_WORKLOAD_IDENTITY_PROVIDER
    - GCP_SERVICE_ACCOUNT_EMAIL
    - GCP_PROJECT_ID

6) Branch protection/trigger alignment

- The auto-deploy workflow triggers on pushes to main. Ensure your default branch is named main, or
  adjust .github/workflows/auto-deploy-on-main.yml accordingly.

Once done:

- PRs and pushes run CI (.github/workflows/build-and-test.yml) automatically.
- Pushes to main also auto-deploy to Cloud Run.

## Troubleshooting

- Gradle cache issues in Actions: rerun without cache by changing cache key or clearing caches.
- Permission denied deploying: verify roles on the service account and WIF binding for the repo.
- Container fails to start on Cloud Run: check logs with `gcloud run services logs read weatherify-api`.
