package com.example.reportsystem

import com.example.reportsystem.service.DocxGeneratorService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.io.File

@Component
class TestReportRunner(
    private val docxGeneratorService: DocxGeneratorService
) : CommandLineRunner {
    override fun run(vararg args: String?) {
        if (args.contains("--test-docx")) {
            val bytes = docxGeneratorService.generateDocx(
                targetLevel = "A2",
                targetGrade = "G3",
                studentType = "Public",
                assessmentTypes = listOf("KET"),
                otherAssessment = "",
                assessmentResultsJson = "{\"reading\":{\"score\":45,\"total\":50,\"level\":\"A2\",\"paperAnalysis\":{},\"causeAnalysis\":[]}}",
                teachingPlanDataJson = "{}"
            )
            File("/tmp/output.docx").writeBytes(bytes)
            println("Wrote /tmp/output.docx")
        }
    }
}
