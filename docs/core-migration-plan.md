# `:core` Module Migration Plan

Goal: turn `:core` into a consumer-agnostic platform layer (Mongo, cache, queue, GCP, neutral utils) with a single namespace `com.androidplay.core.*`. All product domain (entities, repositories, DI wiring) leaves `:core` and lives in its owning module.

## Operating rules for every step

Every step is designed so a single subagent can execute it without context from prior steps:

- **Atomicity.** One step = one PR-sized change. Compiles green and tests pass at the end of the step. No "to-be-fixed-in-next-step" left behind.
- **Verification command is mandatory.** Each step lists the exact `./gradlew` invocation to confirm success. The agent must run it and report results.
- **Bridging shims are allowed *within a step*, removed in the same step or the immediately following one (which the same step lists explicitly).** Never leave deprecated aliases drifting across multiple steps.
- **No drive-by edits.** If an agent finds an unrelated issue, it reports it but does not fix it.
- **Imports only — no behavior changes** in any move step. A move step renames packages and updates imports. Logic edits are separate steps.
- **Each step's prompt for the subagent is the indented block under "Subagent prompt".** Copy it verbatim when delegating.

Target end-state package layout for `:core`:

```
com.androidplay.core.mongo          # MongoConnection, IndexSpec, generic helpers
com.androidplay.core.cache          # CacheRepository + Upstash impl
com.androidplay.core.queue          # JobQueueRepository + Upstash impl
com.androidplay.core.gcp            # GCP utilities (when added)
com.androidplay.core.serialization  # FlexibleObjectIdSerializer, etc.
com.androidplay.core.common         # Result, shared exceptions
com.androidplay.core.di             # coreInfraModule (Mongo + cache + queue beans only)
```

Everything else moves to `:weatherify` (new module) or `:transloom` (existing).

---

## Phase 0 — Baseline

### Step 0.1 — Snapshot baseline build

Capture a clean baseline so later steps can diff against it.

**Verification:** `./gradlew clean build` (root + all modules) passes. Record the command output checksum / test count.

**Subagent prompt:**

> Run `./gradlew clean build` from the repo root `/Users/t0304iw/Desktop/androidplay/api`. Report: (a) BUILD SUCCESSFUL/FAILED, (b) total test count from the test summary lines, (c) any warnings about deprecated APIs. Do not modify any files. If build fails, stop and report — do not attempt fixes.

---

## Phase 1 — Establish the neutral namespace inside `:core`

These steps create `com.androidplay.core.*` and migrate the **already neutral** code into it. No consumer changes yet.

### Step 1.1 — Move `FlexibleObjectIdSerializer` into `com.androidplay.core.serialization`

**Files:**
- Move: `core/src/main/kotlin/bose/ankush/data/model/FlexibleObjectIdSerializer.kt` → `core/src/main/kotlin/com/androidplay/core/serialization/FlexibleObjectIdSerializer.kt`
- Update package declaration to `com.androidplay.core.serialization`
- Update **all** imports of `bose.ankush.data.model.FlexibleObjectIdSerializer` across the repo

**Verification:** `./gradlew :core:compileKotlin build`

**Subagent prompt:**

> Move the file `core/src/main/kotlin/bose/ankush/data/model/FlexibleObjectIdSerializer.kt` to `core/src/main/kotlin/com/androidplay/core/serialization/FlexibleObjectIdSerializer.kt`. Change its `package` declaration to `com.androidplay.core.serialization`. Then `grep -rn "bose.ankush.data.model.FlexibleObjectIdSerializer\|bose.ankush.data.model.\*" /Users/t0304iw/Desktop/androidplay/api` and update every import to `com.androidplay.core.serialization.FlexibleObjectIdSerializer`. For wildcard `bose.ankush.data.model.*` imports, add an explicit `com.androidplay.core.serialization.FlexibleObjectIdSerializer` import only if the file actually references the serializer. Run `./gradlew build`. Report files changed and build result.

### Step 1.2 — Move `Result` and `QueueConnectionException` to `com.androidplay.core.common`

**Files:**
- `core/src/main/kotlin/domain/model/Result.kt` → `core/src/main/kotlin/com/androidplay/core/common/Result.kt`
- `core/src/main/kotlin/domain/exception/QueueConnectionException.kt` → `core/src/main/kotlin/com/androidplay/core/common/QueueConnectionException.kt`
- Update every importer.

