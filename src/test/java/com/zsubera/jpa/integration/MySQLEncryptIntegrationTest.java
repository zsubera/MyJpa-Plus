package com.zsubera.jpa.integration;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@Tag("integration")
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect", "spring.jpa.hibernate.ddl-auto=create",
    "myjpa.encrypt.key=1234567890123456", "myjpa.encrypt.salt=test-salt-value",
    "myjpa-plus.encrypt.skip-salt-check=true"})
class MySQLEncryptIntegrationTest {

    static {
        System.setProperty("myjpa.encrypt.key", "1234567890123456");
        System.setProperty("myjpa.encrypt.salt", "test-salt-value");
        System.setProperty("myjpa-plus.encrypt.skip-salt-check", "true");
    }

    @Autowired
    private EncryptedEntityRepository repository;

    @PersistenceContext
    private EntityManager em;

    @AfterAll
    static void clearEncryptKey() {
        System.clearProperty("myjpa.encrypt.key");
        System.clearProperty("myjpa-plus.encrypt.skip-salt-check");
    }

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
    }

    @Test
    void testEncryptedFieldIsStoredEncrypted() {
        EncryptedEntity entity = new EncryptedEntity();
        entity.setName("test-user");
        entity.setSensitiveData("sensitive-information");
        repository.save(entity);
        repository.flush();
        em.clear();

        EncryptedEntity found = repository.findById(entity.getId()).orElse(null);
        assertNotNull(found);
        assertEquals("sensitive-information", found.getSensitiveData());
    }

    @Test
    void testEncryptedFieldDiffersFromOriginal() {
        EncryptedEntity entity = new EncryptedEntity();
        entity.setName("test-user");
        entity.setSensitiveData("sensitive-information");
        repository.save(entity);
        repository.flush();

        String dbValue = em.createNativeQuery("SELECT sensitive_data FROM encrypted_integration_entity WHERE id = :id")
            .setParameter("id", entity.getId()).getSingleResult().toString();

        assertNotEquals("sensitive-information", dbValue, "Database should store encrypted value");
        assertFalse(dbValue.isEmpty(), "Encrypted value should not be empty");
    }

    @Test
    void testNullEncryptedField() {
        EncryptedEntity entity = new EncryptedEntity();
        entity.setName("test-user");
        entity.setSensitiveData(null);
        repository.save(entity);
        repository.flush();
        em.clear();

        EncryptedEntity found = repository.findById(entity.getId()).orElse(null);
        assertNotNull(found);
        assertNull(found.getSensitiveData());
    }

    @Test
    void testMultipleEncryptedFields() {
        EncryptedEntity entity = new EncryptedEntity();
        entity.setName("test-user");
        entity.setSensitiveData("data1");
        entity.setAnotherSensitive("data2");
        repository.save(entity);
        repository.flush();
        em.clear();

        EncryptedEntity found = repository.findById(entity.getId()).orElse(null);
        assertNotNull(found);
        assertEquals("data1", found.getSensitiveData());
        assertEquals("data2", found.getAnotherSensitive());
    }

    @Test
    void testEncryptedFieldUpdate() {
        EncryptedEntity entity = new EncryptedEntity();
        entity.setName("test-user");
        entity.setSensitiveData("original-data");
        repository.save(entity);
        repository.flush();

        entity.setSensitiveData("updated-data");
        repository.save(entity);
        repository.flush();
        em.clear();

        EncryptedEntity found = repository.findById(entity.getId()).orElse(null);
        assertNotNull(found);
        assertEquals("updated-data", found.getSensitiveData());
    }

    @Test
    void testChineseContentRoundTrip() {
        EncryptedEntity entity = new EncryptedEntity();
        entity.setName("test-user");
        entity.setSensitiveData("中文敏感信息");
        repository.save(entity);
        repository.flush();
        em.clear();

        EncryptedEntity found = repository.findById(entity.getId()).orElse(null);
        assertNotNull(found);
        assertEquals("中文敏感信息", found.getSensitiveData());
    }

    @Test
    void testEmptyStringRoundTrip() {
        EncryptedEntity entity = new EncryptedEntity();
        entity.setName("test-user");
        entity.setSensitiveData("");
        repository.save(entity);
        repository.flush();
        em.clear();

        EncryptedEntity found = repository.findById(entity.getId()).orElse(null);
        assertNotNull(found);
        assertEquals("", found.getSensitiveData());
    }
}
