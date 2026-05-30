# MDBX 实现补完计划

版本：`MDBX-1-DRAFT`

本文是执行文档。它把原始设计清单、现有 `mdbx/` Rust 实现、后续 Monica Android 接入放到同一张路线图里。后续开发应优先更新本文，再按本文推进代码。

## 1. 不可妥协原则

MDBX 必须坚持：

- 本地优先：所有创建、修改、删除、搜索、冲突处理都能离线完成。
- 4ever：格式公开、自描述、自校验、长期可读，新增能力必须保留兼容路径。
- project 优先：`project` 是一等主容器，entry 不允许退化成无归属平铺密码。
- attachment 一等化：附件从 v1 起进入 schema、历史、恢复和完整性模型。
- 类 Git 历史：每个本地变更必须产生 commit，commit DAG、device head、tombstone、conflict 都是核心格式的一部分。
- 因果冲突检测：不能只靠时间戳；同一秘密字段并发修改必须显式冲突。
- 安全默认：加密、认证、完整性验证失败必须失败，不能静默回退。
- 增量写入：常规小改动只更新相关行和追加历史，不能逻辑上重写全库。

## 2. 当前实现状态

`mdbx/` 已经具备这些基础：

- Rust workspace：`mdbx-core`、`mdbx-crypto`、`mdbx-storage`、`mdbx-sync`、`mdbx-cli`。
- SQLite + WAL + foreign key + secure_delete 基线。
- v1 schema 覆盖 `projects`、`entries`、`attachments`、`attachment_chunks`、`commits`、`commit_parents`、`device_heads`、`branches`、`object_versions`、`tombstones`、`snapshots`、`key_epochs`、`conflicts`。
- project、entry、attachment repo 支持创建、更新、软删除和 tombstone。
- attachment 支持 inline/chunked 内容、chunk hash、整体 hash、改名不改内容。
- Tiga 解析支持 entry > project > global。
- Unlock 支持密码/PIN/security key，Argon2id 参数按 Tiga 区分，密码做 Unicode NFC。
- KDBX import/export 具备基本回环。
- conflict detector 支持 JSON 三方字段合并。
- snapshot、recovery、benchmark harness 已有 MVP 骨架。

上一轮已补强：

- 新增 history/integrity 子密钥。
- commit `changed_object_ids_ct` 在有 keyring 时加密。
- commit `integrity_tag` 不再写 `X'00'`，改为 HMAC-SHA-256 或测试模式 SHA-256。
- 已解锁状态下 project/entry/attachment 解密失败不再吞掉。
- 增加密文篡改回归测试。
- recovery health check 已接入 commit integrity tag 重算校验，能发现 commit 元数据篡改。
- 已解锁 vault 仍兼容旧的明文 commit history payload。
- Tiga global/project/entry 写入接口已改为 tracked mutation，必须带 `CommitContext` 并产生 commit。
- entry 移动到其他 project、复制到其他 project 已成为一等 API，并产生可追溯 commit parent 链。
- entry 本地 create/update/move/copy/delete 会写入 `object_versions` 行快照，用于后续因果三方合并。
- sync apply 已能在非快进分叉场景消费 `mdbx-storage/state-v1`，对 entry payload 做 base/local/incoming 三方字段合并。
- entry 不同 payload 字段并发修改会自动产生 merge commit；同一 payload 字段并发修改会生成 unresolved conflict，并记录具体字段名。

## 3. 主要差距

### 3.1 格式与恢复

- `key_epochs.wrapped_epoch_key_ct` 仍有占位使用场景，需要区分测试占位与生产初始化。
- snapshot 当前主要恢复元数据，尚未完整覆盖附件内容 chunk 恢复策略。
- 缺少格式兼容测试：旧 reader、新 reader、未知字段保留、非关键扩展。

### 3.2 同步与冲突

