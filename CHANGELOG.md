# MyJpa-Plus 更新日志

所有显著变更均记录在本文件中。格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.3.11] - 未发布

### 变更
- **删除 `asDto()`** — 移除 `QuerySpec.asDto()` 和 `getProjectionDtoClass()`，投影目标类型改由方法参数决定
- **类型安全的投影查询** — `MyJpaRepository` 新增 `<R> List<R> find(Class<R> resultType, Consumer<QuerySpec<T>> config)` 和 `<R> List<R> find(Class<R> resultType, Specification<T> spec)` 方法；`MyJpaTemplate` 新增 `<T, R> List<R> find(Class<T> entityClass, Class<R> resultType, QuerySpec<T> spec)` 重载
- **投影查询强制走 `find()`** — `DefaultMyJpaRepository.findAll(spec)` 在投影模式下抛出 `UnsupportedOperationException`，引导用户使用 `find(Tuple.class, spec)` 或 `find(Dto.class, spec)`
- **`MyJpaTemplate.find(Class, QuerySpec)` 返回类型修正** — 返回 `List<Tuple>` 而非 `List<T>`
- **移除旧代码生成模块** — 删除 `com.zsubera.jpa.codegen.EntityCodeGenerator`，代码生成功能迁移至独立 Maven 插件项目 `myjpa-plus-maven-plugin`
- **SampledEvictionCache.setMaxSize() 初始化保护** — 新增 `initialized` 标志，缓存初始化后调用 `setMaxSize()` 会记录警告日志和堆栈跟踪，防止运行时意外调用导致数据丢失

### 新增
- **SoftDeleteBulkExecutor 软删除乐观锁检查** — 新增 `softDeleteByIdsWithVersionCheck(em, entityClass, ids, expectedVersion)` 方法，支持可选的 `@Version` 乐观锁检查；当提供 `expectedVersion` 时，WHERE 子句包含版本条件，版本不匹配时抛出 `OptimisticLockException`
- **EncryptionKeyManager Spring Environment 生产检测** — 新增 `setSpringProductionEnvironment(Boolean)` 方法，由自动配置注入 Spring `Environment` 的 active profiles，解决仅通过 `application.yml` 配置 `spring.profiles.active=prod` 时无法检测生产环境的问题
- **EncryptionKeyManager PBKDF2 迭代次数运行时保护** — 新增 `keysInUse` 标志，密钥首次派生后禁止更改 PBKDF2 迭代次数，防止运行时更改导致现有密文无法解密
- **DeleteSpec.executeAsSoftDeleteInTransaction** — 新增事务管理方法，包装软删除操作以自动管理事务，修复在非事务上下文中调用时抛出 `TransactionRequiredException` 的问题

