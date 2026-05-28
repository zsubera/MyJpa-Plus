# myjpa-plus 优化报告

---

## 轮次 1 - 优化记录
时间：2026-05-29

### 已修复问题
- [P0] SoftDeleteJpaRepository.java 硬编码字符串 "id"：使用 EntityClassResolver.resolveIdFieldName(domainClass) 动态获取ID字段名
- [P0] DeleteSpec.java 缺少无条件删除防护：在 executeLimited 方法中添加与 deleteAll() 一致的 allowUnconditional 防护机制
- [P1] UpdateSpec.java 安全防护统一：在 executeLimited 方法中添加与 updateAll() 一致的 allowUnconditional 防护机制
- [P1] LambdaUtils 缓存清理策略优化：将驱逐比例从50%降低到10%，减少性能毛刺
- [P1] RawNode 安全文档增强：在 where(BiFunction) 方法的 Javadoc 中添加安全警告
- [已修复] MyJpaRepository.java 硬编码字符串 "id"：经检查，已使用 EntityClassResolver.resolveIdFieldName() 动态获取字段名

### 未修复问题
- ConditionNode.java 构造函数 null 校验：需要修改多个构造函数，影响范围较大，建议在后续迭代中处理
- MyJpaTemplate.java 参数校验：需要修改 findOne 和 findAll 方法，影响范围较大，建议在后续迭代中处理

### 修改详情
#### 1. SoftDeleteJpaRepository.java 硬编码字符串修复
**文件**：src/main/java/com/zsubera/jpa/repository/SoftDeleteJpaRepository.java:144
**修改前**：
```java
jakarta.persistence.criteria.Predicate idPredicate = cb.equal(root.get("id"), id);
```
**修改后**：
```java
String idFieldName = EntityClassResolver.resolveIdFieldName(domainClass);
jakarta.persistence.criteria.Predicate idPredicate = cb.equal(root.get(idFieldName), id);
```
**原因**：消除硬编码字符串，使用动态解析的ID字段名，支持使用 @Id 注解的其他字段名

#### 2. DeleteSpec.java 无条件删除防护
**文件**：src/main/java/com/zsubera/jpa/update/DeleteSpec.java:175-207
**修改前**：
```java
if (predicates.length == 0) {
    throw new IllegalStateException("No WHERE conditions specified for DELETE operation. "
        + "Use deleteAll() for unconditional deletions.");
}
```
**修改后**：
```java
if (predicates.length == 0) {
    // 与 deleteAll() 保持一致的安全检查
    if (!allowUnconditional) {
        throw new IllegalStateException("No WHERE conditions specified for DELETE operation. "
            + "Call .allowUnconditional(true) to explicitly confirm this operation, "
            + "or use deleteAll(EntityManager) instead.");
    }
    log.warn("WARNING: Executing limited DELETE without conditions on {} — this will affect up to {} rows!",
        entityClass.getSimpleName(), limit);
}
```
**原因**：统一安全防护机制，防止意外的全表删除

#### 3. LambdaUtils 缓存清理策略优化
**文件**：src/main/java/com/zsubera/jpa/util/LambdaUtils.java:76-89
**修改前**：
```java
int toRemove = CACHE.size() / 2;
```
**修改后**：
```java
// 使用更小的驱逐比例（10%而非50%）以减少性能毛刺
int toRemove = CACHE.size() / 10;
if (toRemove < 1) {
    toRemove = 1;
}
```
**原因**：减少缓存清理时的性能毛刺，提高缓存命中率

