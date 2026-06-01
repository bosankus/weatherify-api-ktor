package com.syncling.services

import com.syncling.domain.BillingPlan
import com.syncling.domain.PipelineRunState
import com.syncling.domain.User
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.Properties

data class SmtpConfig(
    val host: String,
    val port: Int = 587,
    val user: String,
    val password: String,
    val fromAddress: String = user,
    val fromName: String = "Syncling"
)

/**
 * Sends transactional emails over SMTP. Constructed with a null [config] to disable
 * all sending (dev / CI environments where SMTP secrets aren't set).
 *
 * All [send*] calls are safe to call without checking [isConfigured] — they silently
 * no-op when config is absent and log a DEBUG line.
 */
class NotificationService(private val config: SmtpConfig?) {

    private val log = LoggerFactory.getLogger(NotificationService::class.java)

    val isConfigured: Boolean get() = config != null

    // ── Public send functions ──────────────────────────────────────────────────

    suspend fun sendPipelineComplete(user: User, run: PipelineRunState, projectName: String) {
        val email = user.email ?: return
        val prUrl = run.prUrl ?: return
        val stringsCount = run.steps
            .firstOrNull { it.id == "TRANSLATING" }?.detail
            ?.substringBefore(" language")?.trim()?.toIntOrNull() ?: 0
        val langDetail = run.steps.firstOrNull { it.id == "TRANSLATING" }?.detail ?: ""

        send(
            to = email,
            subject = "✅ Translations ready — $projectName",
            html = buildHtml(
                title = "Your translations are ready",
                bodyHtml = """
                    <p style="$TEXT">Hi <b>${user.githubUsername}</b>,</p>
                    <p style="$TEXT">Syncling just finished a translation run for <b>${esc(projectName)}</b>.</p>
                    <table style="$STAT_TABLE">
                      <tr><td style="$STAT_LABEL">Languages</td><td style="$STAT_VALUE">${esc(langDetail)}</td></tr>
                      <tr><td style="$STAT_LABEL">Commit</td><td style="$STAT_VALUE"><code>${esc(run.commitShort)}</code></td></tr>
                    </table>
                    <p style="$TEXT">A pull request is open on GitHub with the auto-approved translations. Any flagged strings are waiting in your review portal.</p>
                    ${ctaButton("View Pull Request", prUrl)}
                    ${ctaButton("Open Review Portal", "https://syncling.dev/dashboard#review", secondary = true)}
                """.trimIndent()
            )
        )
    }

    suspend fun sendReviewQueueReminder(user: User, pendingCount: Int) {
        val email = user.email ?: return
        send(
            to = email,
            subject = "$pendingCount translation${if (pendingCount != 1) "s" else ""} waiting for your review",
            html = buildHtml(
                title = "Strings need your attention",
                bodyHtml = """
                    <p style="$TEXT">Hi <b>${user.githubUsername}</b>,</p>
                    <p style="$TEXT">You have <b>$pendingCount string${if (pendingCount != 1) "s" else ""}</b> waiting in your Syncling review portal. Once approved, a pull request is created automatically.</p>
                    ${ctaButton("Review Now", "https://syncling.dev/dashboard#review")}
                    <p style="$HINT">Tip: use <code>?language=es</code> on the API to filter by language if you only review certain locales.</p>
                """.trimIndent()
            )
        )
    }

