package com.zsubera.jpa.annotation;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * JPA 实体监听器，自动填充审计字段。
 *
 * <p>
 * 支持的注解：
 * <ul>
 * <li>{@link CreatedAt} - 创建时间</li>
 * <li>{@link UpdatedAt} - 更新时间</li>
 * <li>{@link CreatedBy} - 创建人</li>
 * <li>{@link UpdatedBy} - 更新人</li>
 * </ul>
 *
 * <p>
 * 使用方式：在实体类上添加 {@code @EntityListeners(AuditEntityListener.class)}。
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;Entity
 *     &#64;EntityListeners(AuditEntityListener.class)
 *     public class User {
 *         &#64;CreatedAt
 *         private Instant createdAt;
 *
 *         &#64;UpdatedAt
 *         private Instant updatedAt;
 *
 *         &#64;CreatedBy
 *         private String createdBy;
 *
 *         @UpdatedBy
 *         private String updatedBy;
 *     }
 * }
 * </pre>
 *
 * @author myjpa-plus
 * @since 1.3.0
 */
@Component
public class AuditEntityListener implements ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(AuditEntityListener.class);

    /** 实体类 -> 审计字段的缓存。 */
    private static final Map<Class<?>, AuditFields> AUDIT_FIELDS_CACHE = new ConcurrentHashMap<>();

    private static ApplicationContext applicationContext;
    private static volatile AuditUserProvider userProvider;

    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        applicationContext = ctx;
    }

    /**
     * 获取 AuditUserProvider 实例（延迟初始化）。
     *
     * @return AuditUserProvider 实例，如果未配置则返回 null
     */
    private static AuditUserProvider getUserProvider() {
        if (userProvider == null && applicationContext != null) {
            try {
                userProvider = applicationContext.getBean(AuditUserProvider.class);
            } catch (Exception e) {
                log.debug("No AuditUserProvider bean found, createdBy/updatedBy will not be auto-filled");
            }
        }
        return userProvider;
    }

    /**
     * 实体持久化前的回调，填充创建时间和创建人。
     *
     * @param entity 实体实例
     */
    @PrePersist
    public void prePersist(Object entity) {
        AuditFields fields = resolveAuditFields(entity.getClass());
        Instant now = Instant.now();

        if (fields.createdAt != null) {
            setFieldValue(entity, fields.createdAt, now);
        }
        if (fields.updatedAt != null) {
            setFieldValue(entity, fields.updatedAt, now);
        }
        if (fields.createdBy != null) {
            AuditUserProvider provider = getUserProvider();
            if (provider != null) {
                setFieldValue(entity, fields.createdBy, provider.getCurrentUser());
            }
        }
        if (fields.updatedBy != null) {
            AuditUserProvider provider = getUserProvider();
            if (provider != null) {
                setFieldValue(entity, fields.updatedBy, provider.getCurrentUser());
            }
        }
    }

    /**
     * 实体更新前的回调，填充更新时间和更新人。
     *
     * @param entity 实体实例
     */
    @PreUpdate
    public void preUpdate(Object entity) {
        AuditFields fields = resolveAuditFields(entity.getClass());
        Instant now = Instant.now();

        if (fields.updatedAt != null) {
            setFieldValue(entity, fields.updatedAt, now);
        }
        if (fields.updatedBy != null) {
            AuditUserProvider provider = getUserProvider();
            if (provider != null) {
                setFieldValue(entity, fields.updatedBy, provider.getCurrentUser());
            }
        }
    }

    /**
     * 解析实体类的审计字段。
     *
     * @param entityClass 实体类
     * @return 审计字段信息
     */
    private static AuditFields resolveAuditFields(Class<?> entityClass) {
        return AUDIT_FIELDS_CACHE.computeIfAbsent(entityClass, cls -> {
            AuditFields fields = new AuditFields();
            for (Field field : cls.getDeclaredFields()) {
                if (field.isAnnotationPresent(CreatedAt.class)) {
                    fields.createdAt = field;
                } else if (field.isAnnotationPresent(UpdatedAt.class)) {
                    fields.updatedAt = field;
                } else if (field.isAnnotationPresent(CreatedBy.class)) {
                    fields.createdBy = field;
                } else if (field.isAnnotationPresent(UpdatedBy.class)) {
                    fields.updatedBy = field;
                }
            }
            return fields;
        });
    }

    /**
     * 设置字段值。
     *
     * @param entity 实体实例
     * @param field 要设置的字段
     * @param value 要设置的值
     */
    private static void setFieldValue(Object entity, Field field, Object value) {
        try {
            field.setAccessible(true);
            Class<?> fieldType = field.getType();
            if (value instanceof Instant instant) {
                if (fieldType == Instant.class) {
                    field.set(entity, instant);
                } else if (fieldType == LocalDateTime.class) {
                    field.set(entity, LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()));
                } else if (fieldType == Date.class) {
                    field.set(entity, Date.from(instant));
                }
            } else if (value instanceof String str) {
                field.set(entity, str);
            }
        } catch (IllegalAccessException e) {
            log.warn("Failed to set audit field {} on entity {}", field.getName(), entity.getClass().getSimpleName(),
                e);
        }
    }

    /** 审计字段信息缓存。 */
    private static final class AuditFields {
        Field createdAt;
        Field updatedAt;
        Field createdBy;
        Field updatedBy;
    }
}
