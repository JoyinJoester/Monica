# Monica Pass CLI 开发文档

版本：`2026-05-24`

本文是 Monica Pass CLI 的开发输入文档。目标是在仓库根目录新增一个独立 Rust 项目 `monica-pass-cli/`，把现有 MDBX Rust workspace 与 Monica Android 的 MDBX 数据模型作为主要数据库能力来源，开发成一个生产可用、安全至上的命令行工具。

## 1. 目标

开发一个 Rust 编写的单文件可执行工具，暂定二进制名为 `monica-pass`。

核心用户：

- 普通用户：在终端里交互式管理 Monica Pass / MDBX 密码库。
- AI Agent：通过受限、可审计、基于 SSH 密钥认证的 JSON 接口调用功能。

安全原则：

- AI Agent 不得直接读取或写入 `.mdbx` 数据库文件。
- AI Agent 不得获得数据库主密码、解锁密钥、明文密码、TOTP seed、Passkey 私钥、附件明文。
- Agent 能拿到的是受策略限制的操作结果，或一次性授权动作，不是无限制数据库访问能力。
- 人类用户可在交互式模式中显式查看秘密；Agent JSON 模式默认只读且不返回秘密明文。

## 2. 已有代码现状

根目录已有 Rust workspace：

```text
mdbx/
  Cargo.toml
  crates/
    mdbx-core
    mdbx-crypto
    mdbx-storage
    mdbx-sync
    mdbx-cli
```

现有 `mdbx-cli` 已经是一个可运行原型，二进制名为 `mdbx`，具备：

- `init`
- `unlock`
- `project list/create/get/edit/delete`
- `entry list/create/get/edit/delete/move/copy`
- `attach list/add/get/info/rename/verify/delete`
- `snapshot create/list/restore`
- `search`
- `sync bundle/apply`

现有 Rust 存储层具备：

- SQLite + bundled rusqlite。
- WAL、foreign_keys、secure_delete、busy_timeout。
- `vault_meta`、`projects`、`entries`、`attachments`、`attachment_chunks`、`commits`、`commit_parents`、`device_heads`、`branches`、`object_versions`、`tombstones`、`snapshots`、`key_epochs`、`conflicts`、`unlock_methods`。
- Argon2id + HKDF-SHA-256 + XChaCha20-Poly1305。
- 字段级 AAD：`object_type:object_id:field_name`。
- PIN / 密码 / security_key 底层支持。
- 中文密码 NFC 规范化测试。
- commit DAG、object_versions、snapshot、sync apply、conflict repo。

Android 侧 MDBX 实现位于：

```text
Monica for Android/app/src/main/java/takagi/ru/monica/repository/MdbxVaultStore.kt
Monica for Android/app/src/main/java/takagi/ru/monica/repository/MdbxVaultCrypto.kt
Monica for Android/app/src/main/java/takagi/ru/monica/data/LocalMdbxDatabase.kt
```

Android 侧当前 MDBX 是 SQLite 文件，schema 与 Rust 规范相近但不完全一致：

- Android 额外有 `devices`、`folders`、`object_index`、`oplog`、`sync_bundles`、`crypto_contexts`、`mdbx_benchmarks`。
- Android `vault_meta` 有 `credential_salt`、`credential_verifier`、`credential_kdf_profile`、`unlock_methods`、`key_file_name`、`key_file_fingerprint`。
- Android 当前加密实现是 PBKDF2-SHA256 + AES-256-GCM 包装 epoch key，字段前缀为 `mdbx:v1:`。
- Rust 规范实现是 Argon2id + XChaCha20-Poly1305 + `unlock_methods` 表 + vault key/keyring。

结论：Monica Pass CLI 必须单独建项目，不直接改造现有 `mdbx-cli`。第一版应优先把 Rust `mdbx` workspace 中的 `mdbx-core`、`mdbx-crypto`、`mdbx-storage`、`mdbx-sync` 作为库依赖或内部适配层复用；`mdbx-cli` 只作为行为参考和测试参考。CLI 还需要兼容 Android 当前测试 MDBX 文件，否则会出现 CLI 创建的库和 Android 创建的库互相打不开的问题。

项目边界：

