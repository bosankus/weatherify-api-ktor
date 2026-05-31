# Build from the repo root — syncling depends on :core which lives there.
# docker build -f syncling/Dockerfile .

# Build stage
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

# Gradle wrapper and root config
COPY gradle/ gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .
COPY gradlew .
COPY gradlew.bat .

# Submodule build files (dependency resolution needs all of them)
COPY core/build.gradle.kts core/build.gradle.kts
COPY weatherify/build.gradle.kts weatherify/build.gradle.kts
COPY syncling/build.gradle.kts syncling/build.gradle.kts

RUN chmod +x gradlew

# Pre-download dependencies
RUN ./gradlew --version && \
    ./gradlew :syncling:dependencies --no-daemon || echo "Warning: dependency check had issues, continuing..."

# Copy source (changes frequently — kept in a separate layer)
COPY core/src/ core/src/
COPY syncling/src/ syncling/src/

# Build only the syncling fat JAR (skips weatherify and the root app)
RUN ./gradlew :syncling:shadowJar --no-daemon -x test || (echo "Build failed!"; exit 1) && \
    if [ -f /app/syncling/build/libs/syncling-all.jar ]; then \
        echo "SUCCESS: syncling-all.jar built"; \
        ls -lh /app/syncling/build/libs/syncling-all.jar; \
    else \
        echo "FAILED: syncling-all.jar not found"; \
        find /app/syncling/build -name "*.jar" -type f; \
        exit 1; \
    fi

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /app/syncling/build/libs/syncling-all.jar ./app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
