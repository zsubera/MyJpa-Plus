# 架构概览

本文档描述 MyJpa-Plus 的内部架构，包括模块关系、数据流和安全防御层。

## 模块地图

```
com.zsubera.jpa
├── spec/              # 查询规范构建器（核心）
│   ├── SFunction        — 可序列化函数式接口，用于 Lambda 属性提取
│   ├── QuerySpec        — 主入口（实现 Specification<T> + ConditionBuilder）
│   ├── SubQuerySpec     — EXISTS/IN 子查询构建器（立即求值）
│   ├── CteSpec          — CTE 构建器（原生 SQL）
│   ├── ConditionBuilder — 40+ 默认条件方法（延迟求值）
│   ├── ConditionNode    — Sealed AST 层级（11 种节点类型）
│   ├── NodeResolver     — AST → JPA Predicate 解析器
│   ├── PredicateHelper  — 共享静态谓词构建器
│   ├── FunctionWhitelist — 函数白名单（启动期 ConcurrentHashMap → 冻结快照）
│   ├── Spec             — Specification 组合工具类（and/or/not 静态方法）
│   ├── QueryAggregates  — count/sum/avg/max/min 表达式工厂
│   ├── JoinGroup / OrJoinGroup / OrGroup / NotGroup — 条件分组构建器
│   ├── BulkConditionSupport — 批量操作条件方法接口
│   ├── CacheKeyBuilder     — 查询缓存键构建（递归深度保护）
│   ├── AggregateSFunction  — 聚合函数可序列化接口
│   ├── AggregateHelper     — 聚合表达式辅助工具
│   └── QueryHavingSupport / QueryConditionSupport / QueryCompositionSupport
│       / QuerySubQuerySupport / QueryJoinSupport / QueryAggregateSupport
│       / QueryOrderBySupport — QuerySpec 拆分辅助类
│
├── update/            # 批量操作
│   ├── UpdateSpec       — 批量 UPDATE 构建器
│   ├── DeleteSpec       — 批量 DELETE 构建器
│   ├── MergeSpec        — UPSERT/MERGE 构建器（4 种方言 + 多行批处理）
│   ├── AbstractBulkOperationSpec — 批量操作条件树和行数限制检查
│   ├── OrConditionBuilder — OR 条件组构建器
│   ├── BulkTransactionHelper — 共享事务管理工具
│   ├── AuditUtils       — 审计日志和调用栈提取
│   ├── DialectDetector  — 从 EntityManager 自动检测数据库方言
│   ├── DialectStrategy  — 方言特定 UPSERT SQL 生成 SPI
│   ├── AbstractDialectStrategy — 共享转义逻辑
│   ├── MysqlDialect / PostgresDialect / OracleDialect / SqlServerDialect
│   ├── PersistenceContextStrategy — 批量操作后持久化上下文管理策略（AUTO_CLEAR / DEFER_TO_CALLER）
│   ├── CoalesceUpsertTransformer — UPSERT SQL 参数合并转换器
│   ├── SqlWithParams       — 原生 SQL + 参数绑定对
│   └── EntityFieldExtractor — 反射实体字段 → 列名/值对
│
├── template/            # 查询执行与缓存
│   ├── MyJpaTemplate    — 主查询/批量模板（Spring Bean）
│   ├── MyJpaTemplateOperations — 模板操作接口（Lambda 查询重载）
│   ├── QueryCacheManager — 基于 Caffeine 的 TTL 缓存（实现 CacheAdapter）
│   ├── CacheAdapter     — 可插拔缓存后端 SPI
│   ├── CacheInvalidationListener — 实体修改后自动驱逐缓存
│   ├── BulkOperationTemplate — 带事务管理的批量执行
│   ├── BatchSaveTemplate — 带 flush/clear 循环的批量持久化/合并
│   ├── KeysetPaginationHelper — Keyset（游标）分页辅助
│   ├── QueryBuildHelper — 查询构建辅助（Specification 合并、count 查询）
│   ├── DeepPaginationGuard — 深度分页保护
│   ├── CachedQueryResult  — 缓存查询结果封装
│   ├── DisabledCacheAdapter — 无操作缓存适配器
│   └── EntityModifiedEvent — 实体修改事件
│
├── projection/        # DTO 与 Tuple 投影（已移除，功能迁移至 QueryProjectionSupport）
│   └── ProjectionSpec   — 已移除
│
├── repository/        # 扩展 Spring Data Repository
│   ├── MyJpaRepository   — 带 Lambda DSL 的 Repository 接口
│   ├── DefaultMyJpaRepository — 带软删除的基础实现
│   ├── MyJpaRepositoryFactoryBean — 自定义 RepositoryFactoryBean
│   ├── MyJpaRepositoryFactory — Repository 工厂实现
│   ├── SoftDeleteContext  — @IgnoreSoftDelete 的 ThreadLocal 栈
│   ├── EntityManagerHelper / EntityManagerResolver — 多数据源解析器
│   ├── IgnoreSoftDeleteAdvisor — @IgnoreSoftDelete 的 AOP 切面
│   └── OptimisticLockRetryAdvisor — @RetryOnOptimisticLock 的 AOP 切面
│
├── softdelete/        # 软删除功能
│   ├── SoftDeleteHelper  — 软删除字段/注解/Specification 缓存
│   └── SoftDeleteBulkExecutor — 通过原生 SQL / CriteriaUpdate 批量删除
│
├── annotation/        # 自定义注解
│   ├── @SoftDelete       — 标记字段为软删除标志
│   ├── @IgnoreSoftDelete — 禁用自动软删除过滤
│   ├── @Encrypt          — AES/GCM 透明加密
│   ├── @Mask             — JSON 输出脱敏
│   ├── MaskType           — 脱敏类型枚举（PHONE/EMAIL/ID_CARD/NAME 等）
│   └── @RetryOnOptimisticLock — 指数退避重试的 AOP 注解
│
├── converter/         # 类型转换器
│   ├── @CodeEnum / @CodeEnumValue — 基于编码的枚举注解
│   ├── CodeEnumType      — 基于编码的枚举的 Hibernate UserType
│   ├── CodeEnumHelper    — 枚举编码解析辅助
│   ├── EncryptConverter  — AES/GCM AttributeConverter
│   ├── EncryptionKeyManager — PBKDF2 密钥派生，多版本轮换
│   └── MaskSerializer    — @Mask 的 Jackson 序列化器
│
├── monitor/           # SQL 监控
│   ├── SlowQueryDataSourceProxy — 基于 JDBC 代理的慢查询检测
│   ├── SlowQueryDataSourceProxyPostProcessor — DataSource 后处理代理
│   ├── QueryMetricsCollector — 单例指标（计数、平均、最大）
│   ├── SqlSanitizer     — 从 SQL 日志中移除敏感数据
│   └── SlowQueryListener — 慢查询事件监听接口
│
├── autoconfigure/     # Spring Boot 自动配置
│   ├── MyJpaPlusAutoConfiguration — 主 @AutoConfiguration
│   ├── MyJpaPlusGlobalConfig — 全局配置 Bean
│   ├── MyJpaPlusProperties — @ConfigurationProperties
│   ├── GlobalConfigHolder — 带 volatile 字段的集中配置
│   ├── EnvironmentHelper — 环境检测工具
│   └── SoftDeleteFilterBean — 软删除 JPA Specification 注入
│
├── exception/         # 自定义异常
│   ├── MyJpaPlusException — 带 ErrorCode + 上下文清理的基础异常
│   ├── QueryBuildException / BulkOperationException / MyJpaDataAccessException
│   └── SecurityViolationException
│
    └── util/             # 共享工具类
    ├── LambdaUtils        — SerializedLambda → 属性名提取
    ├── IdentifierValidator — SQL 标识符验证 + Unicode 同形字符检测
    ├── InClauseBuilder    — IN/NOT IN 子句自动分批
    ├── SampledEvictionCache — 基于 Caffeine 的有界缓存工具类
    ├── CacheEvictionHelper — L1 缓存选择性驱逐
    ├── PageableHelper     — 分页参数辅助工具
    └── EntityClassResolver / StringHelper / EntityGraphHelper
```

