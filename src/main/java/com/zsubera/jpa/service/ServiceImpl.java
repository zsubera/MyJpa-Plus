package com.zsubera.jpa.service;

import com.zsubera.jpa.repository.MyJpaRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通用 Service 实现类，基于 {@link MyJpaRepository} 实现 CRUD 操作。
 *
 * <p>
 * 支持两种注入方式：
 *
 * <p>
 * <strong>方式一：构造函数注入（推荐）</strong>
 *
 * <pre>{@code
 * @Service
 * public class UserServiceImpl extends ServiceImpl<User, Long> implements UserService {
 *     public UserServiceImpl(UserRepository repository) {
 *         super(repository);
 *     }
 * }
 * }</pre>
 *
 * <p>
 * <strong>方式二：Setter 注入</strong>
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;Service
 *     public class UserServiceImpl extends ServiceImpl<User, Long> implements UserService {
 *         @Autowired
 *         public void setUserRepository(UserRepository repository) {
 *             setRepository(repository);
 *         }
 *     }
 * }
 * </pre>
 *
 * @param <T> 实体类型
 * @param <ID> ID 类型
 */
public class ServiceImpl<T, ID> implements IService<T, ID> {

    private static final Logger log = LoggerFactory.getLogger(ServiceImpl.class);

    private MyJpaRepository<T, ID> repository;

    /**
     * 创建 ServiceImpl 实例（无参构造函数，用于 Setter 注入方式）。
     */
    protected ServiceImpl() {
        // 用于 Setter 注入方式
    }

    /**
     * 创建 ServiceImpl 实例（构造函数注入，推荐方式）。
     *
     * @param repository 仓库实例
     * @throws IllegalArgumentException 如果 repository 为 null
     */
    protected ServiceImpl(MyJpaRepository<T, ID> repository) {
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        this.repository = repository;
    }

    /**
     * 设置仓库实例。
     *
     * @param repository 仓库实例
     */
    protected void setRepository(MyJpaRepository<T, ID> repository) {
        this.repository = repository;
    }

    /**
     * 获取仓库实例。
     *
     * @return 仓库实例
     */
    protected MyJpaRepository<T, ID> getRepository() {
        if (repository == null) {
            throw new IllegalStateException("Repository not injected. "
                + "Ensure your Service class extends ServiceImpl and has a MyJpaRepository injected via constructor or @Autowired.");
        }
        return repository;
    }

    @Override
    @Transactional
    public T save(T entity) {
        return getRepository().save(entity);
    }

    @Override
    @Transactional
    public List<T> saveAll(Iterable<T> entities) {
        return getRepository().saveAll(entities);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<T> findById(ID id) {
        return getRepository().findById(id);
    }

    /**
     * 查找所有实体。
     *
     * <p>
     * <strong>安全警告：</strong>此方法将加载所有实体到内存，对于大数据量表可能导致内存溢出。 建议使用 {@link #findAll(Pageable)} 分页查询替代。
     *
     * @return 所有实体列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<T> findAll() {
        return getRepository().findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<T> findAll(Pageable pageable) {
        return getRepository().findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<T> findAllById(Iterable<ID> ids) {
        return getRepository().findAllById(ids);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return getRepository().count();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(ID id) {
        return getRepository().existsById(id);
    }

    @Override
    @Transactional
    public void deleteById(ID id) {
        getRepository().deleteById(id);
    }

    @Override
    @Transactional
    public void delete(T entity) {
        getRepository().delete(entity);
    }

    @Override
    @Transactional
    public void deleteAll(Iterable<T> entities) {
        getRepository().deleteAll(entities);
    }

    @Override
    @Transactional
    public void deleteAll() {
        log.warn("deleteAll() called on {}. Consider using DeleteSpec with allowUnconditional() for safer operation.",
            getRepository().getClass().getSimpleName());
        getRepository().deleteAll();
    }
}
