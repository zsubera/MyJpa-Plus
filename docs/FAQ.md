# 常见问题 (FAQ)

## 目录

- [安装与配置](#安装与配置)
- [查询构建](#查询构建)
- [批量操作](#批量操作)
- [软删除](#软删除)
- [加密与脱敏](#加密与脱敏)
- [性能与调优](#性能与调优)
- [兼容性](#兼容性)

---

## 安装与配置

### Q: 如何添加 myjpa-plus 依赖？

**Maven:**
```xml
<dependency>
    <groupId>io.github.zsubera</groupId>
    <artifactId>myjpa-plus</artifactId>
    <version>1.3.1</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'io.github.zsubera:myjpa-plus:1.3.1'
```

### Q: 需要手动配置吗？

大多数场景下无需手动配置。添加依赖后，`MyJpaPlusAutoConfiguration` 会自动注册 `DefaultMyJpaRepository` 作为所有 Repository 的基类，自动配置 `MyJpaTemplate`、`QueryCacheManager` 等 Bean。

如需自定义配置，在 `application.yml` 中添加：
```yaml
myjpa-plus:
  query:
    max-results: 50000
    deep-pagination-offset-threshold: 500000
  soft-delete:
    auto-filter: true
    block-unconditional-delete: true
```

### Q: 遇到 `SerializedLambda.writeReplace()` 错误怎么办？

这是 Java 模块系统限制。添加 JVM 参数：

```bash
--add-opens java.base/java.lang.invoke=ALL-UNNAMED
```

**Maven spring-boot-maven-plugin 配置：**
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <jvmArguments>
            --add-opens java.base/java.lang.invoke=ALL-UNNAMED
        </jvmArguments>
    </configuration>
</plugin>
```

### Q: 如何替换默认的 Repository 基类？

通过 `@EnableJpaRepositories` 指定：
```java
@EnableJpaRepositories(repositoryFactoryBeanClass = MyJpaRepositoryFactoryBean.class)
```

或禁用自动替换：
```yaml
myjpa-plus:
  auto-repository-base-class: false
```

---

## 查询构建

### Q: QuerySpec 和 Specification 有什么区别？

`QuerySpec` 是基于 Lambda 的类型安全构建器，实现 `Specification<T>` 接口。可以直接传给 Spring Data Repository 的 `findAll(Specification)` 方法。

```java
// QuerySpec 方式（推荐）
QuerySpec<User> spec = new QuerySpec<>();
spec.eq(User::getStatus, "ACTIVE");
repository.findAll(spec);

// 原生 Specification 方式
Specification<User> spec = (root, query, cb) -> cb.equal(root.get("status"), "ACTIVE");
repository.findAll(spec);
```

### Q: 如何构建 OR 条件？

使用 `or(Consumer<OrGroup>)` 方法：
```java
QuerySpec<User> spec = new QuerySpec<>();
spec.or(g -> g
    .eq(User::getRole, "ADMIN")
    .eq(User::getRole, "MODERATOR")
);
```

### Q: 如何使用 EXISTS 子查询？

```java
QuerySpec<Customer> spec = new QuerySpec<>();
spec.exists(Order.class, sub -> sub
    .gt(Order::getAmount, 1000)
);
```

### Q: 如何在查询中使用数据库函数？

使用 `func()` 方法（仅允许白名单中的安全函数）：
```java
spec.func(User::getMetadata, "jsonb_exists", "key");
```

可通过配置扩展白名单：
```yaml
myjpa-plus:
  query:
    extra-safe-functions:
      - MY_CUSTOM_FUNC
    extra-boolean-functions:
      - MY_BOOL_FUNC
```

### Q: 如何自定义缓存后端？

实现 `CacheAdapter` 接口并注册为 Spring Bean：

```java
@Component
public class RedisCacheAdapter implements CacheAdapter {
    @Override
    public <T> T get(String key) { /* Redis GET */ }

    @Override
    public <T> void put(String key, T value, long ttlSeconds) { /* Redis SET EX */ }

    @Override
    public void evictByPrefix(String prefix) { /* Redis KEYS + DEL */ }

    @Override
    public void close() { /* 释放连接 */ }
}
```

---

## 批量操作

### Q: 批量 UPSERT 支持哪些数据库？

| 数据库 | 方言 | 单行 UPSERT | 多行批量 UPSERT |
|--------|------|------------|----------------|
| PostgreSQL | `INSERT ... ON CONFLICT DO UPDATE` | ✅ | ✅ |
| MySQL | `INSERT ... ON DUPLICATE KEY UPDATE` | ✅ | ✅ |
| Oracle | `MERGE INTO ... USING dual` | ✅ | ❌ |
| SQL Server | `MERGE INTO ... USING (...) AS source` | ✅ | ❌ |

不支持多行批量的方言会自动回退到逐行执行。

### Q: 如何处理大批量数据（10万+）？

使用 `executeBatchInSeparateTransactions`，每批在独立事务中提交：

```java
jpa.executeBatchInSeparateTransactions(
    jpa.update(User.class).set(User::getStatus, "INACTIVE"),
    1000  // 每批 1000 行
);
```

优势：
- 避免长事务导致的锁等待超时
- 减少事务日志和回滚段压力
- 每批独立提交，已成功的批次不会因后续失败回滚

### Q: 批量操作有行数限制吗？

默认最大 10,000 行。可通过配置调整：
```yaml
myjpa-plus:
  query:
    max-bulk-operation-rows: 50000
```

设置为 `-1` 可禁用限制（不推荐在生产环境使用）。

---

## 软删除

### Q: 如何启用软删除？

在实体字段上添加 `@SoftDelete` 注解：

```java
@Entity
public class User {
    @Id @GeneratedValue
    private Long id;

    @SoftDelete
    private Boolean deleted;
}
```

查询会自动过滤已删除记录。

### Q: 软删除支持哪些字段类型？

| 类型 | 默认删除值 | 自定义方式 |
|------|-----------|-----------|
| `Boolean` | `true` | 无需配置 |
| `Integer` | `1` | `@SoftDelete(deletedIntValue = 9)` |
| `Enum` | 需指定 | `@SoftDelete(deletedValue = "DELETED")` |
| `String` | `"1"` | `@SoftDelete(deletedStringValue = "X")` |

### Q: 如何跳过自动过滤？

使用 `@IgnoreSoftDelete` 注解：

```java
@Repository
public interface UserRepository extends MyJpaRepository<User, Long> {
    @IgnoreSoftDelete
    List<User> findAllIncludingDeleted();
}
```

或通过 ThreadLocal 临时禁用：
```java
DefaultMyJpaRepository.withAutoFilterOverride(false, () -> {
    // 此范围内不应用软删除过滤
    return userRepository.findAll();
});
```

### Q: 软删除与乐观锁冲突怎么办？

批量软删除（CriteriaUpdate）会绕过 `@Version` 检查。如需保留乐观锁语义，建议：
1. 在 `@SoftDelete` 实体上使用 `@IgnoreSoftDelete` 手动查询后逐条软删除
2. 或在批量操作前检查版本号

---

## 加密与脱敏

### Q: 如何配置加密密钥？

**推荐方式（环境变量）：**
```bash
export MYJPA_ENCRYPT_KEY="your-encryption-key-at-least-16-bytes"
export MYJPA_ENCRYPT_SALT="your-persistent-salt-value"
```

**系统属性方式：**
```bash
java -Dmyjpa.encrypt.key=your-key -Dmyjpa.encrypt.salt=your-salt -jar app.jar
```

### Q: 生产环境必须配置盐值吗？

是的。未配置盐值时，每次 JVM 启动会使用随机盐值，加密数据将无法恢复。

`MyJpaPlusAutoConfiguration` 会在启动时检查：
- 已配置盐值 → 正常启动
- 未配置盐值 + 生产环境 → 阻止启动
- 未配置盐值 + 开发环境 → 记录警告

### Q: 如何实现密钥轮换？

使用多版本密钥格式：
```bash
export MYJPA_ENCRYPT_KEY="v1:old-key-v1,v2:new-key-v2"
export MYJPA_ENCRYPT_KEY_VERSION="v2"
```

轮换步骤：
1. 部署新密钥（v2）并保留旧密钥（v1）
2. 设置 `MYJPA_ENCRYPT_KEY_VERSION=v2`
3. 使用 `EncryptConverter.reEncrypt()` 重新加密历史数据
4. 移除旧密钥

### Q: @Mask 脱敏是如何工作的？

`@Mask` 注解配合 `MaskSerializer`（Jackson 序列化器），在 JSON 输出时自动脱敏：

```java
@Entity
public class User {
    @Mask(type = MaskType.PHONE)
    private String phone;      // 输出: 138****1234

    @Mask(type = MaskType.EMAIL)
    private String email;      // 输出: u***@example.com

    @Mask(type = MaskType.NAME)
    private String name;       // 输出: 张*三
}
```

---

## 性能与调优

### Q: 查询结果有行数限制吗？

通过 `MyJpaTemplate` 查询默认限制 10,000 行。直接使用 `Repository.findAll()` 不限制。

建议始终使用 `MyJpaTemplate`：
```java
@Autowired
private MyJpaTemplate jpa;

List<User> users = jpa.findAll(User.class, spec);
```

### Q: 深度分页性能如何优化？

使用 Keyset 分页（游标分页）替代 Offset 分页：

```java
// 第一页
KeysetPage<User> page1 = jpa.findKeysetPage(
    User.class, spec, Sort.by("id"), 20, null);

// 下一页
KeysetPage<User> page2 = jpa.findKeysetPage(
    User.class, spec, Sort.by("id"), 20, page1.lastSortValues());
```

Keyset 分页性能始终为 O(log n)，不受页码大小影响。

### Q: IN 子句参数过多会报错吗？

`InClauseBuilder` 会自动分批处理：
- Oracle: 每批 1,000 参数
- SQL Server: 每批 2,100 参数
- 其他数据库: 每批 1,000 参数（可通过配置调整）

### Q: 如何监控慢查询？

启用慢查询监控：
```yaml
myjpa-plus:
  monitoring:
    enabled: true
    slow-query-threshold-ms: 1000
```

超过阈值的 SQL 会记录 WARN 日志。如果类路径上有 Micrometer，还会自动记录到 `myjpa.query.duration` 指标。

---

## 兼容性

### Q: 支持哪些 Java 版本？

**最低要求：Java 17+**（使用了 sealed interface 等 Java 17 特性）

### Q: 支持哪些 Spring Boot 版本？

**支持 Spring Boot 3.x**（基于 Spring Data JPA 3.x）

### Q: 支持哪些数据库？

| 数据库 | 最低版本 | UPSERT | 批量 UPSERT | 软删除 | 加密 |
|--------|---------|--------|------------|--------|------|
| PostgreSQL | 12+ | ✅ | ✅ | ✅ | ✅ |
| MySQL | 8.0+ | ✅ | ✅ | ✅ | ✅ |
| Oracle | 12c+ | ✅ | ❌ | ✅ | ✅ |
| SQL Server | 2016+ | ✅ | ❌ | ✅ | ✅ |

### Q: 支持 EclipseLink 吗？

大部分功能兼容 EclipseLink，但以下功能仅支持 Hibernate：
- `@CodeEnum`（基于编码的枚举转换）
- `@IgnoreSoftDelete` AOP 切面（依赖 Hibernate 代理）
- `SqlSlowQueryInterceptor`（依赖 Hibernate StatementInspector）

### Q: 与 MyBatis-Plus 可以共存吗？

可以。myjpa-plus 仅扩展 Spring Data JPA，不与 MyBatis 冲突。但注意两者可能注册同名 Bean（如 `MyJpaTemplate`），可通过 `@ConditionalOnMissingBean` 自动解决。

### Q: 支持多数据源吗？

通过 `EntityManagerResolver` 支持按实体类型解析不同的 `EntityManagerFactory`：

```java
@Component
public class CustomEntityManagerResolver implements EntityManagerResolver {
    @Override
    public EntityManager resolve(Class<?> entityClass) {
        if (Order.class.isAssignableFrom(entityClass)) {
            return orderEntityManager;
        }
        return defaultEntityManager;
    }
}
```
