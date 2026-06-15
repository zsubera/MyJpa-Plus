# MyJpa-Plus 更新日志

所有显著变更均记录在本文件中。格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [未发布]

### 新增
- **Specification 组合工具类** — 新增 `SpecificationCombiner` 支持多个 Specification 的 and/or 组合
- **批量操作 Specification 支持** — `UpdateSpec`、`DeleteSpec` 支持通过 `where(Specification)` 复用查询条件
- **全局配置重构** — `MyJpaPlusGlobalConfig` 集中管理所有跨组件共享的配置，替代分散的 `static volatile` 字段
- **QueryCacheManager 命中率统计** — 新增 `getHitRate()`、`getHitCount()`、`getMissCount()`、`resetStats()` 方法
- **MergeSpec 自增 ID 检测** — 对使用 `@GeneratedValue(strategy=IDENTITY)` 的实体提前抛出友好错误提示
- **Spring Security 审计集成** — 自动配置中提供基于 `SecurityContextHolder` 的默认 `AuditUserProvider` 实现（`@ConditionalOnClass`）
- **EncryptConverter 自动预热** — 检测到 `MYJPA_ENCRYPT_KEY` 环境变量时自动启动后台密钥预热
- **函数白名单可扩展** — 新增 `myjpa-plus.query.extra-safe-functions` 和 `myjpa-plus.query.extra-boolean-functions` 配置项
- **spring-configuration-metadata.json 补全** — 新增 `extra-safe-functions`、`extra-boolean-functions`、`auto-repository-base-class` 配置项元数据

### 修复
- **QuerySpec.copy() 深拷贝** — 修复浅拷贝导致 JoinNode/OrNode/AndNode 嵌套条件共享可变状态的问题
- **QueryCacheManager deque 漂移** — 改进 drift 清理机制，drift 超过阈值时执行全量遍历清理
- **UpdateSpec 缓存驱逐** — 将概率采样驱逐（10%）改为确定性驱逐，消除高并发下缓存无限增长风险
- **@Deprecated 版本号修正** — `QuerySpec.setGlobalConfig()` 的 `@Deprecated(since = "2.1.0")` 修正为 `since = "1.3.0"`
- **ConditionBuilder 扩展性文档修正** — 移除过时的"通过系统属性扩展"描述，改为实际可用的 `addSafeFunctionNames()` API
- **批量操作安全漏洞** — 修复批量操作中的安全漏洞和性能问题
- **OptimisticLockRetryAdvisorTest** — 修复上下文加载失败及缓存自动失效问题
- **QuerySpec.copy() groupStack** — 修复顺序反转 bug

## [1.2.0] - 2026-06-12

### 新增
- **UPSERT/MERGE 支持** — `MergeSpec` 构建器，支持 PostgreSQL `ON CONFLICT`、MySQL `ON DUPLICATE KEY`、H2 `MERGE`
- **CTE 支持** — `CteSpec` 支持普通和递归 Common Table Expression
- **SQL 慢查询监控** — `SqlSlowQueryInterceptor` + `myjpa-plus.monitoring` 配置
- **字段加密** — `@Encrypt` 注解 + `EncryptConverter`（AES/GCM，随机 IV）
- **字段脱敏** — `@Mask` 注解 + `MaskSerializer`（Jackson，支持 PHONE/EMAIL/ID_CARD/NAME）
- **乐观锁自动重试** — `@RetryOnOptimisticLock` 注解，指数退避
- **查询结果缓存** — `QueryCacheManager`，TTL 过期策略
- **数据库函数调用** — `func(field, functionName, comparisonOp, value)` 条件方法
- **DTO 分页查询** — `ProjectionSpec.findDtoPage()` 返回 `Page<DTO>`
- **DTO 列表查询** — `ProjectionSpec.toDtoList()` / `findOneDto()`
- **Case-insensitive 字符串方法** — `containsIgnoreCase`、`startsWithIgnoreCase`、`endsWithIgnoreCase`
- **多字段 LIKE 搜索** — `multiLike(keyword, "field1", "field2")` 支持字符串字段名
- **String 类型软删除** — 支持 `@SoftDelete` 注解的 String 类型 deletedValue
- **实体管理器工厂支持** — 改进 `EntityManagerFactory` 集成
- **软删除仓库全面测试** — 新增软删除功能的完整测试覆盖

### 变更
- **移除 H2 数据库支持** — 测试统一使用 MySQL，移除 H2 依赖
- **移除 BaseEntity 类** — 优化 `AuditEntityListener` 实现，不再依赖基类
- **重构实体字段提取和方言检测** — 统一实体字段提取和数据库方言检测机制
- **重构批量操作模板** — 优化事务管理和内存控制
- **重构条件构建器接口** — 拆分为 8 个子接口，提升可维护性
- **代码注释国际化** — 更新代码注释为中文并改进文档质量

### 修复
- **not() 语义一致性** — 修复否定条件组的语义问题
- **缓存驱逐** — 修复批量操作中的缓存驱逐问题
- **批量操作异常处理** — 改进批量操作的异常处理和错误恢复
- **批量保存** — 修复批量保存中的多个问题
- **CTE/MergeSpec 安全检查** — 重构安全检查和批量操作
- **LIKE 通配符转义** — 修复 `likeSafe()` 的通配符转义问题
- **LambdaUtils 关闭机制** — 修复 `LambdaUtils` 的资源关闭问题
- **null 校验统一** — 统一条件方法的 null 校验逻辑
- **CI/CD 改进** — 配置 MySQL 服务、修复 OWASP 检查、优化测试配置

