# QA Bug Memory

This file is the durable memory for real ReportSystem bugs. Every user-visible bug fix should add or update one entry here and link the regression case that prevents it from coming back.

## Completion Standard

- Bug is fixed.
- Regression case is added to Daily Gate or Monthly Bug Hunt.
- This bug memory entry is updated with reproduction steps and the fixing commit.

## Known Regressions

| Date | Bug | Impact | Regression Case | Fix Commit |
| --- | --- | --- | --- | --- |
| 2026-07-05 | Level dropdown missed `Pre-A1` for the top Lingoland/CEFR row. | K/Starters reports could not map the table's top row correctly. | `TemplateQaValidatorTest` and `scripts/qa_common.py validate` require `Pre-A1` in `GLOBAL_CAPABILITY_MATRIX_CSV`. | QA agent rollout |
| 2026-07-05 | Starters/Movers/Flyers assessment selection could fall back to KET/PET score templates. | Teachers could not reliably record scores for non-KET/PET assessment systems. | `e2e/c_end/test_student_flow.py` asserts selected assessment type becomes the linked default exam type for scored subjects. | QA agent rollout |
| 2026-07-05 | Deploy workflow skipped tests. | Regression bugs could reach production before teacher/user discovery. | `.github/workflows/deploy.yml` now runs Gradle tests and Daily Gate before `bootJar` and deploy. | QA agent rollout |
| 2026-07-05 | DOCX export could leave placeholders or unreadable section breaks. | Generated reports looked broken in WPS/Word. | `scripts/qa_common.py docx` checks exported OOXML for unresolved placeholders and key sections. | QA agent rollout |

## Entry Template

| Date | Bug | Impact | Regression Case | Fix Commit |
| --- | --- | --- | --- | --- |
| YYYY-MM-DD |  |  |  |  |
