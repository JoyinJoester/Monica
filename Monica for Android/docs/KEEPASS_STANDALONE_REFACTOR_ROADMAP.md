# Monica Android KeePass 独立客户端化改造路线图

## 1. 背景与最终决策

### 1.1 决策
- KeePass WebDAV 功能彻底下线（代码、UI、配置、文案、迁移路径全部处理）。
- KeePass 在 Monica 中改为“独立客户端模式”：
  - 行为目标对齐 KeePassDX（作为独立 KeePass 客户端使用）。
  - 不再与 Monica 本地库、Bitwarden 做数据互通/双向映射/联动写入。
- 保留 Monica 本地与 Bitwarden 功能，但与 KeePass 彻底解耦为并行能力。

### 1.2 改造目标
- 在 Monica 内实现稳定的 KeePass 文件管理体验（打开、浏览、编辑、保存、导入导出、错误提示）。
- 格式、凭据、错误分类、兼容性能力尽量向 KeePassDX 对齐。
- 降低复杂耦合导致的读写失败与状态不一致问题。

---

## 2. 范围定义

### 2.1 In Scope（本次必须完成）
- WebDAV 全量下线。
- KeePass 数据域独立化（模型/仓库/ViewModel/UI 交互链路）。
- KeePass 支持能力扩展（文件格式、凭据、兼容性、错误分类）。
- 迁移与回退方案（旧数据引用、旧设置项清理）。
- 完整测试矩阵与验收标准落地。

### 2.2 Out of Scope（本次不做）
- Monica 本地库与 Bitwarden 的新功能扩展。
- 云同步新方案（替代 WebDAV）设计与实现。
- 非 KeePass 领域 UI 大改版。

---

## 3. 目标架构（改造后）

- `KeePass Domain`：独立的数据模型、操作接口、状态管理、错误体系。
- `Monica Local Domain`：保持原有本地库逻辑，不感知 KeePass 细节。
- `Bitwarden Domain`：保持独立同步逻辑，不与 KeePass 互相写入。
- `UI 层`：通过“数据源模式/工作区模式”切换，分别进入 KeePass 或 Monica/Bitwarden 视图，不混合列表。

---

## 4. 任务分解（按优先级）

## P0 - 产品一致性与风险清理
- 下线 KeePass WebDAV：
  - 移除入口、按钮、设置项、文案、备份项中的 WebDAV 配置。
  - 移除 `webdav://` 路径绑定能力与相关分支。（只是keepass webdav不要动webdav备份）
  - 对历史 WebDAV 绑定数据给出迁移提示（仅提示，不继续支持）。
- 移除 KeePass 与 Monica/Bitwarden 的互通：
  - 停止“保存到 KeePass 同时写入本地/Bitwarden”等双写逻辑。
  - 移除 `keepassDatabaseId` 等跨域联动入口在业务流中的强绑定行为。
  - 列表、筛选、移动、回收站等跨域动作改为域内动作。

## P1 - KeePass 核心能力对齐 KeePassDX
- 文件格式支持对齐：
  - 明确支持 `.kdb` + `.kdbx`（版本能力需在启动时检测并提示）。
  - 基于文件头而非扩展名判断解析分支。
- 凭据能力对齐：
  - 密码、密钥文件、密码+密钥文件组合。
  - 评估并预留/实现硬件挑战（challenge-response）能力。
  - key file 兼容策略对齐（XML key / 32-byte / 64-hex / binary-hash）。
- KDF/算法兼容：
  - AES/Twofish/ChaCha20、Argon2 等常见组合兼容性评估与落地。
  - 针对大内存 KDF 场景提供明确错误分类与前置检查。

## P1 - 稳定性与可诊断性
- 错误体系重构：
  - 区分“凭据错误 / 文件损坏 / 格式不支持 / URI 权限 / KDF内存不足 / IO失败”。
  - UI 错误文案与日志标签一一对应，避免全部显示为“密码错误”。
- 读写安全：
  - 外部 URI 写入策略增强（必要时 fallback 模式）。
  - 原子写入与失败回滚策略完善。
- 并发控制：
  - 保持全局解码串行化策略。
  - 明确读写锁粒度，避免并发覆盖。

## P2 - 体验与迁移
- 新建“独立 KeePass 工作区”入口与信息架构。
- 历史数据库引用迁移：
  - 识别旧关联字段并迁移为“只属于 KeePass 域”的记录。
  - 对无法自动迁移场景提供一次性提示与手动指引。
- 文档与帮助页：
  - 新增支持格式、凭据方式、常见错误处理说明。

---

## 5. 代码层改造清单（执行时核对）

- `KeePassKdbxService`：
  - 移除 WebDAV 分支与遗留入口。
  - 抽离并标准化 Credentials 解析器。
  - 增强错误类型输出与日志结构。
- `LocalKeePassViewModel` / `KeePassWebDavViewModel`：
  - 合并/重构为 KeePass 独立场景所需 ViewModel，删除 WebDAV 职责。
- `Password/Note/Document/BankCard/... ViewModel`：
  - 移除对 KeePass 的跨域同步调用。
- `UI`：
  - 删除 KeePass 与本地/Bitwarden 混合存储选择器中的跨域互通选项。
  - 调整筛选项与入口，不再跨域显示同一条数据。
- `DB Schema`：
  - 评估 `keepassDatabaseId` 等历史字段是否保留为兼容字段或进入废弃流程。

---

## 6. 验收标准（Definition of Done）

