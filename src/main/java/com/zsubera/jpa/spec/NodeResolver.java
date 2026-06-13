package com.zsubera.jpa.spec;

import com.zsubera.jpa.exception.MyJpaPlusException;
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

    // [FIX] P1-4: 添加递归深度限制，防止 StackOverflowError
    private static final int MAX_RECURSION_DEPTH = 50;

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
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate resolveNodeWithDepth(ConditionNode node, Path<?> path, Path<?> rootPath,
        CriteriaQuery<?> query, CriteriaBuilder cb, Map<String, Join<?, ?>> joinCache, String pathPrefix, int depth,
        java.util.Set<String> fetchPaths) {
        // [FIX] P1-4: 检查递归深度限制
        if (depth > MAX_RECURSION_DEPTH) {
            throw new MyJpaPlusException("Condition node recursion depth exceeded maximum limit (" + MAX_RECURSION_DEPTH
                + "). This may indicate a circular condition tree or excessively "
                + "nested condition structure. Please simplify your query conditions.");
        }

        if (node instanceof ConditionNode.SimpleNode n) {
            return resolveSimple(n, path, cb);
        }
        if (node instanceof ConditionNode.JoinNode n) {
            return resolveJoin(n, path, rootPath, query, cb, joinCache, pathPrefix, depth, fetchPaths);
        }
        if (node instanceof ConditionNode.OrNode n) {
            return resolveOr(n, path, rootPath, query, cb, joinCache, pathPrefix, depth, fetchPaths);
        }
        if (node instanceof ConditionNode.AndNode n) {
            return resolveAnd(n, path, rootPath, query, cb, joinCache, pathPrefix, depth, fetchPaths);
        }
        if (node instanceof ConditionNode.MultiLikeNode n) {
            return resolveMultiLike(n, path, cb);
        }
        if (node instanceof ConditionNode.CollectionNode n) {
            return resolveCollection(n, path, cb);
        }
        if (node instanceof ConditionNode.ExistsNode<?> n) {
            return resolveExists(n, rootPath, query, cb);
        }
        if (node instanceof ConditionNode.InSubQueryNode<?> n) {
            return resolveInSubQuery(n, path, query, cb);
        }
        if (node instanceof ConditionNode.RawNode n) {
            return n.fn.apply(path, cb);
        }
        if (node instanceof ConditionNode.NegateNode n) {
            Predicate inner =
                resolveNodeWithDepth(n.inner, path, rootPath, query, cb, joinCache, pathPrefix, depth + 1, fetchPaths);
            return inner != null ? cb.not(inner) : null;
        }
        if (node instanceof ConditionNode.FuncNode n) {
            return resolveFuncNode(n, path, cb);
        }
        throw new IllegalArgumentException("Unknown ConditionNode type: " + node.getClass().getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate resolveSimple(ConditionNode.SimpleNode node, Path<?> path, CriteriaBuilder cb) {
        return PredicateHelper.resolveSimplePredicate(path, node, cb);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate resolveFuncNode(ConditionNode.FuncNode node, Path<?> path, CriteriaBuilder cb) {
        Expression<?>[] args = new Expression[node.params.length];
        for (int i = 0; i < node.params.length; i++) {
            Object param = node.params[i];
            if (param instanceof String fieldName && i == 0) {
                args[i] = path.get(fieldName);
            } else {
                args[i] = cb.literal(param);
            }
        }
        Expression<Boolean> funcExpr = cb.function(node.functionName, Boolean.class, args);
        return cb.isTrue(funcExpr);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate resolveJoin(ConditionNode.JoinNode node, Path<?> path, Path<?> rootPath,
        CriteriaQuery<?> query, CriteriaBuilder cb, Map<String, Join<?, ?>> joinCache, String pathPrefix, int depth,
        java.util.Set<String> fetchPaths) {
        String fullPath = (pathPrefix != null && !pathPrefix.isEmpty() ? pathPrefix + "." : "") + node.fieldName;

        boolean isFetch =
            node.joinType == ConditionNode.JoinType.FETCH || node.joinType == ConditionNode.JoinType.LEFT_FETCH;
        jakarta.persistence.criteria.JoinType jt =
            (node.joinType == ConditionNode.JoinType.LEFT || node.joinType == ConditionNode.JoinType.LEFT_FETCH)
                ? jakarta.persistence.criteria.JoinType.LEFT : jakarta.persistence.criteria.JoinType.INNER;

        Join<?, ?> join = joinCache.get(fullPath);
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
        for (ConditionNode inner : node.innerConditions) {
            Predicate p =
                resolveNodeWithDepth(inner, join, rootPath, query, cb, joinCache, fullPath, depth + 1, fetchPaths);
            if (p != null) {
                innerPredicates.add(p);
            }
        }
        return innerPredicates.isEmpty() ? cb.conjunction() : cb.and(innerPredicates.toArray(new Predicate[0]));
    }

    private static Predicate resolveOr(ConditionNode.OrNode node, Path<?> path, Path<?> rootPath,
        CriteriaQuery<?> query, CriteriaBuilder cb, Map<String, Join<?, ?>> joinCache, String pathPrefix, int depth,
        java.util.Set<String> fetchPaths) {
        List<Predicate> childPredicates = new ArrayList<>();
        for (ConditionNode child : node.nodes) {
            Predicate p =
                resolveNodeWithDepth(child, path, rootPath, query, cb, joinCache, pathPrefix, depth + 1, fetchPaths);
            if (p != null) {
                childPredicates.add(p);
            }
        }
        if (childPredicates.isEmpty()) {
            return cb.disjunction();
        }
        if (childPredicates.size() == 1) {
            return childPredicates.get(0);
        }
        return cb.or(childPredicates.toArray(new Predicate[0]));
    }

    private static Predicate resolveAnd(ConditionNode.AndNode node, Path<?> path, Path<?> rootPath,
        CriteriaQuery<?> query, CriteriaBuilder cb, Map<String, Join<?, ?>> joinCache, String pathPrefix, int depth,
        java.util.Set<String> fetchPaths) {
        List<Predicate> childPredicates = new ArrayList<>();
        for (ConditionNode child : node.nodes) {
            Predicate p =
                resolveNodeWithDepth(child, path, rootPath, query, cb, joinCache, pathPrefix, depth + 1, fetchPaths);
            if (p != null) {
                childPredicates.add(p);
            }
        }
        if (childPredicates.isEmpty()) {
            return cb.conjunction();
        }
        if (childPredicates.size() == 1) {
            return childPredicates.get(0);
        }
        return cb.and(childPredicates.toArray(new Predicate[0]));
    }

    private static Predicate resolveMultiLike(ConditionNode.MultiLikeNode node, Path<?> path, CriteriaBuilder cb) {
        List<Predicate> likes = new ArrayList<>();
        String pattern = ConditionalMethods.wrapLikePattern(node.keyword);
        for (String fieldName : node.fieldNames) {
            likes.add(cb.like(path.get(fieldName).as(String.class), pattern, PredicateHelper.LIKE_ESCAPE_CHAR));
        }
        return cb.or(likes.toArray(new Predicate[0]));
    }

    @SuppressWarnings("unchecked")
    private static Predicate resolveCollection(ConditionNode.CollectionNode node, Path<?> path, CriteriaBuilder cb) {
        Path<?> fieldPath = path.get(node.fieldName);
        if (node.op == ConditionNode.CollectionOp.IS_EMPTY) {
            return cb.isEmpty((Expression<Collection<?>>)fieldPath);
        }
        return cb.isNotEmpty((Expression<Collection<?>>)fieldPath);
    }

    @SuppressWarnings("unchecked")
    private static <S> Predicate resolveExists(ConditionNode.ExistsNode<S> node, Path<?> outerPath,
        CriteriaQuery<?> query, CriteriaBuilder cb) {
        if (query == null) {
            log.debug("EXISTS subquery used in count query context (query=null). "
                + "Creating temporary CriteriaQuery for subquery construction.");
            CriteriaQuery<S> tempQuery = cb.createQuery(node.subEntity);
            return resolveExistsInternal(node, outerPath, tempQuery, cb);
        }
        return resolveExistsInternal(node, outerPath, query, cb);
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

    @SuppressWarnings("unchecked")
    private static Root<?> resolveCorrelationRoot(jakarta.persistence.criteria.Subquery<?> subquery, Path<?> path) {
        if (path instanceof Root<?> root) {
            return subquery.correlate(root);
        }
        throw new IllegalArgumentException("EXISTS correlation requires a Root path for correlation, but got "
            + path.getClass().getSimpleName() + ". Ensure EXISTS is used at the query root level.");
    }

    @SuppressWarnings("unchecked")
    private static <S> Predicate resolveInSubQuery(ConditionNode.InSubQueryNode<S> node, Path<?> path,
        CriteriaQuery<?> query, CriteriaBuilder cb) {
        if (query == null) {
            log.debug("IN subquery used in count query context (query=null). "
                + "Creating temporary CriteriaQuery for subquery construction.");
            CriteriaQuery<S> tempQuery = cb.createQuery(node.subEntity);
            return resolveInSubQueryInternal(node, path, tempQuery, cb);
        }
        return resolveInSubQueryInternal(node, path, query, cb);
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
}
