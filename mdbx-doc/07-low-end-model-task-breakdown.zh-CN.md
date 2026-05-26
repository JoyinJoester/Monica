# MDBX 低端模型任务拆分清单

版本：`MDBX-1-DRAFT`

本文用于直接给低端模型派活。每个任务都尽量做成低歧义、低自由发挥、可独立验收。

## 1. 使用方法

给低端模型分配任务时，必须附上：

- 当前任务编号
- 必读 spec 文件
- 不可违反的约束
- 验收标准

推荐固定必读文件：

- `00-agent-rules.zh-CN.md`
- `01-product-spec.zh-CN.md`
- `02-storage-sync-spec.zh-CN.md`
- `03-security-spec.zh-CN.md`
- `06-sqlite-schema-v1.zh-CN.md`

## 2. 总体拆分原则

- 每个任务只解决一个主问题
- 每个任务必须可测试
- 每个任务完成后不依赖隐性上下文
- 先数据结构，后读写，后导入导出，后 UI

## 3. 任务清单

### Task 01: 建立 SQLite v1 schema

目标：

- 创建 `vault_meta`、`projects`、`entries`、`attachments`、`attachment_chunks`、`commits`、`commit_parents`、`device_heads`、`tombstones`、`snapshots`、`key_epochs`

必读：

- `01-product-spec.zh-CN.md`
- `02-storage-sync-spec.zh-CN.md`
- `06-sqlite-schema-v1.zh-CN.md`

禁止：

- 删除 `projects`
- 删除 `attachments`
- 把 entry 做成无 project 归属

验收：

- schema 可创建
- `entries.project_id` 存在
- `attachments.project_id` 存在
- `attachments.entry_id` 可空

### Task 02: 实现 vault 初始化

目标：

- 创建新 vault 时写入 `vault_meta`、默认 branch、初始 key epoch、初始 commit

验收：

- 新 vault 可创建
- 默认 Tiga 模式可记录
- 初始 commit 存在

### Task 03: 实现 project 基础 CRUD

目标：

- 支持创建、读取、更新、软删除 project

禁止：

- project 删除时直接物理清除所有下游数据

验收：

- project 可创建和更新
- 删除生成 tombstone 或删除标记
- 普通更新不会触碰无关附件内容

### Task 04: 实现 entry 基础 CRUD

目标：

- 支持在 project 下创建、读取、更新、软删除 entry

验收：

- entry 必须归属于 project
- 秘密载荷进入加密 payload
- 删除不会破坏 commit 历史

### Task 05: 实现 commit 与 parent 关系

目标：

- 每次 project 或 entry 更新都追加 commit
- 支持单父和多父 commit

验收：

- `(device_id, local_seq)` 唯一
- commit 可追溯父节点
- merge commit 可表示双父或多父

### Task 06: 实现 device head 与 branch head

目标：

- 为每台设备记录 head
- 支持至少一个默认 branch

验收：

- device head 可更新
- branch head 可追随 commit

### Task 07: 实现附件元数据 MVP

目标：

- 支持创建 attachment 元数据
- 支持 project 级和 entry 级附件

验收：

- 可新增 attachment
- attachment 可绑定到 project
- attachment 可选绑定到 entry
- 改名只改元数据

### Task 08: 实现小附件内嵌模式

目标：

- 支持 `embedded-inline`

验收：

- 小附件可写入
- 重开 vault 后可读
- 完整性错误能检测

### Task 09: 实现大附件分块模式骨架

目标：

- 建立 `attachment_chunks` 写入与读取框架

验收：

- 同一 attachment 可写多个 chunk
- chunk 顺序连续
- 元数据修改不重写 chunk 内容

### Task 10: 实现 tombstone 机制

目标：

- project、entry、attachment 删除时写 tombstone

验收：

- tombstone 可查询
- 删除对象不会被并发同步轻易复活

### Task 11: 实现 snapshot MVP

目标：

- 生成可恢复的 snapshot

验收：

- snapshot 可生成
- 可从 snapshot 重建 projects / entries / attachments 元数据

### Task 12: 实现基础冲突检测

目标：

- 识别同对象并发修改
- 区分安全自动合并与必须报冲突的情况

验收：

- 同一秘密字段冲突不会静默合并
- 不同字段修改可在安全时合并

### Task 13: 实现 KDBX 导入器骨架

目标：

- 把 KDBX 条目导入为 project + entry + attachment

必读：

- `08-kdbx-to-mdbx-mapping.zh-CN.md`

验收：

- 每个 KDBX 逻辑项转为一个 project
- 主密码数据进入 entry
- 附件进入 attachment

### Task 14: 实现 KDBX 导出器骨架

目标：

- 支持把 MDBX 导出回 KDBX 兼容结构

验收：

- project 可回收敛为单个 KDBX 逻辑项
- 常见字段不丢失

### Task 15: 实现 Tiga 参数切换

目标：

- 全局、project、entry 三级切换

必读：

- `03-security-spec.zh-CN.md`
- `09-tiga-parameters.zh-CN.md`

验收：

- 默认模式可生效
- project override 可生效
- entry override 优先级最高

### Task 16: 实现多种解锁方式

目标：

- 支持 `PIN`、`密码`、`安全密钥` 三类数据库解锁方式
- 明确区分用户可见解锁方式与底层真实 vault 密钥模型

必读：

- `03-security-spec.zh-CN.md`

禁止：

- 把短 `PIN` 直接当作唯一真实 vault 主秘密
- 把密码错误地限制为 ASCII
- 把安全密钥实现成必须联网的云依赖

验收：

- `PIN` 可作为快速解锁方式使用
- `密码` 输入支持 Unicode
- `密码` 输入支持中文
- `安全密钥` 可作为受支持的解锁方式之一
- 不同解锁方式不会破坏本地优先原则

### Task 17: 实现搜索与索引 MVP

目标：

- 支持 project 标题、标签、entry 类型、更新时间搜索

验收：

- 大量数据下查询明显快于全表扫描

### Task 18: 实现恢复测试与损坏测试

目标：

- 覆盖中断写入、损坏页、损坏 chunk、旧 head、并发冲突

验收：

- 测试脚本可重复运行
- 能输出失败原因

### Task 19: 实现 benchmark harness

目标：

- 跟踪保存、打开、搜索、附件写入、同步 delta 指标

验收：

- 输出可比对的 benchmark 结果
- 可与 KDBX 做对照

## 4. 优先顺序

低端模型执行时按这个顺序：

1. Task 01
2. Task 02
3. Task 03
4. Task 04
5. Task 05
6. Task 07
7. Task 10
8. Task 11
9. Task 12
10. Task 13
11. Task 15
12. Task 16
13. Task 17
14. Task 18
15. Task 19

## 5. 通用拒收规则

以下结果一律拒收：

- 把密码重新做回平铺 entry 模型
- 附件没有单独 schema
- 小修改默认重写大附件内容
- 冲突检测只靠时间戳
- 没有 tombstone
- 没有 commit 历史
