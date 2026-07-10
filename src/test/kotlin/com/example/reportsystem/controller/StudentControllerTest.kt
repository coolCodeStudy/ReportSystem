package com.example.reportsystem.controller

import com.example.reportsystem.entity.AssessmentRecord
import com.example.reportsystem.entity.Student
import com.example.reportsystem.repository.AssessmentRecordRepository
import com.example.reportsystem.repository.StudentRepository
import com.example.reportsystem.service.DocxGeneratorService
import com.example.reportsystem.service.ReportPdfConversionException
import com.example.reportsystem.service.ReportPdfConversionFailure
import com.example.reportsystem.service.ReportPdfConversionService
import com.example.reportsystem.service.StudentArchiveService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.time.LocalDate
import java.util.Optional

class StudentControllerTest {

    private val studentRepository: StudentRepository = mockk()
    private val assessmentRecordRepository: AssessmentRecordRepository = mockk()
    private val docxGeneratorService: DocxGeneratorService = mockk()
    private val reportPdfConversionService: ReportPdfConversionService = mockk()
    private val studentArchiveService: StudentArchiveService = mockk()

    private val controller = StudentController(
        studentRepository,
        assessmentRecordRepository,
        docxGeneratorService,
        reportPdfConversionService,
        studentArchiveService
    )

    @Test
    fun `saveStudent should update name when editing an existing student`() {
        val existingStudent = Student().apply {
            id = 7L
            name = "旧名字"
            school = "Lingoland"
        }
        val savedStudent = slot<Student>()

        every { studentRepository.findById(7L) } returns Optional.of(existingStudent)
        every { studentRepository.save(capture(savedStudent)) } answers { firstArg() }

        val redirect = controller.saveStudent(
            StudentForm(
                id = 7L,
                name = "新名字",
                school = "Lingoland"
            )
        )

        assertThat(redirect).isEqualTo("redirect:/")
        assertThat(savedStudent.captured.name).isEqualTo("新名字")
        verify(exactly = 1) { studentRepository.save(existingStudent) }
    }

    @Test
    fun `saveStudent should sync target grade to blank assessment records`() {
        val existingStudent = Student().apply {
            id = 7L
            name = "Eden"
            school = "香港耀中国际学校"
        }

        every { studentRepository.findById(7L) } returns Optional.of(existingStudent)
        every { studentRepository.save(existingStudent) } returns existingStudent
        every { studentArchiveService.syncBlankTargetGrades(7L, "G5") } returns Unit

        controller.saveStudent(
            StudentForm(
                id = 7L,
                name = "Eden",
                school = "香港耀中国际学校",
                grade = "G5"
            )
        )

        assertThat(existingStudent.grade).isEqualTo("G5")
        verify(exactly = 1) { studentArchiveService.syncBlankTargetGrades(7L, "G5") }
    }

    @Test
    fun `getStudentHistory should fall back to student grade when record target grade is blank`() {
        val student = Student().apply {
            id = 7L
            name = "Eden"
            grade = "G5"
        }
        val record = AssessmentRecord().apply {
            id = 21L
            this.student = student
            assessmentType = "PET"
            targetGrade = null
            lingolandLevel = "A2-"
            assessmentDate = LocalDate.of(2026, 6, 26)
        }

        every { assessmentRecordRepository.findByStudentId(7L) } returns listOf(record)

        val response = controller.getStudentHistory(7L)

        assertThat(response.body).hasSize(1)
        assertThat(response.body!!.first().targetGrade).isEqualTo("G5")
    }

    @Test
    fun `exportHistoricReport should fall back to student grade when record target grade is blank`() {
        val student = Student().apply {
            id = 7L
            name = "Eden"
            grade = "G5"
            studentType = "international"
        }
        val record = AssessmentRecord().apply {
            id = 21L
            this.student = student
            assessmentType = "PET"
            targetGrade = null
            lingolandLevel = "A2-"
        }

        every { assessmentRecordRepository.findById(21L) } returns Optional.of(record)
        every {
            docxGeneratorService.generateDocx(
                "A2-",
                "G5",
                "international",
                listOf("PET"),
                null,
                null,
                null,
                null
            )
        } returns byteArrayOf(1, 2, 3)

        val response = controller.exportHistoricReport(21L, null)

        assertThat(response.body).containsExactly(1, 2, 3)
        verify(exactly = 1) {
            docxGeneratorService.generateDocx(
                "A2-",
                "G5",
                "international",
                listOf("PET"),
                null,
                null,
                null,
                null
            )
        }
    }

    @Test
    fun `exportHistoricReportPdf should convert the same historic DOCX and return PDF`() {
        val student = Student().apply {
            id = 7L
            name = "Viola"
            grade = "G5"
            studentType = "international"
        }
        val record = AssessmentRecord().apply {
            id = 31L
            this.student = student
            assessmentType = "KET"
            targetGrade = "G6"
            lingolandLevel = "B1-"
            selectedExportColumns = "Lingoland,CEFR"
            assessmentResults = "{\"reading\":{}}"
            teachingPlanData = "{\"coursePlans\":[]}"
        }
        val docxBytes = "docx".toByteArray()
        val pdfBytes = "%PDF-1.4".toByteArray()

        every { assessmentRecordRepository.findById(31L) } returns Optional.of(record)
        every {
            docxGeneratorService.generateDocx(
                "B1-",
                "G6",
                "international",
                listOf("KET"),
                listOf("Lingoland", "CEFR"),
                null,
                "{\"reading\":{}}",
                "{\"coursePlans\":[]}"
            )
        } returns docxBytes
        every { reportPdfConversionService.convert(docxBytes) } returns pdfBytes

        val response = controller.exportHistoricReportPdf(31L, null)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentType).isEqualTo(MediaType.APPLICATION_PDF)
        assertThat(response.headers.getFirst("Content-Disposition")).contains(".pdf")
        assertThat(response.body).containsExactly(*pdfBytes)
        verify(exactly = 1) { reportPdfConversionService.convert(docxBytes) }
    }

    @Test
    fun `exportHistoricReportPdf should return service unavailable when conversion is disabled`() {
        val student = Student().apply {
            id = 7L
            name = "Viola"
            grade = "G5"
        }
        val record = AssessmentRecord().apply {
            id = 31L
            this.student = student
            assessmentType = "KET"
            lingolandLevel = "B1-"
        }
        val docxBytes = "docx".toByteArray()

        every { assessmentRecordRepository.findById(31L) } returns Optional.of(record)
        every { docxGeneratorService.generateDocx(any(), any(), any(), any(), any(), any(), any(), any()) } returns docxBytes
        every { reportPdfConversionService.convert(docxBytes) } throws ReportPdfConversionException(
            ReportPdfConversionFailure.UNAVAILABLE,
            "PDF export is disabled."
        )

        val response = controller.exportHistoricReportPdf(31L, null)

        assertThat(response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        assertThat(response.body?.toString(Charsets.UTF_8)).contains("PDF 导出暂时不可用")
    }

    @Test
    fun `exportHistoricReportPdf should return not found for a missing record`() {
        every { assessmentRecordRepository.findById(404L) } returns Optional.empty()

        val response = controller.exportHistoricReportPdf(404L, null)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        verify(exactly = 0) { reportPdfConversionService.convert(any()) }
    }
}
