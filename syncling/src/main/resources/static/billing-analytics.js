/* eslint-env browser */
/**
 * Analytics page hydration. Pulls from /syncling/api/analytics/{...} and renders
 * into the skeleton DOM that BillingAnalyticsApp.kt server-renders.
 *
 * Range switching re-fetches the range-dependent endpoints only (overview,
 * projects, locales, runs, members). Quality and cost-breakdown are point-in-time
 * snapshots and are fetched once on load.
 *
 * Plan gating: a 403 from /overview means FREE — we hide all metric sections and
 * show the soft upgrade banner. Solo gets every section EXCEPT Members and Cost
 * breakdown (those are hidden on plan != "TEAM"). Team owners see everything.
 */
(function () {
  'use strict';

  var STATE = { range: '30d', plan: null };
  var RUNS_PAG = { rows: [], page: 1, pageSize: 10 };

  // ── Utilities ─────────────────────────────────────────────────────────────
  function $(id) { return document.getElementById(id); }
  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }
  function fmtR(n) {
    if (n == null) return '—';
    return '₹' + (n >= 10 ? n.toFixed(0) : n.toFixed(2));
  }
  function fmtInt(n) {
    if (n == null) return '—';
    return n.toLocaleString();
  }
  function fmtAgo(ms) {
    if (!ms) return '—';
    var d = Math.floor((Date.now() - ms) / 86400000);
    if (d < 1) return 'today';
    if (d < 2) return 'yesterday';
    if (d < 30) return d + ' days ago';
    var m = Math.floor(d / 30);
    return m + ' month' + (m === 1 ? '' : 's') + ' ago';
  }
  function fmtMs(ms) {
    if (!ms) return '—';
    if (ms < 1000) return ms + 'ms';
    var s = (ms / 1000).toFixed(1);
    if (ms < 60000) return s + 's';
    return Math.round(ms / 60000) + 'm';
  }
  function fmtDate(ms) {
    if (!ms) return '—';
    return new Date(ms).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  }

  function api(path) {
    return window.tlFetch(path).then(function (r) {
      if (r.status === 403) return { _forbidden: true };
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    });
  }

  function setEmpty(host, msg) {
    host.innerHTML = '';
    host.appendChild(el('div', 'bla-empty', msg));
  }

  // ── Sections ──────────────────────────────────────────────────────────────

  function renderOverview(data) {
    STATE.plan = data.plan;

    // Apply plan gating for Team-only sections
    var teamOnlyVisible = data.plan === 'TEAM' || data.plan === 'ENTERPRISE';
    document.querySelectorAll('[data-team-only]').forEach(function (s) {
      s.style.display = teamOnlyVisible ? '' : 'none';
    });

    // Tracking-since banner
    var banner = $('bla-tracking-banner');
    if (!data.trackingSinceMillis) {
      banner.className = 'bla-tracking-banner warn';
      banner.textContent = 'No pipeline runs recorded yet — metrics will populate as soon as you trigger your first translation.';
    } else {
      var since = new Date(data.trackingSinceMillis).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
      banner.className = 'bla-tracking-banner';
      banner.textContent = 'Per-member tracking active since ' + since + '. Activity before this date is not attributed individually.';
    }

    // Cost/string + month strings + delta + projected
    $('bla-cps').textContent = data.costPerStringInr != null ? fmtR(data.costPerStringInr) : '—';
    $('bla-cps-month-strings').textContent = fmtInt(data.stringsTranslatedThisMonth);
    $('bla-cps-month-sub').textContent = 'strings translated';

    var deltaEl = $('bla-cps-delta');
    var deltaSub = $('bla-cps-delta-sub');
    if (data.costPerStringInr != null && data.lastMonthCostPerStringInr != null && data.lastMonthCostPerStringInr > 0) {
      var pct = ((data.costPerStringInr - data.lastMonthCostPerStringInr) / data.lastMonthCostPerStringInr) * 100;
      var dir = pct <= 0 ? 'down' : 'up';
      var arrow = pct <= 0 ? '▼' : '▲';
      deltaEl.textContent = arrow + ' ' + Math.abs(pct).toFixed(0) + '%';
      deltaEl.parentElement.querySelector('.bla-metric-sub').className = 'bla-metric-sub ' + dir;
      deltaSub.textContent = 'was ' + fmtR(data.lastMonthCostPerStringInr) + ' last month';
    } else {
      deltaEl.textContent = '—';
      deltaSub.textContent = 'no comparable last month';
    }

    if (data.projectedEndOfMonthCostInr != null) {
      $('bla-cps-projected').textContent = fmtR(data.projectedEndOfMonthCostInr);
      $('bla-cps-projected-sub').textContent = 'at current pace';
    } else {
      $('bla-cps-projected').textContent = '—';
      $('bla-cps-projected-sub').textContent = 'available after day 5';
    }

    // Sparkline
    var spark = $('bla-spark');
    spark.innerHTML = '';
    var max = Math.max.apply(null, data.sparkline.map(function (p) { return p.strings; }).concat([1]));
    var currentMonth = new Date().toISOString().slice(0, 7);
    data.sparkline.forEach(function (p) {
      var col = el('div', 'bla-spark-col' + (p.month === currentMonth ? ' current' : ''));
      var pct = (p.strings / max) * 100;
      var bar = el('div', 'bla-spark-bar');
      bar.style.height = Math.max(2, pct) + '%';
      bar.title = p.month + ': ' + fmtInt(p.strings) + ' strings';
      col.appendChild(bar);
      col.appendChild(el('div', 'bla-spark-label', p.month.slice(5)));
      spark.appendChild(col);
    });
  }

  function showPlanGate(forbidden) {
    if (!forbidden) return false;
    $('bla-plan-gate').style.display = '';
    document.querySelectorAll('.bl-card').forEach(function (s) { s.style.display = 'none'; });
    $('bla-tracking-banner').style.display = 'none';
    return true;
  }

  function renderProjects(rows) {
    var host = $('bla-projects-body');
    if (!rows || rows.length === 0) { setEmpty(host, 'No projects with activity in this range.'); return; }
    var t = el('table', 'bla-table');
    t.innerHTML =
      '<thead><tr><th>Project</th><th class="bla-num">Strings</th><th class="bla-num">Runs</th>' +
      '<th class="bla-num">Locales</th><th class="bla-num">Acceptance</th><th>Last run</th></tr></thead>';
    var tb = el('tbody');
    rows.forEach(function (r) {
      var tr = el('tr');
      tr.innerHTML =
        '<td>' + escapeHtml(r.name) + '</td>' +
        '<td class="bla-num">' + fmtInt(r.stringsTranslated) + '</td>' +
        '<td class="bla-num">' + fmtInt(r.runs) + '</td>' +
        '<td class="bla-num">' + fmtInt(r.locales) + '</td>' +
        '<td class="bla-num ' + acceptanceClass(r.acceptanceRatePct) + '">' + (r.acceptanceRatePct != null ? r.acceptanceRatePct + '%' : '—') + '</td>' +
        '<td class="dim">' + fmtAgo(r.lastRunMillis) + '</td>';
      tb.appendChild(tr);
    });
    t.appendChild(tb);
    host.innerHTML = ''; host.appendChild(t);
    $('bla-projects-count').textContent = rows.length + ' project' + (rows.length === 1 ? '' : 's');
  }

  function renderLocales(rows) {
    var host = $('bla-locales-body');
    if (!rows || rows.length === 0) { setEmpty(host, 'No locale activity in this range.'); return; }
    var t = el('table', 'bla-table');
    t.innerHTML = '<thead><tr><th>Locale</th><th class="bla-num">Strings translated</th><th class="bla-num">Projects</th></tr></thead>';
    var tb = el('tbody');
    rows.forEach(function (r) {
      var tr = el('tr');
      tr.innerHTML =
        '<td>' + escapeHtml(r.locale) + '</td>' +
        '<td class="bla-num">' + fmtInt(r.stringsTranslated) + '</td>' +
        '<td class="bla-num">' + fmtInt(r.projectsCount) + '</td>';
      tb.appendChild(tr);
    });
    t.appendChild(tb);
    host.innerHTML = ''; host.appendChild(t);
  }

  function renderQuality(rows) {
    var host = $('bla-quality-body');
    if (!rows || rows.length === 0) { setEmpty(host, 'No quality data yet.'); return; }
    var t = el('table', 'bla-table');
    t.innerHTML =
      '<thead><tr><th>Project</th><th class="bla-num">Auto-approved</th><th class="bla-num">In review</th>' +
      '<th class="bla-num">Blocked</th><th class="bla-num">Acceptance</th></tr></thead>';
    var tb = el('tbody');
    rows.forEach(function (r) {
      var tr = el('tr');
      tr.innerHTML =
        '<td>' + escapeHtml(r.name) + '</td>' +
        '<td class="bla-num">' + fmtInt(r.auto) + '</td>' +
        '<td class="bla-num">' + fmtInt(r.review) + '</td>' +
        '<td class="bla-num">' + fmtInt(r.blocked) + '</td>' +
        '<td class="bla-num ' + acceptanceClass(r.acceptanceRatePct) + '">' + (r.acceptanceRatePct != null ? r.acceptanceRatePct + '%' : '—') + '</td>';
      tb.appendChild(tr);
    });
    t.appendChild(tb);
    host.innerHTML = ''; host.appendChild(t);
  }

  function renderRuns(rows) {
    RUNS_PAG.rows = rows || [];
    RUNS_PAG.page = 1;
    renderRunsView();
  }

  function renderRunsView() {
    var allRows = RUNS_PAG.rows;
    var pageSize = RUNS_PAG.pageSize;
    var page = RUNS_PAG.page;
    var total = allRows.length;
    var totalPages = Math.max(1, Math.ceil(total / pageSize));
    if (page > totalPages) page = RUNS_PAG.page = totalPages;

    var start = (page - 1) * pageSize;
    var end = Math.min(start + pageSize, total);
    var pageRows = allRows.slice(start, end);

    // Toolbar: "Show X entries" select on left, "Showing X–Y of Z" on right
    var toolbar = $('bla-runs-toolbar');
    toolbar.innerHTML = '';
    var leftDiv = el('div', 'bla-runs-toolbar-left');
    var label = document.createElement('label');
    label.className = 'bla-page-size-label';
    label.appendChild(document.createTextNode('Show '));
    var sel = document.createElement('select');
    sel.className = 'bla-page-size-select';
    [10, 20, 50].forEach(function (n) {
      var opt = document.createElement('option');
      opt.value = n;
      opt.textContent = n;
      if (n === pageSize) opt.selected = true;
      sel.appendChild(opt);
    });
    sel.addEventListener('change', function () {
      RUNS_PAG.pageSize = parseInt(sel.value, 10);
      RUNS_PAG.page = 1;
      renderRunsView();
    });
    label.appendChild(sel);
    label.appendChild(document.createTextNode(' entries'));
    leftDiv.appendChild(label);
    toolbar.appendChild(leftDiv);
    if (total > 0) {
      var infoDiv = el('div', 'bla-runs-info');
      infoDiv.textContent = 'Showing ' + (start + 1) + '–' + end + ' of ' + total + ' run' + (total === 1 ? '' : 's');
      toolbar.appendChild(infoDiv);
    }

    // Table
    var host = $('bla-runs-body');
    if (!total) {
      setEmpty(host, 'No pipeline runs in this range yet.');
      $('bla-runs-count').textContent = '';
      renderRunsPagination(1, 1);
      return;
    }
    var t = el('table', 'bla-table');
    t.innerHTML =
      '<thead><tr><th>When</th><th>Project</th><th>Commit</th><th>Triggered by</th>' +
      '<th class="bla-num">Strings</th><th class="bla-num">Duration</th><th>Status</th></tr></thead>';
    var tb = el('tbody');
    pageRows.forEach(function (r) {
      var trigger = r.triggeredByDisplayName || (r.triggeredByLabel === 'external' ? 'External (webhook)' : (r.triggeredByLabel === 'owner' ? 'Owner' : '—'));
      var tr = el('tr');
      tr.innerHTML =
        '<td class="dim">' + fmtDate(r.startedAtMillis) + '</td>' +
        '<td>' + escapeHtml(r.projectName || r.projectId.slice(0, 8)) + '</td>' +
        '<td class="dim">' + escapeHtml(r.commitShort) + '</td>' +
        '<td>' + escapeHtml(trigger) + ' <span class="bla-tag ' + r.triggeredByLabel + '">' + r.triggeredByLabel + '</span></td>' +
        '<td class="bla-num">' + fmtInt(r.stringsTranslated) + '</td>' +
        '<td class="bla-num">' + fmtMs(r.durationMs) + '</td>' +
        '<td>' + escapeHtml(r.status) + '</td>';
      tb.appendChild(tr);
    });
    t.appendChild(tb);
    host.innerHTML = ''; host.appendChild(t);
    $('bla-runs-count').textContent = total + ' run' + (total === 1 ? '' : 's');
    renderRunsPagination(page, totalPages);
  }

  function renderRunsPagination(page, totalPages) {
    var host = $('bla-runs-pagination');
    host.innerHTML = '';
    if (totalPages <= 1) return;

    function pageBtn(label, targetPage, active, disabled) {
      var btn = el('button', 'bla-page-btn' + (active ? ' active' : '') + (disabled ? ' disabled' : ''));
      btn.textContent = label;
      btn.disabled = disabled;
      if (!disabled && !active) {
        btn.addEventListener('click', function () {
          RUNS_PAG.page = targetPage;
          renderRunsView();
        });
      }
      return btn;
    }

    host.appendChild(pageBtn('‹', page - 1, false, page === 1));
    paginationPages(page, totalPages).forEach(function (p) {
      if (p === '…') {
        host.appendChild(el('span', 'bla-page-ellipsis', '…'));
      } else {
        host.appendChild(pageBtn(p, p, p === page, false));
      }
    });
    host.appendChild(pageBtn('›', page + 1, false, page === totalPages));
  }

  function paginationPages(current, total) {
    if (total <= 7) {
      var arr = [];
      for (var i = 1; i <= total; i++) arr.push(i);
      return arr;
    }
    var pages = [1];
    if (current > 3) pages.push('…');
    for (var p = Math.max(2, current - 1); p <= Math.min(total - 1, current + 1); p++) pages.push(p);
    if (current < total - 2) pages.push('…');
    pages.push(total);
    return pages;
  }

  function renderMembers(rows) {
    var host = $('bla-members-body');
    if (!rows || rows.length === 0) { setEmpty(host, 'No member activity in this range.'); return; }
    var t = el('table', 'bla-table');
    t.innerHTML =
      '<thead><tr><th>Member</th><th class="bla-num">Strings</th><th class="bla-num">Runs</th>' +
      '<th class="bla-num">Projects</th><th>Last active</th></tr></thead>';
    var tb = el('tbody');
    rows.forEach(function (r) {
      var tr = el('tr');
      tr.innerHTML =
        '<td>' + escapeHtml(r.displayName) + (r.memberUserId === 'external' ? ' <span class="bla-tag external">webhook</span>' : '') + '</td>' +
        '<td class="bla-num">' + fmtInt(r.stringsTranslated) + '</td>' +
        '<td class="bla-num">' + fmtInt(r.runsTriggered) + '</td>' +
        '<td class="bla-num">' + fmtInt(r.projectsTouched) + '</td>' +
        '<td class="dim">' + fmtAgo(r.lastActiveMillis) + '</td>';
      tb.appendChild(tr);
    });
    t.appendChild(tb);
    host.innerHTML = ''; host.appendChild(t);
  }

  function renderCostBreakdown(data) {
    var host = $('bla-cost-body');
    if (!data || data.totalStringsThisMonth === 0) {
      setEmpty(host, 'No translations attributed yet this month — cost allocation appears once pipelines run.');
      return;
    }
    host.innerHTML = '';
    var grid = el('div', 'bla-cost-grid');

    function renderShareTable(title, rows) {
      var wrap = el('div');
      wrap.appendChild(el('div', 'bla-cost-panel-title', title));
      var t = el('table', 'bla-table');
      t.innerHTML = '<thead><tr><th>Name</th><th class="bla-num">Strings</th><th class="bla-num">₹ share</th></tr></thead>';
      var tb = el('tbody');
      rows.forEach(function (r) {
        var tr = el('tr');
        tr.innerHTML =
          '<td>' + escapeHtml(r.displayName) + '</td>' +
          '<td class="bla-num">' + fmtInt(r.strings) + '</td>' +
          '<td class="bla-num">' + fmtR(r.shareInr) + '</td>';
        tb.appendChild(tr);
      });
      t.appendChild(tb);
      wrap.appendChild(t);
      return wrap;
    }
    grid.appendChild(renderShareTable('Per member', data.perMember));
    grid.appendChild(renderShareTable('Per project', data.perProject));
    host.appendChild(grid);

    // Invariant check: rolled-up member total vs owner billing total
    var drift = data.ownerBillingTotalStrings - data.sumOfMemberStrings;
    var note = el('div', 'bla-cost-invariant' + (drift !== 0 ? ' warn' : ''));
    note.textContent = drift === 0
      ? 'Allocation reconciles with billing (' + fmtInt(data.ownerBillingTotalStrings) + ' strings).'
      : 'Billing total: ' + fmtInt(data.ownerBillingTotalStrings) + ' strings · member sum: ' + fmtInt(data.sumOfMemberStrings) + ' (drift ' + drift + ')';
    host.appendChild(note);
  }

  function acceptanceClass(pct) {
    if (pct == null) return '';
    if (pct >= 85) return 'bla-pct-good';
    if (pct >= 60) return 'bla-pct-warn';
    return 'bla-pct-bad';
  }

  function escapeHtml(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  // ── Load orchestration ─────────────────────────────────────────────────────

  function loadAll() {
    var r = STATE.range;
    api('/api/analytics/overview').then(function (d) {
      if (showPlanGate(d._forbidden)) return;
      renderOverview(d);
    }).catch(function (e) {
      $('bla-tracking-banner').textContent = 'Could not load analytics: ' + e.message;
    });

    api('/api/analytics/projects?range=' + r).then(function (d) { if (!d._forbidden) renderProjects(d); });
    api('/api/analytics/locales?range=' + r).then(function (d) { if (!d._forbidden) renderLocales(d); });
    RUNS_PAG.page = 1;
    api('/api/analytics/runs?range=' + r + '&limit=500').then(function (d) { if (!d._forbidden) renderRuns(d); });
    api('/api/analytics/quality').then(function (d) { if (!d._forbidden) renderQuality(d); });

    // Team-only sections — server returns 403 for non-Team; renderOverview also hides them.
    api('/api/analytics/members?range=' + r).then(function (d) { if (!d._forbidden) renderMembers(d); });
    api('/api/analytics/cost-breakdown').then(function (d) { if (!d._forbidden) renderCostBreakdown(d); });
  }

  function bindRangePicker() {
    var host = $('bla-range-picker');
    if (!host) return;
    host.querySelectorAll('.bla-range-btn').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var r = btn.getAttribute('data-range');
        if (r === STATE.range) return;
        host.querySelectorAll('.bla-range-btn').forEach(function (b) { b.classList.remove('active'); });
        btn.classList.add('active');
        STATE.range = r;
        $('bla-overview-period').textContent = r === 'month' ? 'This month' : 'Last ' + r;
        loadAll();
      });
    });
  }

  function boot() {
    bindRangePicker();
    loadAll();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
