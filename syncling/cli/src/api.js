import { API_BASE } from './config.js';

export async function apiFetch(path, token, options = {}) {
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
      ...(options.headers ?? {}),
    },
  });

  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try {
      const body = await res.json();
      if (body.message) msg = body.message;
    } catch {}
    throw new Error(msg);
  }

  const ct = res.headers.get('content-type') ?? '';
  if (ct.includes('application/json')) return res.json();
  return res; // caller handles binary/text responses
}
