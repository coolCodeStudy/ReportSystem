# Server PDF Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Linux-backed PDF export beside the existing DOCX export while preserving the report's current layout and status icons.

**Architecture:** Continue generating one canonical DOCX with `DocxGeneratorService`, then pass its bytes to a replaceable `ReportPdfConversionService`. The first implementation runs LibreOffice headlessly in an isolated temporary directory, while the controller and browser expose separate Word and PDF downloads.

**Tech Stack:** Kotlin 1.6, Spring Boot 2.7, Apache POI, JUnit 5, MockK, LibreOffice headless, Docker/Ubuntu 24.04, Poppler visual checks.

## Global Constraints

- Preserve the existing DOCX export behavior and endpoint.
- Keep Word export usable when PDF conversion is disabled or unavailable.
- Use Linux LibreOffice only after the representative Viola report passes visual review.
- Keep conversion temporary files isolated per request and always clean them up.
- Bound conversion time and concurrency for the current 512 MB JVM deployment.
- Do not push or deploy without an explicit user request and a passing `./gradlew test` run.

---

## File Map

- Create `src/main/kotlin/com/example/reportsystem/service/ReportPdfConversionService.kt`: engine-neutral PDF conversion contract and typed conversion exception.
- Create `src/main/kotlin/com/example/reportsystem/service/LibreOfficeReportPdfConversionService.kt`: Linux headless conversion, timeout, concurrency, and cleanup.
- Create `src/test/kotlin/com/example/reportsystem/service/LibreOfficeReportPdfConversionServiceTest.kt`: conversion service regression tests.
- Modify `src/main/kotlin/com/example/reportsystem/controller/StudentController.kt`: real historic-report PDF endpoint.
- Modify `src/test/kotlin/com/example/reportsystem/controller/StudentControllerTest.kt`: PDF response and failure tests.
- Modify `src/main/resources/static/js/report-export.js`: separate Word and PDF export commands.
- Create `src/test/kotlin/com/example/reportsystem/frontend/ReportExportTemplateRegressionTest.kt`: browser-script contract checks.
- Modify `src/main/resources/application.properties`: converter feature flag, executable, timeout, and concurrency settings.
- Create `qa/pdf-linux/Dockerfile`: reproducible Ubuntu conversion environment.
- Create `scripts/verify_linux_pdf.sh`: convert a supplied DOCX and retain PDF/PNG artifacts.
- Modify `scripts/qa_common.py`: detect PDF pages where status characters exist in text but are visually absent.
- Modify `scripts/test_qa_common.py`: cover the new status-icon visual check.
- Modify `.github/workflows/deploy.yml`: verify/install the Linux PDF runtime before enabling the systemd flag.
- Modify `docs/qa/bug-memory.md`: record the WPS PDF status-icon regression and its automated coverage.

---

### Task 1: Linux Representative-Report Proof

**Files:**
- Create: `qa/pdf-linux/Dockerfile`
- Create: `scripts/verify_linux_pdf.sh`
- Input: `/Users/lishaocheng/Downloads/Viola_历史记录测评报告.docx`
- Output: `build/pdf-linux-proof/`

**Interfaces:**
- Consumes: one DOCX path as the first script argument.
- Produces: `report.pdf`, page PNGs, `pdfinfo.txt`, `pdffonts.txt`, and `text.txt` under `build/pdf-linux-proof/`.

- [ ] **Step 1: Add the reproducible Linux converter image**

```dockerfile
FROM ubuntu:24.04

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        libreoffice-writer fonts-noto-cjk fonts-noto-color-emoji poppler-utils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /work
```

- [ ] **Step 2: Add the proof script**

The script must resolve the input path, create `build/pdf-linux-proof`, build `reportsystem-pdf-linux-proof`, mount the DOCX read-only as `/input/report.docx`, and run:

```bash
libreoffice --headless \
  -env:UserInstallation=file:///tmp/lo-profile \
  --convert-to pdf \
  --outdir /output \
  /input/report.docx
pdfinfo /output/report.pdf > /output/pdfinfo.txt
pdffonts /output/report.pdf > /output/pdffonts.txt
pdftotext -layout /output/report.pdf /output/text.txt
pdftoppm -png -r 120 /output/report.pdf /output/page
```