    suspend fun sendTrialLimitHit(user: User) {
        val email = user.email ?: return
        send(
            to = email,
            subject = "You've reached your free translation limit",
            html = buildHtml(
                title = "Free plan limit reached",
                bodyHtml = """
                    <p style="$TEXT">Hi <b>${user.githubUsername}</b>,</p>
                    <p style="$TEXT">Your Syncling account has used all <b>500 free strings</b> for this month. New pushes to your watched branch will be queued but not translated until you upgrade.</p>
                    <table style="$STAT_TABLE">
                      <tr><td style="$STAT_LABEL">Solo</td><td style="$STAT_VALUE">5,000 strings · 3 projects · ₹499/mo</td></tr>
                      <tr><td style="$STAT_LABEL">Team</td><td style="$STAT_VALUE">Unlimited strings · 10 projects · ₹1,999/mo</td></tr>
                    </table>
                    ${ctaButton("Upgrade Now", "https://syncling.dev/dashboard#billing")}
                    <p style="$HINT">You can also manually sync any pending pushes after upgrading via the dashboard.</p>
                """.trimIndent()
            )
        )
    }

    suspend fun sendTrialStarted(user: User, plan: BillingPlan, trialEndsOn: String) {
        val email = user.email ?: return
        send(
            to = email,
            subject = "Your ${plan.displayName} trial has started — no charge until $trialEndsOn",
            html = buildHtml(
                title = "Your free trial is live",
                bodyHtml = """
                    <p style="$TEXT">Hi <b>${user.githubUsername}</b>,</p>
                    <p style="$TEXT">Your <b>${plan.displayName}</b> trial is now active. You won't be charged until <b>$trialEndsOn</b>. Cancel any time before then from your dashboard.</p>
                    <table style="$STAT_TABLE">
                      <tr><td style="$STAT_LABEL">Plan</td><td style="$STAT_VALUE">${esc(plan.displayName)}</td></tr>
                      <tr><td style="$STAT_LABEL">First charge</td><td style="$STAT_VALUE">$trialEndsOn</td></tr>
                      <tr><td style="$STAT_LABEL">After trial</td><td style="$STAT_VALUE">${plan.monthlyPricePaise?.let { "₹${it / 100}/month" } ?: "—"}</td></tr>
                    </table>
                    ${ctaButton("Go to dashboard", "https://syncling.space/app")}
                    <p style="$HINT">You can cancel any time from your billing dashboard — no questions asked.</p>
                """.trimIndent()
            )
        )
    }

    suspend fun sendPaymentFailed(user: User, plan: BillingPlan) {
        val email = user.email ?: return
        send(
            to = email,
            subject = "Action required: payment failed for your ${plan.displayName} plan",
            html = buildHtml(
                title = "Payment failed",
                bodyHtml = """
                    <p style="$TEXT">Hi <b>${user.githubUsername}</b>,</p>
                    <p style="$TEXT">We were unable to charge your card for the <b>${plan.displayName}</b> plan. Translations are paused until your payment is resolved.</p>
                    <p style="background:#fef2f2;border-left:3px solid #ef4444;padding:12px 16px;border-radius:4px;font-size:14px;color:#991b1b;">
                        Please update your payment method to resume automatic translations.
                    </p>
                    ${ctaButton("Update payment method", "https://syncling.space/billing")}
                    <p style="$HINT">If you believe this is a mistake, please contact support@androidplay.in.</p>
                """.trimIndent()
            )
        )
    }

    suspend fun sendPaymentReceived(user: User, plan: BillingPlan, amount: String, periodEnd: String) {
        val email = user.email ?: return
        send(
            to = email,
            subject = "Payment confirmed — ${plan.displayName} renewed",
            html = buildHtml(
                title = "Payment received",
                bodyHtml = """
                    <p style="$TEXT">Hi <b>${user.githubUsername}</b>,</p>
                    <p style="$TEXT">Your <b>${plan.displayName}</b> subscription has been renewed successfully.</p>
                    <table style="$STAT_TABLE">
                      <tr><td style="$STAT_LABEL">Amount charged</td><td style="$STAT_VALUE">$amount</td></tr>
                      <tr><td style="$STAT_LABEL">Plan</td><td style="$STAT_VALUE">${esc(plan.displayName)}</td></tr>
                      <tr><td style="$STAT_LABEL">Next renewal</td><td style="$STAT_VALUE">$periodEnd</td></tr>
                    </table>
                    ${ctaButton("View invoice", "https://syncling.space/billing", secondary = true)}
                """.trimIndent()
            )
        )
    }

