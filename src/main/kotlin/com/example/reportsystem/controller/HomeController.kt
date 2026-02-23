package com.example.reportsystem.controller

import com.example.reportsystem.service.PdfService
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayOutputStream

@Controller
class HomeController(private val pdfService: PdfService) {

    @GetMapping("/")
    fun home(): String {
        return "index"
    }

    @GetMapping("/report/pdf")
    fun downloadPdf(): ResponseEntity<ByteArray> {
        val pdfBytes = pdfService.generateReport()

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=lingoland_report.pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdfBytes)
    }

    @RequestMapping(value = ["/report/docx"], method = [org.springframework.web.bind.annotation.RequestMethod.GET, org.springframework.web.bind.annotation.RequestMethod.POST])
    fun downloadDocx(form: UserReportForm?): ResponseEntity<ByteArray> {
        val resource = ClassPathResource("static/Lingoland学习方案.docx")

        // Read the DOCX file and process it
        val document = XWPFDocument(resource.inputStream)

        if (form != null) {
            val targetLevel = form.lingolandLevel
            val targetGrade = form.grade

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
                        """<wp:positionH relativeFrom="column"><wp:posOffset>\$newXOffsetEmu</wp:posOffset></wp:positionH>""")
                        
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
        }

        // Save the modified document to a ByteArrayOutputStream
        val out = ByteArrayOutputStream()
        document.write(out)
        val modifiedBytes = out.toByteArray()
        document.close()

        val mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")

        val contentDisposition = org.springframework.http.ContentDisposition.attachment()
            .filename("Lingoland学习方案.docx", java.nio.charset.StandardCharsets.UTF_8)
            .build()

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
            .header(HttpHeaders.CONTENT_LENGTH, modifiedBytes.size.toString())
            .contentType(mediaType)
            .body(modifiedBytes)
    }
}

data class UserReportForm(
    val name: String? = null,
    val age: Int? = null,
    val gender: String? = null,
    val school: String? = null,
    val grade: String? = null,
    val studentType: String? = null,
    val lingolandLevel: String? = null,
    val studyGoal: String? = null,
    val assessmentType: List<String>? = null,
    val otherAssessment: String? = null
)
