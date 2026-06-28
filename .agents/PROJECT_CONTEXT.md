# ReportSystem 项目速读

> 给后续 Codex/Agent 接手用：先读这份，再按需深入源码。

## 一句话定位

ReportSystem 是一个面向 Lingoland 测评报告生成的 Kotlin + Spring Boot 系统：管理学生档案与测评记录，在 Thymeleaf 工作台里录入测评分析和教学方案，最后基于 Word 模板生成 `.docx` 学习方案。

## 技术栈与运行

- 后端：Spring Boot 2.7.18、Kotlin 1.6.21、JDK 17。
- 页面：Thymeleaf + Bootstrap + 原生 `fetch`，部分页面使用 Alpine.js 风格的前端状态管理。
- 数据库：PostgreSQL，`docker-compose.yml` 提供本地 `reportsystem_db`。
- 文档生成：Apache POI 操作 `src/main/resources/static/Lingoland学习方案.docx`。
- 运行：先启动 Postgres，再执行 `./gradlew bootRun`，访问 `http://localhost:8080`。
- 测试：仓库约定提交/推送前跑 `/test_all`，核心命令是 `./gradlew clean test`。

## 核心业务流

1. 首页 `/` 展示学生档案列表、搜索、统计和新建入口。
2. 学生档案保存在 `student`，测评记录保存在 `assessment_record`。
3. 点击创建测评工作台会调用 `/api/workspace/create`，生成一条空的 `AssessmentRecord`。
4. `/assessment/{id}/workspace` 进入三步工作台：
   - Step 1：基础信息、测评类型、能力矩阵导出列。
   - Step 2：各科测评分数、卷面分析、成因分析。
   - Step 3：课程规划、教学思路、教材、课时、风险提示。
5. 导出历史报告 `/student/history/{recordId}/export` 会读取记录中的 JSON 字段，调用 `DocxGeneratorService.generateDocx(...)` 重新生成 docx。

## 主要模块

- `controller/HomeController.kt`：首页、老式 `/report/docx` 导出入口、保存报告入口。
- `controller/StudentController.kt`：学生保存/软删除、历史记录、历史导出。
- `controller/AssessmentWorkspaceController.kt`：测评工作台创建、展示、Step 1/2/3 保存。
- `controller/AdminController.kt`：后台模板页和通用配置 API，读写 `system_config`。
- `controller/AdminExcelController.kt`：导入分析模板 Excel，解析维度/达标文案。
- `controller/TeachingPlanController.kt`：教学计划导入、教材简介、清理和列表 API。
- `service/DocxGeneratorService.kt`：docx 生成编排核心。
- `service/docx/*Renderer.kt`：能力矩阵、测评分析、教学方案的 Word 渲染细分实现。
- `service/SystemDictionaryService.kt`：学生类型、动态字段、全局配置服务。
- `service/StudentArchiveService.kt`：学生档案与测评记录保存/软删除。
- `service/TeachingPlanService.kt`：从 Excel 导入教学计划。
- `config/SystemInitRunner.kt`：系统启动时初始化默认字典、能力矩阵和全局模板配置。

## 数据模型

- `Student` -> `student`
  - 姓名、电话、年龄、性别、学校、年级、学生类型、动态字段 JSON 文本。
  - `@Where(is_deleted = false)` 软删除。
- `AssessmentRecord` -> `assessment_record`
  - 关联学生，保存测评类型、目标年级、CEFR/Lingoland level、学习目标。
  - `assessmentResults` 和 `teachingPlanData` 是 `jsonb` 字符串。
  - `selectedExportColumns` 控制能力矩阵导出列。
  - 同样使用软删除。
- `StudentTypeDictionary` -> `student_type_dictionary`
  - 学生类型字典、排序、启停、能力矩阵关联列。
- `TypeFormField` -> `type_form_field`
  - 按学生类型配置首页动态字段。
- `SystemConfig` -> `system_config`
  - 系统最重要的轻量配置表，很多模板 JSON 都存在这里。
- `TeachingPlan` / `TextbookConfig`
  - 教学计划大纲与教材/系列介绍。

## 关键 SystemConfig

- `GLOBAL_CAPABILITY_MATRIX_CSV`：全局能力矩阵 CSV。
- `GLOBAL_BASIC_COLUMNS`：能力矩阵基础列。
- `GLOBAL_ASSESSMENT_DESCRIPTIONS`：测评类型列表与导出报告的测评说明。
- `GLOBAL_SUBJECTS_{TYPE_ID}`：某测评类型下的科目列表。
- `GLOBAL_ANALYSIS_CONFIG_{TYPE_ID}_{SUBJECT_KEY}`：卷面分析模板。
- `GLOBAL_CAUSE_ANALYSIS_{TYPE_ID}_{SUBJECT_KEY}`：成因分析选项。
- `GLOBAL_SCORE_RULE_{TYPE_ID}_{SUBJECT_KEY}`：分数/正确率到等级的规则。
- `GLOBAL_TEACHER_INTRODUCTIONS`：师资简介。
- `GLOBAL_TEACHING_APPROACH_TEMPLATE`：教学思路兜底模板。
- `GLOBAL_TEACHING_CHECKLIST_TEMPLATE`：助教课打卡清单兜底模板。
- `GLOBAL_COURSE_FREQUENCY_TEMPLATE`：课程频次兜底模板。
- `GLOBAL_PLAN_RISK_TEMPLATE`：方案风险提示兜底模板。
- `GLOBAL_COURSE_PLAN_DEFAULT` / `GLOBAL_COURSE_PLAN_NOTE_DEFAULT`：工作台课程规划默认值。

