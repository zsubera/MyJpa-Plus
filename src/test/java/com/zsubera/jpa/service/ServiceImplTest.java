package com.zsubera.jpa.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

/**
 * {@link ServiceImpl} 的单元测试。
 */
@DataJpaTest
@ContextConfiguration(classes = ServiceImplTest.TestConfig.class)
class ServiceImplTest {

    @SpringBootApplication
    @EntityScan(basePackageClasses = ServiceTestEntity.class)
    @EnableJpaRepositories(basePackageClasses = ServiceTestRepository.class)
    static class TestConfig {}

    @Autowired
    private ServiceTestRepository repository;

    private TestService service;

    @BeforeEach
    void setUp() {
        service = new TestService(repository);
    }

    // ===== 测试 Service 实现 =====

    static class TestService extends ServiceImpl<ServiceTestEntity, Long> {
        TestService(ServiceTestRepository repository) {
            super(repository);
        }
    }

    // ===== save 测试 =====

    @Test
    @DisplayName("save - 应能正常保存实体")
    void shouldSaveEntity() {
        ServiceTestEntity entity = new ServiceTestEntity("test", 1);
        ServiceTestEntity saved = service.save(entity);

        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertEquals("test", saved.getName());
    }

    @Test
    @DisplayName("save - 保存后应能通过 ID 查找")
    void shouldFindByIdAfterSave() {
        ServiceTestEntity entity = new ServiceTestEntity("findable", 1);
        ServiceTestEntity saved = service.save(entity);

        Optional<ServiceTestEntity> found = service.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("findable", found.get().getName());
    }

    // ===== saveAll 测试 =====

    @Test
    @DisplayName("saveAll - 应能批量保存实体")
    void shouldSaveAllEntities() {
        ServiceTestEntity e1 = new ServiceTestEntity("batch1", 1);
        ServiceTestEntity e2 = new ServiceTestEntity("batch2", 2);

        List<ServiceTestEntity> saved = service.saveAll(Arrays.asList(e1, e2));

        assertEquals(2, saved.size());
        assertNotNull(saved.get(0).getId());
        assertNotNull(saved.get(1).getId());
    }

    // ===== findById 测试 =====

    @Test
    @DisplayName("findById - 存在时应返回 Optional.of")
    void shouldReturnEntityWhenFound() {
        ServiceTestEntity entity = new ServiceTestEntity("exists", 1);
        ServiceTestEntity saved = service.save(entity);

        Optional<ServiceTestEntity> result = service.findById(saved.getId());
        assertTrue(result.isPresent());
        assertEquals(saved.getId(), result.get().getId());
    }

    @Test
    @DisplayName("findById - 不存在时应返回 Optional.empty")
    void shouldReturnEmptyWhenNotFound() {
        Optional<ServiceTestEntity> result = service.findById(99999L);
        assertFalse(result.isPresent());
    }

    // ===== findAll 测试 =====

    @Test
    @DisplayName("findAll - 应返回所有实体")
    void shouldFindAllEntities() {
        service.save(new ServiceTestEntity("all1", 1));
        service.save(new ServiceTestEntity("all2", 2));

        List<ServiceTestEntity> all = service.findAll();
        assertTrue(all.size() >= 2);
    }

    // ===== findAll(Pageable) 测试 =====

    @Test
    @DisplayName("findAll(Pageable) - 应返回分页结果")
    void shouldReturnPagedResults() {
        service.save(new ServiceTestEntity("page1", 1));
        service.save(new ServiceTestEntity("page2", 2));
        service.save(new ServiceTestEntity("page3", 3));

        Page<ServiceTestEntity> page = service.findAll(PageRequest.of(0, 2));
        assertEquals(2, page.getContent().size());
        assertTrue(page.getTotalElements() >= 3);
    }

    // ===== findAllById 测试 =====

