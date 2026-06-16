package com.zsubera.jpa.template;

import com.zsubera.jpa.spec.QuerySpec;
import com.zsubera.jpa.update.DeleteSpec;
import com.zsubera.jpa.update.MergeSpec;
import com.zsubera.jpa.update.UpdateSpec;
import com.zsubera.jpa.util.EntityGraphHelper;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;

/**
 * 从 {@link MyJpaTemplate} 中提取的操作接口，定义所有公共查询和批量操作方法。
 *
 * <p>
 * 该接口作为 MyJpa-Plus 模板层的契约，将调用者与具体实现解耦。包含以下功能：
 *
 * <ul>
 * <li><strong>查询方法</strong>：{@code findById}、{@code findOne}、{@code count}、
 * {@code findAll}、{@code findAllStream}、{@code find}、{@code findPage}、{@code findSlice}、
 * {@code findAllById}、{@code findNotDeletedAllById}、{@code findKeysetPage}、{@code findAllCached}
 * </li>
 * <li><strong>工厂方法</strong>：{@link #update(Class)} 和 {@link #delete(Class)}，用于创建
 * {@link UpdateSpec} 和 {@link DeleteSpec} 实例</li>
 * <li><strong>批量保存</strong>：{@code saveAllBatched} 系列方法，用于批量持久化/合并</li>
 * <li><strong>批量执行</strong>：{@code execute}、{@code executeBatch}、
 * {@code executeWithMaxRows}、{@code executeBatchInSeparateTransactions}，用于批量 DML 操作</li>
 * </ul>
 *
 * <p>
 * <strong>注意：</strong>配置 setter（如 {@code setMaxResults}、
 * {@code setDeepPaginationOffsetThreshold}）被有意排除在该接口之外。它们属于实现类的生命周期配置，
 * 而非操作契约的一部分。
 *
 * @see MyJpaTemplate
 */
public interface MyJpaTemplateOperations {

    /**
     * 在独立事务中执行的批量操作结果。
     *
     * @param totalRows        所有批次受影响的总行数
     * @param batchCount       已执行的批次数
     * @param success          如果所有批次都成功完成则为 {@code true}
     * @param failedBatchIndex 失败批次的从零开始的索引，如果全部成功则为 {@code -1}
     * @param failureCause     导致失败的异常，如果全部成功则为 {@code null}
     */
    record BatchResult(int totalRows, int batchCount, boolean success, int failedBatchIndex, Throwable failureCause) {
    }

    /**
     * 在独立事务中执行的批量操作失败处理策略。
     */
    enum BatchFailureStrategy {
        /**
         * 失败后继续执行剩余批次。已提交的批次不会被回滚。
         */
        CONTINUE,
        /**
         * 批次失败时立即中止。已提交的批次不会被回滚。
         */
        ABORT,
    }

    /**
     * 键集（游标）分页查询的结果。
     *
     * <p>
     * 键集分页通过使用排序列值的 WHERE 条件替代 {@code OFFSET}，避免了大偏移量带来的性能退化。
     * 无论页面深度如何，性能始终保持 O(log n)。
     *
     * @param <T>             实体类型
     * @param content         当前页面的实体列表
     * @param hasNext         如果当前页面之后还有更多页面则为 {@code true}
     * @param lastSortValues  {@code content} 中最后一个实体的排序列值，用作下一页的游标；
     *                        当 {@code hasNext} 为 {@code false} 时为 {@code null}
     */
    record KeysetPage<T>(List<T> content, boolean hasNext, @Nullable Object[] lastSortValues) {
    }

    // ---- 查询方法（Lambda 便捷重载） ----

    /**
     * 使用 Lambda 表达式查找单个实体。
     *
     * @param entityClass 实体类
     * @param config      查询条件配置
     * @param <T>         实体类型
     * @return 包含实体的 {@link Optional}，如果未找到则为空
     */
    default <T> Optional<T> findOne(Class<T> entityClass, Consumer<QuerySpec<T>> config) {
        QuerySpec<T> spec = new QuerySpec<>();
        config.accept(spec);
        return findOne(entityClass, spec);
    }

