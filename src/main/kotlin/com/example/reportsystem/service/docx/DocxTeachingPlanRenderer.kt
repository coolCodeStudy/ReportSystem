package com.example.reportsystem.service.docx

import org.apache.poi.xwpf.usermodel.*
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*
import java.math.BigInteger
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.example.reportsystem.entity.TeachingPlan
import com.example.reportsystem.repository.SystemConfigRepository
import com.example.reportsystem.repository.TeachingPlanRepository
import com.example.reportsystem.repository.TextbookConfigRepository

object DocxTeachingPlanRenderer {
    private val COURSE_PLAN_COL_WIDTHS = listOf(1500L, 800L, 1900L, 1900L, 2200L)
    private val COURSE_PLAN_TABLE_WIDTH = COURSE_PLAN_COL_WIDTHS.sum()
    private val SYLLABUS_HEADERS = listOf("教材", "单元", "课程内容", "学习目标")

    fun render(document: XWPFDocument, teachingPlanDataJson: String, teachingPlanRepository: TeachingPlanRepository, textbookConfigRepository: TextbookConfigRepository, systemConfigRepository: SystemConfigRepository) {
        var targetPara: XWPFParagraph? = null
        for (p in document.paragraphs) {
            if (p.text.contains("{course_schedule}")) {
                targetPara = p
                break
            }
        }

        fun clearPlaceholder() {
            targetPara?.runs?.forEach { it.setText("", 0) }
        }

        val mapper = jacksonObjectMapper()
        val data = try {
            mapper.readTree(teachingPlanDataJson)
        } catch (e: Exception) {
            clearPlaceholder()
            return
        }
        
        if (data.isMissingNode || data.isEmpty) {
            clearPlaceholder()
            return
        }

        val createPara: () -> XWPFParagraph = {
            if (targetPara != null) {
                val c = targetPara!!.ctp.newCursor()
                val p = document.insertNewParagraph(c)
                c.dispose()
                p
            } else {
                document.createParagraph()
            }
        }

        val createTableWrappen: (Int, Int) -> XWPFTable = { rows, cols ->
            if (targetPara != null) {
                val c = targetPara!!.ctp.newCursor()
                val t = document.insertNewTbl(c)
                c.dispose()
                if (t.rows.isNotEmpty()) t.removeRow(0)
                for (r in 0 until rows) {
                    val row = t.createRow()
                    while (row.tableCells.size < cols) {
                        row.addNewTableCell()
                    }
                }
                t
            } else {
                document.createTable(rows, cols)
            }
        }

        val teacherIntro = data.path("teacherIntro").asText()
        if (teacherIntro.isNotBlank()) {
            addSectionTitle(createPara, "师资简介")
            addTextParagraphs(createPara, teacherIntro)
        }



        val coursePlans = data.path("coursePlans")
        val excludedOutlineBookNames = data.path("outlineExcludedBooks")
            .takeIf { it.isArray }
            ?.map { it.asText().trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
        
        val allSelectedSeries = mutableSetOf<String>()
        val exactBookNames = mutableSetOf<String>()
        val printableOutlineBookNames = mutableSetOf<String>()
        if (coursePlans.isArray && coursePlans.size() > 0) {
            coursePlans.forEach { row ->
                val tbStr = row.path("textbook").asText()
                if (tbStr.isNotBlank()) {
                    tbStr.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .forEach { bookName ->
                            val series = bookName.substringBefore("-").trim()
                            allSelectedSeries.add(series) 
                            exactBookNames.add(bookName)
                            if (isOutlineEnabled(row) && bookName !in excludedOutlineBookNames) {
                                printableOutlineBookNames.add(bookName)
                            }
                        }
                }
            }
        }
        
        if (coursePlans.isArray && coursePlans.size() > 0) {
            val validRowData = coursePlans.filter { row ->
                row.path("phase").asText().isNotBlank() || row.path("goal").asText().isNotBlank()
            }
            if (validRowData.isNotEmpty()) {
                addSectionTitle(createPara, "课时规划")
                val table = createTableWrappen(validRowData.size + 2, 5)
                val tblW = table.ctTbl.tblPr?.addNewTblW() ?: table.ctTbl.addNewTblPr().addNewTblW()
                tblW.type = STTblWidth.DXA
                tblW.w = BigInteger.valueOf(COURSE_PLAN_TABLE_WIDTH)
                val tblPr = table.ctTbl.tblPr ?: table.ctTbl.addNewTblPr()
                val tblLayout = tblPr.tblLayout ?: tblPr.addNewTblLayout()
                tblLayout.type = STTblLayoutType.FIXED

                val headerRow = table.getRow(0)
                val headers = listOf("阶段", "时长", "目标", "教材", "预计课时")
                headers.forEachIndexed { col, text ->
                    DocxStyleUtils.setCellText(headerRow.getCell(col), text, bold = true, color = "FFFFFF", fontSize = 10)
                    DocxStyleUtils.setCellWidth(headerRow.getCell(col), COURSE_PLAN_COL_WIDTHS[col])
                    DocxStyleUtils.setCellShading(headerRow.getCell(col), DocxStyleUtils.THEME_PRIMARY)
                    DocxStyleUtils.setZebraBorders(headerRow.getCell(col), isHeader = true)
                }

                validRowData.forEachIndexed { index, rowNode ->
                    val row = table.getRow(index + 1)
                    val cellsText = listOf(
                        rowNode.path("phase").asText(),
                        rowNode.path("duration").asText(),
                        rowNode.path("goal").asText(),
                        rowNode.path("textbook").asText(),
                        formatHoursText(rowNode.path("hours").asText())
                    )
                    cellsText.forEachIndexed { col, text ->
                        DocxStyleUtils.setCellText(row.getCell(col), text, bold = false, fontSize = 9)
                        DocxStyleUtils.setCellWidth(row.getCell(col), COURSE_PLAN_COL_WIDTHS[col])
                        DocxStyleUtils.setCellShading(row.getCell(col), if (index % 2 == 0) DocxStyleUtils.THEME_BG_LIGHT else "FFFFFF")
                        DocxStyleUtils.setZebraBorders(row.getCell(col), isLast = false)
                        DocxStyleUtils.setCellAlignment(row.getCell(col), if (col == 1) ParagraphAlignment.CENTER else ParagraphAlignment.LEFT)
                    }
                }
                
                val totalRow = table.getRow(validRowData.size + 1)
                
                val phaseTotals = mutableListOf<String>()
                validRowData.forEach { rowNode ->
                    val phaseName = rowNode.path("phase").asText()
                    val hoursStr = rowNode.path("hours").asText()

                    val sum = sumExplicitCourseHours(hoursStr)
                    if (sum > 0) {
                        val sumStr = if (sum % 1 == 0.0) sum.toInt().toString() else sum.toString()
                        phaseTotals.add("$phaseName: ${sumStr}h")
                    }
                }
                val totalText = phaseTotals.joinToString("\n")
                
                DocxStyleUtils.setCellText(totalRow.getCell(0), "预计总课时", bold = true, color = "FFFFFF", fontSize = 10)
                DocxStyleUtils.setCellWidth(totalRow.getCell(0), COURSE_PLAN_COL_WIDTHS[0])
                DocxStyleUtils.setCellShading(totalRow.getCell(0), DocxStyleUtils.THEME_PRIMARY)
                DocxStyleUtils.setZebraBorders(totalRow.getCell(0), isHeader = false, isLast = true)
                
                val cell1 = totalRow.getCell(1)
                val cell1Pr = cell1.ctTc.tcPr ?: cell1.ctTc.addNewTcPr()
                val cell1GridSpan = if (cell1Pr.isSetGridSpan) cell1Pr.gridSpan else cell1Pr.addNewGridSpan()
                cell1GridSpan.`val` = BigInteger.valueOf(4)
                if (cell1Pr.isSetHMerge) cell1Pr.unsetHMerge()
                DocxStyleUtils.setCellText(cell1, totalText, bold = false, fontSize = 9)
                DocxStyleUtils.setCellWidth(cell1, COURSE_PLAN_COL_WIDTHS.drop(1).sum())
                DocxStyleUtils.setCellShading(cell1, "E9EDF6")
                DocxStyleUtils.setZebraBorders(cell1, isHeader = false, isLast = true)
                
                for (col in 4 downTo 2) {
                    if (totalRow.tableCells.size > col) {
                        totalRow.removeCell(col)
                    }
                }
                
                DocxStyleUtils.keepTableRowsTogether(table)
                createPara().spacingAfter = 100
            }
        }
        
        val coursePlanNote = data.path("coursePlanNote").asText()
        if (coursePlanNote.isNotBlank()) {
            val pNote = createPara()
            pNote.spacingAfter = 200
            val rNote = pNote.createRun()
            DocxStyleUtils.applyRunFont(rNote)
            rNote.fontSize = 9
            rNote.color = "7F7F7F"
            rNote.setText("*备注：$coursePlanNote")
        }

        if (allSelectedSeries.isNotEmpty()) {
            val intros = mutableListOf<String>()
            for (series in allSelectedSeries) {
                // Try fetching by series name (e.g., NEF)
                var config = textbookConfigRepository.findByBookName(series)
                if (config != null && config.introduction.isNotBlank()) {
                    intros.add("【$series 系列】：\n${config.introduction}")
                }
            }
            if (intros.isNotEmpty()) {
                addSectionTitle(createPara, "教材简介")
                addTextParagraphs(createPara, intros.joinToString("\n\n"))
            }
        }

        // --- 师资简介：从全局配置读取 ---
        val teacherIntrosJson = systemConfigRepository.findByConfigKey("GLOBAL_TEACHER_INTRODUCTIONS")?.configValue
        if (!teacherIntrosJson.isNullOrBlank()) {
            try {
                val items = mapper.readTree(teacherIntrosJson)
                if (items.isArray && items.size() > 0) {
                    addSectionTitle(createPara, "师资简介")
                    items.forEach { item ->
                        val level = item.path("level").asText()
                        val desc = item.path("desc").asText()
                        if (level.isNotBlank()) {
                            // 级别名称：加粗小标题
                            val lp = createPara()
                            lp.spacingBefore = 150
                            lp.spacingAfter = 60
                            lp.indentationLeft = 300
                            val lr = lp.createRun()
                            lr.setText(level)
                            DocxStyleUtils.applyRunFont(lr)
                            lr.fontSize = 10
                            lr.isBold = true
                        }
                        if (desc.isNotBlank()) {
                            addTextParagraphs(createPara, desc)
                        }
                    }
                }
            } catch (e: Exception) {
                System.err.println("Failed to render teacher intros: ${e.message}")
            }
        }

        fun addStarredSectionTitle(title: String) {
            val tp = createPara()
            tp.style = "3"                             // Word Heading 2 样式（并列展示，防止层级溢出嵌套）
            tp.spacingBefore = DocxStyleUtils.SPACING_SECTION
            tp.spacingAfter = DocxStyleUtils.SPACING_BODY
            val starRun = tp.createRun()
            starRun.setText("✦ ")
            starRun.fontFamily = DocxStyleUtils.FONT_MAIN
            starRun.fontSize = 12
            starRun.isBold = true
            starRun.color = DocxStyleUtils.THEME_ACCENT
            val titleRun = tp.createRun()
            titleRun.setText(title)
            titleRun.fontFamily = DocxStyleUtils.FONT_MAIN
            titleRun.fontSize = 12
            titleRun.isBold = true
            titleRun.color = DocxStyleUtils.THEME_PRIMARY
        }

        // --- 教学思路：优先使用该学生的教学思路数据，若为空则从全局模板配置兜底读取 ---
        var finalTeachingApproach = data.path("teachingApproach").asText()
        if (finalTeachingApproach.isBlank()) {
            finalTeachingApproach = systemConfigRepository.findByConfigKey("GLOBAL_TEACHING_APPROACH_TEMPLATE")?.configValue ?: ""
        }
        
        if (finalTeachingApproach.isNotBlank()) {
            addStarredSectionTitle("教学思路")
            addTextParagraphs(createPara, finalTeachingApproach, emphasizeNumberedLines = true)
        }

        var plans = mutableListOf<TeachingPlan>()
        val selectedPlanIdsArray = data.path("selectedPlanIds")
        if (selectedPlanIdsArray.isArray && selectedPlanIdsArray.size() > 0) {
            val ids = selectedPlanIdsArray.map { it.asLong() }
            if (ids.isNotEmpty()) {
                plans.addAll(teachingPlanRepository.findAllById(ids))
            }
        } else if (exactBookNames.isNotEmpty()) {
            val queryBookNames = if (coursePlans.isArray && coursePlans.size() > 0) {
                printableOutlineBookNames
            } else {
                exactBookNames
            }
            if (queryBookNames.isNotEmpty()) {
                plans.addAll(teachingPlanRepository.findByBookNameIn(queryBookNames.toList()))
            }
        }

        val printablePlans = if (coursePlans.isArray && coursePlans.size() > 0) {
            plans.filter { it.bookName in printableOutlineBookNames }
        } else {
            plans
        }

        if (printablePlans.isNotEmpty()) {
            addSectionTitle(createPara, "教学计划大纲")
            val groups = buildSyllabusGroups(printablePlans, coursePlans, printableOutlineBookNames)
            groups.forEachIndexed { groupIndex, group ->
                addSyllabusGroupTitle(createPara, group.title)
                renderSyllabusTable(createTableWrappen, group.plans)
                createPara().spacingAfter = if (groupIndex == groups.lastIndex) 200 else 120
            }
        }

        // --- 助教课打卡清单：优先使用该学生的数据，若为空则从全局模板配置兜底读取 ---
        var teachingChecklist = data.path("teachingChecklist").asText()
        if (teachingChecklist.isBlank()) {
            teachingChecklist = systemConfigRepository.findByConfigKey("GLOBAL_TEACHING_CHECKLIST_TEMPLATE")?.configValue ?: ""
        }
        if (teachingChecklist.isNotBlank()) {
            addStarredSectionTitle("助教课打卡清单")
            addTextParagraphs(createPara, teachingChecklist.removePrefix("助教课打卡清单").trim())
        }

        // --- 课程频次：优先使用该学生的数据，若为空则从全局模板配置兜底读取 ---
        var courseFrequency = data.path("courseFrequency").asText()
        if (courseFrequency.isBlank()) {
            courseFrequency = systemConfigRepository.findByConfigKey("GLOBAL_COURSE_FREQUENCY_TEMPLATE")?.configValue ?: ""
        }
        if (courseFrequency.isNotBlank()) {
            addStarredSectionTitle("课程频次")
            addTextParagraphs(createPara, courseFrequency.removePrefix("课程频次").trim())
        }

        var planRisk = data.path("planRisk").asText()
        if (planRisk.isBlank()) {
            planRisk = systemConfigRepository.findByConfigKey("GLOBAL_PLAN_RISK_TEMPLATE")?.configValue ?: ""
        }
        
        if (planRisk.isNotBlank()) {
            addStarredSectionTitle("方案风险提示")
            addTextParagraphs(createPara, planRisk)
        }
        
        if (targetPara != null) {
            clearPlaceholder()
        }
    }

    private fun addSectionTitle(createPara: () -> XWPFParagraph, title: String) {
        val p = createPara()
        p.style = "3"                              // Word Heading 2 样式（由 Heading 1 降级以确保正确嵌套）
        p.spacingBefore = DocxStyleUtils.SPACING_MAJOR
        p.spacingAfter = DocxStyleUtils.SPACING_BODY
        val r = p.createRun()
        r.setText(title)
        r.fontFamily = DocxStyleUtils.FONT_MAIN
        r.fontSize = 12
        r.isBold = true
        r.color = DocxStyleUtils.THEME_PRIMARY
    }

    private fun addSyllabusGroupTitle(createPara: () -> XWPFParagraph, title: String) {
        val p = createPara()
        p.spacingBefore = DocxStyleUtils.SPACING_SECTION
        p.spacingAfter = DocxStyleUtils.SPACING_BODY
        p.indentationLeft = 300
        val r = p.createRun()
        r.setText(title)
        r.fontFamily = DocxStyleUtils.FONT_MAIN
        r.fontSize = 11
        r.isBold = true
        r.color = DocxStyleUtils.THEME_PRIMARY
    }

    private fun renderSyllabusTable(createTable: (Int, Int) -> XWPFTable, plans: List<TeachingPlan>) {
        val table = createTable(plans.size + 1, SYLLABUS_HEADERS.size)
        val tblPr = table.ctTbl.tblPr ?: table.ctTbl.addNewTblPr()
        val tblW = tblPr.tblW ?: tblPr.addNewTblW()
        tblW.type = STTblWidth.PCT
        tblW.w = BigInteger.valueOf(5000)
        val tblLayout = tblPr.tblLayout ?: tblPr.addNewTblLayout()
        tblLayout.type = STTblLayoutType.FIXED

        val headerRow = table.getRow(0)
        SYLLABUS_HEADERS.forEachIndexed { col, header ->
            val cell = headerRow.getCell(col)
            DocxStyleUtils.setCellText(cell, header, bold = true, color = "FFFFFF", fontSize = 10)
            DocxStyleUtils.setCellShading(cell, DocxStyleUtils.THEME_PRIMARY)
            DocxStyleUtils.setZebraBorders(cell, isHeader = true)
        }

        plans.forEachIndexed { index, plan ->
            val row = table.getRow(index + 1)
            val cellsText = listOf(
                plan.bookName,
                plan.unitCode,
                plan.courseContent.orEmpty(),
                plan.learningObjectives.orEmpty()
            )
            cellsText.forEachIndexed { col, text ->
                val cell = row.getCell(col)
                DocxStyleUtils.setCellText(cell, text, bold = false, fontSize = 9)
                DocxStyleUtils.setCellShading(cell, if (index % 2 == 0) DocxStyleUtils.THEME_BG_LIGHT else "FFFFFF")
                DocxStyleUtils.setZebraBorders(cell, isLast = index == plans.lastIndex)
                DocxStyleUtils.setCellAlignment(cell, ParagraphAlignment.LEFT)
            }
        }
    }

    private fun buildSyllabusGroups(
        plans: List<TeachingPlan>,
        coursePlans: com.fasterxml.jackson.databind.JsonNode,
        printableBookNames: Set<String>
    ): List<SyllabusGroup> {
        val indexedPlans = plans.withIndex().toList()
        val consumedBookNames = mutableSetOf<String>()
        val groups = mutableListOf<SyllabusGroup>()

        if (coursePlans.isArray && coursePlans.size() > 0) {
            coursePlans.forEach { row ->
                if (!isOutlineEnabled(row)) return@forEach

                val bookNames = splitBookNames(row.path("textbook").asText())
                    .filter { it in printableBookNames }
                    .filterNot { it in consumedBookNames }
                if (bookNames.isEmpty()) return@forEach

                val groupPlans = indexedPlans
                    .filter { (_, plan) -> plan.bookName in bookNames }
                    .sortedWith(compareBy<IndexedValue<TeachingPlan>>(
                        { bookNames.indexOf(it.value.bookName).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE },
                        { it.index }
                    ))
                    .map { it.value }

                if (groupPlans.isNotEmpty()) {
                    groups.add(SyllabusGroup(buildSyllabusGroupTitle(row.path("phase").asText(), bookNames), groupPlans))
                    consumedBookNames.addAll(bookNames)
                }
            }
        }

        val remainingPlans = indexedPlans
            .filter { (_, plan) -> plan.bookName !in consumedBookNames }
            .map { it.value }
        if (remainingPlans.isNotEmpty()) {
            remainingPlans
                .groupBy { it.bookName }
                .forEach { (bookName, bookPlans) ->
                    groups.add(SyllabusGroup("${bookName} 教学计划", bookPlans))
                }
        }

        return groups
    }

    private fun isOutlineEnabled(row: com.fasterxml.jackson.databind.JsonNode): Boolean {
        return row.path("outlineEnabled").asBoolean(true)
    }

    private fun splitBookNames(rawText: String): List<String> {
        return rawText.split(",", "，", "、", "/", "／", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun buildSyllabusGroupTitle(phase: String, bookNames: List<String>): String {
        val textbookLabel = bookNames.joinToString(" / ")
        return if (phase.isBlank()) {
            "$textbookLabel 教学计划"
        } else {
            "$phase（$textbookLabel）"
        }
    }

    private fun addTextParagraphs(
        createPara: () -> XWPFParagraph,
        text: String,
        emphasizeNumberedLines: Boolean = false
    ) {
        val pattern = Regex("^((\\d+\\.[\\s\\S]*?[：:]|【[\\s\\S]*?】[：:]?))(.*)$")
        val numberedLinePattern = Regex("^\\d+\\.\\s+.+$")
        text.split("\n").forEach { line ->
            if (line.isNotBlank()) {
                val p = createPara()
                p.spacingAfter = DocxStyleUtils.SPACING_BODY
                p.indentationLeft = 300
                
                // 固定抗网格化行距，让这些大段文字具有阅读呼吸感
                if (p.ctp.pPr == null) p.ctp.addNewPPr()
                if (p.ctp.pPr.spacing == null) p.ctp.pPr.addNewSpacing()
                p.ctp.pPr.spacing.line = BigInteger.valueOf(300) // 15pt
                p.ctp.pPr.spacing.lineRule = org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule.EXACT
                
                val trimmedLine = line.trim()
                val match = pattern.find(trimmedLine)
                if (emphasizeNumberedLines && numberedLinePattern.matches(trimmedLine)) {
                    val r = p.createRun()
                    r.setText(trimmedLine)
                    r.fontFamily = DocxStyleUtils.FONT_MAIN
                    r.fontSize = 10
                    r.isBold = true
                    r.color = DocxStyleUtils.THEME_PRIMARY
                } else if (match != null) {
                    // 如果匹配到诸如 "1. 词汇：" 或 "【听力】："，就加粗前半段
                    val prefixRun = p.createRun()
                    prefixRun.setText(match.groupValues[1])
                    prefixRun.fontFamily = DocxStyleUtils.FONT_MAIN
                    prefixRun.fontSize = 10
                    prefixRun.isBold = true // 智能重点高亮
                    prefixRun.color = DocxStyleUtils.THEME_PRIMARY // 让小标题带上主视觉色
                    
                    val contentRun = p.createRun()
                    contentRun.setText(match.groupValues[3])
                    contentRun.fontFamily = DocxStyleUtils.FONT_MAIN
                    contentRun.fontSize = 10
                } else {
                    // 没有锚点的普通纯文字
                    val r = p.createRun()
                    r.setText(trimmedLine)
                    r.fontFamily = DocxStyleUtils.FONT_MAIN
                    r.fontSize = 10
                }
            }
        }
    }

    private fun formatHoursText(rawText: String): String {
        return rawText
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("(?<=h)(?=\\S+?:)"), "\n")
    }

    private fun sumExplicitCourseHours(hoursText: String): Double {
        val hourValueRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:h|H|小时|课时)""")
        return hourValueRegex.findAll(hoursText)
            .mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull() }
            .sum()
    }

    private data class SyllabusGroup(
        val title: String,
        val plans: List<TeachingPlan>
    )
}
