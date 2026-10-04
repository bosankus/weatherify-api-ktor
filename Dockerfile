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

# Copy submodule build files before dependency download so Gradle can resolve all projects
COPY core/build.gradle.kts core/build.gradle.kts
COPY weatherify/build.gradle.kts weatherify/build.gradle.kts
COPY syncling/build.gradle.kts syncling/build.gradle.kts

# Make gradlew executable
RUN chmod +x gradlew

# Predownload dependencies to improve build performance and catch dependency issues early
RUN ./gradlew --version && \
    ./gradlew dependencies --no-daemon || echo "Warning: Dependency check had issues but continuing..."

# Copy all source code (this layer changes frequently)
COPY src/ src/
COPY core/src/ core/src/
COPY weatherify/src/ weatherify/src/
COPY syncling/src/ syncling/src/

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
        jar tf /app/build/libs/weatherify-api-all.jar > /dev/null && echo "✓ JAR integrity verified"; \
    else \
        echo "✗ FAILED: weatherify-api-all.jar not found!"; \
        echo "Contents of /app/build:"; \
        find /app/build -name "*.jar" -type f; \
        echo "Full directory listing:"; \
        ls -lah /app/build/libs/ 2>/dev/null || echo "build/libs directory does not exist"; \
        exit 1; \
    fi

# Profile-photo encoders. Built on Alpine so they run in the musl runtime image.
# cjpeg -version must contain "mozjpeg". OxiPNG is the upstream musl binary.
FROM eclipse-temurin:17-jre-alpine AS encoders

RUN apk add --no-cache build-base cmake nasm curl tar

WORKDIR /tmp

ARG MOZJPEG_VERSION=4.1.5
RUN curl -fsSL -o mozjpeg.tar.gz "https://github.com/mozilla/mozjpeg/archive/refs/tags/v${MOZJPEG_VERSION}.tar.gz" \
 && tar -xzf mozjpeg.tar.gz \
 && cmake -S "mozjpeg-${MOZJPEG_VERSION}" -B mozjpeg-build \
      -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
      -DCMAKE_BUILD_TYPE=Release \
      -DCMAKE_INSTALL_PREFIX=/opt/mozjpeg \
      -DENABLE_SHARED=1 \
      -DENABLE_STATIC=0 \
      -DPNG_SUPPORTED=0 \
 && cmake --build mozjpeg-build --parallel \
 && cmake --install mozjpeg-build

ARG OXIPNG_VERSION=10.2.1
RUN curl -fsSL -o oxipng.tar.gz "https://github.com/oxipng/oxipng/releases/download/v${OXIPNG_VERSION}/oxipng-${OXIPNG_VERSION}-x86_64-unknown-linux-musl.tar.gz" \
 && tar -xzf oxipng.tar.gz \
 && install -m 0755 "oxipng-${OXIPNG_VERSION}-x86_64-unknown-linux-musl/oxipng" /opt/mozjpeg/bin/oxipng \
 && test -x /opt/mozjpeg/bin/djpeg \
 && LD_LIBRARY_PATH=/opt/mozjpeg/lib /opt/mozjpeg/bin/cjpeg -version 2>&1 | grep -qi mozjpeg \
 && /opt/mozjpeg/bin/oxipng --version

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built fat JAR from builder
COPY --from=builder /app/build/libs/weatherify-api-all.jar ./app.jar

# MozJPEG (cjpeg, djpeg) and OxiPNG for ProfilePhotoBytes. Cloud Run is amd64.
COPY --from=encoders /opt/mozjpeg/bin/cjpeg /opt/mozjpeg/bin/djpeg /opt/mozjpeg/bin/oxipng /usr/local/bin/
COPY --from=encoders /opt/mozjpeg/lib/ /usr/local/lib/
ENV LD_LIBRARY_PATH=/usr/local/lib

# Set environment variables
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
