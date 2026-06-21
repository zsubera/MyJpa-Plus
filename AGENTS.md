# AGENTS.md

## 项目概述

MyJpa-Plus 是一个基于 Lambda 表达式的类型安全 JPA 工具库。提供查询构建、批量操作、UPSERT/MERGE、CTE、投影查询、字段加密/脱敏、SQL 慢查询监控、CacheAdapter SPI 可插拔分布式缓存。

**技术栈**：Java 17+、Spring Boot 3.3.5、Hibernate 6.x、Spring Data JPA

## 快速命令

```bash
# 格式检查（CI 门禁 — 必须首先通过）
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

**Windows PowerShell** — `-D` 参数需加引号：
```powershell
.\mvnw.cmd test "-Dtest=QuerySpecTest" "-DexcludedGroups=integration"
.\mvnw.cmd clean verify "-Dgpg.skip=true" "-Ddependency-check.skip=true"
```

## CI 流水线

`.github/workflows/ci.yml` — 3 个 Job：
1. **build**（Java 17+21 矩阵、MySQL 8.0 服务）：`spotless:check` → `clean verify`
2. **integration**（依赖 build）：Testcontainers PostgreSQL + MySQL
3. **release-deploy**：GPG + Maven Central 发布（由 `v*` 标签触发）

CI MySQL 密码：`ci_test_2024`。本地：`1351.zhong`。

## 代码风格

- **格式化**：Spotless + Eclipse 风格（`eclipse-codestyle.xml`）
- 使用方法引用（`Entity::getField`）保证类型安全 — 禁止硬编码字段名字符串
- `ConditionNode` 是密封接口；所有实现必须为 `final`
- 公共 API 参数必须有 null 检查
- 除非被要求，否则不添加注释；避免过度设计

## 包结构

| 包 | 核心类 |
|---|---|
| `spec` | QuerySpec、ConditionBuilder、ConditionNode、NodeResolver、SubQuerySpec、CacheKeyBuilder、PredicateHelper、AggregateHelper、QueryHavingSupport、QueryConditionSupport、QueryOrderBySupport、QueryAggregateSupport、QuerySubQuerySupport、QueryJoinSupport、QueryCompositionSupport |
| `update` | UpdateSpec、DeleteSpec、MergeSpec、AbstractBulkOperationSpec、DialectDetector |
| `repository` | MyJpaRepository、DefaultMyJpaRepository、SoftDeleteContext、OptimisticLockRetryAdvisor、IgnoreSoftDeleteAdvisor |
| `projection` | ProjectionSpec |
| `template` | MyJpaTemplate、BulkOperationTemplate、BatchSaveTemplate、KeysetPaginationHelper、QueryCacheManager、CacheAdapter、DisabledCacheAdapter |
| `annotation` | @SoftDelete、@Encrypt、@Mask、@RetryOnOptimisticLock、@IgnoreSoftDelete、@CodeEnumValue |
| `autoconfigure` | GlobalConfigHolder、MyJpaPlusAutoConfiguration、SoftDeleteFilterBean、MyJpaPlusProperties |
| `converter` | CodeEnumType、EncryptConverter、MaskSerializer |
| `util` | LambdaUtils、IdentifierValidator、InClauseBuilder、EntityClassResolver |
| `monitor` | SqlSlowQueryInterceptor、SlowQueryDataSourceProxy、QueryMetricsCollector |
| `softdelete` | SoftDeleteHelper |

## 架构

- **双轨条件构建**：查询使用 `ConditionNode` 树（延迟求值），批量操作使用 `BulkConditionNode`（Lambda 包装）。新增条件类型需要同时更新两条轨道。
- **`Op.resolve()` 策略模式**：`Op` 枚举是谓词构建的唯一真实来源。查询解析（`NodeResolver` → `PredicateHelper` → `Op.resolve()`）和批量操作（`BulkConditionSupport` → `Op.resolve()`）都委托给同一个 `Op` 实现。
- **`AbstractBulkOperationSpec`** 是 `UpdateSpec`/`DeleteSpec` 的抽象基类，共享 `checkRowCountBeforeExecute`/`buildPredicates`。
- **`GlobalConfigHolder`** 是全局配置的集中访问点。
- **`MergeSpec`** 使用 `DialectStrategy` 实现数据库方言特定 SQL（PostgreSQL/MySQL/Oracle/SQLServer）。
- **`DefaultMyJpaRepository`** 覆盖所有 `SimpleJpaRepository` 的查询/删除方法，自动注入软删除过滤。
- **CacheAdapter SPI**：`QueryCacheManager` 实现 `CacheAdapter`。用户可通过自定义 `CacheAdapter` Bean 注入 Redis/Caffeine 等分布式缓存。`DisabledCacheAdapter` 用于禁用缓存。
- **提取的内部工具类**（包级私有）：`CacheKeyBuilder`（从 QuerySpec 提取的缓存键生成）、`PredicateHelper`（共享谓词构建）、`AggregateHelper`（HAVING 验证/比较）。

## QuerySpec 拆分

QuerySpec（887 行）已拆分为专注的辅助类：

| 类 | 行数 | 职责 |
|---|---|---|
| `QuerySpec` | 887 | 核心状态、`toSpecification()`、`toPredicate()`、ConditionBuilder 方法、`copy()` |
| `QueryConditionSupport` | 102 | 协调层 — 委托给子查询/JOIN/组合辅助类 |
| `QueryCompositionSupport` | 160 | OR/NOT 组、`then()`、`and()`、`toSpecification()` |
| `QuerySubQuerySupport` | 106 | EXISTS/IN 子查询 |
| `QueryJoinSupport` | 91 | INNER/LEFT/FETCH JOIN |
| `QueryHavingSupport` | 247 | HAVING 条件（通用 + 类型安全聚合） |
| `QueryAggregateSupport` | 100 | GROUP BY 字段、`applyDistinctAndGroupBy()` |
| `QueryOrderBySupport` | 141 | ORDER BY 字段、`getSort()`、`applyOrderBy()` |

**添加新条件类型**：只需修改 `ConditionNode.Op` 枚举 + `ConditionBuilder` 方法 + `ConditionalMethods` 抽象方法 + 测试。无需修改 `PredicateHelper`、`NodeResolver` 或 `BulkConditionSupport` — 它们自动委托给 `Op.resolve()`。

## 测试

### 前置条件
- 本地 MySQL 8.0 运行，已创建 `test` 数据库
- 配置：`src/test/resources/application.properties`（`jdbc:mysql://localhost:3306/test`）
- 凭证通过环境变量：`DB_USERNAME=root`、`DB_PASSWORD=1351.zhong`
- `spring-boot-starter-security` 是测试依赖（用于 `SecurityContextAuditUserProvider` 测试）

