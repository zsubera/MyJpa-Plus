package com.zsubera.jpa.spec;

import com.zsubera.jpa.annotation.SoftDelete;
import jakarta.persistence.*;

@Entity
@Table(name = "soft_delete_int_test_entity")
public class SoftDeleteIntTestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @SoftDelete(deletedIntValue = 1)
    private Integer deleted = 0;

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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
