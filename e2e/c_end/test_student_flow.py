import asyncio
import os
import random
import sys
from pathlib import Path

from playwright.async_api import async_playwright

ROOT_DIR = Path(__file__).resolve().parents[2]
sys.path.append(str(ROOT_DIR / "scripts"))

from qa_common import append_report, validate_docx  # noqa: E402


BASE_URL = os.getenv("BASE_URL", "http://localhost:18080")
QA_MODE = os.getenv("QA_MODE", "gate")
ARTIFACT_DIR = Path(os.getenv("QA_ARTIFACT_DIR", "build/qa-gate"))
DOCX_DIR = ARTIFACT_DIR / "docx"
SCREENSHOT_DIR = ARTIFACT_DIR / "screenshots"
TRACE_DIR = ARTIFACT_DIR / "traces"

LEVEL_BY_ASSESSMENT = {
    "Starters": "Pre-A1",
    "Movers": "A1",
    "Flyers": "A2-",
    "KET": "A2+",
    "PET": "B1-",
    "IELTS": "B2-",
    "TOEFL Junior": "B1+",
    "MAP": "B1-",
}

TARGET_SCHOOLS = ["Lingoland", "Wahaha", "International School", "Kings College", "High School"]
COMMENTS = [
    "该生基础知识扎实，整体掌握到位。",
    "需要针对词汇量做大量补充。",
    "信息提取能力表现较好。",
    "能够在引导下完成表达任务。",
    "课堂参与度较高，学习习惯稳定。",
]


def assessment_matrix() -> list[str]:
    raw = os.getenv("ASSESSMENT_MATRIX", "Starters,Movers,KET,PET" if QA_MODE == "gate" else "Starters,Movers,Flyers,KET,PET,IELTS,TOEFL Junior,MAP")
    return [item.strip() for item in raw.split(",") if item.strip()]


async def click_assessment_type(frame, assessment_type: str) -> None:
    label = frame.locator(f"label.form-check-label:has-text('{assessment_type}')").first
    if await label.count() > 0:
        await label.click()
        return

    clicked = await frame.locator("body").evaluate(
        """(body, assessmentType) => {
            const labels = Array.from(body.ownerDocument.querySelectorAll('label.form-check-label'));
            const label = labels.find(item => item.innerText.trim().includes(assessmentType));
            if (!label) return false;
            label.click();
            return true;
        }""",
        assessment_type,
    )
    if not clicked:
        raise AssertionError(f"Assessment type option not found: {assessment_type}")


async def assert_exam_type_linkage(frame, assessment_type: str) -> None:
    expected = assessment_type
    if assessment_type not in {"Starters", "Movers", "Flyers", "KET", "PET", "IELTS", "TOEFL Junior", "MAP"}:
        return

    subjects = await frame.locator("body").evaluate(
        """body => {
            const manager = body.ownerDocument.defaultView.globalWorkspaceManager;
            if (!manager || !Array.isArray(manager.activeSubjects)) return [];
            return manager.activeSubjects.map(subject => ({
                name: subject.name || subject.id,
                examType: subject.examType || '',
                options: manager.examTypeOptionsFor(subject.id).map(option => option.value)
            }));
        }"""
    )
    bad_subjects = [
        subject
        for subject in subjects
        if expected in subject.get("options", []) and subject.get("examType") != expected
    ]
    if bad_subjects:
        details = "; ".join(f"{item['name']}={item['examType']}" for item in bad_subjects)
        raise AssertionError(f"{assessment_type} did not become the default exam type for linked subjects: {details}")


