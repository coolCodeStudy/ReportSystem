package com.example.reportsystem.service.docx

import org.apache.poi.xwpf.usermodel.*
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth
import java.math.BigInteger
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.example.reportsystem.repository.SystemConfigRepository
import com.example.reportsystem.repository.TeachingPlanRepository
import com.example.reportsystem.repository.TextbookConfigRepository

object DocxTeachingPlanRenderer {

    fun render(document: XWPFDocument, teachingPlanDataJson: String, teachingPlanRepository: TeachingPlanRepository, textbookConfigRepository: TextbookConfigRepository, systemConfigRepository: SystemConfigRepository) {
        val mapper = jacksonObjectMapper()
        val data = try {
            mapper.readTree(teachingPlanDataJson)
        } catch (e: Exception) {
            return
        }
        
        if (data.isMissingNode || data.isEmpty) return
        
        var targetPara: XWPFParagraph? = null
        for (p in document.paragraphs) {
            if (p.text.contains("{course_schedule}")) {
                targetPara = p
                break
            }
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
        
        val allSelectedSeries = mutableSetOf<String>()
        val exactBookNames = mutableSetOf<String>()
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
                tblW.type = STTblWidth.PCT
                tblW.w = BigInteger.valueOf(5000)

                val headerRow = table.getRow(0)
                val headers = listOf("阶段", "时长", "目标", "教材", "预计课时")
                headers.forEachIndexed { col, text ->
                    DocxStyleUtils.setCellText(headerRow.getCell(col), text, bold = true, color = "FFFFFF", fontSize = 10)
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
                        rowNode.path("hours").asText()
                    )
                    cellsText.forEachIndexed { col, text ->
                        DocxStyleUtils.setCellText(row.getCell(col), text, bold = false, fontSize = 9)
                        DocxStyleUtils.setCellShading(row.getCell(col), if (index % 2 == 0) DocxStyleUtils.THEME_BG_LIGHT else "FFFFFF")
                        DocxStyleUtils.setZebraBorders(row.getCell(col), isLast = false)
                    }
                }
                
                val totalRow = table.getRow(validRowData.size + 1)
                
                val phaseTotals = mutableListOf<String>()
                validRowData.forEach { rowNode ->
                    val phaseName = rowNode.path("phase").asText()
                    val hoursStr = rowNode.path("hours").asText()
                    
                    val regex = Regex("\\d+(\\.\\d+)?")
                    val sum = regex.findAll(hoursStr).map { it.value.toDouble() }.sum()
                    if (sum > 0) {
                        val sumStr = if (sum % 1 == 0.0) sum.toInt().toString() else sum.toString()
                        phaseTotals.add("$phaseName: ${sumStr}h")
                    }
                }
                val totalText = phaseTotals.joinToString("\n")
                
                DocxStyleUtils.setCellText(totalRow.getCell(0), "预计总课时", bold = true, color = "FFFFFF", fontSize = 10)
                DocxStyleUtils.setCellShading(totalRow.getCell(0), DocxStyleUtils.THEME_PRIMARY)
                DocxStyleUtils.setZebraBorders(totalRow.getCell(0), isHeader = false, isLast = true)
                
                val cell1 = totalRow.getCell(1)
                cell1.ctTc.addNewTcPr().addNewHMerge().`val` = org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.RESTART
                DocxStyleUtils.setCellText(cell1, totalText, bold = false, fontSize = 9)
                DocxStyleUtils.setCellShading(cell1, "E9EDF6")
                DocxStyleUtils.setZebraBorders(cell1, isHeader = false, isLast = true)
                
                for (col in 2..4) {
                    val c = totalRow.getCell(col) ?: totalRow.addNewTableCell()
                    c.ctTc.addNewTcPr().addNewHMerge().`val` = org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.CONTINUE
                    DocxStyleUtils.setZebraBorders(c, isHeader = false, isLast = true)
                }
                
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
            addTextParagraphs(createPara, finalTeachingApproach)
        }

        var plans = mutableListOf<com.example.reportsystem.entity.TeachingPlan>()
        val selectedPlanIdsArray = data.path("selectedPlanIds")
        if (selectedPlanIdsArray.isArray && selectedPlanIdsArray.size() > 0) {
            val ids = selectedPlanIdsArray.map { it.asLong() }
            if (ids.isNotEmpty()) {
                plans.addAll(teachingPlanRepository.findAllById(ids))
            }
        } else if (exactBookNames.isNotEmpty()) {
            plans.addAll(teachingPlanRepository.findByBookNameIn(exactBookNames.toList()))
        }

        if (plans.isNotEmpty()) {
            addSectionTitle(createPara, "教学计划大纲")
            
            val table = createTableWrappen(plans.size + 1, 4)
                    val tblW = table.ctTbl.tblPr?.addNewTblW() ?: table.ctTbl.addNewTblPr().addNewTblW()
                    tblW.type = STTblWidth.PCT
                    tblW.w = BigInteger.valueOf(5000)

                    val headerRow = table.getRow(0)
                    DocxStyleUtils.setCellText(headerRow.getCell(0), "教材", bold = true, color = "FFFFFF", fontSize = 10)
                    DocxStyleUtils.setCellText(headerRow.getCell(1), "单元", bold = true, color = "FFFFFF", fontSize = 10)
                    DocxStyleUtils.setCellText(headerRow.getCell(2), "课程内容", bold = true, color = "FFFFFF", fontSize = 10)
                    DocxStyleUtils.setCellText(headerRow.getCell(3), "学习目标", bold = true, color = "FFFFFF", fontSize = 10)
                    
                    headerRow.tableCells.forEach {
                        DocxStyleUtils.setCellShading(it, DocxStyleUtils.THEME_PRIMARY)
                        DocxStyleUtils.setZebraBorders(it, isHeader = true)
                    }

                    plans.forEachIndexed { index, plan ->
                        val row = table.getRow(index + 1)
                        DocxStyleUtils.setCellText(row.getCell(0), plan.bookName ?: "", bold = false, fontSize = 9)
                        DocxStyleUtils.setCellText(row.getCell(1), plan.unitCode ?: "", bold = false, fontSize = 9)
                        DocxStyleUtils.setCellText(row.getCell(2), plan.courseContent ?: "", bold = false, fontSize = 9)
                        DocxStyleUtils.setCellText(row.getCell(3), plan.learningObjectives ?: "", bold = false, fontSize = 9)
                        
                        val isLastDataRow = index == plans.size - 1
                        row.tableCells.forEach {
                            DocxStyleUtils.setCellShading(it, if (index % 2 == 0) DocxStyleUtils.THEME_BG_LIGHT else "FFFFFF")
                            DocxStyleUtils.setZebraBorders(it, isLast = isLastDataRow)
                            DocxStyleUtils.setCellAlignment(it, ParagraphAlignment.LEFT)
                        }
                    }
                    createPara().spacingAfter = 200
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
            targetPara!!.runs.forEach { it.setText("", 0) }
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

    private fun addTextParagraphs(createPara: () -> XWPFParagraph, text: String) {
        val pattern = Regex("^((\\d+\\.[\\s\\S]*?[：:]|【[\\s\\S]*?】[：:]?))(.*)$")
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
                if (match != null) {
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
}
