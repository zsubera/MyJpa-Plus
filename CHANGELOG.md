# MyJpa-Plus 更新日志

## [1.2.0] - 2026-05-31

### 新增
- **UPSERT/MERGE 支持** — `MergeSpec` 构建器，支持 PostgreSQL `ON CONFLICT`、MySQL `ON DUPLICATE KEY`、H2 `MERGE`
- **多租户支持** — `@TenantId` + `TenantProvider` + `@IgnoreTenant`，自动在查询中追加租户过滤条件
- **CTE 支持** — `CteSpec` 支持普通和递归 Common Table Expression
- **SQL 慢查询监控** — `SqlSlowQueryInterceptor` + `myjpa-plus.monitoring` 配置
- **字段加密** — `@Encrypt` 注解 + `EncryptConverter`（AES/GCM，随机 IV）
- **字段脱敏** — `@Mask` 注解 + `MaskSerializer`（Jackson，支持 PHONE/EMAIL/ID_CARD/NAME）
- **乐观锁自动重试** — `@RetryOnOptimisticLock` 注解，指数退避
- **代码生成器** — `EntityCodeGenerator` 工具类，生成实体和 Repository 源码
- **查询结果缓存** — `QueryCacheManager`，TTL 过期策略
- **数据库函数调用** — `func(field, functionName, comparisonOp, value)` 条件方法
- **DTO 分页查询** — `ProjectionSpec.findDtoPage()` 返回 `Page<DTO>`
- **DTO 列表查询** — `ProjectionSpec.toDtoList()` / `findOneDto()`
- **Case-insensitive 字符串方法** — `containsIgnoreCase`、`startsWithIgnoreCase`、`endsWithIgnoreCase`
- **FuncNode 完整实现** — ConditionBuilder.func() + ConditionNodeResolver 解析
- **SoftDelete Integer/Enum 测试覆盖** — 新增 12 个测试用例

### 变更
- **统一三种条件评估模型** — SubQuerySpec 和 AbstractBulkOperationSpec 改用 ConditionNode AST，与 QuerySpec 统一
- **ConditionBuilder 接口拆分** — 拆分为 8 个子接口（EqualityConditions、StringConditions 等），ConditionBuilder 继承全部
- **ConditionNodeResolver 独立** — AST 解析逻辑从 QuerySpec 提取到独立的 ConditionNodeResolver 类
- **likeIgnoreCase 行为修正** — 不再自动包裹 %，与 likeSafe 保持一致
- **deepPaginationOffsetLimit 默认值统一** — 改为 -1（禁用），与 Spring Boot 配置一致
- **maxBulkOperationRows 可配置** — 新增 `myjpa-plus.query.max-bulk-operation-rows` 配置项
- **TransactionTemplate 缓存** — MyJpaTemplate 使用 DCL 懒加载缓存，避免重复创建
- **CodeEnumType.assemble() 使用缓存** — 复用 ENUM_CODE_CACHE，O(1) 查找
- **AuditEntityListener 支持实体继承** — 遍历整个类层次结构扫描审计字段
- **spring-configuration-metadata.json 补全** — 记录全部 8 个配置项

### 修复
- 移除所有 `@Deprecated(forRemoval=true)` 方法（like、notLike、rawLike、where 等）
- 修复 LambdaUtils.shutdown() 注释与实际实现不一致
- 修复 SpotBugs 新增的 4 个问题（EncryptConverter、SqlSlowQueryInterceptor、MergeSpec）

### 破坏性变更
- `like()` 和 `notLike()` 已移除，使用 `likeSafe()` / `notLikeSafe()` 替代
- `rawLike()` 已移除，使用 `contains()` / `startsWith()` / `endsWith()` 替代
- `where(BiFunction)` 和 `where(Function)` 已移除，使用类型安全的条件方法替代
- `findAllStream(Class, QuerySpec)` 已移除，使用 `findAllStream(Class, QuerySpec, Consumer)` 替代
- `ConditionNode.RawNode` 已移除
- `likeIgnoreCase()` 不再自动包裹 `%`，需要显式使用 `containsIgnoreCase()` 获取原来的行为

## [1.1.0] - 2026-05-31

### 新增
- **枚举转换支持** — `@CodeEnum` + `@CodeEnumValue` 注解解决 Hibernate 6 枚举映射问题
  - 支持 CHAR(1) 存储枚举编码（如 '0'、'1'、'M'、'F'）
  - 支持 int、long、String 类型的 code 字段
  - 无需创建转换器类，只需在枚举和实体字段上添加注解
- **multiLike 支持字符串字段名** — `multiLike(keyword, "field1", "field2")` 适用于动态字段名场景
- **软删除 Integer 类型支持** — `@SoftDelete(deletedIntValue = 1)` 支持用整数值标记删除状态
- **MyJpaTemplate.count() 方法** — 新增便捷的计数方法

### 变更
- `ConditionBuilder` 添加 `notBetween` 和 `likeIgnoreCase` 的条件变体
- `SubQuerySpec` 和 `AbstractBulkOperationSpec` 添加更多条件便捷方法
- 优化 `LambdaUtils` 缓存驱逐策略，使用 CAS 操作避免竞态条件
- 优化 `InClauseBuilder` 批次处理，避免内存泄漏
- 优化 `MyJpaTemplate` 深度分页警告日志，添加限流机制

### 修复
- 修复 EXISTS 子查询关联限制，支持从 Join 路径关联
- 修复 `MyJpaTemplate.findAllStream` 废弃策略，恢复为可调用的 @Deprecated 方法
- 修复 `SoftDeleteJpaRepository.deleteById` 方法，正确处理软删除实体

## [0.1.0-SNAPSHOT] - 2026-05-26（开发版本，已合并至 1.1.0）

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
- **消除了条件方法的三层重复：** 创建 `PredicateHelper` 共享工具类，`SubQuerySpec` 和 `AbstractBulkOperationSpec` 统一委托给 `PredicateHelper` 构建 Predicate，消除了约 200 行重复的条件构建逻辑。`ConditionBuilder`（延迟 AST 节点）保持独立不变。
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
