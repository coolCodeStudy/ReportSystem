package com.example.reportsystem.controller

import com.example.reportsystem.entity.StudentTypeDictionary
import com.example.reportsystem.entity.TypeFormField
import com.example.reportsystem.service.SystemDictionaryService
import com.example.reportsystem.common.api.ResponseResult
import com.example.reportsystem.dto.GlobalConfigUpdateRequest
import com.example.reportsystem.dto.MatrixUpdateRequest
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import javax.validation.Valid

@Controller
@RequestMapping("/admin")
class AdminController(
    private val dictService: SystemDictionaryService
) {
    @GetMapping("/templates")
    fun templatesPage(model: Model): String {
        return "admin-templates"
    }

    @GetMapping("/analysis-templates")
    fun analysisTemplatesPage(model: Model): String {
        return "admin-analysis-templates"
    }

    // --- API Endpoints ---

    @GetMapping("/api/types")
    @ResponseBody
    fun getAllTypes(): ResponseResult<List<StudentTypeDictionary>> {
        return ResponseResult.success(dictService.getAllStudentTypes())
    }

    @PostMapping("/api/types")
    @ResponseBody
    fun saveType(@RequestBody type: StudentTypeDictionary): ResponseResult<StudentTypeDictionary> {
        return ResponseResult.success(dictService.saveStudentType(type))
    }

    @PostMapping("/api/types/{id}/matrix")
    @ResponseBody
    fun updateMatrix(@PathVariable id: Long, @Valid @RequestBody body: MatrixUpdateRequest): ResponseResult<Nothing> {
        val types = dictService.getAllStudentTypes()
        val type = types.find { it.id == id } ?: throw IllegalArgumentException("Type not found")
        // Save associated_columns if provided instead of matrix (matrix is kept for legacy)
        if (body.associatedColumns != null) {
            type.associatedColumns = body.associatedColumns
        } else if (body.csv != null) {
            type.capabilityMatrixCsv = body.csv
        }
        dictService.saveStudentType(type)
        return ResponseResult.success(null)
    }

    @DeleteMapping("/api/types/{id}")
    @ResponseBody
    fun deleteType(@PathVariable id: Long): ResponseResult<Nothing> {
        dictService.deleteStudentType(id)
        return ResponseResult.success(null)
    }

    @GetMapping("/api/fields/{typeCode}")
    @ResponseBody
    fun getFields(@PathVariable typeCode: String): ResponseResult<List<TypeFormField>> {
        return ResponseResult.success(dictService.getFieldsForType(typeCode))
    }

    @PostMapping("/api/fields")
    @ResponseBody
    fun saveField(@RequestBody field: TypeFormField): ResponseResult<TypeFormField> {
        return ResponseResult.success(dictService.saveFormField(field))
    }

    @DeleteMapping("/api/fields/{id}")
    @ResponseBody
    fun deleteField(@PathVariable id: Long): ResponseResult<Nothing> {
        dictService.deleteFormField(id)
        return ResponseResult.success(null)
    }

    @GetMapping("/api/config/{key}")
    @ResponseBody
    fun getConfig(@PathVariable key: String): ResponseResult<Map<String, String>> {
        val value = dictService.getGlobalConfig(key) ?: ""
        return ResponseResult.success(mapOf("value" to value))
    }

    @PostMapping("/api/config/{key}")
    @ResponseBody
    fun saveConfig(@PathVariable key: String, @Valid @RequestBody body: GlobalConfigUpdateRequest): ResponseResult<Nothing> {
        dictService.saveGlobalConfig(key, body.value)
        return ResponseResult.success(null)
    }
}