**Verification:** `./gradlew build`

**Subagent prompt:**

> Move two files into the `com.androidplay.core.common` package:
> 1. `core/src/main/kotlin/domain/model/Result.kt` → `core/src/main/kotlin/com/androidplay/core/common/Result.kt` (package = `com.androidplay.core.common`)
> 2. `core/src/main/kotlin/domain/exception/QueueConnectionException.kt` → same target package
>
> Grep for `domain.model.Result` and `domain.exception.QueueConnectionException` across the repo and update imports. Run `./gradlew build`. Report files changed.

---

## Phase 2 — Extract neutral infrastructure from product-named files

### Step 2.1 — Move cache (`UpstashCacheRepository` + `CacheRepository`) to `com.androidplay.core.cache`

**Files:**
- `core/src/main/kotlin/domain/repository/CacheRepository.kt` → `core/src/main/kotlin/com/androidplay/core/cache/CacheRepository.kt`
- `core/src/main/kotlin/data/repository/UpstashCacheRepository.kt` → `core/src/main/kotlin/com/androidplay/core/cache/UpstashCacheRepository.kt`
- Update all imports.

**Verification:** `./gradlew build`

**Subagent prompt:**

> Move `CacheRepository` interface and `UpstashCacheRepository` implementation into the new package `com.androidplay.core.cache`. Source files are `core/src/main/kotlin/domain/repository/CacheRepository.kt` and `core/src/main/kotlin/data/repository/UpstashCacheRepository.kt`. Update package declarations and every import across the repo (grep for `domain.repository.CacheRepository` and `data.repository.UpstashCacheRepository`). Run `./gradlew build`.

### Step 2.2 — Move queue (`UpstashJobQueueRepository` + `JobQueueRepository`) to `com.androidplay.core.queue`

Mirror of Step 2.1 for queue files.

**Verification:** `./gradlew build`

**Subagent prompt:**

> Move `JobQueueRepository` interface (`core/src/main/kotlin/domain/repository/JobQueueRepository.kt`) and `UpstashJobQueueRepository` (`core/src/main/kotlin/data/repository/UpstashJobQueueRepository.kt`) into `com.androidplay.core.queue`. Update package declarations and every importer. Run `./gradlew build`.

### Step 2.3 — Introduce `com.androidplay.core.mongo.MongoConnection` (neutral) alongside the existing factory

Do **not** delete `MongoDatabaseFactory` yet — only add the new neutral API and have the old one delegate to it.

**New file:** `core/src/main/kotlin/com/androidplay/core/mongo/MongoConnection.kt`

```kotlin
package com.androidplay.core.mongo

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.util.concurrent.TimeUnit

object MongoConnection {
    fun connect(uri: String, databaseName: String): MongoDatabase =
        MongoClient.create(uri).getDatabase(databaseName)

    fun connect(settings: MongoClientSettings, databaseName: String): MongoDatabase =
        MongoClient.create(settings).getDatabase(databaseName)

    fun pooledSettings(
        uri: String,
        maxPoolSize: Int = 50,
        minPoolSize: Int = 5,
        maxWaitSeconds: Long = 5,
        connectTimeoutSeconds: Long = 5,
        readTimeoutSeconds: Long = 10,
    ): MongoClientSettings = MongoClientSettings.builder()
        .applyConnectionString(ConnectionString(uri))
        .applyToConnectionPoolSettings { it.maxSize(maxPoolSize).minSize(minPoolSize)
            .maxWaitTime(maxWaitSeconds, TimeUnit.SECONDS) }
        .applyToSocketSettings { it.connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS) }
        .build()
}
```

**Verification:** `./gradlew :core:compileKotlin` — new code compiles. No callers yet.

**Subagent prompt:**

> Create the file `core/src/main/kotlin/com/androidplay/core/mongo/MongoConnection.kt` with the exact content provided in step 2.3 of `docs/core-migration-plan.md`. Do not modify any other files. Run `./gradlew :core:compileKotlin`. Report result.

### Step 2.4 — Introduce `IndexSpec` API and `MongoIndexer.ensure(...)` in core

Lets each consumer declare its own indexes without core knowing collection names.

**New file:** `core/src/main/kotlin/com/androidplay/core/mongo/IndexSpec.kt`

