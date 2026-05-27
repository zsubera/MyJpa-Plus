package com.zsubera.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
 * 开箱即用地提供 {@code id}、{@code createdAt} 和 {@code updatedAt} 字段。
 *
 * <p>
 * {@code equals} 和 {@code hashCode} 在 {@code id} 非空（已持久化实体）时基于 {@code id} 字段， 否则回退到对象标识比较。
 */
@MappedSuperclass
public abstract class BaseEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

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

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
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
        // Otherwise fall back to identity comparison.
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
        return id != null ? Objects.hashCode(id) : super.hashCode();
    }
}
