package com.example.reportsystem.service.docx

import org.apache.poi.xwpf.usermodel.*
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*
import java.math.BigInteger
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

object DocxAssessmentAnalysisRenderer {

    fun render(document: XWPFDocument, assessmentResultsJson: String?) {
        if (assessmentResultsJson.isNullOrBlank()) return
        try {
            val mapper = jacksonObjectMapper()
            val analysis = mapper.readTree(assessmentResultsJson)
            if (analysis.isMissingNode || analysis.isEmpty) return

            var targetPara: XWPFParagraph? = null
            for (p in document.paragraphs) {
                if (p.text.contains("{assessment_analysis}")) {
                    targetPara = p
                    break
                }
            }

            fun createPara(): XWPFParagraph {
                return if (targetPara != null) {
                    val c = targetPara.ctp.newCursor()
                    val p = document.insertNewParagraph(c)
                    c.dispose()
                    p
                } else {
                    document.createParagraph()
                }
            }

            fun createTableWrappen(rows: Int, cols: Int): XWPFTable {
                return if (targetPara != null) {
                    val c = targetPara.ctp.newCursor()
                    val t = document.insertNewTbl(c)
                    c.dispose()
                    if (t.rows.isNotEmpty()) t.removeRow(0)
                    for (r in 0 until rows) {
                        val row = t.createRow()
                        for (cIdx in 1 until cols) {
                            row.addNewTableCell()
                        }
                    }
                    t
                } else {
                    document.createTable(rows, cols)
                }
            }

            val titlePara = createPara()
            titlePara.spacingBefore = 400
            titlePara.spacingAfter = 150
            val titleRun = titlePara.createRun()
            titleRun.setText("二、 测评分析")
            titleRun.fontFamily = "微软雅黑"
            titleRun.fontSize = 16
            titleRun.isBold = true

            val subjectMap = mapOf(
                "reading" to "阅读",
                "listening" to "听力",
                "speaking" to "口语",
                "writing" to "写作",
                "language_use" to "语言运用",
                "learning_literacy" to "学习素养"
            )

            for ((key, displayName) in subjectMap) {
                val subjNode = analysis.path(key)
                if (subjNode.isMissingNode || subjNode.isEmpty) continue
                
                if (subjNode.path("score").isMissingNode && subjNode.path("level").isMissingNode && 
                    subjNode.path("paperAnalysis").isMissingNode && subjNode.path("causeAnalysis").isMissingNode) continue

                val score = subjNode.path("score").asText()
                val total = subjNode.path("total").asText()
                val level = subjNode.path("level").asText()
                val prefix = if (key in listOf("reading", "listening")) "正确率" else "得分"

                var headerText = "●  ${displayName}"
                if (score.isNotBlank() && total.isNotBlank()) headerText += "  $prefix $score/$total"
                if (level.isNotBlank()) headerText += "  $level"

                val table = createTableWrappen(1, 2)
                table.removeBorders()
                val tblPr = table.ctTbl.tblPr ?: table.ctTbl.addNewTblPr()
                val tblW = tblPr.tblW ?: tblPr.addNewTblW()
                tblW.type = STTblWidth.PCT
                tblW.w = BigInteger.valueOf(5000)

                val r0 = table.getRow(0)
                val c00 = r0.getCell(0)
                val tcPr0 = c00.ctTc.tcPr ?: c00.ctTc.addNewTcPr()
                tcPr0.addNewGridSpan().`val` = BigInteger.valueOf(2)
                if (r0.tableCells.size > 1) r0.removeCell(1)

                DocxStyleUtils.setCellText(c00, headerText, bold = true, color = "FFFFFF", fontSize = 11)
                DocxStyleUtils.setCellShading(c00, "002060")
                DocxStyleUtils.setWhiteBorders(c00)

                val paperNode = subjNode.path("paperAnalysis")
                if (!paperNode.isMissingNode && paperNode.isObject && paperNode.size() > 0) {
                    val r1 = table.createRow()
                    if (r1.tableCells.size < 2) {
                        while (r1.tableCells.size < 2) r1.addNewTableCell()
                    }
                    val c10 = r1.getCell(0)
                    val c11 = r1.getCell(1)

                    c10.ctTc.tcPr?.let { c10.ctTc.unsetTcPr() }
                    c11.ctTc.tcPr?.let { c11.ctTc.unsetTcPr() }
                    c10.ctTc.addNewTcPr().addNewTcW().apply { w = BigInteger.valueOf(1000); type = STTblWidth.PCT }
                    c11.ctTc.addNewTcPr().addNewTcW().apply { w = BigInteger.valueOf(4000); type = STTblWidth.PCT }

                    DocxStyleUtils.setCellText(c10, "A. 卷面分析", bold = false, color = "000000", fontSize = 10)
                    DocxStyleUtils.setCellAlignment(c10, ParagraphAlignment.CENTER)
                    DocxStyleUtils.setCellShading(c10, "F2F2F2")
                    DocxStyleUtils.setWhiteBorders(c10)

                    DocxStyleUtils.setCellShading(c11, "F9F9F9")
                    DocxStyleUtils.setWhiteBorders(c11)
                    if (c11.paragraphs.isNotEmpty()) {
                        c11.removeParagraph(0)
                    }

                    paperNode.fields().forEach { (dim, valNode) ->
                        val status = valNode.path("status").asText()
                        val text = valNode.path("text").asText()

                        val p1 = c11.addParagraph()
                        p1.spacingBefore = 100
                        p1.spacingAfter = 50

                        val rBullet = p1.createRun()
                        rBullet.fontFamily = "微软雅黑"
                        rBullet.fontSize = 10
                        rBullet.setText("■  ")

                        val rDim = p1.createRun()
                        rDim.fontFamily = "微软雅黑"
                        rDim.fontSize = 10
                        rDim.isBold = true
                        rDim.setText("${dim}: ")

                        val rDots = p1.createRun()
                        val dotCount = maxOf(3, 25 - dim.length * 2)
                        rDots.setText(".".repeat(dotCount) + " ")

                        val rStatus = p1.createRun()
                        rStatus.fontFamily = "Segoe UI Emoji"
                        rStatus.setText(status)

                        if (text.isNotBlank()) {
                            val p2 = c11.addParagraph()
                            p2.spacingBefore = 50
                            p2.spacingAfter = 100
                            p2.indentationLeft = 300
                            val rText = p2.createRun()
                            rText.fontFamily = "微软雅黑"
                            rText.fontSize = 10
                            rText.setText(text)
                        }
                    }
                }

                val causeNode = subjNode.path("causeAnalysis")
                if (!causeNode.isMissingNode && causeNode.isArray && causeNode.size() > 0) {
                    val r2 = table.createRow()
                    if (r2.tableCells.size < 2) {
                        while (r2.tableCells.size < 2) r2.addNewTableCell()
                    }
                    val c20 = r2.getCell(0)
                    val c21 = r2.getCell(1)

                    c20.ctTc.tcPr?.let { c20.ctTc.unsetTcPr() }
                    c21.ctTc.tcPr?.let { c21.ctTc.unsetTcPr() }
                    c20.ctTc.addNewTcPr().addNewTcW().apply { w = BigInteger.valueOf(1000); type = STTblWidth.PCT }
                    c21.ctTc.addNewTcPr().addNewTcW().apply { w = BigInteger.valueOf(4000); type = STTblWidth.PCT }

                    DocxStyleUtils.setCellText(c20, "B. 成因分析", bold = false, color = "000000", fontSize = 10)
                    DocxStyleUtils.setCellAlignment(c20, ParagraphAlignment.CENTER)
                    DocxStyleUtils.setCellShading(c20, "EFEFEF")
                    DocxStyleUtils.setWhiteBorders(c20)

                    DocxStyleUtils.setCellShading(c21, "F2F2F2")
                    DocxStyleUtils.setWhiteBorders(c21)
                    if (c21.paragraphs.isNotEmpty()) {
                        c21.removeParagraph(0)
                    }

                    causeNode.forEach { causeStrNode ->
                        val causeStr = causeStrNode.asText()
                        val pCause = c21.addParagraph()
                        pCause.spacingBefore = 100
                        pCause.spacingAfter = 100
                        pCause.indentationLeft = 150
                        val rBullet = pCause.createRun()
                        rBullet.fontFamily = "微软雅黑"
                        rBullet.fontSize = 10
                        rBullet.setText("●  $causeStr")
                    }
                }

                createPara().spacingAfter = 200
            }
            
            if (targetPara != null) {
                targetPara.runs.forEach { it.setText("", 0) }
            }
            
        } catch (e: Exception) {
            System.err.println("Failed to parse assessment results for docx: ${e.message}")
        }
    }
}