## 数据流：Lambda 查询执行

```
用户代码                    库实现                          JPA 提供者
─────────                   ───────                        ────────────
repository.findAll(s ->     QuerySpec.of(consumer)
  s.eq(User::getName,       │
    "John")                 │
                            │ LambdaUtils.resolveProperty(User::getName)
                            │ → SerializedLambda → "name"
                            │
                            │ QuerySpec.eq("name", "John")
                            │ → 追加 SimpleNode 到条件列表
                            │
                            │ QuerySpec.toPredicate(root, cb, query)
                            │ → NodeResolver.resolve(conditionTree)
                            │ → 递归遍历 ConditionNode 树
                            │ → SimpleNode.resolve() → cb.equal(root.get("name"), "John")
                            │
                            │ 返回 JPA Predicate
                            │
                            │ JPA CriteriaQuery.where(predicate)
                            └──────────────────────────────────→ SELECT * FROM users WHERE name = 'John'
```

## 数据流：单行 UPSERT 执行

```
MergeSpec.withEntity(user)
  .onConflict(User::getEmail)
  .updateOnConflict(User::getName)
  .execute(em)
    │
    ├── DialectDetector.detectDialect(em)
    │   ├── 优先级 1: JDBC URL 属性
    │   ├── 优先级 2: Connection.getMetaData().getDatabaseProductName()
    │   ├── 优先级 3: Hibernate Session 反射
    │   └── 优先级 4: 系统属性 myjpa-plus.dialect
    │
    ├── DialectStrategy.buildUpsertSql(...)
    │   ├── MysqlDialect:     INSERT INTO t (...) VALUES (...) ON DUPLICATE KEY UPDATE col = VALUES(col)
    │   ├── PostgresDialect:  INSERT INTO t (...) VALUES (...) ON CONFLICT (...) DO UPDATE SET col = EXCLUDED.col
    │   ├── OracleDialect:    MERGE INTO t USING (SELECT ? AS col FROM DUAL) ON (...) WHEN NOT MATCHED ...
    │   └── SqlServerDialect: MERGE INTO t USING (VALUES (?)) AS new_row(col) ON (...) WHEN NOT MATCHED ...
    │
    └── em.createNativeQuery(sql).executeUpdate()
```

