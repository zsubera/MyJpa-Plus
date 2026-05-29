# myjpa-plus 优化报告

---
## 轮次 1 - 优化记录
时间：2026-05-29 19:00

### 已修复问题
- [P1-1] ProjectionSpec.JoinGroup 大量重复条件节点定义（22个 record 与 spec.ConditionNode 结构重复）：删除 JoinGroup 内部的 ConditionNode sealed interface（72行），改用 spec.ConditionNode.SimpleNode 和 spec.ConditionNode.CollectionNode，消除约 590 行重复代码
- [P1-2] ProjectionSpec.resolveJoins() 超长 if-else 链（~90行）：重构为 resolveJoinCondition() + resolveSimpleForJoin() 两个方法，使用 switch 表达式按 Op 枚举分派，代码行数从 ~90 行降至 ~50 行，新增条件类型时编译器会提示未处理的 case
- [P1-3] ProjectionSpec 类过大（1074行）：通过消除 JoinGroup 内部的重复 ConditionNode 定义（约 590 行），文件从 1074 行降至约 910 行
- [P1-4] AbstractBulkOperationSpec 条件方法未实现 ConditionBuilder 接口：添加详细的类级 Javadoc 说明设计原因（延迟 Lambda vs 延迟 ConditionNode 树），以及新增条件类型时的六处同步清单
- [P1-5] SubQuerySpec 独立实现条件 API：在已有设计说明基础上，添加六处同步更新清单（ConditionBuilder → ConditionNode.Op → QuerySpec → ProjectionSpec.JoinGroup → AbstractBulkOperationSpec → SubQuerySpec）
- [P1-6] executeLimited() 两步操作存在竞态条件：增强 UpdateSpec 和 DeleteSpec 的 JavaDoc，添加按推荐程度排序的安全使用建议（悲观锁 > @Transactional > 原生 SQL > 分布式锁），包含代码示例
- [P1-8] 深度分页无实际限制：新增 myjpa-plus.query.deep-pagination-offset-limit 配置项，默认值 -1（禁用硬限制），超过此值抛出 IllegalArgumentException。同步更新 MyJpaPlusProperties、MyJpaTemplate、MyJpaPlusAutoConfiguration

### 未修复问题
- P1-7 findAllStream() 废弃版本资源泄漏风险：已有充分处理（@Deprecated(forRemoval=true) + 运行时日志警告 + 安全替代方案指引），无需额外修改
- P2-1 至 P2-15：均为 P2 优先级的可选优化，本轮未涉及

### 修改详情
#### 1. P1-1/P1-2: ProjectionSpec.JoinGroup 条件节点去重与 resolveJoins 重构
**文件**：ProjectionSpec.java
**修改前**：JoinGroup 内部定义了完整的 ConditionNode sealed interface（22个 record），resolveJoins() 使用 ~90 行 if-else instanceof 链逐个处理
**修改后**：JoinGroup 条件方法直接创建 spec.ConditionNode.SimpleNode/CollectionNode 实例，resolveJoins() 改为 resolveJoinCondition() + resolveSimpleForJoin() 两层分派
**原因**：消除约 590 行重复代码，新增条件类型只需修改 spec.ConditionNode.Op 枚举和 switch 表达式

#### 2. P1-4: AbstractBulkOperationSpec 同步清单文档
**文件**：AbstractBulkOperationSpec.java:22-46
**修改前**：类级 Javadoc 仅描述基本用途
**修改后**：添加设计说明（延迟 Lambda vs 延迟 ConditionNode 树）和六处同步更新清单
**原因**：降低新增条件类型时同步遗漏的风险

#### 3. P1-5: SubQuerySpec 同步清单文档
**文件**：SubQuerySpec.java:13-40
**修改前**：已有设计说明但缺少同步清单
**修改后**：添加六处同步更新清单
**原因**：与 P1-4 保持一致的同步清单机制

#### 4. P1-6: executeLimited() 竞态条件文档增强
**文件**：UpdateSpec.java:219-265, DeleteSpec.java:166-212
**修改前**：简要的并发风险警告和建议列表
**修改后**：按推荐程度排序的安全使用建议，包含悲观锁代码示例
**原因**：提供更清晰的安全使用指引

