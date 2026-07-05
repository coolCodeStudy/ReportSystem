package com.example.reportsystem.config

import com.example.reportsystem.entity.SystemConfig
import com.example.reportsystem.repository.StudentTypeDictionaryRepository
import com.example.reportsystem.repository.SystemConfigRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SystemInitRunnerTest {

    private val studentTypeDictionaryRepository: StudentTypeDictionaryRepository = mockk(relaxed = true)
    private val systemConfigRepository: SystemConfigRepository = mockk()

    @Test
    fun `run should seed default course plan configs when missing`() {
        val savedConfigs = mutableListOf<SystemConfig>()
        every { systemConfigRepository.findByConfigKey(any()) } returns null
        every { systemConfigRepository.save(any<SystemConfig>()) } answers {
            firstArg<SystemConfig>().also { savedConfigs.add(it) }
        }
        every { studentTypeDictionaryRepository.count() } returns 1

        SystemInitRunner(studentTypeDictionaryRepository, systemConfigRepository).run()

        val coursePlan = savedConfigs.firstOrNull { it.configKey == "GLOBAL_COURSE_PLAN_DEFAULT" }
        val coursePlanNote = savedConfigs.firstOrNull { it.configKey == "GLOBAL_COURSE_PLAN_NOTE_DEFAULT" }

        assertThat(coursePlan?.configValue).contains("阶段1", "基础课程", "阶段2", "中高级课程")
        assertThat(coursePlanNote?.configValue).contains("课时浮动")
        verify { systemConfigRepository.findByConfigKey("GLOBAL_COURSE_PLAN_DEFAULT") }
        verify { systemConfigRepository.findByConfigKey("GLOBAL_COURSE_PLAN_NOTE_DEFAULT") }
    }

    @Test
    fun `run should fill blank course plan configs without overwriting non-empty values`() {
        val blankPlan = SystemConfig(configKey = "GLOBAL_COURSE_PLAN_DEFAULT", configValue = "")
        val existingNote = SystemConfig(configKey = "GLOBAL_COURSE_PLAN_NOTE_DEFAULT", configValue = "人工填写的备注")
        val savedConfigs = mutableListOf<SystemConfig>()
        every { systemConfigRepository.findByConfigKey(any()) } returns null
        every { systemConfigRepository.findByConfigKey("GLOBAL_COURSE_PLAN_DEFAULT") } returns blankPlan
        every { systemConfigRepository.findByConfigKey("GLOBAL_COURSE_PLAN_NOTE_DEFAULT") } returns existingNote
        every { systemConfigRepository.save(any<SystemConfig>()) } answers {
            firstArg<SystemConfig>().also { savedConfigs.add(it) }
        }
        every { studentTypeDictionaryRepository.count() } returns 1

        SystemInitRunner(studentTypeDictionaryRepository, systemConfigRepository).run()

        assertThat(blankPlan.configValue).contains("阶段1", "基础课程")
        assertThat(existingNote.configValue).isEqualTo("人工填写的备注")
        assertThat(savedConfigs).contains(blankPlan)
        assertThat(savedConfigs).doesNotContain(existingNote)
    }
}
