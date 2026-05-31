# MyJpa-Plus

[![Maven Central](https://img.shields.io/maven-central/v/io.github.zsubera/myjpa-plus)](https://central.sonatype.com/artifact/io.github.zsubera/myjpa-plus)
[![构建状态](https://img.shields.io/github/actions/workflow/status/zsubera/myjpa-plus/ci.yml)](https://github.com/zsubera/MyJpa-Plus/actions)
[![许可证](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-17%2B-green.svg)](https://adoptium.net)

基于 Lambda 表达式的类型安全 JPA 工具库，专为 Spring Data JPA 设计。提供查询构建、批量操作、投影查询、通用 Service 层和审计字段自动填充。

## 特性

### 查询构建（QuerySpec）

- **Lambda 类型安全** — 使用方法引用（`Entity::getField`）替代硬编码字段名字符串
- **流式 API** — 可链式调用的 AND / OR 条件组合
- **JOIN 支持** — 内连接 / 左连接，支持嵌套条件、子连接和连接缓存
- **EXISTS 子查询** — 带类型安全条件的关联子查询
- **OR / NOT 分组** — 可任意嵌套，Consumer 模式自动关闭
- **多字段搜索** — `multiLike` 支持方法引用和字符串字段名
- **便捷字符串** — `startsWith` / `endsWith` / `contains` 无需手拼 `%`
- **集合操作** — `isEmpty` / `isNotEmpty`，适用于 `@OneToMany` 和 `@ManyToMany`
- **null 安全** — `eq(field, null)` 自动转为 `IS NULL`
- **原始 Predicate 注入** — `where((path, cb) -> cb.and(...))` 兜底方案
- **GROUP BY / HAVING** — `groupBy(field1, field2).having(predicate)`

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

### 查询模板（MyJpaTemplate）

- **便捷查询** — `findAll` / `findOne` / `findById` / `findPage` / `count`
- **流式查询** — `findAllStream(entityClass, spec, stream -> { ... })` 安全版自动管理 Stream 生命周期
- **批量保存** — `saveAllBatched(entities, batchSize)` 分批 flush/clear，避免内存溢出
- **分批执行** — `executeBatch(spec, batchSize)` 同一事务内分批；`executeBatchInSeparateTransactions` 每批独立事务避免长事务
- **深度分页保护** — 可配置 offset 硬限制和警告日志阈值
- **最大行数限制** — 查询默认限制返回行数（可配置）
- **EntityGraph 支持** — 查询时指定急切加载策略

### 通用 Service 层（IService / ServiceImpl）

- **IService\<T, ID\>** — 提供 `save` / `findById` / `findAll` / `deleteById` 等 CRUD 接口
- **ServiceImpl** — 基于 `MyJpaRepository` 的实现，支持构造函数注入和 Setter 注入

### 基础实体（BaseEntity）

- **审计字段** — `createdAt` / `updatedAt` / `createdBy` / `updatedBy`（`@PrePersist` / `@PreUpdate` 自动填充时间）
- **乐观锁** — `@Version` 字段
- **ID 策略** — `@GeneratedValue(strategy = IDENTITY)`
- **equals/hashCode** — 基于 `id` 的持久化感知实现

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

- 自动注册 `MyJpaTemplate`、`AuditEntityListener`、`SoftDeleteFilterBean`
- 通过 `application.yml` 配置查询限制和深度分页参数

## 环境要求

- Java 17+
- Spring Boot 3.x
- Spring Data JPA

## 安装

```xml
<dependency>
    <groupId>io.github.zsubera</groupId>
    <artifactId>myjpa-plus</artifactId>
    <version>1.2.0</version>
</dependency>
```

## 快速开始

### 查询构建

```java
// 简单等值查询（null 值自动转为 IS NULL）
List<User> users = userRepository.findAll(
    new QuerySpec<User>()
        .eq(User::getStatus, "ACTIVE")
        .eq(User::getDeletedAt, null)          // → IS NULL
        .toSpecification()
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

### 通用 Service 层

```java
// 定义接口
public interface UserService extends IService<User, Long> {
    // 自定义业务方法
}

// 实现
@Service
public class UserServiceImpl extends ServiceImpl<User, Long> implements UserService {
    public UserServiceImpl(UserRepository repository) {
        super(repository);
    }
}

// 使用
@Autowired
private UserService userService;

User user = userService.findById(1L).orElseThrow();
List<User> activeUsers = userService.findAll();
userService.save(newUser);
userService.deleteById(1L);
```

### 基础实体

```java
@Entity
@EntityListeners(AuditEntityListener.class)
public class Product extends BaseEntity {
    private String name;
    private BigDecimal price;
    // 自动获得 id, createdAt, updatedAt, createdBy, updatedBy, version 字段
}
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
    deep-pagination-offset-limit: 1000000     # 深度分页硬限制（-1 禁用）
    max-bulk-operation-rows: 10000        # 批量操作最大影响行数（-1 禁用）
```

## API 一览

### QuerySpec（查询条件）

| 分类 | 方法 |
|------|------|
| 比较 | `eq`, `ne`, `gt`, `ge`, `lt`, `le` |
| 字符串 | `like`, `notLike`, `startsWith`, `endsWith`, `contains`, `eqIgnoreCase`, `likeIgnoreCase` |
| 集合 | `in`, `notIn`, `in(Collection)`, `notIn(Collection)`, `between`, `isEmpty`, `isNotEmpty` |
| 空值 | `isNull`, `isNotNull` |
| 搜索 | `multiLike(keyword, fields...)` — 支持方法引用和字符串字段名 |
| 连接 | `join(field)`, `leftJoin(field)`, `join(field, consumer)`, `leftJoin(field, consumer)` |
| 子查询 | `exists`, `notExists` |
| 逻辑 | `or(consumer)`, `not(consumer)` |
| 聚合 | `groupBy(field1, field2, ...)`, `having(predicate)` |
| 输出 | `toSpecification()`, `toSpecification(external)` |

### ProjectionSpec（投影查询）

| 分类 | 方法 |
|------|------|
| 字段选择 | `select(field)` |
| 聚合 | `selectCount()`, `selectCountDistinct()`, `selectSum()`, `selectAvg()`, `selectMax()`, `selectMin()` |
| DTO 投影 | `asDto(DtoClass.class)` |
| 连接 | `join(field, consumer)`, `leftJoin(field, consumer)` |
| 排序 | `orderByAsc(field)`, `orderByDesc(field)` |
| 条件 | `where(consumer)`, `conditions()` |
| 输出 | `toTupleQuery(em)`, `toDtoQuery(em)`, `findPage(em, pageable)` |

### UpdateSpec（批量更新）

| 分类 | 方法 |
|------|------|
| SET | `set(field, value)`, `setExpr(field, exprFn)` |
| 条件 | 继承 QuerySpec 的所有条件方法（`eq`, `lt`, `in` 等） |
| 执行 | `execute(em)`, `executeInTransaction(em)`, `executeLimited(em, maxRows)` |
| 无条件 | `allowUnconditional(true)` → `updateAll(em)` |

### DeleteSpec（批量删除）

| 分类 | 方法 |
|------|------|
| 条件 | 继承 QuerySpec 的所有条件方法 |
| 执行 | `execute(em)`, `executeInTransaction(em)`, `executeLimited(em, maxRows)` |
| 无条件 | `allowUnconditional(true)` → `deleteAll(em)` |

### MyJpaTemplate（查询模板）

| 分类 | 方法 |
|------|------|
| 查询 | `findAll`, `findOne`, `findById`, `findPage`, `count` |
| 流式 | `findAllStream(entityClass, spec, consumer)` |
| 创建 | `update(entityClass)`, `delete(entityClass)` |
| 执行 | `execute(spec)`, `executeWithMaxRows(spec, maxRows)` |
| 分批 | `executeBatch(spec, batchSize)`, `executeBatchInSeparateTransactions(spec, batchSize)` |
| 保存 | `saveAllBatched(entities, batchSize)` |

## 协议

Apache 2.0 — 详见 [LICENSE](./LICENSE)
