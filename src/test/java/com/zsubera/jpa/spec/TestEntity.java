package com.zsubera.jpa.spec;

import jakarta.persistence.*;

@Entity(name = "testEntity")
@Table(name = "test_entity")
public class TestEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;
  private Integer status;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  private ParentEntity parent;

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

  public ParentEntity getParent() {
    return parent;
  }

  public void setParent(ParentEntity parent) {
    this.parent = parent;
  }
}