    suspend fun sendSubscriptionEnded(user: User, plan: BillingPlan) {
        val email = user.email ?: return
        send(
            to = email,
            subject = "Your ${plan.displayName} plan has ended",
            html = buildHtml(
                title = "Subscription ended",
                bodyHtml = """
                    <p style="$TEXT">Hi <b>${user.githubUsername}</b>,</p>
                    <p style="$TEXT">Your <b>${plan.displayName}</b> subscription has ended and your account has been moved to the Free plan. Your projects and translation memory are still intact.</p>
                    ${ctaButton("Reactivate plan", "https://syncling.space/billing")}
                    <p style="$HINT">You can upgrade again at any time. Your previous settings and history are preserved.</p>
                """.trimIndent()
            )
        )
    }

    suspend fun sendCheckoutAbandoned(user: User, pendingPlan: BillingPlan) {
        val email = user.email ?: return
        send(
            to = email,
            subject = "Your ${pendingPlan.displayName} trial is waiting",
            html = buildHtml(
                title = "You started a free trial",
                bodyHtml = """
                    <p style="$TEXT">Hi <b>${user.githubUsername}</b>,</p>
                    <p style="$TEXT">You started setting up a <b>${pendingPlan.displayName}</b> trial on Syncling but didn't finish. Your 7-day free trial is still waiting — no charge until the trial ends.</p>
                    ${ctaButton("Complete Setup", "https://syncling.dev/dashboard#billing")}
                    <p style="$HINT">You can cancel any time before the trial ends. No questions asked.</p>
                """.trimIndent()
            )
        )
    }

    suspend fun sendPlanExpiryWarning(user: User, daysLeft: Long, plan: BillingPlan) {
        val email = user.email ?: return
        send(
            to = email,
            subject = "Your ${plan.displayName} plan renews in $daysLeft day${if (daysLeft != 1L) "s" else ""}",
            html = buildHtml(
                title = "Upcoming renewal",
                bodyHtml = """
                    <p style="$TEXT">Hi <b>${user.githubUsername}</b>,</p>
                    <p style="$TEXT">Your <b>${plan.displayName}</b> plan (${plan.monthlyPricePaise?.let { "₹${it / 100}/month" } ?: "custom pricing"}) renews in <b>$daysLeft day${if (daysLeft != 1L) "s" else ""}</b>. No action needed if you'd like to continue.</p>
                    ${ctaButton("Manage Billing", "https://syncling.dev/dashboard#billing", secondary = true)}
                """.trimIndent()
            )
        )
    }

    suspend fun sendOnboardingReminder(user: User, stuckReason: String) {
        val email = user.email ?: return
        send(
            to = email,
            subject = "Pick up where you left off — Syncling",
            html = buildHtml(
                title = "You're close!",
                bodyHtml = """
                    <p style="$TEXT">Hi <b>${user.githubUsername}</b>,</p>
                    <p style="$TEXT">You're almost ready to automate your app's translations. Here's what's left:</p>
                    <p style="background:#fef9c3;border-left:3px solid #f59e0b;padding:12px 16px;border-radius:4px;font-size:14px;color:#92400e;">
                        ${esc(stuckReason)}
                    </p>
                    ${ctaButton("Continue Setup", "https://syncling.dev/dashboard")}
                    <p style="$HINT">Once set up, every push to your watched branch automatically translates your strings and opens a pull request. No manual work needed.</p>
                """.trimIndent()
            )
        )
    }

