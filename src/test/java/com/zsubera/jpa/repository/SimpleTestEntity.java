package com.zsubera.jpa.repository;

import jakarta.persistence.*;

/**
 * Simple entity without @SoftDelete for testing MyJpaRepository with non-soft-delete entities.
 */
@Entity
@Table(name = "simple_test_entity")
public class SimpleTestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

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
}