### 测试模式

单元测试使用 `@DataJpaTest`：
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

**关键：`@DataJpaTest` 不会激活 `MyJpaPlusAutoConfiguration`**。批量操作（`update()/delete()/merge()`）会抛出 `UnsupportedOperationException`。需添加 `@Import(MyJpaPlusAutoConfiguration.class)` 或 `@ContextConfiguration(classes = TestApplication.class)`。

**关键：`@EnableJpaRepositories` 必须指定 `repositoryBaseClass = DefaultMyJpaRepository.class`**，否则测试中软删除不会生效。不指定时 Spring 使用 `SimpleJpaRepository`，`deleteByIdIfExists` 等方法不可用。

**MySQL 数据隔离**：MySQL 在方法之间保留数据。每个 `@DataJpaTest` 需要：
```java
@BeforeEach
void setUp() {
    repository.deleteAll();
    repository.flush();
}
```

**连接池**：`application.properties` 配置 HikariCP `maximum-pool-size=5`。Surefire 使用 `parallel=none` + `forkCount=1` 防止连接池耗尽。

**集成测试**：`@Tag("integration")` + Testcontainers（PostgreSQL、MySQL）

### 测试陷阱

- **Spring 代理包装**：`deleteAllById(null)` 和 `deleteInBatch(null)` 抛出 `InvalidDataAccessApiUsageException`（Spring 包装），而非 `IllegalArgumentException`。使用 `assertThrows(Exception.class, ...)`。
- **SubQuerySpec 延迟执行**：`qs.exists()` 存储 consumer — 在 `toSpecification()` 时才执行。测试异常：`assertThrows(X.class, () -> parentRepository.findAll(qs.toSpecification()))`。
- **反射 `InvocationTargetException`**：通过 `Method.invoke()` 调用时异常被包装。需 `.getCause()` 解包。
- **MySQL 自引用 UPDATE**：MySQL 阻止 `UPDATE ... WHERE EXISTS(SELECT ... FROM same_table)`。在 EXISTS 子查询中使用不同实体类型。
- **`saveAllBatchedPure`** 总是调用 `persist()` — 分离/已存在实体抛出 `EntityExistsException`。混合列表使用 `saveAllBatched`。
- **GlobalConfigHolder.getConfig()** 未配置时返回默认实例。测试需在 `@BeforeEach`/`@AfterEach` 中调用 `setConfig(null)`。
- **EncryptConverter 测试设置**：在 `@BeforeEach`/`@AfterEach` 中调用 `EncryptConverter.clearCaches()` 并通过反射重置 `keyValidated`。设置 `myjpa-plus.encrypt.skip-salt-check=true` 跳过 PBKDF2 盐值检查。
- **`deleteByIdIfExists`**：此方法在 `DefaultMyJpaRepository` 上，不在接口上。通过反射测试：`Method m = DefaultMyJpaRepository.class.getMethod("deleteByIdIfExists", Object.class); m.invoke(proxy, id);`

