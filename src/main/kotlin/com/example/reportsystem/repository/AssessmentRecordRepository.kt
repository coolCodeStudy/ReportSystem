package com.example.reportsystem.repository

import com.example.reportsystem.entity.AssessmentRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AssessmentRecordRepository : JpaRepository<AssessmentRecord, Long> {
    fun findByStudentId(studentId: Long): List<AssessmentRecord>
}
