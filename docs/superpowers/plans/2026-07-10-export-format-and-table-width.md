# Export Format And Analysis Table Width Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace separate Word/PDF commands with one explicit format selection flow and keep assessment-analysis columns at 20%/80% in DOCX and server-generated PDF.

**Architecture:** The browser modal owns format selection and maps it to the two existing endpoints. `DocxAssessmentAnalysisRenderer` owns interoperable table geometry by emitting a fixed-width table, a fixed layout, an explicit grid, and matching cell widths before LibreOffice sees the document.

**Tech Stack:** Kotlin, Spring Boot 2.7, Apache POI XWPF, JUnit 5, AssertJ, vanilla JavaScript, Bootstrap 5, LibreOffice, Poppler.

## Global Constraints

- Word is selected by default.
- Keep `GET /student/history/{recordId}/export` and `GET /student/history/{recordId}/export/pdf` unchanged.
- Keep the existing textbook-outline controls and persistence behavior.
- The assessment-analysis table must use 20%/80% columns in Word, WPS, and LibreOffice PDF.
- Do not change report data, controller behavior, other document tables, or PDF conversion infrastructure.

---

### Task 1: Single Export Command With Format Selection

**Files:**
- Modify: `src/test/kotlin/com/example/reportsystem/frontend/ReportExportTemplateRegressionTest.kt`
- Modify: `src/main/resources/static/js/report-export.js`

**Interfaces:**
- Consumes: existing `ReportExport.open(options)` and existing Word/PDF download URLs.
- Produces: radio controls `reportExportFormatWord` and `reportExportFormatPdf`, and command button `confirmReportExportBtn`.

- [ ] **Step 1: Write the failing frontend regression test**

Replace the current separate-command assertion with assertions that the script contains:

```kotlin
assertThat(script).contains(
    "reportExportFormatWord",
    "reportExportFormatPdf",
    "name=\"reportExportFormat\"",
    "value=\"word\" checked",
    "confirmReportExportBtn",
    "导出报告",
    "/student/history/${'$'}{recordId}/export/pdf"
)
assertThat(script).doesNotContain(
    "exportWordReportBtn",
    "exportPdfReportBtn"
)
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew test --tests com.example.reportsystem.frontend.ReportExportTemplateRegressionTest
```

Expected: FAIL because the old modal still exposes `exportWordReportBtn` and `exportPdfReportBtn`.

- [ ] **Step 3: Implement the format selector and single command**

In `report-export.js`:

```javascript
const exportButtonId = 'confirmReportExportBtn';
const formatName = 'reportExportFormat';
```

Place a Bootstrap segmented radio group at the top of the modal body:

```html
<fieldset class="mb-4">
    <legend class="fw-bold fs-6 mb-2">导出格式</legend>
    <div class="btn-group w-100" role="group" aria-label="导出格式">
        <input type="radio" class="btn-check" name="reportExportFormat" id="reportExportFormatWord" value="word" checked>
        <label class="btn btn-outline-primary" for="reportExportFormatWord"><i class="bi bi-file-earmark-word me-1"></i>Word</label>
        <input type="radio" class="btn-check" name="reportExportFormat" id="reportExportFormatPdf" value="pdf">
        <label class="btn btn-outline-primary" for="reportExportFormatPdf"><i class="bi bi-file-earmark-pdf me-1"></i>PDF</label>
    </div>
</fieldset>
```

Replace both footer commands with:

```html
<button type="button" class="btn btn-primary px-4 shadow-sm" id="confirmReportExportBtn">
    <i class="bi bi-download me-1"></i>导出报告
</button>
```

At every modal open, reset Word as selected. On command click, read the checked `reportExportFormat` value, save the outline settings once, and navigate to the corresponding existing endpoint. Apply loading state only to `confirmReportExportBtn`.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the same Gradle command. Expected: PASS.

- [ ] **Step 5: Commit the frontend behavior**

```bash
git add src/test/kotlin/com/example/reportsystem/frontend/ReportExportTemplateRegressionTest.kt src/main/resources/static/js/report-export.js
git commit -m "Improve report export format selection"
```

### Task 2: Fixed Analysis Table Geometry

**Files:**
- Modify: `src/test/kotlin/com/example/reportsystem/service/DocxGeneratorServiceTest.kt`
- Modify: `src/main/kotlin/com/example/reportsystem/service/docx/DocxAssessmentAnalysisRenderer.kt`

**Interfaces:**
- Consumes: generated analysis table with subject header and `卷面分析` row.
- Produces: table width `8306` DXA, fixed layout, grid columns `1661` and `6645` DXA, and matching cell widths.

- [ ] **Step 1: Write the failing DOCX geometry test**

Generate a report containing paper analysis, locate the table whose cells contain `卷面分析`, then assert:

