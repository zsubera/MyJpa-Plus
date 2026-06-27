# myjpa-plus 全量代码审计报告

**日期**: 2026-06-28
**版本**: 1.3.0
**范围**: src/main/java 全部 ~90 个源文件，12 个包

---

## 汇总

| 严重级别 | 数量 | 说明 |
|---------|------|------|
| **P0** | 8 | 必修 — 数据损坏、并发安全、静默错误 |
| **P1** | 18 | 重要 — 逻辑缺陷、竞态条件、设计问题 |
| **P2** | 25+ | 建议 — 性能、代码质量、边缘场景 |

**最高风险发现**: MergeSpec.executeWithCallbacks 对新实体执行 double-persistence（INSERT+UPDATE），导致触发器/审计表双重写入，影响行数返回值不正确。

**整体评价**: 代码质量较高，类型安全机制（SFunction + SerializedLambda）设计合理，sealed interface 层次清晰。主要问题集中在并发安全（QueryCacheManager 前缀索引竞态、DialectDetector 缓存驱逐竞态）和边界条件处理（NULL 语义、配置值 0 的歧义）。

---

## P0 — 必修问题

### 1. PredicateHelper: LIKE 传入 null 返回 TRUE 而非 FALSE

**位置**: `src/main/java/com/zsubera/jpa/spec/PredicateHelper.java:212-217, 245-250, 281-286, 300-305, 319-324`

**现状**:
```java
public static Predicate like(Path<?> path, String fieldName, String value, CriteriaBuilder cb) {
    if (value == null) {
        return cb.conjunction();  // 返回 TRUE
    }
    ...
}
```

**问题**: SQL 语义中 `NULL LIKE '%'` 返回 NULL（不匹配任何行）。返回 `cb.conjunction()`（恒 TRUE）意味着 NULL LIKE 值会错误匹配所有行。`notLike` 返回 `cb.disjunction()`（恒 FALSE）也是错误的。`startsWith`、`endsWith`、`contains` 存在同样问题。

**影响**: 作为 public API，任何直接调用 `PredicateHelper.like(path, name, null, cb)` 的外部代码都会得到静默错误的结果。

**修复**:
```java
public static Predicate like(Path<?> path, String fieldName, String value, CriteriaBuilder cb) {
    if (value == null) {
        return cb.disjunction();  // NULL LIKE 不匹配任何行
    }
    ...
}

public static Predicate notLike(Path<?> path, String fieldName, String value, CriteriaBuilder cb) {
    if (value == null) {
        return cb.disjunction();  // NULL NOT LIKE 也不匹配
    }
    ...
}

public static Predicate startsWith(Path<?> path, String fieldName, String value, CriteriaBuilder cb) {
    if (value == null) {
        return cb.disjunction();
    }
    ...
}

public static Predicate endsWith(Path<?> path, String fieldName, String value, CriteriaBuilder cb) {
    if (value == null) {
        return cb.disjunction();
    }
    ...
}

public static Predicate contains(Path<?> path, String fieldName, String value, CriteriaBuilder cb) {
    if (value == null) {
        return cb.disjunction();
    }
    ...
}
```

---

### 2. NodeResolver: count 上下文中使用临时 CriteriaQuery 创建子查询

**位置**: `src/main/java/com/zsubera/jpa/spec/NodeResolver.java:345-352, 379-387`

**现状**:
```java
private static <S> Predicate resolveExists(...) {
    CriteriaQuery<?> query = ctx.query();
    if (query == null) {
        CriteriaQuery<S> tempQuery = ctx.cb().createQuery(node.subEntity);
        return resolveExistsInternal(node, ctx.rootPath(), tempQuery, ctx.cb());
    }
    ...
}
```

**问题**: 当主 CriteriaQuery 为 null（count 查询上下文）时，创建临时 CriteriaQuery。在 `resolveExistsInternal` 内部调用 `query.subquery()`，但子查询属于临时查询而非主查询。Hibernate 等 JPA provider 要求子查询属于同一个 CriteriaQuery，否则抛出 `IllegalStateException`。`resolveInSubQuery`（line 379-387）存在同样问题。

