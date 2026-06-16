<div align="center">

# MyJpa-Plus

**基于 Lambda 表达式的类型安全 JPA 工具库**

[![Maven Central](https://img.shields.io/maven-central/v/io.github.zsubera/myjpa-plus?style=flat-square&logo=apachemaven&logoColor=white)](https://central.sonatype.com/artifact/io.github.zsubera/myjpa-plus)
[![构建状态](https://img.shields.io/github/actions/workflow/status/zsubera/myjpa-plus/ci.yml?style=flat-square&logo=githubactions&logoColor=white)](https://github.com/zsubera/MyJpa-Plus/actions)
[![测试覆盖率](https://img.shields.io/badge/coverage-60%25%2B-brightgreen?style=flat-square)](https://github.com/zsubera/MyJpa-Plus/actions)
[![许可证](https://img.shields.io/badge/license-Apache%202.0-blue.svg?style=flat-square)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-17%2B-green.svg?style=flat-square&logo=openjdk&logoColor=white)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)

---

**专为 Spring Data JPA 设计，提供查询构建、批量操作、UPSERT/MERGE、CTE、投影查询、字段加密/脱敏、SQL 慢查询监控、乐观锁自动重试、查询缓存和代码生成。**

[快速开始](#快速开始) · [特性概览](#特性) · [API 文档](#api-一览) · [迁移指南](./MIGRATION.md)

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
// 简单等值查询
List<User> users = repository.findAll(
    new QuerySpec<User>()
        .eq(User::getStatus, "ACTIVE")
);

// OR 多条件组合
List<User> users = repository.findAll(
    new QuerySpec<User>()
        .like(User::getName, "John")
        .or(g -> g.eq(User::getRole, "ADMIN").eq(User::getRole, "MODERATOR"))
);

// JOIN 关联查询
List<Order> orders = orderRepository.findAll(
    new QuerySpec<Order>()
        .join(Order::getCustomer, j -> j.eq(Customer::getCountry, "CN"))
        .gt(Order::getAmount, 1000)
);

// EXISTS 子查询
List<Customer> customers = customerRepository.findAll(
    new QuerySpec<Customer>()
        .exists(Order.class, sub -> sub.gt(Order::getAmount, 1000))
);

// 多字段模糊搜索
List<Product> products = productRepository.findAll(
    new QuerySpec<Product>()
        .multiLike("搜索词", "name", "description")
);
```

### 批量操作

```java
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
```

### 字段加密

```java
@Entity
public class User {
    @Encrypt(algorithm = "AES")
    @Column(name = "id_card")
    private String idCard;
}

// 读写自动加解密
user.setIdCard("310101199001011234");
userRepository.save(user);
// 数据库存储: Base64 编码的密文
```

### 软删除

```java
@Entity
public class User {
    @SoftDelete
    private Boolean deleted = false;
}

// 自动过滤已删除记录
List<User> activeUsers = userRepository.findNotDeletedAll();

// 临时禁用软删除过滤
@IgnoreSoftDelete
Optional<User> findByIdIncludingDeleted(@Param("id") Long id);
```

---

## 数据库兼容性

| 数据库 | 版本要求 | 查询构建 | UPSERT/MERGE | 备注 |
|:---|:---:|:---:|:---:|:---|
| MySQL | 5.7+ | ✅ | ✅ | `ON DUPLICATE KEY UPDATE` |
| PostgreSQL | 9.5+ | ✅ | ✅ | `ON CONFLICT DO UPDATE` |
| Oracle | 12c+ | ✅ | ✅ | `MERGE INTO ... USING` |
| SQL Server | 2008+ | ✅ | ✅ | `MERGE INTO` |

---

## 虚拟线程兼容性

MyJpa-Plus 完全兼容 Java 21+ 虚拟线程（Virtual Threads）：

- `SoftDeleteContext` 的 ThreadLocal 在虚拟线程中行为与平台线程一致
- 推荐使用 `SoftDeleteContext.withIgnore()` 便捷方法自动管理生命周期
- 跨虚拟线程的状态传递请使用 `captureAndResetForAsync()` / `restoreForAsync()`

---

## 环境要求

- **Java**: 17+
- **Spring Boot**: 3.x
- **Spring Data JPA**
- **JPA 实现**: Hibernate 6.x（推荐）

---

## 配置

```yaml
myjpa-plus:
  query:
    max-results: 10000                    # 查询最大返回行数
    deep-pagination-offset-threshold: 100000  # 深度分页警告阈值
    deep-pagination-offset-limit: -1      # 深度分页硬限制（-1 禁用）
    in-clause-max-size: 1000              # IN 子句最大参数数量
    in-clause-hard-limit: 5000            # IN 子句硬限制
    default-timeout-seconds: 30           # 查询超时（秒），-1 禁用
    max-bulk-operation-rows: 10000        # 批量操作最大影响行数（0 禁用）
  soft-delete:
    auto-filter: true                     # 自动应用软删除过滤器
  monitoring:
    enabled: true                         # 启用 SQL 慢查询监控
    slow-query-threshold-ms: 1000         # 慢查询阈值（毫秒）
  cache:
    auto-invalidation-enabled: true       # 缓存自动失效

# 字段加密配置
# 环境变量: MYJPA_ENCRYPT_KEY=<密钥>  MYJPA_ENCRYPT_SALT=<盐值>
# 系统属性: -Dmyjpa.encrypt.key=<密钥>  -Dmyjpa.encrypt.salt=<盐值>
```

---

## API 一览

<details>
<summary><b>QuerySpec（查询条件）</b></summary>

| 分类 | 方法 |
|:---|:---|
| 比较 | `eq`, `ne`, `gt`, `ge`, `lt`, `le` |
| 不区分大小写 | `eqIgnoreCase`, `neIgnoreCase`, `likeIgnoreCase` |
| 字符串 | `like`, `notLike`, `startsWith`, `endsWith`, `contains` |
| 集合 | `in`, `notIn`, `between`, `isEmpty`, `isNotEmpty` |
| 空值 | `isNull`, `isNotNull` |
| 搜索 | `multiLike(keyword, fields...)` |
| 连接 | `join(field)`, `leftJoin(field)`, `join(field, consumer)` |
| 子查询 | `exists`, `notExists`, `inSubQuery` |
| 逻辑 | `or(consumer)`, `not(consumer)` |
| 聚合 | `groupBy(field1, field2, ...)`, `having(predicate)` |
| 函数 | `func(field, functionName, params...)` |
| 输出 | `toSpecification()`, `toSpecification(external)` |

</details>

<details>
<summary><b>ProjectionSpec（投影查询）</b></summary>

| 分类 | 方法 |
|:---|:---|
| 字段选择 | `select(field)` |
| 聚合 | `selectCount()`, `selectSum()`, `selectAvg()`, `selectMax()`, `selectMin()` |
| DTO 投影 | `asDto(DtoClass.class)` |
| 连接 | `join(field, consumer)`, `leftJoin(field, consumer)` |
| 排序 | `orderByAsc(field)`, `orderByDesc(field)` |
| 输出 | `toTupleQuery(em)`, `toDtoQuery(em)`, `findPage(em, pageable)` |

</details>

<details>
<summary><b>UpdateSpec / DeleteSpec（批量操作）</b></summary>

| 分类 | 方法 |
|:---|:---|
| SET | `set(field, value)`, `setExpr(field, exprFn)` |
| 条件 | 继承 QuerySpec 的所有条件方法 |
| 执行 | `execute(em)`, `executeInTransaction(em)`, `executeLimited(em, maxRows)` |
| 无条件 | `allowUnconditional(true)` → `updateAll(em)` / `deleteAll(em)` |

</details>

<details>
<summary><b>MergeSpec（UPSERT）</b></summary>

| 分类 | 方法 |
|:---|:---|
| 实体 | `withEntity(entity)` |
| 冲突 | `onConflict(fields...)` |
| 更新 | `updateOnConflict(fields...)` |
| 执行 | `execute(em)`, `executeInTransaction(em)` |

</details>

<details>
<summary><b>MyJpaTemplate（查询模板）</b></summary>

| 分类 | 方法 |
|:---|:---|
| 查询 | `findAll`, `findOne`, `findById`, `findPage`, `count` |
| 流式 | `findAllStream(entityClass, spec, consumer)` |
| 创建 | `update(entityClass)`, `delete(entityClass)` |
| 执行 | `execute(spec)`, `executeWithMaxRows(spec, maxRows)` |
| 分批 | `executeBatch(spec, batchSize)`, `executeBatchInSeparateTransactions(spec, batchSize)` |
| 保存 | `saveAllBatched(entities, batchSize)` |

</details>

---

## 协议

Apache 2.0 — 详见 [LICENSE](./LICENSE)
