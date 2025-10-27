package data.service

import bose.ankush.util.getSecretValue
import domain.model.Result
import domain.service.EmailService
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import util.Constants
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
data class SendGridEmail(
    val personalizations: List<Personalization>,
    val from: EmailAddress,
    val subject: String,
    val content: List<Content>,
    val attachments: List<Attachment>? = null
)

@Serializable
data class Personalization(
    val to: List<EmailAddress>
)

@Serializable
data class EmailAddress(
    val email: String,
    val name: String? = null
)

@Serializable
data class Content(
    val type: String,
    val value: String
)

@Serializable
data class Attachment(
    val content: String,
    val type: String,
    val filename: String,
    val disposition: String = "attachment"
)

class EmailServiceImpl(private val httpClient: HttpClient) : EmailService {
    private val logger = LoggerFactory.getLogger(EmailServiceImpl::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val sendGridApiKey = getSecretValue(Constants.Auth.SENDGRID_API_KEY)
    private val fromEmail = System.getenv(Constants.Env.FROM_EMAIL) ?: "noreply@androidplay.com"
    private val fromName = System.getenv(Constants.Env.FROM_NAME) ?: "Androidplay"

    override suspend fun sendCancellationEmail(
        userEmail: String,
        subscriptionEndDate: String
    ): Result<Boolean> {
        logger.debug("Sending cancellation email to: $userEmail")

        if (sendGridApiKey.isBlank()) {
            logger.warn("SendGrid API key not configured. Skipping email to $userEmail")
            return Result.success(false)
        }

        return try {
            val formattedDate = formatDate(subscriptionEndDate)
            val htmlContent = buildCancellationEmailHtml(formattedDate)

            val emailPayload = SendGridEmail(
                personalizations = listOf(
                    Personalization(
                        to = listOf(EmailAddress(email = userEmail))
                    )
                ),
                from = EmailAddress(email = fromEmail, name = fromName),
                subject = "Your Subscription Has Been Cancelled",
                content = listOf(
                    Content(type = "text/html", value = htmlContent)
                )
            )

            val response = httpClient.post("https://api.sendgrid.com/v3/mail/send") {
                header("Authorization", "Bearer $sendGridApiKey")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(emailPayload))
            }

            if (response.status.isSuccess()) {
                logger.info("Cancellation email sent successfully to: $userEmail")
                Result.success(true)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to send email. Status: ${response.status}, Body: $errorBody")
                Result.error("Failed to send email: ${response.status}")
            }
        } catch (e: Exception) {
            logger.error("Failed to send cancellation email to $userEmail", e)
            Result.error("Failed to send email: ${e.message}", e)
        }
    }