- 新项目目录：`monica-pass-cli/`。
- 新二进制：`monica-pass`。
- 不在第一阶段重构或替换 `mdbx/crates/mdbx-cli`。
- 可以复用 `mdbx` workspace 的库 crate、schema、crypto、repo、sync 逻辑。
- 如果现有能力只存在于 `mdbx-cli` 的命令层，应先抽到可复用库或在新项目中重新实现薄适配，避免让 Monica Pass CLI 依赖旧 CLI 的内部命令结构。

## 3. 产品边界

必须实现的顶层命令：

```text
unlock
search
get
add
edit
delete
totp
sync
```

推荐额外命令：

```text
init
status
lock
session
agent
policy
audit
snapshot
doctor
```

命令形态建议：

```text
monica-pass unlock --vault <alias-or-path>
monica-pass search <query>
monica-pass get <id> [--field username|url|notes] [--reveal]
monica-pass add login --title ... --username ... --password-stdin
monica-pass edit <id> --title ... --set username=...
monica-pass delete <id>
monica-pass totp <id> [--copy | --json]
monica-pass sync pull|push|bundle|apply|status
monica-pass agent authorize --public-key ~/.ssh/id_ed25519.pub --policy readonly.json
monica-pass agent serve --json
```

## 4. 安全架构

### 4.1 两种执行身份

`Human mode`：

- 可交互输入主密码/PIN/安全密钥。
- 可显式使用 `--reveal` 查看秘密。
- 可写入数据库。
- 默认输出适合终端阅读。

`Agent mode`：

- 只能通过 JSON request/response。
- 必须通过 SSH key 签名完成挑战认证。
- 必须绑定策略文件。
- 默认只读。
- 默认不返回明文密码、TOTP seed、Passkey 私钥、附件内容。
- 所有请求写入审计日志。

### 4.2 SSH 密钥认证机制

Agent 不直接持有数据库密码。认证流程：

1. Agent 调用 `agent.challenge`。
2. CLI 返回 nonce、session_id、expires_at、allowed_algorithms。
3. Agent 用其 SSH 私钥对规范化 challenge 字符串签名。
4. Agent 调用 `agent.authenticate`，提交 public_key_id、signature、requested_scope。
5. CLI 使用已登记 public key 验签。
6. CLI 根据 policy 生成短期 session token。

Challenge 字符串必须包含：

```text
monica-pass-agent-v1
nonce=<base64url>
session_id=<uuid>
created_at=<unix_ms>
vault_alias=<alias>
requested_scope=<scope>
```

会话令牌要求：

- 128 位以上随机值。
- 默认 5 分钟过期。
- 可配置最大 30 分钟。
- 绑定 public key fingerprint、vault alias、policy hash、process/session metadata。
- 存储在用户目录的受限权限文件中，不能写入项目目录。

建议本地路径：

```text
~/.config/monica-pass/config.toml
~/.config/monica-pass/agents.d/*.toml
~/.local/state/monica-pass/sessions.json
~/.local/state/monica-pass/audit.log
```

Windows 对应：

```text
%APPDATA%\MonicaPass\config.toml
%LOCALAPPDATA%\MonicaPass\sessions.json
%LOCALAPPDATA%\MonicaPass\audit.log
```

### 4.3 权限模型

策略文件字段：

```json
{
  "agent_name": "openclaw",
  "vault_aliases": ["work"],
  "mode": "readonly",
  "allow": {
    "search": true,
    "get_metadata": true,
    "get_secret": false,
    "totp_code": true,
    "add": false,
    "edit": false,
    "delete": false,
    "sync": false
  },
  "filters": {
    "project_tags_any": ["agent-ok"],
    "entry_types": ["login", "totp", "ssh-key", "api-token"]
  },
  "limits": {
    "max_results": 20,
    "session_seconds": 300,
    "totp_per_minute": 10
  }
}
```

Agent `get` 的默认返回必须是脱敏数据：

```json
{
  "id": "entry-id",
  "type": "login",
  "title": "GitHub",
  "username": "alice",
  "url": "https://github.com",
  "has_password": true,
  "has_totp": true
}
```

禁止默认返回：

- `password`
- `password_plain`
- `secret`
- `totp_seed`
- `ssh_private_key`
- `privateKeyAlias`
- attachment bytes

如果确实需要 Agent 使用密码，必须走显式能力而不是返回明文。例如：

