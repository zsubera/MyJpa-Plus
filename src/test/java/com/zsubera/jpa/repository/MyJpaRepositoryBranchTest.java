package com.zsubera.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = MyJpaRepositoryBranchTest.TestConfig.class)
class MyJpaRepositoryBranchTest {

    @SpringBootApplication
    @EntityScan(basePackageClasses = {MyJpaTestEntity.class, SimpleTestEntity.class})
    @EnableJpaRepositories(basePackageClasses = {MyJpaTestRepository.class, SimpleTestRepository.class})
    static class TestConfig {}

    @Autowired
    private MyJpaTestRepository repository;

    @Autowired
    private SimpleTestRepository simpleRepository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
        simpleRepository.deleteAll();
        simpleRepository.flush();
    }

    @Test
    void testFindAll_withConsumerAndSort() {
        MyJpaTestEntity e1 = new MyJpaTestEntity();
        e1.setName("z");
        e1.setDeleted(false);
        repository.save(e1);
        MyJpaTestEntity e2 = new MyJpaTestEntity();
        e2.setName("a");
        e2.setDeleted(false);
        repository.save(e2);
        List<MyJpaTestEntity> result =
            repository.findAll(s -> s.eq(MyJpaTestEntity::getDeleted, false), Sort.by("name"));
        assertEquals(2, result.size());
        assertEquals("a", result.get(0).getName());
    }

    @Test
    void testExecute_nullUpdateSpec_throws() {
        assertThrows(Exception.class,
            () -> repository.execute((com.zsubera.jpa.update.UpdateSpec<MyJpaTestEntity>)null));
    }

    @Test
    void testExecute_nullDeleteSpec_throws() {
        assertThrows(Exception.class,
            () -> repository.execute((com.zsubera.jpa.update.DeleteSpec<MyJpaTestEntity>)null));
    }

    @Test
    void testExecute_nullMergeSpec_throws() {
        assertThrows(Exception.class,
            () -> repository.execute((com.zsubera.jpa.update.MergeSpec<MyJpaTestEntity>)null));
    }
}
