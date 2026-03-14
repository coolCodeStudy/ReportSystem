package com.example.reportsystem.entity

import javax.persistence.AttributeConverter
import javax.persistence.Converter

@Converter(autoApply = true)
class StudentTypeConverter : AttributeConverter<StudentType, String> {

    override fun convertToDatabaseColumn(attribute: StudentType?): String? {
        return attribute?.description
    }

    override fun convertToEntityAttribute(dbData: String?): StudentType? {
        return StudentType.fromDescription(dbData)
    }
}
