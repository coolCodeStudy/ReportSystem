package com.example.reportsystem.service.docx

import org.apache.poi.xwpf.usermodel.*
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*
import java.math.BigInteger

object DocxStyleUtils {

    // ── 字体系统 ──────────────────────────────────────────────
    const val FONT_MAIN = "华文宋体"       // 全文唯一正文字体

    // ── 间距系统（单位：twentieths of a point，即 1pt = 20）────
    // 三档固定间距，杜绝"6种间距并存"的混乱
    const val SPACING_BODY    = 100  //  5pt — 正文段后
    const val SPACING_SECTION = 240  // 12pt — ✦ 小节标题前后
    const val SPACING_MAJOR   = 360  // 18pt — 一/二/三 大节标题前

    // ── 品牌色值定义 ─────────────────────────────────────────
    val THEME_PRIMARY  = "003277"     // 导出表格主色
    val THEME_BG_LIGHT = "F8F9FA"     // 极浅灰底（斑马纹白）
    val THEME_BG_DARK  = "F1F3F5"     // 稍深灰底（斑马纹灰）
    val THEME_ACCENT   = "ED7D31"     // 重点强调色（橙）
    val BORDER_GREY    = "E9ECEF"     // 浅灰内边框

    fun getOrCreateCell(row: XWPFTableRow, index: Int): XWPFTableCell {
        return row.getCell(index) ?: row.addNewTableCell()
    }

    fun applyRunFont(run: XWPFRun) {
        run.fontFamily = FONT_MAIN
        val rPr = run.ctr.rPr ?: run.ctr.addNewRPr()
        val fonts = if (rPr.sizeOfRFontsArray() > 0) rPr.getRFontsArray(0) else rPr.addNewRFonts()
        fonts.ascii = FONT_MAIN
        fonts.hAnsi = FONT_MAIN
        fonts.eastAsia = FONT_MAIN
        fonts.cs = FONT_MAIN
    }

    fun applyDocumentFont(document: XWPFDocument) {
        document.paragraphs.forEach { applyParagraphFont(it) }
        document.tables.forEach { applyTableFont(it) }
        document.headerList.forEach { header ->
            header.paragraphs.forEach { applyParagraphFont(it) }
            header.tables.forEach { applyTableFont(it) }
        }
        document.footerList.forEach { footer ->
            footer.paragraphs.forEach { applyParagraphFont(it) }
            footer.tables.forEach { applyTableFont(it) }
        }
    }

    private fun applyTableFont(table: XWPFTable) {
        table.rows.forEach { row ->
            row.tableCells.forEach { cell ->
                cell.paragraphs.forEach { applyParagraphFont(it) }
                cell.tables.forEach { applyTableFont(it) }
            }
        }
    }

    private fun applyParagraphFont(paragraph: XWPFParagraph) {
        paragraph.runs.forEach { applyRunFont(it) }
    }

    fun setCellText(cell: XWPFTableCell, text: String, bold: Boolean = false, color: String? = null, fontSize: Int = 10) {
        val para = cell.paragraphs.firstOrNull() ?: cell.addParagraph()
        
        // 赋予表内呼吸感行距
        para.spacingBefore = 60
        para.spacingAfter = 60
        // Set basic line spacing equivalent to ~1.2
        if (para.ctp.pPr == null) para.ctp.addNewPPr()
        if (para.ctp.pPr.spacing == null) para.ctp.pPr.addNewSpacing()
        para.ctp.pPr.spacing.line = BigInteger.valueOf(280) // ~14pt 行高，完美契合 10pt/9pt 字体
        para.ctp.pPr.spacing.lineRule = org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule.EXACT

        para.runs.forEach { it.setText("", 0) }
        val run = if (para.runs.isEmpty()) para.createRun() else para.runs[0]
        
        val lines = normalizeLineBreaks(text).split("\n")
        lines.forEachIndexed { index, line ->
            run.setText(line)
            if (index < lines.size - 1) {
                run.addBreak()
            }
        }
        
        run.isBold = bold
        run.fontSize = fontSize
        applyRunFont(run)
        if (color != null) {
            run.setColor(color)
        }
    }

    private fun normalizeLineBreaks(text: String): String {
        return text.replace("\r\n", "\n").replace("\r", "\n")
    }

    fun setWhiteBorders(cell: XWPFTableCell) {
        val tcPr = cell.ctTc.tcPr ?: cell.ctTc.addNewTcPr()
        val tcBorders = tcPr.tcBorders ?: tcPr.addNewTcBorders()

        // 使用极品灰或透白边框，消除黑线
        fun setW(border: org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder) {
            border.`val` = org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE
            border.color = "FFFFFF"
            border.sz = BigInteger.valueOf(8)
            border.space = BigInteger.ZERO
        }

        setW(tcBorders.top ?: tcBorders.addNewTop())
        setW(tcBorders.bottom ?: tcBorders.addNewBottom())
        setW(tcBorders.left ?: tcBorders.addNewLeft())
        setW(tcBorders.right ?: tcBorders.addNewRight())
    }

