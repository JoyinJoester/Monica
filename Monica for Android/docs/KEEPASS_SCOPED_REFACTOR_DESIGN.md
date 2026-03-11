# Monica Android KeePass 管理域定向重构设计

## 1. 目标与边界

本设计只处理 KeePass 数据库管理与 KeePass 工作区体验，不改变 Bitwarden 与 Monica 本地数据库的现有行为、同步语义、存储模型与业务流程。

明确边界如下：

- 不修改 Bitwarden 现有同步链路、待同步队列、Cipher 映射与 tombstone 语义。
- 不修改 Monica 本地密码库、笔记、TOTP、卡片、证件等本地数据表的业务含义。
- 不把 KeePass 改造扩展成三端统一存储层。
- 本轮不引入 Monica 本地数据库 schema 变更作为前置条件。

本轮允许调整的范围：

- KeePassKdbxService 的职责边界、节点操作方式、回收站/恢复/分组实现。
- LocalKeePassViewModel 及新增的 KeePass 工作区 ViewModel。
- KeePass 独立工作区下的读取、编辑、删除、恢复、分组管理交互链路。
- 现有业务 ViewModel 中对 KeePass 的直接调用方式，目标是“收口或隔离”，而不是改变其本地/Bitwarden 行为。

## 2. 当前问题不是单点 Bug，而是管理模型错误

现状与路线图冲突。路线图已经要求 KeePass 变为独立客户端模式，并停止与 Monica 本地库、Bitwarden 的双写联动，但代码仍在多个业务 ViewModel 中直接写 KeePass。

现有实现的主要问题：

1. KeePass 不是独立域，而是挂在 Password/Note/Totp/BankCard/Document 等 ViewModel 上的“附属写入目标”。
2. 条目定位依赖 MonicaLocalId 或标题/用户名/网址等回退匹配，缺少稳定的原生节点身份。
3. 分组管理依赖路径字符串，而不是分组 UUID 或节点引用。
4. 回收站移动、恢复、跨组修改本质上是“递归移除 + 按路径重建”，不是节点级移动。
5. 上层业务状态与 KDBX 文件状态是双状态，失败时很容易出现一边成功、一边失败。

因此，删除问题只是最先暴露的表象；同样的脆弱性会持续出现在更新、移动、恢复、改组、批量删除、重命名分组等场景。

## 3. 现状诊断

### 3.1 路线图要求已经明确，但代码未落地

现有路线图已经明确提出：

- KeePass 改为独立客户端模式。
- 不再与 Monica 本地库、Bitwarden 做互通写入。
- 从 Password/Note/Document/BankCard 等 ViewModel 中移除对 KeePass 的跨域同步调用。

这说明后续设计不需要再讨论“要不要独立”，而是需要讨论“如何在不改本地库和 Bitwarden 语义的前提下，把 KeePass 真正收口成独立域”。

### 3.2 KeePass 仍然散落在各业务 ViewModel 中

当前 PasswordViewModel、NoteViewModel、TotpViewModel、BankCardViewModel、DocumentViewModel 都在直接调用 KeePassKdbxService，意味着：

- 任何一个业务 ViewModel 都可以发起 KDBX 写入。
- 同一条 KeePass 规则在多个地方重复实现。
- 删除失败、回收站失败、路径恢复失败会以复制粘贴的方式蔓延。

这类代码应该被视为“待收口调用点”，而不是继续修补的地方。

### 3.3 节点身份不稳定

当前 KeePassKdbxService 在更新、删除、恢复时优先使用 MonicaLocalId 或 MonicaItemId；如果找不到，再回退到字段匹配：

- 密码：Title + UserName + URL
- 安全项：Title + MonicaItemType

这套匹配策略在以下情况下都会失真：

- 同名条目
- 用户修改标题或网址后再次操作
- 同一条目跨分组移动
- 旧数据没有 MonicaLocalId
- 批量导入或外部客户端修改过条目内容

也就是说，现有“删除错条目 / 删除无效 / 恢复到错误位置 / 更新变成新增”都不是偶然问题，而是身份模型不对。

### 3.4 分组和回收站是路径驱动，不是节点驱动

当前 createGroup、renameGroup、deleteGroup 以及 addEntryToGroupPath、removeGroupByPath 都基于字符串路径工作。问题在于：

- 路径不是稳定标识。
- 重名分组天然歧义。
- 改名会改变整条路径，所有依赖旧路径的逻辑都会变脆。
- 恢复时只能“猜目标路径”，而不是把节点放回原父组。

这一点与 KeePassDX 的差异非常大。KeePassDX 的核心操作对象是 Entry/Group 节点及其 UUID，不是路径字符串。

### 3.5 服务层虽然串行化，但没有形成单一事实源

KeePassKdbxService 已经有全局 mutation mutex、decode 串行和 loaded database cache，这说明服务层已经在努力降低并发写坏的风险。但由于上层仍在维护本地状态和 KeePass 状态两个事实源，串行化只能降低损坏概率，不能消除语义不一致。