- `copy_secret_to_clipboard`：写入系统剪贴板，带短过期提示。
- `spawn_with_secret`：CLI 启动子进程并注入环境变量，Agent 只看到子进程退出码。
- `fill_template`：CLI 在本地生成文件但不把秘密回传给 Agent。

第一版建议不实现 `get_secret`，只实现 `totp_code` 作为受限例外，因为 TOTP 是短期验证码；仍需审计和速率限制。

## 5. JSON Agent 接口

传输形式：

- `monica-pass agent serve --stdio`
- 每行一个 JSON request，每行一个 JSON response。
- 不使用网络监听，避免暴露本地端口。

请求示例：

```json
{"id":"1","method":"agent.challenge","params":{"vault":"work","scope":"readonly"}}
{"id":"2","method":"agent.authenticate","params":{"session_id":"...","public_key":"ssh-ed25519 ...","signature":"..."}}
{"id":"3","method":"search","params":{"query":"github","limit":5},"session":"..."}
{"id":"4","method":"get","params":{"id":"entry-id"},"session":"..."}
{"id":"5","method":"totp","params":{"id":"entry-id"},"session":"..."}
```

响应示例：

```json
{"id":"3","ok":true,"result":[{"id":"entry-id","title":"GitHub","type":"login","has_password":true}]}
{"id":"4","ok":true,"result":{"id":"entry-id","title":"GitHub","username":"alice","has_password":true}}
{"id":"5","ok":true,"result":{"code":"123456","remaining_seconds":21}}
```

错误格式：

```json
{"id":"x","ok":false,"error":{"code":"permission_denied","message":"policy denies get_secret"}}
```

## 6. 数据模型映射

MDBX `entry_type` 必须覆盖 Monica Android：

- `login`
- `note`
- `totp`
- `card`
- `document-ref`
- `passkey`
- `ssh-key`
- `api-token`

登录 payload 建议：

```json
{
  "kind": "password",
  "username": "alice",
  "password": "secret",
  "website": "https://example.com",
  "notes": "",
  "login_type": "PASSWORD",
  "authenticator_key": "",
  "passkey_bindings": ""
}
```

TOTP payload 必须兼容 Android `TotpData`：

```json
{
  "kind": "totp",
  "secret": "BASE32",
  "issuer": "GitHub",
  "accountName": "alice",
  "period": 30,
  "digits": 6,
  "algorithm": "SHA1",
  "otpType": "TOTP",
  "counter": 0,
  "steamFingerprint": "",
  "steamDeviceId": "",
  "steamSerialNumber": "",
  "steamSharedSecretBase64": "",
  "steamRevocationCode": "",
  "steamIdentitySecret": "",
  "steamTokenGid": "",
  "steamRawJson": ""
}
```

TOTP 算法必须支持：

- TOTP RFC 6238
- HOTP RFC 4226
- Steam Guard 5 字符码
- Yandex OTP
- Mobile-OTP

第一版至少实现 TOTP/HOTP/Steam；Yandex/MOTP 可作为阶段 2。

## 7. 与 Android MDBX 的兼容策略

当前存在两套接近但不完全一致的实现：

Rust：

- `unlock_methods` 表。
- Argon2id + XChaCha20-Poly1305。
- `object_versions.snapshot_ct`。
- 没有 Android 的 `folders/object_index/oplog/sync_bundles/crypto_contexts/mdbx_benchmarks`。

Android：

- `vault_meta` 中保存 credential salt/verifier/profile。
- `key_epochs.wrapped_epoch_key_ct`。
- PBKDF2-SHA256 + AES-GCM。
- 字段密文前缀 `mdbx:v1:...`。
- `object_versions` 是展开列而非 snapshot blob。
- 额外 oplog/sync bundle/diagnostics 表。

CLI 阶段 1 必须选择一个兼容策略：

1. `Native Rust mode`：新建 CLI vault 使用 Rust schema，最快可完成 CLI。
2. `Android compatibility mode`：读写 Android 当前 MDBX 文件。
3. `Dual mode`：打开时检测 schema，走不同 adapter。

生产目标要求正确接入仓库根目录下的 Rust workspace 和 `docs/` 规范目录，因此最终必须做 `Dual mode`。实现顺序建议：

- 先保留 Rust native mode。
- 增加 schema detector。
- 增加 Android compatibility reader/writer。
- 最终让 Android 与 Rust 统一到同一 schema/crypto 版本。

