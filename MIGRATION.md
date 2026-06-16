# 升级指南

## 从 1.2.0 升级到 1.3.0

### 新增功能（向后兼容）

- **虚拟线程支持**：`SoftDeleteContext.withIgnore()` 便捷方法，推荐替代手动 `pushIgnore()`/`popIgnore()`
- **UPSERT 方言扩展**：新增 Oracle 和 SQL Server 方言支持
- **聚合查询工具类**：`QueryAggregates` 提供独立的 `count`/`sum`/`avg`/`max`/`min`
- **EntityManagerHelper 快速路径**：单数据源场景自动优化，无需修改代码

### 行为变更

| 变更项 | 旧行为 | 新行为 | 影响 |
|-------|--------|--------|------|
| `InClauseBuilder.notIn()` 全 NULL | 静默返回 TRUE | 返回 TRUE + 警告日志 | 无功能变化，日志可见性提升 |
| `QuerySpec.copy()` 空条件 | 深拷贝所有集合 | 快速路径跳过 | 性能提升，无功能变化 |
| `QueryCacheManager` 驱逐 | 最多 `maxEntries` 次尝试 | 最多 `max(16, maxEntries/10)` 次 | 高并发下性能提升 |

### 废弃 API

- `QuerySpec.setMaxTimeoutSeconds(int)` — 请使用 `myjpa-plus.query.max-timeout-seconds` 配置属性
- `DefaultMyJpaRepository.setAutoFilterEnabled(boolean)` — 请使用 `MyJpaPlusGlobalConfig.setAutoFilterEnabled(boolean)`

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
    extra-boolean-functions:   # 新增：扩展布尔函数白名单
      - MY_CUSTOM_CHECK
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
