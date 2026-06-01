package com.zsubera.jpa.update;

import com.zsubera.jpa.exception.MyJpaPlusException;
import com.zsubera.jpa.spec.SFunction;
import com.zsubera.jpa.util.LambdaUtils;
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
import java.util.regex.Pattern;
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

    /** 安全标识符正则：仅允许字母、数字和下划线（表名/列名不允许点号）。 */
    private static final Pattern SAFE_IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /** 安全标识符段正则：用于校验 schema.table 格式中每一段。 */
    private static final Pattern SAFE_IDENTIFIER_PART_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /** B-08: Unicode 标识符正则：允许 Unicode 字母、数字和下划线，支持国际化标识符。 */
    private static final Pattern UNICODE_IDENTIFIER_PART_PATTERN = Pattern.compile("^[\\p{L}_][\\p{L}\\p{N}_]*$");

    /** P1-5: 常见 Unicode 同形字符检测：这些字符与 ASCII 字符视觉相似，可能用于绕过安全检查。 */
    private static final Pattern HOMOGLYPH_PATTERN = Pattern.compile("[\\u0400-\\u04FF\\u0370-\\u03FF\\u0530-\\u058F]");

    /** B-08: 是否启用 Unicode 标识符支持。可通过系统属性 myjpa-plus.merge.unicode-identifiers=true 启用。 */
    private static volatile boolean unicodeIdentifiers = false;

    /** P1-5: Maximum identifier length to prevent abuse. */
    private static final int MAX_IDENTIFIER_LENGTH = 128;

    static {
        String prop = System.getProperty("myjpa-plus.merge.unicode-identifiers");
        if ("true".equalsIgnoreCase(prop)) {
            unicodeIdentifiers = true;
        }
    }

    /**
     * B-08: 设置是否启用 Unicode 标识符支持。
     *
     * @param enabled 是否启用
     */
    public static void setUnicodeIdentifiers(boolean enabled) {
        unicodeIdentifiers = enabled;
    }

    /** 缓存实体类的持久化字段列表，避免每次反射遍历。使用弱引用键防止类加载器泄漏。 */
    private static final java.util.concurrent.ConcurrentMap<Class<?>, List<java.lang.reflect.Field>> FIELD_CACHE =
        new org.springframework.util.ConcurrentReferenceHashMap<>(16,
            org.springframework.util.ConcurrentReferenceHashMap.ReferenceType.WEAK);

    /** P1-14: Maximum cache size before logging warning. */
    private static final int MAX_FIELD_CACHE_SIZE = 1024;

    /** P2: Counter for sampling cache size checks to reduce overhead. */
    private static final java.util.concurrent.atomic.AtomicInteger FIELD_CACHE_CALL_COUNTER =
        new java.util.concurrent.atomic.AtomicInteger(0);

    private final Class<T> entityClass;
    private T entity;
    private final List<String> conflictFields = new ArrayList<>();
    private final List<String> updateFields = new ArrayList<>();
    private boolean explicitUpdateFields = false;

    /** P2-25: Cached dialect per EntityManagerFactory to avoid repeated detection. */
    private static final java.util.concurrent.ConcurrentMap<String, String> DIALECT_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

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
        // P0-7: Use buildSqlFor with local snapshot instead of buildSql to avoid
        // shared mutable field access in concurrent scenarios
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
     * <strong>P2-25 改进：</strong>此方法委托给 {@link #executeH2UpsertFor(EntityManager, Object)} 以消除代码重复。
     *
     * @param em 实体管理器
     * @return 受影响的行数
     */
    private int executeH2Upsert(EntityManager em) {
        // P2-5: Delegate to thread-safe executeH2UpsertFor to eliminate code duplication
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
        String escapedTable = escapeIdentifier(resolveTableName(), "h2");
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(escapedTable).append(" (");
        sql.append(String.join(", ", columns.stream().map(c -> escapeIdentifier(c, "h2")).toList()));
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
        List<String> setClauses = new ArrayList<>();
        List<Object> setParams = new ArrayList<>();
        for (String fieldName : updateFields) {
            for (EntityFieldValue fv : allFieldValues) {
                if (fv.fieldName().equals(fieldName)) {
                    setClauses.add(escapeIdentifier(fv.columnName(), "h2") + " = ?");
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
                    whereClauses.add(escapeIdentifier(col, "h2") + " = ?");
                    whereParams.add(fv.value());
                }
            }
        }
        StringBuilder sql =
            new StringBuilder("UPDATE ").append(escapeIdentifier(resolveTableName(), "h2")).append(" SET ");
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
     * P1: 使用 JPA 标准 API 检测事务状态，避免硬依赖 Hibernate。 先尝试 TransactionSynchronizationManager（Spring 环境）， 再尝试
     * EntityManager.getTransaction().isActive()（RESOURCE_LOCAL）， 最后回退到 Hibernate Session 检测（JTA 环境）。
     *
     * @param em 实体管理器
     * @return 如果有活动事务返回 true
     */
    private static boolean isJtaTransactionActive(EntityManager em) {
        // First try standard JPA API
        try {
            EntityTransaction tx = em.getTransaction();
            if (tx != null) {
                return tx.isActive();
            }
        } catch (Exception ignored) {
            // JTA environment may throw on getTransaction()
            // P2: Log at debug level for diagnostics
            log.debug("getTransaction() threw exception in JTA environment: {}", ignored.getMessage());
        }
        // Fallback: try Hibernate Session (only if Hibernate is on classpath)
        // P0-5: Use pure reflection to avoid compile-time dependency on Hibernate
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
            // Hibernate not on classpath
            log.debug("Hibernate not available for transaction state detection");
        } catch (ReflectiveOperationException e) {
            // Cannot determine transaction state
            log.debug("Cannot determine transaction state via Hibernate reflection: {}", e.getMessage());
        } catch (Exception e) {
            // Cannot determine transaction state
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
        // P0-4: Warn about large entity lists that may cause OOM
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
        // P2: Only flush if there are remaining entities not yet flushed
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
     * <strong>P0-1 修复：</strong>改为按 batchSize 分批提交事务，每 batchSize 个实体提交一次，而非每个实体提交一次。
     *
     * <p>
     * <strong>P1-8 事务管理限制说明：</strong>
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
        // P1: If Spring transaction is active, warn and execute within it (no separate tx possible)
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
                } else if (tx == null) {
                    // JTA environment: cannot manage transactions directly
                    throw new MyJpaPlusException("Cannot manage transactions in JTA environment. "
                        + "Use @Transactional annotation or MyJpaTemplate.executeBatch() instead.");
                }
            }
            try {
                // P1-3: Increment counter before executeSingle to ensure correct batch boundary
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
            em.flush();
            tx.commit();
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
     * <strong>P1-4 改进：</strong>优先使用 getter 方法获取字段值，回退到字段反射。 在 Java 17+ 模块系统下，getter 方法通常不需要 {@code --add-opens} 参数。
     *
     * @param entity 要提取字段值的实体实例
     * @return 字段名、列名和值的列表
     */
    private List<EntityFieldValue> extractFieldValuesFrom(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
        List<EntityFieldValue> fieldValues = new ArrayList<>();
        // P2: Use sampling strategy - only check cache size every 64 calls to reduce overhead
        if ((FIELD_CACHE_CALL_COUNTER.incrementAndGet() & 63) == 0) {
            int cacheSize = FIELD_CACHE.size();
            if (cacheSize > MAX_FIELD_CACHE_SIZE) {
                log.warn(
                    "MergeSpec field cache size ({}) exceeds limit ({}). "
                        + "This may indicate a class loader leak. Weak reference entries will be cleaned by GC.",
                    cacheSize, MAX_FIELD_CACHE_SIZE);
            }
        }
        List<Field> allFields = FIELD_CACHE.computeIfAbsent(entityClass, cls -> {
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
                        // P1-4: Don't call setAccessible in cache population; defer to runtime
                        fields.add(f);
                    }
                }
            }
            return fields;
        });
        for (Field f : allFields) {
            try {
                // P2: Handle @Embedded fields by recursively extracting their sub-fields
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
                            if (!java.lang.reflect.Modifier.isStatic(subField.getModifiers())
                                && !subField.isSynthetic()) {
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
     * P1-4: 获取实体字段值，优先使用 getter 方法，回退到字段反射。
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
                            // P1-9: Thread.sleep() is acceptable here as retry backoff is short (10-40ms).
                            // In Java 21+ virtual thread environments, consider using
                            // CompletableFuture.delayedExecutor() for non-blocking delay.
                            Thread.sleep(backoffMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            // P1-9: Add InterruptedException as suppressed exception to preserve
                            // diagnostic information
                            e.addSuppressed(ie);
                            throw e;
                        }
                        continue;
                    }
                    throw e;
                }
            }
            // P0-2: When explicitUpdateFields=true, cannot fall back to MERGE INTO
            // because it would overwrite all columns, violating partial update contract
            if (explicitUpdateFields) {
                throw new IllegalStateException("H2 UPSERT retry limit (" + maxRetries + ") exhausted. "
                    + "Cannot fall back to MERGE INTO because explicit update fields are specified. "
                    + "MERGE INTO would overwrite all columns, violating partial update contract.");
            }
            // B-10: After retries exhausted, try MERGE again using the thread-safe buildSqlFor method
            log.warn("H2 UPSERT retry limit ({}) exhausted. Falling back to MERGE INTO syntax.", maxRetries);
            SqlWithParams retrySql = buildSqlFor(em, entity);
            return executeNativeQuery(em, retrySql.sql(), retrySql.params());
        }
        SqlWithParams sqlWithParams = buildSqlFor(em, entity);
        if (log.isTraceEnabled()) {
            log.trace("Executing H2 MERGE SQL: {}", sqlWithParams.sql());
        }
        return executeNativeQuery(em, sqlWithParams.sql(), sqlWithParams.params());
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
     * <p>
     * <strong>线程安全警告：</strong>此方法访问实例字段 {@code this.entity}，不是线程安全的。 在多线程环境中请使用
     * {@link #buildSqlFor(EntityManager, Object)} 替代。
     *
     * @param em 实体管理器
     * @return SQL 和参数
     * @deprecated 使用 {@link #buildSqlFor(EntityManager, Object)} 替代，该方法是线程安全的。 此方法将在 2.0 版本中移除。
     */
    @Deprecated(since = "1.2.0", forRemoval = true)
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
        String escapedTable = escapeIdentifier(tableName, "postgresql");
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(escapedTable).append(" (");
        sql.append(String.join(", ", insertColumns.stream().map(c -> escapeIdentifier(c, "postgresql")).toList()));
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
        sql.append(String.join(", ", conflictColumns.stream().map(c -> escapeIdentifier(c, "postgresql")).toList()));
        sql.append(") DO UPDATE SET ");
        List<String> setClauses = new ArrayList<>();
        for (String col : updateColumns) {
            String escaped = escapeIdentifier(col, "postgresql");
            setClauses.add(escaped + " = EXCLUDED." + escaped);
        }
        sql.append(String.join(", ", setClauses));
        return new SqlWithParams(sql.toString(), params);
    }

    /**
     * 构建 MySQL 的 UPSERT SQL：{@code INSERT ... ON DUPLICATE KEY UPDATE ...}。
     */
    private SqlWithParams buildMysqlSql(String tableName, List<String> insertColumns,
        List<EntityFieldValue> insertFieldValues, List<String> updateColumns) {
        String escapedTable = escapeIdentifier(tableName, "mysql");
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(escapedTable).append(" (");
        sql.append(String.join(", ", insertColumns.stream().map(c -> escapeIdentifier(c, "mysql")).toList()));
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
            String escaped = escapeIdentifier(col, "mysql");
            setClauses.add(escaped + " = VALUES(" + escaped + ")");
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
            String escapedTable = escapeIdentifier(tableName, "h2");
            StringBuilder sql = new StringBuilder("INSERT INTO ").append(escapedTable).append(" (");
            sql.append(String.join(", ", insertColumns.stream().map(c -> escapeIdentifier(c, "h2")).toList()));
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
        String escapedTable = escapeIdentifier(tableName, "h2");
        StringBuilder sql = new StringBuilder("MERGE INTO ").append(escapedTable).append(" (");
        sql.append(String.join(", ", insertColumns.stream().map(c -> escapeIdentifier(c, "h2")).toList()));
        sql.append(") KEY (");
        sql.append(String.join(", ", conflictColumns.stream().map(c -> escapeIdentifier(c, "h2")).toList()));
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
            String name = tableName.toString();
            // P0-2: Validate each segment of the table name from annotation to prevent injection
            for (String segment : name.split("\\.")) {
                if (!SAFE_IDENTIFIER_PART_PATTERN.matcher(segment).matches()) {
                    throw new MyJpaPlusException("Invalid table name in @Table annotation: '" + name
                        + "'. Each part must contain only alphanumeric characters and underscores.");
                }
            }
            return name;
        }
        jakarta.persistence.Entity entityAnnotation = entityClass.getAnnotation(jakarta.persistence.Entity.class);
        if (entityAnnotation != null && !entityAnnotation.name().isEmpty()) {
            String name = entityAnnotation.name();
            if (!SAFE_IDENTIFIER_PART_PATTERN.matcher(name).matches()) {
                throw new MyJpaPlusException("Invalid entity name in @Entity annotation: '" + name
                    + "'. Must contain only alphanumeric characters and underscores.");
            }
            return name;
        }
        return camelToSnake(entityClass.getSimpleName());
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
        // P1-5: Check identifier length limit
        if (identifier.length() > MAX_IDENTIFIER_LENGTH) {
            throw new MyJpaPlusException("Identifier length (" + identifier.length() + ") exceeds maximum ("
                + MAX_IDENTIFIER_LENGTH + "): '" + identifier.substring(0, 64) + "...'");
        }
        // B-6: Validate each segment separately to prevent schema injection via dots
        String[] parts = identifier.split("\\.");
        for (String part : parts) {
            // B-08: Use Unicode pattern when enabled, otherwise use ASCII-only pattern
            Pattern validationPattern =
                unicodeIdentifiers ? UNICODE_IDENTIFIER_PART_PATTERN : SAFE_IDENTIFIER_PART_PATTERN;
            if (!validationPattern.matcher(part).matches()) {
                throw new MyJpaPlusException("Invalid SQL identifier: '" + identifier
                    + "'. Each part must contain only alphanumeric characters and underscores." + (unicodeIdentifiers
                        ? "" : " Use myjpa-plus.merge.unicode-identifiers=true for Unicode support."));
            }
            // P1-5: Detect Unicode homoglyphs that may be used for security bypass
            // Cyrillic, Greek, and Armenian characters look similar to Latin characters
            if (unicodeIdentifiers && HOMOGLYPH_PATTERN.matcher(part).find()) {
                String homoglyphMsg = "SECURITY: Identifier '" + part + "' contains Unicode homoglyph characters "
                    + "(Cyrillic/Greek/Armenian). This may indicate a homoglyph attack attempt.";
                if (unicodeIdentifiers
                    && System.getProperty("myjpa-plus.merge.strict-mode", "false").equalsIgnoreCase("true")) {
                    throw new MyJpaPlusException(homoglyphMsg);
                }
                log.warn(homoglyphMsg);
            }
        }
        // Always quote identifiers to handle reserved words and case sensitivity.
        // P0-4: H2 identifiers are now quoted with double quotes to prevent reserved word conflicts.
        // H2 default mode stores identifiers in uppercase, so we convert to uppercase before quoting.
        return switch (dialect) {
            case "postgresql" -> "\"" + identifier.replace("\"", "\"\"") + "\"";
            case "mysql" -> "`" + identifier.replace("`", "``") + "`";
            case "h2" -> "\"" + identifier.toUpperCase().replace("\"", "\"\"") + "\"";
            default -> "\"" + identifier.replace("\"", "\"\"") + "\"";
        };
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
                // B-30: Also check @EmbeddedId
                if (f.isAnnotationPresent(jakarta.persistence.EmbeddedId.class)) {
                    return true;
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
     * <p>
     * <strong>线程安全警告：</strong>此方法访问实例字段 {@code this.entity}，不是线程安全的。 在多线程环境中请使用 {@link #extractFieldValuesFrom(Object)}
     * 替代。
     *
     * @return 字段名、列名和值的列表
     * @deprecated 使用 {@link #extractFieldValuesFrom(Object)} 替代，该方法是线程安全的。 此方法将在 2.0 版本中移除。
     */
    @Deprecated(since = "1.2.0", forRemoval = true)
    private List<EntityFieldValue> extractFieldValues() {
        if (entity == null) {
            throw new IllegalStateException("Entity must be specified via withEntity() before extracting field values");
        }
        return extractFieldValuesFrom(entity);
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
            // P0-2: Validate column name from annotation to prevent injection
            if (!SAFE_IDENTIFIER_PATTERN.matcher(name).matches()) {
                throw new MyJpaPlusException("Invalid column name in @Column annotation: '" + name
                    + "'. Must contain only alphanumeric characters and underscores.");
            }
            return name;
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
        // P2-25: Use cached dialect if available
        String factoryKey = em.getEntityManagerFactory().toString();
        String cached = DIALECT_CACHE.get(factoryKey);
        if (cached != null) {
            return cached;
        }
        // Priority 1: Detect from JDBC URL in EntityManagerFactory properties (no Hibernate dependency)
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

        // Priority 2: Detect via JDBC Connection.getMetaData() through EntityManager.unwrap()
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

        // Priority 3: Hibernate fallback (only if Hibernate is on classpath)
        try {
            Class.forName("org.hibernate.Session");
            org.hibernate.Session session = em.unwrap(org.hibernate.Session.class);
            String[] dialectHolder = new String[1];
            session.doWork(connection -> {
                dialectHolder[0] = connection.getMetaData().getDatabaseProductName().toLowerCase();
            });
            String dialect = mapDialect(dialectHolder[0]);
            DIALECT_CACHE.putIfAbsent(factoryKey, dialect);
            return dialect;
        } catch (ClassNotFoundException e) {
            log.debug("Hibernate not available on classpath");
        } catch (Exception e) {
            log.debug("Hibernate dialect detection failed: {}", e.getMessage());
        }

        // Priority 4: Manual configuration
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
