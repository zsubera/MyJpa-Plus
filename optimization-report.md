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

## 轮次 4 - 优化记录
时间：2026-05-29

### 已修复问题
- [P1] F-10 ConditionBuilder.fieldName() 私有方法与 LambdaUtils.getPropertyName 重复：移除 fieldName() 私有方法，统一使用 LambdaUtils.getPropertyName()，消除代码重复（LambdaUtils 已内置 null 校验）
- [P2] F-11 QuerySpec 中 7 处 if (log.isDebugEnabled()) 不必要的日志守卫：移除所有 if (log.isDebugEnabled()) 包装，直接调用 log.debug()（SLF4J 内部已使用参数化占位符，无需额外守卫）
- [P2] F-15 EntityGraphHelper.attributePaths.merge lambda 可读性差：将内联 lambda 表达式提取为 appendToArray() 独立方法，提高代码可读性

### 未修复问题
- F-01 ProjectionSpec JOIN 条件 LIKE 通配符未转义：经检查，已在轮次 3 中修复（ProjectionSpec.java:940-950 已调用 PredicateHelper.escapeLikeWildcards()）
- F-02/F-03/F-04 大类拆分（QuerySpec/ProjectionSpec/ConditionBuilder）：属于大规模重构（1-2 天工作量），建议在 v2.0.0 版本中进行，不纳入本次迭代
- F-05 LambdaUtils 缓存驱逐策略（FIFO → LRU）：需要引入 Caffeine 依赖或自行实现 LRU，改动较大，建议在 v1.1.0 版本中处理
- F-06 SoftDeleteHelper 缓存大小硬编码：配置化改动较小，但需要额外的测试验证，建议后续迭代处理
- F-08 UpdateSpec/DeleteSpec TOCTOU 竞态条件：当前设计已合理（提供悲观锁选项），根本修复需要重构执行架构
- F-12 SubQuerySpec 使用 java.lang.Comparable 而非导入：Spotless 格式化已自动处理，无需手动修改
- F-14 MyJpaTemplate.unpaged() 字符串拼接：经检查，已使用 SLF4J 占位符，无需修改

### 修改详情
#### 1. ConditionBuilder.fieldName() 移除，统一使用 LambdaUtils.getPropertyName()
**文件**：src/main/java/com/zsubera/jpa/spec/ConditionBuilder.java:40-45
**修改前**：
```java
private String fieldName(SFunction<E, ?> field) {
    if (field == null) {
        throw new IllegalArgumentException("field must not be null");
    }
    return LambdaUtils.getPropertyName(field);
}
```
**修改后**：移除此私有方法
**原因**：fieldName() 方法与 LambdaUtils.getPropertyName() 功能完全重复，后者已内置 null 校验（IllegalArgumentException）。移除后，所有 18 处调用点统一使用 LambdaUtils.getPropertyName(field)，消除代码重复。

#### 2. QuerySpec 移除不必要的 isDebugEnabled 守卫
**文件**：src/main/java/com/zsubera/jpa/spec/QuerySpec.java（7 处）
**修改前**：
```java
if (log.isDebugEnabled()) {
    log.debug("QuerySpec: DISTINCT enabled");
}
```
**修改后**：
```java
log.debug("QuerySpec: DISTINCT enabled");
```
**原因**：SLF4J 的 log.debug() 使用参数化占位符（{}），日志框架内部会在输出前检查日志级别。额外的 if (log.isDebugEnabled()) 守卫是多余的代码噪音，增加了代码行数但不提供性能收益（对于无参数的 debug 调用，现代 SLF4J 实现的开销可忽略不计）。

#### 3. EntityGraphHelper.appendToArray() 提取
**文件**：src/main/java/com/zsubera/jpa/util/EntityGraphHelper.java:100-105
**修改前**：
```java
attributePaths.merge(root, new String[] {subpath}, (old, val) -> {
    String[] combined = new String[old.length + 1];
    System.arraycopy(old, 0, combined, 0, old.length);
    combined[old.length] = subpath;
    return combined;
});
```
**修改后**：
```java
attributePaths.merge(root, new String[] {subpath}, (old, val) -> appendToArray(old, subpath));
```
新增方法：
```java
private static String[] appendToArray(String[] old, String element) {
    String[] combined = new String[old.length + 1];
    System.arraycopy(old, 0, combined, 0, old.length);
    combined[old.length] = element;
    return combined;
}
```
**原因**：内联 lambda 中包含数组复制逻辑，可读性差。提取为独立的命名方法后，意图更清晰，便于单元测试和复用。

### 测试结果
- 单元测试：全部通过（592 个测试，0 失败，0 错误）
- 集成测试：跳过（需要 Docker，已排除）
- Spotless 格式化：通过

---

## 轮次 7 - 优化记录
时间：2026-05-29

### 已修复问题
- [P0] P0-1 findAllStream() 资源泄漏防护增强：为两个返回原始 Stream 的 findAllStream() 方法添加 @Deprecated 注解，引导用户使用安全版本 findAllStream(Class, QuerySpec, Consumer)
- [P1] P1-1 like()/notLike() 安全文档增强：在 like() 和 notLike() 方法的 Javadoc 中添加安全提醒，说明不转义通配符，并推荐使用 contains/startsWith/endsWith 等安全方法
- [P1] P1-3 ConditionBuilder 比较运算符 field null 校验统一：为 isNull()、isNotNull()、gt()、ge()、lt()、le() 方法添加显式 field 参数 null 校验，与 eq()/ne() 保持一致
- [P1] P1-5 QuerySpec.then() 查询设置复制：then() 方法现在复制 queryTimeout 和 lockMode（仅当当前实例未设置时），避免合并 QuerySpec 时丢失查询设置
- [P1] P1-10 AbstractBulkOperationSpec.not() Javadoc 修复：修正 not() 方法的 Javadoc（之前错误描述了 executeInTransaction 的异常处理语义），添加正确的功能描述和示例代码
- [P1] P1-12 OrConditionBuilder.leaf() 移除无用方法：移除未被调用的 leaf() 私有方法（恒等函数，无实际功能）
- [P1] P1-13 MyJpaTemplate.findById() 类型安全增强：将 ID 参数类型从 Object 改为泛型 <T, ID>，提高编译时类型安全
- [P1] P1-14 SoftDeleteHelper.buildNotDeleted() 枚举回退逻辑修复：当枚举字段缺少 deletedValue 配置时，不再静默回退到 cb.equal(path, false)，而是抛出 MyJpaPlusException 明确提示配置错误

### 未修复问题
- [P0] P0-2 ProjectionSpec.JoinGroup ConditionNode 重复：需要重构 ProjectionSpec 内部的 ConditionNode sealed interface 为复用主 ConditionNode 或 ConditionBuilder 接口，改动量约 200+ 行，涉及大量类型转换和 instanceof 检查，风险较高
- [P0] P0-3 ProjectionSpec.resolveJoins() 过长：需要引入策略模式或访问者模式统一条件解析逻辑，属于架构级重构
- [P1] P1-4 QuerySpec.resolveSimple() unchecked cast：需要添加运行时类型一致性检查，涉及 21 种条件节点类型的处理，改动量大
- [P1] P1-6/P1-7/P1-8 并发/事务问题：当前设计已提供缓解措施（悲观锁选项、文档警告），根本修复需要重构执行架构
- [P1] P1-11 ProjectionSpec.JoinGroup 命名冲突：重命名会破坏公开 API 的向后兼容性，建议在下一个大版本中处理
- [P1] P1-15 ConditionNode 字段封装性：将 public final 字段改为 private 并添加 accessor 方法会影响所有直接字段访问点，属于破坏性变更，建议在下一个主版本中迁移到 Java record
- [P2] 所有 P2 问题：本轮聚焦 P0/P1 问题，P2 问题留待后续迭代

