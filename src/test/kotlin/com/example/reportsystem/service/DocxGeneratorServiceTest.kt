package com.example.reportsystem.service

import com.example.reportsystem.entity.SystemConfig
import com.example.reportsystem.entity.StudentTypeDictionary
import com.example.reportsystem.repository.StudentTypeDictionaryRepository
import com.example.reportsystem.repository.SystemConfigRepository
import io.mockk.every
import io.mockk.mockk
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class DocxGeneratorServiceTest {

    private val systemConfigRepository: SystemConfigRepository = mockk()
    private val studentTypeDictionaryRepository: StudentTypeDictionaryRepository = mockk()
    private val teachingPlanRepository: com.example.reportsystem.repository.TeachingPlanRepository = mockk()
    private lateinit var docxGeneratorService: DocxGeneratorService

    @BeforeEach
    fun setUp() {
        docxGeneratorService = DocxGeneratorService(
            studentTypeDictionaryRepository,
            systemConfigRepository,
            teachingPlanRepository
        )

        // Mock basic configurations
        val mockCsv = """
            "Lingoland","CEFR","蓝思值","词汇量","雅思","体外"
            "K","Pre-A1","160L","400","",""
            "G1","A1","165L","800","",""
            "G2","A2-","425L","1100","",""
            "G3","A2+","600L","1500","3",""
            "G4","B1-","725L","2500","4",""
            "G5","B1+","825L","3500","5",""
            "G6","B2-","925L","4500","5.5",""
        """.trimIndent()
        
        every { systemConfigRepository.findByConfigKey("GLOBAL_CAPABILITY_MATRIX_CSV") } returns SystemConfig(
            configKey = "GLOBAL_CAPABILITY_MATRIX_CSV",
            configValue = mockCsv
        )

        every { systemConfigRepository.findByConfigKey("GLOBAL_ASSESSMENT_DESCRIPTIONS") } returns SystemConfig(
            configKey = "GLOBAL_ASSESSMENT_DESCRIPTIONS",
            configValue = """[{"name":"KET","description":"KET考试说明"}]"""
        )

        every { studentTypeDictionaryRepository.findByTypeCode(any()) } returns StudentTypeDictionary(
            typeCode = "TEST",
            typeName = "Test Type",
            sortOrder = 1,
            capabilityMatrixCsv = mockCsv,
            associatedColumns = "Lingoland,CEFR,蓝思值,词汇量,雅思,体外"
        )
    }

    @Test
    fun `generateDocx should throw exception when targetLevel is legacy format (G1-G11)`() {
        assertThatThrownBy {
            docxGeneratorService.generateDocx("G6", "G5", "TEST", null, null)
        }.isInstanceOf(IllegalArgumentException::class.java)
         .hasMessageContaining("CEFR")
    }

    @Test
    fun `generateDocx should generate valid docx when targetLevel is valid CEFR`() {
        // "B2-" logic is valid CEFR
        val resultBytes = docxGeneratorService.generateDocx("B2-", "G5", "TEST", null, listOf("Lingoland", "CEFR", "雅思"))
        
        assertThat(resultBytes).isNotEmpty()
        
        // Assert we can actually parse it as a valid POI Word doc
        val document = XWPFDocument(ByteArrayInputStream(resultBytes))
        
        // Let's verify that the document is validly parsed
        assertThat(document.paragraphs).isNotEmpty()
    }

    @Test
    fun `generateDocx should append assessment descriptions when assessmentTypes matches configuration`() {
        // "B1-" logic is valid CEFR
        val resultBytes = docxGeneratorService.generateDocx("B1-", "G5", "TEST", listOf("KET", "NoneExistingType"), null)
        
        val document = XWPFDocument(ByteArrayInputStream(resultBytes))
        val allText = document.paragraphs.joinToString("\n") { it.text }
        
        // Verify description is appended exactly
        assertThat(allText).contains("测评说明：")
        assertThat(allText).contains("KET考试说明")
        
        // Since "NoneExistingType" is not in our mock configuration, its text shouldn't be here
    }
}
