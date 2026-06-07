/**
 * Syncling CDN Worker — R2 edition
 *
 * Routes:
 *   GET /{projectId}/{locale}/strings.json[?pointer=active|canary]
 *   GET /{projectId}/{locale}/strings.json.sig[?pointer=active|canary]
 *
 * R2 key format:
 *   Pointer:          "{projectId}/{locale}/active"   (plain text, contains version string)
 *   Canary pointer:   "{projectId}/{locale}/canary"   (plain text, contains version string)
 *   Versioned bundle: "{projectId}/{locale}/{version}"  (JSON)
 *   Signature:        "{projectId}/{locale}/{version}.sig"  (hex string)
 *
 * Deploy:
 *   wrangler deploy
 *
 * wrangler.toml binding: [[r2_buckets]] binding = "BUCKET" bucket_name = "<bucket-name>"
 */

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return corsPreflightResponse();
    }

    if (request.method !== "GET") {
      return jsonResponse({ error: "method_not_allowed" }, 405);
    }

    const url = new URL(request.url);

    // Validate pointer query param — only "active" or "canary" allowed.
    const pointerParam = url.searchParams.get("pointer") ?? "active";
    if (pointerParam !== "active" && pointerParam !== "canary") {
      return jsonResponse({ error: "invalid_pointer" }, 400);
    }

    // Route: /{projectId}/{locale}/strings.json
    const bundleMatch = url.pathname.match(/^\/([^/]+)\/([^/]+)\/strings\.json$/);
    if (bundleMatch) {
      const [, projectId, locale] = bundleMatch;
      return serveBundleWithMeta(request, env, projectId, locale, pointerParam);
    }

    // Route: /{projectId}/{locale}/strings.json.sig
    const sigMatch = url.pathname.match(/^\/([^/]+)\/([^/]+)\/strings\.json\.sig$/);
    if (sigMatch) {
      const [, projectId, locale] = sigMatch;
      return serveSignature(env, projectId, locale, pointerParam);
    }

    return jsonResponse({ error: "not_found" }, 404);
  },
};

async function resolveVersion(env, projectId, locale, pointer) {
  let pointerObj;
  try {
    pointerObj = await env.BUCKET.get(`${projectId}/${locale}/${pointer}`);
  } catch (_) {
    return { error: "upstream_unavailable", status: 503 };
  }
  if (pointerObj === null) {
    return { error: "bundle_not_found", status: 404 };
  }
  return { version: await pointerObj.text() };
}

async function serveBundleWithMeta(request, env, projectId, locale, pointer) {
  const resolved = await resolveVersion(env, projectId, locale, pointer);
  if (resolved.error) {
    return jsonResponse({ error: resolved.error }, resolved.status);
  }
  const { version } = resolved;

  let bundleObj;
  try {
    bundleObj = await env.BUCKET.get(`${projectId}/${locale}/${version}`);
  } catch (_) {
    return jsonResponse({ error: "upstream_unavailable" }, 503);
  }
  if (bundleObj === null) {
    return jsonResponse({ error: "bundle_not_found" }, 404);
  }

  const rawJson = await bundleObj.text();
  const bundleVersion = bundleObj.customMetadata?.["bundle-version"] ?? version;

  // Conditional GET
  const clientETag = request.headers.get("If-None-Match");
  const etag = `"${bundleVersion}"`;
  if (clientETag === etag) {
    return new Response(null, { status: 304, headers: corsHeaders() });
  }

  // Inject _meta so SDK StringBundleSerializer can parse it.
  let parsedBundle;
  try {
    parsedBundle = JSON.parse(rawJson);
  } catch (_) {
    return jsonResponse({ error: "bundle_parse_error" }, 500);
  }

  const responseBody = JSON.stringify({
    _meta: {
      version: bundleVersion,
      locale,
      published_at: Date.now(),
    },
    ...parsedBundle,
  });

  return new Response(responseBody, {
    status: 200,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "public, max-age=3600, stale-while-revalidate=86400",
      "ETag": etag,
      "X-Bundle-Version": bundleVersion,
      ...corsHeaders(),
    },
  });
}

async function serveSignature(env, projectId, locale, pointer) {
  const resolved = await resolveVersion(env, projectId, locale, pointer);
  if (resolved.error) {
    return jsonResponse({ error: resolved.error }, resolved.status);
  }
  const { version } = resolved;

  let sigObj;
  try {
    sigObj = await env.BUCKET.get(`${projectId}/${locale}/${version}.sig`);
  } catch (_) {
    return jsonResponse({ error: "upstream_unavailable" }, 503);
  }
  if (sigObj === null) {
    return jsonResponse({ error: "signature_not_found" }, 404);
  }

  const sigHex = await sigObj.text();
  return new Response(sigHex, {
    status: 200,
    headers: {
      "Content-Type": "text/plain; charset=utf-8",
      "Cache-Control": "public, max-age=3600, stale-while-revalidate=86400",
      ...corsHeaders(),
    },
  });
}

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, If-None-Match",
  };
}

function corsPreflightResponse() {
  return new Response(null, { status: 204, headers: corsHeaders() });
}

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      ...corsHeaders(),
    },
  });
}
