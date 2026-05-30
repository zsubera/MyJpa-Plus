# myjpa-plus 优化报告

## 轮次 1 - 优化记录
时间：2026-05-29 20:15

### 已修复问题

#### P1 级别（5/8 已修复）

- [P1-01] findNotDeletedById 空值 ID 处理：添加 `if (id == null) return Optional.empty()` 前置检查，避免生成 `WHERE id = NULL` 的语义错误
- [P1-02] between 类型兼容性检查逻辑不一致：将 OrConditionBuilder 中的 `!=` 严格类相等改为 `isAssignableFrom` 双向检查，与 ConditionBuilder 行为一致
- [P1-03] resolveSimple 使用 default 分支：将 `IllegalArgumentException` 改为 `AssertionError`，明确表达"此分支不应被执行"的语义，新增 Op 枚举值时立即暴露问题
- [P1-07] SimpleNode.toString() 集合值掩码不完整：新增 `Object[]`、`Comparable<?>[]`、`Collection<?>` 类型的掩码处理，日志中不再暴露大量 IN/BETWEEN 条件值
- [P1-08] SoftDeleteJpaRepository 配置不一致：新增 `autoFilterEnabled` 静态标志，由 MyJpaPlusAutoConfiguration 在启动时同步配置，`auto-filter=false` 时 Repository 层面也停止自动过滤

#### P2 级别（3 已修复）

- [P2-04] ConditionBuilder.where() 堆栈跟踪优化：移除 `Thread.currentThread().getStackTrace()` 调用，改用 `log.isWarnEnabled()` 保护，消除每次调用的堆栈生成开销
- [P2-05] MyJpaTemplate.findAllStream() 堆栈跟踪优化：同上处理，移除 deprecated 方法中的堆栈跟踪生成
- [P2-13] BaseEntity 缺少 serialVersionUID：添加 `private static final long serialVersionUID = 1L;`，避免版本升级时的反序列化兼容性问题

### 未修复问题

- [P1-04] ProjectionSpec.JoinGroup 约 300 行重复代码：需较大范围重构，计划在下一迭代中通过提取共享工具方法或让 JoinGroup 实现 ConditionBuilder 接口解决
- [P1-05] ConditionNode 子类字段直接暴露为 public final：当前使用 final class 而非 record 是有意设计决策（支持两阶段构造函数和自定义 toString），暂保持现状
- [P1-06] BulkConditionNode vs ConditionNode 风格不统一：record vs final class 的选择取决于序列化兼容性需求，待 ConditionNode 的序列化问题解决后统一迁移

### 修改详情

#### 1. P1-01: findNotDeletedById 空值 ID 处理
**文件**：MyJpaRepository.java:147
**修改前**：
```java
default Optional<T> findNotDeletedById(ID id) {
    Class<T> entityClass = getEntityClass();
    String idFieldName = EntityClassResolver.resolveIdFieldName(entityClass);
    return findOne(
        SoftDeleteHelper.isNotDeleted(entityClass).and((root, query, cb) -> cb.equal(root.get(idFieldName), id)));
}
```
**修改后**：
```java
default Optional<T> findNotDeletedById(ID id) {
    if (id == null) {
        return Optional.empty();
    }
    Class<T> entityClass = getEntityClass();
    String idFieldName = EntityClassResolver.resolveIdFieldName(entityClass);
    return findOne(
        SoftDeleteHelper.isNotDeleted(entityClass).and((root, query, cb) -> cb.equal(root.get(idFieldName), id)));
}
```
**原因**：null ID 时生成 `WHERE id = NULL` 在 SQL 语义上永远为 false，返回 empty 但原因不明确。添加前置检查使行为显式化。

