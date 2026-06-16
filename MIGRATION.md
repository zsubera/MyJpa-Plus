# 升级指南

## 从 1.2.0 升级到 1.3.0

### 新增功能（向后兼容）

- **查询 Lambda 便捷重载**：`MyJpaRepository` 和 `MyJpaTemplate` 新增 `Consumer<QuerySpec<T>>` Lambda 重载，无需 `new QuerySpec<>()`
  - `findAll(consumer)`, `findOne(consumer)`, `count(consumer)`, `exists(consumer)`
  - `findNotDeletedAll(consumer)`, `findNotDeletedOne(consumer)`, `countNotDeleted(consumer)`
  - **注意**：`Consumer<QuerySpec<T>>` 和 `Specification<T>` 都是函数式接口，传 `null` 时需显式转型避免歧义
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

### 废弃 API

- `QuerySpec.setMaxTimeoutSeconds(int)` — 请使用 `myjpa-plus.query.max-timeout-seconds` 配置属性
- `DefaultMyJpaRepository.setAutoFilterEnabled(boolean)` — 请使用 `MyJpaPlusGlobalConfig.setAutoFilterEnabled(boolean)`
- `DefaultMyJpaRepository.setBlockUnconditionalDelete(boolean)` — 请使用 `MyJpaPlusGlobalConfig.setBlockUnconditionalDelete(boolean)`
- `EncryptConverter.clearCacheForTesting()` — 请使用 `EncryptConverter.clearCaches()`

### Bug 修复

- **BulkOperationTemplate 迭代计数**：修复 `executeBatchInSeparateTransactionsWithResult` 中失败批次 iteration 双重递增
- **MergeSpec 事务管理**：提取 `safeRollback()` 方法消除重复 rollback 逻辑，防止递归风险
- **EntityManagerHelper 竞态条件**：移除 `setEntityManagerFactory()` 中不安全的竞态赋值
- **EncryptConverter ThreadLocal 泄漏**：虚拟线程场景下自动注册事务完成后清理
- **QuerySpec.copy() 深拷贝**：修复浅拷贝导致 JoinNode/OrNode/AndNode 嵌套条件共享可变状态
- **QueryCacheManager deque 漂移**：改进 drift 清理机制，drift 超过阈值时执行全量遍历清理
- **@Deprecated 版本号修正**：`QuerySpec.setGlobalConfig()` 的 `@Deprecated(since)` 修正为 `1.3.0`

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
