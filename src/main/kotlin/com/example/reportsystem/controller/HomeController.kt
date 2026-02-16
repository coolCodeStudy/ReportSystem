package com.example.reportsystem.controller

import com.example.reportsystem.service.PdfService
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
}
