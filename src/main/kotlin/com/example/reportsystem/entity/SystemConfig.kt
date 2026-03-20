package com.example.reportsystem.entity

import javax.persistence.*

@Entity
@Table(name = "system_config")
class SystemConfig(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "config_key", nullable = false, unique = true)
    var configKey: String = "",

    @Column(name = "config_value", columnDefinition = "text")
    var configValue: String? = null
)
