package com.zsubera.jpa.spec;

import com.zsubera.jpa.annotation.SoftDelete;
import jakarta.persistence.*;

@Entity
@Table(name = "soft_delete_string_test_entity")
public class SoftDeleteStringTestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @SoftDelete(deletedStringValue = "Y")
    private String deleted = "N";

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

    public String getDeleted() {
        return deleted;
    }

    public void setDeleted(String deleted) {
        this.deleted = deleted;
    }
}
