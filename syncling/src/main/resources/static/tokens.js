'use strict';
// API Tokens page

const list = document.getElementById('tk-list');
const newBtn = document.getElementById('tk-new-btn');
const modalMount = document.getElementById('tk-modal-mount');

async function loadTokens() {
  try {
    const res = await fetch('/syncling/api/me/tokens', { headers: window.authHeaders || {} });
    const data = await res.json();
    render(data.tokens || []);
  } catch {
    list.innerHTML = '<p class="tk-empty">Failed to load tokens.</p>';
  }
}

function render(tokens) {
  if (!tokens.length) {
    list.innerHTML = '<p class="tk-empty">No API tokens yet. Create one to use the Syncling CLI.</p>';
    return;
  }
  list.innerHTML = tokens.map(t => `
    <div class="tk-row" data-id="${esc(t.id)}">
      <div class="tk-row-info">
        <div class="tk-row-name">${esc(t.name)}</div>
        <div class="tk-row-meta">Created ${fmtDate(t.createdAt)}${t.lastUsedAt ? ' · Last used ' + fmtDate(t.lastUsedAt) : ' · Never used'}</div>
      </div>
      <div class="tk-row-actions">
        <button class="bl-btn danger-outline tk-revoke" data-id="${esc(t.id)}" data-name="${esc(t.name)}">Revoke</button>
      </div>
    </div>
  `).join('');

  list.querySelectorAll('.tk-revoke').forEach(btn => {
    btn.addEventListener('click', () => confirmRevoke(btn.dataset.id, btn.dataset.name));
  });
}

function confirmRevoke(id, name) {
  if (!confirm(`Revoke token "${name}"?\n\nAny CLI sessions using this token will stop working immediately.`)) return;
  revokeToken(id);
}

async function revokeToken(id) {
  const btn = list.querySelector(`.tk-revoke[data-id="${id}"]`);
  if (btn) { btn.disabled = true; btn.textContent = 'Revoking…'; }
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
    if (btn) { btn.disabled = false; btn.textContent = 'Revoke'; }
  }
}

// ── Create modal ──────────────────────────────────────────────────────────────

newBtn.addEventListener('click', openCreateModal);

function openCreateModal() {
  modalMount.innerHTML = `
    <div class="tk-modal-overlay" id="tk-overlay">
      <div class="tk-modal">
        <h2>Create API Token</h2>
        <label class="form-label">Token name
          <input type="text" id="tk-name-input" class="form-input" placeholder="e.g. CI pipeline, Local dev" maxlength="64" style="margin-top:6px">
        </label>
        <div id="tk-reveal-area"></div>
        <div class="tk-modal-actions">
          <button class="bl-btn secondary" id="tk-cancel-btn">Cancel</button>
          <button class="bl-btn primary" id="tk-create-btn">Create token</button>
        </div>
      </div>
    </div>`;

  document.getElementById('tk-overlay').addEventListener('click', e => {
    if (e.target === document.getElementById('tk-overlay')) closeModal();
  });
  document.getElementById('tk-cancel-btn').addEventListener('click', closeModal);
  document.getElementById('tk-create-btn').addEventListener('click', createToken);
  document.getElementById('tk-name-input').focus();
}

async function createToken() {
  const name = document.getElementById('tk-name-input').value.trim();
  if (!name) { document.getElementById('tk-name-input').focus(); return; }

  const createBtn = document.getElementById('tk-create-btn');
  createBtn.disabled = true;
  createBtn.textContent = 'Creating…';

  try {
    const res = await fetch('/syncling/api/me/tokens', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(window.authHeaders || {}) },
      body: JSON.stringify({ name })
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Failed');

    // Show the token once — it will never be shown again
    document.getElementById('tk-reveal-area').innerHTML = `
      <div class="tk-reveal">
        <p>Copy this token now — it won't be shown again.</p>
        <div class="tk-token-value">
          <span class="tk-token-text" id="tk-token-text">${esc(data.token)}</span>
          <button class="bl-btn secondary tk-copy-btn" id="tk-copy-btn">Copy</button>
        </div>
      </div>`;

    document.getElementById('tk-copy-btn').addEventListener('click', () => {
      navigator.clipboard.writeText(data.token).then(() => {
        document.getElementById('tk-copy-btn').textContent = 'Copied!';
        setTimeout(() => { document.getElementById('tk-copy-btn').textContent = 'Copy'; }, 2000);
      });
    });

    document.getElementById('tk-cancel-btn').textContent = 'Done';
    createBtn.style.display = 'none';
    document.getElementById('tk-name-input').disabled = true;

    loadTokens();
  } catch (err) {
    window.toast?.(err.message || 'Failed to create token', 'error');
    createBtn.disabled = false;
    createBtn.textContent = 'Create token';
  }
}

function closeModal() { modalMount.innerHTML = ''; }

// ── Utils ─────────────────────────────────────────────────────────────────────

function esc(s) {
  return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}

function fmtDate(ms) {
  return new Date(ms).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

loadTokens();
