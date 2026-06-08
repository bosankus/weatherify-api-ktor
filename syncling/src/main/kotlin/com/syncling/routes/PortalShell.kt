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
        link(rel = "preconnect", href = "https://fonts.googleapis.com")
        link(rel = "preconnect", href = "https://fonts.gstatic.com") { attributes["crossorigin"] = "" }
        link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Jost:wght@700&display=swap")
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
    // One-time migration: carry the session over from the pre-rebrand key.
    var legacy=localStorage.getItem('transloom_token');
    if(legacy){token=legacy;localStorage.setItem('syncling_token',token);localStorage.removeItem('transloom_token');}
  }
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
    if(pending && !/^\/invite\//.test(location.pathname)){
      window.location.replace('/invite/'+encodeURIComponent(pending));
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
    window.location.href='/auth/logout';
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
  // Page-visibility wake system: after 3 min away, fire all registered callbacks on return.
  // SSE already disconnects on hide; this handles stale REST data (stats, CDN, notifications).
  var _tlHiddenAt=0;
  var _tlWakeCbs=[];
  window._tlOnWake=function(fn){_tlWakeCbs.push(fn);};
  document.addEventListener('visibilitychange',function(){
    if(document.hidden){_tlHiddenAt=Date.now();}
    else if(_tlHiddenAt){var away=Date.now()-_tlHiddenAt;_tlHiddenAt=0;if(away>=60000){_tlWakeCbs.forEach(function(f){try{f(away);}catch(_){}});}}
  });
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
.sb-quota{padding:0 12px;background:var(--surface2);border:1px solid transparent;border-radius:var(--radius-sm);margin-bottom:0;max-height:0;opacity:0;overflow:hidden;transition:all .4s cubic-bezier(0.25,1,0.5,1)}
.sb-quota.visible{padding:10px 12px;margin-bottom:10px;max-height:160px;opacity:1;border-color:var(--border)}
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
  window.tlSubscription=function(f){if(f||!_s){_s=fetch('/api/billing/subscription',{headers:H()}).then(function(r){return r.ok?r.json():null}).catch(function(){return null});}return _s;};
  window.tlUsage=function(f){if(f||!_u){_u=fetch('/api/billing/usage',{headers:H()}).then(function(r){return r.ok?r.json():null}).catch(function(){return null});}return _u;};
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
        html+='<div class="sb-quota-sub">On PRO: ₹'+fmt(soloRate)+' / string at your pace</div>';
      } else {
        html+='<div class="sb-quota-sub">Translate something to see your rate</div>';
      }
      html+='<a href="/billing" class="sb-quota-cta">Compare plans →</a>';
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
      html+='<a href="/billing/analytics" class="sb-quota-cta">See analytics →</a>';
    }
    if(window.location.pathname.indexOf('/billing/analytics') !== -1){
      host.className='sb-quota';
    } else {
      host.className='sb-quota visible';
    }
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
/* FAB */
.sc-fab{position:fixed;bottom:24px;right:24px;z-index:8900;width:52px;height:52px;border-radius:50%;background:var(--accent);border:none;cursor:pointer;display:flex;align-items:center;justify-content:center;box-shadow:0 4px 20px rgba(139,126,255,.5),0 2px 8px rgba(0,0,0,.3);transition:filter .15s,transform .15s;color:#fff}
.sc-fab:hover{filter:brightness(1.1);transform:scale(1.07)}
.sc-fab svg{flex-shrink:0}
.sc-fab-dot{position:absolute;top:3px;right:3px;width:12px;height:12px;border-radius:50%;background:#a78bfa;border:2px solid var(--surface);display:none;animation:sc-pulse 2s infinite}
.sc-fab-dot.show{display:block}
@keyframes sc-pulse{0%,100%{box-shadow:0 0 0 0 rgba(167,139,250,.6)}50%{box-shadow:0 0 0 6px rgba(167,139,250,0)}}
/* Panel */
.sc-panel{position:fixed;bottom:88px;right:24px;z-index:8901;width:400px;max-width:calc(100vw - 24px);background:var(--surface);border:1px solid var(--border);border-radius:18px;box-shadow:0 20px 60px -10px rgba(0,0,0,.6),0 4px 20px -4px rgba(0,0,0,.3);display:flex;flex-direction:column;height:580px;max-height:calc(100vh - 120px);opacity:0;transform:translateY(24px) scale(.95);pointer-events:none;transition:opacity .22s cubic-bezier(.4,0,.2,1),transform .22s cubic-bezier(.4,0,.2,1);overflow:hidden}
.sc-panel.open{opacity:1;transform:none;pointer-events:auto}
/* Admin variant — full-height right-side slide-out, mirrors .pr-drawer */
.sc-panel.sc-admin{top:0;right:0;bottom:0;width:480px;max-width:100vw;height:100vh;max-height:100vh;border-radius:0;border:none;border-left:1px solid var(--border);box-shadow:-16px 0 48px rgba(0,0,0,.5);opacity:1;transform:translateX(100%);transition:transform .3s cubic-bezier(.2,.8,.2,1)}
.sc-panel.sc-admin.open{transform:translateX(0)}
.sc-panel.sc-admin .sc-header{border-radius:0}
/* Header */
.sc-header{flex-shrink:0;background:linear-gradient(135deg,#5535dd 0%,#7c3aed 60%,#a855f7 100%);padding:14px 16px 12px;display:flex;align-items:center;gap:10px}
.sc-header-back{background:rgba(255,255,255,.15);border:none;width:30px;height:30px;border-radius:50%;cursor:pointer;display:none;align-items:center;justify-content:center;color:#fff;flex-shrink:0;transition:background .12s;font-size:16px;line-height:1}
.sc-header-back.visible{display:flex}
.sc-header-back:hover{background:rgba(255,255,255,.25)}
.sc-header-brand{flex:1;min-width:0}
.sc-header-name{font-size:14px;font-weight:700;color:#fff;line-height:1.2;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.sc-header-status{font-size:11px;color:rgba(255,255,255,.8);display:flex;align-items:center;gap:5px;margin-top:2px}
.sc-header-dot{width:7px;height:7px;border-radius:50%;background:#4ade80;flex-shrink:0;animation:sc-pulse 2s infinite}
.sc-header-close{background:rgba(255,255,255,.15);border:none;width:30px;height:30px;border-radius:50%;cursor:pointer;display:flex;align-items:center;justify-content:center;color:#fff;flex-shrink:0;transition:background .12s;font-size:15px;line-height:1}
.sc-header-close:hover{background:rgba(255,255,255,.25)}
/* Body */
.sc-body{flex:1;overflow:hidden;position:relative}
.sc-body-inner{position:absolute;inset:0;overflow-y:auto;padding:16px}
/* Thread wrap — replaces sc-body-inner for the chat view */
.sc-thread-wrap{position:absolute;inset:0;display:none;flex-direction:column}
/* Hero (home view) */
.sc-hero{background:linear-gradient(135deg,#5535dd 0%,#7c3aed 60%,#a855f7 100%);margin:-16px -16px 16px;padding:18px 18px 20px;text-align:center}
.sc-hero-avatar{width:48px;height:48px;border-radius:50%;background:rgba(255,255,255,.2);margin:0 auto 10px;display:flex;align-items:center;justify-content:center;font-size:22px}
.sc-hero-title{font-size:14px;font-weight:700;color:#fff;margin-bottom:4px}
.sc-hero-sub{font-size:12px;color:rgba(255,255,255,.8)}
.sc-sla{display:inline-flex;align-items:center;gap:5px;background:rgba(255,255,255,.15);border:1px solid rgba(255,255,255,.2);border-radius:20px;padding:4px 12px;font-size:11px;font-weight:600;color:rgba(255,255,255,.9);margin-top:10px}
/* Conversations list */
.sc-section-label{font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:.5px;color:var(--text-muted);margin-bottom:8px}
.sc-conv-list{display:flex;flex-direction:column;gap:6px;margin-bottom:14px}
.sc-conv-item{display:flex;align-items:center;gap:10px;background:var(--surface2);border:1px solid var(--border);border-radius:12px;padding:12px 14px;cursor:pointer;transition:border-color .14s,background .14s}
.sc-conv-item:hover{border-color:rgba(139,126,255,.5);background:rgba(139,126,255,.06)}
.sc-conv-dot{width:8px;height:8px;border-radius:50%;background:var(--accent);flex-shrink:0;animation:sc-pulse 2s infinite;display:none}
.sc-conv-dot.active{display:block}
.sc-conv-body{flex:1;min-width:0}
.sc-conv-subject{font-size:13px;font-weight:600;color:var(--text);overflow:hidden;text-overflow:ellipsis;white-space:nowrap;margin-bottom:2px}
.sc-conv-email{font-size:10px;color:var(--text-muted);margin-bottom:2px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.sc-conv-preview{font-size:11px;color:var(--text-muted);overflow:hidden;text-overflow:ellipsis;white-space:nowrap;margin-bottom:5px}
.sc-conv-footer{display:flex;align-items:center;gap:6px;flex-wrap:wrap}
.sc-conv-arrow{color:var(--text-dim);font-size:16px;flex-shrink:0}
.sc-new-conv{width:100%;padding:11px;background:var(--accent);border:none;border-radius:10px;color:#fff;font-size:13px;font-weight:700;cursor:pointer;display:flex;align-items:center;justify-content:center;gap:7px;transition:filter .14s;margin-top:2px}
.sc-new-conv:hover{filter:brightness(1.1)}
/* Thread info bar */
.sc-thread-info{flex-shrink:0;padding:12px 14px;border-bottom:1px solid var(--border);background:var(--surface)}
.sc-thread-subject{font-size:13px;font-weight:700;color:var(--text);margin-bottom:7px;line-height:1.3;overflow:hidden;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical}
.sc-thread-meta{display:flex;align-items:center;gap:6px;flex-wrap:wrap}
.sc-resolve-btn{margin-left:auto;flex-shrink:0;font-size:11px;font-weight:700;color:var(--accent);background:var(--accent-dim);border:1px solid rgba(139,126,255,.3);border-radius:6px;padding:3px 10px;cursor:pointer;transition:background .12s;white-space:nowrap}
.sc-resolve-btn:hover{background:rgba(139,126,255,.2)}
/* Messages area */
.sc-msg-area{flex:1;overflow-y:auto;padding:14px;display:flex;flex-direction:column;gap:10px;scroll-behavior:smooth}
/* Chat bubbles */
.sc-msg{display:flex;flex-direction:column;max-width:86%}
.sc-msg-me{align-self:flex-end;align-items:flex-end}
.sc-msg-other{align-self:flex-start;align-items:flex-start;flex-direction:row;gap:8px;max-width:90%}
.sc-msg-other-inner{display:flex;flex-direction:column;align-items:flex-start;flex:1;min-width:0}
.sc-bubble{padding:10px 13px;border-radius:16px;font-size:13px;line-height:1.55;white-space:pre-wrap;word-break:break-word}
.sc-bubble-me{background:linear-gradient(135deg,#5535dd,#7c3aed);color:#fff;border-bottom-right-radius:4px}
.sc-bubble-other{background:var(--surface2);border:1px solid var(--border);color:var(--text);border-bottom-left-radius:4px}
.sc-msg-meta{font-size:10px;color:var(--text-dim);margin-top:4px;padding:0 2px}
.sc-msg-avatar{width:28px;height:28px;border-radius:50%;background:linear-gradient(135deg,#5535dd,#a855f7);color:#fff;font-size:11px;font-weight:700;display:flex;align-items:center;justify-content:center;flex-shrink:0;margin-top:2px}
/* Waiting / typing indicator */
.sc-waiting{align-self:flex-start;display:flex;align-items:center;gap:10px;padding:10px 14px;background:var(--surface2);border:1px solid var(--border);border-radius:16px;border-bottom-left-radius:4px}
.sc-waiting-text{font-size:12px;color:var(--text-muted)}
.sc-dots{display:flex;gap:4px;align-items:center}
.sc-dot{width:6px;height:6px;border-radius:50%;background:var(--text-dim);animation:sc-bounce .9s infinite}
.sc-dot:nth-child(2){animation-delay:.15s}
.sc-dot:nth-child(3){animation-delay:.3s}
@keyframes sc-bounce{0%,80%,100%{transform:translateY(0);opacity:.4}40%{transform:translateY(-5px);opacity:1}}
/* Compose bar (chat reply) */
.sc-compose-bar{flex-shrink:0;border-top:1px solid var(--border);padding:10px 12px;display:flex;align-items:flex-end;gap:8px;background:var(--surface)}
.sc-reply-textarea{flex:1;background:var(--surface2);border:1.5px solid var(--border);border-radius:10px;color:var(--text);font-size:13px;padding:9px 12px;font-family:inherit;resize:none;min-height:38px;max-height:100px;line-height:1.45;transition:border-color .15s,box-shadow .15s;overflow-y:auto}
.sc-reply-textarea:focus{outline:none;border-color:var(--accent);box-shadow:0 0 0 3px rgba(139,126,255,.12)}
.sc-reply-textarea::placeholder{color:var(--text-dim)}
.sc-reply-send{flex-shrink:0;width:36px;height:36px;border-radius:50%;background:linear-gradient(135deg,#5535dd,#7c3aed);border:none;color:#fff;cursor:pointer;display:flex;align-items:center;justify-content:center;transition:filter .12s,opacity .12s;padding:0}
.sc-reply-send:hover:not(:disabled){filter:brightness(1.1)}
.sc-reply-send:disabled{opacity:.4;cursor:default}
/* Resolved state */
.sc-resolved-note{flex-shrink:0;border-top:1px solid var(--border);padding:10px 14px;text-align:center;font-size:12px;color:var(--text-muted);background:var(--surface)}
.sc-link-btn{background:none;border:none;color:var(--accent);cursor:pointer;font-size:12px;font-weight:600;padding:0}
.sc-link-btn:hover{text-decoration:underline}
/* Status badges */
.sc-status{font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.4px;border-radius:10px;padding:2px 8px;flex-shrink:0}
.sc-status.st-open{background:rgba(251,146,60,.15);color:#fb923c;border:1px solid rgba(251,146,60,.3)}
.sc-status.st-acknowledged{background:var(--accent-dim);color:var(--accent);border:1px solid rgba(139,126,255,.3)}
.sc-status.st-resolved{background:rgba(107,114,128,.1);color:var(--text-muted);border:1px solid rgba(107,114,128,.2)}
.sc-status.cat-bug{background:rgba(239,68,68,.15);color:#f87171;border:1px solid rgba(239,68,68,.25)}
.sc-status.cat-question{background:var(--accent-dim);color:var(--accent);border:1px solid rgba(139,126,255,.25)}
.sc-status.cat-feature{background:rgba(167,139,250,.12);color:#a78bfa;border:1px solid rgba(167,139,250,.25)}
.sc-status.cat-billing{background:rgba(163,230,53,.1);color:#a3e635;border:1px solid rgba(163,230,53,.2)}
/* Compose form (new ticket) */
.sc-compose-title{font-size:14px;font-weight:700;color:var(--text);margin-bottom:14px}
.sc-field{margin-bottom:14px}
.sc-label{display:flex;align-items:center;justify-content:space-between;font-size:11px;font-weight:600;color:var(--text-muted);text-transform:uppercase;letter-spacing:.4px;margin-bottom:6px}
.sc-counter{font-size:11px;color:var(--text-dim);font-weight:400;text-transform:none;letter-spacing:0}
.sc-counter.warn{color:#f59e0b}
.sc-input,.sc-select,.sc-textarea{width:100%;background:var(--surface2);border:1.5px solid var(--border);border-radius:8px;color:var(--text);font-size:13px;padding:9px 11px;box-sizing:border-box;font-family:inherit;transition:border-color .15s,box-shadow .15s}
.sc-input:focus,.sc-select:focus,.sc-textarea:focus{outline:none;border-color:var(--accent);box-shadow:0 0 0 3px rgba(139,126,255,.15)}
.sc-input.sc-err,.sc-textarea.sc-err{border-color:#ef4444}
.sc-input.sc-err:focus,.sc-textarea.sc-err:focus{box-shadow:0 0 0 3px rgba(239,68,68,.15)}
.sc-ferr{font-size:11px;color:#ef4444;margin-top:4px;min-height:16px}
.sc-textarea{resize:vertical;min-height:100px;max-height:180px;line-height:1.55}
.sc-select{appearance:none;background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6'%3E%3Cpath d='M0 0l5 6 5-6z' fill='%2371717a'/%3E%3C/svg%3E");background-repeat:no-repeat;background-position:right 11px center;padding-right:30px}
.sc-send{width:100%;padding:11px;background:linear-gradient(135deg,#5535dd,#7c3aed);border:none;border-radius:10px;color:#fff;font-size:13px;font-weight:700;cursor:pointer;display:flex;align-items:center;justify-content:center;gap:8px;transition:filter .14s,opacity .14s;margin-top:4px}
.sc-send:hover:not(:disabled){filter:brightness(1.1)}
.sc-send:disabled{opacity:.5;cursor:default}
.sc-spinner{width:14px;height:14px;border:2px solid rgba(255,255,255,.3);border-top-color:#fff;border-radius:50%;animation:sc-spin .7s linear infinite}
@keyframes sc-spin{to{transform:rotate(360deg)}}
/* Sent confirmation */
.sc-sent{text-align:center;padding:28px 16px}
.sc-sent-icon{font-size:42px;margin-bottom:12px;animation:sc-pop .35s cubic-bezier(.34,1.56,.64,1)}
@keyframes sc-pop{from{transform:scale(0)}to{transform:scale(1)}}
.sc-sent-title{font-size:16px;font-weight:700;color:var(--text);margin-bottom:6px}
.sc-sent-sub{font-size:12px;color:var(--text-muted);line-height:1.6;margin-bottom:18px}
.sc-sent-ref{font-family:monospace;font-size:11px;background:var(--surface2);border:1px solid var(--border);padding:5px 12px;border-radius:6px;color:var(--accent);display:inline-block;margin-bottom:18px}
.sc-sent-btns{display:flex;gap:8px;justify-content:center}
.sc-btn-ghost{font-size:12px;font-weight:600;padding:8px 16px;border-radius:8px;cursor:pointer;border:1px solid var(--border);background:transparent;color:var(--text-muted);transition:background .12s}
.sc-btn-ghost:hover{background:var(--surface2)}
.sc-btn-accent{font-size:12px;font-weight:700;padding:8px 16px;border-radius:8px;cursor:pointer;border:none;background:linear-gradient(135deg,#5535dd,#7c3aed);color:#fff;transition:filter .12s}
.sc-btn-accent:hover{filter:brightness(1.1)}
/* Empty / Loading */
.sc-empty{text-align:center;padding:40px 16px;color:var(--text-muted);font-size:13px;line-height:1.7}
.sc-empty-icon{font-size:34px;margin-bottom:10px;opacity:.6}
.sc-loading{text-align:center;padding:32px;color:var(--text-muted);font-size:13px}
"""

internal val SUPPORT_CHAT_JS = """(function(){
  var BASE='/api/support';
  var _open=false,_view='home',_convId=null,_convs=null,_thread=null,_loaded=false,_isAdmin=false,_submitting=false,_lastSent=null;
  var _unread={},_sseCtrl=null,_pollTimer=null,_sseDelay=1000,_sseStop=false,_audioCtx=null;
  var _adminOnline=null,_presenceTimer=null,_lastEventId='';
  var CAT_LABELS={'bug':'Bug report','question':'Question','feature':'Feature request','billing':'Billing'};
  var CAT_EMOJIS={'bug':'🐛','question':'💬','feature':'✨','billing':'💳'};

  function H(){return window.authHeaders?window.authHeaders():{'Content-Type':'application/json'};}
  function esc(s){var d=document.createElement('div');d.textContent=String(s==null?'':s);return d.innerHTML;}
  function ago(ms){
    var diff=Date.now()-ms,m=Math.floor(diff/60000),h=Math.floor(m/60),dy=Math.floor(h/24);
    if(m<1)return 'just now';if(m<60)return m+'m ago';if(h<24)return h+'h ago';
    if(dy<7)return dy+'d ago';
    return new Date(ms).toLocaleDateString(undefined,{month:'short',day:'numeric'});
  }
  function statusLabel(st){
    if(st==='open')return 'Open';if(st==='acknowledged')return 'In progress';if(st==='resolved')return 'Resolved';return st;
  }

  // ── Ting sound ──────────────────────────────────────────────────────────────
  // Reuse a single AudioContext — browsers cap concurrent contexts (~6) and
  // creating one per ting throws on bursts of rapid messages.
  function playTing(){
    try{
      var AC=window.AudioContext||window.webkitAudioContext;if(!AC)return;
      if(!_audioCtx)_audioCtx=new AC();
      var ctx=_audioCtx;
      if(ctx.state==='suspended'){try{ctx.resume();}catch(_){}}
      var osc=ctx.createOscillator(),gain=ctx.createGain();
      osc.connect(gain);gain.connect(ctx.destination);
      osc.type='sine';
      osc.frequency.setValueAtTime(880,ctx.currentTime);
      osc.frequency.exponentialRampToValueAtTime(660,ctx.currentTime+0.15);
      gain.gain.setValueAtTime(0.25,ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001,ctx.currentTime+0.6);
      osc.start(ctx.currentTime);osc.stop(ctx.currentTime+0.6);
    }catch(_){}
  }

  // ── SSE real-time channel (both user↔admin) ────────────────────────────────
  function scheduleReconnect(){
    _sseCtrl=null;
    if(_sseStop)return;
    var delay=_sseDelay;
    _sseDelay=Math.min(_sseDelay*2,30000);
    setTimeout(startSse,delay);
  }

  function startSse(){
    if(_sseCtrl||_sseStop)return;
    var token=localStorage.getItem('syncling_token');if(!token)return;
    _sseCtrl=new AbortController();
    var headers={'Authorization':'Bearer '+token};
    if(_lastEventId)headers['Last-Event-ID']=_lastEventId;
    fetch('/api/pipeline/events',{headers:headers,signal:_sseCtrl.signal})
      .then(function(res){
        // Auth failures are not retryable — stop the reconnect loop entirely so a
        // logged-out tab doesn't hammer the endpoint every 30s. Other 4xx/5xx fall
        // through to scheduleReconnect with backoff.
        if(res.status===401||res.status===403){_sseStop=true;_sseCtrl=null;return;}
        if(!res.ok){throw new Error('SSE status '+res.status);}
        // Don't reset backoff on headers alone — a server that 200s then immediately
        // closes would pin the delay at 1s. Reset only after the first real frame.
        var reader=res.body.getReader(),decoder=new TextDecoder(),buf='',gotFrame=false;
        function pump(){
          reader.read().then(function(r){
            if(r.done){scheduleReconnect();return;}
            buf+=decoder.decode(r.value,{stream:true});
            var parts=buf.split('\n\n');buf=parts.pop();
            parts.forEach(function(chunk){
              // Each SSE event can have id: and data: lines. Parse both — the id is
              // stashed in _lastEventId so the next reconnect sends Last-Event-ID and
              // gets a replay of anything missed in between.
              var lines=chunk.split('\n'),dataPayload=null,idPayload=null;
              for(var i=0;i<lines.length;i++){
                var L=lines[i];
                if(L.indexOf('data:')===0)dataPayload=L.slice(5).trim();
                else if(L.indexOf('id:')===0)idPayload=L.slice(3).trim();
              }
              if(idPayload)_lastEventId=idPayload;
              if(!dataPayload)return;
              if(!gotFrame){gotFrame=true;_sseDelay=1000;}
              try{
                var evt=JSON.parse(dataPayload);
                if(evt.type==='support_message')onSseMessage(evt);
                else if(evt.type==='support_presence')onSsePresence(evt);
              }catch(_){}
            });
            pump();
          }).catch(function(){scheduleReconnect();});
        }
        pump();
      }).catch(function(){scheduleReconnect();});
  }

  function onSseMessage(evt){
    var tid=evt.supportTicketId;if(!tid)return;
    // Skip own-echo only when role is confirmed; unknown role always processes (harmless extra fetch).
    if(_loaded && _isAdmin && evt.supportSenderType==='admin') return;
    if(_loaded && !_isAdmin && evt.supportSenderType==='user') return;

    if(evt.supportTicketStatus&&_convs)
      _convs.forEach(function(t){if(t.id===tid)t.status=evt.supportTicketStatus;});

    if(_view==='thread'&&_convId===tid){
      // Inline-append when the event carries the full message — zero extra HTTP call.
      if(evt.supportMessageId&&evt.supportMessageContent&&_thread){
        var msgs=_thread.messages=_thread.messages||[];
        var already=msgs.some(function(m){return m.id===evt.supportMessageId;});
        if(!already){
          msgs.push({id:evt.supportMessageId,senderType:evt.supportSenderType,
                     content:evt.supportMessageContent,sentAt:evt.supportMessageSentAt||Date.now()});
          if(evt.supportTicketStatus)_thread.status=evt.supportTicketStatus;
          renderThread(true);renderHeader();
        }
      } else {
        silentRefreshThread(true);
      }
    } else {
      _unread[tid]=true;updateFabDot();
      if(_view==='home'&&_convs)renderBody();
    }
    playTing();
  }

  function silentRefreshThread(isAdminPoll){
    var id=_convId;if(!id)return;
    var prevCount=_thread?(_thread.messages||[]).length:0;
    fetch(BASE+'/'+id,{headers:H()})
      .then(function(r){return r.ok?r.json():Promise.reject(r.status);})
      .then(function(data){
        var gotNew=(data.messages||[]).length>prevCount;
        _thread=data;
        if(_convs)_convs.forEach(function(t){if(t.id===id)t.status=data.status;});
        if(_view==='thread'&&_convId===id){renderThread(false);if(gotNew&&isAdminPoll)playTing();}
      }).catch(function(){});
  }

  // ── Admin presence (SSE-driven, with a slow polling fallback) ──────────────
  // The server pushes support_presence over SSE the instant an admin connects or
  // the last admin disconnects, so the indicator updates in real-time. The poll
  // below only catches stale connections (e.g. a tab that slept past the SSE
  // backoff) — 5 min is plenty.
  function onSsePresence(evt){
    if(_isAdmin)return;
    var next=!!evt.supportAdminOnline;
    if(_adminOnline!==next){_adminOnline=next;renderHeader();}
  }
  function fetchPresence(){
    if(_isAdmin)return;
    fetch(BASE+'/presence',{headers:H()})
      .then(function(r){return r.ok?r.json():null;})
      .then(function(d){
        if(!d)return;
        var next=!!d.adminOnline;
        if(_adminOnline!==next){_adminOnline=next;renderHeader();}
      }).catch(function(){});
  }
  function startPresencePoll(){
    if(_presenceTimer||_isAdmin)return;
    _presenceTimer=setInterval(fetchPresence,300000);  // 5min reconciliation only
  }
  function stopPresencePoll(){
    if(_presenceTimer){clearInterval(_presenceTimer);_presenceTimer=null;}
  }

  // ── Home view ───────────────────────────────────────────────────────────────
  function viewHome(){
    var h='<div class="sc-hero">'+
      '<div class="sc-hero-avatar">💬</div>'+
      '<div class="sc-hero-title">'+(_isAdmin?'🛠 Support Inbox':'Syncling Support')+'</div>'+
      '<div class="sc-hero-sub">'+(_isAdmin?'All customer conversations':'We\'re here to help you out')+'</div>'+
      (!_isAdmin?'<div class="sc-sla">⏱ We reply within 24 hours</div>':'')+
    '</div>';
    if(!_loaded){
      h+='<div class="sc-loading">Loading conversations…</div>';
    } else if(_convs&&_convs.length){
      h+='<div class="sc-section-label">'+(_isAdmin?'All tickets':'Your conversations')+'</div><div class="sc-conv-list">';
      _convs.forEach(function(t){
        var preview=t.lastMessage||'';var hasNew=!!_unread[t.id];
        var timeMs=t.updatedAt||t.createdAt;
        h+='<div class="sc-conv-item" onclick="window._scThread(\''+esc(t.id)+'\')">'+
          '<div class="sc-conv-dot'+(hasNew?' active':'')+'"></div>'+
          '<div class="sc-conv-body">'+
            '<div class="sc-conv-subject">'+esc(t.subject)+'</div>'+
            (_isAdmin&&t.userEmail?'<div class="sc-conv-email">'+esc(t.userEmail)+'</div>':'')+
            '<div class="sc-conv-preview">'+esc(preview.substring(0,70))+(preview.length>70?'…':'')+'</div>'+
            '<div class="sc-conv-footer">'+
              '<span class="sc-status st-'+esc(t.status)+'">'+statusLabel(t.status)+'</span>'+
              '<span style="font-size:10px;color:var(--text-dim);margin-left:4px">'+ago(timeMs)+'</span>'+
            '</div>'+
          '</div>'+
          '<div class="sc-conv-arrow">›</div>'+
        '</div>';
      });
      h+='</div>';
    } else {
      h+='<div class="sc-empty"><div class="sc-empty-icon">💬</div>'+(_isAdmin?'No support tickets yet.':'No conversations yet.<br>Start one below.')+'</div>';
    }
    if(!_isAdmin){
      h+='<button class="sc-new-conv" onclick="window._scCompose()">'+
        '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>'+
        ' New conversation</button>';
    }
    return h;
  }

  // ── Compose view ────────────────────────────────────────────────────────────
  function viewCompose(){
    return '<div class="sc-compose-title">New conversation</div>'+
      '<div class="sc-field"><label class="sc-label">Category</label>'+
        '<select class="sc-select" id="sc-cat">'+
          Object.keys(CAT_LABELS).map(function(k){return '<option value="'+k+'">'+CAT_EMOJIS[k]+' '+CAT_LABELS[k]+'</option>';}).join('')+
        '</select></div>'+
      '<div class="sc-field"><label class="sc-label"><span>Subject</span><span class="sc-counter" id="sc-subj-ctr">0/200</span></label>'+
        '<input class="sc-input" id="sc-subj" type="text" placeholder="Brief description" maxlength="200" autocomplete="off">'+
        '<div class="sc-ferr" id="sc-subj-err"></div></div>'+
      '<div class="sc-field"><label class="sc-label"><span>Message</span><span class="sc-counter" id="sc-msg-ctr">0/5000</span></label>'+
        '<textarea class="sc-textarea" id="sc-msg" placeholder="Describe your issue in detail…" maxlength="5000"></textarea>'+
        '<div class="sc-ferr" id="sc-msg-err"></div></div>'+
      '<button class="sc-send" id="sc-send" onclick="window._scSubmit()" '+(_submitting?'disabled':'')+'>'+
        (_submitting?'<span class="sc-spinner"></span><span>Sending…</span>':'<span>Send message</span>')+
      '</button>';
  }

  // ── Sent confirmation view ──────────────────────────────────────────────────
  function viewSent(){
    var t=_lastSent;if(!t)return '';
    return '<div class="sc-sent">'+
      '<div class="sc-sent-icon">✅</div>'+
      '<div class="sc-sent-title">Message sent!</div>'+
      '<div class="sc-sent-sub">You\'ll see the reply here in real time.</div>'+
      '<div class="sc-sent-ref">#'+esc((t.id||'').substring(0,8))+'</div>'+
      '<div class="sc-sent-btns">'+
        '<button class="sc-btn-ghost" onclick="window._scCompose()">+ New</button>'+
        '<button class="sc-btn-accent" onclick="window._scThread(\''+esc(t.id||'')+'\')">Open chat →</button>'+
      '</div>'+
    '</div>';
  }

  // ── Thread render ───────────────────────────────────────────────────────────
  function renderThread(scroll){
    if(scroll===undefined)scroll=true;
    var panel=document.getElementById('sc-panel');if(!panel)return;
    var body=panel.querySelector('.sc-body');if(!body)return;
    var inner=panel.querySelector('.sc-body-inner');if(inner)inner.style.display='none';
    var wrap=document.getElementById('sc-thread-wrap');
    if(!wrap){wrap=document.createElement('div');wrap.id='sc-thread-wrap';wrap.className='sc-thread-wrap';body.appendChild(wrap);}
    wrap.style.display='flex';
    if(!_thread){
      wrap.innerHTML='<div style="flex:1;display:flex;align-items:center;justify-content:center;color:var(--text-muted);font-size:13px">Loading…</div>';
      return;
    }
    var t=_thread,isResolved=t.status==='resolved';
    var msgs=t.messages||[];

    // Preserve scroll position for silent refresh
    var prevArea=document.getElementById('sc-msg-area');
    var prevTop=prevArea?prevArea.scrollTop:-1,prevH=prevArea?prevArea.scrollHeight:-1,prevCH=prevArea?prevArea.clientHeight:0;

    var msgsHtml='';
    msgs.forEach(function(m){
      var fromUser=m.senderType==='user';
      var fromMe=_isAdmin?!fromUser:fromUser;
      if(fromMe){
        msgsHtml+='<div class="sc-msg sc-msg-me">'+
          '<div class="sc-bubble sc-bubble-me">'+esc(m.content)+'</div>'+
          '<div class="sc-msg-meta">'+(_isAdmin?'You (Support)':'You')+' · '+ago(m.sentAt)+'</div>'+
        '</div>';
      } else {
        msgsHtml+='<div class="sc-msg sc-msg-other">'+
          '<div class="sc-msg-avatar">'+(fromUser?'?':'S')+'</div>'+
          '<div class="sc-msg-other-inner">'+
            '<div class="sc-bubble sc-bubble-other">'+esc(m.content)+'</div>'+
            '<div class="sc-msg-meta">'+(fromUser?'User':'Support')+' · '+ago(m.sentAt)+'</div>'+
          '</div>'+
        '</div>';
      }
    });
    if(!isResolved&&msgs.length<=1&&!_isAdmin){
      msgsHtml+='<div class="sc-waiting"><div class="sc-dots"><div class="sc-dot"></div><div class="sc-dot"></div><div class="sc-dot"></div></div><span class="sc-waiting-text">Support will reply shortly</span></div>';
    }

    wrap.innerHTML=
      '<div class="sc-thread-info">'+
        '<div class="sc-thread-subject">'+esc(t.subject)+'</div>'+
        '<div class="sc-thread-meta">'+
          '<span class="sc-status cat-'+esc(t.category)+'">'+esc(CAT_EMOJIS[t.category]||'')+' '+esc(CAT_LABELS[t.category]||t.category)+'</span>'+
          '<span class="sc-status st-'+esc(t.status)+'">'+statusLabel(t.status)+'</span>'+
          '<span style="flex:1"></span>'+
          (!isResolved?'<button class="sc-resolve-btn" onclick="window._scResolve()">✓ Mark resolved</button>':'')+
        '</div>'+
      '</div>'+
      '<div class="sc-msg-area" id="sc-msg-area">'+msgsHtml+'</div>'+
      (!isResolved?
        '<div class="sc-compose-bar">'+
          '<textarea class="sc-reply-textarea" id="sc-reply-ta" placeholder="Reply… (Ctrl+Enter to send)" rows="1" maxlength="5000"></textarea>'+
          '<button class="sc-reply-send" id="sc-reply-send" onclick="window._scSendReply()" title="Send">'+
            '<svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg>'+
          '</button>'+
        '</div>':
        '<div class="sc-resolved-note">Conversation resolved. <button class="sc-link-btn" onclick="window._scCompose()">Start a new one →</button></div>'
      );

    wireReplyBox();

    var newArea=document.getElementById('sc-msg-area');
    if(newArea){
      if(scroll){newArea.scrollTop=newArea.scrollHeight;}
      else{var wasAtBottom=prevH<0||(prevTop+prevCH+10>=prevH);if(wasAtBottom)newArea.scrollTop=newArea.scrollHeight;else newArea.scrollTop=prevTop;}
    }
  }

  function wireReplyBox(){
    var ta=document.getElementById('sc-reply-ta');if(!ta)return;
    ta.addEventListener('input',function(){this.style.height='auto';this.style.height=Math.min(this.scrollHeight,100)+'px';});
    ta.addEventListener('keydown',function(e){if((e.ctrlKey||e.metaKey)&&e.key==='Enter'){e.preventDefault();window._scSendReply();}});
    setTimeout(function(){if(ta)ta.focus();},50);
  }

  // ── Shared render helpers ───────────────────────────────────────────────────
  function setThreadMode(on){
    var panel=document.getElementById('sc-panel');if(!panel)return;
    var inner=panel.querySelector('.sc-body-inner');
    var wrap=document.getElementById('sc-thread-wrap');
    if(on){if(inner)inner.style.display='none';if(wrap)wrap.style.display='flex';}
    else{if(inner)inner.style.display='';if(wrap)wrap.style.display='none';}
  }

  function renderBody(){
    setThreadMode(false);
    var panel=document.getElementById('sc-panel');if(!panel)return;
    var inner=panel.querySelector('.sc-body-inner');if(!inner)return;
    var html='';
    if(_view==='home')html=viewHome();
    else if(_view==='compose')html=viewCompose();
    else if(_view==='sent')html=viewSent();
    inner.innerHTML=html;
    wireFormListeners();
    updateFabDot();
  }

  function wireFormListeners(){
    var subj=document.getElementById('sc-subj'),msg=document.getElementById('sc-msg');
    if(subj){
      subj.addEventListener('input',function(){var c=document.getElementById('sc-subj-ctr');if(c){c.textContent=this.value.length+'/200';c.classList.toggle('warn',this.value.length>170);}});
      subj.addEventListener('blur',function(){var e=document.getElementById('sc-subj-err');if(e)e.textContent=this.value.trim()?'':'Subject is required';this.classList.toggle('sc-err',!this.value.trim());});
    }
    if(msg){
      msg.addEventListener('input',function(){var c=document.getElementById('sc-msg-ctr');if(c){c.textContent=this.value.length+'/5000';c.classList.toggle('warn',this.value.length>4250);}});
      msg.addEventListener('blur',function(){var e=document.getElementById('sc-msg-err');if(e)e.textContent=this.value.trim()?'':'Message is required';this.classList.toggle('sc-err',!this.value.trim());});
    }
  }

  function renderHeader(){
    var panel=document.getElementById('sc-panel');if(!panel)return;
    var back=panel.querySelector('.sc-header-back'),nameEl=panel.querySelector('.sc-header-name'),statusEl=panel.querySelector('.sc-header-status');
    if(back)back.classList.toggle('visible',_view!=='home');
    if(nameEl){
      if(_view==='thread'&&_thread)nameEl.textContent=_thread.subject||'Conversation';
      else if(_view==='compose')nameEl.textContent='New conversation';
      else nameEl.textContent=_isAdmin?'🛠 Support Inbox':'Syncling Support';
    }
    if(statusEl){
      if(_view==='thread'&&_thread){
        statusEl.innerHTML='<span class="sc-status st-'+esc(_thread.status)+'" style="font-size:10px">'+statusLabel(_thread.status)+'</span>';
      } else if(_isAdmin){
        statusEl.innerHTML='<div class="sc-header-dot"></div>Admin view';
      } else {
        var online=_adminOnline===true, offline=_adminOnline===false;
        var dotStyle=online?'':(offline?'background:#71717a;animation:none':'');
        var label=online?'Support is online':(offline?"Offline — we'll reply within 24h":'Usually reply within 24h');
        statusEl.innerHTML='<div class="sc-header-dot" style="'+dotStyle+'"></div>'+label;
      }
    }
  }

  function updateFabDot(){
    var dot=document.getElementById('sc-fab-dot');if(!dot)return;
    var any=Object.keys(_unread).some(function(k){return _unread[k];});
    dot.classList.toggle('show',any);
  }

  // ── Navigation ──────────────────────────────────────────────────────────────
  function navigate(newView,newId){
    _view=newView;_convId=newId;
    if(newView==='thread'){
      _thread=null;renderHeader();setThreadMode(true);renderThread(true);
      fetch(BASE+'/'+newId,{headers:H()})
        .then(function(r){return r.ok?r.json():Promise.reject(r.status);})
        .then(function(data){
          _thread=data;_unread[newId]=false;
          if(_convs)_convs.forEach(function(t){if(t.id===newId)t.status=data.status;});
          renderThread(true);renderHeader();updateFabDot();
        }).catch(function(){});
    } else {
      renderBody();renderHeader();
    }
  }

  // ── Public API ──────────────────────────────────────────────────────────────
  window._scHome=function(){navigate('home',null);_loaded=false;loadConvs();};
  window._scThread=function(id){navigate('thread',id);};
  window._scCompose=function(){navigate('compose',null);};
  window._scToggle=function(){
    _open=!_open;
    var panel=document.getElementById('sc-panel');
    if(panel)panel.classList.toggle('open',_open);
    if(_open){if(!_loaded)loadConvs();}
  };
  window._scClose=function(){
    _open=false;
    var panel=document.getElementById('sc-panel');if(panel)panel.classList.remove('open');
  };
  window._scBack=function(){if(_view!=='home')window._scHome();};

  window._scSubmit=function(){
    var cat=document.getElementById('sc-cat'),subj=document.getElementById('sc-subj'),msg=document.getElementById('sc-msg');
    if(!subj||!msg)return;
    var sv=subj.value.trim(),mv=msg.value.trim(),valid=true;
    var se=document.getElementById('sc-subj-err'),me=document.getElementById('sc-msg-err');
    if(!sv){if(se)se.textContent='Subject is required';subj.classList.add('sc-err');valid=false;}
    if(!mv){if(me)me.textContent='Message is required';msg.classList.add('sc-err');valid=false;}
    if(!valid)return;
    _submitting=true;renderBody();
    fetch(BASE,{method:'POST',headers:H(),body:JSON.stringify({category:cat?cat.value:'question',subject:sv,message:mv})})
      .then(function(r){return r.ok?r.json():r.json().then(function(e){throw new Error(e.error||'Failed');});})
      .then(function(d){
        _submitting=false;_lastSent=d;_loaded=false;_convs=null;
        navigate('sent',null);
      })
      .catch(function(e){_submitting=false;renderBody();if(window.toast)toast(e.message||'Failed to send.','error');});
  };

  window._scSendReply=function(){
    var ta=document.getElementById('sc-reply-ta');if(!ta)return;
    var content=ta.value.trim();if(!content)return;
    var btn=document.getElementById('sc-reply-send');
    if(btn)btn.disabled=true;ta.disabled=true;
    fetch(BASE+'/'+_convId+'/messages',{method:'POST',headers:H(),body:JSON.stringify({content:content})})
      .then(function(r){return r.ok?r.json():r.json().then(function(e){throw new Error(e.error||'Failed');});})
      .then(function(msg){
        if(_thread){
          _thread.messages=_thread.messages||[];_thread.messages.push(msg);
          if(msg.senderType==='admin'&&_thread.status==='open')_thread.status='acknowledged';
        }
        ta.value='';ta.style.height='auto';ta.disabled=false;if(btn)btn.disabled=false;
        renderThread(true);renderHeader();
      })
      .catch(function(e){ta.disabled=false;if(btn)btn.disabled=false;if(window.toast)toast(e.message||'Failed to send.','error');});
  };

  window._scResolve=function(){
    if(!_convId)return;
    if(!confirm('Mark this conversation as resolved?'))return;
    fetch(BASE+'/'+_convId+'/resolve',{method:'POST',headers:H()})
      .then(function(r){return r.ok?r.json():Promise.reject(r.status);})
      .then(function(){
        if(_thread)_thread.status='resolved';
        if(_convs)_convs.forEach(function(t){if(t.id===_convId)t.status='resolved';});
        renderThread(false);renderHeader();
      })
      .catch(function(){if(window.toast)toast('Failed to resolve. Please try again.','error');});
  };

  function loadConvs(){
    _loaded=false;if(_view==='home')renderBody();
    fetch(BASE,{headers:H()})
      .then(function(r){return r.ok?r.json():Promise.reject('HTTP '+r.status);})
      .then(function(d){_loaded=true;_convs=d.tickets||[];_isAdmin=!!d.isAdmin;var p=document.getElementById('sc-panel');if(p)p.classList.toggle('sc-admin',_isAdmin);if(_view==='home')renderBody();renderHeader();updateFabDot();if(!_isAdmin)startPresencePoll();})
      .catch(function(){_loaded=true;_convs=[];if(_view==='home')renderBody();});
  }

  function mount(){
    if(document.getElementById('sc-fab'))return;
    var fab=document.createElement('button');
    fab.id='sc-fab';fab.className='sc-fab';
    fab.setAttribute('aria-label','Help & Support');fab.setAttribute('title','Help & support');
    fab.innerHTML='<svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H5.17L4 17.17V4h16v12z"/></svg>'+
      '<div class="sc-fab-dot" id="sc-fab-dot"></div>';
    fab.onclick=window._scToggle;document.body.appendChild(fab);

    var panel=document.createElement('div');
    panel.id='sc-panel';panel.className='sc-panel';
    panel.setAttribute('role','dialog');panel.setAttribute('aria-modal','true');panel.setAttribute('aria-label','Help and Support');
    panel.innerHTML=
      '<div class="sc-header">'+
        '<button class="sc-header-back" onclick="window._scBack()" aria-label="Go back">&#8592;</button>'+
        '<div class="sc-header-brand">'+
          '<div class="sc-header-name">Syncling Support</div>'+
          '<div class="sc-header-status"><div class="sc-header-dot"></div>Usually reply within 24h</div>'+
        '</div>'+
        '<button class="sc-header-close" onclick="window._scClose()" aria-label="Close">&#x2715;</button>'+
      '</div>'+
      '<div class="sc-body"><div class="sc-body-inner"></div></div>';
    document.body.appendChild(panel);
    renderBody();
    startSse();
    // Preload tickets so admin status (and drawer variant) is applied before first open.
    loadConvs();
  }

  document.addEventListener('keydown',function(e){if(e.key==='Escape'&&_open)window._scClose();});
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',mount);
  else mount();
})();"""
