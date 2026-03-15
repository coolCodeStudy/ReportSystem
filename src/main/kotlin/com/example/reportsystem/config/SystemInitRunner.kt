package com.example.reportsystem.config

import com.example.reportsystem.entity.StudentTypeDictionary
import com.example.reportsystem.repository.StudentTypeDictionaryRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

private data class SeedRowData(
    val lingoland: String, val cefr: String, val lansi: String, val cihuiLiang: String,
    val cambridge: String, val toeflJr: String, val toefl: String, val ielts: String,
    val int1: String, val int2: String,
    val dse1a: String, val dse1bc: String, val dse2: String,
    val wbs: String, val beisaisi: String, val mapScore: String, val huli: String, val his: String, val hangwai: String,
    val zj: String, val sh: String
)

private val SEED_ROWS = listOf(
    SeedRowData("K", "Pre-A1", "160L", "400", "Starters", "", "", "", "K", "", "G1", "", "", "G2", "", "", "G1", "", "G2", "", ""),
    SeedRowData("G1", "A1", "165L", "800", "Movers", "", "", "", "G1", "G3", "G2", "G5", "G7", "G3", "K", "190", "G2", "G3", "G3", "", ""),
    SeedRowData("G2", "A2-", "425L", "1100", "Flyers", "", "", "", "G2", "G4", "G3", "G6", "G8", "G4", "G1", "204", "G3", "G4", "G4", "", ""),
    SeedRowData("G3", "A2+", "600L", "1500", "KET", "625", "", "3", "G3", "G5", "G4", "G7", "G9", "G5", "G2", "214", "G4", "G5", "G5", "", ""),
    SeedRowData("G4", "B1-", "725L", "2500", "PET", "725", "31", "4", "G4", "G6", "G5", "G8", "G10", "G6", "G3", "220", "G5", "G6", "G6", "中考近满分", ""),
    SeedRowData("G5", "B1+", "825L", "3500", "PET", "785", "45", "5", "G5", "G7", "G6", "G9", "G11", "G7", "G4", "226", "G6", "G7", "G7", "", "中考近满分"),
    SeedRowData("G6", "B2-", "925L", "4500", "PET", "860", "66", "5.5", "G6", "G8", "G7", "G10", "G12", "G8", "G5", "230", "G7", "G8", "G8", "", ""),
    SeedRowData("G7", "B2+", "1000L", "6000", "FCE", "865", "93", "6.5", "G7", "G9", "G8", "G11", "", "G9", "G6", "236", "G8", "G9", "G9", "高考140", ""),
    SeedRowData("G8", "C1-", "1050L", "7500", "FCE", "900", "101", "7", "G8", "G10", "G9", "G12", "", "G10", "G7", "238", "G9", "G10", "G10", "", "高考140"),
    SeedRowData("G9", "C1+", "1125L", "10000", "CAE", "", "109", "7.5", "G9", "", "G10", "", "", "G11", "G8", "", "G10", "G11", "G11", "", ""),
    SeedRowData("G10", "", "1175L", "15000", "CAE", "", "120", "8", "G10", "", "G11", "", "", "", "G9", "", "G11", "", "", "", ""),
    SeedRowData("G11", "", "1225L", "17500", "", "", "", "", "G11", "", "", "", "", "", "", "", "", "", "", "", ""),
    SeedRowData("", "", "1250L", "20000", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "")
)

private data class SeedColConfig(val header: String, val getValue: (SeedRowData) -> String)

private val FIXED_COLS = listOf(
    SeedColConfig("Lingoland") { it.lingoland }, SeedColConfig("CEFR") { it.cefr }, SeedColConfig("蓝思值") { it.lansi },
    SeedColConfig("词汇量") { it.cihuiLiang }, SeedColConfig("剑桥系考试") { it.cambridge }, SeedColConfig("TOEFL Junior") { it.toeflJr },
    SeedColConfig("托福") { it.toefl }, SeedColConfig("雅思") { it.ielts }
)

@Component
class SystemInitRunner(
    private val studentTypeDictionaryRepository: StudentTypeDictionaryRepository
) : CommandLineRunner {

    private fun generateCsvForType(typeCode: String): String {
        val dynamicCols = when (typeCode) {
            "INTL", "TRANSITION_INTL" -> listOf(SeedColConfig("第一梯队国际") { it.int1 }, SeedColConfig("第二梯队国际") { it.int2 })
            "TRANSITION_HKDSE" -> listOf(SeedColConfig("香港DSE Band 1A") { it.dse1a }, SeedColConfig("香港DSE Band 1B-1C") { it.dse1bc }, SeedColConfig("香港DSE Band 2") { it.dse2 })
            "TRANSITION_HANGZHOU_INTL" -> listOf(SeedColConfig("WBS前20%") { it.wbs }, SeedColConfig("贝赛思") { it.beisaisi }, SeedColConfig("Map (83%-86%)") { it.mapScore }, SeedColConfig("惠立") { it.huli }, SeedColConfig("HIS") { it.his }, SeedColConfig("杭外剑高") { it.hangwai })
            "DOMESTIC" -> listOf(SeedColConfig("浙江高考") { it.zj }, SeedColConfig("上海高考") { it.sh })
            else -> emptyList()
        }
        val allCols = FIXED_COLS + dynamicCols
        val sb = StringBuilder()
        
        // Headers
        sb.append(allCols.joinToString(",") { "\"${it.header.replace("\"", "\"\"")}\"" }).append("\n")
        
        // Rows
        SEED_ROWS.forEach { row ->
            sb.append(allCols.joinToString(",") { "\"${it.getValue(row).replace("\"", "\"\"")}\"" }).append("\n")
        }
        return sb.toString().trim()
    }

    override fun run(vararg args: String?) {
        // Initialize default student types if the table is empty
        if (studentTypeDictionaryRepository.count() == 0L) {
            val defaults = listOf(
                StudentTypeDictionary(typeCode = "INTL", typeName = "国际学校", sortOrder = 1, capabilityMatrixCsv = generateCsvForType("INTL")),
                StudentTypeDictionary(typeCode = "TRANSITION_INTL", typeName = "体制内转国际", sortOrder = 2, capabilityMatrixCsv = generateCsvForType("TRANSITION_INTL")),
                StudentTypeDictionary(typeCode = "TRANSITION_HKDSE", typeName = "体制内转HKDSE", sortOrder = 3, capabilityMatrixCsv = generateCsvForType("TRANSITION_HKDSE")),
                StudentTypeDictionary(typeCode = "TRANSITION_HANGZHOU_INTL", typeName = "体制内转杭州国际学校", sortOrder = 4, capabilityMatrixCsv = generateCsvForType("TRANSITION_HANGZHOU_INTL")),
                StudentTypeDictionary(typeCode = "DOMESTIC", typeName = "体制内", sortOrder = 5, capabilityMatrixCsv = generateCsvForType("DOMESTIC"))
            )
            studentTypeDictionaryRepository.saveAll(defaults)
            println("=== Initialized default student types in database with CSV matrices ===")
        } else {
            // Migration for existing records missing CSV
            val existingTypes = studentTypeDictionaryRepository.findAll()
            var modified = false
            existingTypes.forEach { type ->
                if (type.capabilityMatrixCsv.isNullOrBlank()) {
                    type.capabilityMatrixCsv = generateCsvForType(type.typeCode)
                    modified = true
                }
            }
            if (modified) {
                studentTypeDictionaryRepository.saveAll(existingTypes)
                println("=== Migrated existing student types to include CSV matrices ===")
            }
        }
    }
}