### 修复
- **JoinGroup.or(Consumer) 单消费者 OR 语义错误** — `JoinGroup.or(Consumer)` 单消费者变体未将 lambda 内部条件包装在 AndNode 中，导致链式调用的条件被 OR 而非 AND 组合。修复：单消费者变体与多消费者变体保持一致，将 lambda 内部条件包装在 AndNode 中
- **QueryBuildHelper.executeCountQuery() 软删除过滤不一致** — 当 `GlobalConfigHolder.getConfig()` 返回 null 时，count 查询忽略软删除过滤（`autoFilterEnabled=false`），而 `MyJpaTemplate.shouldApplySoftDeleteFilter()` 返回 true（`cfg == null` 时默认开启）。修复：count 查询与数据查询保持一致的软删除过滤逻辑
- **硬删除路径 EntityModifiedEvent affectedRows 不准确** — `deleteAll()`、`deleteAllInBatch()` 硬删除路径发布事件时硬编码 `affectedRows=1`，`deleteAllById()`、`deleteAll(Iterable)`、`deleteInBatch()` 硬删除路径使用输入列表大小而非实际删除行数。修复：所有硬删除路径改用 CriteriaDelete 批量操作并捕获 `executeUpdate()` 返回值，确保事件发布准确的 affectedRows 计数
- **RedisCacheAdapter.putAll() TTL <= 0 时仍缓存条目** — `putAll()` 在 `defaultTtlSeconds <= 0` 时使用无过期时间的 `set()` 存储条目，导致 Redis 中永久积累过期数据。修复：`ttlSeconds <= 0` 时直接返回，与 `put()` 方法行为一致
- **AbstractBulkOperationSpec.rollbackOrMarkRollbackOnly() Spring 双重回滚** — 在 Spring 管理的事务中直接调用 `tx.rollback()` 后，Spring 的 `processRollback()` 会再次尝试回滚，抛出 `IllegalStateException("Not valid without active transaction")` 并丢失原始异常信息。修复：优先检测 Spring 事务并使用 `setRollbackOnly()`，仅在非 Spring 事务中使用 `tx.rollback()`
- **DefaultMyJpaRepository.findById/existsById/findAllById 复合主键不兼容** — 对 `@IdClass` 实体，`EntityClassResolver.resolveIdFieldName()` 仅返回第一个 `@Id` 字段名，导致 `cb.equal(root.get(firstIdFieldName), id)` 将单列与 `@IdClass` 实例比较，类型不匹配抛出异常。修复：复合主键实体回退到 `entityManager.find()` + 手动软删除检查
- **PostgresDialect.buildUpsertSql() 缺少空列检查** — 单行 UPSERT 路径未检查 `insertColumns` 是否为空，当实体仅有 `@GeneratedValue` 的 `@Id` 字段时生成无效 SQL `INSERT INTO t () VALUES ()`。修复：添加与 MySQL 方言一致的空列检查
- **SoftDeleteBulkExecutor.resolveTimestampColumn() 未验证字段存在性** — 原生 SQL 路径在 `deletedTimestampField` 引用不存在的字段时，会通过 `camelToSnake()` 生成引用不存在列的 SQL，导致运行时 SQL 异常。而 Criteria API 路径会优雅忽略。修复：添加字段存在性检查，不存在时返回 null 跳过时间戳列
- **PredicateHelper.validateRange() 未检查 end 值的 Infinity/NaN** — 同类型路径仅检查 `start` 值的 Infinity/NaN，`end` 值的 Infinity/NaN 会通过验证并生成无效 SQL。修复：对 Double 和 Float 类型同时检查 start 和 end 值
- **CoalesceUpsertTransformer COALESCE 回退列名未引用** — `new Column(col.getColumnName())` 创建的 Column 对象丢失引号元数据，保留字列名（如 `order`）生成无效 SQL。修复：复用原始 Column 对象保留引号
- **Oracle/SqlServer MERGE ON 子句引用不存在的源列** — 当冲突列不在 insertColumns 中时，MERGE ON 子句引用 `source."conflict_col"` 导致数据库报错。修复：将缺失的冲突列以 NULL 值添加到源子查询
- **softDeleteByIdsWithVersionCheck() 部分成功静默** — 批量软删除部分 ID 版本不匹配时，仅返回成功计数而不抛异常或记录警告，调用方无法识别哪些 ID 未被删除。修复：添加警告日志
- **QueryProjectionSupport 投影 count 查询忽略 GROUP BY** — 当投影查询包含 `groupBy()` 时，`executeCountQuery()` 传入 null CriteriaQuery 避免 GROUP BY 副作用，导致 `totalElements` 返回原始行数而非分组数。修复：检测 GROUP BY 存在时执行数据查询并在 Java 中计数分组数
- **Spec-based 操作缺失 EntityModifiedEvent 发布** — `MyJpaRepository` 接口默认方法和 `DefaultMyJpaRepository` 覆写方法中，`update(Consumer)`、`delete(Consumer)`、`merge(Consumer)`、`execute(UpdateSpec)`、`execute(DeleteSpec)`、`execute(MergeSpec)` 六个 spec-based 操作在成功变更后（`affected > 0`）发布 `EntityModifiedEvent`，修复应用级缓存（Redis、Caffeine QueryCacheManager、CacheAdapter）在这些操作后未失效导致脏数据的问题
- **BatchSaveTemplate.isDefaultPrimitiveValue() Float/Double 缺失** — 添加 `Float` 和 `Double` 零值检查，修复 `@Id float/double` 类型实体被错误识别为已存在的问题
- **MergeSpec 不必要的 em.flush()** — 移除 `doBatchSingleRow` 和 `doBatchMultiRow` 中的 `em.flush()` 调用，修复 AUTO_CLEAR 模式下意外持久化无关脏数据的问题
- **DeleteSpec.executeAsSoftDelete() 双重软删除过滤器** — 移除手动添加的软删除守卫谓词，因为 `buildPredicates()` 已经处理，修复 WHERE 子句中冗余的 `deleted = FALSE` 条件
- **DefaultMyJpaRepository.delete() 软删除事务管理** — 软删除路径现在使用 `executeAsSoftDeleteInTransaction()` 包装，修复在非事务上下文中调用时抛出 `TransactionRequiredException` 的问题
- **GlobalConfigHolder.getConfig() 重验证失败循环** — 重验证失败时将 `cachedBeanVerifyTime` 设置为当前时间而非 0，避免立即重试导致警告日志风暴
- **EncryptionKeyManager 非单调时钟** — `getKeyVersion()` 改用 `System.nanoTime()` 替代 `System.currentTimeMillis()`，修复 NTP 同步可能导致的缓存过期异常
- **QueryCacheManager.computeIfAbsent() 幽灵条目** — 在 `addToPrefixIndex` 之前检查 `cache.getIfPresent(key) != null`，避免创建幽灵条目
- **MergeSpec.executeBatchInSeparateTransactions 无限制** — 添加 `resolveMaxBulkOperationRows()` 检查，修复批量 UPSERT 操作不遵守最大行数限制的问题
- **MyJpaTemplate.publishEntityModifiedEvent 多类缓存驱逐** — 为所有不同的实体类发布事件而非仅第一个元素的类，修复混合类型批量保存时缓存驱逐不完整的问题
- **MergeSpec.executeSingleBatchInTransaction() em.flush() 持久化无关脏实体** — 移除 `tx.commit()` 前的 `em.flush()` 调用，修复首次批量 UPSERT 时将调用方持久化上下文中无关脏实体意外持久化的问题
- **EntityFieldExtractor.resolveColumnName() boolean 字段列名解析错误** — 添加 `isXxx()` getter 回退，修复属性访问模式下 `@Column` 注解在 `isActive()` getter 上时列名解析为 snake_case 而非注解值的问题
- **SoftDeleteHelper.doResolveColumnName() 缺少标识符校验和 boolean getter** — 在 camelToSnake 回退路径添加 `IdentifierValidator.validateColumnName()` 调用，并添加 boolean 字段的 `isXxx()` getter 回退，与 `doResolveIdColumnName()` 保持一致

