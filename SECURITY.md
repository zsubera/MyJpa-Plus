# 安全策略

## 支持的版本

| 版本 | 支持状态 |
|---|---|
| 1.3.x | :white_check_mark: |
| 1.2.x | :white_check_mark: |
| 1.1.x | :white_check_mark: |
| < 1.1.0 | :x: 不再支持 |

## 报告漏洞

**请勿通过公开 issue 报告安全漏洞。**

请使用 [GitHub Security Advisory](https://github.com/zsubera/myjpa-plus/security/advisories/new) 私密报告安全问题，或发送邮件至 POM 开发者信息中的地址联系项目维护者。我们会在 48 小时内确认收到，并在修复后公开披露。

## 依赖项

本项目依赖极简：

- **Spring Boot 3.x**（可选，compile 作用域）— 提供 JPA Criteria API 的框架
- **Jackson Databind**（可选，compile 作用域）— 用于字段脱敏序列化
- **SpotBugs Annotations**（可选，compile 作用域）— 静态分析注解

无任何 Web 框架、日志库或序列化库的运行时依赖。MySQL Connector/J 仅 test 作用域。
