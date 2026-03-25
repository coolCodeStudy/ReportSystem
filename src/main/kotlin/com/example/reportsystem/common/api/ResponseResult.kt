package com.example.reportsystem.common.api

data class ResponseResult<T>(
    val code: Int,
    val message: String,
    val data: T? = null
) {
    companion object {
        fun <T> success(data: T? = null, message: String = "Success"): ResponseResult<T> {
            return ResponseResult(200, message, data)
        }

        fun <T> error(code: Int = 500, message: String = "Internal Server Error", data: T? = null): ResponseResult<T> {
            return ResponseResult(code, message, data)
        }
    }
}
