# ReportSystem Bug Discovery Agent

ReportSystem uses a two-tier QA agent:

- Daily Gate: fast deploy/merge gate for obvious regressions.
- Monthly Bug Hunt: high-intensity active bug discovery for hidden workflow, config, UI, and DOCX issues.

## Daily Gate

Run before deploy:

```bash
./gradlew test
scripts/qa_gate.sh
```

Default environment:

```bash
BASE_URL=http://localhost:18080
TEMPLATE_SYNC_SOURCE_BASE_URL=http://121.41.236.44:8080
QA_MODE=gate
QA_ARTIFACT_DIR=build/qa-gate
```

What it checks:

- Application health.
- Template sync from production `system_config` into the local test system.
- Config structure: assessment descriptions, subjects, analysis templates, cause labels, score rules, and `Pre-A1`.
- Student happy path E2E: create student, create assessment, fill scores/analysis, save, export DOCX.
- Regression coverage for Starters/Movers default linkage and unresolved DOCX placeholders.

Artifacts:

- `build/qa-gate/report.md`
- `build/qa-gate/docx/`
- `build/qa-gate/screenshots/`
- `build/qa-gate/traces/`

## Monthly Bug Hunt

Run manually or through GitHub Actions:

```bash
scripts/monthly_bug_hunt.sh
```

Default environment:

```bash
BASE_URL=http://localhost:18080
TEMPLATE_SYNC_SOURCE_BASE_URL=http://121.41.236.44:8080
QA_MODE=hunt
QA_ARTIFACT_DIR=build/bug-hunt
QA_DOCX_DEEP=1
QA_VISUAL_QA=1
```

What it expands:

- Assessment matrix: Starters, Movers, Flyers, KET, PET, IELTS, TOEFL Junior, MAP.
- Admin configuration patrol.
- Broad student workflow matrix.
- DOCX deep render through LibreOffice and full-page PNG preview through Poppler.
- Mechanical Visual QA: blank pages, page count, key section page mapping, and fee-page review hints.

Artifacts:

- `build/bug-hunt/report.md`
- `build/bug-hunt/docx/`
- `build/bug-hunt/docx-render/`
- `build/bug-hunt/screenshots/`
- `build/bug-hunt/traces/`

## Visual QA

Visual QA is intentionally part of Monthly Bug Hunt, not Daily Gate. It is slower and produces heavier artifacts, but it catches report experience issues that ordinary unit tests miss.

Current mechanical checks:

- Render every DOCX page to PNG.
- Flag blank or nearly blank rendered pages.
- Extract PDF page text and map important sections to page numbers.
- Add a review item when fee-page text extraction suggests `费用` and `课程价目表` may be split. This is intentionally not a hard failure because `课程价目表` often lives inside an image and PDF text extraction cannot reliably OCR it.
- Suppress the fee split hint when the `费用` page already contains substantial visual content, which usually means the price image is already on the same readable page.
- Save every page PNG so a human or AI reviewer can inspect the report visually.

Future AI vision review can build on these artifacts by reviewing only high-risk pages such as cover, fee page, assessment analysis, and teaching plan pages.

## Template Sync

Local data is test data. The QA agent treats production `system_config` as source of truth.

Before overwriting local config, the sync step writes a debug-only snapshot:

```text
build/qa-gate/system-config-snapshot-before-sync-*.json
build/bug-hunt/system-config-snapshot-before-sync-*.json
```

The snapshot is for investigation only. It is not used to restore local data automatically.

## Bug Memory Protocol

Every real bug fix must update [`bug-memory.md`](./bug-memory.md):

- Bug summary.
- User impact.
- Reproduction steps or affected workflow.
- Regression case added to Daily Gate or Monthly Bug Hunt.
- Fix commit or rollout reference.

This is the feedback loop: every bug found by a teacher or user becomes a regression the agent can catch next time.

## CI Integration

Deploy workflow:

1. Starts PostgreSQL.
2. Runs `./gradlew test`.
3. Installs Playwright.
4. Starts local ReportSystem.
5. Runs `scripts/qa_gate.sh`.
6. Uploads QA artifacts.
7. Builds JAR and deploys only if the gate passes.

Monthly workflow:

- Triggered by `workflow_dispatch`.
- Triggered monthly by schedule.
- Uploads bug-hunt artifacts for backlog creation.

Daily Gate blocks deploy. Monthly Bug Hunt does not replace human triage; it produces a better bug backlog before users find the issues.
