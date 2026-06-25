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
     * @param path 当前路径
     * @param rootPath 查询根路径（用于 EXISTS/IN 子查询关联）
     * @param query Criteria 查询对象
     * @param cb Criteria 构建器
     * @param joinCache JOIN 缓存
     * @param pathPrefix 路径前缀
     * @param depth 当前递归深度
     * @param fetchPaths 已获取的路径集合
     */
    record NodeContext(@NonNull Path<?> path, @NonNull Path<?> rootPath, @NonNull CriteriaQuery<?> query,
        @NonNull CriteriaBuilder cb, @NonNull Map<String, Join<?, ?>> joinCache, @Nullable String pathPrefix, int depth,
        @NonNull java.util.Set<String> fetchPaths) {
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

        NodeStrategy strategy = STRATEGIES.get(node.getClass());
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown ConditionNode type: " + node.getClass().getName());
        }

        NodeContext ctx = new NodeContext(path, rootPath, query, cb, joinCache, pathPrefix, depth, fetchPaths);
        return strategy.resolve(node, ctx);
    }

    private static Predicate resolveSimple(ConditionNode.SimpleNode node, NodeContext ctx) {
        return PredicateHelper.resolveSimplePredicate(ctx.path(), node, ctx.cb());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate resolveFuncNode(ConditionNode.FuncNode node, NodeContext ctx) {
        CriteriaBuilder cb = ctx.cb();
        Path<?> path = ctx.path();
        Expression<?>[] args = new Expression[node.params.length];
        for (int i = 0; i < node.params.length; i++) {
            Object param = node.params[i];
            if (param instanceof String fieldName && i == 0) {
                args[i] = path.get(fieldName);
            } else {
                args[i] = param != null ? cb.literal(param) : cb.nullLiteral(Object.class);
            }
        }
        Expression<Boolean> funcExpr = cb.function(node.functionName, Boolean.class, args);
        return cb.isTrue(funcExpr);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate resolveJoin(ConditionNode.JoinNode node, NodeContext ctx) {
        Path<?> path = ctx.path();
        Path<?> rootPath = ctx.rootPath();
        CriteriaQuery<?> query = ctx.query();
        CriteriaBuilder cb = ctx.cb();
        Map<String, Join<?, ?>> joinCache = ctx.joinCache();
        String pathPrefix = ctx.pathPrefix();
        int depth = ctx.depth();
        java.util.Set<String> fetchPaths = ctx.fetchPaths();

        String fullPath = (pathPrefix != null && !pathPrefix.isEmpty() ? pathPrefix + "." : "") + node.fieldName;

        boolean isFetch =
            node.joinType == ConditionNode.JoinType.FETCH || node.joinType == ConditionNode.JoinType.LEFT_FETCH;
        jakarta.persistence.criteria.JoinType jt =
            (node.joinType == ConditionNode.JoinType.LEFT || node.joinType == ConditionNode.JoinType.LEFT_FETCH)
                ? jakarta.persistence.criteria.JoinType.LEFT : jakarta.persistence.criteria.JoinType.INNER;

        Join<?, ?> join = joinCache.get(fullPath);
        boolean isNewJoin = join == null;
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
            joinCache.put(fullPath, join);
        }
        List<Predicate> innerPredicates = new ArrayList<>();

        if (!isFetch) {
            Class<?> joinEntityType = join.getJavaType();
            if (joinEntityType != null) {
                String softDeleteFieldName =
                    com.zsubera.jpa.softdelete.SoftDeleteHelper.findSoftDeleteField(joinEntityType);
                if (softDeleteFieldName != null) {
                    jakarta.persistence.criteria.Path<?> deletedPath = join.get(softDeleteFieldName);
                    Predicate softDeleteFilter = com.zsubera.jpa.softdelete.SoftDeleteHelper.buildNotDeleted(cb,
                        deletedPath, softDeleteFieldName, joinEntityType);
                    if (softDeleteFilter != null) {
                        innerPredicates.add(softDeleteFilter);
                    }
                }
            }
        }

        for (ConditionNode inner : node.innerConditions) {
            Predicate p =
                resolveNodeWithDepth(inner, join, rootPath, query, cb, joinCache, fullPath, depth + 1, fetchPaths);
            if (p != null) {
                innerPredicates.add(p);
            }
        }

        // ponytail: For non-fetch joins, apply predicates as ON clause to prevent LEFT JOIN from
        // silently converting to INNER JOIN via WHERE - on(),
        // soft-delete filters and user conditions go to ON.
        // Cache-hit joins fall through to WHERE to avoid overriding prior ON clauses.
        if (!isFetch && isNewJoin && !innerPredicates.isEmpty()) {
            join.on(cb.and(innerPredicates.toArray(new Predicate[0])));
            return cb.conjunction();
        }
        return innerPredicates.isEmpty() ? cb.conjunction() : cb.and(innerPredicates.toArray(new Predicate[0]));
    }

    private static Predicate resolveOr(ConditionNode.OrNode node, NodeContext ctx) {
        List<Predicate> childPredicates = new ArrayList<>();
        for (ConditionNode child : node.nodes) {
            Predicate p = resolveNodeWithDepth(child, ctx.path(), ctx.rootPath(), ctx.query(), ctx.cb(),
                ctx.joinCache(), ctx.pathPrefix(), ctx.depth() + 1, ctx.fetchPaths());
            if (p != null) {
                childPredicates.add(p);
            }
        }
        if (childPredicates.isEmpty()) {
            return ctx.cb().disjunction();
        }
        if (childPredicates.size() == 1) {
            return childPredicates.get(0);
        }
        return ctx.cb().or(childPredicates.toArray(new Predicate[0]));
    }

    private static Predicate resolveAnd(ConditionNode.AndNode node, NodeContext ctx) {
        List<Predicate> childPredicates = new ArrayList<>();
        for (ConditionNode child : node.nodes) {
            Predicate p = resolveNodeWithDepth(child, ctx.path(), ctx.rootPath(), ctx.query(), ctx.cb(),
                ctx.joinCache(), ctx.pathPrefix(), ctx.depth() + 1, ctx.fetchPaths());
            if (p != null) {
                childPredicates.add(p);
            }
        }
        if (childPredicates.isEmpty()) {
            return ctx.cb().conjunction();
        }
        if (childPredicates.size() == 1) {
            return childPredicates.get(0);
        }
        return ctx.cb().and(childPredicates.toArray(new Predicate[0]));
    }

    private static Predicate resolveMultiLike(ConditionNode.MultiLikeNode node, NodeContext ctx) {
        CriteriaBuilder cb = ctx.cb();
        Path<?> path = ctx.path();
        List<Predicate> likes = new ArrayList<>();
        String pattern = ConditionalMethods.wrapLikePattern(node.keyword);
        for (String fieldName : node.fieldNames) {
            likes.add(cb.like(path.get(fieldName).as(String.class), pattern, PredicateHelper.LIKE_ESCAPE_CHAR));
        }
        return cb.or(likes.toArray(new Predicate[0]));
    }

    @SuppressWarnings("unchecked")
    private static Predicate resolveCollection(ConditionNode.CollectionNode node, NodeContext ctx) {
        CriteriaBuilder cb = ctx.cb();
        Path<?> fieldPath = ctx.path().get(node.fieldName);
        if (node.op == ConditionNode.CollectionOp.IS_EMPTY) {
            return cb.isEmpty((Expression<Collection<?>>)fieldPath);
        }
        return cb.isNotEmpty((Expression<Collection<?>>)fieldPath);
    }

    @SuppressWarnings("unchecked")
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
        justification = "Defensive null check for count query context where query may be null despite @NonNull annotation")
    private static <S> Predicate resolveExists(ConditionNode.ExistsNode<S> node, NodeContext ctx) {
        CriteriaQuery<?> query = ctx.query();
        if (query == null) {
            log.debug("EXISTS subquery used in count query context (query=null). "
                + "Creating temporary CriteriaQuery for subquery construction.");
            CriteriaQuery<S> tempQuery = ctx.cb().createQuery(node.subEntity);
            return resolveExistsInternal(node, ctx.rootPath(), tempQuery, ctx.cb());
        }
        return resolveExistsInternal(node, ctx.rootPath(), query, ctx.cb());
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
        throw new IllegalArgumentException("EXISTS correlation requires a Root path for correlation, but got "
            + path.getClass().getSimpleName() + ". Ensure EXISTS is used at the query root level.");
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
        justification = "Defensive null check for count query context where query may be null despite @NonNull annotation")
    private static <S> Predicate resolveInSubQuery(ConditionNode.InSubQueryNode<S> node, NodeContext ctx) {
        CriteriaQuery<?> query = ctx.query();
        if (query == null) {
            log.debug("IN subquery used in count query context (query=null). "
                + "Creating temporary CriteriaQuery for subquery construction.");
            CriteriaQuery<S> tempQuery = ctx.cb().createQuery(node.subEntity);
            return resolveInSubQueryInternal(node, ctx.path(), tempQuery, ctx.cb());
        }
        return resolveInSubQueryInternal(node, ctx.path(), query, ctx.cb());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <S> Predicate resolveInSubQueryInternal(ConditionNode.InSubQueryNode<S> node, Path<?> outerPath,
        CriteriaQuery<?> query, CriteriaBuilder cb) {
        Class<?> outerFieldType = outerPath.get(node.outerFieldName).getJavaType();
        jakarta.persistence.criteria.Subquery<?> subquery = query.subquery(outerFieldType);
        Root<S> subRoot = (Root<S>)subquery.from(node.subEntity);

        SubQuerySpec<S> subSpec = SubQuerySpec.create((Subquery<S>)subquery, subRoot, subRoot, cb);
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
        return node.fn.apply(ctx.path(), ctx.cb());
    }

    private static Predicate resolveNegate(ConditionNode.NegateNode node, NodeContext ctx) {
        Predicate inner = resolveNodeWithDepth(node.inner, ctx.path(), ctx.rootPath(), ctx.query(), ctx.cb(),
            ctx.joinCache(), ctx.pathPrefix(), ctx.depth() + 1, ctx.fetchPaths());
        if (inner == null) {
            return ctx.cb().conjunction();
        }
        return ctx.cb().not(inner);
    }
}
