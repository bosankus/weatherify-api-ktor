package com.syncling.routes

import kotlinx.html.*

/**
 * Authoritative HTML shell for every authenticated portal page (billing,
 * projects, review, dashboard). View files supply only the main-content
 * block — the shell takes care of <head>, sidebar, notification panel,
 * onboarding host, and bootstrapping the bearer token from the short-lived
 * cookie issued by [ApplicationCall.issueBootstrapCookie].
 *
 * @param pageTitle           Title shown in the browser tab — prefixed with "Syncling — ".
 * @param navKey              Active sidebar item: "dash" | "projects" | "billing" | "review".
 * @param reviewBadge         Show the pending-review count badge in the sidebar.
 * @param staticStylesheets   Absolute URLs of stylesheets served from /transloom/static.
 *                            Loaded after the shared CSS so they may override it.
 * @param staticScripts       Absolute URLs of page scripts served from /transloom/static.
 *                            Loaded with `defer` after the inline shell scripts so
 *                            window.authHeaders / toast / Onboarding are already defined.
 * @param externalScripts     Third-party scripts (e.g. Razorpay Checkout.js) loaded
 *                            with `defer` in <head>.
 * @param onboardingPage      If non-null, calls Onboarding.boot('<value>') after load.
 * @param content             Slot for page-specific markup, rendered inside <main>.
 */
