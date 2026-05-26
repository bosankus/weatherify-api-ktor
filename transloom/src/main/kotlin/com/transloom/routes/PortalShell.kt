package com.transloom.routes

import kotlinx.html.*

/**
 * Authoritative HTML shell for every authenticated portal page (billing,
 * projects, review, dashboard). View files supply only the main-content
 * block — the shell takes care of <head>, sidebar, notification panel,
 * onboarding host, and bootstrapping the bearer token from the short-lived
 * cookie issued by [ApplicationCall.issueBootstrapCookie].
 *
 * @param pageTitle           Title shown in the browser tab — prefixed with "Transloom — ".
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
        title { +"Transloom — $pageTitle" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        favicon()
        style { unsafe { +"$SHARED_CSS$SHELL_LAYOUT_CSS$SIDEBAR_QUOTA_CSS$CONVERSION_CSS$ONBOARDING_CSS" } }
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
.sidebar{width:220px;flex-shrink:0;background:var(--surface);border-right:1px solid var(--border);display:flex;flex-direction:column;padding:20px 0}
.sidebar-logo{font-size:16px;font-weight:700;padding:2px 20px 18px;border-bottom:1px solid var(--border);margin-bottom:12px;color:var(--text);gap:10px!important}
.sidebar-logo span{color:var(--text)}
.sidebar-nav{flex:1;display:flex;flex-direction:column;gap:2px;padding:0 10px}
.nav-item{display:flex;align-items:center;justify-content:space-between;padding:9px 12px;border-radius:var(--radius-sm);color:var(--text-muted);font-size:13px;transition:all .12s}
.nav-item:hover{background:var(--surface2);color:var(--text)}
.nav-item.active{background:var(--accent-dim);color:var(--accent);font-weight:500}
.nav-badge{font-size:11px;font-weight:700;border-radius:10px;padding:1px 7px;min-width:20px;text-align:center}
.review-badge{background:var(--accent-dim);color:var(--accent);display:none}
.sidebar-footer{padding:16px 16px 0;border-top:1px solid var(--border);display:flex;flex-direction:column;gap:8px}
.user-chip{display:flex;align-items:center;gap:8px;padding:6px 8px;background:var(--surface2);border:1px solid var(--border);border-radius:var(--radius-sm);min-width:0}
.user-avatar{width:26px;height:26px;border-radius:50%;background:var(--accent-dim);color:var(--accent);font-size:11px;font-weight:700;display:flex;align-items:center;justify-content:center;flex-shrink:0;text-transform:uppercase;letter-spacing:0}
.user-name{font-size:12px;color:var(--text);font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;min-width:0;flex:1}
.logout-btn{width:100%;font-size:12px;padding:7px 12px}
.main-content{flex:1;overflow-y:auto;padding:28px 32px}
.page-header{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:24px;gap:12px}
.page-title{font-size:26px;font-weight:700;letter-spacing:-.5px;line-height:1.2;margin-bottom:3px}
.page-sub{font-size:13px;color:var(--text-muted)}
"""

private val SHELL_RUNTIME_JS = """
(function(){
  var token=localStorage.getItem('transloom_token');
  if(!token){
    var m=document.cookie.match(/(?:^|;\s*)tl_token_bootstrap=([^;]*)/);
    if(m&&m[1]){token=decodeURIComponent(m[1]);localStorage.setItem('transloom_token',token);}
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
    localStorage.removeItem('transloom_token');
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
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',fillUserChip);
  else fillUserChip();
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
.sb-quota-cta{display:block;text-align:center;font-size:11px;font-weight:600;color:var(--accent);padding:6px 8px;border:1px solid rgba(0,229,160,.3);border-radius:5px;transition:background .15s,border-color .15s}
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
  function H(){var t=localStorage.getItem('transloom_token');return t?{'Authorization':'Bearer '+t,'Content-Type':'application/json'}:{'Content-Type':'application/json'};}
  var _s=null,_u=null;
  window.tlSubscription=function(f){if(f||!_s){_s=fetch('/transloom/api/billing/subscription',{headers:H()}).then(function(r){return r.ok?r.json():null}).catch(function(){return null});}return _s;};
  window.tlUsage=function(f){if(f||!_u){_u=fetch('/transloom/api/billing/usage',{headers:H()}).then(function(r){return r.ok?r.json():null}).catch(function(){return null});}return _u;};
})();
"""

internal val SIDEBAR_QUOTA_JS = """
(function(){
  var PRICE={FREE:0,SOLO:499,TEAM:1999};
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
  function boot(){
    var host=document.getElementById('sb-quota');
    if(!host||!window.tlSubscription||!window.tlUsage)return;
    Promise.all([window.tlSubscription(),window.tlUsage()]).then(function(r){render(host,r[0],r[1]);});
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
.conv-toast{position:fixed;bottom:24px;left:50%;transform:translate(-50%,8px);background:var(--surface);border:1px solid var(--accent);border-radius:var(--radius);padding:12px 16px 12px 18px;display:flex;align-items:center;gap:14px;box-shadow:0 12px 36px -8px rgba(0,229,160,.35),0 4px 16px rgba(0,0,0,.4);z-index:9500;opacity:0;transition:opacity .25s,transform .25s;max-width:560px;font-size:13px;color:var(--text)}
.conv-toast.visible{opacity:1;transform:translate(-50%,0)}
.conv-toast .conv-msg{flex:1;line-height:1.45}
.conv-toast .conv-msg strong{color:var(--accent);font-weight:700}
.conv-toast .conv-cta{font-size:12px;font-weight:700;color:#000;background:var(--accent);padding:7px 14px;border-radius:6px;white-space:nowrap;transition:filter .12s}
.conv-toast .conv-cta:hover{filter:brightness(1.08)}
.conv-toast .conv-close{background:transparent;border:none;color:var(--text-muted);font-size:18px;line-height:1;cursor:pointer;padding:2px 4px}
.conv-toast .conv-close:hover{color:var(--text)}
.pr-locked-section{margin-top:8px}
.pr-locked-row{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;padding:12px;background:var(--surface2);border:1px dashed var(--border);border-radius:var(--radius-sm);margin-bottom:8px;opacity:.85;transition:opacity .15s,border-color .15s}
.pr-locked-row:hover{opacity:1;border-color:rgba(0,229,160,.3)}
.pr-locked-info{flex:1;min-width:0}
.pr-locked-title{font-size:13px;font-weight:600;color:var(--text);margin-bottom:3px;display:flex;align-items:center;gap:6px}
.pr-locked-badge{font-size:10px;font-weight:700;letter-spacing:.4px;text-transform:uppercase;color:var(--accent);background:var(--accent-dim);border:1px solid rgba(0,229,160,.3);border-radius:10px;padding:1px 7px}
.pr-locked-hint{font-size:12px;color:var(--text-muted);line-height:1.45}
.pr-locked-cta{font-size:11px;font-weight:600;color:var(--accent);white-space:nowrap;padding:6px 12px;border:1px solid rgba(0,229,160,.3);border-radius:5px;transition:background .15s}
.pr-locked-cta:hover{background:var(--accent-dim)}
"""
