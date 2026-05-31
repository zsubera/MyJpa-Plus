package com.zsubera.jpa.service;

import jakarta.persistence.*;

/**
 * ServiceImpl 测试用实体。
 */
@Entity
@Table(name = "service_test_entity")
class ServiceTestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private Integer status;

    public ServiceTestEntity() {}

    public ServiceTestEntity(String name, Integer status) {
        this.name = name;
        this.status = status;
    }

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
}
