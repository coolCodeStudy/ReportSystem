package com.example.reportsystem.controller

import com.example.reportsystem.entity.AssessmentRecord
import com.example.reportsystem.repository.AssessmentRecordRepository
import com.example.reportsystem.repository.StudentRepository
import com.example.reportsystem.common.api.ResponseResult
import com.example.reportsystem.dto.AssessmentRecordStep1UpdateRequest
import org.springframework.stereotype.Controller
import javax.validation.Valid
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@Controller
class AssessmentWorkspaceController(
    private val assessmentRecordRepository: AssessmentRecordRepository,
    private val studentRepository: StudentRepository
) {

    @PostMapping("/api/workspace/create")
    @ResponseBody
    fun createEmptyWorkspace(@RequestParam studentId: Long): ResponseResult<Map<String, Any>> {
        val student = studentRepository.findById(studentId).orElseThrow {
            IllegalArgumentException("Student not found")
        }
        val record = AssessmentRecord().apply {
            this.student = student
            this.assessmentDate = LocalDate.now()
            this.targetGrade = student.grade
        }
        val saved = assessmentRecordRepository.save(record)
        return ResponseResult.success(mapOf("recordId" to saved.id!!))
    }

    @GetMapping("/assessment/{id}/workspace")
    fun showWorkspace(@PathVariable("id") id: Long, model: Model): String {
        val record = assessmentRecordRepository.findById(id).orElseThrow {
            IllegalArgumentException("Assessment Record not found for id $id")
        }
        
        // Pass info to the Thymeleaf view
        model.addAttribute("recordId", record.id)
        model.addAttribute("studentName", record.student?.name ?: "未知学生")
        model.addAttribute("targetAssessment", record.assessmentType ?: "未指定")
        model.addAttribute("assessmentResults", record.assessmentResults ?: "{}")
        model.addAttribute("record", record)
        model.addAttribute("student", record.student)
        
        return "workspace"
    }

    @PutMapping("/api/assessment/{id}/results")
    @ResponseBody
    fun updateAssessmentResults(
        @PathVariable("id") id: Long,
        @RequestBody resultsJson: String
    ): ResponseResult<Nothing> {
        val record = assessmentRecordRepository.findById(id).orElseThrow {
            IllegalArgumentException("Assessment Record not found for id $id")
        }
        
        record.assessmentResults = resultsJson
        assessmentRecordRepository.save(record)
        
        return ResponseResult.success(null, "Results saved successfully")
    }

    @PutMapping("/api/assessment/{id}/step1")
    @ResponseBody
    fun updateStep1(
        @PathVariable("id") id: Long,
        @Valid @RequestBody data: AssessmentRecordStep1UpdateRequest
    ): ResponseResult<Nothing> {
        val record = assessmentRecordRepository.findById(id).orElseThrow {
            IllegalArgumentException("Assessment Record not found for id $id")
        }
        
        data.lingolandLevel?.let { record.lingolandLevel = it }
        data.assessmentDate?.let { 
            if (it.isNotBlank()) record.assessmentDate = LocalDate.parse(it)
        }
        data.assessmentType?.let { record.assessmentType = it }
        data.selectedExportColumns?.let { record.selectedExportColumns = it }

        assessmentRecordRepository.save(record)
        return ResponseResult.success(null)
    }
}
