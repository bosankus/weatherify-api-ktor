package com.transloom.routes

import com.transloom.domain.BillingPlan
import com.transloom.services.SubscriptionInit
import kotlinx.html.*

/**
 * Branded Razorpay checkout page. We render this on our own domain instead of
 * redirecting the user to Razorpay's hosted short_url, so we control the look,
 * the post-payment redirect, and the messaging around the 60-day trial.
 */
internal fun HTML.checkoutPage(
    plan: BillingPlan,
    init: SubscriptionInit,
    userEmail: String?,
    userName: String,
    avatarUrl: String?
) {
    val pricePaise = plan.monthlyPricePaise ?: 0
    val priceRupees = "₹${"%,d".format(pricePaise / 100)}"
    head {
        meta { charset = "utf-8" }
        meta { name = "viewport"; content = "width=device-width,initial-scale=1" }
        title { +"Start ${plan.displayName} trial · Transloom" }
        link { rel = "icon"; type = "image/svg+xml"; href = "/transloom/favicon.svg" }
        script { src = "https://checkout.razorpay.com/v1/checkout.js" }
        style { unsafe { +CHECKOUT_CSS } }
    }
    body {
        div("co-wrap") {
            // Ambient gradient blobs
            div("co-blob co-blob-1") {}
            div("co-blob co-blob-2") {}

            header("co-header") {
                a {
                    href = "/transloom"
                    classes = setOf("co-brand")
                    unsafe { +CHECKOUT_LOGO_SVG }
                    span { +"Transloom" }
                }
                div("co-user") {
                    if (!avatarUrl.isNullOrBlank()) {
                        img(src = avatarUrl, alt = userName) { classes = setOf("co-avatar") }
                    } else {
                        div("co-avatar co-avatar-fallback") { +userName.take(1).uppercase() }
                    }
                    span("co-user-name") { +userName }
                }
            }

            main("co-main") {
                div("co-stack co-stack-left") {
                    p("co-eyebrow") { +"Step 2 of 2 · Secure payment" }
                    h1("co-title") {
                        +"You're seconds away from "
                        span("co-title-accent") { +"shipping in every language" }
                        +"."
                    }
                    p("co-sub") {
                        +"You won't be charged today. Your card is authorized for the ${plan.displayName} plan and "
                        +"billed only after the 60-day free trial ends. Cancel anytime from your dashboard."
                    }

                    div("co-trust-row") {
                        div("co-trust") {
                            unsafe { +ICON_SHIELD }
                            span { +"PCI-DSS Level 1" }
                        }
                        div("co-trust") {
                            unsafe { +ICON_LOCK }
                            span { +"256-bit TLS" }
                        }
                        div("co-trust") {
                            unsafe { +ICON_RAZORPAY }
                            span { +"Powered by Razorpay" }
                        }
                    }

                    ul("co-checks") {
                        li { unsafe { +ICON_CHECK }; +"No charge for 60 days" }
                        li { unsafe { +ICON_CHECK }; +"Trial end date visible in your dashboard" }
                        li { unsafe { +ICON_CHECK }; +"Cancel any time — keep using free tier" }
                        li { unsafe { +ICON_CHECK }; +"GST-compliant invoices in your account" }
                    }
                }

                aside("co-stack co-stack-right") {
                    div("co-summary") {
                        div("co-summary-head") {
                            span("co-summary-label") { +"PLAN" }
                            span("co-summary-badge") { +"60-day trial" }
                        }
                        h2("co-summary-plan") { +plan.displayName }
                        div("co-summary-price-row") {
                            span("co-summary-price") { +priceRupees }
                            span("co-summary-period") { +"/month after trial" }
                        }
                        p("co-summary-meta") {
                            +"Billed in INR · GST included · Renews monthly"
                        }
                        ul("co-summary-features") {
                            planFeatureRows(plan).forEach { feature ->
                                li {
                                    unsafe { +ICON_DOT }
                                    +feature
                                }
                            }
                        }
                        div("co-summary-divider") {}
                        div("co-summary-due-row") {
                            span { +"Due today" }
                            span("co-summary-due") { +"₹0.00" }
                        }

                        button {
                            type = ButtonType.button
                            id = "co-pay"
                            classes = setOf("co-pay-btn")
                            unsafe { +ICON_LOCK_SOLID }
                            span { +"Authorize card · Start free trial" }
                        }
                        p("co-pay-foot") {
                            +"By continuing you agree to Transloom's Terms and acknowledge the Privacy Policy."
                        }
                    }
                }
            }

            footer("co-footer") {
                span { +"Need help? " }
                a("mailto:support@androidplay.in") { +"support@androidplay.in" }
            }

            // Loading overlay shown until Checkout.js is ready
            div {
                id = "co-overlay"
                classes = setOf("co-overlay")
                div("co-spinner") {}
                p { +"Preparing secure checkout…" }
            }
        }

        script {
            unsafe {
                +"""
                (function(){
                  var cfg = {
                    key: ${quote(init.keyId)},
                    subscription_id: ${quote(init.subscriptionId)},
                    name: 'Transloom',
                    description: ${quote("${plan.displayName} plan · 60-day free trial")},
                    image: 'https://data.androidplay.in/transloom/favicon.svg',
                    prefill: { name: ${quote(userName)}, email: ${quote(userEmail ?: "")} },
                    theme: { color: '#00E5A0', backdrop_color: '#000000' },
                    handler: function (resp) {
                      var params = new URLSearchParams({
                        razorpay_payment_id: resp.razorpay_payment_id || '',
                        razorpay_subscription_id: resp.razorpay_subscription_id || ${quote(init.subscriptionId)},
                        razorpay_signature: resp.razorpay_signature || ''
                      });
                      window.location.href = '/transloom/billing/rp-callback?' + params.toString();
                    },
                    modal: {
                      ondismiss: function () {
                        document.getElementById('co-overlay').classList.remove('co-overlay-show');
                        document.getElementById('co-pay').disabled = false;
                      },
                      escape: true,
                      backdropclose: false
                    },
                    notes: { plan: ${quote(plan.name)} }
                  };

                  function openCheckout(){
                    var rzp = new Razorpay(cfg);
                    rzp.on('payment.failed', function(resp){
                      document.getElementById('co-overlay').classList.remove('co-overlay-show');
                      document.getElementById('co-pay').disabled = false;
                      alert('Payment failed: ' + (resp.error && resp.error.description ? resp.error.description : 'Please try again.'));
                    });
                    rzp.open();
                  }

                  var btn = document.getElementById('co-pay');
                  var overlay = document.getElementById('co-overlay');

                  function ready(){
                    overlay.classList.remove('co-overlay-show');
                    btn.addEventListener('click', function(){
                      btn.disabled = true;
                      overlay.classList.add('co-overlay-show');
                      // small delay so the disabled state paints before modal blocks
                      setTimeout(openCheckout, 80);
                    });
                  }

                  if (window.Razorpay) {
                    overlay.classList.add('co-overlay-show');
                    ready();
                  } else {
                    overlay.classList.add('co-overlay-show');
                    var checkReady = setInterval(function(){
                      if (window.Razorpay) { clearInterval(checkReady); ready(); }
                    }, 50);
                  }
                })();
                """.trimIndent()
            }
        }
    }
}

