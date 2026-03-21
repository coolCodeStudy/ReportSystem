---
description: 运行所有 Kotlin / Spring Boot 测试用例并进行预发版检查
---

# 全量测试与安全检查

你可以输入 `/test_all` 来触发该 Workflow。

执行此宏，我将帮你完成以下事情的检查，确保代码可以安全推向生产环境（或 Git commit）：

// turbo-all
1. 自动执行 `./gradlew clean test`。
2. 读取测试报告并在终端上抓取报错的用例给您。
3. 如果所有测试完美通过，我将验证系统配置表（`SystemConfig` 或 `StudentTypeDictionary`）中是否有未完成迁移的空值，因为数据库在重启后可能会受到影响。
4. 提供一个测试覆盖及代码健康的体检报告总结，如果发现任何问题将直接把出错的堆栈贴出并进入调试模式。
