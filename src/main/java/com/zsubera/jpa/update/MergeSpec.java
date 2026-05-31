package com.zsubera.jpa.update;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.LambdaUtils;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Id;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * UPSERT/MERGE 操作的类型安全构建器，使用 JPA 原生查询生成数据库特定的 SQL。
 *
 * <p>
 * 支持三种数据库方言：
 * <ul>
 * <li>PostgreSQL: {@code INSERT ... ON CONFLICT (...) DO UPDATE SET ...}</li>
 * <li>MySQL: {@code INSERT ... ON DUPLICATE KEY UPDATE ...}</li>
 * <li>H2 (测试): {@code MERGE INTO ... KEY(...) VALUES(...)}</li>
 * </ul>
 *
 * <p>
 * <strong>并发安全说明：</strong>UPSERT 操作在高并发场景下存在竞态条件。 两个并发事务可能同时检测到"不存在"并尝试插入，导致唯一约束冲突。 建议在高并发场景下：
 * <ul>
 * <li>使用数据库级别的唯一约束保护冲突键</li>
 * <li>在 UPSERT 前使用悲观锁（{@code SELECT ... FOR UPDATE}）</li>
 * <li>在应用层使用分布式锁保护整个 UPSERT 流程</li>
 * <li>捕获唯一约束异常并重试</li>
 * </ul>
 *
 * <p>
 * 示例：
 *
 * <pre>{@code
 * new MergeSpec<>(User.class).withEntity(user).onConflict(User::getEmail).updateOnConflict(User::getName, User::getAge)
 *     .execute(em);
 * }</pre>
 *
 * @param <T> 实体类型
 */
public class MergeSpec<T> {

    private static final Logger log = LoggerFactory.getLogger(MergeSpec.class);

    private final Class<T> entityClass;
    private T entity;
    private final List<String> conflictFields = new ArrayList<>();
    private final List<String> updateFields = new ArrayList<>();
    private boolean explicitUpdateFields = false;