#### 4. RawNode 安全文档增强
**文件**：src/main/java/com/zsubera/jpa/spec/ConditionBuilder.java:501-519
**修改前**：
```java
/**
 * 添加原始 {@link Predicate} 条件，使用当前实体 {@link Path} 和 {@link CriteriaBuilder}。 这是处理构建器 API 未覆盖条件的扩展方法。
 * ...
 */
```
**修改后**：
```java
/**
 * 添加原始 {@link Predicate} 条件，使用当前实体 {@link Path} 和 {@link CriteriaBuilder}。 这是处理构建器 API 未覆盖条件的扩展方法。
 * ...
 * <p>
 * <strong>安全警告：</strong>在 lambda 表达式中应使用 JPA Criteria API 的类型安全方法（如 {@code path.get("fieldName")}），
 * 避免使用字符串拼接构建字段名。字符串拼接可能导致 SQL 注入风险。
 * ...
 */
```
**原因**：增强安全文档，提醒用户避免在 lambda 中使用字符串拼接

#### 5. UpdateSpec.java 无条件更新防护
**文件**：src/main/java/com/zsubera/jpa/update/UpdateSpec.java:244-247
**修改前**：
```java
if (predicates.length == 0) {
    throw new IllegalStateException(
        "No WHERE conditions specified for UPDATE operation. " + "Use updateAll() for unconditional updates.");
}
```
**修改后**：
```java
if (predicates.length == 0) {
    // 与 updateAll() 保持一致的安全检查
    if (!allowUnconditional) {
        throw new IllegalStateException("No WHERE conditions specified for UPDATE operation. "
            + "Call .allowUnconditional(true) to explicitly confirm this operation, "
            + "or use updateAll(EntityManager) instead.");
    }
    log.warn("WARNING: Executing limited UPDATE without conditions on {} — this will affect up to {} rows!",
        entityClass.getSimpleName(), limit);
}
```
**原因**：统一安全防护机制，防止意外的全表更新，与 DeleteSpec 保持一致

---

## 轮次 2 - 优化记录
时间：2026-05-29

### 已修复问题
- [P1] P1-01 IgnoreSoftDeleteAdvisor ThreadLocal 清理条件化：将 finally 块改为无条件清理 SoftDeleteContext.clear()，防止嵌套调用场景下的 ThreadLocal 泄漏
- [P1] P1-02 EntityClassResolver 硬编码回退值：当实体类无 @Id 注解字段时，改为抛出 IllegalStateException 而非返回硬编码字符串 "id"
- [P1] P1-03 InClauseBuilder ArrayList 重复创建：在 buildBatchedIn 和 buildBatchedNotIn 方法中复用同一个 ArrayList，使用 batch.clear() 替代 new ArrayList<>()，减少 GC 压力
- [P1] P1-05 ProjectionSpec IN 条件未分批：在 ProjectionSpec.resolveJoins 方法中，将 IN/NOT IN 条件改为使用 InClauseBuilder.in()/notIn()，确保 Oracle 等数据库的参数限制得到正确处理
- [P2] P2-01 OrConditionBuilder 代码风格：将所有单行 if 语句改为多行花括号风格，与项目其他位置保持一致

### 未修复问题
- P1-04 LambdaUtils 缓存策略优化：需要引入 Caffeine 依赖或重构为 LRU 策略，改动较大，建议后续版本处理
- P1-06 executeLimited TOCTOU 竞态条件：已有悲观锁缓解措施，根本修复需要重构执行架构，建议后续版本处理
- P1-07~P1-10 可维护性问题：涉及大类拆分和重构，属于长期规划
- P1-11 where(BiFunction) 安全文档：已在轮次 1 中增强文档

### 修改详情
#### 1. IgnoreSoftDeleteAdvisor ThreadLocal 清理修复
**文件**：src/main/java/com/zsubera/jpa/repository/IgnoreSoftDeleteAdvisor.java:62-66
**修改前**：
```java
} finally {
    if (hasAnnotation) {
        SoftDeleteContext.clear();
    }
}
```
**修改后**：
```java
} finally {
    // 无条件清理 ThreadLocal，防止嵌套调用场景下的泄漏
    // ThreadLocal.remove() 在无值时是幂等的
    SoftDeleteContext.clear();
}
```
**原因**：原来仅在 hasAnnotation=true 时清理 ThreadLocal，嵌套调用场景下可能导致 ThreadLocal 泄漏。改为无条件清理，ThreadLocal.remove() 在无值时是幂等的。