换句话说：

- 现在解决的是“不要同时写坏文件”；
- 还没有解决“谁才是 KeePass 工作区的真实数据源”。

## 4. 设计原则

在用户要求“不动 Bitwarden 和 Monica 本地库”的前提下，本轮设计遵守以下原则：

1. KeePass 只做独立域收口，不做全局存储统一化。
2. Monica 本地库与 Bitwarden 仍维持原有业务流，不以 KeePass 改造为代价重构。
3. KeePass 工作区中的增删改查，统一以 KDBX 文件内容为唯一事实源。
4. 节点操作优先基于 UUID/节点引用，路径仅用于展示与兼容。
5. 原有 keepassDatabaseId、keepassGroupPath、MonicaLocalId 等字段暂时只作为兼容桥，不再继续扩散为未来主模型。
6. 对现有业务 ViewModel 的改造目标是“去直接写 KeePass”，不是改变它们处理本地/Bitwarden 的行为。

## 5. 目标架构

### 5.1 新的 KeePass 域分层

建议把 KeePass 相关逻辑拆成下面三层：

1. KeePassWorkspaceRepository
   - 负责打开数据库、读取节点树、按 UUID 操作条目和分组、提交写入、失败回滚。
   - 这是 KeePass 工作区唯一允许写 KDBX 的入口。

2. KeePassWorkspaceViewModel
   - 负责数据库会话、列表、详情、分组树、回收站、恢复、搜索、错误状态。
   - 只服务 KeePass 独立工作区，不与 PasswordViewModel/NoteViewModel 混用状态。

3. KeePassCompatibilityBridge
   - 只用于兼容现有字段和旧入口。
   - 作用是把旧的 keepassDatabaseId / keepassGroupPath / MonicaLocalId 解释成 KeePass 节点引用。
   - 这是过渡层，不应承载新的业务逻辑。

### 5.2 现有 KeePassKdbxService 的新定位

KeePassKdbxService 不应继续同时扮演以下所有角色：

- 文件 IO 工具
- 业务同步工具
- 兼容匹配工具
- 分组路径工具
- 业务 ViewModel 的万能远端写入器

建议将其重构为两部分：

- KeePassStorageEngine
  - 只负责 decode、encode、写入、缓存、错误分类、原子写入。
- KeePassWorkspaceRepository
  - 只负责节点语义的业务操作。

这样能保留现有底层能力，同时把“按路径删改查”和“按字段猜节点”的历史包袱隔离出去。

## 6. 不改本地数据库前提下的身份方案

由于本轮不把 Monica 本地数据库 schema 变更作为前提，建议先采用“运行时原生节点身份 + 兼容字段桥接”的策略。

### 6.1 KeePass 工作区主身份

KeePass 工作区中的条目和分组，统一使用以下运行时引用：

```text
KeePassNodeRef
- databaseId: Long
- entryUuid: UUID?
- groupUuid: UUID?
- parentGroupUuid: UUID?
- previousParentGroupUuid: UUID?
- isInRecycleBin: Boolean
```

这个引用只存在于 KeePass 域的内存模型和 UI 状态中，不要求落库到 Monica 本地表。

### 6.2 旧字段的处理策略

现有字段继续保留，但降级为兼容用途：

- keepassDatabaseId：只表示“历史上关联到哪个数据库”。
- keepassGroupPath：只作为初始展示路径和兼容恢复线索，不再作为最终定位依据。
- MonicaLocalId / MonicaItemId：仅用于旧条目第一次建桥，成功识别 UUID 后不再参与日常主流程。

### 6.3 兼容桥接流程

旧入口第一次进入 KeePass 工作区时：

1. 先尝试通过 KDBX 条目本身的 UUID 建立节点引用。
2. 如果当前 UI 来源于旧业务对象，则用 MonicaLocalId/MonicaItemId 做一次兼容匹配。
3. 只有兼容匹配失败时，才允许一次字段回退匹配。
4. 一旦建立成功，后续会话中都按 UUID 处理，不再回退到路径和字段匹配。

这能把高风险的模糊匹配限制在迁移入口，而不是扩散到每一次删除和更新里。

## 7. 操作模型设计

### 7.1 删除与回收站

目标行为对齐 KeePassDX：

- 是否可回收，由 recycle bin 元数据与当前节点状态决定。
- 可回收时执行节点移动，而不是“递归删掉后重建一份副本”。
- 不可回收时执行永久删除，并记录 deleted object 元数据。
- 写入失败时，允许在内存状态中撤销回收站移动。

本轮不需要完整复制 KeePassDX 内部实现，但要对齐这几个原则。

### 7.2 更新

更新必须变成“按 UUID 定位后修改该节点”，而不是：

- 先 removeEntryInGroup 找到旧节点
- 再 buildEntry
- 再 addEntryToGroupPath 重新插回去

