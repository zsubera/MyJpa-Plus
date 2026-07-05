package com.zsubera.jpa.template;

import jakarta.persistence.*;

@Entity
@Table(name = "manual_id_entity")
public class ManualIdEntity {
    @Id
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
