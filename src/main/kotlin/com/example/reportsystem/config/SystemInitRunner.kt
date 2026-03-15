package com.example.reportsystem.config

import com.example.reportsystem.entity.StudentTypeDictionary
import com.example.reportsystem.repository.StudentTypeDictionaryRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class SystemInitRunner(
    private val studentTypeDictionaryRepository: StudentTypeDictionaryRepository
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        // Initialize default student types if the table is empty
        if (studentTypeDictionaryRepository.count() == 0L) {
            val defaults = listOf(
                StudentTypeDictionary(typeCode = "INTL", typeName = "国际学校", sortOrder = 1),
                StudentTypeDictionary(typeCode = "TRANSITION_INTL", typeName = "体制内转国际", sortOrder = 2),
                StudentTypeDictionary(typeCode = "TRANSITION_HKDSE", typeName = "体制内转HKDSE", sortOrder = 3),
                StudentTypeDictionary(typeCode = "TRANSITION_HANGZHOU_INTL", typeName = "体制内转杭州国际学校", sortOrder = 4),
                StudentTypeDictionary(typeCode = "DOMESTIC", typeName = "体制内", sortOrder = 5)
            )
            studentTypeDictionaryRepository.saveAll(defaults)
            println("=== Initialized default student types in database ===")
        }
    }
}
