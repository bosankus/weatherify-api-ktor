package com.syncling.routes

import com.syncling.domain.BillingPlan
import com.syncling.services.SubscriptionInit
import kotlinx.html.*

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
        title { +"Start ${plan.displayName} trial · Syncling" }
        link { rel = "icon"; type = "image/svg+xml"; href = "/syncling/favicon.svg" }
        script { src = "https://checkout.razorpay.com/v1/checkout.js" }
        style { unsafe { +CHECKOUT_CSS } }
    }
    body {
        div("co-wrap") {
            div("co-blob co-blob-1") {}
            div("co-blob co-blob-2") {}
            div("co-timer-bar") { div { id = "co-timer-fill"; classes = setOf("co-timer-fill") } }
            div("co-timer-label") {
                span { +"Session expires in " }
                span { id = "co-timer-text"; +"15:00" }
            }
            header("co-header") {
                a(href = "/syncling", classes = "co-brand") {
                    unsafe { +CHECKOUT_LOGO_SVG }
                    span { +"Syncling" }
                }
                div("co-user") {
                    if (!avatarUrl.isNullOrBlank()) {
                        img(src = avatarUrl, alt = userName, classes = "co-avatar")
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
                        +"You're seconds away from "; span("co-title-accent") { +"shipping in every language" }; +"."
                    }
                    p("co-sub") {
                        +"You won't be charged today. Your card is authorized for the ${plan.displayName} plan and "
                        +"billed only after the 7-day free trial ends. Cancel anytime from your dashboard."
                    }
                    div("co-trust-row") {
                        div("co-trust") { unsafe { +ICON_SHIELD }; span { +"PCI-DSS Level 1" } }
                        div("co-trust") { unsafe { +ICON_LOCK }; span { +"256-bit TLS" } }
                        div("co-trust") { unsafe { +ICON_RAZORPAY }; span { +"Powered by Razorpay" } }
                    }
                    ul("co-checks") {
                        li { unsafe { +ICON_CHECK }; +"No charge for 7 days" }
                        li { unsafe { +ICON_CHECK }; +"Trial end date visible in your dashboard" }
                        li { unsafe { +ICON_CHECK }; +"Cancel any time — keep using free tier" }
                        li { unsafe { +ICON_CHECK }; +"GST-compliant invoices in your account" }
                    }
                }
                aside("co-stack co-stack-right") {
                    div("co-summary") {
                        div("co-summary-head") {
                            span("co-summary-label") { +"PLAN" }
                            span("co-summary-badge") { +"7-day trial" }
                        }
                        h2("co-summary-plan") { +plan.displayName }
                        div("co-summary-price-row") {
                            span("co-summary-price") { +priceRupees }
                            span("co-summary-period") { +"/month after trial" }
                        }
                        p("co-summary-meta") { +"Billed in INR · GST included · Renews monthly" }
                        ul("co-summary-features") {
                            planFeatureRows(plan).forEach { feature ->
                                li { unsafe { +ICON_DOT }; +feature }
                            }
                        }
                        div("co-summary-divider") {}
                        div("co-summary-due-row") {
                            span { +"Due today" }
                            span("co-summary-due") { +"₹0.00" }
                        }
                        div {
                            id = "co-pay-error"
                            attributes["style"] = "display:none"
                            classes = setOf("co-pay-error")
                        }
                        button(type = ButtonType.button, classes = "co-pay-btn") {
                            id = "co-pay"
                            unsafe { +ICON_LOCK_SOLID }
                            span { +"Authorize card · Start free trial" }
                        }
                        p("co-pay-foot") {
                            +"By continuing you agree to Syncling's "
                            a("/syncling/terms") { +"Terms of Service" }
                            +" and acknowledge the "
                            a("/syncling/privacy") { +"Privacy Policy" }
                            +"."
                        }
                    }
                }
            }
            footer("co-footer") {
                span { +"Need help? " }
                a("mailto:support@syncling.space") { +"support@syncling.space" }
            }
            div("co-overlay") { id = "co-overlay"; div("co-spinner") {}; p { +"Preparing secure checkout…" } }
            div("co-expired-overlay") {
                id = "co-expired"
                div("co-expired-card") {
                    p("co-expired-title") { +"Session expired" }
                    p("co-expired-body") { +"Your 15-minute checkout window closed. Your card was not charged." }
                    a("/syncling#pricing", classes = "co-pay-btn co-expired-btn") { +"Restart checkout" }
                }
            }
        }
        script {
            unsafe {
                +"""
                (function(){
                  var TIMEOUT=900000,startedAt=Date.now(),timerText=document.getElementById('co-timer-text'),timerFill=document.getElementById('co-timer-fill'),expired=!1;
                  function pad(n){return n<10?'0'+n:''+n}
                  function tick(){if(expired)return;var left=Math.max(0,TIMEOUT-(Date.now()-startedAt));if(timerText)timerText.textContent=pad(Math.floor(left/60000))+':'+pad(Math.floor((left%60000)/1000));if(timerFill)timerFill.style.transform='scaleX('+(left/TIMEOUT)+')';if(left===0){expired=!0;fetch('/billing/cancel-pending',{method:'POST',credentials:'include'});var el=document.getElementById('co-expired');if(el)el.classList.add('co-expired-overlay-show');return}setTimeout(tick,1000)}tick();
                  var cfg={key:${quote(init.keyId)},subscription_id:${quote(init.subscriptionId)},name:'Syncling',description:${quote("${plan.displayName} plan · 7-day free trial")},image:'https://syncling.space/syncling/favicon.svg',prefill:{name:${quote(userName)},email:${quote(userEmail ?: "")}},theme:{color:'#8B7EFF',backdrop_color:'#000000'},handler:function(resp){var params=new URLSearchParams({razorpay_payment_id:resp.razorpay_payment_id||'',razorpay_subscription_id:resp.razorpay_subscription_id||${quote(init.subscriptionId)},razorpay_signature:resp.razorpay_signature||''});window.location.href='/billing/rp-callback?'+params.toString()},modal:{ondismiss:function(){document.getElementById('co-overlay').classList.remove('co-overlay-show');document.getElementById('co-pay').disabled=!1},escape:!0,backdropclose:!1},notes:{plan:${quote(plan.name)}}};
                  function showPayError(msg){var el=document.getElementById('co-pay-error');if(el){el.textContent=msg;el.style.display='block';setTimeout(function(){el.style.display='none'},8000)}}
                  function openCheckout(){var rzp=new Razorpay(cfg);rzp.on('payment.failed',function(resp){document.getElementById('co-overlay').classList.remove('co-overlay-show');document.getElementById('co-pay').disabled=!1;showPayError('Payment failed: '+(resp.error&&resp.error.description?resp.error.description:'Please try again.'))});rzp.open()}
                  var btn=document.getElementById('co-pay'),overlay=document.getElementById('co-overlay');
                  function ready(){overlay.classList.remove('co-overlay-show');btn.addEventListener('click',function(){btn.disabled=!0;overlay.classList.add('co-overlay-show');setTimeout(openCheckout,80)})}
                  if(window.Razorpay){overlay.classList.add('co-overlay-show');ready()}else{overlay.classList.add('co-overlay-show');var checkReady=setInterval(function(){if(window.Razorpay){clearInterval(checkReady);ready()}},50)}
                })();
                """.trimIndent()
            }
        }
    }
}

