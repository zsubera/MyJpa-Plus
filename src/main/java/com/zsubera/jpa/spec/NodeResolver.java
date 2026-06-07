package com.zsubera.jpa.spec;

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
        if (node instanceof ConditionNode.SimpleNode simpleNode) {
            return resolveSimple(simpleNode, path, cb);
        }
        if (node instanceof ConditionNode.JoinNode joinNode) {
            return resolveJoin(joinNode, path, rootPath, query, cb, joinCache, pathPrefix);
        }
        if (node instanceof ConditionNode.OrNode orNode) {
            return resolveOr(orNode, path, rootPath, query, cb, joinCache, pathPrefix);
        }
        if (node instanceof ConditionNode.AndNode andNode) {
            return resolveAnd(andNode, path, rootPath, query, cb, joinCache, pathPrefix);
        }
        if (node instanceof ConditionNode.MultiLikeNode multiLikeNode) {
            return resolveMultiLike(multiLikeNode, path, cb);
        }
        if (node instanceof ConditionNode.CollectionNode collectionNode) {
            return resolveCollection(collectionNode, path, cb);
        }
        if (node instanceof ConditionNode.ExistsNode<?> existsNode) {
            return resolveExists(existsNode, rootPath, query, cb);
        }
        if (node instanceof ConditionNode.InSubQueryNode<?> inSubQueryNode) {
            return resolveInSubQuery(inSubQueryNode, path, query, cb);
        }
        if (node instanceof ConditionNode.RawNode rawNode) {
            return rawNode.fn.apply(path, cb);
        }
        if (node instanceof ConditionNode.NegateNode negateNode) {
            Predicate inner = resolveNode(negateNode.inner, path, rootPath, query, cb, joinCache, pathPrefix);
            return inner != null ? cb.not(inner) : null;
        }
        if (node instanceof ConditionNode.FuncNode funcNode) {
            return resolveFuncNode(funcNode, path, cb);
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
            // FuncNode 参数约定：params[0] 总是由 ConditionBuilder.func() 设置为字段名
            // （通过 resolveProperty(field) 获取），后续参数为函数的额外参数（通过 cb.literal 绑定）。
            // 此处使用 instanceof String 判断是安全的，因为字段名始终是 String 类型。
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
        CriteriaQuery<?> query, CriteriaBuilder cb, Map<String, Join<?, ?>> joinCache, String pathPrefix) {
        String fullPath = (pathPrefix != null && !pathPrefix.isEmpty() ? pathPrefix + "." : "") + node.fieldName;

        boolean isFetch =
            node.joinType == ConditionNode.JoinType.FETCH || node.joinType == ConditionNode.JoinType.LEFT_FETCH;
        jakarta.persistence.criteria.JoinType jt =
            (node.joinType == ConditionNode.JoinType.LEFT || node.joinType == ConditionNode.JoinType.LEFT_FETCH)
                ? jakarta.persistence.criteria.JoinType.LEFT : jakarta.persistence.criteria.JoinType.INNER;

        Join<?, ?> join = joinCache.get(fullPath);
        if (join != null) {
            boolean existingIsFetch = join instanceof jakarta.persistence.criteria.Fetch;
            if (isFetch && !existingIsFetch) {
                log.warn("Join path '{}' was previously created as non-fetch join, but now requested as fetch join. "
                    + "Using existing non-fetch join. Consider using fetchJoin() consistently.", fullPath);
            } else if (!isFetch && existingIsFetch) {
                log.warn("Join path '{}' was previously created as fetch join, but now requested as non-fetch join. "
                    + "Using existing fetch join. Consider using join() consistently.", fullPath);
            }
        } else {
            if (!(path instanceof From<?, ?>)) {
                throw new IllegalArgumentException("Cannot join on non-entity path '" + node.fieldName
                    + "'. Join is only supported on entity paths (Root/Join). "
                    + "For embeddable fields, use direct field access instead of join.");
            }
            if (isFetch) {
                join = (Join<?, ?>)((From<?, ?>)path).fetch(node.fieldName, jt);
            } else {
                join = ((From<?, ?>)path).join(node.fieldName, jt);
            }
            joinCache.put(fullPath, join);
        }

        List<Predicate> innerPredicates = new ArrayList<>();
        for (ConditionNode inner : node.innerConditions) {
            Predicate p = resolveNode(inner, join, rootPath, query, cb, joinCache, fullPath);
            if (p != null) {
                innerPredicates.add(p);
            }
        }
        return innerPredicates.isEmpty() ? cb.conjunction() : cb.and(innerPredicates.toArray(new Predicate[0]));
    }

    private static Predicate resolveOr(ConditionNode.OrNode node, Path<?> path, Path<?> rootPath,
        CriteriaQuery<?> query, CriteriaBuilder cb, Map<String, Join<?, ?>> joinCache, String pathPrefix) {
        List<Predicate> childPredicates = new ArrayList<>();
        for (ConditionNode child : node.nodes) {
            Predicate p = resolveNode(child, path, rootPath, query, cb, joinCache, pathPrefix);
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
        CriteriaQuery<?> query, CriteriaBuilder cb, Map<String, Join<?, ?>> joinCache, String pathPrefix) {
        List<Predicate> childPredicates = new ArrayList<>();
        for (ConditionNode child : node.nodes) {
            Predicate p = resolveNode(child, path, rootPath, query, cb, joinCache, pathPrefix);
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
        Object[] selectInfo = SubQuerySpec.extractSelectInfo(node.subEntity, node.config, cb);
        Class<?> selectType = (Class<?>)selectInfo[0];
        String selectFieldName = (String)selectInfo[1];

        jakarta.persistence.criteria.Subquery<?> subquery;
        Root<S> subRoot;
        if (selectType != null) {
            subquery = (jakarta.persistence.criteria.Subquery)query.subquery(selectType);
            subRoot = (Root<S>)subquery.from(node.subEntity);
        } else {
            subquery = (jakarta.persistence.criteria.Subquery)query.subquery(node.subEntity);
            subRoot = (Root<S>)subquery.from(node.subEntity);
        }

        SubQuerySpec<S> subSpec = SubQuerySpec.create((Subquery<S>)subquery, subRoot, subRoot, cb);
        if (selectType != null) {
            subSpec.presetSelectType(selectType, selectFieldName);
        }
        node.config.accept(subSpec);
        subSpec.applyWhere();

        if (selectType != null) {
            subSpec.applySelectToSubquery();
        } else if (!subSpec.isSelectSet()) {
            ((jakarta.persistence.criteria.Subquery<S>)subquery).select(subRoot);
        }

        CriteriaBuilder.In inClause = cb.in(outerPath.get(node.outerFieldName));
        inClause.value((jakarta.persistence.criteria.Subquery)subquery);
        return node.negate ? cb.not(inClause) : inClause;
    }
}