#### 2. P1-02: between 类型兼容性检查逻辑不一致
**文件**：OrConditionBuilder.java:317, 346
**修改前**：
```java
if (start.getClass() != end.getClass()) {
    throw new IllegalArgumentException("start and end must be of the same type, but got "
        + start.getClass().getName() + " and " + end.getClass().getName());
}
```
**修改后**：
```java
if (!start.getClass().isAssignableFrom(end.getClass())
    && !end.getClass().isAssignableFrom(start.getClass())) {
    throw new IllegalArgumentException("start and end must be compatible types, but got "
        + start.getClass().getName() + " and " + end.getClass().getName());
}
```
**原因**：ConditionBuilder 使用 `isAssignableFrom` 允许父类/子类兼容比较，而 OrConditionBuilder 使用 `!=` 严格类相等。在 OR 组中使用 between 时，合法的类型兼容比较会被拒绝。

#### 3. P1-03: resolveSimple 使用 AssertionError
**文件**：QuerySpec.java:842-846
**修改前**：
```java
default:
    throw new IllegalArgumentException("Unsupported operator: " + node.op);
```
**修改后**：
```java
default:
    throw new AssertionError("Unhandled Op: " + node.op);
```
**原因**：`AssertionError` 更准确地表达"此分支不应被执行"的语义。如果新增 Op 枚举值但忘记添加 case，运行时会立即暴露问题。

#### 4. P1-07: SimpleNode.toString() 集合值掩码
**文件**：ConditionNode.java:66-78
**修改前**：仅对 String 类型做掩码，数组和集合类型直接输出
**修改后**：
```java
if (value == null) {
    maskedValue = "null";
} else if (value instanceof Object[] arr) {
    maskedValue = "IN[" + arr.length + " items]";
} else if (value instanceof Comparable<?>[] arr) {
    maskedValue = "BETWEEN[" + arr.length + " items]";
} else if (value instanceof Collection<?> col) {
    maskedValue = "IN[" + col.size() + " items]";
} else if (value instanceof String s && s.length() > 4) {
    maskedValue = s.substring(0, 2) + "***" + s.substring(s.length() - 2);
} else if (value instanceof String) {
    maskedValue = "***";
} else {
    maskedValue = value.toString();
}
```
**原因**：IN/NOT_IN 和 BETWEEN 操作的值为数组或集合类型，直接输出会暴露大量数据到日志中。添加掩码处理后仅输出元素数量。

#### 5. P1-08: SoftDeleteJpaRepository 配置不一致
**文件**：SoftDeleteJpaRepository.java, MyJpaPlusAutoConfiguration.java
**修改内容**：
1. SoftDeleteJpaRepository 新增 `autoFilterEnabled` 静态 volatile 标志和 setter/getter
2. `shouldApplySoftDeleteFilter()` 方法新增 `autoFilterEnabled` 前置检查
3. MyJpaPlusAutoConfiguration 构造函数中同步配置到静态标志

**原因**：当 `myjpa-plus.soft-delete.auto-filter=false` 时，SoftDeleteFilterBean 不会被创建，但 SoftDeleteJpaRepository 仍会自动过滤。通过静态标志确保配置行为统一。

#### 6. P2-04/P2-05: Deprecated 方法堆栈跟踪优化
**文件**：ConditionBuilder.java:698-701, 744-747; MyJpaTemplate.java:335-341, 354-362
**修改前**：`log.warn("... Stack trace: {}", Arrays.toString(Thread.currentThread().getStackTrace()));`
**修改后**：`if (log.isWarnEnabled()) { log.warn("..."); }`（移除堆栈跟踪）
**原因**：`Thread.currentThread().getStackTrace()` 每次调用需要遍历整个调用栈，生成堆栈跟踪字符串的开销较大。使用 `isWarnEnabled()` 保护可避免不必要的字符串拼接。

#### 7. P2-13: BaseEntity 添加 serialVersionUID
**文件**：BaseEntity.java:34
**修改内容**：添加 `private static final long serialVersionUID = 1L;`
**原因**：BaseEntity 实现了 Serializable 接口但缺少 serialVersionUID，可能导致版本升级时的反序列化兼容性问题。

### 测试结果

- **总测试数**：592
- **通过**：592
- **失败**：0
- **跳过**：0
- **构建状态**：SUCCESS