## Docx 生成机制

- 模板文件：`src/main/resources/static/Lingoland学习方案.docx`。
- 主要占位符：
  - `{assessment_introduction}`：测评说明。
  - `{assessment_analysis}`：Step 2 的各科分析表。
  - `{course_schedule}`：Step 3 的教学方案内容。
- `DocxGeneratorService` 负责读取模板、拒绝旧版 `G1-G11` 作为测评 level、拼接测评说明，并依次调用 renderer。
- `DocxCapabilityMatrixRenderer` 会重建模板中的第一张表，按 `selectedColumns` 裁剪列，并用颜色高亮目标年级和当前水平。
- `DocxAssessmentAnalysisRenderer` 根据工作台保存的 `assessmentResults` JSON，结合 `GLOBAL_SUBJECTS_*` 和分析模板渲染表格。
- `DocxTeachingPlanRenderer` 根据 `teachingPlanData` JSON、教学计划库和全局兜底模板，渲染课程规划、教材简介、师资、教学计划大纲、风险提示等。

## 前端页面

- `templates/index.html`：学生档案首页、历史记录抽屉、工作台 iframe 弹窗、动态字段。
- `templates/workspace.html`：核心测评工作台，负责保存 Step 1/2/3 的 JSON。
- `templates/admin-templates.html`：学生类型、能力矩阵、基础分析模板等后台配置。
- `templates/admin-analysis-templates.html`：按测评类型/科目维护分析模板、成因、评分规则。
- `templates/admin-teaching-plan.html`：教学计划 Excel 导入、教材简介和全局教学方案模板。

## 测试现状

- `AdminControllerTest` 使用 `@WebMvcTest` + `MockkBean` 验证配置 API。
- `DocxGeneratorServiceTest` 用 MockK 纯单元测试验证 docx 生成和旧 level 拦截。
- `IntegrationTemplateTest` 是 `@SpringBootTest`，会真实启动 Spring 上下文，可能依赖本地数据库配置。
- `e2e/b_end`、`e2e/c_end` 有 Python 端到端脚本。

## 开发约定

- Controller 尽量只做参数解析和响应封装，复杂逻辑放 Service。
- 配置型需求优先复用 `system_config`，不要轻易新增表结构。
- Service 单测优先 MockK + JUnit 5，不为了单测启动 H2。
- 页面保持 Thymeleaf 原生模板，不引入重型前端构建链。
- 文档生成相关改动要优先关注模板占位符、POI 表格结构和导出后可打开性。
- 提交或推送前必须按仓库 workflow 跑全量测试；未被明确要求时不要主动 push。
- 用户明确说“push/推送”时，视为已要求执行推送：跑完全量测试后直接提交/推送，不要展开解释审查流程；若底层平台强制拦截并要求二次确认，只用一句简短确认请求，用户确认后立即继续。

## 容易踩坑

- 当前 `application.properties` 直接指向本地 PostgreSQL；跑 `@SpringBootTest` 前要确保数据库可用。
- `AssessmentRecord.assessmentResults` / `teachingPlanData` 虽是 `jsonb`，代码侧大量以原始 JSON 字符串流转，字段名需和 `workspace.html` 保持一致。
- `GLOBAL_ASSESSMENT_DESCRIPTIONS` 的 `id` 会影响 `GLOBAL_SUBJECTS_*`、分析模板、评分规则和 docx 渲染匹配。
- `DocxGeneratorService` 已明确禁止旧版 `G1-G11` 作为 `targetLevel`，应传 CEFR 类值，例如 `B1-`。
- 软删除依赖 Hibernate `@Where`，普通 repository 查询默认看不到已删除数据。
- 根目录有若干历史修复脚本和样例产物，阅读系统时优先看 `src`、`.agents`、Gradle 配置和模板。
- Word 表格单元格需要保留用户输入的分行时，先统一 `\r\n` / `\r` 为 `\n`，再用 Apache POI 的 `XWPFRun.addBreak()` 生成 `<w:br/>`；不要用 `addCarriageReturn()`，否则导出的表格内容在 Word 里容易显得挤在一起或版式混乱。
- 导出设置属于横跨工作台和档案履历的共用行为，新增选项时应优先收口到同一套前端交互和保存字段，再让所有导出入口复用，避免一个入口支持、另一个入口漏掉。
- 前端初始化依赖第三方弹窗或 CDN 脚本时要防御加载顺序，调用 `Swal` 等全局对象前先判断是否存在，并提供原生交互兜底。

## 后续待办线索

- 用户接下来可能会调整导出 Word 的需求。处理时优先查看：
  - `service/DocxGeneratorService.kt`
  - `service/docx/DocxCapabilityMatrixRenderer.kt`
  - `service/docx/DocxAssessmentAnalysisRenderer.kt`
  - `service/docx/DocxTeachingPlanRenderer.kt`
  - `templates/workspace.html`
  - `src/main/resources/static/Lingoland学习方案.docx`
- Word 导出相关改动建议同步补充或调整 `DocxGeneratorServiceTest`，至少验证生成文件可被 POI 打开、关键文案/章节存在、目标占位符被清理。
