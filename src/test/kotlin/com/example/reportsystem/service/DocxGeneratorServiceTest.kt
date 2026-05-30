package com.example.reportsystem.service

import com.example.reportsystem.entity.SystemConfig
import com.example.reportsystem.entity.StudentTypeDictionary
import com.example.reportsystem.repository.StudentTypeDictionaryRepository
import com.example.reportsystem.repository.SystemConfigRepository
import com.example.reportsystem.service.docx.DocxStyleUtils
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.apache.poi.xwpf.usermodel.XWPFTable
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
    private val textbookConfigRepository: com.example.reportsystem.repository.TextbookConfigRepository = mockk()
    private lateinit var docxGeneratorService: DocxGeneratorService

    @BeforeEach
    fun setUp() {
        docxGeneratorService = DocxGeneratorService(
            studentTypeDictionaryRepository,
            systemConfigRepository,
            teachingPlanRepository,
            textbookConfigRepository
        )

        every { systemConfigRepository.findByConfigKey(any()) } returns null
        every { teachingPlanRepository.findByBookNameIn(any()) } returns emptyList()
        every { textbookConfigRepository.findByBookName(any()) } returns null

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
    fun `generateDocx should apply export table color and font`() {
        val resultBytes = docxGeneratorService.generateDocx("B2-", "G5", "TEST", null, listOf("Lingoland", "CEFR", "雅思"))
        val document = XWPFDocument(ByteArrayInputStream(resultBytes))

        val headerFill = normalizeHexColor(document.tables.first().getRow(0).getCell(0).ctTc.tcPr.shd.fill)
        assertThat(headerFill).isEqualTo(DocxStyleUtils.THEME_PRIMARY)

        val runsWithText = collectRuns(document)
            .filter { it.text().isNotBlank() }

        assertThat(runsWithText).isNotEmpty()
        runsWithText.forEach { run ->
            assertThat(run.ctr.rPr.getRFontsArray(0).eastAsia).isEqualTo(DocxStyleUtils.FONT_MAIN)
        }
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

    @Test
    fun `generateDocx should render assessment analysis from saved results even when subject config is missing`() {
        val assessmentResultsJson = """
            {
              "reading": {
                "score": 18,
                "total": 30,
                "level": "A2",
                "paperAnalysis": {
                  "信息理解": { "status": "✅", "text": "理解主旨和关键信息。" }
                },
                "causeAnalysis": ["阅读量不足，缺乏语感"]
              }
            }
        """.trimIndent()

        val resultBytes = docxGeneratorService.generateDocx(
            targetLevel = "B1-",
            targetGrade = "G5",
            studentType = "TEST",
            assessmentTypes = listOf("KET"),
            selectedColumns = null,
            assessmentResultsJson = assessmentResultsJson
        )

        val document = XWPFDocument(ByteArrayInputStream(resultBytes))
        val allText = documentText(document)

        assertThat(allText).contains("卷面分析")
        assertThat(allText).contains("信息理解")
        assertThat(allText).contains("理解主旨和关键信息。")
        assertThat(allText).doesNotContain("暂无测评分析数据")
    }

    @Test
    fun `generateDocx should infer subject display names from cloud subject dimensions`() {
        val assessmentResultsJson = """
            {
              "subj_8swx8y": {
                "score": 22,
                "total": 25,
                "level": "B1",
                "paperAnalysis": {
                  "拼写": { "status": "✅", "text": "拼写基本准确。" },
                  "标点符号": { "status": "✅", "text": "能正确使用常见标点。" },
                  "写作惯例（Cohesion, Unity, Completeness）": { "status": "⚠️", "text": "段落衔接还可以继续强化。" }
                },
                "causeAnalysis": []
              },
              "subj_gpuo0p": {
                "score": 23,
                "total": 25,
                "level": "B1",
                "paperAnalysis": {
                  "阅读速度": { "status": "✅", "text": "阅读速度稳定。" },
                  "阅读策略": { "status": "✅", "text": "能抓住关键信息。" }
                },
                "causeAnalysis": []
              },
              "subj_explicit": {
                "subjectName": "Listening",
                "score": 19,
                "total": 25,
                "level": "A2",
                "paperAnalysis": {
                  "信息理解": { "status": "✅", "text": "能听懂主要信息。" }
                },
                "causeAnalysis": []
              }
            }
        """.trimIndent()

        val resultBytes = docxGeneratorService.generateDocx(
            targetLevel = "B1-",
            targetGrade = "G5",
            studentType = "TEST",
            assessmentTypes = listOf("KET"),
            selectedColumns = null,
            assessmentResultsJson = assessmentResultsJson
        )

        val document = XWPFDocument(ByteArrayInputStream(resultBytes))
        val allText = documentText(document)

        assertThat(allText).contains("▎ 写作")
        assertThat(allText).contains("得分 22/25")
        assertThat(allText).contains("▎ 阅读")
        assertThat(allText).contains("正确率 23/25")
        assertThat(allText).contains("▎ 听力")
        assertThat(allText).contains("正确率 19/25")
        assertThat(allText).doesNotContain("subj_8swx8y")
        assertThat(allText).doesNotContain("subj_gpuo0p")
        assertThat(allText).doesNotContain("subj_explicit")
    }

    @Test
    fun `generateDocx should style teaching approach numbered headings consistently`() {
        val teachingPlanDataJson = """
            {
              "teachingApproach": "1. 强化语言基础：语法和词汇\n通过专项训练补足基础。\n2. 重点强化写作能力，坚固口语能力\n通过输出任务提升表达。"
            }
        """.trimIndent()

        val resultBytes = docxGeneratorService.generateDocx(
            targetLevel = "B1-",
            targetGrade = "G5",
            studentType = "TEST",
            assessmentTypes = listOf("KET"),
            selectedColumns = null,
            teachingPlanDataJson = teachingPlanDataJson
        )

        val document = XWPFDocument(ByteArrayInputStream(resultBytes))
        val firstHeading = document.paragraphs.first { it.text == "1. 强化语言基础：语法和词汇" }
        val secondHeading = document.paragraphs.first { it.text == "2. 重点强化写作能力，坚固口语能力" }

        assertNumberedHeadingStyle(firstHeading)
        assertNumberedHeadingStyle(secondHeading)
    }

    @Test
    fun `generateDocx should format and keep course plan total row with the schedule table`() {
        val teachingPlanDataJson = """
            {
              "coursePlans": [
                {
                  "phase": "基础课程",
                  "duration": "2h",
                  "goal": "强化语言基础",
                  "textbook": "NEF-PI, Unlock4",
                  "hours": "NEF-PI: 68hUnlock4: 40h"
                }
              ],
              "coursePlanNote": "不知道为什么我不是大明星"
            }
        """.trimIndent()

        val resultBytes = docxGeneratorService.generateDocx(
            targetLevel = "B1-",
            targetGrade = "G5",
            studentType = "TEST",
            assessmentTypes = listOf("KET"),
            selectedColumns = null,
            teachingPlanDataJson = teachingPlanDataJson
        )

        val document = XWPFDocument(ByteArrayInputStream(resultBytes))
        val coursePlanTable = document.tables.first { table ->
            table.getRow(0).tableCells.map { it.text }.containsAll(listOf("阶段", "时长", "目标", "教材", "预计课时"))
        }

        assertThat(coursePlanTable.getRow(1).getCell(4).text).contains("NEF-PI: 68h")
        assertThat(coursePlanTable.getRow(1).getCell(4).text).contains("Unlock4: 40h")
        assertThat(coursePlanTable.getRow(2).getCell(0).text).isEqualTo("预计总课时")
        assertThat(coursePlanTable.getRow(0).ctRow.trPr.sizeOfCantSplitArray()).isGreaterThan(0)
        assertThat(coursePlanTable.getRow(1).getCell(0).paragraphs.first().ctp.pPr.isSetKeepNext).isTrue()
    }

    private fun collectRuns(document: XWPFDocument): List<XWPFRun> {
        return document.paragraphs.flatMap { it.runs } +
            document.tables.flatMap { collectRuns(it) }
    }

    private fun documentText(document: XWPFDocument): String {
        return document.paragraphs.joinToString("\n") { it.text } + "\n" +
            document.tables.joinToString("\n") { table ->
                table.rows.joinToString("\n") { row ->
                    row.tableCells.joinToString("\t") { cell -> cell.text }
                }
            }
    }

    private fun collectRuns(table: XWPFTable): List<XWPFRun> {
        return table.rows.flatMap { row ->
            row.tableCells.flatMap { cell ->
                cell.paragraphs.flatMap { it.runs } +
                    cell.tables.flatMap { collectRuns(it) }
            }
        }
    }

    private fun assertNumberedHeadingStyle(paragraph: org.apache.poi.xwpf.usermodel.XWPFParagraph) {
        val runsWithText = paragraph.runs.filter { it.text().isNotBlank() }

        assertThat(runsWithText).isNotEmpty()
        runsWithText.forEach { run ->
            assertThat(run.isBold).isTrue()
            assertThat(run.color).isEqualTo(DocxStyleUtils.THEME_PRIMARY)
        }
    }

    private fun normalizeHexColor(value: Any): String {
        return when (value) {
            is ByteArray -> value.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
            else -> value.toString().uppercase()
        }
    }
}