private fun planFeatureRows(plan: BillingPlan): List<String> = when (plan) {
    BillingPlan.SOLO -> listOf(
        "5,000 strings / month",
        "3 projects",
        "All target languages",
        "Glossary enforcement",
        "Translation memory",
        "Review portal"
    )
    BillingPlan.TEAM -> listOf(
        "Unlimited strings",
        "10 projects",
        "All target languages",
        "Everything in Solo",
        "Priority support"
    )
    else -> emptyList()
}

internal fun HTML.successPage(subscriptionId: String) {
    head {
        meta { charset = "utf-8" }
        meta { name = "viewport"; content = "width=device-width,initial-scale=1" }
        title { +"Trial activated · Transloom" }
        link { rel = "icon"; type = "image/svg+xml"; href = "/transloom/favicon.svg" }
        style { unsafe { +CHECKOUT_CSS } }
    }
    body {
        div("co-wrap co-wrap-success") {
            div("co-blob co-blob-1") {}
            div("co-blob co-blob-2") {}
            div("co-success-card") {
                div("co-success-check") { unsafe { +ICON_BIG_CHECK } }
                h1 { +"Your 60-day trial is live." }
                p("co-success-sub") {
                    +"We've authorized your card but won't bill you for 60 days. "
                    +"Your trial end date is shown in your dashboard — cancel anytime to stay on the free tier."
                }
                p("co-success-meta") {
                    +"Subscription "
                    code { +subscriptionId }
                }
                a("/transloom/app") { classes = setOf("co-pay-btn"); +"Open your dashboard →" }
                p("co-success-foot") {
                    +"Redirecting in "
                    span { id = "co-countdown"; +"5" }
                    +"s…"
                }
            }
        }
        script {
            unsafe {
                +"""
                var n = 5;
                var el = document.getElementById('co-countdown');
                var t = setInterval(function(){
                  n--; if (el) el.textContent = String(n);
                  if (n <= 0) { clearInterval(t); window.location.href = '/transloom/app'; }
                }, 1000);
                """.trimIndent()
            }
        }
    }
}

