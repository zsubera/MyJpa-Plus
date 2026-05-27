package com.zsubera.jpa.integration;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "pg_test_entity")
class PgTestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Integer status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private PgParentEntity parent;

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

    public PgParentEntity getParent() {
        return parent;
    }

    public void setParent(PgParentEntity parent) {
        this.parent = parent;
    }
}

@Entity
@Table(name = "pg_parent_entity")
class PgParentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String category;
    private Integer level;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    private Set<PgTestEntity> children = new HashSet<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Set<PgTestEntity> getChildren() {
        return children;
    }

    public void setChildren(Set<PgTestEntity> children) {
        this.children = children;
    }
}
