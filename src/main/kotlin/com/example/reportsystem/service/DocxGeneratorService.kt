package com.example.reportsystem.service

import org.apache.poi.xwpf.usermodel.*
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*
import com.example.reportsystem.repository.StudentTypeDictionaryRepository
import com.example.reportsystem.repository.SystemConfigRepository
import com.example.reportsystem.entity.StudentTypeDictionary
import java.io.StringReader
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

@Service
class DocxGeneratorService(
    private val studentTypeDictionaryRepository: StudentTypeDictionaryRepository,
    private val systemConfigRepository: SystemConfigRepository
) {

    private val DEFAULT_CSV = """
Lingoland,CEFR,蓝思值,词汇量,剑桥系考试,TOEFL Junior,托福,雅思,香港DSE Band 1A,香港DSE Band 1B-1C,香港DSE Band 2
G1,A1,165L,800,Movers,,,,G2,G5,G7
G2,A2-,425L,1100,Flyers,,,,G3,G6,G8
G3,A2+,600L,1500,KET,625,,3,G4,G7,G9
G4,B1-,725L,2500,PET,725,31,4,G5,G8,G10
G5,B1+,825L,3500,PET,785,45,5,G6,G9,G11
G6,B2-,925L,4500,PET,860,66,5.5,G7,G10,G12
G7,B2+,1000L,6000,FCE,865,93,6.5,G8,G11,
G8,C1-,1050L,7500,FCE,900,101,7,G9,G12,
G9,C1+,1125L,10000,CAE,,109,7.5,G10,,
G10,,1175L,15000,CAE,,120,8,G11,,
G11,,1225L,17500,,,,,,
    """.trimIndent()


    fun generateDocx(
        targetLevel: String?,
        targetGrade: String?,
        studentType: String? = null,
        assessmentTypes: List<String>? = null,
        selectedColumns: List<String>? = null,
        otherAssessment: String? = null,
        assessmentResultsJson: String? = null
    ): ByteArray {
        if (targetLevel != null && targetLevel.matches(Regex("^G([1-9]|10|11)$", RegexOption.IGNORE_CASE))) {
            throw IllegalArgumentException("系统不再支持旧版的 Lingoland 等级 (G1-G11)。请前往学生档案将其测评 Level 更新为对应的 CEFR 等级后再导出报告。")
        }

        val resource = ClassPathResource("static/Lingoland学习方案.docx")
        val document = XWPFDocument(resource.inputStream)

        if (document.tables.isNotEmpty()) {
            rebuildAnalysisTable(document, targetLevel, targetGrade, studentType, assessmentTypes, selectedColumns)
        }

        // --- Interpolate Assessment Descriptions ---
        val descriptionsJson = systemConfigRepository.findByConfigKey("GLOBAL_ASSESSMENT_DESCRIPTIONS")?.configValue
        
        // Calculate the difficulty prefix automatically
        val difficultyNames = mutableListOf<String>()
        if (!otherAssessment.isNullOrBlank()) {
            difficultyNames.add(otherAssessment.trim())
        } else if (!assessmentTypes.isNullOrEmpty()) {
            difficultyNames.addAll(assessmentTypes.map { it.trim() }.filter { it.isNotEmpty() })
        }
        
        val prefixText = if (difficultyNames.isNotEmpty()) {
            "本次测评难度为${difficultyNames.joinToString("、")}难度。"
        } else {
            ""
        }

        var parts: List<String> = emptyList()

        if (!descriptionsJson.isNullOrBlank() && !assessmentTypes.isNullOrEmpty()) {
            try {
                val mapper = jacksonObjectMapper()
                val descs: List<Map<String, String>> = mapper.readValue(descriptionsJson)
                
                val matchedDescs = descs.filter { desc ->
                    val name = desc["name"]?.trim() ?: ""
                    if (name.isEmpty()) return@filter false
                    
                    val isMatch = assessmentTypes.any { type ->
                        val t = type.trim()
                        t.equals(name, ignoreCase = true) || 
                        (t.equals("雅思", ignoreCase = true) && name.equals("IELTS", ignoreCase = true)) ||
                        (t.equals("IELTS", ignoreCase = true) && name.equals("雅思", ignoreCase = true))
                    }
                    isMatch
                }
                
                if (matchedDescs.isNotEmpty()) {
                    var combinedText = matchedDescs.joinToString("\n") { it["description"] ?: "" }
                    // Remove any existing manual difficulty prefix from JSON to avoid duplication
                    combinedText = combinedText.replace(Regex("本次测评难度为.*?难度。\\s*"), "")
                    combinedText = combinedText.replace("。", "。\n")
                    parts = combinedText.split(Regex("\\r?\\n")).map { it.trim() }.filter { it.isNotEmpty() }
                }
            } catch (e: Exception) {
                System.err.println("Failed to parse GLOBAL_ASSESSMENT_DESCRIPTIONS: ${e.message}")
            }
        }
        
        if (prefixText.isNotEmpty()) {
            parts = listOf(prefixText) + parts
        }

        // Always replace the target paragraph to remove default 'KET' text
        val targetPara = document.paragraphs.find { it.text.contains("测评说明") }
        if (targetPara != null) {
            // Completely clear runs to prevent leftover text (e.g. duplicate bullets like "■")
            while (targetPara.runs.isNotEmpty()) {
                targetPara.removeRun(0)
            }
            
            if (parts.isNotEmpty()) {
                var lastPara: XWPFParagraph = targetPara
                parts.forEachIndexed { index, part ->
                    if (index == 0) {
                        val titleRun = targetPara.createRun()
                        titleRun.fontFamily = "微软雅黑"
                        titleRun.fontSize = 10
                        titleRun.isBold = true
                        titleRun.setText("测评说明：")
                        
                        val textRun = targetPara.createRun()
                        textRun.fontFamily = "微软雅黑"
                        textRun.fontSize = 10
                        textRun.setText(part)
                    } else {
                        // Create a new paragraph for subsequent lines to ensure proper wrapping
                        // Copy the formatting of the original paragraph to maintain indentation
                        val cursor = lastPara.getCTP().newCursor()
                        cursor.toNextSibling()
                        val newPara = document.insertNewParagraph(cursor)
                        if (targetPara.getCTP().pPr != null) {
                            newPara.getCTP().pPr = targetPara.getCTP().pPr.copy() as org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr
                            // Remove numbering (bullet) so it doesn't add another dot
                            if (newPara.getCTP().pPr.isSetNumPr) {
                                newPara.getCTP().pPr.unsetNumPr()
                            }
                        }
                        cursor.dispose()
                        lastPara = newPara
                        
                        val textRun = newPara.createRun()
                        textRun.fontFamily = "微软雅黑"
                        textRun.fontSize = 10
                        textRun.setText(part)
                    }
                }
            } else {
                // If parts is empty but we still want the title
                val titleRun = targetPara.createRun()
                titleRun.fontFamily = "微软雅黑"
                titleRun.fontSize = 10
                titleRun.isBold = true
                titleRun.setText("测评说明：")
            }
        }

        // --- Append Assessment Analysis Tables ---
        appendAssessmentAnalysis(document, assessmentResultsJson)

        val out = ByteArrayOutputStream()
        document.write(out)
        val bytes = out.toByteArray()
        document.close()
        return bytes
    }
    
    // ──────────────────────────────────────────────────────────────────────────
    // Assessment Analysis Table Append
    // ──────────────────────────────────────────────────────────────────────────

    private fun appendAssessmentAnalysis(document: XWPFDocument, assessmentResultsJson: String?) {
        if (assessmentResultsJson.isNullOrBlank()) return
        try {
            val mapper = jacksonObjectMapper()
            val analysis = mapper.readTree(assessmentResultsJson)
            if (analysis.isMissingNode || analysis.isEmpty) return

            // Find placeholder
            var targetPara: XWPFParagraph? = null
            for (p in document.paragraphs) {
                if (p.text.contains("{assessment_analysis}")) {
                    targetPara = p
                    break
                }
            }

            // Helper to create paragraph, either inserted or appended
            fun createPara(): XWPFParagraph {
                return if (targetPara != null) {
                    val c = targetPara.ctp.newCursor()
                    val p = document.insertNewParagraph(c)
                    c.dispose()
                    // The standard insert doesn't register it in doc.paragraphs list nicely,
                    // but it works for layout.
                    p
                } else {
                    document.createParagraph()
                }
            }

            // Create Table helper
            fun createTableWrappen(rows: Int, cols: Int): XWPFTable {
                return if (targetPara != null) {
                    val c = targetPara.ctp.newCursor()
                    val t = document.insertNewTbl(c)
                    c.dispose()
                    // It creates 1x1 by default in raw XML, but XWPF wrapper expects layout matching
                    // We must initialize the table structure manually for it to work.
                    
                    // Remove default row to let our loop handle it
                    if (t.rows.isNotEmpty()) t.removeRow(0)
                    for (r in 0 until rows) {
                        val row = t.createRow()
                        for (c in 1 until cols) { // First cell created by createRow usually? Wait, createRow creates 1 cell.
                            row.addNewTableCell()
                        }
                    }
                    t
                } else {
                    document.createTable(rows, cols)
                }
            }

            // Add Header "二、 测评分析"
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
                
                // Make sure it actually has data
                if (subjNode.path("score").isMissingNode && subjNode.path("level").isMissingNode && 
                    subjNode.path("paperAnalysis").isMissingNode && subjNode.path("causeAnalysis").isMissingNode) continue

                val score = subjNode.path("score").asText()
                val total = subjNode.path("total").asText()
                val level = subjNode.path("level").asText()
                val prefix = if (key in listOf("reading", "listening")) "正确率" else "得分"

                var headerText = "●  $displayName"
                if (score.isNotBlank() && total.isNotBlank()) headerText += "  $prefix $score/$total"
                if (level.isNotBlank()) headerText += "  $level"

                // Create Table wrapper
                val table = createTableWrappen(1, 2)
                table.removeBorders()
                val tblPr = table.ctTbl.tblPr ?: table.ctTbl.addNewTblPr()
                val tblW = tblPr.tblW ?: tblPr.addNewTblW()
                tblW.type = STTblWidth.PCT
                tblW.w = BigInteger.valueOf(5000)

                // Row 0: Header
                val r0 = table.getRow(0)
                val c00 = r0.getCell(0)
                val tcPr0 = c00.ctTc.tcPr ?: c00.ctTc.addNewTcPr()
                tcPr0.addNewGridSpan().`val` = BigInteger.valueOf(2)
                if (r0.tableCells.size > 1) r0.removeCell(1)

                setCellText(c00, headerText, bold = true, color = "FFFFFF", fontSize = 11)
                setCellShading(c00, "002060")
                setWhiteBorders(c00)

                // Row 1: Paper Analysis (卷面分析)
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

                    setCellText(c10, "A. 卷面分析", bold = false, color = "000000", fontSize = 10)
                    setCellAlignment(c10, ParagraphAlignment.CENTER)
                    setCellShading(c10, "F2F2F2")
                    setWhiteBorders(c10)

                    setCellShading(c11, "F9F9F9")
                    setWhiteBorders(c11)
                    if (c11.paragraphs.isNotEmpty()) {
                        val firstPara = c11.paragraphs[0]
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
                        rDim.setText("$dim: ")

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

                // Row 2: Cause Analysis (成因分析)
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

                    setCellText(c20, "B. 成因分析", bold = false, color = "000000", fontSize = 10)
                    setCellAlignment(c20, ParagraphAlignment.CENTER)
                    setCellShading(c20, "EFEFEF")
                    setWhiteBorders(c20)

                    setCellShading(c21, "F2F2F2")
                    setWhiteBorders(c21)
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
            
            // Cleanup placeholder
            if (targetPara != null) {
                targetPara.runs.forEach { it.setText("", 0) }
                // For a more complete remove: document.removeBodyElement(document.getPosOfParagraph(targetPara))
            }
            
        } catch (e: Exception) {
            System.err.println("Failed to parse assessment results for docx: ${e.message}")
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Table rebuild
    // ──────────────────────────────────────────────────────────────────────────

    private fun rebuildAnalysisTable(
        document: XWPFDocument,
        targetLevel: String?,
        targetGrade: String?,
        studentType: String?,
        assessmentTypes: List<String>?,
        selectedColumns: List<String>?
    ) {
        val table = document.tables[0]
        val LOW_AGE_TYPES = setOf("starters", "movers", "flyers")

        // ── Fetched Dynamic Capability Matrix ─────────────────────────────
        var headers: List<String> = emptyList()
        var dataRows: List<List<String>> = emptyList()

        val globalCsvConfig = systemConfigRepository.findByConfigKey("GLOBAL_CAPABILITY_MATRIX_CSV")?.configValue

        if (!globalCsvConfig.isNullOrBlank()) {
            val parser = CSVParser(StringReader(globalCsvConfig), CSVFormat.DEFAULT)
            val allCsvRows = parser.records
            if (allCsvRows.isNotEmpty()) {
                headers = allCsvRows.first().toList()
                dataRows = allCsvRows.drop(1).map { it.toList() }
            }
        }
        
        if (headers.isEmpty() || dataRows.isEmpty()) {
            val parser = CSVParser(StringReader(DEFAULT_CSV), CSVFormat.DEFAULT)
            val allCsvRows = parser.records
            headers = allCsvRows.first().toList()
            dataRows = allCsvRows.drop(1).map { it.toList() }
        }

        // Apply selected columns filter
        var filteredHeaders = headers
        val selectedIndices = if (!selectedColumns.isNullOrEmpty()) {
            headers.mapIndexedNotNull { index, headerName -> if (selectedColumns.contains(headerName)) index else null }.takeIf { it.isNotEmpty() }
        } else null

        if (selectedIndices != null) {
            filteredHeaders = selectedIndices.map { headers[it] }
        }

        val showKRow = assessmentTypes?.any { it.trim().lowercase() in LOW_AGE_TYPES } ?: false
        val lingolandGlobalIdx = headers.indexOf("Lingoland")
        val cefrGlobalIdx = headers.indexOf("CEFR")
        
        var fullRowsToRender = dataRows
        if (!showKRow) {
            fullRowsToRender = fullRowsToRender.filter { row -> 
                !(lingolandGlobalIdx >= 0 && row.getOrNull(lingolandGlobalIdx) == "K") 
            }
        }

        // ── Step 3: Clear existing table rows (use high-level API to keep POI state in sync)
        for (i in table.numberOfRows - 1 downTo 0) {
            table.removeRow(i)
        }

        // ── Step 4: Build new rows ─────────────────────────────────────────
        if (filteredHeaders.isNotEmpty() && fullRowsToRender.isNotEmpty()) {
            buildHeaderRow(table, filteredHeaders)
            fullRowsToRender.forEachIndexed { idx, fullRow ->
                val lingolandValue = if (lingolandGlobalIdx >= 0) fullRow.getOrNull(lingolandGlobalIdx) ?: "" else fullRow.getOrNull(0) ?: ""
                val cefrValue = if (cefrGlobalIdx >= 0) fullRow.getOrNull(cefrGlobalIdx) ?: "" else ""

                val isTargetLevel = targetLevel != null && (lingolandValue.equals(targetLevel, ignoreCase = true) || cefrValue.equals(targetLevel, ignoreCase = true))
                val isTargetGrade = targetGrade != null && (
                    lingolandValue.equals(targetGrade, ignoreCase = true) ||
                    lingolandValue.equals("G" + targetGrade.replace("年级","").replace("初一","7")
                        .replace("初二","8").replace("初三","9").replace("高一","10"), ignoreCase = true)
                )
                val isGradeRow = lingolandValue.matches(Regex("^(K|G[0-9]|G1[0-2])$")) || lingolandValue.isEmpty()

                val rowDataForDisplay = if (selectedIndices != null) selectedIndices.map { fullRow.getOrNull(it) ?: "" } else fullRow

                buildDataRow(table, filteredHeaders.size, rowDataForDisplay, isTargetLevel, isTargetGrade, isGradeRow, isLastDataRow = idx == fullRowsToRender.size - 1)
            }
        }

        // ── Step 5: Configure table to Autofit and full width ──────────────
        val tblPr = table.ctTbl.tblPr ?: table.ctTbl.addNewTblPr()
        val tblW = tblPr.tblW ?: tblPr.addNewTblW()
        tblW.type = STTblWidth.PCT
        tblW.w = BigInteger.valueOf(5000) // 100% width
        val tblLayout = tblPr.tblLayout ?: tblPr.addNewTblLayout()
        tblLayout.type = STTblLayoutType.AUTOFIT

        // ── Step 6: Add legend immediately below table ──────────────────
        addLegendBelowTable(document, table)
    }

    private fun addLegendBelowTable(document: XWPFDocument, table: XWPFTable) {
        val cursor = table.ctTbl.newCursor()
        cursor.toNextSibling()
        
        val legendPara = document.insertNewParagraph(cursor)
        legendPara.spacingBefore = 100
        
        fun addRun(text: String, bold: Boolean = false) {
            val r = legendPara.createRun()
            r.fontFamily = "微软雅黑"
            r.fontSize = 9
            r.isBold = bold
            r.setText(text)
        }
        
        addRun("注：")
        addRun("黄色背景", bold = true)
        addRun("表示所在年级；")
        addRun("蓝色背景", bold = true)
        addRun("表示听说读写的水平。")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Row builders
    // ──────────────────────────────────────────────────────────────────────────

    private fun getOrCreateCell(row: XWPFTableRow, index: Int): XWPFTableCell {
        return row.getCell(index) ?: row.addNewTableCell()
    }

    private fun buildHeaderRow(table: XWPFTable, headers: List<String>) {
        val row = table.createRow()
        headers.forEachIndexed { i, colHeader ->
            val cell = getOrCreateCell(row, i)
            setCellText(cell, colHeader, bold = true)
            setCellShading(cell, "4472C4")
            setCellTextColor(cell, "FFFFFF")
            setCellAlignment(cell, ParagraphAlignment.CENTER)
            setCellBorders(cell, isHeader = true, isFirstRow = true, isLastRow = false, isFirstCol = (i == 0), isLastCol = (i == headers.size - 1))
        }
    }

    private fun buildDataRow(
        table: XWPFTable,
        colCount: Int,
        rowData: List<String>,
        isTargetLevel: Boolean,
        isTargetGrade: Boolean,
        isGradeRow: Boolean,
        isLastDataRow: Boolean
    ) {
        val row = table.createRow()

        val bgColor = when {
            isTargetLevel -> "DAE3F4" // Blue
            isTargetGrade -> "FFF2CC" // Yellow
            isGradeRow    -> "F1F1F1" // Gray
            else          -> "FFFFFF"
        }

        for (i in 0 until colCount) {
            val cellValue = rowData.getOrNull(i) ?: ""
            val cell = getOrCreateCell(row, i)
            setCellText(cell, cellValue, bold = false)
            setCellShading(cell, bgColor)
            setCellAlignment(cell, ParagraphAlignment.CENTER)
            setCellBorders(cell, isHeader = false, isFirstRow = false, isLastRow = isLastDataRow, isFirstCol = (i == 0), isLastCol = (i == colCount - 1))
        }
    }


    // ──────────────────────────────────────────────────────────────────────────
    // Cell helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun setCellText(cell: XWPFTableCell, text: String, bold: Boolean, color: String? = null, fontSize: Int = 9) {
        val para = cell.paragraphs.firstOrNull() ?: cell.addParagraph()
        para.runs.forEach { it.setText("", 0) }
        val run = if (para.runs.isEmpty()) para.createRun() else para.runs[0]
        run.setText(text, 0)
        run.isBold = bold
        run.fontSize = fontSize
        run.fontFamily = "微软雅黑"
        if (color != null) {
            run.setColor(color)
        }
    }

    private fun setWhiteBorders(cell: XWPFTableCell) {
        val tcPr = cell.ctTc.tcPr ?: cell.ctTc.addNewTcPr()
        val tcBorders = tcPr.tcBorders ?: tcPr.addNewTcBorders()

        fun setW(border: org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder) {
            border.`val` = org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE
            border.color = "FFFFFF"
            border.sz = BigInteger.valueOf(12)
            border.space = BigInteger.ZERO
        }

        setW(tcBorders.top ?: tcBorders.addNewTop())
        setW(tcBorders.bottom ?: tcBorders.addNewBottom())
        setW(tcBorders.left ?: tcBorders.addNewLeft())
        setW(tcBorders.right ?: tcBorders.addNewRight())
    }

    private fun setCellShading(cell: XWPFTableCell, hexColor: String) {
        val tcPr = cell.ctTc.tcPr ?: cell.ctTc.addNewTcPr()
        val shd = tcPr.shd ?: tcPr.addNewShd()
        shd.`val` = STShd.CLEAR
        shd.color = "auto"
        shd.fill = hexColor
        if (shd.isSetThemeFill) shd.unsetThemeFill()
        if (shd.isSetThemeFillShade) shd.unsetThemeFillShade()
        if (shd.isSetThemeFillTint) shd.unsetThemeFillTint()
    }

    private fun setCellTextColor(cell: XWPFTableCell, hexColor: String) {
        cell.paragraphs.forEach { p -> p.runs.forEach { r -> r.setColor(hexColor) } }
    }

    private fun setCellAlignment(cell: XWPFTableCell, alignment: ParagraphAlignment) {
        cell.paragraphs.forEach { it.alignment = alignment }
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER)
    }

    private fun setCellBorders(cell: XWPFTableCell, isHeader: Boolean, isFirstRow: Boolean, isLastRow: Boolean, isFirstCol: Boolean, isLastCol: Boolean) {
        val tcPr = cell.ctTc.tcPr ?: cell.ctTc.addNewTcPr()
        val tcBorders = tcPr.tcBorders ?: tcPr.addNewTcBorders()

        // In MS Word, cell borders that touch the outer table border will visually blend with it.
        // We will just define the inner-facing borders and use a fallback for outer.
        val top = tcBorders.top ?: tcBorders.addNewTop()
        val left = tcBorders.left ?: tcBorders.addNewLeft()
        val bottom = tcBorders.bottom ?: tcBorders.addNewBottom()
        val right = tcBorders.right ?: tcBorders.addNewRight()

        // Helper
        fun setB(border: org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder, type: org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.Enum, color: String) {
            border.`val` = type
            border.color = color
            border.sz = BigInteger.valueOf(4)
            border.space = BigInteger.ZERO
        }

        if (isHeader) {
            setB(top, org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE, "FFFFFF")
            setB(bottom, org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE, "FFFFFF")
            setB(left, org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE, "FFFFFF")
            setB(right, org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE, "FFFFFF")
        } else {
            setB(top, org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.DASHED, "BFBFBF")
            setB(bottom, org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.DASHED, "BFBFBF")
            setB(left, org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.DASHED, "BFBFBF")
            setB(right, org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.DASHED, "BFBFBF")
        }

        // Keep double line outer borders manually by overriding the specific edge
        if (isFirstRow) setB(top, org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.DOUBLE, "000000")
        if (isLastRow) setB(bottom, org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.DOUBLE, "000000")
        if (isFirstCol) setB(left, org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.DOUBLE, "000000")
        if (isLastCol) setB(right, org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.DOUBLE, "000000")
    }
}