```kotlin
package com.androidplay.core.mongo

import com.mongodb.client.model.IndexOptions
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.bson.Document
import org.slf4j.LoggerFactory

data class IndexSpec(
    val collection: String,
    val keys: Document,
    val options: IndexOptions = IndexOptions(),
)

object MongoIndexer {
    private val log = LoggerFactory.getLogger(MongoIndexer::class.java)
    suspend fun ensure(db: MongoDatabase, specs: List<IndexSpec>) {
        specs.forEach { spec ->
            try {
                db.getCollection<Document>(spec.collection)
                    .createIndex(spec.keys, spec.options)
            } catch (e: Exception) {
                log.error("Failed to ensure index on ${spec.collection}: ${spec.keys.toJson()}", e)
            }
        }
        log.info("MongoDB indexes ensured for database '{}'", db.name)
    }
}
```

**Verification:** `./gradlew :core:compileKotlin`

**Subagent prompt:**

> Create the file `core/src/main/kotlin/com/androidplay/core/mongo/IndexSpec.kt` exactly as specified in step 2.4 of `docs/core-migration-plan.md`. Do not change anything else. Run `./gradlew :core:compileKotlin`.

### Step 2.5 — Add neutral `coreInfraModule` Koin DI

**New file:** `core/src/main/kotlin/com/androidplay/core/di/CoreInfraModule.kt` — exposes only `MongoDatabase`, `CacheRepository`, `JobQueueRepository`. Each consumer composes this with its own product modules.

```kotlin
package com.androidplay.core.di

import com.androidplay.core.cache.CacheRepository
import com.androidplay.core.cache.UpstashCacheRepository
import com.androidplay.core.common.QueueConnectionException
import com.androidplay.core.mongo.MongoConnection
import com.androidplay.core.queue.JobQueueRepository
import com.androidplay.core.queue.UpstashJobQueueRepository
import com.mongodb.MongoClientSettings
import org.koin.dsl.module
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("CoreInfraModule")

fun coreInfraModule(
    mongoUri: String,
    databaseName: String,
    redisUrl: String,
    mongoSettings: MongoClientSettings? = null,
) = module {
    single { mongoSettings
        ?.let { MongoConnection.connect(it, databaseName) }
        ?: MongoConnection.connect(mongoUri, databaseName) }
    single<CacheRepository> { UpstashCacheRepository(redisUrl) }
    single<JobQueueRepository> {
        UpstashJobQueueRepository(redisUrl).also {
            try { it.connect() } catch (e: QueueConnectionException) {
                log.warn("Upstash queue unavailable: {}. Falling back.", e.message)
            }
        }
    }
}
```

**Verification:** `./gradlew :core:compileKotlin`. No callers yet — old `cacheModule` / `coreModule` / `weatherifyModule` remain.

**Subagent prompt:**

> Create `core/src/main/kotlin/com/androidplay/core/di/CoreInfraModule.kt` with the content from step 2.5 of `docs/core-migration-plan.md`. Verify with `./gradlew :core:compileKotlin`.

---

## Phase 3 — Extract weatherify into its own `:weatherify` module

The legacy app at `src/main/kotlin` will become an application module that depends on a new `:weatherify` library module containing what currently lives under `bose.ankush.*` and the unrooted `data/`, `domain/`, `util/` directories of `:core`.

### Step 3.1 — Create empty `:weatherify` Gradle module

**Files:**
- New `weatherify/build.gradle.kts` — mirrors `:core` plugins + `dependencies { api(project(":core")); implementation(libs.koin.core); ... }`
- Update `settings.gradle.kts`: `include(":core", ":transloom", ":weatherify")`
- Create `weatherify/src/main/kotlin/.gitkeep`

**Verification:** `./gradlew :weatherify:compileKotlin` (compiles empty module)

**Subagent prompt:**

> Create a new Gradle module `:weatherify` at `/Users/t0304iw/Desktop/androidplay/api/weatherify`. Mirror the structure of `:core`: copy `core/build.gradle.kts` to `weatherify/build.gradle.kts` and add `api(project(":core"))` to its dependencies block, plus `implementation(libs.koin.core)`, `implementation(libs.kotlinx.serialization.json)`, `implementation(libs.kotlinx.datetime)`. Create directory `weatherify/src/main/kotlin/` with a `.gitkeep` file. Edit `settings.gradle.kts` to add `:weatherify` to the include list. Run `./gradlew :weatherify:compileKotlin`.

