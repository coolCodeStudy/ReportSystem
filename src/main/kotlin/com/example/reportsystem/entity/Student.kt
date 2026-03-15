package com.example.reportsystem.entity

import java.time.LocalDateTime
import javax.persistence.*

import org.hibernate.annotations.Where

@Entity
@Table(name = "student")
@Where(clause = "is_deleted = false")
class Student {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false)
    var name: String = ""

    var phone: String? = null

    var age: Int? = null

    var gender: String? = null

    var school: String? = null

    var grade: String? = null

    @Column(name = "student_type")
    @Convert(converter = StudentTypeConverter::class)
    var studentType: StudentType? = null

    @Column(name = "created_at")
    var createdAt: LocalDateTime = LocalDateTime.now()

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()

    @Column(name = "is_deleted")
    var isDeleted: Boolean = false

    @PrePersist
    fun prePersist() {
        createdAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
