package com.example.reportsystem.controller

import com.example.reportsystem.entity.Student
import com.example.reportsystem.repository.StudentRepository
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import com.example.reportsystem.repository.AssessmentRecordRepository
import com.example.reportsystem.service.DocxGeneratorService
import java.time.format.DateTimeFormatter

data class StudentForm(
    val id: Long? = null,
    val name: String? = null,
    val phone: String? = null,
    val age: Int? = null,
    val gender: String? = null,
    val school: String? = null,
    val grade: String? = null,
    val studentType: com.example.reportsystem.entity.StudentType? = null
)

data class AssessmentHistoryDto(
    val id: Long?,
    val date: String,
    val type: String,
    val targetGrade: String,
    val level: String,
    val goal: String
)

@Controller
@RequestMapping("/student")
class StudentController(
    private val studentRepository: StudentRepository,
    private val assessmentRecordRepository: AssessmentRecordRepository,
    private val docxGeneratorService: DocxGeneratorService
) {

    @PostMapping("/save")
    fun saveStudent(@ModelAttribute form: StudentForm): String {
        val name = form.name?.trim()
        if (name.isNullOrEmpty()) {
            return "redirect:/"
        }

        var student = if (form.id != null) {
            studentRepository.findById(form.id).orElse(null)
        } else {
            studentRepository.findFirstByNameAndSchool(name, form.school)
                ?: studentRepository.findFirstByName(name)
        }

        if (student == null) {
            student = Student().apply {
                this.name = name
                this.phone = form.phone
                this.age = form.age
                this.gender = form.gender
                this.school = form.school
                this.grade = form.grade
                this.studentType = form.studentType
            }
        } else {
            if (form.phone != null) student.phone = form.phone
            if (form.age != null) student.age = form.age
            if (form.gender != null) student.gender = form.gender
            if (form.school != null) student.school = form.school
            if (form.grade != null) student.grade = form.grade
            if (form.studentType != null) student.studentType = form.studentType
        }
        
        studentRepository.save(student)
        return "redirect:/"
    }

    @GetMapping("/{id}/history")
    @ResponseBody
    fun getStudentHistory(@PathVariable id: Long): ResponseEntity<List<AssessmentHistoryDto>> {
        val records = assessmentRecordRepository.findByStudentId(id)
        val dtos = records.sortedByDescending { it.assessmentDate ?: it.createdAt.toLocalDate() }.map {
            val dateStr = it.assessmentDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                ?: it.createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            
            AssessmentHistoryDto(
                id = it.id,
                date = dateStr,
                type = it.assessmentType ?: "-",
                targetGrade = it.targetGrade ?: "-",
                level = it.lingolandLevel ?: "-",
                goal = it.studyGoal ?: "-"
            )
        }
        return ResponseEntity.ok(dtos)
    }

    @GetMapping("/history/{recordId}/export")
    fun exportHistoricReport(@PathVariable recordId: Long): ResponseEntity<ByteArray> {
        val recordOpt = assessmentRecordRepository.findById(recordId)
        if (!recordOpt.isPresent) {
            return ResponseEntity.notFound().build()
        }

        val record = recordOpt.get()
        
        // Use the saved params to regenerate the docx
        val assessmentTypeList = record.assessmentType
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
        val modifiedBytes = docxGeneratorService.generateDocx(
            record.lingolandLevel,
            record.targetGrade,
            record.student?.studentType,
            assessmentTypeList
        )

        val mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    
        val headers = HttpHeaders()
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=${java.net.URLEncoder.encode("${record.student?.name ?: "Student"}_历史记录测评报告.docx", "UTF-8")}")

        return ResponseEntity.ok()
            .headers(headers)
            .header(HttpHeaders.CONTENT_LENGTH, modifiedBytes.size.toString())
            .contentType(mediaType)
            .body(modifiedBytes)
    }
}