    suspend fun sendSupportTicketAck(
        to: String,
        ticketId: String,
        subject: String,
        category: String,
    ) {
        send(
            to = to,
            subject = "We received your support request — $subject",
            html = buildHtml(
                title = "Support request received",
                bodyHtml = """
                    <p style="$TEXT">Thanks for reaching out. We've received your <b>${esc(category)}</b> request and will get back to you shortly.</p>
                    <table style="$STAT_TABLE">
                      <tr><td style="$STAT_LABEL">Ticket ID</td><td style="$STAT_VALUE"><code>${esc(ticketId.take(8))}</code></td></tr>
                      <tr><td style="$STAT_LABEL">Subject</td><td style="$STAT_VALUE">${esc(subject)}</td></tr>
                    </table>
                    <p style="$HINT">You can view the status of your request in the support panel inside the Syncling dashboard.</p>
                """.trimIndent()
            )
        )
    }

    suspend fun sendSupportTicketAlert(
        adminEmail: String,
        ticketId: String,
        userEmail: String?,
        category: String,
        subject: String,
        message: String,
    ) {
        send(
            to = adminEmail,
            subject = "[$category] $subject",
            html = buildHtml(
                title = "New support ticket",
                bodyHtml = """
                    <p style="$TEXT">A new support ticket was submitted.</p>
                    <table style="$STAT_TABLE">
                      <tr><td style="$STAT_LABEL">Ticket ID</td><td style="$STAT_VALUE"><code>${esc(ticketId.take(8))}</code></td></tr>
                      <tr><td style="$STAT_LABEL">From</td><td style="$STAT_VALUE">${esc(userEmail ?: "unknown")}</td></tr>
                      <tr><td style="$STAT_LABEL">Category</td><td style="$STAT_VALUE">${esc(category)}</td></tr>
                      <tr><td style="$STAT_LABEL">Subject</td><td style="$STAT_VALUE">${esc(subject)}</td></tr>
                    </table>
                    <p style="background:#f4f4f5;border-left:3px solid #18181b;padding:12px 16px;border-radius:4px;font-size:14px;color:#3f3f46;white-space:pre-wrap;">${esc(message)}</p>
                """.trimIndent()
            )
        )
    }

    suspend fun sendInviteEmail(
        to: String,
        inviterName: String,
        projectName: String,
        role: String,
        acceptUrl: String
    ) {
        send(
            to = to,
            subject = "$inviterName invited you to $projectName on Syncling",
            html = buildHtml(
                title = "You're invited to collaborate",
                bodyHtml = """
                    <p style="$TEXT"><b>${esc(inviterName)}</b> invited you to <b>${esc(projectName)}</b> as a <b>${esc(role.lowercase())}</b>.</p>
                    <p style="$TEXT">Accept the invite to start reviewing or translating strings on this project.</p>
                    ${ctaButton("Accept Invite", acceptUrl)}
                    <p style="$HINT">This invite link is single-use and expires after acceptance. If you weren't expecting this email, you can safely ignore it.</p>
                """.trimIndent()
            )
        )
    }

    // ── Core SMTP send ─────────────────────────────────────────────────────────

    private suspend fun send(to: String, subject: String, html: String) {
        val cfg = config
        if (cfg == null) {
            log.debug("SMTP not configured — skipping email to {}: {}", to, subject)
            return
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val props = Properties().apply {
                    put("mail.smtp.host", cfg.host)
                    put("mail.smtp.port", cfg.port.toString())
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")
                    put("mail.smtp.connectiontimeout", "10000")
                    put("mail.smtp.timeout", "15000")
                }
                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication() =
                        PasswordAuthentication(cfg.user, cfg.password)
                })

