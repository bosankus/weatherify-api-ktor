# Makefile for Weatherify API

.PHONY: help clean build test coverage shadow run docker-build docker-run deploy-portfolio portfolio-resume portfolio-og

PORTFOLIO_DIR     ?= portfolio-v2
PORTFOLIO_PROJECT ?= ankush-bose

help:
	@echo "Common targets:"
	@echo "  make build              - Clean, build, tests, and create fat JAR"
	@echo "  make test               - Run unit tests"
	@echo "  make coverage           - Generate JaCoCo coverage report"
	@echo "  make shadow             - Build fat JAR (shadowJar)"
	@echo "  make run                - Run the fat JAR locally on port 8080"
	@echo "  make docker-build       - Build Docker image locally"
	@echo "  make docker-run         - Run Docker image locally on port 8080"
	@echo "  make deploy-portfolio   - Deploy portfolio-v2 to https://bose.androidplay.in (Cloudflare Pages)"
	@echo "  make portfolio-resume FILE=path/to.pdf - Replace the résumé PDF and deploy"
	@echo "  make portfolio-og       - Regenerate the link-preview image (needs Google Chrome)"

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

# Personal site (static, separate from the API). See docs/portfolio.md.
deploy-portfolio:
	npx --yes wrangler pages deploy $(PORTFOLIO_DIR) --project-name $(PORTFOLIO_PROJECT) --branch main --commit-dirty=true

portfolio-resume:
	@test -n "$(FILE)" || (echo "Usage: make portfolio-resume FILE=~/Downloads/Resume_AnkushBose.pdf"; exit 1)
	@test -f "$(FILE)" || (echo "File not found: $(FILE)"; exit 1)
	cp "$(FILE)" $(PORTFOLIO_DIR)/assets/Ankush_Bose_Resume.pdf
	$(MAKE) deploy-portfolio

# Renders portfolio-og/card.html (1200x630 @2x) to the share image used by og:image.
CHROME ?= /Applications/Google Chrome.app/Contents/MacOS/Google Chrome
portfolio-og:
	"$(CHROME)" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=2 \
		--window-size=1200,630 --virtual-time-budget=8000 \
		--screenshot="$(CURDIR)/portfolio-og/og.png" "file://$(CURDIR)/portfolio-og/card.html" 2>/dev/null
	sips -s format jpeg -s formatOptions 82 portfolio-og/og.png --out $(PORTFOLIO_DIR)/assets/og.jpg >/dev/null
	rm -f portfolio-og/og.png
	@echo "Wrote $(PORTFOLIO_DIR)/assets/og.jpg — bump ?v= on og:image in index.html, then make deploy-portfolio"