### Step 3.2 — Move `bose.ankush.data.model.*` (entities) to `:weatherify`

Move every file under `core/src/main/kotlin/bose/ankush/data/model/` (User, Feedback, Note, Payment, RefundModels, SavedLocation, ServiceCatalogModels, FinancialModels) — but **not** `FlexibleObjectIdSerializer` (already moved in 1.1) — to `weatherify/src/main/kotlin/com/androidplay/weatherify/domain/`. Change package to `com.androidplay.weatherify.domain`.

**Verification:** `./gradlew build` after also doing step 3.3 — these two are tightly coupled and must land together. Treat 3.2 + 3.3 as a single subagent task.

**Subagent prompt (combined 3.2 + 3.3):**

> Phase: move all weatherify entity classes and update every importer.
>
> 1. Move every `.kt` file under `core/src/main/kotlin/bose/ankush/data/model/` — except `FlexibleObjectIdSerializer.kt` if still present — to `weatherify/src/main/kotlin/com/androidplay/weatherify/domain/`. Change each file's package declaration to `com.androidplay.weatherify.domain`.
> 2. Run `grep -rn "bose.ankush.data.model" /Users/t0304iw/Desktop/androidplay/api/src /Users/t0304iw/Desktop/androidplay/api/transloom /Users/t0304iw/Desktop/androidplay/api/core` to find all importers.
> 3. In every importer, replace `bose.ankush.data.model` with `com.androidplay.weatherify.domain`.
> 4. Add `implementation(project(":weatherify"))` to the root `build.gradle.kts` dependencies (the legacy weatherify app module).
> 5. Run `./gradlew build`. Report which files changed and the build result.

### Step 3.3 — (executed inside 3.2)

### Step 3.4 — Move weatherify repository interfaces & impls to `:weatherify`

**Files to move:**
- `core/src/main/kotlin/domain/repository/{User,Feedback,Payment,Refund,SavedLocation,Note,ServiceCatalog}Repository.kt` → `weatherify/src/main/kotlin/com/androidplay/weatherify/repository/`
- `core/src/main/kotlin/data/repository/{User,Feedback,Payment,Refund,SavedLocation,Note,ServiceCatalog}RepositoryImpl.kt` → `weatherify/src/main/kotlin/com/androidplay/weatherify/repository/mongo/`

Package becomes `com.androidplay.weatherify.repository` / `…repository.mongo`. Update every importer (root `src/`, tests).

**Verification:** `./gradlew build`

**Subagent prompt:**

> Move the seven weatherify repository interfaces (`UserRepository`, `FeedbackRepository`, `PaymentRepository`, `RefundRepository`, `SavedLocationRepository`, `NoteRepository`, `ServiceCatalogRepository`) from `core/src/main/kotlin/domain/repository/` to `weatherify/src/main/kotlin/com/androidplay/weatherify/repository/` (package `com.androidplay.weatherify.repository`). Move the seven matching `*Impl` classes from `core/src/main/kotlin/data/repository/` to `weatherify/src/main/kotlin/com/androidplay/weatherify/repository/mongo/` (package `com.androidplay.weatherify.repository.mongo`). Do **not** move `UpstashCacheRepository` or `UpstashJobQueueRepository` — they belong to core. Update all imports across the repo. Run `./gradlew build`.

### Step 3.5 — Move `DatabaseModule` (typed accessors) to `:weatherify`

Now that the entities live in `:weatherify`, the typed accessor class follows.

**Move:** `core/src/main/kotlin/data/source/DatabaseModule.kt` → `weatherify/src/main/kotlin/com/androidplay/weatherify/db/WeatherifyDb.kt`. Rename class to `WeatherifyDb`. Package `com.androidplay.weatherify.db`. Update importers.

**Verification:** `./gradlew build`

**Subagent prompt:**

> Move `core/src/main/kotlin/data/source/DatabaseModule.kt` to `weatherify/src/main/kotlin/com/androidplay/weatherify/db/WeatherifyDb.kt`. Change package to `com.androidplay.weatherify.db` and rename the class from `DatabaseModule` to `WeatherifyDb`. Grep for usages of `data.source.DatabaseModule` and `DatabaseModule(` across the repo and update them (imports + constructor call sites). Run `./gradlew build`.

