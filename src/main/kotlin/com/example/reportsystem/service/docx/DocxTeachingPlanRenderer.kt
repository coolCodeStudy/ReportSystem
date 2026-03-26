package com.example.reportsystem.service.docx

import org.apache.poi.xwpf.usermodel.*
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth
import java.math.BigInteger
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.example.reportsystem.repository.TeachingPlanRepository

object DocxTeachingPlanRenderer {

    fun render(document: XWPFDocument, teachingPlanDataJson: String, teachingPlanRepository: TeachingPlanRepository) {
        val mapper = jacksonObjectMapper()
        val data = try {
            mapper.readTree(teachingPlanDataJson)
        } catch (e: Exception) {
            return
        }
        
        if (data.isMissingNode || data.isEmpty) return
        
        val titlePara = document.createParagraph()
        titlePara.spacingBefore = 400
        titlePara.spacingAfter = 150
        val titleRun = titlePara.createRun()
        titleRun.setText("三、 语言教学安排")
        titleRun.fontFamily = "微软雅黑"
        titleRun.fontSize = 16
        titleRun.isBold = true

        val teacherIntro = data.path("teacherIntro").asText()
        if (teacherIntro.isNotBlank()) {
            addSectionTitle(document, "1. 师资简介")
            addTextParagraphs(document, teacherIntro)
        }

        val bookIntro = data.path("bookIntro").asText()
        if (bookIntro.isNotBlank()) {
            addSectionTitle(document, "2. 教材简介")
            addTextParagraphs(document, bookIntro)
        }

        val selectedPlanIdsArray = data.path("selectedPlanIds")
        if (selectedPlanIdsArray.isArray && selectedPlanIdsArray.size() > 0) {
            val ids = selectedPlanIdsArray.map { it.asLong() }
            if (ids.isNotEmpty()) {
                val plans = teachingPlanRepository.findAllById(ids)
                if (plans.isNotEmpty()) {
                    addSectionTitle(document, "3. 教学计划大纲")
                    
                    val table = document.createTable(plans.size + 1, 4)
                    val tblW = table.ctTbl.tblPr?.addNewTblW() ?: table.ctTbl.addNewTblPr().addNewTblW()
                    tblW.type = STTblWidth.PCT
                    tblW.w = BigInteger.valueOf(5000)

                    val headerRow = table.getRow(0)
                    DocxStyleUtils.setCellText(headerRow.getCell(0), "教材", bold = true, color = "FFFFFF", fontSize = 10)
                    DocxStyleUtils.setCellText(headerRow.getCell(1), "单元", bold = true, color = "FFFFFF", fontSize = 10)
                    DocxStyleUtils.setCellText(headerRow.getCell(2), "课程内容", bold = true, color = "FFFFFF", fontSize = 10)
                    DocxStyleUtils.setCellText(headerRow.getCell(3), "学习目标", bold = true, color = "FFFFFF", fontSize = 10)
                    
                    headerRow.tableCells.forEach {
                        DocxStyleUtils.setCellShading(it, "002060")
                        DocxStyleUtils.setWhiteBorders(it)
                    }

                    plans.forEachIndexed { index, plan ->
                        val row = table.getRow(index + 1)
                        DocxStyleUtils.setCellText(row.getCell(0), plan.bookName ?: "", bold = false, fontSize = 9)
                        DocxStyleUtils.setCellText(row.getCell(1), plan.unitCode ?: "", bold = false, fontSize = 9)
                        DocxStyleUtils.setCellText(row.getCell(2), plan.courseContent ?: "", bold = false, fontSize = 9)
                        DocxStyleUtils.setCellText(row.getCell(3), plan.learningObjectives ?: "", bold = false, fontSize = 9)
                        
                        row.tableCells.forEach {
                            DocxStyleUtils.setCellShading(it, if (index % 2 == 0) "F2F2F2" else "FFFFFF")
                            DocxStyleUtils.setWhiteBorders(it)
                            DocxStyleUtils.setCellAlignment(it, ParagraphAlignment.LEFT)
                        }
                    }
                    document.createParagraph().spacingAfter = 200
                }
            }
        }

        val coursePlans = data.path("coursePlans")
        if (coursePlans.isArray && coursePlans.size() > 0) {
            val validRowData = coursePlans.filter { row ->
                row.path("phase").asText().isNotBlank() || row.path("goal").asText().isNotBlank()
            }
            if (validRowData.isNotEmpty()) {
                addSectionTitle(document, "4. 课时规划表")
                val table = document.createTable(validRowData.size + 1, 5)
                val tblW = table.ctTbl.tblPr?.addNewTblW() ?: table.ctTbl.addNewTblPr().addNewTblW()
                tblW.type = STTblWidth.PCT
                tblW.w = BigInteger.valueOf(5000)

                val headerRow = table.getRow(0)
                val headers = listOf("阶段", "时长", "目标", "教材", "预计课时")
                headers.forEachIndexed { col, text ->
                    DocxStyleUtils.setCellText(headerRow.getCell(col), text, bold = true, color = "FFFFFF", fontSize = 10)
                    DocxStyleUtils.setCellShading(headerRow.getCell(col), "002060")
                    DocxStyleUtils.setWhiteBorders(headerRow.getCell(col))
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
                        DocxStyleUtils.setCellShading(row.getCell(col), if (index % 2 == 0) "F2F2F2" else "FFFFFF")
                        DocxStyleUtils.setWhiteBorders(row.getCell(col))
                    }
                }
                document.createParagraph().spacingAfter = 100
            }
        }
        
        val coursePlanNote = data.path("coursePlanNote").asText()
        if (coursePlanNote.isNotBlank()) {
            val pNote = document.createParagraph()
            pNote.spacingAfter = 200
            val rNote = pNote.createRun()
            rNote.fontFamily = "微软雅黑"
            rNote.fontSize = 9
            rNote.color = "7F7F7F"
            rNote.setText("*备注：$coursePlanNote")
        }

        val teachingApproach = data.path("teachingApproach").asText()
        if (teachingApproach.isNotBlank()) {
            addSectionTitle(document, "5. 教学思路总结")
            addTextParagraphs(document, teachingApproach)
        }

        val planRisk = data.path("planRisk").asText()
        if (planRisk.isNotBlank()) {
            addSectionTitle(document, "6. 方案风险提示")
            addTextParagraphs(document, planRisk)
        }
    }

    private fun addSectionTitle(document: XWPFDocument, title: String) {
        val p = document.createParagraph()
        p.spacingBefore = 200
        p.spacingAfter = 100
        val r = p.createRun()
        r.setText(title)
        r.fontFamily = "微软雅黑"
        r.fontSize = 12
        r.isBold = true
    }

    private fun addTextParagraphs(document: XWPFDocument, text: String) {
        text.split("\n").forEach { line ->
            if (line.isNotBlank()) {
                val p = document.createParagraph()
                p.spacingAfter = 100
                p.indentationLeft = 300
                val r = p.createRun()
                r.setText(line.trim())
                r.fontFamily = "微软雅黑"
                r.fontSize = 10
            }
        }
    }
}
