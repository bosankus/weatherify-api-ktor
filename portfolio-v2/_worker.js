// Cloudflare Pages advanced-mode worker for bose.androidplay.in.
// Pages never serves this file publicly; every request passes through here first.
//   GET /api/medium  -> latest Medium posts as JSON (cached at the edge for an hour)
//   anything else    -> the static site
// Medium's RSS sends no CORS headers, so the browser can't read it directly.

const MEDIUM_USER = 'bosankus';
const FEED_URL = `https://medium.com/feed/@${MEDIUM_USER}`;
const CACHE_SECONDS = 3600;
const MAX_POSTS = 6;

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (url.pathname === '/api/medium') return medium(request, ctx);
    if (PROTECTED_IMAGES.has(url.pathname)) return protectedImage(request, env, url);
    return env.ASSETS.fetch(request);
  },
};

// The portrait is only served as part of this site's own pages. Opening its URL directly
// (new tab, address bar) or embedding it on another site gets a 403. og.jpg stays public on
// purpose: WhatsApp/LinkedIn/X crawlers fetch it to build link previews.
const PROTECTED_IMAGES = new Set(['/assets/portrait.png']);

async function protectedImage(request, env, url) {
  const dest = request.headers.get('Sec-Fetch-Dest');
  const referer = request.headers.get('Referer');
  const openedDirectly = dest === 'document';
  const foreignReferer = referer && new URL(referer).host !== url.host;
  if (openedDirectly || foreignReferer) {
    return new Response('This image is only available on bose.androidplay.in', {
      status: 403,
      headers: { 'Content-Type': 'text/plain; charset=utf-8', 'X-Robots-Tag': 'noindex' },
    });
  }
  const res = await env.ASSETS.fetch(request);
  const out = new Response(res.body, res);
  out.headers.set('X-Robots-Tag', 'noindex, noimageindex');
  out.headers.set('Content-Disposition', 'inline; filename="ankush-bose-portrait.png"');
  out.headers.set('Cache-Control', 'public, max-age=86400');
  return out;
}

async function medium(request, ctx) {
  const cache = caches.default;
  const cacheKey = new Request(new URL('/api/medium', request.url).toString());
  const hit = await cache.match(cacheKey);
  if (hit) return hit;

  let posts;
  try {
    const res = await fetch(FEED_URL, {
      headers: { 'User-Agent': 'bose.androidplay.in feed reader' },
      cf: { cacheTtl: CACHE_SECONDS, cacheEverything: true },
    });
    if (!res.ok) throw new Error(`Medium responded ${res.status}`);
    posts = parseFeed(await res.text()).slice(0, MAX_POSTS);
  } catch (e) {
    // Short negative cache so a Medium outage doesn't hammer the feed; the page keeps its built-in post.
    return json({ posts: [], error: 'feed unavailable' }, 300, 502);
  }

  const response = json({ profile: `https://medium.com/@${MEDIUM_USER}`, posts }, CACHE_SECONDS);
  ctx.waitUntil(cache.put(cacheKey, response.clone()));
  return response;
}

function json(body, maxAge, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'Cache-Control': `public, max-age=${maxAge}`,
    },
  });
}

// The feed is small and regular, so plain pattern matching is enough (Workers have no DOMParser).
function parseFeed(xml) {
  const items = xml.match(/<item>[\s\S]*?<\/item>/g) || [];
  return items.map((item) => {
    const body = field(item, 'content:encoded');
    const text = decode(body.replace(/<figure[\s\S]*?<\/figure>/g, ' ').replace(/<[^>]+>/g, ' '));
    const words = text.split(/\s+/).filter(Boolean).length;
    const link = field(item, 'link').split('?')[0];
    return {
      title: decode(field(item, 'title')),
      url: link,
      published: new Date(field(item, 'pubDate')).toISOString(),
      readMinutes: Math.max(1, Math.round(words / 230)),
      tags: [...item.matchAll(/<category><!\[CDATA\[([^\]]+)\]\]><\/category>/g)].map((m) => m[1]).slice(0, 3),
      image: (body.match(/<img[^>]+src="([^"]+)"/) || [])[1] || null,
    };
  });
}

function field(item, tag) {
  const m = item.match(new RegExp(`<${tag}>(?:<!\\[CDATA\\[)?([\\s\\S]*?)(?:\\]\\]>)?</${tag}>`));
  return m ? m[1].trim() : '';
}

function decode(s) {
  return s
    .replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"').replace(/&#39;|&#x27;/g, "'")
    .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(n))
    .replace(/\s+/g, ' ').trim();
}
