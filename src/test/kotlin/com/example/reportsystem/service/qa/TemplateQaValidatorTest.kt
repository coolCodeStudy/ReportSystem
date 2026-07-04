package com.example.reportsystem.service.qa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TemplateQaValidatorTest {
    private val validator = TemplateQaValidator()

    @Test
    fun `valid gate configuration has no blocking errors`() {
        val configs = mutableMapOf(
            "GLOBAL_ASSESSMENT_DESCRIPTIONS" to """
                [
                  {"id":"STARTERS","name":"Starters"},
                  {"id":"MOVERS","name":"Movers"},
                  {"id":"FLYERS","name":"Flyers"},
                  {"id":"KET","name":"KET"},
                  {"id":"PET","name":"PET"}
                ]
            """.trimIndent(),
            "GLOBAL_CAPABILITY_MATRIX_CSV" to "Lingoland,CEFR\nK,Pre-A1\nG1,A1",
        )

        listOf("STARTERS", "MOVERS", "FLYERS", "KET", "PET").forEach { typeId ->
            configs["GLOBAL_SUBJECTS_$typeId"] = """[{"id":"reading","key":"READING","name":"阅读理解"}]"""
            configs["GLOBAL_ANALYSIS_CONFIG_${typeId}_READING"] = """[{"label":"定位能力","levels":["好","中","弱"]}]"""
            configs["GLOBAL_CAUSE_ANALYSIS_${typeId}_READING"] = """["词汇量不足","审题不稳定"]"""
            configs["GLOBAL_SCORE_RULE_${typeId}_READING"] = """{"calcType":"SCORE","total":25,"rules":[{"max":10,"level":"A1"}]}"""
        }

        val result = validator.validate(configs, QaMode.GATE)

        assertFalse(result.hasErrors(), result.issues.joinToString("\n") { it.message })
    }

    @Test
    fun `gate configuration requires Pre A1 and daily assessment types`() {
        val result = validator.validate(
            mapOf(
                "GLOBAL_ASSESSMENT_DESCRIPTIONS" to """[{"id":"KET","name":"KET"}]""",
                "GLOBAL_CAPABILITY_MATRIX_CSV" to "Lingoland,CEFR\nG1,A1",
            ),
            QaMode.GATE,
        )

        val errors = result.issues.filter { it.severity == QaIssueSeverity.ERROR }.map { it.message }
        assertTrue(errors.any { it.contains("Pre-A1") })
        assertTrue(errors.any { it.contains("Starters") })
        assertTrue(errors.any { it.contains("Movers") })
    }

    @Test
    fun `invalid json is reported with config key`() {
        val result = validator.validate(
            mapOf(
                "GLOBAL_ASSESSMENT_DESCRIPTIONS" to """[{"id":"KET","name":"KET"}]""",
                "GLOBAL_CAPABILITY_MATRIX_CSV" to "Lingoland,CEFR\nK,Pre-A1",
                "GLOBAL_SUBJECTS_KET" to """[{"id":"reading","key":"READING","name":"阅读理解"}]""",
                "GLOBAL_ANALYSIS_CONFIG_KET_READING" to """not json""",
                "GLOBAL_CAUSE_ANALYSIS_KET_READING" to """[]""",
                "GLOBAL_SCORE_RULE_KET_READING" to """{}""",
            ),
            QaMode.GATE,
        )

        assertTrue(result.hasErrors())
        assertEquals(
            "GLOBAL_ANALYSIS_CONFIG_KET_READING",
            result.issues.first { it.message.contains("JSON") }.key,
        )
    }
}