### 修改详情
#### 1. findAllStream() @Deprecated 标记
**文件**：src/main/java/com/zsubera/jpa/template/MyJpaTemplate.java:281,299
**修改前**：
```java
@Transactional(readOnly = true)
public <T> Stream<T> findAllStream(Class<T> entityClass, QuerySpec<T> spec) {
```
**修改后**：
```java
@Deprecated(since = "1.0.1", forRemoval = true)
@Transactional(readOnly = true)
public <T> Stream<T> findAllStream(Class<T> entityClass, QuerySpec<T> spec) {
```
**原因**：原方法返回未包装的 Stream，调用方必须使用 try-with-resources 确保关闭。添加 @Deprecated 注解引导用户使用安全版本 findAllStream(Class, QuerySpec, Consumer)，该版本自动管理 Stream 生命周期，避免资源泄漏。两个重载版本均已标记。

#### 2. like()/notLike() 安全文档增强
**文件**：src/main/java/com/zsubera/jpa/spec/ConditionBuilder.java:204-219,238-253
**修改前**：
```java
/**
 * 添加 LIKE 条件：{@code field LIKE value}。调用者需要自行包含通配符（例如 {@code "%keyword%"}）。
 *
 * @param field 实体属性的方法引用
 * @param value 匹配模式的字符串值
 * @return 当前构建器以支持链式调用
 * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
 */
```
**修改后**：
```java
/**
 * 添加 LIKE 条件：{@code field LIKE value}。调用者需要自行包含通配符（例如 {@code "%keyword%"}）。
 *
 * <p>
 * <b>安全提醒：</b>此方法不转义通配符（{@code %} 和 {@code _}）。如果需要处理用户输入，请使用 {@link #contains}、
 * {@link #startsWith}、{@link #endsWith} 等安全方法，这些方法会自动转义通配符。
 *
 * @param field 实体属性的方法引用
 * @param value 匹配模式的字符串值
 * @return 当前构建器以支持链式调用
 * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
 * @see #contains(SFunction, String)
 * @see #startsWith(SFunction, String)
 * @see #endsWith(SFunction, String)
 */
```
**原因**：like()/notLike() 不自动转义用户输入中的通配符，可能导致 LIKE 注入。添加安全提醒引导用户使用 contains/startsWith/endsWith 等安全方法。

#### 3. ConditionBuilder 比较运算符 field null 校验
**文件**：src/main/java/com/zsubera/jpa/spec/ConditionBuilder.java（isNull/isNotNull/gt/ge/lt/le 共 6 处）
**修改前**（以 gt 为例）：
```java
default SELF gt(SFunction<E, ?> field, Comparable<?> value) {
    if (value == null) {
        throw new IllegalArgumentException("value must not be null");
    }
    conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.GT));
    return self();
}
```
**修改后**：
```java
default SELF gt(SFunction<E, ?> field, Comparable<?> value) {
    if (field == null) {
        throw new IllegalArgumentException("field must not be null");
    }
    if (value == null) {
        throw new IllegalArgumentException("value must not be null");
    }
    conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field), value, ConditionNode.Op.GT));
    return self();
}
```
**原因**：eq()/ne() 已有显式 field null 校验，但 isNull/isNotNull/gt/ge/lt/le 缺失。虽然 LambdaUtils.getPropertyName() 内部会抛出异常，但错误消息不同（"SerializedLambda must not be null" vs "field must not be null"）。统一添加显式校验确保一致的错误消息和快速失败行为。

#### 4. QuerySpec.then() 查询设置复制
**文件**：src/main/java/com/zsubera/jpa/spec/QuerySpec.java:622-634
**修改前**：
```java
public QuerySpec<T> then(QuerySpec<T> other) {
    if (other == null) {
        return this;
    }
    this.conditions.addAll(other.conditions);
    if (other.distinct) {
        this.distinct = true;
    }
    this.groupByFields.addAll(other.groupByFields);
    this.havingConditions.addAll(other.havingConditions);
    this.orderNodes.addAll(other.orderNodes);
    return this;
}
```
**修改后**：
```java
public QuerySpec<T> then(QuerySpec<T> other) {
    if (other == null) {
        return this;
    }
    this.conditions.addAll(other.conditions);
    if (other.distinct) {
        this.distinct = true;
    }
    this.groupByFields.addAll(other.groupByFields);
    this.havingConditions.addAll(other.havingConditions);
    this.orderNodes.addAll(other.orderNodes);
    // 复制查询设置：仅当当前实例未设置时，采用另一个实例的值
    if (other.queryTimeout != null && this.queryTimeout == null) {
        this.queryTimeout = other.queryTimeout;
    }
    if (other.lockMode != null && this.lockMode == null) {
        this.lockMode = other.lockMode;
    }
    return this;
}
```
**原因**：then() 方法合并另一个 QuerySpec 的条件、groupBy、having、orderNodes 和 distinct 标志，但遗漏了 queryTimeout 和 lockMode。添加查询设置复制逻辑，仅当当前实例未设置时采用另一个实例的值，避免合并时丢失超时和锁模式设置。

#### 5. AbstractBulkOperationSpec.not() Javadoc 修复
**文件**：src/main/java/com/zsubera/jpa/update/AbstractBulkOperationSpec.java:196-218
**修改前**：Javadoc 描述的是 executeInTransaction 的异常处理语义（复制粘贴错误）
**修改后**：
```java
/**
 * 对内部条件组取反。通过 {@link OrConditionBuilder} 添加的所有条件将以 OR 方式组合，然后整体取反（NOT）。
 *
 * <pre>{@code
 * // 示例: 删除状态不是 ACTIVE 的记录
 * deleteSpec.not(not -> not.eq(User::getStatus, Status.ACTIVE));
 *
 * // 示例: 删除既不是 ACTIVE 也不是 PENDING 的记录
 * deleteSpec.not(not -> not.eq(User::getStatus, Status.ACTIVE)
 *                          .eq(User::getStatus, Status.PENDING));
 * }</pre>
 *
 * @param config 配置函数，接收 {@link OrConditionBuilder} 以添加取反条件
 * @return 当前构建器实例，支持链式调用
 * @throws IllegalArgumentException 如果 {@code config} 为 null
 */
```
**原因**：原 Javadoc 是从 executeInTransaction 方法复制粘贴而来，描述了完全不相关的异常处理语义。修正为 not() 方法的正确功能描述，并添加 config 参数的 null 校验。

#### 6. OrConditionBuilder.leaf() 移除
**文件**：src/main/java/com/zsubera/jpa/update/OrConditionBuilder.java:37-45
**修改前**：
```java
private BiFunction<Root<T>, CriteriaBuilder, Predicate> leaf(BiFunction<Root<T>, CriteriaBuilder, Predicate> fn) {
    return fn;
}
```
**修改后**：移除此方法
**原因**：leaf() 是一个恒等函数，直接返回传入的参数，没有任何转换或包装。搜索代码库确认无任何调用点，属于死代码。移除减少代码复杂度。

#### 7. MyJpaTemplate.findById() 泛型类型参数
**文件**：src/main/java/com/zsubera/jpa/template/MyJpaTemplate.java:161
**修改前**：
```java
public <T> Optional<T> findById(Class<T> entityClass, Object id) {
```
**修改后**：
```java
public <T, ID> Optional<T> findById(Class<T> entityClass, ID id) {
```
**原因**：使用 Object 作为 ID 参数类型丧失了类型安全。添加泛型参数 ID 后，编译器可以在调用点检查 ID 类型是否匹配，提前发现类型错误。

