package com.zsubera.jpa.spec;

import com.zsubera.jpa.annotation.SoftDelete;
import jakarta.persistence.*;

@Entity
@Table(name = "soft_delete_test_entity")
public class SoftDeleteTestEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;

  @SoftDelete private Boolean deleted = false;

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

  public Boolean getDeleted() {
    return deleted;
  }

  public void setDeleted(Boolean deleted) {
    this.deleted = deleted;
  }
}
