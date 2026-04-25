# Build stage
FROM gradle:8.5-jdk17 as builder

WORKDIR /app

# Copy gradle configuration files FIRST (for better caching and to catch issues early)
COPY gradle/ gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .
COPY gradlew .
COPY gradlew.bat .

# Make gradlew executable
RUN chmod +x gradlew

# Predownload dependencies to improve build performance and catch dependency issues early
RUN ./gradlew --version && \
    ./gradlew dependencies --no-daemon || echo "Warning: Dependency check had issues but continuing..."

# Copy source code (this layer changes frequently)
COPY src/ src/

# Build the application using the gradle wrapper
RUN echo "================================" && \
    echo "Starting Gradle build..." && \
    echo "================================" && \
    ./gradlew clean build --no-daemon -x test || (echo "Build failed!"; exit 1) && \
    echo "================================" && \
    echo "Build completed. Checking for JAR file..." && \
    echo "================================" && \
    if [ -f /app/build/libs/weatherify-api-all.jar ]; then \
        echo "✓ SUCCESS: Found weatherify-api-all.jar"; \
        ls -lh /app/build/libs/weatherify-api-all.jar; \
    else \
        echo "✗ FAILED: weatherify-api-all.jar not found!"; \
        echo "Contents of /app/build:"; \
        find /app/build -name "*.jar" -type f; \
        echo "Full directory listing:"; \
        ls -lah /app/build/libs/ 2>/dev/null || echo "build/libs directory does not exist"; \
        exit 1; \
    fi

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
