package com.example.reportsystem.service

import com.example.reportsystem.entity.TeachingPlan
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
import java.math.BigInteger

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
    fun `generateDocx should add branded page header and footer to content pages`() {
        val resultBytes = docxGeneratorService.generateDocx("B2-", "G5", "TEST", null, listOf("Lingoland", "CEFR", "雅思"))
        val document = XWPFDocument(ByteArrayInputStream(resultBytes))

        val headerText = document.headerList.joinToString("\n") { it.text }
        val footerText = document.footerList.joinToString("\n") { it.text }

        assertThat(headerText).contains("LINGOLAND 国际学校课程学习方案")
        assertThat(headerText).contains("ENGLISH ASSESSMENT AND STUDY PLAN")
        assertThat(document.headerList.any { it.allPictures.isNotEmpty() }).isTrue()
        assertThat(footerText).contains("LINGOLAND 杭州市上城区钱江路 1366 号华润大厦 B 座 3204 室")
    }

    @Test
    fun `generateDocx should keep the fee image small enough to remain with its heading`() {
        val resultBytes = docxGeneratorService.generateDocx(
            "B2-",
            "G5",
            "TEST",
            null,
            listOf("Lingoland", "CEFR", "雅思")
        )
        val document = XWPFDocument(ByteArrayInputStream(resultBytes))
        val feeHeadingIndex = document.bodyElements.indexOfFirst { element ->
            element is org.apache.poi.xwpf.usermodel.XWPFParagraph && element.text == "费用"
        }

        assertThat(feeHeadingIndex).isGreaterThanOrEqualTo(0)
        val feeHeading = document.bodyElements[feeHeadingIndex] as org.apache.poi.xwpf.usermodel.XWPFParagraph
        val feeImageParagraph = document.bodyElements[feeHeadingIndex + 1] as org.apache.poi.xwpf.usermodel.XWPFParagraph
        val feeImageExtent = feeImageParagraph.runs
            .flatMap { it.ctr.drawingList }
            .flatMap { it.inlineList }
            .first()
            .extent

        assertThat(feeHeading.ctp.pPr.isSetKeepNext).isTrue()
        assertThat(feeImageExtent.cy).isLessThanOrEqualTo(7_200_000L)
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
    fun `generateDocx should skip excluded assessment subjects`() {
        val assessmentResultsJson = """
            {
              "reading": {
                "score": 18,
                "total": 30,
                "level": "A2",
                "paperAnalysis": {
                  "信息理解": { "status": "✅", "text": "理解主旨和关键信息。" }
                },
                "causeAnalysis": []
              },
              "listening": {
                "excluded": true,
                "score": 20,
                "total": 25,
                "level": "B1",
                "paperAnalysis": {
                  "单词辨音": { "status": "✅", "text": "连读弱读识别稳定。" }
                },
                "causeAnalysis": ["听力训练充分。"]
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

        assertThat(allText).contains("▎ 阅读")
        assertThat(allText).contains("理解主旨和关键信息。")
        assertThat(allText).doesNotContain("▎ 听力")
        assertThat(allText).doesNotContain("单词辨音")
        assertThat(allText).doesNotContain("连读弱读识别稳定。")
        assertThat(allText).doesNotContain("听力训练充分。")
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
        val totalRow = coursePlanTable.getRow(2)
        assertThat(totalRow.getCell(0).text).isEqualTo("预计总课时")
        assertThat(totalRow.getCell(1).text).contains("基础课程: 108h")
        assertThat(totalRow.tableCells).hasSize(2)
        assertThat(totalRow.getCell(1).ctTc.tcPr.gridSpan.`val`).isEqualTo(BigInteger.valueOf(4))
        assertThat(coursePlanTable.getRow(0).ctRow.trPr.sizeOfCantSplitArray()).isGreaterThan(0)
        assertThat(coursePlanTable.getRow(1).getCell(0).paragraphs.first().ctp.pPr.isSetKeepNext).isTrue()
    }

    @Test
    fun `generateDocx should ignore textbook digits when summing course plan total hours`() {
        val teachingPlanDataJson = """
            {
              "coursePlans": [
                {
                  "phase": "阶段 2",
                  "duration": "2h",
                  "goal": "强化阅读写作",
                  "textbook": "NEF-UI, Unlock4",
                  "hours": "NEF-UI: 32h\nUnlock4: 40h"
                }
              ]
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

        val totalRow = coursePlanTable.getRow(2)
        assertThat(totalRow.getCell(0).text).isEqualTo("预计总课时")
        assertThat(totalRow.getCell(1).text).contains("阶段 2: 72h")
    }

    @Test
    fun `generateDocx should split syllabus outline tables by course plan phase and keep headers consistent`() {
        val teachingPlanDataJson = """
            {
              "coursePlans": [
                {
                  "phase": "阶段1: 自然拼读与拼写基础",
                  "duration": "主课 1.5h",
                  "goal": "建立音形对应意识",
                  "textbook": "自然拼读Set 1, 自然拼读Set 2&3",
                  "hours": "自然拼读Set 1: 7h自然拼读Set 2&3: 21h"
                },
                {
                  "phase": "阶段2: English Language Arts",
                  "duration": "主课 1.5h",
                  "goal": "提升综合语言运用",
                  "textbook": "Power Up 2",
                  "hours": "Power Up 2: 47h"
                }
              ]
            }
        """.trimIndent()
        val syllabusPlans = listOf(
            TeachingPlan(unitCode = "Power Up 2 - Unit 0", bookName = "Power Up 2", courseContent = "人物名字", learningObjectives = "描述人物"),
            TeachingPlan(unitCode = "自然拼读set 1 - 第1课", bookName = "自然拼读Set 1", courseContent = "字母发音", learningObjectives = "看到字母能读音"),
            TeachingPlan(unitCode = "自然拼读Set 2&3 - 第1课", bookName = "自然拼读Set 2&3", courseContent = "Magic E", learningObjectives = "拼读长元音")
        )
        every {
            teachingPlanRepository.findByBookNameIn(match {
                it.containsAll(listOf("自然拼读Set 1", "自然拼读Set 2&3", "Power Up 2"))
            })
        } returns syllabusPlans

        val resultBytes = docxGeneratorService.generateDocx(
            targetLevel = "B1-",
            targetGrade = "G5",
            studentType = "TEST",
            assessmentTypes = listOf("KET"),
            selectedColumns = null,
            teachingPlanDataJson = teachingPlanDataJson
        )

        val document = XWPFDocument(ByteArrayInputStream(resultBytes))
        val syllabusTables = document.tables.filter { table ->
            table.getRow(0).tableCells.map { it.text } == listOf("教材", "单元", "课程内容", "学习目标")
        }

        assertThat(syllabusTables).hasSize(2)
        assertThat(document.paragraphs.map { it.text }).contains(
            "阶段1: 自然拼读与拼写基础（自然拼读Set 1 / 自然拼读Set 2&3）",
            "阶段2: English Language Arts（Power Up 2）"
        )
        assertThat(syllabusTables[0].rows.drop(1).flatMap { row -> row.tableCells.map { it.text } })
            .contains("自然拼读Set 1", "自然拼读Set 2&3")
            .doesNotContain("Power Up 2")
        assertThat(syllabusTables[1].rows.drop(1).flatMap { row -> row.tableCells.map { it.text } })
            .contains("Power Up 2")
            .doesNotContain("自然拼读Set 1", "自然拼读Set 2&3")
    }

    @Test
    fun `generateDocx should keep textbooks in schedule but skip excluded syllabus books`() {
        val teachingPlanDataJson = """
            {
              "outlineExcludedBooks": ["Hidden Book"],
              "coursePlans": [
                {
                  "phase": "阶段1: 混合教材",
                  "duration": "主课 1.5h",
                  "goal": "保留在课时表",
                  "textbook": "Hidden Book, Visible Book",
                  "hours": "Hidden Book: 12h\nVisible Book: 20h"
                }
              ]
            }
        """.trimIndent()
        val visiblePlans = listOf(
            TeachingPlan(
                unitCode = "Visible Book - Unit 1",
                bookName = "Visible Book",
                courseContent = "可打印课程内容",
                learningObjectives = "可打印学习目标"
            )
        )
        every {
            teachingPlanRepository.findByBookNameIn(match {
                it.contains("Visible Book") && !it.contains("Hidden Book")
            })
        } returns visiblePlans

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
        val syllabusTables = document.tables.filter { table ->
            table.getRow(0).tableCells.map { it.text } == listOf("教材", "单元", "课程内容", "学习目标")
        }

        assertThat(coursePlanTable.rows.flatMap { row -> row.tableCells.map { it.text } })
            .contains("阶段1: 混合教材", "Hidden Book, Visible Book")
        assertThat(syllabusTables).hasSize(1)
        assertThat(document.paragraphs.map { it.text }).contains("阶段1: 混合教材（Visible Book）")
        assertThat(syllabusTables[0].rows.drop(1).flatMap { row -> row.tableCells.map { it.text } })
            .contains("Visible Book", "可打印课程内容")
            .doesNotContain("Hidden Book", "隐藏课程内容")
    }

    @Test
    fun `generateDocx should preserve syllabus learning objective line breaks`() {
        val teachingPlanDataJson = """
            {
              "coursePlans": [
                {
                  "phase": "阶段1: 基础课程",
                  "duration": "主课 1.5h",
                  "goal": "基础语言能力",
                  "textbook": "NEF-E",
                  "hours": "NEF-E: 68h"
                }
              ]
            }
        """.trimIndent()
        every {
            teachingPlanRepository.findByBookNameIn(match { it.contains("NEF-E") })
        } returns listOf(
            TeachingPlan(
                unitCode = "NEF-E-1A",
                bookName = "NEF-E",
                courseContent = "语法：be动词\r\n词汇：数字1-20",
                learningObjectives = "• 用英文介绍自己\r\n• 简单描述他人\n• 数数1-20"
            )
        )

        val resultBytes = docxGeneratorService.generateDocx(
            targetLevel = "B1-",
            targetGrade = "G5",
            studentType = "TEST",
            assessmentTypes = listOf("KET"),
            selectedColumns = null,
            teachingPlanDataJson = teachingPlanDataJson
        )

        val document = XWPFDocument(ByteArrayInputStream(resultBytes))
        val syllabusTable = document.tables.first { table ->
            table.getRow(0).tableCells.map { it.text } == listOf("教材", "单元", "课程内容", "学习目标")
        }
        val objectiveRun = syllabusTable.getRow(1).getCell(3).paragraphs.first().runs.first()

        assertThat(objectiveRun.ctr.sizeOfBrArray()).isEqualTo(2)
        assertThat(objectiveRun.ctr.sizeOfCrArray()).isEqualTo(0)
        assertThat(syllabusTable.getRow(1).getCell(3).text)
            .contains("• 用英文介绍自己")
            .contains("• 简单描述他人")
            .contains("• 数数1-20")
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
