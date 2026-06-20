# AGENTS.md

## 项目概述

MyJpa-Plus 是一个基于 Lambda 表达式的类型安全 JPA 工具库。提供查询构建、批量操作、UPSERT/MERGE、CTE、投影查询、字段加密/脱敏、SQL 慢查询监控。

**技术栈**：Java 17+、Spring Boot 3.3.5、Hibernate 6.x、Spring Data JPA

## 常用命令

```bash
# 格式检查（CI 第一步，必须先通过）
./mvnw spotless:check

# 修复格式
./mvnw spotless:apply

# 单元测试（排除集成测试）
./mvnw test -DexcludedGroups=integration

# 完整验证（CI 标准）
./mvnw clean verify -Dgpg.skip=true -Ddependency-check.skip=true

# 集成测试（需要 Docker）
./mvnw test -Dgroups=integration

# 单个测试类
./mvnw test -Dtest=QuerySpecTest -DexcludedGroups=integration

# 覆盖率报告
./mvnw jacoco:report
```

**Windows**：PowerShell 需要对 `-D` 参数加引号：
```powershell
.\mvnw.cmd test "-Dtest=QuerySpecTest" "-DexcludedGroups=integration"
.\mvnw.cmd clean verify "-Dgpg.skip=true" "-Ddependency-check.skip=true"
```

## CI 流水线

`.github/workflows/ci.yml` — 3 个 job：
1. **build**（Java 17+21 矩阵、MySQL 8.0 服务）：`spotless:check` → `clean verify`
2. **integration**（依赖 build）：Testcontainers PostgreSQL + MySQL
3. **release-deploy**：GPG + Maven Central 发布（`v*` 标签触发）

CI MySQL 密码为 `ci_test_2024`。本地密码为 `1351.zhong`。

## 代码风格

- **格式化**：Spotless + Eclipse 风格（`eclipse-codestyle.xml`）
- 使用方法引用（`Entity::getField`）保证类型安全——禁止硬编码字段名字符串
- `ConditionNode` 是密封类；所有实现必须是 `final`
- 新增条件类型需要 7 个文件同步更新（见下方检查表）
- 公共 API 参数必须有 null 校验

## 包结构

| 包 | 职责 |
|---|---|
| `spec` | 查询构建：QuerySpec、ConditionBuilder、ConditionNode、NodeResolver、SubQuerySpec |
| `update` | 批量操作：UpdateSpec、DeleteSpec、MergeSpec、AbstractBulkOperationSpec、DialectDetector |
| `repository` | 扩展仓库：MyJpaRepository、DefaultMyJpaRepository、SoftDeleteContext、OptimisticLockRetryAdvisor、IgnoreSoftDeleteAdvisor |
| `projection` | 投影查询 |
| `template` | 模板与缓存：MyJpaTemplate、BatchSaveTemplate、KeysetPaginationHelper、QueryCacheManager |
| `annotation` | @SoftDelete、@Encrypt、@Mask、@RetryOnOptimisticLock、@IgnoreSoftDelete、@CodeEnumValue |
| `autoconfigure` | 自动配置：GlobalConfigHolder、MyJpaPlusAutoConfiguration、SoftDeleteFilterBean、MyJpaPlusProperties |
| `converter` | CodeEnumType、EncryptConverter、MaskSerializer |
| `util` | LambdaUtils、IdentifierValidator、InClauseBuilder、EntityClassResolver |
| `monitor` | SQL 监控：SqlSlowQueryInterceptor（StatementInspector）、SlowQueryDataSourceProxy（JDBC 计时）、SlowQueryDataSourceProxyPostProcessor、QueryMetricsCollector |
| `softdelete` | SoftDeleteHelper |

## 核心架构

- **双轨条件构建**：查询使用 `ConditionNode` 树（延迟求值），批量操作使用 `BulkConditionNode`（Lambda 包装）。新增条件类型需要两者同步。
- **`AbstractBulkOperationSpec`** 是 `UpdateSpec` 和 `DeleteSpec` 的基类，共享 `checkRowCountBeforeExecute`、`buildPredicates`。
- **`GlobalConfigHolder`** 是全局配置访问入口——所有配置读取都经过这里。
- **`IdentifierValidator`** 管理 SQL 标识符校验和表名解析（`resolveTableName`）。
- **`MergeSpec`** 通过 `DialectStrategy` 接口生成方言特定的 SQL（PostgreSQL/MySQL/Oracle/SQLServer）。
- **`DefaultMyJpaRepository`** 覆盖所有 `SimpleJpaRepository` 的查询/删除方法，自动注入软删除过滤器。`MyJpaRepository` 在此基础上添加基于 Lambda 的查询/批量操作 default 方法。
- **`MyJpaPlusAutoConfiguration`** 内部类（`MyJpaPlusConfigInitializer`、`SecurityContextAuditUserProvider`、`RepositoryBaseClassPostProcessor`）在 Spring Boot 启动时完成装配。`SlowQueryDataSourceProxyPostProcessor` 在监控启用时自动包装 DataSource。

