package com.transloom.services

import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.element.Text
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.transloom.domain.BillingPlan
import com.transloom.domain.InvoiceRecord
import com.transloom.domain.Subscription
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.ByteArrayOutputStream

/**
 * Renders an InvoiceRecord into a branded PDF that matches Transloom's dark/emerald
 * visual identity. Built entirely with iText kernel + layout — no HTML→PDF detour —
 * so byte size stays small and rendering is deterministic.
 *
 * The layout deliberately leans minimal: a generous dark canvas, a single accent
 * line in emerald, and quiet sans-serif typography. The goal is for the document
 * to feel like a product receipt from a modern SaaS (Linear, Vercel, Stripe) rather
 * than a tax form.
 */
object InvoicePdfGenerator {

    // ─── Brand palette (sourced from Transloom checkout + tailwind theme) ─────
    private val BG = DeviceRgb(10, 10, 10)                 // #0A0A0A — page background
    private val SURFACE = DeviceRgb(26, 26, 26)            // #1A1A1A — card surface
    private val BORDER_SUBTLE = DeviceRgb(42, 42, 42)      // #2A2A2A — divider
    private val TEXT_PRIMARY = DeviceRgb(245, 240, 235)    // #F5F0EB — body text
    private val TEXT_MUTED = DeviceRgb(138, 132, 120)      // #8A8478 — labels
    private val EMERALD = DeviceRgb(0, 229, 160)           // #00E5A0 — primary accent
    private val EMERALD_DEEP = DeviceRgb(0, 168, 122)      // #00A87A — gradient end
    private val GOLD = DeviceRgb(201, 169, 110)            // #C9A96E — secondary accent (subtle)

    data class InvoiceContext(
        val invoice: InvoiceRecord,
        val userEmail: String?,
        val userName: String,
        val plan: BillingPlan,
        val subscription: Subscription?
    )