**影响**: 包含 EXISTS/IN 子查询的 count 查询在运行时失败。

**修复**: 在 count 上下文中不支持 EXISTS/IN 子查询，应抛出明确的 `UnsupportedOperationException`，或在 NodeResolver 中检测 count 上下文并绕过子查询：
```java
private static <S> Predicate resolveExists(...) {
    CriteriaQuery<?> query = ctx.query();
    if (query == null) {
        throw new QueryBuildException(
            "EXISTS subquery is not supported in count query context. " +
            "Use a separate count query without subqueries.");
    }
    return resolveExistsInternal(node, ctx.rootPath(), query, ctx.cb());
}
```

---

### 3. MergeSpec.executeWithCallbacks: 新实体双重持久化

**位置**: `src/main/java/com/zsubera/jpa/update/MergeSpec.java:235-252`

**现状**:
```java
public int executeWithCallbacks(EntityManager em) {
    T entitySnapshot = this.entity;
    if (!em.contains(entitySnapshot)) {
        entitySnapshot = em.merge(entitySnapshot);  // INSERT
    }
    em.flush();  // 执行 INSERT
    return executeWith(em, entitySnapshot);  // UPSERT 又尝试 INSERT
}
```

**问题**: 对新实体：merge+flush 执行 INSERT → UPSERT 再次尝试 INSERT → 变为 UPDATE。INSERT + UPDATE 而非一个 UPSERT。触发器/审计表看到双重写入。对已存在的实体：双 UPDATE。

**影响**: 行数返回值不正确（affected=1 应为 0），触发器副作用。

**修复**:
```java
public int executeWithCallbacks(EntityManager em) {
    T entitySnapshot = this.entity;
    if (!em.contains(entitySnapshot)) {
        entitySnapshot = em.merge(entitySnapshot);
    }
    em.flush();
    // After flush, entity is managed and persisted. Skip native UPSERT
    // since merge+flush already handled the persistence.
    return 0;  // Already handled by JPA lifecycle
}
```

---

### 4. UpdateSpec.evictEntityCache: 吞掉所有异常

**位置**: `src/main/java/com/zsubera/jpa/update/UpdateSpec.java:569-582`

**现状**:
```java
try {
    // ... Hibernate reflection chain ...
    return;
} catch (Exception ignored) {
    em.clear();  // 回退到清除所有 L1 缓存
}
```

**问题**: `catch (Exception ignored)` 捕获包括 NPE、ClassCastException、SecurityException 在内的所有异常。反射链中的实际 bug 被静默吞掉，回退到 `em.clear()`（清除所有实体类型的 L1 缓存），导致同事务内其他实体的缓存被意外清除。

**影响**: 静默的 L1 缓存损坏，同事务内其他实体读取到过期数据。

**修复**:
```java
try {
    Class<?> sessionClass = Class.forName("org.hibernate.Session");
    if (sessionClass.isInstance(em.getDelegate())) {
        Object session = em.unwrap(sessionClass);
        Object factory = session.getClass().getMethod("getSessionFactory").invoke(session);
        Object cache = factory.getClass().getMethod("getCache").invoke(factory);
        cache.getClass().getMethod("evictEntityData", Class.class).invoke(cache, entityClass);
        return;
    }
} catch (ClassNotFoundException | NoSuchMethodException e) {
    // Hibernate not available — fall back to em.clear()
} catch (Exception e) {
    log.warn("Failed to evict entity cache selectively for {}, falling back to em.clear()", 
             entityClass.getSimpleName(), e);
}
em.clear();
```

---

### 5. QueryCacheManager.removeFromPrefixIndex: 竞态丢失索引条目

**位置**: `src/main/java/com/zsubera/jpa/template/QueryCacheManager.java:175-184`

