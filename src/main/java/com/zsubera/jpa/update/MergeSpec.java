package com.zsubera.jpa.update;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.IdentifierValidator;
import com.zsubera.jpa.util.LambdaUtils;
import com.zsubera.jpa.util.QueryTimeoutHelper;
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
 * 支持两种数据库方言：
 * <ul>
 * <li>PostgreSQL: {@code INSERT ... ON CONFLICT (...) DO UPDATE SET ...}</li>
 * <li>MySQL: {@code INSERT ... ON DUPLICATE KEY UPDATE ...}</li>
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
     * 获取实体类。
     *
     * @return 实体类
     */
    public Class<T> getEntityClass() {
        return entityClass;
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
        // Snapshot entity to avoid race condition with concurrent withEntity() calls
        T entitySnapshot = this.entity;
        return executeWith(em, entitySnapshot);
    }

    /**
     * 使用指定实体执行 UPSERT，不修改实例的 {@code entity} 字段。
     * 供 {@link #executeBatch} 内部使用，避免状态污染。
     */
    private int executeWith(EntityManager em, T entityToMerge) {
        String dialect = DialectDetector.detectDialect(em);
        return executeWith(em, entityToMerge, dialect);
    }

    /**
     * 使用指定实体和缓存的方言执行 UPSERT。
     * 供 {@link #executeBatch} 批量循环内部使用，避免重复检测方言。
     */
    private int executeWith(EntityManager em, T entityToMerge, String dialect) {
        SqlWithParams sqlWithParams = buildSqlFor(em, entityToMerge, dialect);
        if (log.isTraceEnabled()) {
            log.trace("Executing UPSERT SQL: {}", sqlWithParams.sql());
        }
        return executeNativeQuery(em, sqlWithParams.sql(), sqlWithParams.params());
    }

    private int executeNativeQuery(EntityManager em, String sql, List<Object> params) {
        var query = em.createNativeQuery(sql);
        QueryTimeoutHelper.applyTimeout(query);
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
                    log.error("Transaction rollback failed after merge failure", rollbackEx);
                    e.addSuppressed(rollbackEx);
                }
            }
            throw e;
        } catch (Exception e) {
            if (isNewTransaction && tx.isActive()) {
                try {
                    tx.rollback();
                } catch (Exception rollbackEx) {
                    log.error("Transaction rollback failed after merge failure", rollbackEx);
                    e.addSuppressed(rollbackEx);
                }
            }
            throw new MyJpaPlusException("Merge operation failed: " + e.getClass().getSimpleName(), e);
        }
    }

    /**
     * 批量执行 UPSERT 操作。
     *
     * @param entities 要 upsert 的实体列表
     * @param em 实体管理器
     * @return 受影响的总行数
     */
    public int executeBatch(List<T> entities, EntityManager em) {
        return executeBatch(entities, em, 100);
    }

    /**
     * 批量执行 UPSERT 操作，指定批次大小。
     *
     * @param entities 要 upsert 的实体列表
     * @param em 实体管理器
     * @param batchSize 每批处理的实体数量
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
        String cachedDialect = DialectDetector.detectDialect(em);
        int total = 0;
        for (int i = 0; i < entities.size(); i++) {
            T entity = entities.get(i);
            if (entity == null) {
                throw new IllegalArgumentException("entities[" + i + "] must not be null");
            }
            if (i > 0 && i % batchSize == 0) {
                em.flush();
                em.clear();
            }
            total += executeWith(em, entity, cachedDialect);
        }
        return total;
    }

    /**
     * 在事务中批量执行 UPSERT 操作。
     *
     * @param entities 要 upsert 的实体列表
     * @param em 实体管理器
     * @return 受影响的总行数
     */
    public int executeBatchInTransaction(List<T> entities, EntityManager em) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return executeBatch(entities, em);
        }
        EntityTransaction tx = em.getTransaction();
        if (tx == null) {
            if (!isJtaTransactionActive(em)) {
                throw new MyJpaPlusException("JTA environment detected but no active transaction. "
                    + "Use @Transactional annotation or manually begin a transaction.");
            }
            return executeBatch(entities, em);
        }
        boolean isNewTransaction = !tx.isActive();
        if (isNewTransaction) {
            tx.begin();
        }
        try {
            int result = executeBatch(entities, em);
            if (isNewTransaction) {
                tx.commit();
            }
            return result;
        } catch (RuntimeException e) {
            if (isNewTransaction && tx.isActive()) {
                try {
                    tx.rollback();
                } catch (Exception rollbackEx) {
                    log.error("Transaction rollback failed after batch merge failure", rollbackEx);
                    e.addSuppressed(rollbackEx);
                }
            }
            throw e;
        } catch (Exception e) {
            if (isNewTransaction && tx.isActive()) {
                try {
                    tx.rollback();
                } catch (Exception rollbackEx) {
                    log.error("Transaction rollback failed after batch merge failure", rollbackEx);
                    e.addSuppressed(rollbackEx);
                }
            }
            throw new MyJpaPlusException("Batch merge operation failed: " + e.getClass().getSimpleName(), e);
        }
    }

    /**
     * 分批在独立事务中执行 UPSERT 操作。
     *
     * @param entities 要 upsert 的实体列表
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
            throw new MyJpaPlusException(
                "executeBatchInSeparateTransactions cannot be called within an active @Transactional method. "
                    + "Use executeBatch() to run within the existing transaction, "
                    + "or call from a non-transactional context for true batch isolation.");
        }
        int total = 0;
        int count = 0;
        int batchStartCount = 0;
        int batchStartTotal = 0;
        String cachedDialect = DialectDetector.detectDialect(em);
        EntityTransaction tx = null;
        boolean txStarted = false;
        for (T ent : entities) {
            if (count % batchSize == 0) {
                if (txStarted && tx != null && tx.isActive()) {
                    try {
                        em.flush();
                        tx.commit();
                    } catch (RuntimeException commitEx) {
                        try {
                            tx.rollback();
                        } catch (Exception rollbackEx) {
                            log.error("Transaction rollback failed after batch commit failure", rollbackEx);
                            commitEx.addSuppressed(rollbackEx);
                        }
                        throw commitEx;
                    }
                }
                tx = em.getTransaction();
                if (tx != null && !tx.isActive()) {
                    tx.begin();
                    txStarted = true;
                    batchStartCount = count;
                    batchStartTotal = total;
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
                SqlWithParams sqlWithParams = buildSqlFor(em, ent, cachedDialect);
                total += executeNativeQuery(em, sqlWithParams.sql(), sqlWithParams.params());
                count++;
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
                        log.error("Transaction rollback failed after batch UPSERT failure", rollbackEx);
                        e.addSuppressed(rollbackEx);
                    }
                }
                total = batchStartTotal;
                throw e;
            }
        }
        if (txStarted && tx != null && tx.isActive()) {
            try {
                em.flush();
                tx.commit();
            } catch (RuntimeException e) {
                try {
                    tx.rollback();
                } catch (Exception rollbackEx) {
                    log.error("Transaction rollback failed after final batch commit failure", rollbackEx);
                    e.addSuppressed(rollbackEx);
                }
                total = batchStartTotal;
                throw e;
            }
        }
        return total;
    }

    private SqlWithParams buildSqlFor(EntityManager em, T entity, String dialect) {
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
        DialectStrategy strategy = DialectDetector.DIALECT_STRATEGIES.get(dialect);
        if (strategy == null) {
            throw new MyJpaPlusException(
                "Unsupported database dialect: " + dialect + ". Supported dialects: postgresql, mysql");
        }
        return strategy.buildUpsertSql(tableName, insertColumns, insertFieldValues, effectiveConflictFields,
            effectiveUpdateFields);
    }

    /**
     * 解析实体对应的数据库表名。优先使用 {@code @Table(name)} 注解，
     * 其次使用 {@code @Entity(name)} 注解，最后使用驼峰转下划线策略。
     *
     * <p>
     * <strong>注意：</strong>JPA Metamodel 的 {@code EntityType.getName()} 返回的是
     * {@code @Entity(name)} 的值而非表名，因此此处通过注解直接扫描获取表名。
     * 对于使用 {@code @SecondaryTable} 或 {@code @AttributeOverride} 的复杂映射，
     * 建议显式使用 {@code @Table(name)} 注解。
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
        String name = StringHelper.camelToSnake(entityClass.getSimpleName());
        IdentifierValidator.validateTableName(name);
        return name;
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

    private static boolean isJtaTransactionActive(EntityManager em) {
        try {
            return em.getTransaction() != null && em.getTransaction().isActive();
        } catch (Exception e) {
            return false;
        }
    }
}
