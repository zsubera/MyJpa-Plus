package com.zsubera.jpa.integration;

import com.zsubera.jpa.annotation.SoftDelete;
import jakarta.persistence.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

@Entity
@Table(name = "soft_delete_integration_entity")
class SoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private Integer status;

    @SoftDelete
    private Boolean active = true;

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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}

interface SoftDeleteEntityRepository
    extends JpaRepository<SoftDeleteEntity, Long>, JpaSpecificationExecutor<SoftDeleteEntity> {}
