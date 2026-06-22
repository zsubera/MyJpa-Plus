package com.zsubera.jpa.update;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.IdentifierValidator;
import com.zsubera.jpa.util.LambdaUtils;
import com.zsubera.jpa.util.QueryTimeoutHelper;
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
 * // 自动检测方言
 * new MergeSpec<>(User.class).withEntity(user).onConflict(User::getEmail).updateOnConflict(User::getName, User::getAge)
 *     .execute(em);
 *
 * // 显式指定方言（适用于 Aurora、PolarDB 等兼容数据库）
 * new MergeSpec<>(User.class).dialect(new MysqlDialect()).withEntity(user).onConflict(User::getEmail)
 *     .updateOnConflict(User::getName).execute(em);
 * }</pre>
 *
 * @param <T> 实体类型
 * @see DialectStrategy
 * @see DialectDetector
 */
public class MergeSpec<T> {

    private static final Logger log = LoggerFactory.getLogger(MergeSpec.class);

    private final Class<T> entityClass;
    private final EntityFieldExtractor<T> fieldExtractor;
    private volatile T entity;
    private final List<String> conflictFields = new ArrayList<>();
    private final List<String> updateFields = new ArrayList<>();
    private boolean explicitUpdateFields = false;
    private DialectStrategy customDialect;

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
     * 显式指定数据库方言，绕过自动检测。
     *
     * <p>
     * 调用此方法后，UPSERT 操作将使用指定的方言而非通过 {@link DialectDetector} 自动检测。 适用于自动检测无法识别的数据库（如 Aurora、PolarDB 等兼容数据库）。
     *
     * <p>
     * <strong>必须在 {@link #execute(EntityManager)}、{@link #executeBatch(List, EntityManager)} 等执行方法之前调用。</strong>
     *
     * @param dialect 方言策略实例
     * @return 当前构建器实例，支持链式调用
     * @throws IllegalArgumentException 如果 dialect 为 null
     */
    public MergeSpec<T> dialect(DialectStrategy dialect) {
        if (dialect == null) {
            throw new IllegalArgumentException("dialect must not be null");
        }
        this.customDialect = dialect;
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
        warnIdentityGeneration();
        // Snapshot entity to avoid race condition with concurrent withEntity() calls
        T entitySnapshot = this.entity;
        return executeWith(em, entitySnapshot);
    }

    private DialectStrategy resolveDialectStrategy(EntityManager em) {
        if (customDialect != null) {
            return customDialect;
        }
        String dialect = DialectDetector.detectDialect(em);
        DialectStrategy strategy = DialectDetector.getDialectStrategy(dialect);
        if (strategy == null) {
            throw new MyJpaPlusException("Unsupported database dialect: " + dialect
                + ". Supported dialects: postgresql, mysql, oracle, sqlserver. "
                + "Register a custom dialect via DialectDetector.registerDialect().");
        }
        return strategy;
    }

    /**
     * 使用指定实体执行 UPSERT，不修改实例的 {@code entity} 字段。
     * 供 {@link #executeBatch} 内部使用，避免状态污染。
     */
    private int executeWith(EntityManager em, T entityToMerge) {
        DialectStrategy strategy = resolveDialectStrategy(em);
        return executeWith(em, entityToMerge, strategy);
    }

    private int executeWith(EntityManager em, T entityToMerge, DialectStrategy strategy) {
        SqlWithParams sqlWithParams = buildSqlFor(em, entityToMerge, strategy);
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
     * 验证实体类是否使用 IDENTITY 策略的自增 ID（仅当未指定显式冲突列时）。
     * 当检测到 IDENTITY 策略时记录警告日志，但不阻止执行。
     *
     * <p>
     * UPSERT 操作要求业务唯一键作为冲突检测列，自增 ID 实体建议显式指定冲突列。
     */
    private void warnIdentityGeneration() {
        if (!conflictFields.isEmpty()) {
            return;
        }
        Class<?> clazz = entityClass;
        while (clazz != null && clazz != Object.class) {
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(jakarta.persistence.Id.class)
                    || field.isAnnotationPresent(jakarta.persistence.EmbeddedId.class)) {
                    jakarta.persistence.GeneratedValue gva =
                        field.getAnnotation(jakarta.persistence.GeneratedValue.class);
                    if (gva != null && gva.strategy() == jakarta.persistence.GenerationType.IDENTITY) {
                        log.warn(
                            "MergeSpec: entity '{}' uses @GeneratedValue(strategy=IDENTITY) on field '{}' "
                                + "without explicit onConflict(). UPSERT works best with a business unique key. "
                                + "Consider using onConflict() with a non-auto-generated unique field, "
                                + "or use @GeneratedValue(strategy=TABLE/SEQUENCE/UUID) instead.",
                            entityClass.getSimpleName(), field.getName());
                    }
                    return;
                }
            }
            clazz = clazz.getSuperclass();
        }
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
        warnIdentityGeneration();
        return executeInManagedTransaction(em, () -> execute(em));
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
        return executeInManagedTransaction(em, () -> executeBatch(entities, em));
    }

