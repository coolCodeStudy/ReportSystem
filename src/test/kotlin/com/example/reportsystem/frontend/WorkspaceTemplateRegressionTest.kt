package com.example.reportsystem.frontend

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class WorkspaceTemplateRegressionTest {

    @Test
    fun `workspace save should persist not applicable paper analysis rows`() {
        val template = Files.readString(Paths.get("src/main/resources/templates/workspace.html"))

        assertThat(template).contains(
            "subjData.paperAnalysis[row.dimension] = { status: row.status, text: row.text.trim() };"
        )
        assertThat(template).doesNotContain(
            "if ((row.status !== 'NA' && row.status !== 'N/A') || row.text.trim() !== '')"
        )
    }
}
