package com.example.reportsystem.controller

import com.example.reportsystem.entity.TeachingPlan
import com.example.reportsystem.entity.TextbookConfig
import com.example.reportsystem.repository.TextbookConfigRepository
import com.example.reportsystem.service.TeachingPlanService
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@Controller
class TeachingPlanController(
    private val teachingPlanService: TeachingPlanService,
    private val textbookConfigRepository: TextbookConfigRepository
) {

    @GetMapping("/admin/teaching-plan")
    fun teachingPlanPage(): String {
        return "admin-teaching-plan"
    }

    @ResponseBody
    @GetMapping("/admin/api/textbook-config/{bookName}")
    fun getTextbookConfig(@PathVariable bookName: String): ResponseEntity<Map<String, Any>> {
        val config = textbookConfigRepository.findByBookName(bookName)
        return ResponseEntity.ok(mapOf("code" to 200, "data" to (config?.introduction ?: "")))
    }

    @ResponseBody
    @PostMapping("/admin/api/textbook-config")
    fun saveTextbookConfig(@RequestBody payload: Map<String, String>): ResponseEntity<Map<String, Any>> {
        val bookName = payload["bookName"] ?: return ResponseEntity.badRequest().body(mapOf("code" to 400, "message" to "Missing bookName"))
        val intro = payload["introduction"] ?: ""
        
        var config = textbookConfigRepository.findByBookName(bookName)
        if (config == null) {
            config = TextbookConfig(bookName = bookName, introduction = intro)
        } else {
            config.introduction = intro
        }
        textbookConfigRepository.save(config)
        
        return ResponseEntity.ok(mapOf("code" to 200, "message" to "Saved successfully"))
    }

    @ResponseBody
    @GetMapping("/admin/api/teaching-plan/list")
    fun getAllPlans(): ResponseEntity<Map<String, Any>> {
        val plans = teachingPlanService.getAllPlans()
        return ResponseEntity.ok(mapOf("code" to 200, "data" to plans))
    }

    @ResponseBody
    @PostMapping("/admin/api/teaching-plan/import")
    fun importPlans(
        @RequestParam("file") file: MultipartFile
    ): ResponseEntity<Map<String, Any>> {
        return try {
            teachingPlanService.importTeachingPlans(file)
            ResponseEntity.ok(mapOf("code" to 200, "message" to "导入成功"))
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.badRequest().body(mapOf("code" to 400, "message" to "导入失败: ${e.message}"))
        }
    }

    @ResponseBody
    @DeleteMapping("/admin/api/teaching-plan/book/{bookName}")
    fun deletePlansByBookName(@PathVariable bookName: String): ResponseEntity<Map<String, Any>> {
        teachingPlanService.deletePlansByBookName(bookName)
        return ResponseEntity.ok(mapOf("code" to 200, "message" to "删除教材 $bookName 成功"))
    }

    @ResponseBody
    @DeleteMapping("/admin/api/teaching-plan/clear")
    fun clearPlans(): ResponseEntity<Map<String, Any>> {
        teachingPlanService.deleteAllPlans()
        return ResponseEntity.ok(mapOf("code" to 200, "message" to "清空成功"))
    }
}
