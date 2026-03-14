package com.example.reportsystem.repository

import com.example.reportsystem.entity.Student
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StudentRepository : JpaRepository<Student, Long> {
    fun findFirstByNameAndSchool(name: String, school: String?): Student?
    fun findFirstByName(name: String): Student?
}
