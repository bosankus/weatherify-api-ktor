# CI/CD and Developer Experience for Weatherify API

This guide describes the recommended pipelines and local tooling to build, test, and deploy the
Weatherify API.

The project targets Google App Engine Standard (Java 17) and provides both:

- Google Cloud Build pipeline for build/test/deploy.
- GitHub Actions workflows for PR validation and manual deployment via Cloud Build.
- A Makefile for common local tasks.

## Overview

Artifacts

- Fat JAR: build/libs/weatherify-api-all.jar (matches src/main/appengine/app.yaml entrypoint)
- Test reports: build/reports/tests/test
- Coverage report (JaCoCo): build/reports/jacoco/index.html

Key files

- cloudbuild.yaml — Cloud Build config (build/test/coverage/jar and optional deploy)
- .github/workflows/build-and-test.yml — CI for PRs and pushes
- .github/workflows/deploy.yml — Manual deployment workflow that triggers Cloud Build using Workload
  Identity Federation (WIF)
- Makefile — Local convenience commands
- .editorconfig — Consistent formatting defaults

## Cloud Build

cloudbuild.yaml runs:

1) ./gradlew clean test jacocoTestReport shadowJar
2) Optionally deploys to App Engine when _DEPLOY=true.
3) Uploads artifacts to a GCS path for the build.

Substitutions (flags):

- _DEPLOY: "true" to also deploy to App Engine; default "false"
- _PROMOTE: "true" to promote the version; default "false"
- _APP_YAML: path to app.yaml; default src/main/appengine/app.yaml

Examples:

- Build only:
  gcloud builds submit --config=cloudbuild.yaml --substitutions=_DEPLOY="false" .

- Build and deploy (no promote):
  gcloud builds submit \
  --config=cloudbuild.yaml \
  --substitutions=_DEPLOY="true",_PROMOTE="false",_APP_YAML="src/main/appengine/app.yaml" \
  .

Prerequisites:

- gcloud CLI installed and authenticated (gcloud auth login)
- App Engine app created in your project: gcloud app create --region=YOUR_REGION
- Appropriate IAM on your account or service account (App Engine Deployer, Cloud Build Editor,
  Storage Writer)

Note: The artifact upload bucket path uses gs://$PROJECT_ID-cloudbuild-artifacts/... Ensure a bucket
with that name exists or adjust the path in cloudbuild.yaml.

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

### 2) Manual Deploy via Cloud Build (CD)

File: .github/workflows/deploy.yml

Trigger:

- Manually from GitHub UI (workflow_dispatch)

Behavior:

- Authenticates to GCP using Workload Identity Federation (no JSON key)
- Runs gcloud builds submit with substitutions (_DEPLOY, _PROMOTE, _APP_YAML)

Required GitHub Actions secrets:

- GCP_WORKLOAD_IDENTITY_PROVIDER — Resource name of your WIF provider
- GCP_SERVICE_ACCOUNT_EMAIL — Email of the GCP service account used by GitHub Actions
- GCP_PROJECT_ID — Target project ID

#### Setting up Workload Identity Federation (one-time)

1. Create a service account, e.g. github-deployer@PROJECT_ID.iam.gserviceaccount.com
2. Grant minimal roles to this service account:
    - roles/appengine.deployer
    - roles/cloudbuild.builds.editor
    - roles/iam.serviceAccountUser (may be required for some environments)
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

6. Run the Deploy via Cloud Build workflow from the Actions tab.

## Makefile (local dev)

Common commands:

- make build — Clean, test, coverage, and build fat JAR
- make test — Run unit tests
- make coverage — Generate JaCoCo report (HTML at build/reports/jacoco/index.html)
- make shadow — Build fat JAR only
- make run — Run the app locally from the fat JAR on port 8080
- make cloudbuild-build — Submit Cloud Build (build only)
- make cloudbuild-deploy PROMOTE=true — Submit Cloud Build and deploy; set PROMOTE to true or false
- make app-deploy — Deploy directly using gcloud app deploy (no promote)

## Notes

- The app.yaml entrypoint expects weatherify-api-all.jar, which the Shadow plugin produces based on
  rootProject.name set to weatherify-api.
- Ensure Secret Manager and env variables are configured in GCP as per app.yaml and util/GCPUtil.kt
  behavior.
- CI does not require access to real secrets; unit tests rely on local defaults.

## Automation checklist (one-time setup)

Make sure these are done so the provided scripts and workflows run automatically:

1) Project prerequisites

- Billing enabled on the GCP project.
- gcloud initialized locally (for manual runs): gcloud auth login and gcloud config set project
  PROJECT_ID.

2) Enable required APIs

- App Engine Admin API: appengine.googleapis.com
- Cloud Build API: cloudbuild.googleapis.com
- IAM Service Account Credentials API: iamcredentials.googleapis.com
- Secret Manager API: secretmanager.googleapis.com

3) App Engine app (one-time)

- Create the App Engine app and select a region:
  gcloud app create --region=YOUR_REGION

4) Secret Manager secrets expected by the code

- jwt-secret — contents: a sufficiently long random string
- db-connection-string — contents: MongoDB connection URI
- weather-data-secret — contents: OpenWeatherMap API key

5) Runtime environment variables (already in app.yaml)

- Check src/main/appengine/app.yaml for GCP_PROJECT_ID, DB_NAME, WEATHER_URL, AIR_POLLUTION_URL,
  JWT_* values and adjust as needed.

6) GitHub Actions → Workload Identity Federation

- Create a service account (e.g., github-deployer@PROJECT_ID.iam.gserviceaccount.com)
- Grant minimal roles:
    - roles/appengine.deployer
    - roles/cloudbuild.builds.editor
    - roles/iam.serviceAccountUser (if required)
    - roles/secretmanager.secretAccessor (only if you plan to access secrets in CI; not required for
      build-only)
- Configure a Workload Identity Pool/Provider and bind your repo as described above.
- Add repository secrets:
    - GCP_WORKLOAD_IDENTITY_PROVIDER
    - GCP_SERVICE_ACCOUNT_EMAIL
    - GCP_PROJECT_ID

7) Branch protection/trigger alignment

- The auto-deploy workflow triggers on pushes to main. Ensure your default branch is named main, or
  adjust .github/workflows/auto-deploy-on-main.yml accordingly.

Once done:

- PRs and pushes run CI (.github/workflows/build-and-test.yml) automatically.
- Pushes to main also run auto-deploy via Cloud Build.

## Troubleshooting

- Gradle cache issues in Actions: rerun without cache by changing cache key or clearing caches.
- App Engine region not set: run gcloud app create --region=YOUR_REGION once per project.
- Permission denied deploying: verify roles on the service account and WIF binding for the repo.