    /**
     * 使用 Lambda 表达式统计实体数量。
     *
     * @param entityClass 实体类
     * @param config      查询条件配置
     * @param <T>         实体类型
     * @return 匹配的实体数量
     */
    default <T> long count(Class<T> entityClass, Consumer<QuerySpec<T>> config) {
        QuerySpec<T> spec = new QuerySpec<>();
        config.accept(spec);
        return count(entityClass, spec);
    }

    /**
     * 使用 Lambda 表达式查找所有实体。
     *
     * @param entityClass 实体类
     * @param config      查询条件配置
     * @param <T>         实体类型
     * @return 匹配的实体列表
     */
    default <T> List<T> findAll(Class<T> entityClass, Consumer<QuerySpec<T>> config) {
        QuerySpec<T> spec = new QuerySpec<>();
        config.accept(spec);
        return findAll(entityClass, spec);
    }

    /**
     * 使用 Lambda 表达式查找所有实体，带自定义最大行数限制。
     *
     * @param entityClass 实体类
     * @param config      查询条件配置
     * @param maxResults  最大返回结果数，{@code -1} 表示无限制
     * @param <T>         实体类型
     * @return 匹配的实体列表
     */
    default <T> List<T> findAll(Class<T> entityClass, Consumer<QuerySpec<T>> config, int maxResults) {
        QuerySpec<T> spec = new QuerySpec<>();
        config.accept(spec);
        return findAll(entityClass, spec, maxResults);
    }

    /**
     * 使用 Lambda 表达式查找所有实体，带显式排序。
     *
     * @param entityClass 实体类
     * @param config      查询条件配置
     * @param sort        排序规则
     * @param <T>         实体类型
     * @return 匹配的实体列表
     */
    default <T> List<T> findAll(Class<T> entityClass, Consumer<QuerySpec<T>> config, Sort sort) {
        QuerySpec<T> spec = new QuerySpec<>();
        config.accept(spec);
        return findAll(entityClass, spec, sort);
    }

    /**
     * 使用 Lambda 表达式分页查找所有实体。
     *
     * @param entityClass 实体类
     * @param config      查询条件配置
     * @param pageable    分页参数
     * @param <T>         实体类型
     * @return 包含匹配实体和总数的 {@link Page}
     */
    default <T> Page<T> findAll(Class<T> entityClass, Consumer<QuerySpec<T>> config, Pageable pageable) {
        QuerySpec<T> spec = new QuerySpec<>();
        config.accept(spec);
        return findAll(entityClass, spec, pageable);
    }

    /**
     * 使用 Lambda 表达式流式处理所有实体。
     *
     * @param entityClass 实体类
     * @param config      查询条件配置
     * @param consumer    处理流的消费者
     * @param <T>         实体类型
     */
    default <T> void findAllStream(Class<T> entityClass, Consumer<QuerySpec<T>> config, Consumer<Stream<T>> consumer) {
        QuerySpec<T> spec = new QuerySpec<>();
        config.accept(spec);
        findAllStream(entityClass, spec, consumer);
    }

    /**
     * 使用 Lambda 表达式查找所有实体，带查询级缓存。
     *
     * @param entityClass 实体类
     * @param config      查询条件配置
     * @param ttlSeconds  缓存生存时间（秒）
     * @param <T>         实体类型
     * @return 匹配的实体列表
     */
    default <T> List<T> findAllCached(Class<T> entityClass, Consumer<QuerySpec<T>> config, long ttlSeconds) {
        QuerySpec<T> spec = new QuerySpec<>();
        config.accept(spec);
        return findAllCached(entityClass, spec, ttlSeconds);
    }

    /**
     * 根据主键查找实体。
     *
     * <p>
     * 对于非软删除实体，直接委托给 {@link jakarta.persistence.EntityManager#find(Class, Object)}
     * 以获得最佳性能。对于软删除实体，使用基于 Specification 的查询来自动排除已删除的记录。
     *
     * @param entityClass 实体类
     * @param id          主键值
     * @param <T>         实体类型
     * @param <ID>        ID 类型
     * @return 包含实体的 {@link Optional}，如果未找到则为空
     */
    <T, ID> Optional<T> findById(Class<T> entityClass, ID id);