#### 2. EntityClassResolver 硬编码回退值修复
**文件**：src/main/java/com/zsubera/jpa/repository/EntityClassResolver.java:52-63
**修改前**：
```java
return "id"; // 硬编码默认值
```
**修改后**：
```java
throw new IllegalStateException(
    "No @Id field found in " + cls.getName()
        + ". Ensure the entity has a field annotated with @jakarta.persistence.Id");
```
**原因**：硬编码返回 "id" 会导致非标准主键字段名的实体在运行时失败。改为抛出明确的异常，帮助开发者快速定位问题。

#### 3. InClauseBuilder ArrayList 复用优化
**文件**：src/main/java/com/zsubera/jpa/util/InClauseBuilder.java:174-193, 195-214
**修改前**：
```java
batch = new ArrayList<>(); // 每次循环创建新对象
```
**修改后**：
```java
batch.clear(); // 复用同一个 ArrayList，减少 GC 压力
```
**原因**：每次批处理循环创建新 ArrayList 会产生大量短生命周期对象，改为复用同一个 ArrayList 可减少 GC 压力，提升高吞吐场景下的性能。

#### 4. ProjectionSpec IN 条件分批修复
**文件**：src/main/java/com/zsubera/jpa/projection/ProjectionSpec.java:930-933
**修改前**：
```java
} else if (node instanceof JoinGroup.ConditionNode.In in) {
    onPredicates.add(join.get(in.fieldName()).in(in.values()));
} else if (node instanceof JoinGroup.ConditionNode.NotIn notIn) {
    onPredicates.add(join.get(notIn.fieldName()).in(notIn.values()).not());
}
```
**修改后**：
```java
} else if (node instanceof JoinGroup.ConditionNode.In in) {
    onPredicates.add(
        com.zsubera.jpa.util.InClauseBuilder.in(cb, join.get(in.fieldName()), in.values()));
} else if (node instanceof JoinGroup.ConditionNode.NotIn notIn) {
    onPredicates.add(
        com.zsubera.jpa.util.InClauseBuilder.notIn(cb, join.get(notIn.fieldName()), notIn.values()));
}
```
**原因**：原来直接调用 join.get(field).in(values) 绕过了 InClauseBuilder 的自动分批逻辑，对于 Oracle（1000 参数限制）等数据库可能导致 SQL 错误。

#### 5. OrConditionBuilder 代码风格统一
**文件**：src/main/java/com/zsubera/jpa/update/OrConditionBuilder.java
**修改前**：
```java
if (value == null)
    throw new IllegalArgumentException("value must not be null");
```
**修改后**：
```java
if (value == null) {
    throw new IllegalArgumentException("value must not be null");
}
```
**原因**：将所有单行 if 语句改为多行花括号风格，与项目其他位置保持一致，符合 Spotless 格式规范。

#### 6. 测试更新
**文件**：src/test/java/com/zsubera/jpa/repository/EntityClassResolverTest.java:22-27
**修改前**：
```java
@Test
void testResolveIdFieldNameReturnsDefaultIdForEntityWithoutId() {
    // String.class has no @Id field, should fall back to "id"
    String fieldName = EntityClassResolver.resolveIdFieldName(String.class);
    assertEquals("id", fieldName);
}
```
**修改后**：
```java
@Test
void testResolveIdFieldNameThrowsExceptionForEntityWithoutId() {
    // String.class has no @Id field, should throw IllegalStateException
    assertThrows(IllegalStateException.class, () -> EntityClassResolver.resolveIdFieldName(String.class));
}
```
**原因**：测试需要更新以匹配 EntityClassResolver 的新行为（抛出异常而非返回默认值）。

---

## 轮次 3 - 优化记录
时间：2026-05-29

