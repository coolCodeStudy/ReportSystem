package com.example.reportsystem.controller

import com.example.reportsystem.service.SystemDictionaryService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import io.mockk.just
import io.mockk.Runs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(AdminController::class)
class AdminControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var dictService: SystemDictionaryService

    @Test
    fun `getConfig should return 200 and JSON config when key exists`() {
        every { dictService.getGlobalConfig("GLOBAL_ASSESSMENT_DESCRIPTIONS") } returns """[{"name":"KET","description":"Desc"}]"""

        mockMvc.perform(get("/admin/api/config/GLOBAL_ASSESSMENT_DESCRIPTIONS"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.value").value("""[{"name":"KET","description":"Desc"}]"""))
    }

    @Test
    fun `getConfig should return empty string value when key does not exist but still 200`() {
        // Because the controller does: val value = dictService.getGlobalConfig(key) ?: ""
        every { dictService.getGlobalConfig("MISSING_KEY") } returns null

        mockMvc.perform(get("/admin/api/config/MISSING_KEY"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.value").value(""))
    }

    @Test
    fun `saveConfig should save config and return 200`() {
        every { dictService.saveGlobalConfig("NEW_KEY", "NewValue") } just Runs

        mockMvc.perform(
            post("/admin/api/config/NEW_KEY")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"value": "NewValue"}""")
        )
            .andExpect(status().isOk)

        verify(exactly = 1) { dictService.saveGlobalConfig("NEW_KEY", "NewValue") }
    }
}