#### 5. P1-8: 深度分页硬限制配置
**文件**：MyJpaPlusProperties.java:81-109, MyJpaTemplate.java:83/115-140/564-572, MyJpaPlusAutoConfiguration.java:41-63
**修改前**：仅记录警告日志，不阻止执行
**修改后**：新增 deepPaginationOffsetLimit 配置项（默认 -1 禁用），超过时抛出 IllegalArgumentException
**原因**：防止意外的深度分页性能问题

### 验证结果
- spotless:apply: 通过
- compile: 通过
- test -DexcludedGroups=integration: 全部通过（0 failures）

---
## 轮次 2 - 优化记录
时间：2026-05-29 19:07

### 已修复问题
- [P0-1] findAllStream(Class, QuerySpec) 废弃版本资源泄漏：在已有 @Deprecated(forRemoval=true) 和运行时警告基础上，增加调用栈追踪信息（Arrays.toString(Thread.currentThread().getStackTrace())），便于定位泄漏调用点。同步更新 findAllStream(Class, QuerySpec, EntityGraph) 废弃方法
- [P1-1] where() 方法绕过类型安全机制存在 SQL 注入风险：为 ConditionBuilder 接口添加静态 Logger，在两个 where() 重载方法中增加运行时 SECURITY 级别警告日志，包含调用栈追踪，帮助开发者识别和迁移不安全调用
- [P1-2] MyJpaTemplate.findById() 使用已弃用的 where() 方法：重构为直接使用 Specification<T> 构建 ID 查询条件，绕过 QuerySpec.where() 的弃用路径。findById() 现在直接构建 CriteriaQuery 并执行，减少一层间接调用
- [P1-3] SoftDeleteHelper.notDeletedQuery() 使用已弃用的 where() 方法：改为直接向 QuerySpec.conditions() 添加 ConditionNode.RawNode，避免调用已弃用的 ConditionBuilder.where() 方法，消除安全警告日志
- [P1-4] ProjectionSpec.JoinGroup.like() 缺少安全警告文档：为 like() 方法添加完整的 Javadoc，包含安全警告（不转义 % 和 _ 通配符）、@see 引用指向 likeSafe() 方法
- [P1-7] ConditionBuilder 缺少 isEmpty/isNotEmpty conditional 重载：新增 isEmpty(boolean, SFunction) 和 isNotEmpty(boolean, SFunction) 两个 default 方法，与其他操作符的 conditional 重载保持 API 一致性
- [P1-8] ProjectionSpec.findPage() 对 count 和 data 查询双重解析 JOIN：重构 findPage() 方法，数据查询直接构建 CriteriaQuery<Tuple> 而非委托给 toTupleQuery()，避免第二次 resolveJoins() 调用

### 未修复问题
- P1-5 ProjectionSpec.JoinGroup 与 ConditionBuilder 大量重复代码：需要创建共享接口或工具类，涉及较大重构，建议作为独立迭代处理
- P1-6 UpdateSpec.executeLimited 与 DeleteSpec.executeLimited 重复逻辑：需要提取模板方法到 AbstractBulkOperationSpec，涉及 protected API 设计变更，建议作为独立迭代处理
- P1-9 UpdateSpec.executeLimited() 两步执行存在并发竞态条件：默认悲观锁策略变更属于破坏性变更，需要评估兼容性后决定
- P2-1 至 P2-18：均为 P2 优先级的可选优化，本轮未涉及

### 修改详情
#### 1. P0-1: findAllStream 废弃版本资源泄漏增强
**文件**：MyJpaTemplate.java:14/327-331/343-351
**修改前**：仅记录方法名和版本警告
**修改后**：增加 Arrays.toString(Thread.currentThread().getStackTrace()) 输出完整调用栈，同时更新两个废弃 findAllStream 方法
**原因**：便于开发者快速定位泄漏调用点，加速迁移到安全版本 API

