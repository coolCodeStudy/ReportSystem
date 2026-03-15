package com.example.reportsystem.service

import com.example.reportsystem.entity.StudentTypeDictionary
import com.example.reportsystem.entity.TypeFormField
import com.example.reportsystem.repository.StudentTypeDictionaryRepository
import com.example.reportsystem.repository.TypeFormFieldRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SystemDictionaryService(
    private val typeDictRepo: StudentTypeDictionaryRepository,
    private val formFieldRepo: TypeFormFieldRepository
) {
    fun getAllActiveStudentTypes(): List<StudentTypeDictionary> {
        return typeDictRepo.findByStatusOrderBySortOrderAsc("ACTIVE")
    }

    fun getAllStudentTypes(): List<StudentTypeDictionary> {
        return typeDictRepo.findAll().sortedBy { it.sortOrder }
    }

    @Transactional
    fun saveStudentType(type: StudentTypeDictionary): StudentTypeDictionary {
        return typeDictRepo.save(type)
    }

    @Transactional
    fun deleteStudentType(id: Long) {
        val type = typeDictRepo.findById(id).orElseThrow { RuntimeException("Type not found") }
        // Also delete associated fields
        val fields = formFieldRepo.findByStudentTypeCodeOrderBySortOrderAsc(type.typeCode)
        formFieldRepo.deleteAll(fields)
        typeDictRepo.delete(type)
    }

    fun getFieldsForType(typeCode: String): List<TypeFormField> {
        return formFieldRepo.findByStudentTypeCodeOrderBySortOrderAsc(typeCode)
    }

    @Transactional
    fun saveFormField(field: TypeFormField): TypeFormField {
        return formFieldRepo.save(field)
    }

    @Transactional
    fun deleteFormField(id: Long) {
        formFieldRepo.deleteById(id)
    }
}