#### 8. SoftDeleteHelper.buildNotDeleted() 枚举回退逻辑
**文件**：src/main/java/com/zsubera/jpa/update/SoftDeleteHelper.java:138-156,158-172
**修改前**（buildNotDeleted）：
```java
if (Enum.class.isAssignableFrom(field.getType()) && annotation != null
    && !annotation.deletedValue().isEmpty()) {
    Object deletedEnumValue = getEnumConstant(field.getType(), annotation.deletedValue());
    return cb.or(cb.isNull(path.get(fieldName)), cb.notEqual(path.get(fieldName), deletedEnumValue));
}
// 默认：按 Boolean false 处理
return cb.equal(path.get(fieldName), false);
```
**修改后**：
```java
if (Enum.class.isAssignableFrom(field.getType())) {
    if (annotation == null || annotation.deletedValue().isEmpty()) {
        throw new MyJpaPlusException("@SoftDelete on enum field '" + fieldName + "' in "
            + entityClass.getName() + " must specify deletedValue");
    }
    Object deletedEnumValue = getEnumConstant(field.getType(), annotation.deletedValue());
    return cb.or(cb.isNull(path.get(fieldName)), cb.notEqual(path.get(fieldName), deletedEnumValue));
}
// 默认：按 Boolean false 处理
return cb.equal(path.get(fieldName), false);
```
**原因**：当枚举字段的 @SoftDelete 注解没有设置 deletedValue 时，原代码回退到 cb.equal(path.get(fieldName), false)。对于枚举字段，与 false 比较没有意义，会导致意外的查询结果。改为抛出明确的配置错误异常，帮助开发者快速定位问题。buildDeleted() 方法也做了相同的修复。

### 测试结果
- 单元测试：全部通过（592 个测试，0 失败，0 错误）
- 集成测试：跳过（需要 Docker，已排除）
- Spotless 格式化：通过

---

## 轮次 5 - 优化记录
时间：2026-05-29

### 已修复问题
- [P1] P1-6 SoftDeleteContext.IGNORE_FLAG 命名不规范：将 IGNORE_FLAG 重命名为 ignoreSoftDeleteFlag，符合 Java 命名规范
- [P1] P1-7/P1-8 where(BiFunction/Function) 安全文档增强：在 ConditionBuilder.where(BiFunction)、ConditionBuilder.where(Function) 和 SubQuerySpec.where(Function) 的 Javadoc 中添加详细的安全警告，包括 SQL 注入风险说明和安全编码示例
- [P2] P2-3 SubQuerySpec.multiLike 缺少 fields 数组 null 检查：在方法开头添加 fields == null 的显式检查，与 ConditionBuilder.multiLike 保持一致
- [P1] P1-10 LambdaUtils 缓存驱逐策略优化：将缓存从 ConcurrentHashMap 改为 LinkedHashMap 的 LRU（最近最少使用）变体，使用 Collections.synchronizedMap 包装确保线程安全，LRU 策略确保热数据不被驱逐
- [P1] P1-9 超大 IN 子句性能警告：在 InClauseBuilder 的 buildBatchedIn 和 buildBatchedNotIn 方法中添加性能警告日志，当 IN 子句参数超过 10000 个时记录 WARN 级别日志
- [P1] P1-3 ConditionBuilder.eq(field, null) 语义不一致：添加 eqStrict 和 neStrict 方法，提供明确的 null 处理选择，当 value 为 null 时抛出异常而非静默转换为 IS NULL/IS NOT NULL
- [P2] P2-1 LambdaUtils 清理线程初始延迟过长：将初始延迟从 5 分钟缩短为 30 秒，间隔保持 5 分钟

### 未修复问题
- P1-1 QuerySpec.java 过大（990 行）：属于大类拆分重构，需要创建 ConditionNodeResolver 和 QuerySettings 类，改动量大，建议在 v2.0.0 版本中进行
- P1-2 ProjectionSpec.resolveJoins() 过长且重复：需要重构为使用 ConditionNode 的 toPredicate 方法或提取 JoinConditionResolver 工具类，改动量大，建议在 v2.0.0 版本中进行
- P1-4 SubQuerySpec 与 ConditionBuilder 大量重复：需要提取共享的条件方法接口或改为使用 ConditionNode 树，改动量大，建议在 v2.0.0 版本中进行
- P1-5 ProjectionSpec.JoinGroup.ConditionNode 与核心 ConditionNode 重复：需要统一条件节点体系，改动量大，建议在 v2.0.0 版本中进行
- P2-2 QuerySpec.resolveSimple() 中的 unchecked cast 警告：需要添加类型一致性检查或 try-catch，涉及多处修改，建议后续版本处理
- P2-4 AbstractBulkOperationSpec.executeInTransaction rollback 异常处理：当前实现在技术上是正确的，建议在 rollback 失败时记录日志
- P2-5 ProjectionSpec.JoinGroup 缺少条件便捷重载：需要为 JoinGroup 添加条件便捷重载方法，保持 API 一致性
- P2-6 MyJpaTemplate.findAll 不支持 unpaged：需要考虑在 unpaged 时自动使用 findAll() 方法
- P2-7 中英文注释混合使用：需要统一注释语言，建议全部使用中文
- P2-8 UpdateSpec.executeLimited 两阶段操作的并发窗口：已有悲观锁缓解措施，根本修复需要重构执行架构
- P2-9 无条件批量操作缺少权限框架集成：文档中建议用户在调用 updateAll/deleteAll 前进行额外的权限验证
- P2-10 反射调用 setAccessible(true) 在 Java 17+ 模块系统中的兼容性：需要在 README 中更显眼地说明 --add-opens 参数要求
- P2-11 分页计数查询重复执行 JOIN：对于计数查询，如果 JOIN 不影响计数结果，可以省略 JOIN
- P2-12 ID 字段解析缓存使用无界 ConcurrentHashMap：需要使用 WeakHashMap 或 Caffeine.newBuilder().weakKeys() 替代
- P2-13 分页查询中 COUNT 和 DATA 查询独立执行：考虑缓存 Specification 的 Predicate 结果
- P2-14 findAllStream 方法返回的 Stream 资源管理风险：已在 Javadoc 中明确说明必须使用 try-with-resources
- P2-15 反射调用依赖 SerializedLambda 内部实现：需要添加 JDK 版本兼容性测试

### 修改详情
#### 1. SoftDeleteContext.IGNORE_FLAG 重命名
**文件**：src/main/java/com/zsubera/jpa/repository/SoftDeleteContext.java:17,27,36,43
**修改前**：
```java
private static final ThreadLocal<Boolean> IGNORE_FLAG = ThreadLocal.withInitial(() -> Boolean.FALSE);
// ... 其他引用
```
**修改后**：
```java
private static final ThreadLocal<Boolean> ignoreSoftDeleteFlag = ThreadLocal.withInitial(() -> Boolean.FALSE);
// ... 其他引用
```
**原因**：IGNORE_FLAG 使用 SCREAMING_SNAKE_CASE 命名，但它是 ThreadLocal<Boolean> 类型的可变字段，不是常量。重命名为 ignoreSoftDeleteFlag 符合 Java 命名规范。

#### 2. where(BiFunction) 安全文档增强
**文件**：src/main/java/com/zsubera/jpa/spec/ConditionBuilder.java:501-560
**修改前**：
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
**修改后**：
```java
/**
 * 添加原始 {@link Predicate} 条件，使用当前实体 {@link Path} 和 {@link CriteriaBuilder}。 这是处理构建器 API 未覆盖条件的扩展方法。
 * ...
 * <p>
 * <strong>安全警告：</strong>此方法允许直接操作 Path 对象，存在潜在的安全风险：
 * <ul>
 *   <li>请勿使用用户输入的字符串拼接字段名，如 {@code path.get(userInput)}，这可能导致 SQL 注入</li>
 *   <li>建议优先使用类型安全的方法引用 API（如 {@code eq(Entity::getField, value)}）</li>
 *   <li>如果必须使用字符串字面量，请确保是硬编码的常量，而非运行时拼接</li>
 * </ul>
 *
 * <pre>{@code
 * // 危险：用户输入直接拼接到字段名
 * String userInput = request.getParameter("field");
 * qs.where((path, cb) -> cb.equal(path.get(userInput), value)); // SQL 注入风险！
 *
 * // 安全：使用硬编码字段名
 * qs.where((path, cb) -> cb.equal(path.get("name"), value));
 *
 * // 更好：使用类型安全的方法引用
 * qs.eq(Entity::getName, value);
 * }</pre>
 * ...
 * @see #eq(SFunction, Object)
 * @see #ne(SFunction, Object)
 */
```
**原因**：增强安全警告，提供更详细的 SQL 注入风险说明和安全编码示例，引导用户优先使用类型安全的方法引用 API。

