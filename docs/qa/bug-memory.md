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
| 2026-07-05 | DOCX cover leaked the template text `封面文案标题`. | Teachers would see unfinished template copy on the generated report cover. | `DocxExportGoldenSampleTest` and `scripts/qa_common.py docx` reject `封面文案标题`; final bug hunt verified Starters/KET/PET/etc. exports no longer contain it. | Local hotfix pending push |
| 2026-07-05 | IELTS exists in assessment descriptions but has no subject configuration. | Selecting IELTS renders no score/analysis tabs, so teachers cannot record IELTS assessment data. | `scripts/qa_common.py validate` reports `GLOBAL_SUBJECTS_type_fn1tzj`; E2E IELTS case fails with no subject tabs. | Open config bug |
| 2026-07-05 | Visual QA falsely flagged a readable fee page as split because `课程价目表` is image text and the following ABOUT US page is visually dense. | Monthly Bug Hunt could block or distract on a false DOCX layout issue even when WPS/Word looks acceptable. | `scripts/test_qa_common.py` keeps blank pages as hard failures, demotes fee split guesses to review items, and suppresses the hint when the fee page already contains substantial visual content. | Visual QA calibration pending push |

## Entry Template

| Date | Bug | Impact | Regression Case | Fix Commit |
| --- | --- | --- | --- | --- |
| YYYY-MM-DD |  |  |  |  |
