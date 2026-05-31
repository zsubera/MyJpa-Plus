package com.zsubera.jpa.update;

import com.zsubera.jpa.annotation.SoftDelete;
import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.ConditionNode;
import com.zsubera.jpa.spec.QuerySpec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * 软删除实体辅助工具类。
 *
 * <p>
 * 用于处理带有 {@link SoftDelete @SoftDelete} 注解的实体，提供自动过滤软删除记录的 {@link Specification} 实例。
 *
 * <p>
 * 支持的字段类型：
 * <ul>
 * <li>{@code Boolean} / {@code boolean} — {@code true} 表示"已删除"，{@code false}（或 {@code null}）表示"未删除"</li>
 * <li>{@code Integer} / {@code int} — 通过 {@link SoftDelete#deletedIntValue()} 指定表示"已删除"的整数值（默认 1），其他值表示"未删除"</li>
 * <li>{@code Enum} — 通过 {@link SoftDelete#deletedValue()} 指定表示"已删除"的枚举值名称</li>
 * </ul>
 *
 * <p>
 * 缓存策略：所有缓存均使用 {@link ConcurrentHashMap} 实现线程安全访问。缓存无驱逐策略， 因为实体类数量在实际应用中有限且有界。 FIELD_CACHE 在超过 1024 条目时会记录警告日志，
 * 以帮助诊断潜在的类加载器泄漏问题。NOT_DELETED_SPEC_CACHE 和 DELETED_SPEC_CACHE 按实体类缓存 Specification 实例， 避免每次调用创建新的 lambda。
 *
 * <p>
 * 使用示例：
 *
 * <pre>{@code
 * Specification<Product> notDeleted = SoftDeleteHelper.isNotDeleted(Product.class);
 * List<Product> active = repository.findAll(notDeleted.and(otherSpec));
 * }</pre>
 *
 * @author myjpa-plus
 * @see SoftDelete
 * @see Specification
 */
public final class SoftDeleteHelper {

    private static final int MAX_CACHE_SIZE = 1024;
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(SoftDeleteHelper.class);

    /** 安全标识符段正则：用于校验 schema.table 格式中每一段。 */
    private static final java.util.regex.Pattern SAFE_IDENTIFIER_PART_PATTERN =
        java.util.regex.Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    // 没有@SoftDelete字段的实体的哨兵值（避免在缓存中出现空缓存）
    private static final String NO_FIELD_SENTINEL = "\0";

    // 缓存：entityClass ->字段名（或“无字段”的哨兵）
    // 使用ConcurrentHashMap实现线程安全访问;实体类别数量在实际中是有限的
    // 缓存: entityClass -> 字段名（或"无字段"的哨兵值）
    // 使用弱引用键允许类加载器在 OSGi/热重载场景中被 GC 回收
    private static final ConcurrentMap<Class<?>, String> FIELD_CACHE =
        new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    // 缓存: entityClass -> isNotDeleted Specification
    // 使用弱引用键允许类加载器在 OSGi/热重载场景中被 GC 回收
    private static final ConcurrentMap<Class<?>, Specification<?>> NOT_DELETED_SPEC_CACHE =
        new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    // 缓存: entityClass -> isDeleted Specification
    // 使用弱引用键允许类加载器在 OSGi/热重载场景中被 GC 回收
    private static final ConcurrentMap<Class<?>, Specification<?>> DELETED_SPEC_CACHE =
        new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    /** 缓存: (entityClass, fieldName) -> Field 对象，避免重复反射查找。 */
    private static final ConcurrentMap<String, Field> FIELD_OBJECT_CACHE =
        new ConcurrentReferenceHashMap<>(64, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    /**
     * 转义 SQL 标识符，防止注入。
     *
     * <p>
     * 使用双引号包裹标识符，并验证标识符仅包含安全字符。 支持 schema.table 格式（按点号分段校验每一段）。
     *
     * @param identifier SQL 标识符
     * @return 转义后的标识符
     * @throws IllegalArgumentException 如果标识符包含非法字符
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
        justification = "Utility method used by softDeleteAll and softDeleteByIds")
    static String escapeIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            throw new IllegalArgumentException("Identifier must not be null or empty");
        }
        // P0-2: Support schema.table format by validating each segment separately
        String[] parts = identifier.split("\\.");
        for (String part : parts) {
            if (!SAFE_IDENTIFIER_PART_PATTERN.matcher(part).matches()) {
                throw new IllegalArgumentException("Invalid SQL identifier: '" + identifier
                    + "'. Each part must contain only alphanumeric characters and underscores.");
            }
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private SoftDeleteHelper() {}

    /**
     * P0-5: Batch soft delete all entities of the given class using a single UPDATE statement.
     *
     * @param em EntityManager instance
     * @param entityClass the entity class
     * @param <T> entity type
     * @return number of affected rows
     */
    public static <T> int softDeleteAll(EntityManager em, Class<T> entityClass) {
        if (em == null) {
            throw new IllegalArgumentException("em must not be null");
        }
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        String fieldName = findSoftDeleteField(entityClass);
        if (fieldName == null) {
            throw new IllegalArgumentException("Entity " + entityClass.getSimpleName() + " has no @SoftDelete field");
        }
        String tableName = resolveTableName(entityClass);
        String columnName = resolveColumnName(entityClass, fieldName);
        Field field = getField(entityClass, fieldName);
        if (field == null) {
            throw new IllegalArgumentException("Cannot resolve @SoftDelete field: " + fieldName);
        }
        SoftDelete annotation = field.getAnnotation(SoftDelete.class);
        String escapedTable = escapeIdentifier(tableName);
        String escapedColumn = escapeIdentifier(columnName);
        // Build UPDATE SQL based on field type — use parameterized queries to prevent SQL injection
        if (field.getType() == Boolean.class || field.getType() == boolean.class) {
            return em.createNativeQuery("UPDATE " + escapedTable + " SET " + escapedColumn + " = true WHERE "
                + escapedColumn + " = false OR " + escapedColumn + " IS NULL").executeUpdate();
        }
        if (field.getType() == Integer.class || field.getType() == int.class) {
            int deletedValue = (annotation != null) ? annotation.deletedIntValue() : 1;
            return em.createNativeQuery("UPDATE " + escapedTable + " SET " + escapedColumn + " = ?1 WHERE "
                + escapedColumn + " != ?1 OR " + escapedColumn + " IS NULL").setParameter(1, deletedValue)
                .executeUpdate();
        }
        // Enum: delegate to field-based approach
        throw new MyJpaPlusException("Batch soft delete for enum fields is not supported via native query. "
            + "Use deleteAll() which handles enum fields via entity loading.");
    }

    /**
     * P0-5: Batch soft delete entities by IDs using a single UPDATE statement.
     *
     * @param em EntityManager instance
     * @param entityClass the entity class
     * @param ids the IDs of entities to soft delete
     * @param <T> entity type
     * @param <ID> ID type
     * @return number of affected rows
     */
    public static <T, ID> int softDeleteByIds(EntityManager em, Class<T> entityClass, List<ID> ids) {
        if (em == null) {
            throw new IllegalArgumentException("em must not be null");
        }
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String fieldName = findSoftDeleteField(entityClass);
        if (fieldName == null) {
            throw new IllegalArgumentException("Entity " + entityClass.getSimpleName() + " has no @SoftDelete field");
        }
        String tableName = resolveTableName(entityClass);
        String columnName = resolveColumnName(entityClass, fieldName);
        String idFieldName = resolveIdColumnName(entityClass);
        Field field = getField(entityClass, fieldName);
        if (field == null) {
            throw new IllegalArgumentException("Cannot resolve @SoftDelete field: " + fieldName);
        }
        SoftDelete annotation = field.getAnnotation(SoftDelete.class);
        String escapedTable = escapeIdentifier(tableName);
        String escapedColumn = escapeIdentifier(columnName);
        String escapedIdColumn = escapeIdentifier(idFieldName);
        // P1-4: Use named parameters instead of positional parameters to avoid index conflicts
        // in some JPA implementations (especially when combining SET and IN clause parameters)
        String setParamName = "deletedValue";
        String setClause;
        boolean useParamBinding = false;
        Object deletedParamValue = null;
        if (field.getType() == Boolean.class || field.getType() == boolean.class) {
            setClause = escapedColumn + " = true";
        } else if (field.getType() == Integer.class || field.getType() == int.class) {
            int deletedValue = (annotation != null) ? annotation.deletedIntValue() : 1;
            // P1-4: Use named parameter to avoid positional parameter conflicts
            setClause = escapedColumn + " = :" + setParamName;
            useParamBinding = true;
            deletedParamValue = deletedValue;
        } else {
            throw new MyJpaPlusException("Batch soft delete by IDs for enum fields is not supported via native query.");
        }
        // P1-8: Use InClauseBuilder.getMaxInClauseSize() instead of hardcoded 1000
        int batchSize = com.zsubera.jpa.util.InClauseBuilder.getMaxInClauseSize();
        // Use IN clause with batch splitting for large ID lists
        int total = 0;
        for (int i = 0; i < ids.size(); i += batchSize) {
            List<ID> batch = ids.subList(i, Math.min(i + batchSize, ids.size()));
            StringBuilder placeholders = new StringBuilder();
            for (int j = 0; j < batch.size(); j++) {
                if (j > 0) {
                    placeholders.append(", ");
                }
                placeholders.append(":id").append(j);
            }
            String sql = "UPDATE " + escapedTable + " SET " + setClause + " WHERE " + escapedIdColumn + " IN ("
                + placeholders + ")";
            var query = em.createNativeQuery(sql);
            if (useParamBinding) {
                query.setParameter(setParamName, deletedParamValue);
            }
            for (int j = 0; j < batch.size(); j++) {
                query.setParameter("id" + j, batch.get(j));
            }
            total += query.executeUpdate();
        }
        return total;
    }

    /**
     * Resolve the database table name for the entity class.
     */
    private static String resolveTableName(Class<?> entityClass) {
        jakarta.persistence.Table tableAnnotation = entityClass.getAnnotation(jakarta.persistence.Table.class);
        if (tableAnnotation != null && !tableAnnotation.name().isEmpty()) {
            StringBuilder tableName = new StringBuilder();
            if (!tableAnnotation.catalog().isEmpty()) {
                tableName.append(tableAnnotation.catalog()).append('.');
            }
            if (!tableAnnotation.schema().isEmpty()) {
                tableName.append(tableAnnotation.schema()).append('.');
            }
            tableName.append(tableAnnotation.name());
            return tableName.toString();
        }
        jakarta.persistence.Entity entityAnnotation = entityClass.getAnnotation(jakarta.persistence.Entity.class);
        if (entityAnnotation != null && !entityAnnotation.name().isEmpty()) {
            return entityAnnotation.name();
        }
        return camelToSnake(entityClass.getSimpleName());
    }

    /**
     * Resolve the database column name for a field.
     */
    private static String resolveColumnName(Class<?> entityClass, String fieldName) {
        Field field = getField(entityClass, fieldName);
        if (field != null) {
            jakarta.persistence.Column columnAnnotation = field.getAnnotation(jakarta.persistence.Column.class);
            if (columnAnnotation != null && !columnAnnotation.name().isEmpty()) {
                return columnAnnotation.name();
            }
        }
        return fieldName;
    }

    /**
     * Resolve the ID column name for the entity class.
     */
    private static String resolveIdColumnName(Class<?> entityClass) {
        for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(jakarta.persistence.Id.class)) {
                    jakarta.persistence.Column columnAnnotation = f.getAnnotation(jakarta.persistence.Column.class);
                    if (columnAnnotation != null && !columnAnnotation.name().isEmpty()) {
                        return columnAnnotation.name();
                    }
                    return f.getName();
                }
            }
        }
        throw new IllegalStateException("No @Id field found in " + entityClass.getName());
    }

    /**
     * Convert camelCase to snake_case.
     */
    private static String camelToSnake(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    char prev = name.charAt(i - 1);
                    boolean nextIsLower = (i + 1 < name.length()) && Character.isLowerCase(name.charAt(i + 1));
                    if (Character.isLowerCase(prev) || (Character.isUpperCase(prev) && nextIsLower)) {
                        sb.append('_');
                    }
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 返回排除软删除记录的 {@link Specification}。
     *
     * <p>
     * 对于 {@code Boolean} 类型字段，生成条件为 {@code field = false}；对于引用类型 {@code Boolean} 字段（使用 null 表示"未删除"），生成条件为
     * {@code field IS NULL}。对于 {@code Enum} 类型字段，生成条件为 {@code field != deletedValue}。
     *
     * <p>
     * 结果按实体类缓存，避免每次调用创建新的 lambda 实例。
     *
     * @param entityClass 实体类
     * @param <T> 实体类型
     * @return 排除软删除记录的 Specification
     */
    @SuppressWarnings("unchecked")
    public static <T> Specification<T> isNotDeleted(Class<T> entityClass) {
        return (Specification<T>)NOT_DELETED_SPEC_CACHE.computeIfAbsent(entityClass, cls -> {
            String fieldName = findSoftDeleteField(entityClass);
            if (fieldName == null) {
                return (Specification<T>)(root, query, cb) -> cb.conjunction();
            }
            return (Specification<T>)(root, query, cb) -> buildNotDeleted(cb, root, fieldName, entityClass);
        });
    }

    /**
     * 返回仅匹配软删除记录的 {@link Specification}。
     *
     * <p>
     * 结果按实体类缓存，避免每次调用创建新的 lambda 实例。
     *
     * @param entityClass 实体类
     * @param <T> 实体类型
     * @return 匹配软删除记录的 Specification
     */
    @SuppressWarnings("unchecked")
    public static <T> Specification<T> isDeleted(Class<T> entityClass) {
        return (Specification<T>)DELETED_SPEC_CACHE.computeIfAbsent(entityClass, cls -> {
            String fieldName = findSoftDeleteField(entityClass);
            if (fieldName == null) {
                return (Specification<T>)(root, query, cb) -> cb.disjunction();
            }
            return (Specification<T>)(root, query, cb) -> buildDeleted(cb, root, fieldName, entityClass);
        });
    }

    /**
     * 构建预应用软删除条件的新 {@link QuerySpec}。
     *
     * <p>
     * 注意：与缓存结果的 {@link #isNotDeleted(Class)} 不同，此方法每次调用都会创建新的 {@code QuerySpec} 实例，因为 {@code
     * QuerySpec} 是可变的，不适合共享。
     *
     * @param entityClass 实体类
     * @param <T> 实体类型
     * @return 应用了软删除条件的新 QuerySpec
     */
    public static <T> QuerySpec<T> notDeletedQuery(Class<T> entityClass) {
        String fieldName = findSoftDeleteField(entityClass);
        if (fieldName == null) {
            return new QuerySpec<>();
        }
        QuerySpec<T> qs = new QuerySpec<>();
        // 使用内部工厂方法创建 RawNode，不触发安全审计日志（此谓词不接受用户输入）
        qs.conditions()
            .add(ConditionNode.ofInternalPredicate((path, cb) -> buildNotDeleted(cb, path, fieldName, entityClass)));
        return qs;
    }

    private static Predicate buildNotDeleted(CriteriaBuilder cb, Path<?> path, String fieldName, Class<?> entityClass) {
        Field field = getField(entityClass, fieldName);
        if (field == null) {
            return cb.equal(path.get(fieldName), false);
        }
        SoftDelete annotation = field.getAnnotation(SoftDelete.class);
        // Boolean 类型
        if (field.getType() == Boolean.class || field.getType() == boolean.class) {
            return cb.or(cb.isNull(path.get(fieldName)), cb.equal(path.get(fieldName), false));
        }
        // Integer 类型
        if (field.getType() == Integer.class || field.getType() == int.class) {
            int deletedValue = (annotation != null) ? annotation.deletedIntValue() : 1;
            return cb.or(cb.isNull(path.get(fieldName)), cb.notEqual(path.get(fieldName), deletedValue));
        }
        // 枚举类型
        if (Enum.class.isAssignableFrom(field.getType())) {
            if (annotation == null || annotation.deletedValue().isEmpty()) {
                throw new MyJpaPlusException("@SoftDelete on enum field '" + fieldName + "' in " + entityClass.getName()
                    + " must specify deletedValue");
            }
            Object deletedEnumValue = getEnumConstant(field.getType(), annotation.deletedValue());
            return cb.or(cb.isNull(path.get(fieldName)), cb.notEqual(path.get(fieldName), deletedEnumValue));
        }
        // 不支持的类型：抛出异常而非静默回退
        throw new MyJpaPlusException("@SoftDelete field '" + fieldName + "' in " + entityClass.getName()
            + " has unsupported type: " + field.getType().getName() + ". Supported types: Boolean, Integer, Enum.");
    }

    private static Predicate buildDeleted(CriteriaBuilder cb, Path<?> path, String fieldName, Class<?> entityClass) {
        Field field = getField(entityClass, fieldName);
        if (field == null) {
            return cb.equal(path.get(fieldName), true);
        }
        SoftDelete annotation = field.getAnnotation(SoftDelete.class);
        // Boolean 类型
        if (field.getType() == Boolean.class || field.getType() == boolean.class) {
            return cb.equal(path.get(fieldName), true);
        }
        // Integer 类型
        if (field.getType() == Integer.class || field.getType() == int.class) {
            int deletedValue = (annotation != null) ? annotation.deletedIntValue() : 1;
            return cb.equal(path.get(fieldName), deletedValue);
        }
        // 枚举类型
        if (Enum.class.isAssignableFrom(field.getType())) {
            if (annotation == null || annotation.deletedValue().isEmpty()) {
                throw new MyJpaPlusException("@SoftDelete on enum field '" + fieldName + "' in " + entityClass.getName()
                    + " must specify deletedValue");
            }
            Object deletedEnumValue = getEnumConstant(field.getType(), annotation.deletedValue());
            return cb.equal(path.get(fieldName), deletedEnumValue);
        }
        // 不支持的类型：抛出异常而非静默回退
        throw new MyJpaPlusException("@SoftDelete field '" + fieldName + "' in " + entityClass.getName()
            + " has unsupported type: " + field.getType().getName() + ". Supported types: Boolean, Integer, Enum.");
    }

    /**
     * 获取枚举类型的指定常量。
     *
     * @param enumType 枚举类型
     * @param constantName 枚举常量名称
     * @return 枚举常量
     * @throws IllegalStateException 如果枚举常量不存在
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object getEnumConstant(Class<?> enumType, String constantName) {
        try {
            return Enum.valueOf((Class<Enum>)enumType, constantName);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(String.format(
                "Enum constant '%s' not found in %s. " + "Please check the @SoftDelete(deletedValue) configuration.",
                constantName, enumType.getName()), e);
        }
    }

    /**
     * 查找实体类上带有 {@link SoftDelete @SoftDelete} 注解的字段名称。
     *
     * <p>
     * 会遍历类层次结构（包括父类）。结果按实体类缓存。
     *
     * @param entityClass 要扫描的实体类
     * @return 字段名称，如果未找到 {@code @SoftDelete} 字段则返回 {@code null}
     */
    public static String findSoftDeleteField(Class<?> entityClass) {
        // 使用 cache.size() 检查缓存大小，超过阈值时记录警告
        // ConcurrentReferenceHashMap 使用弱引用键，GC 会自动回收不再引用的条目
        // 这里的大小检查仅用于诊断目的，不需要手动清理
        int currentSize = FIELD_CACHE.size();
        if (currentSize > MAX_CACHE_SIZE) {
            log.warn("SoftDeleteHelper field cache size ({}) exceeds limit ({}). "
                + "This may indicate a class loader leak or excessive entity classes. "
                + "Weak reference entries will be cleaned by GC automatically.", currentSize, MAX_CACHE_SIZE);
        }
        String result = FIELD_CACHE.computeIfAbsent(entityClass, cls -> {
            // Try getter-based resolution first (Java 17+ compatible)
            String viaGetter = resolveSoftDeleteFieldNameViaGetter(cls);
            if (viaGetter != null) {
                return viaGetter;
            }
            // Fallback to field-based reflection
            for (Field field : getAllFields(cls)) {
                if (field.isAnnotationPresent(SoftDelete.class)) {
                    try {
                        field.setAccessible(true);
                    } catch (SecurityException e) {
                        throw new IllegalStateException(
                            "Cannot set accessible on @SoftDelete field '" + field.getName() + "' in "
                                + cls.getSimpleName() + ". " + "If using Java 17+ module system, add JVM argument: "
                                + "--add-opens java.base/java.lang.reflect=ALL-UNNAMED",
                            e);
                    }
                    return field.getName();
                }
            }
            return NO_FIELD_SENTINEL;
        });
        return NO_FIELD_SENTINEL.equals(result) ? null : result;
    }

    /**
     * Resolve soft delete field name via getter methods (Java 17+ compatible). Scans public methods for @SoftDelete
     * annotation on getters.
     *
     * @param entityClass the entity class to scan
     * @return the field name, or null if not found via getters
     */
    private static String resolveSoftDeleteFieldNameViaGetter(Class<?> entityClass) {
        for (Method m : entityClass.getMethods()) {
            SoftDelete ann = m.getAnnotation(SoftDelete.class);
            if (ann != null && m.getParameterCount() == 0) {
                String name = m.getName();
                if (name.startsWith("get") && name.length() > 3) {
                    return Character.toLowerCase(name.charAt(3)) + name.substring(4);
                }
                if (name.startsWith("is") && name.length() > 2 && Character.isUpperCase(name.charAt(2))) {
                    return Character.toLowerCase(name.charAt(2)) + name.substring(3);
                }
            }
        }
        return null;
    }

    private static Field getField(Class<?> entityClass, String fieldName) {
        String cacheKey = entityClass.getName() + "#" + fieldName;
        return FIELD_OBJECT_CACHE.computeIfAbsent(cacheKey, k -> {
            Class<?> current = entityClass;
            while (current != null && current != Object.class) {
                try {
                    return current.getDeclaredField(fieldName);
                } catch (NoSuchFieldException e) {
                    current = current.getSuperclass();
                }
            }
            return null;
        });
    }

    /**
     * 检查给定实体是否被标记为软删除。
     *
     * @param entityClass 实体类
     * @param entity 实体实例
     * @return 如果实体已软删除返回 {@code true}，否则返回 {@code false}
     * @throws IllegalArgumentException 如果实体类或实体实例为 {@code null}
     */
    @SuppressWarnings({"rawtypes"})
    public static <T> boolean isSoftDeleted(Class<T> entityClass, T entity) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
        String fieldName = findSoftDeleteField(entityClass);
        if (fieldName == null) {
            return false;
        }
        Field field = getField(entityClass, fieldName);
        if (field == null) {
            return false;
        }
        try {
            field.setAccessible(true);
        } catch (SecurityException e) {
            log.warn(
                "Cannot set accessible on field '{}' in {}. " + "If using Java 17+ module system, add JVM argument: "
                    + "--add-opens java.base/java.lang.reflect=ALL-UNNAMED",
                field.getName(), entityClass.getSimpleName());
            throw new MyJpaPlusException("Cannot access soft delete field '" + fieldName + "'", e);
        }
        try {
            Object value = field.get(entity);
            if (value == null) {
                return false;
            }
            // Boolean type
            if (value instanceof Boolean) {
                return Boolean.TRUE.equals(value);
            }
            // Integer type
            if (value instanceof Integer intValue && field.isAnnotationPresent(SoftDelete.class)) {
                SoftDelete annotation = field.getAnnotation(SoftDelete.class);
                if (annotation != null) {
                    return intValue.equals(annotation.deletedIntValue());
                }
            }
            // Enum type
            if (value instanceof Enum enumValue && field.isAnnotationPresent(SoftDelete.class)) {
                SoftDelete annotation = field.getAnnotation(SoftDelete.class);
                if (annotation != null && !annotation.deletedValue().isEmpty()) {
                    return enumValue.name().equals(annotation.deletedValue());
                }
            }
            return false;
        } catch (ReflectiveOperationException e) {
            throw new MyJpaPlusException(String.format(
                "Failed to read soft delete field '%s' from entity %s. "
                    + "If using Java 17+ module system, add JVM argument: "
                    + "--add-opens java.base/java.lang.reflect=ALL-UNNAMED",
                fieldName, entity.getClass().getSimpleName()), e);
        }
    }

    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new java.util.ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                // 过滤静态字段和合成字段，只返回实例字段
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
            clazz = clazz.getSuperclass();
        }
        return fields;
    }
}