```kotlin
assertThat(analysisTable.ctTbl.tblPr.tblW.type).isEqualTo(STTblWidth.DXA)
assertThat(analysisTable.ctTbl.tblPr.tblW.w.toLong()).isEqualTo(8306L)
assertThat(analysisTable.ctTbl.tblPr.tblLayout.type).isEqualTo(STTblLayoutType.FIXED)
assertThat(analysisTable.ctTbl.tblGrid.gridColList.map { it.w.toLong() })
    .containsExactly(1661L, 6645L)

val analysisRow = analysisTable.rows.first { row -> row.tableCells.any { it.text == "卷面分析" } }
assertThat(analysisRow.getCell(0).ctTc.tcPr.tcW.w.toLong()).isEqualTo(1661L)
assertThat(analysisRow.getCell(1).ctTc.tcPr.tcW.w.toLong()).isEqualTo(6645L)
assertThat(analysisTable.getRow(0).getCell(0).ctTc.tcPr.gridSpan.`val`.toInt()).isEqualTo(2)
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew test --tests 'com.example.reportsystem.service.DocxGeneratorServiceTest.generateDocx should lock assessment analysis columns for PDF conversion'
```

Expected: FAIL because the table uses percentage widths and has no explicit `tblGrid`.

- [ ] **Step 3: Implement explicit fixed geometry**

Add renderer constants:

```kotlin
private const val ANALYSIS_TABLE_WIDTH = 8306L
private const val ANALYSIS_LABEL_COL_WIDTH = 1661L
private const val ANALYSIS_CONTENT_COL_WIDTH = ANALYSIS_TABLE_WIDTH - ANALYSIS_LABEL_COL_WIDTH
```

Add a private helper that sets `tblW` to DXA, sets `tblLayout` to `FIXED`, clears and recreates two `tblGrid` columns, and gives the initially merged header cell the full table width. Call it immediately after creating each analysis table.

Replace percentage `tcW` creation on the paper-analysis row with:

```kotlin
DocxStyleUtils.setCellWidth(c10, ANALYSIS_LABEL_COL_WIDTH)
DocxStyleUtils.setCellWidth(c11, ANALYSIS_CONTENT_COL_WIDTH)
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the same focused Gradle command. Expected: PASS.

- [ ] **Step 5: Run all DOCX generator tests**

```bash
./gradlew test --tests com.example.reportsystem.service.DocxGeneratorServiceTest
```

Expected: PASS.

- [ ] **Step 6: Commit the table fix**

```bash
git add src/test/kotlin/com/example/reportsystem/service/DocxGeneratorServiceTest.kt src/main/kotlin/com/example/reportsystem/service/docx/DocxAssessmentAnalysisRenderer.kt
git commit -m "Lock analysis table widths for PDF export"
```

### Task 3: Integrated And Visual Verification

**Files:**
- Modify: `docs/qa/bug-memory.md`
- Generate temporarily: `tmp/pdfs/export-width-verification/report.docx`
- Generate temporarily: `tmp/pdfs/export-width-verification/report.pdf`
- Generate temporarily: `tmp/pdfs/export-width-verification/page-*.png`

**Interfaces:**
- Consumes: local record `101`, Word endpoint, PDF endpoint, LibreOffice, and Poppler.
- Produces: test evidence and a durable regression-memory entry.

- [ ] **Step 1: Run the full automated suite**

```bash
./gradlew test --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Start the updated app on a free local port**

```bash
REPORT_PDF_ENABLED=true REPORT_PDF_EXECUTABLE=/opt/homebrew/bin/soffice ./gradlew bootRun -PlocalPort=18081
```

Expected: Tomcat starts on port `18081`.

- [ ] **Step 3: Export matching Word and PDF artifacts**

```bash
mkdir -p tmp/pdfs/export-width-verification
curl -fsS http://localhost:18081/student/history/101/export -o tmp/pdfs/export-width-verification/report.docx
curl -fsS http://localhost:18081/student/history/101/export/pdf -o tmp/pdfs/export-width-verification/report.pdf
pdftoppm -png -r 144 tmp/pdfs/export-width-verification/report.pdf tmp/pdfs/export-width-verification/page
```

Expected: valid non-empty DOCX/PDF files and rendered PNG pages.

- [ ] **Step 4: Verify the rendered table and modal**

Inspect the assessment-analysis page PNG and confirm the divider is approximately 20% across the table, text is not clipped, status icons remain visible, and the next section starts cleanly. Open the export modal and confirm Word is selected by default, PDF can be selected, and only one `导出报告` command is visible.

- [ ] **Step 5: Record the regression**

Append a `LibreOffice analysis column reflow` entry to `docs/qa/bug-memory.md` with root cause (missing fixed grid), impact, fixed layout requirement, and the DOCX geometry regression test.

- [ ] **Step 6: Clean temporary investigation artifacts and commit memory**

```bash
rm -rf tmp/pdfs/width-investigation tmp/pdfs/export-width-verification
git add docs/qa/bug-memory.md
git commit -m "Record PDF analysis width regression"
```

- [ ] **Step 7: Final clean-tree verification**

```bash
git status --short
git diff --check HEAD~3..HEAD
```

Expected: no unexpected working-tree changes and no whitespace errors.