- [ ] **Step 3: Run the Linux proof**

Run:

```bash
scripts/verify_linux_pdf.sh "/Users/lishaocheng/Downloads/Viola_历史记录测评报告.docx"
```

Expected: exit code `0`, a non-empty `build/pdf-linux-proof/report.pdf`, and page PNGs.

- [ ] **Step 4: Review blocking visual criteria**

Render and inspect the assessment pages, teaching-plan pages, and fee page. Stop implementation if any status icon is absent, a table is clipped, a page is unexpectedly blank, or the fee image is split from its heading.

- [ ] **Step 5: Commit the proof harness**

```bash
git add qa/pdf-linux/Dockerfile scripts/verify_linux_pdf.sh
git commit -m "Add Linux PDF conversion proof harness"
```

---

### Task 2: LibreOffice Conversion Service

**Files:**
- Create: `src/main/kotlin/com/example/reportsystem/service/ReportPdfConversionService.kt`
- Create: `src/main/kotlin/com/example/reportsystem/service/LibreOfficeReportPdfConversionService.kt`
- Test: `src/test/kotlin/com/example/reportsystem/service/LibreOfficeReportPdfConversionServiceTest.kt`

**Interfaces:**
- Produces: `fun convert(docxBytes: ByteArray): ByteArray`.
- Throws: `ReportPdfConversionException` with `UNAVAILABLE`, `TIMEOUT`, or `FAILED` reason.

- [ ] **Step 1: Write failing contract and service tests**

Cover disabled conversion, missing executable, successful conversion, non-zero exit, timeout, empty PDF, and temporary-directory cleanup. Use a temporary executable shell script in each process test so no installed LibreOffice is required by unit tests.

```kotlin
assertThatThrownBy { service.convert(byteArrayOf(1)) }
    .isInstanceOf(ReportPdfConversionException::class.java)
    .extracting("reason")
    .isEqualTo(ReportPdfConversionFailure.UNAVAILABLE)
```

- [ ] **Step 2: Run the focused test and verify failure**

```bash
./gradlew test --tests com.example.reportsystem.service.LibreOfficeReportPdfConversionServiceTest
```

Expected: FAIL because the conversion types do not exist.

- [ ] **Step 3: Add the engine-neutral contract**

```kotlin
interface ReportPdfConversionService {
    fun convert(docxBytes: ByteArray): ByteArray
}

enum class ReportPdfConversionFailure { UNAVAILABLE, TIMEOUT, FAILED }

class ReportPdfConversionException(
    val reason: ReportPdfConversionFailure,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
```

- [ ] **Step 4: Implement the LibreOffice service**

Use Spring properties `report.pdf.enabled`, `report.pdf.executable`, `report.pdf.timeout-seconds`, and `report.pdf.max-concurrent`. Acquire a fair semaphore, create `Files.createTempDirectory("reportsystem-pdf-")`, redirect stdout/stderr to files, invoke LibreOffice with an isolated `UserInstallation`, verify `report.pdf`, and delete the directory recursively in `finally`.

- [ ] **Step 5: Run the focused test and verify pass**

```bash
./gradlew test --tests com.example.reportsystem.service.LibreOfficeReportPdfConversionServiceTest
```

Expected: PASS.

- [ ] **Step 6: Commit the service**

```bash
git add src/main/kotlin/com/example/reportsystem/service/ReportPdfConversionService.kt \
  src/main/kotlin/com/example/reportsystem/service/LibreOfficeReportPdfConversionService.kt \
  src/test/kotlin/com/example/reportsystem/service/LibreOfficeReportPdfConversionServiceTest.kt
git commit -m "Add LibreOffice report PDF conversion service"
```

---

### Task 3: Historic Report PDF Endpoint

**Files:**
- Modify: `src/main/kotlin/com/example/reportsystem/controller/StudentController.kt`
- Modify: `src/test/kotlin/com/example/reportsystem/controller/StudentControllerTest.kt`

**Interfaces:**
- Consumes: `ReportPdfConversionService.convert(docxBytes)` from Task 2.
- Produces: `GET /student/history/{recordId}/export/pdf?columns=...` with `application/pdf`.

- [ ] **Step 1: Add failing controller tests**

