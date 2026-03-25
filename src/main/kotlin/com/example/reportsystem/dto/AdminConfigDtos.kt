package com.example.reportsystem.dto

import javax.validation.constraints.NotBlank

data class GlobalConfigUpdateRequest(
    @field:NotBlank(message = "配置内容不能为空")
    val value: String
)

data class MatrixUpdateRequest(
    val csv: String? = null,
    val associatedColumns: String? = null
)
