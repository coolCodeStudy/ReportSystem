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
            val globalCsvConfig = systemConfigRepository.findByConfigKey("GLOBAL_CAPABILITY_MATRIX_CSV")?.configValue
            com.example.reportsystem.service.docx.DocxCapabilityMatrixRenderer.render(
                document, targetLevel, targetGrade, studentType, assessmentTypes, selectedColumns, globalCsvConfig
            )
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
                        val cursor = lastPara.getCTP().newCursor()
                        cursor.toNextSibling()
                        val newPara = document.insertNewParagraph(cursor)
                        if (targetPara.getCTP().pPr != null) {
                            newPara.getCTP().pPr = targetPara.getCTP().pPr.copy() as org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr
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
                val titleRun = targetPara.createRun()
                titleRun.fontFamily = "微软雅黑"
                titleRun.fontSize = 10
                titleRun.isBold = true
                titleRun.setText("测评说明：")
            }
        }

        // --- Append Assessment Analysis Tables ---
        com.example.reportsystem.service.docx.DocxAssessmentAnalysisRenderer.render(document, assessmentResultsJson)

        val out = ByteArrayOutputStream()
        document.write(out)
        val bytes = out.toByteArray()
        document.close()
        return bytes
    }
}


