# 升级指南

## 从 1.2.0 升级到 1.3.0

### 破坏性变更

- **移除废弃方法**：以下方法已在 1.3.0 中移除，请迁移到替代方案：
  - `QuerySpec.setGlobalConfig()` → `GlobalConfigHolder.setConfig()`
  - `QuerySpec.setMaxTimeoutSeconds()` → `myjpa-plus.query.max-timeout-seconds` 配置属性
  - `QuerySpec.toSql()` → `toDescription()`
  - `DefaultMyJpaRepository.setAutoFilterEnabled()` → `MyJpaPlusGlobalConfig.setSoftDeleteAutoFilter()`
  - `DefaultMyJpaRepository.setBlockUnconditionalDelete()` → `MyJpaPlusGlobalConfig.setBlockUnconditionalDelete()`
  - `DefaultMyJpaRepository.deleteByIdOrThrow()` → `deleteById()`
  - `ConditionBuilder.addSafeFunctionNames()` → `FunctionWhitelist.addSafeFunctionNames()`
  - `ConditionBuilder.addBooleanFunctionNames()` → `FunctionWhitelist.addBooleanFunctionNames()`
  - `ConditionBuilder.freezeExtraFunctionNames()` → `FunctionWhitelist.freezeExtraFunctionNames()`
  - `EncryptConverter.clearCacheForTesting()` → `EncryptConverter.clearCaches()`
  - `CacheInvalidationListener(QueryCacheManager)` → `CacheInvalidationListener(CacheAdapter)`
  - `MyJpaPlusGlobalConfig.setAutoFilterEnabled()` → `setSoftDeleteAutoFilter()`
  - `SoftDeleteContext.captureAndReset()` → `captureAndResetForAsync()` / `withIgnore()`
  - `MyJpaRepository.findNotDeletedAll(...)` → `findAll(...)` （自动过滤已应用）
  - `MyJpaRepository.findNotDeletedOne(...)` → `findOne(...)` （自动过滤已应用）
  - `MyJpaRepository.findNotDeletedById(...)` → `findById(...)` （自动过滤已应用）
  - `MyJpaRepository.countNotDeleted(...)` → `count(...)` （自动过滤已应用）

- **QuerySpec 拆分**：QuerySpec 内部方法已提取到辅助类，公共 API 不变
- **@since/@deprecated 注解清理**：移除所有 `@since` 注解

### 新增功能（向后兼容）

- **查询 Lambda 便捷重载**：`MyJpaRepository` 和 `MyJpaTemplate` 新增 `Consumer<QuerySpec<T>>` Lambda 重载，无需 `new QuerySpec<>()`
  - `findAll(consumer)`, `findOne(consumer)`, `count(consumer)`, `exists(consumer)`
  - **注意**：`Consumer<QuerySpec<T>>` 和 `Specification<T>` 都是函数式接口，传 `null` 时需显式转型避免歧义
- **QuerySpec.of() 工厂方法**：`QuerySpec.of(consumer)` 创建并配置实例，替代 `new QuerySpec<>() + accept()` 两步
- **虚拟线程支持**：`SoftDeleteContext.withIgnore()` 便捷方法，推荐替代手动 `pushIgnore()`/`popIgnore()`
- **UPSERT 方言扩展**：新增 Oracle 和 SQL Server 方言支持
- **聚合查询工具类**：`QueryAggregates` 提供独立的 `count`/`sum`/`avg`/`max`/`min`
- **EntityManagerHelper 快速路径**：单数据源场景自动优化，无需修改代码
- **softDeleteAll 行数保护**：`SoftDeleteHelper.softDeleteAll()` 默认最多更新 10000 行
- **multiLike 嵌套字段校验**：`multiLike(keyword, "address.city")` 对每段进行安全校验
- **EncryptConverter 事务清理**：虚拟线程场景下自动清理 Cipher ThreadLocal

### 行为变更

