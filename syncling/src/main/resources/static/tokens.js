'use strict';

const listCard = document.getElementById('tk-list-card');
const listBody = document.getElementById('tk-list-body');
const listMeta = document.getElementById('tk-list-meta');
const newBtn   = document.getElementById('tk-new-btn');
const modalMount = document.getElementById('tk-modal-mount');

// ── Load & render ─────────────────────────────────────────────────────────────

async function loadTokens() {
  try {
    const res = await fetch('/api/me/tokens', { headers: window.authHeaders ? window.authHeaders() : {} });
    const data = await res.json();
    render(data.tokens || []);
  } catch {
    listBody.innerHTML = '<div class="tk-empty"><p>Failed to load tokens.</p></div>';
  }
}

const TYPE_META = {
  CLI:     { label: 'CLI',     cls: 'tk-badge-cli',     icon: terminalIcon() },
  ANDROID: { label: 'Android', cls: 'tk-badge-android', icon: androidIcon() },
  IOS:     { label: 'iOS',     cls: 'tk-badge-ios',     icon: appleIcon() },
};

function typeMeta(type) {
  return TYPE_META[type] || TYPE_META.CLI;
}

function platformsOf(t) {
  if (Array.isArray(t.platforms) && t.platforms.length) return t.platforms;
  if (t.type) return [t.type];
  return ['CLI'];
}

// Pick a primary icon for the row: prefer SDK platforms over CLI for visual emphasis.
function primaryPlatform(platforms) {
  return platforms.find(p => p === 'ANDROID')
    || platforms.find(p => p === 'IOS')
    || platforms[0]
    || 'CLI';
}

function render(tokens) {
  if (listMeta) listMeta.textContent = tokens.length ? `${tokens.length} / 10` : '';

  if (!tokens.length) {
    listBody.innerHTML = `
      <div class="tk-empty">
        <div class="tk-empty-icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4"/>
          </svg>
        </div>
        <h3>No tokens or SDK keys yet</h3>
        <p>Create a CLI token, Android SDK key, or iOS SDK key<br>to start integrating with Syncling.</p>
        <button class="bl-btn primary" id="tk-empty-new-btn">+ New key</button>
      </div>`;
    document.getElementById('tk-empty-new-btn')?.addEventListener('click', openCreateModal);
    return;
  }

  listBody.innerHTML = tokens.map(t => {
    const platforms = platformsOf(t);
    const primaryMeta = typeMeta(primaryPlatform(platforms));
    const badges = platforms.map(p => {
      const m = typeMeta(p);
      return `<span class="tk-type-badge ${esc(m.cls)}">${esc(m.label)}</span>`;
    }).join('');
    return `
    <div class="tk-row" data-id="${esc(t.id)}">
      <div class="tk-icon">
        ${primaryMeta.icon}
      </div>
      <div class="tk-row-info">
        <div class="tk-row-name">
          ${esc(t.name)}
          ${badges}
        </div>
        <div class="tk-row-meta">${t.lastUsedAt ? 'Last used ' + fmtDate(t.lastUsedAt) : 'Never used'}</div>
      </div>
      <div class="tk-row-date">Created ${fmtDate(t.createdAt)}</div>
      <div class="tk-row-actions">
        <button class="tk-action-btn revoke" data-id="${esc(t.id)}" data-name="${esc(t.name)}" title="Revoke" aria-label="Revoke ${esc(t.name)}">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
            <path d="M10 11v6"/><path d="M14 11v6"/>
            <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
          </svg>
        </button>
      </div>
    </div>
  `;
  }).join('');

  listBody.querySelectorAll('.tk-action-btn.revoke').forEach(btn => {
    btn.addEventListener('click', () => showRevokeConfirm(btn.dataset.id, btn.dataset.name, btn));
  });
}

// ── Inline revoke confirm ─────────────────────────────────────────────────────

function showRevokeConfirm(id, name, triggerBtn) {
  const row = triggerBtn.closest('.tk-row');
  if (row.nextElementSibling?.classList.contains('tk-revoke-confirm')) return;

  const bar = document.createElement('div');
  bar.className = 'tk-revoke-confirm';
  bar.innerHTML = `
    <span>Revoke <strong>${esc(name)}</strong>? This cannot be undone.</span>
    <div class="tk-revoke-confirm-actions">
      <button class="bl-btn" id="tk-rc-cancel">Cancel</button>
      <button class="bl-btn danger-ghost" id="tk-rc-confirm">Revoke</button>
    </div>`;
  row.insertAdjacentElement('afterend', bar);
  triggerBtn.disabled = true;

  bar.querySelector('#tk-rc-cancel').addEventListener('click', () => {
    bar.remove();
    triggerBtn.disabled = false;
  });
  bar.querySelector('#tk-rc-confirm').addEventListener('click', () => {
    bar.remove();
    revokeToken(id);
  });
}