internal fun HTML.portalShell(
    pageTitle: String,
    navKey: String,
    reviewBadge: Boolean = false,
    staticStylesheets: List<String> = emptyList(),
    staticScripts: List<String> = emptyList(),
    externalScripts: List<String> = emptyList(),
    onboardingPage: String? = null,
    mainClass: String = "main-content",
    content: MAIN.() -> Unit,
) {
    head {
        title { +"Syncling — $pageTitle" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        favicon()
        style { unsafe { +"$SHARED_CSS$SHELL_LAYOUT_CSS$SIDEBAR_QUOTA_CSS$CONVERSION_CSS$ONBOARDING_CSS$SUPPORT_CHAT_CSS" } }
        staticStylesheets.forEach { href ->
            link(rel = "stylesheet", href = href)
        }
        externalScripts.forEach { src ->
            script {
                this.src = src
                defer = true
            }
        }
    }
    body {
        div("app-layout") {
            unsafe { +appSidebar(navKey, reviewBadge) }
            main(mainClass) { content() }
        }

        div { id = "toast"; classes = setOf("toast") }
        div { id = "ob-host" }

        // Inline shell runtime: token bootstrap, authHeaders(), toast(), logout().
        // Loaded synchronously before NOTIFICATIONS_JS / ONBOARDING_JS because both
        // reference window.authHeaders.
        script { unsafe { +SHELL_RUNTIME_JS } }
        script { unsafe { +BILLING_CACHE_JS } }
        script { unsafe { +SIDEBAR_QUOTA_JS } }
        script { unsafe { +NOTIFICATIONS_JS } }
        script { unsafe { +ONBOARDING_JS } }
        script { unsafe { +SUPPORT_CHAT_JS } }

        staticScripts.forEach { src ->
            script {
                this.src = src
                defer = true
            }
        }
        if (onboardingPage != null) {
            script { unsafe { +"document.addEventListener('DOMContentLoaded',()=>window.Onboarding&&Onboarding.boot('$onboardingPage'));" } }
        }
    }
}

/**
 * Lightweight placeholder used while a view is being rebuilt. Renders inside the
 * portal shell so navigation, notifications, and onboarding still work.
 */
internal fun HTML.comingSoonPage(label: String, navKey: String) {
    portalShell(pageTitle = label, navKey = navKey) {
        div("page-header") {
            div {
                h1("page-title") { +label }
                p("page-sub") { +"This page is being rebuilt and will return shortly." }
            }
        }
        div("card") {
            attributes["style"] = "padding:48px;text-align:center;color:var(--text-muted);font-size:14px"
            +"Coming soon."
        }
    }
}

/**
 * Bootstraps the JWT into localStorage from the short-lived `tl_token_bootstrap`
 * cookie, then exposes the small set of window-globals every portal page relies on:
 *
 *   window.authHeaders()        → headers object for fetch (Authorization + JSON)
 *   window.tlFetch(path, opts)  → fetch wrapper that injects auth headers
 *   window.toast(msg, kind)     → bottom-right toast notification
 *   window.logout()             → clears token and redirects to /transloom/auth/logout
 *
 * Kept inline (rather than served as a static file) because it must run before
 * any other script — extracting it would require an extra blocking <script src>.
 */
/**
 * Layout chrome shared by every authenticated portal page rendered through
 * [portalShell]: the flex container that pairs the sidebar with the scrollable
 * main column, the sidebar/nav styling, and the basic page-header typography.
 *
 * Kept separate from [SHARED_CSS] (which is also used by unauthenticated
 * landing pages that don't have a sidebar) and from `DASHBOARD_CSS` (which
 * holds dashboard-only widgets and modals). Dashboard renders pull this in
 * alongside `DASHBOARD_CSS` so the two stay in sync.
 */
internal const val SHELL_LAYOUT_CSS = """
.app-layout{display:flex;height:100vh;overflow:hidden}
.sidebar{width:220px;flex-shrink:0;background:linear-gradient(180deg,#0f0b1a 0%,var(--surface) 100%);border-right:1px solid rgba(139,126,255,.15);display:flex;flex-direction:column;padding:20px 0;transition:width .18s ease}
.sidebar-head{display:flex;align-items:center;gap:8px;padding:2px 16px 18px;border-bottom:1px solid var(--border);margin-bottom:12px}
.sidebar-logo{flex:1;min-width:0;display:flex;align-items:center;gap:10px;font-size:16px;font-weight:700;color:var(--text);overflow:hidden;padding:0}
.sidebar-logo .brand-mark{flex-shrink:0}
.sidebar-logo .brand-text{color:var(--text);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.sidebar-toggle{flex-shrink:0;width:26px;height:26px;display:flex;align-items:center;justify-content:center;background:var(--surface2);border:1px solid var(--border);border-radius:var(--radius-sm);color:var(--text-muted);cursor:pointer;padding:0;transition:color .12s,background .12s,transform .18s}
.sidebar-toggle:hover{color:var(--text);background:var(--surface)}
.sidebar-nav{flex:1;display:flex;flex-direction:column;gap:2px;padding:0 10px}
.nav-item{display:flex;align-items:center;gap:10px;padding:9px 12px;border-radius:var(--radius-sm);color:var(--text-muted);font-size:13px;line-height:1;transition:background .12s,color .12s}
.nav-item .nav-icon{flex-shrink:0;width:16px;height:16px}
.nav-item .nav-label{flex:1;min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.nav-item:hover{background:var(--surface2);color:var(--text)}
.nav-item.active{background:var(--accent-dim);color:var(--accent);font-weight:500}
.nav-badge{margin-left:auto;font-size:11px;font-weight:700;border-radius:10px;padding:1px 7px;min-width:20px;text-align:center}
.review-badge{background:var(--accent-dim);color:var(--accent);display:none}
.sidebar-footer{padding:16px 16px 0;border-top:1px solid var(--border);display:flex;flex-direction:column;gap:8px}
.user-chip{display:flex;align-items:center;gap:8px;padding:6px 8px;background:var(--surface2);border:1px solid var(--border);border-radius:var(--radius-sm);min-width:0}
.user-avatar{width:26px;height:26px;border-radius:50%;background:var(--accent-dim);color:var(--accent);font-size:11px;font-weight:700;display:flex;align-items:center;justify-content:center;flex-shrink:0;text-transform:uppercase;letter-spacing:0}
.user-name{font-size:12px;color:var(--text);font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;min-width:0;flex:1}
.logout-btn{width:100%;font-size:12px;padding:7px 12px}
.sidebar.collapsed{width:64px}
.sidebar.collapsed .sidebar-head{padding:2px 10px 18px;justify-content:center}
.sidebar.collapsed .sidebar-logo{display:none}
.sidebar.collapsed .sidebar-toggle{margin:0 auto}
.sidebar.collapsed .sb-toggle-icon{transform:rotate(180deg)}
.sidebar.collapsed .sidebar-nav{padding:0 8px}
.sidebar.collapsed .nav-item{justify-content:center;padding:9px}
.sidebar.collapsed .nav-item .nav-label{display:none}
.sidebar.collapsed .nav-badge{display:none}
.sidebar.collapsed .sidebar-footer{padding:12px 8px 0}
.sidebar.collapsed .sb-quota{display:none!important}
.sidebar.collapsed .user-name{display:none}
.sidebar.collapsed .user-chip{justify-content:center;padding:6px}
.sidebar.collapsed .logout-btn{display:none}
.main-content{flex:1;overflow-y:auto;padding:28px 32px}
.page-header{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:24px;gap:12px}
.page-title{font-size:26px;font-weight:700;letter-spacing:-.5px;line-height:1.2;margin-bottom:3px}
.page-sub{font-size:13px;color:var(--text-muted)}
"""

internal val SHELL_RUNTIME_JS = """
(function(){
  var token=localStorage.getItem('syncling_token');
  if(!token){
    var m=document.cookie.match(/(?:^|;\s*)tl_token_bootstrap=([^;]*)/);
    if(m&&m[1]){token=decodeURIComponent(m[1]);localStorage.setItem('syncling_token',token);}
  }
  // Post-OAuth invite handoff: if the user came back from GitHub with a pending
  // invite token stashed by invite.js, bounce them straight to the invite page
  // so the now-authenticated session can accept in one click. Skip when we're
  // already on the invite page (avoids a redirect loop) or have no session.
  if(token){
    var pending=localStorage.getItem('pending_invite_token');
    if(pending && !/^\/transloom\/invite\//.test(location.pathname)){
      window.location.replace('/transloom/invite/'+encodeURIComponent(pending));
      return;
    }
  }
  window.authHeaders=function(){
    return token?{'Authorization':'Bearer '+token,'Content-Type':'application/json'}
                :{'Content-Type':'application/json'};
  };
  window.tlFetch=function(path,opts){
    opts=opts||{};
    var h=Object.assign({},window.authHeaders(),opts.headers||{});
    return fetch(path,Object.assign({},opts,{headers:h}));
  };
  window.toast=function(msg,kind){
    var t=document.getElementById('toast');
    if(!t){t=document.createElement('div');t.id='toast';t.className='toast';document.body.appendChild(t);}
    t.textContent=msg;
    t.className='toast '+(kind||'')+' show';
    clearTimeout(t._timer);
    t._timer=setTimeout(function(){t.className='toast '+(kind||'');},2800);
  };
  window.logout=function(){
    localStorage.removeItem('syncling_token');
    window.location.href='/transloom/auth/logout';
  };
  function fillUserChip(){
    if(!token)return;
    var p={};try{p=JSON.parse(atob(token.split('.')[1]));}catch(_){}
    var name=p.username?'@'+p.username:(p.email||'You');
    var src=p.username||p.email||'?';
    var initial=src.charAt(0).toUpperCase();
    var n=document.getElementById('user-name');if(n)n.textContent=name;
    var a=document.getElementById('user-avatar');if(a)a.textContent=initial;
  }
  function applySidebarState(){
    var sb=document.getElementById('app-sidebar');if(!sb)return;
    var collapsed=localStorage.getItem('syncling_sidebar_collapsed')==='1';
    sb.classList.toggle('collapsed',collapsed);
    var btn=document.getElementById('sidebar-toggle');
    if(btn){
      var label=collapsed?'Expand sidebar':'Collapse sidebar';
      btn.setAttribute('aria-label',label);
      btn.setAttribute('title',label);
    }
  }
  window.toggleSidebar=function(){
    var sb=document.getElementById('app-sidebar');if(!sb)return;
    var collapsed=!sb.classList.contains('collapsed');
    localStorage.setItem('syncling_sidebar_collapsed',collapsed?'1':'0');
    applySidebarState();
  };
  function boot(){fillUserChip();applySidebarState();}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',boot);
  else boot();
})();
"""

/**
 * Cost-per-string widget that lives in the sidebar footer on every authenticated
 * page. Visible to all paid plans plus FREE (with plan-appropriate framing).
 * Hidden for ENTERPRISE (no plan price to divide by) and when the user has no
 * strings translated and no history (the metric is meaningless).
 *
 * FREE shows anchored upsell math ("On Solo: ₹X / string at your pace").
 * SOLO/TEAM show the real effective rate with month-over-month delta and an
 * end-of-month projection.
 */
internal const val SIDEBAR_QUOTA_CSS = """
.sb-quota{padding:10px 12px;background:var(--surface2);border:1px solid var(--border);border-radius:var(--radius-sm);margin-bottom:10px;display:none}
.sb-quota.visible{display:block}
.sb-quota-eyebrow{font-size:10px;font-weight:600;color:var(--text-dim);text-transform:uppercase;letter-spacing:.5px;margin-bottom:6px}
.sb-quota-num{font-size:18px;font-weight:700;color:var(--text);font-variant-numeric:tabular-nums;display:flex;align-items:baseline;gap:8px;margin-bottom:4px;line-height:1.1}
.sb-quota-unit{font-size:11px;color:var(--text-muted);font-weight:500}
.sb-quota-delta{font-size:11px;font-weight:600;font-variant-numeric:tabular-nums}
.sb-quota-delta.down{color:var(--accent)}
.sb-quota-delta.up{color:var(--yellow)}
.sb-quota-sub{font-size:11px;color:var(--text-muted);margin-bottom:8px;line-height:1.4}
.sb-quota-cta{display:block;text-align:center;font-size:11px;font-weight:600;color:var(--accent);padding:6px 8px;border:1px solid rgba(139,126,255,.3);border-radius:5px;transition:background .15s,border-color .15s}
.sb-quota-cta:hover{background:var(--accent-dim);border-color:var(--accent)}
"""

/**
 * Per-page-load promise cache around the two billing endpoints. Lives outside
 * SHELL_RUNTIME_JS so the dashboard page (which has its own bootstrap) can pull
 * it in independently. Idempotent — re-including is a no-op.
 */
internal const val BILLING_CACHE_JS = """
(function(){
  if(window.tlSubscription)return;
  function H(){var t=localStorage.getItem('syncling_token');return t?{'Authorization':'Bearer '+t,'Content-Type':'application/json'}:{'Content-Type':'application/json'};}
  var _s=null,_u=null;
  window.tlSubscription=function(f){if(f||!_s){_s=fetch('/transloom/api/billing/subscription',{headers:H()}).then(function(r){return r.ok?r.json():null}).catch(function(){return null});}return _s;};
  window.tlUsage=function(f){if(f||!_u){_u=fetch('/transloom/api/billing/usage',{headers:H()}).then(function(r){return r.ok?r.json():null}).catch(function(){return null});}return _u;};
})();
"""

internal val SIDEBAR_QUOTA_JS = """
(function(){
  var PRICE={FREE:0,SOLO:499,TEAM:1999};
  var CACHE_KEY='tl_quota_cache';
  function fmt(n){return n>=10?n.toFixed(0):n.toFixed(2);}
  function pad2(n){n=String(n);return n.length<2?'0'+n:n;}
  function lastMonthKey(){
    var d=new Date(); d.setDate(1); d.setMonth(d.getMonth()-1);
    return d.getFullYear()+'-'+pad2(d.getMonth()+1);
  }
  function daysInMonth(){
    var d=new Date(); return new Date(d.getFullYear(),d.getMonth()+1,0).getDate();
  }
  function findLast(history,key){
    if(!history)return null;
    for(var i=0;i<history.length;i++){
      if(history[i].yearMonth===key && (history[i].stringsTranslated||0)>0)return history[i];
    }
    return null;
  }
  function render(host,sub,usage){
    if(!sub||!usage){host.classList.remove('visible');return;}
    var plan=sub.plan;
    if(plan==='ENTERPRISE'){host.classList.remove('visible');return;}
    var strings=usage.stringsTranslated||0;
    var history=usage.history||[];
    if(strings===0 && history.length===0){host.classList.remove('visible');return;}
    var price=PRICE[plan];
    if(typeof price!=='number'){host.classList.remove('visible');return;}
    var html='';
    if(plan==='FREE'){
      html+='<div class="sb-quota-eyebrow">Your effective rate</div>';
      html+='<div class="sb-quota-num">₹0<span class="sb-quota-unit">/ string</span></div>';
      if(strings>0){
        var soloRate=PRICE.SOLO/Math.max(1,strings);
        html+='<div class="sb-quota-sub">On Solo: ₹'+fmt(soloRate)+' / string at your pace</div>';
      } else {
        html+='<div class="sb-quota-sub">Translate something to see your rate</div>';
      }
      html+='<a href="/transloom/billing" class="sb-quota-cta">Compare plans →</a>';
    } else {
      var current=price/Math.max(1,strings);
      var lm=findLast(history,lastMonthKey());
      var deltaHtml='';
      if(lm){
        var lastRate=price/lm.stringsTranslated;
        var delta=lastRate>0?((current-lastRate)/lastRate)*100:0;
        var dir=delta<=0?'down':'up';
        var arrow=delta<=0?'▼':'▲';
        deltaHtml=' <span class="sb-quota-delta '+dir+'">'+arrow+' '+Math.abs(delta).toFixed(0)+'%</span>';
      }
      var d=new Date();
      var day=d.getDate(), dim=daysInMonth();
      var subHtml='';
      if(day>5 && strings>0){
        var projected=price/(strings*dim/day);
        subHtml='<div class="sb-quota-sub">Projected: ₹'+fmt(projected)+' by month-end</div>';
      } else if(!lm){
        subHtml='<div class="sb-quota-sub">First month — projection available later</div>';
      }
      html+='<div class="sb-quota-eyebrow">Cost / string</div>';
      html+='<div class="sb-quota-num">₹'+fmt(current)+deltaHtml+'</div>';
      html+=subHtml;
      html+='<a href="/transloom/billing/analytics" class="sb-quota-cta">See analytics →</a>';
    }
    host.className='sb-quota visible';
    host.innerHTML=html;
  }
  function readCache(){
    try{var v=localStorage.getItem(CACHE_KEY);return v?JSON.parse(v):null;}catch(_){return null;}
  }
  function writeCache(sub,usage){
    try{localStorage.setItem(CACHE_KEY,JSON.stringify({sub:sub,usage:usage}));}catch(_){}
  }
  function boot(){
    var host=document.getElementById('sb-quota');
    if(!host||!window.tlSubscription||!window.tlUsage)return;
    var cached=readCache();
    if(cached&&cached.sub&&cached.usage)render(host,cached.sub,cached.usage);
    Promise.all([window.tlSubscription(),window.tlUsage()]).then(function(r){
      if(r[0]&&r[1])writeCache(r[0],r[1]);
      render(host,r[0],r[1]);
    });
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',boot);
  else boot();
})();
"""

/**
 * Styling for the post-pipeline conversion banner (dashboard) and the locked
 * Pro-feature rows in the projects drawer. Kept in the shell so both surfaces
 * share the same look.
 */
internal const val CONVERSION_CSS = """
.conv-toast{position:fixed;bottom:24px;left:50%;transform:translate(-50%,8px);background:var(--surface);border:1px solid var(--accent);border-radius:var(--radius);padding:12px 16px 12px 18px;display:flex;align-items:center;gap:14px;box-shadow:0 12px 36px -8px rgba(139,126,255,.35),0 4px 16px rgba(0,0,0,.4);z-index:9500;opacity:0;transition:opacity .25s,transform .25s;max-width:560px;font-size:13px;color:var(--text)}
.conv-toast.visible{opacity:1;transform:translate(-50%,0)}
.conv-toast .conv-msg{flex:1;line-height:1.45}
.conv-toast .conv-msg strong{color:var(--accent);font-weight:700}
.conv-toast .conv-cta{font-size:12px;font-weight:700;color:#000;background:var(--accent);padding:7px 14px;border-radius:6px;white-space:nowrap;transition:filter .12s}
.conv-toast .conv-cta:hover{filter:brightness(1.08)}
.conv-toast .conv-close{background:transparent;border:none;color:var(--text-muted);font-size:18px;line-height:1;cursor:pointer;padding:2px 4px}
.conv-toast .conv-close:hover{color:var(--text)}
.pr-locked-section{margin-top:8px}
.pr-locked-row{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;padding:12px;background:var(--surface2);border:1px dashed var(--border);border-radius:var(--radius-sm);margin-bottom:8px;opacity:.85;transition:opacity .15s,border-color .15s}
.pr-locked-row:hover{opacity:1;border-color:rgba(139,126,255,.3)}
.pr-locked-info{flex:1;min-width:0}
.pr-locked-title{font-size:13px;font-weight:600;color:var(--text);margin-bottom:3px;display:flex;align-items:center;gap:6px}
.pr-locked-badge{font-size:10px;font-weight:700;letter-spacing:.4px;text-transform:uppercase;color:var(--accent);background:var(--accent-dim);border:1px solid rgba(139,126,255,.3);border-radius:10px;padding:1px 7px}
.pr-locked-hint{font-size:12px;color:var(--text-muted);line-height:1.45}
.pr-locked-cta{font-size:11px;font-weight:600;color:var(--accent);white-space:nowrap;padding:6px 12px;border:1px solid rgba(139,126,255,.3);border-radius:5px;transition:background .15s}
.pr-locked-cta:hover{background:var(--accent-dim)}
"""

internal const val SUPPORT_CHAT_CSS = """
.sc-fab{position:fixed;bottom:24px;right:24px;z-index:8900;width:48px;height:48px;border-radius:50%;background:var(--accent);border:none;cursor:pointer;display:flex;align-items:center;justify-content:center;box-shadow:0 4px 16px rgba(139,126,255,.4),0 2px 8px rgba(0,0,0,.3);transition:filter .15s,transform .15s;color:#000}
.sc-fab:hover{filter:brightness(1.1);transform:scale(1.06)}
.sc-fab svg{flex-shrink:0}
.sc-panel{position:fixed;bottom:84px;right:24px;z-index:8901;width:360px;max-width:calc(100vw - 32px);background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);box-shadow:0 12px 40px -8px rgba(0,0,0,.5);display:flex;flex-direction:column;max-height:540px;transform:translateY(16px) scale(.97);opacity:0;pointer-events:none;transition:opacity .18s,transform .18s}
.sc-panel.open{opacity:1;transform:none;pointer-events:auto}
.sc-panel-head{display:flex;align-items:center;justify-content:space-between;padding:14px 16px 12px;border-bottom:1px solid var(--border);flex-shrink:0}
.sc-panel-title{font-size:14px;font-weight:700;color:var(--text)}
.sc-panel-close{background:transparent;border:none;color:var(--text-muted);cursor:pointer;font-size:18px;line-height:1;padding:2px 4px;border-radius:4px}
.sc-panel-close:hover{color:var(--text);background:var(--surface2)}
.sc-tabs{display:flex;gap:2px;padding:10px 12px 0;border-bottom:1px solid var(--border);flex-shrink:0}
.sc-tab{font-size:12px;font-weight:600;color:var(--text-muted);padding:6px 10px;border-radius:var(--radius-sm) var(--radius-sm) 0 0;border:1px solid transparent;border-bottom:none;cursor:pointer;background:transparent;transition:color .12s,background .12s;margin-bottom:-1px}
.sc-tab.active{color:var(--accent);background:var(--surface);border-color:var(--border);border-bottom-color:var(--surface)}
.sc-tab:hover:not(.active){color:var(--text);background:var(--surface2)}
.sc-body{flex:1;overflow-y:auto;padding:14px 16px}
.sc-field{margin-bottom:12px}
.sc-label{display:block;font-size:11px;font-weight:600;color:var(--text-muted);text-transform:uppercase;letter-spacing:.4px;margin-bottom:5px}
.sc-input,.sc-select,.sc-textarea{width:100%;background:var(--surface2);border:1px solid var(--border);border-radius:var(--radius-sm);color:var(--text);font-size:13px;padding:8px 10px;box-sizing:border-box;font-family:inherit;transition:border-color .12s}
.sc-input:focus,.sc-select:focus,.sc-textarea:focus{outline:none;border-color:var(--accent)}
.sc-textarea{resize:vertical;min-height:90px;max-height:180px;line-height:1.5}
.sc-select{appearance:none;background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6'%3E%3Cpath d='M0 0l5 6 5-6z' fill='%2371717a'/%3E%3C/svg%3E");background-repeat:no-repeat;background-position:right 10px center;padding-right:28px}
.sc-submit{width:100%;padding:10px;background:var(--accent);border:none;border-radius:var(--radius-sm);color:#000;font-size:13px;font-weight:700;cursor:pointer;transition:filter .12s;margin-top:4px}
.sc-submit:hover:not(:disabled){filter:brightness(1.08)}
.sc-submit:disabled{opacity:.5;cursor:default}
.sc-success{text-align:center;padding:20px 8px}
.sc-success-icon{font-size:32px;margin-bottom:10px}
.sc-success-title{font-size:14px;font-weight:700;color:var(--text);margin-bottom:6px}
.sc-success-sub{font-size:12px;color:var(--text-muted);line-height:1.5;margin-bottom:14px}
.sc-ticket-id{font-family:monospace;font-size:12px;background:var(--surface2);border:1px solid var(--border);padding:4px 8px;border-radius:4px;color:var(--accent)}
.sc-new-btn{font-size:12px;font-weight:600;color:var(--accent);background:transparent;border:1px solid rgba(139,126,255,.3);border-radius:5px;padding:7px 14px;cursor:pointer;transition:background .12s}
.sc-new-btn:hover{background:var(--accent-dim)}
.sc-ticket-list{display:flex;flex-direction:column;gap:8px}
.sc-ticket-item{background:var(--surface2);border:1px solid var(--border);border-radius:var(--radius-sm);padding:10px 12px}
.sc-ticket-row{display:flex;align-items:center;gap:6px;margin-bottom:4px}
.sc-ticket-subject{font-size:13px;font-weight:600;color:var(--text);flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.sc-badge{font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.4px;border-radius:10px;padding:1px 7px;flex-shrink:0}
.sc-badge.cat-bug{background:#3f1010;color:#f87171}
.sc-badge.cat-question{background:var(--accent-dim);color:var(--accent)}
.sc-badge.cat-feature{background:#1e1a3f;color:#a78bfa}
.sc-badge.cat-billing{background:#1f2a10;color:#a3e635}
.sc-badge.st-open{background:#2a1a00;color:#fb923c}
.sc-badge.st-acknowledged{background:var(--accent-dim);color:var(--accent)}
.sc-badge.st-resolved{background:#111;color:var(--text-muted)}
.sc-ticket-meta{font-size:11px;color:var(--text-dim)}
.sc-empty{text-align:center;padding:32px 16px;color:var(--text-muted);font-size:13px}
.sc-loading{text-align:center;padding:24px;color:var(--text-muted);font-size:13px}
"""

internal val SUPPORT_CHAT_JS = """(function(){
  var BASE='/transloom/api/support';
  var open=false,tab='new',submitting=false,success=null,tickets=null,loaded=false;
  function H(){return window.authHeaders?window.authHeaders():{'Content-Type':'application/json'};}
  function esc(s){var d=document.createElement('div');d.textContent=String(s==null?'':s);return d.innerHTML;}
  function fmtDate(ms){var d=new Date(ms);return d.toLocaleDateString(undefined,{month:'short',day:'numeric'});}
  function render(){
    var fab=document.getElementById('sc-fab');
    var panel=document.getElementById('sc-panel');
    if(!fab||!panel)return;
    panel.classList.toggle('open',open);
    var body=panel.querySelector('.sc-body');
    if(!body)return;
    if(tab==='new'){
      if(success){
        body.innerHTML='<div class="sc-success"><div class="sc-success-icon">✅</div><div class="sc-success-title">Request submitted!</div><div class="sc-success-sub">We\'ll get back to you at your account email. Your ticket ID:</div><div class="sc-ticket-id">'+esc(success.id.substring(0,8))+'</div><div style="margin-top:14px"><button class="sc-new-btn" onclick="window._scNewTicket()">+ New ticket</button></div></div>';
      } else {
        body.innerHTML='<div><div class="sc-field"><label class="sc-label">Category</label><select class="sc-select" id="sc-cat"><option value="bug">Bug report</option><option value="question">Question</option><option value="feature">Feature request</option><option value="billing">Billing</option></select></div><div class="sc-field"><label class="sc-label">Subject</label><input class="sc-input" id="sc-subj" type="text" placeholder="Brief description" maxlength="200"></div><div class="sc-field"><label class="sc-label">Message</label><textarea class="sc-textarea" id="sc-msg" placeholder="Describe your issue or question…" maxlength="5000"></textarea></div><button class="sc-submit" id="sc-submit-btn" onclick="window._scSubmit()">'+(submitting?'Sending…':'Send message')+'</button></div>';
        if(submitting)document.getElementById('sc-submit-btn').disabled=true;
      }
    } else {
      if(!loaded){
        body.innerHTML='<div class="sc-loading">Loading…</div>';
        fetch(BASE,{headers:H()}).then(function(r){return r.ok?r.json():null}).then(function(d){
          loaded=true;tickets=d&&d.tickets?d.tickets:[];render();
        }).catch(function(){loaded=true;tickets=[];render();});
      } else if(!tickets||!tickets.length){
        body.innerHTML='<div class="sc-empty">No tickets yet. Submit one in the New Ticket tab.</div>';
      } else {
        var html='<div class="sc-ticket-list">';
        tickets.forEach(function(t){
          var catCls='cat-'+t.category,stCls='st-'+t.status;
          html+='<div class="sc-ticket-item"><div class="sc-ticket-row"><span class="sc-ticket-subject">'+esc(t.subject)+'</span><span class="sc-badge '+catCls+'">'+esc(t.category)+'</span></div><div class="sc-ticket-row"><span class="sc-badge '+stCls+'">'+esc(t.status)+'</span><span class="sc-ticket-meta" style="margin-left:4px">'+fmtDate(t.createdAt)+'</span></div></div>';
        });
        html+='</div>';
        body.innerHTML=html;
      }
    }
  }
  window._scNewTicket=function(){success=null;render();};
  window._scSubmit=function(){
    var cat=document.getElementById('sc-cat');
    var subj=document.getElementById('sc-subj');
    var msg=document.getElementById('sc-msg');
    if(!cat||!subj||!msg)return;
    var sv=subj.value.trim(),mv=msg.value.trim();
    if(!sv){subj.focus();if(window.toast)toast('Subject is required','error');return;}
    if(!mv){msg.focus();if(window.toast)toast('Message is required','error');return;}
    submitting=true;render();
    fetch(BASE,{method:'POST',headers:H(),body:JSON.stringify({category:cat.value,subject:sv,message:mv})})
      .then(function(r){return r.ok?r.json():r.json().then(function(e){throw new Error(e.error||'Failed');});})
      .then(function(d){submitting=false;success=d;loaded=false;tickets=null;render();})
      .catch(function(e){submitting=false;render();if(window.toast)toast(e.message||'Failed to submit','error');});
  };
  window._scToggle=function(){open=!open;if(open&&tab==='tickets'&&!loaded){loaded=false;}render();};
  window._scTab=function(t){tab=t;if(t==='tickets'&&!loaded){loaded=false;}render();};
  window._scClose=function(){open=false;render();};
  function mount(){
    if(document.getElementById('sc-fab'))return;
    var fab=document.createElement('button');
    fab.id='sc-fab';fab.className='sc-fab';fab.setAttribute('aria-label','Support');fab.setAttribute('title','Help & support');
    fab.innerHTML='<svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>';
    fab.onclick=window._scToggle;
    document.body.appendChild(fab);
    var panel=document.createElement('div');
    panel.id='sc-panel';panel.className='sc-panel';panel.setAttribute('role','dialog');panel.setAttribute('aria-label','Support');
    panel.innerHTML='<div class="sc-panel-head"><span class="sc-panel-title">Help &amp; Support</span><button class="sc-panel-close" onclick="window._scClose()" aria-label="Close">✕</button></div><div class="sc-tabs"><button class="sc-tab active" id="sc-tab-new" onclick="window._scTab(\'new\')">New Ticket</button><button class="sc-tab" id="sc-tab-tickets" onclick="window._scTab(\'tickets\')">My Tickets</button></div><div class="sc-body"></div>';
    document.body.appendChild(panel);
    var tabs=panel.querySelectorAll('.sc-tab');
    var origRender=render;
    render=function(){
      tabs.forEach(function(t){t.classList.toggle('active',t.id==='sc-tab-'+tab);});
      origRender();
    };
    render();
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',mount);
  else mount();
})();"""
