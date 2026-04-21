# Step 1: Build the application
FROM gradle:7-jdk17 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN ./gradlew build --no-daemon

# Step 2: Create the runtime image
FROM eclipse-temurin:17-jre-jammy
COPY --from=build /home/gradle/src/build/libs/*.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