### 迁移
- `asDto()` 已删除，需替换为 `find()` 的 `resultType` 参数
  ```java
  // 之前
  repo.findAll(new QuerySpec<User>().select(User::getName).asDto(NameDto.class));
  // 之后
  repo.find(NameDto.class, s -> s.select(User::getName));
  ```
- `findAll(spec)` 不再支持投影模式，需改用 `find()`:
  ```java
  // 之前
  repo.findAll(new QuerySpec<User>().select(User::getName));
  // 之后
  repo.find(Tuple.class, s -> s.select(User::getName));
  ```

## [1.3.1] - 2026-07-14

### 新增
- **持久化上下文策略** — `AbstractBulkOperationSpec.persistenceStrategy()` 和 `MergeSpec.persistenceStrategy()` 支持 `PersistenceContextStrategy.DEFER_TO_CALLER`，允许调用方自行管理批量操作后的 flush/clear，默认保持 `AUTO_CLEAR` 向后兼容
- **并发安全的批量更新/删除限制** — 增强行数限制检查的线程安全性，修复并发场景下限制绕过问题

### 变更
- **Caffeine 缓存统一** — 全部手写缓存实现替换为 Caffeine，消除约 1000 行手写缓存代码
  - `SampledEvictionCache`：内部实现从 ConcurrentHashMap + 采样驱逐改为 Caffeine（13+ 处引用）
  - `QueryCacheManager`：从 847 行手写实现缩减为 ~300 行 Caffeine 后端
  - `EncryptionKeyManager`：密钥缓存从 ConcurrentHashMap + RWLock + 手动 LRU 改为 Caffeine
  - `DialectDetector`：方言缓存从 ConcurrentHashMap + 手动驱逐改为 Caffeine
  - `QueryMetricsCollector`：指标存储从 ConcurrentHashMap + 手动驱逐改为 Caffeine
  - 13 处 `ConcurrentReferenceHashMap` 弱引用缓存替换为 `Caffeine.newBuilder().weakKeys()`
  - 移除 `EncryptionKeyManager` 中的 `ReentrantReadWriteLock` 和手动 LRU 淘汰逻辑
- **依赖更新** — 新增 Caffeine 3.1.8（由 Spring Boot BOM 管理版本）
- **CteSpec UNION SELECT 检测移除** — 移除 UNION SELECT / UNION ALL SELECT 注入检测模式，它们是非递归和递归 CTE 中的合法语法（如 `WITH cte AS (SELECT 1 UNION ALL SELECT 2) SELECT * FROM cte`），之前会导致误报
- **BulkTransactionHelper clear 时序** — `em.clear()` 移至 `tx.commit()` 之后执行，修复新事务中清空持久化上下文导致提交后 L1 缓存丢失的问题
- **CacheEvictionHelper Hibernate 检测** — 增加 `hibernateSessionClass.isInstance()` 空值安全检测，修复非 Hibernate 环境下的 `ClassCastException`