### 6.1 功能验收
- WebDAV 相关功能在 UI、逻辑、配置中不可用且无残留入口。
- KeePass 可作为独立客户端工作：打开、编辑、保存、重载稳定可用。
- `.kdb`、`.kdbx`（常见版本与算法）可按预期提示“支持/不支持”。
- 凭据方式在支持范围内可正确解锁，不支持时给出明确原因。

### 6.2 数据一致性
- KeePass 操作不再影响 Monica 本地/Bitwarden 数据。
- Monica 本地/Bitwarden 操作不再触发 KeePass 写入。
- 旧版本用户升级后不会出现崩溃或死循环同步。

### 6.3 可观测性
- 每类关键失败都有可定位日志：
  - `LEGACY_KDB_UNSUPPORTED`
  - `FORMAT_UNSUPPORTED`
  - `INVALID_CREDENTIAL`
  - `URI_PERMISSION_DENIED`
  - `KDF_MEMORY_INSUFFICIENT`
  - `IO_READ_WRITE_FAILED`

---

## 7. 测试矩阵（必须覆盖）

- 文件类型：`.kdb` / `.kdbx`
- 凭据组合：密码 / 密钥文件 / 密码+密钥文件 /（若支持）硬件挑战
- 算法与KDF：AES / Twofish / ChaCha20 / Argon2（不同参数档位）
- 存储来源：内部存储 / SAF 外部 URI
- 操作链路：新建、导入、解锁、编辑、保存、重开、异常恢复
- 异常场景：权限丢失、文件被外部修改、内存不足、文件损坏

---

## 8. 风险与缓解

- 风险：历史用户依赖 WebDAV。
  - 缓解：版本说明 + 首次升级弹窗 + 数据导出指引。
- 风险：格式/凭据扩展引入新回归。
  - 缓解：先建兼容测试样本库，再灰度发布。
- 风险：跨域字段遗留导致混乱。
  - 缓解：迁移脚本 + 只读兼容层 + 明确废弃时间线。

---

## 9. 里程碑建议

1. M1（1-2天）：冻结范围、删除 WebDAV UI 入口、加迁移提示。
2. M2（3-5天）：拆除跨域互通链路，确保 KeePass 独立运行。
3. M3（5-8天）：格式/凭据能力扩展与错误体系完善。
4. M4（2-4天）：全量测试矩阵、修复回归、更新文档。

---

## 10. 执行备注

- 当前文档是“任务母单”，后续每个里程碑拆成独立 issue/PR。
- 在你明确说“开始写代码”之前，仅做分析与文档，不进入代码改动。

---

## 11. KeePassDX 对齐评估（2026-03-08）

> 基准来源（用于对齐目标）：  
> - KeePassDX README（功能列表、格式与算法支持）  
> - KeePassDX Wiki / Hardware Key（硬件密钥现状）

### 11.1 当前已对齐（Monica）

- `KDBX 3.x / 4.x` 新建与读取（含导入场景）。
- 建库高级参数：数据库版本、外层算法（AES / ChaCha20 / Twofish）、KDF（AES-KDF / Argon2d / Argon2id）、转换次数、内存、并行度。
- 本地文件与 SAF 外部 URI 管理（内部/外部存储切换、导入引用）。
- 密码 + 密钥文件组合解锁（含空密码+密钥文件的兼容回退）。
- key file 兼容回退增强：导入/验证时支持 `raw / XML<Data> / 64-hex / sha256(raw)` 多候选凭据尝试，降低不同客户端生成 keyfile 的兼容失败率。
- 凭据失败可诊断性增强：候选凭据全部失败时会返回“已尝试组合”摘要，便于定位 keyfile 形态不匹配问题。
- 基础可观测性：解密耗时展示；错误分类与错误码日志（`FORMAT_UNSUPPORTED / INVALID_CREDENTIAL / URI_PERMISSION_DENIED / KDF_MEMORY_INSUFFICIENT / IO_READ_WRITE_FAILED`）。
- `kdb` 识别链路：已基于文件头 + 扩展名兜底识别旧版 `*.kdb`，并明确提示“需先转存为 `.kdbx`”，避免误判为密码错误。
- 导入页体验增强：选择 `*.kdb` 文件时前置提示“需先转换为 `.kdbx`”，并在 Snackbar 中显示 KeePass 错误码，减少排障成本。
- 导入诊断弹窗：KDBX 导入失败时展示错误码、失败原因与分类型修复建议（凭据/权限/KDF/格式/IO）。

### 11.2 尚未对齐（与 KeePassDX 仍有差距）

- 旧格式 `*.kdb (v1/v2)`：已支持识别与升级指引，但仍未提供 `kdb` 全量读写链路。
- 硬件密钥挑战响应（YubiKey HMAC-SHA1 / FIDO2 hmac-secret）：Monica 尚未接入。
- KeePass 客户端体验深度项（按 KeePassDX 视角）：
  - 条目历史（entry history）编辑体验。
  - 动态模板（dynamic templates）完整 UI/流程。
  - KeePass 独立工作区下的全量编辑细节（批量、合并、冲突处理细化）。

### 11.3 下一步优先级（建议按此继续）

1. `P0`：补齐 `kdb` 文件识别与明确提示（至少做到“可识别 + 可升级迁移指引”，避免误判为密码错误）。  
2. `P1`：硬件密钥能力调研与 PoC（优先 YubiKey challenge-response）。  
3. `P1`：KeePass 独立工作区增强（历史版本、模板、分组批量操作）。  
4. `P1`：兼容样本回归集（KDBX3/4 + AES/ChaCha20/Twofish + Argon2/AES-KDF + 密钥文件组合）。  
