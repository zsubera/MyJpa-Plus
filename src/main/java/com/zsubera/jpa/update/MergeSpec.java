package com.zsubera.jpa.update;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.IdentifierValidator;
import com.zsubera.jpa.util.LambdaUtils;
import com.zsubera.jpa.util.StringHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
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
    private final EntityFieldExtractor<T> fieldExtractor;
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
        this.fieldExtractor = new EntityFieldExtractor<>(entityClass);
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
            conflictFields.add(fieldExtractor.resolveJavaFieldToDbColumn(LambdaUtils.getPropertyName(field)));
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
            // 将 Java 属性名转换为数据库列名，确保 UPSERT SQL 引用正确的列
            updateFields.add(fieldExtractor.resolveJavaFieldToDbColumn(LambdaUtils.getPropertyName(field)));
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
        String dialect = DialectDetector.detectDialect(em);
        if ("h2".equals(dialect)) {
            return executeH2Upsert(em);
        }
        T entitySnapshot = this.entity;
        SqlWithParams sqlWithParams = buildSqlFor(em, entitySnapshot);
        if (log.isTraceEnabled()) {
            log.trace("Executing UPSERT SQL: {}", sqlWithParams.sql());
        }
        return executeNativeQuery(em, sqlWithParams.sql(), sqlWithParams.params());
    }

    private int executeH2Upsert(EntityManager em) {
        T entitySnapshot = this.entity;
        return executeH2UpsertFor(em, entitySnapshot);
    }

    private int executeSimpleInsert(EntityManager em, List<EntityFieldExtractor.EntityFieldValue> allFieldValues) {
        List<String> columns = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (EntityFieldExtractor.EntityFieldValue fv : allFieldValues) {
            if (fv.value() != null || !fieldExtractor.isAutoGeneratedId(fv.fieldName())) {
                columns.add(fv.columnName());
                params.add(fv.value());
            }
        }
        var h2 = (H2Dialect)DialectDetector.DIALECT_STRATEGIES.get("h2");
        String escapedTable = h2.escapeIdentifier(resolveTableName());
        List<String> escapedColumns = new ArrayList<>();
        for (String col : columns) {
            escapedColumns.add(h2.escapeIdentifier(col));
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(escapedTable).append(" (");
        sql.append(String.join(", ", escapedColumns));
        sql.append(") VALUES (");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        sql.append(")");
        if (log.isTraceEnabled()) {
            log.trace("Executing H2 INSERT SQL: {}", sql);
        }
        return executeNativeQuery(em, sql.toString(), params);
    }

    private int executeConditionalUpdate(EntityManager em, List<EntityFieldExtractor.EntityFieldValue> allFieldValues,
        List<String> conflictColumns) {
        var h2 = (H2Dialect)DialectDetector.DIALECT_STRATEGIES.get("h2");
        List<String> setClauses = new ArrayList<>();
        List<Object> setParams = new ArrayList<>();
        // 使用数据库列名作为 key，因为 updateFields 现在存储的是列名
        java.util.Map<String, EntityFieldExtractor.EntityFieldValue> fieldValueMap = new java.util.LinkedHashMap<>();
        for (EntityFieldExtractor.EntityFieldValue fv : allFieldValues) {
            fieldValueMap.put(fv.columnName(), fv);
        }
        for (String columnName : updateFields) {
            EntityFieldExtractor.EntityFieldValue fv = fieldValueMap.get(columnName);
            if (fv != null) {
                setClauses.add(h2.escapeIdentifier(fv.columnName()) + " = ?");
                setParams.add(fv.value());
            }
        }
        if (setClauses.isEmpty()) {
            return 0;
        }
        List<String> whereClauses = new ArrayList<>();
        List<Object> whereParams = new ArrayList<>();
        for (String col : conflictColumns) {
            for (EntityFieldExtractor.EntityFieldValue fv : allFieldValues) {
                if (fv.columnName().equals(col)) {
                    whereClauses.add(h2.escapeIdentifier(col) + " = ?");
                    whereParams.add(fv.value());
                }
            }
        }
        StringBuilder sql =
            new StringBuilder("UPDATE ").append(h2.escapeIdentifier(resolveTableName())).append(" SET ");
        sql.append(String.join(", ", setClauses));
        sql.append(" WHERE ");
        sql.append(String.join(" AND ", whereClauses));
        List<Object> allParams = new ArrayList<>(setParams);
        allParams.addAll(whereParams);
        if (log.isTraceEnabled()) {
            log.trace("Executing H2 UPDATE SQL: {}", sql);
        }
        return executeNativeQuery(em, sql.toString(), allParams);
    }

    private int executeNativeQuery(EntityManager em, String sql, List<Object> params) {
        var query = em.createNativeQuery(sql);
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
     * @throws MyJpaPlusException 如果在 JTA 环境中没有活动事务
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
            if (!isJtaTransactionActive(em)) {
                throw new MyJpaPlusException("JTA environment detected but no active transaction. "
                    + "Use @Transactional annotation or manually begin a transaction before calling executeInTransaction().");
            }
            return execute(em);
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

    private static boolean isJtaTransactionActive(EntityManager em) {
        try {
            EntityTransaction tx = em.getTransaction();
            if (tx != null) {
                return tx.isActive();
            }
        } catch (Exception ignored) {
            log.debug("getTransaction() threw exception in JTA environment: {}", ignored.getMessage());
        }
        try {
            Class<?> sessionClass = Class.forName("org.hibernate.Session");
            Object session = em.unwrap(sessionClass);
            java.lang.reflect.Method getTransaction = sessionClass.getMethod("getTransaction");
            Object transaction = getTransaction.invoke(session);
            if (transaction == null) {
                return false;
            }
            java.lang.reflect.Method isActive = transaction.getClass().getMethod("isActive");
            return (Boolean)isActive.invoke(transaction);
        } catch (ClassNotFoundException e) {
            log.debug("Hibernate not available for transaction state detection");
        } catch (ReflectiveOperationException e) {
            log.debug("Cannot determine transaction state via Hibernate reflection: {}", e.getMessage());
        } catch (Exception e) {
            log.debug("Cannot determine transaction state via Hibernate: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 批量执行 UPSERT 操作。
     *
     * @param entities 要 UPSERT 的实体列表
     * @param em 实体管理器
     * @param batchSize 每批大小，建议值为 50-200
     * @return 受影响的总行数
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
        if (entities.size() > 10_000) {
            log.warn(
                "Large entity list size ({}). This may cause excessive memory usage. "
                    + "Consider using executeBatchInSeparateTransactions() or processing entities in smaller chunks.",
                entities.size());
        }
        int total = 0;
        int count = 0;
        for (T ent : entities) {
            total += executeSingle(em, ent);
            count++;
            if (count % batchSize == 0) {
                em.flush();
                em.clear();
                if (log.isDebugEnabled()) {
                    log.debug("Batch UPSERT: {} entities processed (total affected: {})", count, total);
                }
            }
        }
        if (count % batchSize != 0) {
            em.flush();
            em.clear();
        }
        return total;
    }

    /**
     * 分批在独立事务中执行 UPSERT 操作。
     *
     * @param entities 要 UPSERT 的实体列表
     * @param em 实体管理器
     * @param batchSize 每批大小
     * @return 受影响的总行数
     */
    public int executeBatchInSeparateTransactions(List<T> entities, EntityManager em, int batchSize) {
        if (entities == null || entities.isEmpty()) {
            throw new IllegalArgumentException("entities must not be null or empty");
        }
        if (em == null) {
            throw new IllegalArgumentException("em must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            log.warn("executeBatchInSeparateTransactions called within an active Spring transaction. "
                + "All operations will execute within the existing transaction. "
                + "Use executeBatch() for Spring-managed transactions.");
            return executeBatch(entities, em, batchSize);
        }
        int total = 0;
        int count = 0;
        EntityTransaction tx = null;
        boolean txStarted = false;
        for (T ent : entities) {
            if (count % batchSize == 0) {
                if (txStarted && tx != null && tx.isActive()) {
                    em.flush();
                    tx.commit();
                }
                tx = em.getTransaction();
                if (tx != null && !tx.isActive()) {
                    tx.begin();
                    txStarted = true;
                } else if (tx != null && tx.isActive()) {
                    throw new MyJpaPlusException("executeBatchInSeparateTransactions requires no active transaction. "
                        + "An active RESOURCE_LOCAL transaction was detected. "
                        + "Use executeBatch() to run within the existing transaction.");
                } else if (tx == null) {
                    throw new MyJpaPlusException("Cannot manage transactions in JTA environment. "
                        + "Use @Transactional annotation or MyJpaTemplate.executeBatch() instead.");
                }
            }
            try {
                count++;
                total += executeSingle(em, ent);
                if (count % batchSize == 0) {
                    em.clear();
                    if (log.isDebugEnabled()) {
                        log.debug("Batch UPSERT (separate tx): {} entities processed (total affected: {})", count,
                            total);
                    }
                }
            } catch (RuntimeException e) {
                if (txStarted && tx != null && tx.isActive()) {
                    try {
                        tx.rollback();
                    } catch (Exception rollbackEx) {
                        e.addSuppressed(rollbackEx);
                    }
                }
                throw e;
            }
        }
        if (txStarted && tx != null && tx.isActive()) {
            try {
                em.flush();
                tx.commit();
            } catch (RuntimeException e) {
                if (tx.isActive()) {
                    try {
                        tx.rollback();
                    } catch (Exception rollbackEx) {
                        e.addSuppressed(rollbackEx);
                    }
                }
                throw e;
            }
        }
        return total;
    }

    private int executeSingle(EntityManager em, T entityToMerge) {
        String dialect = DialectDetector.detectDialect(em);
        if ("h2".equals(dialect)) {
            return executeH2UpsertFor(em, entityToMerge);
        }
        SqlWithParams sqlWithParams = buildSqlFor(em, entityToMerge);
        if (log.isTraceEnabled()) {
            log.trace("Executing UPSERT SQL: {}", sqlWithParams.sql());
        }
        return executeNativeQuery(em, sqlWithParams.sql(), sqlWithParams.params());
    }

    private SqlWithParams buildSqlFor(EntityManager em, T entity) {
        List<String> effectiveConflictFields =
            conflictFields.isEmpty() ? fieldExtractor.resolveIdColumnNames() : new ArrayList<>(conflictFields);
        Set<String> conflictSet = new LinkedHashSet<>(effectiveConflictFields);
        List<EntityFieldExtractor.EntityFieldValue> allFieldValues = fieldExtractor.extractFieldValues(entity);
        List<String> insertColumns = new ArrayList<>();
        List<EntityFieldExtractor.EntityFieldValue> insertFieldValues = new ArrayList<>();
        for (EntityFieldExtractor.EntityFieldValue fv : allFieldValues) {
            if (fv.value() != null || !fieldExtractor.isAutoGeneratedId(fv.fieldName())) {
                insertColumns.add(fv.columnName());
                insertFieldValues.add(fv);
            }
        }
        List<String> effectiveUpdateFields =
            explicitUpdateFields ? updateFields : allNonConflictColumns(allFieldValues, conflictSet);
        String tableName = resolveTableName();
        String dialect = DialectDetector.detectDialect(em);
        DialectStrategy strategy = DialectDetector.DIALECT_STRATEGIES.get(dialect);
        if (strategy == null) {
            throw new MyJpaPlusException(
                "Unsupported database dialect: " + dialect + ". Supported dialects: postgresql, mysql, h2");
        }
        return strategy.buildUpsertSql(tableName, insertColumns, insertFieldValues, effectiveConflictFields,
            effectiveUpdateFields);
    }

    private int executeH2UpsertFor(EntityManager em, T entity) {
        List<String> effectiveConflictFields =
            conflictFields.isEmpty() ? fieldExtractor.resolveIdColumnNames() : new ArrayList<>(conflictFields);
        List<EntityFieldExtractor.EntityFieldValue> allFieldValues = fieldExtractor.extractFieldValues(entity);
        boolean allConflictKeysNull = true;
        for (EntityFieldExtractor.EntityFieldValue fv : allFieldValues) {
            if (effectiveConflictFields.contains(fv.columnName()) && fv.value() != null) {
                allConflictKeysNull = false;
                break;
            }
        }
        if (allConflictKeysNull) {
            return executeSimpleInsert(em, allFieldValues);
        }
        if (explicitUpdateFields) {
            int maxRetries = 3;
            for (int attempt = 0; attempt < maxRetries; attempt++) {
                try {
                    int updated = executeConditionalUpdate(em, allFieldValues, effectiveConflictFields);
                    if (updated > 0) {
                        return updated;
                    }
                    return executeSimpleInsert(em, allFieldValues);
                } catch (jakarta.persistence.PersistenceException e) {
                    if (attempt < maxRetries - 1 && e.getMessage() != null
                        && e.getMessage().toLowerCase().contains("unique")) {
                        long backoffMs = (long)(10 * Math.pow(2, attempt))
                            + java.util.concurrent.ThreadLocalRandom.current().nextLong(0, 10);
                        if (log.isDebugEnabled()) {
                            log.debug("H2 UPSERT race condition detected on attempt {}/{}, retrying after {}ms",
                                attempt + 1, maxRetries, backoffMs);
                        }
                        try {
                            Thread.sleep(backoffMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            e.addSuppressed(ie);
                            throw e;
                        }
                        continue;
                    }
                    throw e;
                }
            }
            throw new IllegalStateException("H2 UPSERT retry limit (" + maxRetries + ") exhausted. "
                + "Cannot fall back to MERGE INTO because explicit update fields are specified. "
                + "MERGE INTO would overwrite all columns, violating partial update contract.");
        }
        SqlWithParams sqlWithParams = buildSqlFor(em, entity);
        if (log.isTraceEnabled()) {
            log.trace("Executing H2 MERGE SQL: {}", sqlWithParams.sql());
        }
        return executeNativeQuery(em, sqlWithParams.sql(), sqlWithParams.params());
    }

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
            String name = tableName.toString();
            IdentifierValidator.validateTableName(name);
            return name;
        }
        jakarta.persistence.Entity entityAnnotation = entityClass.getAnnotation(jakarta.persistence.Entity.class);
        if (entityAnnotation != null && !entityAnnotation.name().isEmpty()) {
            String name = entityAnnotation.name();
            IdentifierValidator.validateTableName(name);
            return name;
        }
        return StringHelper.camelToSnake(entityClass.getSimpleName());
    }

    private List<String> allNonConflictColumns(List<EntityFieldExtractor.EntityFieldValue> allFieldValues,
        Set<String> conflictSet) {
        List<String> columns = new ArrayList<>();
        for (EntityFieldExtractor.EntityFieldValue fv : allFieldValues) {
            if (!conflictSet.contains(fv.columnName())) {
                columns.add(fv.columnName());
            }
        }
        return columns;
    }
}
