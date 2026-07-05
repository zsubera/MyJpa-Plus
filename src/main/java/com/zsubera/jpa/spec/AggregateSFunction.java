package com.zsubera.jpa.spec;

/**
 * 聚合函数包装器，用于在投影查询中表示 COUNT/SUM/AVG/MAX/MIN 表达式。
 *
 * <p>
 * 通过 {@link QuerySpec#count()}、{@link QuerySpec#sum(SFunction)} 等静态工厂方法创建。
 *
 * @param <T> 实体类型
 * @param <R> 聚合结果类型
 */
public final class AggregateSFunction<T, R> implements SFunction<T, R> {

    enum AggregateType {
        COUNT, COUNT_FIELD, SUM, AVG, MAX, MIN
    }

    private final AggregateType type;
    private final String fieldName;
    private final String alias;

    AggregateSFunction(AggregateType type, String fieldName, String alias) {
        this.type = type;
        this.fieldName = fieldName;
        this.alias = alias;
    }

    @Override
    public R apply(T t) {
        throw new UnsupportedOperationException("AggregateSFunction is a marker, not callable");
    }

    public AggregateType getAggregateType() {
        return type;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getAlias() {
        return alias;
    }

    public boolean isCountAll() {
        return type == AggregateType.COUNT;
    }
}