**现状**:
```java
private void removeFromPrefixIndex(String key) {
    String prefix = extractPrefix(key);
    java.util.Set<String> keys = prefixIndex.get(prefix);
    if (keys != null) {
        keys.remove(key);
        if (keys.isEmpty()) {
            prefixIndex.remove(prefix, keys);  // line 181: 条件 remove
        }
    }
}
```

**问题**: `keys.isEmpty()`（line 180）和 `prefixIndex.remove(prefix, keys)`（line 181）之间存在竞态。线程 A 看到 set 为空，线程 B 通过 `computeIfAbsent` 向同一 set 添加新 key（返回的仍是未被 remove 的 set），然后线程 A 移除 map 条目——同时移除了线程 B 的 key。此后该前缀的索引永久丢失，直到 key 被重新插入。

**影响**: 受影响前缀的缓存条目永远不会被 `evictByPrefix()` 驱逐。在软删除场景中，已删除实体的查询结果可能永久留在缓存中直到 TTL 过期。

**修复**: 改用 `compute` 原子操作：
```java
private void removeFromPrefixIndex(String key) {
    String prefix = extractPrefix(key);
    prefixIndex.computeIfPresent(prefix, (k, keys) -> {
        keys.remove(key);
        return keys.isEmpty() ? null : keys;
    });
}
```

---

### 6. QueryCacheManager.evictByPrefix: 迭代中修改 ConcurrentHashMap

**位置**: `src/main/java/com/zsubera/jpa/template/QueryCacheManager.java:709-717`

**现状**:
```java
for (java.util.Map.Entry<String, CachedQueryResult<?>> entry : store.entrySet()) {
    if (entry.getKey().startsWith(normalizedPrefix)) {
        store.remove(entry.getKey());
        insertionTimestamps.remove(entry.getKey());
        removeFromPrefixIndex(entry.getKey());
        count++;
    }
}
```

**问题**: 在 `store.entrySet()` 迭代中调用 `store.remove()`。ConcurrentHashMap 的弱一致性迭代器不会抛出 CME，但可能跳过匹配的条目（在迭代器快照之后出现的条目）。更严重的是，每个 `removeFromPrefixIndex` 调用可能触发 Finding 5 的竞态。

**影响**: 无法在一次调用中完全清除前缀匹配的条目。

**修复**:
```java
public int evictByPrefix(String prefix) {
    String normalizedPrefix = normalizePrefix(prefix);
    List<String> keysToRemove = store.keySet().stream()
        .filter(key -> key.startsWith(normalizedPrefix))
        .collect(Collectors.toList());
    int count = 0;
    for (String key : keysToRemove) {
        if (store.remove(key) != null) {
            insertionTimestamps.remove(key);
            removeFromPrefixIndex(key);
            count++;
        }
    }
    return count;
}
```

---

### 7. EncryptionKeyManager: Salt 缓存无防御性拷贝

**位置**: `src/main/java/com/zsubera/jpa/converter/EncryptionKeyManager.java:282-294`

**现状**:
```java
private static byte[] getSalt() {
    byte[] cached = cachedSalt;
    if (cached != null) {
        return cached;  // 返回可变引用
    }
    byte[] bytes = salt.getBytes(StandardCharsets.UTF_8);
    cachedSalt = bytes;
    return bytes;
}
```

**问题**: `cachedSalt` 持有 `byte[]` 的直接引用，`getSalt()` 将同一可变引用返回给所有调用者。如果调用者修改数组，会静默破坏后续所有密钥派生。`clearCaches()` 设置 `cachedSalt = null` 但不清零旧数组，盐值材料残留在堆中。

**影响**: 并发访问或恶意调用者可静默损坏加密密钥。