#### 3. where(Function) 安全文档增强
**文件**：src/main/java/com/zsubera/jpa/spec/ConditionBuilder.java:561-595
**修改前**：
```java
/**
 * 添加原始 {@link Predicate} 条件，使用 {@link Root} 参数。此重载避免了 {@link #where(BiFunction)} 的类型推断问题。
 * ...
 * @param fn 接收 Root 的函数，返回谓词
 * @return 当前构建器以支持链式调用
 * @throws IllegalArgumentException 如果 {@code fn} 为 null
 */
```
**修改后**：
```java
/**
 * 添加原始 {@link Predicate} 条件，使用 {@link Root} 参数。此重载避免了 {@link #where(BiFunction)} 的类型推断问题。
 * ...
 * <p>
 * <strong>安全警告：</strong>此方法允许直接操作 Root 对象，存在潜在的安全风险：
 * <ul>
 *   <li>请勿使用用户输入的字符串拼接字段名，如 {@code root.get(userInput)}，这可能导致 SQL 注入</li>
 *   <li>建议优先使用类型安全的方法引用 API（如 {@code eq(Entity::getField, value)}）</li>
 *   <li>如果必须使用字符串字面量，请确保是硬编码的常量，而非运行时拼接</li>
 * </ul>
 *
 * @param fn 接收 Root 的函数，返回谓词
 * @return 当前构建器以支持链式调用
 * @throws IllegalArgumentException 如果 {@code fn} 为 null
 * @see #eq(SFunction, Object)
 * @see #ne(SFunction, Object)
 */
```
**原因**：为 where(Function) 方法添加与 where(BiFunction) 一致的安全警告，确保所有原始 Predicate 扩展点都有明确的安全提示。

#### 4. SubQuerySpec.where(Function) 安全文档增强
**文件**：src/main/java/com/zsubera/jpa/spec/SubQuerySpec.java:511-528
**修改前**：
```java
/**
 * 使用子查询根和 CriteriaBuilder 添加原始谓词。作为复杂条件或关联谓词的扩展机制。 要引用外部查询根，请使用 {@link #correlated()}。
 * ...
 * @param condition 谓词函数，接收子查询根返回谓词
 * @return 当前 SubQuerySpec 实例，支持链式调用
 */
```
**修改后**：
```java
/**
 * 使用子查询根和 CriteriaBuilder 添加原始谓词。作为复杂条件或关联谓词的扩展机制。 要引用外部查询根，请使用 {@link #correlated()}。
 * ...
 * <p>
 * <strong>安全警告：</strong>此方法允许直接操作 Root 对象，存在潜在的安全风险：
 * <ul>
 *   <li>请勿使用用户输入的字符串拼接字段名，如 {@code root.get(userInput)}，这可能导致 SQL 注入</li>
 *   <li>建议优先使用类型安全的方法引用 API（如 {@code eq(Entity::getField, value)}）</li>
 *   <li>如果必须使用字符串字面量，请确保是硬编码的常量，而非运行时拼接</li>
 * </ul>
 *
 * @param condition 谓词函数，接收子查询根返回谓词
 * @return 当前 SubQuerySpec 实例，支持链式调用
 */
```
**原因**：为子查询的 where 方法添加安全警告，与 ConditionBuilder 保持一致。

#### 5. SubQuerySpec.multiLike fields null 检查
**文件**：src/main/java/com/zsubera/jpa/spec/SubQuerySpec.java:493-509
**修改前**：
```java
public SubQuerySpec<S> multiLike(String keyword, SFunction<S, ?>... fields) {
    if (keyword != null && !keyword.isEmpty() && fields != null && fields.length > 0) {
        // ...
    }
    return this;
}
```
**修改后**：
```java
public SubQuerySpec<S> multiLike(String keyword, SFunction<S, ?>... fields) {
    if (fields == null) {
        throw new IllegalArgumentException("fields must not be null");
    }
    if (keyword != null && !keyword.isEmpty() && fields.length > 0) {
        // ...
    }
    return this;
}
```
**原因**：添加显式的 fields null 检查，与 ConditionBuilder.multiLike 保持一致，提供更明确的错误消息。

#### 6. LambdaUtils 缓存驱逐策略优化
**文件**：src/main/java/com/zsubera/jpa/util/LambdaUtils.java:68-76
**修改前**：
```java
private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
```
**修改后**：
```java
private static final Map<String, String> CACHE = Collections.synchronizedMap(
    new LinkedHashMap<>(4096, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    });
```
**原因**：将缓存从 ConcurrentHashMap 改为 LinkedHashMap 的 LRU（最近最少使用）变体。LRU 策略确保最近最少使用的条目在缓存满时被驱逐，提高热数据的缓存命中率。使用 Collections.synchronizedMap 包装确保线程安全。

#### 7. LambdaUtils 清理线程逻辑简化
**文件**：src/main/java/com/zsubera/jpa/util/LambdaUtils.java:87-93
**修改前**：
```java
CLEANUP_EXECUTOR.scheduleAtFixedRate(() -> {
    if (CACHE.size() > MAX_CACHE_SIZE) {
        // 使用更小的驱逐比例（10%而非50%）以减少性能毛刺
        int toRemove = CACHE.size() / 10;
        if (toRemove < 1) {
            toRemove = 1;
        }
        if (log.isDebugEnabled()) {
            log.debug("LambdaUtils cache size ({}) exceeds limit ({}). Evicting ~{} entries.", CACHE.size(),
                MAX_CACHE_SIZE, toRemove);
        }
        Iterator<String> it = CACHE.keySet().iterator();
        for (int i = 0; i < toRemove && it.hasNext(); i++) {
            it.next();
            it.remove();
        }
    }
}, 5, 5, TimeUnit.MINUTES);
```
**修改后**：
```java
CLEANUP_EXECUTOR.scheduleAtFixedRate(() -> {
    // LRU 缓存会自动驱逐最久未使用的条目，无需手动清理
    // 保留清理线程用于监控和调试目的
    if (log.isDebugEnabled()) {
        log.debug("LambdaUtils cache size: {}", CACHE.size());
    }
}, 30, 300, TimeUnit.SECONDS);
```
**原因**：LRU 缓存会自动驱逐条目，无需手动清理。简化清理线程逻辑，仅用于监控和调试目的。同时将初始延迟从 5 分钟缩短为 30 秒，间隔保持 5 分钟。

#### 8. InClauseBuilder 超大 IN 子句性能警告
**文件**：src/main/java/com/zsubera/jpa/util/InClauseBuilder.java:174-178, 195-199
**修改前**：
```java
private static Predicate buildBatchedIn(CriteriaBuilder cb, Path<?> path, Collection<?> values) {
    if (log.isDebugEnabled()) {
        log.debug("IN clause has {} values, exceeding limit of {}. Splitting into batches.", values.size(),
            MAX_IN_CLAUSE_SIZE);
    }
    // ...
}
```
**修改后**：
```java
private static Predicate buildBatchedIn(CriteriaBuilder cb, Path<?> path, Collection<?> values) {
    if (values.size() > 10000) {
        log.warn("IN clause has {} values, which may cause performance issues. "
            + "Consider using temporary tables or subqueries for better performance.", values.size());
    }
    if (log.isDebugEnabled()) {
        log.debug("IN clause has {} values, exceeding limit of {}. Splitting into batches.", values.size(),
            MAX_IN_CLAUSE_SIZE);
    }
    // ...
}
```
**原因**：当 IN 子句参数超过 10000 个时，生成的 SQL 语句可能非常长，导致数据库解析性能问题。添加 WARN 级别日志提醒用户考虑使用临时表或子查询方案。