    fun render(ctx: InvoiceContext): ByteArray {
        val out = ByteArrayOutputStream()
        PdfWriter(out).use { writer ->
            PdfDocument(writer).use { pdf ->
                pdf.defaultPageSize = PageSize.A4
                val regular = PdfFontFactory.createFont(StandardFonts.HELVETICA)
                val bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)
                val mono = PdfFontFactory.createFont(StandardFonts.COURIER)

                Document(pdf).use { doc ->
                    doc.setMargins(56f, 56f, 56f, 56f)

                    // Paint the dark background across the entire page before any content lands on it.
                    val page = pdf.addNewPage()
                    val pageRect = page.pageSize
                    PdfCanvas(page).apply {
                        saveState()
                        setFillColor(BG)
                        rectangle(0.0, 0.0, pageRect.width.toDouble(), pageRect.height.toDouble())
                        fill()
                        restoreState()
                    }
                    drawLogo(page, x = 56f, y = pageRect.height - 88f)
                    drawAccentBar(page, pageRect)

                    doc.add(headerBlock(regular, bold, mono, ctx))
                    doc.add(spacer(28f))
                    doc.add(metaBlock(regular, bold, mono, ctx))
                    doc.add(spacer(28f))
                    doc.add(lineItems(regular, bold, ctx))
                    doc.add(spacer(20f))
                    doc.add(totalBlock(regular, bold, ctx))
                    doc.add(spacer(56f))
                    doc.add(footerBlock(regular, bold))
                }
            }
        }
        return out.toByteArray()
    }

    // ─── Visual elements rendered directly on the page canvas ────────────────

    /**
     * The Transloom mark: a rounded emerald square with two crossing lines, mirroring
     * the checkout / favicon SVG. iText doesn't do real gradients in kernel without
     * shading patterns, so we approximate the #00F5B0 → #00A87A feel by painting two
     * overlapping rounded rects with slight transparency offset.
     */
    private fun drawLogo(page: com.itextpdf.kernel.pdf.PdfPage, x: Float, y: Float) {
        val size = 28f
        val radius = 6f
        val canvas = PdfCanvas(page)
        canvas.saveState()
        // Bright top-left half
        canvas.setFillColor(DeviceRgb(0, 245, 176))
        canvas.roundRectangle(x.toDouble(), y.toDouble(), size.toDouble(), size.toDouble(), radius.toDouble())
        canvas.fill()
        // Darker bottom-right overlay creates a pseudo-gradient
        canvas.setFillColor(EMERALD_DEEP)
        canvas.roundRectangle(
            (x + size / 2f).toDouble(), y.toDouble(),
            (size / 2f).toDouble(), (size / 2f).toDouble(),
            0.0
        )
        canvas.fill()

        // Two thin dark "loom" lines crossing through the mark
        canvas.setStrokeColor(DeviceRgb(10, 10, 10))
        canvas.setLineWidth(2f)
        // horizontal
        canvas.moveTo((x + 6f).toDouble(), (y + size / 2f).toDouble())
        canvas.lineTo((x + size - 6f).toDouble(), (y + size / 2f).toDouble())
        canvas.stroke()
        // vertical
        canvas.moveTo((x + size / 2f).toDouble(), (y + 6f).toDouble())
        canvas.lineTo((x + size / 2f).toDouble(), (y + size - 6f).toDouble())
        canvas.stroke()
        canvas.restoreState()
    }

    /** Single hairline emerald accent — separates header from body, anchors the brand. */
    private fun drawAccentBar(page: com.itextpdf.kernel.pdf.PdfPage, pageRect: Rectangle) {
        val canvas = PdfCanvas(page)
        canvas.saveState()
        canvas.setFillColor(EMERALD)
        // Short solid bar at the very top — a band of brand colour reading like a status LED
        canvas.rectangle(0.0, (pageRect.height - 4).toDouble(), 160.0, 4.0)
        canvas.fill()
        canvas.restoreState()
    }

    // ─── Document blocks ──────────────────────────────────────────────────────

    private fun headerBlock(regular: PdfFont, bold: PdfFont, mono: PdfFont, ctx: InvoiceContext): Table {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
            .useAllAvailableWidth()
            .setBorder(Border.NO_BORDER)

        val left = Cell().apply {
            setBorder(Border.NO_BORDER)
            setPaddingLeft(40f) // leave space for the canvas-drawn logo
            add(Paragraph("TRANSLOOM")
                .setFont(bold).setFontSize(13f).setFontColor(TEXT_PRIMARY)
                .setCharacterSpacing(3f).setMarginBottom(2f))
            add(Paragraph("Translation infrastructure")
                .setFont(regular).setFontSize(8f).setFontColor(TEXT_MUTED)
                .setCharacterSpacing(1.5f))
        }

        val right = Cell().apply {
            setBorder(Border.NO_BORDER)
            setTextAlignment(TextAlignment.RIGHT)
            add(Paragraph("INVOICE")
                .setFont(bold).setFontSize(22f).setFontColor(TEXT_PRIMARY)
                .setCharacterSpacing(4f).setMarginBottom(6f))
            add(Paragraph(ctx.invoice.razorpayPaymentId)
                .setFont(mono).setFontSize(8f).setFontColor(EMERALD)
                .setMarginBottom(0f))
        }

        table.addCell(left)
        table.addCell(right)
        return table
    }

    private fun metaBlock(regular: PdfFont, bold: PdfFont, mono: PdfFont, ctx: InvoiceContext): Table {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
            .useAllAvailableWidth()
            .setBorder(Border.NO_BORDER)

        val dateStr = ctx.invoice.createdAt.toLocalDateTime(TimeZone.UTC).date.toString()
        val periodStr = ctx.invoice.periodEnd.toLocalDateTime(TimeZone.UTC).date.toString()

        val left = Cell().apply {
            setBorder(Border.NO_BORDER)
            add(label("BILLED TO", regular))
            add(value(ctx.userName, bold))
            ctx.userEmail?.let { add(value(it, regular)) }
        }
        val right = Cell().apply {
            setBorder(Border.NO_BORDER)
            setTextAlignment(TextAlignment.RIGHT)
            add(label("ISSUED", regular))
            add(value(dateStr, bold))
            add(label("NEXT RENEWAL", regular).setMarginTop(10f))
            add(value(periodStr, regular))
        }

        table.addCell(left)
        table.addCell(right)
        return table
    }

    private fun lineItems(regular: PdfFont, bold: PdfFont, ctx: InvoiceContext): Table {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(3f, 1f, 1f)))
            .useAllAvailableWidth()
            .setBackgroundColor(SURFACE)
            .setBorder(SolidBorder(BORDER_SUBTLE, 0.5f))
            .setMarginTop(6f)

        // Header row
        table.addHeaderCell(headerCell("DESCRIPTION", regular, TextAlignment.LEFT))
        table.addHeaderCell(headerCell("PLAN", regular, TextAlignment.CENTER))
        table.addHeaderCell(headerCell("AMOUNT", regular, TextAlignment.RIGHT))

        val priceLine = formatAmount(ctx.invoice.amountPaise, ctx.invoice.currency)
        val description = buildString {
            append("Transloom ")
            append(ctx.plan.displayName)
            append(" — monthly subscription")
        }
        val statusText = ctx.invoice.status.replaceFirstChar { it.uppercase() }
        val statusColor = if (ctx.invoice.status.equals("captured", true) ||
            ctx.invoice.status.equals("paid", true)) EMERALD else GOLD

        table.addCell(itemCell(description, regular, TextAlignment.LEFT).apply {
            add(Paragraph(statusText)
                .setFont(regular).setFontSize(7.5f).setFontColor(statusColor)
                .setCharacterSpacing(1.2f).setMarginTop(4f))
        })
        table.addCell(itemCell(ctx.plan.displayName, regular, TextAlignment.CENTER))
        table.addCell(itemCell(priceLine, bold, TextAlignment.RIGHT))

        return table
    }

    private fun totalBlock(regular: PdfFont, bold: PdfFont, ctx: InvoiceContext): Table {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(3f, 2f)))
            .useAllAvailableWidth()
            .setBorder(Border.NO_BORDER)

        val left = Cell().apply { setBorder(Border.NO_BORDER) }

        val right = Cell().apply {
            setBorder(Border.NO_BORDER)
            setBorderTop(SolidBorder(EMERALD, 1f))
            setPaddingTop(12f)
            setTextAlignment(TextAlignment.RIGHT)

            val total = Paragraph()
                .setMarginBottom(2f)
                .add(Text("TOTAL  ")
                    .setFont(regular).setFontSize(9f).setFontColor(TEXT_MUTED)
                    .setCharacterSpacing(2f))
                .add(Text(formatAmount(ctx.invoice.amountPaise, ctx.invoice.currency))
                    .setFont(bold).setFontSize(20f).setFontColor(TEXT_PRIMARY))
            add(total)
            add(Paragraph("Paid via Razorpay · ${ctx.invoice.currency.uppercase()}")
                .setFont(regular).setFontSize(8f).setFontColor(TEXT_MUTED))
        }

        table.addCell(left)
        table.addCell(right)
        return table
    }

    private fun footerBlock(regular: PdfFont, bold: PdfFont): Paragraph {
        return Paragraph()
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(TEXT_MUTED).setFontSize(8.5f).setFont(regular)
            .add(Text("Thank you for building with Transloom.\n"))
            .add(Text("Transloom by Androidplay  ·  support@androidplay.in").setFont(bold).setFontColor(TEXT_PRIMARY))
            .add(Text("\nGST-compliant invoices are also available from your Razorpay customer portal."))
    }

    // ─── Cell helpers ────────────────────────────────────────────────────────

    private fun label(text: String, font: PdfFont): Paragraph =
        Paragraph(text).setFont(font).setFontSize(7.5f).setFontColor(TEXT_MUTED)
            .setCharacterSpacing(1.8f).setMarginBottom(4f)

    private fun value(text: String, font: PdfFont): Paragraph =
        Paragraph(text).setFont(font).setFontSize(11f).setFontColor(TEXT_PRIMARY).setMarginBottom(2f)

    private fun headerCell(text: String, font: PdfFont, align: TextAlignment): Cell =
        Cell().apply {
            setBorder(Border.NO_BORDER)
            setBorderBottom(SolidBorder(BORDER_SUBTLE, 0.5f))
            setPaddings(14f, 18f, 12f, 18f)
            setTextAlignment(align)
            add(Paragraph(text).setFont(font).setFontSize(7.5f).setFontColor(TEXT_MUTED).setCharacterSpacing(1.8f))
        }

    private fun itemCell(text: String, font: PdfFont, align: TextAlignment): Cell =
        Cell().apply {
            setBorder(Border.NO_BORDER)
            setPaddings(16f, 18f, 16f, 18f)
            setTextAlignment(align)
            add(Paragraph(text).setFont(font).setFontSize(11f).setFontColor(TEXT_PRIMARY))
        }

    private fun spacer(height: Float): Paragraph =
        Paragraph(" ").setFontSize(height).setMargin(0f)

    private fun formatAmount(paise: Int, currency: String): String {
        val major = paise / 100.0
        val symbol = if (currency.equals("INR", true)) "Rs " else "${currency.uppercase()} "
        return "$symbol${"%,.2f".format(major)}"
    }
}
