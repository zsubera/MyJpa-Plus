package com.zsubera.jpa.integration;

import jakarta.persistence.*;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "optimistic_lock_integration_entity")
class OptimisticLockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String value;

    @Version
    private Long version;

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

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}

interface OptimisticLockEntityRepository extends JpaRepository<OptimisticLockEntity, Long> {}
