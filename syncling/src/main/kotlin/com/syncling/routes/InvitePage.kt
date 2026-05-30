package com.syncling.routes

import kotlinx.html.*

/**
 * Public landing page for invite links (/transloom/invite/{token}).
 *
 * Server renders a chrome-less card; the client (invite.js) reads the token
 * from the URL, fetches GET /transloom/api/invites/{token} for the preview,
 * and either POSTs accept (if a session token is present in localStorage) or
 * sends the user through GitHub OAuth — saving the token to localStorage so
 * the post-login dashboard can complete the accept automatically.
 *
 * Kept outside the portalShell because the invitee may not have an account
 * yet and the dark portal chrome would be misleading.
 */
internal fun HTML.invitePage() {
    head {
        title { +"Syncling — You've been invited" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        favicon()
        style { unsafe { +"$SHARED_CSS$INVITE_CSS" } }
    }
    body {
        nav("inv-nav") {
            div("inv-nav-inner") {
                a("/transloom") {
                    classes = setOf("brand", "inv-brand")
                    unsafe { +LOGO_SVG }
                    span { +"Syncling" }
                }
            }
        }

        main("inv-main") {
            div("inv-card") {
                id = "inv-card"
                // Skeleton placeholder until invite.js loads the preview.
                div("inv-skeleton") {
                    div("inv-sk-line inv-sk-line-md")
                    div("inv-sk-line inv-sk-line-lg")
                    div("inv-sk-line inv-sk-line-sm")
                }
            }
            p("inv-foot") {
                +"Not expecting this invite? You can safely close this tab."
            }
        }

        div { id = "toast"; classes = setOf("toast") }

        // Minimal shell runtime — invite.js only needs toast() and a fetch wrapper that
        // doesn't error when no token is present.
        script { unsafe { +INVITE_SHELL_JS } }
        script {
            src = "/transloom/static/invite.js"
            defer = true
        }
    }
}

private const val LOGO_SVG = """
<svg class="brand-mark" viewBox="0 0 32 32" width="26" height="26" aria-hidden="true" focusable="false">
  <defs>
    <linearGradient id="invTlmGrad" x1="0" y1="0" x2="32" y2="32" gradientUnits="userSpaceOnUse">
      <stop offset="0%" stop-color="#A890FF"/>
      <stop offset="100%" stop-color="#5535DD"/>
    </linearGradient>
  </defs>
  <rect x="0" y="0" width="32" height="32" rx="8" fill="url(#invTlmGrad)"/>
  <path d="M8.5 10.5 H23.5" stroke="#0a0a0a" stroke-width="2.8" stroke-linecap="round"/>
  <path d="M16 10.5 V23" stroke="#0a0a0a" stroke-width="2.8" stroke-linecap="round"/>
  <path d="M10 18.5 Q13 16.5 16 18.5 T22 18.5" stroke="#0a0a0a" stroke-width="2" stroke-linecap="round" fill="none" opacity="0.55"/>
</svg>
"""

private const val INVITE_CSS = """
.inv-nav{padding:20px 24px;border-bottom:1px solid var(--border)}
.inv-nav-inner{max-width:960px;margin:0 auto;display:flex;align-items:center}
.inv-brand{display:flex;align-items:center;gap:10px;color:var(--text);font-weight:700;font-size:16px}
.inv-brand span{color:var(--text)}

.inv-main{min-height:calc(100vh - 70px);display:flex;flex-direction:column;align-items:center;justify-content:center;padding:48px 24px 80px;gap:18px}
.inv-card{background:var(--surface);border:1px solid var(--border);border-radius:14px;padding:36px 32px;width:100%;max-width:460px;box-shadow:0 20px 60px rgba(0,0,0,.4)}
.inv-foot{color:var(--text-muted);font-size:12px;text-align:center;max-width:460px}

.inv-eyebrow{display:inline-flex;align-items:center;gap:6px;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:var(--accent);background:var(--accent-dim);padding:5px 10px;border-radius:20px;margin-bottom:18px}
.inv-title{font-size:22px;font-weight:700;line-height:1.3;letter-spacing:-.3px;margin:0 0 6px;color:var(--text)}
.inv-sub{color:var(--text-muted);font-size:14px;line-height:1.6;margin:0 0 22px}

.inv-meta{background:var(--surface2);border:1px solid var(--border);border-radius:var(--radius-sm);padding:14px 16px;margin-bottom:22px;display:grid;grid-template-columns:90px 1fr;gap:10px 12px;font-size:13px}
.inv-meta dt{color:var(--text-muted);font-weight:500}
.inv-meta dd{color:var(--text);font-weight:600;margin:0;word-break:break-word}
.inv-role-pill{display:inline-flex;align-items:center;padding:2px 10px;border-radius:20px;background:var(--accent-dim);color:var(--accent);font-size:12px;font-weight:700;letter-spacing:.3px;text-transform:uppercase}

.inv-btn{display:inline-flex;align-items:center;justify-content:center;gap:8px;width:100%;background:var(--accent);color:#051a13;border:1px solid var(--accent);border-radius:var(--radius-sm);padding:12px 18px;font-size:14px;font-weight:700;cursor:pointer;font-family:inherit;transition:background .15s,transform .15s,box-shadow .15s}
.inv-btn:hover{background:#7A6DEE;transform:translateY(-1px);box-shadow:0 8px 24px -8px rgba(139,126,255,.45)}
.inv-btn[disabled]{opacity:.6;cursor:not-allowed;transform:none;box-shadow:none}
.inv-btn-secondary{background:transparent;color:var(--text-dim);border:1px solid var(--border);margin-top:10px}
.inv-btn-secondary:hover{color:var(--text);border-color:var(--text-muted);background:transparent}
.inv-btn .bl-spin{width:14px;height:14px;border-radius:50%;border:2px solid currentColor;border-top-color:transparent;animation:bl-spin .7s linear infinite}
@keyframes bl-spin{to{transform:rotate(360deg)}}

.inv-error{color:var(--red);font-size:13px;margin-top:14px;padding:10px 12px;background:rgba(255,77,79,.08);border:1px solid rgba(255,77,79,.25);border-radius:var(--radius-sm)}
.inv-state-bad .inv-title{color:var(--text)}
.inv-state-bad .inv-eyebrow{color:var(--text-muted);background:var(--surface2)}

.inv-skeleton{display:flex;flex-direction:column;gap:12px}
.inv-sk-line{background:linear-gradient(90deg,var(--surface) 0%,var(--surface2) 50%,var(--surface) 100%);background-size:200% 100%;animation:inv-shimmer 1.4s ease-in-out infinite;border-radius:6px;height:14px}
.inv-sk-line-sm{width:40%;height:10px}
.inv-sk-line-md{width:65%;height:18px}
.inv-sk-line-lg{width:90%;height:60px;border-radius:8px}
@keyframes inv-shimmer{to{background-position:-200% 0}}

/* Toast lives in shared.js normally; redefine minimally for this page. */
.toast{position:fixed;bottom:24px;left:50%;transform:translateX(-50%);background:var(--surface2);color:var(--text);border:1px solid var(--border);border-radius:var(--radius-sm);padding:10px 16px;font-size:13px;box-shadow:0 8px 24px rgba(0,0,0,.4);opacity:0;pointer-events:none;transition:opacity .2s}
.toast.show{opacity:1}
.toast.error{border-color:var(--red);color:var(--red)}
.toast.success{border-color:var(--accent);color:var(--accent)}
"""

private const val INVITE_SHELL_JS = """
(function(){
  // Surface a token if the user already has a session — invite.js then offers
  // an immediate Accept button instead of routing through OAuth.
  window.tlToken = function(){ return localStorage.getItem('syncling_token'); };
  window.authHeaders = function(){
    var t = window.tlToken();
    return t ? {'Authorization':'Bearer '+t,'Content-Type':'application/json'}
             : {'Content-Type':'application/json'};
  };
  window.tlFetch = function(path, opts){
    opts = opts || {};
    var h = Object.assign({}, window.authHeaders(), opts.headers || {});
    return fetch(path, Object.assign({}, opts, {headers: h}));
  };
  window.toast = function(msg, kind){
    var t = document.getElementById('toast');
    if(!t){t=document.createElement('div');t.id='toast';t.className='toast';document.body.appendChild(t);}
    t.textContent = msg;
    t.className = 'toast '+(kind||'')+' show';
    clearTimeout(t._timer);
    t._timer = setTimeout(function(){ t.className = 'toast '+(kind||''); }, 2800);
  };
})();
"""
