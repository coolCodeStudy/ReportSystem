package com.example.reportsystem.service.qa

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

enum class QaMode {
    GATE,
    HUNT,
}

enum class QaIssueSeverity {
    ERROR,
    WARNING,
}

data class QaIssue(
    val severity: QaIssueSeverity,
    val key: String,
    val message: String,
)

data class QaValidationResult(
    val mode: QaMode,
    val issues: List<QaIssue>,
) {
    fun hasErrors(): Boolean = issues.any { it.severity == QaIssueSeverity.ERROR }
}

class TemplateQaValidator {
    private val mapper = jacksonObjectMapper()

    fun validate(configs: Map<String, String>, mode: QaMode): QaValidationResult {
        val issues = mutableListOf<QaIssue>()
        val descriptions = parseArray(configs, "GLOBAL_ASSESSMENT_DESCRIPTIONS", issues)
        val assessmentTypes = descriptions
            ?.mapNotNull { node ->
                val id = node.path("id").asText("").ifBlank { node.path("name").asText("") }
                val name = node.path("name").asText("").ifBlank { id }
                if (id.isBlank() && name.isBlank()) null else AssessmentType(id, name)
            }
            .orEmpty()

        validateRequiredTypes(mode, assessmentTypes, issues)
        validateCapabilityMatrix(configs, issues)
        validateAssessmentTemplates(configs, assessmentTypes, issues)

        return QaValidationResult(mode, issues)
    }

    private fun validateRequiredTypes(
        mode: QaMode,
        assessmentTypes: List<AssessmentType>,
        issues: MutableList<QaIssue>,
    ) {
        val required = if (mode == QaMode.GATE) {
            listOf("Starters", "Movers", "Flyers", "KET", "PET")
        } else {
            listOf("Starters", "Movers", "Flyers", "KET", "PET", "IELTS", "TOEFL Junior", "MAP")
        }
        val available = assessmentTypes.flatMap { listOf(it.id, it.name, normalizeKey(it.id), normalizeKey(it.name)) }
            .map { it.lowercase() }
            .toSet()

        required.forEach { expected ->
            val candidates = listOf(expected, normalizeKey(expected)).map { it.lowercase() }
            if (candidates.none { it in available }) {
                issues += QaIssue(
                    QaIssueSeverity.ERROR,
                    "GLOBAL_ASSESSMENT_DESCRIPTIONS",
                    "Missing assessment type required by ${mode.name.lowercase()} QA: $expected",
                )
            }
        }
    }

    private fun validateCapabilityMatrix(configs: Map<String, String>, issues: MutableList<QaIssue>) {
        val matrix = configs["GLOBAL_CAPABILITY_MATRIX_CSV"].orEmpty()
        if (!matrix.contains("Pre-A1", ignoreCase = true)) {
            issues += QaIssue(
                QaIssueSeverity.ERROR,
                "GLOBAL_CAPABILITY_MATRIX_CSV",
                "Capability matrix must include Pre-A1 so K/Starters reports can map the top row correctly.",
            )
        }
    }

    private fun validateAssessmentTemplates(
        configs: Map<String, String>,
        assessmentTypes: List<AssessmentType>,
        issues: MutableList<QaIssue>,
    ) {
        assessmentTypes.forEach { type ->
            val typeKey = resolveConfigKey(configs, "GLOBAL_SUBJECTS_", type)
            if (typeKey == null) {
                issues += QaIssue(
                    QaIssueSeverity.ERROR,
                    "GLOBAL_SUBJECTS_${normalizeKey(type.id.ifBlank { type.name })}",
                    "Missing subject configuration for ${type.displayName()}",
                )
                return@forEach
            }

            val subjects = parseArray(configs, typeKey, issues) ?: return@forEach
            if (subjects.isEmpty()) {
                issues += QaIssue(
                    QaIssueSeverity.ERROR,
                    typeKey,
                    "Subject configuration for ${type.displayName()} is empty.",
                )
                return@forEach
            }

            subjects.forEach subjectLoop@{ subject ->
                val subjectKey = subject.path("key").asText("")
                    .ifBlank { subject.path("id").asText("") }
                    .ifBlank { subject.path("name").asText("") }
                if (subjectKey.isBlank()) {
                    issues += QaIssue(QaIssueSeverity.ERROR, typeKey, "A subject in ${type.displayName()} is missing id/key/name.")
                    return@subjectLoop
                }
                validateJsonConfig(configs, type, subjectKey, "GLOBAL_ANALYSIS_CONFIG_", issues, required = true)
                validateJsonConfig(configs, type, subjectKey, "GLOBAL_CAUSE_ANALYSIS_", issues, required = true)
                validateJsonConfig(configs, type, subjectKey, "GLOBAL_SCORE_RULE_", issues, required = true)
            }
        }
    }

    private fun validateJsonConfig(
        configs: Map<String, String>,
        type: AssessmentType,
        subjectKey: String,
        prefix: String,
        issues: MutableList<QaIssue>,
        required: Boolean,
    ) {
        val key = resolveConfigKey(configs, prefix, type, subjectKey)
        if (key == null) {
            val expected = "${prefix}${normalizeKey(type.id.ifBlank { type.name })}_${normalizeKey(subjectKey)}"
            val severity = if (required) QaIssueSeverity.ERROR else QaIssueSeverity.WARNING
            issues += QaIssue(severity, expected, "Missing ${expected.removePrefix("GLOBAL_")} for ${type.displayName()} / $subjectKey")
            return
        }
        parseJson(configs[key].orEmpty(), key, issues)
    }

    private fun parseArray(configs: Map<String, String>, key: String, issues: MutableList<QaIssue>): List<JsonNode>? {
        val node = parseJson(configs[key].orEmpty(), key, issues) ?: return null
        if (!node.isArray) {
            issues += QaIssue(QaIssueSeverity.ERROR, key, "$key must be a JSON array.")
            return null
        }
        return node.toList()
    }

    private fun parseJson(value: String, key: String, issues: MutableList<QaIssue>): JsonNode? {
        if (value.isBlank()) {
            issues += QaIssue(QaIssueSeverity.ERROR, key, "$key is empty.")
            return null
        }
        return try {
            mapper.readTree(value)
        } catch (ex: Exception) {
            issues += QaIssue(QaIssueSeverity.ERROR, key, "$key is not valid JSON: ${ex.message}")
            null
        }
    }

    private fun resolveConfigKey(configs: Map<String, String>, prefix: String, type: AssessmentType): String? {
        return candidates(type).map { "$prefix$it" }.firstOrNull { configs.containsKey(it) }
    }

    private fun resolveConfigKey(configs: Map<String, String>, prefix: String, type: AssessmentType, subjectKey: String): String? {
        val subjectCandidates = listOf(subjectKey, normalizeKey(subjectKey)).distinct()
        return candidates(type)
            .flatMap { typeCandidate -> subjectCandidates.map { "$prefix${typeCandidate}_$it" } }
            .firstOrNull { configs.containsKey(it) }
    }

    private fun candidates(type: AssessmentType): List<String> {
        return listOf(
            type.id,
            type.name,
            normalizeKey(type.id),
            normalizeKey(type.name),
        ).filter { it.isNotBlank() }.distinct()
    }

    private fun normalizeKey(raw: String): String {
        return raw.trim()
            .uppercase()
            .replace(Regex("[^A-Z0-9]+"), "_")
            .trim('_')
    }

    private data class AssessmentType(val id: String, val name: String) {
        fun displayName(): String = name.ifBlank { id }
    }
}
