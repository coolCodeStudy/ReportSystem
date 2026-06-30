package com.example.reportsystem.service.docx

import org.apache.poi.util.Units
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy
import org.apache.poi.xwpf.usermodel.Document
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.TableRowHeightRule
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFFooter
import org.apache.poi.xwpf.usermodel.XWPFHeader
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth
import org.springframework.core.io.ClassPathResource
import java.math.BigInteger

object DocxPageBrandRenderer {
    private const val BRAND_TABLE_WIDTH = 8306L
    private const val HEADER_LOGO_CELL_WIDTH = 2500L
    private const val HEADER_TEXT_CELL_WIDTH = BRAND_TABLE_WIDTH - HEADER_LOGO_CELL_WIDTH
    private const val ADDRESS = "LINGOLAND 杭州市上城区钱江路 1366 号华润大厦 B 座 3204 室"

    fun render(document: XWPFDocument) {
        val sections = findMainContentSections(document) ?: return
        ensureHeaderFooterMargins(sections.content)

        val policy = XWPFHeaderFooterPolicy(document, sections.content)
        listOf(XWPFHeaderFooterPolicy.FIRST, XWPFHeaderFooterPolicy.DEFAULT).forEach { type ->
            populateHeader(policy.createHeader(type))
            populateFooter(policy.createFooter(type))
        }

        sections.trailing.forEach { trailingSectPr ->
            val trailingPolicy = XWPFHeaderFooterPolicy(document, trailingSectPr)
            listOf(XWPFHeaderFooterPolicy.FIRST, XWPFHeaderFooterPolicy.DEFAULT).forEach { type ->
                populateBlankHeader(trailingPolicy.createHeader(type))
                populateBlankFooter(trailingPolicy.createFooter(type))
            }
        }
    }

    private fun findMainContentSections(document: XWPFDocument): MainContentSections? {
        var inMainContent = false
        var contentSectPr: CTSectPr? = null
        val trailingSectPrs = mutableListOf<CTSectPr>()

        for (paragraph in document.paragraphs) {
            val text = paragraph.text
            if (
                text.contains("LINGOLAND国际学校课程学习方案") ||
                text.contains("测评介绍及升学目标分析") ||
                text.contains("语言教学安排") ||
                text.contains("{course_schedule}")
            ) {
                inMainContent = true
            }

            val pPr = paragraph.ctp.pPr
            if (inMainContent && pPr != null && pPr.isSetSectPr) {
                if (contentSectPr == null) {
                    contentSectPr = pPr.sectPr
                } else {
                    trailingSectPrs.add(pPr.sectPr)
                }
            }
        }

        val bodySectPr = document.document.body.sectPr
        if (contentSectPr != null && bodySectPr != null && trailingSectPrs.none { it === bodySectPr }) {
            trailingSectPrs.add(bodySectPr)
        }

        return contentSectPr?.let { MainContentSections(it, trailingSectPrs) }
    }

    private fun ensureHeaderFooterMargins(sectPr: CTSectPr) {
        val pgMar = sectPr.pgMar ?: sectPr.addNewPgMar()
        if (readDxa(pgMar.header) < 720L) {
            pgMar.header = BigInteger.valueOf(720L)
        }
        if (readDxa(pgMar.footer) < 720L) {
            pgMar.footer = BigInteger.valueOf(720L)
        }
    }

    private fun readDxa(value: Any?): Long {
        return when (value) {
            is BigInteger -> value.toLong()
            is Number -> value.toLong()
            else -> value?.toString()?.toLongOrNull() ?: 0L
        }
    }

    private fun populateHeader(header: XWPFHeader) {
        header.clearHeaderFooter()

        val table = header.createTable(2, 2)
        configureBrandTable(table)
        table.setCellMargins(0, 0, 0, 0)

        val topRow = table.getRow(0)
        topRow.height = 640
        topRow.heightRule = TableRowHeightRule.AT_LEAST

        val logoCell = topRow.getCell(0)
        configureCell(logoCell, HEADER_LOGO_CELL_WIDTH)
        addLogo(logoCell)

        val titleCell = topRow.getCell(1)
        configureCell(titleCell, HEADER_TEXT_CELL_WIDTH)
        addHeaderTitle(titleCell)

        val lineRow = table.getRow(1)
        lineRow.height = 80
        lineRow.heightRule = TableRowHeightRule.EXACT
        configureCell(lineRow.getCell(0), HEADER_LOGO_CELL_WIDTH)
        val lineCell = lineRow.getCell(1)
        configureCell(lineCell, HEADER_TEXT_CELL_WIDTH)
        DocxStyleUtils.setCellShading(lineCell, DocxStyleUtils.THEME_PRIMARY)
    }