### Step 3.6 — Move `ValidationUtils` to `:weatherify`

It depends on weatherify's `User`/`UserRole`, so it belongs there.

**Move:** `core/src/main/kotlin/util/ValidationUtils.kt` → `weatherify/src/main/kotlin/com/androidplay/weatherify/util/ValidationUtils.kt`. Package `com.androidplay.weatherify.util`. Update every importer.

**Verification:** `./gradlew build`

**Subagent prompt:**

> Move `core/src/main/kotlin/util/ValidationUtils.kt` to `weatherify/src/main/kotlin/com/androidplay/weatherify/util/ValidationUtils.kt`. Update package to `com.androidplay.weatherify.util`. Grep for `import util.ValidationUtils` and update. Run `./gradlew build`.

### Step 3.7 — Move `weatherifyModule` Koin DI to `:weatherify`, port to `coreInfraModule`

**Move:** `core/src/main/kotlin/com/transloom/core/di/WeatherifyModule.kt` → `weatherify/src/main/kotlin/com/androidplay/weatherify/di/WeatherifyModule.kt`. Package `com.androidplay.weatherify.di`.

**Edit:** Rewrite the module to:
- No longer create the `MongoDatabase` bean itself — expect it from `coreInfraModule`.
- Define a `weatherifyIndexes(): List<IndexSpec>` listing every index currently in `MongoDatabaseFactory.createWeatherifyIndexes`.
- Provide `WeatherifyDb` and the seven repository bindings.

**Edit:** Root `src/main/kotlin/Application.kt` (or wherever Koin is started) — replace `weatherifyModule(uri, dbName)` with `coreInfraModule(uri, dbName, redisUrl) + weatherifyModule()`, and call `MongoIndexer.ensure(get(), weatherifyIndexes())` at startup.

**Verification:** `./gradlew build` and `./gradlew :weatherify:test` (or whatever runs the existing test suite).

**Subagent prompt:**

> Read step 3.7 of `docs/core-migration-plan.md` for full context. Tasks:
> 1. Move `core/src/main/kotlin/com/transloom/core/di/WeatherifyModule.kt` to `weatherify/src/main/kotlin/com/androidplay/weatherify/di/WeatherifyModule.kt` and rewrite it as described: it must consume the `MongoDatabase` bean from `coreInfraModule` (do not create one), provide `WeatherifyDb` via `single { WeatherifyDb(get()) }`, and bind all seven weatherify repositories. Also export a top-level function `weatherifyIndexes(): List<IndexSpec>` containing every index that `MongoDatabaseFactory.createWeatherifyIndexes` currently creates (copy them verbatim from `core/src/main/kotlin/com/transloom/core/mongodb/MongoDatabase.kt` lines 55–107).
> 2. Find the legacy weatherify app's Koin startup (likely `src/main/kotlin/Application.kt` or a file in `src/main/kotlin/di/`). Replace any call wiring `weatherifyModule(uri, dbName)` with `coreInfraModule(uri, dbName, redisUrl)` plus the new `weatherifyModule()`. After Koin starts, call `runBlocking { MongoIndexer.ensure(get<MongoDatabase>(), weatherifyIndexes()) }`. Remove any direct `cacheModule(...)` call — it's subsumed by `coreInfraModule`.
> 3. Run `./gradlew build`. If tests fail, report — do not edit them blindly.

### Step 3.8 — Delete weatherify dead code from `:core`

Once 3.7 builds green, the now-unused `MongoDatabaseFactory.weatherifyClientSettings` and `createWeatherifyIndexes` are dead.

**Edit:** `core/src/main/kotlin/com/transloom/core/mongodb/MongoDatabase.kt` — delete `weatherifyClientSettings(...)` and `createWeatherifyIndexes(...)`. Keep `MongoDatabaseFactory` only for transloom (will be removed in Phase 4).

**Verification:** `./gradlew build`

**Subagent prompt:**

> Open `core/src/main/kotlin/com/transloom/core/mongodb/MongoDatabase.kt`. Delete the methods `weatherifyClientSettings` (lines around 39–53) and `createWeatherifyIndexes` (lines around 55–107). Keep everything else. First grep `grep -rn "weatherifyClientSettings\|createWeatherifyIndexes" /Users/t0304iw/Desktop/androidplay/api` — there must be zero references outside this file. If there are any, stop and report. Otherwise delete and run `./gradlew build`.