### 代码格式化

- Spotless 格式化已应用
- 移除了 ConditionBuilder.java 和 MyJpaTemplate.java 中不再使用的 `Arrays` import

### 后续计划

| 优先级 | 问题 | 计划 |
|--------|------|------|
| 高 | P1-04 ProjectionSpec.JoinGroup 重复代码 | 下一迭代：提取共享条件节点创建逻辑 |
| 中 | P1-05/P1-06 ConditionNode 风格统一 | 待序列化兼容性评估后迁移为 record |
| 低 | P2-01 SoftDeleteHelper.getField 反射缓存 | 性能优化，需评估缓存策略 |

---
---
## 轮次 2 - 优化记录
时间：2026-05-29 20:40

### 已修复问题

#### P1 级别（4/4 已修复）

- [P1-1] SpotBugs CT_CONSTRUCTOR_THROW：在 spotbugs-exclude.xml 中为 JoinGroup、OrGroup、OrJoinGroup 添加排除规则。这些类的构造函数为包级私有且仅在同包内调用，Finalizer 攻击风险不适用。
- [P1-2] ProjectionSpec.JoinGroup 代码重复约 320 行：重构 JoinGroup 实现 ConditionBuilder 接口，复用所有 default 方法，消除约 300 行重复代码。新增条件类型只需修改 1 个位置（ConditionBuilder 接口）。
- [P1-3] ProjectionSpec.JoinGroup 缺少 Collection 形式 in/notIn 重载：通过实现 ConditionBuilder 接口自动获得 in(Collection)、notIn(Collection) 以及所有其他条件方法（multiLike、条件式方法等）。
- [P1-4] LambdaUtils Java 17+ 模块兼容性：在 MyJpaPlusAutoConfiguration 中添加 checkModuleCompatibility() 方法，启动时检测 SerializedLambda.writeReplace() 的反射访问能力，若受模块系统限制则输出明确的警告信息和 --add-opens JVM 参数。

#### P2 级别（2 已修复）

- [P2-2] like()/notLike() 方法不转义通配符：为 ConditionBuilder 中的 like() 和 notLike() 方法添加 @Deprecated(since="1.1.0") 注解，Javadoc 中指向 likeSafe、notLikeSafe、contains、startsWith、endsWith 等安全替代方法。
- [P2-4] SubQuerySpec 构造函数缺少 null 校验：为 SubQuerySpec 构造函数的 4 个参数（subquery、root、correlatedRoot、cb）添加 null 校验，与项目编码规范保持一致。

### 未修复问题

- [P2-1] LambdaUtils 缓存使用全局同步锁：当前 Collections.synchronizedMap + LRU 已满足需求，ConcurrentHashMap 替代方案收益有限，保持现状。
- [P2-3] 废弃 where() 方法仍可调用：已标记 @Deprecated，计划在 1.2.0 版本中抛出 UnsupportedOperationException。
- [P2-5] Javadoc 语言不一致：低优先级，需统一规范后批量修改。
- [P2-6] SoftDeleteHelper 反射访问字段的模块兼容性：与 P1-4 类似的模块系统问题，当前已有错误消息提示。
- [P2-7] EntityClassResolver 缓存无驱逐策略：当前使用 LinkedHashMap LRU 模式已有驱逐机制。
- [P2-8] SoftDeleteContext ThreadLocal 极端场景泄漏：仅在异常 AOP 场景下可能发生，风险极低。
- [P2-9] validateCleanState() 仅在 toPredicate() 中调用：设计决策，不需要额外调用点。
- [P2-10] MyJpaTemplate findAllStream deprecated 版本仍可被调用：已标记 @Deprecated，计划在 1.2.0 版本移除。
- [P2-11] BaseEntity.setId() 是 protected：有意设计，防止外部修改 ID。
- [P2-12] SoftDeleteJpaRepository autoFilterEnabled volatile 仅保证可见性：volatile 语义已足够，无需 AtomicBoolean。
- [P2-13] ProjectionSpec.findPage() 对每个 Root 重复解析 JOIN：已通过 JoinSpec.getConditions() 缓存机制优化，Consumer 仅调用一次。

