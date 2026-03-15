package com.example.reportsystem.repository

import com.example.reportsystem.entity.StudentTypeDictionary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StudentTypeDictionaryRepository : JpaRepository<StudentTypeDictionary, Long> {
    fun findByStatusOrderBySortOrderAsc(status: String): List<StudentTypeDictionary>
    fun existsByTypeCode(typeCode: String): Boolean
    fun findByTypeCode(typeCode: String): StudentTypeDictionary?
}
