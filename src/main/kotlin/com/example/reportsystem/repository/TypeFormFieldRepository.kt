package com.example.reportsystem.repository

import com.example.reportsystem.entity.TypeFormField
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TypeFormFieldRepository : JpaRepository<TypeFormField, Long> {
    fun findByStudentTypeCodeOrderBySortOrderAsc(studentTypeCode: String): List<TypeFormField>
}
