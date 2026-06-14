package com.zsubera.jpa.integration;

import com.zsubera.jpa.annotation.*;
import jakarta.persistence.*;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "audit_integration_entity")
@EntityListeners(AuditEntityListener.class)
class AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @CreatedAt
    private Instant createdAt;

    @UpdatedAt
    private Instant updatedAt;

    @CreatedBy
    private String createdBy;

    @UpdatedBy
    private String updatedBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}

interface AuditEntityRepository extends JpaRepository<AuditEntity, Long> {}
