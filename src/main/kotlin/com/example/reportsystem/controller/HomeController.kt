package com.example.reportsystem.controller

import com.example.reportsystem.service.PdfService
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class HomeController(private val pdfService: PdfService) {

    @GetMapping("/")
    fun home(): String {
        return "index"
    }

    @GetMapping("/report/pdf")
    fun downloadPdf(): ResponseEntity<ByteArray> {
        val pdfBytes = pdfService.generateReport()

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=lingoland_report.pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdfBytes)
    }

    @GetMapping("/report/docx")
    fun downloadDocx(): ResponseEntity<ByteArray> {
        val resource = ClassPathResource("static/Lingoland学习方案.docx")
        val bytes = resource.inputStream.readBytes()
        val mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")

        val contentDisposition = org.springframework.http.ContentDisposition.attachment()
            .filename("Lingoland学习方案.docx", java.nio.charset.StandardCharsets.UTF_8)
            .build()

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
            .header(HttpHeaders.CONTENT_LENGTH, bytes.size.toString())
            .contentType(mediaType)
            .body(bytes)
    }
}
