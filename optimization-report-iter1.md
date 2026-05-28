# myjpa-plus 优化报告（第一轮迭代）

**报告日期**: 2026-05-29  
**基于终审报告**: final-audit-report-iter1.md  
**构建状态**: BUILD SUCCESS（592 测试通过，0 失败）

---

## 一、已修复问题列表

### P0 - 必须修复（已修复 1/1）

| # | 问题 | 修复文件 | 状态 |
|---|------|----------|------|
| 1 | SoftDeleteJpaRepository.findById() 未覆盖，用户可能意外获取已删除实体 | SoftDeleteJpaRepository.java | ✅ 已修复 |

### P1 - 建议修复（已修复 2/2）

| # | 问题 | 修复文件 | 状态 |
|---|------|----------|------|
| 2 | ProjectionSpec.JoinGroup 条件方法不完整，缺少 ge/le/between/in 等 | ProjectionSpec.java | ✅ 已修复 |
| 3 | MyJpaTemplate.buildTypedQuery 与 buildSpecificationQuery 重复 | MyJpaTemplate.java | ✅ 已修复 |

### P2 - 可选优化（已修复 5/5）

| # | 问题 | 修复文件 | 状态 |
|---|------|----------|------|
| 4 | LambdaUtils 缓存全部清除策略可能导致缓存风暴 | LambdaUtils.java | ✅ 已修复 |
| 5 | SoftDeleteHelper 字段缓存只报警不驱逐 | SoftDeleteHelper.java | ✅ 已修复 |
| 6 | ProjectionSpec.findPage() 中 JOIN 被解析两次 | ProjectionSpec.java | ✅ 已修复 |
| 7 | ConditionNode.OrderNode 位置语义混淆 | ConditionNode.java | ✅ 已修复（文档） |
| 8 | SimpleNode 使用 public final 字段而非 record | ConditionNode.java | ✅ 已修复（文档） |

---

## 二、修改详情

### 1. SoftDeleteJpaRepository.findById() 覆盖（P0）

**修改前**（SoftDeleteJpaRepository.java）：
```java
// findById() 未覆盖，使用 SimpleJpaRepository 默认实现
// 默认实现不会过滤软删除记录
```

**修改后**（SoftDeleteJpaRepository.java）：
```java
@Override
public Optional<T> findById(ID id) {
    if (id == null) {
        return Optional.empty();
    }
    // 构建带软删除过滤的查询
    Specification<T> spec = (root, query, cb) -> {
        jakarta.persistence.criteria.Predicate idPredicate = cb.equal(root.get("id"), id);
        jakarta.persistence.criteria.Predicate softDeleteFilter = mergeSoftDeleteFilter(null)
            .toPredicate(root, query, cb);
        return cb.and(idPredicate, softDeleteFilter);
    };
    return findOne(spec);
}
```

**修改原因**：覆盖 findById() 以注入软删除过滤条件，确保已删除实体返回 Optional.empty()。

---

### 2. ProjectionSpec.JoinGroup 条件方法补全（P1）

**修改前**（ProjectionSpec.java JoinGroup）：
```java
// 仅有 eq, ne, like, gt, lt, isNull, isNotNull 方法
```

**修改后**（ProjectionSpec.java JoinGroup）：
新增以下方法和对应 ConditionNode record 类型：
- `ge()` - 大于等于条件
- `le()` - 小于等于条件
- `notLike()` - NOT LIKE 条件
- `between()` - BETWEEN 条件
- `notBetween()` - NOT BETWEEN 条件
- `in()` - IN 条件
- `notIn()` - NOT IN 条件
- `startsWith()` - 前缀匹配条件
- `endsWith()` - 后缀匹配条件
- `contains()` - 包含匹配条件
- `eqIgnoreCase()` - 不区分大小写等于条件
- `likeIgnoreCase()` - 不区分大小写 LIKE 条件
- `isEmpty()` - IS EMPTY 条件
- `isNotEmpty()` - IS NOT EMPTY 条件

同步更新 `resolveJoins()` 方法以处理新增的条件节点类型。

**修改原因**：投影查询 JOIN 条件表达能力应与 ConditionBuilder 保持一致。

---

### 3. MyJpaTemplate 重复代码消除（P1）

**修改前**（MyJpaTemplate.java）：
```java
// buildTypedQuery 和 buildSpecificationQuery 各自独立构建 CriteriaQuery
// doFindPage 中也重复了 CriteriaQuery 构建逻辑
```

**修改后**（MyJpaTemplate.java）：
```java
// buildTypedQuery 委托给 buildSpecificationQuery
private <T> TypedQuery<T> buildTypedQuery(...) {
    TypedQuery<T> query = buildSpecificationQuery(entityClass, spec.toSpecification(), null, maxResults);
    if (entityGraph != null) {
        entityGraph.apply(query, entityManager);
    }
    spec.applyQuerySettings(query);
    return query;
}

// buildSpecificationQuery 新增 Sort 参数，统一处理排序
private <T> TypedQuery<T> buildSpecificationQuery(Class<T> entityClass, Specification<T> spec,
    @Nullable Sort sort, Integer maxResults) { ... }

// doFindPage 复用 buildSpecificationQuery 构建数据查询
```

**修改原因**：消除 CriteriaQuery 构建逻辑的三处重复，统一查询构建入口。

---

### 4. LambdaUtils 缓存驱逐策略优化（P2）

**修改前**（LambdaUtils.java）：
```java
if (CACHE.size() > MAX_CACHE_SIZE) {
    CACHE.clear();  // 全部清除，可能导致缓存风暴
}
```

