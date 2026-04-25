# Build stage
FROM gradle:8.5-jdk17 as builder

WORKDIR /app

# Copy gradle files first (better caching)
COPY gradle/ gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Predownload dependencies to improve build performance and catch dependency issues early
RUN gradle dependencies --no-daemon

# Copy source code (this layer changes frequently)
COPY src/ src/

# Build the application (Ktor plugin creates the -all.jar automatically)
# Use --build-cache=false to prevent caching issues in Cloud Build
RUN gradle clean build --no-daemon --build-cache=false -x test -q && \
    ls -lah /app/build/libs/ && \
    test -f /app/build/libs/weatherify-api-all.jar || (echo "ERROR: weatherify-api-all.jar not found!" && ls -la /app/build/libs/ && exit 1)

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built fat JAR from builder
COPY --from=builder /app/build/libs/weatherify-api-all.jar ./app.jar

# Verify JAR is present and not corrupted
RUN test -f app.jar && jar tf app.jar > /dev/null || (echo "JAR verification failed!" && exit 1)

# Set environment variables
ENV GCP_PROJECT_ID="1017382896100"
ENV DB_NAME="weatherify-app-db"
ENV WEATHER_URL="https://api.openweathermap.org/data/3.0/onecall"
ENV AIR_POLLUTION_URL="https://api.openweathermap.org/data/2.5/air_pollution"
ENV JWT_EXPIRATION="3600000"
ENV JWT_AUDIENCE="jwt-audience"
ENV JWT_ISSUER="jwt-issuer"
ENV JWT_REALM="jwt-realm"
ENV GA_ENABLED="true"
ENV GA_TRACKING_ID="G-EBVRVNN6JF"
ENV GA_MEASUREMENT_ID="G-LWRPRSSDRY"
ENV GA_API_SECRET=""
ENV GRACE_PERIOD_HOURS="72"
ENV SUBSCRIPTION_EXPIRY_CHECK_INTERVAL_MINUTES="720"
ENV FROM_NAME="Androidplay Inc."
ENV FROM_EMAIL="ankush@androidplay.in"
ENV REFUND_FEATURE_ENABLED="true"
ENV INSTANT_REFUND_ENABLED="true"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