    @Test
    @DisplayName("findAllById - 应返回匹配的实体")
    void shouldFindEntitiesByIds() {
        ServiceTestEntity e1 = service.save(new ServiceTestEntity("id1", 1));
        ServiceTestEntity e2 = service.save(new ServiceTestEntity("id2", 2));
        service.save(new ServiceTestEntity("id3", 3));

        List<ServiceTestEntity> found = service.findAllById(Arrays.asList(e1.getId(), e2.getId()));
        assertEquals(2, found.size());
    }

    // ===== count 测试 =====

    @Test
    @DisplayName("count - 应返回正确的总数")
    void shouldReturnCorrectCount() {
        long initialCount = service.count();
        service.save(new ServiceTestEntity("count1", 1));
        service.save(new ServiceTestEntity("count2", 2));

        assertEquals(initialCount + 2, service.count());
    }

    // ===== existsById 测试 =====

    @Test
    @DisplayName("existsById - 存在时应返回 true")
    void shouldReturnTrueWhenExists() {
        ServiceTestEntity entity = service.save(new ServiceTestEntity("exists", 1));

        assertTrue(service.existsById(entity.getId()));
    }

    @Test
    @DisplayName("existsById - 不存在时应返回 false")
    void shouldReturnFalseWhenNotExists() {
        assertFalse(service.existsById(99999L));
    }

    // ===== deleteById 测试 =====

    @Test
    @DisplayName("deleteById - 应能删除实体")
    void shouldDeleteById() {
        ServiceTestEntity entity = service.save(new ServiceTestEntity("toDelete", 1));
        Long id = entity.getId();

        service.deleteById(id);
        assertFalse(service.existsById(id));
    }

    // ===== delete 测试 =====

    @Test
    @DisplayName("delete - 应能删除指定实体")
    void shouldDeleteEntity() {
        ServiceTestEntity entity = service.save(new ServiceTestEntity("toDelete", 1));
        Long id = entity.getId();

        service.delete(entity);
        assertFalse(service.existsById(id));
    }

    // ===== deleteAll(Iterable) 测试 =====

    @Test
    @DisplayName("deleteAll(Iterable) - 应能批量删除实体")
    void shouldDeleteAllEntities() {
        ServiceTestEntity e1 = service.save(new ServiceTestEntity("del1", 1));
        ServiceTestEntity e2 = service.save(new ServiceTestEntity("del2", 2));

        service.deleteAll(Arrays.asList(e1, e2));
        assertFalse(service.existsById(e1.getId()));
        assertFalse(service.existsById(e2.getId()));
    }

    // ===== deleteAll 测试 =====

    @Test
    @DisplayName("deleteAll - 应能删除所有实体")
    void shouldDeleteAll() {
        service.save(new ServiceTestEntity("all1", 1));
        service.save(new ServiceTestEntity("all2", 2));

        service.deleteAll();
        assertEquals(0, service.count());
    }

    // ===== 构造函数 null 校验测试 =====

    @Test
    @DisplayName("构造函数 - repository 为 null 时应抛出异常")
    void shouldThrowWhenRepositoryIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new ServiceImpl<ServiceTestEntity, Long>(null) {});
    }

    // ===== getRepository 未注入测试 =====

    @Test
    @DisplayName("getRepository - 未注入时应抛出异常")
    void shouldThrowWhenRepositoryNotInjected() {
        ServiceImpl<ServiceTestEntity, Long> emptyService = new ServiceImpl<>() {};
        assertThrows(IllegalStateException.class, emptyService::findAll);
    }

    // ===== Setter 注入测试 =====

    @Test
    @DisplayName("setRepository - 应能通过 Setter 注入仓库")
    void shouldAllowSetterInjection() {
        ServiceImpl<ServiceTestEntity, Long> setterService = new ServiceImpl<>() {};
        setterService.setRepository(repository);

        // 验证注入后可以正常使用
        setterService.save(new ServiceTestEntity("setter", 1));
        assertTrue(setterService.count() > 0);
    }
}
