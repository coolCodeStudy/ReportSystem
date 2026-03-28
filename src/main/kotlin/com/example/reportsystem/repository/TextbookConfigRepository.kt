package com.example.reportsystem.repository

import com.example.reportsystem.entity.TextbookConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TextbookConfigRepository : JpaRepository<TextbookConfig, Long> {
    fun findByBookName(bookName: String): TextbookConfig?
}
