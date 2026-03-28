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
                        while (row.tableCells.size < cols) {
                            row.addNewTableCell()
                        }
                    }
                    t
                } else {
                    document.createTable(rows, cols)
                }
            }

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
                
                val scoreNode = subjNode.path("score")
                val isScoreEmptyOrZero = scoreNode.isMissingNode || scoreNode.asText().isBlank() || scoreNode.asDouble(0.0) == 0.0
                val isPaperEmpty = subjNode.path("paperAnalysis").isMissingNode || subjNode.path("paperAnalysis").isEmpty
                val isCauseEmpty = subjNode.path("causeAnalysis").isMissingNode || subjNode.path("causeAnalysis").isEmpty

                if (isScoreEmptyOrZero && isPaperEmpty && isCauseEmpty) continue

                val score = subjNode.path("score").asText()
                val total = subjNode.path("total").asText()
                val level = subjNode.path("level").asText()
                val prefix = if (key in listOf("reading", "listening")) "正确率" else "得分"

                var headerText = "▎ ${displayName}"
                if (score.isNotBlank() && total.isNotBlank()) headerText += "  $prefix $score/$total"
                if (level.isNotBlank()) headerText += "  $level"

                val table = createTableWrappen(1, 2)
                table.removeBorders()
                val tblPr = table.ctTbl.tblPr ?: table.ctTbl.addNewTblPr()
                val tblW = tblPr.tblW ?: tblPr.addNewTblW()
                tblW.type = STTblWidth.PCT
                tblW.w = BigInteger.valueOf(5000)

                // 显式设置极其紧凑的单元格内边距 (2pt)
                val cellMar = tblPr.tblCellMar ?: tblPr.addNewTblCellMar()
                (cellMar.top ?: cellMar.addNewTop()).apply { w = BigInteger.valueOf(40); type = STTblWidth.DXA }
                (cellMar.bottom ?: cellMar.addNewBottom()).apply { w = BigInteger.valueOf(40); type = STTblWidth.DXA }
                (cellMar.left ?: cellMar.addNewLeft()).apply { w = BigInteger.valueOf(100); type = STTblWidth.DXA }
                (cellMar.right ?: cellMar.addNewRight()).apply { w = BigInteger.valueOf(100); type = STTblWidth.DXA }

                val r0 = table.getRow(0)
                val c00 = r0.getCell(0)
                val tcPr0 = c00.ctTc.tcPr ?: c00.ctTc.addNewTcPr()
                tcPr0.addNewGridSpan().`val` = BigInteger.valueOf(2)
                if (r0.tableCells.size > 1) r0.removeCell(1)

                DocxStyleUtils.setCellText(c00, headerText, bold = true, color = "FFFFFF", fontSize = 11)
                DocxStyleUtils.setCellShading(c00, DocxStyleUtils.THEME_PRIMARY)
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
                    
                    c10.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER)
                    c11.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.TOP)

                    DocxStyleUtils.setCellText(c10, "卷面分析", bold = true, color = DocxStyleUtils.THEME_PRIMARY, fontSize = 10)
                    DocxStyleUtils.setCellAlignment(c10, ParagraphAlignment.LEFT)
                    DocxStyleUtils.setCellShading(c10, DocxStyleUtils.THEME_BG_DARK)
                    DocxStyleUtils.setWhiteBorders(c10)

                    DocxStyleUtils.setCellShading(c11, DocxStyleUtils.THEME_BG_LIGHT)
                    DocxStyleUtils.setWhiteBorders(c11)
                    var isFirstP1 = true
                    paperNode.fields().forEach { (dim, valNode) ->
                        val status = valNode.path("status").asText()
                        val text = valNode.path("text").asText()

                        val p1 = if (isFirstP1 && c11.paragraphs.isNotEmpty()) {
                            isFirstP1 = false
                            c11.paragraphs[0]
                        } else {
                            c11.addParagraph()
                        }
                        p1.spacingBefore = 120  // 6pt — 拉开不同指标间的距离
                        p1.spacingAfter  = 40   // 2pt — 指标行与自己的描述略微断开
                        p1.indentationHanging = 200
                        if (p1.ctp.pPr == null) p1.ctp.addNewPPr()
                        if (p1.ctp.pPr.spacing == null) p1.ctp.pPr.addNewSpacing()
                        p1.ctp.pPr.spacing.line = BigInteger.valueOf(280) // 单行指标继续保持紧凑 14pt
                        p1.ctp.pPr.spacing.lineRule = STLineSpacingRule.EXACT

                        val rBullet = p1.createRun()
                        rBullet.fontFamily = DocxStyleUtils.FONT_MAIN
                        rBullet.fontSize = 10
                        rBullet.color = DocxStyleUtils.THEME_ACCENT
                        rBullet.setText("■  ")

                        val rDim = p1.createRun()
                        rDim.fontFamily = DocxStyleUtils.FONT_MAIN
                        rDim.fontSize = 10
                        rDim.isBold = true
                        rDim.setText("${dim}: ")

                        val rDots = p1.createRun()
                        val dotCount = maxOf(3, 25 - dim.length * 2)
                        rDots.setText(".".repeat(dotCount) + " ")

                        val rStatus = p1.createRun()
                        rStatus.fontFamily = DocxStyleUtils.FONT_MAIN  // 统一字体，移除 Segoe UI Emoji
                        rStatus.setText(status)

                        if (text.isNotBlank()) {
                            val p2 = c11.addParagraph()
                            p2.spacingBefore = 0    // 0pt — 紧跟在指标行后面
                            p2.spacingAfter  = 60   // 3pt — 为了跟下一个指标行的 6pt 叠加
                            p2.indentationLeft = 300
                            val rText = p2.createRun()
                            rText.fontFamily = DocxStyleUtils.FONT_MAIN
                            rText.fontSize = 10
                            rText.setText(text)
                            // 增加描述文本内部的行距，给多行文本呼吸感 (15pt)
                            if (p2.ctp.pPr == null) p2.ctp.addNewPPr()
                            if (p2.ctp.pPr.spacing == null) p2.ctp.pPr.addNewSpacing()
                            p2.ctp.pPr.spacing.line = BigInteger.valueOf(300) // 放宽到 15pt
                            p2.ctp.pPr.spacing.lineRule = STLineSpacingRule.EXACT
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

                    c20.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER)
                    c21.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.TOP)

                    DocxStyleUtils.setCellText(c20, "成因分析", bold = true, color = DocxStyleUtils.THEME_PRIMARY, fontSize = 10)
                    DocxStyleUtils.setCellAlignment(c20, ParagraphAlignment.LEFT)
                    DocxStyleUtils.setCellShading(c20, DocxStyleUtils.THEME_BG_DARK)
                    DocxStyleUtils.setWhiteBorders(c20)

                    DocxStyleUtils.setCellShading(c21, DocxStyleUtils.THEME_BG_LIGHT)
                    DocxStyleUtils.setWhiteBorders(c21)
                    var isFirstCause = true
                    causeNode.forEach { causeStrNode ->
                        val causeStr = causeStrNode.asText()
                        val pCause = if (isFirstCause && c21.paragraphs.isNotEmpty()) {
                            isFirstCause = false
                            c21.paragraphs[0]
                        } else {
                            c21.addParagraph()
                        }
                        pCause.spacingBefore = 80   // 4pt
                        pCause.spacingAfter  = 80   // 4pt
                        pCause.indentationLeft = 200
                        pCause.indentationHanging = 200
                        if (pCause.ctp.pPr == null) pCause.ctp.addNewPPr()
                        if (pCause.ctp.pPr.spacing == null) pCause.ctp.pPr.addNewSpacing()
                        pCause.ctp.pPr.spacing.line = BigInteger.valueOf(300) // 15pt 行高，多行阅读更舒适
                        pCause.ctp.pPr.spacing.lineRule = STLineSpacingRule.EXACT
                        val rBullet = pCause.createRun()
                        rBullet.fontFamily = DocxStyleUtils.FONT_MAIN
                        rBullet.fontSize = 10
                        rBullet.color = DocxStyleUtils.THEME_ACCENT
                        rBullet.setText("•  ")
                        
                        val rText = pCause.createRun()
                        rText.fontFamily = DocxStyleUtils.FONT_MAIN
                        rText.fontSize = 10
                        rText.setText(causeStr)
                    }
                }

                createPara().spacingAfter = 300
            }
            
            if (targetPara != null) {
                targetPara.runs.forEach { it.setText("", 0) }
            }
            
        } catch (e: Exception) {
            System.err.println("Failed to parse assessment results for docx: ${e.message}")
        }
    }
}
