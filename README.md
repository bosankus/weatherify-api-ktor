# Weatherify API

[![Kotlin](https://img.shields.io/badge/kotlin-2.2.0-blue.svg)](https://kotlinlang.org)
[![Ktor](https://img.shields.io/badge/ktor-3.2.2-orange.svg)](https://ktor.io)
[![MongoDB](https://img.shields.io/badge/mongodb-5.5.1-green.svg)](https://mongodb.com)

A Ktor backend API for
the [Compose-Weatherify Android app](https://github.com/bosankus/Compose-Weatherify), providing
weather data, user authentication, and feedback management.

## Overview

Weatherify API is a Kotlin-based backend service built with Ktor and MongoDB. It provides:

- Real-time weather and air pollution data
- User authentication with JWT
- Feedback submission and management
- Interactive API documentation UI

## Architecture

The application follows a modular architecture with clear separation of concerns. See
the [Architecture Diagram](docs/architecture.md) for a visual representation of the system
components and their relationships.

### Data Flows

The application implements several key data flows:

1. **Weather Data Retrieval** - How weather data is fetched from external APIs and returned to
   clients
2. **Authentication** - User registration and login processes
3. **Feedback Submission** - How feedback is submitted and stored

Detailed sequence diagrams for these flows are available in
the [Data Flows Documentation](docs/data-flows.md).

## Documentation

The project includes the following documentation files:

- **[README.md](README.md)**: This file - provides an overview of the project, setup instructions,
  and basic usage information.
- **[API Reference](docs/api-reference.md)**: Comprehensive documentation of all API endpoints,
  request parameters, response formats, and authentication requirements.
- **[Architecture Diagram](docs/architecture.md)**: Visual representation of the system components
  and their relationships.
- **[Data Flows Documentation](docs/data-flows.md)**: Detailed sequence diagrams for key data flows
  in the application.
- **[GCP Enhancements and Feature Ideas](docs/gcp-enhancements.md)**: Recommended GCP services,
  rationale, and phased roadmap to enhance this project.

## API Endpoints

The API provides endpoints for weather data, authentication, and feedback management. For detailed
documentation of all endpoints, request parameters, response formats, and examples, see
the [API Reference](docs/api-reference.md).

### Weather

```
GET /weather?lat={latitude}&lon={longitude}
```

Returns comprehensive weather data including current conditions, hourly and daily forecasts.

```
GET /air-pollution?lat={latitude}&lon={longitude}
```

Returns air quality information for the specified location.

### Authentication

```
POST /register
```

Registers a new user. Requires email and password in request body.

```
POST /login
```

Authenticates a user and returns a JWT token. Requires email and password in request body.

```
POST /logout
```

Logs out the current user by clearing the jwt_token cookie on the server and invalidating the
session. Returns 200 with a JSON ApiResponse.

Example request:

```
POST /logout
```

Example success response:

```
{
  "status": true,
  "message": "Logged out successfully",
  "data": null
}
```

Notes:

- Works for both admin and regular users.
- When integrating in the browser, also clear any locally stored JWT (e.g., localStorage) after
  calling this endpoint.

### Feedback

```
GET /feedback?id={feedbackId}
```

Retrieves feedback by ID.

```
POST /feedback
```

Submits new feedback. Requires deviceId, deviceOs, feedbackTitle, and feedbackDescription.

```
DELETE /feedback?id={feedbackId}
```

Deletes feedback by ID.

## Setup & Installation

### Prerequisites

- JDK 17+
- MongoDB (local or remote)
- OpenWeatherMap API key (for weather data)

### Environment Variables

- `WEATHER_URL`: Weather data API URL
- `AIR_POLLUTION_URL`: Air pollution data API URL
- `DB_NAME`: MongoDB database name
- `JWT_EXPIRATION`: JWT token expiration time in milliseconds
- `JWT_AUDIENCE`: JWT audience
- `JWT_ISSUER`: JWT issuer
- `JWT_REALM`: JWT realm
- `WEATHER_API_KEY`: OpenWeatherMap API key (for local development)
- `DB_CONNECTION_STRING`: MongoDB connection string (for local development)

### Running Locally

```bash
# Build the project
./gradlew build

# Run the server
./gradlew run
```

The server will start at http://0.0.0.0:8080

### Deployment

The application is configured for deployment to Google Cloud Platform App Engine:

```bash
./gradlew appengineDeploy
```

## Project Structure

```
src/
├── main/
│   ├── kotlin/
│   │   ├── Application.kt         # Main application entry point
│   │   ├── Authentication.kt      # JWT authentication configuration
│   │   ├── HTTP.kt                # HTTP configuration
│   │   ├── Monitoring.kt          # Logging and monitoring
│   │   ├── Routing.kt             # API route configuration
│   │   ├── config/                # Environment and JWT configuration
│   │   ├── data/                  # Data models and database access
│   │   ├── route/                 # API route handlers
│   │   └── util/                  # Utility classes and constants
│   └── resources/                 # Static resources and configuration
└── test/                          # Test classes
```

## Technologies

- **[Kotlin](https://kotlinlang.org/)**: Modern JVM language
- **[Ktor](https://ktor.io/)**: Lightweight asynchronous web framework
- **[MongoDB](https://www.mongodb.com/)**: NoSQL database
- **[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)**: JSON serialization
- **[kotlinx.html](https://github.com/Kotlin/kotlinx.html)**: HTML DSL
- **[Google Cloud Platform](https://cloud.google.com/)**: Hosting platform

## CI/CD

See the full guide: docs/ci-cd.md

Quick start:

- Local build and tests: make build (or ./gradlew clean test jacocoTestReport shadowJar)
- Run locally: make run (runs build/libs/weatherify-api-all.jar on port 8080)
- Cloud Build (build only): gcloud builds submit --config=cloudbuild.yaml --substitutions=_DEPLOY="
  false" .
- Cloud Build (build + deploy): gcloud builds submit --config=cloudbuild.yaml --substitutions=_
  DEPLOY="true",_PROMOTE="false",_APP_YAML="src/main/appengine/app.yaml" .
- GitHub Actions: PRs and pushes run Build and Test workflow automatically; use “Deploy via Cloud
  Build” workflow manually for deployments.

Notes:

- Deploy workflow uses Workload Identity Federation. Add repository secrets:
  GCP_WORKLOAD_IDENTITY_PROVIDER, GCP_SERVICE_ACCOUNT_EMAIL, GCP_PROJECT_ID.
- Shadow JAR name is weatherify-api-all.jar, matching src/main/appengine/app.yaml entrypoint.

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## Extending the API (Feature Modules & Route Registrars)

To make the project scalable and maintainable, route registration is modular.

- Each feature exposes a Ktor `Route` extension (e.g., `Route.weatherRoute()`), keeping handlers
  self-contained.
- A tiny `RouteRegistrar` interface allows features to self-register their routes without changing
  central wiring.
- An ordered list of registrars is provided via Koin in `di/RouteModule.kt`, and `base/Routing.kt`
  iterates this list.

Add a new page/feature:

1. Create or reuse a `Route` extension under `src/main/kotlin/route/...`.
2. Add a registrar in `route/common/Registrars.kt` that delegates to the extension, e.g.:
   ```kotlin
   object MyFeatureRegistrar : RouteRegistrar { override fun register(r: Route) { with(r) { myFeatureRoute() } } }
   ```
3. Bind it in `di/RouteModule.kt` and add it to the ordered list.
4. If your feature needs new services/repos, wire them in `di/DomainModule.kt` / `di/DataModule.kt`.

Nothing in existing behavior changes when you follow this pattern; it simply reduces coupling.

## Developer Workflow

For contribution guidelines, coding conventions, and how to add features safely,
see [CONTRIBUTING.md](CONTRIBUTING.md).