async def fill_analysis_tabs(frame) -> None:
    tabs = frame.locator("#subjectTabs button.nav-link")
    tab_count = await tabs.count()
    if tab_count == 0:
        raise AssertionError("No subject tabs were rendered after choosing the assessment type.")

    for idx in range(tab_count):
        tab = tabs.nth(idx)
        tab_name = (await tab.inner_text()).strip()
        await tab.evaluate("node => node.click()")
        await asyncio.sleep(0.4)
        pane = frame.locator(".tab-pane").nth(idx)

        await pane.evaluate(
            """pane => {
                pane.querySelectorAll("input[type='number']").forEach((input, index) => {
                    const max = Number(input.getAttribute('max') || input.dataset.max || 100);
                    const value = Math.max(1, Math.round((Number.isFinite(max) ? max : 100) * (0.58 + index * 0.06)));
                    input.value = String(value);
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                    input.dispatchEvent(new Event('change', { bubbles: true }));
                });
                pane.querySelectorAll('textarea').forEach((textarea, index) => {
                    if (!textarea.value.trim()) {
                        textarea.value = ['阅读定位较稳定，需要继续扩大词汇量。', '表达能够完成基本任务，细节展开仍需练习。', '学习习惯良好，可以提高复盘频率。'][index % 3];
                        textarea.dispatchEvent(new Event('input', { bubbles: true }));
                    }
                });
                pane.querySelectorAll('.logic-row .btn-group button').forEach((button, index) => {
                    if (index % 2 === 0) button.click();
                });
                Array.from(pane.querySelectorAll('.cause-tag')).slice(0, 2).forEach(tag => tag.click());
            }"""
        )
        print(f"    -> filled subject tab: {tab_name}")


async def fill_teaching_plan(frame) -> None:
    await frame.locator("#tab-step3").evaluate("node => node.click()")
    await asyncio.sleep(1)
    step3 = frame.locator("#step3-content")
    await step3.evaluate(
        """root => {
            root.querySelectorAll('textarea').forEach((textarea, index) => {
                if (!textarea.value.trim()) {
                    textarea.value = [
                        '阶段1：夯实基础，建立稳定学习节奏。',
                        '围绕目标学校要求补足词汇、阅读和表达。',
                        '每周完成课堂复盘和错题整理。',
                        'E2E 巡检：确认长文本可以正常保存和导出。'
                    ][index % 4];
                    textarea.dispatchEvent(new Event('input', { bubbles: true }));
                }
            });
            const firstTextbook = root.querySelector('.dropdown-menu .form-check-input');
            if (firstTextbook) {
                firstTextbook.checked = true;
                firstTextbook.dispatchEvent(new Event('change', { bubbles: true }));
            }
        }"""
    )


async def save_workspace(frame, label: str) -> None:
    print(f"    -> saving {label}")
    await frame.locator("body").evaluate("node => node.ownerDocument.defaultView.saveWorkspaceData()")
    await asyncio.sleep(2)
    await frame.locator("body").evaluate(
        """body => {
            const swal = body.ownerDocument.querySelector('.swal2-container');
            if (swal) swal.remove();
        }"""
    )


async def export_report(page, frame, docx_path: Path) -> None:
    await frame.locator("#exportReportBtn").evaluate("node => node.click()")
    await asyncio.sleep(0.8)

    word_format = frame.locator("#reportExportFormatWord")
    if await word_format.count() > 0:
        await word_format.evaluate("node => { node.checked = true; node.dispatchEvent(new Event('change', { bubbles: true })); }")

    confirm_button = frame.locator("#confirmReportExportBtn")
    await confirm_button.wait_for(state="visible", timeout=30000)
    async with page.expect_download(timeout=120000) as download_info:
        await confirm_button.click()

    download = await download_info.value
    await download.save_as(str(docx_path))


async def create_student_and_open_workspace(page, assessment_type: str) -> str:
    rnd_id = random.randint(1000, 9999)
    student_name = f"QA-{assessment_type.replace(' ', '')}-{rnd_id}"
    age = random.randint(7, 14)
    grade = random.choice(["G1", "G2", "G3", "G4", "G5", "G6", "G7"])

    await page.goto(f"{BASE_URL}/", wait_until="networkidle")
    await page.click("button[data-bs-target='#studentRecordOffcanvas']")
    await page.fill("#sname", student_name)
    await page.fill("#sage", str(age))
    await page.select_option("#sgender", "男" if random.randint(0, 1) else "女")
    await page.fill("#sschool", random.choice(TARGET_SCHOOLS))
    await page.select_option("#sgrade", grade)
    await page.click("button:has-text('保存建档')")
    await asyncio.sleep(2)
    await page.keyboard.press("Escape")

    await page.fill("input[name='q']", student_name)
    await asyncio.sleep(1)
    await page.locator("button.view-history-btn").first.click()
    await asyncio.sleep(1)

    if await page.locator("#createNewRecordBtn").count() > 0:
        await page.locator("#createNewRecordBtn").click()
    else:
        await page.locator("button:has-text('为TA录入新测评')").first.click()
    await page.locator("#workspaceIframe").wait_for(timeout=30000)
    await asyncio.sleep(2)
    return student_name