                val msg = MimeMessage(session).apply {
                    setFrom(InternetAddress(cfg.fromAddress, cfg.fromName))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                    setSubject(subject, "UTF-8")

                    val textPart = MimeBodyPart().apply {
                        setText(htmlToPlainText(html), "UTF-8")
                    }
                    val htmlPart = MimeBodyPart().apply {
                        setContent(html, "text/html; charset=UTF-8")
                    }
                    val multipart = MimeMultipart("alternative").apply {
                        addBodyPart(textPart)
                        addBodyPart(htmlPart) // HTML last = preferred by mail clients
                    }
                    setContent(multipart)
                }
                Transport.send(msg)
                log.info("Email sent: to={} subject=\"{}\"", to, subject)
            }.onFailure {
                log.error("Email send failed: to={} subject=\"{}\" error={}", to, subject, it.message)
            }
        }
    }

    // ── HTML template helpers ──────────────────────────────────────────────────

    private fun buildHtml(title: String, bodyHtml: String): String = """
        <!DOCTYPE html>
        <html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
        <body style="margin:0;padding:0;background:#f4f4f5;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
          <table width="100%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:40px 0;">
            <tr><td align="center">
              <table width="560" cellpadding="0" cellspacing="0" style="max-width:560px;width:100%;background:#ffffff;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,.1);overflow:hidden;">

                <!-- Header -->
                <tr><td style="background:#18181b;padding:24px 32px;">
                  <span style="font-size:20px;font-weight:700;color:#ffffff;letter-spacing:-0.5px;">Syncling</span>
                  <span style="font-size:13px;color:#71717a;margin-left:10px;">Automated i18n</span>
                </td></tr>

                <!-- Body -->
                <tr><td style="padding:32px;">
                  <h1 style="margin:0 0 20px;font-size:20px;font-weight:700;color:#18181b;line-height:1.3;">${esc(title)}</h1>
                  $bodyHtml
                </td></tr>

                <!-- Footer -->
                <tr><td style="background:#f4f4f5;padding:20px 32px;border-top:1px solid #e4e4e7;">
                  <p style="margin:0;font-size:12px;color:#71717a;line-height:1.6;">
                    You're receiving this because you have a Syncling account.<br>
                    <a href="https://syncling.dev/dashboard#settings" style="color:#71717a;">Manage email preferences</a>
                    &nbsp;·&nbsp;
                    <a href="https://syncling.dev" style="color:#71717a;">syncling.dev</a>
                  </p>
                </td></tr>

              </table>
            </td></tr>
          </table>
        </body></html>
    """.trimIndent()

    private fun ctaButton(label: String, url: String, secondary: Boolean = false): String {
        val bg = if (secondary) "#f4f4f5" else "#18181b"
        val fg = if (secondary) "#18181b" else "#ffffff"
        val border = if (secondary) "1px solid #e4e4e7" else "none"
        return """<p style="margin:20px 0;">
          <a href="${esc(url)}" style="display:inline-block;padding:12px 24px;background:$bg;color:$fg;border:$border;border-radius:6px;font-size:14px;font-weight:600;text-decoration:none;">${esc(label)}</a>
        </p>"""
    }

    private fun htmlToPlainText(html: String): String =
        html.replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("<p[^>]*>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

    private fun esc(s: String?): String = (s ?: "")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    companion object {
        private const val TEXT = "margin:0 0 16px;font-size:15px;color:#3f3f46;line-height:1.6;"
        private const val HINT = "margin:16px 0 0;font-size:13px;color:#71717a;line-height:1.5;"
        private const val STAT_TABLE = "width:100%;border-collapse:collapse;margin:16px 0;font-size:14px;"
        private const val STAT_LABEL = "padding:8px 12px;background:#f4f4f5;color:#52525b;font-weight:500;border-radius:4px 0 0 4px;width:130px;"
        private const val STAT_VALUE = "padding:8px 12px;color:#18181b;"

        /** Build from environment variables. Returns null if SMTP_USER is not set. */
        fun fromEnv(
            host: String,
            port: Int,
            user: String,
            password: String,
            fromName: String
        ): NotificationService {
            val cfg = if (user.isBlank() || password.isBlank()) null
                      else SmtpConfig(host = host, port = port, user = user, password = password, fromName = fromName)
            return NotificationService(cfg)
        }
    }
}
