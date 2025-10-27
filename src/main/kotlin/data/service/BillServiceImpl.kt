package data.service

import bose.ankush.data.model.BillGenerationResponse
import bose.ankush.data.model.Payment
import bose.ankush.data.model.Subscription
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import domain.model.Result
import domain.repository.PaymentRepository
import domain.repository.UserRepository
import domain.service.BillService
import domain.service.EmailService
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Implementation of BillService using iText for PDF generation.
 */
class BillServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val userRepository: UserRepository,
    private val emailService: EmailService
) : BillService {
    private val logger = LoggerFactory.getLogger(BillServiceImpl::class.java)

    override suspend fun generateBill(
        userEmail: String,
        paymentIds: List<String>,
        subscriptionIds: List<String>
    ): Result<ByteArray> {
        logger.debug("Generating bill for user: $userEmail")
        return try {
            // Fetch user
            val userResult = userRepository.findUserByEmail(userEmail)
            if (userResult is Result.Error) {
                return Result.error("User not found: ${userResult.message}")
            }
            val user = (userResult as Result.Success<bose.ankush.data.model.User?>).data
                ?: return Result.error("User not found")

            // Fetch payments
            val paymentsResult = paymentRepository.getPaymentsByUserEmail(userEmail)
            if (paymentsResult is Result.Error) {
                return Result.error("Failed to fetch payments: ${paymentsResult.message}")
            }
            val allPayments = (paymentsResult as Result.Success).data
            val selectedPayments = allPayments.filter { paymentIds.contains(it.id) }

            // Get selected subscriptions
            val selectedSubscriptions = user.subscriptions.filter { sub ->
                subscriptionIds.contains(sub.sourcePaymentId)
            }

            // Generate invoice number
            val invoiceNumber = generateInvoiceNumber()

            // Generate PDF
            val pdfBytes = createPdfInvoice(
                invoiceNumber = invoiceNumber,
                userEmail = userEmail,
                payments = selectedPayments,
                subscriptions = selectedSubscriptions
            )

            logger.debug("Bill generated successfully: $invoiceNumber")
            Result.success(pdfBytes)
        } catch (e: Exception) {
            logger.error("Failed to generate bill", e)
            Result.error("Failed to generate bill: ${e.message}", e)
        }
    }

    override suspend fun generateAndSendBill(
        adminEmail: String,
        userEmail: String,
        paymentIds: List<String>,
        subscriptionIds: List<String>
    ): Result<BillGenerationResponse> {
        logger.debug("Generating and sending bill for user: $userEmail by admin: $adminEmail")
        return try {
            // Generate PDF
            val billResult = generateBill(userEmail, paymentIds, subscriptionIds)
            if (billResult is Result.Error) {
                return Result.error(billResult.message)
            }
            val pdfBytes = (billResult as Result.Success).data
            val invoiceNumber = generateInvoiceNumber()

            // Send via email with attachment
            var emailSent = false
            var emailError: String? = null

            try {
                val emailSubject = "Your Invoice - $invoiceNumber"
                val emailHtmlContent = buildBillEmailHtml(invoiceNumber, userEmail)
                val filename = "$invoiceNumber.pdf"

                val emailResult = emailService.sendEmailWithAttachment(
                    userEmail = userEmail,
                    subject = emailSubject,
                    htmlContent = emailHtmlContent,
                    attachmentData = pdfBytes,
                    attachmentFilename = filename
                )

                emailSent = emailResult is Result.Success && emailResult.data == true
                if (emailResult is Result.Error) {
                    emailError = emailResult.message
                }

                logger.info("Bill sent via email to $userEmail: $invoiceNumber (success: $emailSent)")
            } catch (e: Exception) {
                logger.warn("Failed to send bill via email", e)
                emailError = e.message
            }

            val response = BillGenerationResponse(
                success = true,
                message = if (emailSent) "Bill generated and sent successfully via email"
                else "Bill generated successfully. ${emailError ?: "Email not sent"}",
                invoiceNumber = invoiceNumber,
                emailSent = emailSent
            )

            logger.info("Bill generated by admin $adminEmail for user $userEmail: $invoiceNumber")
            Result.success(response)
        } catch (e: Exception) {
            logger.error("Failed to generate and send bill", e)
            Result.error("Failed to generate and send bill: ${e.message}", e)
        }
    }

    /**
     * Generates a unique invoice number using timestamp and random suffix.
     * Format: INV-{timestamp}-{random}
     *
     * Example: INV-1627660800123-456
     *
     * @return Unique invoice number string
     */
    private fun generateInvoiceNumber(): String {
        val timestamp = System.currentTimeMillis()
        val random = Random().nextInt(1000)
        return "INV-${timestamp}-${random}"
    }

    /**
     * Creates a PDF invoice document using iText library.
     *
     * The invoice includes:
     * - Company header and branding
     * - Invoice metadata (number, dates)
     * - Customer information
     * - Itemized list of charges (payments and subscriptions)
     * - Subtotal, tax, and total calculations
     * - Payment instructions and footer
     *
     * @param invoiceNumber Unique invoice identifier
     * @param userEmail Customer email address
     * @param payments List of payment records to include
     * @param subscriptions List of subscription records to include
     * @return PDF document as byte array
     */
    private fun createPdfInvoice(
        invoiceNumber: String,
        userEmail: String,
        payments: List<Payment>,
        subscriptions: List<Subscription>
    ): ByteArray {
        // Use ByteArrayOutputStream to generate PDF in memory
        val outputStream = ByteArrayOutputStream()
        val writer = PdfWriter(outputStream)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc)

        try {
            // === HEADER SECTION ===
            // Add centered "INVOICE" title
            document.add(
                Paragraph("INVOICE")
                    .setFontSize(24f)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
            )

            document.add(Paragraph("\n"))

            // Add company information
            document.add(
                Paragraph("Androidplay")
                    .setFontSize(14f)
                    .setBold()
            )
            document.add(Paragraph("Weather API Services"))
            document.add(Paragraph("https://androidplay.com"))
            document.add(Paragraph("\n"))

            // === INVOICE METADATA SECTION ===
            // Calculate invoice and due dates
            val now = Instant.now()
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.systemDefault())
            val invoiceDate = formatter.format(now)
            val dueDate = formatter.format(now.plusSeconds(30L * 24 * 60 * 60)) // 30 days from now

            // Create 2-column table for metadata (label on left, value on right)
            val metadataTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                .useAllAvailableWidth()

            metadataTable.addCell(
                Cell().add(Paragraph("Invoice Number:").setBold())
                    .setBorder(null)
            )
            metadataTable.addCell(
                Cell().add(Paragraph(invoiceNumber))
                    .setBorder(null)
                    .setTextAlignment(TextAlignment.RIGHT)
            )

            metadataTable.addCell(
                Cell().add(Paragraph("Invoice Date:").setBold())
                    .setBorder(null)
            )
            metadataTable.addCell(
                Cell().add(Paragraph(invoiceDate))
                    .setBorder(null)
                    .setTextAlignment(TextAlignment.RIGHT)
            )

            metadataTable.addCell(
                Cell().add(Paragraph("Due Date:").setBold())
                    .setBorder(null)
            )
            metadataTable.addCell(
                Cell().add(Paragraph(dueDate))
                    .setBorder(null)
                    .setTextAlignment(TextAlignment.RIGHT)
            )

            document.add(metadataTable)
            document.add(Paragraph("\n"))

            // === CUSTOMER INFORMATION ===
            document.add(Paragraph("Bill To:").setBold())
            document.add(Paragraph(userEmail))
            document.add(Paragraph("\n"))

            // === ITEMIZED CHARGES TABLE ===
            // Create 3-column table: Description (60%), Service Period (30%), Amount (10%)
            val itemsTable = Table(UnitValue.createPercentArray(floatArrayOf(3f, 2f, 1f)))
                .useAllAvailableWidth()

            // Add header row with gray background
            itemsTable.addHeaderCell(
                Cell().add(Paragraph("Description").setBold())
                    .setBackgroundColor(ColorConstants.LIGHT_GRAY)
            )
            itemsTable.addHeaderCell(
                Cell().add(Paragraph("Service Period").setBold())
                    .setBackgroundColor(ColorConstants.LIGHT_GRAY)
            )
            itemsTable.addHeaderCell(
                Cell().add(Paragraph("Amount").setBold())
                    .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                    .setTextAlignment(TextAlignment.RIGHT)
            )

            var subtotal = 0.0

            // Add each payment as a line item
            // Amounts are stored in paise/cents, so divide by 100
            payments.forEach { payment ->
                val amount = (payment.amount ?: 0).toDouble() / 100.0
                subtotal += amount

                itemsTable.addCell(Cell().add(Paragraph("Payment - ${payment.status}")))
                itemsTable.addCell(Cell().add(Paragraph(formatDate(payment.createdAt))))
                itemsTable.addCell(
                    Cell().add(Paragraph("${payment.currency ?: "INR"} %.2f".format(amount)))
                        .setTextAlignment(TextAlignment.RIGHT)
                )
            }

            // Add each subscription as a line item
            // Try to find the associated payment to get the amount
            subscriptions.forEach { subscription ->
                val associatedPayment = payments.find { it.paymentId == subscription.sourcePaymentId }
                val amount = (associatedPayment?.amount ?: 0).toDouble() / 100.0

                // Only add to subtotal if we found a valid amount
                // This prevents double-counting if the payment was already included above
                if (amount > 0) {
                    subtotal += amount
                }

                itemsTable.addCell(
                    Cell().add(Paragraph("${subscription.service.name} Subscription"))
                )
                itemsTable.addCell(
                    Cell().add(Paragraph("${formatDate(subscription.startDate)} to ${formatDate(subscription.endDate)}"))
                )
                itemsTable.addCell(
                    Cell().add(Paragraph("INR %.2f".format(amount)))
                        .setTextAlignment(TextAlignment.RIGHT)
                )
            }

            document.add(itemsTable)
            document.add(Paragraph("\n"))

            // === TOTALS SECTION ===
            // Create 2-column table for totals (label on left, amount on right)
            val totalsTable = Table(UnitValue.createPercentArray(floatArrayOf(4f, 1f)))
                .useAllAvailableWidth()

            totalsTable.addCell(
                Cell().add(Paragraph("Subtotal:").setBold())
                    .setBorder(null)
                    .setTextAlignment(TextAlignment.RIGHT)
            )
            totalsTable.addCell(
                Cell().add(Paragraph("INR %.2f".format(subtotal)))
                    .setBorder(null)
                    .setTextAlignment(TextAlignment.RIGHT)
            )

            // Tax calculation (currently 0% - can be updated based on business requirements)
            totalsTable.addCell(
                Cell().add(Paragraph("Tax (0%):").setBold())
                    .setBorder(null)
                    .setTextAlignment(TextAlignment.RIGHT)
            )
            totalsTable.addCell(
                Cell().add(Paragraph("INR 0.00"))
                    .setBorder(null)
                    .setTextAlignment(TextAlignment.RIGHT)
            )

            // Total (larger font for emphasis)
            totalsTable.addCell(
                Cell().add(Paragraph("Total:").setBold().setFontSize(14f))
                    .setBorder(null)
                    .setTextAlignment(TextAlignment.RIGHT)
            )
            totalsTable.addCell(
                Cell().add(Paragraph("INR %.2f".format(subtotal)).setBold().setFontSize(14f))
                    .setBorder(null)
                    .setTextAlignment(TextAlignment.RIGHT)
            )

            document.add(totalsTable)
            document.add(Paragraph("\n\n"))

            // === PAYMENT INSTRUCTIONS ===
            document.add(Paragraph("Payment Instructions:").setBold())
            document.add(Paragraph("Thank you for using Androidplay Weather API services."))
            document.add(Paragraph("For any questions regarding this invoice, please contact support."))
            document.add(Paragraph("\n"))

            // === FOOTER ===
            document.add(
                Paragraph("Thank you for your business!")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(10f)
            )

        } finally {
            // Always close the document to finalize the PDF
            document.close()
        }

        return outputStream.toByteArray()
    }

    /**
     * Formats an ISO 8601 date string to a human-readable format.
     *
     * @param dateString ISO 8601 formatted date string (e.g., "2024-02-15T10:30:00Z")
     * @return Formatted date string (e.g., "Feb 15, 2024") or original string if parsing fails
     */
    private fun formatDate(dateString: String): String {
        return try {
            val instant = Instant.parse(dateString)
            val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (e: Exception) {
            // Return original string if parsing fails
            dateString
        }
    }

    /**
     * Builds HTML email content for bill notification
     *
     * @param invoiceNumber The invoice number
     * @param userEmail The user's email address
     * @return HTML email content
     */
    private fun buildBillEmailHtml(invoiceNumber: String, userEmail: String): String {
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
                        background-color: #e3f2fd;
                        border-left: 4px solid #2196f3;
                        padding: 15px;
                        margin: 20px 0;
                        border-radius: 4px;
                    }
                    .invoice-number {
                        font-size: 20px;
                        font-weight: bold;
                        color: #2196f3;
                        text-align: center;
                        margin: 20px 0;
                        padding: 15px;
                        background-color: #f5f5f5;
                        border-radius: 6px;
                    }
                    .footer {
                        text-align: center;
                        color: #666;
                        font-size: 14px;
                        margin-top: 20px;
                    }
                    .attachment-note {
                        background-color: #fff3cd;
                        border-left: 4px solid #ffc107;
                        padding: 12px;
                        margin: 15px 0;
                        border-radius: 4px;
                        font-size: 14px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>📄 Your Invoice is Ready</h1>
                    </div>
                    <div class="content">
                        <p>Hello,</p>
                        <p>Thank you for using Androidplay Weather API services. Your invoice has been generated and is attached to this email.</p>

                        <div class="invoice-number">
                            Invoice #$invoiceNumber
                        </div>

                        <div class="attachment-note">
                            <strong>📎 Attachment:</strong> Please find your invoice attached as a PDF file. You can download and save it for your records.
                        </div>

                        <div class="info-box">
                            <strong>Need Help?</strong> If you have any questions about this invoice or need assistance, please don't hesitate to contact our support team.
                        </div>

                        <p>We appreciate your business and look forward to continuing to serve you.</p>
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