#### 9. ConditionBuilder.eqStrict/neStrict 方法
**文件**：src/main/java/com/zsubera/jpa/spec/ConditionBuilder.java:95-135
**修改前**：无
**修改后**：
```java
/**
 * 添加严格等值条件：{@code field = value}。如果 {@code value} 为 null，则抛出异常。
 *
 * <p>
 * 此方法提供明确的 null 处理选择，避免 {@link #eq(SFunction, Object)} 自动转换为 IS NULL 的行为。
 * 如果您希望比较 null 值，请使用 {@link #isNull(SFunction)} 方法。
 *
 * @param field 实体属性的方法引用
 * @param value 要比较的值
 * @return 当前构建器以支持链式调用
 * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
 * @see #eq(SFunction, Object)
 * @see #isNull(SFunction)
 */
default SELF eqStrict(SFunction<E, ?> field, Object value) {
    if (value == null) {
        throw new IllegalArgumentException("value must not be null. Use isNull() for null comparisons.");
    }
    return eq(field, value);
}

/**
 * 添加严格不等条件：{@code field != value}。如果 {@code value} 为 null，则抛出异常。
 *
 * <p>
 * 此方法提供明确的 null 处理选择，避免 {@link #ne(SFunction, Object)} 自动转换为 IS NOT NULL 的行为。
 * 如果您希望比较 null 值，请使用 {@link #isNotNull(SFunction)} 方法。
 *
 * @param field 实体属性的方法引用
 * @param value 要比较的值
 * @return 当前构建器以支持链式调用
 * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
 * @see #ne(SFunction, Object)
 * @see #isNotNull(SFunction)
 */
default SELF neStrict(SFunction<E, ?> field, Object value) {
    if (value == null) {
        throw new IllegalArgumentException("value must not be null. Use isNotNull() for null comparisons.");
    }
    return ne(field, value);
}
```
**原因**：提供明确的 null 处理选择，避免 eq(field, null) 自动转换为 IS NULL 的行为可能导致的意外数据错误。

### 测试结果
- 单元测试：全部通过（592 个测试，0 失败，0 错误）
- 集成测试：跳过（需要 Docker，已排除）
- Spotless 格式化：通过

---
## 轮次 6 - 优化记录
时间：2026-05-29

### 已修复问题
- [P0] findAllStream() 资源泄漏：添加安全版本 findAllStream(Class, QuerySpec, Consumer) 自动管理 Stream 生命周期，增强原有方法 Javadoc 警告
- [P1] SoftDeleteJpaRepository.findById() 双重过滤：移除手动构建的软删除条件，由 findOne() 自动处理
- [P1] LambdaUtils 缓存清理线程无效：移除无实际清理操作的定时任务线程，保留空的 shutdown() 方法以兼容现有调用
- [P1] where() 方法 SQL 注入风险：增强两个 where() 方法的 Javadoc 安全警告，明确标注"绕过类型安全机制"
- [P1] executeLimited() 未使用 InClauseBuilder：UpdateSpec 和 DeleteSpec 的 executeLimited() 改用 InClauseBuilder.in() 处理大型 IN 子句
- [P1] executeBatch() 长事务风险：增强 UpdateSpec 和 DeleteSpec 的 executeBatch() Javadoc，添加长事务风险警告和操作建议
- [P1] executeLimited() 并发时间窗口：增强 UpdateSpec 和 DeleteSpec 的 executeLimited() Javadoc，标注并发风险和悲观锁建议
- [P1] SoftDeleteHelper.getEnumConstant() 静默降级：改为抛出 IllegalStateException，移除调用方的 null 检查
- [P1] SoftDeleteHelper.isSoftDeleted() 静默返回：反射失败时改为抛出 MyJpaPlusException
- [P1] AbstractBulkOperationSpec 异常语义不一致：添加 Javadoc 明确说明 RuntimeException 直接重抛、checked exception 包装为 MyJpaPlusException 的行为

### 未修复问题
- [P1] ProjectionSpec.JoinGroup 命名冲突：重命名会破坏公开 API 的向后兼容性，建议在下一个大版本中处理
- [P1] ProjectionSpec.JoinGroup.ConditionNode 代码重复：需要大型重构统一条件节点类型，风险较高
- [P1] resolveJoins() 方法过长：需要引入策略模式或访问者模式，属于架构级重构
- [P1] SoftDeleteHelper.setAccessible() SecurityException 被吞掉（REL-12）：当前日志警告行为合理，字段名仍然被发现，后续访问失败时会由 isSoftDeleted() 抛出异常
- [P2] 所有 P2 问题：本轮聚焦 P0/P1 问题，P2 问题留待后续迭代

### 修改详情
#### 1. findAllStream() 安全版本
**文件**：src/main/java/com/zsubera/jpa/template/MyJpaTemplate.java
**修改内容**：
- 添加 `import java.util.function.Consumer`
- 增强原有 findAllStream(Class, QuerySpec) 的 Javadoc，添加"推荐使用安全版本"提示
- 新增 `findAllStream(Class<T>, QuerySpec<T>, Consumer<Stream<T>>)` 方法，自动在 try-with-resources 中管理 Stream 生命周期
- 包含完整的 null 校验（entityClass、spec、consumer）

#### 2. SoftDeleteJpaRepository.findById() 双重过滤修复
**文件**：src/main/java/com/zsubera/jpa/repository/SoftDeleteJpaRepository.java:138-151
**修改前**：
```java
Specification<T> spec = (root, query, cb) -> {
    String idFieldName = EntityClassResolver.resolveIdFieldName(domainClass);
    jakarta.persistence.criteria.Predicate idPredicate = cb.equal(root.get(idFieldName), id);
    jakarta.persistence.criteria.Predicate softDeleteFilter =
        mergeSoftDeleteFilter(null).toPredicate(root, query, cb);
    return cb.and(idPredicate, softDeleteFilter);
};
return findOne(spec);
```
**修改后**：
```java
Specification<T> spec = (root, query, cb) -> {
    String idFieldName = EntityClassResolver.resolveIdFieldName(domainClass);
    return cb.equal(root.get(idFieldName), id);
};
return findOne(spec);
```
**原因**：findOne() 内部会调用 mergeSoftDeleteFilter(spec)，之前手动添加软删除条件导致双重过滤

#### 3. LambdaUtils 缓存清理线程移除
**文件**：src/main/java/com/zsubera/jpa/util/LambdaUtils.java
**修改内容**：
- 移除 `ScheduledExecutorService` 相关 import
- 移除 CLEANUP_EXECUTOR 字段声明和静态初始化块
- shutdown() 方法改为空操作，保留方法签名以兼容 MyJpaPlusAutoConfiguration
**原因**：LRU LinkedHashMap 已自动驱逐过期条目，定时任务仅记录日志不执行清理，浪费线程资源

#### 4. where() 方法安全警告增强
**文件**：src/main/java/com/zsubera/jpa/spec/ConditionBuilder.java:543-583, 593-629
**修改内容**：将安全警告从"存在潜在的安全风险"改为"此方法绕过类型安全机制，存在潜在的SQL注入风险"，并添加推荐使用类型安全方法的建议

