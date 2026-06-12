# MyJpa-Plus

[![Maven Central](https://img.shields.io/maven-central/v/io.github.zsubera/myjpa-plus)](https://central.sonatype.com/artifact/io.github.zsubera/myjpa-plus)
[![构建状态](https://img.shields.io/github/actions/workflow/status/zsubera/myjpa-plus/ci.yml)](https://github.com/zsubera/MyJpa-Plus/actions)
[![许可证](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-17%2B-green.svg)](https://adoptium.net)

基于 Lambda 表达式的类型安全 JPA 工具库，专为 Spring Data JPA 设计。提供查询构建、批量操作、UPSERT/MERGE、CTE、投影查询、查询模板、多租户隔离、字段加密/脱敏、SQL 慢查询监控、乐观锁自动重试、查询缓存和代码生成。

## 特性

### 查询构建（QuerySpec）

- **Lambda 类型安全** — 使用方法引用（`Entity::getField`）替代硬编码字段名字符串
- **流式 API** — 可链式调用的 AND / OR 条件组合
- **JOIN 支持** — 内连接 / 左连接，支持嵌套条件、子连接和连接缓存
- **EXISTS 子查询** — 带类型安全条件的关联子查询
- **OR / NOT 分组** — 可任意嵌套，Consumer 模式自动关闭
- **多字段搜索** — `multiLike` 支持方法引用和字符串字段名
- **便捷字符串** — `startsWith` / `endsWith` / `contains` 无需手拼 `%`
- **不区分大小写** — `eqIgnoreCase` / `neIgnoreCase` / `likeIgnoreCase`
- **集合操作** — `isEmpty` / `isNotEmpty`，适用于 `@OneToMany` 和 `@ManyToMany`
- **null 安全** — `eq(field, null)` 自动转为 `IS NULL`
- **原始 Predicate 注入** — `where((path, cb) -> cb.and(...))` 兜底方案
- **GROUP BY / HAVING** — `groupBy(field1, field2).having(predicate)`
- **数据库函数** — `func(field, "UPPER", Op.EQ, value)` 调用数据库函数进行条件判断

### 投影查询（ProjectionSpec）

- **字段选择** — `select(Entity::getField)` 类型安全选择字段
- **聚合函数** — `selectCount()` / `selectCountDistinct()` / `selectSum()` / `selectAvg()` / `selectMax()` / `selectMin()`
- **DTO 构造函数投影** — `asDto(DtoClass.class)` 直接映射到 DTO
- **Tuple 查询** — `toTupleQuery(entityManager)` 返回 `Tuple` 结果
- **JOIN 和排序** — 支持 `join()` / `leftJoin()` / `orderByAsc()` / `orderByDesc()`
- **分页** — `findPage(entityManager, pageable)`

### 批量操作（UpdateSpec / DeleteSpec）

- **批量更新** — `new UpdateSpec<>(User.class).set(User::getStatus, "INACTIVE").lt(User::getLastLogin, date).execute(em)`
- **表达式 SET** — `setExpr(User::getBalance, (root, cb) -> cb.sum(root.get("balance"), cb.literal(100)))` 支持原子操作
- **批量删除** — `new DeleteSpec<>(Log.class).lt(Log::getTimestamp, cutoff).execute(em)`
- **安全限制** — 无条件操作需显式 `allowUnconditional(true)`
- **行数限制** — `executeLimited(em, maxRows)` 限制最大影响行数

### UPSERT / 合并（MergeSpec）

- **类型安全 UPSERT** — `new MergeSpec<>(User.class).withEntity(user).onConflict(User::getEmail).execute(em)`
- **冲突列指定** — `onConflict(User::getEmail)` 支持单列或多列唯一键
- **选择性更新** — `updateOnConflict(User::getName, User::getAge)` 仅更新指定列
- **多数据库方言** — 自动检测并生成对应 SQL：PostgreSQL (`ON CONFLICT DO UPDATE`)、MySQL (`ON DUPLICATE KEY UPDATE`)、H2 (`MERGE INTO`)
- **事务支持** — `executeInTransaction(em)` 自动管理事务

### CTE 公共表表达式（CteSpec）

- **非递归 CTE** — `CteSpec.with("name").columns("id", "name").as("SELECT ...").select("SELECT * FROM name").getResultList(em)`
- **递归 CTE** — `CteSpec.withRecursive("tree").as("... UNION ALL ...").select("...").getResultList(em)`
- **多 CTE 链式** — `.and("second_cte").as("...")` 链式添加多个 CTE
- **参数绑定** — `.setParameter("name", value)` 命名参数绑定
- **SQL 预览** — `.buildSql()` 仅构建 SQL 不执行，用于调试

### 查询模板（MyJpaTemplate）

- **便捷查询** — `findAll` / `findOne` / `findById` / `findPage` / `count`
- **流式查询** — `findAllStream(entityClass, spec, stream -> { ... })` 安全版自动管理 Stream 生命周期
- **批量保存** — `saveAllBatched(entities, batchSize)` 分批 flush/clear，避免内存溢出
- **分批执行** — `executeBatch(spec, batchSize)` 同一事务内分批；`executeBatchInSeparateTransactions` 每批独立事务避免长事务
- **深度分页保护** — 可配置 offset 硬限制和警告日志阈值
- **最大行数限制** — 查询默认限制返回行数（可配置）
- **EntityGraph 支持** — 查询时指定急切加载策略

### 查询结果缓存（QueryCacheManager）

- **TTL 过期** — `cache.put("key", result, 60)` 存入缓存，60 秒后自动过期
- **懒清除** — 访问时检查是否过期，过期条目自动移除
- **便捷操作** — `get(key)` / `evict(key)` / `clear()` / `size()`
- **基于 ConcurrentHashMap** — 线程安全，无外部依赖

### 字段加密（@Encrypt）

- **AES-GCM 加密** — `@Encrypt(algorithm="AES")` 标记字段，`EncryptConverter` 自动加解密
- **JPA AttributeConverter** — 透明集成，读写自动处理
- **密钥配置** — 通过环境变量 `MYJPA_ENCRYPT_KEY` 或系统属性 `myjpa.encrypt.key` 设置（16/24/32 字节）

### 字段脱敏（@Mask）

- **多种脱敏类型** — `@Mask(type=MaskType.PHONE)` / `EMAIL` / `ID_CARD` / `NAME`
- **Jackson 集成** — `MaskSerializer.MaskModule` 注册后自动对 `@Mask` 字段脱敏
- **示例效果** — 手机号 `138****1234`，邮箱 `u***@example.com`，姓名 `张*明`

### 乐观锁自动重试（@RetryOnOptimisticLock）

- **方法级注解** — `@RetryOnOptimisticLock(maxRetries=3, backoffMs=100)` 标记 Service 方法
- **指数退避** — 首次重试等待 `backoffMs`，后续按 `backoffMs * 2^attempt` 递增
- **透明重试** — 捕获 `OptimisticLockException` 后自动重试，无需手动处理

### SQL 慢查询监控

- **DataSource 代理** — 通过 JDBC 代理拦截 `prepareStatement`，在 `execute` 前后测量耗时
- **阈值配置** — `myjpa-plus.monitoring.slow-query-threshold-ms`（默认 1000ms）
- **开关控制** — `myjpa-plus.monitoring.enabled=true` 启用
- **自动装配** — Spring Boot 自动配置，无需手动创建 Bean

### 代码生成器（EntityCodeGenerator）

- **实体生成** — `EntityCodeGenerator.generateEntity(tableName, columns, packageName)` 生成 JPA 实体源码
- **仓库生成** — `EntityCodeGenerator.generateRepository(...)` 生成 `MyJpaRepository` 接口源码
- **脚手架工具** — 轻量级代码生成，适合快速搭建实体和仓库骨架

### 审计注解

- `@CreatedAt` / `@UpdatedAt` / `@CreatedBy` / `@UpdatedBy` — 标记审计字段
- `AuditEntityListener` — `@PrePersist` / `@PreUpdate` 自动填充
- `AuditUserProvider` — 接口，实现后自动填充 `createdBy` / `updatedBy`

### 枚举转换

- `@CodeEnum` + `@CodeEnumValue` — 解决 Hibernate 6 枚举映射问题
- 支持 `int` / `long` / `String` 类型的 code 字段
- 无需创建转换器类

### 软删除

- `@SoftDelete` — 支持 Boolean、Integer、Enum 类型
- `@IgnoreSoftDelete` — 临时禁用软删除过滤
- `SoftDeleteJpaRepository` — 提供 `findNotDeletedAll` / `findNotDeletedById` / `countNotDeleted` 等便捷方法

### Spring Boot 自动配置

- 自动注册 `MyJpaTemplate`、`AuditEntityListener`、`SoftDeleteFilterBean`、`SqlSlowQueryInterceptor`
- 通过 `application.yml` 配置查询限制、深度分页参数、监控阈值

## 环境要求

- Java 17+
- Spring Boot 3.x
- Spring Data JPA

## 安装

```xml
<dependency>
    <groupId>io.github.zsubera</groupId>
    <artifactId>myjpa-plus</artifactId>
    <version>1.3.0</version>
</dependency>
```

## 快速开始

### 查询构建

```java
// 简单等值查询（null 值自动转为 IS NULL，toSpecification() 可选）
List<User> users = userRepository.findAll(
    new QuerySpec<User>()
        .eq(User::getStatus, "ACTIVE")
        .eq(User::getDeletedAt, null)          // → IS NULL
);

// OR 多条件组合（Consumer 模式，无需 endOr）
List<User> users = userRepository.findAll(
    new QuerySpec<User>()
        .like(User::getName, "%John%")
        .or(g -> g.eq(User::getRole, "ADMIN").eq(User::getRole, "MODERATOR"))
        .toSpecification()
);

// JOIN 关联查询（Consumer 模式，无需 endJoin）
List<Order> orders = orderRepository.findAll(
    new QuerySpec<Order>()
        .join(Order::getCustomer, j -> j.eq(Customer::getCountry, "CN").gt(Customer::getLevel, 3))
        .contains(Order::getRemark, "紧急")     // LIKE '%紧急%'
        .toSpecification()
);

// NOT 条件组
List<User> users = userRepository.findAll(
    new QuerySpec<User>()
        .not(g -> g.eq(User::getStatus, "DELETED"))
        .toSpecification()
);

// EXISTS 子查询
List<Customer> customers = customerRepository.findAll(
    new QuerySpec<Customer>()
        .exists(Order.class, sub -> sub
            .gt(Order::getAmount, 1000)
            .eq(Order::getStatus, "PAID")
        )
        .toSpecification()
);

// 多字段模糊搜索（方法引用）
List<Product> products = productRepository.findAll(
    new QuerySpec<Product>()
        .multiLike("蓝色", Product::getName, Product::getDescription)
        .toSpecification()
);

// 多字段模糊搜索（字符串字段名，适用于动态字段）
List<Product> products = productRepository.findAll(
    new QuerySpec<Product>()
        .multiLike("蓝色", "name", "description")
        .toSpecification()
);

// 不区分大小写查询
List<User> users = userRepository.findAll(
    new QuerySpec<User>()
        .eqIgnoreCase(User::getEmail, "John@Example.COM")
        .toSpecification()
);

// 原始 Predicate 注入（兜底方案）
List<Order> orders = orderRepository.findAll(
    new QuerySpec<Order>()
        .where((path, cb) -> cb.or(
            cb.isNull(path.get("canceledAt")),
            cb.greaterThan(path.get("paidAmount"), 0)
        ))
        .toSpecification()
);

// 与外部 Specification 组合
List<User> users = userRepository.findAll(
    new QuerySpec<User>()
        .eq(User::getStatus, "ACTIVE")
        .toSpecification(existingSpec)
);

// GROUP BY + HAVING
List<Object[]> results = userRepository.findAll(
    new QuerySpec<User>()
        .groupBy(User::getDepartment)
        .having((root, cb) -> cb.gt(cb.count(root.get("department")), 5))
        .toSpecification()
);

// 数据库函数调用
List<User> users = userRepository.findAll(
    new QuerySpec<User>()
        .func(User::getMetadata, "jsonb_exists", "vip_flag")
        .toSpecification()
);
```

### 投影查询

```java
// 选择特定字段，返回 Tuple
List<Tuple> results = new ProjectionSpec<>(User.class)
    .select(User::getName)
    .select(User::getEmail)
    .where(q -> q.eq(User::getStatus, "ACTIVE"))
    .orderByAsc(User::getName)
    .toTupleQuery(entityManager)
    .getResultList();

// 聚合查询
List<Tuple> stats = new ProjectionSpec<>(Order.class)
    .select(Order::getCategory)
    .selectCount()
    .selectSum(Order::getAmount)
    .selectAvg(Order::getAmount)
    .groupBy(Order::getCategory)
    .toTupleQuery(entityManager)
    .getResultList();

// DTO 构造函数投影
List<UserDto> dtos = new ProjectionSpec<>(User.class)
    .select(User::getName)
    .select(User::getEmail)
    .asDto(UserDto.class)
    .where(q -> q.eq(User::getStatus, "ACTIVE"))
    .toDtoQuery(entityManager)
    .getResultList();

// 投影 + JOIN
List<Tuple> results = new ProjectionSpec<>(Order.class)
    .select(Order::getOrderNo)
    .join(Order::getCustomer, j -> j.eq(Customer::getCountry, "CN"))
    .toTupleQuery(entityManager)
    .getResultList();

// 投影分页
Page<Tuple> page = new ProjectionSpec<>(User.class)
    .select(User::getName)
    .select(User::getEmail)
    .where(q -> q.eq(User::getStatus, "ACTIVE"))
    .findPage(entityManager, PageRequest.of(0, 20));
```

### 批量更新 / 删除

```java
@Autowired
private MyJpaTemplate jpa;

// 批量更新
int updated = jpa.execute(
    jpa.update(User.class)
        .set(User::getStatus, "INACTIVE")
        .lt(User::getLastLogin, cutoffDate)
);

// 表达式 SET（原子操作）
int updated = jpa.execute(
    jpa.update(Account.class)
        .setExpr(Account::getBalance, (root, cb) -> cb.sum(root.get("balance"), cb.literal(100)))
        .eq(Account::getId, accountId)
);

// 批量删除
int deleted = jpa.execute(
    jpa.delete(LogEntry.class)
        .lt(LogEntry::getTimestamp, oldDate)
);

// 分批执行（同一事务）
int total = jpa.executeBatch(
    jpa.update(User.class).set(User::setStatus, "MIGRATED").eq(User::getVersion, 0),
    1000  // 每批 1000 行
);

// 分批提交（每批独立事务，避免长事务）
int total = jpa.executeBatchInSeparateTransactions(
    jpa.delete(Archive.class).lt(Archive::getCreatedAt, fiveYearsAgo),
    5000
);

// 限制最大影响行数
int updated = jpa.executeWithMaxRows(
    jpa.update(User.class).set(User::setStatus, "INACTIVE"),
    100
);
```

### UPSERT / 合并

```java
@Autowired
private MyJpaTemplate jpa;

// 基本 UPSERT（冲突时更新所有非冲突列）
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

// 多列唯一键
int affected = new MergeSpec<>(Order.class)
    .withEntity(order)
    .onConflict(Order::getOrderNo, Order::getTenantId)
    .execute(em);

// 自动事务管理
int affected = new MergeSpec<>(User.class)
    .withEntity(user)
    .onConflict(User::getEmail)
    .executeInTransaction(em);
```

### CTE 公共表表达式

```java
// 非递归 CTE
List<Object[]> results = CteSpec
    .with("active_users")
    .columns("id", "name")
    .as("SELECT id, name FROM users WHERE active = true")
    .select("SELECT * FROM active_users WHERE name LIKE :name")
    .setParameter("name", "%John%")
    .getResultList(em);

// 递归 CTE（树形查询）
List<Object[]> results = CteSpec
    .withRecursive("category_tree")
    .columns("id", "name", "parent_id", "depth")
    .as("SELECT id, name, parent_id, 0 FROM categories WHERE parent_id IS NULL"
        + " UNION ALL "
        + "SELECT c.id, c.name, c.parent_id, ct.depth + 1 FROM categories c "
        + "JOIN category_tree ct ON c.parent_id = ct.id")
    .select("SELECT * FROM category_tree ORDER BY depth")
    .getResultList(em);

// 多 CTE 链式
List<Object[]> results = CteSpec
    .with("active_users").as("SELECT * FROM users WHERE active = true")
    .and("recent_orders").as("SELECT * FROM orders WHERE created_at > NOW() - INTERVAL '7 days'")
    .select("SELECT u.*, o.total FROM active_users u JOIN recent_orders o ON u.id = o.user_id")
    .getResultList(em);

// 仅构建 SQL（调试用）
String sql = CteSpec.with("tmp").as("SELECT 1").select("SELECT * FROM tmp").buildSql();
```

### MyJpaTemplate 查询

```java
@Autowired
private MyJpaTemplate jpa;

// 查询
List<User> users = jpa.findAll(User.class, new QuerySpec<User>().eq(User::getStatus, "ACTIVE"));
Optional<User> user = jpa.findOne(User.class, new QuerySpec<User>().eq(User::getEmail, email));
Optional<User> user = jpa.findById(User.class, userId);
long count = jpa.count(User.class, new QuerySpec<User>().eq(User::getStatus, "ACTIVE"));

// 分页
Page<User> page = jpa.findAll(User.class, spec, PageRequest.of(0, 20));

// 流式查询（安全版，自动关闭 Stream）
jpa.findAllStream(User.class, spec, stream -> {
    stream.filter(u -> u.getAge() > 18).forEach(this::processUser);
});

// 批量保存（分批 flush/clear）
List<User> saved = jpa.saveAllBatched(users, 100);

// EntityGraph 急切加载
List<Order> orders = jpa.findAll(Order.class, spec,
    EntityGraphHelper.of(Order.class, "withItems"));
```

### 查询结果缓存

```java
@Autowired
private QueryCacheManager cache;

// 存入缓存（60 秒 TTL）
List<User> users = jpa.findAll(User.class, spec);
cache.put("active-users", users, 60);

// 读取缓存（过期返回 null）
List<User> cached = cache.get("active-users");
if (cached != null) {
    return cached;
}

// 手动清除
cache.evict("active-users");
cache.clear();
```

### 字段加密

```java
// 1. 设置密钥（环境变量或系统属性）
// export MYJPA_ENCRYPT_KEY=0123456789abcdef  （16/24/32 字节）

// 2. 实体字段标记 @Encrypt
@Entity
public class User {
    @Encrypt(algorithm = "AES")
    @Column(name = "id_card")
    private String idCard;
}

// 3. 读写自动加解密（EncryptConverter 基于 AES-GCM）
user.setIdCard("310101199001011234");
userRepository.save(user);
// 数据库存储: Base64 编码的密文

User found = userRepository.findById(id).orElseThrow();
found.getIdCard(); // → "310101199001011234"（自动解密）
```

### 字段脱敏

```java
// 1. 实体字段标记 @Mask
public class User {
    @Mask(type = MaskType.PHONE)
    private String phone;

    @Mask(type = MaskType.EMAIL)
    private String email;

    @Mask(type = MaskType.NAME)
    private String name;

    @Mask(type = MaskType.ID_CARD)
    private String idCard;
}

// 2. 注册 Jackson 模块
@Bean
public Module maskModule() {
    return new MaskSerializer.MaskModule();
}

// 3. 序列化时自动脱敏
// phone: "13812341234"  → "138****1234"
// email: "user@example.com" → "u***@example.com"
// name:  "张三丰"        → "张*丰"
// idCard: "310101199001011234" → "310***********1234"
```

### 乐观锁自动重试

```java
// 标记需要重试的方法
@RetryOnOptimisticLock(maxRetries = 3, backoffMs = 100)
public void updateProduct(Long id, String newName) {
    Product p = productRepository.findById(id).orElseThrow();
    p.setName(newName);
    productRepository.save(p);
}
// OptimisticLockException 时自动重试，指数退避: 100ms → 200ms → 400ms
```

### 代码生成器

```java
// 定义列
List<EntityCodeGenerator.ColumnDef> columns = List.of(
    new EntityCodeGenerator.ColumnDef("name", "String", false),
    new EntityCodeGenerator.ColumnDef("price", "BigDecimal", true),
    new EntityCodeGenerator.ColumnDef("createdAt", "Instant", false)
);

// 生成实体源码
String entitySrc = EntityCodeGenerator.generateEntity("products", columns, "com.example.domain");

// 生成仓库源码
String repoSrc = EntityCodeGenerator.generateRepository(
    "products", columns, "com.example.domain", "com.example.repo");
```

### 审计注解

```java
@Entity
@EntityListeners(AuditEntityListener.class)
public class Order {
    @CreatedAt
    private Instant createdAt;

    @UpdatedAt
    private Instant updatedAt;

    @CreatedBy
    private String createdBy;

    @UpdatedBy
    private String updatedBy;
}

// 实现 AuditUserProvider 以自动填充用户信息
@Component
public class SecurityAuditUserProvider implements AuditUserProvider {
    @Override
    public String getCurrentUser() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
```

### 枚举转换

```java
// 1. 枚举定义 - 在 code 字段上加 @CodeEnumValue
public enum StatusEnum {
    ACTIVE(0, "正常"),
    DELETED(1, "已删除");

    @CodeEnumValue
    private final int code;
    private final String desc;
    // ...
}

// 2. 实体使用 - 在枚举字段上加 @CodeEnum
@Entity
public class User {
    @CodeEnum
    @Column(name = "status")
    private StatusEnum status;
}

// 3. 与 @SoftDelete 配合使用
@Entity
public class User {
    @SoftDelete(deletedValue = "DELETED")
    @CodeEnum
    @Column(name = "del_flag")
    private DelFlag delFlag;
}
```

### 软删除

```java
// Boolean 类型
@SoftDelete
private Boolean deleted = false;

// Integer 类型
@SoftDelete(deletedIntValue = 1)
private Integer isDeleted = 0;

// Enum 类型
@SoftDelete(deletedValue = "DELETED")
@CodeEnum
private DelFlag delFlag;

// 仓库方法
public interface UserRepository extends MyJpaRepository<User, Long> {}

List<User> activeUsers = userRepository.findNotDeletedAll();
Optional<User> user = userRepository.findNotDeletedById(1L);
long count = userRepository.countNotDeleted();

// 临时禁用软删除过滤
@IgnoreSoftDelete
@Query("SELECT u FROM User u WHERE u.id = :id")
Optional<User> findByIdIncludingDeleted(@Param("id") Long id);
```

## 配置

```yaml
myjpa-plus:
  query:
    max-results: 10000                    # 查询最大返回行数
    deep-pagination-offset-threshold: 100000  # 深度分页警告阈值
    deep-pagination-offset-limit: -1      # 深度分页硬限制（-1 禁用）
    in-clause-max-size: 1000              # IN 子句最大参数数量
    in-clause-hard-limit: 5000            # IN 子句硬限制
    lambda-cache-size: 4096               # Lambda 属性名缓存大小
  soft-delete:
    auto-filter: true                     # 自动应用软删除过滤器
  monitoring:
    enabled: true                         # 启用 SQL 慢查询监控
    slow-query-threshold-ms: 1000         # 慢查询阈值（毫秒）

# 字段加密密钥（二选一）
# 环境变量: MYJPA_ENCRYPT_KEY=0123456789abcdef
# 系统属性: -Dmyjpa.encrypt.key=0123456789abcdef
```

## API 一览

### QuerySpec（查询条件）

| 分类 | 方法 |
|---|---|
| 比较 | `eq`, `ne`, `gt`, `ge`, `lt`, `le` |
| 不区分大小写 | `eqIgnoreCase`, `neIgnoreCase`, `likeIgnoreCase` |
| 字符串 | `like`, `notLike`, `startsWith`, `endsWith`, `contains` |
| 集合 | `in`, `notIn`, `in(Collection)`, `notIn(Collection)`, `between`, `isEmpty`, `isNotEmpty` |
| 空值 | `isNull`, `isNotNull` |
| 搜索 | `multiLike(keyword, fields...)` — 支持方法引用和字符串字段名 |
| 连接 | `join(field)`, `leftJoin(field)`, `join(field, consumer)`, `leftJoin(field, consumer)` |
| 子查询 | `exists`, `notExists`, `inSubQuery` |
| 逻辑 | `or(consumer)`, `not(consumer)` |
| 聚合 | `groupBy(field1, field2, ...)`, `having(predicate)` |
| 函数 | `func(field, functionName, params...)` — 调用数据库函数 |
| 输出 | `toSpecification()`, `toSpecification(external)` |

### ProjectionSpec（投影查询）

| 分类 | 方法 |
|---|---|
| 字段选择 | `select(field)` |
| 聚合 | `selectCount()`, `selectCountDistinct()`, `selectSum()`, `selectAvg()`, `selectMax()`, `selectMin()` |
| DTO 投影 | `asDto(DtoClass.class)` |
| 连接 | `join(field, consumer)`, `leftJoin(field, consumer)` |
| 排序 | `orderByAsc(field)`, `orderByDesc(field)` |
| 条件 | `where(consumer)`, `conditions()` |
| 输出 | `toTupleQuery(em)`, `toDtoQuery(em)`, `findPage(em, pageable)` |

### UpdateSpec（批量更新）

| 分类 | 方法 |
|---|---|
| SET | `set(field, value)`, `setExpr(field, exprFn)` |
| 条件 | 继承 QuerySpec 的所有条件方法（`eq`, `lt`, `in` 等） |
| 执行 | `execute(em)`, `executeInTransaction(em)`, `executeLimited(em, maxRows)` |
| 无条件 | `allowUnconditional(true)` → `updateAll(em)` |

### DeleteSpec（批量删除）

| 分类 | 方法 |
|---|---|
| 条件 | 继承 QuerySpec 的所有条件方法 |
| 执行 | `execute(em)`, `executeInTransaction(em)`, `executeLimited(em, maxRows)` |
| 无条件 | `allowUnconditional(true)` → `deleteAll(em)` |

### MergeSpec（UPSERT）

| 分类 | 方法 |
|---|---|
| 实体 | `withEntity(entity)` |
| 冲突 | `onConflict(fields...)` — 指定唯一键列 |
| 更新 | `updateOnConflict(fields...)` — 仅更新指定列（可选） |
| 执行 | `execute(em)`, `executeInTransaction(em)` |

### CteSpec（CTE）

| 分类 | 方法 |
|---|---|
| 创建 | `with(name)`, `withRecursive(name)` |
| 定义 | `columns(...)`, `as(sql)` |
| 链式 | `and(name)` — 添加多个 CTE |
| 主查询 | `select(sql)` |
| 参数 | `setParameter(name, value)` |
| 输出 | `getResultList(em)`, `getSingleResult(em)`, `buildSql()` |

### MyJpaTemplate（查询模板）

| 分类 | 方法 |
|---|---|
| 查询 | `findAll`, `findOne`, `findById`, `findPage`, `count` |
| 流式 | `findAllStream(entityClass, spec, consumer)` |
| 创建 | `update(entityClass)`, `delete(entityClass)` |
| 执行 | `execute(spec)`, `executeWithMaxRows(spec, maxRows)` |
| 分批 | `executeBatch(spec, batchSize)`, `executeBatchInSeparateTransactions(spec, batchSize)` |
| 保存 | `saveAllBatched(entities, batchSize)` |

### QueryCacheManager（查询缓存）

| 分类 | 方法 |
|---|---|
| 读写 | `get(key)`, `put(key, value, ttlSeconds)` |
| 清除 | `evict(key)`, `clear()` |
| 信息 | `size()` |

### EntityCodeGenerator（代码生成）

| 分类 | 方法 |
|---|---|
| 实体 | `generateEntity(tableName, columns, packageName)` |
| 仓库 | `generateRepository(tableName, columns, entityPackage, repoPackage)` |

## 协议

Apache 2.0 — 详见 [LICENSE](./LICENSE)
