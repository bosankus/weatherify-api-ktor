/**
 * Transloom CDN Worker
 *
 * Routes:
 *   GET /{projectId}/{locale}/strings.json
 *
 * KV key format: "{projectId}:{locale}"
 * KV metadata format: { "bundleVersion": "<hex>" }
 *
 * Deploy:
 *   wrangler deploy
 *
 * wrangler.toml binding: [[kv_namespaces]] binding = "BUNDLES" id = "<namespace_id>"
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
    // Expect path: /{projectId}/{locale}/strings.json
    const match = url.pathname.match(/^\/([^/]+)\/([^/]+)\/strings\.json$/);
    if (!match) {
      return jsonResponse({ error: "not_found" }, 404);
    }

    const [, projectId, locale] = match;
    const kvKey = `${projectId}:${locale}`;

    let valueWithMeta;
    try {
      valueWithMeta = await env.BUNDLES.getWithMetadata(kvKey, { type: "text" });
    } catch (err) {
      // KV is unavailable — degrade to 503 so stale-while-revalidate can serve cached response.
      return jsonResponse({ error: "upstream_unavailable" }, 503);
    }

    if (valueWithMeta.value === null) {
      return jsonResponse({ error: "bundle_not_found" }, 404);
    }

    const { value, metadata } = valueWithMeta;
    const bundleVersion = metadata?.bundleVersion ?? hashValue(value);

    // Conditional GET: return 304 if client's ETag matches.
    const clientETag = request.headers.get("If-None-Match");
    const etag = `"${bundleVersion}"`;
    if (clientETag === etag) {
      return new Response(null, {
        status: 304,
        headers: corsHeaders(),
      });
    }

    return new Response(value, {
      status: 200,
      headers: {
        "Content-Type": "application/json; charset=utf-8",
        "Cache-Control": "public, max-age=3600, stale-while-revalidate=86400",
        "ETag": etag,
        "X-Bundle-Version": bundleVersion,
        ...corsHeaders(),
      },
    });
  },
};

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, If-None-Match",
  };
}

function corsPreflightResponse() {
  return new Response(null, {
    status: 204,
    headers: corsHeaders(),
  });
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

// Fallback: simple djb2 hash when KV metadata is absent.
function hashValue(str) {
  let h = 5381;
  for (let i = 0; i < str.length; i++) {
    h = ((h << 5) + h) ^ str.charCodeAt(i);
    h = h >>> 0;
  }
  return h.toString(16);
}