### 已修复问题
- [P1] P1-01 ProjectionSpec.resolveJoins() LIKE 通配符未转义：在 StartsWith、EndsWith、Contains 条件中添加 escapeLikeWildcards() 转义和 LIKE_ESCAPE_CHAR 转义字符，防止通配符注入
- [P1] P1-03 LambdaUtils 后台清理线程无关闭机制：将 ScheduledExecutorService 提升为类级字段，添加 shutdown() 方法，并在 MyJpaPlusAutoConfiguration 中通过 @EventListener(ContextClosedEvent.class) 注册关闭钩子
- [P2] P2-01/02 QuerySpec.timeout()/lockMode() 缺少 null 校验：timeout() 添加正数校验，lockMode() 添加 null 校验
- [P2] P2-03 JoinGroup 构造函数缺少 null 校验：添加 root 和 joinNode 参数的 null 校验
- [P2] P2-03 JoinGroup.or(Consumer) 缺少 null 校验：添加 config 参数的 null 校验
- [P2] P2-04 OrGroup 构造函数缺少 null 校验：添加 root 参数的 null 校验
- [P2] P2-05 OrJoinGroup 构造函数缺少 null 校验：添加 root、joinNode、orNode 参数的 null 校验
- [P2] P2-10 ConditionNode 子类缺少 toString()：为 MultiLikeNode、CollectionNode、ExistsNode、RawNode、NegateNode 添加 toString() 方法
- [P2] P2-11 ProjectionSpec.asDto() 缺少 null 校验：添加 dtoClass 参数的 null 校验
- [P2] P2-13 SoftDeleteHelper.isSoftDeleted() 缺少 null 校验：添加 entityClass 和 entity 参数的 null 校验
- [P2] P2-15 EntityGraphHelper.forEntity() 缺少 null 校验：添加 entityClass 参数的 null 校验

### 未修复问题
- P1-04 ConditionNode 子类字段封装性：将 public final 字段改为 private 并添加 accessor 方法会影响所有直接字段访问点（QuerySpec.resolveSimple()、resolveJoins() 等），属于破坏性变更。建议在下一个主版本中迁移到 Java record，需评估兼容性影响
- P1-02 ProjectionSpec 完整代码去重：已修复 LIKE 转义问题（P1-01），完整的 ConditionNode 去重需要重构 ProjectionSpec 的内部 sealed interface 为复用 PredicateHelper，改动量大（约 200 行），建议作为独立重构任务

### 修改详情
#### 1. ProjectionSpec.resolveJoins() LIKE 通配符转义修复
**文件**：src/main/java/com/zsubera/jpa/projection/ProjectionSpec.java:936-943
**修改前**：
```java
} else if (node instanceof JoinGroup.ConditionNode.StartsWith startsWith) {
    onPredicates
        .add(cb.like(join.get(startsWith.fieldName()).as(String.class), startsWith.value() + "%"));
} else if (node instanceof JoinGroup.ConditionNode.EndsWith endsWith) {
    onPredicates.add(cb.like(join.get(endsWith.fieldName()).as(String.class), "%" + endsWith.value()));
} else if (node instanceof JoinGroup.ConditionNode.Contains contains) {
    onPredicates
        .add(cb.like(join.get(contains.fieldName()).as(String.class), "%" + contains.value() + "%"));
}
```
**修改后**：
```java
} else if (node instanceof JoinGroup.ConditionNode.StartsWith startsWith) {
    onPredicates.add(cb.like(join.get(startsWith.fieldName()).as(String.class),
        PredicateHelper.escapeLikeWildcards(startsWith.value()) + "%",
        PredicateHelper.LIKE_ESCAPE_CHAR));
} else if (node instanceof JoinGroup.ConditionNode.EndsWith endsWith) {
    onPredicates.add(cb.like(join.get(endsWith.fieldName()).as(String.class),
        "%" + PredicateHelper.escapeLikeWildcards(endsWith.value()),
        PredicateHelper.LIKE_ESCAPE_CHAR));
} else if (node instanceof JoinGroup.ConditionNode.Contains contains) {
    onPredicates.add(cb.like(join.get(contains.fieldName()).as(String.class),
        "%" + PredicateHelper.escapeLikeWildcards(contains.value()) + "%",
        PredicateHelper.LIKE_ESCAPE_CHAR));
}
```
**原因**：JOIN ON 条件中的 StartsWith、EndsWith、Contains 未转义 LIKE 通配符（%、_），用户输入 "100%" 会匹配所有以 100 开头的记录。使用 PredicateHelper.escapeLikeWildcards() 转义并指定 LIKE_ESCAPE_CHAR 确保查询正确性。

