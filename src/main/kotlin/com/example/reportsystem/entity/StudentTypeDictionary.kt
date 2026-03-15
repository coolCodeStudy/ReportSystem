package com.example.reportsystem.entity

import javax.persistence.*

@Entity
@Table(name = "student_type_dictionary")
class StudentTypeDictionary(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "type_code", nullable = false, unique = true)
    var typeCode: String = "",

    @Column(name = "type_name", nullable = false)
    var typeName: String = "",

    @Column(nullable = false)
    var status: String = "ACTIVE", // ACTIVE or INACTIVE

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0
)