**修复**:
```java
private static volatile byte[] cachedSalt;

private static byte[] getSalt() {
    byte[] cached = cachedSalt;
    if (cached != null) {
        return cached.clone();  // 返回防御性拷贝
    }
    synchronized (EncryptionKeyManager.class) {
        cached = cachedSalt;
        if (cached != null) {
            return cached.clone();
        }
        byte[] bytes = salt.getBytes(StandardCharsets.UTF_8);
        cachedSalt = bytes;
        return bytes.clone();
    }
}
```

---

### 8. SlowQueryDataSourceProxy: batchCount++ 在 method.invoke() 之前

**位置**: `src/main/java/com/zsubera/jpa/monitor/SlowQueryDataSourceProxy.java:186-187, 276-317`

**现状**:
```java
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if ("addBatch".equals(name)) {
        batchCount++;       // 先递增
        return method.invoke(target, args);  // 可能抛异常
    }
    if ("clearBatch".equals(name)) {
        batchCount = 0;
        return method.invoke(target, args);
    }
```

**问题**: 如果 `method.invoke()` 在 `addBatch` 上抛出异常，`batchCount` 已经递增但 JDBC addBatch 未完成。后续 `executeBatch()` 报告不正确的 batch 大小。`StatementTimingHandler`（line 276-317）存在同样问题。

**影响**: 慢查询日志中记录不正确的 batch 大小。

**修复**:
```java
if ("addBatch".equals(name)) {
    Object result = method.invoke(target, args);  // 先执行
    batchCount++;                                  // 成功后递增
    return result;
}
```

---

## P1 — 重要问题

### 9. CteSpec.select() 不验证 DDL/DML

**位置**: `src/main/java/com/zsubera/jpa/spec/CteSpec.java:331-338`

**问题**: `as()` 和 `asSafe()` 调用 `validateSelectOnly()` 阻止 CTE 体中的 DDL/DML，但 `select()` 只调用 `checkSqlSafety()`，防御层不一致。

**修复**: 在 `select()` 中也调用 `validateSelectOnly()` 或统一使用同一验证方法。

---

### 10. CacheKeyBuilder: Lambda hashCode 导致跨 JVM 不稳定

**位置**: `src/main/java/com/zsubera/jpa/spec/CacheKeyBuilder.java:144-158`

**问题**: `EXISTS`/`IN` 子查询和 `RawNode` 的缓存 key 使用 lambda 的 `hashCode()`，该值在 JVM 重启后不确定。语义相同的查询在重启后产生不同缓存 key，查询缓存命中率在重启后降为零。

**修复**: 将 lambda `hashCode` 替换为基于 lambda 内容的确定性哈希（如序列化后哈希），或在缓存 key 中包含 lambda 的方法名+类名。

---

### 11. FunctionWhitelist: addSafeFunctionNames 与 freezeExtraFunctionNames 之间的竞态

**位置**: `src/main/java/com/zsubera/jpa/spec/FunctionWhitelist.java:66-76, 111-116`

**问题**: `freezeExtraFunctionNames()` 非原子地快照 safe set 和 boolean set。并发读取者可能观察到新的 safe set 但旧的 boolean set。`reset()` 非原子地清空 CHM 和替换快照。

**修复**: 使用单个 `AtomicReference<Snapshot>` 替代分离的两个快照，或在 freeze 期间加锁。

---

### 12. QuerySpec.copy() 不深拷贝 havingConditions lambda

**位置**: `src/main/java/com/zsubera/jpa/spec/QuerySpec.java:168-169`

**问题**: `havingConditions` 是 lambda 列表，`addAll` 只拷贝引用。lambda 可能捕获原始 spec 的可变状态。

**修复**: 如果文档说明 `copy()` 不应在 `or()/not()` 消费者内调用，应在 having 条件处也加注释说明限制。

---

### 13. BulkOperationTemplate: max-rows 限制在同事务路径中只解析一次

**位置**: `src/main/java/com/zsubera/jpa/template/BulkOperationTemplate.java:289-316`