## 数据流：多行批量 UPSERT

当 `MergeSpec.executeBatch()` 通过 `supportsBatchUpsert()` 检测到方言支持批量模式时：

```
MergeSpec.executeBatch([user1, user2, user3], em)
  │
  ├── DialectStrategy.supportsBatchUpsert()? → true
  │   ├── MysqlDialect:     true
  │   └── PostgresDialect:  true
  │
  ├── EntityFieldExtractor.extractFieldValues() 每个实体
  │   → [user1: (name→"A", email→"a@x"), user2: (name→"B", email→"b@x"), ...]
  │
  ├── DialectStrategy.buildBatchUpsertSql(...)
  │   ├── MysqlDialect:
  │   │     INSERT INTO t (name, email) VALUES (?, ?), (?, ?), (?, ?)
  │   │     ON DUPLICATE KEY UPDATE name = VALUES(name)
  │   ├── PostgresDialect:
  │   │     INSERT INTO t (name, email) AS _new VALUES (?, ?), (?, ?), (?, ?)
  │   │     ON CONFLICT (email) DO UPDATE SET name = _new.name
  │   └── （不支持批量模式的方言回退到逐行循环）
  │
  └── em.createNativeQuery(sql).executeUpdate()
     → N 行仅 1 次网络往返（逐行模式需 N 次）
```

## 安全防御层

库实现了纵深防御，包含多个安全层：

### 第 1 层：输入验证（信任边界）

```
用户 Lambda 引用
  → LambdaUtils.resolveProperty()
    → IdentifierValidator.validateColumnName()
      → 正则: ^[a-zA-Z_][a-zA-Z0-9_]*$（ASCII）或 Unicode 模式
      → Unicode 同形字符检测（西里尔字母、希腊字母、亚美尼亚字母、全角字符）
      → 最大长度: 128 字符
```

### 第 2 层：函数白名单（代码注入防御）

