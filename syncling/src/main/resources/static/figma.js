/**
 * Figma inbox client.
 *
 * Owns:
 *   - Project picker (#fg-project-select)   — /api/projects
 *   - Status tabs (#fg-tabs)                — PENDING / PR_OPEN / REJECTED
 *   - Candidate list (#fg-list)             — /api/figma/projects/{id}/candidates
 *   - Bulk actions (#fg-actions)            — approve → PR, reject
 *
 * Thumbnails are frame screenshots served behind auth, so they're blob-fetched
 * through tlFetch and attached as object URLs (an <img src> can't carry the
 * bearer token). One fetch per unique frame, cached for the page lifetime.
 */
(function () {
    if (!window.tlFetch) {
        console.error('figma.js: portal shell not initialised (no window.tlFetch)');
        return;
    }

    const $ = id => document.getElementById(id);
    const esc = s => {
        const d = document.createElement('div');
        d.textContent = String(s == null ? '' : s);
        return d.innerHTML;
    };
    const KEY_RE = /^[a-z][a-z0-9_]{0,79}$/;

    // ── State ────────────────────────────────────────────────────────────────
    let projects = [];
    let currentProjectId = null;
    let currentStatus = 'PENDING';
    let candidates = [];
    let conflicts = {};                 // effectiveKey → reason, from a 409 approve
    const previewUrls = new Map();      // "fileKey|frameId" → object URL (or 'none')

    // ── Boot ─────────────────────────────────────────────────────────────────
    document.addEventListener('DOMContentLoaded', init);

    async function init() {
        const bootId = $('fg-bootstrap')?.dataset.projectId || '';
        try {
            const res = await tlFetch('/api/projects');
            if (!res.ok) throw new Error('projects ' + res.status);
            projects = (await res.json()).projects || [];
        } catch (_) {
            renderError('Could not load your projects. Refresh to retry.');
            return;
        }
        if (!projects.length) {
            renderNoProjects();
            return;
        }
        currentProjectId = projects.some(p => p.id === bootId) ? bootId : projects[0].id;
        fillProjectSelect();
        fillTargetFiles();
        bindToolbar();
        await loadCandidates();
        if (window._tlOnWake) window._tlOnWake(() => loadCandidates());
    }

    function fillProjectSelect() {
        const sel = $('fg-project-select');
        sel.innerHTML = projects.map(p =>
            `<option value="${esc(p.id)}"${p.id === currentProjectId ? ' selected' : ''}>${esc(p.name)}</option>`).join('');
        if (projects.length < 2) sel.setAttribute('data-single', '1');
        sel.onchange = () => {
            currentProjectId = sel.value;
            conflicts = {};
            history.replaceState(null, '', '/figma/' + currentProjectId);
            fillTargetFiles();
            loadCandidates();
        };
    }

    function currentProject() { return projects.find(p => p.id === currentProjectId); }

    function fillTargetFiles() {
        const sel = $('fg-target-file');
        const files = (currentProject() || {}).sourceFilePaths || [];
        sel.innerHTML = files.map(f => `<option value="${esc(f)}">${esc(f)}</option>`).join('');
        sel.classList.toggle('visible', files.length > 1);
    }

    function bindToolbar() {
        document.querySelectorAll('.fg-tab').forEach(tab => {
            tab.onclick = () => {
                document.querySelectorAll('.fg-tab').forEach(t => t.classList.toggle('active', t === tab));
                currentStatus = tab.dataset.status;
                conflicts = {};
                loadCandidates();
            };
        });
        $('fg-approve-btn').onclick = approveSelected;
        $('fg-reject-btn').onclick = rejectSelected;
    }

    // ── Data ─────────────────────────────────────────────────────────────────
    async function loadCandidates() {
        const listEl = $('fg-list');
        listEl.innerHTML = '<div class="fg-row fg-row-skeleton"></div><div class="fg-row fg-row-skeleton"></div>';
        try {
            const res = await tlFetch(`/api/figma/projects/${currentProjectId}/candidates?status=${currentStatus}&limit=200`);
            if (!res.ok) throw new Error('candidates ' + res.status);
            const data = await res.json();
            candidates = data.candidates || [];
        } catch (_) {
            renderError('Could not load the inbox. Refresh to retry.');
            return;
        }
        render();
        loadThumbnails();
    }

    // ── Rendering ────────────────────────────────────────────────────────────
    function render() {
        const listEl = $('fg-list');
        if (!candidates.length) {
            listEl.innerHTML = emptyStateHtml();
            updateActionButtons();
            return;
        }
        listEl.innerHTML = candidates.map(rowHtml).join('');

        listEl.querySelectorAll('.fg-check').forEach(cb => { cb.onchange = updateActionButtons; });
        listEl.querySelectorAll('.fg-key-input:not([disabled])').forEach(input => {
            input.onchange = () => saveKey(input);
            input.oninput = () => input.classList.remove('fg-key-err');
        });
        listEl.querySelectorAll('.fg-thumb[data-zoom]').forEach(img => { img.onclick = () => openLightbox(img.src); });
        updateActionButtons();
    }

    function rowHtml(c) {
        const pending = c.status === 'PENDING';
        const conflict = conflicts[c.effectiveKey];
        const meta = [c.pageName, c.frameName].filter(Boolean).join(' › ');
        const badges = [];
        if (c.boundKey) badges.push('<span class="fg-badge update">Copy update</span>');
        if (c.duplicateOfKey) badges.push(`<span class="fg-badge duplicate">Duplicate</span><span class="fg-badge-hint">same text as <code>${esc(c.duplicateOfKey)}</code></span>`);
        else if (c.similarToKey) badges.push(`<span class="fg-badge similar">Similar</span><span class="fg-badge-hint">≈ <code>${esc(c.similarToKey)}</code>${c.similarityScore ? ' (' + Math.round(c.similarityScore * 100) + '%)' : ''}</span>`);
        if (c.status === 'PR_OPEN') badges.push('<span class="fg-badge pr">PR opened</span>' + (c.prUrl ? ` <a class="fg-pr-link" href="${esc(c.prUrl)}" target="_blank" rel="noopener">View PR ↗</a>` : ''));
        if (c.status === 'REJECTED') badges.push('<span class="fg-badge rejected">Rejected</span>');

        const thumb = c.figmaFrameId
            ? `<img class="fg-thumb" data-key="${esc(c.figmaFileKey)}|${esc(c.figmaFrameId)}" alt="" loading="lazy">`
            : '<div class="fg-thumb fg-thumb-empty">no frame</div>';

        return `<div class="fg-row${conflict ? ' fg-conflict' : ''}" data-id="${esc(c.id)}">
            ${pending ? `<input type="checkbox" class="fg-check" data-id="${esc(c.id)}">` : ''}
            ${thumb}
            <div class="fg-row-body">
                <div class="fg-text">${esc(c.sourceText)}</div>
                <div class="fg-meta">${meta ? esc(meta) + '<span class="fg-sep">·</span>' : ''}${esc(c.nodeName)}</div>
                <div class="fg-key-line">
                    <input type="text" class="fg-key-input" data-id="${esc(c.id)}" value="${esc(c.effectiveKey)}"
                        ${pending && !c.boundKey ? '' : 'disabled'}
                        title="${c.boundKey ? 'Bound to an existing key — approving updates its text in place' : 'String key written to the source file'}">
                    <span class="fg-key-saved" data-id="${esc(c.id)}">saved</span>
                    ${badges.join(' ')}
                </div>
                ${conflict ? `<div class="fg-conflict-msg">${esc(conflict)}</div>` : ''}
            </div>
        </div>`;
    }

    function emptyStateHtml() {
        if (currentStatus !== 'PENDING') {
            return `<div class="fg-empty"><p>Nothing here yet.</p></div>`;
        }
        return `<div class="fg-empty">
            <h3>Inbox zero 🎉</h3>
            <p>Push strings from Figma and they'll land here for review.</p>
            <p>In Figma: <strong>Plugins → Syncling — Strings to GitHub</strong>, select your frames, and send.
               The plugin needs an <a href="/tokens">API token</a> and this project's ID
               <code>${esc(currentProjectId || '')}</code>.</p>
        </div>`;
    }

    function renderError(msg) { $('fg-list').innerHTML = `<div class="fg-empty"><p>${esc(msg)}</p></div>`; }

    function renderNoProjects() {
        $('fg-list').innerHTML = `<div class="fg-empty">
            <h3>No projects yet</h3>
            <p>Create a project first — the Figma inbox stages strings per project.</p>
            <p><a href="/projects">Go to projects →</a></p>
        </div>`;
    }

    function selectedIds() {
        return Array.from(document.querySelectorAll('.fg-check:checked')).map(cb => cb.dataset.id);
    }

    function updateActionButtons() {
        const n = selectedIds().length;
        $('fg-approve-btn').disabled = n === 0;
        $('fg-reject-btn').disabled = n === 0;
        $('fg-approve-btn').textContent = n > 0 ? `Approve ${n} → PR` : 'Approve → PR';
    }

    // ── Actions ──────────────────────────────────────────────────────────────
    async function saveKey(input) {
        const id = input.dataset.id;
        const key = input.value.trim();
        if (!KEY_RE.test(key)) {
            input.classList.add('fg-key-err');
            toast('Keys are lowercase letters, digits and underscores, starting with a letter.', 'error');
            return;
        }
        try {
            const res = await tlFetch(`/api/figma/candidates/${id}`, { method: 'PATCH', body: JSON.stringify({ key }) });
            if (!res.ok) {
                const body = await res.json().catch(() => ({}));
                throw new Error(body.error || 'HTTP ' + res.status);
            }
            const c = candidates.find(x => x.id === id);
            if (c) { c.finalKey = key; c.effectiveKey = key; }
            const saved = document.querySelector(`.fg-key-saved[data-id="${CSS.escape(id)}"]`);
            if (saved) { saved.classList.add('show'); setTimeout(() => saved.classList.remove('show'), 1600); }
        } catch (e) {
            input.classList.add('fg-key-err');
            toast(e.message || 'Could not save the key', 'error');
        }
    }

    async function approveSelected() {
        const ids = selectedIds();
        if (!ids.length) return;
        const btn = $('fg-approve-btn');
        btn.disabled = true;
        btn.textContent = 'Opening PR…';
        conflicts = {};
        try {
            const targetSel = $('fg-target-file');
            const body = { ids };
            if (targetSel.value) body.targetFile = targetSel.value;
            const res = await tlFetch(`/api/figma/projects/${currentProjectId}/approve`, {
                method: 'POST', body: JSON.stringify(body)
            });
            const data = await res.json().catch(() => ({}));
            if (res.status === 409 && data.conflicts) {
                conflicts = data.conflicts;
                render();
                toast('Key conflicts — fix the highlighted rows first.', 'error');
                return;
            }
            if (!res.ok) throw new Error(data.error || 'HTTP ' + res.status);
            toast(`PR opened — ${(data.keysAdded || []).length} new, ${(data.keysUpdated || []).length} updated.`, 'success');
            if (data.prUrl) window.open(data.prUrl, '_blank', 'noopener');
            await loadCandidates();
        } catch (e) {
            toast(e.message || 'Could not open the PR', 'error');
        } finally {
            btn.textContent = 'Approve → PR';
            updateActionButtons();
        }
    }

    async function rejectSelected() {
        const ids = selectedIds();
        if (!ids.length) return;
        const btn = $('fg-reject-btn');
        btn.disabled = true;
        try {
            const res = await tlFetch(`/api/figma/projects/${currentProjectId}/reject`, {
                method: 'POST', body: JSON.stringify({ ids })
            });
            if (!res.ok) {
                const body = await res.json().catch(() => ({}));
                throw new Error(body.error || 'HTTP ' + res.status);
            }
            toast(`${ids.length} string${ids.length === 1 ? '' : 's'} rejected. The same copy won't be re-suggested.`, 'success');
            await loadCandidates();
        } catch (e) {
            toast(e.message || 'Could not reject', 'error');
        } finally {
            updateActionButtons();
        }
    }

    // ── Thumbnails ───────────────────────────────────────────────────────────
    async function loadThumbnails() {
        const imgs = Array.from(document.querySelectorAll('.fg-thumb[data-key]'));
        const wanted = new Map();
        imgs.forEach(img => {
            const key = img.dataset.key;
            if (!wanted.has(key)) wanted.set(key, []);
            wanted.get(key).push(img);
        });
        for (const [key, els] of wanted) {
            let url = previewUrls.get(key);
            if (url === undefined) {
                const [fileKey, frameId] = key.split('|');
                try {
                    const res = await tlFetch(`/api/figma/projects/${currentProjectId}/preview?fileKey=${encodeURIComponent(fileKey)}&frameId=${encodeURIComponent(frameId)}`);
                    url = res.ok ? URL.createObjectURL(await res.blob()) : 'none';
                } catch (_) { url = 'none'; }
                previewUrls.set(key, url);
            }
            els.forEach(img => {
                if (url === 'none') {
                    const ph = document.createElement('div');
                    ph.className = 'fg-thumb fg-thumb-empty';
                    ph.textContent = 'no preview';
                    img.replaceWith(ph);
                } else {
                    img.src = url;
                    img.setAttribute('data-zoom', '1');
                    img.onclick = () => openLightbox(url);
                }
            });
        }
    }

    function openLightbox(src) {
        let box = $('fg-lightbox');
        if (!box) {
            box = document.createElement('div');
            box.id = 'fg-lightbox';
            box.className = 'fg-lightbox';
            box.innerHTML = '<img alt="Frame preview">';
            box.onclick = () => box.classList.remove('open');
            document.body.appendChild(box);
        }
        box.querySelector('img').src = src;
        box.classList.add('open');
    }
})();
