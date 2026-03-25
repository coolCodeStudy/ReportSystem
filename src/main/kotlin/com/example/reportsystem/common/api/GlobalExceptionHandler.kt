package com.example.reportsystem.common.api

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import javax.validation.ConstraintViolationException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseResult<Nothing> {
        log.error("Unhandled Exception: ", e)
        return ResponseResult.error(500, "Server Error: ${e.message}")
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(e: IllegalArgumentException): ResponseResult<Nothing> {
        log.warn("Illegal Argument: ${e.message}")
        return ResponseResult.error(400, e.message ?: "Bad Request")
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseResult<Nothing> {
        val errors = e.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        log.warn("Validation Error: {}", errors)
        return ResponseResult.error(400, "Validation failed: $errors")
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(e: ConstraintViolationException): ResponseResult<Nothing> {
        log.warn("Constraint Violation: ${e.message}")
        return ResponseResult.error(400, e.message ?: "Validation failed")
    }
}