## 8. 编译与静态链接

当前 `rusqlite` 已启用 `bundled`，有利于单文件分发。

发布目标：

```text
cargo build --release
```

建议新项目结构：

```text
monica-pass-cli/
  Cargo.toml
  README.md
  src/
    main.rs
    cli.rs
    output.rs
    session.rs
    policy.rs
    agent.rs
    vault/
    commands/
```

`monica-pass-cli/Cargo.toml` 通过 path dependency 复用根目录 `mdbx` workspace 的库 crate。不要把 `monica-pass` 作为 `mdbx-cli` 的第二个 bin，也不要把旧 `mdbx-cli` 改名。

Windows 静态单文件可先接受 bundled SQLite + MSVC runtime 依赖；若要求完全静态，应增加 target 文档：

- `x86_64-pc-windows-msvc`
- `x86_64-unknown-linux-musl`
- `aarch64-unknown-linux-musl`
- `x86_64-apple-darwin`
- `aarch64-apple-darwin`

## 9. 风险清单

高风险：

- Android 与 Rust MDBX 加密/解锁模型不一致。
- Agent JSON 如果返回明文密码，会违反核心安全目标。
- `--unlock-password` 命令行参数会进入 shell history/process list，不适合生产。
- 现有 CLI `entry get` 会直接打印 payload，生产版必须按模式脱敏。
- 现有 CLI 没有 session token、policy、audit。
- 现有 CLI security key unlock 底层支持但命令层未支持。

中风险：

- TOTP Steam/Yandex/MOTP 需要和 Android 算法逐项对齐。
- 大附件导出需要防止 Agent 间接读取明文。
- sync bundle 导入可能触发冲突，需要 JSON 化结果并暴露冲突摘要。
- Windows/macOS/Linux 的配置目录和文件权限处理不同。

低风险：

- 顶层命令重组。
- README 和示例。
- Rust crate 拆分。

## 10. 分阶段开发计划

### 阶段 1：独立项目创建与 CLI 命令骨架

目标：

- 在仓库根目录新增 `monica-pass-cli/` Rust 项目。
- 产物二进制名为 `monica-pass`。
- 通过 path dependency 复用 `mdbx-core`、`mdbx-crypto`、`mdbx-storage`、`mdbx-sync`。
- 不修改旧 `mdbx-cli` 的命令行为。
- 顶层实现 `unlock/search/get/add/edit/delete/totp/sync`。
- 普通用户模式默认输出人类可读文本。

验收：

- `cd monica-pass-cli && cargo build --release` 成功。
- `monica-pass --help` 显示核心命令。

### 阶段 2：安全输出与人类交互模式

目标：

- 移除生产命令中的 `--unlock-password` 默认推荐路径。
- 支持 `--password-stdin`、交互输入、环境隔离警告。
- `get` 默认脱敏，`get --reveal` 只在人类模式可用。
- `totp` 输出短期 code，不输出 seed。

验收：

- `get` 默认不打印 `password/secret/totp_seed`。
- `get --reveal` 需要已解锁人类会话或交互确认。

### 阶段 3：会话令牌与本地 session store

目标：

- `unlock` 生成短期 session token。
- session token 绑定 vault、权限、创建时间、过期时间。
- `lock` / session revoke。
- 文件权限检查。

验收：

- 过期 token 被拒绝。
- 不同 vault token 不能串用。
- session 文件权限过宽时拒绝使用。

### 阶段 4：SSH key Agent 认证与 policy

目标：

- `agent authorize` 登记 public key。
- `agent challenge/authenticate` 验签。
- 支持 policy：readonly、write、totp-only、自定义。
- Agent session 只能通过 policy 允许的命令。

验收：

- 未登记 key 不能认证。
- 错误签名不能认证。
- readonly agent 不能 add/edit/delete/sync push。

### 阶段 5：JSON Agent stdio 接口

目标：

- `agent serve --stdio`。
- JSON line request/response。
- 支持 `search/get/totp/sync status`。
- 统一错误码。
- 完整审计日志。

验收：

- Agent JSON `get` 不返回明文密码。
- Agent JSON `totp` 可按 policy 返回短期 code。
- 每次请求写入 audit log。

### 阶段 6：Android MDBX 兼容层与同步

目标：