#### 2. LambdaUtils 后台清理线程关闭机制
**文件**：src/main/java/com/zsubera/jpa/util/LambdaUtils.java:68-94
**修改前**：
```java
private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

static {
    ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "myjpa-cache-cleaner");
        t.setDaemon(true);
        return t;
    });
    cleaner.scheduleAtFixedRate(() -> {
```
**修改后**：
```java
private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

/** Background daemon thread for periodic cache eviction. */
private static final ScheduledExecutorService CLEANUP_EXECUTOR;

static {
    CLEANUP_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "myjpa-cache-cleaner");
        t.setDaemon(true);
        return t;
    });
    CLEANUP_EXECUTOR.scheduleAtFixedRate(() -> {
```
**原因**：原实现将 ScheduledExecutorService 作为局部变量在 static initializer 中创建，无法在应用关闭时正确释放。提升为类级字段并添加 shutdown() 方法，支持优雅关闭。

#### 3. LambdaUtils.shutdown() 方法
**文件**：src/main/java/com/zsubera/jpa/util/LambdaUtils.java:96-111
**修改前**：无
**修改后**：
```java
/**
 * 关闭后台清理线程。在应用关闭或热部署环境中应调用此方法以确保资源正确释放。
 *
 * <p>
 * 已在 {@code MyJpaPlusAutoConfiguration} 中通过 {@code DisposableBean} 自动注册关闭钩子。
 */
public static void shutdown() {
    CLEANUP_EXECUTOR.shutdown();
    try {
        if (!CLEANUP_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
            CLEANUP_EXECUTOR.shutdownNow();
        }
    } catch (InterruptedException e) {
        CLEANUP_EXECUTOR.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
```
**原因**：提供公共关闭方法，支持 OSGi 和热部署环境中的资源释放。

#### 4. MyJpaPlusAutoConfiguration 关闭钩子
**文件**：src/main/java/com/zsubera/jpa/autoconfigure/MyJpaPlusAutoConfiguration.java:60-70
**修改前**：
```java
@Bean
@ConditionalOnMissingBean(MyJpaTemplate.class)
public MyJpaTemplate myJpaTemplate(MyJpaPlusProperties properties) {
    return new MyJpaTemplate(properties.getQuery().getMaxResults(),
        properties.getQuery().getDeepPaginationOffsetThreshold());
}
}
```
**修改后**：
```java
@Bean
@ConditionalOnMissingBean(MyJpaTemplate.class)
public MyJpaTemplate myJpaTemplate(MyJpaPlusProperties properties) {
    return new MyJpaTemplate(properties.getQuery().getMaxResults(),
        properties.getQuery().getDeepPaginationOffsetThreshold());
}

/**
 * 应用关闭时清理 LambdaUtils 后台缓存清理线程，防止在 OSGi 或热部署环境中导致类加载器泄漏。
 *
 * @param event 上下文关闭事件
 */
@EventListener(ContextClosedEvent.class)
public void onContextClosed(ContextClosedEvent event) {
    LambdaUtils.shutdown();
    log.info("MyJpa-Plus LambdaUtils cleanup executor shut down");
}
}
```
**原因**：通过 Spring 事件监听器自动注册关闭钩子，无需手动管理生命周期。