    /**
     * 根据给定的 {@link QuerySpec} 查找单个实体。
     *
     * <p>
     * 查询结果限制为最多一条。如果匹配到多个实体，返回第一个。
     *
     * @param entityClass 实体类
     * @param spec        查询规约
     * @param <T>         实体类型
     * @return 包含实体的 {@link Optional}，如果未找到则为空
     */
    <T> Optional<T> findOne(Class<T> entityClass, QuerySpec<T> spec);

    /**
     * 根据给定的 {@link Specification} 查找单个实体。
     *
     * @param entityClass 实体类
     * @param spec        JPA Specification 谓词
     * @param <T>         实体类型
     * @return 包含实体的 {@link Optional}，如果未找到则为空
     */
    <T> Optional<T> findOne(Class<T> entityClass, Specification<T> spec);

    /**
     * 统计匹配给定 {@link QuerySpec} 的实体数量。
     *
     * @param entityClass 实体类
     * @param spec        查询规约
     * @param <T>         实体类型
     * @return 匹配的实体数量
     */
    <T> long count(Class<T> entityClass, QuerySpec<T> spec);

    /**
     * 统计匹配给定 {@link Specification} 的实体数量。
     *
     * @param entityClass 实体类
     * @param spec        JPA Specification 谓词
     * @param <T>         实体类型
     * @return 匹配的实体数量
     */
    <T> long count(Class<T> entityClass, Specification<T> spec);

    /**
     * 查找匹配给定 {@link QuerySpec} 的所有实体。
     *
     * <p>
     * 结果限制为配置的最大行数（默认 10,000）。使用
     * {@link #findAll(Class, QuerySpec, int)} 指定自定义限制，或使用
     * {@link #findAllStream(Class, QuerySpec, Consumer)} 进行无限制的流式处理。
     *
     * @param entityClass 实体类
     * @param spec        查询规约
     * @param <T>         实体类型
     * @return 匹配的实体列表
     */
    <T> List<T> findAll(Class<T> entityClass, QuerySpec<T> spec);

    /**
     * 查找匹配给定 {@link QuerySpec} 的所有实体，带自定义最大行数限制。
     *
     * @param entityClass 实体类
     * @param spec        查询规约
     * @param maxResults  最大返回结果数，{@code -1} 表示无限制
     * @param <T>         实体类型
     * @return 匹配的实体列表
     */
    <T> List<T> findAll(Class<T> entityClass, QuerySpec<T> spec, int maxResults);

    /**
     * 查找匹配给定 {@link QuerySpec} 的所有实体，带显式排序。
     *
     * @param entityClass 实体类
     * @param spec        查询规约
     * @param sort        排序规则
     * @param <T>         实体类型
     * @return 匹配的实体列表
     */
    <T> List<T> findAll(Class<T> entityClass, QuerySpec<T> spec, Sort sort);

    /**
     * 查找匹配给定 {@link QuerySpec} 的所有实体，带可选的 {@link EntityGraphHelper} 用于急加载。
     *
     * @param entityClass 实体类
     * @param spec        查询规约
     * @param entityGraph 用于急加载的实体图，{@code null} 则跳过
     * @param <T>         实体类型
     * @return 匹配的实体列表
     */
    <T> List<T> findAll(Class<T> entityClass, QuerySpec<T> spec, EntityGraphHelper<T> entityGraph);

    /**
     * 查找匹配给定 {@link QuerySpec} 的所有实体，带可选的 {@link EntityGraphHelper} 和自定义最大行数限制。
     *
     * @param entityClass 实体类
     * @param spec        查询规约
     * @param entityGraph 用于急加载的实体图，{@code null} 则跳过
     * @param maxResults  最大返回结果数，{@code -1} 表示无限制
     * @param <T>         实体类型
     * @return 匹配的实体列表
     */
    <T> List<T> findAll(Class<T> entityClass, QuerySpec<T> spec, EntityGraphHelper<T> entityGraph, int maxResults);