async def run_single_assessment(browser, assessment_type: str) -> dict[str, str]:
    DOCX_DIR.mkdir(parents=True, exist_ok=True)
    SCREENSHOT_DIR.mkdir(parents=True, exist_ok=True)
    TRACE_DIR.mkdir(parents=True, exist_ok=True)

    context = await browser.new_context(accept_downloads=True, viewport={"width": 1440, "height": 900})
    await context.tracing.start(screenshots=True, snapshots=True, sources=True)
    page = await context.new_page()
    trace_path = TRACE_DIR / f"{assessment_type.replace(' ', '_')}.zip"

    try:
        print(f"🚀 [QA:{QA_MODE}] running assessment flow: {assessment_type}")
        student_name = await create_student_and_open_workspace(page, assessment_type)
        frame = page.locator("#workspaceIframe").content_frame

        await click_assessment_type(frame, assessment_type)
        await asyncio.sleep(1)

        level = LEVEL_BY_ASSESSMENT.get(assessment_type, "B1-")
        await frame.locator("#step1_level").select_option(level)
        await assert_exam_type_linkage(frame, assessment_type)
        await save_workspace(frame, "step 1")

        await frame.locator("#tab-step2").evaluate("node => node.click()")
        await asyncio.sleep(1)
        await fill_analysis_tabs(frame)
        await save_workspace(frame, "step 2")

        await fill_teaching_plan(frame)
        await save_workspace(frame, "step 3")

        docx_path = DOCX_DIR / f"{student_name}.docx"
        await export_report(page, frame, docx_path)
        if validate_docx(docx_path, ARTIFACT_DIR) != 0:
            raise AssertionError(f"DOCX quick check failed for {docx_path}")

        await context.tracing.stop()
        await context.close()
        append_report(ARTIFACT_DIR, f"## E2E {assessment_type}\n\n- Status: PASS\n- DOCX: `{docx_path}`\n\n")
        return {"assessment": assessment_type, "status": "PASS", "docx": str(docx_path)}
    except Exception as exc:
        screenshot_path = SCREENSHOT_DIR / f"{assessment_type.replace(' ', '_')}.png"
        try:
            await page.screenshot(path=str(screenshot_path), full_page=True)
        except Exception:
            pass
        try:
            await context.tracing.stop(path=str(trace_path))
        except Exception:
            pass
        await context.close()
        append_report(
            ARTIFACT_DIR,
            f"## E2E {assessment_type}\n\n- Status: FAIL\n- Error: `{exc}`\n- Screenshot: `{screenshot_path}`\n- Trace: `{trace_path}`\n\n",
        )
        return {"assessment": assessment_type, "status": "FAIL", "error": str(exc)}


async def run() -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(headless=True)
        results = []
        for assessment_type in assessment_matrix():
            results.append(await run_single_assessment(browser, assessment_type))
        await browser.close()

    failures = [result for result in results if result["status"] != "PASS"]
    summary_lines = ["## E2E Matrix Summary", "", "| Assessment | Status | Detail |", "| --- | --- | --- |"]
    for result in results:
        detail = result.get("docx") or result.get("error", "")
        summary_lines.append(f"| {result['assessment']} | {result['status']} | `{detail}` |")
    append_report(ARTIFACT_DIR, "\n".join(summary_lines) + "\n\n")

    if failures:
        failed_names = ", ".join(item["assessment"] for item in failures)
        raise SystemExit(f"E2E matrix failed for: {failed_names}")


if __name__ == "__main__":
    asyncio.run(run())
