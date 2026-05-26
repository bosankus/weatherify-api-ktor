/**
 * Members page client.
 *
 * Owns three things:
 *   - Project picker (#mb-project-select)         — fetches /transloom/api/projects
 *   - Member list (#mb-list)                       — per-project list + role/revoke controls
 *   - Invite modal (#mb-modal-mount)               — built lazily on first open
 *
 * State is module-level (no framework). All mutations re-render the list by
 * recomputing innerHTML — same pattern projects.js uses. Permissions are
 * inferred from the caller's own row: if my role < ADMIN, role-selects and
 * remove buttons are disabled (except my own row for self-leave).
 */
(function () {
    if (!window.tlFetch) {
        console.error('members.js: portal shell not initialised (no window.tlFetch)');
        return;
    }

    const PROJECTS_API = '/transloom/api/projects';
    const ROLES = ['ADMIN', 'TRANSLATOR', 'VIEWER'];   // OWNER is implicit, set on create
    const ROLE_LABELS = { OWNER: 'Owner', ADMIN: 'Admin', TRANSLATOR: 'Translator', VIEWER: 'Viewer' };

    // ── Tiny helpers ─────────────────────────────────────────────────────────
    const $ = id => document.getElementById(id);
    const esc = s => {
        const d = document.createElement('div');
        d.textContent = String(s == null ? '' : s);
        return d.innerHTML;
    };
    const initial = s => (s || '?').trim().charAt(0).toUpperCase();
    const myUserId = (() => {
        const t = localStorage.getItem('transloom_token');
        if (!t) return null;
        try { return JSON.parse(atob(t.split('.')[1])).userId || null; } catch (_) { return null; }
    })();

    // ── State ────────────────────────────────────────────────────────────────
    let projects = [];
    let currentProjectId = null;
    let members = [];
    let myRole = null;           // resolved from the list once it loads

    // ── Boot ─────────────────────────────────────────────────────────────────
    document.addEventListener('DOMContentLoaded', init);

    async function init() {
        const initial = $('mb-bootstrap')?.dataset.projectId || '';
        await loadProjects(initial);
        wireHeader();
    }

    async function loadProjects(preferredId) {
        try {
            const r = await tlFetch(PROJECTS_API);
            if (!r.ok) throw new Error('status ' + r.status);
            const j = await r.json();
            projects = j.projects || [];
        } catch (err) {
            console.warn('members: project list failed', err);
            renderError('Could not load your projects. Refresh to try again.');
            return;
        }

        const select = $('mb-project-select');
        if (!projects.length) {
            // Server already renders an empty-state page for /transloom/members when
            // there are no projects. If we somehow land here, swap in a soft empty state.
            renderEmpty('Create a project before inviting teammates.');
            select.style.display = 'none';
            return;
        }

        select.innerHTML = projects
            .map(p => `<option value="${esc(p.id)}">${esc(p.name)}</option>`)
            .join('');
        // Hide picker entirely when there's only one project.
        select.style.display = projects.length === 1 ? 'none' : '';

        const pick = preferredId && projects.find(p => p.id === preferredId)
            ? preferredId
            : projects[0].id;
        select.value = pick;
        await switchProject(pick);
    }

    function wireHeader() {
        $('mb-project-select').addEventListener('change', e => {
            const id = e.target.value;
            history.replaceState(null, '', `/transloom/members/${id}`);
            switchProject(id);
        });
        $('mb-invite-btn').addEventListener('click', () => {
            if ($('mb-invite-btn').disabled) return;
            openInviteModal();
        });
    }

    async function switchProject(projectId) {
        currentProjectId = projectId;
        await loadMembers();
    }

    async function loadMembers() {
        if (!currentProjectId) return;
        try {
            const r = await tlFetch(`${PROJECTS_API}/${currentProjectId}/members`);
            if (!r.ok) throw new Error('status ' + r.status);
            const j = await r.json();
            members = j.members || [];
            myRole = (myUserId && (members.find(m => m.userId === myUserId)?.role)) || null;
            renderList();
            $('mb-invite-btn').disabled = !canManage();
        } catch (err) {
            console.warn('members: list load failed', err);
            renderError('Could not load members. Refresh to try again.');
        }
    }

    function canManage() {
        return myRole === 'OWNER' || myRole === 'ADMIN';
    }

    function renderList() {
        const host = $('mb-list');
        if (!host) return;
        if (!members.length) {
            host.outerHTML = '<div class="mb-empty" id="mb-list"><h3>No members yet</h3><p>Send an invite to start collaborating.</p></div>';
            return;
        }
        // Sort: ACTIVE first (OWNER, ADMIN, TRANSLATOR, VIEWER), then INVITED, then REVOKED.
        const roleOrder = { OWNER: 0, ADMIN: 1, TRANSLATOR: 2, VIEWER: 3 };
        const statusOrder = { ACTIVE: 0, INVITED: 1, REVOKED: 2 };
        const sorted = members.slice().sort((a, b) => {
            const s = (statusOrder[a.status] ?? 9) - (statusOrder[b.status] ?? 9);
            if (s !== 0) return s;
            return (roleOrder[a.role] ?? 9) - (roleOrder[b.role] ?? 9);
        });
        // Restore mb-list element if previous render swapped it for empty state.
        host.outerHTML = `<div class="mb-list" id="mb-list">${sorted.map(renderRow).join('')}</div>`;
        wireRowEvents();
    }

    function renderRow(m) {
        const isMe = myUserId && m.userId === myUserId;
        const isOwner = m.role === 'OWNER';
        const manage = canManage();
        const displayName = m.displayName ? `@${m.displayName}` : (m.email || 'Pending invite');
        const statusClass = m.status.toLowerCase();
        // OWNER can't be edited or removed from this UI — transfer-ownership lives elsewhere.
        // Non-managers see disabled controls. Self-leave is allowed for any non-OWNER own row.
        const roleEditable = manage && !isOwner;
        const removable = (manage && !isOwner) || (isMe && !isOwner);
        return `
            <div class="mb-row" data-membership-id="${esc(m.id)}" data-role="${esc(m.role)}" data-status="${esc(m.status)}">
                <div class="mb-identity">
                    <div class="mb-avatar">${esc(initial(displayName.replace(/^@/, '') || m.email))}</div>
                    <div class="mb-identity-text">
                        <div class="mb-name">${esc(displayName)}${isMe ? ' <span style="color:var(--text-muted);font-weight:400">(you)</span>' : ''}</div>
                        <div class="mb-email">${esc(m.email)}</div>
                    </div>
                </div>
                <div class="mb-role-cell">
                    <select class="mb-role-select" data-act="role" ${roleEditable ? '' : 'disabled'}>
                        ${isOwner ? `<option value="OWNER" selected>Owner</option>` : ''}
                        ${ROLES.map(r => `<option value="${r}" ${r === m.role ? 'selected' : ''}>${esc(ROLE_LABELS[r])}</option>`).join('')}
                    </select>
                </div>
                <span class="mb-status ${statusClass}">${esc(m.status)}</span>
                <button class="mb-remove-btn" data-act="remove" title="${isMe ? 'Leave project' : 'Remove member'}" ${removable ? '' : 'disabled'}>
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-2 14a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2L5 6"/></svg>
                </button>
            </div>`;
    }

    function wireRowEvents() {
        $('mb-list').querySelectorAll('.mb-row').forEach(row => {
            const id = row.dataset.membershipId;
            row.querySelector('[data-act="role"]')?.addEventListener('change', e => updateRole(id, e.target.value, row));
            row.querySelector('[data-act="remove"]')?.addEventListener('click', () => removeMember(id, row));
        });
    }

    async function updateRole(membershipId, newRole, row) {
        const prev = row.dataset.role;
        if (newRole === prev) return;
        try {
            const r = await tlFetch(`${PROJECTS_API}/${currentProjectId}/members/${membershipId}`, {
                method: 'PATCH',
                body: JSON.stringify({ role: newRole }),
            });
            if (!r.ok) {
                const j = await r.json().catch(() => ({}));
                throw new Error(j.error || 'status ' + r.status);
            }
            row.dataset.role = newRole;
            toast(`Role updated to ${ROLE_LABELS[newRole] || newRole}`, 'success');
            // Refetch so a self-demotion immediately reflects in disabled controls.
            await loadMembers();
        } catch (err) {
            toast(err.message || 'Could not update role', 'error');
            row.querySelector('[data-act="role"]').value = prev;
        }
    }

    async function removeMember(membershipId, row) {
        const isMe = row.querySelector('.mb-name')?.textContent.includes('(you)');
        const verb = isMe ? 'leave this project' : 'remove this member';
        if (!confirm(`Are you sure you want to ${verb}?`)) return;
        try {
            const r = await tlFetch(`${PROJECTS_API}/${currentProjectId}/members/${membershipId}`, { method: 'DELETE' });
            if (!r.ok && r.status !== 204) {
                const j = await r.json().catch(() => ({}));
                throw new Error(j.error || 'status ' + r.status);
            }
            toast(isMe ? 'You left the project' : 'Member removed', 'success');
            if (isMe) {
                window.location.href = '/transloom/projects';
                return;
            }
            await loadMembers();
        } catch (err) {
            toast(err.message || 'Could not remove member', 'error');
        }
    }

    // ── Invite modal ─────────────────────────────────────────────────────────
    function openInviteModal() {
        const mount = $('mb-modal-mount');
        if (!mount || mount.firstElementChild) return;
        mount.innerHTML = inviteModalHtml();
        const overlay = mount.firstElementChild;
        requestAnimationFrame(() => overlay.classList.add('show'));

        overlay.addEventListener('click', e => {
            if (e.target === overlay || e.target.closest('[data-modal-act="close"]')) return closeInviteModal();
            const submit = e.target.closest('[data-modal-act="submit"]');
            if (submit) submitInvite(overlay, submit);
        });
        document.addEventListener('keydown', escInvite);
        overlay.querySelector('#mb-invite-email').focus();
    }

    function escInvite(e) { if (e.key === 'Escape') closeInviteModal(); }
    function closeInviteModal() {
        const mount = $('mb-modal-mount');
        if (mount) mount.innerHTML = '';
        document.removeEventListener('keydown', escInvite);
    }

    function inviteModalHtml() {
        return `
        <div class="mb-modal-overlay" role="dialog" aria-modal="true" aria-labelledby="mb-invite-title">
          <div class="mb-modal">
            <div class="mb-modal-head">
              <h3 id="mb-invite-title">Invite a teammate</h3>
              <button type="button" class="mb-modal-close" data-modal-act="close" aria-label="Close">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              </button>
            </div>
            <p class="mb-modal-sub">They'll get an email with a single-use link. The invite stays active here even if the email fails.</p>

            <div class="mb-form-row">
              <label for="mb-invite-email">Email address</label>
              <input type="email" id="mb-invite-email" placeholder="teammate@company.com" autocomplete="off">
            </div>

            <div class="mb-form-row">
              <label for="mb-invite-role">Role</label>
              <select id="mb-invite-role">
                <option value="TRANSLATOR" selected>Translator</option>
                <option value="ADMIN">Admin</option>
                <option value="VIEWER">Viewer</option>
              </select>
              <div class="mb-role-help">
                <b>Translator</b> — approves, rejects, hotfixes and triggers sync.<br>
                <b>Admin</b> — everything translator can do, plus settings, glossary, members.<br>
                <b>Viewer</b> — read-only.
              </div>
            </div>

            <div class="mb-modal-actions">
              <button type="button" class="bl-btn" data-modal-act="close">Cancel</button>
              <button type="button" class="bl-btn primary" data-modal-act="submit">Send invite</button>
            </div>
          </div>
        </div>`;
    }

    async function submitInvite(overlay, btn) {
        const email = overlay.querySelector('#mb-invite-email').value.trim();
        const role = overlay.querySelector('#mb-invite-role').value;
        const emailInput = overlay.querySelector('#mb-invite-email');
        emailInput.removeAttribute('aria-invalid');
        if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
            emailInput.setAttribute('aria-invalid', 'true');
            toast('Enter a valid email', 'error');
            return;
        }

        setBusy(btn, true, 'Sending');
        try {
            const r = await tlFetch(`${PROJECTS_API}/${currentProjectId}/members`, {
                method: 'POST',
                body: JSON.stringify({ email, role }),
            });
            if (!r.ok) {
                const j = await r.json().catch(() => ({}));
                throw new Error(j.error || 'status ' + r.status);
            }
            toast('Invite sent', 'success');
            closeInviteModal();
            await loadMembers();
        } catch (err) {
            toast(err.message || 'Could not send invite', 'error');
        } finally {
            setBusy(btn, false);
        }
    }

    // ── Generic helpers ──────────────────────────────────────────────────────
    function setBusy(btn, busy, label) {
        if (!btn) return;
        if (busy) {
            btn.dataset.originalText = btn.textContent;
            btn.disabled = true;
            btn.innerHTML = `<span class="bl-spin"></span> ${esc(label || 'Working')}`;
        } else {
            btn.disabled = false;
            btn.textContent = btn.dataset.originalText || 'Submit';
        }
    }

    function renderError(msg) {
        const host = $('mb-list');
        if (host) host.outerHTML = `<div class="mb-empty" id="mb-list"><h3>Something went wrong</h3><p>${esc(msg)}</p></div>`;
    }
    function renderEmpty(msg) {
        const host = $('mb-list');
        if (host) host.outerHTML = `<div class="mb-empty" id="mb-list"><h3>No projects</h3><p>${esc(msg)}</p><a href="/transloom/projects" class="bl-btn primary">Go to Projects</a></div>`;
    }
})();
