package com.zsubera.jpa.autoconfigure;

import com.zsubera.jpa.annotation.SoftDelete;
import com.zsubera.jpa.update.SoftDeleteHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Auto-filter bean that enables transparent soft-delete filtering.
 * <p>
 * When {@code myjpa-plus.soft-delete.auto-filter} is enabled, this bean
 * tracks all entity classes with {@link SoftDelete @SoftDelete} annotations
 * and provides a mechanism to automatically apply soft-delete filters.
 * <p>
 * Usage in repositories:
 * <pre>{@code
 * // Apply auto-filter to any query
 * Specification<User> spec = userQuerySpec();
 * Specification<User> filtered = softDeleteFilterBean.apply(spec, User.class);
 * }</pre>
 * <p>
 * The bean is automatically activated when {@code auto-filter: true} is set
 * in configuration (default).
 */
@Component
public class SoftDeleteFilterBean implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(SoftDeleteFilterBean.class);

    private final List<Class<?>> softDeleteEntities = new CopyOnWriteArrayList<>();

    @Override
    public void afterPropertiesSet() {
        // Scan for entity classes with @SoftDelete is done dynamically
        // when apply() is called, cached for performance
        log.debug("SoftDeleteFilterBean initialized");
    }

    /**
     * Registers an entity class for auto-filtering.
     * Called by custom repository implementations.
     */
    public void registerEntity(Class<?> entityClass) {
        if (!softDeleteEntities.contains(entityClass) && hasSoftDeleteField(entityClass)) {
            softDeleteEntities.add(entityClass);
            log.debug("Registered {} for soft-delete auto-filtering", entityClass.getSimpleName());
        }
    }

    /**
     * Applies the soft-delete filter to the given specification if the entity
     * has a {@link SoftDelete @SoftDelete} annotated field.
     *
     * @param spec        the original specification
     * @param entityClass the entity class
     * @param <T>         the entity type
     * @return the combined specification with soft-delete filter applied,
     *         or the original specification if the entity has no soft-delete field
     */
    @SuppressWarnings("unchecked")
    public <T> Specification<T> apply(Specification<T> spec, Class<T> entityClass) {
        if (hasSoftDeleteField(entityClass)) {
            Specification<T> notDeleted = SoftDeleteHelper.isNotDeleted(entityClass);
            return spec == null ? notDeleted : spec.and(notDeleted);
        }
        return spec;
    }

    /**
     * Checks whether the given entity class has a {@link SoftDelete @SoftDelete} field.
     */
    public boolean hasSoftDeleteField(Class<?> entityClass) {
        return softDeleteEntities.contains(entityClass) || scanForSoftDelete(entityClass);
    }

    private boolean scanForSoftDelete(Class<?> entityClass) {
        for (Field field : getAllFields(entityClass)) {
            if (field.isAnnotationPresent(SoftDelete.class)) {
                softDeleteEntities.add(entityClass);
                return true;
            }
        }
        return false;
    }

    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }
}