**问题**: `executeBatchInternal` 中 `effectiveLimit` 只在顶部解析一次，而 `executeBatchInSeparateTransactionsWithResult` 在循环中每次迭代都解析。运行时配置变更在同事务批次中被静默忽略。

**修复**: 统一两个路径的行为，或在文档中说明同事务路径的限制。

---

### 14. DefaultMyJpaRepository: runWithOverride 事务清理覆盖外层覆盖

**位置**: `src/main/java/com/zsubera/jpa/repository/DefaultMyJpaRepository.java:208-229`

**问题**: 嵌套的 `withAutoFilterOverride` 调用中，内层的事务清理回调在外层仍活跃时触发，擦除外层的软删除过滤状态。

**修复**: 事务清理应检查嵌套深度，只在最外层调用时注册清理。

---

### 15. MyJpaTemplate.resolveConfigValue: 配置值 0 被视为"未配置"

**位置**: `src/main/java/com/zsubera/jpa/template/MyJpaTemplate.java:981-993`

**问题**: `if (value != 0)` 将 0 视为"未设置"，用户配置 `myjpa-plus.query.max-results: 0` 会静默回退到默认值。

**修复**: 使用 `Integer` 而非 `int`，用 `null` 表示"未配置"：
```java
private static int resolveConfigValue(
    java.util.function.Function<MyJpaPlusGlobalConfig, Integer> getter,
    int localFallback) {
    MyJpaPlusGlobalConfig config = GlobalConfigHolder.getConfig();
    if (config != null) {
        Integer value = getter.apply(config);
        if (value != null) {
            return value;
        }
    }
    return localFallback;
}
```

---

### 16. QueryCacheManager.get(): clear() 后可能返回过期数据

**位置**: `src/main/java/com/zsubera/jpa/template/QueryCacheManager.java:248-279`

**问题**: `get()` 不获取 evictionLock 也不检查 clearGeneration。在 `clear()` 完成前获取到 `store.get(key)` 结果的线程仍会返回过期值。

**修复**: 在 `get()` 中检查 `clearGeneration`（使用读取优化的 AtomicLong 语义）。

---

### 17. EntityManagerHelper.removeResolver: remove 在 synchronized 块外执行

**位置**: `src/main/java/com/zsubera/jpa/repository/EntityManagerHelper.java:145-151`

**问题**: `resolvers.remove()` 在 `synchronized(resolverCheckLock)` 外执行，并发的 `registerEntityManagerFactoryIfAbsent()` 可能交错。

**修复**: 将 `remove` 操作移入 synchronized 块。

---

### 18. AbstractBulkOperationSpec.probeExceedsLimit: stream limit 可能不下推到 SQL

**位置**: `src/main/java/com/zsubera/jpa/update/AbstractBulkOperationSpec.java:528-538`

**问题**: `getResultStream().limit().count()` 是否下推到 SQL LIMIT 取决于 JPA provider 实现。EclipseLink 等可能将所有匹配行加载到内存。

**修复**:
```java
TypedQuery<Integer> q = em.createQuery(probeQuery);
q.setMaxResults(probeLimit);
return q.getResultList().size() >= probeLimit;
```

---

### 19. EntityFieldExtractor: @Version 字段包含在 UPSERT 列中

**位置**: `src/main/java/com/zsubera/jpa/update/EntityFieldExtractor.java:106-123`

**问题**: `@Version` 字段不在排除列表中，其值被包含在 UPSERT INSERT 列表。冲突时 UPSERT 会将版本更新为实体实例中的旧值，静默重置乐观锁计数器。

**修复**: 在字段过滤中添加排除：
```java
&& !f.isAnnotationPresent(jakarta.persistence.Version.class)
```

---

### 20. DialectDetector.cacheDialect: TOCTOU 竞态

**位置**: `src/main/java/com/zsubera/jpa/update/DialectDetector.java:99-107`

**问题**: `size()` 不是原子操作，与后续 `remove()` 之间存在竞态窗口。弱一致性迭代器可能移除同一 entry 导致缓存持续超限。