- `mdbx-sync` 协商和 bundle 已有；Rust core 已支持 `mdbx-storage/state-v1` 对象状态 payload，并能在 fast-forward apply 时落地 `projects`、`entries`、`attachments`、`attachment_chunks`。
- entry payload 已接入字段级 base commit 查找、不同字段自动合并、同字段 unresolved conflict。
- entry conflict resolution 已有 storage core 写回路径：`local-wins`/`incoming-wins` 会生成 merge commit、推进 entry head、记录 object version，并标记 conflict resolved。
- 已补充“同一秘密字段并发修改必须冲突”和“不同字段并发修改自动合并”的 storage 回归测试。
- 已补充删除/修改并发回归：远端删除/本地修改会保留 tombstone 并产生 `deleted` conflict；本地删除/远端修改不会复活 entry。
- project/attachment 的字段级三方合并尚未完成，目前仍按对象级 conflict 处理。
- `custom/manual merge` 在 Rust storage core 已有显式 merged payload API；Android 合并编辑器尚未接入。

### 3.3 变更历史覆盖

- 部分维护/搜索/tag 类操作是否需要 commit 还未统一分类。
- repo mutation 已开始推进 `device_heads` 与 `branches.main`，但多数仍不是单事务包裹“写对象 + 写 commit + 更新 head”，崩溃窗口还需收窄。

### 3.4 性能与增量

- benchmark harness 已有，但还没有形成可发布报告。
- 缺少云盘 delta 观测：小修改、附件改名、附件替换、snapshot、compaction。
- 尚未引入 zstd/MessagePack 等二进制序列化/压缩策略。
- external-hash-ref 附件模式尚未实现。

### 3.5 安全

- 内存清零、明文驻留最小化、密钥文件、硬件密钥、生物识别封装仍需扩展。
- header/content 全局认证模型还不完整。
- 加密上下文 AAD 已覆盖字段级，但 commit/bundle/snapshot 的认证边界还需统一记录到规范。

### 3.6 Android 接入

- Android 侧当前不应把 MDBX 当作普通 Room 表的附属字段；最终应直接调用 MDBX 操作层。
- Android MDBX 管理页已有冲突队列和 local/incoming 解决入口；entry 冲突解决已收紧为写回 MDBX 历史，不再只改 `conflicts.resolution`。
- 新建、删除、移动、复制、分类、passkey、Bitwarden/KeePass 兼容路径都要映射到 MDBX project/entry/attachment/history。
- Android 管理页需要显示同步状态、device heads、unresolved conflicts、Tiga 状态、snapshot/health check。

## 4. 分阶段路线图

### P0：格式可信与恢复闭环

目标：让 `.mdbx` 文件能自校验、能发现历史篡改、能对损坏给出明确报告。

任务：

- recovery 验证 commit integrity tag。（已完成）
- health check 输出 commit tag mismatch、missing parent、dangling head、chunk mismatch。（已完成）
- snapshot 明确 payload 加密策略，拒绝已解锁状态下的篡改。
- 整理“生产初始化不得保留占位密文”的测试边界。

验收：

- `cargo fmt`
- `$env:CARGO_INCREMENTAL='0'; cargo test`
- 篡改 commit 字段会被 health check 报错。

### P1：所有 mutation 进入 commit 历史

目标：任何用户可见的新增、删除、移动、复制、Tiga 切换都能被同步和回放。

任务：

- Tiga setter 改为接收 `CommitContext` 或新增 tracked API。（已完成）
- repo mutation 使用事务包裹对象写入、commit 写入、head 更新。
- 分类/标签/搜索索引变更定义是否进入历史；用户可见语义必须进入历史。
- 移动 entry 到 project、复制 entry、恢复 tombstone 等操作形成一等 API。（entry 移动/复制已完成）

验收：

- 所有 repo 用户可见 mutation 都产生 commit。
- 崩溃注入不会留下“有 commit 无对象”或“有对象无 commit”的健康状态。

### P2：同步 apply 与冲突闭环

目标：多设备离线编辑后，通过文件/网盘同步可以安全合并。

任务：

- 实现 serialized commit 导出/导入。（CLI bundle 与 storage core apply 基础路径已完成）
- 构建 base commit 查找和 fast-forward 判断。（对象级 fast-forward 已完成；entry payload 字段级 base 已完成）
- 将 incoming commit apply 到 storage，必要时调用 conflict detector。（project/entry/attachment/chunk 状态落地已完成；非快进 state payload 已开始进入 apply 流程）
- 同字段并发秘密修改生成 conflict；不同字段安全合并。（entry payload 已完成）
- conflict resolve 写回 commit，并更新 head。（entry `local-wins`/`incoming-wins`/`custom payload` 已完成）

验收：