### 当前测试数量：3010 个单元测试

## 关键约定

- JaCoCo 最低 90% 行覆盖率（强制执行于：spec、update、repository、projection、template、annotation、autoconfigure、codegen、converter、exception、monitor、softdelete、util）
- SpotBugs Medium 阈值
- PR 检查清单：spotless:check → verify → SpotBugs → JaCoCo → 测试 → CHANGELOG

## SpotBugs 抑制

使用 `@edu.umd.cs.findbugs.annotations.SuppressFBWarnings` 标注有意设计：
- `MS_EXPOSE_REP`：单例有意暴露配置（GlobalConfigHolder）
- `RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE`：边界情况的防御性空检查
- `EI_EXPOSE_REP` / `EI_EXPOSE_REP2`：Record 组件或缓存的有意暴露

## 添加新运算符（4 文件同步清单）

1. `ConditionNode.java` — `Op` 枚举（+ `resolve()` case，或新节点类型的 sealed interface `permits`）
2. `ConditionBuilder.java` — default 方法
3. `ConditionalMethods.java` — 抽象方法声明（如果批量操作需要）
4. `QuerySpecTest.java` — 测试用例

**无需修改**：`PredicateHelper`、`NodeResolver`、`BulkConditionSupport`、`OrConditionBuilder` — 它们自动委托给 `Op.resolve()`。

## 提取的工具类

重构过程中提取的内部辅助类（包级私有，非公共 API）：

| 类 | 包 | 用途 |
|---|---|---|
| `CacheKeyBuilder` | `spec` | 从条件树生成缓存键（从 QuerySpec 提取） |
| `PredicateHelper` | `spec` | 查询和批量操作的共享谓词构建 |
| `AggregateHelper` | `spec` | HAVING 运算符验证和表达式比较 |

## 关键类中的辅助方法

**DefaultMyJpaRepository**（557 行）：
- `executeDeleteOrBlock()` — 统一的三路删除逻辑（软删除 → 硬删除阻断 → 硬删除回退）
- `withIdAndSoftDelete()` — 基于 ID 的规格与软删除过滤器合并
- `isAutoFilterEnabled()` / `isBlockUnconditionalDelete()` — 优先检查 GlobalConfigHolder，回退到本地 ConfigProvider

**MyJpaTemplate**（1557 行）：
- `validateQueryParams()` — entityClass + spec 的 null 检查
- `validateBatchParams()` — param + 正 batchSize 的 null 检查
- `requireInitialized()` — 统一的 getter 空值守卫与错误消息

**ConditionNode.Op**：
- `toObjectArray()` — 将原生类型数组（int[]、long[] 等）转换为 Object[]，用于 IN/NOT_IN 操作符