private fun planFeatureRows(plan: BillingPlan): List<String> = when (plan) {
    BillingPlan.SOLO -> listOf("5,000 strings / month", "3 projects", "All target languages", "Up to 10 source files / project", "Glossary enforcement", "Translation memory", "Review portal")
    BillingPlan.TEAM -> listOf("Unlimited strings", "10 projects", "All target languages", "Up to 20 source files / project", "Everything in PRO", "Priority support")
    else -> emptyList()
}

/**
 * Shown when a recurring charge failed and the subscription is on hold. Re-opens
 * Razorpay Checkout against the existing subscription so the customer can clear the
 * outstanding payment and resume the plan.
 */
internal fun HTML.paymentPendingPage(
    plan: BillingPlan,
    subscriptionId: String,
    keyId: String,
    userEmail: String?,
    userName: String,
    avatarUrl: String?
) {
    val pricePaise = plan.monthlyPricePaise ?: 0
    val priceRupees = "₹${"%,d".format(pricePaise / 100)}"
    head {
        meta { charset = "utf-8" }
        meta { name = "viewport"; content = "width=device-width,initial-scale=1" }
        title { +"Payment pending · Syncling" }
        link { rel = "icon"; type = "image/svg+xml"; href = "/syncling/favicon.svg" }
        script { src = "https://checkout.razorpay.com/v1/checkout.js" }
        style { unsafe { +CHECKOUT_CSS } }
    }
    body {
        div("co-wrap") {
            div("co-blob co-blob-1") {}
            div("co-blob co-blob-2") {}
            header("co-header") {
                a(href = "/syncling", classes = "co-brand") {
                    unsafe { +CHECKOUT_LOGO_SVG }
                    span { +"Syncling" }
                }
                div("co-user") {
                    if (!avatarUrl.isNullOrBlank()) {
                        img(src = avatarUrl, alt = userName, classes = "co-avatar")
                    } else {
                        div("co-avatar co-avatar-fallback") { +userName.take(1).uppercase() }
                    }
                    span("co-user-name") { +userName }
                }
            }
            main("co-main") {
                div("co-stack co-stack-left") {
                    p("co-eyebrow") { +"Action required · Payment pending" }
                    h1("co-title") {
                        +"Your subscription is "; span("co-title-accent") { +"on hold" }; +"."
                    }
                    p("co-sub") {
                        +"Your last payment for the ${plan.displayName} plan didn't go through, so translations are "
                        +"paused. Complete the pending payment below to resume your subscription instantly — "
                        +"your projects, languages, and settings are untouched."
                    }
                    div("co-trust-row") {
                        div("co-trust") { unsafe { +ICON_SHIELD }; span { +"PCI-DSS Level 1" } }
                        div("co-trust") { unsafe { +ICON_LOCK }; span { +"256-bit TLS" } }
                        div("co-trust") { unsafe { +ICON_RAZORPAY }; span { +"Powered by Razorpay" } }
                    }
                    ul("co-checks") {
                        li { unsafe { +ICON_CHECK }; +"Access resumes the moment payment succeeds" }
                        li { unsafe { +ICON_CHECK }; +"No data or configuration is lost while on hold" }
                        li { unsafe { +ICON_CHECK }; +"GST-compliant invoice in your account after payment" }
                    }
                }
                aside("co-stack co-stack-right") {
                    div("co-summary") {
                        div("co-summary-head") {
                            span("co-summary-label") { +"PLAN" }
                            span("co-summary-badge") { +"Payment due" }
                        }
                        h2("co-summary-plan") { +plan.displayName }
                        div("co-summary-price-row") {
                            span("co-summary-price") { +priceRupees }
                            span("co-summary-period") { +"/month" }
                        }
                        p("co-summary-meta") { +"Billed in INR · GST included · Renews monthly" }
                        div("co-summary-divider") {}
                        div("co-summary-due-row") {
                            span { +"Due now" }
                            span("co-summary-due") { +priceRupees }
                        }
                        div {
                            id = "co-pay-error"
                            attributes["style"] = "display:none"
                            classes = setOf("co-pay-error")
                        }
                        button(type = ButtonType.button, classes = "co-pay-btn") {
                            id = "co-pay"
                            unsafe { +ICON_LOCK_SOLID }
                            span { +"Complete payment · Resume plan" }
                        }
                        p("co-pay-foot") {
                            +"Questions about this charge? Contact "
                            a("mailto:support@syncling.space") { +"support@syncling.space" }
                            +"."
                        }
                    }
                }
            }
            footer("co-footer") {
                span { +"Need help? " }
                a("mailto:support@syncling.space") { +"support@syncling.space" }
            }
            div("co-overlay") { id = "co-overlay"; div("co-spinner") {}; p { +"Preparing secure checkout…" } }
        }
        script {
            unsafe {
                +"""
                (function(){
                  var cfg={key:${quote(keyId)},subscription_id:${quote(subscriptionId)},name:'Syncling',description:${quote("${plan.displayName} plan · pending payment")},image:'https://syncling.space/syncling/favicon.svg',prefill:{name:${quote(userName)},email:${quote(userEmail ?: "")}},theme:{color:'#8B7EFF',backdrop_color:'#000000'},handler:function(resp){var params=new URLSearchParams({razorpay_payment_id:resp.razorpay_payment_id||'',razorpay_subscription_id:resp.razorpay_subscription_id||${quote(subscriptionId)},razorpay_signature:resp.razorpay_signature||'',flow:'retry'});window.location.href='/billing/rp-callback?'+params.toString()},modal:{ondismiss:function(){document.getElementById('co-overlay').classList.remove('co-overlay-show');document.getElementById('co-pay').disabled=!1},escape:!0,backdropclose:!1}};
                  function showPayError(msg){var el=document.getElementById('co-pay-error');if(el){el.textContent=msg;el.style.display='block';setTimeout(function(){el.style.display='none'},8000)}}
                  function openCheckout(){var rzp=new Razorpay(cfg);rzp.on('payment.failed',function(resp){document.getElementById('co-overlay').classList.remove('co-overlay-show');document.getElementById('co-pay').disabled=!1;showPayError('Payment failed: '+(resp.error&&resp.error.description?resp.error.description:'Please try again.'))});rzp.open()}
                  var btn=document.getElementById('co-pay'),overlay=document.getElementById('co-overlay');
                  function ready(){overlay.classList.remove('co-overlay-show');btn.addEventListener('click',function(){btn.disabled=!0;overlay.classList.add('co-overlay-show');setTimeout(openCheckout,80)})}
                  if(window.Razorpay){ready()}else{overlay.classList.add('co-overlay-show');var checkReady=setInterval(function(){if(window.Razorpay){clearInterval(checkReady);ready()}},50)}
                })();
                """.trimIndent()
            }
        }
    }
}

