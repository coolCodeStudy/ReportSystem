import asyncio
import random
from playwright.async_api import async_playwright

# Core Mapping Configuration — only subjects that have scores
SCORING_CONFIG = {
    "KET": {
        "听力": {"calcType": "SCORE", "total": 25, "rules": [
            {"max": 10, "level": "低于A1"},
            {"max": 16, "level": "A1"},
            {"max": 22, "level": "A2"},
            {"max": 25, "level": "B1"}
        ]},
        "阅读理解": {"calcType": "RATE", "total": 100, "rules": [
            {"max": 40.0, "level": "低于A1"},
            {"max": 65.0, "level": "A1"},
            {"max": 90.0, "level": "A2"},
            {"max": 100.0, "level": "B1"}
        ]},
        "口语": {"calcType": "SCORE", "total": 45, "rules": [
            {"max": 17, "level": "低于A1"},
            {"max": 26, "level": "A1"},
            {"max": 40, "level": "A2"},
            {"max": 45, "level": "B1"}
        ]},
        "写作": {"calcType": "SCORE", "total": 30, "rules": [
            {"max": 11, "level": "低于A1"},
            {"max": 17, "level": "A1"},
            {"max": 25, "level": "A2"},
            {"max": 30, "level": "B1"}
        ]}
    },
    "PET": {
        "听力": {"calcType": "SCORE", "total": 25, "rules": [
            {"max": 10, "level": "低于A2"},
            {"max": 17, "level": "A2"},
            {"max": 22, "level": "B1"},
            {"max": 25, "level": "B2"}
        ]},
        "阅读理解": {"calcType": "RATE", "total": 100, "rules": [
            {"max": 37.5, "level": "低于A2"},
            {"max": 68.75, "level": "A2"},
            {"max": 87.5, "level": "B1"},
            {"max": 100.0, "level": "B2"}
        ]},
        "口语": {"calcType": "SCORE", "total": 30, "rules": [
            {"max": 11, "level": "低于A2"},
            {"max": 17, "level": "A2"},
            {"max": 26, "level": "B1"},
            {"max": 30, "level": "B2"}
        ]},
        "写作": {"calcType": "SCORE", "total": 40, "rules": [
            {"max": 15, "level": "低于A2"},
            {"max": 23, "level": "A2"},
            {"max": 33, "level": "B1"},
            {"max": 40, "level": "B2"}
        ]}
    }
}

# Subjects that are non-scored but still need paper analysis filled
NON_SCORED_SUBJECTS = ["语言应用", "学习素养"]

target_school = ["Lingoland", "Wahaha", "International School", "Kings College", "High School"]

sample_comments = [
    "该生基础知识十分扎实，整体掌握到位。",
    "稍微落后于预期进度，但在可提升范围内。",
    "需要针对词汇量做大量补充。",
    "对信息提取能力表现非常出色。",
    "能够灵活运用所学知识进行实际交际。",
    "在课堂上表现出较强的参与度和学习积极性。",
]

async def fill_paper_analysis_and_causes(pane):
    """Fill paper analysis buttons and cause tags for a given tab pane."""
    rows = await pane.locator(".logic-row").element_handles()
    for row in rows:
        btns = await row.query_selector_all(".btn-group button")
        if len(btns) > 1:
            btn_index = random.choice([0, 1])
            await btns[btn_index].evaluate('node => node.click()')

        textareas = await row.query_selector_all("textarea")
        for ta in textareas:
            await ta.evaluate(f"node => node.value = '{random.choice(sample_comments)}'")
            await ta.evaluate("node => node.dispatchEvent(new Event('input', { bubbles: true }))")

    # Also click some cause analysis tags
    cause_tags = await pane.locator(".cause-tag").element_handles()
    if cause_tags:
        num_to_select = min(random.randint(1, 3), len(cause_tags))
        selected_indices = random.sample(range(len(cause_tags)), num_to_select)
        for idx in selected_indices:
            await cause_tags[idx].evaluate('node => node.click()')