    fun setZebraBorders(cell: XWPFTableCell, isHeader: Boolean = false, isLast: Boolean = false) {
        val tcPr = cell.ctTc.tcPr ?: cell.ctTc.addNewTcPr()
        val tcBorders = tcPr.tcBorders ?: tcPr.addNewTcBorders()

        fun setBorder(border: org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder, colorStr: String, size: Long = 4) {
            border.`val` = org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE
            border.color = colorStr
            border.sz = BigInteger.valueOf(size)
            border.space = BigInteger.ZERO
        }
        
        fun removeBorder(border: org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder) {
            border.`val` = org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NONE
            border.sz = BigInteger.ZERO
            border.space = BigInteger.ZERO
        }

        removeBorder(tcBorders.left ?: tcBorders.addNewLeft())
        removeBorder(tcBorders.right ?: tcBorders.addNewRight())

        if (isHeader) {
            setBorder(tcBorders.top ?: tcBorders.addNewTop(), THEME_PRIMARY, 12)
            setBorder(tcBorders.bottom ?: tcBorders.addNewBottom(), THEME_PRIMARY, 12)
        } else {
            setBorder(tcBorders.top ?: tcBorders.addNewTop(), BORDER_GREY, 4)
            if (isLast) {
                setBorder(tcBorders.bottom ?: tcBorders.addNewBottom(), BORDER_GREY, 8)
            } else {
                setBorder(tcBorders.bottom ?: tcBorders.addNewBottom(), BORDER_GREY, 4)
            }
        }
    }

    fun setCellShading(cell: XWPFTableCell, hexColor: String) {
        val tcPr = cell.ctTc.tcPr ?: cell.ctTc.addNewTcPr()
        val shd = tcPr.shd ?: tcPr.addNewShd()
        shd.`val` = STShd.CLEAR
        shd.color = "auto"
        shd.fill = hexColor
        if (shd.isSetThemeFill) shd.unsetThemeFill()
        if (shd.isSetThemeFillShade) shd.unsetThemeFillShade()
        if (shd.isSetThemeFillTint) shd.unsetThemeFillTint()
    }

    fun setCellTextColor(cell: XWPFTableCell, hexColor: String) {
        cell.paragraphs.forEach { p -> p.runs.forEach { r -> r.setColor(hexColor) } }
    }

    fun setCellAlignment(cell: XWPFTableCell, alignment: ParagraphAlignment) {
        cell.paragraphs.forEach { it.alignment = alignment }
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER)
    }

    fun setCellWidth(cell: XWPFTableCell, width: Long) {
        val tcPr = cell.ctTc.tcPr ?: cell.ctTc.addNewTcPr()
        val tcW = tcPr.tcW ?: tcPr.addNewTcW()
        tcW.type = STTblWidth.DXA
        tcW.w = BigInteger.valueOf(width)
    }

    fun keepTableRowsTogether(table: XWPFTable) {
        table.rows.forEachIndexed { rowIndex, row ->
            val trPr = row.ctRow.trPr ?: row.ctRow.addNewTrPr()
            if (trPr.sizeOfCantSplitArray() == 0) {
                trPr.addNewCantSplit()
            }
            if (rowIndex < table.rows.size - 1) {
                row.tableCells.forEach { cell ->
                    cell.paragraphs.forEach { paragraph ->
                        val pPr = paragraph.ctp.pPr ?: paragraph.ctp.addNewPPr()
                        if (!pPr.isSetKeepNext) {
                            pPr.addNewKeepNext()
                        }
                    }
                }
            }
        }
    }

    fun setCellBorders(cell: XWPFTableCell, isHeader: Boolean, isFirstRow: Boolean, isLastRow: Boolean, isFirstCol: Boolean, isLastCol: Boolean) {
        val tcPr = cell.ctTc.tcPr ?: cell.ctTc.addNewTcPr()
        val tcBorders = tcPr.tcBorders ?: tcPr.addNewTcBorders()

        val top = tcBorders.top ?: tcBorders.addNewTop()
        val left = tcBorders.left ?: tcBorders.addNewLeft()
        val bottom = tcBorders.bottom ?: tcBorders.addNewBottom()
        val right = tcBorders.right ?: tcBorders.addNewRight()

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

        if (isFirstRow) setB(top, org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.DOUBLE, "000000")
        if (isLastRow) setB(bottom, org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.DOUBLE, "000000")
        if (isFirstCol) setB(left, org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.DOUBLE, "000000")
        if (isLastCol) setB(right, org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.DOUBLE, "000000")
    }
}
