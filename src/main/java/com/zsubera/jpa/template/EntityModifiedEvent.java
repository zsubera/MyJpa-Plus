package com.zsubera.jpa.template;

/**
 * 实体变更事件，用于在实体发生 INSERT/UPDATE/DELETE 操作后触发缓存失效。
 *
 * <p>
 * 此事件通过 Spring ApplicationEvent 机制发布，可在事务提交后自动清除相关查询缓存。
 * 配合 {@link CacheInvalidationListener} 使用，实现查询缓存的自动失效。
 *
 * <p>
 * 使用示例：
 *
 * <pre>{@code
 * // 发布实体变更事件
 * applicationEventPublisher.publishEvent(new EntityModifiedEvent(Order.class, 5));
 *
 * // 或使用带实体名的构造函数
 * applicationEventPublisher.publishEvent(new EntityModifiedEvent("Order", 5));
 * }</pre>
 *
 * @author myjpa-plus

 */
public class EntityModifiedEvent {

    private final String entityName;
    private final int affectedRows;

    /**
     * 创建实体变更事件。
     *
     * @param entityClass 变更的实体类
     * @param affectedRows 受影响的行数
     */
    public EntityModifiedEvent(Class<?> entityClass, int affectedRows) {
        this.entityName = entityClass.getSimpleName();
        this.affectedRows = affectedRows;
    }

    /**
     * 创建实体变更事件。
     *
     * @param entityName 变更的实体名称
     * @param affectedRows 受影响的行数
     */
    public EntityModifiedEvent(String entityName, int affectedRows) {
        this.entityName = entityName;
        this.affectedRows = affectedRows;
    }

    /**
     * 获取变更的实体名称。
     *
     * @return 实体名称
     */
    public String getEntityName() {
        return entityName;
    }

    /**
     * 获取受影响的行数。
     *
     * @return 受影响的行数
     */
    public int getAffectedRows() {
        return affectedRows;
    }
}
