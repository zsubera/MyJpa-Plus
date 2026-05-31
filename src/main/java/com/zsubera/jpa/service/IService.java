package com.zsubera.jpa.service;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 通用 Service 接口，提供 CRUD 便捷方法。
 *
 * <p>
 * 轻量级 IService 接口，减少用户编写样板代码。配合 {@link ServiceImpl} 使用。
 *
 * <pre>{@code
 * public interface UserService extends IService<User, Long> {
 *     // 自定义业务方法
 * }
 *
 * @Service
 * public class UserServiceImpl extends ServiceImpl<User, Long> implements UserService {
 *     // 实现自定义业务方法
 * }
 * }</pre>
 *
 * @param <T> 实体类型
 * @param <ID> ID 类型
 */
public interface IService<T, ID> {

    /**
     * 保存单个实体。
     *
     * @param entity 要保存的实体
     * @return 保存后的实体
     */
    T save(T entity);

    /**
     * 批量保存实体。
     *
     * @param entities 要保存的实体列表
     * @return 保存后的实体列表
     */
    List<T> saveAll(Iterable<T> entities);

    /**
     * 根据 ID 查找实体。
     *
     * @param id 实体 ID
     * @return 匹配实体的 Optional 包装
     */
    Optional<T> findById(ID id);

    /**
     * 查找所有实体。
     *
     * @return 所有实体列表
     */
    List<T> findAll();

    /**
     * 分页查找所有实体。
     *
     * @param pageable 分页信息
     * @return 分页结果
     */
    Page<T> findAll(Pageable pageable);

    /**
     * 根据 ID 列表查找实体。
     *
     * @param ids ID 列表
     * @return 匹配实体列表
     */
    List<T> findAllById(Iterable<ID> ids);

    /**
     * 统计实体总数。
     *
     * @return 实体总数
     */
    long count();

    /**
     * 根据 ID 判断实体是否存在。
     *
     * @param id 实体 ID
     * @return 如果实体存在返回 true，否则返回 false
     */
    boolean existsById(ID id);

    /**
     * 根据 ID 删除实体。
     *
     * @param id 实体 ID
     */
    void deleteById(ID id);

    /**
     * 删除指定实体。
     *
     * @param entity 要删除的实体
     */
    void delete(T entity);

    /**
     * 批量删除实体。
     *
     * @param entities 要删除的实体列表
     */
    void deleteAll(Iterable<T> entities);

    /**
     * 删除所有实体。
     *
     * <p>
     * <strong>安全警告：</strong>此方法将删除表中的所有记录，操作不可逆。 请确保在生产环境中谨慎使用，建议在调用前添加额外的安全确认机制。
     */
    void deleteAll();
}