| 变更项 | 旧行为 | 新行为 | 影响 |
|-------|--------|--------|------|
| `InClauseBuilder.notIn()` 全 NULL | 静默返回 TRUE | 返回 TRUE + 警告日志 | 无功能变化，日志可见性提升 |
| `QuerySpec.copy()` 空条件 | 深拷贝所有集合 | 快速路径跳过 | 性能提升，无功能变化 |
| `QueryCacheManager` 驱逐 | 最多 `maxEntries` 次尝试 | 最多 `max(16, maxEntries/10)` 次 | 高并发下性能提升 |
| `QuerySpec.toPredicate()` | 调用 `validateCleanState()` | 仅在 `toSpecification()` 中验证 | Spring Data 内部组合 Specification 时不再意外触发验证 |
| `SqlSlowQueryInterceptor` | 无条件注册 | 仅在 Hibernate 环境注册（`@ConditionalOnClass`） | EclipseLink 环境不再因缺少 Hibernate 而启动失败 |
| 函数白名单扩展 | 直接修改全局 ConcurrentHashMap | 使用不可变快照（`AtomicReference<Set<String>>`） | 运行时检查无锁且线程安全 |
| `checkRowCountBeforeExecute` 计数策略 | 先 `SELECT 1 LIMIT n+1` 快速探测，超限时精确 COUNT | 直接精确 `SELECT COUNT(*)` | 更准确（消除探测与 COUNT 间的竞态），代价为多一次 COUNT 查询 |
| `FunctionWhitelist` 函数名检查 | 仅查冻结快照 | 先查冻结快照，未命中回退到 `ConcurrentHashMap` 实时集合 | 启动期间冻结滞后不再导致误拒绝 |
| `EncryptConverter.removeCipher()` | 清理 ThreadLocal Cipher | 无操作（GCM 模式下每次操作新建 Cipher 实例） | API 保持兼容，调用无副作用 |
| `EncryptConverter` 版本前缀解析失败 | 警告日志 + 静默当作无版本数据 | 抛出 `MyJpaPlusException` | 数据损坏可被及早发现，不再静默降级 |
| `IdentifierValidator.strictMode` | 每次调用读取 `System.getProperty()` | 静态初始化缓存一次，提供 `setStrictMode(boolean)` setter | 性能优化，可运行时切换 |
| `SoftDeleteHelper.softDeleteAll` | 无条件执行全表 UPDATE | 检查 `TransactionSynchronizationManager.isActualTransactionActive()` | 防止无事务场景下不可回滚的数据丢失 |
| `EntityFieldExtractor @Embedded` 循环检测 | `Set<Class<?>>`（class 引用） | `Set<Object>`（实例引用） | 同类型不同实体实例不再误判为循环 |
| `NodeResolver` 软删除 JOIN 过滤 | LEFT JOIN 的软删除条件放在 WHERE 子句 | LEFT JOIN 条件放在 `join.on()` 子句（新创建的 join） | 修复 LEFT JOIN 退化为 INNER JOIN |
| `EncryptConverter` GCM Cipher | ThreadLocal 缓存，每次操作复用 | 每次操作新建 Cipher 实例 | 修复 JDK-8201324 状态损坏，线程安全 |
| `CacheKeyBuilder.appendCacheKey` | 无递归限制 | 128 层深度限制，超出后截断为 `DEPTH_EXCEEDED` | 防止恶意深层条件树导致 StackOverflowError |

### 废弃 API（已在 1.3.0 中移除）

- `QuerySpec.setMaxTimeoutSeconds(int)` — 请使用 `myjpa-plus.query.max-timeout-seconds` 配置属性
- `DefaultMyJpaRepository.setAutoFilterEnabled(boolean)` — 请使用 `MyJpaPlusGlobalConfig.setSoftDeleteAutoFilter(boolean)`
- `DefaultMyJpaRepository.setBlockUnconditionalDelete(boolean)` — 请使用 `MyJpaPlusGlobalConfig.setBlockUnconditionalDelete(boolean)`
- `EncryptConverter.clearCacheForTesting()` — 请使用 `EncryptConverter.clearCaches()`
- `QuerySpec.setGlobalConfig()` — 请使用 `GlobalConfigHolder.setConfig()`
- `QuerySpec.toSql()` — 请使用 `toDescription()`
- `DefaultMyJpaRepository.deleteByIdOrThrow()` — 请使用 `deleteById()`
- `ConditionBuilder.addSafeFunctionNames()` — 请使用 `FunctionWhitelist.addSafeFunctionNames()`
- `ConditionBuilder.addBooleanFunctionNames()` — 请使用 `FunctionWhitelist.addBooleanFunctionNames()`
- `ConditionBuilder.freezeExtraFunctionNames()` — 请使用 `FunctionWhitelist.freezeExtraFunctionNames()`
- `CacheInvalidationListener(QueryCacheManager)` — 请使用 `CacheInvalidationListener(CacheAdapter)`
- `MyJpaPlusGlobalConfig.setAutoFilterEnabled()` — 请使用 `setSoftDeleteAutoFilter()`
- `SoftDeleteContext.captureAndReset()` — 请使用 `captureAndResetForAsync()` / `withIgnore()`

