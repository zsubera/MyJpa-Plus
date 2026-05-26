# 贡献指南

## 开发环境搭建

- JDK 17+
- Maven 3.8+

```bash
git clone https://github.com/zsubera/myjpa-plus
cd myjpa-plus
./mvnw compile
```

## 构建

```bash
./mvnw clean verify
```

## 运行测试

```bash
./mvnw test
```

## 代码风格

- 使用方法引用（`Entity::getField`）确保类型安全 — 切勿硬编码字段名字符串
- 为公开 API 参数添加 null 校验
- 遵循现有包结构：`com.zsubera.jpa.spec`
- 所有条件方法应作为默认方法归属于 `ConditionBuilder` 接口

## 添加新运算符

1. 在 `QuerySpec.Op` 中添加枚举值
2. 在 `ConditionBuilder<E, SELF>` 中添加默认方法
3. 在 `QuerySpec.resolveSimple()` 中添加对应的 case
4. 在 `QuerySpecTest` 中添加测试

## Pull Request 检查清单

- [ ] 构建通过：`./mvnw clean verify`
- [ ] 为新增功能添加了测试
- [ ] 已更新 CHANGELOG.md
