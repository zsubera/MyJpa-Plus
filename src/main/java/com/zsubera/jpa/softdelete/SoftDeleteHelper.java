package com.zsubera.jpa.softdelete;

import com.zsubera.jpa.annotation.SoftDelete;
import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.ConditionNode;
import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.util.IdentifierValidator;
import com.zsubera.jpa.util.StringHelper;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.springframework.data.jpa.domain.Specification;

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
 * <li>{@code String} — 通过 {@link SoftDelete#deletedStringValue()} 指定表示"已删除"的字符串值（默认 "1"），适用于 {@code char(1)} 等场景</li>
 * </ul>
 *
 * <p>
 * 缓存策略：采用双策略缓存架构：
 * <ul>
 * <li><strong>弱引用缓存</strong>（{@link ConcurrentReferenceHashMap}，WEAK keys）：用于以 {@code Class} 为键的缓存
 *   （FIELD_CACHE, NOT_DELETED_SPEC_CACHE, DELETED_SPEC_CACHE, ANNOTATION_CACHE, FIELD_OBJECT_CACHE）。
 *   弱引用允许类加载器在 OSGi/热重载场景中被 GC 回收，防止类加载器泄漏。</li>
 * <li><strong>采样驱逐缓存</strong>（{@link com.zsubera.jpa.util.SampledEvictionCache}）：用于以 {@code String} 为键的缓存
 *   （COLUMN_NAME_CACHE, ID_COLUMN_NAME_CACHE, RESOLVED_VALUE_CACHE）。字符串键无自然 GC 生命周期，
 *   使用固定容量上限（1024）+ 采样驱逐防止内存泄漏。</li>
 * </ul>
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

    /** 缓存检测到的数据库方言，避免重复检测 */
    private static volatile String cachedDialect;

    /**
     * SQL 保留字集合（MySQL + PostgreSQL 交集），用于表名验证。
     */
    private static final java.util.Set<String> SQL_RESERVED_WORDS = java.util.Set.of("SELECT", "INSERT", "UPDATE",
        "DELETE", "FROM", "WHERE", "INTO", "VALUES", "SET", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "CROSS", "ON",
        "USING", "AS", "DISTINCT", "ALL", "UNION", "EXCEPT", "INTERSECT", "ORDER", "BY", "GROUP", "HAVING", "LIMIT",
        "OFFSET", "CREATE", "ALTER", "DROP", "TRUNCATE", "RENAME", "INDEX", "TABLE", "VIEW", "SCHEMA", "DATABASE",
        "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "UNIQUE", "CHECK", "DEFAULT", "NULL", "NOT", "CONSTRAINT", "BEGIN",
        "COMMIT", "ROLLBACK", "SAVEPOINT", "TRANSACTION", "TRUE", "FALSE", "AND", "OR", "IN", "EXISTS", "BETWEEN",
        "LIKE", "IS", "ANY", "SOME", "COUNT", "SUM", "AVG", "MIN", "MAX", "COALESCE", "NULLIF", "CASE", "WHEN", "THEN",
        "ELSE", "END", "CAST", "USER", "COLUMN", "ROW", "VALUE", "TYPE", "STATUS", "DATA", "TEXT", "DATE", "TIME");

    /** 采样缓存大小检查的计数器，减少开销。 */
    private static final java.util.concurrent.atomic.AtomicInteger CALL_COUNTER =
        new java.util.concurrent.atomic.AtomicInteger(0);

    /** 没有 @SoftDelete 字段的实体的哨兵值（避免在缓存中出现空缓存）。 */
    private static final String NO_FIELD_SENTINEL = "\0";

    /**
     * 缓存: entityClass -> 字段名（或"无字段"的哨兵值）。
     * 使用弱引用键允许类加载器在 OSGi/热重载场景中被 GC 回收。
     */
    private static final Cache<Class<?>, String> FIELD_CACHE =
        Caffeine.newBuilder().weakKeys().build();

    /**
     * 缓存: entityClass -> isNotDeleted Specification。
     * 使用弱引用键允许类加载器在 OSGi/热重载场景中被 GC 回收。
     */
    private static final Cache<Class<?>, Specification<?>> NOT_DELETED_SPEC_CACHE =
        Caffeine.newBuilder().weakKeys().build();

    /**
     * 缓存: entityClass -> isDeleted Specification。
     * 使用弱引用键允许类加载器在 OSGi/热重载场景中被 GC 回收。
     */
    private static final Cache<Class<?>, Specification<?>> DELETED_SPEC_CACHE =
        Caffeine.newBuilder().weakKeys().build();

    /** 缓存: (entityClass, fieldName) -> Field 对象（或哨兵值），避免重复反射查找。使用 Caffeine weakKeys 允许类加载器 GC。 */
    private static final Cache<Class<?>, ConcurrentHashMap<String, Object>> FIELD_OBJECT_CACHE =
        Caffeine.newBuilder().weakKeys().build();

    /** 缓存: entityClass -> SoftDelete annotation，避免每次查询重复反射查找。 */
    private static final Cache<Class<?>, SoftDelete> ANNOTATION_CACHE =
        Caffeine.newBuilder().weakKeys().build();

    /** 列名/ID列名缓存最大条目数，防止动态代理类名导致无限增长。 */
    private static final int MAX_NAME_CACHE_SIZE = 4096;

    /** 缓存: (entityClassName#fieldName) -> resolved column name，使用采样驱逐防止内存泄漏。 */
    private static final com.zsubera.jpa.util.SampledEvictionCache<String, String> COLUMN_NAME_CACHE =
        new com.zsubera.jpa.util.SampledEvictionCache<>(MAX_NAME_CACHE_SIZE, 0.75, 100, 256);

    /** 缓存: entityClassName -> resolved ID column name，使用采样驱逐防止内存泄漏。 */
    private static final com.zsubera.jpa.util.SampledEvictionCache<String, String> ID_COLUMN_NAME_CACHE =
        new com.zsubera.jpa.util.SampledEvictionCache<>(MAX_NAME_CACHE_SIZE, 0.75, 100, 256);

    /**
     * 验证 SQL 标识符安全性，确保不含注入字符。
     *
     * <p>
     * 验证标识符仅包含安全字符（字母、数字、下划线）。 支持 schema.table 格式（按点号分段校验每一段）。
     * 不添加引号——标识符已通过正则校验确保安全，避免 MySQL/PostgreSQL 引号风格差异。
     *
     * @param identifier SQL 标识符
     * @return 验证后的标识符（原样返回）
     * @throws IllegalArgumentException 如果标识符包含非法字符
     */
    static String validateIdentifier(String identifier) {
        IdentifierValidator.validate(identifier);
        return identifier;
    }

    /**
     * 将 SQL 标识符用引号包裹，确保大小写敏感。根据数据库类型自动选择正确的引用字符：
     * <ul>
     * <li>MySQL: 反引号 {@code `identifier`}</li>
     * <li>PostgreSQL/Oracle/SQL Server: 双引号 {@code "identifier"} (ANSI SQL 标准)</li>
     * </ul>
     *
     * @param identifier 已验证的 SQL 标识符
     * @param dialect 数据库方言（"mysql"、"postgresql"、"oracle"、"sqlserver"）
     * @return 引号包裹的标识符
     */
    static String quoteIdentifier(String identifier, String dialect) {
        if (identifier == null || identifier.isEmpty()) {
            return identifier;
        }
        char quoteChar = "mysql".equals(dialect) ? '`' : '"';
        // 处理 schema.table 格式
        String[] parts = identifier.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(quoteChar).append(parts[i]).append(quoteChar);
        }
        return sb.toString();
    }

    /**
     * 将 SQL 标识符用双引号包裹（ANSI SQL 标准）。默认使用双引号，适用于 PostgreSQL。
     * 对于 MySQL，请使用 {@link #quoteIdentifier(String, String)} 并传入方言参数。
     *
     * @param identifier 已验证的 SQL 标识符
     * @return 双引号包裹的标识符
     */
    static String quoteIdentifier(String identifier) {
        return quoteIdentifier(identifier, "postgresql");
    }

    /**
     * 检测数据库方言类型。优先使用系统属性 myjpa-plus.dialect，否则通过 JDBC 元数据检测。
     *
     * @param em EntityManager 实例
     * @return 数据库方言字符串（"mysql"、"postgresql"、"oracle"、"sqlserver"）
     */
    static String detectDialect(jakarta.persistence.EntityManager em) {
        // 优先使用缓存
        String cached = cachedDialect;
        if (cached != null) {
            return cached;
        }

        // 同步检测方言，避免并发检测导致的连接问题
        synchronized (SoftDeleteHelper.class) {
            // 双重检查缓存
            cached = cachedDialect;
            if (cached != null) {
                return cached;
            }

            // 优先使用系统属性
            String manualDialect = System.getProperty("myjpa-plus.dialect");
            if (manualDialect != null && !manualDialect.isEmpty()) {
                cachedDialect = manualDialect.toLowerCase();
                return cachedDialect;
            }

            // 通过 Hibernate SessionFactory 的数据库方言检测
            try {
                jakarta.persistence.EntityManagerFactory emf = em.getEntityManagerFactory();
                // 检查 Hibernate dialect 属性
                Object dialectProp = emf.getProperties().get("hibernate.dialect");
                if (dialectProp != null) {
                    String dialectStr = dialectProp.toString().toLowerCase();
                    log.debug("Detected Hibernate dialect property: {}", dialectStr);
                    if (dialectStr.contains("mysql")) {
                        cachedDialect = "mysql";
                        return cachedDialect;
                    } else if (dialectStr.contains("postgresql")) {
                        cachedDialect = "postgresql";
                        return cachedDialect;
                    } else if (dialectStr.contains("oracle")) {
                        cachedDialect = "oracle";
                        return cachedDialect;
                    } else if (dialectStr.contains("sqlserver") || dialectStr.contains("microsoft")) {
                        cachedDialect = "sqlserver";
                        return cachedDialect;
                    }
                }

                // 尝试通过 JDBC 获取连接
                java.sql.Connection conn = null;
                try {
                    conn = em.unwrap(java.sql.Connection.class);
                } catch (Exception ex) {
                    log.debug("Failed to unwrap Connection directly: {}", ex.getMessage());
                }

                if (conn != null) {
                    int unwrapAttempts = 0;
                    while (conn.isWrapperFor(java.sql.Connection.class) && unwrapAttempts < 5) {
                        conn = conn.unwrap(java.sql.Connection.class);
                        unwrapAttempts++;
                    }
                    String productName = conn.getMetaData().getDatabaseProductName().toLowerCase();
                    log.debug("Detected database product: {}", productName);
                    if (productName.contains("mysql")) {
                        cachedDialect = "mysql";
                    } else if (productName.contains("postgresql")) {
                        cachedDialect = "postgresql";
                    } else if (productName.contains("oracle")) {
                        cachedDialect = "oracle";
                    } else if (productName.contains("microsoft") || productName.contains("sqlserver")) {
                        cachedDialect = "sqlserver";
                    } else {
                        cachedDialect = "postgresql";
                    }
                    return cachedDialect;
                }

                log.warn("Could not detect database dialect, defaulting to postgresql");
                cachedDialect = "postgresql";
                return cachedDialect;
            } catch (Exception e) {
                log.warn("Failed to detect database dialect, defaulting to postgresql: {}", e.getMessage());
                cachedDialect = "postgresql";
                return cachedDialect;
            }
        }
    }

    /**
     * 验证表名标识符。用于原生 SQL 中不需要大小写敏感的场景。
     *
     * <p>
     * 额外检查表名是否为 SQL 保留字，防止语法错误。
     */
    static String validateTableName(String identifier) {
        String validated = validateIdentifier(identifier);
        // 检查是否为 SQL 保留字（不区分大小写）
        String upper = validated.toUpperCase(java.util.Locale.ROOT);
        if (SQL_RESERVED_WORDS.contains(upper)) {
            log.warn("Table name '{}' is a SQL reserved word. " + "This may cause syntax errors on some databases. "
                + "Consider using @Table(name = \"...\") to specify a different table name.", identifier);
        }
        return validated;
    }

    private SoftDeleteHelper() {}

    /**
     * 软删除值解析结果。
     *
     * @param booleanField 是否为 Boolean 类型（无需参数绑定，直接使用字面量 true）
     * @param dbValue 需要绑定到参数的数据库值（Boolean 类型时为 null）
     */
    public record ResolvedDeletedValue(boolean booleanField, Object dbValue) {
    }

    /**
     * 解析 @SoftDelete 字段的删除值。统一 Boolean/Integer/Enum/String 类型的分派逻辑。
     *
     * @param entityClass 实体类
     * @param field 软删除字段
     * @param annotation SoftDelete 注解
     * @return 解析后的删除值
     * @throws MyJpaPlusException 如果字段类型不支持或枚举缺少 deletedValue
     */
    public static ResolvedDeletedValue resolveDeletedValue(Class<?> entityClass, Field field, SoftDelete annotation) {
        if (field.getType() == Boolean.class || field.getType() == boolean.class) {
            return new ResolvedDeletedValue(true, null);
        }
        if (field.getType() == Integer.class || field.getType() == int.class) {
            int deletedValue = (annotation != null) ? annotation.deletedIntValue() : 1;
            return new ResolvedDeletedValue(false, deletedValue);
        }
        if (Enum.class.isAssignableFrom(field.getType())) {
            if (annotation == null || annotation.deletedValue().isEmpty()) {
                throw new MyJpaPlusException("@SoftDelete on enum field '" + field.getName() + "' in "
                    + entityClass.getName() + " must specify deletedValue");
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            Enum<?> deletedEnumValue = Enum.valueOf((Class<Enum>)field.getType(), annotation.deletedValue());
            Enumerated enumerated = field.getAnnotation(Enumerated.class);
            com.zsubera.jpa.converter.CodeEnum codeEnumAnnotation =
                field.getAnnotation(com.zsubera.jpa.converter.CodeEnum.class);
            Object dbValue;
            if (codeEnumAnnotation != null) {
                // @CodeEnum 优先：使用 @CodeEnumValue 字段的值作为数据库值
                java.lang.reflect.Field codeField =
                    com.zsubera.jpa.converter.CodeEnumType.resolveCodeField(field.getType());
                if (codeField != null) {
                    try {
                        codeField.setAccessible(true);
                        dbValue = codeField.get(deletedEnumValue);
                    } catch (IllegalAccessException e) {
                        log.warn("Cannot access @CodeEnumValue field '{}' on enum {}; falling back to name()",
                            codeField.getName(), field.getType().getSimpleName());
                        dbValue = deletedEnumValue.name();
                    }
                } else {
                    dbValue = deletedEnumValue.name();
                }
            } else if (enumerated != null && enumerated.value() == EnumType.STRING) {
                dbValue = deletedEnumValue.name();
            } else {
                dbValue = deletedEnumValue.ordinal();
            }
            return new ResolvedDeletedValue(false, dbValue);
        }
        if (field.getType() == String.class) {
            String deletedValue = (annotation != null && !annotation.deletedStringValue().isEmpty())
                ? annotation.deletedStringValue() : "1";
            return new ResolvedDeletedValue(false, deletedValue);
        }
        throw new MyJpaPlusException(
            "@SoftDelete field '" + field.getName() + "' in " + entityClass.getName() + " has unsupported type: "
                + field.getType().getName() + ". Supported types: Boolean, Integer, Enum, String.");
    }

    /**
     * 解析实体类对应的数据库表名。
     */
    static String resolveTableName(Class<?> entityClass) {
        return IdentifierValidator.resolveTableName(entityClass);
    }

    /**
     * 解析字段对应的数据库列名。结果通过 {@link #COLUMN_NAME_CACHE} 缓存，避免重复反射扫描类层次。
     *
     * <p>支持字段访问和属性访问两种模式。先查字段上的 @Column，再查 getter 上的 @Column。</p>
     */
    static String resolveColumnName(Class<?> entityClass, String fieldName) {
        String cacheKey = getEntityBaseName(entityClass) + "#" + fieldName;
        String result = COLUMN_NAME_CACHE.get(cacheKey);
        if (result != null)
            return result;
        result = doResolveColumnName(entityClass, fieldName);
        COLUMN_NAME_CACHE.put(cacheKey, result);
        return result;
    }

    private static String doResolveColumnName(Class<?> entityClass, String fieldName) {
        Field field = getField(entityClass, fieldName);
        if (field != null) {
            jakarta.persistence.Column columnAnnotation = field.getAnnotation(jakarta.persistence.Column.class);
            if (columnAnnotation != null && !columnAnnotation.name().isEmpty()) {
                return validateAndReturnColumnName(columnAnnotation.name());
            }
        }
        // 属性访问模式：检查 getter 上的 @Column
        String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(getterName) && m.getParameterCount() == 0) {
                    jakarta.persistence.Column columnAnnotation = m.getAnnotation(jakarta.persistence.Column.class);
                    if (columnAnnotation != null && !columnAnnotation.name().isEmpty()) {
                        return validateAndReturnColumnName(columnAnnotation.name());
                    }
                    return StringHelper.camelToSnake(fieldName);
                }
            }
        }
        return StringHelper.camelToSnake(fieldName);
    }

    private static String validateAndReturnColumnName(String name) {
        if (!IdentifierValidator.SAFE_IDENTIFIER_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                "Invalid @Column name: " + name + ". Must contain only alphanumeric characters and underscores.");
        }
        return name;
    }

    /**
     * 解析实体类的 ID 列名。结果通过 {@link #ID_COLUMN_NAME_CACHE} 缓存，避免重复反射。
     *
     * <p>支持字段访问和属性访问两种模式（@Id 可能在字段上也可能在 getter 方法上）。</p>
     */
    static String resolveIdColumnName(Class<?> entityClass) {
        String cacheKey = getEntityBaseName(entityClass);
        String result = ID_COLUMN_NAME_CACHE.get(cacheKey);
        if (result != null)
            return result;
        result = doResolveIdColumnName(entityClass);
        ID_COLUMN_NAME_CACHE.put(cacheKey, result);
        return result;
    }

    /**
     * 获取实体的基类名称，剥离 Hibernate 动态代理后缀（如 {@code _$$_javassist_1}）和 Spring CGLIB 后缀（{@code $$EnhancerByCGLIB$$}）。
     * 确保缓存键不因代理类而产生爆炸。
     */
    static String getEntityBaseName(Class<?> entityClass) {
        String name = entityClass.getName();
        int idx = name.indexOf("_$$_");
        if (idx > 0)
            return name.substring(0, idx);
        idx = name.indexOf("$$EnhancerByCGLIB$$");
        if (idx > 0)
            return name.substring(0, idx);
        return name;
    }

    private static String doResolveIdColumnName(Class<?> entityClass) {
        for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
            // 检查字段上的 @Id
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(jakarta.persistence.Id.class)) {
                    return resolveColumnFromIdField(f);
                }
            }
            // 检查 getter 方法上的 @Id（属性访问模式）
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(jakarta.persistence.Id.class) && m.getName().startsWith("get")
                    && m.getParameterCount() == 0) {
                    String methodName = m.getName();
                    String fieldName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
                    jakarta.persistence.Column columnAnnotation = m.getAnnotation(jakarta.persistence.Column.class);
                    if (columnAnnotation != null && !columnAnnotation.name().isEmpty()) {
                        String name = columnAnnotation.name();
                        if (!IdentifierValidator.SAFE_IDENTIFIER_PATTERN.matcher(name).matches()) {
                            throw new IllegalArgumentException("Invalid @Column name for @Id method: " + name
                                + ". Must contain only alphanumeric characters and underscores.");
                        }
                        return name;
                    }
                    return StringHelper.camelToSnake(fieldName);
                }
            }
        }
        throw new IllegalStateException(
            "No @Id field or getter method found in " + (entityClass != null ? entityClass.getName() : "null"));
    }

    private static String resolveColumnFromIdField(Field f) {
        jakarta.persistence.Column columnAnnotation = f.getAnnotation(jakarta.persistence.Column.class);
        if (columnAnnotation != null && !columnAnnotation.name().isEmpty()) {
            String name = columnAnnotation.name();
            if (!IdentifierValidator.SAFE_IDENTIFIER_PATTERN.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid @Column name for @Id field: " + name
                    + ". Must contain only alphanumeric characters and underscores.");
            }
            return name;
        }
        return StringHelper.camelToSnake(f.getName());
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
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        return (Specification<T>)NOT_DELETED_SPEC_CACHE.get(entityClass, cls -> {
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
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        return (Specification<T>)DELETED_SPEC_CACHE.get(entityClass, cls -> {
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

    /**
     * 软删除值比较策略接口，统一 Boolean/Integer/Enum/String 类型的分派逻辑。
     */
    @FunctionalInterface
    private interface DeletedValuePredicate {
        Predicate test(CriteriaBuilder cb, Path<?> path, Object deletedValue);
    }

    /**
     * 解析软删除值并构建谓词。统一 Boolean/Integer/Enum/String 类型的分派逻辑。
     *
     * @param cb CriteriaBuilder
     * @param path 字段路径
     * @param fieldName 字段名
     * @param entityClass 实体类
     * @param isNotDeleted true 表示构建"未删除"谓词，false 表示构建"已删除"谓词
     * @return 构建的谓词
     */
    /** ponytail: 缓存 ResolvedDeletedValue，避免每次 JOIN 重新解析。使用采样驱逐防止内存泄漏。 */
    private static final com.zsubera.jpa.util.SampledEvictionCache<String, ResolvedDeletedValue> RESOLVED_VALUE_CACHE =
        new com.zsubera.jpa.util.SampledEvictionCache<>(1024, 0.75, 100, 64);

    private static Predicate resolveDeletedPredicate(CriteriaBuilder cb, Path<?> path, String fieldName,
        Class<?> entityClass, boolean isNotDeleted) {
        Field field = getField(entityClass, fieldName);
        if (field == null) {
            throw new com.zsubera.jpa.exception.MyJpaPlusException("Cannot resolve @SoftDelete field '" + fieldName
                + "' in " + entityClass.getName() + ". Ensure the field exists and is accessible.");
        }
        String cacheKey = getEntityBaseName(entityClass) + "#" + fieldName;
        ResolvedDeletedValue resolved = RESOLVED_VALUE_CACHE.computeIfAbsent(cacheKey, k -> {
            SoftDelete annotation = ANNOTATION_CACHE.get(entityClass, cls -> {
                Field f = getField(cls, fieldName);
                return f != null ? f.getAnnotation(SoftDelete.class) : null;
            });
            return resolveDeletedValue(entityClass, field, annotation);
        });
        if (resolved.booleanField()) {
            if (isNotDeleted) {
                return cb.or(cb.isNull(path.get(fieldName)), cb.equal(path.get(fieldName), false));
            }
            return cb.equal(path.get(fieldName), true);
        }
        Object dbValue = resolved.dbValue();
        if (isNotDeleted) {
            return cb.or(cb.isNull(path.get(fieldName)), cb.notEqual(path.get(fieldName), dbValue));
        }
        return cb.equal(path.get(fieldName), dbValue);
    }

    /**
     * 构建排除已删除记录的谓词。支持 Boolean/Integer/Enum/String 类型的 @SoftDelete 字段。
     * 供 {@link com.zsubera.jpa.spec.NodeResolver} 等外部调用方在 JOIN 场景中使用。
     *
     * @param cb CriteriaBuilder
     * @param path 字段路径
     * @param fieldName 软删除字段名
     * @param entityClass 实体类
     * @return "未删除"谓词
     */
    public static Predicate buildNotDeleted(CriteriaBuilder cb, Path<?> path, String fieldName, Class<?> entityClass) {
        return resolveDeletedPredicate(cb, path, fieldName, entityClass, true);
    }

    private static Predicate buildDeleted(CriteriaBuilder cb, Path<?> path, String fieldName, Class<?> entityClass) {
        return resolveDeletedPredicate(cb, path, fieldName, entityClass, false);
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
        // 使用采样策略——每 256 次调用才检查一次缓存大小以减少开销
        if ((CALL_COUNTER.incrementAndGet() & 255) == 0) {
            long currentSize = FIELD_CACHE.estimatedSize();
            if (currentSize > MAX_CACHE_SIZE) {
                log.warn("SoftDeleteHelper field cache size ({}) exceeds limit ({}). "
                    + "This may indicate a class loader leak or excessive entity classes. "
                    + "Weak reference entries will be cleaned by GC automatically.", currentSize, MAX_CACHE_SIZE);
            }
        }
        String result = FIELD_CACHE.get(entityClass, cls -> {
            // 扫描字段上的 @SoftDelete 注解
            for (Field field : getAllFields(cls)) {
                if (field.isAnnotationPresent(SoftDelete.class)) {
                    try {
                        field.setAccessible(true);
                    } catch (SecurityException e) {
                        // ponytail: 返回字段名而非 sentinel——调用方会在 getField() 时再次遇到 SecurityException。
                        // 若返回 NO_FIELD_SENTINEL，findSoftDeleteField() 会返回 null，导致软删除过滤被永久禁用。
                        String moduleName = cls.getModule() != null ? cls.getModule().getName() : "unnamed";
                        String pkg = cls.getPackageName();
                        log.error(
                            "Cannot access @SoftDelete field '{}' in {}. " + "Module '{}' does not open package '{}'. "
                                + "Solutions: 1) Add to module-info.java: opens {}; "
                                + "2) JVM: --add-opens {}/{}=ALL-UNNAMED; " + "3) Use public getter/setter.",
                            field.getName(), cls.getSimpleName(), moduleName, pkg, pkg, moduleName, pkg);
                        return field.getName();
                    }
                    return field.getName();
                }
            }
            return NO_FIELD_SENTINEL;
        });
        return NO_FIELD_SENTINEL.equals(result) ? null : result;
    }

    private static final java.lang.reflect.Method NO_FIELD_SENTINEL_METHOD;
    static {
        try {
            NO_FIELD_SENTINEL_METHOD = Object.class.getDeclaredMethod("toString");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static Field getField(Class<?> entityClass, String fieldName) {
        ConcurrentHashMap<String, Object> innerCache =
            FIELD_OBJECT_CACHE.get(entityClass, k -> new ConcurrentHashMap<>());
        Object cached = innerCache.computeIfAbsent(fieldName, k -> {
            Class<?> current = entityClass;
            while (current != null && current != Object.class) {
                try {
                    Field f = current.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException e) {
                    current = current.getSuperclass();
                } catch (SecurityException e) {
                    String moduleName = current.getModule() != null ? current.getModule().getName() : "unnamed";
                    String pkg = current.getPackageName();
                    throw new MyJpaPlusException("Cannot access field '" + fieldName + "' in " + current.getName() + "."
                        + " Module '" + moduleName + "' does not open package '" + pkg + "'."
                        + " Add JVM argument: --add-opens " + pkg + "=ALL-UNNAMED", e);
                }
            }
            return NO_FIELD_SENTINEL_METHOD;
        });
        return cached instanceof Field f ? f : null;
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
            Object value = field.get(entity);
            if (value == null) {
                return false;
            }
            // ponytail: 缓存 SoftDelete 注解，避免高频率调用时重复反射
            SoftDelete annotation =
                ANNOTATION_CACHE.get(entityClass, cls -> field.getAnnotation(SoftDelete.class));
            if (annotation == null) {
                // ponytail: 防御性检查——弱引用缓存驱逐后重建时 annotation 可能为 null。
                // 此时回退到 Boolean 类型判断（不依赖 annotation），避免 NPE 导致整个查询失败。
                if (value instanceof Boolean) {
                    return Boolean.TRUE.equals(value);
                }
                return false;
            }
            // Boolean 类型
            if (value instanceof Boolean) {
                return Boolean.TRUE.equals(value);
            }
            // Integer 类型
            if (value instanceof Integer intValue) {
                return intValue.equals(annotation.deletedIntValue());
            }
            // 枚举类型
            if (value instanceof Enum enumValue) {
                return !annotation.deletedValue().isEmpty() && enumValue.name().equals(annotation.deletedValue());
            }
            // String 类型（支持 char(1) 等字符串软删除）
            if (value instanceof String strValue) {
                return !annotation.deletedStringValue().isEmpty() && strValue.equals(annotation.deletedStringValue());
            }
            return false;
        } catch (ReflectiveOperationException e) {
            throw new MyJpaPlusException(String.format(
                "Failed to read soft delete field '%s' from entity %s. "
                    + "If using Java 17+ module system, add JVM argument: " + "--add-opens %s=ALL-UNNAMED",
                fieldName, entity.getClass().getSimpleName(), entity.getClass().getPackageName()), e);
        }
    }

    /** 字段缓存，使用弱引用防止类加载器泄漏（热部署/OSGi 场景） */
    private static final Cache<Class<?>, java.util.List<Field>> FIELDS_CACHE =
        Caffeine.newBuilder().weakKeys().build();

    private static List<Field> getAllFields(Class<?> clazz) {
        return FIELDS_CACHE.get(clazz, c -> {
            List<Field> fields = new java.util.ArrayList<>();
            Class<?> current = c;
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    // 过滤静态字段和合成字段，只返回实例字段
                    if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                        fields.add(field);
                    }
                }
                current = current.getSuperclass();
            }
            return java.util.Collections.unmodifiableList(fields);
        });
    }

    /**
     * 清除所有缓存。用于应用关闭时清理，防止热部署场景下的类加载器泄漏。
     */
    public static void shutdown() {
        FIELD_CACHE.invalidateAll();
        NOT_DELETED_SPEC_CACHE.invalidateAll();
        DELETED_SPEC_CACHE.invalidateAll();
        FIELD_OBJECT_CACHE.invalidateAll();
        ANNOTATION_CACHE.invalidateAll();
        FIELDS_CACHE.invalidateAll();
        COLUMN_NAME_CACHE.clear();
        ID_COLUMN_NAME_CACHE.clear();
        RESOLVED_VALUE_CACHE.clear();
    }
}