### 缺陷修复

- **BulkOperationTemplate 迭代计数**：修复 `executeBatchInSeparateTransactionsWithResult` 中失败批次 iteration 双重递增
- **MergeSpec 事务管理**：提取 `safeRollback()` 方法消除重复 rollback 逻辑，防止递归风险
- **EntityManagerHelper 竞态条件**：移除 `setEntityManagerFactory()` 中不安全的竞态赋值
- **EncryptConverter ThreadLocal 泄漏**：虚拟线程场景下自动注册事务完成后清理
- **QuerySpec.copy() 深拷贝**：修复浅拷贝导致 JoinNode/OrNode/AndNode 嵌套条件共享可变状态
- **QueryCacheManager deque 漂移**：改进 drift 清理机制，drift 超过阈值时执行全量遍历清理
- **@Deprecated 版本号修正**：`QuerySpec.setGlobalConfig()` 的 `@Deprecated(since)` 修正为 `1.3.0`
- **NodeResolver LEFT JOIN 软删除**：软删除条件从 WHERE 移至 ON 子句，修复 LEFT JOIN 退化为 INNER JOIN
- **DefaultMyJpaRepository 硬编码谓词**：`deleteByIdIfExists` 改用 `SoftDeleteHelper.buildNotDeleted()`，支持非 Boolean 软删除类型
- **LambdaUtils 类型转换**：`writeReplace()` 返回值做 `instanceof` 检查后再转型，修复 JDK 版本差异
- **PredicateHelper 精度丢失**：`between` 操作使用 `BigDecimal.toString()` 构造避免 `valueOf(double)` 精度问题
- **IdentifierValidator 性能**：`strictMode` 缓存到静态字段，消除每次调用的系统属性读取开销
- **EncryptConverter GCM 状态损坏**：移除非线程安全的 ThreadLocal Cipher 缓存，每次操作新建实例
- **SoftDeleteHelper 属性访问**：`resolveIdColumnName`/`resolveColumnName` 增加 getter 方法扫描，支持 `@Access(AccessType.PROPERTY)` 实体
- **SoftDeleteHelper 事务保护**：`softDeleteAll` 增加活动事务检查
- **AbstractBulkOperationSpec 计数准确性**：`checkRowCountBeforeExecute` 改用精确 COUNT 消除竞态
- **FunctionWhitelist 冻结滞后**：`containsSafeFunction()`/`containsBooleanFunction()` 增加实时集合回退
- **CacheKeyBuilder 递归保护**：`appendCacheKey` 添加 128 层深度限制

## 从 1.1.0 升级到 1.2.0

### 破坏性变更

- **移除 H2 数据库支持**：测试统一使用 MySQL，如需 H2 请保持 1.1.0
- **移除 BaseEntity 类**：审计字段通过 `AuditEntityListener` 自动填充，无需继承基类
- **MergeSpec 不支持自增 ID 实体**：需要业务唯一键（如 email），或使用 `@GeneratedValue(strategy=TABLE/SEQUENCE/UUID)`

### 新增功能

- UPSERT/MERGE 支持（`MergeSpec`）
- CTE 公共表表达式（`CteSpec`）
- SQL 慢查询监控
- 字段加密（`@Encrypt`）和脱敏（`@Mask`）
- 乐观锁自动重试（`@RetryOnOptimisticLock`）
- 查询结果缓存（`QueryCacheManager`）
- 数据库函数调用（`func()`）

## 配置迁移

### 1.2.0 → 1.3.0

新增配置项（可选）：

```yaml
myjpa-plus:
  query:
    extra-safe-functions:      # 新增：扩展函数白名单
      - ARRAY_TO_STRING
      - REGEXP_REPLACE
    extra-boolean-functions:   # 新增：扩展布尔函数白名单
      - MY_CUSTOM_CHECK
    lambda-cache-size: 4096    # 新增：Lambda 属性名缓存大小
```

### 1.1.0 → 1.2.0

新增配置项：

```yaml
myjpa-plus:
  soft-delete:
    auto-filter: true                     # 新增
    block-unconditional-delete: true      # 新增
  monitoring:
    enabled: false                        # 新增
    slow-query-threshold-ms: 1000         # 新增
  cache:
    auto-invalidation-enabled: true       # 新增
```