    private fun populateFooter(footer: XWPFFooter) {
        footer.clearHeaderFooter()

        val p = footer.createParagraph()
        p.alignment = ParagraphAlignment.LEFT
        p.spacingBefore = 0
        p.spacingAfter = 0

        val square = p.createRun()
        applyBrandRun(square, 8, bold = true)
        square.color = DocxStyleUtils.THEME_PRIMARY
        square.setText("■ ")

        val text = p.createRun()
        applyBrandRun(text, 8, bold = true)
        text.color = "666666"
        text.setText(ADDRESS)
    }

    private fun populateBlankHeader(header: XWPFHeader) {
        header.clearHeaderFooter()
        header.createParagraph()
    }

    private fun populateBlankFooter(footer: XWPFFooter) {
        footer.clearHeaderFooter()
        footer.createParagraph()
    }

    private fun configureBrandTable(table: XWPFTable) {
        table.removeBorders()
        val tblPr = table.ctTbl.tblPr ?: table.ctTbl.addNewTblPr()
        val tblW = tblPr.tblW ?: tblPr.addNewTblW()
        tblW.type = STTblWidth.DXA
        tblW.w = BigInteger.valueOf(BRAND_TABLE_WIDTH)

        val tblLayout = tblPr.tblLayout ?: tblPr.addNewTblLayout()
        tblLayout.type = STTblLayoutType.FIXED
    }

    private fun configureCell(cell: XWPFTableCell, width: Long) {
        DocxStyleUtils.setCellWidth(cell, width)
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER)
        cell.paragraphs.forEach { paragraph ->
            paragraph.spacingBefore = 0
            paragraph.spacingAfter = 0
        }
    }

    private fun addLogo(cell: XWPFTableCell) {
        val paragraph = cell.paragraphs.firstOrNull() ?: cell.addParagraph()
        paragraph.alignment = ParagraphAlignment.LEFT
        val run = paragraph.createRun()
        val logo = ClassPathResource("static/images/lingoland_logo.jpg")
        if (logo.exists()) {
            logo.inputStream.use { input ->
                run.addPicture(
                    input,
                    Document.PICTURE_TYPE_JPEG,
                    "lingoland_logo.jpg",
                    Units.toEMU(44.0),
                    Units.toEMU(44.0)
                )
            }
        } else {
            applyBrandRun(run, 12, bold = true)
            run.color = DocxStyleUtils.THEME_PRIMARY
            run.setText("LINGOLAND")
        }
    }

    private fun addHeaderTitle(cell: XWPFTableCell) {
        val paragraph = cell.paragraphs.firstOrNull() ?: cell.addParagraph()
        paragraph.alignment = ParagraphAlignment.RIGHT
        paragraph.spacingBefore = 0
        paragraph.spacingAfter = 0

        val cn = paragraph.createRun()
        applyBrandRun(cn, 11, bold = true)
        cn.color = DocxStyleUtils.THEME_PRIMARY
        cn.setText("LINGOLAND 国际学校课程学习方案")
        cn.addBreak()

        val en = paragraph.createRun()
        applyBrandRun(en, 8, bold = true)
        en.color = DocxStyleUtils.THEME_PRIMARY
        en.setText("ENGLISH ASSESSMENT AND STUDY PLAN")
    }

    private fun applyBrandRun(run: XWPFRun, fontSize: Int, bold: Boolean) {
        DocxStyleUtils.applyRunFont(run)
        run.fontSize = fontSize
        run.isBold = bold
    }

    private data class MainContentSections(
        val content: CTSectPr,
        val trailing: List<CTSectPr>
    )
}
