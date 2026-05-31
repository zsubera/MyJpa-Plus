# MyJpa-Plus

[![Maven Central](https://img.shields.io/maven-central/v/io.github.zsubera/myjpa-plus)](https://central.sonatype.com/artifact/io.github.zsubera/myjpa-plus)
[![构建状态](https://img.shields.io/github/actions/workflow/status/zsubera/myjpa-plus/ci.yml)](https://github.com/zsubera/MyJpa-Plus/actions)
[![许可证](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-17%2B-green.svg)](https://adoptium.net)

基于 Lambda 表达式的类型安全 JPA 动态查询构建器，专为 Spring Data JPA 设计。

## 特性

- **Lambda 类型安全** — 使用方法引用（`Entity::getField`）替代硬编码字段名字符串
- **流式 API** — 可链式调用的 AND / OR 条件组合
- **JOIN 支持** — 内连接 / 左连接，支持嵌套条件、子连接和连接缓存
- **EXISTS 子查询** — 带类型安全条件的关联子查询
- **OR 分组** — 可在 AND 分组内任意嵌套 OR 分组，反之亦然
- **NOT 条件组** — 否定一组条件（`not(g -> g.eq(...))`）
- **多字段搜索** — 单个关键词跨多个字段 LIKE 搜索（支持方法引用和字符串字段名）
- **便捷字符串** — `startsWith` / `endsWith` / `contains` 无需手拼 `%`
- **集合操作** — `isEmpty` / `isNotEmpty`，适用于 `@OneToMany` 和 `@ManyToMany`
- **null 安全** — `eq(field, null)` 自动转为 `IS NULL`；所有 Lambda 参数进行 null 检查
- **原始 Predicate 注入** — `where((path, cb) -> cb.and(...))` 兜底方案
- **Consumer 模式** — `or(g -> ...)` / `join(field, g -> ...)` 自动关闭，告别 begin/end 遗漏
- **枚举转换** — `@CodeEnum` + `@CodeEnumValue` 注解解决 Hibernate 6 枚举映射问题
- **软删除** — `@SoftDelete` 注解支持 Boolean、Integer、Enum 类型

## 环境要求

- Java 17+
- Spring Boot 3.x
- Spring Data JPA

## 安装

```xml
<dependency>
    <groupId>io.github.zsubera</groupId>
    <artifactId>myjpa-plus</artifactId>
    <version>1.1.0</version>
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
```

### 枚举转换

解决 Hibernate 6 将 CHAR(1) 枚举列映射为 TINYINT 导致的 `ArrayIndexOutOfBoundsException` 问题。

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
```

## API 一览

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

## 协议

Apache 2.0 — 详见 [LICENSE](./LICENSE)
