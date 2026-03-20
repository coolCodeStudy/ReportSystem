# 全局能力矩阵及动态列导出功能改造

## 需求分析
目前每个 [StudentType](file:///Users/lishaocheng/code/ReportSystem/src/main/kotlin/com/example/reportsystem/entity/StudentTypeDictionary.kt#5-27) 拥有自己独立的 CSV 矩阵，而在导出 Word 报告时直接读取该类型对应的 CSV 渲染表格。
新的需求为：
1. **统一聚合**：系统只需维护**一个全局的 CSV**（包含所有行和所有可能的列，如 "Lingoland", "CEFR", "托福", "雅思" 等）。
2. **学生类型关联列**：[StudentType](file:///Users/lishaocheng/code/ReportSystem/src/main/kotlin/com/example/reportsystem/entity/StudentTypeDictionary.kt#5-27) 不再维护完整的 CSV，而是仅关联需要展示的**列名列表**（例如 `["Lingoland", "CEFR", "雅思"]`）。
3. **导出时动态勾选**：前端导出报告时，弹出一个多选框界面。界面中列出全局 CSV 中的所有表头，并且默认勾选当前学生类型所关联的那些列。用户可以继续修改勾选状态，然后导出对应的 Word 文档。

## 不需要清空数据库（平滑升级）
当前可以通过 Spring Data JPA 的自动更新特性增加列，旧的 CSV 数据我们可以保留或迁移，无需完全清空数据库。

---

## 改造方案详细步骤

### 1. 数据库与实体类改造 (Backend)
- **新增全局配置表 (SystemConfig)**:
  - 实体类：`SystemConfig`，包含 `configKey` 和 `configValue`。用于存储全局的 `GLOBAL_CAPABILITY_MATRIX_CSV`。
  - 在 `SystemInitRunner` 中插入包含所有列的聚合大表（默认 CSV）以供初始化使用。
- **修改 [StudentTypeDictionary](file:///Users/lishaocheng/code/ReportSystem/src/main/kotlin/com/example/reportsystem/entity/StudentTypeDictionary.kt#5-27)**:
  - 新增字段 `associatedColumns` (或 `associated_columns`)，存储用逗号分隔的列名或 JSON 数组。
  - 保留原有的 `capabilityMatrixCsv` 字段，确保系统不报错，后续可停用。

### 2. 管理后台配置接口及界面改造 ([admin-templates.html](file:///Users/lishaocheng/code/ReportSystem/src/main/resources/templates/admin-templates.html))
- **全局 CSV 管理区**:
  - 在页面顶部或单独的 Tab 增加“全局能力矩阵配置”区域，提供一个全局的 textarea 修改全局 CSV 并保存。
- **体系字段关联区**:
  - 当点击左侧某个“学生体系”后，右侧不再展示 CSV 文本框。
  - 而是前端解析“全局 CSV”的第一行（表头），将其渲染成一组 Checkbox 多选框。
  - 自动勾选当前体系 `associatedColumns` 中包含的列。
  - 点击“保存”时，向后端保存这组被关联的列。

### 3. 导出 Word 报表接口改造 ([DocxGeneratorService](file:///Users/lishaocheng/code/ReportSystem/src/main/kotlin/com/example/reportsystem/service/DocxGeneratorService.kt#15-277) & Endpoints)
- **参数动态化**:
  - 修改 `DocxGeneratorService.generateDocx(...)` 逻辑，允许传入 `selectedColumns: List<String>`。
  - 生成表格时，仅保留属于 `selectedColumns` 的列，并同时保留这些列在原始 CSV 中的同行数据。
- **导出 API 扩展**:
  - GET `/student/history/{id}/export` 增加可选参数 `?columns=Lingoland,CEFR,雅思...`。

### 4. 教务前端导出流程改造 ([index.html](file:///Users/lishaocheng/code/ReportSystem/src/main/resources/templates/index.html))
- **阻断直接下载并引入弹窗**:
  - `view-history-btn` 弹出的履历列表中，"导出报告" 原来是一个直接的 `<a>` 标签下载链接。
  - 将其改为触发一个新弹窗（`#exportColumnsModal`）。
- **弹窗逻辑**:
  - 读取“全局 CSV”的列头以生成复选框。
  - 根据该记录涉及的 [StudentType](file:///Users/lishaocheng/code/ReportSystem/src/main/kotlin/com/example/reportsystem/entity/StudentTypeDictionary.kt#5-27)，从后端获取要**默认勾选**的列。
  - 用户确认勾选后，拼接 URL `?columns=...` 再触发文件下载。

---

## User Review Required
> [!IMPORTANT]
> 1. 上述方案会将原有的“分体系CSV维护”改为“在一张大表中维护完整的映射关系”，您觉得这样的业务逻辑是否符合您的预期？
> 2. 因为需要做导出前的勾选弹窗，所以“导出报告”需要改为两步走：**点击导出 -> 弹出勾选项（默认选中关联列） -> 确认下载**。
> 3. 目前不需要清空数据库，只需等后端更新并在 `SystemInitRunner` 刷入默认全局配置后，去管理后台重新关联列和编辑即可。如果您同意这个方案，我将开始分步骤执行。
