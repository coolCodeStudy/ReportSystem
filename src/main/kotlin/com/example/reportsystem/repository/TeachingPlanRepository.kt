package com.example.reportsystem.repository

import com.example.reportsystem.entity.TeachingPlan
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TeachingPlanRepository : JpaRepository<TeachingPlan, Long> {
    fun findByUnitCodeAndBookName(unitCode: String, bookName: String): TeachingPlan?
    fun deleteByBookName(bookName: String)
    fun findByBookNameIn(bookNames: List<String>): List<TeachingPlan>
}
