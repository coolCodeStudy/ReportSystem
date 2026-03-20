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
        selectedColumns: List<String>? = null
    ): ByteArray {
        if (targetLevel != null && targetLevel.matches(Regex("^G([1-9]|10|11)$", RegexOption.IGNORE_CASE))) {
            throw IllegalArgumentException("系统不再支持旧版的 Lingoland 等级 (G1-G11)。请前往学生档案将其测评 Level 更新为对应的 CEFR 等级后再导出报告。")
        }

        val resource = ClassPathResource("static/Lingoland学习方案.docx")
        val document = XWPFDocument(resource.inputStream)

        if (document.tables.isNotEmpty()) {
            rebuildAnalysisTable(document, targetLevel, targetGrade, studentType, assessmentTypes, selectedColumns)
        }

        // --- Append Assessment Descriptions ---
        val descriptionsJson = systemConfigRepository.findByConfigKey("GLOBAL_ASSESSMENT_DESCRIPTIONS")?.configValue
        if (!descriptionsJson.isNullOrBlank() && !assessmentTypes.isNullOrEmpty()) {
            try {
                val mapper = jacksonObjectMapper()
                val descs: List<Map<String, String>> = mapper.readValue(descriptionsJson)
                
                val matchedDescs = descs.filter { desc ->
                    val name = desc["name"]?.trim() ?: ""
                    name.isNotEmpty() && assessmentTypes.any { it.trim().equals(name, ignoreCase = true) }
                }
                
                if (matchedDescs.isNotEmpty()) {
                    val para = document.createParagraph()
                    para.spacingBefore = 400
                    
                    val titleRun = para.createRun()
                    titleRun.fontFamily = "微软雅黑"
                    titleRun.fontSize = 10
                    titleRun.isBold = true
                    titleRun.setText("测评说明：")
                    
                    val combinedText = matchedDescs.joinToString("\n") { it["description"] ?: "" }.trim()
                    val parts = combinedText.split("\n")
                    
                    parts.forEachIndexed { index, part ->
                        if (index > 0) {
                            val breakRun = para.createRun()
                            breakRun.addBreak()
                        }
                        val textRun = para.createRun()
                        textRun.fontFamily = "微软雅黑"
                        textRun.fontSize = 10
                        textRun.setText(part)
                    }
                }
            } catch (e: Exception) {
                System.err.println("Failed to parse GLOBAL_ASSESSMENT_DESCRIPTIONS: ${e.message}")
            }
        }

        val out = ByteArrayOutputStream()
        document.write(out)
        val bytes = out.toByteArray()
        document.close()
        return bytes
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

    private fun setCellText(cell: XWPFTableCell, text: String, bold: Boolean) {
        val para = cell.paragraphs.firstOrNull() ?: cell.addParagraph()
        para.runs.forEach { it.setText("", 0) }
        val run = if (para.runs.isEmpty()) para.createRun() else para.runs[0]
        run.setText(text, 0)
        run.isBold = bold
        run.fontSize = 9
        run.fontFamily = "微软雅黑"
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
