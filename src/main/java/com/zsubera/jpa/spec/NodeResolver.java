package com.zsubera.jpa.spec;

import com.zsubera.jpa.exception.QueryBuildException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.slf4j.LoggerFactory;

/**
 * 条件节点树到 JPA {@link Predicate} 的解析器。
 *
 * <p>
 * 从 {@link QuerySpec} 中提取的解析逻辑，将延迟构建的 {@link ConditionNode} 树在查询执行时 转换为 JPA Criteria API 的 {@link Predicate} 树。此分离降低了
 * QuerySpec 的复杂度。
 *
 * <p>
 * 内部工具类——不建议应用代码直接使用。
 */
final class NodeResolver {

    private static final Logger log = LoggerFactory.getLogger(NodeResolver.class);

    private static final int MAX_RECURSION_DEPTH = 50;

    /**
     * 解析上下文，封装节点解析所需的所有参数。
     *
     * <p>
     * 可变对象，递归过程中复用同一个实例（仅更新 path、pathPrefix、depth），
     * 避免每次递归都创建新对象。
     */
    static final class NodeContext {
        Path<?> path;
        Path<?> rootPath;
        CriteriaQuery<?> query;
        CriteriaBuilder cb;
        Map<String, Join<?, ?>> joinCache;
        String pathPrefix;
        int depth;
        java.util.Set<String> fetchPaths;
        boolean applySoftDeleteFilter;

        NodeContext(Path<?> path, Path<?> rootPath, @Nullable CriteriaQuery<?> query,
            CriteriaBuilder cb, Map<String, Join<?, ?>> joinCache, @Nullable String pathPrefix,
            java.util.Set<String> fetchPaths, boolean applySoftDeleteFilter) {
            this.path = path;
            this.rootPath = rootPath;
            this.query = query;
            this.cb = cb;
            this.joinCache = joinCache;
            this.pathPrefix = pathPrefix;
            this.depth = 0;
            this.fetchPaths = fetchPaths;
            this.applySoftDeleteFilter = applySoftDeleteFilter;
        }
    }

    /**
     * 节点解析策略接口。
     *
     * <p>
     * 每种 {@link ConditionNode} 类型对应一个策略实现，负责将节点转换为 JPA {@link Predicate}。
     */
    @FunctionalInterface
    interface NodeStrategy {
        /**
         * 解析条件节点并转换为 Predicate。
         *
         * @param node 条件节点
         * @param ctx 解析上下文
         * @return 生成的 Predicate，如果节点无条件则返回 null
         */
        Predicate resolve(ConditionNode node, NodeContext ctx);
    }

    /** 节点类型到解析策略的不可变映射表。静态初始化后不再修改。 */
    @SuppressWarnings("rawtypes")
    private static final Map<Class<? extends ConditionNode>, NodeStrategy> STRATEGIES = Map.ofEntries(
        Map.entry(ConditionNode.SimpleNode.class, (node, ctx) -> resolveSimple((ConditionNode.SimpleNode)node, ctx)),
        Map.entry(ConditionNode.JoinNode.class, (node, ctx) -> resolveJoin((ConditionNode.JoinNode)node, ctx)),
        Map.entry(ConditionNode.OrNode.class, (node, ctx) -> resolveOr((ConditionNode.OrNode)node, ctx)),
        Map.entry(ConditionNode.AndNode.class, (node, ctx) -> resolveAnd((ConditionNode.AndNode)node, ctx)),
        Map.entry(ConditionNode.MultiLikeNode.class,
            (node, ctx) -> resolveMultiLike((ConditionNode.MultiLikeNode)node, ctx)),
        Map.entry(ConditionNode.CollectionNode.class,
            (node, ctx) -> resolveCollection((ConditionNode.CollectionNode)node, ctx)),
        Map.entry(ConditionNode.ExistsNode.class, (node, ctx) -> resolveExists((ConditionNode.ExistsNode<?>)node, ctx)),
        Map.entry(ConditionNode.InSubQueryNode.class,
            (node, ctx) -> resolveInSubQuery((ConditionNode.InSubQueryNode<?>)node, ctx)),
        Map.entry(ConditionNode.RawNode.class, (node, ctx) -> resolveRaw((ConditionNode.RawNode)node, ctx)),
        Map.entry(ConditionNode.NegateNode.class, (node, ctx) -> resolveNegate((ConditionNode.NegateNode)node, ctx)),
        Map.entry(ConditionNode.FuncNode.class, (node, ctx) -> resolveFuncNode((ConditionNode.FuncNode)node, ctx)));

