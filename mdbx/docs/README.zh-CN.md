# MDBX 规范索引

这个文件夹是 MDBX 项目的规范主目录。

MDBX 是一种本地优先的加密密码数据库格式与参考架构。
它的核心设计目标是：

- 本地优先
- 长期存档稳定
- 类 Git 的冲突防止与恢复能力
- 在网盘工作流中显著优于 KDBX 的同步表现
- 通过 Tiga 模型提供三档安全状态
- 以 `project` 为中心组织密码
- 原生支持附件
- 4ever And 4ever：旧 vault 长期可读，数据安全优先于一时方便

## RFC 风格分层

从现在开始，这套文档按更正式的 RFC 风格分层：

- `00` 到 `05`
  - 核心规范层
  - 定义术语、原则、产品模型、存储同步、安全和 RFC 文档约定

- `06` 到 `09`
  - 实现指导层
  - 定义 SQLite schema、任务拆分、当前实现完成计划等

- `10` 以后
  - 状态总结层
  - 定义当前完成度、CLI 接入、风险、下一步建议

## 阅读顺序

如果你是负责实现的低端模型，请严格按下面顺序阅读：

1. `00-agent-rules.md`
2. `01-product-spec.md`
3. `02-storage-sync-spec.md`
4. `03-security-spec.md`
5. `04-roadmap-acceptance.md`
6. `05-rfc-structure.zh-CN.md`

如果你是中文阅读者，请按下面顺序阅读：

1. `00-agent-rules.zh-CN.md`
2. `01-product-spec.zh-CN.md`
3. `02-storage-sync-spec.zh-CN.md`
4. `03-security-spec.zh-CN.md`
5. `04-roadmap-acceptance.zh-CN.md`
6. `05-rfc-structure.zh-CN.md`
7. `06-sqlite-schema-v1.zh-CN.md`
8. `07-low-end-model-task-breakdown.zh-CN.md`
9. `08-implementation-completion-plan.zh-CN.md`
10. `11-monica-pass-cli-development.zh-CN.md`

## 文档职责

- `00-agent-rules.md` / `00-agent-rules.zh-CN.md`
  - 给执行模型的规则文档。
  - 规定什么可以做，什么不可以擅自发明。

- `01-product-spec.md` / `01-product-spec.zh-CN.md`
  - 产品目标、核心约束、领域模型、对象模型、用户可见行为。

- `02-storage-sync-spec.md` / `02-storage-sync-spec.zh-CN.md`
  - 单文件格式、内部存储、增量写入、同步、冲突处理、附件存储规则。

- `03-security-spec.md` / `03-security-spec.zh-CN.md`
  - Tiga 模式、加密、密钥层级、内存处理、安全约束。

- `04-roadmap-acceptance.md` / `04-roadmap-acceptance.zh-CN.md`
  - 路线图、MVP 边界、验收标准、测试矩阵、任务拆解模板。

- `05-rfc-structure.zh-CN.md`
  - 说明这套 spec 如何按 RFC 风格组织，哪些文档是规范性的，哪些是实现指导性的。

- `06-sqlite-schema-v1.zh-CN.md`
  - SQLite 初版 schema 设计文档，定义项目、条目、附件、提交历史等表结构。

- `07-low-end-model-task-breakdown.zh-CN.md`
  - 专门给低端模型使用的任务拆分清单，可直接派活。

- `08-implementation-completion-plan.zh-CN.md`
  - 当前实现完成计划，记录已经完成的安全、同步、tracked mutation 和客户端接入事项。

- `11-monica-pass-cli-development.zh-CN.md`
  - Monica Pass CLI 开发与接入说明。

## 不可妥协原则

所有实现和所有设计决策都必须保留以下性质：

- 本地优先
- 长期可读、可迁移
- 前向兼容与后向兼容
- 4ever And 4ever：新版本必须能读旧 vault；旧实现应尽量保留未知但非关键的数据
- 数据安全优先于便利性
- 不依赖中心服务器
- 以 `project` 为中心组织密码
- 原生附件能力
- 比 KDBX 更安全的同步与冲突处理
- 比 KDBX 更好的网盘同步性能

## 核心词汇

- `vault`
  - 一个 MDBX 数据库文件。

- `project`
  - 一个现实世界中的账号、网站、应用、组织、身份集合、工作环境、服务集合的主容器。

- `entry`
  - `project` 内部的具体记录，例如登录项、笔记、卡片、令牌、密钥等。

- `attachment`
  - 归属于 `project` 或 `entry` 的文件或二进制内容。

- `tiga mode`
  - 存储值和 API 使用三档之一：`power`、`multi`、`sky`。
  - 兼容性显示名可以使用：`Power Type`、`Multi Type`、`Sky Type`。
  - 语义映射为：`power` = 最高防护，`multi` = 平衡默认，`sky` = 灵活便携但仍然安全。

- `oplog`
  - 追加式变更历史，用于同步与恢复。

- `snapshot`
  - 可用于恢复的压缩状态快照。

## 后续新增文档的写法要求

以后如果继续往这个目录里补规范，必须遵守：

- 使用 `MUST`、`MUST NOT`、`SHOULD`、`SHOULD NOT`、`MAY` 这种 RFC 风格词汇
- 每条核心要求都要可测试
- 规范性要求和建议性说明要分开
- 先写规则，再写示例
- 核心数据模型不能留下歧义

## 范围边界

这个目录定义的是规范和实现指导。
它不是生产代码目录。
生产代码必须遵循这里的规则，而不是重新定义这里的规则。
