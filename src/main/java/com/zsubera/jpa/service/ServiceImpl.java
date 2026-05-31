package com.zsubera.jpa.service;

import com.zsubera.jpa.repository.MyJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通用 Service 实现类，基于 {@link MyJpaRepository} 实现 CRUD 操作。
 *
 * <p>
 * 使用示例：
 *
 * <pre>{@code
 * @Service
 * public class UserServiceImpl extends ServiceImpl<User, Long> implements UserService {
 *     // 实现自定义业务方法
 * }
 * }</pre>
 *
 * @param <T> 实体类型
 * @param <ID> ID 类型
 */
public class ServiceImpl<T, ID> implements IService<T, ID> {

    private MyJpaRepository<T, ID> repository;

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
        getRepository().deleteAll();
    }
}