#### 5. executeLimited() 使用 InClauseBuilder
**文件**：src/main/java/com/zsubera/jpa/update/UpdateSpec.java:271, DeleteSpec.java:211
**修改前**：`update.where(updateRoot.get(idFieldName).in(ids));`
**修改后**：`update.where(InClauseBuilder.in(cb, updateRoot.get(idFieldName), ids));`
**原因**：避免超出 Oracle(1000)、SQL Server(2100) 等数据库的 IN 子句参数限制

#### 6. executeBatch() 长事务风险警告
**文件**：src/main/java/com/zsubera/jpa/template/MyJpaTemplate.java
**修改内容**：为 UpdateSpec 和 DeleteSpec 的 executeBatch() 方法添加长事务风险警告，建议使用较小的 batchSize 和监控数据库事务日志

#### 7. SoftDeleteHelper 枚举配置错误处理
**文件**：src/main/java/com/zsubera/jpa/update/SoftDeleteHelper.java:185-192
**修改前**：catch IllegalArgumentException 后 log.warn 并返回 null
**修改后**：catch IllegalArgumentException 后抛出 IllegalStateException，附带配置错误提示
**原因**：尽早发现 @SoftDelete(deletedValue) 配置错误，避免运行时生成错误 SQL

#### 8. SoftDeleteHelper.isSoftDeleted() 反射失败处理
**文件**：src/main/java/com/zsubera/jpa/update/SoftDeleteHelper.java:315-322
**修改前**：catch ReflectiveOperationException 后 log.warn 并返回 false
**修改后**：catch ReflectiveOperationException 后抛出 MyJpaPlusException，附带模块系统修复提示
**原因**：反射失败时静默返回 false 会导致已删除实体被错误判断为未删除

#### 9. AbstractBulkOperationSpec 异常语义文档
**文件**：src/main/java/com/zsubera/jpa/update/AbstractBulkOperationSpec.java:85-91
**修改内容**：添加异常处理语义说明，明确 RuntimeException 直接重抛、checked exception 包装为 MyJpaPlusException 的行为

### 测试结果
- 单元测试：全部通过（592 个测试，0 失败，0 错误）
- 集成测试：跳过（需要 Docker，已排除）
- Spotless 格式化：通过

---

## 轮次 8 - 优化记录
时间：2026-05-29

### 已修复问题
- [P1] P1-1 where() 方法绕过类型安全：为 ConditionBuilder.where(BiFunction)、ConditionBuilder.where(Function) 和 SubQuerySpec.where(Function) 方法添加 @Deprecated 注解，明确标记为不推荐使用，引导用户使用类型安全的 eq/ne/like 等方法
- [P1] P1-2 like()/notLike() 不转义通配符：添加 likeSafe() 和 notLikeSafe() 方法，自动转义用户输入中的 % 和 _ 通配符，提供安全的模糊查询替代方案
- [P1] P1-4 QuerySpec 无查询结果限制：在 QuerySpec 类的 Javadoc 中添加安全建议，明确说明直接使用 Repository.findAll(spec) 可能导致 OOM，推荐使用 MyJpaTemplate 进行查询
- [P1] P1-5 executeLimited() 竞态条件：增强 UpdateSpec.executeLimited() 和 DeleteSpec.executeLimited() 的文档警告，明确说明两步操作的竞态条件风险，并建议使用悲观锁或数据库原生 LIMIT 语法
- [P1] P1-6 ProjectionSpec.JoinGroup 条件节点重复：在 ProjectionSpec.JoinGroup.ConditionNode 的 Javadoc 中添加交叉引用注释，说明此类型与 spec.ConditionNode 结构相同但为避免循环依赖而独立定义，修改时必须同步

### 未修复问题
- [P1] P1-3 LambdaUtils 缓存使用 synchronizedMap：当前已使用 LRU LinkedHashMap 实现，虽然 synchronizedMap 在高并发下可能有锁竞争，但 LRU 策略对缓存命中率至关重要。改用 ConcurrentHashMap 会失去 LRU 特性，需要引入 Caffeine 依赖或自行实现分段锁 LRU，改动较大，建议在 v1.1.0 版本中处理
- [P2] 所有 P2 问题：本轮聚焦 P1 问题，P2 问题留待后续迭代

### 修改详情
#### 1. where() 方法 @Deprecated 标记
**文件**：src/main/java/com/zsubera/jpa/spec/ConditionBuilder.java:615-622, 654-661
**修改前**：
```java
/**
 * 添加原始 {@link Predicate} 条件，使用当前实体 {@link Path} 和 {@link CriteriaBuilder}。
 * ...
 * @see #eq(SFunction, Object)
 * @see #ne(SFunction, Object)
 */
@SuppressWarnings("unchecked")
default SELF where(BiFunction<Path<E>, CriteriaBuilder, Predicate> fn) {
```
**修改后**：
```java
/**
 * 添加原始 {@link Predicate} 条件，使用当前实体 {@link Path} 和 {@link CriteriaBuilder}。
 * ...
 * @deprecated 推荐使用类型安全的 {@link #eq(SFunction, Object)}、{@link #like(SFunction, String)} 等方法替代。
 *             此方法绕过类型安全机制，存在潜在的 SQL 注入风险。
 * @see #eq(SFunction, Object)
 * @see #ne(SFunction, Object)
 */
@Deprecated(since = "1.1.0", forRemoval = false)
@SuppressWarnings("unchecked")
default SELF where(BiFunction<Path<E>, CriteriaBuilder, Predicate> fn) {
```
**原因**：where() 方法允许直接操作 Path/Root 对象，使用字符串字面量访问字段，绕过了类型安全机制。添加 @Deprecated 注解明确标记为不推荐使用，引导用户优先使用类型安全的方法引用 API。同样修改了 where(Function) 和 SubQuerySpec.where(Function) 方法。

#### 2. likeSafe()/notLikeSafe() 方法
**文件**：src/main/java/com/zsubera/jpa/spec/ConditionBuilder.java:256-270, 299-313
**修改前**：无
**修改后**：
```java
/**
 * 添加带自动通配符转义的 LIKE 条件：{@code field LIKE value}。 值中的 {@code %} 或 {@code _} 字符会被转义，作为字面量处理。
 *
 * <p>
 * 此方法是 {@link #like(SFunction, String)} 的安全版本，适用于处理用户输入。
 *
 * @param field 实体属性的方法引用
 * @param value 要匹配的原始字符串值（通配符会被转义）
 * @return 当前构建器以支持链式调用
 * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
 * @see #like(SFunction, String)
 * @see #rawLike(SFunction, String)
 */
default SELF likeSafe(SFunction<E, ?> field, String value) {
    if (value == null) {
        throw new IllegalArgumentException("value must not be null");
    }
    conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field),
        escapeLikeWildcards(value), ConditionNode.Op.LIKE, PredicateHelper.LIKE_ESCAPE_CHAR));
    return self();
}

/**
 * 添加带自动通配符转义的 NOT LIKE 条件：{@code field NOT LIKE value}。 值中的 {@code %} 或 {@code _} 字符会被转义，作为字面量处理。
 *
 * <p>
 * 此方法是 {@link #notLike(SFunction, String)} 的安全版本，适用于处理用户输入。
 *
 * @param field 实体属性的方法引用
 * @param value 要匹配的原始字符串值（通配符会被转义）
 * @return 当前构建器以支持链式调用
 * @throws IllegalArgumentException 如果 {@code field} 或 {@code value} 为 null
 * @see #notLike(SFunction, String)
 */
default SELF notLikeSafe(SFunction<E, ?> field, String value) {
    if (value == null) {
        throw new IllegalArgumentException("value must not be null");
    }
    conditions().add(new ConditionNode.SimpleNode(LambdaUtils.getPropertyName(field),
        escapeLikeWildcards(value), ConditionNode.Op.NOT_LIKE, PredicateHelper.LIKE_ESCAPE_CHAR));
    return self();
}
```
**原因**：like()/notLike() 方法不自动转义用户输入中的通配符，可能导致 LIKE 注入。添加 likeSafe()/notLikeSafe() 方法作为安全版本，自动使用 PredicateHelper.escapeLikeWildcards() 转义通配符，适用于处理用户输入。