private fun quote(s: String): String {
    val escaped = s.replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "")
    return "'$escaped'"
}

// ─── Inline SVG icons ─────────────────────────────────────────────────────────

private const val CHECKOUT_LOGO_SVG = """
<svg viewBox="0 0 32 32" width="28" height="28" aria-hidden="true">
  <defs><linearGradient id="coLg" x1="0" y1="0" x2="32" y2="32" gradientUnits="userSpaceOnUse">
    <stop offset="0%" stop-color="#00F5B0"/><stop offset="100%" stop-color="#00A87A"/>
  </linearGradient></defs>
  <rect width="32" height="32" rx="8" fill="url(#coLg)"/>
  <path d="M8.5 10.5 H23.5" stroke="#0a0a0a" stroke-width="2.8" stroke-linecap="round"/>
  <path d="M16 10.5 V23" stroke="#0a0a0a" stroke-width="2.8" stroke-linecap="round"/>
</svg>
"""

private const val ICON_CHECK = """<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#00E5A0" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>"""

private const val ICON_DOT = """<svg xmlns="http://www.w3.org/2000/svg" width="6" height="6" viewBox="0 0 6 6"><circle cx="3" cy="3" r="2.4" fill="#00E5A0"/></svg>"""

private const val ICON_LOCK = """<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>"""

private const val ICON_LOCK_SOLID = """<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="#0a0a0a" stroke="none"><path d="M19 10h-1V7a6 6 0 1 0-12 0v3H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-9a2 2 0 0 0-2-2zM8 7a4 4 0 0 1 8 0v3H8z"/></svg>"""

private const val ICON_SHIELD = """<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><polyline points="9 12 11 14 15 10"/></svg>"""

private const val ICON_RAZORPAY = """<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M16.4 0 8 8.4l-1.6 6L11 9.8z"/><path d="M3 24l4.6-17H13l-4.6 17z"/></svg>"""

private const val ICON_BIG_CHECK = """<svg xmlns="http://www.w3.org/2000/svg" width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="#00E5A0" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10" fill="rgba(0,229,160,.12)"/><polyline points="16.5 9.5 10.7 15 7.5 12"/></svg>"""

// ─── Checkout CSS ─────────────────────────────────────────────────────────────