async def run():
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        # Using desktop viewport for reliable tests
        context = await browser.new_context(viewport={'width': 1280, 'height': 800})
        page = await context.new_page()
        
        # Generation param
        rnd_id = str(random.randint(1000, 9999))
        student_name = f"SmartTest-{rnd_id}"
        age = random.randint(8, 15)
        grades = ["G3", "G4", "G5", "G6", "G7", "G8"]
        target_grade = random.choice(grades)
        target_school_name = random.choice(target_school)
        exam_type = random.choice(["KET", "PET"])
        
        print(f"🚀 [INIT] Setup complete: Student: {student_name} | Target: {target_grade} | Config: {exam_type}")

        # Navigate
        print("📡 Navigating to Students Management...")
        await page.goto("http://localhost:8080/")
        await asyncio.sleep(2)

        # Create student 
        print(f"👤 Creating Student {student_name}...")
        await page.click("button[data-bs-target='#studentRecordOffcanvas']")
        await asyncio.sleep(1)
        await page.fill("#sname", student_name)
        await page.fill("#sage", str(age))
        await page.select_option("#sgender", "男" if random.randint(0,1) else "女")
        await page.fill("#sschool", target_school_name)
        await page.select_option("#sgrade", target_grade)
        await page.click("button:has-text('保存建档')")
        await asyncio.sleep(2)

        # Dismiss swal just in case
        await page.keyboard.press("Escape")
        await asyncio.sleep(1)

        # Use fuzzy search to filter
        await page.fill("input[name='q']", student_name)
        await asyncio.sleep(1)
        
        # Open history
        print(f"📂 Opening History space...")
        await page.locator("button.view-history-btn").first.click()
        await asyncio.sleep(2)
        
        # New record
        print(f"➕ Creating New Assessment Record...")
        btn_count = await page.locator("#createNewRecordBtn").count()
        if btn_count > 0:
            await page.locator("#createNewRecordBtn").click()
        else:
            await page.locator("button:has-text('为TA录入新测评')").first.click()
        await asyncio.sleep(4)

        # ═══════════════════════════════════════════════════════════════
        # STEP 1: 基础信息与目标分析
        # ═══════════════════════════════════════════════════════════════
        print(f"📝 [Step 1] Inside Workspace for {exam_type}...")
        frame = page.locator("#workspaceIframe").content_frame
        
        await frame.locator(f"label.form-check-label:has-text('{exam_type}')").first.click()
        await asyncio.sleep(1)
        
        print("🎚️ Selecting target level...")
        levels_available = ["A1", "A2-", "A2+", "B1-", "B1+", "B2-", "B2+"]
        await frame.locator("#step1_level").select_option(random.choice(levels_available))
        await asyncio.sleep(0.5)

        print("💾 Saving Step 1...")
        await frame.locator("body").evaluate("node => node.ownerDocument.defaultView.saveWorkspaceData()")
        await asyncio.sleep(2)
        
        # ═══════════════════════════════════════════════════════════════
        # STEP 2: 测评分析 — ALL subjects
        # ═══════════════════════════════════════════════════════════════
        print(f"🎲 [Step 2] Simulating exam interaction across ALL subjects...")
        tabs = await frame.locator("#subjectTabs button.nav-link").element_handles()
        
        for i, tab in enumerate(tabs):
            tab_name = await tab.inner_text()
            tab_name = tab_name.strip()
            
            await tab.evaluate('node => node.click()')
            await asyncio.sleep(1)
            
            pane = frame.locator(".tab-pane").nth(i)
            
            # ── Scored subjects: fill score + paper analysis + causes ──
            if tab_name in SCORING_CONFIG.get(exam_type, {}):
                cfg = SCORING_CONFIG[exam_type][tab_name]
                max_score = cfg['total'] if cfg['total'] > 0 else 100
                rnd_score = random.randint(6, max_score)
                
                if tab_name == '阅读理解' and cfg['calcType'] == 'RATE':
                   rnd_score = round(random.uniform(25, 95), 1)
                   
                score_input = pane.locator("input[type='number']").first
                if await score_input.count() > 0:
                    await score_input.evaluate(f"node => node.value = '{rnd_score}'")
                    await score_input.evaluate("node => node.dispatchEvent(new Event('input', { bubbles: true }))")
                    await asyncio.sleep(0.5)
                    level_div = pane.locator(".auto-grade-display")
                    actual_lvl = await level_div.inner_text()
                    print(f"    -> [{tab_name}] Score = {rnd_score} => Level = {actual_lvl}")

            # ── Non-scored subjects (语言应用, 学习素养): only paper analysis + causes ──
            elif tab_name in NON_SCORED_SUBJECTS:
                print(f"    -> [{tab_name}] Non-scored subject — filling paper analysis & causes only")

            else:
                print(f"    -> [{tab_name}] Unknown subject — filling paper analysis & causes")

            # Fill paper analysis and cause tags for ALL subjects
            await fill_paper_analysis_and_causes(pane)
            await asyncio.sleep(0.3)

        # Save workspace data before moving to step 3
        print("💾 Saving Step 2 workspace data...")
        await frame.locator("body").evaluate("node => node.ownerDocument.defaultView.saveWorkspaceData()")
        await asyncio.sleep(3)
        # Dismiss any Swal overlay that might have appeared
        try:
            swal_container = frame.locator(".swal2-container")
            if await swal_container.count() > 0:
                await frame.locator("body").evaluate("node => { const s = node.ownerDocument.querySelector('.swal2-container'); if(s) s.remove(); }")
        except Exception:
            pass

        # ═══════════════════════════════════════════════════════════════
        # STEP 3: 语言教学安排
        # ═══════════════════════════════════════════════════════════════
        print("📚 [Step 3] Filling 语言教学安排...")
        
        # Switch to Step 3 via sidebar
        await frame.locator("#tab-step3").evaluate("node => node.click()")
        await asyncio.sleep(2)

        step3_content = frame.locator("#step3-content")

        # Fill course plan rows — the default template has 2 rows
        plan_rows = step3_content.locator("tbody tr")
        row_count = await plan_rows.count()
        print(f"    -> Found {row_count} course plan row(s)")
        
        if row_count > 0:
            # Fill first row
            row0 = plan_rows.nth(0)
            phase_ta = row0.locator("textarea").first
            if await phase_ta.count() > 0:
                current_val = await phase_ta.input_value()
                if not current_val.strip():
                    await phase_ta.fill("阶段1:\n基础课程")
            
            # Fill goal textarea (3rd textarea in row)
            goal_tas = row0.locator("textarea")
            goal_count = await goal_tas.count()
            if goal_count >= 3:
                goal_ta = goal_tas.nth(2)
                current_val = await goal_ta.input_value()
                if not current_val.strip():
                    await goal_ta.fill("1. 用轻松的方式引入，建立英语学习兴趣\n2. 拓展词汇量\n3. 基础语法学习")

        # Try to select a textbook from the dropdown using JS to avoid interception
        textbook_dropdowns = step3_content.locator("button[data-bs-toggle='dropdown']")
        dropdown_count = await textbook_dropdowns.count()
        if dropdown_count > 0:
            print("    -> Selecting textbooks from dropdown...")
            first_dropdown = textbook_dropdowns.first
            await first_dropdown.evaluate("node => node.click()")
            await asyncio.sleep(1)
            
            # Check available textbook checkboxes
            checkboxes = step3_content.locator(".dropdown-menu .form-check-input")
            cb_count = await checkboxes.count()
            if cb_count > 0:
                # Select first textbook
                await checkboxes.first.evaluate("node => { node.checked = true; node.dispatchEvent(new Event('change', {bubbles: true})); }")
                await asyncio.sleep(0.5)
                print(f"    -> Selected 1 of {cb_count} available textbook(s)")
            
            # Close dropdown by clicking elsewhere (via JS to avoid interception)
            await step3_content.locator("h5").first.evaluate("node => node.click()")
            await asyncio.sleep(0.5)

        # Fill new textareas for teaching strategy, checklist, frequency, and risk
        print("    -> Filling editable config fields: Approach, Checklist, Frequency, Risk")
        approach_ta = step3_content.locator("textarea[x-model='data.teachingApproach']")
        if await approach_ta.count() > 0:
            await approach_ta.fill("E2E 测试专属：针对该学生的独特教学思路。")
            await approach_ta.evaluate("node => node.dispatchEvent(new Event('input', { bubbles: true }))")
        
        checklist_ta = step3_content.locator("textarea[x-model='data.teachingChecklist']")
        if await checklist_ta.count() > 0:
            await checklist_ta.fill("E2E 测试定制清单：\n1. 助教课定制要求\n2. E2E测试打卡任务")
            await checklist_ta.evaluate("node => node.dispatchEvent(new Event('input', { bubbles: true }))")
        
        frequency_ta = step3_content.locator("textarea[x-model='data.courseFrequency']")
        if await frequency_ta.count() > 0:
            await frequency_ta.fill("E2E 测试定制频次：每周二次必修，一次选修。")
            await frequency_ta.evaluate("node => node.dispatchEvent(new Event('input', { bubbles: true }))")
            
        risk_ta = step3_content.locator("textarea[x-model='data.planRisk']")
        if await risk_ta.count() > 0:
            await risk_ta.fill("E2E 测试定制风险：学生可能存在作业拖延的风险。")
            await risk_ta.evaluate("node => node.dispatchEvent(new Event('input', { bubbles: true }))")

        # Save all changes using the global action bar
        print("💾 Saving all workspace data...")
        await frame.locator("body").evaluate("node => node.ownerDocument.defaultView.saveWorkspaceData()")
        await asyncio.sleep(3)

        # ═══════════════════════════════════════════════════════════════
        # EXPORT — Generate Word document
        # ═══════════════════════════════════════════════════════════════
        print(f"💾 Exporting Document...")
        async with page.expect_download(timeout=120000) as download_info:
            await frame.locator("button:has-text('导出报告')").evaluate('node => node.click()')
        
        download = await download_info.value
        save_path = f"test_{student_name}_final.docx"
        await download.save_as(save_path)
        print(f"✅ Success! Report exported to {save_path}")

        await browser.close()

if __name__ == "__main__":
    asyncio.run(run())

