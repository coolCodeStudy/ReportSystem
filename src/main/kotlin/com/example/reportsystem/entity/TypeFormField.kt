package com.example.reportsystem.entity

import javax.persistence.*

@Entity
@Table(name = "type_form_field")
class TypeFormField(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "student_type_code", nullable = false)
    var studentTypeCode: String = "",

    @Column(name = "label_name", nullable = false)
    var labelName: String = "",

    @Column(name = "field_key", nullable = false)
    var fieldKey: String = "",

    @Column(name = "field_type", nullable = false)
    var fieldType: String = "TEXT", // TEXT, NUMBER, TEXTAREA, SELECT

    @Column(name = "options_json", columnDefinition = "TEXT")
    var optionsJson: String? = null, // JSON array for SELECT options

    @Column(name = "is_required", nullable = false)
    var isRequired: Boolean = false,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0
)
