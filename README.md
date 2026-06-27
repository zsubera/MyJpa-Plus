<div align="center">

# MyJpa-Plus

**基于 Lambda 表达式的类型安全 JPA 工具库**

[![Maven Central](https://img.shields.io/maven-central/v/io.github.zsubera/myjpa-plus?style=flat-square&logo=apachemaven&logoColor=white)](https://central.sonatype.com/artifact/io.github.zsubera/myjpa-plus)
[![构建状态](https://img.shields.io/github/actions/workflow/status/zsubera/myjpa-plus/ci.yml?style=flat-square&logo=githubactions&logoColor=white)](https://github.com/zsubera/MyJpa-Plus/actions)
[![测试覆盖率](https://img.shields.io/badge/coverage-86%25%2B-brightgreen?style=flat-square)](https://github.com/zsubera/MyJpa-Plus/actions)
[![许可证](https://img.shields.io/badge/license-Apache%202.0-blue.svg?style=flat-square)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-17%2B-green.svg?style=flat-square&logo=openjdk&logoColor=white)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)

---

**专为 Spring Data JPA 设计，提供查询构建、批量操作、UPSERT/MERGE、CTE、投影查询、字段加密/脱敏、SQL 慢查询监控、乐观锁自动重试、聚合查询、查询缓存和代码生成。**

[快速开始](#快速开始) · [特性概览](#特性) · [扩展点](#扩展点) · [架构](docs/architecture.md) · [迁移指南](./MIGRATION.md)

</div>

---

## 特性

<div align="center">

| 核心能力 | 描述 |
|:---:|:---|
| 🔍 | **查询构建** — Lambda 类型安全，流式 API，JOIN/OR/NOT 任意嵌套 |
| 📊 | **投影查询** — 字段选择、聚合函数、DTO 构造函数投影 |
| ⚡ | **批量操作** — 类型安全的 UPDATE/DELETE，支持行数限制和分批执行 |
| 🔀 | **UPSERT/MERGE** — 支持 PostgreSQL、MySQL、Oracle、SQL Server 四种方言 |
| 🌳 | **CTE** — 非递归和递归公共表表达式 |
| 🔐 | **字段加密** — AES-GCM 透明加解密，支持密钥轮换 |
| 🎭 | **字段脱敏** — 手机号、邮箱、姓名、身份证自动脱敏 |
| 🔄 | **乐观锁重试** — 方法级注解，指数退避自动重试 |
| 📈 | **慢查询监控** — DataSource 代理，自动检测慢 SQL |
| 🗑️ | **软删除** — 支持 Boolean/Integer/Enum/String 类型 |
| 🏗️ | **多数据源** — 按实体类型解析不同的 EntityManagerFactory |
| ☕ | **Spring Boot 自动配置** — 开箱即用，零配置 |
| 🔌 | **可插拔缓存** — CacheAdapter SPI 支持注入 Redis/Caffeine 等分布式缓存 |

</div>

---

## 快速开始

### 安装

```xml
<dependency>
    <groupId>io.github.zsubera</groupId>
    <artifactId>myjpa-plus</artifactId>
    <version>1.3.0</version>
</dependency>
```

### 查询构建

```java
// 简单等值查询（Lambda 模式，推荐）
List<User> users = repository.findAll(s -> s.eq(User::getStatus, "ACTIVE"));

// OR 多条件组合
List<User> users = repository.findAll(s ->
    s.like(User::getName, "John")
     .or(g -> g.eq(User::getRole, "ADMIN").eq(User::getRole, "MODERATOR"))
);

// JOIN 关联查询
List<Order> orders = orderRepository.findAll(s ->
    s.join(Order::getCustomer, j -> j.eq(Customer::getCountry, "CN"))
     .gt(Order::getAmount, 1000)
);

// EXISTS 子查询
List<Customer> customers = customerRepository.findAll(s ->
    s.exists(Order.class, sub -> sub.gt(Order::getAmount, 1000))
);

// 多字段模糊搜索
List<Product> products = productRepository.findAll(s ->
    s.multiLike("搜索词", "name", "description")
);

// 独立创建 QuerySpec 实例
QuerySpec<User> spec = QuerySpec.of(s -> s.eq(User::getStatus, "ACTIVE"));
repository.findAll(spec);
repository.count(spec);
```

### 批量操作

```java
// 方式 1：直接在 Repository 上调用（Lambda 模式，推荐）
repository.update(s -> s.set(User::getStatus, "INACTIVE").eq(User::getStatus, "ACTIVE"));
repository.delete(s -> s.lt(User::getCreatedAt, cutoffDate));

// 方式 2：通过 MyJpaTemplate
@Autowired
private MyJpaTemplate jpa;

// 批量更新
int updated = jpa.execute(
    jpa.update(User.class)
        .set(User::getStatus, "INACTIVE")
        .lt(User::getLastLogin, cutoffDate)
);

// 批量删除
int deleted = jpa.execute(
    jpa.delete(LogEntry.class)
        .lt(LogEntry::getTimestamp, oldDate)
);

// 分批执行（每批独立事务）
int total = jpa.executeBatchInSeparateTransactions(
    jpa.update(User.class).set(User::getStatus, "INACTIVE"),
    1000
);
```

### UPSERT / 合并

```java
// 基本 UPSERT
int affected = new MergeSpec<>(User.class)
    .withEntity(user)
    .onConflict(User::getEmail)
    .execute(em);

// 指定冲突时更新的列
int affected = new MergeSpec<>(User.class)
    .withEntity(user)
    .onConflict(User::getEmail)
    .updateOnConflict(User::getName, User::getAge)
    .execute(em);

// 触发 JPA 生命周期回调后再执行 UPSERT
int affected = new MergeSpec<>(User.class)
    .withEntity(user)
    .onConflict(User::getEmail)
    .updateOnConflict(User::getName)
    .executeWithCallbacks(em);

// 批量 UPSERT（自动使用多行 UPSERT 优化）
List<User> users = List.of(user1, user2, user3);
int total = new MergeSpec<>(User.class)
    .onConflict(User::getEmail)
    .executeBatch(users, em);

// 事务内执行
int affected = new MergeSpec<>(User.class)
    .withEntity(user)
    .onConflict(User::getEmail)
    .executeInTransaction(em);
```

### 字段加密

```java
@Entity
public class User {
    @Encrypt(algorithm = "AES")
    @Column(name = "id_card")
    private String idCard;
}

// 读写自动加解密，数据库存储密文
user.setIdCard("110101199001011234");
userRepository.save(user);
// 数据库：AES-GCM 密文

String idCard = userRepository.findById(1L).getIdCard();
// 返回明文："110101199001011234"
```

### 字段脱敏

```java
@Entity
public class User {
    @Mask(type = MaskType.PHONE)
    private String phone;
    
    @Mask(type = MaskType.EMAIL)
    private String email;
}

// JSON 输出时自动脱敏
// phone: "138****1234"
// email: "u***@example.com"
```

### 乐观锁重试

```java
@RetryOnOptimisticLock(maxRetries = 5, maxTotalTimeoutMs = 10000)
public void updateBalance(Long userId, BigDecimal amount) {
    Account account = accountRepository.findById(userId).orElseThrow();
    account.setBalance(account.getBalance().add(amount));
    accountRepository.save(account);
}
// 重试策略：指数退避，最多重试 5 次，总超时 10 秒
```

### CTE 查询

```java
// 递归 CTE：查询组织树
CteSpec cte = CteSpec.recursive("org_tree")
    .as("SELECT id, name, parent_id FROM organizations WHERE parent_id IS NULL " +
        "UNION ALL " +
        "SELECT o.id, o.name, o.parent_id FROM organizations o " +
        "INNER JOIN org_tree t ON o.parent_id = t.id")
    .mainQuery("SELECT * FROM org_tree");

List<Object[]> result = cte.getResultList(em);
```

### 聚合查询

```java
// 按状态统计用户数
List<Object[]> result = repository.findAll(s ->
    s.select(User::getStatus, s.count(User::getId))
     .groupBy(User::getStatus)
     .having(s.gt(s.count(User::getId), 10))
);

// DTO 投影
record UserStats(String status, long count) {}
List<UserStats> stats = repository.findAll(s ->
    s.select(User::getStatus, s.count(User::getId))
     .groupBy(User::getStatus)
     .projectTo(UserStats.class)
);
```

### 查询缓存

```java
@Autowired
private QueryCacheManager cache;

// 写入缓存（TTL 60 秒）
cache.put("user-list", users, 60);

// 读取缓存
List<User> cached = cache.get("user-list");

// 前缀驱逐（实体更新后清除相关缓存）
cache.evictByPrefix("com.example.User:");

// 事务提交后自动清除
cache.evictByPrefixAfterTransactionCommit("com.example.User:");
```

### 软删除

```java
@Entity
public class User {
    @SoftDelete
    private Boolean deleted;
}

// 查询自动过滤已删除记录（无需额外条件）
List<User> activeUsers = userRepository.findAll();

// Specification API
Specification<User> notDeleted = SoftDeleteHelper.isNotDeleted(User.class);
List<User> activeUsers = repository.findAll(notDeleted.and(otherSpec));

// 批量软删除
SoftDeleteHelper.softDeleteAll(em, User.class, true);
SoftDeleteHelper.softDeleteByIds(em, User.class, List.of(1L, 2L, 3L));

// 检查实体是否已软删除
boolean deleted = SoftDeleteHelper.isSoftDeleted(User.class, user);
```

---

## 扩展点

| SPI / 接口 | 用途 |
|:---|:---|
| `DialectStrategy` | 注册自定义 UPSERT SQL 方言，通过 `DialectDetector.registerDialect()` 注册 |
| `CacheAdapter` | 可插拔查询缓存后端，默认基于 ConcurrentHashMap，可替换为 Redis/Caffeine |
| `SoftDeleteBulkExecutor.EventPublisher` | 批量软删除后的缓存失效回调，由自动配置在启动时注册 |
| `OptimisticLockRetryAdvisor` | 重试策略配置：`maxRetries`、`maxTotalTimeoutMs`（系统属性） |

---

## 架构

见 [`docs/architecture.md`](docs/architecture.md) — 模块地图、数据流图（Lambda 查询执行、UPSERT 执行）、七层安全防御体系、线程安全性汇总表。

## 许可证

Apache 2.0。详见 [`LICENSE`](LICENSE)。
