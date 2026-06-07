package com.zsubera.jpa.update;

import static com.zsubera.jpa.spec.ConditionalMethods.requireField;

import com.zsubera.jpa.spec.ConditionalMethods;
import com.zsubera.jpa.spec.PredicateHelper;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.update.AbstractBulkOperationSpec.BulkConditionNode;
import jakarta.persistence.criteria.Predicate;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * 批量操作（{@link UpdateSpec} 和 {@link DeleteSpec}）中 OR 条件组的构建器。
 *
 * <p>
 * 通过此构建器添加的所有条件将以 OR 方式组合。由 {@link AbstractBulkOperationSpec#or(java.util.function.Consumer)} 隐式创建。
 *
 * @param <T> 实体类型
 * @param <SELF> 父构建器类型
 */
public class OrConditionBuilder<T, SELF extends AbstractBulkOperationSpec<T, SELF>>
    implements ConditionalMethods<T, OrConditionBuilder<T, SELF>> {

    private final SELF parent;
    private final List<BulkConditionNode> nodes;

    /**
     * 构造函数。
     *
     * @param parent 父构建器
     * @param nodes 条件节点列表
     */
    OrConditionBuilder(SELF parent, List<BulkConditionNode> nodes) {
        this.parent = parent;
        this.nodes = nodes;
    }

    @Override
    public OrConditionBuilder<T, SELF> self() {
        return this;
    }

    /**
     * 添加等于条件：{@code field = value}。
     *
     * @param field 实体属性的方法引用
     * @param value 比较值
     * @return 当前构建器实例
     */
    @Override
    public OrConditionBuilder<T, SELF> eq(SFunction<T, ?> field, @Nullable Object value) {
        requireField(field);
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.eq(root, name, value, cb)));
        return this;
    }

    /**
     * 添加不等于条件：{@code field != value}。
     *
     * @param field 实体属性的方法引用
     * @param value 比较值
     * @return 当前构建器实例
     */
    @Override
    public OrConditionBuilder<T, SELF> ne(SFunction<T, ?> field, @Nullable Object value) {
        requireField(field);
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.ne(root, name, value, cb)));
        return this;
    }

    /**
     * 添加大于条件：{@code field > value}。
     *
     * @param field 实体属性的方法引用
     * @param value 比较值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    @Override
    public OrConditionBuilder<T, SELF> gt(SFunction<T, ?> field, Comparable<?> value) {
        requireField(field);
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.gt(root, name, value, cb)));
        return this;
    }

    /**
     * 添加大于等于条件：{@code field >= value}。
     *
     * @param field 实体属性的方法引用
     * @param value 比较值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    @Override
    public OrConditionBuilder<T, SELF> ge(SFunction<T, ?> field, Comparable<?> value) {
        requireField(field);
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.ge(root, name, value, cb)));
        return this;
    }

    /**
     * 添加小于条件：{@code field < value}。
     *
     * @param field 实体属性的方法引用
     * @param value 比较值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    @Override
    public OrConditionBuilder<T, SELF> lt(SFunction<T, ?> field, Comparable<?> value) {
        requireField(field);
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.lt(root, name, value, cb)));
        return this;
    }

    /**
     * 添加小于等于条件：{@code field <= value}。
     *
     * @param field 实体属性的方法引用
     * @param value 比较值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    @Override
    public OrConditionBuilder<T, SELF> le(SFunction<T, ?> field, Comparable<?> value) {
        requireField(field);
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.le(root, name, value, cb)));
        return this;
    }

    /**
     * 添加包含匹配条件：{@code field LIKE '%value%'}。值中的通配符会被自动转义。
     *
     * @param field 实体属性的方法引用
     * @param value 匹配值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    @Override
    public OrConditionBuilder<T, SELF> like(SFunction<T, ?> field, String value) {
        requireField(field);
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = parent.property(field);
        String pattern = ConditionalMethods.wrapLikePattern(value);
        nodes.add(new BulkConditionNode.LeafNode(
            (root, cb) -> PredicateHelper.like(root, name, pattern, cb, PredicateHelper.LIKE_ESCAPE_CHAR)));
        return this;
    }

    /**
     * 添加 NOT LIKE 包含匹配条件：{@code field NOT LIKE '%value%'}。值中的通配符会被自动转义。
     *
     * @param field 实体属性的方法引用
     * @param value 匹配值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    @Override
    public OrConditionBuilder<T, SELF> notLike(SFunction<T, ?> field, String value) {
        requireField(field);
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = parent.property(field);
        String pattern = ConditionalMethods.wrapLikePattern(value);
        nodes.add(new BulkConditionNode.LeafNode(
            (root, cb) -> PredicateHelper.notLike(root, name, pattern, cb, PredicateHelper.LIKE_ESCAPE_CHAR)));
        return this;
    }

    /**
     * 添加前缀匹配条件：{@code field LIKE 'value%'}。
     *
     * @param field 实体属性的方法引用
     * @param value 前缀值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    @Override
    public OrConditionBuilder<T, SELF> startsWith(SFunction<T, ?> field, String value) {
        requireField(field);
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.startsWith(root, name, value, cb)));
        return this;
    }

    /**
     * 添加后缀匹配条件：{@code field LIKE '%value'}。
     *
     * @param field 实体属性的方法引用
     * @param value 后缀值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    @Override
    public OrConditionBuilder<T, SELF> endsWith(SFunction<T, ?> field, String value) {
        requireField(field);
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.endsWith(root, name, value, cb)));
        return this;
    }

    /**
     * 添加忽略大小写的等于条件：{@code UPPER(field) = UPPER(value)}。
     *
     * @param field 实体属性的方法引用
     * @param value 比较值（不可为 null，如需检查 null 请使用 isNull()）
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    @Override
    public OrConditionBuilder<T, SELF> eqIgnoreCase(SFunction<T, ?> field, String value) {
        requireField(field);
        if (value == null) {
            throw new IllegalArgumentException(
                "value must not be null for eqIgnoreCase. " + "Use isNull() for null checks.");
        }
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.eqIgnoreCase(root, name, value, cb)));
        return this;
    }

    /**
     * 添加忽略大小写的不等于条件：{@code UPPER(field) <> UPPER(value)}。
     *
     * @param field 实体属性的方法引用
     * @param value 比较值（不可为 null，如需检查 null 请使用 isNotNull()）
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    @Override
    public OrConditionBuilder<T, SELF> neIgnoreCase(SFunction<T, ?> field, String value) {
        requireField(field);
        if (value == null) {
            throw new IllegalArgumentException(
                "value must not be null for neIgnoreCase. " + "Use isNotNull() for null checks.");
        }
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.neIgnoreCase(root, name, value, cb)));
        return this;
    }

    /**
     * 添加忽略大小写的 LIKE 条件：{@code UPPER(field) LIKE UPPER('%value%')}。
     *
     * <p>
     * 值中的 {@code %} 或 {@code _} 字符会被转义，作为字面量处理，防止 LIKE 注入。
     *
     * @param field 实体属性的方法引用
     * @param value 要匹配的原始字符串值（通配符会被转义）
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 value 为 null
     */
    @Override
    public OrConditionBuilder<T, SELF> likeIgnoreCase(SFunction<T, ?> field, String value) {
        requireField(field);
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String name = parent.property(field);
        String pattern = ConditionalMethods.wrapLikePattern(value);
        nodes.add(new BulkConditionNode.LeafNode(
            (root, cb) -> PredicateHelper.likeIgnoreCase(root, name, pattern, cb, PredicateHelper.LIKE_ESCAPE_CHAR)));
        return this;
    }

    /**
     * 添加 IN 条件：{@code field IN (values)}。
     *
     * @param field 实体属性的方法引用
     * @param values 值数组
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 values 为 null 或空
     */
    @Override
    public OrConditionBuilder<T, SELF> in(SFunction<T, ?> field, Object... values) {
        requireField(field);
        String name = parent.property(field);
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.in(root, name, values, cb)));
        return this;
    }

    /**
     * 添加 NOT IN 条件：{@code field NOT IN (values)}。
     *
     * @param field 实体属性的方法引用
     * @param values 值数组
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 values 为 null 或空
     */
    @Override
    public OrConditionBuilder<T, SELF> notIn(SFunction<T, ?> field, Object... values) {
        requireField(field);
        String name = parent.property(field);
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.notIn(root, name, values, cb)));
        return this;
    }

    /**
     * 添加 IN 条件：{@code field IN (values)}。
     *
     * @param field 实体属性的方法引用
     * @param values 值集合
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 values 为 null 或空
     */
    @Override
    public OrConditionBuilder<T, SELF> in(SFunction<T, ?> field, java.util.Collection<?> values) {
        requireField(field);
        String name = parent.property(field);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.in(root, name, values, cb)));
        return this;
    }

    /**
     * 添加 NOT IN 条件：{@code field NOT IN (values)}。
     *
     * @param field 实体属性的方法引用
     * @param values 值集合
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 values 为 null 或空
     */
    @Override
    public OrConditionBuilder<T, SELF> notIn(SFunction<T, ?> field, java.util.Collection<?> values) {
        requireField(field);
        String name = parent.property(field);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.notIn(root, name, values, cb)));
        return this;
    }

    /**
     * 添加 IS NULL 条件：{@code field IS NULL}。
     *
     * @param field 实体属性的方法引用
     * @return 当前构建器实例
     */
    @Override
    public OrConditionBuilder<T, SELF> isNull(SFunction<T, ?> field) {
        requireField(field);
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.isNull(root, name, cb)));
        return this;
    }

    /**
     * 添加 IS NOT NULL 条件：{@code field IS NOT NULL}。
     *
     * @param field 实体属性的方法引用
     * @return 当前构建器实例
     */
    @Override
    public OrConditionBuilder<T, SELF> isNotNull(SFunction<T, ?> field) {
        requireField(field);
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.isNotNull(root, name, cb)));
        return this;
    }

    /**
     * 添加 BETWEEN 条件：{@code field BETWEEN start AND end}。
     *
     * @param field 实体属性的方法引用
     * @param start 范围起始值
     * @param end 范围结束值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 start 或 end 为 null，或类型不匹配，或 start 大于 end
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public OrConditionBuilder<T, SELF> between(SFunction<T, ?> field, Comparable<?> start, Comparable<?> end) {
        requireField(field);
        PredicateHelper.validateRange(start, end);
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.between(root, name, start, end, cb)));
        return this;
    }

    /**
     * 添加 NOT BETWEEN 条件：{@code field NOT BETWEEN start AND end}。
     *
     * @param field 实体属性的方法引用
     * @param start 范围起始值
     * @param end 范围结束值
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 start 或 end 为 null，或类型不兼容，或 start 大于 end
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public OrConditionBuilder<T, SELF> notBetween(SFunction<T, ?> field, Comparable<?> start, Comparable<?> end) {
        requireField(field);
        PredicateHelper.validateRange(start, end);
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.notBetween(root, name, start, end, cb)));
        return this;
    }

    /**
     * 添加集合为空条件：{@code field IS EMPTY}。
     *
     * @param field 实体属性的方法引用
     * @return 当前构建器实例
     */
    @Override
    public OrConditionBuilder<T, SELF> isEmpty(SFunction<T, ?> field) {
        requireField(field);
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.isEmpty(root, name, cb)));
        return this;
    }

    /**
     * 添加集合非空条件：{@code field IS NOT EMPTY}。
     *
     * @param field 实体属性的方法引用
     * @return 当前构建器实例
     */
    @Override
    public OrConditionBuilder<T, SELF> isNotEmpty(SFunction<T, ?> field) {
        requireField(field);
        String name = parent.property(field);
        nodes.add(new BulkConditionNode.LeafNode((root, cb) -> PredicateHelper.isNotEmpty(root, name, cb)));
        return this;
    }

    /**
     * 添加多字段 LIKE 搜索条件。关键字被包装为 {@code %keyword%} 并与每个给定字段匹配，使用 OR 连接。
     *
     * <p>
     * 值中的 {@code %} 或 {@code _} 字符会被转义，作为字面量处理，防止 LIKE 注入。
     *
     * @param keyword 搜索关键字
     * @param fields 一个或多个字符串属性的方法引用
     * @return 当前构建器实例
     * @throws IllegalArgumentException 如果 keyword 为 null，或 fields 为 null，或 fields 包含 null 元素
     */
    @SuppressWarnings("unchecked")
    public OrConditionBuilder<T, SELF> multiLike(String keyword, SFunction<T, ?>... fields) {
        if (keyword == null) {
            throw new IllegalArgumentException("keyword must not be null");
        }
        if (fields == null) {
            throw new IllegalArgumentException("fields must not be null");
        }
        if (!keyword.isEmpty() && fields.length > 0) {
            String[] fieldNames = new String[fields.length];
            for (int i = 0; i < fields.length; i++) {
                if (fields[i] == null) {
                    throw new IllegalArgumentException("fields[" + i + "] must not be null");
                }
                fieldNames[i] = parent.property(fields[i]);
            }
            String escaped = PredicateHelper.escapeLikeWildcards(keyword);
            String pattern = "%" + escaped + "%";
            nodes.add(new BulkConditionNode.LeafNode((root, cb) -> {
                List<Predicate> likes = new java.util.ArrayList<>();
                for (String fieldName : fieldNames) {
                    likes.add(PredicateHelper.like(root, fieldName, pattern, cb, PredicateHelper.LIKE_ESCAPE_CHAR));
                }
                return cb.or(likes.toArray(new Predicate[0]));
            }));
        }
        return this;
    }
}
