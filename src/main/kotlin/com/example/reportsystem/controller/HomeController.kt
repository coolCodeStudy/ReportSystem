package com.example.reportsystem.controller

import com.example.reportsystem.dto.StudentDto
import com.example.reportsystem.repository.AssessmentRecordRepository
import com.example.reportsystem.repository.StudentRepository
import com.example.reportsystem.service.PdfService
import com.example.reportsystem.service.StudentArchiveService
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter

@Controller
class HomeController(
    private val pdfService: PdfService,
    private val studentArchiveService: StudentArchiveService,
    private val docxGeneratorService: com.example.reportsystem.service.DocxGeneratorService,
    private val studentRepository: StudentRepository,
    private val assessmentRecordRepository: AssessmentRecordRepository,
    private val dictService: com.example.reportsystem.service.SystemDictionaryService
) {

    @GetMapping("/")
    fun home(model: Model): String {
        val activeTypes = dictService.getAllActiveStudentTypes()
        
        val students = studentRepository.findAllByOrderByUpdatedAtDesc()
        val dtos = students.map { student ->
            val records = student.id?.let { assessmentRecordRepository.findByStudentId(it) } ?: emptyList()
            val latestRecord = records.maxByOrNull { it.createdAt }

            val genderStr = student.gender ?: "未知"
            val ageStr = student.age?.let { "${it}岁" } ?: ""
            val genderAgeInfo = if (ageStr.isNotEmpty()) "$genderStr | $ageStr" else genderStr

            val schoolStr = student.school ?: latestRecord?.targetGrade ?: "未填写"

            val dateStr = latestRecord?.assessmentDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                ?: latestRecord?.createdAt?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                ?: student.updatedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                
            val goalStr = latestRecord?.studyGoal ?: "-"
            val levelStr = latestRecord?.lingolandLevel ?: "-"
            
            val typeName = activeTypes.find { it.typeCode == student.studentType }?.typeName ?: student.studentType ?: "未定"

            StudentDto(
                id = student.id,
                name = student.name,
                phone = student.phone,
                age = student.age,
                gender = student.gender,
                school = student.school,
                grade = student.grade,
                genderAgeInfo = if (genderStr.isEmpty() && ageStr.isEmpty()) "" else "$genderStr $ageStr".trim(),
                schoolOrTarget = schoolStr,
                studentType = student.studentType ?: "未定",
                studentTypeName = typeName,
                latestAssessmentDate = dateStr,
                latestStudyGoal = goalStr,
                latestLevel = levelStr,
                dynamicData = student.dynamicData
            )
        }
        
        model.addAttribute("students", dtos)
        model.addAttribute("activeStudentTypes", activeTypes)
        return "index"
    }

    @GetMapping("/report/pdf")
    fun downloadPdf(): ResponseEntity<ByteArray> {
        val pdfBytes = pdfService.generateReport()

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=lingoland_report.pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdfBytes)
    }

    @RequestMapping(value = ["/report/docx"], method = [org.springframework.web.bind.annotation.RequestMethod.GET, org.springframework.web.bind.annotation.RequestMethod.POST])
    fun downloadDocx(form: UserReportForm?): ResponseEntity<ByteArray> {
        var targetLevel: String? = null
        var targetGrade: String? = null
        var finalStudentType = form?.studentType

        if (form != null) {
            try {
                val student = studentArchiveService.saveOrUpdateArchive(form)
                if (student != null) {
                    finalStudentType = student.studentType
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            targetLevel = form.lingolandLevel
            targetGrade = form.grade
        }

        val modifiedBytes = docxGeneratorService.generateDocx(
            targetLevel,
            targetGrade,
            finalStudentType,
            form?.assessmentType
        )

        val mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    
        val headers = HttpHeaders()
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=${java.net.URLEncoder.encode("Lingoland学习方案.docx", "UTF-8")}")

        return ResponseEntity.ok()
            .headers(headers)
            .header(HttpHeaders.CONTENT_LENGTH, modifiedBytes.size.toString())
            .contentType(mediaType)
            .body(modifiedBytes)
    }

    @PostMapping("/report/save")
    fun saveReport(form: UserReportForm?): String {
        if (form != null) {
            try {
                studentArchiveService.saveOrUpdateArchive(form)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return "redirect:/"
    }
}

data class UserReportForm(
    val name: String? = null,
    val phone: String? = null,
    val age: Int? = null,
    val gender: String? = null,
    val school: String? = null,
    val grade: String? = null,
    val studentType: String? = null,
    val lingolandLevel: String? = null,
    val studyGoal: String? = null,
    val assessmentType: List<String>? = null,
    val otherAssessment: String? = null,
    val assessmentDate: String? = null,
    val dynamicData: String? = null
)
