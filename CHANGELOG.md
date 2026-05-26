# MyJpa-Plus 更新日志

## [0.1.0-SNAPSHOT] - 2026-05-26

### 破坏性变更
- **`DeleteSpec` 现在要求显式指定 WHERE 条件。** 在不带任何条件的情况下调用 `execute()` 或 `toDelete()` 会抛出 `IllegalStateException`。请使用新的 `deleteAll(EntityManager)` 进行无条件删除。
- **修复了 `resolveOr()` 空分组的语义：** 之前返回 `cb.conjunction()`（1=1），现在返回 `cb.disjunction()`（1=0），语义上更加正确。

### 新增
- `eqIgnoreCase` / `likeIgnoreCase` — 不区分大小写的字符串条件（基于 UPPER）
- `groupBy(SFunction...)` — 支持 GROUP BY 子句
- `having(BiFunction)` — 支持聚合查询的 HAVING 子句
- `where(BiFunction)` — 原始 Predicate 注入，作为兜底方案
- `not(Consumer)` — 否定条件组
- `startsWith` / `endsWith` / `contains` — 便捷的 LIKE 方法
- `in(Collection)` / `notIn(Collection)` 重载
- Consumer 模式：`or(Consumer)` / `join(field, Consumer)` / `leftJoin(field, Consumer)`
- Spring Boot 自动配置
- 通过 `correlate(root)` 支持子查询关联
- `SubQuerySpec.correlatedEq()` — 类型化关联 Predicate 构建器
- `LambdaUtils` 属性名缓存（按 implClass + methodName）
- `in` / `notIn` 的空值校验
- `eq(field, null)` 自动转换为 `IS NULL`
- `endOr()` 在调用不匹配时抛出 `IllegalStateException`
- `DeleteSpec.deleteAll(EntityManager)` / `deleteAllInTransaction(EntityManager)` — 安全的无条件删除
- SoftDeleteHelper Specification 缓存（按 entityClass）

### 修复
- `SubQuerySpec` 条件不再互相覆盖
- `select()` 不再被 `resolveExists` 静默覆盖
- `resolveSimple` 正确处理 IN/NOT_IN 中的 Collection 值
- `SoftDeleteHelper.findSoftDeleteField()` 的竞态条件（get + computeIfAbsent）
- `AbstractBulkOperationSpec.executeInTransaction()` 现在捕获 `Exception`（而非仅 `RuntimeException`）

### 变更
- `ConditionNode` → sealed 接口；所有实现类均为 `final`
- SpotBugs 阈值设为 Medium
- JaCoCo 覆盖率最低 60%（排除 autoconfigure）
- 启用 doclint（`reference,html`）
- 版本从 `0.0.1` 升级至 `0.1.0-SNAPSHOT`（语义化版本）
- POM `<name>` 从 `MyJpa-Plus` 修正为 `myjpa-plus`

### 基础设施
- GitHub Actions CI（JDK 17/21 矩阵 + v* 标签触发发布部署）
- Dependabot 自动依赖更新
- CODE_OF_CONDUCT、ISSUE_TEMPLATE、PR_TEMPLATE、.editorconfig

## [0.0.1] - 2026-05-20（原始 jpa-extensions 分支）

### 初始发布
- 基于 Lambda API 的类型安全 JPA `Specification` 构建器
- `QuerySpec<T>`：eq, ne, gt, ge, lt, le, like, notLike, in, notIn, between, isNull, isNotNull
- JOIN 支持：`join()`、`leftJoin()` 配合 `JoinGroup`
- OR 分组：`or()` 配合 `OrGroup`，在连接中嵌套使用 `OrJoinGroup`
- EXISTS 子查询配合 `SubQuerySpec`
- 通过 `multiLike` 实现多字段 LIKE 搜索
- Spring MVC 参数解析器：`@SearchParam`、`@ListParam`
- 针对 Hibernate 延迟代理的 Jackson 序列化器