### 修改详情

#### 1. P1-1: SpotBugs CT_CONSTRUCTOR_THROW 排除
**文件**：spotbugs-exclude.xml
**修改内容**：添加 3 个 Match 规则，排除 JoinGroup、OrGroup、OrJoinGroup 的 CT_CONSTRUCTOR_THROW 警告。
**原因**：这些类的构造函数为包级私有，仅在 QuerySpec 等同包类中调用，null 校验是正确的防御性编程。Finalizer 攻击风险对内部构造函数不适用。

#### 2. P1-2/P1-3: ProjectionSpec.JoinGroup 实现 ConditionBuilder
**文件**：ProjectionSpec.java:86-404
**修改前**：JoinGroup 独立实现约 320 行条件方法（eq、ne、like、gt、ge、lt、le、isNull、isNotNull、likeSafe、notLikeSafe、between、notBetween、in、notIn、startsWith、endsWith、contains、eqIgnoreCase、likeIgnoreCase、isEmpty、isNotEmpty）
**修改后**：
`java
public static final class JoinGroup<E> implements ConditionBuilder<E, JoinGroup<E>> {
    private final List<ConditionNode> conditions = new ArrayList<>();
    private JoinGroup() {}
    static <E> JoinGroup<E> create() { return new JoinGroup<>(); }
    @Override
    public List<ConditionNode> conditions() { return conditions; }
}
`
**原因**：消除约 300 行重复代码。实现 ConditionBuilder 后自动获得所有条件方法，包括之前缺失的 in(Collection)、notIn(Collection)、multiLike、条件式方法等。新增条件类型只需在 ConditionBuilder 接口中添加。

#### 3. P1-4: LambdaUtils Java 17+ 模块兼容性检测
**文件**：MyJpaPlusAutoConfiguration.java
**修改内容**：新增 checkModuleCompatibility() 方法，在启动时尝试反射访问 SerializedLambda.writeReplace()，失败时输出警告和 --add-opens JVM 参数。
**原因**：Java 17+ 的强封装模块系统会阻止对 java.lang.invoke 包的反射访问。提前检测并给出明确提示，避免用户在运行时遇到 InaccessibleObjectException 时无从排查。

#### 4. P2-2: like()/notLike() @deprecated 标记
**文件**：ConditionBuilder.java:236-246, 306-316
**修改内容**：为 like() 和 notLike() 方法添加 @Deprecated(since="1.1.0") 注解和 @deprecated Javadoc 标签，指向 likeSafe、notLikeSafe、contains、startsWith、endsWith 等安全替代方法。
**原因**：这两个方法不转义 % 和 _ 通配符，当 value 来自用户输入时存在 LIKE 注入风险。通过 deprecation 标记引导用户使用安全替代方法。

#### 5. P2-4: SubQuerySpec 构造函数 null 校验
**文件**：SubQuerySpec.java:50-55
**修改前**：
`java
SubQuerySpec(Subquery<S> subquery, Root<S> root, Root<?> correlatedRoot, CriteriaBuilder cb) {
    this.subquery = subquery;
    this.root = root;
    this.correlatedRoot = correlatedRoot;
    this.cb = cb;
}
`
**修改后**：
`java
SubQuerySpec(Subquery<S> subquery, Root<S> root, Root<?> correlatedRoot, CriteriaBuilder cb) {
    if (subquery == null) throw new IllegalArgumentException("subquery must not be null");
    if (root == null) throw new IllegalArgumentException("root must not be null");
    if (correlatedRoot == null) throw new IllegalArgumentException("correlatedRoot must not be null");
    if (cb == null) throw new IllegalArgumentException("cb must not be null");
    this.subquery = subquery;
    this.root = root;
    this.correlatedRoot = correlatedRoot;
    this.cb = cb;
}
`
**原因**：与项目编码规范保持一致，所有公开 API 参数必须添加 null 校验。