### 修复
- **EncryptConverter Cipher 池 RuntimeException 泄漏** — `cipher.init()` 抛出 `IllegalArgumentException` 等 RuntimeException 时 Cipher 未归还池中，添加 `cipherReturned` 标志位确保所有异常路径归还
- **DefaultMyJpaRepository 批量操作忽略 AUTO_FILTER_OVERRIDE** — `update()` 和 `delete()` 默认方法未检查 ThreadLocal 覆盖值，覆写为委托 `shouldApplySoftDeleteFilter()`
- **SoftDeleteHelper.isSoftDeleted NPE 防御** — 弱引用缓存驱逐重建时 annotation 可能为 null，添加防御性检查回退到 Boolean 类型判断
- **SoftDeleteBulkExecutor 计数查询** — `countActiveRows()` 中 `Boolean.FALSE` 修正为 `Boolean.TRUE`，修复软删除布尔类型实体的活跃行计数错误
- **SoftDeleteHelper.clearCaches 方言缓存** — 新增 `cachedDialect = null` 清理，修复缓存清理后方言残留导致上下文切换错误
- **DeleteSpec.executeAsSoftDelete 行数限制** — COUNT 查询现在包含软删除过滤条件，精确匹配实际 UPDATE 影响行数，防止并发修改导致计数不准确
- **DeleteSpec JTA 回滚** — `rollback()` 调用前添加 `IllegalStateException` 捕获，修复 JTA 事务环境中回滚时崩溃
- **executeCountQuery 遗漏 AUTO_FILTER_OVERRIDE** — 修复分页计数查询未检查 `AUTO_FILTER_OVERRIDE` ThreadLocal，导致分页总记录数不一致
- **批量保存异常处理和缓存清理** — 修复批量保存失败时缓存未正确清理的问题
- **条件节点空值处理** — 修复 `null` 值在条件节点中的处理不一致，补充 71 个测试覆盖关键缺口
- **批量操作安全漏洞** — 修复批量操作中的多个安全漏洞和性能问题（SQL 注入、行数限制绕过等）
- **批量操作循环重复处理相同行** — 修复游标分页批量处理中因排序不稳定导致的重复处理问题
- **高影响正确性修复** — 修复 17 个正确性缺陷，覆盖数据完整性、并发安全、安全漏洞和崩溃风险
- **EntityManagerHelper 多数据源竞争条件** — 修复并发注册时的竞态条件
- **SQL 标识符引用** — 修复 SQL 标识符引用和多数据源支持问题

### 优化
- **查询性能和加密转换器** — 优化多个组件的性能和线程安全性
- **批量保存模板版本方法处理** — 修复版本方法处理和安全警告日志

### 测试
- **SampledEvictionCacheTest 更新** — 适配 Caffeine 行为（lazy eviction、estimatedSize 近似值）
- **LambdaUtilsTest 更新** — 适配 Caffeine 缓存驱逐行为
- **SlowQueryDataSourceProxy 补充测试** — 新增内部类测试覆盖
- **SlowQueryDataSourceProxyPostProcessor 测试** — 新增后处理代理测试
- **PredicateHelper.validateRange 测试** — 新增边界情况测试
- **加密转换器、条件节点、异常处理** — 补充测试覆盖
- **预存测试隔离修复** — 修复 6 个预存测试的隔离性问题

## [1.3.0] - 2026-07-04

### 新增
- **FAQ 文档** — 新增 `docs/FAQ.md`，覆盖安装配置、查询构建、批量操作、软删除、加密脱敏、性能调优、兼容性等 30+ 常见问题
- **数据库兼容性矩阵** — 新增 `docs/database-compatibility.md`，包含 4 种数据库 × 12 个维度的详细对比
- **executeWithCallbacks** — `MergeSpec.executeWithCallbacks(em)` 先 flush 持久化上下文触发 JPA 生命周期回调后再执行原生 UPSERT
- **多行 UPSERT 批处理** — `MergeSpec.executeBatch()` 自动使用 `INSERT INTO ... VALUES (...), (...) ON CONFLICT ...` 多行语法（MySQL/PostgreSQL），方言不支持时回退逐行
- **supportsBatchUpsert()** — `DialectStrategy` 新增能力检测方法，`MergeSpec` 改用能力检查而非 try-catch
- **SoftDeleteBulkExecutor** — 从 `SoftDeleteHelper` 提取批量软删除操作（~450 行），Helper 降至 ~740 行，保留所有公共 API 委托保持向后兼容
- **查询 Lambda 便捷重载** — `MyJpaRepository` 和 `MyJpaTemplate` 新增 `Consumer<QuerySpec<T>>` Lambda 重载，无需 `new QuerySpec<>()`
  - `findAll(consumer)`, `findOne(consumer)`, `count(consumer)`, `exists(consumer)`
  - `MyJpaTemplate` 同步新增对应 Lambda 重载
- **QuerySpec.of() 工厂方法** — 新增 `QuerySpec.of(consumer)` 静态工厂方法，将 3 行创建代码简化为 1 行
- **虚拟线程兼容性** — `SoftDeleteContext` 和 `DefaultMyJpaRepository` 完全兼容 Java 21+ 虚拟线程
  - 新增 `withIgnore(Runnable)` 和 `withIgnore(Supplier)` 便捷方法，自动管理生命周期
  - 虚拟线程隔离性验证测试（`SoftDeleteContextVirtualThreadTest`）
- **UPSERT 方言扩展** — `MergeSpec` 支持 4 种数据库方言
  - 新增 `OracleDialect`（`MERGE INTO ... USING ... ON ... WHEN MATCHED/NOT MATCHED`）
  - 新增 `SqlServerDialect`（`[方括号]` 转义 + `MERGE INTO`）
  - `DialectDetector` 默认注册 postgresql、mysql、oracle、sqlserver 四种方言
  - 新增 `removeDialect()` 运行时移除方言方法