- A/B 设备同字段并发修改产生 unresolved conflict。（storage 回归已覆盖）
- A/B 设备不同字段并发修改自动合并。（storage 回归已覆盖）
- entry conflict 选择 local-wins/incoming-wins 后会写入 merge commit 并更新 head。（storage 回归已覆盖）
- entry conflict custom payload 会写入 merge commit、替换 payload、记录 object version，并标记为 custom。（storage 回归已覆盖）
- 删除与修改并发不会误复活 tombstone。（storage 回归已覆盖）

### P3：附件与性能完成

目标：明显优于 KDBX 的小修改同步和大附件行为。

任务：

- external-hash-ref 模式。
- 附件 blob 内容寻址目录或可插拔 blob provider。
- metadata-only 更新不触碰 chunk。
- benchmark 输出 delta size 和时延报告。
- 可选 zstd 压缩和二进制 payload 序列化。

验收：

- 小 entry 修改保存目标 `<100 ms`。
- 附件改名不改 chunk。
- 大附件不导致普通 entry 修改产生大 delta。

### P4：迁移与长期兼容

目标：KDBX 用户可迁移，MDBX 格式可长期维护。

任务：

- KDBX import/export 覆盖 passkey、totp、ssh key、custom fields、attachments。
- RFC 结构补齐：header、schema、crypto、commit、sync、snapshot、extensions。
- 兼容性测试矩阵落地。
- CLI 增加 `health`、`import-kdbx`、`export-kdbx`、`snapshot`、`sync-bundle`。

验收：

- import/export roundtrip 报告。
- 旧 vault 打开测试。
- 未知非关键扩展保留测试。

### P5：Monica Android 接入

目标：Android 对 MDBX 的所有用户操作直接落到 MDBX 文件和 MDBX 历史。

任务：

- Android 定义 `MdbxRepository` 边界：所有新建、删除、移动、复制、分类、passkey 操作只通过它进入 MDBX。
- 新建页面、移动/复制页面、新建分类菜单支持选择 MDBX vault/project。
- 密码、TOTP、passkey、SSH、API token 映射到 MDBX entry type。
- Bitwarden/KeePass 兼容字段进入 payload 映射表。
- MDBX 管理页增加 health check、snapshot、device heads、sync status、conflict list、resolve action。
- 离线操作立即本地生效；同步后依据 commit DAG 合并。

验收：

- A 设备删除 entry，B 设备同步后消失；离线时 B 保留本地状态，联网/同步后按 commit 合并。
- 同一字段并发修改在 Android 管理页出现冲突处理入口。
- Android 选择 entry conflict 的“使用本地/使用传入”会写 merge commit、推进 entry head 并刷新列表；“标记解决”不得静默吞掉 entry 冲突。
- passkey 可存储并导入/导出到 Bitwarden/KeePass 兼容结构。

## 5. 工作规则

- 每个阶段先写测试或 health check，再改实现。
- 不允许引入“为了 UI 方便绕过 MDBX repo”的写入路径。
- 不允许把认证失败降级成 warning。
- 不允许把 Android Room 当 MDBX 的真源；Room 最多是索引/cache。
- 每个合并切片必须说明验收命令。

## 6. 当前起步切片

当前 P2 entry 字段级合并切片已完成：

- `object_versions` 存储 entry commit 快照。
- 非快进 sync apply 会消费 state payload，并对 entry payload 做三方合并。
- 不同字段并发修改自动合并并产生双 parent merge commit。
- 同字段并发修改生成 unresolved conflict。
- entry conflict 支持 `local-wins`/`incoming-wins` 写回 merge commit 并推进 head。
- Rust core 支持 `custom` merged payload 写回，用于后续 Android 手动合并编辑器。
- 删除/修改并发会生成 `deleted` conflict：远端 tombstone 不丢，本地删除不会被远端修改复活。
- 已通过 `cargo test -p mdbx-storage entry::tests` 与 `cargo test -p mdbx-storage sync_apply`。

下一刀建议：

- Android custom/manual merge editor 接入 Rust core/本地 store 的 explicit merged payload API。
- project/attachment 字段级 merge 与 conflict resolve。
- Android `MdbxRepository` 最小 JNI/FFI 操作边界，禁止 Room 成为 MDBX 真源；同时修复现有 Android 编译断引用后恢复 Gradle 验证。