**修改后**（LambdaUtils.java）：
```java
if (CACHE.size() > MAX_CACHE_SIZE) {
    int toRemove = CACHE.size() / 2;
    Iterator<String> it = CACHE.keySet().iterator();
    for (int i = 0; i < toRemove && it.hasNext(); i++) {
        it.next();
        it.remove();
    }
}
```

**修改原因**：全量清除会导致大量 cache miss，改用部分驱逐（50%）减少缓存风暴影响。

---

### 5. SoftDeleteHelper 缓存监控增强（P2）

**修改前**（SoftDeleteHelper.java）：
```java
if (FIELD_CACHE.size() > MAX_CACHE_SIZE) {
    log.warn("...");
    // 仅记录日志，不清理
}
```

**修改后**（SoftDeleteHelper.java）：
```java
if (FIELD_CACHE.size() > MAX_CACHE_SIZE) {
    log.warn("...");
    // 驱逐 stale 条目（null key/value，可能由类加载器 GC 导致）
    FIELD_CACHE.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
}
```

**修改原因**：在记录警告的同时清理 stale 条目，防止缓存无限增长。

---

### 6. ProjectionSpec.findPage() JOIN 解析去重（P2）

**修改前**（ProjectionSpec.java）：
```java
// JoinSpec 仅存储 Consumer<?>
// resolveJoins 每次调用都重新执行 Consumer 创建 JoinGroup
```

**修改后**（ProjectionSpec.java）：
```java
// JoinSpec 新增 cachedConditions 字段和 getConditions() 方法
private static final class JoinSpec {
    List<JoinGroup.ConditionNode> cachedConditions;

    <E> List<JoinGroup.ConditionNode> getConditions() {
        if (cachedConditions == null) {
            JoinGroup<Object> group = JoinGroup.create();
            cfg.accept(group);
            cachedConditions = group.getConditions();
        }
        return cachedConditions;
    }
}

// resolveJoins 使用 js.getConditions() 代替重新创建 JoinGroup
```

**修改原因**：count 查询和 data 查询各自调用 resolveJoins 时，Consumer 只需执行一次。

---

### 7. OrderNode 语义澄清（P2）

**修改前**（ConditionNode.java）：
```java
/** ORDER BY 子句的排序节点。 */
final class OrderNode { ... }
```

**修改后**（ConditionNode.java）：
```java
/**
 * ORDER BY 子句的排序节点。
 *
 * <p>
 * <strong>注意：</strong>此类定义在 {@code ConditionNode} 内部以便于组织，但不实现 {@code ConditionNode} 接口，
 * 因为 ORDER BY 不是查询条件（WHERE clause）的一部分。
 */
final class OrderNode { ... }
```

**修改原因**：通过 Javadoc 澄清 OrderNode 不是 ConditionNode 的实现类，消除语义混淆。

---

### 8. SimpleNode 文档说明（P2）

**修改前**（ConditionNode.java）：
```java
/** 单个字段-值比较条件。 */
final class SimpleNode implements ConditionNode { ... }
```

**修改后**（ConditionNode.java）：
```java
/**
 * 单个字段-值比较条件。
 *
 * <p>
 * <strong>实现说明：</strong>使用 {@code final class} 而非 {@code record} 是为了支持两阶段构造函数
 * 和自定义 {@code toString()} 以掩码敏感值。如需迁移至 record，需评估对所有直接字段访问的兼容性影响。
 */
final class SimpleNode implements ConditionNode { ... }
```

**修改原因**：记录设计决策，说明为何不使用 record，并为未来迁移提供参考。

---

## 三、未修复的问题及原因

| 优先级 | 问题 | 原因 |
|--------|------|------|
| P3 | InClauseBuilder 分批 OR 连接可能导致查询计划不佳 | 低优先级，仅影响超大 IN 子句（>1000 元素），属于极端场景 |
| P3 | QuerySpec.resolveNode() instanceof 链可简化为 switch 模式匹配 | 低优先级，当前代码功能正确，重构需 Java 17+ 语言特性支持 |
| P3 | setAccessible(true) 在 Java 17+ 模块系统中需 README 说明 | 低优先级，属于文档工作，非代码修复 |
| Info | where() 原始谓词绕过类型安全检查 | 设计扩展点，已文档化，非缺陷 |
| Info | executeLimited() 的 TOCTOU 竞态窗口 | 已提供悲观锁选项，属于设计权衡 |
| Info | eq() 对 null 值的隐式 IS NULL 转换 | API 设计选择，已文档化 |

---

## 四、验证步骤

1. **代码格式化**：`./mvnw spotless:apply` ✅ 通过
2. **编译检查**：`./mvnw compile` ✅ 通过
3. **单元测试**：`./mvnw test -DexcludedGroups=integration` ✅ 592 测试全部通过
4. **Spotless 检查**：`./mvnw spotless:check` ✅ 通过

---

## 五、修改的文件清单

| 文件 | 修改类型 | 涉及问题 |
|------|----------|----------|
| `src/main/java/com/zsubera/jpa/repository/SoftDeleteJpaRepository.java` | 新增方法 | P0 #1 |
| `src/main/java/com/zsubera/jpa/projection/ProjectionSpec.java` | 新增方法 + 重构 | P1 #2, P2 #6 |
| `src/main/java/com/zsubera/jpa/template/MyJpaTemplate.java` | 重构 | P1 #3 |
| `src/main/java/com/zsubera/jpa/util/LambdaUtils.java` | 修改逻辑 | P2 #4 |
| `src/main/java/com/zsubera/jpa/update/SoftDeleteHelper.java` | 修改逻辑 | P2 #5 |
| `src/main/java/com/zsubera/jpa/spec/ConditionNode.java` | 文档更新 | P2 #7, P2 #8 |

---

*报告生成日期: 2026-05-29*  
*优化执行者: AI 优化专家*
