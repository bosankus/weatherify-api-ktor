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
        style { unsafe { +"$SHARED_CSS$ONBOARDING_CSS" } }
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
private val SHELL_RUNTIME_JS = """
(function(){
  var token=localStorage.getItem('transloom_token');
  if(!token){
    var m=document.cookie.match(/(?:^|;\s*)tl_token_bootstrap=([^;]*)/);
    if(m&&m[1]){token=decodeURIComponent(m[1]);localStorage.setItem('transloom_token',token);}
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
})();
"""
