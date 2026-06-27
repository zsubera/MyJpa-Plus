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
│   ├── QueryAggregates  — count/sum/avg/max/min 表达式工厂
│   └── QueryHavingSupport / QueryConditionSupport / QueryCompositionSupport
│       / QuerySubQuerySupport / QueryJoinSupport / QueryAggregateSupport
│       / QueryOrderBySupport — QuerySpec 拆分辅助类
│
├── update/            # 批量操作
│   ├── UpdateSpec       — 批量 UPDATE 构建器
│   ├── DeleteSpec       — 批量 DELETE 构建器
│   ├── MergeSpec        — UPSERT/MERGE 构建器（4 种方言 + 多行批处理）
│   ├── DialectDetector  — 从 EntityManager 自动检测数据库方言
│   ├── DialectStrategy  — 方言特定 UPSERT SQL 生成 SPI
│   ├── AbstractDialectStrategy — 共享转义逻辑
│   ├── MysqlDialect / PostgresDialect / OracleDialect / SqlServerDialect
│   └── EntityFieldExtractor — 反射实体字段 → 列名/值对
│
├── template/          # 查询执行与缓存
│   ├── MyJpaTemplate    — 主查询/批量模板（Spring Bean）
│   ├── QueryCacheManager — 基于 ConcurrentHashMap 的 TTL 缓存（实现 CacheAdapter）
│   ├── CacheAdapter     — 可插拔缓存后端 SPI
│   ├── BulkOperationTemplate — 带事务管理的批量执行
│   └── BatchSaveTemplate — 带 flush/clear 循环的批量持久化/合并
│
├── projection/        # DTO 与 Tuple 投影
│   └── ProjectionSpec   — 字段选择、聚合、JOIN、DTO 映射
│
├── repository/        # 扩展 Spring Data Repository
│   ├── MyJpaRepository   — 带 Lambda DSL 的 Repository 接口
│   ├── DefaultMyJpaRepository — 带软删除的基础实现
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
│   └── @RetryOnOptimisticLock — 指数退避重试的 AOP 注解
│
├── converter/         # 类型转换器
│   ├── EncryptConverter  — AES/GCM AttributeConverter
│   ├── EncryptionKeyManager — PBKDF2 密钥派生，多版本轮换
│   ├── CodeEnumType      — 基于编码的枚举的 Hibernate UserType
│   └── MaskSerializer    — @Mask 的 Jackson 序列化器
│
├── monitor/           # SQL 监控
│   ├── SlowQueryDataSourceProxy — 基于 JDBC 代理的慢查询检测
│   ├── QueryMetricsCollector — 单例指标（计数、平均、最大）
│   └── SqlSanitizer     — 从 SQL 日志中移除敏感数据
│
├── autoconfigure/     # Spring Boot 自动配置
│   ├── MyJpaPlusAutoConfiguration — 主 @AutoConfiguration
│   ├── MyJpaPlusProperties — @ConfigurationProperties
│   └── GlobalConfigHolder — 带 volatile 字段的集中配置
│
├── codegen/           # 代码生成
│   └── EntityCodeGenerator — 从表元数据生成 JPA 实体源码
│
├── exception/         # 自定义异常
│   ├── MyJpaPlusException — 带 ErrorCode + 上下文清理的基础异常
│   ├── QueryBuildException / BulkOperationException / DataAccessException
│   └── SecurityViolationException / TimeoutException
│
    └── util/             # 共享工具类
    ├── LambdaUtils        — SerializedLambda → 属性名提取
    ├── IdentifierValidator — SQL 标识符验证 + Unicode 同形字符检测
    ├── InClauseBuilder    — IN/NOT IN 子句自动分批
    ├── SampledEvictionCache — 基于 ConcurrentHashMap 的采样驱逐缓存
    └── EntityClassResolver / StringHelper / EntityGraphHelper / AuditUtils
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
  → SQL 注入模式检测（注释、分号、UNION SELECT、WAITFOR DELAY）
  → 未绑定参数检测
  → strictMode = true（硬编码，不可禁用）
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
| ProjectionSpec | 否 | 声明为可变构建器 |
| EntityGraphHelper | 否 | 声明为可变构建器 |
| FunctionWhitelist | 是 | ConcurrentHashMap + AtomicReference 冻结快照 |
| SoftDeleteContext | 是 | ThreadLocal<Integer> 计数器 |
| EntityManagerHelper | 是 | ConcurrentHashMap + volatile 字段 |
| SoftDeleteBulkExecutor | 是 | 无状态（纯静态方法，无共享可变状态） |
| QueryCacheManager | 是 | ConcurrentHashMap + ReentrantLock.tryLock() |
| DialectDetector | 是 | ConcurrentHashMap 缓存 |
| EncryptConverter | 是 | 每次操作新建 Cipher（JDK-8201324 规避） |
| InClauseBuilder | 是 | AtomicReference<Config> |
| LambdaUtils | 是 | SampledEvictionCache（基于 ConcurrentHashMap） |
| IdentifierValidator | 是 | Volatile 标志（仅配置时写入） |
| SampledEvictionCache | 是 | ConcurrentHashMap + tryLock 驱逐 |