---

## Phase 4 — Move transloom-specific code out of `:core`

### Step 4.1 — Move `com.transloom.core.domain.*` to `:transloom`

**Move:** every file in `core/src/main/kotlin/com/transloom/core/domain/` → `transloom/src/main/kotlin/com/transloom/domain/`. Package becomes `com.transloom.domain`.

**Verification:** `./gradlew build`

**Subagent prompt:**

> Move all files under `core/src/main/kotlin/com/transloom/core/domain/` to `transloom/src/main/kotlin/com/transloom/domain/`. Change each file's package declaration from `com.transloom.core.domain` to `com.transloom.domain`. Grep for `com.transloom.core.domain` and update all importers. Run `./gradlew build`.

### Step 4.2 — Move transloom repository interfaces

**Move:** `core/src/main/kotlin/com/transloom/core/repository/*.kt` → `transloom/src/main/kotlin/com/transloom/repository/`. Package `com.transloom.repository`. Update importers.

**Verification:** `./gradlew build`

**Subagent prompt:**

> Move all six transloom repository interface files from `core/src/main/kotlin/com/transloom/core/repository/` to `transloom/src/main/kotlin/com/transloom/repository/`. Update package to `com.transloom.repository`. Grep `com.transloom.core.repository` and update imports. Run `./gradlew build`.

### Step 4.3 — Move transloom Mongo repository impls

**Move:** `core/src/main/kotlin/com/transloom/core/mongodb/Mongo*Repository.kt` → `transloom/src/main/kotlin/com/transloom/repository/mongo/`. Package `com.transloom.repository.mongo`. Leave `MongoDatabase.kt` (the factory) in core for now.

**Verification:** `./gradlew build`

**Subagent prompt:**

> Move the six `Mongo*Repository.kt` files from `core/src/main/kotlin/com/transloom/core/mongodb/` to `transloom/src/main/kotlin/com/transloom/repository/mongo/`. Update package to `com.transloom.repository.mongo`. Do NOT move `MongoDatabase.kt` (the factory) yet. Update importers. Run `./gradlew build`.

### Step 4.4 — Move `coreModule` Koin DI to `:transloom` and port to `coreInfraModule`

Mirror of 3.7 for transloom.

**Move:** `core/src/main/kotlin/com/transloom/core/di/CoreModule.kt` → `transloom/src/main/kotlin/com/transloom/di/TransloomModule.kt`. Rename function `coreModule` → `transloomModule`. Package `com.transloom.di`.

**Edit:** Rewrite to consume `MongoDatabase` from `coreInfraModule`. Export `transloomIndexes(): List<IndexSpec>` from the contents of `MongoDatabaseFactory.createTransloomIndexes`.

**Edit:** `transloom/src/main/kotlin/com/transloom/Application.kt` — replace `coreModule(uri, key, dbName) + cacheModule(redisUrl)` with `coreInfraModule(uri, dbName, redisUrl) + transloomModule(encryptionKey)`. Add `MongoIndexer.ensure(get(), transloomIndexes())` at startup.

**Verification:** `./gradlew build`

**Subagent prompt:**

> Step 4.4 — see `docs/core-migration-plan.md`. Tasks:
> 1. Move `core/src/main/kotlin/com/transloom/core/di/CoreModule.kt` to `transloom/src/main/kotlin/com/transloom/di/TransloomModule.kt`. Rename the function `coreModule` to `transloomModule(encryptionKey: String)` (drop mongoUri/databaseName parameters — those are owned by `coreInfraModule`). Do not create a `MongoDatabase` bean inside this module. Add a top-level `transloomIndexes(): List<IndexSpec>` containing the index specs currently in `MongoDatabaseFactory.createTransloomIndexes` (verbatim translation, lines 109–155 of `core/src/main/kotlin/com/transloom/core/mongodb/MongoDatabase.kt`).
> 2. Edit `transloom/src/main/kotlin/com/transloom/Application.kt`: replace `coreModule(...) + cacheModule(...)` with `coreInfraModule(mongoUri, dbName, redisUrl) + transloomModule(encryptionKey)`. After `startKoin`, call `runBlocking { MongoIndexer.ensure(get<MongoDatabase>(), transloomIndexes()) }`.
> 3. Run `./gradlew build`.

