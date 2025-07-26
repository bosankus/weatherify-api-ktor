# Environment Setup Guide

## Environment Variables

| Variable Name          | Description                       | Default Value                                         |
|------------------------|-----------------------------------|-------------------------------------------------------|
| `WEATHER_API_KEY`      | Weather service API key           | dummy_weather_api_key                                 |
| `WEATHER_URL`          | Weather data endpoint             | https://api.openweathermap.org/data/3.0/onecall       |
| `AIR_POLLUTION_URL`    | Air pollution endpoint            | https://api.openweathermap.org/data/2.5/air_pollution |
| `DB_NAME`              | MongoDB database name             | weather                                               |
| `DB_CONNECTION_STRING` | MongoDB connection string         | mongodb://localhost:27017                             |
| `GCP_PROJECT_ID`       | GCP Project ID for Secret Manager | None (required for production)                        |
| `JWT_EXPIRATION`       | JWT token expiration (ms)         | 3600000 (1 hour)                                      |
| `JWT_AUDIENCE`         | JWT audience claim                | jwt-audience                                          |
| `JWT_ISSUER`           | JWT issuer claim                  | jwt-issuer                                            |
| `JWT_REALM`            | JWT realm                         | jwt-realm                                             |

## Setting Environment Variables

There are several ways to set environment variables depending on your development environment and
deployment platform.

### Local Development

#### 1. Using IntelliJ IDEA

1. Open your project in IntelliJ IDEA
2. Go to **Run** > **Edit Configurations**
3. Select your run configuration
4. In the **Environment variables** field, add your variables in the format:
   ```
   WEATHER_API_KEY=your_api_key;WEATHER_URL=your_url;AIR_POLLUTION_URL=your_url;DB_NAME=your_db_name;DB_CONNECTION_STRING=your_connection_string;GCP_PROJECT_ID=your_gcp_project_id
   ```
5. Click **Apply** and **OK**

#### 2. Using Command Line (Temporary)

**Linux/macOS:**

```bash
export WEATHER_API_KEY=your_api_key
export WEATHER_URL=your_url
export AIR_POLLUTION_URL=your_url
export DB_NAME=your_db_name
export DB_CONNECTION_STRING=your_connection_string
export GCP_PROJECT_ID=your_gcp_project_id
```

**Windows (Command Prompt):**

```cmd
set WEATHER_API_KEY=your_api_key
set WEATHER_URL=your_url
set AIR_POLLUTION_URL=your_url
set DB_NAME=your_db_name
set DB_CONNECTION_STRING=your_connection_string
set GCP_PROJECT_ID=your_gcp_project_id
```

**Windows (PowerShell):**

```powershell
$env:WEATHER_API_KEY="your_api_key"
$env:WEATHER_URL="your_url"
$env:AIR_POLLUTION_URL="your_url"
$env:DB_NAME="your_db_name"
$env:DB_CONNECTION_STRING="your_connection_string"
$env:GCP_PROJECT_ID="your_gcp_project_id"
```

#### 3. Using .env File with Gradle

1. Create a `.env` file in the project root (make sure to add it to `.gitignore`)
2. Add your environment variables:
   ```
   WEATHER_API_KEY=your_api_key
   WEATHER_URL=your_url
   AIR_POLLUTION_URL=your_url
   DB_NAME=your_db_name
   DB_CONNECTION_STRING=your_connection_string
   GCP_PROJECT_ID=your_gcp_project_id
   JWT_EXPIRATION=86400000
   JWT_AUDIENCE=jwt-audience
   JWT_ISSUER=jwt-issuer
   JWT_REALM=jwt-realm
   ```
3. Add the `dotenv-gradle` plugin to your `build.gradle.kts`:
   ```kotlin
   plugins {
       // other plugins
       id("io.github.cdimascio.dotenv") version "2.0.1"
   }
   ```
4. Apply the environment variables in your build file:
   ```kotlin
   apply {
       plugin("io.github.cdimascio.dotenv")
   }
   ```

### Production Deployment

Set environment variables according to your deployment platform:

#### 1. Google Cloud App Engine

Use `app.yaml`:

```yaml
env_variables:
  WEATHER_API_KEY: "your_api_key"
  # Add other variables as needed
```

#### 2. Docker

**Dockerfile:**

```dockerfile
ENV WEATHER_API_KEY=your_api_key
# Add other variables as needed
```

**docker-compose.yml:**

```yaml
services:
  app:
    environment:
      - WEATHER_API_KEY=your_api_key
      # Add other variables as needed
```

#### 3. Kubernetes

```yaml
containers:
  - name: api
    env:
      - name: WEATHER_API_KEY
        value: "your_api_key"
    # Add other variables as needed
```

> Note: Always set all required environment variables listed in the table above.

## Verifying Environment Variables

To verify your environment variables:

- Add a debug endpoint that displays non-sensitive configuration values
- Add logging statements at application startup
- Check logs for configuration-related errors

> Important: Never log sensitive information like API keys or connection strings

## Google Cloud Secret Manager Integration

This project uses Google Cloud Secret Manager for storing sensitive information in production
environments.

To use Google Cloud Secret Manager:

1. Set up a Google Cloud project and enable the Secret Manager API
2. Create the following secrets:
    - `weather-data-secret` - Weather API key
    - `jwt-secret` - JWT token signing key
    - `db-connection-string` - MongoDB connection string
3. Set the `GCP_PROJECT_ID` environment variable

The application falls back to environment variables if Secret Manager is unavailable.

## Security Notes

### Password Requirements

- Min 8 characters
- At least one: uppercase, lowercase, digit, special character

## Security Best Practices

- Never commit sensitive data to version control
- Use .env files for local development (add to .gitignore)
- Use secrets management in production (GCP Secret Manager, AWS Secrets Manager, Kubernetes Secrets)
- Restrict access with appropriate IAM permissions