#### 3. QuerySpec 安全建议文档
**文件**：src/main/java/com/zsubera/jpa/spec/QuerySpec.java:20-42
**修改前**：
```java
/**
 * 基于 Lambda 的类型安全 JPA {@link Specification} 查询构建器。
 * ...
 * 示例：
 *
 * <pre>{@code
 * new QuerySpec<User>().eq(User::getStatus, "ACTIVE").or().like(User::getName, "%John%").like(User::getEmail, "%john%")
 *     .endOr().toSpecification();
 * }</pre>
 *
 * @param <T> 被查询的根实体类型
 */
```
**修改后**：
```java
/**
 * 基于 Lambda 的类型安全 JPA {@link Specification} 查询构建器。
 * ...
 * <strong>安全建议：</strong>直接使用 {@code Repository.findAll(spec)} 可能导致全表查询和内存溢出。
 * 推荐使用 {@link com.zsubera.jpa.template.MyJpaTemplate} 进行查询，它提供了内置的结果数量限制和分页支持。
 *
 * <pre>{@code
 * // 推荐：使用 MyJpaTemplate（自动限制结果数量）
 * MyJpaTemplate template = ...;
 * List<User> users = template.findAll(User.class, spec);
 *
 * // 或使用分页
 * Page<User> page = template.findPage(User.class, spec, pageable);
 *
 * // 不推荐：直接使用 Repository（可能导致 OOM）
 * // repository.findAll(spec); // 无结果数量限制
 * }</pre>
 *
 * 示例：
 *
 * <pre>{@code
 * new QuerySpec<User>().eq(User::getStatus, "ACTIVE").or().like(User::getName, "%John%").like(User::getEmail, "%john%")
 *     .endOr().toSpecification();
 * }</pre>
 *
 * @param <T> 被查询的根实体类型
 * @see com.zsubera.jpa.template.MyJpaTemplate#findAll(Class, QuerySpec)
 * @see com.zsubera.jpa.template.MyJpaTemplate#findPage(Class, QuerySpec, org.springframework.data.domain.Pageable)
 */
```
**原因**：QuerySpec 作为 Specification 的实现，本身不提供 maxResults 限制。直接使用 Repository.findAll(spec) 可能导致全表查询和内存溢出。在 Javadoc 中添加安全建议，明确推荐使用 MyJpaTemplate 进行查询。

#### 4. executeLimited() 竞态条件文档增强
**文件**：src/main/java/com/zsubera/jpa/update/UpdateSpec.java:200-214, src/main/java/com/zsubera/jpa/update/DeleteSpec.java:150-162
**修改前**：
```java
/**
 * <strong>并发风险警告：</strong>此方法存在并发时间窗口。在查询ID和执行更新之间，其他事务可能修改或删除记录。 对于高并发场景，建议：
 * <ul>
 * <li>使用 {@link #executeLimited(EntityManager, int, boolean)} 并设置 {@code pessimisticLock=true}</li>
 * <li>或者在应用层使用分布式锁</li>
 * <li>监控数据库锁等待情况</li>
 * </ul>
 */
```
**修改后**：
```java
/**
 * <strong>并发风险警告：</strong>此方法分两步执行（先查询 ID，再更新），在高并发场景下存在竞态条件。
 * 在查询ID和执行更新之间，其他事务可能修改或删除记录，导致数据不一致。对于高并发场景，建议：
 * <ul>
 * <li>使用 {@link #executeLimited(EntityManager, int, boolean)} 并设置 {@code pessimisticLock=true}</li>
 * <li>或者在应用层使用分布式锁</li>
 * <li>监控数据库锁等待情况</li>
 * <li>考虑使用数据库原生的 {@code UPDATE ... LIMIT} 语法（如果数据库支持）</li>
 * </ul>
 */
```
**原因**：executeLimited() 方法分两步执行（先查询 ID，再更新/删除），在高并发场景下存在竞态条件。增强文档警告，明确说明两步操作的风险，并建议使用悲观锁或数据库原生 LIMIT 语法。

#### 5. ProjectionSpec.JoinGroup.ConditionNode 交叉引用
**文件**：src/main/java/com/zsubera/jpa/projection/ProjectionSpec.java:540-603
**修改前**：
```java
/**
 * 条件节点定义，用于 JOIN ON 子句中的条件表达式。
 *
 * <p>
 * 支持的条件类型：
 * ...
 */
```
**修改后**：
```java
/**
 * 条件节点定义，用于 JOIN ON 子句中的条件表达式。
 *
 * <p>
 * <strong>注意：</strong>此类型与 {@link com.zsubera.jpa.spec.ConditionNode} 结构相同，
 * 但为避免 ProjectionSpec 对 spec 包的循环依赖而独立定义。
 * 修改此类型时，必须同步修改 spec.ConditionNode。
 *
 * <p>
 * 支持的条件类型：
 * ...
 * @see com.zsubera.jpa.spec.ConditionNode
 */
```
**原因**：ProjectionSpec.JoinGroup.ConditionNode 与 spec.ConditionNode 结构完全相同，但为避免循环依赖而独立定义。添加交叉引用注释，提醒开发者修改时必须同步两处定义。

### 测试结果
- 单元测试：全部通过（592 个测试，0 失败，0 错误）
- 集成测试：跳过（需要 Docker，已排除）
- Spotless 格式化：通过

---
## 轮次 9 - 优化记录
时间：2026-05-29

### 已修复问题
- [P1] P1-1 AbstractBulkOperationSpec.where() 缺少 @Deprecated 和安全警告：为 where(Function) 方法添加 @Deprecated 注解和安全警告 Javadoc，与 ConditionBuilder.where() 和 SubQuerySpec.where() 保持一致
- [P1] P1-2 ProjectionSpec.JoinGroup 缺少安全 LIKE 方法：添加 likeSafe() 和 notLikeSafe() 方法，自动转义用户输入中的通配符，新增 ConditionNode.LikeSafe 和 NotLikeSafe 记录，在 resolveJoins() 中添加对应的 instanceof 分支处理
- [P1] P1-5 ConditionBuilder LIKE 方法缺少 field null 校验：为 like()、rawLike()、likeSafe()、notLike()、notLikeSafe()、startsWith()、endsWith()、contains()、likeIgnoreCase() 共 9 个方法添加 field 参数的 null 校验
- [P1] P1-6 SubQuerySpec.eq()/ne() 缺少文档和 @Nullable 注解：添加 @Nullable 注解和 Javadoc，说明 null value 时自动转为 IS NULL/IS NOT NULL 的行为，与 ConditionBuilder.eq()/ne() 保持一致
- [P1] P1-7 SoftDeleteHelper Specification 缓存无驱逐策略：将 NOT_DELETED_SPEC_CACHE 和 DELETED_SPEC_CACHE 从 ConcurrentHashMap 改为 ConcurrentReferenceHashMap(weak keys)，与 FIELD_CACHE 保持一致，允许类加载器卸载后 GC 回收
- [P1] P1-9 MyJpaTemplate.findAllStream() 安全版本调用 deprecated 方法：提取 doFindStream() 私有方法，安全版本和 deprecated 版本均调用 doFindStream()，消除内部调用 deprecated 方法的循环依赖；deprecated 版本添加日志警告提示下一版本将抛出异常

