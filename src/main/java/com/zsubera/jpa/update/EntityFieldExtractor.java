package com.zsubera.jpa.update;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.util.IdentifierValidator;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * 实体字段值提取器。
 *
 * <p>
 * 从 JPA 实体中提取字段名、列名和值的映射，支持 {@code @Embedded} 嵌套提取。
 * 使用 getter 优先策略减少 {@code --add-opens} 需求。
 *
 * <p>
 * 缓存策略：字段列表使用弱引用键缓存（防止类加载器泄漏），自动生成 ID 检测结果使用强引用缓存。
 *
 * @param <T> 实体类型

 */
final class EntityFieldExtractor<T> {

    private static final Logger log = LoggerFactory.getLogger(EntityFieldExtractor.class);

    /** 缓存实体类的持久化字段列表，避免每次反射遍历。使用弱引用键防止类加载器泄漏。 */
    private static final ConcurrentReferenceHashMap<Class<?>, List<Field>> FIELD_CACHE =
        new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    /** getAllFields() 使用独立缓存，避免与 extractFieldValues() 的不同过滤器共享缓存。 */
    private static final ConcurrentReferenceHashMap<Class<?>, List<Field>> ALL_FIELDS_CACHE =
        new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    private static final int MAX_FIELD_CACHE_SIZE = 1024;

    /** 采样概率分母——每 1024 次调用检查一次缓存大小 */
    private static final int CACHE_CHECK_SAMPLING = 1024;

    /** 自动生成 ID 字段检测结果的缓存。使用弱引用键防止类加载器泄漏。 */
    private static final ConcurrentReferenceHashMap<String, Boolean> AUTO_GENERATED_ID_CACHE =
        new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    /** 缓存 resolveIdColumnNames 的结果，避免每次 MergeSpec 调用时重复反射扫描 */
    private static final java.util.concurrent.ConcurrentMap<String, List<String>> ID_COLUMN_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>(16);

    /** 缓存 resolveJavaFieldToDbColumn 的结果，避免每次跨实例调用时重复反射扫描类层次 */
    private static final java.util.concurrent.ConcurrentMap<String, String> JAVA_FIELD_TO_DB_COLUMN_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>(64);

    private final Class<T> entityClass;