## [1.1.0] - 2026-05-31

### 新增
- **枚举转换支持** — `@CodeEnum` + `@CodeEnumValue` 注解解决 Hibernate 6 枚举映射问题
  - 支持 CHAR(1) 存储枚举编码（如 '0'、'1'、'M'、'F'）
  - 支持 int、long、String 类型的 code 字段
  - 无需创建转换器类，只需在枚举和实体字段上添加注解
- **multiLike 支持字符串字段名** — `multiLike(keyword, "field1", "field2")` 适用于动态字段名场景
- **软删除 Integer 类型支持** — `@SoftDelete(deletedIntValue = 1)` 支持用整数值标记删除状态
- **MyJpaTemplate.count() 方法** — 新增便捷的计数方法
- **聚合函数 API** — 新增聚合查询功能

### 变更
- `ConditionBuilder` 添加 `notBetween` 和 `likeIgnoreCase` 的条件变体
- `SubQuerySpec` 和 `AbstractBulkOperationSpec` 添加更多条件便捷方法
- 优化 `LambdaUtils` 缓存驱逐策略，使用 CAS 操作避免竞态条件
- 优化 `InClauseBuilder` 批次处理，避免内存泄漏
- 优化 `MyJpaTemplate` 深度分页警告日志，添加限流机制
- 统一废弃方法行为，优化缓存性能

### 修复
- 修复 EXISTS 子查询关联限制，支持从 Join 路径关联
- 修复 `MyJpaTemplate.findAllStream` 废弃策略，恢复为可调用的 @Deprecated 方法
- 修复 `DefaultMyJpaRepository.deleteById` 方法，正确处理软删除实体
- 修复终审报告全部 P0/P1/P2 问题（共 19 项）
- 统一日志框架为 SLF4J
- 修复 Spring Boot 配置集成问题
- 修复审计日志问题

## [1.0.0] - 2026-05-28

### 修复
- 修复 `between` 操作中类型不匹配的验证问题
- 代码格式化和文档优化

## [0.0.2] - 2026-05-28

### 新增
- **批量操作 LIMIT 支持** — `executeWithMaxRows()` 方法支持单次调用最大受影响行数限制
- **大型 IN 子句处理** — 自动拆分超过数据库参数限制的 IN 子句
- **GroupBy + Having 支持** — `groupBy(SFunction...)` 和 `having(BiFunction)` 条件方法
- **eqIgnoreCase/likeIgnoreCase** — 不区分大小写的字符串条件（基于 UPPER）
- **SubQuerySpec 关联** — 通过 `correlate(root)` 支持子查询关联
- **Spring Boot 自动配置** — 自动注册 `MyJpaTemplate` 和相关组件
- **EntityGraphHelper 测试** — 新增 17 个测试覆盖所有公共方法
- **MyJpaTemplate 全面测试** — 新增 108 个测试覆盖核心功能

### 变更
- **ConditionNode → sealed 接口** — 所有实现类均为 `final`
- **提取条件构建逻辑** — 创建 `PredicateHelper` 共享工具类，消除约 200 行重复代码
- **SpotBugs 阈值设为 Medium**
- **JaCoCo 覆盖率最低 60%**（排除 autoconfigure）
- **启用 doclint**（`reference,html`）
- **POM `<name>` 修正** — 从 `MyJpa-Plus` 修正为 `myjpa-plus`

### 修复
- `SubQuerySpec` 条件不再互相覆盖
- `select()` 不再被 `resolveExists` 静默覆盖
- `resolveSimple` 正确处理 IN/NOT_IN 中的 Collection 值
- `SoftDeleteHelper.findSoftDeleteField()` 的竞态条件修复
- `AbstractBulkOperationSpec.executeInTransaction()` 捕获 `Exception`（而非仅 `RuntimeException`）
- `LambdaUtils` 缓存按 class#method 存储
- SpotBugs MEDIUM 问题修复（6 个）

### 基础设施
- GitHub Actions CI（JDK 17/21 矩阵 + v* 标签触发发布部署）
- Dependabot 自动依赖更新
- CODE_OF_CONDUCT、ISSUE_TEMPLATE、PR_TEMPLATE、.editorconfig
- OWASP 依赖检查（每周定时扫描）

## [0.0.1] - 2026-05-26

### 初始发布
- 基于 Lambda API 的类型安全 JPA `Specification` 构建器
- `QuerySpec<T>`：eq, ne, gt, ge, lt, le, like, notLike, in, notIn, between, isNull, isNotNull
- JOIN 支持：`join()`、`leftJoin()` 配合 `JoinGroup`
- OR 分组：`or()` 配合 `OrGroup`，在连接中嵌套使用 `OrJoinGroup`
- EXISTS 子查询配合 `SubQuerySpec`
- 通过 `multiLike` 实现多字段 LIKE 搜索
- Spring MVC 参数解析器：`@SearchParam`、`@ListParam`
- 针对 Hibernate 延迟代理的 Jackson 序列化器
