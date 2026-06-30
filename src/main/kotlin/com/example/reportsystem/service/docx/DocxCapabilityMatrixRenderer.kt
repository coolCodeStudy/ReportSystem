package com.example.reportsystem.service.docx

import org.apache.poi.xwpf.usermodel.*
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.StringReader
import java.math.BigInteger

object DocxCapabilityMatrixRenderer {

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

    fun render(
        document: XWPFDocument,
        targetLevel: String?,
        targetGrade: String?,
        studentType: String?,
        assessmentTypes: List<String>?,
        selectedColumns: List<String>?,
        globalCsvConfig: String?
    ) {
        if (document.tables.isEmpty()) return
        val table = document.tables[0]
        val LOW_AGE_TYPES = setOf("starters", "movers", "flyers")

        var headers: List<String> = emptyList()
        var dataRows: List<List<String>> = emptyList()

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

        for (i in table.numberOfRows - 1 downTo 0) {
            table.removeRow(i)
        }

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

                buildDataRow(table, filteredHeaders.size, rowDataForDisplay, isTargetLevel, isTargetGrade, isGradeRow,
                    isLastDataRow = idx == fullRowsToRender.size - 1)
            }
        }

        val tblPr = table.ctTbl.tblPr ?: table.ctTbl.addNewTblPr()
        val tblW = tblPr.tblW ?: tblPr.addNewTblW()
        tblW.type = STTblWidth.PCT
        tblW.w = BigInteger.valueOf(5000)
        val tblLayout = tblPr.tblLayout ?: tblPr.addNewTblLayout()
        tblLayout.type = STTblLayoutType.AUTOFIT

        addLegendBelowTable(document, table)
    }

    private fun addLegendBelowTable(document: XWPFDocument, table: XWPFTable) {
        val cursor = table.ctTbl.newCursor()
        cursor.toNextSibling()
        val legendPara = document.insertNewParagraph(cursor)
        legendPara.spacingBefore = 100
        
        fun addRun(text: String, bold: Boolean = false) {
            val r = legendPara.createRun()
            DocxStyleUtils.applyRunFont(r)
            r.fontSize = 9
            r.isBold = bold
            r.setText(text)
        }
        
        addRun("注：")
        addRun("黄色背景", bold = true)
        addRun("表示目标年级；")
        addRun("蓝色背景", bold = true)
        addRun("表示本次测评结果。")
    }

    private fun buildHeaderRow(table: XWPFTable, headers: List<String>) {
        val row = table.createRow()
        headers.forEachIndexed { i, colHeader ->
            val cell = DocxStyleUtils.getOrCreateCell(row, i)
            DocxStyleUtils.setCellText(cell, colHeader, bold = true, fontSize = 8)
            // 紧凑内边距（2pt 上下）
            cell.paragraphs.forEach { p -> p.spacingBefore = 40; p.spacingAfter = 40 }
            DocxStyleUtils.setCellShading(cell, DocxStyleUtils.THEME_PRIMARY)
            DocxStyleUtils.setCellTextColor(cell, "FFFFFF")
            DocxStyleUtils.setCellAlignment(cell, ParagraphAlignment.CENTER)
            DocxStyleUtils.setCellBorders(cell, isHeader = true, isFirstRow = true, isLastRow = false, isFirstCol = (i == 0), isLastCol = (i == headers.size - 1))
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
            isTargetLevel -> "DAE3F4"
            isTargetGrade -> "FFF2CC"
            isGradeRow    -> "F1F1F1"
            else          -> "FFFFFF"
        }

        for (i in 0 until colCount) {
            val cellValue = rowData.getOrNull(i) ?: ""
            val cell = DocxStyleUtils.getOrCreateCell(row, i)
            DocxStyleUtils.setCellText(cell, cellValue, bold = false, fontSize = 8)
            // 紧凑内边距（2pt 上下）
            cell.paragraphs.forEach { p -> p.spacingBefore = 40; p.spacingAfter = 40 }
            DocxStyleUtils.setCellShading(cell, bgColor)
            DocxStyleUtils.setCellAlignment(cell, ParagraphAlignment.CENTER)
            DocxStyleUtils.setCellBorders(cell, isHeader = false, isFirstRow = false, isLastRow = isLastDataRow, isFirstCol = (i == 0), isLastCol = (i == colCount - 1))
        }
    }
}
