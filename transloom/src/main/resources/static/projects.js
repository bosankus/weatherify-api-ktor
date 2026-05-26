/**
 * Projects page client.
 *
 * Three top-level views, all owned by this file:
 *   - The grid (#pr-list)               — lazy-rendered cards from GET /projects
 *   - The create modal (#pr-modal-mount) — built lazily on first open
 *   - The detail drawer (#pr-drawer-mount) — built lazily on first card click
 *
 * State is small enough to keep as module-level variables instead of pulling in
 * a framework — the grid is the only thing that re-renders in bulk, and it does
 * so by recomputing innerHTML from `projectsState`.
 */
(function () {
    if (!window.tlFetch) {
        console.error('projects.js: portal shell not initialised (no window.tlFetch)');
        return;
    }

    const API = '/transloom/api/projects';

    // ── Catalog data ─────────────────────────────────────────────────────────
    // Curated dropdowns. Backend accepts free strings, so adding to these lists
    // is purely a UX change — no server work required.
    const CATEGORIES = ['Productivity', 'Social', 'Finance', 'E-commerce', 'Education', 'Health', 'Travel', 'Entertainment', 'Utilities', 'Other'];
    const TONES = ['Casual', 'Conversational', 'Formal', 'Friendly', 'Professional', 'Playful', 'Technical'];

    // Top 20-ish languages most apps target. The "code" matches Android locale
    // qualifiers; "file" is the default per-language strings file path.
    const LANGUAGES = [
        { code: 'es', name: 'Spanish' },
        { code: 'fr', name: 'French' },
        { code: 'de', name: 'German' },
        { code: 'it', name: 'Italian' },
        { code: 'pt', name: 'Portuguese (BR)', region: 'BR' },
        { code: 'pt', name: 'Portuguese (PT)', region: 'PT' },
        { code: 'ja', name: 'Japanese' },
        { code: 'ko', name: 'Korean' },
        { code: 'zh', name: 'Chinese (Simplified)', region: 'CN' },
        { code: 'zh', name: 'Chinese (Traditional)', region: 'TW' },
        { code: 'hi', name: 'Hindi' },
        { code: 'ar', name: 'Arabic' },
        { code: 'ru', name: 'Russian' },
        { code: 'tr', name: 'Turkish' },
        { code: 'pl', name: 'Polish' },
        { code: 'nl', name: 'Dutch' },
        { code: 'sv', name: 'Swedish' },
        { code: 'id', name: 'Indonesian' },
        { code: 'th', name: 'Thai' },
        { code: 'vi', name: 'Vietnamese' },
    ];

    // ── Utility helpers ──────────────────────────────────────────────────────
    const $ = id => document.getElementById(id);
    const esc = s => {
        const d = document.createElement('div');
        d.textContent = String(s == null ? '' : s);
        return d.innerHTML;
    };
    const langKey = t => `${t.code}|${t.region || ''}`;
    const langLabel = t => t.region ? `${t.name || t.code} (${t.region})` : (t.name || t.code);
    const defaultLangFile = t => `values-${t.region ? `${t.code}-r${t.region}` : t.code}/strings.xml`;

    let projectsState = [];

    // ── Boot ─────────────────────────────────────────────────────────────────
    document.addEventListener('click', e => {
        const newBtn = e.target.closest('#pr-new-btn');
        if (newBtn) openCreateModal();
        const card = e.target.closest('[data-project-id]');
        if (card) openDrawer(card.dataset.projectId);
    });

    loadProjects();

    async function loadProjects() {
        try {
            const r = await tlFetch(API);
            if (!r.ok) throw new Error('status ' + r.status);
            const { projects = [] } = await r.json();
            projectsState = projects;
            renderGrid();
        } catch (err) {
            console.warn('projects: list load failed', err);
            $('pr-list').innerHTML = renderError('Could not load projects. Refresh to try again.');
        }
    }

    function renderError(msg) {
        return `<div class="pr-empty"><h3>Something went wrong</h3><p>${esc(msg)}</p></div>`;
    }

    function renderGrid() {
        const host = $('pr-list');
        if (!host) return;
        if (!projectsState.length) {
            host.innerHTML =
                '<div class="pr-empty">' +
                '<h3>No projects yet</h3>' +
                '<p>Connect a GitHub repo to start translating on every push.</p>' +
                '<button type="button" class="bl-btn primary" id="pr-empty-cta">+ New project</button>' +
                '</div>';
            const cta = document.getElementById('pr-empty-cta');
            if (cta) cta.addEventListener('click', openCreateModal);
            return;
        }
        host.innerHTML = projectsState.map(renderCard).join('');
    }

    function renderCard(p) {
        // We don't have a per-project connected-state in the list response, so
        // the dot is purely informational until the drawer fetches detail.
        return `
            <article class="pr-card" data-project-id="${esc(p.id)}" tabindex="0" role="button" aria-label="Open ${esc(p.name)}">
                <div class="pr-card-top">
                    <h3 class="pr-card-name">${esc(p.name)}</h3>
                    <span class="pr-card-status" aria-hidden="true"></span>
                </div>
                <div class="pr-card-repo" title="${esc(p.githubRepo)}">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 19c-5 1.5-5-2.5-7-3m14 6v-3.87a3.37 3.37 0 0 0-.94-2.61c3.14-.35 6.44-1.54 6.44-7A5.44 5.44 0 0 0 20 4.77 5.07 5.07 0 0 0 19.91 1S18.73.65 16 2.48a13.38 13.38 0 0 0-7 0C6.27.65 5.09 1 5.09 1A5.07 5.07 0 0 0 5 4.77a5.44 5.44 0 0 0-1.5 3.78c0 5.42 3.3 6.61 6.44 7A3.37 3.37 0 0 0 9 18.13V22"/></svg>
                    ${esc(p.githubRepo)}
                </div>
                <div class="pr-card-meta">
                    <span class="pr-card-meta-item"><strong>${p.targetCount}</strong> languages</span>
                    <span class="pr-card-meta-item"><strong>${esc(p.watchBranch)}</strong> branch</span>
                </div>
            </article>`;
    }

    // ── Create modal ─────────────────────────────────────────────────────────
    function openCreateModal() {
        const mount = $('pr-modal-mount');
        if (!mount || mount.firstElementChild) return;
        mount.innerHTML = renderCreateModalHtml();
        // firstElementChild skips the leading whitespace text node from the template literal.
        const overlay = mount.firstElementChild;
        requestAnimationFrame(() => overlay.classList.add('show'));

        const selectedLangs = []; // [{code, name, region, file}]
        const langChips = overlay.querySelector('#pr-lang-chips');
        const langSelect = overlay.querySelector('#pr-lang-select');
        const langAdd = overlay.querySelector('#pr-lang-add-btn');

        function renderLangChips() {
            if (!selectedLangs.length) {
                langChips.innerHTML = '<span style="color:var(--text-muted);font-size:12px;padding:6px">Add at least one target language</span>';
                return;
            }
            langChips.innerHTML = selectedLangs.map((l, i) =>
                `<span class="pr-lang-chip">${esc(langLabel(l))}<button type="button" data-rm="${i}" aria-label="Remove ${esc(langLabel(l))}">×</button></span>`
            ).join('');
        }
        renderLangChips();

        // Build the language dropdown, hiding already-selected entries.
        function refreshLangSelect() {
            const taken = new Set(selectedLangs.map(langKey));
            const remaining = LANGUAGES.filter(l => !taken.has(langKey(l)));
            langSelect.innerHTML = '<option value="">Choose a language…</option>' +
                remaining.map((l, i) => `<option value="${i}">${esc(langLabel(l))}</option>`).join('');
            langSelect.dataset.options = JSON.stringify(remaining);
        }
        refreshLangSelect();

        langAdd.addEventListener('click', () => {
            const idx = langSelect.value;
            if (!idx) return;
            const remaining = JSON.parse(langSelect.dataset.options);
            const pick = remaining[parseInt(idx, 10)];
            selectedLangs.push({ ...pick, file: defaultLangFile(pick) });
            renderLangChips();
            refreshLangSelect();
        });

        langChips.addEventListener('click', e => {
            const btn = e.target.closest('[data-rm]');
            if (!btn) return;
            selectedLangs.splice(parseInt(btn.dataset.rm, 10), 1);
            renderLangChips();
            refreshLangSelect();
        });

        overlay.addEventListener('click', e => {
            if (e.target === overlay || e.target.closest('[data-modal-act="close"]')) {
                return closeCreateModal();
            }
            const submit = e.target.closest('[data-modal-act="submit"]');
            if (submit) submitCreate(overlay, selectedLangs, submit);
        });

        document.addEventListener('keydown', escCreate);
        overlay.querySelector('#pr-name').focus();
    }

    function escCreate(e) { if (e.key === 'Escape') closeCreateModal(); }
    function closeCreateModal() {
        const mount = $('pr-modal-mount');
        if (mount) mount.innerHTML = '';
        document.removeEventListener('keydown', escCreate);
    }

    function renderCreateModalHtml() {
        return `
        <div class="pr-modal-overlay" role="dialog" aria-modal="true" aria-labelledby="pr-create-title">
          <div class="pr-modal">
            <div class="pr-modal-head">
              <h3 id="pr-create-title">New project</h3>
              <button type="button" class="pr-modal-close" data-modal-act="close" aria-label="Close">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              </button>
            </div>
            <p class="pr-modal-sub">Connect a GitHub repo and we'll auto-install the push webhook. Translations run on every commit to the watched branch.</p>

            <div class="pr-form-row">
              <label for="pr-name">Project name</label>
              <input type="text" id="pr-name" maxlength="100" placeholder="My amazing app">
            </div>

            <div class="pr-form-row">
              <label for="pr-repo">GitHub repository</label>
              <input type="text" id="pr-repo" placeholder="owner/repo">
              <div class="pr-form-hint">Public or private — we'll use your GitHub OAuth token to access it.</div>
            </div>

            <div class="pr-form-grid-2">
              <div class="pr-form-row">
                <label for="pr-branch">Branch to watch</label>
                <input type="text" id="pr-branch" value="main">
              </div>
              <div class="pr-form-row">
                <label for="pr-source">Source file path</label>
                <input type="text" id="pr-source" value="values/strings.xml">
              </div>
            </div>

            <div class="pr-form-grid-2">
              <div class="pr-form-row">
                <label for="pr-category">Category</label>
                <select id="pr-category">${CATEGORIES.map(c => `<option>${esc(c)}</option>`).join('')}</select>
              </div>
              <div class="pr-form-row">
                <label for="pr-tone">Tone</label>
                <select id="pr-tone">${TONES.map(t => `<option>${esc(t)}</option>`).join('')}</select>
              </div>
            </div>

            <div class="pr-form-row">
              <label>Target languages</label>
              <div class="pr-lang-list" id="pr-lang-chips"></div>
              <div class="pr-lang-add">
                <select id="pr-lang-select"></select>
                <button type="button" class="bl-btn" id="pr-lang-add-btn">Add</button>
              </div>
              <div class="pr-form-hint">Up to 25 languages.</div>
            </div>

            <div class="pr-modal-actions">
              <button type="button" class="bl-btn" data-modal-act="close">Cancel</button>
              <button type="button" class="bl-btn primary" data-modal-act="submit">Create project</button>
            </div>
          </div>
        </div>`;
    }

    async function submitCreate(overlay, selectedLangs, btn) {
        const name = overlay.querySelector('#pr-name').value.trim();
        const repo = overlay.querySelector('#pr-repo').value.trim();
        const branch = overlay.querySelector('#pr-branch').value.trim() || 'main';
        const source = overlay.querySelector('#pr-source').value.trim() || 'values/strings.xml';
        const category = overlay.querySelector('#pr-category').value;
        const tone = overlay.querySelector('#pr-tone').value;

        // Inline validation — server will validate too, but failing here is
        // faster and lets us highlight individual fields.
        const errors = [];
        if (!name) errors.push(['pr-name', 'Name is required']);
        if (!repo || !repo.includes('/')) errors.push(['pr-repo', 'Use owner/repo format']);
        if (!selectedLangs.length) errors.push(['pr-lang-select', 'Add at least one language']);
        if (selectedLangs.length > 25) errors.push(['pr-lang-select', 'Maximum 25 languages']);

        overlay.querySelectorAll('[aria-invalid="true"]').forEach(el => el.removeAttribute('aria-invalid'));
        if (errors.length) {
            errors.forEach(([id]) => overlay.querySelector('#' + id)?.setAttribute('aria-invalid', 'true'));
            toast(errors[0][1], 'error');
            return;
        }

        setBusy(btn, true, 'Creating');
        try {
            const r = await tlFetch(API, {
                method: 'POST',
                body: JSON.stringify({
                    name, githubRepo: repo, watchBranch: branch,
                    sourceFilePaths: [source],
                    category, tone,
                    targets: selectedLangs.map(l => ({
                        code: l.code, name: l.name, region: l.region || null, file: l.file,
                    })),
                    sharedMemoryOptIn: false,
                }),
            });
            if (!r.ok) {
                const err = await r.json().catch(() => ({ error: 'Failed to create project' }));
                if (err.code === 'GITHUB_REAUTH_REQUIRED') {
                    toast('Reconnect GitHub to create this project', 'error');
                    setTimeout(() => window.location.href = err.reauthUrl || '/transloom/auth/github', 1000);
                    return;
                }
                throw new Error(err.error || 'Failed to create');
            }
            closeCreateModal();
            toast('Project created — first run is on its way', 'success');
            loadProjects();
            window.Onboarding?.refresh?.();
        } catch (err) {
            toast(err.message, 'error');
        } finally {
            setBusy(btn, false, 'Create project');
        }
    }

    // ── Detail drawer ────────────────────────────────────────────────────────
    let openProjectId = null;

    async function openDrawer(id) {
        if (openProjectId === id) return;
        openProjectId = id;
        const mount = $('pr-drawer-mount');
        mount.innerHTML = renderDrawerSkeleton();
        const overlay = mount.querySelector('.pr-drawer-overlay');
        const drawer = mount.querySelector('.pr-drawer');
        requestAnimationFrame(() => {
            overlay.classList.add('show');
            drawer.classList.add('show');
        });
        overlay.addEventListener('click', closeDrawer);
        document.addEventListener('keydown', escDrawer);

        try {
            const r = await tlFetch(`${API}/${encodeURIComponent(id)}`);
            if (!r.ok) throw new Error('status ' + r.status);
            const detail = await r.json();
            renderDrawerDetail(detail);
        } catch (err) {
            mount.querySelector('.pr-drawer-body').innerHTML = renderError('Could not load project.');
            console.warn('projects: detail load failed', err);
        }
    }

    function escDrawer(e) { if (e.key === 'Escape') closeDrawer(); }

    function closeDrawer() {
        const mount = $('pr-drawer-mount');
        const overlay = mount?.querySelector('.pr-drawer-overlay');
        const drawer = mount?.querySelector('.pr-drawer');
        if (!overlay || !drawer) { if (mount) mount.innerHTML = ''; openProjectId = null; return; }
        overlay.classList.remove('show');
        drawer.classList.remove('show');
        setTimeout(() => { mount.innerHTML = ''; openProjectId = null; }, 280);
        document.removeEventListener('keydown', escDrawer);
    }

    function renderDrawerSkeleton() {
        return `
        <div class="pr-drawer-overlay"></div>
        <aside class="pr-drawer" role="dialog" aria-modal="true" aria-labelledby="pr-drawer-title">
          <header class="pr-drawer-head">
            <h2 class="pr-drawer-title" id="pr-drawer-title">Loading…</h2>
            <button type="button" class="pr-modal-close" aria-label="Close" data-drawer-act="close">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>
          </header>
          <div class="pr-drawer-body"><div class="pr-card-skeleton" style="height:120px;border-radius:8px"></div></div>
          <footer class="pr-drawer-foot"></footer>
        </aside>`;
    }

    function renderDrawerDetail(p) {
        const mount = $('pr-drawer-mount');
        mount.querySelector('#pr-drawer-title').textContent = p.name;
        mount.querySelector('[data-drawer-act="close"]').addEventListener('click', closeDrawer);

        const webhookOk = !!p.webhookVerifiedAt;
        const body = mount.querySelector('.pr-drawer-body');
        body.innerHTML = `
          <section class="pr-section">
            <h3 class="pr-section-title">Repository</h3>
            <dl class="pr-detail-meta">
              <dt>Repo</dt><dd><a href="https://github.com/${esc(p.githubRepo)}" target="_blank" rel="noopener">${esc(p.githubRepo)}</a></dd>
              <dt>Branch</dt><dd>${esc(p.watchBranch)}</dd>
              <dt>Source</dt><dd>${esc((p.sourceFilePaths || []).join(', '))}</dd>
              <dt>Webhook</dt><dd>${webhookOk
                ? '<span class="pr-webhook-pill connected">● Connected</span>'
                : '<span class="pr-webhook-pill disconnected">● Not verified</span> <button type="button" class="bl-btn" style="margin-left:8px;padding:4px 10px;font-size:11px" data-drawer-act="reinstall-webhook">Reinstall</button>'}</dd>
              <dt>Targets</dt><dd>${(p.targets || []).length} languages</dd>
            </dl>
          </section>

          <section class="pr-section">
            <h3 class="pr-section-title">Project settings</h3>
            <div class="pr-form-grid-2">
              <div class="pr-form-row">
                <label for="pr-edit-tone">Tone</label>
                <select id="pr-edit-tone">${TONES.map(t => `<option ${t === p.tone ? 'selected' : ''}>${esc(t)}</option>`).join('')}</select>
              </div>
              <div class="pr-form-row">
                <label for="pr-edit-category">Category</label>
                <select id="pr-edit-category">${CATEGORIES.map(c => `<option ${c === p.category ? 'selected' : ''}>${esc(c)}</option>`).join('')}</select>
              </div>
            </div>
          </section>

          <section class="pr-section">
            <h3 class="pr-section-title">Automation</h3>
            ${toggleRow('autoApproveEnabled', 'Auto-approve safe translations',
                'When the cultural-sensitivity check passes, skip human review.', p.autoApproveEnabled)}
            ${toggleRow('culturalSensitivityEnabled', 'Run cultural sensitivity checks',
                'Adds an AI pass that flags tone, formality, and regional issues.', p.culturalSensitivityEnabled)}
            ${toggleRow('otaEnabled', 'Publish to CDN (OTA)',
                'Push translation bundles to the CDN at the end of each run.', p.otaEnabled)}
            ${toggleRow('autoPromote', 'Auto-promote new bundles',
                'New CDN bundles become active immediately. Disable to require manual promotion.', p.autoPromote)}
            ${toggleRow('sharedMemoryOptIn', 'Contribute to shared memory',
                'Approved translations help other Transloom projects with similar phrases.', p.sharedMemoryOptIn)}
          </section>

          <section class="pr-section">
            <h3 class="pr-section-title">Export</h3>
            <p class="pr-toggle-hint" style="margin:0 0 10px">Download approved translations in your source format. Skips blocked or pending-review rows.</p>
            <div class="pr-form-grid-2">
              <div class="pr-form-row" style="margin:0">
                <label for="pr-export-lang">Language</label>
                <select id="pr-export-lang">
                  ${(p.targets || []).map(t => `<option value="${esc(t.code)}">${esc(t.name || t.code)}${t.region ? ' (' + esc(t.region) + ')' : ''}</option>`).join('')}
                </select>
              </div>
              <div class="pr-form-row" style="margin:0">
                <label for="pr-export-fmt">Format</label>
                <select id="pr-export-fmt">
                  <option value="auto">Auto (match source)</option>
                  <option value="xml">Android XML</option>
                  <option value="strings">iOS .strings</option>
                  <option value="json">JSON / ARB</option>
                </select>
              </div>
            </div>
            <button type="button" class="bl-btn primary" data-drawer-act="export" style="margin-top:10px">Download bundle</button>
          </section>

          <section class="pr-section">
            <h3 class="pr-section-title">Wire into CI</h3>
            <p class="pr-toggle-hint" style="margin:0 0 8px">Trigger a translation run from any CI step. Drop your JWT in <code>$TL_TOKEN</code>:</p>
            <pre class="pr-ci-snippet" data-ci-snippet><code>curl -X POST \\
  -H "Authorization: Bearer $TL_TOKEN" \\
  ${location.origin}/transloom/api/projects/${esc(p.id)}/sync</code></pre>
            <button type="button" class="bl-btn" data-drawer-act="copy-ci" style="margin-top:8px;padding:6px 12px;font-size:12px">Copy snippet</button>
          </section>
        `;

        // Wire toggles and reinstall.
        body.querySelectorAll('[data-toggle]').forEach(el => {
            el.addEventListener('click', () => toggleSetting(p.id, el));
        });
        body.querySelectorAll('#pr-edit-tone, #pr-edit-category').forEach(el => {
            el.addEventListener('change', () => saveBasicSetting(p.id, el));
        });
        body.querySelector('[data-drawer-act="reinstall-webhook"]')?.addEventListener('click', e => reinstallWebhook(p.id, e.currentTarget));
        body.querySelector('[data-drawer-act="export"]')?.addEventListener('click', e => exportBundle(p.id, e.currentTarget, body));
        body.querySelector('[data-drawer-act="copy-ci"]')?.addEventListener('click', e => copyCiSnippet(e.currentTarget, body));

        const foot = mount.querySelector('.pr-drawer-foot');
        foot.innerHTML = `
          <button type="button" class="bl-btn danger" data-drawer-act="delete">Delete project</button>
          <div style="display:flex;gap:8px">
            <button type="button" class="bl-btn" data-drawer-act="sync">Sync now</button>
          </div>`;
        foot.querySelector('[data-drawer-act="delete"]').addEventListener('click', e => deleteProject(p, e.currentTarget));
        foot.querySelector('[data-drawer-act="sync"]').addEventListener('click', e => syncNow(p.id, e.currentTarget));
    }

    function toggleRow(field, label, hint, value) {
        return `
          <div class="pr-toggle-row">
            <div class="pr-toggle-label-block">
              <p class="pr-toggle-label">${esc(label)}</p>
              <p class="pr-toggle-hint">${esc(hint)}</p>
            </div>
            <button type="button" class="pr-switch" aria-pressed="${value ? 'true' : 'false'}" data-toggle="${esc(field)}" aria-label="${esc(label)}"></button>
          </div>`;
    }

    async function toggleSetting(projectId, btn) {
        const field = btn.dataset.toggle;
        const next = btn.getAttribute('aria-pressed') !== 'true';
        btn.setAttribute('aria-pressed', next ? 'true' : 'false');
        btn.setAttribute('disabled', '');
        try {
            const r = await tlFetch(`${API}/${encodeURIComponent(projectId)}`, {
                method: 'PUT',
                body: JSON.stringify({ [field]: next }),
            });
            if (!r.ok) throw new Error('save failed');
        } catch (err) {
            // Rollback optimistic toggle.
            btn.setAttribute('aria-pressed', next ? 'false' : 'true');
            toast('Could not save setting', 'error');
        } finally {
            btn.removeAttribute('disabled');
        }
    }

    async function saveBasicSetting(projectId, el) {
        const field = el.id === 'pr-edit-tone' ? 'tone' : 'category';
        try {
            const r = await tlFetch(`${API}/${encodeURIComponent(projectId)}`, {
                method: 'PUT',
                body: JSON.stringify({ [field]: el.value }),
            });
            if (!r.ok) throw new Error();
            toast('Saved', 'success');
        } catch {
            toast('Could not save', 'error');
        }
    }

    async function reinstallWebhook(projectId, btn) {
        setBusy(btn, true, 'Reinstalling');
        try {
            const r = await tlFetch(`${API}/${encodeURIComponent(projectId)}/install-webhook`, { method: 'POST' });
            if (!r.ok) throw new Error();
            toast('Webhook reinstalled', 'success');
            openProjectId = null;
            openDrawer(projectId);
        } catch {
            toast('Webhook install failed', 'error');
            setBusy(btn, false, 'Reinstall');
        }
    }

    async function syncNow(projectId, btn) {
        setBusy(btn, true, 'Syncing');
        try {
            const r = await tlFetch(`${API}/${encodeURIComponent(projectId)}/sync`, { method: 'POST' });
            if (!r.ok) {
                const err = await r.json().catch(() => ({}));
                throw new Error(err.error || 'Sync failed');
            }
            toast('Sync queued — check the dashboard for progress', 'success');
        } catch (err) {
            toast(err.message, 'error');
        } finally {
            setBusy(btn, false, 'Sync now');
        }
    }

    async function deleteProject(p, btn) {
        if (!confirm(`Delete "${p.name}"? Translations and history will be removed. This cannot be undone.`)) return;
        setBusy(btn, true, 'Deleting');
        try {
            const r = await tlFetch(`${API}/${encodeURIComponent(p.id)}`, { method: 'DELETE' });
            if (!r.ok) throw new Error();
            toast('Project deleted', 'success');
            closeDrawer();
            loadProjects();
        } catch {
            toast('Delete failed', 'error');
            setBusy(btn, false, 'Delete project');
        }
    }

    async function exportBundle(projectId, btn, body) {
        const lang = body.querySelector('#pr-export-lang')?.value;
        const fmt = body.querySelector('#pr-export-fmt')?.value || 'auto';
        if (!lang) { toast('Pick a language to export', 'error'); return; }
        setBusy(btn, true, 'Preparing');
        try {
            const url = `${API}/${encodeURIComponent(projectId)}/export?lang=${encodeURIComponent(lang)}&format=${encodeURIComponent(fmt)}`;
            const r = await tlFetch(url);
            if (r.status === 204) { toast('Nothing to export yet for this language', 'error'); return; }
            if (!r.ok) throw new Error('export failed');
            const blob = await r.blob();
            const disp = r.headers.get('Content-Disposition') || '';
            const m = disp.match(/filename="?([^"]+)"?/);
            const filename = m ? m[1] : `${lang}.txt`;
            const a = document.createElement('a');
            a.href = URL.createObjectURL(blob);
            a.download = filename;
            document.body.appendChild(a); a.click();
            setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 0);
            toast('Bundle downloaded', 'success');
        } catch {
            toast('Export failed', 'error');
        } finally {
            setBusy(btn, false, 'Download bundle');
        }
    }

    async function copyCiSnippet(btn, body) {
        const snippet = body.querySelector('[data-ci-snippet]')?.innerText || '';
        try {
            await navigator.clipboard.writeText(snippet);
            toast('Copied to clipboard', 'success');
        } catch {
            toast('Copy failed — select manually', 'error');
        }
    }

    function setBusy(btn, busy, label) {
        if (!btn) return;
        if (busy) {
            btn.disabled = true;
            btn.dataset.prevLabel = btn.textContent;
            btn.innerHTML = '<span class="bl-spin"></span> ' + esc(label);
        } else {
            btn.disabled = false;
            btn.textContent = label || btn.dataset.prevLabel || 'Done';
        }
    }
})();