    /**
     * 分页查找匹配给定 {@link QuerySpec} 的实体。
     *
     * <p>
     * 执行额外的 count 查询以确定匹配实体的总数。深度分页（大偏移量）可能会根据配置触发警告或异常。
     *
     * @param entityClass 实体类
     * @param spec        查询规约
     * @param pageable    分页参数
     * @param <T>         实体类型
     * @return 包含匹配实体和总数的 {@link Page}
     */
    <T> Page<T> findAll(Class<T> entityClass, QuerySpec<T> spec, Pageable pageable);

    /**
     * 在受管生命周期内流式处理匹配给定 {@link QuerySpec} 的实体。
     *
     * <p>
     * {@link Stream} 在 try-with-resources 块内打开和消费，确保底层数据库游标的正确清理。
     *
     * <pre>{@code
     * jpa.findAllStream(User.class, spec, stream -> {
     *     stream.filter(u -> u.getAge() > 18).forEach(this::processUser);
     * });
     * }</pre>
     *
     * @param entityClass 实体类
     * @param spec        查询规约
     * @param consumer    处理流的消费者；流在消费者返回后自动关闭
     * @param <T>         实体类型
     */
    <T> void findAllStream(Class<T> entityClass, QuerySpec<T> spec, Consumer<Stream<T>> consumer);

    /**
     * 在受管生命周期内流式处理匹配给定 {@link QuerySpec} 的实体，带可选的 {@link EntityGraphHelper} 用于急加载。
     *
     * @param entityClass 实体类
     * @param spec        查询规约
     * @param entityGraph 用于急加载的实体图，{@code null} 则跳过
     * @param consumer    处理流的消费者；流在消费者返回后自动关闭
     * @param <T>         实体类型
     */
    <T> void findAllStream(Class<T> entityClass, QuerySpec<T> spec, EntityGraphHelper<T> entityGraph,
        Consumer<Stream<T>> consumer);

    /**
     * 查找匹配给定 {@link Specification} 的所有实体。
     *
     * <p>
     * 结果限制为配置的最大行数。该方法接受原始 JPA Specification 而非 {@link QuerySpec}。
     *
     * @param entityClass 实体类
     * @param spec        JPA Specification 谓词
     * @param <T>         实体类型
     * @return 匹配的实体列表
     */
    <T> List<T> find(Class<T> entityClass, Specification<T> spec);

    /**
     * 查找匹配给定 {@link Specification} 的所有实体，带自定义最大行数限制。
     *
     * @param entityClass 实体类
     * @param spec        JPA Specification 谓词
     * @param maxResults  最大返回结果数
     * @param <T>         实体类型
     * @return 匹配的实体列表
     */
    <T> List<T> find(Class<T> entityClass, Specification<T> spec, int maxResults);

    /**
     * 分页查找匹配给定 {@link Specification} 的实体。
     *
     * <p>
     * 执行额外的 count 查询以确定匹配实体的总数。深度分页（大偏移量）可能会根据配置触发警告或异常。
     *
     * @param entityClass 实体类
     * @param spec        JPA Specification 谓词
     * @param pageable    分页参数
     * @param <T>         实体类型
     * @return 包含匹配实体和总数的 {@link Page}
     */
    <T> Page<T> findPage(Class<T> entityClass, Specification<T> spec, Pageable pageable);

    /**
     * 切片查找匹配给定 {@link Specification} 的实体，不执行 count 查询。
     *
     * <p>
     * 使用"多取一行"技术来判断是否存在下一页，避免了单独 count 查询的开销。适用于不需要总数的场景。
     *
     * @param entityClass 实体类
     * @param spec        JPA Specification 谓词
     * @param pageable    分页参数
     * @param <T>         实体类型
     * @return 包含匹配实体和 hasNext 标志的 {@link Slice}
     */
    <T> Slice<T> findSlice(Class<T> entityClass, Specification<T> spec, Pageable pageable);