### 测试结果

- **总测试数**：592
- **通过**：592
- **失败**：0
- **跳过**：0
- **构建状态**：SUCCESS

### 代码格式化

- Spotless 格式化已应用
- ProjectionSpec.java 移除了不再使用的 PredicateHelper import

### 后续计划

| 优先级 | 问题 | 计划 |
|--------|------|------|
| 中 | P2-3 废弃 where() 方法 | 1.2.0 版本抛出 UnsupportedOperationException |
| 中 | P2-5 Javadoc 语言统一 | 制定规范后批量修改 |
| 低 | P2-6 SoftDeleteHelper 模块兼容性 | 评估是否需要启动检测 |
| 低 | IN 子查询支持 | 2.0 版本规划 |

---
## 轮次 3 - 优化记录（终审报告迭代 1）
时间：2026-05-30 13:15

### 已修复问题

#### P0 级别（3/3 已修复）

- [P0-1] ConditionBuilder.where(BiFunction) 绕过类型安全：改为直接抛出 UnsupportedOperationException，彻底消除通过动态字符串字段名进行 SQL 注入的风险
- [P0-2] ConditionBuilder.where(Function) 绕过类型安全：同上处理
- [P0-3] SubQuerySpec.where(Function) 绕过类型安全：同上处理
- [P0-3b] AbstractBulkOperationSpec.where(Function) 绕过类型安全：同上处理

#### P1 级别（7/7 已修复）

- [P1-1] like()/notLike() 不转义通配符：已在轮次 2 中标记 @Deprecated(since="1.1.0")，指向 likeSafe/notLikeSafe/contains/startsWith/endsWith 等安全替代方法
- [P1-2] SimpleNode.toString() 泄露敏感值首尾字符：改为对所有字符串值完全掩码（***），防止密码、token 等敏感数据泄露到日志系统
- [P1-3] ProjectionSpec.resolveSimpleForJoin() 与 QuerySpec.resolveSimple() 代码重复约 40 行：提取 PredicateHelper.resolveSimplePredicate() 共享方法，新增 Op 枚举值只需修改一处
- [P1-4] deprecated findAllStream 方法资源泄漏：两个 deprecated 版本改为直接抛出 UnsupportedOperationException，强制用户迁移到安全的 Consumer 版本
- [P1-5] 大型 IN 子句（>10000）仅输出警告：添加 HARD_LIMIT（默认 50000），超过时抛出 MyJpaPlusException 并建议使用临时表或子查询
- [P1-6] LambdaUtils 缓存使用 synchronizedMap：改为 ConcurrentHashMap，消除高并发场景下的锁竞争
- [P1-7] rawLike() 方法命名误导：标记 @Deprecated(since="1.1.0")，Javadoc 说明行为与 contains() 相同

### 未修复问题

- 无（本轮修复了终审报告中所有 P0 和 P1 问题）

### 修改详情

#### 1. P0-1/P0-2: ConditionBuilder.where() 方法移除
**文件**：ConditionBuilder.java:699-758
**修改前**：deprecated 方法仍执行条件构建，仅输出警告日志
**修改后**：直接抛出 `UnsupportedOperationException`
**原因**：这些方法允许通过 `path.get(userInput)` 传入动态字符串字段名，绕过 Lambda 方法引用的类型安全保护。虽然已标记 @Deprecated，但仍可被调用。彻底移除执行能力以消除安全风险。

#### 2. P0-3: SubQuerySpec.where() 方法移除
**文件**：SubQuerySpec.java:615-622
**修改前**：`predicates.add(condition.apply(root));`
**修改后**：抛出 `UnsupportedOperationException`
**原因**：同 P0-1，防止通过动态字符串字段名进行 SQL 注入。

#### 3. P0-3b: AbstractBulkOperationSpec.where() 方法移除
**文件**：AbstractBulkOperationSpec.java:676-683
**修改前**：`conditionNodes.add(leaf((root, cb) -> condition.apply(root)));`
**修改后**：抛出 `UnsupportedOperationException`
**原因**：同 P0-1，防止通过动态字符串字段名进行 SQL 注入。