internal fun HTML.successPage(
    subscriptionId: String,
    token: String? = null,
    plan: BillingPlan? = null,
    trialEndsOn: String? = null
) {
    val dashUrl = if (!token.isNullOrBlank()) "/app?token=${token}" else "/app"
    val planName = plan?.displayName ?: "your plan"
    head {
        meta { charset = "utf-8" }
        meta { name = "viewport"; content = "width=device-width,initial-scale=1" }
        title { +"Trial activated · Syncling" }
        link { rel = "icon"; type = "image/svg+xml"; href = "/syncling/favicon.svg" }
        style { unsafe { +CHECKOUT_CSS } }
    }
    body {
        div("co-wrap co-wrap-success") {
            div("co-blob co-blob-1") {}
            div("co-blob co-blob-2") {}
            div("co-success-card") {
                div("co-success-check") { unsafe { +ICON_BIG_CHECK } }
                h1 { +"Payment confirmed — $planName is active." }
                if (trialEndsOn != null) {
                    p("co-success-sub") {
                        +"Your 7-day free trial has started. We'll send a reminder before charging your card. First payment: "; strong { +trialEndsOn }; +". Cancel anytime from your dashboard."
                    }
                } else {
                    p("co-success-sub") { +"Your 7-day free trial is live. We won't charge your card until the trial ends. Cancel anytime from your dashboard." }
                }
                div("co-success-steps") {
                    div("co-success-step") {
                        span("co-step-num") { +"1" }; div("co-step-body") { strong { +"Create your project" }; p { +"Connect a GitHub repo and choose your source strings file." } }
                    }
                    div("co-success-step") {
                        span("co-step-num") { +"2" }; div("co-step-body") { strong { +"Install the GitHub webhook" }; p { +"One click from the project settings — takes 10 seconds." } }
                    }
                    div("co-success-step") {
                        span("co-step-num") { +"3" }; div("co-step-body") { strong { +"Push a commit" }; p { +"Syncling opens a PR with every language translated automatically." } }
                    }
                }
                a(dashUrl, classes = "co-pay-btn co-success-cta") { +"Go to dashboard →" }
                if (!token.isNullOrBlank()) {
                    button(type = ButtonType.button, classes = "co-activate-btn") { id = "co-activate-now"; +"Start trial now — don't wait" }
                    p("co-activate-hint") { +"Starts your 7-day trial clock immediately so you know the exact end date." }
                }
                p("co-success-meta") { +"Subscription ref: "; code { +subscriptionId } }
            }
        }
        script {
            unsafe {
                +"""
                var dashUrl=${quote(dashUrl)},token=${if (token.isNullOrBlank()) "null" else quote(token)};
                var activateBtn=document.getElementById('co-activate-now');
                if(activateBtn&&token){activateBtn.addEventListener('click',function(){activateBtn.disabled=!0;activateBtn.textContent='Activating…';fetch('/api/billing/activate-now',{method:'POST',headers:{'Authorization':'Bearer '+token}}).finally(function(){window.location.href=dashUrl})})}
                """.trimIndent()
            }
        }
    }
}

