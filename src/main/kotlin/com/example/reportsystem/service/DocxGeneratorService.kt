package com.example.reportsystem.service

import org.apache.poi.xwpf.usermodel.*
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.math.BigInteger

// ─── Data model ────────────────────────────────────────────────────────────────

/** One row of the 升学目标分析 table. */
data class RowData(
    val lingoland: String,
    val cefr: String,
    // 固定列（始终显示）
    val lansi: String,      // 蓝思值
    val cihuiLiang: String, // 词汇量
    val cambridge: String,  // 剑桥系考试
    val toeflJr: String,    // TOEFL Junior
    val toefl: String,      // 托福
    val ielts: String,      // 雅思
    // 国际学校 / 体制内转国际（动态）
    val int1: String,   // 第一梯队国际
    val int2: String,   // 第二梯队国际
    // 体制内转HKDSE（动态）
    val dse1a: String,  // 香港DSE Band 1A
    val dse1bc: String, // 香港DSE Band 1B-1C
    val dse2: String,   // 香港DSE Band 2
    // 体制内转杭州国际学校（动态）
    val wbs: String,    // WBS前20%
    val beisaisi: String, // 贝赛思
    val mapScore: String, // Map (83%-86%)
    val huli: String,   // 惠立
    val his: String,    // HIS
    val hangwai: String, // 杭外剑高
    // 体制内（动态）
    val zj: String,     // 浙江高考
    val sh: String      // 上海高考
)

data class ColConfig(val header: String, val getValue: (RowData) -> String)

// ─── Row data ───────────────────────────────────────────────────────────────────

private val ALL_ROWS: List<RowData> = listOf(
    //                  Lingo   CEFR     蓝思值     词汇量     剑桥考试    TOEFL Jr  托福    雅思     int1  int2   dse1a  dse1bc  dse2   wbs    贝赛思  map    惠立    HIS    杭外     浙江       上海
    // K row (low-age only)
    RowData("K",   "Pre-A1", "160L",   "400",    "Starters", "",      "",     "",      "K",   "",    "G1",  "",    "",    "G2",  "",    "",    "G1",  "",    "G2",  "",       ""),
    RowData("G1",  "A1",     "165L",   "800",    "Movers",   "",      "",     "",      "G1",  "G3",  "G2",  "G5",  "G7",  "G3",  "K",   "190", "G2",  "G3",  "G3",  "",       ""),
    RowData("G2",  "A2-",    "425L",   "1100",   "Flyers",   "",      "",     "",      "G2",  "G4",  "G3",  "G6",  "G8",  "G4",  "G1",  "204", "G3",  "G4",  "G4",  "",       ""),
    RowData("G3",  "A2+",    "600L",   "1500",   "KET",      "625",   "",     "3",     "G3",  "G5",  "G4",  "G7",  "G9",  "G5",  "G2",  "214", "G4",  "G5",  "G5",  "",       ""),
    RowData("G4",  "B1-",    "725L",   "2500",   "PET",      "725",   "31",   "4",     "G4",  "G6",  "G5",  "G8",  "G10", "G6",  "G3",  "220", "G5",  "G6",  "G6",  "中考近满分", ""),
    RowData("G5",  "B1+",    "825L",   "3500",   "PET",      "785",   "45",   "5",     "G5",  "G7",  "G6",  "G9",  "G11", "G7",  "G4",  "226", "G6",  "G7",  "G7",  "",       "中考近满分"),
    RowData("G6",  "B2-",    "925L",   "4500",   "PET",      "860",   "66",   "5.5",   "G6",  "G8",  "G7",  "G10", "G12", "G8",  "G5",  "230", "G7",  "G8",  "G8",  "",       ""),
    RowData("G7",  "B2+",    "1000L",  "6000",   "FCE",      "865",   "93",   "6.5",   "G7",  "G9",  "G8",  "G11", "",    "G9",  "G6",  "236", "G8",  "G9",  "G9",  "高考140",  ""),
    RowData("G8",  "C1-",    "1050L",  "7500",   "FCE",      "900",   "101",  "7",     "G8",  "G10", "G9",  "G12", "",    "G10", "G7",  "238", "G9",  "G10", "G10", "",       "高考140"),
    RowData("G9",  "C1+",    "1125L",  "10000",  "CAE",      "",      "109",  "7.5",   "G9",  "",    "G10", "",    "",    "G11", "G8",  "",    "G10", "G11", "G11", "",       ""),
    RowData("G10", "",       "1175L",  "15000",  "CAE",      "",      "120",  "8",     "G10", "",    "G11", "",    "",    "",    "G9",  "",    "G11", "",    "",    "",       ""),
    RowData("G11", "",       "1225L",  "17500",  "",         "",      "",     "",      "G11", "",    "",    "",    "",    "",    "",    "",    "",    "",    "",    "",       ""),
    RowData("",    "",       "1250L",  "20000",  "",         "",      "",     "",      "",    "",    "",    "",    "",    "",    "",    "",    "",    "",    "",    "",       "")
)

