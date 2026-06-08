package com.zsubera.jpa.annotation;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.util.ConcurrentReferenceHashMap;

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
 * <pre>{@code
 * @Entity
 * @EntityListeners(AuditEntityListener.class)
 * public class User {
 *     @CreatedAt
 *     private Instant createdAt;
 *
 *     @UpdatedAt
 *     private Instant updatedAt;
 *
 *     @CreatedBy
 *     private String createdBy;
 *
 *     @UpdatedBy
 *     private String updatedBy;
 * }
 * }</pre>
 *
 * <p>
 * <strong>注意：</strong>此类不使用 {@code @Component} 注解，而是通过 {@link com.zsubera.jpa.autoconfigure.MyJpaPlusAutoConfiguration}
 * 注册为 Bean。 这是因为 JPA {@code @EntityListeners} 机制与 Spring {@code @Component} 的生命周期不同， 同时使用会导致两个身份混淆。
 *
 * @author myjpa-plus
 * @since 1.2.0
 */
public class AuditEntityListener implements ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(AuditEntityListener.class);

    /**
     * 实体类 -> 审计字段的缓存。
     *
     * <p>
     * 使用弱引用键（{@link ConcurrentReferenceHashMap.ReferenceType#WEAK}）， 允许 GC 在热部署场景下回收旧类加载器的条目，防止类加载器泄漏。
     */
    private static final Map<Class<?>, AuditFields> AUDIT_FIELDS_CACHE =
        new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    private static volatile ApplicationContext applicationContext;
    private static volatile AuditUserProvider userProvider;

    /** 哨兵值，表示未找到 AuditUserProvider Bean，防止重复查找。 */
    private static final AuditUserProvider NO_PROVIDER_SENTINEL = () -> null;

    /** 标记是否已尝试查找 provider。 */
    private static volatile boolean providerLookupAttempted = false;

    /** 审计时间戳的可配置时区，默认使用系统时区。 */
    private static volatile java.time.ZoneId auditZoneId = java.time.ZoneId.systemDefault();

    @Override
    public void setApplicationContext(@NonNull ApplicationContext ctx) {
        applicationContext = ctx;
    }

    /**
     * 清理静态资源引用。在 Bean 销毁时调用，防止内存泄漏。
     */
    public static void destroy() {
        applicationContext = null;
        userProvider = null;
        providerLookupAttempted = false;
    }

    /**
     * 设置审计时间戳使用的时区。由自动配置类调用。
     *
     * @param zoneId 时区 ID，如 "UTC"、"Asia/Shanghai"
     */
    public static void setAuditZoneId(java.time.ZoneId zoneId) {
        if (zoneId != null) {
            auditZoneId = zoneId;
        }
    }

    /**
     * 获取 AuditUserProvider 实例（延迟初始化，线程安全）。
     *
     * <p>
     * 使用哨兵值 NO_PROVIDER_SENTINEL 防止缓存失败后无限重入。
     *
     * @return AuditUserProvider 实例，如果未配置则返回 null
     */
    private static AuditUserProvider getUserProvider() {
        if (providerLookupAttempted) {
            return userProvider == NO_PROVIDER_SENTINEL ? null : userProvider;
        }
        if (applicationContext != null) {
            synchronized (AuditEntityListener.class) {
                if (!providerLookupAttempted) {
                    try {
                        userProvider = applicationContext.getBean(AuditUserProvider.class);
                    } catch (Exception e) {
                        log.debug("No AuditUserProvider bean found, createdBy/updatedBy will not be auto-filled");
                        // 设置哨兵值，防止重复查找
                        userProvider = NO_PROVIDER_SENTINEL;
                    }
                    providerLookupAttempted = true;
                }
            }
        }
        return userProvider == NO_PROVIDER_SENTINEL ? null : userProvider;
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
        // 获取 provider 一次，避免重复查找
        AuditUserProvider provider = (fields.createdBy != null || fields.updatedBy != null) ? getUserProvider() : null;
        if (provider != null) {
            // 只调用一次 getCurrentUser()，避免重复查找和潜在的不一致
            String currentUser = provider.getCurrentUser();
            if (fields.createdBy != null) {
                setFieldValue(entity, fields.createdBy, currentUser);
            }
            if (fields.updatedBy != null) {
                setFieldValue(entity, fields.updatedBy, currentUser);
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
     * <p>
     * 预设置 {@code setAccessible(true)} 以减少每次实体操作的反射开销。
     *
     * @param entityClass 实体类
     * @return 审计字段信息
     */
    private static AuditFields resolveAuditFields(Class<?> entityClass) {
        return AUDIT_FIELDS_CACHE.computeIfAbsent(entityClass, cls -> {
            AuditFields fields = new AuditFields();
            // 遍历完整类层次结构，查找父类中的审计字段
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (field.isAnnotationPresent(CreatedAt.class)) {
                        if (fields.createdAt == null) {
                            field.setAccessible(true);
                            fields.createdAt = field;
                        }
                    } else if (field.isAnnotationPresent(UpdatedAt.class)) {
                        if (fields.updatedAt == null) {
                            field.setAccessible(true);
                            fields.updatedAt = field;
                        }
                    } else if (field.isAnnotationPresent(CreatedBy.class)) {
                        if (fields.createdBy == null) {
                            field.setAccessible(true);
                            fields.createdBy = field;
                        }
                    } else if (field.isAnnotationPresent(UpdatedBy.class)) {
                        if (fields.updatedBy == null) {
                            field.setAccessible(true);
                            fields.updatedBy = field;
                        }
                    }
                }
            }
            return fields;
        });
    }

    /**
     * 设置字段值。
     *
     * <p>
     * 注意：字段的可访问性已在 {@link #resolveAuditFields(Class)} 中预设置，无需重复调用。
     *
     * @param entity 实体实例
     * @param field 要设置的字段
     * @param value 要设置的值
     */
    private static void setFieldValue(Object entity, Field field, Object value) {
        try {
            Class<?> fieldType = field.getType();
            if (value instanceof Instant instant) {
                if (fieldType == Instant.class) {
                    field.set(entity, instant);
                } else if (fieldType == LocalDateTime.class) {
                    // 使用可配置的时区，而非系统默认时区
                    field.set(entity, LocalDateTime.ofInstant(instant, auditZoneId));
                } else if (fieldType == Date.class) {
                    field.set(entity, Date.from(instant));
                }
            } else if (value instanceof String str) {
                if (fieldType == String.class) {
                    field.set(entity, str);
                } else if (fieldType == Long.class || fieldType == long.class) {
                    try {
                        field.set(entity, Long.parseLong(str));
                    } catch (NumberFormatException nfe) {
                        log.warn("Cannot convert audit value '{}' to Long for field {}", str, field.getName());
                    }
                } else if (fieldType == Integer.class || fieldType == int.class) {
                    try {
                        field.set(entity, Integer.parseInt(str));
                    } catch (NumberFormatException nfe) {
                        log.warn("Cannot convert audit value '{}' to Integer for field {}", str, field.getName());
                    }
                } else {
                    log.warn("Unsupported audit field type {} for field {} on entity {}", fieldType.getSimpleName(),
                        field.getName(), entity.getClass().getSimpleName());
                }
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
