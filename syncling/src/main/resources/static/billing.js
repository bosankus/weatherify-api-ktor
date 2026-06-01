/**
 * Billing page client.
 *
 * Loads in three independent waves (subscription, usage, invoices) so a single
 * slow endpoint can't blank the page. Each loader has its own try/catch and
 * empty-state. All API calls go through window.tlFetch (defined in PortalShell).
 */
(function () {
    if (!window.tlFetch) {
        console.error('billing.js: portal shell not initialised (no window.tlFetch)');
        return;
    }

    const API = '/api/billing';
    const PLAN_PICKER_ID = 'bl-plan-picker';

    // ── Plan catalog (mirror of BillingPlan.kt — kept here so the upgrade
    //    modal can render without an extra round-trip). If a new plan is added
    //    server-side, append it here too. The server enforces validity.
    const PLANS = {
        SOLO: {
            displayName: 'Solo',
            monthlyPaise: 49900,
            stringLimit: 5000,
            maxProjects: 3,
            features: ['5,000 strings/month', '3 projects', 'Unlimited languages'],
        },
        TEAM: {
            displayName: 'Team',
            monthlyPaise: 199900,
            stringLimit: null,
            maxProjects: 10,
            features: ['Unlimited strings', '10 projects', 'Unlimited languages', 'Priority support'],
        },
    };

    // ── Utility helpers ──────────────────────────────────────────────────────
    const $ = id => document.getElementById(id);
    const fmtRupees = paise => '₹' + (paise / 100).toLocaleString('en-IN', { maximumFractionDigits: 0 });
    const fmtInt = n => n == null ? '—' : n.toLocaleString('en-IN');
    const esc = s => {
        const d = document.createElement('div');
        d.textContent = String(s == null ? '' : s);
        return d.innerHTML;
    };
    const fmtDate = iso => {
        if (!iso) return '—';
        try {
            return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
        } catch { return iso; }
    };
    const fmtMonth = ym => {
        // "2026-05" → "May"
        if (!ym || ym.length < 7) return ym || '';
        const [, m] = ym.split('-');
        return ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][parseInt(m, 10) - 1] || ym;
    };

    let currentSub = null;   // last SubscriptionResponse
    let currentUsage = null; // last UsageResponse

    // ── Banner ───────────────────────────────────────────────────────────────
    function showBanner(kind, title, msg, actions) {
        const b = $('bl-banner');
        if (!b) return;
        const acts = (actions || []).map(a =>
            `<button type="button" class="bl-btn ${esc(a.kind || '')}" data-banner-act="${esc(a.id)}">${esc(a.label)}</button>`
        ).join('');
        b.innerHTML =
            `<div class="bl-banner-icon">${kind === 'success'
                ? '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>'
                : '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="13"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>'}</div>` +
            `<div class="bl-banner-body">` +
            `<div class="bl-banner-title">${esc(title)}</div>` +
            `<div class="bl-banner-msg">${esc(msg)}</div>` +
            (acts ? `<div class="bl-banner-actions">${acts}</div>` : '') +
            `</div>` +
            `<button type="button" class="bl-banner-close" data-banner-act="dismiss" aria-label="Dismiss">×</button>`;
        b.className = 'bl-banner ' + kind + ' show';
    }

    function hideBanner() {
        const b = $('bl-banner');
        if (b) b.className = 'bl-banner';
    }

    document.addEventListener('click', e => {
        const t = e.target.closest('[data-banner-act]');
        if (!t) return;
        const act = t.dataset.bannerAct;
        if (act === 'dismiss') hideBanner();
        else if (act === 'dismiss-limit') dismissLimit();
        else if (act === 'upgrade') openPlanPicker();
    });

    // ── Subscription loader ──────────────────────────────────────────────────
    async function loadSubscription() {
        try {
            const res = await tlFetch(API + '/subscription');
            if (!res.ok) throw new Error('status ' + res.status);
            const sub = await res.json();
            currentSub = sub;
            renderPlan(sub);
            maybeShowContextualBanner(sub);
        } catch (err) {
            $('bl-plan-status').textContent = 'Unavailable';
            $('bl-plan-name').textContent = 'Could not load plan';
            console.warn('billing: subscription load failed', err);
        }
    }

    function renderPlan(sub) {
        const isFree = sub.plan === 'FREE';
        const isPaid = !isFree && sub.plan !== 'ENTERPRISE';

        $('bl-plan-name').textContent = sub.displayName || sub.plan;

        // Status pill
        const pill = $('bl-plan-status');
        if (sub.cancelAtPeriodEnd) {
            pill.className = 'bl-pill bl-pill-cancelling';
            pill.textContent = 'Cancelling';
        } else if (sub.inTrial) {
            pill.className = 'bl-pill bl-pill-trial';
            pill.textContent = 'Trial';
        } else if (isFree) {
            pill.className = 'bl-pill bl-pill-muted';
            pill.textContent = 'Free';
        } else {
            pill.className = 'bl-pill bl-pill-active';
            pill.textContent = 'Active';
        }

        // Price
        if (sub.monthlyPricePaise) {
            $('bl-plan-price-amount').textContent = fmtRupees(sub.monthlyPricePaise);
            $('bl-plan-price-period').textContent = '/month';
        } else if (sub.plan === 'ENTERPRISE') {
            $('bl-plan-price-amount').textContent = 'Custom';
            $('bl-plan-price-period').textContent = '';
        } else {
            $('bl-plan-price-amount').textContent = 'Free';
            $('bl-plan-price-period').textContent = '';
        }

        // Renewal / trial / projects
        $('bl-plan-renewal').textContent = sub.currentPeriodEnd
            ? fmtDate(sub.currentPeriodEnd)
            : sub.inTrial
                ? 'After trial'
                : isFree
                    ? 'No renewal'
                    : sub.plan === 'ENTERPRISE'
                        ? 'Custom billing'
                        : '—';
        $('bl-plan-trial').textContent = sub.trialEndsOn
            ? fmtDate(sub.trialEndsOn)
            : isFree
                ? 'Not in trial'
                : isPaid
                    ? 'Trial ended'
                    : '—';
        renderPlanProjects();

        // Action buttons
        const actions = $('bl-plan-actions');
        const buttons = [];
        if (isFree) {
            buttons.push({ id: 'upgrade', label: 'Upgrade plan', primary: true });
        } else if (sub.cancelAtPeriodEnd) {
            buttons.push({ id: 'upgrade', label: 'Change plan' });
        } else {
            if (sub.inTrial) buttons.push({ id: 'activate', label: 'End trial & start billing' });
            buttons.push({ id: 'upgrade', label: 'Change plan' });
            if (isPaid) buttons.push({ id: 'cancel', label: 'Cancel plan', kind: 'danger' });
        }
        actions.innerHTML = buttons.map(b =>
            `<button type="button" class="bl-btn ${b.primary ? 'primary' : ''} ${b.kind || ''}" data-plan-act="${b.id}">${esc(b.label)}</button>`
        ).join('');

        // Header CTA
        $('bl-header-cta').innerHTML = isFree
            ? `<button type="button" class="bl-btn primary" data-plan-act="upgrade">Upgrade plan</button>`
            : '';
    }

    document.addEventListener('click', e => {
        const t = e.target.closest('[data-plan-act]');
        if (!t) return;
        const act = t.dataset.planAct;
        if (act === 'upgrade') openPlanPicker();
        else if (act === 'activate') activateNow(t);
        else if (act === 'cancel') cancelSubscription(t);
    });

    function maybeShowContextualBanner(sub) {
        if (sub.trialLimitHit) {
            showBanner('warn', 'Trial limit reached',
                'You\'ve hit the trial usage limit. Translations are paused until you activate your subscription.',
                [
                    { id: 'activate', label: 'Activate now', kind: 'primary' },
                    { id: 'dismiss-limit', label: 'Dismiss' },
                ]);
        } else if (sub.cancelAtPeriodEnd && sub.currentPeriodEnd) {
            showBanner('warn', 'Subscription cancelling',
                `Your plan will end on ${fmtDate(sub.currentPeriodEnd)}. You can resume any time before then.`);
        } else if (sub.inTrial && sub.daysUntilRenewal != null && sub.daysUntilRenewal <= 2) {
            showBanner('warn', 'Trial ending soon',
                `Your free trial ends in ${sub.daysUntilRenewal} day${sub.daysUntilRenewal === 1 ? '' : 's'}.`);
        }
    }

    async function dismissLimit() {
        try { await tlFetch(API + '/dismiss-limit', { method: 'POST' }); } catch {}
        hideBanner();
    }

    // ── Usage loader ─────────────────────────────────────────────────────────
    async function loadUsage() {
        try {
            const res = await tlFetch(API + '/usage');
            if (!res.ok) throw new Error('status ' + res.status);
            currentUsage = await res.json();
            renderUsage(currentUsage);
        } catch (err) {
            $('bl-usage-strings').textContent = '—';
            $('bl-usage-projects').textContent = '—';
            $('bl-usage-spark').innerHTML = '<div class="bl-invoice-empty">Unable to load usage</div>';
            console.warn('billing: usage load failed', err);
        }
    }

    function renderPlanProjects() {
        const el = $('bl-plan-projects');
        if (!el) return;
        const sub = currentSub;
        const u = currentUsage;
        if (!sub) { el.textContent = '—'; return; }
        const unlimited = sub.maxProjects === -1;
        if (u != null && typeof u.projectsUsed === 'number') {
            el.textContent = unlimited
                ? `${fmtInt(u.projectsUsed)} (unlimited)`
                : `${fmtInt(u.projectsUsed)} / ${fmtInt(sub.maxProjects)}`;
        } else {
            el.textContent = unlimited ? 'Unlimited' : `0 / ${fmtInt(sub.maxProjects)}`;
        }
    }

    function renderUsage(u) {
        renderPlanProjects();
        // Strings
        const strLimit = u.stringLimit;
        $('bl-usage-strings').textContent = strLimit
            ? `${fmtInt(u.stringsTranslated)} / ${fmtInt(strLimit)}`
            : `${fmtInt(u.stringsTranslated)} (unlimited)`;
        renderBar('bl-usage-strings-bar', u.stringsTranslated, strLimit);
        $('bl-usage-strings-hint').textContent = strLimit
            ? `${pct(u.stringsTranslated, strLimit)}% of quota used`
            : 'No quota on your current plan';

        // Projects
        const projLimit = u.projectLimit;
        $('bl-usage-projects').textContent = projLimit
            ? `${fmtInt(u.projectsUsed)} / ${fmtInt(projLimit)}`
            : `${fmtInt(u.projectsUsed)} (unlimited)`;
        renderBar('bl-usage-projects-bar', u.projectsUsed, projLimit);
        $('bl-usage-projects-hint').textContent = projLimit
            ? `${pct(u.projectsUsed, projLimit)}% of project slots used`
            : 'No project cap on your current plan';

        // Sparkline
        renderSpark(u.history || []);

        // Period label
        const now = new Date();
        $('bl-usage-period').textContent = now.toLocaleDateString('en-IN', { month: 'long', year: 'numeric' });
    }

    function pct(n, limit) {
        if (!limit) return 0;
        return Math.min(100, Math.round((n / limit) * 100));
    }

    function renderBar(id, value, limit) {
        const el = $(id);
        if (!el) return;
        const p = limit ? pct(value, limit) : Math.min(100, value > 0 ? 30 : 0);
        el.style.width = p + '%';
        el.classList.remove('near-limit', 'over-limit');
        if (limit) {
            if (value >= limit) el.classList.add('over-limit');
            else if (value / limit > 0.8) el.classList.add('near-limit');
        }
    }

    function renderSpark(history) {
        const host = $('bl-usage-spark');
        const statsHost = $('bl-usage-spark-stats');
        if (!host) return;
        const currentYm = new Date().toISOString().slice(0, 7);

        // Build a 6-slot window ending at current month so the axis is always 6 months wide.
        const byMonth = new Map((history || []).map(h => [h.month, h.count || 0]));
        const slots = [];
        const now = new Date();
        for (let i = 5; i >= 0; i--) {
            const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
            const ym = d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0');
            slots.push({ month: ym, count: byMonth.get(ym) || 0 });
        }

        const total = slots.reduce((s, h) => s + h.count, 0);
        if (statsHost) {
            if (total === 0) {
                statsHost.innerHTML = '';
            } else {
                const peak = slots.reduce((a, b) => b.count > a.count ? b : a, slots[0]);
                statsHost.innerHTML =
                    `<span class="bl-spark-stat"><b>${fmtInt(total)}</b> total</span>` +
                    `<span class="bl-spark-stat-sep">·</span>` +
                    `<span class="bl-spark-stat">Peak <b>${fmtInt(peak.count)}</b> in ${esc(fmtMonth(peak.month))}</span>`;
            }
        }

        if (total === 0) {
            host.innerHTML = '<div class="bl-spark-empty">No translation activity in the last 6 months yet. Charts will populate as you translate strings.</div>';
            return;
        }

        const max = Math.max(1, ...slots.map(h => h.count));
        const cols = slots.map(h => {
            const heightPct = h.count === 0 ? 0 : Math.max(6, Math.round((h.count / max) * 100));
            const isCur = h.month === currentYm ? 'true' : 'false';
            const isZero = h.count === 0 ? 'true' : 'false';
            return `<div class="bl-spark-col" data-current="${isCur}">` +
                   `<div class="bl-spark-col-value">${fmtInt(h.count)}</div>` +
                   `<div class="bl-spark-col-track">` +
                       `<div class="bl-spark-col-bar" data-current="${isCur}" data-zero="${isZero}" style="height:${heightPct}%"></div>` +
                   `</div>` +
                   `<div class="bl-spark-col-month">${esc(fmtMonth(h.month))}</div>` +
                   `</div>`;
        }).join('');
        host.innerHTML = `<div class="bl-spark-grid">${cols}</div>`;
    }

    // ── Invoices loader ──────────────────────────────────────────────────────
    async function loadInvoices() {
        try {
            const res = await tlFetch(API + '/invoices');
            if (!res.ok) throw new Error('status ' + res.status);
            const { invoices = [] } = await res.json();
            renderInvoices(invoices);
        } catch (err) {
            $('bl-invoice-list').innerHTML = '<div class="bl-invoice-empty">Unable to load invoices</div>';
            console.warn('billing: invoices load failed', err);
        }
    }

    function renderInvoices(invoices) {
        const host = $('bl-invoice-list');
        if (!host) return;
        if (!invoices.length) {
            host.innerHTML = '<div class="bl-invoice-empty">No invoices yet — your first charge will appear here after your trial ends.</div>';
            return;
        }
        host.innerHTML = invoices.map(inv => {
            const statusKey = (inv.status || '').toLowerCase();
            return `<div class="bl-invoice-row">` +
                `<span data-label="Date">${esc(fmtDate(inv.date))}</span>` +
                `<span class="bl-invoice-ref" data-label="Reference">${esc(inv.id)}</span>` +
                `<span class="bl-invoice-amount" data-label="Amount">${esc(inv.amount)}</span>` +
                `<span class="bl-invoice-status ${esc(statusKey)}" data-label="Status">${esc(inv.status)}</span>` +
                `<a href="/api/billing/invoices/${encodeURIComponent(inv.id)}/receipt" target="_blank" rel="noopener">View</a>` +
                `</div>`;
        }).join('');
    }

    // ── Plan picker modal ────────────────────────────────────────────────────
    function openPlanPicker() {
        closePlanPicker();
        const currentPlan = currentSub ? currentSub.plan : 'FREE';
        const overlay = document.createElement('div');
        overlay.id = PLAN_PICKER_ID;
        overlay.className = 'bl-modal-overlay';
        overlay.innerHTML = `
            <div class="bl-modal" role="dialog" aria-modal="true" aria-labelledby="bl-picker-title">
                <h3 id="bl-picker-title">Choose a plan</h3>
                <p>Start with a 7-day free trial. Cancel any time before the trial ends and you won't be charged.</p>
                <div class="bl-modal-plans">
                    ${Object.entries(PLANS).map(([key, p]) => {
                        const isCurrent = key === currentPlan;
                        return `<div class="bl-modal-plan ${isCurrent ? '' : ''}" data-picker-plan="${esc(key)}">
                            <div class="bl-modal-plan-name">${esc(p.displayName)}</div>
                            <div class="bl-modal-plan-price">${fmtRupees(p.monthlyPaise)} <span style="color:var(--text-muted)">/month</span></div>
                            <div class="bl-modal-plan-features">${p.features.map(f => '• ' + esc(f)).join('<br>')}</div>
                            ${isCurrent ? '<div class="bl-modal-plan-features" style="margin-top:8px;color:var(--accent);font-weight:600">Current plan</div>' : ''}
                        </div>`;
                    }).join('')}
                </div>
                <div class="bl-modal-actions">
                    <button type="button" class="bl-btn" data-picker-act="close">Cancel</button>
                    <button type="button" class="bl-btn primary" data-picker-act="start" disabled>Start trial</button>
                </div>
            </div>`;
        document.body.appendChild(overlay);
        requestAnimationFrame(() => overlay.classList.add('show'));

        let selectedPlan = null;
        overlay.addEventListener('click', e => {
            if (e.target === overlay) return closePlanPicker();
            const picker = e.target.closest('[data-picker-plan]');
            if (picker) {
                const plan = picker.dataset.pickerPlan;
                if (plan === currentPlan) return;
                selectedPlan = plan;
                overlay.querySelectorAll('[data-picker-plan]').forEach(el => el.classList.remove('selected'));
                picker.classList.add('selected');
                overlay.querySelector('[data-picker-act="start"]').disabled = false;
                return;
            }
            const actBtn = e.target.closest('[data-picker-act]');
            if (!actBtn) return;
            const act = actBtn.dataset.pickerAct;
            if (act === 'close') closePlanPicker();
            else if (act === 'start' && selectedPlan) startSubscription(selectedPlan, actBtn);
        });

        document.addEventListener('keydown', escClose);
    }

    function escClose(e) { if (e.key === 'Escape') closePlanPicker(); }
    function closePlanPicker() {
        const el = $(PLAN_PICKER_ID);
        if (el) el.remove();
        document.removeEventListener('keydown', escClose);
    }

    // ── Razorpay checkout flow ───────────────────────────────────────────────
    async function startSubscription(planKey, btn) {
        setBusy(btn, true, 'Preparing checkout');
        let init;
        try {
            const res = await tlFetch(API + '/subscribe', {
                method: 'POST',
                body: JSON.stringify({ plan: planKey }),
            });
            if (!res.ok) {
                const err = await res.json().catch(() => ({}));
                throw new Error(err.message || 'Failed to create subscription');
            }
            init = await res.json();
        } catch (err) {
            setBusy(btn, false, 'Start trial');
            toast(err.message || 'Could not start checkout', 'error');
            return;
        }

        if (!window.Razorpay) {
            setBusy(btn, false, 'Start trial');
            toast('Razorpay failed to load. Refresh and try again.', 'error');
            return;
        }

        const rzp = new window.Razorpay({
            key: init.keyId,
            subscription_id: init.subscriptionId,
            name: 'Syncling',
            description: PLANS[init.plan].displayName + ' — 7-day free trial',
            theme: { color: '#8B7EFF' },
            handler: function (resp) { confirmPayment(resp); },
            modal: { ondismiss: function () { setBusy(btn, false, 'Start trial'); } },
        });
        rzp.on('payment.failed', resp => {
            setBusy(btn, false, 'Start trial');
            const reason = resp.error?.description || resp.error?.reason || 'Payment failed. Please try again.';
            const code = resp.error?.code ? ` (${resp.error.code})` : '';
            toast(reason + code, 'error');
        });
        rzp.open();
    }

    async function confirmPayment(resp) {
        try {
            const r = await tlFetch(API + '/confirm-payment', {
                method: 'POST',
                body: JSON.stringify({
                    paymentId: resp.razorpay_payment_id,
                    subscriptionId: resp.razorpay_subscription_id,
                    signature: resp.razorpay_signature,
                }),
            });
            if (!r.ok) throw new Error('Verification failed');
            const data = await r.json();
            closePlanPicker();
            toast(`Welcome to ${data.displayName || data.plan}!`, 'success');
            showBanner('success', 'Subscription active',
                'Your trial has started. You won\'t be charged until it ends.');
            // Reload subscription + usage to reflect the new plan.
            loadSubscription();
            loadUsage();
        } catch (err) {
            toast('Payment verified but plan sync failed — refresh in a moment.', 'error');
            console.warn('confirm-payment failed', err);
        }
    }

    // ── Activate-now / Cancel ────────────────────────────────────────────────
    async function activateNow(btn) {
        if (!confirm('End your free trial and start billing immediately?')) return;
        setBusy(btn, true, 'Activating');
        try {
            const r = await tlFetch(API + '/activate-now', { method: 'POST' });
            if (!r.ok) {
                const err = await r.json().catch(() => ({}));
                throw new Error(err.message || 'Activation failed');
            }
            toast('Plan activated', 'success');
            hideBanner();
            await loadSubscription();
        } catch (err) {
            toast(err.message, 'error');
        } finally {
            setBusy(btn, false, 'End trial & start billing');
        }
    }

    async function cancelSubscription(btn) {
        if (!confirm('Cancel your subscription at the end of the current billing period?')) return;
        setBusy(btn, true, 'Cancelling');
        try {
            const r = await tlFetch(API + '/cancel', { method: 'POST' });
            if (!r.ok) {
                const err = await r.json().catch(() => ({}));
                throw new Error(err.message || 'Cancel failed');
            }
            toast('Subscription will cancel at period end', 'success');
            await loadSubscription();
        } catch (err) {
            toast(err.message, 'error');
        } finally {
            setBusy(btn, false, 'Cancel plan');
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

    // ── Boot ─────────────────────────────────────────────────────────────────
    loadSubscription();
    loadUsage();
    loadInvoices();
})();
