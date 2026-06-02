# MDBX

语言：[简体中文](README.md) | [English](README.en.md)

这个目录包含 Monica MDBX 的 Rust workspace 和实现接入说明。

MDBX 是 Monica 的本地优先加密 vault 格式。它的目标不是简单替代某张密码表，而是提供长期可维护的本地数据库、类 Git 的逻辑历史、同步冲突处理、原生附件、快照恢复和 Tiga 安全模式。

规范性文档在 `docs/`。

MDBX 的准则是 **4ever And 4ever**：旧 vault 必须长期可读，新增能力必须尽量保留兼容路径，数据安全永远优先于一时方便。

## Workspace 结构

- `crates/mdbx-core`
  - 核心领域类型。
- `crates/mdbx-crypto`
  - 加密、KDF、密钥材料处理。
- `crates/mdbx-sync`
  - 同步 payload 和 object payload 模型。
- `crates/mdbx-storage`
  - SQLite schema、vault 初始化、repo、搜索、快照、冲突、恢复、sync state。
- `crates/mdbx-cli`
  - 本地测试和开发用 CLI。
- crate-local `tests/`
  - 兼容性、crypto vector、并发、恢复场景跟随各 crate 放置。

## 本目录文档

- `CLIENT_INTEGRATION_GUIDE.md`
  - 英文版客户端接入指南。
- `CLIENT_INTEGRATION_GUIDE.zh-CN.md`
  - 中文版客户端接入指南。

## 规范文档

修改存储行为前必须先读 `docs/`：

- `docs/README.md` / `docs/README.zh-CN.md`
- `docs/01-product-spec.zh-CN.md`
- `docs/02-storage-sync-spec.zh-CN.md`
- `docs/03-security-spec.zh-CN.md`
- `docs/06-sqlite-schema-v1.zh-CN.md`

`docs/` 定义格式和产品约束；Rust workspace 负责实现这些约束，并提供实际客户端接入说明。

## 客户端支持等级

MDBX 支持需要明确标注：

- **只读支持**
  - 打开、解锁 vault。
  - 显示文件夹、条目、附件元数据。
  - 不写表、不写 commit、不写 tombstone、不写 snapshot、不处理 conflict。
- **基础读写支持**
  - 创建和编辑条目、文件夹。
  - 正确维护 commit、object version、tombstone、snapshot、branch head、device head。
- **同步支持**
  - 合并 commit DAG，保留 tombstone，检测 conflict，安全应用 sync state。
- **完整 Monica 兼容支持**
  - 提供必备管理面板、诊断、快照结构预览、字段级历史、支持 MDBX 文件夹的新建/移动/复制流程。

完整清单见 `CLIENT_INTEGRATION_GUIDE.zh-CN.md`。

## 必备用户管理面板

完整客户端应该提供：

- MDBX 格式管理首页
- 数据库详情页
- 文件夹 / 结构管理
- 移动 / 复制目标选择
- 冲突管理
- 提交历史
- 快照
- 快照结构预览
- 诊断 / 维护
- 解锁与安全

点击“MDBX 格式管理”应该进入 MDBX 管理首页，不应该自动进入上次打开的某个数据库详情页。

普通用户界面不应该暴露 raw sync bundle、benchmark、底层 chunk 调试等开发者工具。这些工具可以放在开发者模式。

## 开发命令

在本目录执行：

```powershell
cargo test
```

本地开发 CLI：

```powershell
cargo run -p mdbx-cli -- --help
```

## 实现规则

除非正在修改 storage 层本身，否则客户端代码不要绕过 repo/storage API 直接写底层表。

兼容性和恢复能力属于实现要求，不是后续润色项。新增加密 envelope、表、索引、解锁方式和 Tiga 策略时，除非存在必须处理的关键安全问题，否则必须保持旧 vault 可读。

客户端代码不应该直接写：

- `commits`
- `commit_parents`
- `object_versions`
- `tombstones`
- `snapshots`
- `key_epochs`
- `conflicts`
- `device_heads`
- `branches`

批量用户操作通常应该生成一个用户级 commit，而不是每个对象一个 commit。

## 兼容性检查

宣称完整支持前，客户端至少应该确认：

- 能打开 Monica 创建的 MDBX vault。
- 能创建嵌套文件夹，并把嵌套文件夹作为目标。
- 批量移动、复制、删除会合并成用户级 commit。
- tombstone 能防止删除对象被复活。
- 两个客户端读取同一 vault 数量一致。
- 能检测并展示冲突。
- 能创建快照，回滚快照需要二次确认。
- 诊断页能显示同步、健康、历史、tombstone、附件、dangling head 状态。
