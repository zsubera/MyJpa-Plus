package com.zsubera.jpa.integration;

import com.zsubera.jpa.annotation.Encrypt;
import com.zsubera.jpa.converter.EncryptConverter;
import jakarta.persistence.*;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "encrypted_integration_entity")
class EncryptedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Encrypt
    @Convert(converter = EncryptConverter.class)
    @Column(name = "sensitive_data")
    private String sensitiveData;

    @Encrypt
    @Convert(converter = EncryptConverter.class)
    @Column(name = "another_sensitive")
    private String anotherSensitive;

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

    public String getSensitiveData() {
        return sensitiveData;
    }

    public void setSensitiveData(String sensitiveData) {
        this.sensitiveData = sensitiveData;
    }

    public String getAnotherSensitive() {
        return anotherSensitive;
    }

    public void setAnotherSensitive(String anotherSensitive) {
        this.anotherSensitive = anotherSensitive;
    }
}

interface EncryptedEntityRepository extends JpaRepository<EncryptedEntity, Long> {}
