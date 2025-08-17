# Contributing to Weatherify API

Thank you for your interest in contributing! This document explains how to work on the project
safely and effectively as the portal grows, with a focus on reusability and zero regressions.

Important: All code in this repository is in production. Do not change existing behavior or
endpoints unless explicitly agreed.

## Table of Contents

- Project Overview
- Branching & Pull Requests
- Coding Standards
- Architecture Overview
- Adding a New Feature/Page (Routes)
- Adding Services, Repositories, and Data Sources
- Static Assets (Web Pages, JS, CSS)
- Testing
- PR Checklist

## Project Overview

- Kotlin + Ktor backend
- Koin for Dependency Injection
- MongoDB for persistence
- Feature-based, modular route registration via RouteRegistrar

Directory highlights:

- src/main/kotlin/base: Ktor setup (Auth, HTTP, Monitoring, Routing)
- src/main/kotlin/route: Route handlers
- src/main/kotlin/di: Koin modules
- src/main/kotlin/data: Data sources and repositories
- src/main/kotlin/domain: Domain interfaces and services
- src/main/resources: Static assets and config
- docs: Architecture and API documentation

## Branching & Pull Requests

- Create feature branches from main: `feature/<short-description>`
- Keep PRs small and focused
- Include tests for changes
- Do not change public API or URLs unless approved (and documented)
- Ensure CI build/tests pass before requesting review

Commit messages:

- Use imperative form: "Add Weather cache invalidation"
- Reference issues where applicable

## Coding Standards

- Kotlin language level: see gradle/libs.versions.toml
- Follow Kotlin style conventions
- Prefer constructor injection and explicit interfaces
- Keep functions small and cohesive
- Log with slf4j; avoid leaking secrets

## Architecture Overview (Scalable Routing)

We use a lightweight feature-based route registration pattern to keep Routing scalable as the portal
grows.

Key elements:

- route/common/RouteRegistrar.kt: tiny contract for route registration
- route/common/Registrars.kt: delegates to existing Route extension functions
- di/RouteModule.kt: provides an ordered List<RouteRegistrar>
- base/Routing.kt: iterates registrars to register routes, and preserves redirects & 404 handling

This enables contributors to add features without editing a central monolithic file and without
changing existing behavior.

## Adding a New Feature/Page (Routes)

1) Create a Ktor Route extension function in `src/main/kotlin/route/...`, e.g.

```kotlin
fun Route.myFeatureRoute() {
    get("/my-feature") { /* handler */ }
}
```

2) Add a registrar in `route/common/Registrars.kt`:

```kotlin
object MyFeatureRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        with(root) { myFeatureRoute() }
    }
}
```

3) Bind it in `di/RouteModule.kt` and include it in the ordered list:

```kotlin
single<RouteRegistrar>(named("myFeature")) { MyFeatureRegistrar }
// Then add get(named("myFeature")) to the list order
```

4) If the feature needs auth, apply Ktor authentication within your Route extension without changing
   URLs.

5) Do not modify existing routes/endpoints unless approved. Preserve existing behavior.

## Adding Services, Repositories, and Data Sources

- Define domain interfaces in `src/main/kotlin/domain/...`
- Implement data-layer repositories in `src/main/kotlin/data/...`
- Wire bindings in `di/DataModule.kt` (repositories/data sources) and `di/DomainModule.kt` (
  services)
- Prefer constructor injection; avoid service locators

## Static Assets (Web Pages, JS, CSS)

- Place new assets under `src/main/resources/web/...`
- Keep shared assets under `src/main/resources/web/css` and `web/js`
- Reuse components and utilities to avoid duplication

## Testing

- Put unit tests under `src/test/kotlin`
- Add tests for services, repositories, and utilities
- For route handlers, prefer testing via Ktor test host where practical
- Run locally:

```
./gradlew build
```

## PR Checklist

- [ ] No existing endpoint behavior changed
- [ ] New routes registered via RouteRegistrar and bound in di/RouteModule.kt
- [ ] DI bindings added for any new services/repositories
- [ ] Tests added/updated; build passes
- [ ] Docs updated (README or docs/architecture.md) if needed
- [ ] No secrets or credentials in code or logs

Thank you for contributing! 🎉