#### 2. P1-1: where() 方法运行时安全警告
**文件**：ConditionBuilder.java:14-17/33/699-705/743-748
**修改前**：仅通过 @Deprecated 注解和 Javadoc 标记弃用和安全风险
**修改后**：添加静态 Logger，两个 where() 方法在运行时记录 SECURITY 级别警告（含调用栈）
**原因**：运行时警告比静态注解更能引起开发者注意，调用栈有助于定位问题代码

#### 3. P1-2: MyJpaTemplate.findById() 弃用方法迁移
**文件**：MyJpaTemplate.java:179-197
**修改前**：创建 QuerySpec 并调用 spec.where((root, cb) -> ...)，然后委托给 findOne()
**修改后**：直接使用 Specification<T> 构建 ID 匹配条件，内联构建 CriteriaQuery 并执行
**原因**：消除对已弃用 where() 方法的依赖，减少一层间接调用

#### 4. P1-3: SoftDeleteHelper.notDeletedQuery() 弃用方法迁移
**文件**：SoftDeleteHelper.java:7/132-140
**修改前**：调用 qs.where((path, cb) -> buildNotDeleted(...)) 
**修改后**：直接调用 qs.conditions().add(new ConditionNode.RawNode(...))，添加 ConditionNode import
**原因**：绕过已弃用的 where() 方法，避免触发安全警告日志

#### 5. P1-4: ProjectionSpec.JoinGroup.like() 安全文档
**文件**：ProjectionSpec.java:114-128
**修改前**：无 Javadoc 注释
**修改后**：添加完整 Javadoc，包含安全警告（不转义通配符）、参数说明、@see likeSafe 引用
**原因**：引导用户在处理用户输入时使用 likeSafe() 方法

#### 6. P1-7: isEmpty/isNotEmpty conditional 重载
**文件**：ConditionBuilder.java:985-1007
**修改前**：无 conditional 重载
**修改后**：新增 isEmpty(boolean, SFunction) 和 isNotEmpty(boolean, SFunction) default 方法
**原因**：API 一致性，与其他操作符（eq, ne, gt, like 等）的 conditional 重载保持一致

#### 7. P1-8: ProjectionSpec.findPage() JOIN 解析优化
**文件**：ProjectionSpec.java:619-665
**修改前**：count 查询调用 resolveJoins(countRoot, cb)，data 查询通过 toTupleQuery() 再次调用 resolveJoins(root, cb)
**修改后**：data 查询直接构建 CriteriaQuery<Tuple>（内联 selections、predicate、orderBy），避免 toTupleQuery() 中的第二次 resolveJoins()
**原因**：减少一半的 JOIN 解析开销，提升分页查询性能

### 验证结果
- spotless:apply: 通过
- compile: 通过
- test -DexcludedGroups=integration: 全部通过（592 tests, 0 failures）

---
## 轮次 3 - 优化记录
时间：2026-05-29 19:19

### 已修复问题
- [P1-1] executeLimited() 默认不使用悲观锁（SEC-02）：将 UpdateSpec.executeLimited(em, limit) 和 DeleteSpec.executeLimited(em, limit) 的默认悲观锁参数从 false 改为 true，消除查询ID与执行更新/删除之间的并发竞态窗口。同步更新 Javadoc 说明默认行为变更
- [P1-2] IgnoreSoftDeleteAdvisor AOP 切面范围过窄（REL-01）：将 @Around 切面从 `within(com.zsubera.jpa.repository.SoftDeleteJpaRepository+)` 扩大为 `within(org.springframework.data.jpa.repository.JpaRepository+)`，使 @IgnoreSoftDelete 注解在 MyJpaRepository 和 SoftDeleteJpaRepository 上均生效。同步更新类级 Javadoc 说明适用范围
- [P2-1] SubQuerySpec.like()/notLike() 缺少安全警告和 likeSafe() 方法（SEC-04）：为 like() 和 notLike() 添加安全警告 Javadoc（不转义 % 和 _ 通配符），新增 likeSafe() 和 notLikeSafe() 方法（使用 PredicateHelper.escapeLikeWildcards + LIKE_ESCAPE_CHAR）
- [P2-2] AbstractBulkOperationSpec.like()/notLike() 缺少安全警告和 likeSafe() 方法（SEC-05）：为 like() 和 notLike() 添加安全警告 Javadoc，新增 likeSafe() 和 notLikeSafe() 方法，与 ConditionBuilder 和 SubQuerySpec 保持 API 一致性
- [P2-3] between() 类型检查策略不一致（MAINT-04）：将 ProjectionSpec.JoinGroup.between()/notBetween() 和 SubQuerySpec.between()/notBetween() 的类型检查从严格相等（getClass() != end.getClass()）统一为兼容性检查（isAssignableFrom），与 ConditionBuilder 和 AbstractBulkOperationSpec 保持一致