    override suspend fun sendRefundNotification(
        userEmail: String,
        refundId: String,
        amount: Double,
        paymentId: String,
        status: String
    ): Result<Boolean> {
        logger.debug("Sending refund notification email to: $userEmail")

        if (sendGridApiKey.isBlank()) {
            logger.warn("SendGrid API key not configured. Skipping email to $userEmail")
            return Result.success(false)
        }

        return try {
            val (subject, htmlContent) = buildRefundEmailContent(refundId, amount, paymentId, status)

            val emailPayload = SendGridEmail(
                personalizations = listOf(
                    Personalization(
                        to = listOf(EmailAddress(email = userEmail))
                    )
                ),
                from = EmailAddress(email = fromEmail, name = fromName),
                subject = subject,
                content = listOf(
                    Content(type = "text/html", value = htmlContent)
                )
            )

            val response = httpClient.post("https://api.sendgrid.com/v3/mail/send") {
                header("Authorization", "Bearer $sendGridApiKey")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(emailPayload))
            }

            if (response.status.isSuccess()) {
                logger.info("Refund notification sent successfully to: $userEmail")
                Result.success(true)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to send refund email. Status: ${response.status}, Body: $errorBody")
                Result.error("Failed to send refund email: ${response.status}")
            }
        } catch (e: Exception) {
            logger.error("Failed to send refund notification to $userEmail", e)
            Result.error("Failed to send refund email: ${e.message}", e)
        }
    }

    private fun buildRefundEmailContent(
        refundId: String,
        amount: Double,
        paymentId: String,
        status: String
    ): Pair<String, String> {
        val formattedAmount = "₹${String.format("%.2f", amount)}"

        return when (status.lowercase()) {
            "initiated" -> {
                val subject = "Refund Initiated - $formattedAmount"
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            body {
                                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                                line-height: 1.6;
                                color: #333;
                                max-width: 600px;
                                margin: 0 auto;
                                padding: 20px;
                            }
                            .container {
                                background-color: #f9f9f9;
                                border-radius: 8px;
                                padding: 30px;
                                border: 1px solid #e0e0e0;
                            }
                            .header {
                                text-align: center;
                                margin-bottom: 30px;
                            }
                            .header h1 {
                                color: #2c3e50;
                                margin: 0;
                                font-size: 24px;
                            }
                            .content {
                                background-color: white;
                                padding: 25px;
                                border-radius: 6px;
                                margin-bottom: 20px;
                            }
                            .info-box {
                                background-color: #d1ecf1;
                                border-left: 4px solid #17a2b8;
                                padding: 15px;
                                margin: 20px 0;
                                border-radius: 4px;
                            }
                            .details {
                                background-color: #f8f9fa;
                                padding: 15px;
                                border-radius: 4px;
                                margin: 15px 0;
                            }
                            .details p {
                                margin: 8px 0;
                            }
                            .footer {
                                text-align: center;
                                color: #666;
                                font-size: 14px;
                                margin-top: 20px;
                            }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <h1>Refund Initiated</h1>
                            </div>
                            <div class="content">
                                <p>Hello,</p>
                                <p>We're writing to confirm that your refund has been initiated and is being processed.</p>

                                <div class="info-box">
                                    <strong>Refund Amount:</strong> $formattedAmount
                                </div>

                                <div class="details">
                                    <p><strong>Refund ID:</strong> $refundId</p>
                                    <p><strong>Payment ID:</strong> $paymentId</p>
                                    <p><strong>Status:</strong> Processing</p>
                                </div>

                                <p>Your refund is being processed and should be credited to your original payment method within 5-7 business days.</p>

                                <p>You will receive another notification once the refund has been successfully processed.</p>

                                <p>If you have any questions, please don't hesitate to contact our support team.</p>
                            </div>
                            <div class="footer">
                                <p>Thank you for using Androidplay</p>
                                <p style="font-size: 12px; color: #999;">This is an automated message. Please do not reply to this email.</p>
                            </div>
                        </div>
                    </body>
                    </html>
                """.trimIndent()
                Pair(subject, html)
            }

            "processed" -> {
                val subject = "Refund Completed - $formattedAmount"
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            body {
                                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                                line-height: 1.6;
                                color: #333;
                                max-width: 600px;
                                margin: 0 auto;
                                padding: 20px;
                            }
                            .container {
                                background-color: #f9f9f9;
                                border-radius: 8px;
                                padding: 30px;
                                border: 1px solid #e0e0e0;
                            }
                            .header {
                                text-align: center;
                                margin-bottom: 30px;
                            }
                            .header h1 {
                                color: #28a745;
                                margin: 0;
                                font-size: 24px;
                            }
                            .content {
                                background-color: white;
                                padding: 25px;
                                border-radius: 6px;
                                margin-bottom: 20px;
                            }
                            .success-box {
                                background-color: #d4edda;
                                border-left: 4px solid #28a745;
                                padding: 15px;
                                margin: 20px 0;
                                border-radius: 4px;
                            }
                            .details {
                                background-color: #f8f9fa;
                                padding: 15px;
                                border-radius: 4px;
                                margin: 15px 0;
                            }
                            .details p {
                                margin: 8px 0;
                            }
                            .footer {
                                text-align: center;
                                color: #666;
                                font-size: 14px;
                                margin-top: 20px;
                            }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <h1>✓ Refund Completed</h1>
                            </div>
                            <div class="content">
                                <p>Hello,</p>
                                <p>Great news! Your refund has been successfully processed.</p>

                                <div class="success-box">
                                    <strong>Refund Amount:</strong> $formattedAmount
                                </div>

                                <div class="details">
                                    <p><strong>Refund ID:</strong> $refundId</p>
                                    <p><strong>Payment ID:</strong> $paymentId</p>
                                    <p><strong>Status:</strong> Completed</p>
                                </div>

                                <p>The refund amount has been credited to your original payment method. Depending on your bank or payment provider, it may take 5-7 business days for the amount to reflect in your account.</p>

                                <p>If you don't see the refund in your account after 7 business days, please contact your bank or payment provider.</p>

                                <p>Thank you for your patience!</p>
                            </div>
                            <div class="footer">
                                <p>Thank you for using Androidplay</p>
                                <p style="font-size: 12px; color: #999;">This is an automated message. Please do not reply to this email.</p>
                            </div>
                        </div>
                    </body>
                    </html>
                """.trimIndent()
                Pair(subject, html)
            }

            "failed" -> {
                val subject = "Refund Failed - $formattedAmount"
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            body {
                                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                                line-height: 1.6;
                                color: #333;
                                max-width: 600px;
                                margin: 0 auto;
                                padding: 20px;
                            }
                            .container {
                                background-color: #f9f9f9;
                                border-radius: 8px;
                                padding: 30px;
                                border: 1px solid #e0e0e0;
                            }
                            .header {
                                text-align: center;
                                margin-bottom: 30px;
                            }
                            .header h1 {
                                color: #dc3545;
                                margin: 0;
                                font-size: 24px;
                            }
                            .content {
                                background-color: white;
                                padding: 25px;
                                border-radius: 6px;
                                margin-bottom: 20px;
                            }
                            .error-box {
                                background-color: #f8d7da;
                                border-left: 4px solid #dc3545;
                                padding: 15px;
                                margin: 20px 0;
                                border-radius: 4px;
                            }
                            .details {
                                background-color: #f8f9fa;
                                padding: 15px;
                                border-radius: 4px;
                                margin: 15px 0;
                            }
                            .details p {
                                margin: 8px 0;
                            }
                            .footer {
                                text-align: center;
                                color: #666;
                                font-size: 14px;
                                margin-top: 20px;
                            }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <h1>Refund Failed</h1>
                            </div>
                            <div class="content">
                                <p>Hello,</p>
                                <p>We're writing to inform you that your refund could not be processed.</p>

                                <div class="error-box">
                                    <strong>Refund Amount:</strong> $formattedAmount
                                </div>

                                <div class="details">
                                    <p><strong>Refund ID:</strong> $refundId</p>
                                    <p><strong>Payment ID:</strong> $paymentId</p>
                                    <p><strong>Status:</strong> Failed</p>
                                </div>

                                <p>Unfortunately, the refund could not be completed due to a technical issue. Our team has been notified and will investigate this matter.</p>

                                <p>We will attempt to process your refund again, or our support team will contact you directly to resolve this issue.</p>

                                <p>We apologize for any inconvenience this may have caused.</p>
                            </div>
                            <div class="footer">
                                <p>Thank you for using Androidplay</p>
                                <p style="font-size: 12px; color: #999;">This is an automated message. Please do not reply to this email.</p>
                            </div>
                        </div>
                    </body>
                    </html>
                """.trimIndent()
                Pair(subject, html)
            }

            else -> {
                val subject = "Refund Update - $formattedAmount"
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            body {
                                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                                line-height: 1.6;
                                color: #333;
                                max-width: 600px;
                                margin: 0 auto;
                                padding: 20px;
                            }
                            .container {
                                background-color: #f9f9f9;
                                border-radius: 8px;
                                padding: 30px;
                                border: 1px solid #e0e0e0;
                            }
                            .content {
                                background-color: white;
                                padding: 25px;
                                border-radius: 6px;
                            }
                            .details {
                                background-color: #f8f9fa;
                                padding: 15px;
                                border-radius: 4px;
                                margin: 15px 0;
                            }
                            .details p {
                                margin: 8px 0;
                            }
                            .footer {
                                text-align: center;
                                color: #666;
                                font-size: 14px;
                                margin-top: 20px;
                            }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="content">
                                <h2>Refund Update</h2>
                                <div class="details">
                                    <p><strong>Refund ID:</strong> $refundId</p>
                                    <p><strong>Amount:</strong> $formattedAmount</p>
                                    <p><strong>Payment ID:</strong> $paymentId</p>
                                    <p><strong>Status:</strong> $status</p>
                                </div>
                            </div>
                            <div class="footer">
                                <p>Thank you for using Androidplay</p>
                            </div>
                        </div>
                    </body>
                    </html>
                """.trimIndent()
                Pair(subject, html)
            }
        }
    }

    override suspend fun sendEmailWithAttachment(
        userEmail: String,
        subject: String,
        htmlContent: String,
        attachmentData: ByteArray,
        attachmentFilename: String
    ): Result<Boolean> {
        logger.debug("Sending email with attachment to: $userEmail")

        if (sendGridApiKey.isNullOrBlank()) {
            logger.warn("SendGrid API key not configured. Skipping email to $userEmail")
            return Result.success(false)
        }

        return try {
            val base64Content = java.util.Base64.getEncoder().encodeToString(attachmentData)

            val emailPayload = SendGridEmail(
                personalizations = listOf(
                    Personalization(
                        to = listOf(EmailAddress(email = userEmail))
                    )
                ),
                from = EmailAddress(email = fromEmail, name = fromName),
                subject = subject,
                content = listOf(
                    Content(type = "text/html", value = htmlContent)
                ),
                attachments = listOf(
                    Attachment(
                        content = base64Content,
                        type = "application/pdf",
                        filename = attachmentFilename,
                        disposition = "attachment"
                    )
                )
            )

            val response = httpClient.post("https://api.sendgrid.com/v3/mail/send") {
                header("Authorization", "Bearer $sendGridApiKey")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(emailPayload))
            }

            if (response.status.isSuccess()) {
                logger.info("Email with attachment sent successfully to: $userEmail")
                Result.success(true)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to send email with attachment. Status: ${response.status}, Body: $errorBody")
                Result.error("Failed to send email: ${response.status}")
            }
        } catch (e: Exception) {
            logger.error("Failed to send email with attachment to $userEmail", e)
            Result.error("Failed to send email: ${e.message}", e)
        }
    }

    private fun formatDate(dateString: String): String {
        return try {
            val instant = Instant.parse(dateString)
            val formatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy")
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (e: Exception) {
            logger.warn("Failed to format date: $dateString", e)
            dateString
        }
    }

    private fun buildCancellationEmailHtml(endDate: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                        line-height: 1.6;
                        color: #333;
                        max-width: 600px;
                        margin: 0 auto;
                        padding: 20px;
                    }
                    .container {
                        background-color: #f9f9f9;
                        border-radius: 8px;
                        padding: 30px;
                        border: 1px solid #e0e0e0;
                    }
                    .header {
                        text-align: center;
                        margin-bottom: 30px;
                    }
                    .header h1 {
                        color: #2c3e50;
                        margin: 0;
                        font-size: 24px;
                    }
                    .content {
                        background-color: white;
                        padding: 25px;
                        border-radius: 6px;
                        margin-bottom: 20px;
                    }
                    .info-box {
                        background-color: #fff3cd;
                        border-left: 4px solid #ffc107;
                        padding: 15px;
                        margin: 20px 0;
                        border-radius: 4px;
                    }
                    .footer {
                        text-align: center;
                        color: #666;
                        font-size: 14px;
                        margin-top: 20px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Subscription Cancelled</h1>
                    </div>
                    <div class="content">
                        <p>Hello,</p>
                        <p>We're writing to confirm that your premium subscription has been cancelled as requested.</p>

                        <div class="info-box">
                            <strong>Important:</strong> You will continue to have access to premium features until <strong>$endDate</strong>.
                        </div>

                        <p>After this date, your account will revert to the free tier. You can resubscribe at any time to regain access to premium features.</p>

                        <p>If you cancelled by mistake or have changed your mind, you can reactivate your subscription anytime before the end date.</p>

                        <p>We're sorry to see you go! If you have any feedback about why you cancelled, we'd love to hear from you.</p>
                    </div>
                    <div class="footer">
                        <p>Thank you for using Androidplay</p>
                        <p style="font-size: 12px; color: #999;">This is an automated message. Please do not reply to this email.</p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