- **聚合查询工具类** — 新增 `QueryAggregates` 提供独立的 `count`/`sum`/`avg`/`max`/`min` 聚合表达式工厂方法
- **UPSERT 方言测试** — 新增 `OracleDialectTest`、`SqlServerDialectTest`
- **softDeleteAll 行数保护** — `SoftDeleteHelper.softDeleteAll()` 新增 `maxRows` 参数，默认最多更新 10000 行
- **multiLike 嵌套字段校验** — `multiLike(keyword, "address.city")` 现在对每段调用 `IdentifierValidator.validateColumnName()` 进行安全校验
- **EncryptConverter 事务清理** — 新增 `registerTransactionCleanupIfNeeded()`，在事务中自动注册 `afterCompletion` 回调清理 Cipher ThreadLocal，防止虚拟线程场景内存泄漏
- **CacheAdapter SPI** — 新增可插拔缓存适配器接口，支持注入 Redis/Caffeine 等分布式缓存
  - 新增 `CacheAdapter` 接口、`DisabledCacheAdapter` 无操作实现
  - `QueryCacheManager` 实现 `CacheAdapter`（向后兼容）
  - `MyJpaTemplate` 内部使用 `CacheAdapter`，新增 `setCacheAdapter()`/`getCacheAdapter()`
  - `CacheInvalidationListener` 接受 `CacheAdapter`（旧 `QueryCacheManager` 构造函数标记为 `@Deprecated`）
- **Java 模块系统兼容性** — 完整的 `--add-opens` 修复指引
  - `ModuleCompatibilityChecker` 输出 Maven/Gradle/CLI/环境变量修复命令
  - `LambdaUtils` 错误消息包含构建工具配置
  - README.md 新增醒目的模块系统兼容性章节
- **Op.resolve() 策略模式** — `Op` 枚举作为谓词构建的唯一真实来源
  - 添加新运算符从 7 文件同步简化为 4 文件
  - `PredicateHelper`、`BulkConditionSupport` 自动委托给 `Op.resolve()`

### 优化
- **EncryptionKeyManager LRU 缓存淘汰** — 密钥缓存从 FIFO 改为 LRU 策略，新增访问时间戳追踪，多版本轮换场景下减少密钥重新派生次数
- **SlowQueryDataSourceProxy 移除冗余锁** — `createProxy` 方法移除 `ReentrantLock`，直接使用 `SampledEvictionCache.computeIfAbsent()` 的 ConcurrentHashMap 原子性保证线程安全
- **QuerySpec 拆分** — 从 1265 行拆分为 887 行 + 7 个辅助类
  - `QueryHavingSupport`: HAVING 条件（通用 + 类型安全聚合）
  - `QueryConditionSupport`: 协调层（子查询/JOIN/组合）
  - `QueryCompositionSupport`: OR/NOT 组、条件组合
  - `QuerySubQuerySupport`: EXISTS/IN 子查询
  - `QueryJoinSupport`: INNER/LEFT/FETCH JOIN
  - `QueryAggregateSupport`: GROUP BY 字段管理
  - `QueryOrderBySupport`: ORDER BY 字段管理
- **Deprecated API 清理** — 移除 11 个废弃方法，简化代码库
  - `QuerySpec`: `setGlobalConfig()`, `setMaxTimeoutSeconds()`, `toSql()`
  - `DefaultMyJpaRepository`: `setAutoFilterEnabled()`, `setBlockUnconditionalDelete()`, `deleteByIdOrThrow()`
  - `ConditionBuilder`: `addSafeFunctionNames()`, `addBooleanFunctionNames()`, `freezeExtraFunctionNames()`
  - `EncryptConverter`: `clearCacheForTesting()`
  - `CacheInvalidationListener`: `CacheInvalidationListener(QueryCacheManager)` 构造函数
  - `MyJpaPlusGlobalConfig`: `setAutoFilterEnabled()`
  - `SoftDeleteContext`: `captureAndReset()`