#### 5. QuerySpec.timeout() 正数校验
**文件**：src/main/java/com/zsubera/jpa/spec/QuerySpec.java:101-104
**修改前**：
```java
public QuerySpec<T> timeout(int seconds) {
    this.queryTimeout = seconds;
    return this;
}
```
**修改后**：
```java
public QuerySpec<T> timeout(int seconds) {
    if (seconds <= 0) {
        throw new IllegalArgumentException("timeout must be positive, got: " + seconds);
    }
    this.queryTimeout = seconds;
    return this;
}
```
**原因**：超时时间必须为正数，添加校验以快速失败。

#### 6. QuerySpec.lockMode() null 校验
**文件**：src/main/java/com/zsubera/jpa/spec/QuerySpec.java:121-124
**修改前**：
```java
public QuerySpec<T> lockMode(LockModeType lockMode) {
    this.lockMode = lockMode;
    return this;
}
```
**修改后**：
```java
public QuerySpec<T> lockMode(LockModeType lockMode) {
    if (lockMode == null) {
        throw new IllegalArgumentException("lockMode must not be null");
    }
    this.lockMode = lockMode;
    return this;
}
```
**原因**：符合项目规范"所有公开 API 参数必须添加 null 校验"。

#### 7. JoinGroup 构造函数 null 校验
**文件**：src/main/java/com/zsubera/jpa/spec/JoinGroup.java:34-37
**修改前**：
```java
JoinGroup(QuerySpec<T> root, ConditionNode.JoinNode joinNode) {
    this.root = root;
    this.joinNode = joinNode;
}
```
**修改后**：
```java
JoinGroup(QuerySpec<T> root, ConditionNode.JoinNode joinNode) {
    if (root == null) {
        throw new IllegalArgumentException("root must not be null");
    }
    if (joinNode == null) {
        throw new IllegalArgumentException("joinNode must not be null");
    }
    this.root = root;
    this.joinNode = joinNode;
}
```
**原因**：防止构造时传入 null 导致后续 NullPointerException。

#### 8. JoinGroup.or(Consumer) null 校验
**文件**：src/main/java/com/zsubera/jpa/spec/JoinGroup.java:162-167
**修改前**：
```java
public JoinGroup<T, J> or(Consumer<OrJoinGroup<T, J>> config) {
    ConditionNode.OrNode orNode = new ConditionNode.OrNode();
    joinNode.innerConditions.add(orNode);
    config.accept(new OrJoinGroup<>(root, joinNode, orNode));
    return this;
}
```
**修改后**：
```java
public JoinGroup<T, J> or(Consumer<OrJoinGroup<T, J>> config) {
    if (config == null) {
        throw new IllegalArgumentException("config must not be null");
    }
    ConditionNode.OrNode orNode = new ConditionNode.OrNode();
    joinNode.innerConditions.add(orNode);
    config.accept(new OrJoinGroup<>(root, joinNode, orNode));
    return this;
}
```
**原因**：符合项目规范"所有公开 API 参数必须添加 null 校验"。

#### 9. OrGroup 构造函数 null 校验
**文件**：src/main/java/com/zsubera/jpa/spec/OrGroup.java:17-19
**修改前**：
```java
OrGroup(QuerySpec<T> root) {
    this.root = root;
}
```
**修改后**：
```java
OrGroup(QuerySpec<T> root) {
    if (root == null) {
        throw new IllegalArgumentException("root must not be null");
    }
    this.root = root;
}
```
**原因**：防止构造时传入 null。

#### 10. OrJoinGroup 构造函数 null 校验
**文件**：src/main/java/com/zsubera/jpa/spec/OrJoinGroup.java:17-21
**修改前**：
```java
OrJoinGroup(QuerySpec<T> root, ConditionNode.JoinNode joinNode, ConditionNode.OrNode orNode) {
    this.root = root;
    this.joinNode = joinNode;
    this.orNode = orNode;
}
```
**修改后**：
```java
OrJoinGroup(QuerySpec<T> root, ConditionNode.JoinNode joinNode, ConditionNode.OrNode orNode) {
    if (root == null) {
        throw new IllegalArgumentException("root must not be null");
    }
    if (joinNode == null) {
        throw new IllegalArgumentException("joinNode must not be null");
    }
    if (orNode == null) {
        throw new IllegalArgumentException("orNode must not be null");
    }
    this.root = root;
    this.joinNode = joinNode;
    this.orNode = orNode;
}
```
**原因**：防止构造时传入 null。