**修复**: 改用 `computeIfPresent` 或使用有界缓存（如 Caffeine）。

---

### 21. EntityFieldExtractor: ID_COLUMN_CACHE 和 JAVA_FIELD_TO_DB_COLUMN_CACHE 无界

**位置**: `src/main/java/com/zsubera/jpa/update/EntityFieldExtractor.java:52-57`

**问题**: 与 FIELD_CACHE 使用 ConcurrentReferenceHashMap 不同，这两个缓存使用普通 ConcurrentHashMap，条目永不驱逐。

**修复**: 改用 `ConcurrentReferenceHashMap` with `ReferenceType.WEAK` keys。

---

### 22. BulkTransactionHelper: 包装受检异常

**位置**: `src/main/java/com/zsubera/jpa/update/BulkTransactionHelper.java:90-95`

**问题**: 用户 lambda 的受检异常被包装在 `MyJpaPlusException` 中，破坏 catch 类型期望。

**修复**: 只包装未预期的受检异常，让已知 JPA 异常原样传播。

---

### 23. DeleteSpec: Javadoc 声称 em.clear() 但代码使用选择性驱逐

**位置**: `src/main/java/com/zsubera/jpa/update/DeleteSpec.java:189-191 vs 271`

**修复**: 更新 Javadoc 以匹配实际的选择性驱逐行为。

---

### 24. AbstractBulkOperationSpec.where(Spec): 临时 CriteriaQuery 可能破坏复杂 Specification

**位置**: `src/main/java/com/zsubera/jpa/update/AbstractBulkOperationSpec.java:316-326`

**问题**: 临时 CriteriaQuery 被丢弃，Specification 向其添加的子查询/连接静默丢失。

**修复**: 在文档中明确说明此限制。

---

### 25. EncryptionKeyManager.clearCaches: 不清零 salt 就置 null

**位置**: `src/main/java/com/zsubera/jpa/converter/EncryptionKeyManager.java:355-366`

**修复**:
```java
cachedSalt = null;  // 改为:
if (cachedSalt != null) {
    java.util.Arrays.fill(cachedSalt, (byte) 0);
    cachedSalt = null;
}
```

---

### 26. EncryptionKeyManager.refreshKeyVersion: 与 clearCaches 完全重复

**位置**: `src/main/java/com/zsubera/jpa/converter/EncryptionKeyManager.java:368-380`

**修复**: `refreshKeyVersion()` 应委托给 `clearCaches()`。

---

### 27. GlobalConfigHolder.resolveConfigValue: != 0 作为"已配置"标志

**位置**: `src/main/java/com/zsubera/jpa/autoconfigure/GlobalConfigHolder.java:141-151`

**修复**: 同 Finding 15，使用 `Integer` 表示可选值。

---

### 28. SoftDeleteBulkExecutor.softDeleteByIdsUsingEntityManager: 缺少 evictEntityCache

**位置**: `src/main/java/com/zsubera/jpa/softdelete/SoftDeleteBulkExecutor.java:307-311`

**修复**: 添加 `UpdateSpec.evictEntityCache(em, entityClass);`。

---

### 29. SlowQueryDataSourceProxy.StatementTimingHandler: 同 batchCount 问题

**位置**: `src/main/java/com/zsubera/jpa/monitor/SlowQueryDataSourceProxy.java:276-317`

**修复**: 同 Finding 8。

---

## P2 — 建议改进

