package com.zsubera.jpa.spec;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/**
 * Specification 组合工具类，提供便捷的静态方法用于组合 {@link Specification}。
 *
 * <p>
 * 与 {@link QuerySpec} 的条件方法不同，这些方法可以直接操作 {@link Specification} 对象，
 * 适用于需要组合多个独立 Specification 的场景。
 *
 * <p>
 * 使用示例：
 * <pre>{@code
 * Specification<User> active = (root, query, cb) -> cb.equal(root.get("status"), "ACTIVE");
 * Specification<User> adult = (root, query, cb) -> cb.greaterThan(root.get("age"), 18);
 *
 * // AND 组合：必须同时满足
 * Specification<User> combined = Spec.all(active, adult);
 *
 * // OR 组合：满足任一即可
 * Specification<User> combined = Spec.any(active, adult);
 *
 * // NOT 取反：不满足条件
 * Specification<User> combined = Spec.not(active);
 *
 * // 复杂组合
 * Specification<User> complex = Spec.all(
 *     Spec.any(active, adult),
 *     Spec.not((root, query, cb) -> cb.isNull(root.get("deletedAt")))
 * );
 * }</pre>
 *

 */
public final class Spec {

    private Spec() {}

    /**
     * AND 组合：所有 Specification 都必须匹配。
     *
     * <p>
     * 等价于 {@code spec1.and(spec2).and(spec3)...}
     *
     * @param specs 要组合的 Specification 列表
     * @param <T> 实体类型
     * @return 组合后的 Specification
     * @throws IllegalArgumentException 如果 specs 为 null 或空
     */
    @SafeVarargs
    public static <T> Specification<T> all(Specification<T>... specs) {
        if (specs == null || specs.length == 0) {
            throw new IllegalArgumentException("specs must not be null or empty");
        }
        for (int i = 0; i < specs.length; i++) {
            if (specs[i] == null) {
                throw new IllegalArgumentException("specs[" + i + "] must not be null");
            }
        }
        if (specs.length == 1) {
            return specs[0];
        }
        Specification<T> result = specs[0];
        for (int i = 1; i < specs.length; i++) {
            result = result.and(specs[i]);
        }
        return result;
    }

    /**
     * OR 组合：任一 Specification 匹配即可。
     *
     * <p>
     * 等价于 {@code spec1.or(spec2).or(spec3)...}
     *
     * @param specs 要组合的 Specification 列表
     * @param <T> 实体类型
     * @return 组合后的 Specification
     * @throws IllegalArgumentException 如果 specs 为 null 或空
     */
    @SafeVarargs
    public static <T> Specification<T> any(Specification<T>... specs) {
        if (specs == null || specs.length == 0) {
            throw new IllegalArgumentException("specs must not be null or empty");
        }
        for (int i = 0; i < specs.length; i++) {
            if (specs[i] == null) {
                throw new IllegalArgumentException("specs[" + i + "] must not be null");
            }
        }
        if (specs.length == 1) {
            return specs[0];
        }
        Specification<T> result = specs[0];
        for (int i = 1; i < specs.length; i++) {
            result = result.or(specs[i]);
        }
        return result;
    }

    /**
     * NOT 取反：不满足 Specification 的记录。
     *
     * <p>
     * 等价于：
     * <pre>{@code
     * (root, query, cb) -> cb.not(spec.toPredicate(root, query, cb))
     * }</pre>
     *
     * @param spec 要取反的 Specification
     * @param <T> 实体类型
     * @return 取反后的 Specification
     * @throws IllegalArgumentException 如果 spec 为 null
     */
    public static <T> Specification<T> not(Specification<T> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        return (root, query, cb) -> {
            Predicate predicate = spec.toPredicate(root, query, cb);
            return cb.not(predicate);
        };
    }

    /**
     * 创建一个始终匹配的 Specification（全选）。
     *
     * @param <T> 实体类型
     * @return 始终返回 true 的 Specification
     */
    public static <T> Specification<T> always() {
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * 创建一个永不匹配的 Specification（全不选）。
     *
     * @param <T> 实体类型
     * @return 始终返回 false 的 Specification
     */
    public static <T> Specification<T> never() {
        return (root, query, cb) -> cb.disjunction();
    }

    /**
     * 条件组合：根据条件选择性地应用 Specification。
     *
     * <p>
     * 当 {@code condition} 为 true 时返回 {@code spec}，否则返回 {@link #always()}。
     *
     * @param condition 条件
     * @param spec 条件为 true 时应用的 Specification
     * @param <T> 实体类型
     * @return 条件 Specification 或 always()
     * @throws IllegalArgumentException 如果 spec 为 null
     */
    public static <T> Specification<T> when(boolean condition, Specification<T> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        return condition ? spec : always();
    }
}