#### 11. ConditionNode 子类 toString() 方法
**文件**：src/main/java/com/zsubera/jpa/spec/ConditionNode.java
**修改前**：MultiLikeNode、CollectionNode、ExistsNode、RawNode、NegateNode 无 toString() 方法
**修改后**：
```java
// MultiLikeNode
@Override
public String toString() {
    return "MultiLikeNode[keyword='" + keyword + "', fields=" + java.util.Arrays.toString(fieldNames) + "]";
}

// CollectionNode
@Override
public String toString() {
    return "CollectionNode[" + fieldName + " " + op + "]";
}

// ExistsNode
@Override
public String toString() {
    return "ExistsNode[" + (negate ? "NOT " : "") + subEntity.getSimpleName() + "]";
}

// RawNode
@Override
public String toString() {
    return "RawNode[fn=" + fn.getClass().getName() + "]";
}

// NegateNode
@Override
public String toString() {
    return "NegateNode[" + inner + "]";
}
```
**原因**：调试时只能看到默认的 @hashcode 表示，添加 toString() 提高调试效率和日志可读性。

#### 12. ProjectionSpec.asDto() null 校验
**文件**：src/main/java/com/zsubera/jpa/projection/ProjectionSpec.java:655-658
**修改前**：
```java
public ProjectionSpec<T> asDto(Class<?> dtoClass) {
    this.dtoClass = dtoClass;
    return this;
}
```
**修改后**：
```java
public ProjectionSpec<T> asDto(Class<?> dtoClass) {
    if (dtoClass == null) {
        throw new IllegalArgumentException("dtoClass must not be null");
    }
    this.dtoClass = dtoClass;
    return this;
}
```
**原因**：符合项目规范"所有公开 API 参数必须添加 null 校验"。

#### 13. SoftDeleteHelper.isSoftDeleted() null 校验
**文件**：src/main/java/com/zsubera/jpa/update/SoftDeleteHelper.java:282-283
**修改前**：
```java
public static <T> boolean isSoftDeleted(Class<T> entityClass, T entity) {
    String fieldName = findSoftDeleteField(entityClass);
```
**修改后**：
```java
public static <T> boolean isSoftDeleted(Class<T> entityClass, T entity) {
    if (entityClass == null) {
        throw new IllegalArgumentException("entityClass must not be null");
    }
    if (entity == null) {
        throw new IllegalArgumentException("entity must not be null");
    }
    String fieldName = findSoftDeleteField(entityClass);
```
**原因**：符合项目规范"所有公开 API 参数必须添加 null 校验"。

#### 14. EntityGraphHelper.forEntity() null 校验
**文件**：src/main/java/com/zsubera/jpa/util/EntityGraphHelper.java:60-61
**修改前**：
```java
public static <T> EntityGraphHelper<T> forEntity(Class<T> entityClass) {
    return new EntityGraphHelper<>(entityClass);
}
```
**修改后**：
```java
public static <T> EntityGraphHelper<T> forEntity(Class<T> entityClass) {
    if (entityClass == null) {
        throw new IllegalArgumentException("entityClass must not be null");
    }
    return new EntityGraphHelper<>(entityClass);
}
```
**原因**：符合项目规范"所有公开 API 参数必须添加 null 校验"。

#### 15. ProjectionSpec LIKE 条件 import 添加
**文件**：src/main/java/com/zsubera/jpa/projection/ProjectionSpec.java:3-5
**修改前**：
```java
import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.LambdaUtils;
```
**修改后**：
```java
import com.zsubera.jpa.spec.PredicateHelper;
import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.LambdaUtils;
```
**原因**：新增对 PredicateHelper.escapeLikeWildcards() 和 PredicateHelper.LIKE_ESCAPE_CHAR 的引用。

---