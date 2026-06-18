# AGENTS.md

## 项目概述

MyJpa-Plus 是基于 Lambda 表达式的类型安全 JPA 工具库，提供查询构建、批量操作、UPSERT/MERGE、CTE、投影查询、字段加密/脱敏、SQL 慢查询监控。

**技术栈**：Java 17+、Spring Boot 3.x、Hibernate 6.x、Spring Data JPA

## 常用命令

```bash
# 格式检查（CI 第一步）
./mvnw spotless:check

# 格式化代码
./mvnw spotless:apply

# 单元测试（排除集成测试）
./mvnw test -DexcludedGroups=integration

# 完整验证（CI 标准）
./mvnw clean verify -Dgpg.skip=true -Ddependency-check.skip=true

# 集成测试（需要 Docker）
./mvnw test -Dgroups=integration
```

**Windows 注意**：PowerShell 中需用双引号包裹 `-D` 参数，如 `".\mvnw.cmd clean verify" "-Dgpg.skip=true"`

## 代码风格

- **格式化工具**：Spotless + Eclipse 代码风格（`eclipse-codestyle.xml`）
- 使用方法引用（`Entity::getField`）确保类型安全，禁止硬编码字段名字符串
- `ConditionNode` 是 sealed 接口，所有实现类必须为 `final`
- 新增条件类型需同步更新 7 个位置（详见 CONTRIBUTING.md 清单）

## 测试

- **单元测试**：`@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + 本地 MySQL
  - 连接信息：`jdbc:mysql://localhost:3306/test`（见 `src/test/resources/application.properties`）
  - 需要本地 MySQL 运行，且存在 `test` 数据库
- **集成测试**：`@Tag("integration")` + Testcontainers（PostgreSQL、MySQL）
- 核心测试类：`QuerySpecTest`、`ConditionBuilderValidationTest`

## 包结构

| 包 | 职责 |
|---|---|
| `com.zsubera.jpa.spec` | 查询构建：QuerySpec、ConditionBuilder、ConditionNode |
| `com.zsubera.jpa.update` | 批量操作：UpdateSpec、DeleteSpec、MergeSpec |
| `com.zsubera.jpa.repository` | 扩展 Repository：MyJpaRepository |
| `com.zsubera.jpa.projection` | 投影查询 |
| `com.zsubera.jpa.template` | 模板与缓存：MyJpaTemplate |
| `com.zsubera.jpa.annotation` | 注解：@SoftDelete、@Encrypt、@Mask |

## 关键约定

- 所有条件方法归属于 `ConditionBuilder` 或其子接口
- JaCoCo 覆盖率最低 95%（核心包）
- SpotBugs 阈值：Medium
- PR 检查清单：spotless:check → verify → SpotBugs → JaCoCo → 测试 → CHANGELOG

## SpotBugs 抑制

遇到误报时使用 `@SuppressFBWarnings` 注解（已导入 `edu.umd.cs.findbugs.annotations`）：

- `MS_EXPOSE_REP`：配置单例故意暴露（GlobalConfigHolder）
- `RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE`：防御性 null 检查（count query 场景）
- `RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE`：移除冗余三元表达式
