package com.example.reportsystem.service

import com.example.reportsystem.entity.StudentTypeDictionary
import com.example.reportsystem.entity.SystemConfig
import com.example.reportsystem.entity.TextbookConfig
import com.example.reportsystem.repository.StudentTypeDictionaryRepository
import com.example.reportsystem.repository.SystemConfigRepository
import com.example.reportsystem.repository.TeachingPlanRepository
import com.example.reportsystem.repository.TextbookConfigRepository
import io.mockk.every
import io.mockk.mockk
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class DocxExportGoldenSampleTest {

    private val systemConfigRepository: SystemConfigRepository = mockk()
    private val studentTypeDictionaryRepository: StudentTypeDictionaryRepository = mockk()
    private val teachingPlanRepository: TeachingPlanRepository = mockk()
    private val textbookConfigRepository: TextbookConfigRepository = mockk()
    private lateinit var docxGeneratorService: DocxGeneratorService

    private val mockCsv = """
        "Lingoland","CEFR","蓝思值","词汇量","雅思","体外"
        "K","Pre-A1","160L","400","",""
        "G1","A1","165L","800","",""
        "G2","A2-","425L","1100","",""
        "G3","A2+","600L","1500","3",""
        "G4","B1-","725L","2500","4",""
        "G5","B1+","825L","3500","5",""
        "G6","B2-","925L","4500","5.5",""
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        docxGeneratorService = DocxGeneratorService(
            studentTypeDictionaryRepository,
            systemConfigRepository,
            teachingPlanRepository,
            textbookConfigRepository
        )

        every { systemConfigRepository.findByConfigKey(any()) } returns null
        every { systemConfigRepository.findByConfigKey("GLOBAL_CAPABILITY_MATRIX_CSV") } returns SystemConfig(
            configKey = "GLOBAL_CAPABILITY_MATRIX_CSV",
            configValue = mockCsv
        )
        every { systemConfigRepository.findByConfigKey("GLOBAL_ASSESSMENT_DESCRIPTIONS") } returns SystemConfig(
            configKey = "GLOBAL_ASSESSMENT_DESCRIPTIONS",
            configValue = """
                [
                  {"id":"starters","name":"Starters","description":"Starters低龄测评说明。"},
                  {"id":"smart-test","name":"SmartTest-9803","description":"SmartTest综合测评说明。"}
                ]
            """.trimIndent()
        )
        every { studentTypeDictionaryRepository.findByTypeCode(any()) } returns StudentTypeDictionary(
            typeCode = "TEST",
            typeName = "Test Type",
            sortOrder = 1,
            capabilityMatrixCsv = mockCsv,
            associatedColumns = "Lingoland,CEFR,蓝思值,词汇量,雅思,体外"
        )
        every { teachingPlanRepository.findByBookNameIn(any()) } returns emptyList()
        every { teachingPlanRepository.findAllById(any<Iterable<Long>>()) } returns emptyList()
        every { textbookConfigRepository.findByBookName(any()) } returns null
        every { textbookConfigRepository.findByBookName("NEF") } returns TextbookConfig(
            bookName = "NEF",
            introduction = "NEF教材简介用于golden sample。"
        )
        every { textbookConfigRepository.findByBookName("Unlock4") } returns TextbookConfig(
            bookName = "Unlock4",
            introduction = "Unlock4教材简介用于golden sample。"
        )
        every { textbookConfigRepository.findByBookName("Starters") } returns TextbookConfig(
            bookName = "Starters",
            introduction = "Starters教材简介用于golden sample。"
        )
    }

    @Test
    fun `smart test golden sample should keep core export structure stable`() {
        val document = generateDocument(
            targetLevel = "B1-",
            targetGrade = "G5",
            assessmentTypes = listOf("SmartTest-9803"),
            assessmentResultsJson = smartTestAssessmentResultsJson(),
            teachingPlanDataJson = multiStageTeachingPlanDataJson()
        )
        val allText = documentText(document)

        assertNoPlaceholders(allText)
        assertThat(document.tables.size).isGreaterThanOrEqualTo(8)
        assertThat(allText).contains("SmartTest综合测评说明")
        assertThat(allText).contains("▎ 写作")
        assertThat(allText).contains("▎ 阅读")
        assertThat(allText).contains("▎ 听力")
        assertThat(allText).contains("▎ 口语")
        assertThat(allText).contains("▎ 语言应用")
        assertThat(allText).contains("▎ 学习素养")
        assertThat(allText).contains("课时规划")
        assertThat(allText).contains("*备注：每阶段结束后根据测评反馈微调课时。")
        assertThat(allText).contains("NEF教材简介用于golden sample")
        assertThat(allText).contains("Unlock4教材简介用于golden sample")

        val coursePlanTable = coursePlanTable(document)
        assertThat(coursePlanTable.rows).hasSize(4)
        assertThat(coursePlanTable.getRow(1).getCell(4).text).contains("NEF-PI: 68h")
        assertThat(coursePlanTable.getRow(1).getCell(4).text).contains("Unlock4: 40h")
        assertThat(coursePlanTable.getRow(2).getCell(4).text).contains("Unlock4: 24h")
        assertThat(coursePlanTable.getRow(3).getCell(0).text).isEqualTo("预计总课时")
    }

    @Test
    fun `starters golden sample should render low age report without leaking placeholders`() {
        val document = generateDocument(
            targetLevel = "A1",
            targetGrade = "G1",
            assessmentTypes = listOf("Starters"),
            assessmentResultsJson = startersAssessmentResultsJson(),
            teachingPlanDataJson = startersTeachingPlanDataJson()
        )
        val allText = documentText(document)

        assertNoPlaceholders(allText)
        assertThat(allText).contains("Starters低龄测评说明")
        assertThat(allText).contains("▎ 听力")
        assertThat(allText).contains("正确率 12/20")
        assertThat(allText).contains("▎ 口语")
        assertThat(allText).contains("得分 14/20")
        assertThat(allText).contains("Starters教材简介用于golden sample")
    }

    @Test
    fun `empty assessment analysis golden sample should clear placeholder and show fallback text`() {
        val document = generateDocument(
            targetLevel = "B1-",
            targetGrade = "G5",
            assessmentTypes = listOf("SmartTest-9803"),
            assessmentResultsJson = "{}",
            teachingPlanDataJson = null
        )
        val allText = documentText(document)

        assertNoPlaceholders(allText)
        assertThat(allText).contains("暂无测评分析数据。")
    }

    @Test
    fun `long text golden sample should not drop assessment or teaching plan content`() {
        val longAssessmentText = "超长测评文本-" + (1..80).joinToString("") { "阅读策略需要通过精读和限时泛读交替训练。" } + "-TAIL-A"
        val longTeachingText = "超长教学思路-" + (1..80).joinToString("") { "课堂输入后必须安排输出任务并及时反馈。" } + "-TAIL-B"
        val document = generateDocument(
            targetLevel = "B1-",
            targetGrade = "G5",
            assessmentTypes = listOf("SmartTest-9803"),
            assessmentResultsJson = """
                {
                  "reading": {
                    "score": 18,
                    "total": 30,
                    "level": "A2",
                    "paperAnalysis": {
                      "阅读策略": { "status": "⚠️", "text": "$longAssessmentText" }
                    },
                    "causeAnalysis": ["长文本成因分析-TAIL-C"]
                  }
                }
            """.trimIndent(),
            teachingPlanDataJson = """
                {
                  "teachingApproach": "1. 长文本教学思路\n$longTeachingText"
                }
            """.trimIndent()
        )
        val allText = documentText(document)

        assertNoPlaceholders(allText)
        assertThat(allText).contains("超长测评文本-")
        assertThat(allText).contains("-TAIL-A")
        assertThat(allText).contains("超长教学思路-")
        assertThat(allText).contains("-TAIL-B")
        assertThat(allText).contains("长文本成因分析-TAIL-C")
    }

    private fun generateDocument(
        targetLevel: String,
        targetGrade: String,
        assessmentTypes: List<String>,
        assessmentResultsJson: String?,
        teachingPlanDataJson: String?
    ): XWPFDocument {
        val bytes = docxGeneratorService.generateDocx(
            targetLevel = targetLevel,
            targetGrade = targetGrade,
            studentType = "TEST",
            assessmentTypes = assessmentTypes,
            selectedColumns = null,
            assessmentResultsJson = assessmentResultsJson,
            teachingPlanDataJson = teachingPlanDataJson
        )
        return XWPFDocument(ByteArrayInputStream(bytes))
    }

    private fun assertNoPlaceholders(allText: String) {
        assertThat(allText).doesNotContain("{assessment_introduction}")
        assertThat(allText).doesNotContain("{assessment_analysis}")
        assertThat(allText).doesNotContain("{course_schedule}")
    }

    private fun coursePlanTable(document: XWPFDocument): XWPFTable {
        return document.tables.first { table ->
            table.getRow(0).tableCells.map { it.text }.containsAll(listOf("阶段", "时长", "目标", "教材", "预计课时"))
        }
    }

    private fun documentText(document: XWPFDocument): String {
        return document.paragraphs.joinToString("\n") { it.text } + "\n" +
            document.tables.joinToString("\n") { table ->
                table.rows.joinToString("\n") { row ->
                    row.tableCells.joinToString("\t") { cell -> cell.text }
                }
            }
    }

    private fun smartTestAssessmentResultsJson(): String {
        return """
            {
              "subj_writing": {
                "score": 22,
                "total": 25,
                "level": "B1",
                "paperAnalysis": {
                  "拼写": { "status": "✅", "text": "拼写基本准确。" },
                  "写作惯例（Cohesion, Unity, Completeness）": { "status": "⚠️", "text": "段落衔接还可以继续强化。" }
                },
                "causeAnalysis": ["写作输出频率不足。"]
              },
              "subj_reading": {
                "score": 23,
                "total": 25,
                "level": "B1",
                "paperAnalysis": {
                  "阅读速度": { "status": "✅", "text": "阅读速度稳定。" },
                  "阅读策略": { "status": "✅", "text": "能抓住关键信息。" }
                },
                "causeAnalysis": []
              },
              "subj_listening": {
                "score": 18,
                "total": 25,
                "level": "A2",
                "paperAnalysis": {
                  "单词辨音": { "status": "⚠️", "text": "连读弱读识别需要训练。" }
                },
                "causeAnalysis": []
              },
              "subj_speaking": {
                "score": 19,
                "total": 25,
                "level": "A2",
                "paperAnalysis": {
                  "口音": { "status": "✅", "text": "发音可理解。" },
                  "互动和回应": { "status": "⚠️", "text": "回应速度需要提升。" }
                },
                "causeAnalysis": []
              },
              "subj_language": {
                "score": 20,
                "total": 25,
                "level": "B1",
                "paperAnalysis": {
                  "词形变化": { "status": "⚠️", "text": "词形变化还不稳定。" }
                },
                "causeAnalysis": []
              },
              "subj_literacy": {
                "score": 21,
                "total": 25,
                "level": "B1",
                "paperAnalysis": {
                  "学习策略": { "status": "✅", "text": "具备基础复盘意识。" }
                },
                "causeAnalysis": []
              }
            }
        """.trimIndent()
    }

    private fun startersAssessmentResultsJson(): String {
        return """
            {
              "listening": {
                "score": 12,
                "total": 20,
                "level": "Pre-A1",
                "paperAnalysis": {
                  "单词辨音": { "status": "✅", "text": "能识别熟悉词汇。" }
                },
                "causeAnalysis": []
              },
              "speaking": {
                "score": 14,
                "total": 20,
                "level": "A1",
                "paperAnalysis": {
                  "互动和回应": { "status": "✅", "text": "能完成简单问答。" }
                },
                "causeAnalysis": []
              }
            }
        """.trimIndent()
    }

    private fun multiStageTeachingPlanDataJson(): String {
        return """
            {
              "coursePlans": [
                {
                  "phase": "基础课程",
                  "duration": "2个月",
                  "goal": "强化语言基础",
                  "textbook": "NEF-PI, Unlock4",
                  "hours": "NEF-PI: 68hUnlock4: 40h"
                },
                {
                  "phase": "提升课程",
                  "duration": "1个月",
                  "goal": "强化综合输出",
                  "textbook": "Unlock4",
                  "hours": "Unlock4: 24h"
                }
              ],
              "coursePlanNote": "每阶段结束后根据测评反馈微调课时。",
              "teachingApproach": "1. 强化语言基础：语法和词汇\n2. 重点强化写作能力，稳固口语能力",
              "teachingChecklist": "助教课打卡清单\n课前预习\n课后复盘",
              "courseFrequency": "每周2次正课，每周1次助教课。",
              "planRisk": "如遇请假需顺延课程节奏。"
            }
        """.trimIndent()
    }

    private fun startersTeachingPlanDataJson(): String {
        return """
            {
              "coursePlans": [
                {
                  "phase": "启蒙课程",
                  "duration": "1个月",
                  "goal": "建立听说兴趣",
                  "textbook": "Starters",
                  "hours": "Starters: 16h"
                }
              ],
              "coursePlanNote": "低龄学生以兴趣和稳定输入为优先。",
              "teachingApproach": "1. 建立课堂安全感\n通过游戏化任务完成输入和输出。"
            }
        """.trimIndent()
    }
}
