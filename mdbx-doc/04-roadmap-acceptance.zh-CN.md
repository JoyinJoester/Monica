# MDBX 路线图与验收规范

版本：`MDBX-1-DRAFT`

本文定义先做什么、后做什么、如何判断完成、以及如何把工作拆给低端模型。

## 1. 交付策略

MDBX 必须按分层顺序构建。
不要从 UI 优先开始。
不要先做插件系统。
不要先做过度超前的同步基础设施。

正确顺序：

1. 数据模型与文件格式
2. 存储引擎与加密持久化
3. 历史与冲突检测
4. 导入导出桥接
5. 基准测试与恢复测试
6. 参考 UI 与客户端集成

## 2. MVP 范围

MVP 必须包含：

- 单一 `.mdbx` vault 文件
- SQLite 作为内部存储基线
- 加密的 project 记录
- 加密的 entry 记录
- 附件 schema，以及至少一种可工作的附件存储模式
- Tiga 三模式支持
- 追加友好的历史记录
- 基础冲突检测元数据
- snapshot 或等效恢复检查点
- KDBX 导入
- KDBX 导出或至少明确的导出桥接方案
- 与 KDBX 对比的 benchmark harness
- 一个参考客户端实现

## 3. 后续阶段

后续版本最好加入：

- 更完整的合并 UI
- 更强的 CRDT 文本处理
- 大附件分块优化
- 外部附件 hash 引用模式
- 多平台客户端
- 浏览器扩展自动填充集成
- 插件沙箱
- 审计工具

## 4. 必需工作流分组

所有 roadmap 或 issue 拆分最好映射到这些轨道：

- `format`
- `storage`
- `sync`
- `security`
- `migration`
- `benchmarks`
- `reference-client`
- `docs`

## 5. 低端模型任务模板

给较弱模型分配任务时，最好固定使用这个模板。

### Goal

明确写出必须产出的目标。

### Inputs

列出本任务必须遵循的 `mdbx-doc/` 规范文件。

### Constraints

列出不可协商的限制。

### Deliverables

列出明确的输出文件、模块或结果。

### Acceptance Criteria

列出必须通过的测试与条件。

### Forbidden Changes

列出不允许擅自修改或发明的内容。

## 6. 示例任务拆解

### Task A: schema draft

目标：

- 定义第一版 SQLite schema，覆盖 projects、entries、attachments、commits、tombstones

验收：

- schema 包含 `projects`
- schema 包含 `attachments`
- `entries` 引用 `project_id`
- 附件归属与 hash 字段存在
- 没有任何表结构把密码当成无 project 的平铺列表

### Task B: basic encrypted storage layer

目标：

- 为 project 和 entry 实现记录级加密与认证持久化

验收：

- 能创建 vault
- 能创建 project
- 能在 project 下创建 entry
- 重开后能正确读出两者
- 篡改后能检测认证失败

### Task C: attachment MVP

目标：

- 实现附件元数据以及一种小文件内嵌附件模式

验收：

- 附件能添加到 project
- 重开数据库后附件元数据仍在
- 完整性不匹配时能被检测
- 修改 project 元数据时不会不必要地重写附件载荷逻辑

## 7. Benchmark 要求

MDBX 必须输出以下基准证据：

- 小修改保存时延
- vault 打开时延
- 搜索时延
- 添加附件时延
- 小元数据修改后的同步 delta 大小
- 附件改名后的同步 delta 大小
- 附件内容替换后的同步 delta 大小

在可行情况下，MDBX 必须与 KDBX 进行对照。

## 8. 恢复测试矩阵

验证计划必须包括：

- 保存中断
- 同步中断
- 元数据页损坏
- 附件 chunk 损坏
- device head 过期
- 同一秘密字段并发修改
- 不同字段并发修改
- project 删除与恢复
- attachment 删除与恢复

## 9. 兼容性测试矩阵

验证计划最好包括：

- 旧版 reader 能打开带非关键扩展的新 vault
- 新版 reader 能打开旧 vault
- 重写过程中未知字段被保留
- KDBX 导入回环基本正确
- 在支持的前提下，附件元数据可在导入导出中保留

## 10. 规范阶段完成标准

只有在以下条件全部满足时，才能认为 spec 阶段完成：

- 术语稳定
- `project` 模型被明确为强制主模型
- 附件模型被明确为强制一等能力
- 安全模式定义完整
- 存储与同步规则完整
- 路线图与验收规则完整
- 文档足够清晰，另一模型无需自行发明核心架构即可实现

## 11. 拒收规则

以下 roadmap 或任务计划不可接受：

- 把 `project` 模型推迟到后面再做
- 把 `attachment` 模型推迟到后面再做
- 没有 benchmark 计划却声称性能更好
- 忽略恢复测试
- 没有说明 KDBX 迁移路径