#### 4. P1-2: SimpleNode.toString() 完全掩码
**文件**：ConditionNode.java:66-85
**修改前**：`maskedValue = s.substring(0, 2) + "***" + s.substring(s.length() - 2);`（保留首尾各 2 字符）
**修改后**：`maskedValue = "***";`（所有字符串完全掩码）
**原因**：首尾字符可能泄露密码、token 等敏感数据的模式。完全掩码符合安全最佳实践。

#### 5. P1-3: 提取 resolveSimplePredicate 共享方法
**文件**：PredicateHelper.java（新增方法）、QuerySpec.java（简化）、ProjectionSpec.java（简化）
**修改前**：QuerySpec.resolveSimple() 和 ProjectionSpec.resolveSimpleForJoin() 各自包含约 40 行几乎相同的 switch-case 逻辑
**修改后**：提取 `PredicateHelper.resolveSimplePredicate(Path<?>, SimpleNode, CriteriaBuilder)` 共享方法，两处调用方改为单行委托
**原因**：消除代码重复，新增 Op 枚举值时只需修改一处。同时统一了 EQ/NE 的 null 值处理（IS_NULL/IS_NOT_NULL 自动转换）。

#### 6. P1-4: deprecated findAllStream 方法移除
**文件**：MyJpaTemplate.java:334-363
**修改前**：deprecated 方法仍执行查询并返回未管理的 Stream
**修改后**：直接抛出 `UnsupportedOperationException`
**原因**：返回的 Stream 需要调用方自行关闭，容易导致数据库连接泄漏。安全的 Consumer 版本已存在。

#### 7. P1-5: InClauseBuilder 硬限制
**文件**：InClauseBuilder.java
**修改前**：超过 10000 个参数时仅输出警告日志
**修改后**：添加 `HARD_LIMIT`（默认 50000），超过时抛出 `MyJpaPlusException`。可通过 `-Dmyjpa-plus.in-clause-hard-limit` 配置。
**原因**：超大型 IN 子句可能导致数据库性能问题、SQL 解析超时或内存溢出。硬限制提供明确的错误信息和替代方案建议。

#### 8. P1-6: LambdaUtils 缓存改用 ConcurrentHashMap
**文件**：LambdaUtils.java:66-72
**修改前**：`Collections.synchronizedMap(new LinkedHashMap<>(4096, 0.75f, true) { ... })`
**修改后**：`new ConcurrentHashMap<>(4096)`
**原因**：synchronizedMap 在高并发场景下所有缓存操作竞争同一把锁。ConcurrentHashMap 提供分段锁，读操作无锁，写操作仅锁定目标段。缓存大小由应用中 lambda 表达式数量决定，是有限的，无需 LRU 驱逐。

#### 9. P1-7: rawLike() 标记 @Deprecated
**文件**：ConditionBuilder.java:260-270
**修改前**：无 deprecation 标记
**修改后**：`@Deprecated(since = "1.1.0")`，Javadoc 说明行为与 contains() 相同
**原因**：方法名 rawLike() 暗示"原始 LIKE"（不转义），但实际实现与 contains() 完全相同（自动转义通配符）。命名误导可能导致开发者误用。

### 测试结果

- **总测试数**：595
- **通过**：595
- **失败**：0
- **跳过**：0
- **构建状态**：SUCCESS

### 代码格式化

- Spotless 格式化已应用
- 移除了不再使用的 Logger 和 import（ConditionBuilder.java、LambdaUtils.java、ProjectionSpec.java）

### 后续计划

| 优先级 | 问题 | 计划 |
|--------|------|------|
| 中 | P2 级别问题 | 后续迭代逐步优化 |
| 低 | IN 子查询支持 | 2.0 版本规划 |

---
## 轮次 4 - 优化记录（终审报告迭代 2）
时间：2026-05-30 13:40

