package com.example.reportsystem.service

import com.lowagie.text.Document
import com.lowagie.text.Font
import com.lowagie.text.Image
import com.lowagie.text.Paragraph
import com.lowagie.text.pdf.BaseFont
import com.lowagie.text.pdf.PdfWriter
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.awt.Color
import java.io.ByteArrayOutputStream

@Service
class PdfService {

    fun generateReport(): ByteArray {
        val document = Document()
        val out = ByteArrayOutputStream()

        try {
            PdfWriter.getInstance(document, out)
            document.open()

            // Add Logo
            try {
                val logoResource = ClassPathResource("static/images/lingoland_logo.jpg")
                if (logoResource.exists()) {
                    val logo = Image.getInstance(logoResource.url)
                    logo.scaleToFit(150f, 150f)
                    logo.alignment = Image.ALIGN_CENTER
                    document.add(logo)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Add Title
            // Note: For Chinese characters support, we would need a Chinese font.
            // For now, using standard font, assuming English content or standard fallback.
            // If Chinese is needed, we'd need to load a simplified chinese font.
            // Using HELVETICA for English title for now.
            val titleFont = Font(Font.HELVETICA, 24f, Font.BOLD, Color.BLACK)
            val title = Paragraph("\nLingoland Assessment Report", titleFont)
            title.alignment = Paragraph.ALIGN_CENTER
            document.add(title)

            document.add(Paragraph("\n"))
            document.add(Paragraph("\n"))

            // Add Content
            val bodyFont = Font(Font.HELVETICA, 12f, Font.NORMAL, Color.DARK_GRAY)
            document.add(Paragraph("Name: Student Name", bodyFont))
            document.add(Paragraph("Date: 2023-10-27", bodyFont))
            document.add(Paragraph("Score: 95/100", bodyFont))
            
            document.add(Paragraph("\n"))
            document.add(Paragraph("Feedback:", bodyFont))
            document.add(Paragraph("Great job! You have shown excellent understanding of the course material.", bodyFont))

            document.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return out.toByteArray()
    }
}
