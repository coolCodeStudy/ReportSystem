package com.example.reportsystem.controller

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
}