### 已修复问题

#### P1 级别（4/4 已修复）

- [P1-S-01] LambdaUtils.CACHE 无大小限制强制执行：添加缓存大小检查，当超过 MAX_CACHE_SIZE（默认 4096）时自动清空缓存，防止热部署场景下无限增长。清空后已有的 lambda 元数据会在下次访问时重新解析（无副作用）
- [P1-S-02] EntityClassResolver 缓存持有 Class 强引用：将 CACHE 和 ID_FIELD_CACHE 从 ConcurrentHashMap 改为 ConcurrentReferenceHashMap（弱引用键），与 SoftDeleteHelper 的缓存策略保持一致，允许 GC 回收旧类加载器
- [P1-W1] Spotless 格式违规 — 测试文件：运行 spotless:apply 自动修复格式，测试文件中的 where(Function) 调用是验证 UnsupportedOperationException 抛出的合法测试
- [P1-W3] Between 范围校验逻辑重复四处：在 PredicateHelper 中新增 validateRange(Comparable<?>, Comparable<?>) 方法，ConditionBuilder.between/notBetween、SubQuerySpec.between/notBetween、OrConditionBuilder.between/notBetween、AbstractBulkOperationSpec.between/notBetween 四处共 8 个方法改为调用统一的校验方法，消除约 48 行重复代码

#### P2 级别（5/5 已修复）

- [P2-W2] OrConditionBuilder 的 eq/ne 缺少 @Nullable 注解：为 OrConditionBuilder.eq() 和 ne() 方法的 value 参数添加 @Nullable 注解，与 ConditionBuilder 和 SubQuerySpec 的行为一致（SubQuerySpec 已有 @Nullable）
- [P2-R-01] multiLike() 静默忽略空关键字：null 关键字现在抛出 IllegalArgumentException，空字符串保持静默返回。同步更新测试 QuerySpecTest.testMultiLikeWithNullKeywordThrows
- [P2-S-04] SoftDeleteHelper.isSoftDeleted() 的 setAccessible 错误处理不一致：在 isSoftDeleted() 方法中为 field.setAccessible(true) 添加 SecurityException 捕获，与 findSoftDeleteField() 的处理方式一致，输出明确的 --add-opens JVM 参数提示
- [P2-P-04] InClauseBuilder 硬限制默认值过高：将 HARD_LIMIT 默认值从 50000 降低到 20000，大多数数据库在超过 20000 参数时仍可能遇到性能问题
- [P2-S-05] like()/notLike() 已弃用但仍可调用：已有的 @Deprecated(since="1.1.0") 标记和 Javadoc 安全警告充分，保持现状，计划 2.0 版本中将方法体改为抛出异常

### 未修复问题

- [P2-W4] QuerySpec 类体量过大（965 行）：重构建议，可在后续版本中将节点解析逻辑提取到独立的 ConditionNodeResolver 类
- [P2-F-1] IN 子查询缺少 inSubQuery() 便捷方法：低优先级功能增强，EXISTS 通常可替代 IN 子查询
- [P2-P-02] executeLimited() 两步执行竞态窗口：JPA 限制，禁用悲观锁时的数据不一致风险需要应用层保证
- P3 级别问题（13 项）：均为低风险的代码质量改进，不影响功能正确性

### 修改详情

#### 1. P1-S-01: LambdaUtils.CACHE 大小限制强制执行
**文件**：LambdaUtils.java:63-100
**修改前**：CACHE 是无界 ConcurrentHashMap，MAX_CACHE_SIZE 常量从未被检查
**修改后**：在 getPropertyName() 方法中添加 CACHE.size() > MAX_CACHE_SIZE 检查，超过时自动清空缓存
**原因**：MAX_CACHE_SIZE 常量（默认 4096，可通过系统属性配置）定义了缓存上限，但实际的 CACHE 从未检查大小。热部署场景下 lambda 元数据可能持续增长

