package com.example.reportsystem.entity

import javax.persistence.*

@Entity
@Table(name = "textbook_configs")
class TextbookConfig(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "book_name", nullable = false, unique = true)
    var bookName: String = "",

    @Column(name = "introduction", columnDefinition = "TEXT")
    var introduction: String = ""
)
