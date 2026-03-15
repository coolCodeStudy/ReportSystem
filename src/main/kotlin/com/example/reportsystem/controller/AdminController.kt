package com.example.reportsystem.controller

import com.example.reportsystem.entity.StudentTypeDictionary
import com.example.reportsystem.entity.TypeFormField
import com.example.reportsystem.service.SystemDictionaryService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity

@Controller
@RequestMapping("/admin")
class AdminController(
    private val dictService: SystemDictionaryService
) {
    @GetMapping("/templates")
    fun templatesPage(model: Model): String {
        return "admin-templates"
    }

    // --- API Endpoints ---

    @GetMapping("/api/types")
    @ResponseBody
    fun getAllTypes(): ResponseEntity<List<StudentTypeDictionary>> {
        return ResponseEntity.ok(dictService.getAllStudentTypes())
    }

    @PostMapping("/api/types")
    @ResponseBody
    fun saveType(@RequestBody type: StudentTypeDictionary): ResponseEntity<StudentTypeDictionary> {
        return ResponseEntity.ok(dictService.saveStudentType(type))
    }

    @DeleteMapping("/api/types/{id}")
    @ResponseBody
    fun deleteType(@PathVariable id: Long): ResponseEntity<Void> {
        dictService.deleteStudentType(id)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/api/fields/{typeCode}")
    @ResponseBody
    fun getFields(@PathVariable typeCode: String): ResponseEntity<List<TypeFormField>> {
        return ResponseEntity.ok(dictService.getFieldsForType(typeCode))
    }

    @PostMapping("/api/fields")
    @ResponseBody
    fun saveField(@RequestBody field: TypeFormField): ResponseEntity<TypeFormField> {
        return ResponseEntity.ok(dictService.saveFormField(field))
    }

    @DeleteMapping("/api/fields/{id}")
    @ResponseBody
    fun deleteField(@PathVariable id: Long): ResponseEntity<Void> {
        dictService.deleteFormField(id)
        return ResponseEntity.ok().build()
    }
}