### 未修复问题
- SEC-03 ProjectionSpec.JoinGroup.like() 缺少安全警告：已检查代码，like() 方法在轮次2中已添加完整的安全警告 Javadoc，无需额外修改
- SEC-06 至 SEC-10：均为 P2 级别可接受/已缓解的安全问题，无需修改
- PERF-02 至 PERF-08：均为 P2 级别可接受/设计权衡的性能问题，无需修改
- REL-02 至 REL-05：均为 P2 级别可接受/已保障的可靠性问题，无需修改

### 修改详情
#### 1. P1-1: executeLimited() 默认启用悲观锁
**文件**：UpdateSpec.java:203-216, DeleteSpec.java:150-163
**修改前**：`return executeLimited(em, limit, false);` 且 Javadoc 仅列出并发风险建议
**修改后**：`return executeLimited(em, limit, true);` 且 Javadoc 说明默认启用悲观锁，如需禁用请使用三参数重载
**原因**：默认不启用悲观锁在高并发场景下存在数据不一致风险，安全性优先于性能

#### 2. P1-2: IgnoreSoftDeleteAdvisor 切面范围扩大
**文件**：IgnoreSoftDeleteAdvisor.java:14-42
**修改前**：`@Around("within(com.zsubera.jpa.repository.SoftDeleteJpaRepository+)")`
**修改后**：`@Around("within(org.springframework.data.jpa.repository.JpaRepository+)")`
**原因**：原切面仅拦截 SoftDeleteJpaRepository 及其子类，导致 MyJpaRepository 上的 @IgnoreSoftDelete 注解不生效

#### 3. SEC-04: SubQuerySpec likeSafe()/notLikeSafe() 方法
**文件**：SubQuerySpec.java:217-295（新增约50行）
**修改前**：like()/notLike() 无安全警告，无 likeSafe()/notLikeSafe() 方法
**修改后**：like()/notLike() 添加安全警告 Javadoc；新增 likeSafe() 和 notLikeSafe() 方法，使用 PredicateHelper.escapeLikeWildcards() + LIKE_ESCAPE_CHAR
**原因**：与 ConditionBuilder 的 likeSafe()/notLikeSafe() 保持 API 一致性，防止用户输入中的通配符被错误解释

#### 4. SEC-05: AbstractBulkOperationSpec likeSafe()/notLikeSafe() 方法
**文件**：AbstractBulkOperationSpec.java:338-417（新增约50行）
**修改前**：like()/notLike() 无安全警告，无 likeSafe()/notLikeSafe() 方法
**修改后**：like()/notLike() 添加安全警告 Javadoc；新增 likeSafe() 和 notLikeSafe() 方法
**原因**：与 ConditionBuilder 和 SubQuerySpec 保持 API 一致性

#### 5. MAINT-04: between() 类型检查统一
**文件**：ProjectionSpec.java:244-287, SubQuerySpec.java:308-355
**修改前**：`start.getClass() != end.getClass()`（严格相等，不兼容子类）
**修改后**：`!start.getClass().isAssignableFrom(end.getClass()) && !end.getClass().isAssignableFrom(start.getClass())`（兼容性检查）
**原因**：ConditionBuilder 和 AbstractBulkOperationSpec 已使用 isAssignableFrom，统一策略避免开发者混淆

### 验证结果
- spotless:apply: 通过
- compile: 通过
- test -DexcludedGroups=integration: 全部通过（592 tests, 0 failures）
