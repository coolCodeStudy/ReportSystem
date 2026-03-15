package com.example.reportsystem.dto

data class StudentDto(
    val id: Long?,
    val name: String,
    val phone: String?,
    val age: Int?,
    val gender: String?,
    val school: String?,
    val grade: String?,
    val genderAgeInfo: String,
    val schoolOrTarget: String,
    val studentType: String,
    val latestAssessmentDate: String,
    val latestStudyGoal: String,
    val latestLevel: String,
    val dynamicData: String?
)
