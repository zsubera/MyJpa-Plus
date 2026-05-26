package com.zsubera.jpa.update;

import com.zsubera.jpa.annotation.SoftDelete;
import com.zsubera.jpa.spec.QuerySpec;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

    // Sentinel value for entities without a @SoftDelete field (avoids null in cache)
    private static final String NO_FIELD_SENTINEL = "\0";

    // Cache: entityClass -> field name (or sentinel for "no field")
    private static final Map<Class<?>, String> FIELD_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Class<?>, String> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });

    // Cache: entityClass -> Specification for isNotDeleted
    private static final ConcurrentMap<Class<?>, Specification<?>> NOT_DELETED_SPEC_CACHE = new ConcurrentHashMap<>();

    // Cache: entityClass -> Specification for isDeleted
    private static final ConcurrentMap<Class<?>, Specification<?>> DELETED_SPEC_CACHE = new ConcurrentHashMap<>();

    private SoftDeleteHelper() {
    }

    /**
     * Returns a {@link Specification} that excludes soft-deleted records.
     * The generated condition is {@code field = false} for {@code Boolean} fields,
     * or {@code field IS NULL} for reference {@code Boolean} fields that use null for "not deleted".
     * <p>
     * The result is cached per entity class to avoid creating new lambda instances on every call.
     */
    @SuppressWarnings("unchecked")
    public static <T> Specification<T> isNotDeleted(Class<T> entityClass) {
        return (Specification<T>) NOT_DELETED_SPEC_CACHE.computeIfAbsent(entityClass, cls -> {
            String fieldName = findSoftDeleteField(entityClass);
            if (fieldName == null) {
                return (Specification<T>) (root, query, cb) -> cb.conjunction();
            }
            return (Specification<T>) (root, query, cb) -> buildNotDeleted(cb, root, fieldName, entityClass);
        });
    }

    /**
     * Returns a {@link Specification} that matches only soft-deleted records.
     * <p>
     * The result is cached per entity class to avoid creating new lambda instances on every call.
     */
    @SuppressWarnings("unchecked")
    public static <T> Specification<T> isDeleted(Class<T> entityClass) {
        return (Specification<T>) DELETED_SPEC_CACHE.computeIfAbsent(entityClass, cls -> {
            String fieldName = findSoftDeleteField(entityClass);
            if (fieldName == null) {
                return (Specification<T>) (root, query, cb) -> cb.conjunction();
            }
            return (Specification<T>) (root, query, cb) -> buildDeleted(cb, root, fieldName);
        });
    }

    /**
     * Builds a new {@link QuerySpec} with the soft-delete condition pre-applied.
     * <p>
     * Note: Unlike {@link #isNotDeleted(Class)} which caches the result,
     * this method creates a new {@code QuerySpec} instance on each call
     * because {@code QuerySpec} is mutable and not safe for sharing.
     *
     * @param entityClass the entity class
     * @param <T> the entity type
     * @return a new QuerySpec with the soft-delete condition applied
     */
    @SuppressWarnings("unchecked")
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

    private static Predicate buildDeleted(CriteriaBuilder cb, Path<?> path, String fieldName) {
        return cb.equal(path.get(fieldName), true);
    }

    private static String findSoftDeleteField(Class<?> entityClass) {
        String result = FIELD_CACHE.computeIfAbsent(entityClass, cls -> {
            for (Field field : getAllFields(cls)) {
                if (field.isAnnotationPresent(SoftDelete.class)) {
                    field.setAccessible(true);
                    return field.getName();
                }
            }
            return NO_FIELD_SENTINEL;
        });
        return NO_FIELD_SENTINEL.equals(result) ? null : result;
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

    /**
     * Checks whether the given entity is marked as soft-deleted.
     *
     * @param entityClass the entity class
     * @param entity the entity instance
     * @return true if the entity is soft-deleted, false otherwise
     */
    public static <T> boolean isSoftDeleted(Class<T> entityClass, T entity) {
        String fieldName = findSoftDeleteField(entityClass);
        if (fieldName == null) {
            return false;
        }
        Field field = getField(entityClass, fieldName);
        if (field == null) {
            return false;
        }
        field.setAccessible(true);
        try {
            return Boolean.TRUE.equals(field.get(entity));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new java.util.ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                fields.add(field);
            }
            clazz = clazz.getSuperclass();
        }
        return fields;
    }
}
