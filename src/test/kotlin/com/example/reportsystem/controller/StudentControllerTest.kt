package com.example.reportsystem.controller

import com.example.reportsystem.entity.AssessmentRecord
import com.example.reportsystem.entity.Student
import com.example.reportsystem.repository.AssessmentRecordRepository
import com.example.reportsystem.repository.StudentRepository
import com.example.reportsystem.service.DocxGeneratorService
import com.example.reportsystem.service.StudentArchiveService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Optional

class StudentControllerTest {

    private val studentRepository: StudentRepository = mockk()
    private val assessmentRecordRepository: AssessmentRecordRepository = mockk()
    private val docxGeneratorService: DocxGeneratorService = mockk()
    private val studentArchiveService: StudentArchiveService = mockk()

    private val controller = StudentController(
        studentRepository,
        assessmentRecordRepository,
        docxGeneratorService,
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
}
