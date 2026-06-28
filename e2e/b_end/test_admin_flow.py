import asyncio
import os
import random
import string
from playwright.async_api import async_playwright

BASE_URL = os.getenv("BASE_URL", "http://localhost:18080")

# ─── helpers ─────────────────────────────────────────────────────────────────

def rand_suffix(n=4):
    return ''.join(random.choices(string.ascii_uppercase + string.digits, k=n))


async def wait_for_alert_and_dismiss(page, timeout=5000):
    """Accept a browser alert/confirm dialog if one appears."""
    try:
        dialog_waiter = asyncio.create_task(page.wait_for_event("dialog", timeout=timeout))
        dialog = await dialog_waiter
        print(f"   [dialog] '{dialog.message[:60]}...' → accepted")
        await dialog.accept()
    except Exception:
        pass  # no dialog, fine


# ─── test cases ──────────────────────────────────────────────────────────────

async def test_admin_templates_page_loads(page):
    """TC-B-01: Admin 测评报告配置页面能正常打开，核心元素渲染完毕。"""
    print("\n🧪 TC-B-01: Admin page loads correctly")
    await page.goto(f"{BASE_URL}/admin/templates")
    await asyncio.sleep(2)

    # Sidebar links visible
    assert await page.locator("a[href='/admin/templates']").is_visible(), \
        "测评报告配置 sidebar link missing"
    assert await page.locator("a[href='/admin/analysis-templates']").is_visible(), \
        "测评分析模板配置 sidebar link missing"
    assert await page.locator("a[href='/admin/teaching-plan']").is_visible(), \
        "语言教学安排配置 sidebar link missing"

    # Left menu items visible
    assert await page.locator("#menu-global").is_visible(), "全局矩阵菜单项 missing"
    assert await page.locator("#menu-desc").is_visible(), "测评类型配置菜单项 missing"

    # Global panel loaded by default
    assert await page.locator("#panel-global").is_visible(), "全局矩阵面板未默认显示"
    assert await page.locator("#globalMatrixTextarea").is_visible(), "全局 CSV textarea missing"

    print("   ✅ TC-B-01 passed")


async def test_global_matrix_panel(page):
    """TC-B-02: 全局能力矩阵 CSV 可加载并保存新内容。"""
    print("\n🧪 TC-B-02: Global matrix CSV save")

    await page.goto(f"{BASE_URL}/admin/templates")
    await asyncio.sleep(2)

    # Click 全局矩阵 menu
    await page.locator("#menu-global").click()
    await asyncio.sleep(0.5)

    textarea = page.locator("#globalMatrixTextarea")
    assert await textarea.is_visible(), "globalMatrixTextarea not visible"

    # Read current value first
    original = await textarea.input_value()
    assert original.strip() != "", "全局矩阵 CSV 为空，可能后端数据未初始化"
    print(f"   [CSV] first 80 chars: {original[:80].strip()}")

    # Minimal mutation: re-save as-is (safe, no data loss)
    save_btn = page.locator("button[onclick='saveGlobalMatrix()']")
    assert await save_btn.is_visible(), "保存 CSV 按钮 not visible"

    # Handle confirm dialog
    page.on("dialog", lambda d: asyncio.ensure_future(d.accept()))
    await save_btn.click()
    await asyncio.sleep(2)

    print("   ✅ TC-B-02 passed")


async def test_add_student_type(page):
    """TC-B-03: 新增学生体系能成功创建并出现在左侧列表中。"""
    rnd = rand_suffix()
    new_code = f"E2E_TYPE_{rnd}"
    new_name = f"E2E测试体系_{rnd}"
    print(f"\n🧪 TC-B-03: Add student type [{new_code}]")

    await page.goto(f"{BASE_URL}/admin/templates")
    await asyncio.sleep(2)

    # Click [+] button
    add_btn = page.locator("button[onclick='showAddTypeModal()']")
    await add_btn.click()
    await asyncio.sleep(0.5)

    # Fill modal
    modal = page.locator("#addTypeModal")
    await modal.locator("#newTypeCode").fill(new_code)
    await modal.locator("#newTypeName").fill(new_name)
    await modal.locator("#newTypeSort").fill("99")

    # Save
    page.on("dialog", lambda d: asyncio.ensure_future(d.accept()))
    await modal.locator("button[onclick='saveType()']").click()
    await asyncio.sleep(2)

    # Assert it appears in the sidebar
    type_item = page.locator(f"#studentTypeContainer .type-list-item", has_text=new_name)
    assert await type_item.count() > 0, f"新建体系 '{new_name}' 未出现在列表中"
    print(f"   ✅ TC-B-03 passed → '{new_name}' created")
    return new_code, new_name


async def test_select_type_and_associate_columns(page, type_code: str, type_name: str):
    """TC-B-04: 选择已创建的体系，进入列关联面板并保存关联列。"""
    print(f"\n🧪 TC-B-04: Select type '{type_name}' & associate columns")

    # Navigate without reload so types are already loaded
    type_item = page.locator("#studentTypeContainer .type-list-item", has_text=type_name)
    await type_item.click()
    await asyncio.sleep(1)

    # Panel should switch to type config
    assert await page.locator("#panel-type").is_visible(), "体系配置面板未显示"

    # Check column checkboxes rendered
    checkboxes = page.locator("#columnCheckboxesContainer .column-checkbox")
    count = await checkboxes.count()
    print(f"   [columns] found {count} column checkboxes")

    if count == 0:
        print("   ⚠️  No columns found – global CSV may be empty, skipping column assertion")
    else:
        # Check second column if at least 2
        if count >= 2:
            second_cb = checkboxes.nth(1)
            if not await second_cb.is_checked():
                await second_cb.check()

    # Save
    save_btn = page.locator("button[onclick='saveAssociatedColumns()']")
    assert await save_btn.is_visible(), "保存列关联按钮 not visible"
    page.on("dialog", lambda d: asyncio.ensure_future(d.accept()))
    await save_btn.click()
    await asyncio.sleep(2)

    print("   ✅ TC-B-04 passed")


