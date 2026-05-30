/**
 * Review portal client.
 *
 * One source of truth: `state.items` (all pending reviews fetched in one request,
 * up to MAX_FETCH). The visible list is derived on every render by applying
 * filter + search to that array, then grouping by (project + commit). This keeps
 * filtering and search instant — no extra round-trips.
 *
 * Cards are rendered as raw HTML strings (one `.rv-list` innerHTML write per
 * render) instead of per-card DOM diffing — the dataset is small (<= 200 items)
 * and this keeps the file approachable. Per-card actions wire up via event
 * delegation on `#rv-list`.
 */
(function () {
    if (!window.tlFetch) {
        console.error('review.js: portal shell not initialised (no window.tlFetch)');
        return;
    }

    const API = '/transloom/api/review';
    const MAX_FETCH = 200; // server caps at 200 anyway

    const state = {
        items: [],
        filter: 'all', // 'all' | 'review' | 'blocked' | 'cultural'
        search: '',
        editedTexts: {}, // id → in-progress edit text (lost on refresh)
        rejecting: null, // id of the card whose reject panel is open
        hotfixing: null, // id of the card whose hotfix confirm panel is open
    };

    // ── Utility helpers ──────────────────────────────────────────────────────
    const $ = id => document.getElementById(id);
    const esc = s => {
        const d = document.createElement('div');
        d.textContent = String(s == null ? '' : s);
        return d.innerHTML;
    };
    const langLabel = it => it.targetRegion
        ? `${it.targetLanguage}-${it.targetRegion}`.toUpperCase()
        : it.targetLanguage.toUpperCase();

    const isCultural = it =>
        (it.blockReason || '').toLowerCase().includes('cultural') ||
        (it.blockReason || '').toLowerCase().includes('sensitiv');

    const statusPill = it => {
        if (isCultural(it)) return { cls: 'rv-pill-cultural', label: 'Cultural' };
        if ((it.status || '').toUpperCase().includes('BLOCK')) return { cls: 'rv-pill-blocked', label: 'Blocked' };
        return { cls: 'rv-pill-review', label: 'Needs review' };
    };

    const cardSeverityClass = it => {
        if ((it.status || '').toUpperCase().includes('BLOCK')) return 'status-blocked';
        return 'status-review';
    };

    // ── Boot ─────────────────────────────────────────────────────────────────
    $('rv-refresh-btn')?.addEventListener('click', () => load(true));
    $('rv-filters')?.addEventListener('click', e => {
        const btn = e.target.closest('[data-filter]');
        if (!btn) return;
        state.filter = btn.dataset.filter;
        $('rv-filters').querySelectorAll('[data-filter]').forEach(b => {
            const on = b === btn;
            b.classList.toggle('active', on);
            b.setAttribute('aria-selected', on ? 'true' : 'false');
        });
        renderList();
    });
    $('rv-search')?.addEventListener('input', debounce(e => {
        state.search = e.target.value.trim().toLowerCase();
        renderList();
    }, 120));

    load();

    // ── Loader ───────────────────────────────────────────────────────────────
    async function load(force) {
        if (force) {
            const btn = $('rv-refresh-btn');
            if (btn) btn.disabled = true;
        }
        try {
            const r = await tlFetch(`${API}?limit=${MAX_FETCH}`);
            if (!r.ok) throw new Error('status ' + r.status);
            const data = await r.json();
            state.items = data.pending_reviews || [];
            renderStats();
            renderList();
        } catch (err) {
            console.warn('review: load failed', err);
            renderError();
        } finally {
            const btn = $('rv-refresh-btn');
            if (btn) btn.disabled = false;
        }
    }

    function renderError() {
        $('rv-list').innerHTML = `
            <div class="rv-empty">
                <div class="rv-empty-icon warn">
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                </div>
                <div class="rv-empty-title">Couldn't load reviews</div>
                <div class="rv-empty-sub">Refresh to try again. If this keeps happening, check the dashboard for pipeline errors.</div>
            </div>`;
    }

    // ── Stats + filter counts ────────────────────────────────────────────────
    function renderStats() {
        const items = state.items;
        const pending = items.filter(i => !(i.status || '').toUpperCase().includes('BLOCK')).length;
        const blocked = items.filter(i => (i.status || '').toUpperCase().includes('BLOCK')).length;
        const cultural = items.filter(isCultural).length;
        const projects = new Set(items.map(i => i.projectId)).size;

        const chips = [
            { cls: 'rv-chip-pending', label: `${pending} pending` },
            { cls: 'rv-chip-blocked', label: `${blocked} blocked` },
            { cls: 'rv-chip-cultural', label: `${cultural} cultural` },
            { cls: 'rv-chip-projects', label: `${projects} project${projects === 1 ? '' : 's'}` },
        ];
        $('rv-stats').innerHTML = chips.map(c => `<span class="rv-stat-chip ${c.cls}">${esc(c.label)}</span>`).join('');

        // Filter counts.
        setCount('all', items.length);
        setCount('review', pending);
        setCount('blocked', blocked);
        setCount('cultural', cultural);
    }

    function setCount(filter, n) {
        const el = document.querySelector(`[data-filter-count="${filter}"]`);
        if (el) el.textContent = String(n);
    }

    // ── Filtering + grouping ─────────────────────────────────────────────────
    function visibleItems() {
        let xs = state.items;
        if (state.filter === 'review') xs = xs.filter(i => !(i.status || '').toUpperCase().includes('BLOCK'));
        else if (state.filter === 'blocked') xs = xs.filter(i => (i.status || '').toUpperCase().includes('BLOCK'));
        else if (state.filter === 'cultural') xs = xs.filter(isCultural);

        if (state.search) {
            const q = state.search;
            xs = xs.filter(i =>
                i.stringKey?.toLowerCase().includes(q) ||
                i.projectName?.toLowerCase().includes(q) ||
                i.sourceText?.toLowerCase().includes(q) ||
                i.translatedText?.toLowerCase().includes(q) ||
                langLabel(i).toLowerCase().includes(q)
            );
        }
        return xs;
    }

    function groupItems(xs) {
        // Group by project + commit so reviewers see all cards from one PR together.
        const groups = new Map();
        for (const it of xs) {
            const key = `${it.projectId}|${it.commitShort || 'unknown'}|${it.pipelineRunId || ''}`;
            let g = groups.get(key);
            if (!g) {
                g = {
                    key,
                    projectId: it.projectId,
                    projectName: it.projectName,
                    commitShort: it.commitShort,
                    pipelineRunId: it.pipelineRunId,
                    items: [],
                };
                groups.set(key, g);
            }
            g.items.push(it);
        }
        return [...groups.values()];
    }

    // ── Render ───────────────────────────────────────────────────────────────
    function renderList() {
        const xs = visibleItems();
        const host = $('rv-list');
        if (!xs.length) {
            host.innerHTML = renderEmpty();
            return;
        }
        const groups = groupItems(xs);
        host.innerHTML = groups.map(renderGroup).join('');
    }

    function renderEmpty() {
        const reason = state.search ? 'No matches for that search.'
            : state.filter === 'blocked' ? 'No blocked translations right now.'
            : state.filter === 'cultural' ? 'No cultural sensitivity flags right now.'
            : 'No translations are waiting for review.';
        return `
            <div class="rv-empty">
                <div class="rv-empty-icon">
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
                </div>
                <div class="rv-empty-title">${esc(reason)}</div>
            </div>`;
    }

    function renderGroup(g) {
        const unblocked = g.items.filter(i => !(i.status || '').toUpperCase().includes('BLOCK') && !i.lockedAt);
        const approvableIds = unblocked.map(i => i.id);
        return `
            <div class="rv-group">
                <div class="rv-group-header">
                    ${g.commitShort ? `<span class="rv-commit-badge">${esc(g.commitShort)}</span>` : ''}
                    <div class="rv-group-meta">
                        <span class="rv-group-project">${esc(g.projectName || 'Project')}</span>
                        <span class="rv-group-count">· ${g.items.length} string${g.items.length === 1 ? '' : 's'}</span>
                    </div>
                    ${approvableIds.length > 1
                        ? `<button type="button" class="rv-group-approve-all" data-approve-group="${esc(approvableIds.join(','))}">
                              Approve all <span class="rv-gaa-count">${approvableIds.length}</span>
                           </button>`
                        : ''}
                </div>
                ${g.items.map(renderCard).join('')}
            </div>`;
    }

    function renderCard(it) {
        const sev = cardSeverityClass(it);
        const pill = statusPill(it);
        const locked = !!it.lockedAt;
        const edited = state.editedTexts[it.id] != null ? state.editedTexts[it.id] : it.translatedText;
        const rejecting = state.rejecting === it.id;
        const hotfixing = state.hotfixing === it.id;
        const hotfixAllowed = !!it.projectOtaEnabled;

        const blockBanner = it.blockReason && !isCultural(it) ? `
            <div class="rv-block-banner">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="flex-shrink:0;margin-top:2px"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                <span>${esc(it.blockReason)}</span>
            </div>` : '';

        const culturalBanner = isCultural(it) && it.blockReason ? `
            <div class="rv-cultural-banner">
                <div class="rv-cultural-banner-title">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>
                    Cultural sensitivity check
                </div>
                <ul class="rv-cultural-issues"><li>${esc(it.blockReason)}</li></ul>
            </div>` : '';

        const lockBanner = locked ? `
            <div class="rv-lock-banner">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="flex-shrink:0"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                <span>Claimed for review${it.lockedBy ? ` by ${esc(it.lockedBy.slice(0, 8))}` : ''}</span>
                <button type="button" class="rv-btn-unlock" data-unlock="${esc(it.id)}">Release</button>
            </div>` : '';

        const diffBanner = it.previousTranslatedText ? `
            <div class="rv-diff-banner">
                <span class="rv-diff-label">Retranslation</span>
                <span class="rv-diff-prev">${esc(it.previousTranslatedText)}</span>
            </div>` : '';

        return `
            <div class="rv-card ${sev}" data-card-id="${esc(it.id)}">
                <div class="rv-card-header">
                    <div class="rv-card-header-left">
                        <span class="rv-key" title="${esc(it.stringKey)}">${esc(it.stringKey)}</span>
                        <div class="rv-badges">
                            <span class="rv-badge rv-badge-project">${esc(it.projectName || 'Project')}</span>
                            <span class="rv-badge rv-badge-lang">${esc(langLabel(it))}</span>
                            ${locked ? `<span class="rv-badge rv-badge-locked">
                                <svg width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                                Locked</span>` : ''}
                        </div>
                    </div>
                    <div class="rv-card-header-right">
                        <span class="rv-status-pill ${pill.cls}">${pill.label}</span>
                        ${locked ? '' : `<button type="button" class="rv-btn-lock" data-lock="${esc(it.id)}" title="Claim for review" aria-label="Claim for review">
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                        </button>`}
                    </div>
                </div>
                ${blockBanner}${culturalBanner}${lockBanner}${diffBanner}
                <div class="rv-body">
                    <div class="rv-source">
                        <div class="rv-pane-label">Source</div>
                        <div class="rv-source-text">${esc(it.sourceText)}</div>
                    </div>
                    <div class="rv-target">
                        <div class="rv-pane-label">Translation <span class="rv-editable-hint">(editable)</span></div>
                        <textarea class="rv-textarea" data-edit="${esc(it.id)}" rows="3">${esc(edited)}</textarea>
                    </div>
                </div>
                <div class="rv-actions">
                    <span class="rv-char-hint" data-charhint="${esc(it.id)}">${edited.length} char${edited.length === 1 ? '' : 's'}</span>
                    <div class="rv-action-btns">
                        ${(it.status || '').toUpperCase().includes('BLOCK')
                            ? `<button type="button" class="rv-btn-reject" data-retry-translation="${esc(it.id)}" title="Ask Gemini to translate this string again">
                                   <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 .49-4.5"/></svg>
                                   Retry translation
                               </button>`
                            : ''}
                        <button type="button" class="rv-btn-reject" data-reject="${esc(it.id)}">Reject</button>
                        ${hotfixAllowed
                            ? `<button type="button" class="rv-btn-hotfix" data-hotfix="${esc(it.id)}" title="Publish this translation to the CDN immediately">
                                   <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
                                   Hotfix
                               </button>`
                            : `<button type="button" class="rv-btn-hotfix disabled" disabled title="Enable OTA on this project's settings to hotfix translations" aria-disabled="true">
                                   <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
                                   Hotfix
                               </button>`}
                        <button type="button" class="rv-btn-approve" data-approve="${esc(it.id)}">Approve</button>
                    </div>
                </div>
                <div class="rv-hotfix-panel ${hotfixing ? 'open' : ''}" data-hotfix-panel="${esc(it.id)}">
                    <div class="rv-hotfix-warning">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
                        <div>
                            <strong>Publish this translation to the CDN immediately.</strong>
                            Hotfix skips the follow-up PR flow and pushes the new text to live apps via the OTA bundle.
                            ${(it.previousTranslatedText || edited !== it.translatedText) ? `<br>Make sure your edits in the text area above are exactly what you want shipped.` : ''}
                        </div>
                    </div>
                    <div class="rv-hotfix-footer">
                        <span class="rv-hotfix-summary" data-hotfix-summary="${esc(it.id)}">${edited.length} char${edited.length === 1 ? '' : 's'} to publish</span>
                        <button type="button" class="rv-btn-cancel" data-hotfix-cancel="${esc(it.id)}">Cancel</button>
                        <button type="button" class="rv-btn-confirm-hotfix" data-hotfix-confirm="${esc(it.id)}">
                            <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 4 12 14.01 9 11.01"/></svg>
                            Publish hotfix
                        </button>
                    </div>
                </div>
                <div class="rv-reject-panel ${rejecting ? 'open' : ''}" data-reject-panel="${esc(it.id)}">
                    <label style="font-size:12px;color:var(--text-muted)">Reject reason — shared with the next pipeline run.</label>
                    <textarea class="rv-textarea rv-reject-textarea" data-reject-text="${esc(it.id)}" placeholder="e.g. tone too formal, brand name should not be translated…"></textarea>
                    <div class="rv-reject-footer">
                        <button type="button" class="rv-btn-cancel" data-reject-cancel="${esc(it.id)}">Cancel</button>
                        <button type="button" class="rv-btn-confirm-reject" data-reject-confirm="${esc(it.id)}">Reject translation</button>
                    </div>
                </div>
            </div>`;
    }

    // ── Event delegation on #rv-list ────────────────────────────────────────
    $('rv-list').addEventListener('input', e => {
        const ta = e.target.closest('[data-edit]');
        if (!ta) return;
        const id = ta.dataset.edit;
        state.editedTexts[id] = ta.value;
        const hint = document.querySelector(`[data-charhint="${cssEsc(id)}"]`);
        if (hint) hint.textContent = `${ta.value.length} char${ta.value.length === 1 ? '' : 's'}`;
        // Keep the hotfix-confirm summary in sync if its panel is open.
        const summary = document.querySelector(`[data-hotfix-summary="${cssEsc(id)}"]`);
        if (summary) summary.textContent = `${ta.value.length} char${ta.value.length === 1 ? '' : 's'} to publish`;
    });

    $('rv-list').addEventListener('click', e => {
        const t = e.target;
        const lockBtn = t.closest('[data-lock]');
        if (lockBtn) return lock(lockBtn.dataset.lock);
        const unlockBtn = t.closest('[data-unlock]');
        if (unlockBtn) return unlock(unlockBtn.dataset.unlock);
        const approveBtn = t.closest('[data-approve]');
        if (approveBtn) return approve(approveBtn.dataset.approve, approveBtn);
        const rejectBtn = t.closest('[data-reject]');
        if (rejectBtn) return openReject(rejectBtn.dataset.reject);
        const cancelBtn = t.closest('[data-reject-cancel]');
        if (cancelBtn) return cancelReject(cancelBtn.dataset.rejectCancel);
        const confirmBtn = t.closest('[data-reject-confirm]');
        if (confirmBtn) return confirmReject(confirmBtn.dataset.rejectConfirm, confirmBtn);
        const groupBtn = t.closest('[data-approve-group]');
        if (groupBtn) return approveGroup(groupBtn.dataset.approveGroup.split(','), groupBtn);
        const hotfixBtn = t.closest('[data-hotfix]');
        if (hotfixBtn) return openHotfix(hotfixBtn.dataset.hotfix);
        const hotfixCancelBtn = t.closest('[data-hotfix-cancel]');
        if (hotfixCancelBtn) return cancelHotfix(hotfixCancelBtn.dataset.hotfixCancel);
        const hotfixConfirmBtn = t.closest('[data-hotfix-confirm]');
        if (hotfixConfirmBtn) return confirmHotfix(hotfixConfirmBtn.dataset.hotfixConfirm, hotfixConfirmBtn);
        const retryBtn = t.closest('[data-retry-translation]');
        if (retryBtn) return retryTranslation(retryBtn.dataset.retryTranslation, retryBtn);
    });

    async function retryTranslation(id, btn) {
        setBusy(btn, true);
        try {
            const r = await tlFetch(`${API}/${encodeURIComponent(id)}/retry-translation`, { method: 'POST' });
            if (!r.ok) {
                const err = await r.json().catch(() => ({}));
                throw new Error(err.error || 'retry failed');
            }
            const { status } = await r.json();
            if (status === 'auto') {
                removeItem(id);
                toast('Translation recovered — approved automatically', 'success');
            } else {
                toast('Retry succeeded — moved to review', 'success');
                await load(true);
            }
        } catch (e) {
            toast(e.message || 'Retry failed', 'error');
            setBusy(btn, false);
        }
    }

    function cssEsc(s) {
        // Selectors built from IDs must escape special characters; data attribute
        // values can contain hyphens that querySelector copes with, but we still
        // sanitise just in case.
        return String(s).replace(/"/g, '\\"');
    }

    // ── Actions ──────────────────────────────────────────────────────────────
    async function lock(id) {
        const r = await tlFetch(`${API}/${encodeURIComponent(id)}/lock`, { method: 'POST' });
        if (r.status === 409) return toast('Already claimed by another reviewer', 'error');
        if (!r.ok) return toast('Could not claim review', 'error');
        await refreshOne(id, { lockedAt: Date.now(), lockedBy: 'you' });
    }

    async function unlock(id) {
        const r = await tlFetch(`${API}/${encodeURIComponent(id)}/unlock`, { method: 'POST' });
        if (!r.ok) return toast('Could not release', 'error');
        await refreshOne(id, { lockedAt: null, lockedBy: null });
    }

    async function approve(id, btn) {
        const item = state.items.find(i => i.id === id);
        if (!item) return;
        const edited = state.editedTexts[id];
        const changed = edited != null && edited.trim() !== item.translatedText.trim();
        const isHotfixCandidate = item.lockedAt; // approving a locked card after edit can be a hotfix
        setBusy(btn, true);
        try {
            const body = changed ? { editedText: edited } : {};
            const r = await tlFetch(`${API}/${encodeURIComponent(id)}/approve`, {
                method: 'POST',
                body: JSON.stringify(body),
            });
            if (!r.ok) throw new Error('approve failed');
            removeItem(id);
            toast(changed ? 'Approved with edits' : 'Approved', 'success');
        } catch (err) {
            toast('Approve failed', 'error');
            setBusy(btn, false);
        }
    }

    async function approveGroup(ids, btn) {
        if (!ids.length) return;
        if (!confirm(`Approve all ${ids.length} translations in this commit?`)) return;
        setBusy(btn, true);
        try {
            const r = await tlFetch(`${API}/batch-approve`, {
                method: 'POST',
                body: JSON.stringify({ ids }),
            });
            if (!r.ok) throw new Error('batch failed');
            const { approved, total } = await r.json();
            ids.forEach(removeItem);
            toast(`Approved ${approved} of ${total}`, 'success');
        } catch {
            toast('Batch approve failed', 'error');
            setBusy(btn, false);
        }
    }

    function openReject(id) {
        // Close any other open panel.
        if (state.rejecting && state.rejecting !== id) cancelReject(state.rejecting);
        if (state.hotfixing) cancelHotfix(state.hotfixing);
        state.rejecting = id;
        const panel = document.querySelector(`[data-reject-panel="${cssEsc(id)}"]`);
        panel?.classList.add('open');
        panel?.querySelector('[data-reject-text]')?.focus();
    }

    function cancelReject(id) {
        state.rejecting = null;
        const panel = document.querySelector(`[data-reject-panel="${cssEsc(id)}"]`);
        panel?.classList.remove('open');
    }

    // ── Hotfix ───────────────────────────────────────────────────────────────
    function openHotfix(id) {
        // Close any other open confirm/reject panel so only one is visible at a time.
        if (state.rejecting) cancelReject(state.rejecting);
        if (state.hotfixing && state.hotfixing !== id) cancelHotfix(state.hotfixing);
        state.hotfixing = id;
        const panel = document.querySelector(`[data-hotfix-panel="${cssEsc(id)}"]`);
        panel?.classList.add('open');
        panel?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    }

    function cancelHotfix(id) {
        if (state.hotfixing === id) state.hotfixing = null;
        const panel = document.querySelector(`[data-hotfix-panel="${cssEsc(id)}"]`);
        panel?.classList.remove('open');
    }

    async function confirmHotfix(id, btn) {
        const item = state.items.find(i => i.id === id);
        if (!item) return;
        if (!item.projectOtaEnabled) {
            return toast('Project OTA is disabled — enable it in project settings first', 'error');
        }
        const text = state.editedTexts[id] != null ? state.editedTexts[id] : item.translatedText;
        if (!text.trim()) {
            return toast('Translation cannot be empty', 'error');
        }
        setBusy(btn, true);
        try {
            const r = await tlFetch(`${API}/${encodeURIComponent(id)}/hotfix`, {
                method: 'POST',
                body: JSON.stringify({ newText: text }),
            });
            if (!r.ok) {
                const err = await r.json().catch(() => ({}));
                if (r.status === 409) throw new Error(err.error || 'Enable OTA on the project before hotfixing');
                if (r.status === 503) throw new Error('CDN publish service is unavailable right now');
                throw new Error(err.error || 'Hotfix failed');
            }
            const resp = await r.json();
            state.hotfixing = null;
            removeItem(id);
            const publish = resp.publish;
            if (publish) {
                const localesLabel = `${publish.locales.length} locale${publish.locales.length === 1 ? '' : 's'}`;
                if (publish.skipped) {
                    toast(`Hotfix saved — CDN publish skipped (no change in bundle)`, 'success');
                } else if (publish.promoted) {
                    toast(`Hotfix live: ${publish.bundleVersion} (${localesLabel})`, 'success');
                } else {
                    toast(`Hotfix published: ${publish.bundleVersion} — promote manually in Projects to go live`, 'success');
                }
            } else {
                // hotfix() succeeded but the publish step crashed and was swallowed by
                // the server; the text is saved but not yet on the CDN.
                toast('Hotfix saved, but CDN publish failed — retry from project sync', 'error');
            }
        } catch (err) {
            toast(err.message || 'Hotfix failed', 'error');
            setBusy(btn, false);
        }
    }

    async function confirmReject(id, btn) {
        const text = document.querySelector(`[data-reject-text="${cssEsc(id)}"]`)?.value?.trim();
        if (!text) return toast('Add a reason so the next run can avoid this translation', 'error');
        setBusy(btn, true);
        try {
            const r = await tlFetch(`${API}/${encodeURIComponent(id)}/reject`, {
                method: 'POST',
                body: JSON.stringify({ reason: text }),
            });
            if (!r.ok) throw new Error();
            removeItem(id);
            state.rejecting = null;
            toast('Rejected — next run will pick this up', 'success');
        } catch {
            toast('Reject failed', 'error');
            setBusy(btn, false);
        }
    }

    // ── State mutation helpers ───────────────────────────────────────────────
    function removeItem(id) {
        state.items = state.items.filter(i => i.id !== id);
        delete state.editedTexts[id];
        renderStats();
        renderList();
    }

    async function refreshOne(id, patch) {
        const idx = state.items.findIndex(i => i.id === id);
        if (idx === -1) return;
        state.items[idx] = { ...state.items[idx], ...patch };
        renderStats();
        renderList();
    }

    function setBusy(btn, busy) {
        if (!btn) return;
        if (busy) {
            btn.disabled = true;
            btn.dataset.prevHtml = btn.innerHTML;
            btn.innerHTML = '<span class="bl-spin" style="width:11px;height:11px"></span>';
        } else {
            btn.disabled = false;
            if (btn.dataset.prevHtml) btn.innerHTML = btn.dataset.prevHtml;
        }
    }

    function debounce(fn, ms) {
        let t;
        return function () {
            const args = arguments;
            clearTimeout(t);
            t = setTimeout(() => fn.apply(this, args), ms);
        };
    }
})();
