package com.zsubera.jpa.integration;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "mysql_parent_entity")
class MySQLParentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String category;
    private Integer level;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    private Set<MySQLTestEntity> children = new HashSet<>();

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

    public Set<MySQLTestEntity> getChildren() {
        return children;
    }

    public void setChildren(Set<MySQLTestEntity> children) {
        this.children = children;
    }
}
