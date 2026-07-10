package com.example.reportsystem.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

@Service
class LibreOfficeReportPdfConversionService(
    @Value("\${report.pdf.enabled:false}") private val enabled: Boolean,
    @Value("\${report.pdf.executable:libreoffice}") private val executable: String,
    @Value("\${report.pdf.timeout-seconds:120}") private val timeoutSeconds: Long,
    @Value("\${report.pdf.max-concurrent:1}") maxConcurrent: Int,
    @Value("\${report.pdf.temp-root:\${java.io.tmpdir}}") tempRoot: String
) : ReportPdfConversionService {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val conversionSlots = Semaphore(maxConcurrent.coerceAtLeast(1), true)
    private val tempRootPath = Path.of(tempRoot)

    override fun convert(docxBytes: ByteArray): ByteArray {
        if (!enabled) {
            throw ReportPdfConversionException(
                ReportPdfConversionFailure.UNAVAILABLE,
                "PDF export is disabled."
            )
        }

        try {
            conversionSlots.acquire()
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ReportPdfConversionException(
                ReportPdfConversionFailure.FAILED,
                "PDF conversion was interrupted before it started.",
                exception
            )
        }

        return try {
            convertInTemporaryDirectory(docxBytes)
        } finally {
            conversionSlots.release()
        }
    }

    private fun convertInTemporaryDirectory(docxBytes: ByteArray): ByteArray {
        Files.createDirectories(tempRootPath)
        val workDirectory = Files.createTempDirectory(tempRootPath, "reportsystem-pdf-")
        val startedAt = System.nanoTime()

        try {
            val input = workDirectory.resolve("report.docx")
            val output = workDirectory.resolve("report.pdf")
            val profile = Files.createDirectories(workDirectory.resolve("profile"))
            val stdout = workDirectory.resolve("stdout.log")
            val stderr = workDirectory.resolve("stderr.log")
            Files.write(input, docxBytes)

            val command = listOf(
                executable,
                "--headless",
                "--nologo",
                "--nodefault",
                "--nolockcheck",
                "--nofirststartwizard",
                "-env:UserInstallation=${profile.toUri()}",
                "--convert-to",
                "pdf",
                "--outdir",
                workDirectory.toString(),
                input.toString()
            )

            val process = try {
                ProcessBuilder(command)
                    .redirectOutput(stdout.toFile())
                    .redirectError(stderr.toFile())
                    .start()
            } catch (exception: IOException) {
                throw ReportPdfConversionException(
                    ReportPdfConversionFailure.UNAVAILABLE,
                    "LibreOffice could not be started.",
                    exception
                )
            }

            val completed = try {
                process.waitFor(timeoutSeconds.coerceAtLeast(1), TimeUnit.SECONDS)
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                stopProcess(process)
                throw ReportPdfConversionException(
                    ReportPdfConversionFailure.FAILED,
                    "PDF conversion was interrupted.",
                    exception
                )
            }

            if (!completed) {
                stopProcess(process)
                throw ReportPdfConversionException(
                    ReportPdfConversionFailure.TIMEOUT,
                    "LibreOffice PDF conversion timed out."
                )
            }

            if (process.exitValue() != 0) {
                val details = readDiagnostic(stderr)
                throw ReportPdfConversionException(
                    ReportPdfConversionFailure.FAILED,
                    "LibreOffice exited with exit code ${process.exitValue()}${details.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "."}"
                )
            }

            if (!Files.isRegularFile(output) || Files.size(output) == 0L) {
                throw ReportPdfConversionException(
                    ReportPdfConversionFailure.FAILED,
                    "LibreOffice did not produce a non-empty PDF."
                )
            }

            val result = Files.readAllBytes(output)
            logger.info(
                "PDF conversion completed in {} ms with {} bytes",
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                result.size
            )
            return result
        } catch (exception: ReportPdfConversionException) {
            throw exception
        } catch (exception: IOException) {
            throw ReportPdfConversionException(
                ReportPdfConversionFailure.FAILED,
                "PDF conversion workspace failed.",
                exception
            )
        } finally {
            deleteRecursively(workDirectory)
        }
    }

    private fun stopProcess(process: Process) {
        process.destroy()
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            process.destroyForcibly()
        }
    }

    private fun readDiagnostic(path: Path): String = runCatching {
        Files.readString(path).replace(Regex("\\s+"), " ").trim().take(500)
    }.getOrDefault("")

    private fun deleteRecursively(directory: Path) {
        runCatching {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }.onFailure { exception ->
            logger.warn("Could not fully clean PDF conversion workspace {}", directory, exception)
        }
    }
}

