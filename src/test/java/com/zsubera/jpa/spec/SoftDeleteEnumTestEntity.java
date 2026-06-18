package com.zsubera.jpa.spec;

import com.zsubera.jpa.annotation.SoftDelete;
import jakarta.persistence.*;

@Entity
@Table(name = "soft_delete_enum_test_entity")
public class SoftDeleteEnumTestEntity {

    public enum Status {
        ACTIVE, ARCHIVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    @SoftDelete(deletedValue = "ARCHIVED")
    private Status status = Status.ACTIVE;

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

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
