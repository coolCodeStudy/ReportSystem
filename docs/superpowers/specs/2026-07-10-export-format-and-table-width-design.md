# Export Format And Analysis Table Width Design

## Goal

Make report export clearer for teachers and keep assessment-analysis table proportions consistent across Word, WPS, and server-generated PDF.

## Export Interaction

- Keep the existing export-settings modal and textbook-outline controls.
- Replace the two footer commands with a Word/PDF format selector and one `导出报告` button.
- Default to Word because it preserves the established editable-report workflow.
- Show familiar Word and PDF icons inside the selector.
- Save the outline settings once, then navigate to the endpoint selected by the format control.
- While exporting, disable the single command and show the existing generating state.

The endpoints remain unchanged:

- Word: `GET /student/history/{recordId}/export`
- PDF: `GET /student/history/{recordId}/export/pdf`

## Analysis Table Layout

The generated DOCX currently stores 20% and 80% preferred cell widths but omits the table grid. Word and WPS infer the intended proportions, while LibreOffice creates an equal two-column grid during PDF conversion. This produces the observed 50%/50% layout.

The renderer will make the table geometry explicit:

- Use a fixed table layout.
- Set the table width to the available report content width in DXA units.
- Write a two-column `tblGrid` using 20% and 80% of that width.
- Set the analysis-row cell widths to the same DXA values.
- Preserve the merged full-width subject header and all existing colors, spacing, text, and status icons.

Absolute table geometry is preferred over percentage-only hints because it is interpreted consistently by Word, WPS, and LibreOffice.

## Scope

This change applies only to the generated assessment-analysis tables and the export modal. It does not change report data, endpoints, persistence, other document tables, or PDF conversion infrastructure.

## Verification

### Automated

- Frontend regression test asserts one format selector, Word selected by default, one export command, and both download routes.
- DOCX regression test asserts fixed table layout, an explicit 20%/80% grid, matching cell widths, and a full-width merged header.
- Existing report-generation and controller tests remain green.

### Rendered Output

- Generate Word and PDF from the same saved local report.
- Render the PDF to PNG.
- Confirm the analysis divider is near 20% of the usable table width rather than the page midpoint.
- Confirm headings, analysis text, icons, page breaks, fee page, headers, and footers remain readable.

## Success Criteria

- A teacher explicitly chooses Word or PDF before using one `导出报告` command.
- Word remains the default format.
- The assessment-analysis table uses the same 20%/80% column proportions in DOCX and PDF.
- No report content or existing export setting is lost.