- **@since/@deprecated 注解清理** — 移除所有文件中的 `@since` 注解
- **HikariCP 连接池配置** — 测试环境配置 `maximum-pool-size=5`，解决 MySQL 连接池耗尽问题
- **EntityManagerHelper 单数据源快速路径** — 移除 `setEntityManagerFactory()` 中不安全的 `allResolversUseDefault` 竞态赋值，由 `register/remove` 统一管理
- **BulkOperationTemplate 迭代计数** — 修复 `executeBatchInSeparateTransactionsWithResult` 中失败批次 iteration 双重递增问题，统一到循环末尾递增
- **MergeSpec 事务管理** — 提取 `safeRollback()` 方法消除重复的 rollback 逻辑，`executeInManagedTransaction` 和 `executeBatchInSeparateTransactions` 统一使用
- **MergeSpec.executeBatch 事务检查** — 无活动事务时抛出 `MyJpaPlusException` 而非静默执行
- **InClauseBuilder NullFilterResult** — 引入 `NullFilterResult` record 替代不安全的 `Object[]` 返回类型
- **UpdateSpec 缓存驱逐** — 将 `NUMERIC_FIELD_CACHE` 驱逐移入 `computeIfAbsent` 内部，避免多线程并发驱逐
- **UpdateSpec 行数检查** — `execute()` 在 limit 配置为 `Integer.MAX_VALUE` 时跳过 probe query
- **QuerySpec RawNode 缓存键** — 使用 `className + hashCode` 替代 `System.identityHashCode`，提高缓存命中率
- **QuerySpec.copy() 性能优化** — 空条件树使用快速路径，跳过深拷贝；减少不必要的集合拷贝
- **EntityCodeGenerator 实验性标记** — 标记为 `@apiNote Experimental`，明确为独立脚手架工具，不属于核心 API
- **QueryCacheManager 驱逐策略优化** — 驱逐循环添加有界重试限制（`max(16, maxEntries/10)`），避免高并发下长时间持锁
- **InClauseBuilder 语义可见性** — `notIn()` 全 NULL 时添加警告日志，提醒用户行为与 SQL 语义不同
- **@SuppressFBWarnings 审计** — 修复 `SoftDeleteHelper` 误用的注解，为 7 个缺失说明的注解添加 justification，改善 `QuerySpec` SE_BAD_FIELD 说明
- **or(QuerySpec) 文档改进** — Javadoc 明确说明返回 `Specification<T>` 而非 `QuerySpec<T>` 的原因，引导用户使用 `or(Consumer<OrGroup>)` 模式

### 修复
- **EncryptConverter Cipher 对象池泄漏** — 修复加密/解密失败路径中 Cipher 对象未归还到对象池的问题，防止长时间运行后池耗尽
- **EncryptConverter 测试异常类型修正** — 4 个测试断言从 `IllegalStateException` 修正为 `MyJpaPlusException`，与实际实现一致
- **BulkOperationTemplate 迭代计数** — 修复 `executeBatchInSeparateTransactionsWithResult` 中失败批次 iteration 双重递增导致 `maxBatchIterations` 提前触发的问题
- **MergeSpec 事务状态追踪** — 修复 `executeBatchInSeparateTransactions` 中失败回滚时 `total` 回退不一致的问题，提取 `safeRollback()` 统一异常处理
- **MergeSpec 事务异常处理** — 修复 `executeInManagedTransaction` 中 rollback 异常可能导致递归的风险
- **EntityManagerHelper 竞态条件** — 移除 `setEntityManagerFactory()` 中检查 `resolvers.isEmpty()` 的竞态赋值
- **EncryptConverter ThreadLocal 泄漏** — 在虚拟线程场景下自动注册事务完成后清理 Cipher ThreadLocal
- **QuerySpec.copy() 深拷贝** — 修复浅拷贝导致 JoinNode/OrNode/AndNode 嵌套条件共享可变状态的问题
- **QueryCacheManager deque 漂移** — 改进 drift 清理机制，drift 超过阈值时执行全量遍历清理
- **UpdateSpec 缓存驱逐** — 将概率采样驱逐（10%）改为确定性驱逐，消除高并发下缓存无限增长风险
- **ConditionBuilder 扩展性文档修正** — 移除过时的"通过系统属性扩展"描述，改为实际可用的 `addSafeFunctionNames()` API
- **批量操作安全漏洞** — 修复批量操作中的安全漏洞和性能问题
- **OptimisticLockRetryAdvisorTest** — 修复上下文加载失败及缓存自动失效问题
- **QuerySpec.copy() groupStack** — 修复顺序反转 bug
- **DefaultMyJpaRepository 配置同步** — `isAutoFilterEnabled()` 和 `isBlockUnconditionalDelete()` 优先检查 `GlobalConfigHolder`，修复移除废弃同步方法后配置断开的问题
- **NodeResolver LEFT JOIN 软删除过滤位置** — 软删除条件从 WHERE 子句移至 ON 子句，修复 LEFT JOIN 退化为 INNER JOIN 的问题
- **DefaultMyJpaRepository.deleteByIdIfExists 硬编码谓词** — 改用 `SoftDeleteHelper.buildNotDeleted()` 替换硬编码 `Boolean.FALSE` 谓词，支持非 Boolean 软删除类型
- **JTA 事务检测崩溃** — `AbstractBulkOperationSpec` 和 `BulkTransactionHelper` 捕获 `IllegalStateException`，修复 JTA 环境中 `em.getTransaction()` 崩溃
- **EntityManagerHelper 多数据源竞争条件** — `resolvers.put()` 和 `registerEntityManagerFactoryIfAbsent()` 加同步锁；每个 entry 独立探测类型，修复并发注册时的竞态
- **LambdaUtils.writeReplace 类型转换** — 修复 JDK 不同版本上 `writeReplace()` 返回类型不一致导致的 `ClassCastException`
- **PredicateHelper BigDecimal 精度丢失** — `between` 操作中改用 `new BigDecimal(value.toString())` 避免 `BigDecimal.valueOf(double)` 精度丢失
- **EntityFieldExtractor @Embedded 循环检测** — 从 `Set<Class<?>>` 改为 `Set<Object>`（实例检测），修复同类型不同实例误判为循环引用
- **IdentifierValidator System.getProperty 缓存** — `strictMode` 缓存到静态字段，避免每次调用 `checkHomoglyphs()` 读取系统属性
- **EncryptConverter GCM Cipher 生命周期** — 移除 `CIPHER_THREAD_LOCAL`，每次操作创建新 Cipher 实例，修复 JDK-8201324 GCM 状态损坏
- **SoftDeleteHelper 属性访问实体支持** — `resolveIdColumnName` 和 `resolveColumnName` 新增 getter 方法 `@Id`/`@Column` 扫描，支持 `@Access(AccessType.PROPERTY)` 实体
- **SoftDeleteHelper.softDeleteAll 事务检查** — 执行前检查 `TransactionSynchronizationManager.isActualTransactionActive()`，防止无事务的全表更新不可回滚
- **EncryptConverter 版本前缀解析失败** — 无效版本前缀从静默降级改为抛出 `MyJpaPlusException`，避免掩盖数据损坏
- **AbstractBulkOperationSpec 预执行精确计数** — `checkRowCountBeforeExecute` 改用精确 `SELECT COUNT(*)` 替代快速探测 `SELECT 1 LIMIT n`，消除探测与精确计数间的竞态窗口
- **FunctionWhitelist 懒冻结** — 新增 `containsSafeFunction()`/`containsBooleanFunction()`，消费者先查冻结快照，未命中回退到实时集合，修复启动期间冻结滞后导致的误拒绝
- **CacheKeyBuilder 递归深度保护** — `appendCacheKey` 添加 128 层递归深度限制，防止恶意深层条件树导致 `StackOverflowError`

