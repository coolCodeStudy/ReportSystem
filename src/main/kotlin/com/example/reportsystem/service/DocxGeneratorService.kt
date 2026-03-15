package com.example.reportsystem.service

import org.apache.poi.xwpf.usermodel.*
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*
import com.example.reportsystem.repository.StudentTypeDictionaryRepository
import com.example.reportsystem.entity.StudentTypeDictionary
import java.io.StringReader
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.math.BigInteger

@Service
class DocxGeneratorService(
    private val studentTypeDictionaryRepository: StudentTypeDictionaryRepository
) {

    private val DEFAULT_CSV = """
Lingoland,CEFR,蓝思值,词汇量,剑桥系考试,TOEFL Junior,托福,雅思
K,Pre-A1,BR-180L,200-400,,,,
G1,A1.1,190L-300L,400-800,,,,
G2,A1.2,310L-410L,800-1200,,,,
G3,A2.1,420L-600L,1200-1500,YLE Movers,,,
G4,A2.2,610L-740L,1500-2000,YLE Flyers,,,
G5,B1.1,750L-850L,2000-2500,KET(120-139),,少儿托福 STEP 1,
G6,B1.2,860L-920L,2500-3500,PET(140-152),600-750,少儿托福 STEP 2,
G7,B2.1,930L-1010L,3500-4500,FCE(160-172),760-840,40-60,4.5-5.0
G8,B2.2,1020L-1090L,4500-6000,CAE(180-192),850-900,60-80,5.5-6.0
G9,C1.1,1100L-1200L,6000-8000,CPE(200-210),,80-100,6.5-7.0
G10,C1.2,1210L-1300L,8000-10000,,,100-110,7.0-7.5
G11,C2,1300L+,10000+,,,110+,7.5+
    """.trimIndent()


    fun generateDocx(
        targetLevel: String?,
        targetGrade: String?,
        studentType: String? = null,
        assessmentTypes: List<String>? = null
    ): ByteArray {
        val resource = ClassPathResource("static/Lingoland学习方案.docx")
        val document = XWPFDocument(resource.inputStream)

        if (document.tables.isNotEmpty()) {
            rebuildAnalysisTable(document, targetLevel, targetGrade, studentType, assessmentTypes)
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
        assessmentTypes: List<String>?
    ) {
        val table = document.tables[0]
        val LOW_AGE_TYPES = setOf("starters", "movers", "flyers")

        // ── Fetched Dynamic Capability Matrix ─────────────────────────────
        var headers: List<String> = emptyList()
        var dataRows: List<List<String>> = emptyList()

        if (!studentType.isNullOrBlank()) {
            val typeEntity = studentTypeDictionaryRepository.findByTypeCode(studentType)
            val csvContent = typeEntity?.capabilityMatrixCsv
            if (!csvContent.isNullOrBlank()) {
                val parser = CSVParser(StringReader(csvContent), CSVFormat.DEFAULT)
                val allCsvRows = parser.records
                if (allCsvRows.isNotEmpty()) {
                    headers = allCsvRows.first().toList()
                    dataRows = allCsvRows.drop(1).map { it.toList() }
                }
            }
        }
        
        if (headers.isEmpty() || dataRows.isEmpty()) {
            val parser = CSVParser(StringReader(DEFAULT_CSV), CSVFormat.DEFAULT)
            val allCsvRows = parser.records
            headers = allCsvRows.first().toList()
            dataRows = allCsvRows.drop(1).map { it.toList() }
        }

        val showKRow = assessmentTypes?.any { it.trim().lowercase() in LOW_AGE_TYPES } ?: false
        var rowsToRender = dataRows
        if (!showKRow && rowsToRender.isNotEmpty()) {
            rowsToRender = rowsToRender.filter { it.isNotEmpty() && it[0] != "K" }
        }

        // ── Step 3: Clear existing table rows (use high-level API to keep POI state in sync)
        for (i in table.numberOfRows - 1 downTo 0) {
            table.removeRow(i)
        }

        // ── Step 4: Build new rows ─────────────────────────────────────────
        if (headers.isNotEmpty() && rowsToRender.isNotEmpty()) {
            buildHeaderRow(table, headers)
            rowsToRender.forEachIndexed { idx, rowData ->
                buildDataRow(table, headers.size, rowData, targetLevel, targetGrade, isLastDataRow = idx == rowsToRender.size - 1)
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
        targetLevel: String?,
        targetGrade: String?,
        isLastDataRow: Boolean
    ) {
        val row = table.createRow()
        val lingoland = rowData.getOrNull(0) ?: ""

        val isTargetLevel = targetLevel != null && lingoland.equals(targetLevel, ignoreCase = true)
        val isTargetGrade = targetGrade != null && (
            lingoland.equals(targetGrade, ignoreCase = true) ||
            lingoland.equals("G" + targetGrade.replace("年级","").replace("初一","7")
                .replace("初二","8").replace("初三","9").replace("高一","10"), ignoreCase = true)
        )
        val isGradeRow = lingoland.matches(Regex("^(K|G[0-9]|G1[0-2])$")) || lingoland.isEmpty()

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
