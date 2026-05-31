package com.zsubera.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
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
 */
@MappedSuperclass
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @Column(updatable = false, length = 64)
    private String createdBy;

    @Column(length = 64)
    private String updatedBy;

    @Version
    private Long version;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BaseEntity that)) {
            return false;
        }
        // Use id-based comparison only if both entities are persisted (id != null).
        // Otherwise fall back to identity comparison (always false for different instances).
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
        // Fixed hash code for unpersisted entities to satisfy the equals/hashCode contract:
        // All unpersisted entities of the same class have the same hashCode,
        // and equals() returns false between them (identity comparison).
        return getClass().hashCode();
    }
}