## [1.2.0] - 2026-06-12

### 新增
- **UPSERT/MERGE 支持** — `MergeSpec` 构建器，支持 PostgreSQL `ON CONFLICT`、MySQL `ON DUPLICATE KEY`、H2 `MERGE`
- **CTE 支持** — `CteSpec` 支持普通和递归 Common Table Expression
- **SQL 慢查询监控** — `SqlSlowQueryInterceptor` + `myjpa-plus.monitoring` 配置
- **字段加密** — `@Encrypt` 注解 + `EncryptConverter`（AES/GCM，随机 IV）
- **字段脱敏** — `@Mask` 注解 + `MaskSerializer`（Jackson，支持 PHONE/EMAIL/ID_CARD/NAME）
- **乐观锁自动重试** — `@RetryOnOptimisticLock` 注解，指数退避
- **查询结果缓存** — `QueryCacheManager`，TTL 过期策略
- **数据库函数调用** — `func(field, functionName, comparisonOp, value)` 条件方法
- **Case-insensitive 字符串查询** — `eqIgnoreCase`、`neIgnoreCase`、`likeIgnoreCase`
- **多字段 LIKE 搜索** — `multiLike(keyword, "field1", "field2")` 支持字符串字段名
- **String 类型软删除** — 支持 `@SoftDelete` 注解的 String 类型 deletedValue
- **实体管理器工厂支持** — 改进 `EntityManagerFactory` 集成
- **软删除仓库全面测试** — 新增软删除功能的完整测试覆盖

### 变更
- **移除 H2 数据库支持** — 测试统一使用 MySQL，移除 H2 依赖
- **移除 BaseEntity 类** — 优化 `AuditEntityListener` 实现，不再依赖基类
- **重构实体字段提取和方言检测** — 统一实体字段提取和数据库方言检测机制
- **重构批量操作模板** — 优化事务管理和内存控制
- **重构条件构建器接口** — 拆分为 8 个子接口，提升可维护性
- **代码注释国际化** — 更新代码注释为中文并改进文档质量

### 修复
- **not() 语义一致性** — 修复否定条件组的语义问题
- **缓存驱逐** — 修复批量操作中的缓存驱逐问题
- **批量操作异常处理** — 改进批量操作的异常处理和错误恢复
- **批量保存** — 修复批量保存中的多个问题
- **CTE/MergeSpec 安全检查** — 重构安全检查和批量操作
- **LIKE 通配符转义** — 修复 `likeSafe()` 的通配符转义问题
- **LambdaUtils 关闭机制** — 修复 `LambdaUtils` 的资源关闭问题
- **null 校验统一** — 统一条件方法的 null 校验逻辑
- **CI/CD 改进** — 配置 MySQL 服务、修复 OWASP 检查、优化测试配置

## [1.1.0] - 2026-05-31

