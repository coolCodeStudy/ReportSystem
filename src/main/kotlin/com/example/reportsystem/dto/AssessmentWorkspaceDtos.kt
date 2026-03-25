package com.example.reportsystem.dto

import javax.validation.constraints.Size

data class AssessmentRecordStep1UpdateRequest(
    @field:Size(max = 50, message = "Lingoland Level 字符串长度不能超过50")
    val lingolandLevel: String? = null,

    val assessmentDate: String? = null,

    @field:Size(max = 50, message = "参考体系类型 字符串长度不能超过50")
    val assessmentType: String? = null,

    val selectedExportColumns: String? = null
)
