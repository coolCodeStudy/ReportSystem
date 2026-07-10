package com.example.reportsystem.frontend

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReportExportTemplateRegressionTest {

    @Test
    fun `report export modal should offer separate Word and PDF commands`() {
        val script = Files.readString(Paths.get("src/main/resources/static/js/report-export.js"))

        assertThat(script).contains(
            "exportWordReportBtn",
            "exportPdfReportBtn",
            "导出 Word",
            "导出 PDF",
            "/student/history/${'$'}{recordId}/export/pdf"
        )
        assertThat(script).doesNotContain("id=\"confirmReportExportSettingsBtn\"")
    }
}