    EntityFieldExtractor(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * 字段值记录，包含 Java 字段名、数据库列名和字段值。
     *
     * @param fieldName Java 字段名
     * @param columnName 数据库列名
     * @param value 字段值
     */
    record EntityFieldValue(String fieldName, String columnName, Object value) {
    }

    /**
     * 从实体提取所有字段值。
     *
     * @param entity 要提取字段值的实体实例
     * @return 字段名、列名和值的列表
     */
    List<EntityFieldValue> extractFieldValues(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
        return extractFieldValues(entity, new java.util.HashSet<>());
    }

    private List<EntityFieldValue> extractFieldValues(Object entity, Set<Object> visited) {
        List<EntityFieldValue> fieldValues = new ArrayList<>();
        if (!visited.add(entity)) {
            throw new MyJpaPlusException("Circular @Embedded reference detected: " + entity.getClass().getName()
                + " has already been visited. Check your entity mapping for cycles in @Embedded objects.");
        }
        // 使用采样策略——随机采样检查缓存大小以减少开销
        if (java.util.concurrent.ThreadLocalRandom.current().nextInt(CACHE_CHECK_SAMPLING) == 0) {
            int cacheSize = FIELD_CACHE.size();
            if (cacheSize > MAX_FIELD_CACHE_SIZE) {
                log.warn(
                    "EntityFieldExtractor field cache size ({}) exceeds limit ({}). "
                        + "This may indicate a class loader leak. Weak reference entries will be cleaned by GC.",
                    cacheSize, MAX_FIELD_CACHE_SIZE);
            }
        }
        // 使用实际实体类作为缓存键
        Class<?> effectiveClass = entity.getClass();
        List<Field> allFields = FIELD_CACHE.computeIfAbsent(effectiveClass, cls -> {
            List<Field> fields = new ArrayList<>();
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())
                        && !java.lang.reflect.Modifier.isTransient(f.getModifiers())
                        && !f.isAnnotationPresent(jakarta.persistence.Transient.class)
                        && !f.isAnnotationPresent(jakarta.persistence.OneToMany.class)
                        && !f.isAnnotationPresent(jakarta.persistence.ManyToOne.class)
                        && !f.isAnnotationPresent(jakarta.persistence.ManyToMany.class)
                        && !f.isAnnotationPresent(jakarta.persistence.OneToOne.class)
                        && !f.isAnnotationPresent(jakarta.persistence.EmbeddedId.class) && !isInsertableFalse(f)) {
                        fields.add(f);
                    }
                }
            }
            return List.copyOf(fields);
        });
        for (Field f : allFields) {
            try {
                if (f.isAnnotationPresent(jakarta.persistence.Embedded.class)) {
                    Object embeddedValue = getFieldValue(entity, f);
                    if (embeddedValue != null) {
                        jakarta.persistence.AttributeOverride[] overrides =
                            f.getAnnotationsByType(jakarta.persistence.AttributeOverride.class);
                        java.util.Map<String, String> overrideMap = new java.util.LinkedHashMap<>();
                        for (jakarta.persistence.AttributeOverride override : overrides) {
                            overrideMap.put(override.name(), override.column().name());
                        }
                        extractEmbeddedFields(embeddedValue, f.getName(), overrideMap, fieldValues, visited);
                    }
                } else {
                    Object value = getFieldValue(entity, f);
                    String columnName = resolveColumnName(f);
                    fieldValues.add(new EntityFieldValue(f.getName(), columnName, value));
                }
            } catch (MyJpaPlusException e) {
                throw e;
            } catch (Exception e) {
                throw new MyJpaPlusException(
                    "Failed to access field: " + f.getName() + ". If using Java 17+ module system, add JVM argument: "
                        + "--add-opens " + f.getDeclaringClass().getPackageName() + "=ALL-UNNAMED",
                    e);
            }
        }
        return fieldValues;
    }

    /**
     * 递归提取 @Embedded 字段中的子字段，支持嵌套 @Embedded。
     */
    private void extractEmbeddedFields(Object embeddedValue, String prefix, java.util.Map<String, String> overrideMap,
        List<EntityFieldValue> fieldValues, Set<Object> visited) throws Exception {
        for (Field subField : getAllFields(embeddedValue.getClass())) {
            if (java.lang.reflect.Modifier.isStatic(subField.getModifiers()) || subField.isSynthetic()) {
                continue;
            }
            if (subField.isAnnotationPresent(jakarta.persistence.Embedded.class)) {
                // 递归处理嵌套的 @Embedded
                Object nestedValue = getFieldValue(embeddedValue, subField);
                if (nestedValue != null) {
                    // ponytail: 跟踪对象实例而非 Class，允许同一 Embeddable 类型的不同实例共存
                    if (!visited.add(nestedValue)) {
                        throw new MyJpaPlusException("Circular @Embedded reference detected in field '" + prefix + "."
                            + subField.getName() + "': " + nestedValue.getClass().getName());
                    }
                    jakarta.persistence.AttributeOverride[] nestedOverrides =
                        subField.getAnnotationsByType(jakarta.persistence.AttributeOverride.class);
                    java.util.Map<String, String> nestedOverrideMap = new java.util.LinkedHashMap<>(overrideMap);
                    for (jakarta.persistence.AttributeOverride ov : nestedOverrides) {
                        nestedOverrideMap.put(ov.name(), ov.column().name());
                    }
                    extractEmbeddedFields(nestedValue, prefix + "." + subField.getName(), nestedOverrideMap,
                        fieldValues, visited);
                    visited.remove(nestedValue);
                }
            } else {
                Object subValue = getFieldValue(embeddedValue, subField);
                String columnName = overrideMap.getOrDefault(subField.getName(), resolveColumnName(subField));
                fieldValues.add(new EntityFieldValue(prefix + "." + subField.getName(), columnName, subValue));
            }
        }
    }

    /**
     * 从实体类层次结构中获取所有字段，包括继承的字段。结果使用 FIELD_CACHE 缓存。
     *
     * @param clazz 要扫描的类
     * @return 所有字段列表（不包括静态和合成字段）
     */
    private static List<Field> getAllFields(Class<?> clazz) {
        return ALL_FIELDS_CACHE.computeIfAbsent(clazz, cls -> {
            List<Field> fields = new ArrayList<>();
            Class<?> current = cls;
            while (current != null && current != Object.class) {
                for (Field f : current.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) && !f.isSynthetic()
                        && !f.isAnnotationPresent(jakarta.persistence.Transient.class)
                        && !f.isAnnotationPresent(jakarta.persistence.OneToMany.class)
                        && !f.isAnnotationPresent(jakarta.persistence.ManyToOne.class)
                        && !f.isAnnotationPresent(jakarta.persistence.ManyToMany.class)
                        && !f.isAnnotationPresent(jakarta.persistence.OneToOne.class) && !isInsertableFalse(f)) {
                        fields.add(f);
                    }
                }
                current = current.getSuperclass();
            }
            return List.copyOf(fields);
        });
    }

    /**
     * 获取实体字段值，优先使用 getter 方法，回退到字段反射。
     *
     * @param entity 实体实例
     * @param field 字段
     * @return 字段值
     * @throws Exception 如果无法访问字段
     */
    private Object getFieldValue(Object entity, Field field) throws Exception {
        Class<?> cls = entity.getClass();
        String clsName = cls.getName();
        int idx = clsName.indexOf("$$");
        if (idx > 0) {
            cls = cls.getSuperclass();
        }
        String fieldName = field.getName();
        // 尝试 getXxx() getter 方法
        String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        try {
            java.lang.reflect.Method getter = cls.getMethod(getterName);
            return getter.invoke(entity);
        } catch (NoSuchMethodException ignored) {
            // getter 不可用
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getTargetException() != null ? new Exception(e.getTargetException()) : e;
        }
        // 尝试 isXxx() getter（boolean 类型）
        if (field.getType() == boolean.class || field.getType() == Boolean.class) {
            String isGetterName = "is" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            try {
                java.lang.reflect.Method isGetter = cls.getMethod(isGetterName);
                return isGetter.invoke(entity);
            } catch (NoSuchMethodException ignored) {
                // is-getter 不可用
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getTargetException() != null ? new Exception(e.getTargetException()) : e;
            }
        }
        // 回退到字段反射
        try {
            field.setAccessible(true);
        } catch (InaccessibleObjectException e) {
            throw new MyJpaPlusException("Cannot access field '" + fieldName + "' in " + cls.getName()
                + ". If using Java 17+ module system, add JVM argument: " + "--add-opens "
                + field.getDeclaringClass().getPackageName() + "=ALL-UNNAMED", e);
        }
        return field.get(entity);
    }

    /**
     * 检查字段是否为自动生成的 @Id 字段。结果被缓存以避免重复反射扫描。
     *
     * @param fieldName Java 字段名
     * @return 如果是自动生成的 ID 字段则返回 true
     */
    boolean isAutoGeneratedId(String fieldName) {
        String cacheKey = entityClass.getName() + "#" + fieldName;
        boolean result = AUTO_GENERATED_ID_CACHE.computeIfAbsent(cacheKey, k -> {
            for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField(fieldName);
                    if (f.isAnnotationPresent(Id.class)) {
                        jakarta.persistence.GeneratedValue gva =
                            f.getAnnotation(jakarta.persistence.GeneratedValue.class);
                        return gva != null;
                    }
                    if (f.isAnnotationPresent(jakarta.persistence.EmbeddedId.class)) {
                        return true;
                    }
                } catch (NoSuchFieldException ignored) {
                    // 继续检查父类
                }
            }
            return false;
        });
        return result;
    }

    /**
     * 检查字段是否标注了 {@code @Column(insertable = false)}。
     */
    private static boolean isInsertableFalse(Field f) {
        Column column = f.getAnnotation(Column.class);
        return column != null && !column.insertable();
    }

    /**
     * 从实体类层次结构中解析 @Id 注解字段对应的数据库列名。
     *
     * @return ID 列名列表
     * @throws IllegalStateException 如果实体类没有 @Id 或 @EmbeddedId 注解的字段
     */
    List<String> resolveIdColumnNames() {
        String cacheKey = entityClass.getName();
        List<String> cached = ID_COLUMN_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<String> idColumns = new ArrayList<>();
        boolean hasEmbeddedId = false;
        for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(Id.class)) {
                    idColumns.add(resolveColumnName(f));
                }
                if (f.isAnnotationPresent(jakarta.persistence.EmbeddedId.class)) {
                    hasEmbeddedId = true;
                }
            }
        }
        if (idColumns.isEmpty()) {
            if (hasEmbeddedId) {
                throw new IllegalStateException("Entity " + entityClass.getName() + " uses @EmbeddedId. "
                    + "Call onConflict() to specify the conflict columns explicitly for MergeSpec.");
            }
            throw new IllegalStateException("No @Id field found in " + entityClass.getName()
                + ". Ensure the entity has a field annotated with @jakarta.persistence.Id");
        }
        List<String> result = List.copyOf(idColumns);
        ID_COLUMN_CACHE.put(cacheKey, result);
        return result;
    }

    /**
     * 解析字段对应的数据库列名。优先使用 {@code @Column(name)} 注解，否则回退到
     * {@code camelCase -> snake_case} 的保守转换。
     *
     * <p>
     * 如果项目使用了自定义 PhysicalNamingStrategy，请为涉及原生 SQL/批量操作的字段显式声明
     * {@code @Column(name = "...")}，避免列名推断偏差。
     *
     * @param field 实体字段
     * @return 数据库列名
     */
    String resolveColumnName(Field field) {
        Column columnAnnotation = field.getAnnotation(Column.class);
        if (columnAnnotation != null && !columnAnnotation.name().isEmpty()) {
            String name = columnAnnotation.name();
            // 校验注解中的列名以防止注入
            IdentifierValidator.validateColumnName(name);
            return name;
        }
        // 回退到 snake_case 转换
        String name = com.zsubera.jpa.util.StringHelper.camelToSnake(field.getName());
        IdentifierValidator.validateColumnName(name);
        return name;
    }

    /**
     * 将 Java 字段名解析为数据库列名。遍历实体类及其父类的字段，匹配字段名后通过 {@link #resolveColumnName(Field)} 解析。
     *
     * @param javaFieldName Java 字段名
     * @return 数据库列名
     */
    String resolveJavaFieldToDbColumn(String javaFieldName) {
        String cacheKey = entityClass.getName() + "#" + javaFieldName;
        String cached = JAVA_FIELD_TO_DB_COLUMN_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals(javaFieldName)) {
                    String result = resolveColumnName(f);
                    JAVA_FIELD_TO_DB_COLUMN_CACHE.put(cacheKey, result);
                    return result;
                }
            }
        }
        // 回退：未找到 Field，直接使用字段名（安全校验）
        IdentifierValidator.validateColumnName(javaFieldName);
        JAVA_FIELD_TO_DB_COLUMN_CACHE.put(cacheKey, javaFieldName);
        return javaFieldName;
    }
}