    /**
     * 创建指定实体类型的 MergeSpec 构建器。
     *
     * @param entityClass 实体类
     * @throws IllegalArgumentException 如果 entityClass 为 null
     */
    public MergeSpec(Class<T> entityClass) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass must not be null");
        }
        this.entityClass = entityClass;
    }

    /**
     * 指定要 upsert 的实体实例。
     *
     * @param entity 实体实例
     * @return 当前构建器实例，支持链式调用
     * @throws IllegalArgumentException 如果 entity 为 null
     */
    public MergeSpec<T> withEntity(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
        this.entity = entity;
        return this;
    }

    /**
     * 指定冲突检测列（唯一键）。未指定时默认使用 {@code @Id} 字段。
     *
     * @param fields 冲突列的方法引用
     * @return 当前构建器实例，支持链式调用
     * @throws IllegalArgumentException 如果 fields 为 null 或空
     */
    @SafeVarargs
    public final MergeSpec<T> onConflict(SFunction<T, ?>... fields) {
        if (fields == null || fields.length == 0) {
            throw new IllegalArgumentException("fields must not be empty");
        }
        for (SFunction<T, ?> field : fields) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            conflictFields.add(LambdaUtils.getPropertyName(field));
        }
        return this;
    }

    /**
     * 指定冲突时要更新的列。未调用此方法时，更新所有非冲突列。
     *
     * @param fields 冲突时要更新的列的方法引用
     * @return 当前构建器实例，支持链式调用
     * @throws IllegalArgumentException 如果 fields 为 null 或空
     */
    @SafeVarargs
    public final MergeSpec<T> updateOnConflict(SFunction<T, ?>... fields) {
        if (fields == null || fields.length == 0) {
            throw new IllegalArgumentException("fields must not be empty");
        }
        for (SFunction<T, ?> field : fields) {
            if (field == null) {
                throw new IllegalArgumentException("field must not be null");
            }
            updateFields.add(LambdaUtils.getPropertyName(field));
        }
        explicitUpdateFields = true;
        return this;
    }

    /**
     * 执行 UPSERT 操作并返回受影响的行数。
     *
     * <p>
     * <strong>需要活动事务。</strong>建议使用 {@link #executeInTransaction(EntityManager)}。
     *
     * @param em 实体管理器
     * @return 受影响的行数
     * @throws IllegalStateException 如果未指定实体
     * @throws jakarta.persistence.TransactionRequiredException 如果没有活动事务
     */
    public int execute(EntityManager em) {
        if (em == null) {
            throw new IllegalArgumentException("em must not be null");
        }
        if (entity == null) {
            throw new IllegalStateException("Entity must be specified via withEntity() before executing");
        }
        String dialect = detectDialect(em);
        if ("h2".equals(dialect)) {
            return executeH2Upsert(em);
        }
        SqlWithParams sqlWithParams = buildSql(em);
        if (log.isDebugEnabled()) {
            log.debug("Executing UPSERT SQL: {}", sqlWithParams.sql());
        }
        return executeNativeQuery(em, sqlWithParams.sql(), sqlWithParams.params());
    }

    /**
     * H2 专用的 UPSERT 实现。使用 UPDATE-then-INSERT 策略以支持部分更新。
     *
     * <p>
     * H2 的 MERGE INTO 语法不支持仅更新指定列（总是替换整行）， 因此使用以下策略：
     * <ol>
     * <li>如果冲突键值全为 null（新实体自动生成 ID），使用简单 INSERT</li>
     * <li>如果指定了 updateOnConflict，先尝试 UPDATE，再 INSERT</li>
     * <li>否则使用 MERGE INTO 替换整行</li>
     * </ol>
     *
     * @param em 实体管理器
     * @return 受影响的行数
     */
    private int executeH2Upsert(EntityManager em) {
        List<String> effectiveConflictFields =
            conflictFields.isEmpty() ? resolveIdColumnNames() : new ArrayList<>(conflictFields);
        List<EntityFieldValue> allFieldValues = extractFieldValues();
        boolean allConflictKeysNull = true;
        for (EntityFieldValue fv : allFieldValues) {
            if (effectiveConflictFields.contains(fv.columnName()) && fv.value() != null) {
                allConflictKeysNull = false;
                break;
            }
        }
        if (allConflictKeysNull) {
            return executeSimpleInsert(em, allFieldValues);
        }
        if (explicitUpdateFields) {
            // Use UPDATE-then-INSERT strategy for partial column updates.
            // H2 MERGE INTO replaces the entire row, which doesn't support partial updates.
            // Note: This approach has a potential race condition in high-concurrency scenarios.
            // For production use with concurrent access, consider using PostgreSQL or MySQL
            // which support INSERT ... ON CONFLICT/DO UPDATE with partial column updates.
            int updated = executeConditionalUpdate(em, allFieldValues, effectiveConflictFields);
            if (updated > 0) {
                return updated;
            }
            return executeSimpleInsert(em, allFieldValues);
        }
        SqlWithParams sqlWithParams = buildSql(em);
        if (log.isDebugEnabled()) {
            log.debug("Executing H2 MERGE SQL: {}", sqlWithParams.sql());
        }
        return executeNativeQuery(em, sqlWithParams.sql(), sqlWithParams.params());
    }

    /**
     * 执行简单 INSERT（不处理冲突）。
     *
     * @param em 实体管理器
     * @param allFieldValues 所有字段值
     * @return 受影响的行数
     */
    private int executeSimpleInsert(EntityManager em, List<EntityFieldValue> allFieldValues) {
        List<String> columns = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (EntityFieldValue fv : allFieldValues) {
            if (fv.value() != null || !isAutoGeneratedId(fv.fieldName())) {
                columns.add(fv.columnName());
                params.add(fv.value());
            }
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(resolveTableName()).append(" (");
        sql.append(String.join(", ", columns));
        sql.append(") VALUES (");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        sql.append(")");
        if (log.isDebugEnabled()) {
            log.debug("Executing H2 INSERT SQL: {}", sql);
        }
        return executeNativeQuery(em, sql.toString(), params);
    }

    /**
     * 执行条件 UPDATE（仅更新指定列，按冲突键匹配）。
     *
     * @param em 实体管理器
     * @param allFieldValues 所有字段值
     * @param conflictColumns 冲突列名列表
     * @return 受影响的行数
     */
    private int executeConditionalUpdate(EntityManager em, List<EntityFieldValue> allFieldValues,
        List<String> conflictColumns) {
        List<String> setClauses = new ArrayList<>();
        List<Object> setParams = new ArrayList<>();
        for (String fieldName : updateFields) {
            for (EntityFieldValue fv : allFieldValues) {
                if (fv.fieldName().equals(fieldName)) {
                    setClauses.add(fv.columnName() + " = ?");
                    setParams.add(fv.value());
                }
            }
        }
        if (setClauses.isEmpty()) {
            return 0;
        }
        List<String> whereClauses = new ArrayList<>();
        List<Object> whereParams = new ArrayList<>();
        for (String col : conflictColumns) {
            for (EntityFieldValue fv : allFieldValues) {
                if (fv.columnName().equals(col)) {
                    whereClauses.add(col + " = ?");
                    whereParams.add(fv.value());
                }
            }
        }
        StringBuilder sql = new StringBuilder("UPDATE ").append(resolveTableName()).append(" SET ");
        sql.append(String.join(", ", setClauses));
        sql.append(" WHERE ");
        sql.append(String.join(" AND ", whereClauses));
        List<Object> allParams = new ArrayList<>(setParams);
        allParams.addAll(whereParams);
        if (log.isDebugEnabled()) {
            log.debug("Executing H2 UPDATE SQL: {}", sql);
        }
        return executeNativeQuery(em, sql.toString(), allParams);
    }

    /**
     * 执行原生 SQL 查询并绑定参数。
     *
     * @param em 实体管理器
     * @param sql SQL 语句
     * @param params 参数值列表
     * @return 受影响的行数
     */
    private int executeNativeQuery(EntityManager em, String sql, List<Object> params) {
        var query = em.createNativeQuery(sql, entityClass);
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }
        return query.executeUpdate();
    }

    /**
     * 在事务中执行 UPSERT 操作。如果没有活动事务，则自动创建新事务。
     *
     * @param em 实体管理器
     * @return 受影响的行数
     * @throws IllegalStateException 如果未指定实体
     */
    public int executeInTransaction(EntityManager em) {
        if (em == null) {
            throw new IllegalArgumentException("em must not be null");
        }
        if (entity == null) {
            throw new IllegalStateException("Entity must be specified via withEntity() before executing");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return execute(em);
        }
        EntityTransaction tx = em.getTransaction();
        if (tx == null) {
            if (log.isDebugEnabled()) {
                log.debug("JTA environment detected, executing with container-managed transaction");
            }
            try {
                return execute(em);
            } catch (jakarta.persistence.TransactionRequiredException e) {
                throw new MyJpaPlusException("No active transaction in JTA environment. "
                    + "Ensure a container-managed transaction is active, or use @Transactional annotation.", e);
            }
        }
        boolean isNewTransaction = !tx.isActive();
        if (isNewTransaction) {
            tx.begin();
        }
        try {
            int result = execute(em);
            if (isNewTransaction) {
                tx.commit();
            }
            return result;
        } catch (RuntimeException e) {
            if (isNewTransaction && tx.isActive()) {
                try {
                    tx.rollback();
                } catch (Exception rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
            }
            throw e;
        } catch (Exception e) {
            if (isNewTransaction && tx.isActive()) {
                try {
                    tx.rollback();
                } catch (Exception rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
            }
            throw new MyJpaPlusException("Merge operation failed: " + e.getClass().getSimpleName(), e);
        }
    }

    /**
     * 批量执行 UPSERT 操作。
     *
     * <p>
     * 对实体列表中的每个实体执行 UPSERT。所有操作在同一个事务中执行，使用 EntityManager 的 flush/clear 进行分批处理以减少内存占用。
     *
     * @param entities 要 UPSERT 的实体列表
     * @param em 实体管理器
     * @param batchSize 每批大小，建议值为 50-200
     * @return 受影响的总行数
     * @throws IllegalArgumentException 如果 entities 为 null 或 batchSize 不是正数
     */
    public int executeBatch(List<T> entities, EntityManager em, int batchSize) {
        if (entities == null || entities.isEmpty()) {
            throw new IllegalArgumentException("entities must not be null or empty");
        }
        if (em == null) {
            throw new IllegalArgumentException("em must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        int total = 0;
        int count = 0;
        T originalEntity = this.entity;
        try {
            for (T ent : entities) {
                this.entity = ent;
                total += execute(em);
                count++;
                if (count % batchSize == 0) {
                    em.flush();
                    em.clear();
                    if (log.isDebugEnabled()) {
                        log.debug("Batch UPSERT: {} entities processed (total affected: {})", count, total);
                    }
                }
            }
            em.flush();
            em.clear();
        } finally {
            this.entity = originalEntity;
        }
        return total;
    }

    /**
     * SQL 构建结果，包含 SQL 语句和有序的参数值。
     *
     * @param sql SQL 语句（使用 ? 占位符）
     * @param params 参数值列表（按 ? 出现顺序排列）
     */
    private record SqlWithParams(String sql, List<Object> params) {
    }

    /**
     * 构建数据库特定的 UPSERT SQL。
     *
     * @param em 实体管理器
     * @return SQL 和参数
     */
    private SqlWithParams buildSql(EntityManager em) {
        List<String> effectiveConflictFields =
            conflictFields.isEmpty() ? resolveIdColumnNames() : new ArrayList<>(conflictFields);
        Set<String> conflictSet = new LinkedHashSet<>(effectiveConflictFields);
        List<EntityFieldValue> allFieldValues = extractFieldValues();
        List<String> insertColumns = new ArrayList<>();
        List<EntityFieldValue> insertFieldValues = new ArrayList<>();
        for (EntityFieldValue fv : allFieldValues) {
            if (fv.value() != null || !isAutoGeneratedId(fv.fieldName())) {
                insertColumns.add(fv.columnName());
                insertFieldValues.add(fv);
            }
        }
        List<String> effectiveUpdateFields =
            explicitUpdateFields ? updateFields : allNonConflictColumns(allFieldValues, conflictSet);
        Set<String> conflictFieldNames = new LinkedHashSet<>();
        for (String col : effectiveConflictFields) {
            for (EntityFieldValue fv : allFieldValues) {
                if (fv.columnName().equals(col)) {
                    conflictFieldNames.add(fv.fieldName());
                }
            }
        }
        String tableName = resolveTableName();
        String dialect = detectDialect(em);
        return switch (dialect) {
            case "postgresql" -> buildPostgresSql(tableName, insertColumns, insertFieldValues, effectiveConflictFields,
                effectiveUpdateFields);
            case "mysql" -> buildMysqlSql(tableName, insertColumns, insertFieldValues, effectiveUpdateFields);
            case "h2" -> buildH2Sql(tableName, insertColumns, insertFieldValues, effectiveConflictFields,
                effectiveUpdateFields, allFieldValues, conflictFieldNames);
            default -> throw new MyJpaPlusException(
                "Unsupported database dialect: " + dialect + ". Supported dialects: postgresql, mysql, h2");
        };
    }

    /**
     * 构建 PostgreSQL 的 UPSERT SQL：{@code INSERT ... ON CONFLICT (...) DO UPDATE SET ...}。
     */
    private SqlWithParams buildPostgresSql(String tableName, List<String> insertColumns,
        List<EntityFieldValue> insertFieldValues, List<String> conflictColumns, List<String> updateColumns) {
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(tableName).append(" (");
        sql.append(String.join(", ", insertColumns));
        sql.append(") VALUES (");
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < insertFieldValues.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
            params.add(insertFieldValues.get(i).value());
        }
        sql.append(") ON CONFLICT (");
        sql.append(String.join(", ", conflictColumns));
        sql.append(") DO UPDATE SET ");
        List<String> setClauses = new ArrayList<>();
        for (String col : updateColumns) {
            setClauses.add(col + " = EXCLUDED." + col);
        }
        sql.append(String.join(", ", setClauses));
        return new SqlWithParams(sql.toString(), params);
    }

    /**
     * 构建 MySQL 的 UPSERT SQL：{@code INSERT ... ON DUPLICATE KEY UPDATE ...}。
     */
    private SqlWithParams buildMysqlSql(String tableName, List<String> insertColumns,
        List<EntityFieldValue> insertFieldValues, List<String> updateColumns) {
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(tableName).append(" (");
        sql.append(String.join(", ", insertColumns));
        sql.append(") VALUES (");
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < insertFieldValues.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
            params.add(insertFieldValues.get(i).value());
        }
        sql.append(") ON DUPLICATE KEY UPDATE ");
        List<String> setClauses = new ArrayList<>();
        for (String col : updateColumns) {
            setClauses.add(col + " = VALUES(" + col + ")");
        }
        sql.append(String.join(", ", setClauses));
        return new SqlWithParams(sql.toString(), params);
    }

    /**
     * 构建 H2 的 UPSERT SQL：{@code MERGE INTO ... KEY(...) VALUES(...)}。
     *
     * <p>
     * H2 使用原生 MERGE INTO 语法。当冲突键值为 null（新实体的自动生成 ID）时， 使用简单 INSERT 代替，因为 H2 的 MERGE INTO 不支持 null KEY 值。
     */
    private SqlWithParams buildH2Sql(String tableName, List<String> insertColumns,
        List<EntityFieldValue> insertFieldValues, List<String> conflictColumns, List<String> updateColumns,
        List<EntityFieldValue> allFieldValues, Set<String> conflictFieldNames) {
        boolean allConflictKeysNull = true;
        for (EntityFieldValue fv : allFieldValues) {
            if (conflictFieldNames.contains(fv.fieldName()) && fv.value() != null) {
                allConflictKeysNull = false;
                break;
            }
        }
        if (allConflictKeysNull) {
            StringBuilder sql = new StringBuilder("INSERT INTO ").append(tableName).append(" (");
            sql.append(String.join(", ", insertColumns));
            sql.append(") VALUES (");
            List<Object> params = new ArrayList<>();
            for (int i = 0; i < insertFieldValues.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append("?");
                params.add(insertFieldValues.get(i).value());
            }
            sql.append(")");
            return new SqlWithParams(sql.toString(), params);
        }
        StringBuilder sql = new StringBuilder("MERGE INTO ").append(tableName).append(" (");
        sql.append(String.join(", ", insertColumns));
        sql.append(") KEY (");
        sql.append(String.join(", ", conflictColumns));
        sql.append(") VALUES (");
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < insertFieldValues.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
            params.add(insertFieldValues.get(i).value());
        }
        sql.append(")");
        return new SqlWithParams(sql.toString(), params);
    }

    /**
     * 解析实体的表名。优先使用 {@code @Table} 注解，否则使用 {@code @Entity} 注解的 name 属性， 最后回退到类名的蛇形命名。
     *
     * @return 数据库表名
     */
    private String resolveTableName() {
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
     * 将驼峰命名转换为蛇形命名。
     *
     * @param name 驼峰命名字符串
     * @return 蛇形命名字符串
     */
    private static String camelToSnake(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                // Add underscore before uppercase letter if:
                // - Not at the start
                // - Previous char is lowercase (e.g., "camelCase" -> "camel_case")
                // - Next char is lowercase and previous is uppercase (e.g., "XMLParser" -> "xml_parser")
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
     * 获取所有非冲突列（用于默认更新）。
     *
     * @param allFieldValues 所有字段值
     * @param conflictSet 冲突列集合
     * @return 非冲突列名列表
     */
    private List<String> allNonConflictColumns(List<EntityFieldValue> allFieldValues, Set<String> conflictSet) {
        List<String> columns = new ArrayList<>();
        for (EntityFieldValue fv : allFieldValues) {
            if (!conflictSet.contains(fv.columnName())) {
                columns.add(fv.columnName());
            }
        }
        return columns;
    }

    /**
     * 检查字段是否为自动生成的 @Id 字段。
     *
     * @param fieldName Java 字段名
     * @return 如果是自动生成的 ID 字段则返回 true
     */
    private boolean isAutoGeneratedId(String fieldName) {
        for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(fieldName);
                if (f.isAnnotationPresent(Id.class)) {
                    jakarta.persistence.GeneratedValue gva = f.getAnnotation(jakarta.persistence.GeneratedValue.class);
                    return gva != null;
                }
            } catch (NoSuchFieldException ignored) {
                // 继续检查父类
            }
        }
        return false;
    }

    /**
     * 从实体类层次结构中解析 @Id 注解字段对应的数据库列名。
     *
     * @return ID 列名列表
     * @throws IllegalStateException 如果实体类没有 @Id 注解的字段
     */
    private List<String> resolveIdColumnNames() {
        List<String> idColumns = new ArrayList<>();
        for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(Id.class)) {
                    idColumns.add(resolveColumnName(f));
                }
            }
        }
        if (idColumns.isEmpty()) {
            throw new IllegalStateException("No @Id field found in " + entityClass.getName()
                + ". Ensure the entity has a field annotated with @jakarta.persistence.Id");
        }
        return idColumns;
    }

    /**
     * 提取实体实例中所有持久化字段的值，遍历类层次结构。
     *
     * @return 字段名、列名和值的列表
     */
    private List<EntityFieldValue> extractFieldValues() {
        if (entity == null) {
            throw new IllegalStateException("Entity must be specified via withEntity() before extracting field values");
        }
        List<EntityFieldValue> fieldValues = new ArrayList<>();
        List<Field> allFields = new ArrayList<>();
        for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())
                    && !java.lang.reflect.Modifier.isTransient(f.getModifiers())
                    && !f.isAnnotationPresent(jakarta.persistence.Transient.class)
                    && !f.isAnnotationPresent(jakarta.persistence.OneToMany.class)
                    && !f.isAnnotationPresent(jakarta.persistence.ManyToOne.class)
                    && !f.isAnnotationPresent(jakarta.persistence.ManyToMany.class)
                    && !f.isAnnotationPresent(jakarta.persistence.OneToOne.class)
                    && !f.isAnnotationPresent(jakarta.persistence.EmbeddedId.class)) {
                    // Skip @Embedded fields - they require special handling via @AttributeOverride
                    if (f.isAnnotationPresent(jakarta.persistence.Embedded.class)) {
                        log.debug(
                            "Skipping @Embedded field '{}' in MergeSpec - use @AttributeOverride for column mapping",
                            f.getName());
                        continue;
                    }
                    allFields.add(f);
                }
            }
        }
        for (Field f : allFields) {
            f.setAccessible(true);
            try {
                Object value = f.get(entity);
                String columnName = resolveColumnName(f);
                fieldValues.add(new EntityFieldValue(f.getName(), columnName, value));
            } catch (IllegalAccessException e) {
                throw new MyJpaPlusException("Failed to access field: " + f.getName(), e);
            }
        }
        return fieldValues;
    }

    /**
     * 解析字段对应的数据库列名。优先使用 {@code @Column(name)} 注解，否则使用字段名。
     *
     * @param field 实体字段
     * @return 数据库列名
     */
    private String resolveColumnName(Field field) {
        Column columnAnnotation = field.getAnnotation(Column.class);
        if (columnAnnotation != null && !columnAnnotation.name().isEmpty()) {
            return columnAnnotation.name();
        }
        return field.getName();
    }

    /**
     * 检测数据库方言。优先通过 Hibernate Session 获取，回退到 EntityManagerFactory 属性。
     *
     * @param em 实体管理器
     * @return 数据库方言标识（postgresql、mysql 或 h2）
     */
    private String detectDialect(EntityManager em) {
        try {
            org.hibernate.Session session = em.unwrap(org.hibernate.Session.class);
            String[] dialectHolder = new String[1];
            session.doWork(connection -> {
                dialectHolder[0] = connection.getMetaData().getDatabaseProductName().toLowerCase();
            });
            return mapDialect(dialectHolder[0]);
        } catch (Exception e) {
            try {
                Object jdbcUrl = em.getEntityManagerFactory().getProperties().get("jakarta.persistence.jdbc.url");
                if (jdbcUrl == null) {
                    jdbcUrl = em.getEntityManagerFactory().getProperties().get("hibernate.connection.url");
                }
                if (jdbcUrl != null) {
                    String url = jdbcUrl.toString().toLowerCase();
                    if (url.contains("postgresql")) {
                        return "postgresql";
                    }
                    if (url.contains("mysql")) {
                        return "mysql";
                    }
                    if (url.contains("h2")) {
                        return "h2";
                    }
                }
            } catch (Exception ex) {
                log.debug("Failed to detect dialect from properties: {}", ex.getMessage());
            }
            log.warn(
                "Failed to detect database dialect: {}. "
                    + "Set system property 'myjpa-plus.dialect' to 'postgresql', 'mysql', or 'h2' to specify manually.",
                e.getMessage());
            String manualDialect = System.getProperty("myjpa-plus.dialect");
            if (manualDialect != null && !manualDialect.isEmpty()) {
                String mapped = mapDialect(manualDialect.toLowerCase());
                log.info("Using manually configured dialect: {}", mapped);
                return mapped;
            }
            throw new MyJpaPlusException("Failed to detect database dialect and no manual dialect configured. "
                + "Set system property 'myjpa-plus.dialect' to 'postgresql', 'mysql', or 'h2'.", e);
        }
    }

    /**
     * 将数据库产品名称映射为方言标识。
     *
     * @param productName 数据库产品名称（小写）
     * @return 方言标识
     */
    private static String mapDialect(String productName) {
        if (productName.contains("postgresql")) {
            return "postgresql";
        }
        if (productName.contains("mysql")) {
            return "mysql";
        }
        if (productName.contains("h2")) {
            return "h2";
        }
        return productName;
    }

    /**
     * 字段值记录，包含 Java 字段名、数据库列名和字段值。
     *
     * @param fieldName Java 字段名
     * @param columnName 数据库列名
     * @param value 字段值
     */
    private record EntityFieldValue(String fieldName, String columnName, Object value) {
    }
}
