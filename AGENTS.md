# AGENTS.md

## 项目概述

MyJpa-Plus 是基于 Lambda 表达式的类型安全 JPA 工具库，提供查询构建、批量操作、UPSERT/MERGE、CTE、投影查询、字段加密/脱敏、SQL 慢查询监控。

**技术栈**：Java 17+、Spring Boot 3.x、Hibernate 6.x、Spring Data JPA

## 常用命令

```bash
# 格式检查（CI 第一步，必须先通过）
./mvnw spotless:check

# 格式化代码（CI 失败时用这个修复）
./mvnw spotless:apply

# 单元测试（排除集成测试）
./mvnw test -DexcludedGroups=integration

# 完整验证（CI 标准）
./mvnw clean verify -Dgpg.skip=true -Ddependency-check.skip=true

# 集成测试（需要 Docker）
./mvnw test -Dgroups=integration

# 跑单个测试类
./mvnw test -Dtest=QuerySpecTest -DexcludedGroups=integration
```

**Windows 注意**：PowerShell 中需用双引号包裹 `-D` 参数，如 `".\mvnw.cmd clean verify" "-Dgpg.skip=true"`

## CI 流程

CI 在 `.github/workflows/ci.yml`，分三步：
1. `spotless:check` — 格式检查
2. `clean verify` — 构建 + 单元测试（跳过 GPG 和 OWASP）
3. 集成测试（Testcontainers PostgreSQL + MySQL）

CI 矩阵测试 Java 17 和 21。单元测试需要 MySQL 8.0（CI 通过 services 容器提供）。

## 代码风格

- **格式化工具**：Spotless + Eclipse 代码风格（`eclipse-codestyle.xml`）
- 使用方法引用（`Entity::getField`）确保类型安全，禁止硬编码字段名字符串
- `ConditionNode` 是 sealed 接口，所有实现类必须为 `final`
- 新增条件类型需同步更新 7 个位置（详见 CONTRIBUTING.md 清单）
- 公开 API 参数必须添加 null 校验

## 包结构

| 包 | 职责 |
|---|---|
| `com.zsubera.jpa.spec` | 查询构建：QuerySpec、ConditionBuilder、ConditionNode、NodeResolver |
| `com.zsubera.jpa.update` | 批量操作：UpdateSpec、DeleteSpec、MergeSpec、AbstractBulkOperationSpec |
| `com.zsubera.jpa.repository` | 扩展 Repository：MyJpaRepository、SoftDeleteContext、OptimisticLockRetryAdvisor |
| `com.zsubera.jpa.projection` | 投影查询 |
| `com.zsubera.jpa.template` | 模板与缓存：MyJpaTemplate、QueryCacheManager、BatchSaveTemplate |
| `com.zsubera.jpa.annotation` | 注解：@SoftDelete、@Encrypt、@Mask、@RetryOnOptimisticLock |
| `com.zsubera.jpa.converter` | 转换器：CodeEnumType、EncryptConverter、MaskSerializer |
| `com.zsubera.jpa.util` | 工具类：LambdaUtils、InClauseBuilder、IdentifierValidator |
| `com.zsubera.jpa.monitor` | SQL 监控：SqlSlowQueryInterceptor、QueryMetricsCollector |

## 关键架构

- **条件构建双轨制**：查询用 `ConditionNode` 树（延迟执行），批量操作用 `BulkConditionNode`（Lambda 包装）。新增条件需两边同步。
- **`AbstractBulkOperationSpec`** 是 `UpdateSpec` 和 `DeleteSpec` 的基类，共享 `checkRowCountBeforeExecute`、`buildPredicates` 等方法。
- **`GlobalConfigHolder`** 是全局配置的集中访问点，所有配置读取通过此类。
- **`IdentifierValidator`** 统一管理 SQL 标识符验证和表名解析（`resolveTableName`）。

## 测试

- **单元测试**：`@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + 本地 MySQL
  - 连接信息：`jdbc:mysql://localhost:3306/test`（见 `src/test/resources/application.properties`）
  - 需要本地 MySQL 运行，且存在 `test` 数据库
- **集成测试**：`@Tag("integration")` + Testcontainers（PostgreSQL、MySQL）
- 核心测试类：`QuerySpecTest`、`ConditionBuilderValidationTest`、`UpdateSpecTest`、`DeleteSpecTest`

## 关键约定

- JaCoCo 覆盖率最低 90%（全部 13 个核心包，见 pom.xml 配置）
- SpotBugs 阈值：Medium
- PR 检查清单：spotless:check → verify → SpotBugs → JaCoCo → 测试 → CHANGELOG

## SpotBugs 抑制

遇到误报时使用 `@SuppressFBWarnings` 注解（已导入 `edu.umd.cs.findbugs.annotations`）：

- `MS_EXPOSE_REP`：配置单例故意暴露（GlobalConfigHolder）
- `RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE`：防御性 null 检查（count query 场景）
- `RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE`：移除冗余三元表达式
- `EI_EXPOSE_REP` / `EI_EXPOSE_REP2`：Record 组件或缓存故意暴露

## 新增条件类型的同步清单

修改 `ConditionBuilder` 添加新条件时，必须同步更新以下位置：

1. `ConditionNode.java` — `Op` 枚举（sealed 接口新增节点需加 `permits`）
2. `ConditionBuilder.java` — default 方法
3. `ConditionalMethods.java` — 抽象方法声明（若批量操作也需要）
4. `NodeResolver.java` — `if-else instanceof` 分支
5. `AbstractBulkOperationSpec.java` — 批量操作实现（若需要）
6. `OrConditionBuilder.java` — 批量 OR 组实现（若需要）
7. `QuerySpecTest.java` — 测试用例
