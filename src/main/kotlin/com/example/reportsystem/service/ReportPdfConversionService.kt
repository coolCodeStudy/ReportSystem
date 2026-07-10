package com.example.reportsystem.service

interface ReportPdfConversionService {
    fun convert(docxBytes: ByteArray): ByteArray
}

enum class ReportPdfConversionFailure {
    UNAVAILABLE,
    TIMEOUT,
    FAILED
}

class ReportPdfConversionException(
    val reason: ReportPdfConversionFailure,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

