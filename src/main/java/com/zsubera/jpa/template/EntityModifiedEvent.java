package com.zsubera.jpa.template;

import org.springframework.context.ApplicationEvent;

/**
 * 实体变更事件，用于在实体发生 INSERT/UPDATE/DELETE 操作后触发缓存失效。
 *
 * <p>
 * 继承 {@link ApplicationEvent} 以与 Spring 事件体系完全兼容，支持 SpEL 条件过滤
 * （如 {@code @EventListener(condition = "#event.affectedRows > 10")}）。
 *
 * <p>
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
public class EntityModifiedEvent extends ApplicationEvent {

    private final String entityName;
    private final int affectedRows;

    /**
     * 创建实体变更事件。
     *
     * @param entityClass 变更的实体类
     * @param affectedRows 受影响的行数
     */
    public EntityModifiedEvent(Class<?> entityClass, int affectedRows) {
        super(entityClass);
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
        super(entityName);
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