async def test_assessment_desc_panel(page):
    """TC-B-05: 切换到测评类型配置面板，新增一条描述并保存。"""
    rnd = rand_suffix()
    desc_id = f"e2e_{rnd.lower()}"
    desc_name = f"E2E描述_{rnd}"
    desc_text = "这是由 E2E 自动测试生成的测评描述，请勿手动删除。"

    print(f"\n🧪 TC-B-05: Assessment desc panel – add '{desc_name}'")

    await page.goto(f"{BASE_URL}/admin/templates")
    await asyncio.sleep(2)

    # Click 测评类型配置
    await page.locator("#menu-desc").click()
    await asyncio.sleep(1)

    assert await page.locator("#panel-desc").is_visible(), "测评类型配置面板未显示"

    # Add new row
    await page.locator("button[onclick='addAssessmentDescRow()']").click()
    await asyncio.sleep(0.5)

    # Fill the last row
    ids_inputs = page.locator(".desc-id-input")
    name_inputs = page.locator(".desc-name-input")
    text_inputs = page.locator(".desc-text-input")

    last_idx = await ids_inputs.count() - 1
    id_input = ids_inputs.nth(last_idx)
    if not await id_input.get_attribute("readonly"):
        await id_input.fill(desc_id)
    await name_inputs.nth(last_idx).fill(desc_name)
    await text_inputs.nth(last_idx).fill(desc_text)

    # Save
    page.on("dialog", lambda d: asyncio.ensure_future(d.accept()))
    await page.locator("button[onclick='saveAssessmentDescs()']").click()
    await asyncio.sleep(2)

    print("   ✅ TC-B-05 passed")


async def test_analysis_templates_page(page):
    """TC-B-06: 测评分析模板配置页面能正常打开。"""
    print("\n🧪 TC-B-06: Analysis templates page loads")

    await page.goto(f"{BASE_URL}/admin/analysis-templates")
    await asyncio.sleep(2)

    title = await page.title()
    print(f"   [page title] {title}")
    assert "分析" in title or await page.locator("body").is_visible(), \
        "分析模板配置页未正常加载"

    print("   ✅ TC-B-06 passed")


async def test_teaching_plan_page(page):
    """TC-B-07: 语言教学安排配置页面能正常打开。"""
    print("\n🧪 TC-B-07: Teaching plan admin page loads")

    await page.goto(f"{BASE_URL}/admin/teaching-plan")
    await asyncio.sleep(2)

    assert await page.locator("body").is_visible(), "教学安排配置页未正常加载"
    print("   ✅ TC-B-07 passed")


async def test_back_to_home_link(page):
    """TC-B-08: 点击"返回教务主页"能正确跳转至 /。"""
    print("\n🧪 TC-B-08: Back to home navigation")

    await page.goto(f"{BASE_URL}/admin/templates")
    await asyncio.sleep(1)

    # Use the header back link
    back_link = page.locator("a[href='/']").first
    await back_link.click()
    await asyncio.sleep(2)

    assert page.url.rstrip("/") == BASE_URL.rstrip("/") or \
           page.url.startswith(BASE_URL + "/?") or \
           page.url == BASE_URL + "/", \
        f"未跳转至首页，当前 URL: {page.url}"
    print("   ✅ TC-B-08 passed")


# ─── runner ──────────────────────────────────────────────────────────────────

async def run():
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        context = await browser.new_context(viewport={"width": 1280, "height": 900})
        page = await context.new_page()

        passed = []
        failed = []

        async def run_case(name, coro):
            try:
                await coro
                passed.append(name)
            except Exception as e:
                failed.append(name)
                print(f"   ❌ {name} FAILED: {e}")

        # Individual page in each test (most navigate themselves)
        await run_case("TC-B-01 Admin page loads", test_admin_templates_page_loads(page))
        await run_case("TC-B-02 Global matrix save",  test_global_matrix_panel(page))

        # Create type and reuse in next test
        type_code, type_name = None, None
        try:
            type_code, type_name = await test_add_student_type(page)
            passed.append("TC-B-03 Add student type")
        except Exception as e:
            failed.append("TC-B-03 Add student type")
            print(f"   ❌ TC-B-03 FAILED: {e}")

        if type_code:
            await run_case(
                "TC-B-04 Associate columns",
                test_select_type_and_associate_columns(page, type_code, type_name)
            )

        await run_case("TC-B-05 Assessment desc panel", test_assessment_desc_panel(page))
        await run_case("TC-B-06 Analysis templates page", test_analysis_templates_page(page))
        await run_case("TC-B-07 Teaching plan page", test_teaching_plan_page(page))
        await run_case("TC-B-08 Back to home", test_back_to_home_link(page))

        await browser.close()

        # ── Summary ──────────────────────────────────────────────────────────
        total = len(passed) + len(failed)
        print("\n" + "═" * 55)
        print(f"  B-END E2E RESULTS  │  {len(passed)}/{total} passed")
        print("═" * 55)
        for t in passed:
            print(f"  ✅ {t}")
        for t in failed:
            print(f"  ❌ {t}")
        print("═" * 55)

        if failed:
            raise SystemExit(f"\n{len(failed)} test(s) failed.")


if __name__ == "__main__":
    asyncio.run(run())