### 新增
- **枚举转换支持** — `@CodeEnum` + `@CodeEnumValue` 注解解决 Hibernate 6 枚举映射问题
  - 支持 CHAR(1) 存储枚举编码（如 '0'、'1'、'M'、'F'）
  - 支持 int、long、String 类型的 code 字段
  - 无需创建转换器类，只需在枚举和实体字段上添加注解
- **multiLike 支持字符串字段名** — `multiLike(keyword, "field1", "field2")` 适用于动态字段名场景
- **软删除 Integer 类型支持** — `@SoftDelete(deletedIntValue = 1)` 支持用整数值标记删除状态
- **MyJpaTemplate.count() 方法** — 新增便捷的计数方法
- **聚合函数 API** — 新增聚合查询功能

### 变更
- `ConditionBuilder` 添加 `notBetween` 和 `likeIgnoreCase` 的条件变体
- `SubQuerySpec` 和 `AbstractBulkOperationSpec` 添加更多条件便捷方法
- 优化 `LambdaUtils` 缓存驱逐策略，使用 CAS 操作避免竞态条件
- 优化 `InClauseBuilder` 批次处理，避免内存泄漏
- 优化 `MyJpaTemplate` 深度分页警告日志，添加限流机制
- 统一废弃方法行为，优化缓存性能

### 修复
- 修复 EXISTS 子查询关联限制，支持从 Join 路径关联
- 修复 `MyJpaTemplate.findAllStream` 废弃策略，恢复为可调用的 @Deprecated 方法
- 修复 `DefaultMyJpaRepository.deleteById` 方法，正确处理软删除实体
- 修复终审报告全部 P0/P1/P2 问题（共 19 项）
- 统一日志框架为 SLF4J
- 修复 Spring Boot 配置集成问题
- 修复审计日志问题

## [1.0.0] - 2026-05-28

### 修复
- 修复 `between` 操作中类型不匹配的验证问题
- 代码格式化和文档优化

## [0.0.2] - 2026-05-28

### 新增
- **批量操作 LIMIT 支持** — `executeWithMaxRows()` 方法支持单次调用最大受影响行数限制
- **大型 IN 子句处理** — 自动拆分超过数据库参数限制的 IN 子句
- **GroupBy + Having 支持** — `groupBy(SFunction...)` 和 `having(BiFunction)` 条件方法
- **eqIgnoreCase/likeIgnoreCase** — 不区分大小写的字符串条件（基于 UPPER）
- **SubQuerySpec 关联** — 通过 `correlate(root)` 支持子查询关联
- **Spring Boot 自动配置** — 自动注册 `MyJpaTemplate` 和相关组件
- **EntityGraphHelper 测试** — 新增 17 个测试覆盖所有公共方法
- **MyJpaTemplate 全面测试** — 新增 108 个测试覆盖核心功能

### 变更
- **ConditionNode → sealed 接口** — 所有实现类均为 `final`
- **提取条件构建逻辑** — 创建 `PredicateHelper` 共享工具类，消除约 200 行重复代码
- **SpotBugs 阈值设为 Medium**
- **JaCoCo 覆盖率最低 60%**（排除 autoconfigure）
- **启用 doclint**（`reference,html`）
- **POM `<name>` 修正** — 从 `MyJpa-Plus` 修正为 `myjpa-plus`

### 修复
- `SubQuerySpec` 条件不再互相覆盖
- `select()` 不再被 `resolveExists` 静默覆盖
- `resolveSimple` 正确处理 IN/NOT_IN 中的 Collection 值
- `SoftDeleteHelper.findSoftDeleteField()` 的竞态条件修复
- `AbstractBulkOperationSpec.executeInTransaction()` 捕获 `Exception`（而非仅 `RuntimeException`）
- `LambdaUtils` 缓存按 class#method 存储
- SpotBugs MEDIUM 问题修复（6 个）

### 基础设施
- GitHub Actions CI（JDK 17/21 矩阵 + v* 标签触发发布部署）
- Dependabot 自动依赖更新
- CODE_OF_CONDUCT、ISSUE_TEMPLATE、PR_TEMPLATE、.editorconfig
- OWASP 依赖检查（每周定时扫描）

## [0.0.1] - 2026-05-26

### 初始发布
- 基于 Lambda API 的类型安全 JPA `Specification` 构建器
- `QuerySpec<T>`：eq, ne, gt, ge, lt, le, like, notLike, in, notIn, between, isNull, isNotNull
- JOIN 支持：`join()`、`leftJoin()` 配合 `JoinGroup`
- OR 分组：`or()` 配合 `OrGroup`，在连接中嵌套使用 `OrJoinGroup`
- EXISTS 子查询配合 `SubQuerySpec`
- 通过 `multiLike` 实现多字段 LIKE 搜索
- Spring MVC 参数解析器：`@SearchParam`、`@ListParam`
- 针对 Hibernate 延迟代理的 Jackson 序列化器