### 未修复问题
- [P1] P1-3 ProjectionSpec.JoinGroup 与 spec.ConditionNode 代码重复：需要提取共享条件节点类型到公共包或重构 ProjectionSpec 内部 sealed interface 为复用主 ConditionNode，改动量约 200+ 行，涉及大量类型转换和 instanceof 检查，风险较高，建议在 v2.0.0 版本中处理
- [P1] P1-4 ProjectionSpec.resolveJoins() 过长 if-else instanceof 链：需要引入 Java 17+ switch pattern matching 或策略模式，属于架构级重构，建议在 v2.0.0 版本中处理
- [P1] P1-8 executeLimited() 两步执行竞态条件：此问题为 JPA 的固有限制，当前已提供悲观锁选项和详细文档警告，无法在不重构执行架构的情况下完全消除

### 修改详情
#### 1. AbstractBulkOperationSpec.where() @Deprecated 标记
**文件**：src/main/java/com/zsubera/jpa/update/AbstractBulkOperationSpec.java:582-595
**修改前**：
`java
/**
 * 添加自定义条件。
 *
 * @param condition 自定义条件函数，接收 Root 返回 Predicate
 * @return 当前构建器实例
 * @throws IllegalArgumentException 如果 condition 为 null
 */
public SELF where(Function<Root<T>, Predicate> condition) {
`
**修改后**：
`java
/**
 * 添加自定义条件。
 *
 * <p>
 * <strong>安全警告：此方法绕过类型安全机制，存在潜在的SQL注入风险！</strong>
 * <ul>
 * <li>请勿使用用户输入的字符串拼接字段名，如 {@code root.get(userInput)}，这可能导致 SQL 注入</li>
 * <li>建议优先使用类型安全的方法引用 API（如 {@code eq(Entity::getField, value)}）</li>
 * </ul>
 *
 * @param condition 自定义条件函数，接收 Root 返回 Predicate
 * @return 当前构建器实例
 * @throws IllegalArgumentException 如果 condition 为 null
 * @deprecated 推荐使用类型安全的 {@link #eq(SFunction, Object)}、{@link #like(SFunction, String)} 等方法替代。
 *             此方法绕过类型安全机制，存在潜在的 SQL 注入风险。
 */
@Deprecated(since = "1.1.0", forRemoval = false)
public SELF where(Function<Root<T>, Predicate> condition) {
`
**原因**：AbstractBulkOperationSpec.where() 是三个包含 where() 方法的类中唯一未标记 @Deprecated 的。添加 @Deprecated 和安全警告与 ConditionBuilder.where()、SubQuerySpec.where() 保持一致，IDE 会显示弃用警告引导用户迁移到类型安全 API。

#### 2. ProjectionSpec.JoinGroup likeSafe()/notLikeSafe() 方法
**文件**：src/main/java/com/zsubera/jpa/projection/ProjectionSpec.java
**修改内容**：
- 新增 ConditionNode.LikeSafe 和 ConditionNode.NotLikeSafe record 类型
- 新增 JoinGroup.likeSafe(SFunction, String) 方法，自动转义通配符
- 新增 JoinGroup.notLikeSafe(SFunction, String) 方法，自动转义通配符
- 在 esolveJoins() 中添加 LikeSafe 和 NotLikeSafe 的 instanceof 分支处理，使用 PredicateHelper.escapeLikeWildcards() 和 LIKE_ESCAPE_CHAR
**原因**：JoinGroup 的 like()/notLike() 不转义通配符，缺少安全替代方法。添加 likeSafe()/notLikeSafe() 与 ConditionBuilder 保持一致，消除 JOIN 条件中的 LIKE 注入风险。

#### 3. ConditionBuilder LIKE 方法 field null 校验
**文件**：src/main/java/com/zsubera/jpa/spec/ConditionBuilder.java（9 处）
**修改前**（以 like() 为例）：
`java
default SELF like(SFunction<E, ?> field, String value) {
    if (value == null) {
        throw new IllegalArgumentException("value must not be null");
    }
`
**修改后**：
`java
default SELF like(SFunction<E, ?> field, String value) {
    if (field == null) {
        throw new IllegalArgumentException("field must not be null");
    }
    if (value == null) {
        throw new IllegalArgumentException("value must not be null");
    }
`
**原因**：like()、rawLike()、likeSafe()、notLike()、notLikeSafe()、startsWith()、endsWith()、contains()、likeIgnoreCase() 共 9 个方法只校验了 value 为 null，未校验 field 为 null。虽然 LambdaUtils.getPropertyName() 内部会抛出异常，但错误消息不同（"SerializedLambda must not be null" vs "field must not be null"）。统一添加显式校验确保一致的错误消息和快速失败行为。

#### 4. SubQuerySpec.eq()/ne() @Nullable 和 Javadoc
**文件**：src/main/java/com/zsubera/jpa/spec/SubQuerySpec.java:120-135
**修改前**：
`java
/**
 * 添加子查询实体的等值条件。
 *
 * @param field 实体字段
 * @param value 比较值
 * @return 当前 SubQuerySpec 实例，支持链式调用
 */
public SubQuerySpec<S> eq(SFunction<S, ?> field, Object value) {
`
**修改后**：
`java
/**
 * 添加子查询实体的等值条件。value 为 null 时自动转为 IS NULL。
 *
 * @param field 实体字段
 * @param value 比较值，null 表示 IS NULL
 * @return 当前 SubQuerySpec 实例，支持链式调用
 * @throws IllegalArgumentException 如果 field 为 null
 */
public SubQuerySpec<S> eq(SFunction<S, ?> field, @Nullable Object value) {
`
**原因**：SubQuerySpec.eq()/ne() 缺少 @Nullable 注解和 Javadoc，与其他方法（gt/ge/lt/le）及 ConditionBuilder.eq() 的行为不一致。添加 @Nullable 注解和 Javadoc 说明 null value 时的行为。

#### 5. SoftDeleteHelper Specification 缓存改弱引用
**文件**：src/main/java/com/zsubera/jpa/update/SoftDeleteHelper.java:64-67
**修改前**：
`java
private static final ConcurrentMap<Class<?>, Specification<?>> NOT_DELETED_SPEC_CACHE = new ConcurrentHashMap<>();
private static final ConcurrentMap<Class<?>, Specification<?>> DELETED_SPEC_CACHE = new ConcurrentHashMap<>();
`
**修改后**：
`java
private static final ConcurrentMap<Class<?>, Specification<?>> NOT_DELETED_SPEC_CACHE =
    new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);
private static final ConcurrentMap<Class<?>, Specification<?>> DELETED_SPEC_CACHE =
    new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);
`
**原因**：NOT_DELETED_SPEC_CACHE 和 DELETED_SPEC_CACHE 使用强引用 ConcurrentHashMap，无驱逐策略。FIELD_CACHE 已正确使用弱引用键的 ConcurrentReferenceHashMap。改为弱引用后，三个缓存统一使用弱引用键，Class 对象在类加载器卸载后可被 GC 回收，防止热重部署环境中的内存泄漏。

#### 6. MyJpaTemplate.findAllStream() 重构
**文件**：src/main/java/com/zsubera/jpa/template/MyJpaTemplate.java
**修改内容**：
- 新增 doFindStream(Class, QuerySpec) 私有方法，包含实际查询逻辑
- indAllStream(Class, QuerySpec, Consumer) 安全版本改为调用 doFindStream() 而非 deprecated 版本
- indAllStream(Class, QuerySpec) deprecated 版本添加日志警告，改为调用 doFindStream()
- indAllStream(Class, QuerySpec, EntityGraphHelper) deprecated 版本添加日志警告
**原因**：安全版本 findAllStream(Class, QuerySpec, Consumer) 内部调用了 deprecated 的 findAllStream(Class, QuerySpec)，形成循环依赖。提取 doFindStream() 私有方法消除此问题，为下一版本移除不安全版本做好准备。deprecated 版本添加日志警告提示下一版本将抛出 UnsupportedOperationException。

### 测试结果
- 单元测试：全部通过（592 个测试，0 失败，0 错误）
- 集成测试：跳过（需要 Docker，已排除）
- Spotless 格式化：通过

---
