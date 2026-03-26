package com.example.reportsystem.entity

import javax.persistence.*

@Entity
@Table(name = "teaching_plans")
class TeachingPlan(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "unit_code", nullable = false)
    var unitCode: String = "",

    @Column(name = "book_name", nullable = false)
    var bookName: String = "",

    @Column(name = "course_content", columnDefinition = "TEXT")
    var courseContent: String? = null,

    @Column(name = "learning_objectives", columnDefinition = "TEXT")
    var learningObjectives: String? = null,

    @Column(name = "teaching_duration")
    var teachingDuration: String? = null
)