- schema detector。
- Rust native adapter。
- Android compatibility adapter。
- sync bundle/status/apply 对齐 Android `oplog/sync_bundles/conflicts`。

验收：

- CLI 能打开 Rust 创建的 vault。
- CLI 能识别 Android 创建的 vault。
- 对不兼容加密模型给出明确错误和迁移建议。

### 阶段 7：README、测试矩阵、发布构建

目标：

- 完整 README.md。
- 安装、交互使用、Agent 调用示例。
- 测试覆盖核心安全边界。
- release 构建脚本。

验收：

- `cargo test` 通过。
- `cargo build --release` 通过。
- README 包含 OpenClaw/Agent JSON 示例。

## 11. 第一轮编码建议

下一步不应直接大改 storage，也不应直接改造旧 `mdbx-cli`。建议先新增独立项目：

```text
monica-pass-cli/
  Cargo.toml
  README.md
  src/
    main.rs
    cli.rs
    output.rs
    session.rs
    policy.rs
    agent.rs
    totp.rs
    vault/
      mod.rs
      detector.rs
      rust_native.rs
      android_compat.rs
    commands/
      unlock.rs
      search.rs
      get.rs
      add.rs
      edit.rs
      delete.rs
      sync.rs
```

这样可以把安全边界放在 Monica Pass CLI 层，复用现有 storage/repo/crypto/sync 能力，同时避免第一阶段破坏 MDBX 核心库和旧 `mdbx-cli`。

## 12. 完成定义

最终完成必须同时满足：

- Rust 单文件可执行。
- `cargo build --release` 成功。
- 核心命令可用：`unlock/search/get/add/edit/delete/totp/sync`。
- 普通用户交互可用。
- Agent JSON 接口可用。
- SSH key challenge/authenticate 可用。
- session token、policy、readonly、安全输出、audit 完整。
- README 完整。
- Agent 不直接获得数据库文件路径、数据库密钥或明文密码。

## 13. 当前实现状态（2026-05-24 迭代）

已新增独立项目：

```text
monica-pass-cli/
  Cargo.toml
  README.md
  build-release.ps1
  .cargo/config.toml
  src/
```

已实现命令：

```text
init
status
config path
config show
config set-default-vault
config clear-default-vault
doctor
android export
android migrate
unlock
lock
search
get
add login
add note
edit
delete
totp
generate
tui
sync status
sync bundle
sync apply
session status
session revoke
audit path
audit tail
agent authorize
agent challenge
agent authenticate
agent serve --stdio
```

已实现安全边界：

- `get` 默认脱敏，只有人类 CLI 显式 `--reveal` 才输出秘密字段。
- `--agent --reveal` 被拒绝。
- Agent stdio `get` 只返回 masked payload。
- search 不匹配 `password/secret/seed/totp_seed/private_key/passkey_private_key` 等秘密字段，避免通过搜索侧信道枚举秘密。
- SSH key 授权、challenge、OpenSSH `SSHSIG` 验签和短期 Agent session 已接入。
- policy 支持 `readonly`、`allow_totp`、命令白名单，并拒绝 readonly policy 包含写命令。
- 普通 CLI 的 `--session` 会校验 vault 绑定和过期时间，并对 readonly session 拒绝写命令。
- session token 不保存主密码、数据库 key 或明文秘密；它只是授权和审计上下文。
- `audit.jsonl` 记录命令、actor、vault hash、session hash、policy、readonly、成功/失败，不记录秘密值。
- Android-compatible `android export` 默认脱敏 payload 中的秘密字段，只有人类显式 `--reveal` 才输出明文。
- Android-compatible `android migrate` 只写入新的 Rust native vault，不原地修改 Android 测试库。
- `tui` 是人类本地交互界面，默认脱敏详情，只有按 `Enter`/`r` 才在当前本地界面临时 reveal。
- `tui` 的筛选逻辑不匹配 password、TOTP seed、private key 等秘密字段，避免用搜索侧信道探测秘密值。
- `generate` 是人类 CLI 明文输出命令，`--agent generate` 被拒绝，避免 Agent 获取新生成的明文密码。
- `add login --generate-password` 会在本地生成密码并直接写入 vault，不通过 argv 暴露 secret。

已实现跨平台与多语言基础：