private const val CHECKOUT_CSS = """
:root{--bg:#080808;--surface:#111;--surface2:#161616;--border:#1f1f1f;--accent:#00E5A0;--accent-soft:rgba(0,229,160,.14);--accent-glow:rgba(0,229,160,.32);--text:#f0f0f0;--text-dim:#9a9a9a;--text-muted:#666;--radius:14px;--radius-sm:8px}
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
html,body{background:var(--bg);color:var(--text);font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',system-ui,sans-serif;-webkit-font-smoothing:antialiased;min-height:100%}
a{color:var(--accent);text-decoration:none}
button{font-family:inherit;cursor:pointer;border:none}
ul{list-style:none}
.co-wrap{position:relative;min-height:100vh;display:flex;flex-direction:column;overflow:hidden}
.co-wrap-success{align-items:center;justify-content:center}
.co-blob{position:absolute;width:560px;height:560px;border-radius:50%;filter:blur(120px);opacity:.45;pointer-events:none;z-index:0}
.co-blob-1{background:radial-gradient(circle at 30% 30%,#00E5A0,transparent 60%);top:-180px;left:-180px;animation:coDrift 18s ease-in-out infinite}
.co-blob-2{background:radial-gradient(circle at 70% 70%,#0066ff,transparent 60%);bottom:-220px;right:-220px;opacity:.22;animation:coDrift 22s ease-in-out infinite reverse}
@keyframes coDrift{0%,100%{transform:translate(0,0) scale(1)}50%{transform:translate(40px,30px) scale(1.06)}}
.co-header{position:relative;z-index:2;display:flex;justify-content:space-between;align-items:center;padding:24px 40px;border-bottom:1px solid rgba(255,255,255,.04)}
.co-brand{display:inline-flex;align-items:center;gap:10px;color:var(--text);font-weight:700;font-size:17px;letter-spacing:-.2px}
.co-user{display:flex;align-items:center;gap:10px;background:rgba(255,255,255,.03);border:1px solid var(--border);border-radius:999px;padding:6px 14px 6px 6px}
.co-avatar{width:28px;height:28px;border-radius:50%;display:block}
.co-avatar-fallback{background:linear-gradient(135deg,#00E5A0,#00A87A);display:flex;align-items:center;justify-content:center;color:#0a0a0a;font-weight:700;font-size:13px}
.co-user-name{font-size:13px;color:var(--text-dim);font-weight:500}
.co-main{position:relative;z-index:2;flex:1;display:grid;grid-template-columns:minmax(0,1.05fr) minmax(360px,440px);gap:60px;padding:60px 40px;max-width:1200px;width:100%;margin:0 auto;align-items:start}
@media(max-width:880px){.co-main{grid-template-columns:1fr;gap:32px;padding:32px 20px}}
.co-stack-left{padding-top:8px}
.co-eyebrow{font-size:12px;font-weight:600;color:var(--accent);letter-spacing:.16em;text-transform:uppercase;margin-bottom:18px;animation:coFade .6s ease both}
.co-title{font-size:44px;line-height:1.08;letter-spacing:-1.2px;font-weight:700;margin-bottom:20px;animation:coFade .6s ease .05s both}
@media(max-width:880px){.co-title{font-size:32px}}
.co-title-accent{background:linear-gradient(120deg,#00E5A0,#00A87A);-webkit-background-clip:text;background-clip:text;color:transparent}
.co-sub{font-size:16px;line-height:1.6;color:var(--text-dim);max-width:520px;margin-bottom:30px;animation:coFade .6s ease .1s both}
.co-trust-row{display:flex;flex-wrap:wrap;gap:10px;margin-bottom:30px;animation:coFade .6s ease .15s both}
.co-trust{display:inline-flex;align-items:center;gap:6px;background:rgba(255,255,255,.03);border:1px solid var(--border);border-radius:999px;padding:6px 12px;font-size:12px;color:var(--text-dim);font-weight:500}
.co-trust svg{color:var(--accent)}
.co-checks{display:flex;flex-direction:column;gap:12px;animation:coFade .6s ease .2s both}
.co-checks li{display:flex;align-items:center;gap:12px;font-size:14px;color:var(--text-dim)}
.co-checks li svg{flex-shrink:0}
.co-summary{position:relative;background:linear-gradient(180deg,rgba(255,255,255,.03),rgba(255,255,255,.01));border:1px solid var(--border);border-radius:var(--radius);padding:28px;animation:coFade .7s ease .15s both;box-shadow:0 30px 80px -40px rgba(0,229,160,.25),0 0 0 1px rgba(0,229,160,.05) inset}
.co-summary::before{content:'';position:absolute;inset:-1px;border-radius:inherit;padding:1px;background:linear-gradient(180deg,rgba(0,229,160,.4),transparent 60%);-webkit-mask:linear-gradient(#000 0 0) content-box,linear-gradient(#000 0 0);-webkit-mask-composite:xor;mask-composite:exclude;pointer-events:none;opacity:.6}
.co-summary-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:10px}
.co-summary-label{font-size:11px;font-weight:600;letter-spacing:.16em;color:var(--text-muted);text-transform:uppercase}
.co-summary-badge{display:inline-flex;align-items:center;font-size:11px;font-weight:600;color:var(--accent);background:var(--accent-soft);border:1px solid rgba(0,229,160,.25);padding:4px 10px;border-radius:999px;text-transform:uppercase;letter-spacing:.04em}
.co-summary-plan{font-size:30px;font-weight:700;letter-spacing:-.8px;margin-bottom:6px}
.co-summary-price-row{display:flex;align-items:baseline;gap:6px;margin-bottom:6px}
.co-summary-price{font-size:38px;font-weight:700;letter-spacing:-1px;color:var(--text)}
.co-summary-period{font-size:14px;color:var(--text-muted);font-weight:500}
.co-summary-meta{font-size:12px;color:var(--text-muted);margin-bottom:22px}
.co-summary-features{display:flex;flex-direction:column;gap:10px;margin-bottom:24px}
.co-summary-features li{display:flex;align-items:center;gap:10px;font-size:14px;color:var(--text-dim)}
.co-summary-divider{height:1px;background:linear-gradient(90deg,transparent,var(--border),transparent);margin:0 0 20px}
.co-summary-due-row{display:flex;justify-content:space-between;align-items:baseline;margin-bottom:24px}
.co-summary-due-row>span:first-child{font-size:14px;color:var(--text-dim)}
.co-summary-due{font-size:22px;font-weight:700;color:var(--accent);letter-spacing:-.4px}
.co-pay-btn{width:100%;display:inline-flex;align-items:center;justify-content:center;gap:10px;background:linear-gradient(180deg,#00F5B0,#00C98D);color:#0a0a0a;font-size:15px;font-weight:600;padding:15px 20px;border-radius:10px;letter-spacing:-.1px;transition:transform .2s ease,box-shadow .25s ease,filter .2s ease;box-shadow:0 14px 32px -10px rgba(0,229,160,.5),0 0 0 1px rgba(0,229,160,.4) inset}
.co-pay-btn:hover:not(:disabled){transform:translateY(-1px);box-shadow:0 18px 40px -12px rgba(0,229,160,.6),0 0 0 1px rgba(0,229,160,.5) inset}
.co-pay-btn:active:not(:disabled){transform:translateY(0)}
.co-pay-btn:disabled{opacity:.55;cursor:wait;filter:grayscale(.3)}
.co-pay-foot{margin-top:14px;font-size:12px;color:var(--text-muted);line-height:1.5;text-align:center}
.co-footer{position:relative;z-index:2;padding:24px 40px;text-align:center;font-size:13px;color:var(--text-muted);border-top:1px solid rgba(255,255,255,.04)}
.co-overlay{position:fixed;inset:0;background:rgba(8,8,8,.85);backdrop-filter:blur(8px);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:18px;z-index:9999;opacity:0;pointer-events:none;transition:opacity .25s ease}
.co-overlay.co-overlay-show{opacity:1;pointer-events:auto}
.co-overlay p{color:var(--text-dim);font-size:14px}
.co-spinner{width:36px;height:36px;border-radius:50%;border:2.5px solid rgba(255,255,255,.08);border-top-color:var(--accent);animation:coSpin .8s linear infinite}
@keyframes coSpin{to{transform:rotate(360deg)}}
@keyframes coFade{from{opacity:0;transform:translateY(8px)}to{opacity:1;transform:none}}
.co-success-card{position:relative;z-index:2;max-width:460px;background:linear-gradient(180deg,rgba(255,255,255,.03),rgba(255,255,255,.01));border:1px solid var(--border);border-radius:var(--radius);padding:48px 40px;text-align:center;box-shadow:0 30px 80px -40px rgba(0,229,160,.3)}
.co-success-check{display:flex;justify-content:center;margin-bottom:24px;animation:coPop .55s cubic-bezier(.2,.9,.3,1.3) both}
@keyframes coPop{0%{opacity:0;transform:scale(.6)}60%{transform:scale(1.05)}100%{opacity:1;transform:scale(1)}}
.co-success-card h1{font-size:28px;font-weight:700;letter-spacing:-.6px;margin-bottom:14px}
.co-success-sub{font-size:15px;color:var(--text-dim);margin-bottom:18px;line-height:1.55}
.co-success-meta{font-size:12px;color:var(--text-muted);margin-bottom:28px}
.co-success-meta code{background:rgba(255,255,255,.05);padding:3px 8px;border-radius:4px;color:var(--text-dim);font-size:11px;font-family:ui-monospace,Menlo,monospace}
.co-success-foot{margin-top:18px;font-size:12px;color:var(--text-muted)}
.co-success-foot span{color:var(--accent);font-weight:600}
"""
