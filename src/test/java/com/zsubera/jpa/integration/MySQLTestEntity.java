package com.zsubera.jpa.integration;

import jakarta.persistence.*;

@Entity
@Table(name = "mysql_test_entity", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class MySQLTestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Integer status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private MySQLParentEntity parent;

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

    public MySQLParentEntity getParent() {
        return parent;
    }

    public void setParent(MySQLParentEntity parent) {
        this.parent = parent;
    }
}
