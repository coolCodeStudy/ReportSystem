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

作为 AI，若遇到用户需求有冲突时，应始终以这套架构守则来提供实现方案并向用户作合理解构。

## 4. 强制工作流约束 (Workflow Protocol)
- **极度严格执行：Whenever you are asked to commit or push code, you MUST ALWAYS run the `/test_all` workflow first.**
- 在发起 `git push` 给远端之前，必须无条件执行 `run_command` 来跑一遍 `./gradlew test` 以确保主干稳定性。如果测试报错，必须停下来修复。
- **绝对静默原则**：除非用户明确且直接地向你下达了“push”、“推送代码”的指令，否则在你写完代码、测试通过后，绝对不允许擅自运行 `git push` 等相关的代码推送动作！你只需在本地改好代码并告知用户。
