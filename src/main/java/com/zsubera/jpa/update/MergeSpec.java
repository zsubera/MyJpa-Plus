package com.zsubera.jpa.update;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.monitor.SqlSanitizer;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.IdentifierValidator;
import com.zsubera.jpa.util.LambdaUtils;
import com.zsubera.jpa.util.SampledEvictionCache;
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
 * <strong>⚠️ 重要：</strong>此构建器使用 {@code em.createNativeQuery()} 执行原生 SQL，
 * <strong>绕过</strong> JPA 生命周期回调（{@code @PrePersist}、{@code @PreUpdate}、{@code @PreRemove} 等）。
 * 如需触发生命周期回调，请在执行 UPSERT 前手动处理：
 * <pre>{@code
 * // 如果实体已存在于持久化上下文，先 flush 确保回调触发
 * if (em.contains(entity)) {
 *     em.flush();
 * }
 * new MergeSpec<>(User.class).withEntity(entity).onConflict(User::getEmail).execute(em);
 * }</pre>
 * 对于包含 {@code @PrePersist} / {@code @PreUpdate} 回调的实体，建议在 UPSERT 前显式调用 {@code entityManager.persist(entity)}
 * 或 {@code entityManager.merge(entity)} 触发回调，然后 flush。或使用 JPA 标准 {@code merge()} 作为替代。
 *
 * <p>
 * 支持四种数据库方言：
 * <ul>
 * <li>PostgreSQL: {@code INSERT ... ON CONFLICT (...) DO UPDATE SET ...}</li>
 * <li>MySQL: {@code INSERT ... ON DUPLICATE KEY UPDATE ...}</li>
 * <li>Oracle: {@code MERGE INTO ... USING dual ...}</li>
 * <li>SQL Server: {@code MERGE INTO ... USING (...) AS source ...}</li>
 * </ul>
 *
 * <p>
 * <strong>线程安全说明：</strong>此构建器非线程安全。不要在多个线程间共享同一个 {@code MergeSpec} 实例。
 * 如果需要在多线程环境下使用，请为每个线程创建独立的实例。
 *
 * <p>
 * <strong>并发 UPSERT 说明：</strong>UPSERT 操作在高并发场景下存在竞态条件。 两个并发事务可能同时检测到"不存在"并尝试插入，导致唯一约束冲突。 建议在高并发场景下：
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

    /** 缓存实体类的 IDENTITY 策略警告检查结果，避免每次 UPSERT 执行时重复反射扫描。 */
    private static final SampledEvictionCache<String, Boolean> IDENTITY_WARNING_CACHE =
        new SampledEvictionCache<>(256, 0.75, 100, 64);

    /** 表名缓存，避免每次 UPSERT 重复反射扫描 @Table/@Entity 注解。 */
    private static final SampledEvictionCache<Class<?>, String> TABLE_NAME_CACHE =
        new SampledEvictionCache<>(512, 0.75, 100, 128);

    /** executeBatch(List, EntityManager) 的默认批次大小。 */
    static final int DEFAULT_BATCH_SIZE = 100;

    private final Class<T> entityClass;
    private final EntityFieldExtractor<T> fieldExtractor;
    private T entity;
    private final List<String> conflictFields = new ArrayList<>();
    private final List<String> updateFields = new ArrayList<>();
    private boolean explicitUpdateFields = false;
    private DialectStrategy customDialect;
    private PersistenceContextStrategy persistenceContextStrategy = PersistenceContextStrategy.AUTO_CLEAR;

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
     * 设置批量操作后的持久化上下文管理策略。
     */
    public MergeSpec<T> persistenceStrategy(PersistenceContextStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("strategy must not be null");
        }
        this.persistenceContextStrategy = strategy;
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

    /**
     * 执行 UPSERT 操作前先 flush 持久化上下文，触发实体上的 JPA 生命周期回调
     * （{@code @PrePersist}、{@code @PreUpdate}、{@code @Version} 自动递增等），
     * 然后再执行原生 UPSERT SQL。
     *
     * <p>
     * 此方法解决了 {@link #execute(EntityManager)} 绕过 JPA 回调的问题。
     * 如果实体已与持久化上下文关联（托管状态），flush 会同步所有待定的更改，
     * 包括通过回调设置的时间戳、业务编号等字段。flush 后 UPSERT 使用更新后的字段值。
     *
     * <p>
     * 如果实体是游离状态（不在持久化上下文中），则先执行 {@code em.merge(entity)}
     * 使其变为托管状态再 flush，确保所有回调已触发。merge 后再用托管实体的最新
     * 字段值执行原生 UPSERT。
     *
     * <p>
     * <strong>注意：</strong>返回值始终为 1，因为 JPA {@code merge()} + {@code flush()} 无法获取实际影响行数。
     * 此方法适用于需要触发 JPA 生命周期回调的场景，如果需要精确的影响行数，请使用 {@link #execute(EntityManager)}。
     *
     * @param em 实体管理器
     * @return 始终返回 1（JPA merge+flush 无法获取实际影响行数）
     * @throws IllegalStateException 如果未指定实体
     */
    public int executeWithCallbacks(EntityManager em) {
        if (em == null) {
            throw new IllegalArgumentException("em must not be null");
        }
        if (entity == null) {
            throw new IllegalStateException("Entity must be specified via withEntity() before executing");
        }
        warnIdentityGeneration();
        if (!conflictFields.isEmpty()) {
            throw new UnsupportedOperationException(
                "executeWithCallbacks() does not support onConflict() " + "(conflict fields: " + conflictFields + "). "
                    + "onConflict() is only supported by native UPSERT (execute()). "
                    + "Use executeWithCallbacks() without onConflict(), or use execute() for conflict handling.");
        }
        if (!updateFields.isEmpty()) {
            throw new UnsupportedOperationException(
                "executeWithCallbacks() does not support updateOnConflict() " + "(update fields: " + updateFields
                    + "). " + "updateOnConflict() is only supported by native UPSERT (execute()). "
                    + "Use executeWithCallbacks() without updateOnConflict(), or use execute() for conflict handling.");
        }
        T entitySnapshot = this.entity;
        // Step 1: merge if detached to make managed, then flush to trigger callbacks
        if (!em.contains(entitySnapshot)) {
            em.merge(entitySnapshot);
        }
        em.flush();
        // Step 2: after flush, entity is managed and persisted — skip native UPSERT
        // since merge+flush already handled the persistence and lifecycle callbacks.
        return 1;
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
        List<String> effectiveConflictFields =
            conflictFields.isEmpty() ? fieldExtractor.resolveIdColumnNames() : new ArrayList<>(conflictFields);
        java.util.Set<String> conflictSet = new java.util.LinkedHashSet<>(effectiveConflictFields);
        return executeWith(em, entityToMerge, strategy, effectiveConflictFields, conflictSet);
    }

    private int executeWith(EntityManager em, T entityToMerge, DialectStrategy strategy,
        List<String> preComputedConflictFields, java.util.Set<String> preComputedConflictSet) {
        SqlWithParams sqlWithParams =
            buildSqlFor(em, entityToMerge, strategy, preComputedConflictFields, preComputedConflictSet);
        if (log.isTraceEnabled()) {
            log.trace("Executing UPSERT SQL: {}", SqlSanitizer.sanitize(sqlWithParams.sql()));
        }
        validateGeneratedSql(sqlWithParams.sql());
        return executeNativeQuery(em, sqlWithParams.sql(), sqlWithParams.params());
    }

    /**
     * 使用 JSqlParser 验证生成的 UPSERT SQL 语法。解析失败时记录警告但不阻断执行。
     */
    private static void validateGeneratedSql(String sql) {
        try {
            net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql);
        } catch (net.sf.jsqlparser.JSQLParserException e) {
            log.warn("Generated UPSERT SQL syntax warning: {} — {}", e.getMessage(),
                sql.length() > 100 ? sql.substring(0, 100) + "..." : sql);
        }
    }

    private int executeNativeQuery(EntityManager em, String sql, List<Object> params) {
        var query = em.createNativeQuery(sql);
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
        String cacheKey = entityClass.getName();
        Boolean cached = IDENTITY_WARNING_CACHE.get(cacheKey);
        if (Boolean.TRUE.equals(cached)) {
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
                    IDENTITY_WARNING_CACHE.put(cacheKey, Boolean.TRUE);
                    return;
                }
            }
            clazz = clazz.getSuperclass();
        }
        // No @Id found — cache FALSE to allow future re-detection if the class changes
        IDENTITY_WARNING_CACHE.put(cacheKey, Boolean.FALSE);
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
        return executeBatch(entities, em, DEFAULT_BATCH_SIZE);
    }

    /**
     * 批量执行 UPSERT 操作，指定批次大小。
     *
     * <p>
     * 优先尝试多行 UPSERT（{@link DialectStrategy#buildBatchUpsertSql}），
     * 如果方言不支持（抛出 {@link UnsupportedOperationException}），则回退到逐行 UPSERT。
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
            if (!isEntityTransactionActive(em)) {
                throw new MyJpaPlusException(
                    "executeBatch requires an active transaction. Use executeBatchInTransaction() for auto-managed transactions, "
                        + "or executeBatchInSeparateTransactions() for per-batch isolation.");
            }
        }
        warnIdentityGeneration();
        DialectStrategy cachedStrategy = resolveDialectStrategy(em);
        if (cachedStrategy.supportsBatchUpsert()) {
            return doBatchMultiRow(entities, em, batchSize, cachedStrategy);
        }
        log.debug("Dialect '{}' does not support multi-row batch UPSERT, using single-row", cachedStrategy.name());
        return doBatchSingleRow(entities, em, batchSize, cachedStrategy);
    }

    private int doBatchSingleRow(List<T> entities, EntityManager em, int batchSize, DialectStrategy strategy) {
        List<String> effectiveConflictFields =
            conflictFields.isEmpty() ? fieldExtractor.resolveIdColumnNames() : new ArrayList<>(conflictFields);
        Set<String> conflictSet = new LinkedHashSet<>(effectiveConflictFields);
        int total = 0;
        try {
            for (int i = 0; i < entities.size(); i++) {
                T entity = entities.get(i);
                if (entity == null) {
                    throw new IllegalArgumentException("entities[" + i + "] must not be null");
                }
                if (i > 0 && i % batchSize == 0
                    && persistenceContextStrategy == PersistenceContextStrategy.AUTO_CLEAR) {
                    em.flush();
                    em.clear();
                }
                total += executeWith(em, entity, strategy, effectiveConflictFields, conflictSet);
            }
            if (persistenceContextStrategy == PersistenceContextStrategy.AUTO_CLEAR) {
                em.flush();
            }
        } catch (RuntimeException e) {
            if (em.isOpen()) {
                em.clear();
            }
            throw e;
        }
        return total;
    }

    private int doBatchMultiRow(List<T> entities, EntityManager em, int batchSize, DialectStrategy strategy) {
        List<String> effectiveConflictFields =
            conflictFields.isEmpty() ? fieldExtractor.resolveIdColumnNames() : new ArrayList<>(conflictFields);
        Set<String> conflictSet = new LinkedHashSet<>(effectiveConflictFields);
        String tableName = resolveTableName();
        int total = 0;
        try {
            for (int i = 0; i < entities.size(); i += batchSize) {
                int end = Math.min(i + batchSize, entities.size());

                // ponytail: 单次遍历提取字段值并缓存，避免对每个实体执行两次反射。
                // ponytail: 收集所有实体的列名并集（而非仅首个实体的列集），
                // 解决 @Embedded 字段在不同实体间 null/非null 导致列不一致的问题。
                // 缺失列的实体在构建 VALUES 时自动填充 NULL（第 576-591 行处理）。
                List<List<EntityFieldExtractor.EntityFieldValue>> cachedAllFvs = new ArrayList<>(end - i);
                java.util.LinkedHashSet<String> allColumnsSet = new java.util.LinkedHashSet<>();
                for (int j = i; j < end; j++) {
                    T entity = entities.get(j);
                    if (entity == null) {
                        throw new IllegalArgumentException("entities[" + j + "] must not be null");
                    }
                    List<EntityFieldExtractor.EntityFieldValue> allFvs = fieldExtractor.extractFieldValues(entity);
                    cachedAllFvs.add(allFvs);
                    for (EntityFieldExtractor.EntityFieldValue fv : allFvs) {
                        if (fv.value() != null || !fieldExtractor.isAutoGeneratedId(fv.fieldName())) {
                            allColumnsSet.add(fv.columnName());
                        }
                    }
                }
                List<String> insertColumns = new ArrayList<>(allColumnsSet);

                // 从当前批次所有实体的字段值中提取 UPDATE 列的并集。
                // 当 @Embedded 字段为 null 时，其子列不会出现在该实体的字段值中；
                // 如果批次中其他实体的该 @Embedded 非 null，则需要包含这些列以确保
                // 冲突行被正确更新。
                List<String> effectiveUpdateFields;
                if (explicitUpdateFields) {
                    effectiveUpdateFields = updateFields;
                } else {
                    java.util.Set<String> updateColSet = new java.util.LinkedHashSet<>();
                    for (List<EntityFieldExtractor.EntityFieldValue> allFvs : cachedAllFvs) {
                        for (EntityFieldExtractor.EntityFieldValue fv : allFvs) {
                            if (!conflictSet.contains(fv.columnName()) && fv.updatable()
                                && !fieldExtractor.isAutoGeneratedId(fv.fieldName())) {
                                updateColSet.add(fv.columnName());
                            }
                        }
                    }
                    effectiveUpdateFields = new ArrayList<>(updateColSet);
                }

                // ponytail: 计算需要 COALESCE 保护的列。
                // 当批次中某列在部分实体存在、部分实体缺失时（如混合 null/@Embedded），
                // 缺失实体会被填充 NULL。在 ON CONFLICT DO UPDATE SET 中，这些 NULL 会
                // 覆盖冲突行的已有值。通过对这些列应用 COALESCE(EXCLUDED.col, col)，
                // 使得 NULL 被忽略、已有数据得以保留。
                java.util.Set<String> coalesceColumns = java.util.Collections.emptySet();
                if (!explicitUpdateFields && !effectiveUpdateFields.isEmpty()) {
                    coalesceColumns = new java.util.HashSet<>();
                    for (String col : effectiveUpdateFields) {
                        boolean presentInAll = true;
                        for (List<EntityFieldExtractor.EntityFieldValue> allFvs : cachedAllFvs) {
                            boolean found = false;
                            for (EntityFieldExtractor.EntityFieldValue fv : allFvs) {
                                if (fv.columnName().equals(col)) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                presentInAll = false;
                                break;
                            }
                        }
                        if (!presentInAll) {
                            coalesceColumns.add(col);
                        }
                    }
                }

                // Build batch field values in unified column order using cached extraction
                List<List<EntityFieldExtractor.EntityFieldValue>> batchFieldValues =
                    new ArrayList<>(cachedAllFvs.size());
                for (List<EntityFieldExtractor.EntityFieldValue> allFvs : cachedAllFvs) {
                    // 使用线性扫描替代 HashMap，减少批量场景下的 GC 压力
                    // insertColumns 列表通常较小（<50），线性扫描 O(n*m) 可接受
                    List<EntityFieldExtractor.EntityFieldValue> insertFvs = new ArrayList<>(insertColumns.size());
                    for (String col : insertColumns) {
                        EntityFieldExtractor.EntityFieldValue fv = null;
                        for (EntityFieldExtractor.EntityFieldValue f : allFvs) {
                            if (f.columnName().equals(col)) {
                                fv = f;
                                break;
                            }
                        }
                        insertFvs
                            .add(fv != null ? fv : new EntityFieldExtractor.EntityFieldValue(col, col, null, true));
                    }
                    batchFieldValues.add(insertFvs);
                }
                SqlWithParams batchSql = strategy.buildBatchUpsertSql(tableName, insertColumns, batchFieldValues,
                    effectiveConflictFields, effectiveUpdateFields);
                // 为缺失列填充的 NULL 添加 COALESCE 保护，防止覆盖已有数据
                batchSql = new SqlWithParams(CoalesceUpsertTransformer.applyCoalesce(batchSql.sql(), coalesceColumns),
                    batchSql.params());
                total += executeNativeQuery(em, batchSql.sql(), batchSql.params());
                if (persistenceContextStrategy == PersistenceContextStrategy.AUTO_CLEAR) {
                    em.flush();
                    em.clear();
                }
            }
        } catch (RuntimeException e) {
            if (em.isOpen()) {
                em.clear();
            }
            throw e;
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
        try {
            em.getTransaction();
        } catch (IllegalStateException e) {
            throw new MyJpaPlusException("JTA environment detected. "
                + "executeBatchInSeparateTransactions requires RESOURCE_LOCAL transaction manager. "
                + "Use @Transactional annotation or MyJpaTemplate.executeBatch() instead.", e);
        }
        DialectStrategy cachedStrategy = resolveDialectStrategy(em);
        int total = 0;
        int batchStart = 0;
        int iterationCount = 0;
        // ponytail: 10000 与 BulkOperationTemplate.DEFAULT_MAX_BATCH_ITERATIONS 保持一致
        int maxIterations = com.zsubera.jpa.autoconfigure.GlobalConfigHolder.resolveMaxUpsertBatchIterations(10000);
        while (batchStart < entities.size()) {
            if (++iterationCount > maxIterations) {
                throw new MyJpaPlusException("executeBatchInSeparateTransactions exceeded max iterations ("
                    + maxIterations + ") with " + entities.size() + " entities at batchSize " + batchSize
                    + ". This is likely due to an extremely large entity list.");
            }
            int batchEnd = Math.min(batchStart + batchSize, entities.size());
            int committedBatch;
            try {
                committedBatch = executeSingleBatchInTransaction(entities, em, cachedStrategy, batchStart, batchEnd);
            } catch (RuntimeException | Error e) {
                log.error("Batch UPSERT (separate tx) failed at entities [{}-{}] after {} committed rows: {}",
                    batchStart, batchEnd - 1, total, e.getMessage());
                throw new MyJpaPlusException("Batch upsert failed after " + (iterationCount - 1) + " batches committed "
                    + total + " rows. Remaining rows were NOT committed.", e);
            }
            total += committedBatch;
            batchStart = batchEnd;
        }
        return total;
    }

    private int executeSingleBatchInTransaction(List<T> entities, EntityManager em, DialectStrategy strategy,
        int batchStart, int batchEnd) {
        EntityTransaction tx;
        try {
            tx = em.getTransaction();
        } catch (IllegalStateException e) {
            throw new MyJpaPlusException("JTA environment detected in executeSingleBatchInTransaction. "
                + "executeBatchInSeparateTransactions requires RESOURCE_LOCAL transaction manager.", e);
        }
        if (tx == null || tx.isActive()) {
            throw new MyJpaPlusException("executeBatchInSeparateTransactions requires no active transaction. "
                + "An active RESOURCE_LOCAL transaction was detected. "
                + "Use executeBatch() to run within the existing transaction.");
        }
        tx.begin();
        try {
            int batchTotal;
            if (strategy.supportsBatchUpsert()) {
                batchTotal =
                    doBatchMultiRow(entities.subList(batchStart, batchEnd), em, batchEnd - batchStart, strategy);
            } else {
                batchTotal = 0;
                for (int i = batchStart; i < batchEnd; i++) {
                    T ent = entities.get(i);
                    if (ent == null) {
                        throw new IllegalArgumentException("entities[" + i + "] must not be null");
                    }
                    SqlWithParams sqlWithParams = buildSqlFor(em, ent, strategy);
                    batchTotal += executeNativeQuery(em, sqlWithParams.sql(), sqlWithParams.params());
                }
            }
            em.flush();
            tx.commit();
            if (log.isDebugEnabled()) {
                log.debug("Batch UPSERT (separate tx): entities [{}-{}] committed (batch affected: {})", batchStart,
                    batchEnd - 1, batchTotal);
            }
            return batchTotal;
        } catch (RuntimeException | Error e) {
            safeRollback(tx, e);
            throw e;
        } finally {
            em.clear();
        }
    }

    private void safeRollback(EntityTransaction tx, Throwable original) {
        BulkTransactionHelper.safeRollback(tx, original);
    }

    private SqlWithParams buildSqlFor(EntityManager em, T entity, DialectStrategy strategy) {
        List<String> effectiveConflictFields =
            conflictFields.isEmpty() ? fieldExtractor.resolveIdColumnNames() : new ArrayList<>(conflictFields);
        Set<String> conflictSet = new LinkedHashSet<>(effectiveConflictFields);
        return buildSqlFor(em, entity, strategy, effectiveConflictFields, conflictSet);
    }

    private SqlWithParams buildSqlFor(EntityManager em, T entity, DialectStrategy strategy,
        List<String> effectiveConflictFields, Set<String> conflictSet) {
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
        return TABLE_NAME_CACHE.computeIfAbsent(entityClass, clazz -> IdentifierValidator.resolveTableName(clazz));
    }

    private List<String> allNonConflictColumns(List<EntityFieldExtractor.EntityFieldValue> allFieldValues,
        Set<String> conflictSet) {
        List<String> columns = new ArrayList<>();
        for (EntityFieldExtractor.EntityFieldValue fv : allFieldValues) {
            if (!conflictSet.contains(fv.columnName()) && fv.updatable()
                && !fieldExtractor.isAutoGeneratedId(fv.fieldName())) {
                columns.add(fv.columnName());
            }
        }
        return columns;
    }

    /**
     * @deprecated 保留用于测试兼容性。直接使用 {@link BulkTransactionHelper#isJtaTransactionActive(EntityManager)}。
     */
    @Deprecated
    static boolean isJtaTransactionActive(EntityManager em) {
        return BulkTransactionHelper.isJtaTransactionActive(em);
    }

    /**
     * 检查 JPA EntityTransaction 是否活动（纯 JPA 环境回退路径）。
     * 当 Spring TransactionSynchronizationManager 报告无事务时，
     * 此方法检查底层的 JPA 本地事务。
     */
    private static boolean isEntityTransactionActive(EntityManager em) {
        try {
            EntityTransaction tx = em.getTransaction();
            return tx != null && tx.isActive();
        } catch (IllegalStateException e) {
            // JTA 环境：getTransaction() 抛出 IllegalStateException
            return false;
        }
    }
}