“删除再重建”会丢掉节点连续性，放大回收站、恢复、附件、历史记录和 previous parent 的问题。

### 7.3 移动分组

分组的创建、改名、删除、移动都应转向 UUID/节点引用驱动。

路径仍然可以保留给 UI 展示，但不能作为写操作的真实定位输入。否则重名与改名问题永远存在。

### 7.4 恢复

恢复顺序建议如下：

1. 优先 previousParentGroupUuid。
2. 若父组不存在，则尝试最近一次有效 groupUuid 路径映射。
3. 若仍失败，则恢复到根组下的“Recovered”或约定安全组。
4. UI 给出提示，但不再静默失败。

这样可以替代当前 resolveRestoreGroupPath 基于路径猜测的模式。

## 8. 现有代码的具体收口方案

### 8.1 第一批必须收口的调用点

下列 ViewModel 中的 KeePass 直接调用，应逐步迁移到 KeePassCompatibilityBridge 或完全移除：

- PasswordViewModel
- NoteViewModel
- TotpViewModel
- BankCardViewModel
- DocumentViewModel
- TrashViewModel

处理原则不是把这些 ViewModel 一次性重写，而是：

- 对 KeePass 工作区入口：完全切换到 KeePassWorkspaceViewModel。
- 对历史混合入口：只保留最薄的一层兼容桥。
- 任何新的 KeePass 功能，不再落在这些业务 ViewModel 上。

### 8.2 LocalKeePassViewModel 的保留与扩展

LocalKeePassViewModel 当前已经承担数据库列表、验证、分组缓存、建组/改名/删组等职责，可以继续保留为“数据库管理入口”。

但它不应继续变成 KeePass 条目业务的总控制器。建议边界如下：

- LocalKeePassViewModel：管理数据库文件本身。
- KeePassWorkspaceViewModel：管理数据库内容与条目/分组操作。

## 9. 分阶段实施路线

### 阶段 A：收口，不改业务语义

目标：先把 KeePass 从分散调用改成集中入口，同时不改变 Bitwarden 和 Monica 本地库行为。

执行内容：

- 新增 KeePassWorkspaceRepository 与 KeePassWorkspaceViewModel。
- KeePassKdbxService 拆出底层存储职责。
- 业务 ViewModel 停止新增任何 KeePass 直接写入代码。
- 现有混合入口只通过兼容桥访问 KeePass。

验收标准：

- KeePass 新功能不再写进 Password/Note/Totp/BankCard/Document ViewModel。
- KeePass 工作区的条目操作统一走一个入口。

### 阶段 B：替换身份模型

目标：把主流程从路径/字段匹配切到节点引用。

执行内容：

- 引入运行时 KeePassNodeRef。
- 条目详情、删除、恢复、移动、分组操作改为 UUID 驱动。
- 旧匹配逻辑仅保留在兼容桥中。

验收标准：

- 绝大多数日常操作不再依赖 matchByKey / matchSecureItemByKey。
- 删除、恢复、移动不再依赖 keepassGroupPath 作为唯一依据。

### 阶段 C：回收站与恢复模型对齐 KeePassDX

目标：把最容易出问题的删除/恢复链路稳定下来。

执行内容：

- 引入节点移动到 recycle bin 的正式语义。
- 完善 previous parent 记录与恢复顺序。
- 写入失败时回滚内存状态。

验收标准：

- 删除、回收站、恢复在重命名分组、跨组移动后仍可稳定工作。
- 不再出现“UI 删除失败但文件已改”或“本地已删但 KDBX 未删”的高频不一致。

### 阶段 D：彻底收尾旧桥接逻辑

目标：把旧的路径/字段猜测逻辑压缩到最小范围。

执行内容：

- 缩减 matchByKey / matchSecureItemByKey 的使用面。
- 缩减 resolveRestoreGroupPath 的路径主导逻辑。
- 把 keepassGroupPath 降级为展示字段，而不是操作主键。

## 10. 明确不做的事情

为了满足当前边界，本设计明确不做以下内容：

- 不改 Bitwarden 待同步删除、待同步更新、Cipher 对应关系与同步时机。
- 不改 Monica 本地密码、笔记、TOTP、卡片、证件的 Room 表语义。
- 不把 KeePass 条目强行并入 Monica 本地统一列表模型。
- 不以“统一三套存储”为目标启动大重构。

## 11. 推荐下一步

下一步不应该继续修零散删除 Bug，而应该先做一张“调用点收口清单”，明确：

1. 哪些 ViewModel 的 KeePass 直连代码先冻结。
2. KeePassWorkspaceRepository 首批需要承接的操作集。
3. 哪些旧路径匹配逻辑可以在第一阶段就下沉到兼容桥。

如果继续按现在的分散模式修问题，删除修完后，恢复、移动、分组和批量操作还会继续重复出现同类故障。