## 测试

### 前置条件
- 本地 MySQL 8.0 运行中，已创建 `test` 数据库
- 配置：`jdbc:mysql://localhost:3306/test`（见 `src/test/resources/application.properties`）
- 凭据通过环境变量：`DB_USERNAME=root`、`DB_PASSWORD=1351.zhong`
- `spring-boot-starter-security` 是测试依赖（用于 `SecurityContextAuditUserProvider` 测试）

### 测试模式

**单元测试**使用 `@DataJpaTest`：
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TestConfig.class)
class MyTest {
    @SpringBootApplication
    @EntityScan(basePackageClasses = SoftDeleteRepoTestEntity.class)
    @EnableJpaRepositories(basePackageClasses = {SoftDeleteRepoTestRepository.class},
        repositoryBaseClass = DefaultMyJpaRepository.class)
    static class TestConfig {}
}
```

**关键：`@DataJpaTest` 不会激活 `MyJpaPlusAutoConfiguration`**。批量操作（`update()/delete()/merge()`）没有它会抛 `UnsupportedOperationException`。需要添加 `@Import(MyJpaPlusAutoConfiguration.class)` 或 `@ContextConfiguration(classes = TestApplication.class)`。

**关键：`@EnableJpaRepositories` 必须指定 `repositoryBaseClass = DefaultMyJpaRepository.class`**，否则软删除在测试中不生效。没有它，Spring 代理使用 `SimpleJpaRepository`，`deleteByIdIfExists` 等方法不可用。

**MySQL 数据隔离**：MySQL 在方法间保留数据。每个 `@DataJpaTest` 必须有：
```java
@BeforeEach
void setUp() {
    repository.deleteAll();
    repository.flush();
}
```

**MySQL 连接池**：`application.properties` 配置 HikariCP `maximum-pool-size=5`，防止 3009 个测试同时运行时连接耗尽。`pom.xml` 中 Surefire 配置了 `parallel=none` + `forkCount=1`。

**集成测试**：`@Tag("integration")` + Testcontainers（PostgreSQL、MySQL）

### 测试陷阱

- **反射绕过 Spring 代理**：`DefaultMyJpaRepository` 上的方法（如 `deleteByIdIfExists`）不能直接在 Spring 代理上调用——会抛 `ClassCastException`。使用 `org.springframework.test.util.AopTestUtils` 或直接转换为代理。
- **Spring 代理包装**：`deleteAllById(null)` 和 `deleteInBatch(null)` 抛 `InvalidDataAccessApiUsageException`（Spring 包装的）而非 `IllegalArgumentException`。使用 `assertThrows(Exception.class, ...)` 或捕获特定的 Spring 异常类型。
- **Mockito 事务管理**：`executeInTransaction` / `executeInManagedTransaction` 路径需要 Mockito mock，因为 `@DataJpaTest` 包装在 Spring 事务中。创建独立的非 `@DataJpaTest` 类并使用 Mockito。
- **SubQuerySpec 延迟执行**：`qs.exists()` 存储 consumer——它在 `toSpecification()` 时执行。测试异常用：`assertThrows(X.class, () -> parentRepository.findAll(qs.toSpecification()))`，不要包装 `qs.exists()`。
- **反射 `InvocationTargetException`**：通过 `Method.invoke()` 调用私有方法时，异常会被包装在 `InvocationTargetException` 中。必须通过 `.getCause()` 解包。
- **MySQL 自引用 UPDATE**：MySQL 禁止 `UPDATE ... WHERE EXISTS(SELECT ... FROM same_table)`。在 EXISTS 子查询中使用不同的实体类型。
- **`saveAllBatchedPure`** 始终调用 `persist()`——分离/已有实体会抛 `EntityExistsException`。混合列表使用 `saveAllBatched`。
- **GlobalConfigHolder.getConfig()** 未通过 `setConfig()` 配置时返回默认实例。测试必须在 `@BeforeEach`/`@AfterEach` 中 `setConfig(null)` 以控制行为。
- **`condition=false` 的 ConditionalMethods**：当所有条件被跳过时，QuerySpec 没有 WHERE——EXISTS 子查询对所有行返回 true（不是 0）。
- **`deleteInBatch` 软删除路径**：使用 `PersistenceUnitUtil.getIdentifier(entity)` 需要托管实体。调用 `deleteInBatch(List.of(entity))` 前先 `repository.save()` 再 `repository.flush()`。
- **`deleteByIdIfExists` / `deleteByIdOrThrow`**：这些方法在 `DefaultMyJpaRepository` 上，不在接口上。通过 `@DataJpaTest` 测试时使用反射：`Method m = DefaultMyJpaRepository.class.getMethod("deleteByIdIfExists", Object.class); m.invoke(proxy, id);`
- **历史测试错误**：此前约 291 个测试（`KeysetPaginationHelperTest`、`BulkOperationTemplateTest`、`QueryCacheManagerTransactionTest` 等）因 MySQL 连接池耗尽失败。已通过配置 HikariCP `maximum-pool-size=5` 和 Surefire `parallel=none` 修复。全部 3009 个测试现在通过。
- **EncryptConverter 测试设置**：在 `@BeforeEach`/`@AfterEach` 中调用 `EncryptConverter.clearCacheForTesting()` 并通过反射重置 `keyValidated` 字段。设置 `myjpa-plus.encrypt.skip-salt-check=true` 跳过 PBKDF2 盐值校验。
- **SecurityContextAuditUserProvider**：通过反射使用 `Class.forName("org.springframework.security.core.context.SecurityContextHolder")`。类路径上没有 Spring Security 时返回 `"ANONYMOUS"`。有 Spring Security 测试依赖时，mock `SecurityContextHolder` 测试认证/未认证路径。

### 重要测试文件

| 文件 | 测试数 | 说明 |
|---|---|---|
| `QuerySpecTest` | ~100 | 核心查询构建测试 |
| `SubQuerySpecTest` | ~98 | EXISTS/子查询测试 |
| `ConditionBuilderValidationTest` | ~75 | 条件校验 + func() |
| `UpdateSpecTest` | ~114 | 批量更新 + EXISTS/NOT EXISTS |
| `DeleteSpecTest` | ~50 | 批量删除 |
| `MergeSpecTest` | ~45 | UPSERT 真实数据库测试 |
| `MergeSpecTransactionTest` | ~13 | Mockito 事务路径 |
| `DefaultMyJpaRepositoryTest` | ~41 | 软删除仓库 |
| `DefaultMyJpaRepositoryIntegrationTest` | ~20 | deleteInBatch/deleteByIdIfExists 反射测试 |
| `MyJpaRepositoryTest` | ~30 | Lambda 查询/批量操作 |
| `SoftDeleteContextTest` | ~27 | ThreadLocal 计数器行为 |
| `IgnoreSoftDeleteAdvisorTest` | ~7 | AOP 拦截 + SoftDeleteContext |
| `OptimisticLockRetryAdvisorTest` | ~8 | 重试逻辑 + 异常处理 |
| `PredicateHelperTest` | ~63 | 静态谓词辅助方法 |
| `LambdaUtilsTest` | ~20 | Lambda 提取 + 缓存 |
| `EncryptConverterTest` | ~15 | 加密/解密往返 |
| `EncryptConverterCoverageTest` | ~30 | 多密钥、盐值、isProdProfile 路径 |
| `CodeEnumTypeTest` | ~30 | 枚举类型转换 |
| `CodeEnumTypeCoverageTest` | ~25 | char/long/int 类型、ordinal、assemble |

## 关键约定

- JaCoCo 最低 90% 行覆盖率（强制执行范围：spec、update、repository、projection、template、annotation、autoconfigure、codegen、converter、exception、monitor、softdelete、util）
- 当前整体：~84.9% 指令覆盖率、~86.7% 行覆盖率
- SpotBugs Medium 阈值
- PR 检查清单：spotless:check → verify → SpotBugs → JaCoCo → 测试 → CHANGELOG

## SpotBugs 抑制

使用 `@edu.umd.cs.findbugs.annotations.SuppressFBWarnings` 标注有意的设计：
- `MS_EXPOSE_REP`：单例有意暴露配置（GlobalConfigHolder）
- `RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE`：边界情况的防御性空检查
- `EI_EXPOSE_REP` / `EI_EXPOSE_REP2`：Record 组件或缓存的有意暴露

## 新增条件类型的 7 文件同步检查表

向 `ConditionBuilder` 添加条件时：

1. `ConditionNode.java` — `Op` 枚举（+ sealed interface `permits`）
2. `ConditionBuilder.java` — default 方法
3. `ConditionalMethods.java` — 抽象方法声明（批量操作需要时）
4. `NodeResolver.java` — `if-else instanceof` 分支
5. `AbstractBulkOperationSpec.java` — 批量操作实现（需要时）
6. `OrConditionBuilder.java` — 批量 OR 组实现（需要时）
7. `QuerySpecTest.java` — 测试用例