Add the converter mock to the controller constructor and verify that the PDF endpoint generates the same DOCX parameters as Word export, invokes conversion once, returns `%PDF` bytes, and names the file `<student>_历史记录测评报告.pdf`. Add a missing-record `404` test and a conversion-unavailable `503` test.

- [ ] **Step 2: Run the focused controller tests and verify failure**

```bash
./gradlew test --tests com.example.reportsystem.controller.StudentControllerTest
```

Expected: FAIL because the constructor and PDF endpoint do not exist.

- [ ] **Step 3: Extract shared report generation and add the endpoint**

Create a private `generateHistoricReportDocx(record, columns): ByteArray` helper so Word and PDF cannot drift. Map `ReportPdfConversionException` to HTTP 503 with a short UTF-8 Chinese body; keep missing records as 404.

```kotlin
@GetMapping("/history/{recordId}/export/pdf")
fun exportHistoricReportPdf(
    @PathVariable recordId: Long,
    @RequestParam(required = false) columns: String?
): ResponseEntity<ByteArray>
```

- [ ] **Step 4: Run controller tests and verify pass**

```bash
./gradlew test --tests com.example.reportsystem.controller.StudentControllerTest
```

Expected: PASS.

- [ ] **Step 5: Commit the endpoint**

```bash
git add src/main/kotlin/com/example/reportsystem/controller/StudentController.kt \
  src/test/kotlin/com/example/reportsystem/controller/StudentControllerTest.kt
git commit -m "Add historic report PDF endpoint"
```

---

### Task 4: Word And PDF Export Commands

**Files:**
- Modify: `src/main/resources/static/js/report-export.js`
- Create: `src/test/kotlin/com/example/reportsystem/frontend/ReportExportTemplateRegressionTest.kt`

**Interfaces:**
- Consumes: current `ReportExport.open(options)` call sites unchanged.
- Produces: Word URL `/student/history/{id}/export` and PDF URL `/student/history/{id}/export/pdf`.

- [ ] **Step 1: Write a failing browser-script regression test**

Read `report-export.js` as text and assert it contains distinct `exportWordReportBtn`, `exportPdfReportBtn`, and `/export/pdf` contracts while no longer containing one generic `确认导出` command.

- [ ] **Step 2: Run the focused test and verify failure**

```bash
./gradlew test --tests com.example.reportsystem.frontend.ReportExportTemplateRegressionTest
```

Expected: FAIL because the two commands do not exist.

- [ ] **Step 3: Implement two explicit commands**

Replace the single confirm command with `导出 Word` and `导出 PDF`. Both must apply and save the same export settings before navigation. Only the clicked button enters a loading state. Use the existing Bootstrap icon set with `bi-file-earmark-word` and `bi-file-earmark-pdf`.

- [ ] **Step 4: Run the focused test and verify pass**

```bash
./gradlew test --tests com.example.reportsystem.frontend.ReportExportTemplateRegressionTest
```

Expected: PASS.

- [ ] **Step 5: Commit the UI**

```bash
git add src/main/resources/static/js/report-export.js \
  src/test/kotlin/com/example/reportsystem/frontend/ReportExportTemplateRegressionTest.kt
git commit -m "Add Word and PDF report export commands"
```

---

### Task 5: Runtime Configuration And Deployment Guard

**Files:**
- Modify: `src/main/resources/application.properties`
- Modify: `.github/workflows/deploy.yml`

**Interfaces:**
- Consumes environment variables `REPORT_PDF_ENABLED`, `REPORT_PDF_EXECUTABLE`, `REPORT_PDF_TIMEOUT_SECONDS`, and `REPORT_PDF_MAX_CONCURRENT`.
- Produces a systemd service with PDF disabled unless the deployment runtime check passes.

- [ ] **Step 1: Add application properties**

```properties
report.pdf.enabled=${REPORT_PDF_ENABLED:false}
report.pdf.executable=${REPORT_PDF_EXECUTABLE:libreoffice}
report.pdf.timeout-seconds=${REPORT_PDF_TIMEOUT_SECONDS:120}
report.pdf.max-concurrent=${REPORT_PDF_MAX_CONCURRENT:1}
```

- [ ] **Step 2: Add a deployment runtime check**

