/**
 * Invite landing page client.
 *
 * Flow:
 *   1. Read the token from the URL path (/invite/{token}).
 *   2. Fetch GET /api/invites/{token} — public, returns project + role preview.
 *   3a. If logged in (token in localStorage), show "Accept invite" — POSTs accept
 *       and redirects to the project's members page on success.
 *   3b. Otherwise, save the invite token under pending_invite_token and offer
 *       "Continue with GitHub" — after OAuth, the dashboard's first paint can
 *       look at that key and route the user back here to accept.
 */
(function () {
    const card = document.getElementById('inv-card');
    if (!card) return;

    const token = location.pathname.split('/').pop();
    if (!token) {
        renderError("Missing invite token.");
        return;
    }

    loadPreview();

    async function loadPreview() {
        try {
            // No auth header needed — endpoint is public.
            const r = await fetch(`/api/invites/${encodeURIComponent(token)}`);
            if (r.status === 404) {
                renderBad('Invite not found', "This link is no longer valid. It may have already been used or revoked. Ask the person who invited you to send a fresh link.");
                return;
            }
            if (!r.ok) throw new Error('status ' + r.status);
            const data = await r.json();
            if (data.expired) {
                renderBad('Invite already used', "This invite has already been accepted or revoked. If you need access again, ask an admin to re-invite you.");
                return;
            }
            renderPreview(data);
        } catch (err) {
            console.warn('invite preview failed', err);
            renderError("We couldn't load this invite. Refresh to try again.");
        }
    }

    function renderPreview(data) {
        const loggedIn = !!window.tlToken();
        const inviter = data.invitedBy ? `@${data.invitedBy}` : 'Someone';
        card.innerHTML = `
            <span class="inv-eyebrow">You're invited</span>
            <h1 class="inv-title">${esc(inviter)} invited you to ${esc(data.projectName)}</h1>
            <p class="inv-sub">Accept to join as a <b>${roleLabel(data.role).toLowerCase()}</b>. You can change your role later if the project admins approve.</p>
            <dl class="inv-meta">
                <dt>Project</dt><dd>${esc(data.projectName)}</dd>
                <dt>Role</dt><dd><span class="inv-role-pill">${esc(roleLabel(data.role))}</span></dd>
                <dt>Email</dt><dd>${esc(data.email)}</dd>
            </dl>
            ${loggedIn
                ? `<button type="button" class="inv-btn" id="inv-accept">Accept invite</button>
                   <button type="button" class="inv-btn inv-btn-secondary" id="inv-logout">Sign in with a different account</button>`
                : `<button type="button" class="inv-btn" id="inv-login">Continue with GitHub</button>
                   <p class="inv-foot" style="margin-top:14px">We use GitHub to verify it's really you — no separate password.</p>`
            }
        `;

        if (loggedIn) {
            document.getElementById('inv-accept').addEventListener('click', accept);
            document.getElementById('inv-logout').addEventListener('click', () => {
                localStorage.setItem('pending_invite_token', token);
                localStorage.removeItem('syncling_token');
                window.location.href = '/auth/github';
            });
        } else {
            document.getElementById('inv-login').addEventListener('click', () => {
                // Stash the token so the dashboard can resume the flow after OAuth.
                localStorage.setItem('pending_invite_token', token);
                window.location.href = '/auth/github';
            });
        }
    }

    async function accept(e) {
        const btn = e.currentTarget;
        setBusy(btn, true, 'Joining');
        try {
            const r = await tlFetch(`/api/invites/${encodeURIComponent(token)}/accept`, { method: 'POST' });
            if (r.status === 401) {
                // Session expired between the preview and the accept — route through OAuth.
                localStorage.setItem('pending_invite_token', token);
                window.location.href = '/auth/github';
                return;
            }
            const j = await r.json().catch(() => ({}));
            if (!r.ok) throw new Error(j.error || 'status ' + r.status);
            localStorage.removeItem('pending_invite_token');
            toast('Welcome to the project!', 'success');
            setTimeout(() => { window.location.href = `/members/${j.projectId}`; }, 600);
        } catch (err) {
            toast(err.message || 'Could not accept invite', 'error');
            setBusy(btn, false);
        }
    }

    function renderBad(title, msg) {
        card.classList.add('inv-state-bad');
        card.innerHTML = `
            <span class="inv-eyebrow">Invite</span>
            <h1 class="inv-title">${esc(title)}</h1>
            <p class="inv-sub">${esc(msg)}</p>
            <a href="/syncling" class="inv-btn inv-btn-secondary" style="margin-top:0">Back to Syncling</a>
        `;
    }

    function renderError(msg) {
        card.innerHTML = `<p class="inv-error">${esc(msg)}</p>`;
    }

    function esc(s) {
        const d = document.createElement('div');
        d.textContent = String(s == null ? '' : s);
        return d.innerHTML;
    }

    function roleLabel(r) {
        return ({ OWNER: 'Owner', ADMIN: 'Admin', TRANSLATOR: 'Translator', VIEWER: 'Viewer' })[r] || r;
    }

    function setBusy(btn, busy, label) {
        if (!btn) return;
        if (busy) {
            btn.dataset.originalText = btn.textContent;
            btn.disabled = true;
            btn.innerHTML = `<span class="bl-spin"></span> ${esc(label || 'Working')}`;
        } else {
            btn.disabled = false;
            btn.textContent = btn.dataset.originalText || 'Accept';
        }
    }
})();