    private NodeResolver() {}

    /**
     * 解析条件节点并转换为 Predicate。
     *
     * @param node 条件节点
     * @param path 当前路径
     * @param rootPath 查询根路径（用于 EXISTS/IN 子查询关联）
     * @param query Criteria 查询对象
     * @param cb Criteria 构建器
     * @param joinCache JOIN 缓存
     * @param pathPrefix 路径前缀
     * @return 生成的 Predicate，如果节点无条件则返回 null
     */
    static Predicate resolveNode(ConditionNode node, Path<?> path, Path<?> rootPath, CriteriaQuery<?> query,
        CriteriaBuilder cb, Map<String, Join<?, ?>> joinCache, String pathPrefix) {
        return resolveNodeWithDepth(node, path, rootPath, query, cb, joinCache, pathPrefix, 0,
            java.util.Collections.newSetFromMap(new java.util.HashMap<>()));
    }

    static Predicate resolveNode(ConditionNode node, Path<?> path, Path<?> rootPath, CriteriaQuery<?> query,
        CriteriaBuilder cb, Map<String, Join<?, ?>> joinCache, String pathPrefix, java.util.Set<String> fetchPaths) {
        return resolveNodeWithDepth(node, path, rootPath, query, cb, joinCache, pathPrefix, 0, fetchPaths);
    }

    /**
     * 解析条件节点并转换为 Predicate（带深度限制）。
     *
     * @param node 条件节点
     * @param path 当前路径
     * @param rootPath 查询根路径（用于 EXISTS/IN 子查询关联）
     * @param query Criteria 查询对象
     * @param cb Criteria 构建器
     * @param joinCache JOIN 缓存
     * @param pathPrefix 路径前缀
     * @param depth 当前递归深度
     * @return 生成的 Predicate，如果节点无条件则返回 null
     */
    private static Predicate resolveNodeWithDepth(ConditionNode node, Path<?> path, Path<?> rootPath,
        CriteriaQuery<?> query, CriteriaBuilder cb, Map<String, Join<?, ?>> joinCache, String pathPrefix, int depth,
        java.util.Set<String> fetchPaths) {

        if (depth > MAX_RECURSION_DEPTH) {
            throw new QueryBuildException("Condition node recursion depth exceeded maximum limit ("
                + MAX_RECURSION_DEPTH + "). This may indicate a circular condition tree or excessively "
                + "nested condition structure. Please simplify your query conditions.");
        }

        // ponytail: 在顶层计算一次软删除过滤，避免每个 JoinNode 都重复调用
        boolean applySoftDeleteFilter = shouldApplySoftDeleteAutoFilter();

        NodeContext ctx = new NodeContext(path, rootPath, query, cb, joinCache, pathPrefix, fetchPaths,
            applySoftDeleteFilter);
        ctx.depth = depth;
        return resolveNodeInternal(node, ctx);
    }