- `platform.rs` 统一 Monica Pass 状态目录：
  - Windows: `%LOCALAPPDATA%\MonicaPass`
  - macOS: `~/Library/Application Support/MonicaPass`
  - Linux: `$XDG_STATE_HOME/monica-pass` 或 `~/.local/state/monica-pass`
- `MONICA_PASS_HOME` 可覆盖状态目录。
- Unix-like 系统上 session/agent/audit 文件通过安全写入 helper 收紧为 owner-only 权限。
- `i18n.rs` 集中语言 catalog，支持 `--lang zh-CN`、`MONICA_PASS_LANG`、`LANG`。

已实现兼容诊断：

- `doctor` 可识别 Rust native schema、Android compat schema、unknown schema、文件不存在。
- Android MDBX 当前支持只读导出和安全迁移；仍不直接打开写入。原因是 Android 测试实现与 Rust native 在加密和 schema 上仍不完全一致，贸然原地写入可能损坏测试库。

已实现发布配置：

- `rusqlite` 使用 bundled SQLite。
- Windows/MSVC 通过 `.cargo/config.toml` 请求 `+crt-static`。
- `build-release.ps1` 支持默认 release 构建和传入 target。

已实现可用性增强（2026-05-25 迭代）：

- 新增 `status`：集中展示 vault 路径、文件存在性、schema 类型、native open 状态、session 状态、audit 路径；提供凭据时额外展示 sync commit 统计。
- 新增 `tui`：单独终端界面，支持浏览、即时筛选、上下移动、详情查看、显式临时 reveal、退出。
- TUI 面向普通用户高频场景，不开放给 `--agent` 模式。
- TUI 使用 `ratatui 0.26` + `crossterm 0.27`，兼容当前本机 Rust 1.86 工具链，避免抬高项目 MSRV。
- README 已加入 TUI 快捷键、status、日常工作流说明。

已实现可用性增强（2026-05-25 夜间迭代）：

- 新增 `config`：支持查看配置文件路径、展示配置、设置默认 vault、清除默认 vault。
- 全局 vault 解析顺序调整为：显式 `--vault` > `config.default_vault` > 当前目录 `vault.mdbx`。
- 新增 `generate`：使用 OS 随机源生成高强度密码，默认长度 24，默认包含符号。
- `add login` 新增 `--generate-password`、`--generated-length`、`--generated-no-symbols`，用于创建登录项时直接写入生成密码。
- 修复 `search` 子命令短参数冲突：`--tag` 改为 `-g/--tag`，`--entry-type` 保持 `-t/--entry-type`。
- 新增 Clap debug assertion 测试，防止后续命令参数冲突在真实 debug 运行时 panic。
- 真实 smoke 已覆盖：临时 `MONICA_PASS_HOME`、默认 vault 设置、无 `--vault` init、generate、add login 生成密码、search、masked get、status、Agent generate 拒绝。
- 配置损坏自救：`config path`、`config clear-default-vault`、`config set-default-vault` 不依赖解析现有 `config.json`，即使配置文件损坏也能定位、清除和重写。

已通过的测试范围：

- Android-compatible PBKDF2/AES-GCM 解锁参数解析。
- Android-compatible plaintext schema export。
- Android export payload 默认脱敏。
- masked output 不泄漏 secret。
- Agent masked payload 不泄漏 password/TOTP seed/private key 值。
- search 不命中秘密字段。
- TUI 筛选不命中秘密字段。
- TUI 详情默认脱敏，显式 reveal 后才显示秘密字段。
- password generator 覆盖长度、字符类别和过短拒绝。
- config 默认 vault 解析覆盖显式路径优先级。
- config 损坏恢复覆盖 set/clear 覆盖坏配置。
- Clap command debug assertions 覆盖短参数冲突。
- readonly policy 拒绝写命令。
- TOTP/HOTP RFC 向量。
- audit event hash session token，不写入 token 明文。
- vault detector 区分 rust-native 和 android-compat。
- i18n 语言检测。
- 跨平台 state dir override。

仍需后续真实联调的部分：

- Android-compatible 原地写入 adapter。
- OpenClaw 等真实 Agent 的长期 stdio 调用体验。
- macOS/Linux 发布包与文件权限细节。
- 如果要让 Agent 无需环境变量长期访问，需要单独设计本地安全 daemon 或系统 keyring 集成；不能把 session token 直接升级成数据库解锁 key。
