# Weatherify - Ktor Backend API

[![Kotlin](https://img.shields.io/badge/kotlin-1.9.0-blue.svg)](https://kotlinlang.org)
[![Ktor](https://img.shields.io/badge/ktor-2.3.3-orange.svg)](https://ktor.io)
[![KMongo](https://img.shields.io/badge/kmongo-4.9.0-green.svg)](https://litote.org/kmongo/)

A robust Ktor-KMongo backend API for the [Compose-Weatherify Android app](https://github.com/bosankus/Compose-Weatherify), providing weather data, feedback management, and more.

<!-- If you have a banner image for the API, uncomment and update the URL below -->
<!-- ![Weatherify Banner](URL_TO_YOUR_BANNER_IMAGE) -->

## Table of Contents

- [Introduction](#introduction)
- [Features](#features)
- [API Endpoints](#api-endpoints)
- [Technologies](#technologies)
- [Code Organization](#code-organization)
- [Building & Running](#building--running)
- [Contributing](#contributing)

## Introduction

Weatherify Backend API is a fully functional Ktor-KMongo backend service that powers the [Compose-Weatherify Android app](https://github.com/bosankus/Compose-Weatherify). This API provides:

- Real-time weather data and air pollution information
- User feedback submission and management
- Secure user authentication with JWT
- Interactive web UI for API documentation and testing
- Resume viewing functionality

The service is deployed on Google Cloud Platform (GCP) App Engine for reliable, scalable hosting.

## Features

Here's a list of Ktor features included in this project:

| Name                                                                   | Description                                                                        |
| ------------------------------------------------------------------------|------------------------------------------------------------------------------------ |
| [Call Logging](https://start.ktor.io/p/call-logging)                   | Logs client requests for monitoring and debugging                                  |
| [Default Headers](https://start.ktor.io/p/default-headers)             | Adds a default set of headers to HTTP responses                                    |
| [Content Negotiation](https://start.ktor.io/p/content-negotiation)     | Provides automatic content conversion according to Content-Type and Accept headers |
| [Routing](https://start.ktor.io/p/routing)                             | Provides a structured routing DSL for organizing endpoints                         |
| [kotlinx.serialization](https://start.ktor.io/p/kotlinx-serialization) | Handles JSON serialization using kotlinx.serialization library                     |

## API Endpoints

The API provides the following endpoints:

### Home
- `GET /` - Interactive HTML documentation page for exploring the API

### Weather
- `GET /weather` - Get weather data for a location
  - Query parameters: `lat` (latitude), `lon` (longitude)
  - Response: Comprehensive weather data including current conditions, hourly forecasts, daily forecasts, and weather alerts
  - Response model includes:
    - Current weather (temperature, humidity, wind, etc.)
    - Hourly forecasts (up to 48 hours)
    - Daily forecasts (up to 7 days)
    - Weather alerts (if available)

### Air Pollution
- `GET /air-pollution` - Get air pollution data for a location
  - Query parameters: `lat` (latitude), `lon` (longitude)
  - Response: Detailed air quality information for the specified location
  - Response model includes:
    - Air Quality Index (AQI)
    - Concentration of pollutants (CO, NO2, O3, SO2, PM2.5, PM10, etc.)
    - Timestamp of measurement

### Feedback
- `GET /feedback` - Get feedback by ID
  - Query parameters: `id` (feedback ID)
  - Response: Feedback details if found
- `POST /feedback` - Submit new feedback
  - Required parameters: `deviceId`, `deviceOs`, `feedbackTitle`, `feedbackDescription`
  - Response: ID of the created feedback
- `DELETE /feedback` - Delete feedback by ID
  - Query parameters: `id` (feedback ID)
  - Response: Success or error message

### Authentication

- `POST /register` - Register a new user
  - Request body: JSON with `email` and `password`
  - Password requirements: Min 8 chars, uppercase, lowercase, digit, special char
  - Response: Success message with 201 status code
- `POST /login` - Authenticate a user
  - Request body: JSON with `email` and `password`
  - Response: JWT token (valid for 24 hours) in response body and Authorization header
  - Token contains user email as subject and appropriate claims

## Technologies

This project is built with the following technologies:

- **[Kotlin](https://kotlinlang.org/)** - Modern, concise programming language
- **[Ktor](https://ktor.io/)** - Lightweight framework for building asynchronous servers
- **[KMongo](https://litote.org/kmongo/)** - Kotlin toolkit for MongoDB
- **[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)** - JSON serialization library
- **[kotlinx.html](https://github.com/Kotlin/kotlinx.html)** - DSL for building HTML
- **[Google Cloud Platform](https://cloud.google.com/)** - Cloud hosting platform

## Code Organization

The project follows a clean, modular architecture with a focus on maintainability and scalability:

### Package Structure

The codebase is organized into the following packages:

| Package  | Description                                                               |
|----------|---------------------------------------------------------------------------|
| `root`   | Main application files (Application.kt, Authentication.kt, HTTP.kt, etc.) |
| `config` | Configuration classes for environment variables, JWT, etc.                |
| `data`   | Data models and database-related code                                     |
| `route`  | API route handlers for different endpoints                                |
| `util`   | Utility classes including constants and helper functions                  |

This organization separates concerns and makes the codebase easier to navigate and maintain.

### Constants Management

All string constants are centralized in a single `Constants.kt` file located in the `util` package.
This approach offers several benefits:

- **Maintainability**: Easier to update values in one place
- **Consistency**: Prevents duplication and inconsistencies
- **Readability**: Improves code clarity with meaningful constant names
- **Type Safety**: Reduces errors from typos in string literals

The constants are organized into logical categories:

| Category   | Description                              | Examples                                       |
|------------|------------------------------------------|------------------------------------------------|
| `Database` | Database names, collections, field names | `USERS_COLLECTION`, `EMAIL_FIELD`              |
| `Auth`     | JWT configuration, validation messages   | `JWT_SECRET_NAME`, `INVALID_PASSWORD_STRENGTH` |
| `Api`      | Endpoints, URLs, query parameters        | `WEATHER_ENDPOINT`, `PARAM_LAT`                |
| `Messages` | Response messages for different features | `REGISTRATION_SUCCESS`, `WEATHER_RETRIEVED`    |
| `Env`      | Environment variable names               | `JWT_EXPIRATION`, `WEATHER_URL`                |

### Usage Example

Instead of hardcoding strings:

```kotlin
// Before
val collection = database.getCollection<User>("users")
```

Use the constants:

```kotlin
// After
val collection = database.getCollection<User>(Constants.Database.USERS_COLLECTION)
```

This approach makes the codebase more maintainable and less prone to errors from typos or
inconsistent string values.

## Building & Running

To build or run the project, use one of the following tasks:

| Task                          | Description                                                          |
| -------------------------------|---------------------------------------------------------------------- |
| `./gradlew test`              | Run the tests                                                        |
| `./gradlew build`             | Build everything                                                     |
| `buildFatJar`                 | Build an executable JAR of the server with all dependencies included |
| `buildImage`                  | Build the docker image to use with the fat JAR                       |
| `publishImageToLocalRegistry` | Publish the docker image locally                                     |
| `run`                         | Run the server                                                       |
| `runDocker`                   | Run using the local docker image                                     |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

## Contributing

Contributions are welcome! If you'd like to contribute to this project, please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request
