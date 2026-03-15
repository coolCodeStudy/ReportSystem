package com.example.reportsystem.service

import com.example.reportsystem.controller.UserReportForm
import com.example.reportsystem.entity.AssessmentRecord
import com.example.reportsystem.entity.Student
import com.example.reportsystem.repository.AssessmentRecordRepository
import com.example.reportsystem.repository.StudentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Service
class StudentArchiveService(
    private val studentRepository: StudentRepository,
    private val assessmentRecordRepository: AssessmentRecordRepository
) {
    @Transactional
    fun saveOrUpdateArchive(form: UserReportForm) {
        val name = form.name?.trim()
        if (name.isNullOrEmpty()) {
            return
        }

        var student = studentRepository.findFirstByNameAndSchool(name, form.school)
            ?: studentRepository.findFirstByName(name)

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
            student = studentRepository.save(student)
        } else {
            var updated = false
            if (form.phone != null && student.phone != form.phone) { student.phone = form.phone; updated = true }
            if (form.age != null && student.age != form.age) { student.age = form.age; updated = true }
            if (form.gender != null && student.gender != form.gender) { student.gender = form.gender; updated = true }
            if (form.school != null && student.school != form.school) { student.school = form.school; updated = true }
            if (form.grade != null && student.grade != form.grade) { student.grade = form.grade; updated = true }
            if (form.studentType != null && student.studentType != form.studentType) { student.studentType = form.studentType; updated = true }
            if (updated) {
                student = studentRepository.save(student)
            }
        }

        val assessmentTypesStr = form.assessmentType?.joinToString(", ")
        
        var parsedDate = LocalDate.now()
        if (!form.assessmentDate.isNullOrBlank()) {
            try {
                parsedDate = LocalDate.parse(form.assessmentDate, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (e: DateTimeParseException) {
                // Ignore and fallback to today
            }
        }

        val record = AssessmentRecord().apply {
            this.student = student
            this.assessmentType = assessmentTypesStr
            this.otherAssessment = form.otherAssessment
            this.targetGrade = form.grade
            this.lingolandLevel = form.lingolandLevel
            this.studyGoal = form.studyGoal
            this.assessmentDate = parsedDate
        }
        assessmentRecordRepository.save(record)
    }

    @Transactional
    fun deleteStudent(studentId: Long) {
        // Find and soft-delete all associated assessment records first
        val records = assessmentRecordRepository.findByStudentId(studentId)
        records.forEach { it.isDeleted = true }
        assessmentRecordRepository.saveAll(records)

        // Now soft-delete the student
        studentRepository.findById(studentId).ifPresent {
            it.isDeleted = true
            studentRepository.save(it)
        }
    }

    @Transactional
    fun deleteAssessmentRecord(recordId: Long) {
        assessmentRecordRepository.findById(recordId).ifPresent {
            it.isDeleted = true
            assessmentRecordRepository.save(it)
        }
    }
}