async function revokeToken(id) {
  const btn = listBody.querySelector(`.tk-action-btn[data-id="${id}"]`);
  if (btn) { btn.disabled = true; }
  try {
    const res = await fetch(`/api/me/tokens/${id}`, {
      method: 'DELETE',
      headers: window.authHeaders ? window.authHeaders() : {}
    });
    if (!res.ok) throw new Error();
    window.toast?.('Token revoked', 'success');
    loadTokens();
  } catch {
    window.toast?.('Failed to revoke token', 'error');
    if (btn) btn.disabled = false;
  }
}

// ── Create modal ──────────────────────────────────────────────────────────────

newBtn.addEventListener('click', openCreateModal);

function openCreateModal() {
  const overlay = document.createElement('div');
  overlay.className = 'tk-modal-overlay';
  overlay.id = 'tk-overlay';
  overlay.innerHTML = `
    <div class="tk-modal" role="dialog" aria-modal="true" aria-labelledby="tk-modal-title">
      <div class="tk-modal-head">
        <h3 id="tk-modal-title">New Token / SDK Key</h3>
        <button class="tk-modal-close" id="tk-modal-close-btn" aria-label="Close">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
      <p class="tk-modal-sub">Pick one or more platforms and give the key a descriptive name. A single key can authenticate the CLI plus your Android &amp; iOS SDKs.</p>

      <div class="tk-form-row">
        <label>Platforms</label>
        <div class="tk-type-selector" id="tk-type-selector" role="group" aria-label="Platforms">
          <button type="button" class="tk-type-opt active" data-type="CLI" aria-pressed="true">
            ${checkIcon()} ${terminalIcon(14)} CLI
          </button>
          <button type="button" class="tk-type-opt" data-type="ANDROID" aria-pressed="false">
            ${checkIcon()} ${androidIcon(14)} Android SDK
          </button>
          <button type="button" class="tk-type-opt" data-type="IOS" aria-pressed="false">
            ${checkIcon()} ${appleIcon(14)} iOS SDK
          </button>
        </div>
        <div class="tk-form-hint">Select multiple to issue one key that works across surfaces.</div>
      </div>

      <div class="tk-form-row">
        <label for="tk-name-input">Name</label>
        <input type="text" id="tk-name-input" placeholder="e.g. Production Android, CI pipeline" maxlength="64" autocomplete="off">
        <div class="tk-form-hint">Max 64 characters. Up to 10 tokens per account.</div>
      </div>

      <div id="tk-reveal-area"></div>
      <div class="tk-modal-actions">
        <button class="bl-btn" id="tk-cancel-btn">Cancel</button>
        <button class="bl-btn primary" id="tk-create-btn">Create</button>
      </div>
    </div>`;

  modalMount.appendChild(overlay);
  requestAnimationFrame(() => overlay.classList.add('show'));

  overlay.addEventListener('click', e => { if (e.target === overlay) closeModal(); });
  overlay.querySelector('#tk-modal-close-btn').addEventListener('click', closeModal);
  overlay.querySelector('#tk-cancel-btn').addEventListener('click', closeModal);
  overlay.querySelector('#tk-create-btn').addEventListener('click', createToken);
  overlay.querySelector('#tk-name-input').addEventListener('keydown', e => {
    if (e.key === 'Enter') createToken();
  });

  overlay.querySelectorAll('.tk-type-opt').forEach(btn => {
    btn.addEventListener('click', () => {
      const opts = overlay.querySelectorAll('.tk-type-opt');
      const isActive = btn.classList.contains('active');
      const activeCount = Array.from(opts).filter(b => b.classList.contains('active')).length;
      // Prevent deselecting the last remaining platform.
      if (isActive && activeCount === 1) return;
      btn.classList.toggle('active');
      btn.setAttribute('aria-pressed', btn.classList.contains('active') ? 'true' : 'false');
    });
  });

  overlay.querySelector('#tk-name-input').focus();
  document.addEventListener('keydown', onEsc);
}

function onEsc(e) {
  if (e.key === 'Escape') closeModal();
}

