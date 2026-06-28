---
name: Spring Boot Expert Assistant
description: 提供在开发 ReportSystem 时的专属 Kotlin & Spring 架构指南和测试方案指导。
---

# ReportSystem 专属 Spring Boot 开发守则

当你发现任何复杂的技术债或者面对大型重构需要帮助时，可以要求我“启用 Spring Boot Expert Skill”。

## 1. 架构约定 (Architecture Conventions)
- **Controller 层**：禁止在 Controller 中书写核心逻辑，Controller 应仅负责 `@PathVariable`、`@RequestBody` 解析与响应封装（返回 `ResponseEntity` 等）。
- **Service 层**：复杂的文档生成（如使用 Apache POI 制作 Word）、能力矩阵（CSV 的动态列裁剪及 CEFR 高亮逻辑）必须收口在 Service 层（例如 `DocxGeneratorService` 及其拆分的子类）。
- **配置数据源**：禁止肆意修改数据表 Scheme！尽量复用 `system_config`（采用 ConfigKey 和 ConfigValue 存储 JSON 的方式）进行轻量级字典存取。

## 2. 单元测试策略 (Testing Strategy)
- 测试核心不依靠 H2 内置内存库，而是全部使用 **MockK** + **JUnit 5**，做到真正的“快速反馈单元”。
- Spring Boot Controller 层使用 `@WebMvcTest` 结合 `com.ninjasquad.springmockk.MockkBean` 注入假冒 Service，只断言路由和 JSON。
- 业务 Service 层采用纯手工注入（不启动 Spring 容器），并使用 `every { xxx } returns yyy` 和 `verify { xxx }` 来断言交互是否执行。

## 3. 前后端交互
- 保持 `index.html` 和 `admin-templates.html` 为原生前端模版引擎（Thymeleaf）。
- 对于动态模块复选框、长列表更新，使用原生的 `fetch` 接口异步通信拉取 JSON 并在 JS 中构建 DOM，并使用 Bootstrap 5 Modal 显示。

## 3.1 Excel 与导出表格一致性
- 教学计划大纲 Excel 的每个 Sheet 代表一本教材或一套教材的稳定边界；导入后不得在导出层把不同教材无提示地拼接成一张连续大表。
- 当课时规划中存在多个阶段/多套教材时，Word 导出的“教学计划大纲”必须按课时规划阶段顺序拆成独立表格；同一阶段包含多本同属一套方案的教材时，可合并在该阶段表内。
- 若没有课时规划阶段信息，则退回按教材 `bookName` 分组导出，保留 Excel Sheet 的教材边界。
- 多张教材大纲表必须复用同一套表格系统：相同表头、列顺序、字体、字号、主题色、斑马纹、边框和间距；只允许通过表前小标题区分阶段/教材，不允许临时手搓第二套样式。
- 修改 Excel 导入、教学计划渲染或教材分组逻辑时，必须补充测试断言表格数量、表头一致性和分组内容归属，防止再次出现多教材连续粘连的问题。

作为 AI，若遇到用户需求有冲突时，应始终以这套架构守则来提供实现方案并向用户作合理解构。

## 4. 强制工作流约束 (Workflow Protocol)
- **极度严格执行：Whenever you are asked to commit or push code, you MUST ALWAYS run the `/test_all` workflow first.**
- 在发起 `git push` 给远端之前，必须无条件执行 `run_command` 来跑一遍 `./gradlew test` 以确保主干稳定性。如果测试报错，必须停下来修复。
- **绝对静默原则**：除非用户明确且直接地向你下达了“push”、“推送代码”的指令，否则在你写完代码、测试通过后，绝对不允许擅自运行 `git push` 等相关的代码推送动作！你只需在本地改好代码并告知用户。
- **推送交互习惯**：当用户已经明确说“push/推送”时，视为授权推送；测试通过后直接执行提交/推送，不要长篇解释安全审查。若底层平台强制要求二次确认，只用一句话说明“平台要求再次确认推送到远端”，用户确认后立即继续。

## 5. 工作区清理准则 (Workspace Cleanup Protocol)
- **清理无用产物**：在完成功能验证或测试（如写了大量 Python/Shell 模拟脚本、输出日志 txt 或临时的生成文件区）后，必须主动运行清理指令，删除过程中产生的中间无用文件，仅保留“最具有价值”或“最终确认成功”的一个脚本与生成物，以保持工作区整洁。
