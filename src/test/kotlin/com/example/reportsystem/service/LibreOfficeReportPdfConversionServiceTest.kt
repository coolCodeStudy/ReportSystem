package com.example.reportsystem.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LibreOfficeReportPdfConversionServiceTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `convert should reject requests when PDF export is disabled`() {
        val service = service(enabled = false, executable = "/missing/libreoffice")

        val error = catchThrowableOfType(
            { service.convert(byteArrayOf(1, 2, 3)) },
            ReportPdfConversionException::class.java
        )

        assertThat(error.reason).isEqualTo(ReportPdfConversionFailure.UNAVAILABLE)
        assertThat(workDirectories()).isEmpty()
    }

    @Test
    fun `convert should return generated PDF bytes and clean its work directory`() {
        val executable = executable(
            "success.sh",
            """
                outdir=""
                while [ "${'$'}#" -gt 0 ]; do
                    if [ "${'$'}1" = "--outdir" ]; then
                        shift
                        outdir="${'$'}1"
                    fi
                    shift
                done
                printf '%%PDF-1.4\nlinux-proof\n' > "${'$'}outdir/report.pdf"
            """.trimIndent()
        )
        val service = service(executable = executable)

        val result = service.convert("docx".toByteArray())

        assertThat(result.toString(Charsets.UTF_8)).startsWith("%PDF-1.4")
        assertThat(workDirectories()).isEmpty()
    }

    @Test
    fun `convert should report unavailable when executable cannot be started`() {
        val service = service(executable = tempDir.resolve("missing-libreoffice").toString())

        val error = catchThrowableOfType(
            { service.convert("docx".toByteArray()) },
            ReportPdfConversionException::class.java
        )

        assertThat(error.reason).isEqualTo(ReportPdfConversionFailure.UNAVAILABLE)
        assertThat(workDirectories()).isEmpty()
    }

    @Test
    fun `convert should report failed when LibreOffice exits with an error`() {
        val executable = executable("failure.sh", "exit 7")
        val service = service(executable = executable)

        val error = catchThrowableOfType(
            { service.convert("docx".toByteArray()) },
            ReportPdfConversionException::class.java
        )

        assertThat(error.reason).isEqualTo(ReportPdfConversionFailure.FAILED)
        assertThat(error.message).contains("exit code 7")
        assertThat(workDirectories()).isEmpty()
    }

    @Test
    fun `convert should time out and clean its work directory`() {
        val executable = executable("timeout.sh", "sleep 3")
        val service = service(executable = executable, timeoutSeconds = 1)

        val error = catchThrowableOfType(
            { service.convert("docx".toByteArray()) },
            ReportPdfConversionException::class.java
        )

        assertThat(error.reason).isEqualTo(ReportPdfConversionFailure.TIMEOUT)
        assertThat(workDirectories()).isEmpty()
    }

    @Test
    fun `convert should reject an empty PDF`() {
        val executable = executable(
            "empty.sh",
            """
                outdir=""
                while [ "${'$'}#" -gt 0 ]; do
                    if [ "${'$'}1" = "--outdir" ]; then
                        shift
                        outdir="${'$'}1"
                    fi
                    shift
                done
                : > "${'$'}outdir/report.pdf"
            """.trimIndent()
        )
        val service = service(executable = executable)

        val error = catchThrowableOfType(
            { service.convert("docx".toByteArray()) },
            ReportPdfConversionException::class.java
        )

        assertThat(error.reason).isEqualTo(ReportPdfConversionFailure.FAILED)
        assertThat(error.message).contains("non-empty PDF")
        assertThat(workDirectories()).isEmpty()
    }

    private fun service(
        enabled: Boolean = true,
        executable: String,
        timeoutSeconds: Long = 5
    ) = LibreOfficeReportPdfConversionService(
        enabled = enabled,
        executable = executable,
        timeoutSeconds = timeoutSeconds,
        maxConcurrent = 1,
        tempRoot = tempDir.toString()
    )

    private fun executable(name: String, body: String): String {
        val path = tempDir.resolve(name)
        Files.writeString(path, "#!/bin/sh\n$body\n")
        path.toFile().setExecutable(true)
        return path.toString()
    }

    private fun workDirectories(): List<Path> = Files.list(tempDir).use { paths ->
        paths.filter { Files.isDirectory(it) && it.fileName.toString().startsWith("reportsystem-pdf-") }
            .toList()
    }
}

