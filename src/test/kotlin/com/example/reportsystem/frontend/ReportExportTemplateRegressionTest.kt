package com.example.reportsystem.frontend

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReportExportTemplateRegressionTest {

    @Test
    fun `report export modal should choose a format before one export command`() {
        val script = Files.readString(Paths.get("src/main/resources/static/js/report-export.js"))

        assertThat(script).contains(
            "reportExportFormatWord",
            "reportExportFormatPdf",
            "name=\"reportExportFormat\"",
            "value=\"word\" checked",
            "confirmReportExportBtn",
            "导出报告",
            "/student/history/${'$'}{recordId}/export/pdf"
        )
        assertThat(script).doesNotContain(
            "exportWordReportBtn",
            "exportPdfReportBtn"
        )
    }
}
