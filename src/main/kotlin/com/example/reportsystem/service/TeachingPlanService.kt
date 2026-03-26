package com.example.reportsystem.service

import com.example.reportsystem.entity.TeachingPlan
import com.example.reportsystem.repository.TeachingPlanRepository
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
class TeachingPlanService(private val teachingPlanRepository: TeachingPlanRepository) {

    @Transactional
    fun importTeachingPlans(file: MultipartFile) {
        val workbook = WorkbookFactory.create(file.inputStream)
        val formatter = DataFormatter()
        
        for (sheetIndex in 0 until workbook.numberOfSheets) {
            val sheet = workbook.getSheetAt(sheetIndex)
            val bookName = sheet.sheetName.trim()
            if (bookName.isEmpty()) continue
            
            // Skip header
            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                
                val unitCodeCell = row.getCell(0)
                if (unitCodeCell == null) continue
                val unitCode = formatter.formatCellValue(unitCodeCell).trim()
                if (unitCode.isEmpty()) continue
                
                val courseContentCell = row.getCell(1)
                val learningObjectivesCell = row.getCell(2)
                val teachingDurationCell = row.getCell(3)
                
                val courseContent = courseContentCell?.let { formatter.formatCellValue(it).trim() }
                val learningObjectives = learningObjectivesCell?.let { formatter.formatCellValue(it).trim() }
                val teachingDuration = teachingDurationCell?.let { formatter.formatCellValue(it).trim() }
                
                var existingPlan = teachingPlanRepository.findByUnitCodeAndBookName(unitCode, bookName)
                if (existingPlan == null) {
                    existingPlan = TeachingPlan(unitCode = unitCode, bookName = bookName)
                }
                existingPlan.courseContent = courseContent
                existingPlan.learningObjectives = learningObjectives
                existingPlan.teachingDuration = teachingDuration
                
                teachingPlanRepository.save(existingPlan)
            }
        }
        workbook.close()
    }

    fun getAllPlans(): List<TeachingPlan> {
        return teachingPlanRepository.findAll()
    }

    @Transactional
    fun deleteAllPlans() {
        teachingPlanRepository.deleteAll()
    }

    @Transactional
    fun deletePlansByBookName(bookName: String) {
        teachingPlanRepository.deleteByBookName(bookName)
    }
}
