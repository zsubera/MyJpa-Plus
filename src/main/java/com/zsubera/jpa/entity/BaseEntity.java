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
 * Base entity with common audit and identity fields.
 *
 * <p>Extend this class to avoid repeating boilerplate fields:
 *
 * <pre>{@code
 * @Entity
 * public class Product extends BaseEntity {
 *     private String name;
 * }
 * }</pre>
 *
 * <p>Provides {@code id}, {@code createdAt}, and {@code updatedAt} out of the box.
 *
 * <p>{@code equals} and {@code hashCode} are based on the {@code id} field when non-null (persisted
 * entities), otherwise fall back to identity comparison.
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