### Step 4.5 — Delete `MongoDatabaseFactory` and old DI modules from `:core`

Now nothing in core or any consumer references `MongoDatabaseFactory`, `cacheModule`, `coreModule`, or `weatherifyModule` (the old one).

**Edits:**
- Delete `core/src/main/kotlin/com/transloom/core/mongodb/MongoDatabase.kt`
- Delete `core/src/main/kotlin/com/transloom/core/di/CacheModule.kt` (file moved/replaced earlier? — confirm; if `cacheModule` still exists here, delete it)
- Confirm `core/src/main/kotlin/com/transloom/core/` is empty and delete directory tree.

**Verification:** `./gradlew build`

**Subagent prompt:**

> Run `grep -rn "MongoDatabaseFactory\|cacheModule\|fun coreModule\|fun weatherifyModule" /Users/t0304iw/Desktop/androidplay/api/{core,src,transloom,weatherify}`. There must be zero hits outside the files about to be deleted. If any external reference remains, stop and report it. Otherwise delete:
> - `core/src/main/kotlin/com/transloom/core/mongodb/MongoDatabase.kt`
> - `core/src/main/kotlin/com/transloom/core/di/CacheModule.kt` (if it still exists with old `cacheModule` function)
> - The empty `core/src/main/kotlin/com/transloom/` directory tree.
>
> Also delete the now-empty parent directories: `core/src/main/kotlin/bose/`, `core/src/main/kotlin/data/`, `core/src/main/kotlin/domain/`, `core/src/main/kotlin/util/`. Use `find core/src/main/kotlin/{bose,data,domain,util} -type f` first; if any file remains, stop and report. Run `./gradlew build`.

---

## Phase 5 — Cleanup & rename

### Step 5.1 — Rename core artifact group

`core/build.gradle.kts`: `group = "com.transloom.core"` → `group = "com.androidplay.core"`.

**Verification:** `./gradlew build`

**Subagent prompt:**

> Edit `core/build.gradle.kts`: change `group = "com.transloom.core"` to `group = "com.androidplay.core"`. Run `./gradlew build`.

### Step 5.2 — Final audit

Confirm no `bose.ankush`, `com.transloom.core`, unrooted `data.`/`domain.`/`util.` packages remain in `:core/src/main/kotlin`.

**Verification:**
- `find core/src/main/kotlin -type d` should show only `com/androidplay/core/{cache,common,di,mongo,queue,serialization}` (and `gcp` if added).
- `./gradlew clean build` matches the test count baseline from Step 0.1.

**Subagent prompt:**

> Run:
> 1. `find /Users/t0304iw/Desktop/androidplay/api/core/src/main/kotlin -type d`
> 2. `grep -rln "package bose.ankush\|package com.transloom.core\|package data\.\|package domain\.\|package util$" /Users/t0304iw/Desktop/androidplay/api/core`
> 3. `./gradlew clean build`
>
> The directory listing must contain only paths under `com/androidplay/core/`. The grep must return zero results. Build must succeed and total test count must match the baseline recorded in Step 0.1. Report any deviation.

---

## Roll-back protocol

Each step is one commit. If a later step uncovers that an earlier move was wrong, `git revert <commit>` of the offending step. Do not amend or rebase already-committed steps — they're the rollback boundary.

## Open questions to resolve before starting

1. **GCP utilities.** None currently in `:core`. When/where do they get added? Phase 2 has a placeholder package; if GCP code lives elsewhere today, add a Phase 2.6 to move it. - Answer: GCPUtils should stay inside core module, with capabilities to provide all kinds of resources required by all the consumers.
2. **Weatherify legacy module name.** This plan uses `:weatherify`. Confirm or pick a different name before Step 3.1. - Answer: in the legacy code i.e outside transloom module, our current code has weatherify API endpoints, and dashboard related endpoints, ideally we should have 2 different modules :weatherify & :dashboard with their own purposes. Probably that is huge change and we must do it. Also know that :dashboard will control all the features and monitor their items as it is doing now for weatherify, analyse it before decising."
3. **Test module placement.** `src/test/kotlin/...` currently sits at root. After Phase 3, do tests stay at root (testing the app) or move into `:weatherify` (testing the library)? Default in this plan: stay at root, since they test routes/services. Confirm before Step 3.4. - Answers: ignore tests as of now. 