    /**
     * 根据主键集合查找实体。
     *
     * <p>
     * 自动处理大型 IN 子句，将其拆分为多个批次以避免超出数据库参数限制（如 Oracle：1000，SQL Server：2100）。
     *
     * @param entityClass 实体类
     * @param ids         主键值集合
     * @param <T>         实体类型
     * @param <ID>        ID 类型
     * @return 匹配的实体列表
     */
    <T, ID> List<T> findAllById(Class<T> entityClass, Collection<ID> ids);

    /**
     * 根据主键集合查找未软删除的实体。
     *
     * <p>
     * 与 {@link #findAllById(Class, Collection)} 相同，但自动过滤掉软删除的记录。
     *
     * @param entityClass 实体类
     * @param ids         主键值集合
     * @param <T>         实体类型
     * @param <ID>        ID 类型
     * @return 匹配的未删除实体列表
     */
    <T, ID> List<T> findNotDeletedAllById(Class<T> entityClass, Collection<ID> ids);

    /**
     * 执行键集（游标）分页查询。
     *
     * <p>
     * 使用上一页最后一个实体的排序列值作为 WHERE 游标，无论页面深度如何，性能始终保持 O(log n)。
     *
     * <pre>{@code
     * // 第一页
     * KeysetPage<User> page1 = jpa.findKeysetPage(User.class, spec, Sort.by("id"), 20, null);
     *
     * // 下一页：传入 page1 的 lastSortValues
     * KeysetPage<User> page2 = jpa.findKeysetPage(User.class, spec, Sort.by("id"), 20, page1.lastSortValues());
     * }</pre>
     *
     * <p>
     * <strong>约束条件：</strong>
     * <ul>
     * <li>排序字段必须在数据库中建立索引以获得最佳性能</li>
     * <li>排序字段组合应唯一（或包含主键）以避免遗漏记录</li>
     * <li>仅支持向前/向后导航；如需随机页访问，请使用偏移量分页</li>
     * </ul>
     *
     * @param entityClass   实体类
     * @param spec          JPA Specification 谓词
     * @param sort          排序规则（至少一个字段；包含主键以确保唯一性）
     * @param pageSize      每页实体数
     * @param lastSortValues 上一页最后一个实体的排序列值，{@code null} 则获取第一页
     * @param <T>           实体类型
     * @return 包含当前页面和下一页游标的 {@link KeysetPage}
     */
    <T> KeysetPage<T> findKeysetPage(Class<T> entityClass, Specification<T> spec, Sort sort, int pageSize,
        @Nullable Object[] lastSortValues);

    /**
     * 查找匹配给定 {@link QuerySpec} 的所有实体，带查询级缓存。
     *
     * <p>
     * 如果缓存中存在该查询的命中，则返回缓存列表（作为防御性副本）。否则执行查询并将结果存入缓存。
     * 在活动事务内调用时，缓存写入通过 {@code TransactionSynchronization} 延迟到事务提交后。
     *
     * <p>
     * 需要在模板上配置 {@link QueryCacheManager}。
     *
     * @param entityClass 实体类
     * @param spec        查询规约
     * @param ttlSeconds  缓存生存时间（秒）
     * @param <T>         实体类型
     * @return 匹配的实体列表
     */
    <T> List<T> findAllCached(Class<T> entityClass, QuerySpec<T> spec, long ttlSeconds);

    // ---- 工厂方法 ----

    /**
     * 为给定实体类创建新的 {@link UpdateSpec}。
     *
     * <p>
     * 返回的 spec 是未绑定的。将其传递给 {@link #execute(UpdateSpec)} 以使用模板的
     * {@link jakarta.persistence.EntityManager} 执行更新。
     *
     * @param entityClass 要更新的实体类
     * @param <T>         实体类型
     * @return 新的未绑定 {@link UpdateSpec}
     */
    <T> UpdateSpec<T> update(Class<T> entityClass);

