package com.zsubera.jpa.softdelete;

import com.zsubera.jpa.annotation.SoftDelete;
import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.ConditionNode;
import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.util.QueryTimeoutHelper;
import com.zsubera.jpa.update.AuditUtils;
import com.zsubera.jpa.util.StringHelper;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.lang.reflect.Field;
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
 * <li>{@code String} — 通过 {@link SoftDelete#deletedStringValue()} 指定表示"已删除"的字符串值（默认 "1"），适用于 {@code char(1)} 等场景</li>
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
 * @since 0.0.2
 */
public final class SoftDeleteHelper {

    private static final int MAX_CACHE_SIZE = 1024;
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(SoftDeleteHelper.class);

    /** 采样缓存大小检查的计数器，减少开销。 */
    private static final java.util.concurrent.atomic.AtomicInteger CALL_COUNTER =
        new java.util.concurrent.atomic.AtomicInteger(0);

    /** 安全标识符段正则：用于校验 schema.table 格式中每一段。 */
    private static final java.util.regex.Pattern SAFE_IDENTIFIER_PART_PATTERN =
        java.util.regex.Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /** 没有 @SoftDelete 字段的实体的哨兵值（避免在缓存中出现空缓存）。 */
    private static final String NO_FIELD_SENTINEL = "\0";

    /**
     * 缓存: entityClass -&gt; 字段名（或"无字段"的哨兵值）。
     * 使用弱引用键允许类加载器在 OSGi/热重载场景中被 GC 回收。
     */
    private static final ConcurrentMap<Class<?>, String> FIELD_CACHE =
        new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    /**
     * 缓存: entityClass -&gt; isNotDeleted Specification。
     * 使用弱引用键允许类加载器在 OSGi/热重载场景中被 GC 回收。
     */
    private static final ConcurrentMap<Class<?>, Specification<?>> NOT_DELETED_SPEC_CACHE =
        new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    /**
     * 缓存: entityClass -&gt; isDeleted Specification。
     * 使用弱引用键允许类加载器在 OSGi/热重载场景中被 GC 回收。
     */
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

        // 仅验证不转义——标识符已通过正则校验确保安全，避免 MySQL/PostgreSQL 引号风格差异
        String[] parts = identifier.split("\\.");
        for (String part : parts) {
            if (!SAFE_IDENTIFIER_PART_PATTERN.matcher(part).matches()) {
                throw new IllegalArgumentException("Invalid SQL identifier: '" + identifier
                    + "'. Each part must contain only alphanumeric characters and underscores.");
            }
        }
        return identifier;
    }

    /**
     * 验证表名标识符但不添加双引号。用于原生 SQL 中不需要大小写敏感的场景。
     */
    static String validateTableName(String identifier) {
        return escapeIdentifier(identifier);
    }

    private SoftDeleteHelper() {}

    /**
     * 软删除值解析结果。
     *
     * @param booleanField 是否为 Boolean 类型（无需参数绑定，直接使用字面量 true）
     * @param dbValue 需要绑定到参数的数据库值（Boolean 类型时为 null）
     */
    private record ResolvedDeletedValue(boolean booleanField, Object dbValue) {
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
    private static ResolvedDeletedValue resolveDeletedValue(Class<?> entityClass, Field field, SoftDelete annotation) {
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
            Object dbValue = (enumerated != null && enumerated.value() == EnumType.STRING) ? deletedEnumValue.name()
                : deletedEnumValue.ordinal();
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
     * 使用单条 UPDATE 语句批量软删除给定类的所有实体。
     *
     * <p>
     * <strong>安全要求：</strong>必须传入 {@code allowUnconditional=true} 显式确认， 否则将抛出
     * {@link IllegalStateException}。此机制防止误调用导致全表数据被意外标记为已删除。
     *
     * <p>
     * <strong>已知限制：</strong>此方法使用原生 SQL UPDATE 语句，会绕过以下 JPA 机制：
     * <ul>
     * <li>JPA 生命周期回调（{@code @PreUpdate}、{@code @PostUpdate}）不会被触发</li>
     * <li>乐观锁（{@code @Version}）不会被检查或递增</li>
     * <li>执行后会调用 {@code EntityManager.clear()} 以使一级缓存失效</li>
     * </ul>
     * 如果您的实体依赖上述机制，请使用 {@link com.zsubera.jpa.update.UpdateSpec} 逐条更新，
     * 或直接使用 JPQL/CriteriaUpdate。
     *
     * @param em EntityManager 实例
     * @param entityClass 实体类
     * @param allowUnconditional 必须为 true 才能允许无条件软删除
     * @param <T> 实体类型
     * @return 受影响的行数
     * @throws IllegalStateException 如果 allowUnconditional 为 false
     */
    public static <T> int softDeleteAll(EntityManager em, Class<T> entityClass, boolean allowUnconditional) {
        if (em == null) {
            throw new IllegalArgumentException("em must not be null");
        }
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        if (!allowUnconditional) {
            throw new IllegalStateException(
                "softDeleteAll without conditions is dangerous. Pass allowUnconditional=true to confirm.");
        }
        // 审计日志
        if (log.isWarnEnabled()) {
            log.warn("AUDIT: Executing unconditional soft DELETE on {} — this will affect ALL rows! Call stack: {}",
                entityClass.getSimpleName(), AuditUtils.getCallStack());
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
        ResolvedDeletedValue resolved = resolveDeletedValue(entityClass, field, annotation);
        int updated;
        if (resolved.booleanField()) {
            // Boolean 字段：使用参数绑定的 SQL 以确保跨数据库兼容性
            // PostgreSQL 使用 true/false，MySQL 使用 1/0，参数绑定由 JDBC 驱动处理
            var q = em
                .createNativeQuery("UPDATE " + escapedTable + " SET " + escapedColumn + " = ?1 WHERE " + escapedColumn
                    + " = ?2 OR " + escapedColumn + " IS NULL")
                .setParameter(1, Boolean.TRUE).setParameter(2, Boolean.FALSE);
            QueryTimeoutHelper.applyTimeout(q);
            updated = q.executeUpdate();
        } else {
            String whereClause = escapedColumn + " != ?1 OR " + escapedColumn + " IS NULL";
            var q =
                em.createNativeQuery("UPDATE " + escapedTable + " SET " + escapedColumn + " = ?1 WHERE " + whereClause)
                    .setParameter(1, resolved.dbValue());
            QueryTimeoutHelper.applyTimeout(q);
            updated = q.executeUpdate();
        }
        // 原生 SQL 绕过 JPA 生命周期，需要清除 L1 缓存以确保后续查询一致性
        // 仅在实际更新了行时才 clear，避免无谓地丢弃持久化上下文中的待持久化实体
        if (updated > 0) {
            em.clear();
        }
        return updated;
    }

    /**
     * 使用单条 UPDATE 语句按 ID 批量软删除实体。
     *
     * <p>
     * <strong>限制说明：</strong>此方法使用原生 SQL 批量更新，绕过 JPA 生命周期回调（如 {@code @PreUpdate}、{@code @PostUpdate}）。
     * 如果实体需要触发生命周期回调（如审计字段自动填充），请使用 {@code CriteriaUpdate} 替代方案：
     *
     * <pre>{@code
     * CriteriaBuilder cb = em.getCriteriaBuilder();
     * CriteriaUpdate<Entity> update = cb.createCriteriaUpdate(Entity.class);
     * Root<Entity> root = update.from(Entity.class);
     * update.set("deleted", true).where(root.get("id").in(ids));
     * em.createQuery(update).executeUpdate();
     * }</pre>
     *
     * @param em EntityManager 实例
     * @param entityClass 实体类
     * @param ids 要软删除的实体 ID 列表
     * @param <T> 实体类型
     * @param <ID> ID 类型
     * @return 受影响的行数
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
        // 检查ID列表大小是否超过硬限制，防止Oracle等数据库的IN子句参数限制
        int hardLimit = com.zsubera.jpa.util.InClauseBuilder.getHardLimit();
        if (ids.size() > hardLimit) {
            throw new IllegalArgumentException("ID list size (" + ids.size() + ") exceeds the hard limit (" + hardLimit
                + "). " + "Consider processing in smaller batches or using a temporary table.");
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
        ResolvedDeletedValue resolved = resolveDeletedValue(entityClass, field, annotation);
        // 使用命名参数替代位置参数以避免某些 JPA 实现中的索引冲突
        String setParamName = "deletedValue";
        String setClause;
        boolean useParamBinding = false;
        Object deletedParamValue = null;
        if (resolved.booleanField()) {
            setClause = escapedColumn + " = :" + setParamName;
            useParamBinding = true;
            deletedParamValue = Boolean.TRUE;
        } else {
            setClause = escapedColumn + " = :" + setParamName;
            useParamBinding = true;
            deletedParamValue = resolved.dbValue();
        }
        // 使用 InClauseBuilder.getMaxInClauseSize() 替代硬编码的 1000
        int batchSize = com.zsubera.jpa.util.InClauseBuilder.getMaxInClauseSize();
        // 对大型 ID 列表使用带批次拆分的 IN 子句
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
            QueryTimeoutHelper.applyTimeout(query);
            if (useParamBinding) {
                query.setParameter(setParamName, deletedParamValue);
            }
            for (int j = 0; j < batch.size(); j++) {
                query.setParameter("id" + j, batch.get(j));
            }
            total += query.executeUpdate();
        }
        // 原生 SQL 绕过 JPA 生命周期，需要清除 L1 缓存以确保后续查询一致性
        if (total > 0) {
            em.clear();
        }
        return total;
    }

    /**
     * 安全标识符验证模式，防止 SQL 注入。
     */
    private static final java.util.regex.Pattern SAFE_IDENTIFIER_PATTERN =
        java.util.regex.Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /**
     * 解析实体类对应的数据库表名。
     */
    private static String resolveTableName(Class<?> entityClass) {
        jakarta.persistence.Table tableAnnotation = entityClass.getAnnotation(jakarta.persistence.Table.class);
        if (tableAnnotation != null && !tableAnnotation.name().isEmpty()) {
            // 逐段校验以防止 SQL 注入
            String catalog = tableAnnotation.catalog();
            String schema = tableAnnotation.schema();
            String name = tableAnnotation.name();
            if (!catalog.isEmpty() && !SAFE_IDENTIFIER_PATTERN.matcher(catalog).matches()) {
                throw new IllegalArgumentException("Invalid @Table catalog: " + catalog);
            }
            if (!schema.isEmpty() && !SAFE_IDENTIFIER_PATTERN.matcher(schema).matches()) {
                throw new IllegalArgumentException("Invalid @Table schema: " + schema);
            }
            if (!SAFE_IDENTIFIER_PATTERN.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid @Table name: " + name);
            }
            StringBuilder tableName = new StringBuilder();
            if (!catalog.isEmpty()) {
                tableName.append(catalog).append('.');
            }
            if (!schema.isEmpty()) {
                tableName.append(schema).append('.');
            }
            tableName.append(name);
            return tableName.toString();
        }
        jakarta.persistence.Entity entityAnnotation = entityClass.getAnnotation(jakarta.persistence.Entity.class);
        if (entityAnnotation != null && !entityAnnotation.name().isEmpty()) {
            String name = entityAnnotation.name();
            // 校验 @Entity name 以防止 SQL 注入
            if (!SAFE_IDENTIFIER_PATTERN.matcher(name).matches()) {
                throw new IllegalArgumentException(
                    "Invalid @Entity name: " + name + ". Must contain only alphanumeric characters and underscores.");
            }
            return name;
        }
        return StringHelper.camelToSnake(entityClass.getSimpleName());
    }

    /**
     * 解析字段对应的数据库列名。
     */
    private static String resolveColumnName(Class<?> entityClass, String fieldName) {
        Field field = getField(entityClass, fieldName);
        if (field != null) {
            jakarta.persistence.Column columnAnnotation = field.getAnnotation(jakarta.persistence.Column.class);
            if (columnAnnotation != null && !columnAnnotation.name().isEmpty()) {
                String name = columnAnnotation.name();
                if (!SAFE_IDENTIFIER_PATTERN.matcher(name).matches()) {
                    throw new IllegalArgumentException("Invalid @Column name: " + name
                        + ". Must contain only alphanumeric characters and underscores.");
                }
                return name;
            }
        }
        return StringHelper.camelToSnake(fieldName);
    }

    /**
     * 解析实体类的 ID 列名。
     */
    private static String resolveIdColumnName(Class<?> entityClass) {
        for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(jakarta.persistence.Id.class)) {
                    jakarta.persistence.Column columnAnnotation = f.getAnnotation(jakarta.persistence.Column.class);
                    if (columnAnnotation != null && !columnAnnotation.name().isEmpty()) {
                        String name = columnAnnotation.name();
                        if (!SAFE_IDENTIFIER_PATTERN.matcher(name).matches()) {
                            throw new IllegalArgumentException("Invalid @Column name for @Id field: " + name
                                + ". Must contain only alphanumeric characters and underscores.");
                        }
                        return name;
                    }
                    return StringHelper.camelToSnake(f.getName());
                }
            }
        }
        throw new IllegalStateException(
            "No @Id field found in " + (entityClass != null ? entityClass.getName() : "null"));
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
            throw new NullPointerException("entityClass must not be null");
        }
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
        if (entityClass == null) {
            throw new NullPointerException("entityClass must not be null");
        }
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
    private static Predicate resolveDeletedPredicate(CriteriaBuilder cb, Path<?> path, String fieldName,
        Class<?> entityClass, boolean isNotDeleted) {
        Field field = getField(entityClass, fieldName);
        if (field == null) {
            // 字段未找到时的默认行为
            if (isNotDeleted) {
                return cb.or(cb.isNull(path.get(fieldName)), cb.equal(path.get(fieldName), false));
            }
            return cb.equal(path.get(fieldName), true);
        }
        SoftDelete annotation = field.getAnnotation(SoftDelete.class);

        // Boolean 类型
        if (field.getType() == Boolean.class || field.getType() == boolean.class) {
            if (isNotDeleted) {
                return cb.or(cb.isNull(path.get(fieldName)), cb.equal(path.get(fieldName), false));
            }
            return cb.equal(path.get(fieldName), true);
        }

        // Integer 类型
        if (field.getType() == Integer.class || field.getType() == int.class) {
            int deletedValue = (annotation != null) ? annotation.deletedIntValue() : 1;
            if (isNotDeleted) {
                return cb.or(cb.isNull(path.get(fieldName)), cb.notEqual(path.get(fieldName), deletedValue));
            }
            return cb.equal(path.get(fieldName), deletedValue);
        }

        // 枚举类型
        if (Enum.class.isAssignableFrom(field.getType())) {
            if (annotation == null || annotation.deletedValue().isEmpty()) {
                throw new MyJpaPlusException("@SoftDelete on enum field '" + fieldName + "' in " + entityClass.getName()
                    + " must specify deletedValue");
            }
            Object deletedEnumValue = getEnumConstant(field.getType(), annotation.deletedValue());
            if (isNotDeleted) {
                return cb.or(cb.isNull(path.get(fieldName)), cb.notEqual(path.get(fieldName), deletedEnumValue));
            }
            return cb.equal(path.get(fieldName), deletedEnumValue);
        }

        // String 类型（支持 char(1) 等字符串软删除，如 '0'/'1'）
        if (field.getType() == String.class) {
            String deletedValue = (annotation != null && !annotation.deletedStringValue().isEmpty())
                ? annotation.deletedStringValue() : "1";
            if (isNotDeleted) {
                return cb.or(cb.isNull(path.get(fieldName)), cb.notEqual(path.get(fieldName), deletedValue));
            }
            return cb.equal(path.get(fieldName), deletedValue);
        }

        // 不支持的类型：抛出异常而非静默回退
        throw new MyJpaPlusException(
            "@SoftDelete field '" + fieldName + "' in " + entityClass.getName() + " has unsupported type: "
                + field.getType().getName() + ". Supported types: Boolean, Integer, Enum, String.");
    }

    private static Predicate buildNotDeleted(CriteriaBuilder cb, Path<?> path, String fieldName, Class<?> entityClass) {
        return resolveDeletedPredicate(cb, path, fieldName, entityClass, true);
    }

    private static Predicate buildDeleted(CriteriaBuilder cb, Path<?> path, String fieldName, Class<?> entityClass) {
        return resolveDeletedPredicate(cb, path, fieldName, entityClass, false);
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
        // 使用采样策略——每 64 次调用才检查一次缓存大小以减少开销
        if ((CALL_COUNTER.incrementAndGet() & 63) == 0) {
            int currentSize = FIELD_CACHE.size();
            if (currentSize > MAX_CACHE_SIZE) {
                log.warn("SoftDeleteHelper field cache size ({}) exceeds limit ({}). "
                    + "This may indicate a class loader leak or excessive entity classes. "
                    + "Weak reference entries will be cleaned by GC automatically.", currentSize, MAX_CACHE_SIZE);
            }
        }
        String result = FIELD_CACHE.computeIfAbsent(entityClass, cls -> {
            // 扫描字段上的 @SoftDelete 注解
            for (Field field : getAllFields(cls)) {
                if (field.isAnnotationPresent(SoftDelete.class)) {
                    try {
                        field.setAccessible(true);
                    } catch (SecurityException e) {

                        String moduleName = cls.getModule() != null ? cls.getModule().getName() : "unnamed";
                        String pkg = cls.getPackageName();
                        throw new IllegalStateException(String.format(
                            "Cannot access @SoftDelete field '%s' in %s. " + "Module '%s' does not open package '%s'.%n"
                                + "Solutions (pick one):%n" + "  1. Add to module-info.java: opens %s;%n"
                                + "  2. JVM argument: --add-opens %s/%s=ALL-UNNAMED%n"
                                + "  3. Use a public getter/setter for the @SoftDelete field",
                            field.getName(), cls.getSimpleName(), moduleName, pkg, pkg, moduleName, pkg), e);
                    }
                    return field.getName();
                }
            }
            return NO_FIELD_SENTINEL;
        });
        return NO_FIELD_SENTINEL.equals(result) ? null : result;
    }

    private static Field getField(Class<?> entityClass, String fieldName) {
        String cacheKey = entityClass.getName() + "#" + fieldName;
        return FIELD_OBJECT_CACHE.computeIfAbsent(cacheKey, k -> {
            Class<?> current = entityClass;
            while (current != null && current != Object.class) {
                try {
                    Field f = current.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException e) {
                    current = current.getSuperclass();
                } catch (SecurityException e) {
                    // 不缓存不可访问字段——setAccessible 永久失败，缓存会导致后续调用也失败
                    String moduleName = current.getModule() != null ? current.getModule().getName() : "unnamed";
                    String pkg = current.getPackageName();
                    throw new MyJpaPlusException("Cannot access field '" + fieldName + "' in " + current.getName() + "."
                        + " Module '" + moduleName + "' does not open package '" + pkg + "'."
                        + " Add JVM argument: --add-opens " + pkg + "=ALL-UNNAMED", e);
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
            Object value = field.get(entity);
            if (value == null) {
                return false;
            }
            // Boolean 类型
            if (value instanceof Boolean) {
                return Boolean.TRUE.equals(value);
            }
            // Integer 类型
            if (value instanceof Integer intValue && field.isAnnotationPresent(SoftDelete.class)) {
                SoftDelete annotation = field.getAnnotation(SoftDelete.class);
                if (annotation != null) {
                    return intValue.equals(annotation.deletedIntValue());
                }
            }
            // 枚举类型
            if (value instanceof Enum enumValue && field.isAnnotationPresent(SoftDelete.class)) {
                SoftDelete annotation = field.getAnnotation(SoftDelete.class);
                if (annotation != null && !annotation.deletedValue().isEmpty()) {
                    return enumValue.name().equals(annotation.deletedValue());
                }
            }
            // String 类型（支持 char(1) 等字符串软删除）
            if (value instanceof String strValue && field.isAnnotationPresent(SoftDelete.class)) {
                SoftDelete annotation = field.getAnnotation(SoftDelete.class);
                if (annotation != null && !annotation.deletedStringValue().isEmpty()) {
                    return strValue.equals(annotation.deletedStringValue());
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

    /** 字段缓存，使用弱引用防止类加载器泄漏（热部署/OSGi 场景） */
    private static final java.util.concurrent.ConcurrentMap<Class<?>, java.util.List<Field>> FIELDS_CACHE =
        new org.springframework.util.ConcurrentReferenceHashMap<>(16,
            org.springframework.util.ConcurrentReferenceHashMap.ReferenceType.WEAK);

    private static List<Field> getAllFields(Class<?> clazz) {
        return FIELDS_CACHE.computeIfAbsent(clazz, c -> {
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
        FIELD_CACHE.clear();
        NOT_DELETED_SPEC_CACHE.clear();
        DELETED_SPEC_CACHE.clear();
        FIELD_OBJECT_CACHE.clear();
        FIELDS_CACHE.clear();
    }
}
