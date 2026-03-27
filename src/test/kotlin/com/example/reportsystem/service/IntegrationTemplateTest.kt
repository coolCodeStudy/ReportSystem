package com.example.reportsystem.service

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.File
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
class IntegrationTemplateTest {

    @Autowired
    lateinit var docxGeneratorService: DocxGeneratorService

    @Test
    fun `generate test report to verify template structure`() {
        val bytes = docxGeneratorService.generateDocx(
            targetLevel = "A2",
            targetGrade = "G3",
            studentType = "Public",
            assessmentTypes = listOf("KET"),
            otherAssessment = "",
            assessmentResultsJson = "{\"reading\":{\"score\":45,\"total\":50,\"level\":\"A2\",\"paperAnalysis\":{},\"causeAnalysis\":[]}}",
            teachingPlanDataJson = "{}"
        )
        val file = File("/tmp/test_output.docx")
        file.writeBytes(bytes)
        println("Generated test DOCX at ${file.absolutePath}")
    }
}