    /**
     * 内部节点解析，复用已有的 NodeContext（仅更新 path、pathPrefix、depth）。
     */
    private static Predicate resolveNodeInternal(ConditionNode node, NodeContext ctx) {
        NodeStrategy strategy = STRATEGIES.get(node.getClass());
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown ConditionNode type: " + node.getClass().getName());
        }
        return strategy.resolve(node, ctx);
    }

    /** ponytail: 将软删除自动过滤与全局配置及 IgnoreSoftDelete 上下文联动。 */
    private static boolean shouldApplySoftDeleteAutoFilter() {
        com.zsubera.jpa.autoconfigure.MyJpaPlusGlobalConfig config =
            com.zsubera.jpa.autoconfigure.GlobalConfigHolder.getConfig();
        if (config != null && !config.isSoftDeleteAutoFilter()) {
            return false;
        }
        if (com.zsubera.jpa.repository.SoftDeleteContext.isIgnoreSoftDelete()) {
            return false;
        }
        return true;
    }

    private static Predicate resolveSimple(ConditionNode.SimpleNode node, NodeContext ctx) {
        return PredicateHelper.resolveSimplePredicate(ctx.path, node, ctx.cb);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate resolveFuncNode(ConditionNode.FuncNode node, NodeContext ctx) {
        CriteriaBuilder cb = ctx.cb;
        Path<?> path = ctx.path;
        Expression<?>[] args = new Expression[node.params.length];
        for (int i = 0; i < node.params.length; i++) {
            Object param = node.params[i];
            if (i == 0) {
                if (!(param instanceof String fieldName)) {
                    throw new QueryBuildException(
                        "First parameter of function '" + node.functionName + "' must be a field name (String), "
                            + "but got " + (param != null ? param.getClass().getSimpleName() + ": " + param : "null")
                            + ". Use a field reference like entity::getFieldName as the first argument.");
                }
                try {
                    args[i] = path.get(fieldName);
                } catch (IllegalArgumentException e) {
                    throw new QueryBuildException("Failed to resolve field '" + fieldName + "' for function '"
                        + node.functionName + "': " + e.getMessage(), e);
                }
            } else {
                args[i] = param != null ? cb.literal(param) : cb.nullLiteral(Object.class);
            }
        }
        Expression<Boolean> funcExpr = cb.function(node.functionName, Boolean.class, args);
        return cb.isTrue(funcExpr);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate resolveJoin(ConditionNode.JoinNode node, NodeContext ctx) {
        Path<?> path = ctx.path;
        Path<?> rootPath = ctx.rootPath;
        CriteriaQuery<?> query = ctx.query;
        CriteriaBuilder cb = ctx.cb;
        Map<String, Join<?, ?>> joinCache = ctx.joinCache;
        String pathPrefix = ctx.pathPrefix;
        int depth = ctx.depth;
        java.util.Set<String> fetchPaths = ctx.fetchPaths;

        String fullPath = (pathPrefix != null && !pathPrefix.isEmpty() ? pathPrefix + "." : "") + node.fieldName;

        boolean isFetch =
            node.joinType == ConditionNode.JoinType.FETCH || node.joinType == ConditionNode.JoinType.LEFT_FETCH;
        jakarta.persistence.criteria.JoinType jt =
            (node.joinType == ConditionNode.JoinType.LEFT || node.joinType == ConditionNode.JoinType.LEFT_FETCH)
                ? jakarta.persistence.criteria.JoinType.LEFT : jakarta.persistence.criteria.JoinType.INNER;

        // ponytail: include JoinType in cache key to prevent LEFT JOIN silently downgraded to INNER JOIN
        // (or vice versa) when the same path is joined with different types.
        String cacheKey = fullPath + ":" + jt;
        Join<?, ?> join = joinCache.get(cacheKey);
        if (join != null) {
            boolean existingIsFetch = fetchPaths.contains(fullPath);
            if (!isFetch && existingIsFetch) {
                log.warn("Join path '{}' reusing existing fetch join (requested non-fetch). "
                    + "This means conditions will be applied to a fetch join, which may change query semantics "
                    + "(eager loading instead of lazy).", fullPath);
            } else if (isFetch && !existingIsFetch) {
                throw new IllegalStateException("Join path '" + fullPath
                    + "' was previously created as non-fetch join, but now requested as fetch join. "
                    + "This would cause eager loading to be silently ignored. "
                    + "Reorder your conditions so fetch joins come first, or use different paths.");
            }
        } else {
            if (!(path instanceof From<?, ?>)) {
                throw new IllegalArgumentException("Cannot join on non-entity path '" + node.fieldName
                    + "'. Join is only supported on entity paths (Root/Join). "
                    + "For embeddable fields, use direct field access instead of join.");
            }
            if (isFetch) {
                join = (Join<?, ?>)((From<?, ?>)path).fetch(node.fieldName, jt);
                fetchPaths.add(fullPath);
            } else {
                join = ((From<?, ?>)path).join(node.fieldName, jt);
            }
            joinCache.put(cacheKey, join);
        }
        // ponytail: 使用预估容量的 ArrayList，减少 resize 开销
        List<Predicate> innerPredicates = new ArrayList<>(Math.max(node.innerConditions.size(), 2));

        // ponytail: 使用预计算的 softDeleteFilter，避免重复调用 shouldApplySoftDeleteAutoFilter()
        if (!isFetch && ctx.applySoftDeleteFilter) {
            Class<?> joinEntityType = join.getJavaType();
            if (joinEntityType != null) {
                String softDeleteFieldName =
                    com.zsubera.jpa.softdelete.SoftDeleteHelper.findSoftDeleteField(joinEntityType);
                if (softDeleteFieldName != null) {
                    Predicate softDeleteFilter = com.zsubera.jpa.softdelete.SoftDeleteHelper.buildNotDeleted(cb, join,
                        softDeleteFieldName, joinEntityType);
                    if (softDeleteFilter != null) {
                        innerPredicates.add(softDeleteFilter);
                    }
                }
            }
        }

        for (ConditionNode inner : node.innerConditions) {
            Predicate p = resolveJoinChild(inner, join, rootPath, query, cb, joinCache, fullPath, depth + 1, fetchPaths,
                ctx.applySoftDeleteFilter);
            if (p != null) {
                innerPredicates.add(p);
            }
        }

        // ponytail: For non-fetch joins, keep join-scoped predicates on the ON clause so LEFT JOIN
        // does not silently degrade into INNER JOIN. Cache hits must merge with the existing ON
        // predicate instead of falling back to WHERE.
        if (!isFetch && !innerPredicates.isEmpty()) {
            Predicate newOnPredicate = cb.and(innerPredicates.toArray(new Predicate[0]));
            Predicate existingOnPredicate = join.getOn();
            join.on(existingOnPredicate == null ? newOnPredicate : cb.and(existingOnPredicate, newOnPredicate));
            return cb.conjunction();
        }
        if (isFetch && node.joinType == ConditionNode.JoinType.LEFT_FETCH && !innerPredicates.isEmpty()) {
            throw new QueryBuildException(
                "LEFT FETCH JOIN with filter conditions is not supported by the JPA Criteria API. "
                    + "FETCH JOIN cannot specify an ON clause, so conditions are applied to WHERE, "
                    + "which silently degrades LEFT JOIN semantics to INNER JOIN (rows with no matching "
                    + "children will be excluded). Path: '" + (fullPath != null ? fullPath : node.fieldName) + "'. "
                    + "Use leftJoin() (non-fetch) with conditions instead, or move the filter to a separate "
                    + "WHERE predicate outside the join block.");
        }
        return innerPredicates.isEmpty() ? cb.conjunction() : cb.and(innerPredicates.toArray(new Predicate[0]));
    }

    /** ponytail: JOIN 子节点解析，使用扁平参数避免创建 NodeContext。 */
    private static Predicate resolveJoinChild(ConditionNode child, Join<?, ?> join, Path<?> rootPath,
        CriteriaQuery<?> query, CriteriaBuilder cb, Map<String, Join<?, ?>> joinCache, String pathPrefix, int depth,
        java.util.Set<String> fetchPaths, boolean applySoftDeleteFilter) {

        if (depth > MAX_RECURSION_DEPTH) {
            throw new QueryBuildException("Condition node recursion depth exceeded maximum limit ("
                + MAX_RECURSION_DEPTH + "). This may indicate a circular condition tree or excessively "
                + "nested condition structure. Please simplify your query conditions.");
        }

        NodeContext ctx = new NodeContext(join, rootPath, query, cb, joinCache, pathPrefix, fetchPaths,
            applySoftDeleteFilter);
        ctx.depth = depth;
        return resolveNodeInternal(child, ctx);
    }

    private static Predicate resolveOr(ConditionNode.OrNode node, NodeContext ctx) {
        // ponytail: 单子节点优化，避免创建列表和 cb.or() 包装
        if (node.nodes.size() == 1) {
            return resolveChild(node.nodes.get(0), ctx);
        }
        List<Predicate> childPredicates = new ArrayList<>(node.nodes.size());
        for (ConditionNode child : node.nodes) {
            Predicate p = resolveChild(child, ctx);
            if (p != null) {
                childPredicates.add(p);
            }
        }
        if (childPredicates.isEmpty()) {
            return ctx.cb.disjunction();
        }
        if (childPredicates.size() == 1) {
            return childPredicates.get(0);
        }
        return ctx.cb.or(childPredicates.toArray(new Predicate[0]));
    }

    private static Predicate resolveAnd(ConditionNode.AndNode node, NodeContext ctx) {
        // ponytail: 单子节点优化，避免创建列表和 cb.and() 包装
        if (node.nodes.size() == 1) {
            return resolveChild(node.nodes.get(0), ctx);
        }
        List<Predicate> childPredicates = new ArrayList<>(node.nodes.size());
        for (ConditionNode child : node.nodes) {
            Predicate p = resolveChild(child, ctx);
            if (p != null) {
                childPredicates.add(p);
            }
        }
        if (childPredicates.isEmpty()) {
            return ctx.cb.conjunction();
        }
        if (childPredicates.size() == 1) {
            return childPredicates.get(0);
        }
        return ctx.cb.and(childPredicates.toArray(new Predicate[0]));
    }

    /** ponytail: 递归解析子节点，复用 NodeContext（仅更新 path、pathPrefix、depth）。 */
    private static Predicate resolveChild(ConditionNode child, NodeContext ctx) {
        int newDepth = ctx.depth + 1;
        if (newDepth > MAX_RECURSION_DEPTH) {
            throw new QueryBuildException("Condition node recursion depth exceeded maximum limit ("
                + MAX_RECURSION_DEPTH + "). This may indicate a circular condition tree or excessively "
                + "nested condition structure. Please simplify your query conditions.");
        }
        // ponytail: 保存并恢复 ctx 状态，避免创建新 NodeContext
        Path<?> savedPath = ctx.path;
        String savedPathPrefix = ctx.pathPrefix;
        int savedDepth = ctx.depth;
        ctx.path = ctx.path;
        ctx.pathPrefix = ctx.pathPrefix;
        ctx.depth = newDepth;
        try {
            return resolveNodeInternal(child, ctx);
        } finally {
            ctx.path = savedPath;
            ctx.pathPrefix = savedPathPrefix;
            ctx.depth = savedDepth;
        }
    }

    private static Predicate resolveMultiLike(ConditionNode.MultiLikeNode node, NodeContext ctx) {
        CriteriaBuilder cb = ctx.cb;
        Path<?> path = ctx.path;
        // ponytail: 使用预估容量的 ArrayList，避免 resize 开销
        List<Predicate> likes = new ArrayList<>(node.fieldNames.length);
        String pattern = ConditionalMethods.wrapLikePattern(node.keyword);
        for (String fieldName : node.fieldNames) {
            likes.add(cb.like(path.get(fieldName).as(String.class), pattern, PredicateHelper.LIKE_ESCAPE_CHAR));
        }
        return cb.or(likes.toArray(new Predicate[0]));
    }

    @SuppressWarnings("unchecked")
    private static Predicate resolveCollection(ConditionNode.CollectionNode node, NodeContext ctx) {
        CriteriaBuilder cb = ctx.cb;
        Path<?> fieldPath = ctx.path.get(node.fieldName);
        if (node.op == ConditionNode.CollectionOp.IS_EMPTY) {
            return cb.isEmpty((Expression<Collection<?>>)fieldPath);
        }
        return cb.isNotEmpty((Expression<Collection<?>>)fieldPath);
    }

    @SuppressWarnings("unchecked")
    private static <S> Predicate resolveExists(ConditionNode.ExistsNode<S> node, NodeContext ctx) {
        CriteriaQuery<?> query = ctx.query;
        if (query == null) {
            throw new QueryBuildException("EXISTS subquery is not supported in count query context (query=null). "
                + "Use a separate count query without subqueries.");
        }
        return resolveExistsInternal(node, ctx.rootPath, query, ctx.cb);
    }

    private static <S> Predicate resolveExistsInternal(ConditionNode.ExistsNode<S> node, Path<?> rootPath,
        CriteriaQuery<?> query, CriteriaBuilder cb) {
        jakarta.persistence.criteria.Subquery<S> subquery = query.subquery(node.subEntity);
        Root<S> subRoot = subquery.from(node.subEntity);
        Root<?> correlatedOuter = resolveCorrelationRoot(subquery, rootPath);
        SubQuerySpec<S> subSpec = SubQuerySpec.create(subquery, subRoot, correlatedOuter, cb);
        node.config.accept(subSpec);
        subSpec.applyWhere();
        if (!subSpec.isSelectSet()) {
            subquery.select(subRoot);
        }
        return node.negate ? cb.not(cb.exists(subquery)) : cb.exists(subquery);
    }

    private static Root<?> resolveCorrelationRoot(jakarta.persistence.criteria.Subquery<?> subquery, Path<?> path) {
        if (path instanceof Root<?> root) {
            return subquery.correlate(root);
        }
        if (path instanceof jakarta.persistence.criteria.Join<?, ?>) {
            throw new QueryBuildException(
                "EXISTS/IN subquery inside a JOIN node's conditions cannot correlate against a Join path. "
                    + "The JPA Criteria API requires correlation via a Root path, but the current path is a "
                    + path.getClass().getSimpleName()
                    + ". Move the subquery condition to the query root level, or restructure the query "
                    + "to use a non-fetch JOIN with the subquery in the WHERE clause.");
        }
        throw new IllegalArgumentException("EXISTS correlation requires a Root path for correlation, but got "
            + path.getClass().getSimpleName() + ". Ensure EXISTS is used at the query root level.");
    }

    private static <S> Predicate resolveInSubQuery(ConditionNode.InSubQueryNode<S> node, NodeContext ctx) {
        CriteriaQuery<?> query = ctx.query;
        if (query == null) {
            throw new QueryBuildException("IN subquery is not supported in count query context (query=null). "
                + "Use a separate count query without subqueries.");
        }
        return resolveInSubQueryInternal(node, ctx.path, ctx.rootPath, query, ctx.cb);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <S> Predicate resolveInSubQueryInternal(ConditionNode.InSubQueryNode<S> node, Path<?> outerPath,
        Path<?> rootPath, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Class<?> outerFieldType = outerPath.get(node.outerFieldName).getJavaType();
        jakarta.persistence.criteria.Subquery<?> subquery = query.subquery(outerFieldType);
        Root<S> subRoot = (Root<S>)subquery.from(node.subEntity);

        Root<?> correlatedOuter = resolveCorrelationRoot(subquery, rootPath);
        SubQuerySpec<S> subSpec = SubQuerySpec.create((Subquery<S>)subquery, subRoot, correlatedOuter, cb);
        node.config.accept(subSpec);
        subSpec.applyWhere();

        if (!subSpec.isSelectSet()) {
            ((jakarta.persistence.criteria.Subquery<S>)subquery).select(subRoot);
        }

        CriteriaBuilder.In inClause = cb.in(outerPath.get(node.outerFieldName));
        inClause.value((jakarta.persistence.criteria.Subquery)subquery);
        return node.negate ? cb.not(inClause) : inClause;
    }

    private static Predicate resolveRaw(ConditionNode.RawNode node, NodeContext ctx) {
        return node.fn.apply(ctx.path, ctx.cb);
    }

    private static Predicate resolveNegate(ConditionNode.NegateNode node, NodeContext ctx) {
        Predicate inner = resolveChild(node.inner, ctx);
        if (inner == null) {
            return ctx.cb.disjunction();
        }
        return ctx.cb.not(inner);
    }
}
