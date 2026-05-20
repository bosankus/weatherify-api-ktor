# Transloom CDN — Integration Guide

This guide covers everything a developer needs to consume Transloom's global edge-delivered
translation bundles. Translations are written to **Cloudflare KV** on every pipeline run and
served from the nearest point-of-presence (PoP) in under 20 ms.

---

## Table of contents

- [How it works](#how-it-works)
- [CDN endpoint](#cdn-endpoint)
- [Response format](#response-format)
- [Caching and ETag](#caching-and-etag)
- [Android SDK](#android-sdk)
- [iOS SDK](#ios-sdk)
- [JavaScript / Web](#javascript--web)
- [Kotlin Multiplatform (KMP)](#kotlin-multiplatform-kmp)
- [Manual HTTP integration](#manual-http-integration)
- [Bundle versioning](#bundle-versioning)
- [Error codes](#error-codes)
- [Offline / cache-first behaviour](#offline--cache-first-behaviour)
- [Monitoring and dashboard](#monitoring-and-dashboard)
- [FAQ](#faq)

---

## How it works

```
git push  →  Transloom pipeline  →  Cloudflare KV (250+ PoPs)
                                          ↓
                              GET /{projectId}/{locale}/strings.json
                                          ↓
                                   App renders translated UI
```

1. Your team pushes a commit — Transloom detects new or changed strings and translates them.
2. After translation the pipeline runs a **CDN Publish** step that writes a versioned JSON bundle
   per locale into Cloudflare Workers KV.
3. Your app or SDK fetches the bundle for the user's locale on launch (or in the background).
4. Subsequent fetches use the cached bundle until a new version is published; a conditional
   `If-None-Match` request returns `304 Not Modified` and costs near-zero bandwidth.

---

## CDN endpoint

```
GET https://cdn.transloom.dev/{projectId}/{locale}/strings.json
```

| Segment     | Description                                              | Example                        |
|-------------|----------------------------------------------------------|--------------------------------|
| `projectId` | The UUID of your Transloom project                       | `a1b2c3d4-…`                   |
| `locale`    | BCP-47 locale code matching your configured target       | `es`, `fr`, `de`, `ja`, `hi`  |

### Example

```
GET https://cdn.transloom.dev/a1b2c3d4-e5f6-7890-abcd-ef1234567890/es/strings.json
```

> **Finding your Project ID** — open the Transloom dashboard → Projects → click your project →
> copy the UUID from the URL or the project settings panel.

---

## Response format

A successful `200 OK` response body is a flat JSON object mapping every string key to its
translated value for the requested locale.

```json
{
  "onboarding_welcome_title": "Bienvenido a Transloom",
  "settings_save_button": "Guardar cambios",
  "error_network_unavailable": "Red no disponible. Intenta de nuevo.",
  "checkout_confirm_purchase": "Confirmar compra"
}
```

Keys match exactly the string names in your `strings.xml` (Android) or `Localizable.strings`
(iOS) source file. Only strings that have been translated and approved are included — pending
or blocked strings are excluded from published bundles.

### Response headers

| Header              | Value / description                                              |
|---------------------|------------------------------------------------------------------|
| `Content-Type`      | `application/json; charset=utf-8`                                |
| `Cache-Control`     | `public, max-age=3600, stale-while-revalidate=86400`             |
| `ETag`              | `"<bundleVersion>"` — 16-character hex hash of bundle contents   |
| `X-Bundle-Version`  | Same value as ETag, without quotes                               |

---

## Caching and ETag

The worker sets `Cache-Control: public, max-age=3600, stale-while-revalidate=86400`:

- Bundles are **fresh for 1 hour** — no network request is made during this window.
- After 1 hour the bundle is **stale-while-revalidate** for 24 hours — the cached value is
  served immediately while a background revalidation runs.
- **ETag conditional requests** avoid re-downloading unchanged bundles. Store the ETag from the
  initial response and send it as `If-None-Match` on subsequent fetches. A `304 Not Modified`
  response means your cached bundle is still current.

```http
# First request
GET /a1b2c3d4/es/strings.json HTTP/1.1

# Server response
HTTP/1.1 200 OK
ETag: "3f7a9c1b4d2e6f08"
Cache-Control: public, max-age=3600, stale-while-revalidate=86400

{"onboarding_welcome_title":"Bienvenido a Transloom", …}

# Subsequent request with stored ETag
GET /a1b2c3d4/es/strings.json HTTP/1.1
If-None-Match: "3f7a9c1b4d2e6f08"

# Unchanged — zero download cost
HTTP/1.1 304 Not Modified
```

---

## Android SDK

The Android SDK is in production and will be released publicly soon. It wraps the CDN fetch,
disk caching, ETag handling, and fallback to bundled strings transparently.

### 1. Add the dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.transloom:android-sdk:1.0.0")
}
```

### 2. Initialize once in `Application`

```kotlin
import com.transloom.sdk.Transloom

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Transloom.init(
            context  = this,
            apiKey   = "YOUR_API_KEY",      // from Transloom dashboard → Settings → API key
            // Optional — override defaults
            locale   = Locale.getDefault(),  // default: device locale
            fallback = Locale.ENGLISH,       // default: "en" (bundled strings)
            cacheDir = cacheDir              // default: app cache directory
        )
    }
}
```

### 3. Resolve strings at call time

```kotlin
// Anywhere in your app — returns instantly from in-memory cache
val title   = Transloom.string("onboarding_welcome_title")
val saveBtn = Transloom.string("settings_save_button")

// With a fallback for keys not yet published to CDN
val label   = Transloom.string("new_feature_label", default = "New feature")
```

### 4. Observe updates (optional)

```kotlin
// Receive a callback when a new bundle version arrives in the background
Transloom.addUpdateListener { newVersion ->
    // Re-render any live UI that depends on translated strings
    runOnUiThread { refreshUi() }
}
```

### SDK behaviour summary

| Scenario | Behaviour |
|---|---|
| App launch, bundle cached and fresh | Returns cached strings instantly, no network call |
| App launch, bundle stale (>1 h) | Returns cached strings; fetches update in background |
| App launch, no cached bundle | Shows `fallback` locale strings; fetches from CDN; notifies listener when done |
| No network | Returns last cached bundle; falls back to bundled strings if no cache |
| New bundle published mid-session | Silent background fetch; listener called when complete |

---

## iOS SDK

The iOS SDK is in production and will be released publicly soon. It is written in Swift with
zero third-party dependencies and supports UIKit and SwiftUI.

### 1. Add via Swift Package Manager

```swift
// Package.swift
dependencies: [
    .package(url: "https://github.com/transloom/transloom-ios-sdk", from: "1.0.0")
]
```

Or in Xcode: **File → Add Packages** → paste the repository URL.

### 2. Configure in `AppDelegate` or `@main`

```swift
import Transloom

// UIKit — AppDelegate.swift
func application(_ application: UIApplication,
                 didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
    Transloom.configure(
        apiKey:   "YOUR_API_KEY",
        locale:   Locale.current,          // default: device locale
        fallback: Locale(identifier: "en") // default: bundled strings
    )
    return true
}

// SwiftUI — @main struct
@main
struct MyApp: App {
    init() {
        Transloom.configure(apiKey: "YOUR_API_KEY")
    }
    var body: some Scene { WindowGroup { ContentView() } }
}
```

### 3. Resolve strings

```swift
// Static helper — backed by in-memory cache
let title   = Transloom.string("onboarding_welcome_title")
let saveBtn = Transloom.string("settings_save_button")
let label   = Transloom.string("new_feature_label", default: "New feature")

// SwiftUI view modifier
Text(Transloom.string("onboarding_welcome_title"))
    .font(.largeTitle)
```

### 4. Observe updates

```swift
Transloom.onUpdate { version in
    DispatchQueue.main.async {
        // Invalidate and redraw live views
        NotificationCenter.default.post(name: .transloomBundleUpdated, object: nil)
    }
}
```

### SDK behaviour summary

Same cache-first, stale-while-revalidate strategy as the Android SDK. Disk cache is stored in
`Library/Caches/com.transloom/` and is cleared only when the OS evicts cache data under storage
pressure.

---

## JavaScript / Web

No SDK is required for web consumers — use the standard `fetch` API with a simple caching
wrapper.

```typescript
// transloom.ts

const BASE_URL = 'https://cdn.transloom.dev';
const PROJECT_ID = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';

interface StringBundle {
  [key: string]: string;
}

let _bundle: StringBundle = {};
let _etag: string | null = null;

export async function loadTranslations(locale: string): Promise<void> {
  const url = `${BASE_URL}/${PROJECT_ID}/${locale}/strings.json`;
  const headers: HeadersInit = {};
  if (_etag) headers['If-None-Match'] = _etag;

  const res = await fetch(url, { headers });

  if (res.status === 304) return;          // bundle unchanged — keep current
  if (!res.ok) throw new Error(`CDN fetch failed: ${res.status}`);

  _bundle = await res.json();
  _etag   = res.headers.get('ETag');
}

export function t(key: string, fallback = key): string {
  return _bundle[key] ?? fallback;
}

// Usage
await loadTranslations(navigator.language.split('-')[0]); // e.g. "es"
document.getElementById('title')!.textContent = t('onboarding_welcome_title');
```

### React hook

```tsx
import { useEffect, useState } from 'react';
import { loadTranslations, t } from './transloom';

export function useTransloom(locale: string) {
  const [ready, setReady] = useState(false);

  useEffect(() => {
    loadTranslations(locale).then(() => setReady(true));
    // Refresh every 30 min to pick up new CDN bundles mid-session
    const id = setInterval(() => loadTranslations(locale), 30 * 60 * 1000);
    return () => clearInterval(id);
  }, [locale]);

  return { t, ready };
}

// In a component
function WelcomeScreen() {
  const { t, ready } = useTransloom('es');
  if (!ready) return null;
  return <h1>{t('onboarding_welcome_title')}</h1>;
}
```

---

## Kotlin Multiplatform (KMP)

For shared KMP modules, call the CDN directly with `ktor-client`:

```kotlin
// commonMain
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json

private const val CDN = "https://cdn.transloom.dev"
private const val PROJECT_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"

class TransloomClient(private val http: HttpClient) {

    private var bundle: Map<String, String> = emptyMap()
    private var etag: String? = null

    suspend fun load(locale: String) {
        val response: HttpResponse = http.get("$CDN/$PROJECT_ID/$locale/strings.json") {
            etag?.let { header("If-None-Match", it) }
        }
        if (response.status.value == 304) return
        bundle = Json.decodeFromString(response.bodyAsText())
        etag   = response.headers["ETag"]
    }

    fun get(key: String, default: String = key): String = bundle[key] ?: default
}
```

---

## Manual HTTP integration

If you are not using a supported SDK, here is the minimal integration pattern:

```
GET https://cdn.transloom.dev/{projectId}/{locale}/strings.json
```

**Request headers:**

| Header          | Required | Description                                         |
|-----------------|----------|-----------------------------------------------------|
| `If-None-Match` | No       | ETag from the previous response — enables 304 path  |

**Response codes:**

| Code | Meaning                                                              |
|------|----------------------------------------------------------------------|
| 200  | New bundle returned — parse and cache the JSON body and ETag         |
| 304  | Bundle unchanged — your cached copy is still valid                   |
| 404  | Project ID or locale not found, or no approved strings yet published |
| 503  | Cloudflare KV temporarily unavailable — retry with backoff           |

**Recommended polling strategy:**

- Fetch on app launch, user locale change, or foreground resume.
- Store the ETag from each `200` response and send it on the next request.
- After a `503`, wait at least 5 s before retrying; use exponential backoff (max 60 s).
- Do not poll more frequently than every 5 minutes in the foreground.

---

## Bundle versioning

Every bundle is stamped with a 16-character hex `bundleVersion` derived from a SHA-256 hash
of all locale strings. Transloom only writes a new KV entry when the content actually changes —
if your latest commit introduced no new or updated strings, the existing bundle version is
preserved and the `CDN_PUBLISH` pipeline step reports *"Bundle unchanged — already live"*.

The current bundle version is visible in:

- The **CDN Status** widget in your Transloom dashboard (right sidebar).
- The `ETag` / `X-Bundle-Version` response header.
- The **pipeline run card** — once the `Publishing to CDN` step completes, the run footer shows
  a green chip with the short bundle hash.

---

## Error codes

The CDN worker returns JSON error objects for non-2xx responses:

```json
{ "error": "bundle_not_found" }
```

| Error code            | HTTP | Cause and action                                              |
|-----------------------|------|---------------------------------------------------------------|
| `bundle_not_found`    | 404  | No bundle published for this project/locale yet — check the dashboard pipeline activity |
| `not_found`           | 404  | Invalid URL path format — verify project ID and locale code  |
| `method_not_allowed`  | 405  | Only `GET` and `OPTIONS` are supported                        |
| `upstream_unavailable`| 503  | Cloudflare KV read error — retry with exponential backoff    |

---

## Offline / cache-first behaviour

Both native SDKs and the manual integration pattern should follow a **cache-first** strategy:

1. On startup, immediately render UI using the last cached bundle (disk or in-memory).
2. Fire an async CDN fetch in the background.
3. If a new bundle arrives (`200 OK`), update the cache and notify the app (listener /
   notification / state update).
4. If no network is available, the cached bundle is used until connectivity returns.
5. If there is no cached bundle and no network, render using the **fallback locale** (typically
   `en`) that was shipped with the app binary.

This ensures the UI is never blocked waiting for a network response and the app works fully
offline after the first successful CDN fetch.

---

## Monitoring and dashboard

### Dashboard CDN Status widget

The **CDN Status** card in the right sidebar of your Transloom dashboard shows:

- **Locale count** — how many locales are live on the edge.
- **Bundle** — the first 12 characters of the current bundle version hash.
- **Published** — how long ago the bundle was last written to Cloudflare KV.
- **Locale chips** — individual locale codes that are live.
- **Cloudflare PoPs** — pulsing indicators for Mumbai · Singapore · Frankfurt · New York · Tokyo
  confirming that replication is active.
- **SDK consumer note** — a reminder that SDK consumers refresh on next app launch or background
  sync.

### Pipeline run card

Every pipeline run card in the **Pipeline Activity** section includes a `Publishing to CDN`
step (step 7). Once complete, a green globe chip appears in the run footer showing the detail
(`"N locales live on edge"`).

### Real-time SSE notification

When a new bundle is published the dashboard receives a `cdn_ready` Server-Sent Event and
shows a toast:

> Translations live on edge — SDK consumers will refresh on next launch

---

## FAQ

**Q: What happens when I publish a new CDN bundle — do existing app sessions pick it up immediately?**

No. Open sessions continue using the in-memory bundle until the next foreground resume or
background refresh cycle (configurable in the SDKs). Strings are not hot-swapped mid-session by
default to avoid layout shifts. You can call `Transloom.refresh()` from your app code to force
an immediate fetch if needed.

---

**Q: How do I add a new locale?**

Go to **Projects → your project → Edit → Target languages** and add the locale. The next
pipeline run will translate and publish it. The CDN endpoint for that locale becomes live
within ~45 seconds of the run completing.

---

**Q: My locale returns `bundle_not_found` (404) — why?**

Three possible reasons:
1. No pipeline run has completed successfully for this project yet.
2. The locale is configured as a target but no strings have been approved for it yet (pending
   review or blocked strings are excluded).
3. The project ID in your request URL is incorrect — double-check in the dashboard.

---

**Q: Can I pin a specific bundle version?**

Not via the standard endpoint — it always returns the current (latest) bundle. If you need a
specific version for testing, fetch the response and compare the `X-Bundle-Version` header to
the version shown in the dashboard.

---

**Q: Is there a rate limit?**

The CDN is served by Cloudflare's global network with no Transloom-level rate limit. Cloudflare
imposes standard Workers request limits on the free tier. For high-traffic production apps,
ensure your SDK caches aggressively (respect `Cache-Control: max-age=3600`) to avoid
unnecessary origin hits.

---

**Q: How large can a bundle be?**

Cloudflare KV values are limited to 25 MB. A typical app with 500 strings per locale produces
a bundle well under 50 KB. If you approach the limit, Transloom logs a warning during the CDN
publish step and the pipeline step will error.

---

**Q: I need to test a new locale before it goes live — how?**

Use the **Review Portal** (`/transloom/review-portal`) to approve strings for a specific locale,
then manually trigger a CDN publish from the dashboard (`Projects → your project → Publish to CDN`).
The endpoint becomes live immediately and can be verified with `curl`:

```bash
curl -I https://cdn.transloom.dev/{projectId}/{locale}/strings.json
# Expect: HTTP/2 200, ETag: "...", X-Bundle-Version: "..."
```

---

*Last updated: 2026-05-20 · Transloom CDN v1*