| # | 位置 | 问题 | 建议 |
|---|------|------|------|
| 30 | CacheKeyBuilder.java:186 | 注释声称 SHA-256 但实际是 FNV-1a | 更新注释 |
| 31 | ConditionBuilder.java:100 | 类加载时不必要的中间 HashSet | 直接用 Set.of() 构建 |
| 32 | CteSpec.java:516 | 每次 streaming query 创建 Proxy | 缓存 Proxy 或改用原生 API |
| 33 | SubQuerySpec.java:597 | 缺少 @Nullable 注解 | 添加 @Nullable |
| 34 | NodeResolver.java:267 | INNER JOIN ON 子句放置 soft-delete 谓词 | 文档说明设计意图 |
| 35 | OrGroup.java:28 | internalJoin 方法疑似未完成特性 | 文档说明或移除 |
| 36 | QueryCompositionSupport.java:152 | merge 时 timeout/lockMode 静默丢失 | 文档说明或取严格值 |
| 37 | CteSpec.java:393 | getSingleResult 加载完整结果列表 | 注入 LIMIT 1 或用 getSingleResult() |
| 38 | QueryCacheManager:340 | 每次 put() 调用 ConcurrentHashMap.size() | 用 AtomicLong 计数器替代 |
| 39 | KeysetPaginationHelper:48 | MySQL ASC + nullable 列排序不正确 | 文档说明限制 |
| 40 | IgnoreSoftDeleteAdvisor:70 | 驱逐按 CHM 结构偏差移除条目 | 改用简单 ConcurrentHashMap 不驱逐 |
| 41 | BulkOperationTemplate:59 | 未使用的 txManager 参数 | 移除或使用 |
| 42 | MyJpaTemplate:575 | shouldApplySoftDeleteFilter 不检查 AUTO_FILTER_OVERRIDE | 添加检查 |
| 43 | SoftDeleteContext:178 | push/pop 不匹配时静默恢复 | 文档说明或抛异常 |
| 44 | CacheInvalidationListener:62 | recentEvictions 永不清理 | 添加 TTL 清理 |
| 45 | OptimisticLockRetryAdvisor:131 | 无 txManager 时重试无效 | 文档说明限制 |
| 46 | SampledEvictionCache:122 | 驱逐按 hash 顺序而非访问顺序 | 更新文档 |
| 47 | EntityFieldExtractor:226 | `$$` 代理检测启发式不精确 | 检查已知代理模式 |
| 48 | PostgresDialect:66 | 批量 INSERT 逻辑与 MysqlDialect 重复 | 提取公共方法 |
| 49 | MergeSpec:373 vs 392 | 空列表行为不一致（返回 0 vs 抛异常） | 统一为抛异常 |
| 50 | MaskSerializer:79 | 3-6 字符手机号掩码规则不一致 | 统一掩码策略 |
| 51 | CodeEnumType:188 | char/Character 回退到 setString | 用 setString 处理 |
| 52 | SoftDeleteHelper + SoftDeleteBulkExecutor | hasVersionField 方法重复 | 提取到 SoftDeleteHelper |
| 53 | SqlSanitizer:61 | COMMENT_PATTERN 缺少反回溯保护 | 已有长度限制，可接受 |
| 54 | EntityCodeGenerator:203 | 链式 String.replace 对用户输入脆弱 | 文档说明输入验证限制 |

---

## 修复优先级建议

**第一批（P0，立即修复）**:
1. PredicateHelper NULL LIKE 语义 — 1行修复
2. QueryCacheManager removeFromPrefixIndex 竞态 — 改用 computeIfPresent
3. QueryCacheManager evictByPrefix 迭代修复 — 改为先收集再删除
4. EncryptionKeyManager salt 防御性拷贝 — 3行修复
5. SlowQueryDataSourceProxy batchCount 顺序 — 交换顺序
6. UpdateSpec.evictEntityCache 异常处理 — 缩小 catch 范围
7. MergeSpec.executeWithCallbacks double-persistence — 逻辑修复
8. NodeResolver count 上下文子查询 — 抛异常或跳过

**第二批（P1，计划修复）**:
- QueryCacheManager 并发安全
- GlobalConfigHolder/MyJpaTemplate 的 0 值配置
- EntityFieldExtractor @Version 排除
- 加密模块清零
- BulkTransactionHelper 异常包装

**第三批（P2，持续改进）**:
- 文档更新
- 代码重复消除
- 性能微优化