```
QuerySpec.func(field, "functionName", params...)
  → 三层验证:
    1. SAFE_FIELD_NAME_PATTERN 正则
    2. SAFE_FUNCTION_NAMES 集合（CONCAT、COALESCE、IFNULL 等）
    3. BOOLEAN_FUNCTION_NAMES 集合（IS_NULL、IS_EMPTY 等）
  → WHITELIST_ENFORCED = true（硬编码，不可禁用）
```

### 第 3 层：SQL 注入检测（CTE 模块）

```
CteSpec.as(sql)
  → CTE 名称的保留字验证
  → 危险关键词检测（DROP、TRUNCATE、GRANT 等），带词边界匹配
  → SQL 注入模式检测（注释、分号、WAITFOR DELAY）
  → 未绑定参数检测
  → strictMode = true（硬编码，不可禁用）
  → 注：UNION SELECT / UNION ALL SELECT 在递归和非递归 CTE 中均为合法语法，不作为注入特征检测
```

### 第 4 层：LIKE 通配符转义

```
QuerySpec.like(field, "pattern%_")
  → PredicateHelper.escapeLikeWildcards()
    → 转义顺序: \ → \\, 然后 _ → \_, 然后 % → \%
    → LIKE 转义字符: \
```

### 第 5 层：数据清理（输出）

```
MyJpaPlusException.toString()
  → 正则检测: password=, token=, key=, secret=, credential=, ssn, credit_card
  → 替换为 ***（词边界断言防止 "primaryKey" 误报）
  → 截断至 200 字符

SqlSanitizer.sanitize(sql)
  → 移除: 字符串字面量、数字字面量、注释、美元引号字符串
  → 保留: LIMIT/OFFSET 数字（用于调试）
  → 在保护过程中使用 Unicode 私有使用区哨兵
```

### 第 6 层：安全守卫（运行时限制）

```
查询最大结果数:        10,000 行（可配置）
深度分页限制:         1,000,000 offset（可配置）
批量操作行数:         10,000 行（可配置）
IN 子句硬限制:        5,000 参数（可配置）
无条件操作:          必须显式传入 allowUnconditional(true)
乐观锁重试:           最大 20 次，最大 60 秒总超时
递归深度:             NodeResolver=50, CacheKeyBuilder=128
```

### 第 7 层：加密安全（加密）

```
@Encrypt 字段
  → 算法: AES/GCM/NoPadding（认证加密，128 位标签）
  → 密钥派生: PBKDF2WithHmacSHA256（600,000 次迭代）
  → 多版本密钥轮换: v1:key1,v2:key2 格式
  → Salt 验证: 生产环境未配置 salt 时阻止运行
  → 明文清理: 加密后 Arrays.fill(plaintextBytes, 0)
  → 密钥材料清理: PBKDF2 后 Arrays.fill(keyChars, '\0')
```

## 线程安全性汇总

| 组件 | 线程安全 | 机制 |
|------|---------|------|
| QuerySpec | 否 | 声明为可变构建器 |
| SubQuerySpec | 否 | 声明为可变构建器 |
| ProjectionSpec | 已移除 | 功能迁移至 `QueryProjectionSupport` |
| EntityGraphHelper | 否 | 声明为可变构建器 |
| FunctionWhitelist | 是 | ConcurrentHashMap + AtomicReference 冻结快照 |
| SoftDeleteContext | 是 | ThreadLocal<Integer> 计数器 |
| EntityManagerHelper | 是 | ConcurrentHashMap + volatile 字段 |
| SoftDeleteBulkExecutor | 是 | 无状态（纯静态方法，无共享可变状态） |
| QueryCacheManager | 是 | Caffeine 内置 segment 锁 |
| DialectDetector | 是 | Caffeine 缓存 |
| EncryptConverter | 是 | ConcurrentLinkedDeque Cipher 对象池（有界，64 容量），borrow/return 原子操作，异常路径 catch 块归还 |
| InClauseBuilder | 是 | AtomicReference<Config> |
| LambdaUtils | 是 | Caffeine 缓存（SampledEvictionCache 后端） |
| IdentifierValidator | 是 | Volatile 标志（仅配置时写入） |
| SampledEvictionCache | 是 | Caffeine 内置并发安全 |
