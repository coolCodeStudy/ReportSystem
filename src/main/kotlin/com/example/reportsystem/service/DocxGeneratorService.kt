package com.example.reportsystem.service

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream

@Service
class DocxGeneratorService {

    fun generateDocx(targetLevel: String?, targetGrade: String?): ByteArray {
        val resource = ClassPathResource("static/Lingoland学习方案.docx")
        val document = XWPFDocument(resource.inputStream)

        // Iterate over all tables in the document
        if (document.tables.isNotEmpty()) {
            val table = document.tables[0]
            var tingshuoRunObj: org.apache.poi.xwpf.usermodel.XWPFRun? = null
            var tingshuoPara: org.apache.poi.xwpf.usermodel.XWPFParagraph? = null
            var suozaiRunObj: org.apache.poi.xwpf.usermodel.XWPFRun? = null
            var suozaiPara: org.apache.poi.xwpf.usermodel.XWPFParagraph? = null

            // Isolate the floating drawing textboxes from the entire document
            for (p in document.paragraphs) {
                for (r in p.runs) {
                    if (r.ctr.xmlText().contains("听说读写")) {
                        tingshuoRunObj = r; tingshuoPara = p
                    }
                }
            }
            for (t in document.tables) {
                for (row in t.rows) {
                    for (cell in row.tableCells) {
                        for (p in cell.paragraphs) {
                            for (r in p.runs) {
                                if (r.ctr.xmlText().contains("所在年级")) {
                                    suozaiRunObj = r; suozaiPara = p
                                }
                            }
                        }
                    }
                }
            }

            var tingshuoTargetRowIndex = -1
            var suozaiTargetRowIndex = -1

            for ((index, row) in table.rows.withIndex()) {
                val firstCellText = row.getCell(0)?.text?.trim() ?: continue
                
                val isTargetLevel = targetLevel != null && firstCellText.equals(targetLevel, ignoreCase = true)
                val isTargetGrade = targetGrade != null && (firstCellText.equals(targetGrade, ignoreCase = true) || firstCellText.equals("G" + targetGrade.replace("年级", "").replace("初一", "7").replace("初二", "8").replace("初三", "9").replace("高一", "10"), ignoreCase = true))

                if (isTargetLevel) {
                    tingshuoTargetRowIndex = index

                    for (cell in row.tableCells) {
                        var tcPr = cell.ctTc.tcPr
                        if (tcPr == null) tcPr = cell.ctTc.addNewTcPr()
                        var shd = tcPr.shd
                        if (shd == null) shd = tcPr.addNewShd()
                        shd.`val` = org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd.CLEAR
                        shd.color = "auto"
                        shd.fill = "DAE3F4" // Pastel Blue
                        if (shd.isSetThemeFill) shd.unsetThemeFill()
                        if (shd.isSetThemeFillShade) shd.unsetThemeFillShade()
                        if (shd.isSetThemeFillTint) shd.unsetThemeFillTint()
                    }
                } else if (isTargetGrade) {
                    suozaiTargetRowIndex = index

                     for (cell in row.tableCells) {
                        var tcPr = cell.ctTc.tcPr
                        if (tcPr == null) tcPr = cell.ctTc.addNewTcPr()
                        var shd = tcPr.shd
                        if (shd == null) shd = tcPr.addNewShd()
                        shd.`val` = org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd.CLEAR
                        shd.color = "auto"
                        shd.fill = "FFF2CC" // Pastel Yellow
                        if (shd.isSetThemeFill) shd.unsetThemeFill()
                        if (shd.isSetThemeFillShade) shd.unsetThemeFillShade()
                        if (shd.isSetThemeFillTint) shd.unsetThemeFillTint()
                     }
                } else {
                    // For any other legitimate row in this grading table, revert the color to default gray
                    if (firstCellText.matches(Regex("^(K|G[1-9]|G1[0-2])$"))) {
                         for (cell in row.tableCells) {
                            var tcPr = cell.ctTc.tcPr
                            if (tcPr == null) tcPr = cell.ctTc.addNewTcPr()
                            var shd = tcPr.shd
                            if (shd == null) shd = tcPr.addNewShd()
                            shd.`val` = org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd.CLEAR
                            shd.color = "auto"
                            shd.fill = "F1F1F1" // Default Gray
                            if (shd.isSetThemeFill) shd.unsetThemeFill()
                            if (shd.isSetThemeFillShade) shd.unsetThemeFillShade()
                            if (shd.isSetThemeFillTint) shd.unsetThemeFillTint()
                         }
                    }
                }
            }

            // Both labels should have identical left-edge extensions and span the exact same width 
            // to appear perfectly aligned vertically. 
            // The blue label "听说读写" originally had -1045845 EMUs column-relative offset. We sync both to this value.
            val unifiedColumnOffsetX = "-1045845"

            fun moveRunInsideCell(run: org.apache.poi.xwpf.usermodel.XWPFRun, sourcePara: org.apache.poi.xwpf.usermodel.XWPFParagraph, targetPara: org.apache.poi.xwpf.usermodel.XWPFParagraph, newXOffsetEmu: String) {
                var ctrXml = run.ctr.xmlText()
                
                // Stop the drawing from stretching the table cell
                ctrXml = ctrXml.replace(Regex("""layoutInCell="1""""), """layoutInCell="0"""")

                // Apply identical relative column horizontal offset to eliminate staggered outer gaps
                ctrXml = ctrXml.replace(Regex("""<wp:positionH relativeFrom="column">\s*<wp:posOffset>-?\d+</wp:posOffset>\s*</wp:positionH>"""),
                    """<wp:positionH relativeFrom="column"><wp:posOffset>$newXOffsetEmu</wp:posOffset></wp:positionH>""")
                    
                // Align vertically directly to the top of the target table cell's paragraph
                ctrXml = ctrXml.replace(Regex("""<wp:positionV relativeFrom="paragraph">\s*<wp:posOffset>-?\d+</wp:posOffset>\s*</wp:positionV>"""),
                    """<wp:positionV relativeFrom="paragraph"><wp:posOffset>0</wp:posOffset></wp:positionV>""")
                    
                // Also zero out VML markup shifts just in case
                ctrXml = ctrXml.replace(Regex("""margin-left:-?\d+(\.\d+)?pt;"""), "margin-left:0pt;")
                ctrXml = ctrXml.replace(Regex("""margin-top:-?\d+(\.\d+)?pt;"""), "margin-top:0pt;")

                val newCtr = org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR.Factory.parse(ctrXml)
                val newRun = targetPara.createRun()
                newRun.ctr.set(newCtr)

                sourcePara.removeRun(sourcePara.runs.indexOf(run))
            }

            if (tingshuoRunObj != null && tingshuoPara != null && tingshuoTargetRowIndex != -1) {
                val targetCellPara = table.rows[tingshuoTargetRowIndex].getCell(0)?.paragraphs?.firstOrNull() ?: table.rows[tingshuoTargetRowIndex].getCell(0)?.addParagraph()
                if (targetCellPara != null) moveRunInsideCell(tingshuoRunObj, tingshuoPara, targetCellPara, unifiedColumnOffsetX)
            }
            if (suozaiRunObj != null && suozaiPara != null && suozaiTargetRowIndex != -1) {
                val targetCellPara = table.rows[suozaiTargetRowIndex].getCell(0)?.paragraphs?.firstOrNull() ?: table.rows[suozaiTargetRowIndex].getCell(0)?.addParagraph()
                if (targetCellPara != null) moveRunInsideCell(suozaiRunObj, suozaiPara, targetCellPara, unifiedColumnOffsetX)
            }
        }

        val out = ByteArrayOutputStream()
        document.write(out)
        val modifiedBytes = out.toByteArray()
        document.close()

        return modifiedBytes
    }
}
