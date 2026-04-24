# Makefile for Weatherify API

.PHONY: help clean build test coverage shadow run docker-build docker-run

help:
	@echo "Common targets:"
	@echo "  make build              - Clean, build, tests, and create fat JAR"
	@echo "  make test               - Run unit tests"
	@echo "  make coverage           - Generate JaCoCo coverage report"
	@echo "  make shadow             - Build fat JAR (shadowJar)"
	@echo "  make run                - Run the fat JAR locally on port 8080"
	@echo "  make docker-build       - Build Docker image locally"
	@echo "  make docker-run         - Run Docker image locally on port 8080"

clean:
	./gradlew --no-daemon clean

build:
	./gradlew --no-daemon clean test jacocoTestReport shadowJar

test:
	./gradlew --no-daemon test

coverage:
	./gradlew --no-daemon jacocoTestReport
	@echo "Open build/reports/jacoco/index.html in your browser."

shadow:
	./gradlew --no-daemon shadowJar

run: shadow
	java -jar build/libs/weatherify-api-all.jar

docker-build:
	docker build -t weatherify-api .

docker-run: docker-build
	docker run -p 8080:8080 weatherify-api