    /**
     * 为给定实体类创建新的 {@link DeleteSpec}。
     *
     * <p>
     * 返回的 spec 是未绑定的。将其传递给 {@link #execute(DeleteSpec)} 以使用模板的
     * {@link jakarta.persistence.EntityManager} 执行删除。
     *
     * @param entityClass 要删除的实体类
     * @param <T>         实体类型
     * @return 新的未绑定 {@link DeleteSpec}
     */
    <T> DeleteSpec<T> delete(Class<T> entityClass);

    // ---- 批量保存方法 ----

    /**
     * 使用 {@code EntityManager} flush/clear 循环批量保存实体。
     *
     * <p>
     * 对每个实体使用 {@code merge()}：新实体插入，已存在实体更新。定期 {@code flush()} 和
     * {@code clear()} 通过清除一级缓存来降低内存压力。
     *
     * <p>
     * <strong>注意：</strong>返回的实体处于游离状态。访问懒加载关联会抛出
     * {@code LazyInitializationException}。
     *
     * @param entities  要保存的实体
     * @param batchSize 每批实体数（建议：50-200）
     * @param <T>       实体类型
     * @return 处于游离状态的已保存实体
     */
    <T> List<T> saveAllBatched(Iterable<T> entities, int batchSize);

    /**
     * 仅使用 {@code persist()} 操作批量保存实体。
     *
     * <p>
     * 与 {@link #saveAllBatched(Iterable, int)} 不同，该方法避免了 {@code merge()} 产生的额外 SELECT。
     * 适用于所有实体保证为新实体的场景（如 UUID 主键且手动分配 ID 的实体）。已存在的实体会抛出
     * {@code EntityExistsException}。
     *
     * @param entities  要持久化的实体
     * @param batchSize 每批实体数（建议：50-200）
     * @param <T>       实体类型
     * @return 处于游离状态的已持久化实体
     */
    <T> List<T> saveAllBatchedPure(Iterable<T> entities, int batchSize);

    /**
     * 批量保存实体，每个批次在独立事务中提交。
     *
     * <p>
     * 防止长时间运行的事务导致锁等待超时、事务日志膨胀和回滚表空间耗尽。适用于超大数据集（100,000+ 实体）。
     *
     * <p>
     * <strong>注意：</strong>如果某个批次失败，之前已提交的批次<em>不会</em>被回滚。调用者负责处理部分成功的情况。
     *
     * @param entities  要保存的实体
     * @param batchSize 每批实体数（建议：50-200）
     * @param <T>       实体类型
     * @return 已保存的实体
     */
    <T> List<T> saveAllBatchedInSeparateTransactions(Iterable<T> entities, int batchSize);

    // ---- 批量执行方法 ----

    /**
     * 执行由给定 {@link UpdateSpec} 定义的批量更新。
     *
     * @param spec 更新规约
     * @param <T>  实体类型
     * @return 受影响的行数
     */
    <T> int execute(UpdateSpec<T> spec);

    /**
     * 执行由给定 {@link DeleteSpec} 定义的批量删除。
     *
     * @param spec 删除规约
     * @param <T>  实体类型
     * @return 受影响的行数
     */
    <T> int execute(DeleteSpec<T> spec);

    /**
     * 执行由给定 {@link MergeSpec} 定义的 upsert（插入或更新）操作。
     *
     * @param spec merge 规约
     * @param <T>  实体类型
     * @return 受影响的行数
     */
    <T> int execute(MergeSpec<T> spec);

    /**
     * 使用给定的 {@link MergeSpec} 和实体列表执行批量 upsert。
     *
     * <p>
     * 在单个事务内按批次处理实体，定期刷新和清除 {@code EntityManager} 以管理内存。
     *
     * @param mergeSpec merge 规约（必须配置冲突列）
     * @param entities  要 upsert 的实体
     * @param batchSize 每批实体数（建议：50-200）
     * @param <T>       实体类型
     * @return 受影响的总行数
     */
    <T> int executeBatch(MergeSpec<T> mergeSpec, List<T> entities, int batchSize);

