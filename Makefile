# Makefile for Weatherify API

.PHONY: help clean build test coverage shadow run cloudbuild-build cloudbuild-deploy app-deploy

help:
	@echo "Common targets:"
	@echo "  make build              - Clean, build, tests, and create fat JAR"
	@echo "  make test               - Run unit tests"
	@echo "  make coverage           - Generate JaCoCo coverage report"
	@echo "  make shadow             - Build fat JAR (shadowJar)"
	@echo "  make run                - Run the fat JAR locally on port 8080"
	@echo "  make cloudbuild-build   - Cloud Build: build only (no deploy)"
	@echo "  make cloudbuild-deploy  - Cloud Build: build and deploy to App Engine (PROMOTE=true|false)"
	@echo "  make app-deploy         - Deploy current workspace to App Engine directly"

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

cloudbuild-build:
	gcloud builds submit --config=cloudbuild.yaml --substitutions=_DEPLOY="false" .

# Usage: make cloudbuild-deploy PROMOTE=true
cloudbuild-deploy:
	gcloud builds submit --config=cloudbuild.yaml --substitutions=_DEPLOY="true",_PROMOTE="${PROMOTE:-false}",_APP_YAML="src/main/appengine/app.yaml" .

app-deploy:
	gcloud app deploy src/main/appengine/app.yaml --quiet --no-promote
