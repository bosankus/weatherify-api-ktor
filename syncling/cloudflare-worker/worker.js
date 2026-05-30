/**
 * Syncling CDN Worker — R2 edition
 *
 * Routes:
 *   GET /{projectId}/{locale}/strings.json
 *
 * R2 key format:
 *   Active pointer: "{projectId}/{locale}/active"  (plain text, contains the version string)
 *   Versioned bundle: "{projectId}/{locale}/{version}"  (JSON, has x-amz-meta-bundle-version)
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
    // Expect path: /{projectId}/{locale}/strings.json
    const match = url.pathname.match(/^\/([^/]+)\/([^/]+)\/strings\.json$/);
    if (!match) {
      return jsonResponse({ error: "not_found" }, 404);
    }

    const [, projectId, locale] = match;

    // Resolve active version via the pointer object
    let activeObj;
    try {
      activeObj = await env.BUCKET.get(`${projectId}/${locale}/active`);
    } catch (err) {
      return jsonResponse({ error: "upstream_unavailable" }, 503);
    }
    if (activeObj === null) {
      return jsonResponse({ error: "bundle_not_found" }, 404);
    }
    const version = await activeObj.text();

    // Fetch the versioned bundle
    let bundleObj;
    try {
      bundleObj = await env.BUCKET.get(`${projectId}/${locale}/${version}`);
    } catch (err) {
      return jsonResponse({ error: "upstream_unavailable" }, 503);
    }
    if (bundleObj === null) {
      return jsonResponse({ error: "bundle_not_found" }, 404);
    }

    const value = await bundleObj.text();
    const bundleVersion = bundleObj.customMetadata?.["bundle-version"] ?? version;

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
