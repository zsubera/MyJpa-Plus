package com.zsubera.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * 包含通用审计和标识字段的基础实体。
 *
 * <p>
 * 继承此类可避免重复编写样板字段：
 *
 * <pre>{@code
 * @Entity
 * public class Product extends BaseEntity {
 *     private String name;
 * }
 * }</pre>
 *
 * <p>
 * 开箱即用地提供 {@code id}、{@code createdAt}、{@code updatedAt}、 {@code createdBy}、{@code updatedBy} 和 {@code version} 字段。
 *
 * <p>
 * {@code createdAt} 和 {@code updatedAt} 通过 {@link PrePersist} 和 {@link PreUpdate} 自动填充。 {@code createdBy} 和
 * {@code updatedBy} 不在此类中自动填充，需要通过以下方式之一配置：
 * <ul>
 * <li>使用 {@link com.zsubera.jpa.annotation.AuditEntityListener} + {@link com.zsubera.jpa.annotation.AuditUserProvider}
 * 自动填充</li>
 * <li>在业务代码中手动设置</li>
 * <li>通过 AOP 切面拦截填充</li>
 * </ul>
 *
 * <p>
 * {@code equals} 和 {@code hashCode} 在 {@code id} 非空（已持久化实体）时基于 {@code id} 字段， 否则使用固定 hashCode（基于实体类），确保 equals/hashCode
 * 契约成立。
 *
 * <p>
 * <strong>ID 生成策略说明：</strong>默认使用 {@link GenerationType.IDENTITY}，适用于 MySQL、PostgreSQL、H2 等支持自增列的数据库。 如果使用 Oracle
 * 或其他不支持自增列的数据库，子类应覆盖 {@code id} 字段并使用 {@link GenerationType#SEQUENCE} 或 {@link GenerationType#TABLE}。示例：
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;Entity
 *     public class OracleEntity extends BaseEntity {
 *         &#64;Override
 *         &#64;Id
 *         &#64;GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_gen")
 *         @SequenceGenerator(name = "seq_gen", sequenceName = "my_sequence")
 *         protected Long getId() {
 *             return super.getId();
 *         }
 *     }
 * }
 * </pre>
 */
@MappedSuperclass
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(updatable = false, length = 64)
    private String createdBy;

    @Column(length = 64)
    private String updatedBy;

    @Version
    private Long version;

    /**
     * JPA 生命周期回调：在持久化前自动设置 createdAt 和 updatedAt。
     *
     * <p>
     * <strong>注意：</strong>子类覆写此方法时必须调用 {@code super.prePersist()}， 否则 createdAt 和 updatedAt 字段不会被自动填充。
     */
    @PrePersist
    protected void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    /**
     * 设置实体 ID。
     *
     * <p>
     * <strong>设计说明：</strong>此方法为 {@code protected} 访问级别，这是有意的设计决策：
     * <ul>
     * <li>ID 通常由 JPA 框架通过 {@code @GeneratedValue} 自动管理，不应由业务代码手动设置</li>
     * <li>子类可以在特定场景（如数据导入、测试）中访问此方法</li>
     * <li>如需在测试中设置 ID，可通过反射或使用测试工具类</li>
     * </ul>
     *
     * @param id 实体 ID
     */
    protected void setId(Long id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    protected void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    protected void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    protected void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    protected void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Long getVersion() {
        return version;
    }

    protected void setVersion(Long version) {
        this.version = version;
    }

    /**
     * 基于实体 ID 的相等性比较。
     *
     * <p>
     * 仅当两个实体均已持久化（{@code id != null}）时，基于 {@code id} 进行比较。 否则返回 {@code false}（未持久化实体始终不相等）。
     *
     * <p>
     * <strong>Set 使用警告：</strong>未持久化实体的 {@code equals()} 始终返回 {@code false}，这意味着：
     * <ul>
     * <li>将未持久化实体添加到 {@code HashSet} 中会导致重复条目</li>
     * <li>在持久化前使用 {@code Set} 去重不会生效</li>
     * <li>建议在实体持久化后再进行 Set 操作，或使用 {@code List} 替代</li>
     * </ul>
     *
     * @param o 要比较的对象
     * @return 如果两个实体的 ID 相等则返回 true
     */
    @Override
    @SuppressWarnings("rawtypes")
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        // 使用 getClass() 进行严格的类型相等性检查，防止跨子类比较。
        // 对于 JPA 实体而言，这比 instanceof 更安全，因为不同实体类型
        // 即使共享相同 ID 也不应被视为相等。
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BaseEntity that = (BaseEntity)o;
        // 仅当两个实体均已持久化（id != null）时才使用基于 id 的比较。
        // 否则回退到引用比较（不同实例始终为 false）。
        Long id = getId();
        Long thatId = that.getId();
        if (id != null && thatId != null) {
            return Objects.equals(id, thatId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        Long id = getId();
        if (id != null) {
            return Objects.hashCode(id);
        }
        // 未持久化实体使用固定 hashCode，以满足 equals/hashCode 契约：
        // 同一类型的所有未持久化实体具有相同 hashCode，
        // 且 equals() 在它们之间返回 false（引用比较）。
        return getClass().hashCode();
    }
}
