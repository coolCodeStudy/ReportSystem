---
description: 快速生成一个标准且完整的 Spring Boot 业务模块
---

# 快速生成 Spring Boot 业务模块

当你需要添加一个新功能或数据表时，可以直接输入 `/create_feature <模块名>` 来调用我。
我会自动为你执行以下标准流水线作业：

1. **实体类与存储层**
   - 在 `entity` 包下创建 Kotlin 数据类实体，并配置对应的 `@Entity` 和行标映射。
   - 在 `repository` 包下创建对应的 `JpaRepository` 接口。

2. **业务逻辑层**
   - 在 `service` 包下创建含有核心业务的 Service 类。
   - 自动为你注入相关的 Repository，并实现基本的 CRUD 模板。

3. **接口控制器层**
   - 在 `controller` 包下生成 `RestController` 或标准的页面 Controller。
   - 配置好标准的 RESTful 路由（GET, POST, PUT, DELETE）。

4. **单元测试防护网**
   - 同步在 `src/test/kotlin` 的对应包下创建带有 `@WebMvcTest` 或 MockK 的测试类。
   - 生成增删改查的基础断言测试代码。

**注意**：在调用这个 Workflow 前，你可以顺便在同一句话里加上你需要的字段名（比如：“/create_feature Teacher 包含 name, age, phone 并且只需生成只读接口”），我会完全根据你的定制要求来脚手架这些文件。