    /**
     * 执行由给定 {@link UpdateSpec} 定义的批量更新。
     *
     * <p>
     * 在单个事务内按批次处理匹配的实体。每批之后刷新和清除 {@code EntityManager} 以控制内存使用。
     * 所有批次一起成功或失败（单个事务）。
     *
     * @param spec      更新规约
     * @param batchSize 每批行数
     * @param <T>       实体类型
     * @return 受影响的总行数
     */
    <T> int executeBatch(UpdateSpec<T> spec, int batchSize);

    /**
     * 执行由给定 {@link DeleteSpec} 定义的批量删除。
     *
     * <p>
     * 在单个事务内按批次处理匹配的实体。每批之后刷新和清除 {@code EntityManager} 以控制内存使用。
     * 所有批次一起成功或失败（单个事务）。
     *
     * @param spec      删除规约
     * @param batchSize 每批行数
     * @param <T>       实体类型
     * @return 受影响的总行数
     */
    <T> int executeBatch(DeleteSpec<T> spec, int batchSize);

    /**
     * 执行带单次调用最大受影响行数限制的批量更新。
     *
     * @param spec    更新规约
     * @param maxRows 最大可受影响行数；传入 {@code -1} 使用全局配置
     * @param <T>     实体类型
     * @return 受影响的行数
     */
    <T> int executeWithMaxRows(UpdateSpec<T> spec, int maxRows);

    /**
     * 执行带单次调用最大受影响行数限制的批量删除。
     *
     * @param spec    删除规约
     * @param maxRows 最大可受影响行数；传入 {@code -1} 使用全局配置
     * @param <T>     实体类型
     * @return 受影响的行数
     */
    <T> int executeWithMaxRows(DeleteSpec<T> spec, int maxRows);

    /**
     * 在独立事务中执行批量更新，带失败处理策略。
     *
     * <p>
     * 每个批次在独立事务中提交。当批次失败时，{@code failureStrategy} 决定是继续还是中止。
     * 已提交的批次永远不会被回滚。
     *
     * @param spec            更新规约
     * @param batchSize       每批行数
     * @param failureStrategy 批次失败时应用的策略
     * @param <T>             实体类型
     * @return 汇总结果的 {@link BatchResult}
     */
    <T> BatchResult executeBatchInSeparateTransactions(UpdateSpec<T> spec, int batchSize,
        BatchFailureStrategy failureStrategy);

    /**
     * 在独立事务中执行批量删除，带失败处理策略。
     *
     * <p>
     * 每个批次在独立事务中提交。当批次失败时，{@code failureStrategy} 决定是继续还是中止。
     * 已提交的批次永远不会被回滚。
     *
     * @param spec            删除规约
     * @param batchSize       每批行数
     * @param failureStrategy 批次失败时应用的策略
     * @param <T>             实体类型
     * @return 汇总结果的 {@link BatchResult}
     */
    <T> BatchResult executeBatchInSeparateTransactions(DeleteSpec<T> spec, int batchSize,
        BatchFailureStrategy failureStrategy);

    /**
     * 在独立事务中执行批量更新，失败时继续执行。
     *
     * <p>
     * 便捷重载，等价于调用
     * {@code executeBatchInSeparateTransactions(spec, batchSize, BatchFailureStrategy.CONTINUE)}。
     * 每个批次独立提交；某个批次的失败不会回滚之前的批次。
     *
     * @param spec      更新规约
     * @param batchSize 每批行数
     * @param <T>       实体类型
     * @return 所有批次受影响的总行数
     */
    <T> int executeBatchInSeparateTransactions(UpdateSpec<T> spec, int batchSize);

    /**
     * 在独立事务中执行批量删除，失败时继续执行。
     *
     * <p>
     * 便捷重载，等价于调用
     * {@code executeBatchInSeparateTransactions(spec, batchSize, BatchFailureStrategy.CONTINUE)}。
     * 每个批次独立提交；某个批次的失败不会回滚之前的批次。
     *
     * @param spec      删除规约
     * @param batchSize 每批行数
     * @param <T>       实体类型
     * @return 所有批次受影响的总行数
     */
    <T> int executeBatchInSeparateTransactions(DeleteSpec<T> spec, int batchSize);
}
