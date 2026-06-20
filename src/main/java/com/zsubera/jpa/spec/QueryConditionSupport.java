package com.zsubera.jpa.spec;

import java.util.function.Consumer;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;

/**
 * QuerySpec 的条件节点辅助类，协调子查询、JOIN、OR/NOT 和条件组合方法的委托。
 *
 * <p>
 * 从 {@link QuerySpec} 中提取，作为内部辅助类的协调器。所有实际逻辑已委托给专门的辅助类：
 * <ul>
 * <li>{@link QuerySubQuerySupport} — EXISTS/IN 子查询</li>
 * <li>{@link QueryJoinSupport} — JOIN 关联</li>
 * <li>{@link QueryCompositionSupport} — OR/NOT 条件组和条件组合</li>
 * </ul>
 *
 * <p>
 * 内部工具类——不建议应用代码直接使用。
 *
 * @param <T> 实体类型
 */
final class QueryConditionSupport<T> {

    private final QuerySpec<T> parent;
    private final QueryJoinSupport<T> joinSupport;
    private final QuerySubQuerySupport<T> subQuerySupport;
    private final QueryCompositionSupport<T> compositionSupport;

    QueryConditionSupport(QuerySpec<T> parent) {
        this.parent = parent;
        this.joinSupport = new QueryJoinSupport<>(parent);
        this.subQuerySupport = new QuerySubQuerySupport<>(parent);
        this.compositionSupport = new QueryCompositionSupport<>(parent);
    }

    // ---- 子查询方法（委托给 QuerySubQuerySupport） ----

    <S> QuerySpec<T> exists(Class<S> subEntity, Consumer<SubQuerySpec<S>> config) {
        return subQuerySupport.exists(subEntity, config);
    }

    <S> QuerySpec<T> notExists(Class<S> subEntity, Consumer<SubQuerySpec<S>> config) {
        return subQuerySupport.notExists(subEntity, config);
    }

    <S> QuerySpec<T> inSubQuery(SFunction<T, ?> outerField, Class<S> subEntity,
        java.util.function.Consumer<SubQuerySpec<S>> config) {
        return subQuerySupport.inSubQuery(outerField, subEntity, config);
    }

    <S> QuerySpec<T> notInSubQuery(SFunction<T, ?> outerField, Class<S> subEntity,
        java.util.function.Consumer<SubQuerySpec<S>> config) {
        return subQuerySupport.notInSubQuery(outerField, subEntity, config);
    }

    // ---- JOIN 方法（委托给 QueryJoinSupport） ----

    <J> QuerySpec<T> join(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
        return joinSupport.join(field, config);
    }

    <J> QuerySpec<T> leftJoin(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
        return joinSupport.leftJoin(field, config);
    }

    <J> QuerySpec<T> fetchJoin(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
        return joinSupport.fetchJoin(field, config);
    }

    <J> QuerySpec<T> leftFetchJoin(SFunction<T, ?> field, Consumer<JoinGroup<T, J>> config) {
        return joinSupport.leftFetchJoin(field, config);
    }

    // ---- OR/NOT 方法（委托给 QueryCompositionSupport） ----

    QuerySpec<T> or(Consumer<OrGroup<T>> config) {
        return compositionSupport.or(config);
    }

    QuerySpec<T> not(Consumer<NotGroup<T>> config) {
        return compositionSupport.not(config);
    }

    // ---- 条件组合方法（委托给 QueryCompositionSupport） ----

    Specification<T> toSpecification() {
        return compositionSupport.toSpecification();
    }

    Specification<T> toSpecification(@Nullable Specification<T> external) {
        return compositionSupport.toSpecification(external);
    }

    Specification<T> or(QuerySpec<T> other) {
        return compositionSupport.or(other);
    }

    QuerySpec<T> then(QuerySpec<T> other) {
        return compositionSupport.then(other);
    }
}