In the SSH deployment script, detect `apt-get`, `dnf`, or `yum`; install LibreOffice Writer/headless plus Noto CJK and Emoji fonts using the distribution's package names; run `fc-cache -f`; and verify `command -v libreoffice || command -v soffice`. Abort deployment before changing the systemd service if verification fails.

- [ ] **Step 3: Enable the service only after verification**

Add these systemd environment lines:

```ini
Environment=REPORT_PDF_ENABLED=true
Environment=REPORT_PDF_EXECUTABLE=/usr/bin/libreoffice
Environment=REPORT_PDF_TIMEOUT_SECONDS=120
Environment=REPORT_PDF_MAX_CONCURRENT=1
```

Resolve the actual executable path during deployment rather than assuming `/usr/bin/libreoffice` when only `soffice` exists.

- [ ] **Step 4: Validate workflow syntax and configuration tests**

Run:

```bash
./gradlew test
```

Expected: PASS. Inspect `.github/workflows/deploy.yml` for valid indentation and ensure the runtime check occurs before systemd restart.

- [ ] **Step 5: Commit runtime configuration**

```bash
git add src/main/resources/application.properties .github/workflows/deploy.yml
git commit -m "Configure Linux PDF export runtime"
```

---

### Task 6: Status-Icon Visual Regression

**Files:**
- Modify: `scripts/qa_common.py`
- Modify: `scripts/test_qa_common.py`
- Modify: `docs/qa/bug-memory.md`

**Interfaces:**
- Consumes: PDF page text and rendered PNGs already generated by `render_docx_for_deep_check`.
- Produces: a visual-QA failure when `✅` or `❗` exists in the PDF text layer but no corresponding visible colored/icon pixels appear near the analysis rows.

- [ ] **Step 1: Add a failing focused visual test**

Construct one synthetic page where extracted text contains `✅` and `❗` but the rendered image contains only black analysis text. Assert that the new check reports `status icons may be invisible`. Construct a second image with green and red status regions and assert no issue.

- [ ] **Step 2: Run Python QA tests and verify failure**

```bash
python3 -m unittest scripts/test_qa_common.py
```

Expected: FAIL because status-icon visibility is not checked.

- [ ] **Step 3: Implement the icon-visibility heuristic**

Only run the heuristic on pages whose extracted text contains `✅` or `❗`. Measure non-neutral green/red pixel presence in the central report-content area and report a review-blocking issue when both status characters exist but neither color family is visible. Include thresholds in the returned details for calibration.

- [ ] **Step 4: Run Python QA tests and verify pass**

```bash
python3 -m unittest scripts/test_qa_common.py
```

Expected: PASS.

- [ ] **Step 5: Record bug memory and commit**

Add the WPS/macOS PDF case, reproduction steps, fix path, and regression command to `docs/qa/bug-memory.md`.

```bash
git add scripts/qa_common.py scripts/test_qa_common.py docs/qa/bug-memory.md
git commit -m "Detect invisible PDF status icons"
```

---

### Task 7: End-To-End Verification And Handoff

**Files:**
- Verify: all files changed in Tasks 1-6.
- Artifacts: `build/pdf-linux-proof/` and `build/qa-gate/`.

**Interfaces:**
- Produces: a tested local implementation and reviewable PDF/PNG evidence; no push or production deployment in this task.

- [ ] **Step 1: Run all Kotlin tests**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run QA unit tests**

```bash
python3 -m unittest scripts/test_qa_common.py
```

Expected: all tests pass.

- [ ] **Step 3: Re-run the Linux representative proof**

```bash
scripts/verify_linux_pdf.sh "/Users/lishaocheng/Downloads/Viola_历史记录测评报告.docx"
```

Expected: PDF and PNG artifacts are regenerated successfully.

- [ ] **Step 4: Verify PDF content and visual pages**

Confirm the PDF contains `二、测评分析`, `四、费用`, `✅`, and `❗` in its extracted text. Inspect all pages flagged by visual QA and compare page count with the accepted reference.

- [ ] **Step 5: Check repository state**

```bash
git diff --check
git status --short
```

Expected: no whitespace errors; only intentional uncommitted user changes remain.

- [ ] **Step 6: Report outcome**

Give the user the Linux-produced PDF path and summarize any layout differences. Do not push or deploy until the user explicitly requests it.