private fun quote(s: String): String = "'${s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "")}'"

private const val CHECKOUT_LOGO_SVG = """<svg viewBox="0 0 32 32" width="28" height="28" aria-hidden="true"><defs><linearGradient id="coLg" x1="0" y1="0" x2="32" y2="32" gradientUnits="userSpaceOnUse"><stop offset="0%" stop-color="#A890FF"/><stop offset="100%" stop-color="#5535DD"/></linearGradient></defs><rect width="32" height="32" rx="8" fill="url(#coLg)"/><path d="M8.5 10.5 H23.5" stroke="#0a0a0a" stroke-width="2.8" stroke-linecap="round"/><path d="M16 10.5 V23" stroke="#0a0a0a" stroke-width="2.8" stroke-linecap="round"/></svg>"""
private const val ICON_CHECK = """<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#8B7EFF" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>"""
private const val ICON_DOT = """<svg xmlns="http://www.w3.org/2000/svg" width="6" height="6" viewBox="0 0 6 6"><circle cx="3" cy="3" r="2.4" fill="#8B7EFF"/></svg>"""
private const val ICON_LOCK = """<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>"""
private const val ICON_LOCK_SOLID = """<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="#0a0a0a" stroke="none"><path d="M19 10h-1V7a6 6 0 1 0-12 0v3H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-9a2 2 0 0 0-2-2zM8 7a4 4 0 0 1 8 0v3H8z"/></svg>"""
private const val ICON_SHIELD = """<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><polyline points="9 12 11 14 15 10"/></svg>"""
private const val ICON_RAZORPAY = """<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M16.4 0 8 8.4l-1.6 6L11 9.8z"/><path d="M3 24l4.6-17H13l-4.6 17z"/></svg>"""
private const val ICON_BIG_CHECK = """<svg xmlns="http://www.w3.org/2000/svg" width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="#8B7EFF" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10" fill="rgba(139,126,255,.12)"/><polyline points="16.5 9.5 10.7 15 7.5 12"/></svg>"""