    /**
     * 在托管事务中执行操作。如果没有活动事务，自动创建新事务。
     */
    private int executeInManagedTransaction(EntityManager em, java.util.function.IntSupplier action) {
        return BulkTransactionHelper.executeInManagedTransaction(em, action);
    }

    /**
     * 批量执行 UPSERT 操作。
     *
     * @param entities 要 upsert 的实体列表
     * @param em 实体管理器
     * @return 受影响的总行数
     */
    public int executeBatch(List<T> entities, EntityManager em) {
        if (entities == null || entities.isEmpty()) {
            throw new IllegalArgumentException("entities must not be null or empty");
        }
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
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new MyJpaPlusException(
                "executeBatch requires an active transaction. Use executeBatchInTransaction() for auto-managed transactions, "
                    + "or executeBatchInSeparateTransactions() for per-batch isolation.");
        }
        DialectStrategy cachedStrategy = resolveDialectStrategy(em);
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
            total += executeWith(em, entity, cachedStrategy);
        }
        return total;
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
        // 预检 JTA 环境：RESOURCE_LOCAL 事务在循环外一次性确认
        EntityTransaction probeTx = em.getTransaction();
        if (probeTx == null) {
            throw new MyJpaPlusException("Cannot manage transactions in JTA environment. "
                + "Use @Transactional annotation or MyJpaTemplate.executeBatch() instead.");
        }
        DialectStrategy cachedStrategy = resolveDialectStrategy(em);
        int total = 0;
        int batchStart = 0;
        while (batchStart < entities.size()) {
            int batchEnd = Math.min(batchStart + batchSize, entities.size());
            int batchTotal = 0;
            EntityTransaction tx = em.getTransaction();
            if (tx == null || tx.isActive()) {
                throw new MyJpaPlusException("executeBatchInSeparateTransactions requires no active transaction. "
                    + "An active RESOURCE_LOCAL transaction was detected. "
                    + "Use executeBatch() to run within the existing transaction.");
            }
            tx.begin();
            try {
                for (int i = batchStart; i < batchEnd; i++) {
                    T ent = entities.get(i);
                    if (ent == null) {
                        throw new IllegalArgumentException("entities[" + i + "] must not be null");
                    }
                    SqlWithParams sqlWithParams = buildSqlFor(em, ent, cachedStrategy);
                    batchTotal += executeNativeQuery(em, sqlWithParams.sql(), sqlWithParams.params());
                }
                em.flush();
                tx.commit();
                total += batchTotal;
                if (log.isDebugEnabled()) {
                    log.debug("Batch UPSERT (separate tx): entities [{}-{}] committed (batch affected: {}, total: {})",
                        batchStart, batchEnd - 1, batchTotal, total);
                }
            } catch (RuntimeException e) {
                safeRollback(tx, e);
                throw e;
            } finally {
                em.clear();
            }
            batchStart = batchEnd;
        }
        return total;
    }

    private void safeRollback(EntityTransaction tx, Exception original) {
        BulkTransactionHelper.safeRollback(tx, original);
    }

    private SqlWithParams buildSqlFor(EntityManager em, T entity, DialectStrategy strategy) {
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
        return IdentifierValidator.resolveTableName(entityClass);
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
        return BulkTransactionHelper.isJtaTransactionActive(em);
    }
}
