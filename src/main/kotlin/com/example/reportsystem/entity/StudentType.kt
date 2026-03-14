package com.example.reportsystem.entity

enum class StudentType(val description: String) {
    INTL("国际学校"),
    TRANSITION_INTL("体制内转国际"),
    TRANSITION_HKDSE("体制内转HKDSE"),
    TRANSITION_HANGZHOU_INTL("体制内转杭州国际学校"),
    DOMESTIC("体制内");

    companion object {
        fun fromDescription(desc: String?): StudentType? {
            if (desc == null) return null
            return values().find { it.description == desc }
        }
    }
}
