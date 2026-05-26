package com.zsubera.jpa.update;

import com.zsubera.jpa.annotation.SoftDelete;
import com.zsubera.jpa.spec.QuerySpec;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper for working with {@link SoftDelete @SoftDelete} annotated entities.
 * <p>
 * Provides {@link Specification} instances that automatically filter
 * soft-deleted records using the annotated field.
 * <p>
 * Example:
 * <pre>{@code
 * Specification<Product> notDeleted = SoftDeleteHelper.isNotDeleted(Product.class);
 * List<Product> active = repository.findAll(notDeleted.and(otherSpec));
 * }</pre>
 */
public final class SoftDeleteHelper {

    private static final int MAX_CACHE_SIZE = 1024;
    private static final Map<Class<?>, String> FIELD_CACHE = new ConcurrentHashMap<>();

    private SoftDeleteHelper() {
    }

    /**
     * Returns a {@link Specification} that excludes soft-deleted records.
     * The generated condition is {@code field = false} for {@code Boolean} fields,
     * or {@code field IS NULL} for reference {@code Boolean} fields that use null for "not deleted".
     */
    public static <T> Specification<T> isNotDeleted(Class<T> entityClass) {
        String fieldName = findSoftDeleteField(entityClass);
        if (fieldName == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> buildNotDeleted(cb, root, fieldName, entityClass);
    }

    /**
     * Returns a {@link Specification} that matches only soft-deleted records.
     */
    public static <T> Specification<T> isDeleted(Class<T> entityClass) {
        String fieldName = findSoftDeleteField(entityClass);
        if (fieldName == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> buildDeleted(cb, root, fieldName, entityClass);
    }

    /**
     * Builds a {@link QuerySpec} with the soft-delete condition pre-applied.
     */
    public static <T> QuerySpec<T> notDeletedQuery(Class<T> entityClass) {
        String fieldName = findSoftDeleteField(entityClass);
        if (fieldName == null) {
            return new QuerySpec<>();
        }
        QuerySpec<T> qs = new QuerySpec<>();
        qs.where((path, cb) -> buildNotDeleted(cb, path, fieldName, entityClass));
        return qs;
    }

    private static Predicate buildNotDeleted(CriteriaBuilder cb, Path<?> path, String fieldName, Class<?> entityClass) {
        Field field = getField(entityClass, fieldName);
        if (field != null && field.getType() == Boolean.class) {
            return cb.or(cb.isNull(path.get(fieldName)), cb.equal(path.get(fieldName), false));
        }
        return cb.equal(path.get(fieldName), false);
    }

    private static Predicate buildDeleted(CriteriaBuilder cb, Path<?> path, String fieldName, Class<?> entityClass) {
        return cb.equal(path.get(fieldName), true);
    }

    private static String findSoftDeleteField(Class<?> entityClass) {
        String cached = FIELD_CACHE.get(entityClass);
        if (cached != null) {
            return cached;
        }
        if (FIELD_CACHE.size() >= MAX_CACHE_SIZE) {
            FIELD_CACHE.clear();
        }
        return FIELD_CACHE.computeIfAbsent(entityClass, cls -> {
            for (Field field : getAllFields(cls)) {
                if (field.isAnnotationPresent(SoftDelete.class)) {
                    field.setAccessible(true);
                    return field.getName();
                }
            }
            return "";
        });
    }

    private static Field getField(Class<?> entityClass, String fieldName) {
        try {
            return entityClass.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            Class<?> sup = entityClass.getSuperclass();
            if (sup != null && sup != Object.class) {
                return getField(sup, fieldName);
            }
            return null;
        }
    }

    private static java.util.List<Field> getAllFields(Class<?> clazz) {
        java.util.List<Field> fields = new java.util.ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                fields.add(field);
            }
            clazz = clazz.getSuperclass();
        }
        return fields;
    }
}
