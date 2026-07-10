# Server PDF Export Design

## Goal

Add a production-safe PDF export alongside the existing DOCX export. The PDF must preserve the report's current visual design, including headers, footers, tables, images, the fee page, and the status icons.

## Product Behavior

- Keep both export formats available: Word for editing and PDF for sharing or printing.
- Generate both formats from the same saved assessment record and the same export-column settings.
- Do not expose the intermediate DOCX used during PDF conversion.
- Return a clear Chinese error message when PDF conversion is temporarily unavailable; Word export must continue to work.

## Architecture

Introduce a `ReportPdfConversionService` interface in the service layer. The first implementation invokes Linux LibreOffice in headless mode. Controllers request report bytes from the existing `DocxGeneratorService`, then pass those bytes to the conversion service when PDF is requested.

The conversion implementation will:

1. Create a unique temporary working directory per request.
2. Write the generated DOCX into that directory.
3. Run LibreOffice with an isolated user profile and a bounded timeout.
4. Verify that a non-empty PDF was produced.
5. Return the PDF bytes.
6. Delete the temporary directory in a `finally` block.

This interface keeps the conversion engine replaceable. Aspose.Words or another engine can be introduced later without changing controllers or browser behavior.

## Linux Runtime

Production must provide:

- LibreOffice headless binaries.
- Fontconfig.
- Open-source Chinese fonts from the Noto CJK family.
- An emoji/symbol font capable of rendering the existing status characters.

The deployment workflow will install or verify these dependencies before restarting the application. Startup health remains independent of LibreOffice so a converter problem does not take down normal application use or Word export.

## API And UI

- Preserve `GET /student/history/{recordId}/export` as the DOCX endpoint.
- Add `GET /student/history/{recordId}/export/pdf` for PDF.
- Reuse the same optional `columns` query parameter.
- Present two explicit commands in the export UI: `导出 Word` and `导出 PDF`.
- Disable only the selected command while its request is running.

The existing demo-only `/report/pdf` endpoint is not part of the real report flow and should not be reused as the implementation.

## Failure Handling

- Apply a conversion timeout so a hung LibreOffice process cannot exhaust request threads.
- Limit concurrent conversions to protect the current 512 MB JVM deployment.
- Log the record ID, conversion duration, exit code, and sanitized stderr.
- Never log report contents.
- Return HTTP 503 for converter-unavailable or timeout failures.
- Return HTTP 500 only for unexpected generation failures.

## Verification Strategy

### Automated

- Unit-test controller routing, media type, filename, and converter interaction with MockK.
- Unit-test temporary-directory cleanup, timeout handling, missing executable handling, and non-zero exit codes.
- Add an integration test that converts a representative generated DOCX on Linux when LibreOffice is available.
- Check PDF page count, non-empty text extraction, required section titles, and presence of status characters.
- Extend Monthly Bug Hunt to retain the DOCX, PDF, and page PNGs.

### Visual Acceptance

Before production deployment, convert the real Viola report in an Ubuntu/Linux environment and compare it against the accepted DOCX/WPS appearance. Review:

- page count and page breaks;
- header and footer placement;
- Chinese font substitution and line wrapping;
- tables and long analysis text;
- teaching-plan and fee pages;
- image dimensions and cropping;
- visible status icons.

Any missing icon, clipped text, unexpected blank page, table overflow, or material section movement blocks deployment.

## Rollout

1. Build and validate the converter in a local Linux container.
2. Run the representative-report visual acceptance check.
3. Add the PDF command behind a configuration flag, disabled by default.
4. Install and verify runtime dependencies on ECS.
5. Enable PDF export and run a production smoke test.
6. Keep DOCX export as the fallback during rollout.

## Success Criteria

- Teachers can download either DOCX or PDF from the same saved report.
- PDF export requires no local WPS or Word conversion.
- Existing color status icons are visible in the PDF.
- The representative report passes automated and human visual checks on Linux.
- Conversion failures do not affect saving, reopening, or Word export.

