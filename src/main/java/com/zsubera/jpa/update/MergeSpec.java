package com.zsubera.jpa.update;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.IdentifierValidator;
import com.zsubera.jpa.util.LambdaUtils;
import com.zsubera.jpa.util.StringHelper;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Id;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
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

    /** 缓存实体类的持久化字段列表，避免每次反射遍历。使用弱引用键防止类加载器泄漏。 */
    private static final java.util.concurrent.ConcurrentMap<Class<?>, List<java.lang.reflect.Field>> FIELD_CACHE =
        new org.springframework.util.ConcurrentReferenceHashMap<>(16,
            org.springframework.util.ConcurrentReferenceHashMap.ReferenceType.WEAK);

    /** 缓存大小超过限制前的警告阈值。 */
    private static final int MAX_FIELD_CACHE_SIZE = 1024;

    /** 采样概率分母——每 1024 次调用检查一次缓存大小，避免 AtomicInteger 缓存行弹跳 */
    private static final int CACHE_CHECK_SAMPLING = 1024;

    private final Class<T> entityClass;
    private T entity;
    private final List<String> conflictFields = new ArrayList<>();
    private final List<String> updateFields = new ArrayList<>();
    private boolean explicitUpdateFields = false;

    /** 每个 EntityManagerFactory 缓存的方言，避免重复检测。 */
    private static final java.util.concurrent.ConcurrentMap<String, String> DIALECT_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** 方言策略实例映射，避免重复创建。 */
    private static final java.util.Map<String, DialectStrategy> DIALECT_STRATEGIES =
        java.util.Map.of("postgresql", new PostgresDialect(), "mysql", new MysqlDialect(), "h2", new H2Dialect());

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
            conflictFields.add(resolveJavaFieldToDbColumn(LambdaUtils.getPropertyName(field)));
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
        // 使用 buildSqlFor 和本地快照而非 buildSql，避免并发场景下共享可变字段访问
        T entitySnapshot = this.entity;
        SqlWithParams sqlWithParams = buildSqlFor(em, entitySnapshot);
        if (log.isTraceEnabled()) {
            log.trace("Executing UPSERT SQL: {}", sqlWithParams.sql());
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
     * <p>
     * <strong>改进：</strong>此方法委托给 {@link #executeH2UpsertFor(EntityManager, Object)} 以消除代码重复。
     *
     * @param em 实体管理器
     * @return 受影响的行数
     */
    private int executeH2Upsert(EntityManager em) {
        // 委托给线程安全的 executeH2UpsertFor 以消除代码重复
        T entitySnapshot = this.entity;
        return executeH2UpsertFor(em, entitySnapshot);
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
        H2Dialect h2 = (H2Dialect)DIALECT_STRATEGIES.get("h2");
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
        H2Dialect h2 = (H2Dialect)DIALECT_STRATEGIES.get("h2");
        List<String> setClauses = new ArrayList<>();
        List<Object> setParams = new ArrayList<>();
        java.util.Map<String, EntityFieldValue> fieldValueMap = new java.util.LinkedHashMap<>();
        for (EntityFieldValue fv : allFieldValues) {
            fieldValueMap.put(fv.fieldName(), fv);
        }
        for (String fieldName : updateFields) {
            EntityFieldValue fv = fieldValueMap.get(fieldName);
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
            for (EntityFieldValue fv : allFieldValues) {
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

    /**
     * 执行原生 SQL 查询并绑定参数。
     *
     * @param em 实体管理器
     * @param sql SQL 语句
     * @param params 参数值列表
     * @return 受影响的行数
     */
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
     * <p>
     * <strong>JTA 环境说明：</strong>在 JTA 环境中（{@code em.getTransaction()} 返回 null），如果没有活动事务， 将抛出明确的异常，而不是尝试执行后失败。请确保使用
     * {@code @Transactional} 注解或手动开始事务。
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
            // JTA 环境：检查是否有活动事务
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

    /**
     * 检查 JTA 环境中是否有活动事务。
     *
     * <p>
     * 使用 JPA 标准 API 检测事务状态，避免硬依赖 Hibernate。 先尝试 TransactionSynchronizationManager（Spring 环境）， 再尝试
     * EntityManager.getTransaction().isActive()（RESOURCE_LOCAL）， 最后回退到 Hibernate Session 检测（JTA 环境）。
     *
     * @param em 实体管理器
     * @return 如果有活动事务返回 true
     */
    private static boolean isJtaTransactionActive(EntityManager em) {
        // 首先尝试标准 JPA API
        try {
            EntityTransaction tx = em.getTransaction();
            if (tx != null) {
                return tx.isActive();
            }
        } catch (Exception ignored) {
            // JTA 环境可能在 getTransaction() 时抛出异常
            // 以 DEBUG 级别记录以便诊断
            log.debug("getTransaction() threw exception in JTA environment: {}", ignored.getMessage());
        }
        // 回退：尝试 Hibernate Session（仅当 Hibernate 在 classpath 上时）
        // 使用纯反射避免对 Hibernate 的编译时依赖
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
            // Hibernate 不在 classpath 上
            log.debug("Hibernate not available for transaction state detection");
        } catch (ReflectiveOperationException e) {
            // 无法确定事务状态
            log.debug("Cannot determine transaction state via Hibernate reflection: {}", e.getMessage());
        } catch (Exception e) {
            // 无法确定事务状态
            log.debug("Cannot determine transaction state via Hibernate: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 批量执行 UPSERT 操作。
     *
     * <p>
     * 对实体列表中的每个实体执行 UPSERT。所有操作在同一个事务中执行，使用 EntityManager 的 flush/clear 进行分批处理以减少内存占用。
     *
     * <p>
     * <strong>线程安全说明：</strong>此方法使用局部变量而非修改实例字段，支持在多线程环境中并发调用同一 {@code MergeSpec} 实例。
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
        // 警告可能造成 OOM 的大型实体列表
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
        // 仅在还有未 flush 的实体时才 flush
        if (count % batchSize != 0) {
            em.flush();
            em.clear();
        }
        return total;
    }

    /**
     * 分批在独立事务中执行 UPSERT 操作。每批操作完成后立即提交事务，避免长事务导致的数据库锁等待超时问题。
     *
     * <p>
     * 适用于大数据量 UPSERT 场景。与 {@link #executeBatch(List, EntityManager, int)} 不同， 此方法每批操作完成后立即提交事务，已提交的批次不会因后续批次失败而回滚。
     *
     * <p>
     * <strong>修复：</strong>改为按 batchSize 分批提交事务，每 batchSize 个实体提交一次，而非每个实体提交一次。
     *
     * <p>
     * <strong>事务管理限制说明：</strong>
     * <ul>
     * <li>此方法绕过 Spring 事务管理，直接使用 JPA {@code EntityTransaction}。在 Spring 管理的事务中调用时， 将回退到
     * {@link #executeBatch(List, EntityManager, int)} 方法。</li>
     * <li>此方法不兼容 Extended Persistence Context（如 {@code @PersistenceContext(type = EXTENDED)}）， 因为每个批次的
     * {@code em.clear()} 会清除扩展持久化上下文中的所有托管实体。</li>
     * <li>在 JTA 环境中无法直接管理事务，将抛出 {@link MyJpaPlusException}。请使用 {@code @Transactional} 注解或
     * {@link com.zsubera.jpa.template.MyJpaTemplate#executeBatch} 替代。</li>
     * </ul>
     *
     * @param entities 要 UPSERT 的实体列表
     * @param em 实体管理器
     * @param batchSize 每批大小，建议值为 50-200
     * @return 受影响的总行数
     * @throws IllegalArgumentException 如果 entities 为 null 或 batchSize 不是正数
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
        // 如果 Spring 事务已激活，发出警告并在其中执行（无法创建独立事务）
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
            // 每 batchSize 个实体开始一个新事务
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
                    // 已有 RESOURCE_LOCAL 事务——无法实现批次隔离
                    throw new MyJpaPlusException("executeBatchInSeparateTransactions requires no active transaction. "
                        + "An active RESOURCE_LOCAL transaction was detected. "
                        + "Use executeBatch() to run within the existing transaction.");
                } else if (tx == null) {
                    // JTA 环境：无法直接管理事务
                    throw new MyJpaPlusException("Cannot manage transactions in JTA environment. "
                        + "Use @Transactional annotation or MyJpaTemplate.executeBatch() instead.");
                }
            }
            try {
                // 在 executeSingle 之前增加计数器，确保正确的批次边界
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
        // 提交最后一批
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

    /**
     * 对单个实体执行 UPSERT 操作。使用参数传递实体实例，避免修改共享实例字段，保证线程安全。
     *
     * @param em 实体管理器
     * @param entityToMerge 要 UPSERT 的实体
     * @return 受影响的行数
     */
    private int executeSingle(EntityManager em, T entityToMerge) {
        String dialect = detectDialect(em);
        if ("h2".equals(dialect)) {
            return executeH2UpsertFor(em, entityToMerge);
        }
        SqlWithParams sqlWithParams = buildSqlFor(em, entityToMerge);
        if (log.isTraceEnabled()) {
            log.trace("Executing UPSERT SQL: {}", sqlWithParams.sql());
        }
        return executeNativeQuery(em, sqlWithParams.sql(), sqlWithParams.params());
    }

    /**
     * 从指定实体提取字段值（线程安全版本，不访问实例字段 this.entity）。
     *
     * <p>
     * <strong>改进：</strong>优先使用 getter 方法获取字段值，回退到字段反射。 在 Java 17+ 模块系统下，getter 方法通常不需要 {@code --add-opens} 参数。
     *
     * @param entity 要提取字段值的实体实例
     * @return 字段名、列名和值的列表
     */
    private List<EntityFieldValue> extractFieldValuesFrom(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
        return extractFieldValues(entity, new java.util.HashSet<>());
    }

    private List<EntityFieldValue> extractFieldValues(Object entity, java.util.Set<Class<?>> visited) {
        List<EntityFieldValue> fieldValues = new ArrayList<>();
        if (!visited.add(entity.getClass())) {
            throw new MyJpaPlusException("Circular @Embedded reference detected: " + entity.getClass().getName()
                + " has already been visited. Check your entity mapping for cycles in @Embedded objects.");
        }
        // 使用采样策略——随机采样检查缓存大小以减少开销，避免 AtomicInteger 缓存行弹跳
        if (java.util.concurrent.ThreadLocalRandom.current().nextInt(CACHE_CHECK_SAMPLING) == 0) {
            int cacheSize = FIELD_CACHE.size();
            if (cacheSize > MAX_FIELD_CACHE_SIZE) {
                log.warn(
                    "MergeSpec field cache size ({}) exceeds limit ({}). "
                        + "This may indicate a class loader leak. Weak reference entries will be cleaned by GC.",
                    cacheSize, MAX_FIELD_CACHE_SIZE);
            }
        }
        // 使用实际实体类作为缓存键，确保 JPA 继承映射（TABLE_PER_CLASS）场景下
        // 子类特有字段不被遗漏。entityClass 是声明时指定的父类，entity.getClass() 是运行时实际类。
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
                        && !f.isAnnotationPresent(jakarta.persistence.EmbeddedId.class)) {
                        fields.add(f);
                    }
                }
            }
            return fields;
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
                        for (Field subField : embeddedValue.getClass().getDeclaredFields()) {
                            if (!java.lang.reflect.Modifier.isStatic(subField.getModifiers()) && !subField.isSynthetic()
                                && !subField.isAnnotationPresent(jakarta.persistence.Embedded.class)) {
                                Object subValue = getFieldValue(embeddedValue, subField);
                                String columnName =
                                    overrideMap.getOrDefault(subField.getName(), resolveColumnName(subField));
                                fieldValues.add(
                                    new EntityFieldValue(f.getName() + "." + subField.getName(), columnName, subValue));
                            }
                        }
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
     * 获取实体字段值，优先使用 getter 方法，回退到字段反射。
     *
     * @param entity 实体实例
     * @param field 字段
     * @return 字段值
     * @throws Exception 如果无法访问字段
     */
    private Object getFieldValue(Object entity, Field field) throws Exception {
        Class<?> cls = entity.getClass();
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
     * 为指定实体构建 UPSERT SQL（线程安全版本）。
     *
     * @param em 实体管理器
     * @param entity 要 UPSERT 的实体
     * @return SQL 和参数
     */
    private SqlWithParams buildSqlFor(EntityManager em, T entity) {
        List<String> effectiveConflictFields =
            conflictFields.isEmpty() ? resolveIdColumnNames() : new ArrayList<>(conflictFields);
        Set<String> conflictSet = new LinkedHashSet<>(effectiveConflictFields);
        List<EntityFieldValue> allFieldValues = extractFieldValuesFrom(entity);
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
        String tableName = resolveTableName();
        String dialect = detectDialect(em);
        DialectStrategy strategy = DIALECT_STRATEGIES.get(dialect);
        if (strategy == null) {
            throw new MyJpaPlusException(
                "Unsupported database dialect: " + dialect + ". Supported dialects: postgresql, mysql, h2");
        }
        return strategy.buildUpsertSql(tableName, insertColumns, insertFieldValues, effectiveConflictFields,
            effectiveUpdateFields);
    }

    /**
     * 为指定实体执行 H2 UPSERT（线程安全版本）。
     *
     * @param em 实体管理器
     * @param entity 要 UPSERT 的实体
     * @return 受影响的行数
     */
    private int executeH2UpsertFor(EntityManager em, T entity) {
        List<String> effectiveConflictFields =
            conflictFields.isEmpty() ? resolveIdColumnNames() : new ArrayList<>(conflictFields);
        List<EntityFieldValue> allFieldValues = extractFieldValuesFrom(entity);
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
                            // Thread.sleep() 在此处可以接受，因为重试退避时间短（10-40ms）。
                            // 在 Java 21+ 虚拟线程环境中，考虑使用
                            // CompletableFuture.delayedExecutor() 实现非阻塞延迟。
                            Thread.sleep(backoffMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            // 将 InterruptedException 作为抑制异常添加以保留诊断信息
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
            String name = tableName.toString();
            // 校验注解中表名的每个部分以防止注入
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

    /**
     * 验证并转义 SQL 标识符（表名、列名），防止 SQL 注入。
     *
     * <p>
     * 标识符必须仅包含字母、数字、下划线和点号。不满足条件时抛出异常。 对于 PostgreSQL 使用双引号转义，对于 MySQL 使用反引号转义。 H2 默认模式将标识符存储为大写，引用会使标识符变为大小写敏感，因此 H2
     * 标识符不加引号以保持向后兼容。
     *
     * @param identifier 标识符
     * @param dialect 数据库方言
     * @return 转义后的标识符
     * @throws MyJpaPlusException 如果标识符包含非法字符
     */
    private static String escapeIdentifier(String identifier, String dialect) {
        if (identifier == null || identifier.isEmpty()) {
            throw new MyJpaPlusException("Identifier must not be null or empty");
        }
        // 使用 IdentifierValidator 进行验证
        IdentifierValidator.validate(identifier);
        // 委托给方言策略进行标识符转义
        DialectStrategy strategy = DIALECT_STRATEGIES.get(dialect);
        if (strategy == null) {
            // 回退到双引号转义
            return "\"" + identifier.replace("\"", "\"\"") + "\"";
        }
        return strategy.escapeIdentifier(identifier);
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

    /** 自动生成 ID 字段检测结果的缓存。 */
    private static final java.util.concurrent.ConcurrentMap<String, Boolean> AUTO_GENERATED_ID_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 检查字段是否为自动生成的 @Id 字段。结果被缓存以避免重复反射扫描。
     *
     * @param fieldName Java 字段名
     * @return 如果是自动生成的 ID 字段则返回 true
     */
    private boolean isAutoGeneratedId(String fieldName) {
        String cacheKey = entityClass.getName() + "#" + fieldName;
        return AUTO_GENERATED_ID_CACHE.computeIfAbsent(cacheKey, k -> {
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
     * 解析字段对应的数据库列名。优先使用 {@code @Column(name)} 注解，否则使用字段名。
     *
     * @param field 实体字段
     * @return 数据库列名
     */
    private String resolveColumnName(Field field) {
        Column columnAnnotation = field.getAnnotation(Column.class);
        if (columnAnnotation != null && !columnAnnotation.name().isEmpty()) {
            String name = columnAnnotation.name();
            // 校验注解中的列名以防止注入
            IdentifierValidator.validateColumnName(name);
            return name;
        }
        return field.getName();
    }

    /**
     * 将 Java 字段名解析为数据库列名。遍历实体类及其父类的字段，匹配字段名后通过 {@link #resolveColumnName(Field)} 解析。 如果找不到对应的 Field，回退到直接使用 Java
     * 字段名并进行安全校验。
     *
     * @param javaFieldName Java 字段名
     * @return 数据库列名
     */
    private String resolveJavaFieldToDbColumn(String javaFieldName) {
        for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals(javaFieldName)) {
                    return resolveColumnName(f);
                }
            }
        }
        // 回退：未找到 Field，直接使用字段名（安全校验）
        IdentifierValidator.validateColumnName(javaFieldName);
        return javaFieldName;
    }

    /**
     * 检测数据库方言。优先通过 Hibernate Session 获取，回退到 EntityManagerFactory 属性。
     *
     * @param em 实体管理器
     * @return 数据库方言标识（postgresql、mysql 或 h2）
     */
    private String detectDialect(EntityManager em) {
        // 使用稳定的缓存键（如果可用则使用 JDBC URL，否则基于 identity）
        jakarta.persistence.EntityManagerFactory emf = em.getEntityManagerFactory();
        String factoryKey = resolveFactoryKey(emf);
        String cached = DIALECT_CACHE.get(factoryKey);
        if (cached != null) {
            return cached;
        }
        // 优先级 1：从 EntityManagerFactory 属性中的 JDBC URL 检测（无 Hibernate 依赖）
        try {
            Object jdbcUrl = em.getEntityManagerFactory().getProperties().get("jakarta.persistence.jdbc.url");
            if (jdbcUrl == null) {
                jdbcUrl = em.getEntityManagerFactory().getProperties().get("hibernate.connection.url");
            }
            if (jdbcUrl != null) {
                String url = jdbcUrl.toString().toLowerCase();
                if (url.contains("postgresql")) {
                    DIALECT_CACHE.putIfAbsent(factoryKey, "postgresql");
                    return "postgresql";
                }
                if (url.contains("mysql")) {
                    DIALECT_CACHE.putIfAbsent(factoryKey, "mysql");
                    return "mysql";
                }
                if (url.contains("h2")) {
                    DIALECT_CACHE.putIfAbsent(factoryKey, "h2");
                    return "h2";
                }
            }
        } catch (Exception ex) {
            log.debug("Failed to detect dialect from properties: {}", ex.getMessage());
        }

        // 优先级 2：通过 EntityManager.unwrap() 的 JDBC Connection.getMetaData() 检测
        try {
            java.sql.Connection conn = em.unwrap(java.sql.Connection.class);
            if (conn != null) {
                String productName = conn.getMetaData().getDatabaseProductName().toLowerCase();
                String dialect = mapDialect(productName);
                DIALECT_CACHE.putIfAbsent(factoryKey, dialect);
                return dialect;
            }
        } catch (Exception e) {
            log.debug("Failed to detect dialect via JDBC Connection.unwrap(): {}", e.getMessage());
        }

        // 优先级 3：Hibernate 回退（仅当 Hibernate 在 classpath 上时）
        // 使用纯反射 + 动态代理避免对 Hibernate 的编译时依赖
        try {
            Class<?> sessionClass = Class.forName("org.hibernate.Session");
            Object session = em.unwrap(sessionClass);
            Class<?> workClass = Class.forName("org.hibernate.jdbc.Work");
            String[] dialectHolder = new String[1];
            Object workProxy = java.lang.reflect.Proxy.newProxyInstance(workClass.getClassLoader(),
                new Class<?>[] {workClass}, (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(this, args);
                    }
                    if ("execute".equals(method.getName()) && args.length == 1
                        && args[0] instanceof java.sql.Connection conn) {
                        dialectHolder[0] = conn.getMetaData().getDatabaseProductName().toLowerCase();
                        return null;
                    }
                    return method.invoke(proxy, args);
                });
            java.lang.reflect.Method doWork = sessionClass.getMethod("doWork", workClass);
            doWork.invoke(session, workProxy);
            String dialect = mapDialect(dialectHolder[0]);
            DIALECT_CACHE.putIfAbsent(factoryKey, dialect);
            return dialect;
        } catch (ClassNotFoundException e) {
            log.debug("Hibernate not available on classpath");
        } catch (Exception e) {
            log.debug("Hibernate dialect detection failed: {}", e.getMessage());
        }

        // 优先级 4：手动配置
        log.warn("Failed to detect database dialect automatically. "
            + "Set system property 'myjpa-plus.dialect' to 'postgresql', 'mysql', or 'h2' to specify manually.");
        String manualDialect = System.getProperty("myjpa-plus.dialect");
        if (manualDialect != null && !manualDialect.isEmpty()) {
            String mapped = mapDialect(manualDialect.toLowerCase());
            log.info("Using manually configured dialect: {}", mapped);
            DIALECT_CACHE.putIfAbsent(factoryKey, mapped);
            return mapped;
        }
        throw new MyJpaPlusException("Failed to detect database dialect and no manual dialect configured. "
            + "Set system property 'myjpa-plus.dialect' to 'postgresql', 'mysql', or 'h2'.");
    }

    /**
     * 将数据库产品名称映射为方言标识。
     *
     * @param productName 数据库产品名称（小写）
     * @return 方言标识
     */
    private static String mapDialect(String productName) {
        if (productName == null) {
            return "unknown";
        }
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
     * 为 EntityManagerFactory 生成稳定的缓存键。
     *
     * <p>
     * 优先使用 JDBC URL 作为缓存键（跨 JVM 重启稳定），回退到基于 identityHashCode 的键。
     *
     * @param emf EntityManagerFactory 实例
     * @return 稳定的缓存键字符串
     */
    private static String resolveFactoryKey(jakarta.persistence.EntityManagerFactory emf) {
        try {
            Object jdbcUrl = emf.getProperties().get("jakarta.persistence.jdbc.url");
            if (jdbcUrl == null) {
                jdbcUrl = emf.getProperties().get("hibernate.connection.url");
            }
            if (jdbcUrl != null) {
                return jdbcUrl.toString();
            }
        } catch (Exception ignored) {
            // 回退到基于 identity 的键
        }
        return emf.getClass().getName() + "@" + System.identityHashCode(emf);
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
}