async function createToken() {
  const input = document.getElementById('tk-name-input');
  const name = input?.value.trim();
  if (!name) { input?.focus(); input?.setAttribute('aria-invalid', 'true'); return; }
  input?.removeAttribute('aria-invalid');

  const platforms = Array.from(document.querySelectorAll('.tk-type-opt.active'))
    .map(b => b.dataset.type);
  if (!platforms.length) platforms.push('CLI');

  const createBtn = document.getElementById('tk-create-btn');
  const cancelBtn = document.getElementById('tk-cancel-btn');
  createBtn.disabled = true;
  createBtn.innerHTML = '<span class="bl-spin"></span>Creating…';

  try {
    const res = await fetch('/api/me/tokens', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(window.authHeaders ? window.authHeaders() : {}) },
      body: JSON.stringify({ name, platforms })
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Failed');

    const granted = (data.platforms && data.platforms.length) ? data.platforms : platforms;
    const niceNames = { CLI: 'CLI', ANDROID: 'Android SDK', IOS: 'iOS SDK' };
    const typeLabel = granted.length === 1
      ? `${niceNames[granted[0]] || 'key'} token`
      : `key for ${granted.map(p => niceNames[p] || p).join(' + ')}`;

    document.getElementById('tk-reveal-area').innerHTML = `
      <div class="tk-reveal">
        <div class="tk-reveal-label">Your new ${esc(typeLabel)}</div>
        <div class="tk-reveal-value">
          <span class="tk-reveal-text" id="tk-reveal-text">${esc(data.token)}</span>
          <button class="bl-btn tk-copy-btn" id="tk-copy-btn">Copy</button>
        </div>
        <div class="tk-reveal-note">Copy this key now — it won't be shown again.</div>
      </div>`;

    document.getElementById('tk-copy-btn').addEventListener('click', () => {
      navigator.clipboard.writeText(data.token).then(() => {
        const btn = document.getElementById('tk-copy-btn');
        btn.textContent = 'Copied!';
        setTimeout(() => { btn.textContent = 'Copy'; }, 2000);
      });
    });

    createBtn.style.display = 'none';
    cancelBtn.textContent = 'Done';
    if (input) { input.disabled = true; }
    document.querySelectorAll('.tk-type-opt').forEach(b => b.disabled = true);
    loadTokens();
  } catch (err) {
    window.toast?.(err.message || 'Failed to create token', 'error');
    createBtn.disabled = false;
    createBtn.textContent = 'Create';
  }
}

function closeModal() {
  document.removeEventListener('keydown', onEsc);
  const overlay = document.getElementById('tk-overlay');
  if (!overlay) return;
  overlay.classList.remove('show');
  overlay.addEventListener('transitionend', () => overlay.remove(), { once: true });
}

// ── Icon helpers ──────────────────────────────────────────────────────────────

function terminalIcon(size = 16) {
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="4 17 10 11 4 5"/><line x1="12" y1="19" x2="20" y2="19"/></svg>`;
}

// Linear outline of the actual Android (bugdroid) brand logo: domed head, two
// antennae, two eyes. Kept stroke-only so it inherits currentColor cleanly.
function androidIcon(size = 16) {
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4.5 15.5V12a7.5 7.5 0 0 1 15 0v3.5z"/><line x1="7.5" y1="6.5" x2="5.5" y2="3.5"/><line x1="16.5" y1="6.5" x2="18.5" y2="3.5"/><circle cx="9" cy="11" r="0.9" fill="currentColor" stroke="none"/><circle cx="15" cy="11" r="0.9" fill="currentColor" stroke="none"/></svg>`;
}

// Linear outline of the actual Apple brand logo: bitten apple silhouette with leaf.
function appleIcon(size = 16) {
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M16.7 12.6c0-2.4 2-3.6 2.1-3.6-1.1-1.7-2.9-1.9-3.5-1.9-1.5-.2-2.9 .9-3.7 .9-.8 0-1.9-.9-3.1-.9-1.6 0-3.1 .9-3.9 2.4-1.7 2.9-.4 7.2 1.2 9.6 .8 1.2 1.8 2.5 3 2.4 1.2 0 1.7-.8 3.1-.8 1.5 0 1.9 .8 3.1 .8 1.3 0 2.1-1.2 2.9-2.4 .9-1.4 1.3-2.7 1.3-2.8-.1 0-2.5-1-2.5-3.7z"/><path d="M14.2 5.4c.6-.8 1.1-1.9 1-3-1 0-2.1 .6-2.8 1.4-.6 .7-1.1 1.8-.9 2.8 1.1 .1 2.2-.5 2.7-1.2z"/></svg>`;
}

function checkIcon(size = 12) {
  return `<svg class="tk-type-check" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="5 12.5 10 17.5 19 7"/></svg>`;
}

// ── Utils ─────────────────────────────────────────────────────────────────────

function esc(s) {
  return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}

function fmtDate(ms) {
  return new Date(ms).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

loadTokens();