// ─── Column definitions ────────────────────────────────────────────────────────

private val FIXED_COLS = listOf(
    ColConfig("Lingoland") { it.lingoland },
    ColConfig("CEFR") { it.cefr },
    ColConfig("蓝思值") { it.lansi },
    ColConfig("词汇量") { it.cihuiLiang },
    ColConfig("剑桥系考试") { it.cambridge },
    ColConfig("TOEFL Junior") { it.toeflJr },
    ColConfig("托福") { it.toefl },
    ColConfig("雅思") { it.ielts }
)

private val INT_COLS = listOf(
    ColConfig("第一梯队国际") { it.int1 },
    ColConfig("第二梯队国际") { it.int2 }
)

private val HKDSE_COLS = listOf(
    ColConfig("香港DSE Band 1A") { it.dse1a },
    ColConfig("香港DSE Band 1B-1C") { it.dse1bc },
    ColConfig("香港DSE Band 2") { it.dse2 }
)

private val HZ_INT_COLS = listOf(
    ColConfig("WBS前20%") { it.wbs },
    ColConfig("贝赛思") { it.beisaisi },
    ColConfig("Map (83%-86%)") { it.mapScore },
    ColConfig("惠立") { it.huli },
    ColConfig("HIS") { it.his },
    ColConfig("杭外剑高") { it.hangwai }
)

private val DOMESTIC_COLS = listOf(
    ColConfig("浙江高考") { it.zj },
    ColConfig("上海高考") { it.sh }
)

// ─── Low-age assessment types ──────────────────────────────────────────────────

private val LOW_AGE_TYPES = setOf("starters", "movers", "flyers")

// ─── Service ───────────────────────────────────────────────────────────────────

@Service
class DocxGeneratorService {

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

        // (Floating boxes should ideally be removed directly from the DOCX template)

        // ── Step 2: Determine columns and rows ─────────────────────────────
        val dynamicCols: List<ColConfig> = when (studentType) {
            "INTL", "TRANSITION_INTL" -> INT_COLS
            "TRANSITION_HKDSE"       -> HKDSE_COLS
            "TRANSITION_HANGZHOU_INTL" -> HZ_INT_COLS
            "DOMESTIC"              -> DOMESTIC_COLS
            else                  -> emptyList()
        }
        val allCols = FIXED_COLS + dynamicCols

        val showKRow = assessmentTypes?.any { it.trim().lowercase() in LOW_AGE_TYPES } ?: false
        val rows = if (showKRow) ALL_ROWS else ALL_ROWS.filter { it.lingoland != "K" }

        // ── Step 3: Clear existing table rows (use high-level API to keep POI state in sync)
        for (i in table.numberOfRows - 1 downTo 0) {
            table.removeRow(i)
        }

        // ── Step 4: Build new rows ─────────────────────────────────────────
        buildHeaderRow(table, allCols)
        rows.forEachIndexed { idx, rowData ->
            buildDataRow(table, allCols, rowData, targetLevel, targetGrade, isLastDataRow = idx == rows.size - 1)
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

    private fun buildHeaderRow(table: XWPFTable, cols: List<ColConfig>) {
        val row = table.createRow()
        cols.forEachIndexed { i, col ->
            val cell = getOrCreateCell(row, i)
            setCellText(cell, col.header, bold = true)
            setCellShading(cell, "4472C4")
            setCellTextColor(cell, "FFFFFF")
            setCellAlignment(cell, ParagraphAlignment.CENTER)
            setCellBorders(cell, isHeader = true, isFirstRow = true, isLastRow = false, isFirstCol = (i == 0), isLastCol = (i == cols.size - 1))
        }
    }

    private fun buildDataRow(
        table: XWPFTable,
        cols: List<ColConfig>,
        rowData: RowData,
        targetLevel: String?,
        targetGrade: String?,
        isLastDataRow: Boolean
    ) {
        val row = table.createRow()
        val lingoland = rowData.lingoland

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

        cols.forEachIndexed { i, col ->
            val cell = getOrCreateCell(row, i)
            setCellText(cell, col.getValue(rowData), bold = false)
            setCellShading(cell, bgColor)
            setCellAlignment(cell, ParagraphAlignment.CENTER)
            setCellBorders(cell, isHeader = false, isFirstRow = false, isLastRow = isLastDataRow, isFirstCol = (i == 0), isLastCol = (i == cols.size - 1))
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