private const val CHECKOUT_CSS = """
:root{--bg:#0a0a0a;--surface:#121212;--surface2:#1a1a1a;--border:#282828;--accent:#8B7EFF;--accent-soft:rgba(139,126,255,.14);--text:#f0f0f0;--text-dim:#a0a0a0;--text-muted:#707070;--radius:16px;--radius-sm:10px}
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
html,body{background:var(--bg);color:var(--text);font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',system-ui,sans-serif;-webkit-font-smoothing:antialiased;min-height:100%}
a{color:var(--accent);text-decoration:none}
button{font-family:inherit;cursor:pointer;border:none}
ul{list-style:none}
.co-wrap{position:relative;min-height:100vh;display:flex;flex-direction:column;overflow:hidden}
.co-wrap-success{align-items:center;justify-content:center}
.co-blob{position:absolute;width:600px;height:600px;border-radius:50%;filter:blur(140px);opacity:.35;pointer-events:none;z-index:0}
.co-blob-1{background:radial-gradient(circle at 30% 30%,#8B7EFF,transparent 60%);top:-200px;left:-200px;animation:coDrift 20s ease-in-out infinite}
.co-blob-2{background:radial-gradient(circle at 70% 70%,#0066ff,transparent 60%);bottom:-250px;right:-250px;opacity:.18;animation:coDrift 24s ease-in-out infinite reverse}
@keyframes coDrift{0%,100%{transform:translate(0,0) scale(1)}50%{transform:translate(50px,40px) scale(1.08)}}
.co-header{position:relative;z-index:2;display:flex;justify-content:space-between;align-items:center;padding:28px 48px;border-bottom:1px solid var(--border)}
.co-brand{display:inline-flex;align-items:center;gap:12px;color:var(--text);font-weight:700;font-size:18px;letter-spacing:-.2px}
.co-user{display:flex;align-items:center;gap:12px;background:var(--surface);border:1px solid var(--border);border-radius:999px;padding:8px 16px 8px 8px}
.co-avatar{width:32px;height:32px;border-radius:50%;display:block}
.co-avatar-fallback{background:linear-gradient(135deg,#8B7EFF,#5535DD);display:flex;align-items:center;justify-content:center;color:#0a0a0a;font-weight:700;font-size:14px}
.co-user-name{font-size:14px;color:var(--text-dim);font-weight:500}
.co-main{position:relative;z-index:2;flex:1;display:grid;grid-template-columns:minmax(0,1.1fr) minmax(380px,480px);gap:80px;padding:80px 48px;max-width:1280px;width:100%;margin:0 auto;align-items:start}
@media(max-width:920px){.co-main{grid-template-columns:1fr;gap:40px;padding:40px 24px}}
.co-stack-left{padding-top:12px}
.co-eyebrow{font-size:13px;font-weight:700;color:var(--accent);letter-spacing:1.5px;text-transform:uppercase;margin-bottom:20px;animation:coFade .6s ease both}
.co-title{font-size:48px;line-height:1.1;letter-spacing:-1.5px;font-weight:800;margin-bottom:24px;animation:coFade .6s ease .05s both}
@media(max-width:920px){.co-title{font-size:36px}}
.co-title-accent{background:linear-gradient(120deg,#8B7EFF,#5535DD);-webkit-background-clip:text;background-clip:text;color:transparent}
.co-sub{font-size:17px;line-height:1.65;color:var(--text-dim);max-width:560px;margin-bottom:40px;animation:coFade .6s ease .1s both}
.co-trust-row{display:flex;flex-wrap:wrap;gap:12px;margin-bottom:40px;animation:coFade .6s ease .15s both}
.co-trust{display:inline-flex;align-items:center;gap:8px;background:var(--surface);border:1px solid var(--border);border-radius:999px;padding:8px 16px;font-size:13px;color:var(--text-dim);font-weight:500}
.co-trust svg{color:var(--accent)}
.co-checks{display:flex;flex-direction:column;gap:16px;animation:coFade .6s ease .2s both}
.co-checks li{display:flex;align-items:center;gap:14px;font-size:15px;color:var(--text-dim)}
.co-checks li svg{flex-shrink:0}
.co-summary{position:relative;background:linear-gradient(180deg,var(--surface),rgba(18,18,18,0));border:1px solid var(--border);border-radius:var(--radius);padding:32px;animation:coFade .7s ease .15s both;box-shadow:0 40px 100px -50px rgba(139,126,255,.3),0 0 0 1px rgba(139,126,255,.1) inset}
.co-summary::before{content:'';position:absolute;inset:-1px;border-radius:inherit;padding:1px;background:linear-gradient(180deg,rgba(139,126,255,.5),transparent 70%);-webkit-mask:linear-gradient(#fff 0 0) content-box,linear-gradient(#fff 0 0);-webkit-mask-composite:xor;mask-composite:exclude;pointer-events:none;opacity:.7}
.co-summary-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:12px}
.co-summary-label{font-size:12px;font-weight:700;letter-spacing:1.5px;color:var(--text-muted);text-transform:uppercase}
.co-summary-badge{display:inline-flex;align-items:center;font-size:12px;font-weight:700;color:var(--accent);background:var(--accent-soft);border:1px solid rgba(139,126,255,.3);padding:5px 12px;border-radius:999px;text-transform:uppercase;letter-spacing:.5px}
.co-summary-plan{font-size:32px;font-weight:800;letter-spacing:-1px;margin-bottom:8px}
.co-summary-price-row{display:flex;align-items:baseline;gap:8px;margin-bottom:8px}
.co-summary-price{font-size:42px;font-weight:800;letter-spacing:-1.5px;color:var(--text)}
.co-summary-period{font-size:15px;color:var(--text-muted);font-weight:500}
.co-summary-meta{font-size:13px;color:var(--text-muted);margin-bottom:28px}
.co-summary-features{display:flex;flex-direction:column;gap:12px;margin-bottom:32px}
.co-summary-features li{display:flex;align-items:center;gap:12px;font-size:15px;color:var(--text-dim)}
.co-summary-divider{height:1px;background:linear-gradient(90deg,transparent,var(--border),transparent);margin:0 0 24px}
.co-summary-due-row{display:flex;justify-content:space-between;align-items:baseline;margin-bottom:28px}
.co-summary-due-row>span:first-child{font-size:15px;color:var(--text-dim)}
.co-summary-due{font-size:24px;font-weight:800;color:var(--accent);letter-spacing:-.5px}
.co-pay-btn{width:100%;display:inline-flex;align-items:center;justify-content:center;gap:12px;background:linear-gradient(180deg,#A890FF,#00C98D);color:#0a0a0a;font-size:16px;font-weight:700;padding:18px 24px;border-radius:var(--radius-sm);letter-spacing:-.1px;transition:transform .2s ease,box-shadow .25s ease,filter .2s ease;box-shadow:0 16px 36px -12px rgba(139,126,255,.6),0 0 0 1px rgba(139,126,255,.5) inset}
.co-pay-btn:hover:not(:disabled){transform:translateY(-2px);box-shadow:0 20px 44px -14px rgba(139,126,255,.7),0 0 0 1px rgba(139,126,255,.6) inset}
.co-pay-btn:active:not(:disabled){transform:translateY(0)}
.co-pay-btn:disabled{opacity:.5;cursor:wait;filter:grayscale(.4)}
.co-pay-foot{margin-top:16px;font-size:13px;color:var(--text-muted);line-height:1.6;text-align:center}
.co-footer{position:relative;z-index:2;padding:28px 48px;text-align:center;font-size:14px;color:var(--text-muted);border-top:1px solid var(--border)}
.co-overlay{position:fixed;inset:0;background:rgba(10,10,10,.88);backdrop-filter:blur(12px);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:20px;z-index:9999;opacity:0;pointer-events:none;transition:opacity .3s ease}
.co-overlay.co-overlay-show{opacity:1;pointer-events:auto}
.co-overlay p{color:var(--text-dim);font-size:15px}
.co-spinner{width:40px;height:40px;border-radius:50%;border:3px solid rgba(255,255,255,.1);border-top-color:var(--accent);animation:coSpin .8s linear infinite}
@keyframes coSpin{to{transform:rotate(360deg)}}
@keyframes coFade{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:none}}
.co-success-card{position:relative;z-index:2;max-width:520px;background:linear-gradient(180deg,var(--surface),rgba(18,18,18,0));border:1px solid var(--border);border-radius:var(--radius);padding:56px 48px;text-align:center;box-shadow:0 40px 100px -40px rgba(139,126,255,.35)}
.co-success-check{display:flex;justify-content:center;margin-bottom:28px;animation:coPop .6s cubic-bezier(.2,.9,.3,1.3) both}
@keyframes coPop{0%{opacity:0;transform:scale(.5)}60%{transform:scale(1.06)}100%{opacity:1;transform:scale(1)}}
.co-success-card h1{font-size:32px;font-weight:800;letter-spacing:-.8px;margin-bottom:16px}
.co-success-sub{font-size:16px;color:var(--text-dim);margin-bottom:20px;line-height:1.6}
.co-success-meta{font-size:13px;color:var(--text-muted);margin-bottom:32px}
.co-success-meta code{background:var(--surface2);padding:4px 10px;border-radius:6px;color:var(--text-dim);font-size:12px;font-family:ui-monospace,Menlo,monospace;border:1px solid var(--border)}
.co-success-cta{margin-bottom:16px}
.co-success-steps{display:flex;flex-direction:column;gap:20px;margin:32px 0;text-align:left}
.co-success-step{display:flex;align-items:flex-start;gap:16px}
.co-step-num{flex-shrink:0;width:32px;height:32px;border-radius:50%;background:var(--accent-soft);border:1px solid rgba(139,126,255,.35);display:flex;align-items:center;justify-content:center;font-size:14px;font-weight:700;color:var(--accent)}
.co-step-body strong{display:block;font-size:15px;color:var(--text);margin-bottom:4px}
.co-step-body p{font-size:14px;color:var(--text-dim);margin:0}
.co-activate-btn{width:100%;margin-top:6px;padding:14px 24px;background:transparent;border:1px solid var(--border);border-radius:var(--radius-sm);color:var(--text-dim);font-size:15px;font-weight:600;cursor:pointer;transition:border-color .2s,color .2s}
.co-activate-btn:hover:not(:disabled){border-color:var(--accent);color:var(--accent)}
.co-activate-btn:disabled{opacity:.5;cursor:wait}
.co-activate-hint{margin-top:10px;font-size:13px;color:var(--text-muted);text-align:center}
.co-timer-bar{position:relative;z-index:3;height:4px;background:rgba(255,255,255,.08);overflow:hidden}
.co-timer-fill{position:absolute;inset:0;background:linear-gradient(90deg,#8B7EFF,#5535DD);transform-origin:left;transition:transform 1s linear}
.co-timer-label{position:relative;z-index:3;padding:8px 48px;font-size:12px;color:var(--text-muted);background:rgba(139,126,255,.05);border-bottom:1px solid rgba(139,126,255,.1)}
.co-expired-overlay{position:fixed;inset:0;background:rgba(10,10,10,.95);backdrop-filter:blur(16px);display:flex;align-items:center;justify-content:center;z-index:9998;opacity:0;pointer-events:none;transition:opacity .3s ease}
.co-expired-overlay.co-expired-overlay-show{opacity:1;pointer-events:auto}
.co-expired-card{max-width:420px;background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);padding:48px;text-align:center}
.co-expired-title{font-size:24px;font-weight:800;margin-bottom:16px;color:var(--text)}
.co-expired-body{font-size:15px;color:var(--text-dim);margin-bottom:32px;line-height:1.6}
.co-expired-btn{display:inline-flex;width:auto;padding:14px 32px;text-decoration:none}
.co-pay-error{background:#2d1212;border:1px solid #7f1d1d;border-radius:var(--radius-sm);color:#fca5a5;font-size:14px;font-weight:500;padding:12px 16px;margin-bottom:16px;line-height:1.5}
"""