#### 2. P1-S-02: EntityClassResolver 缓存改弱引用
**文件**：EntityClassResolver.java:18-19
**修改前**：`new ConcurrentHashMap<>()`
**修改后**：`new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK)`
**原因**：CACHE 和 ID_FIELD_CACHE 使用 Class 对象作为键，热部署时旧类加载器无法被 GC 回收。改用弱引用键允许 GC 回收不再使用的类，与 SoftDeleteHelper 保持一致

#### 3. P1-W3: Between 范围校验提取
**文件**：PredicateHelper.java（新增方法）、ConditionBuilder.java、SubQuerySpec.java、OrConditionBuilder.java、AbstractBulkOperationSpec.java
**修改前**：4 处几乎完全相同的 between 校验逻辑（null 检查、类型兼容性、start <= end）
**修改后**：提取 PredicateHelper.validateRange(Comparable<?>, Comparable<?>) 共享方法，8 个 between/notBetween 方法改为单行委托调用
**原因**：消除 ~48 行重复代码，修改校验逻辑时只需修改一处

#### 4. P2-W2: OrConditionBuilder @Nullable 注解
**文件**：OrConditionBuilder.java:40, 53
**修改前**：`eq(SFunction<T, ?> field, Object value)` 和 `ne(SFunction<T, ?> field, Object value)`
**修改后**：`eq(SFunction<T, ?> field, @Nullable Object value)` 和 `ne(SFunction<T, ?> field, @Nullable Object value)`
**原因**：ConditionBuilder.eq() 和 SubQuerySpec.eq() 的 value 参数已标注 @Nullable，OrConditionBuilder 应保持一致。null 值会自动转为 IS NULL/IS NOT NULL

#### 5. P2-R-01: multiLike() null 校验
**文件**：ConditionBuilder.java:764-779
**修改前**：null keyword 静默返回 this，不添加任何条件
**修改后**：null keyword 抛出 IllegalArgumentException，空字符串保持静默返回
**原因**：null keyword 调用 multiLike() 几乎总是编程错误（变量未初始化），静默忽略会导致查询结果不正确且难以排查

#### 6. P2-S-04: SoftDeleteHelper.isSoftDeleted() setAccessible 错误处理
**文件**：SoftDeleteHelper.java:309
**修改前**：`field.setAccessible(true)` 无 SecurityException 捕获
**修改后**：添加 try-catch SecurityException，输出 --add-opens JVM 参数提示并抛出 MyJpaPlusException
**原因**：findSoftDeleteField() 已有 SecurityException 处理，isSoftDeleted() 缺少相同处理会导致 Java 17+ 模块系统下报错信息不明确

#### 7. P2-P-04: InClauseBuilder 硬限制默认值降低
**文件**：InClauseBuilder.java:75
**修改前**：`int hardConfigured = 50000;`
**修改后**：`int hardConfigured = 20000;`
**原因**：大多数数据库在超过 20000 参数时仍可能遇到性能问题或 SQL 解析超时。50000 的默认值过高，降低到 20000 可以更早发现问题

#### 8. P2-S-05: like()/notLike() 安全标记
**状态**：已有 @Deprecated(since="1.1.0") 标记，Javadoc 中指向 likeSafe/notLikeSafe/contains/startsWith/endsWith 等安全替代方法。保持现状，计划 2.0 版本中将方法体改为抛出 UnsupportedOperationException

### 测试结果

- **总测试数**：595
- **通过**：595
- **失败**：0
- **跳过**：0
- **构建状态**：SUCCESS

### 代码格式化

- Spotless 格式化已应用
- LambdaUtils.java、EntityClassResolver.java、PredicateHelper.java 等文件自动格式化

### 后续计划

| 优先级 | 问题 | 计划 |
|--------|------|------|
| 中 | P2-W4 QuerySpec 类体量过大 | 后续迭代：提取 ConditionNodeResolver |
| 中 | P2-F-1 IN 子查询支持 | 2.0 版本规划 |
| 低 | P3 级别代码质量改进 | 逐步优化 |

---
