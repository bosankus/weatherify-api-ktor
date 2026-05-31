'use strict';

const listCard = document.getElementById('tk-list-card');
const listBody = document.getElementById('tk-list-body');
const listMeta = document.getElementById('tk-list-meta');
const newBtn   = document.getElementById('tk-new-btn');
const modalMount = document.getElementById('tk-modal-mount');

// ── Load & render ─────────────────────────────────────────────────────────────

async function loadTokens() {
  try {
    const res = await fetch('/syncling/api/me/tokens', { headers: window.authHeaders || {} });
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
    const meta = typeMeta(t.type || 'CLI');
    return `
    <div class="tk-row" data-id="${esc(t.id)}">
      <div class="tk-icon">
        ${meta.icon}
      </div>
      <div class="tk-row-info">
        <div class="tk-row-name">
          ${esc(t.name)}
          <span class="tk-type-badge ${esc(meta.cls)}">${esc(meta.label)}</span>
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
    const res = await fetch(`/syncling/api/me/tokens/${id}`, {
      method: 'DELETE',
      headers: window.authHeaders || {}
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
      <p class="tk-modal-sub">Choose a type and give it a descriptive name — you'll see this in the list.</p>

      <div class="tk-form-row">
        <label>Type</label>
        <div class="tk-type-selector" id="tk-type-selector">
          <button type="button" class="tk-type-opt active" data-type="CLI">
            ${terminalIcon(14)} CLI
          </button>
          <button type="button" class="tk-type-opt" data-type="ANDROID">
            ${androidIcon(14)} Android SDK
          </button>
          <button type="button" class="tk-type-opt" data-type="IOS">
            ${appleIcon(14)} iOS SDK
          </button>
        </div>
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
      overlay.querySelectorAll('.tk-type-opt').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
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

  const activeType = document.querySelector('.tk-type-opt.active');
  const type = activeType?.dataset.type || 'CLI';

  const createBtn = document.getElementById('tk-create-btn');
  const cancelBtn = document.getElementById('tk-cancel-btn');
  createBtn.disabled = true;
  createBtn.innerHTML = '<span class="bl-spin"></span>Creating…';

  try {
    const res = await fetch('/syncling/api/me/tokens', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(window.authHeaders || {}) },
      body: JSON.stringify({ name, type })
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Failed');

    const typeLabel = { CLI: 'CLI token', ANDROID: 'Android SDK key', IOS: 'iOS SDK key' }[type] || 'key';

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

function androidIcon(size = 16) {
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 16V8a7 7 0 0 1 14 0v8"/><rect x="3" y="16" width="18" height="5" rx="2"/><line x1="8" y1="4" x2="6" y2="2"/><line x1="16" y1="4" x2="18" y2="2"/></svg>`;
}

function appleIcon(size = 16) {
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2C9.5 2 8 4 8 4S5 4.5 4 7c-1.5 3.5 0 8 2 10 1 1.5 2 2 3 2s1.5-.5 3-.5 2 .5 3 .5 2-.5 3-2c1-1.5 2-4 2-6 0-3-2-5-5-5-1.5 0-2.5 1-3 1s-1.5-1-3-1z"/><path d="M12 2c0-1 1-2 2-2"/></svg>`;
}

// ── Utils ─────────────────────────────────────────────────────────────────────

function esc(s) {
  return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}

function fmtDate(ms) {
  return new Date(ms).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

loadTokens();
