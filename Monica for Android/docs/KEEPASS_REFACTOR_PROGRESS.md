# Monica Android KeePass 重构进度文档

最后更新：2026-03-10

## 1. 当前目标

本轮工作只处理 Monica Android 对 KeePass 数据库的管理能力，不改 Bitwarden 逻辑，不改 Monica 本地数据库的既有业务语义。

当前主线目标不是“修一个删除 Bug 就结束”，而是把 Monica 的 KeePass 模式逐步改造成接近 KeePassDX 的管理模型：

- 以 KeePass 原生节点身份为中心，而不是依赖 Monica 本地行 ID 或标题路径猜测。
- 让删除、更新、恢复、移动、分组等行为尽量基于原生 UUID 运作。
- 把 KeePass 从散落在各业务 ViewModel 里的附属写入目标，收口成更独立、更稳定的管理域。

## 2. 已确认的问题根因

前期分析已经确认，KeePass 条目删除异常不是单点问题，而是架构问题的外显：

- Monica 过去更像是 Room 本地对象驱动，KDBX 文件只是“同步目标”。
- 条目匹配大量依赖 MonicaLocalId、MonicaSecureItemId、标题、用户名、网址、分组路径等脆弱条件。
- 分组与恢复行为过度依赖路径字符串，重命名、重组、重名分组时容易失真。
- 这和 KeePassDX 的思路不同。KeePassDX 更接近 KDBX-first，依赖条目 UUID、分组 UUID、回收站元数据和父组关系来操作节点。

这也是为什么 Monica 在 KeePass 管理上会反复出现“删除失败、更新错位、恢复不准、移动后失联”这类问题。

## 3. 已完成工作

### 3.1 第一阶段收口已经完成的部分

已新增并接入以下过渡层：

- KeePassWorkspaceRepository
- KeePassCompatibilityBridge
- KeePassWorkspaceViewModel

目的不是最终形态，而是先把 KeePass 访问点收口，停止继续扩散直接调用 KeePassKdbxService 的写法。

已迁移或处理过的主要范围：

- PasswordViewModel
- NoteViewModel
- TotpViewModel
- BankCardViewModel
- DocumentViewModel
- TrashViewModel
- LocalKeePassViewModel
- 部分 UI 分组读取入口

### 3.2 已落地的本地身份模型升级

这是当前最关键的一步，已经开始把 KeePass 原生身份从“服务层内部有、上层不用”改成“上层也持有、同步也使用”。

已完成：

- 在 PasswordEntry 增加 keepassEntryUuid、keepassGroupUuid。
- 在 SecureItem 增加 keepassEntryUuid、keepassGroupUuid。
- 为上述字段增加对应索引。
- DAO 与 Repository 已支持通过 KeePass entry UUID 定位本地投影对象。

涉及的核心文件包括：

- PasswordEntry.kt
- SecureItem.kt
- PasswordEntryDao.kt
- SecureItemDao.kt
- PasswordRepository.kt
- SecureItemRepository.kt

### 3.3 数据库版本和迁移已补齐

Room 数据库版本已从 46 升级到 47，并增加 46 -> 47 迁移，用于新增 KeePass UUID 相关字段和索引。

已处理事项：

- 新增 keepass_entry_uuid 字段。
- 新增 keepass_group_uuid 字段。
- 补齐索引创建。
- 清理过一次迁移注册重复问题。

### 3.4 KeePassKdbxService 已开始改成 UUID-first

这部分是本轮最重要的行为修正。

已完成的变化：

- KeePassEntryData 已输出 entryUuid、groupUuid。
- 遍历上下文已保留 groupUuid。
- 更新、删除、回收站移动、恢复等匹配逻辑已优先使用 KeePass 原生 UUID。
- 旧的 MonicaLocalId、MonicaSecureItemId、标题字段匹配降级为兼容回退，而不是主流程。
- 新建或改写 KeePass 条目时，已经开始保留或生成 keepassEntryUuid。
- restore 路径解析已开始接收 preferredGroupUuid，向分组 UUID 驱动过渡。

这意味着 Monica 对 KeePass 条目的操作，已经不再完全依赖“猜它是谁”，而是开始依赖“确认它就是哪个原生节点”。

### 3.5 多个业务 ViewModel 已接入 UUID 同步逻辑

以下视图模型的 KeePass 同步导入或新建流程，已经接入 UUID-first 处理：

- PasswordViewModel
- NoteViewModel
- TotpViewModel
- BankCardViewModel
- DocumentViewModel

已完成的实际变化：

- 同步导入时优先通过 KeePass entry UUID 查找本地对象。
- 同步后的本地投影会持久化 keepassEntryUuid、keepassGroupUuid。
- 对于新建的 KeePass-backed 条目，已开始主动分配 keepassEntryUuid，避免后续再次落回模糊匹配。

### 3.6 本轮新增修复：笔记编辑不再丢失 KeePass 原生身份

这一轮继续补了笔记编辑链上的一个关键缺口。

之前的问题是：

- NoteViewModel 在 updateNote 和 moveNoteToStorage 里重建 SecureItem 时，没有稳定继承已有的 keepassEntryUuid 和 keepassGroupUuid。
- 结果是一个已经完成 UUID 绑定的 KeePass 笔记，在编辑后又会退回旧的模糊匹配路径。

本轮已修复：

- 同库更新时，保留原有 keepassEntryUuid。
- 同组更新时，保留原有 keepassGroupUuid。
- 从本地首次迁入 KeePass 或跨 KeePass 库迁移时，生成新的 keepassEntryUuid。
- 分组发生变化时，不再错误沿用旧 groupUuid。

这一步的意义是，笔记编辑页现在也开始真正站到 UUID-first 主线上，而不是只在同步导入时有 UUID、手工编辑后又把它丢掉。

### 3.7 本轮继续补齐：银行卡和证件编辑/迁移链接入同样的身份保持规则

这一轮继续向下补了另外两条 SecureItem 子类型的真实缺口：

- BankCardViewModel
- DocumentViewModel

之前这两条链的问题和笔记类似：

- updateCard / updateDocument 在重建 SecureItem 时，没有显式保留 keepassEntryUuid 与 keepassGroupUuid。
- moveCardToStorage / moveDocumentToStorage 在切换存储位置时，也会直接覆盖 keepassDatabaseId / keepassGroupPath，而没有按 KeePass 原生身份规则决定 UUID 是否继承。

本轮已修复：

- 同库编辑时保留原有 keepassEntryUuid。
- 同库且同组迁移时保留原有 keepassGroupUuid。
- 首次迁入 KeePass 或跨 KeePass 库迁移时重新生成 keepassEntryUuid。
- 分组变化时不再错误沿用旧的 keepassGroupUuid。

这意味着笔记之外，银行卡和证件也开始从“只在导入阶段带 UUID”推进到“手工编辑和存储迁移也保持 UUID-first”。

### 3.8 本轮继续补齐：TOTP 保存链不再在更新时丢失 KeePass UUID

这一轮进一步确认，TOTP 主链也存在同类问题：

- saveTotpItem 在更新现有条目时，会重写 keepassDatabaseId 与 keepassGroupPath。
- 但此前没有根据“是否同库、是否同组”来决定 keepassEntryUuid / keepassGroupUuid 是否继承。

本轮已补齐：

- 同库更新时保留 keepassEntryUuid。
- 同库且分组未变化时保留 keepassGroupUuid。
- 绑定密码导致迁入 KeePass、跨 KeePass 库迁移或首次创建到 KeePass 时，重新生成或建立正确的 entry UUID。
- 绑定密码切换带来的分组变化，不再错误沿用旧 group UUID。

到这里，Password、Note、TOTP、BankCard、Document 这几条核心 KeePass 写入主链，已经都开始按同一套 UUID-first 身份保持规则运作。

### 3.9 本轮继续补齐：笔记编辑上游不再丢失 KeePass 分组路径

这一轮继续补的是一个更靠近 UI 和草稿态的缺口。

之前的问题是：

- 笔记列表在 KeePass 分组筛选下发起“新建笔记”时，NoteCategoryFilter.KeePassGroupFilter 会被转换成只带 keepassDatabaseId 的 NoteDraftStorageTarget。
- 也就是说，groupPath 在进入 AddEditNoteScreen 之前就已经丢了。
- 后续 NoteEditorViewModel 和 RememberedStorageTarget 也都只记 databaseId，不记 keepassGroupPath。

本轮已修复：

- NoteDraftStorageTarget 增加 keepassGroupPath。
- NoteEditorUiState / NoteEditDraft 增加 keepassGroupPath。
- AddEditNoteScreen 在保存时把 keepassGroupPath 一并传给 NoteViewModel。
- RememberedStorageTarget 与 SettingsManager 增加 keepassGroupPath 持久化。
- KeePassGroupFilter 转草稿目标时，不再丢失 groupPath。

这一步的意义是，笔记不仅底层写入主链开始 UUID-first，连上游“从哪个 KeePass 分组发起创建”这层上下文也不再在 UI 流转过程中被静默抹掉。

### 3.10 本轮继续补齐：TOTP 新建与编辑入口不再丢失 KeePass 分组路径

这一轮继续把同类问题扩展到 TOTP。

之前的问题是：

- TOTP 的 saveTotpItem 只接 keepassDatabaseId，不接 keepassGroupPath。
- AddEditTotpScreen 只记住数据库，不记目标组路径。
- SimpleMainScreen 和 MainActivity 中，用于“根据当前筛选给新建项带默认存储目标”的结构也只保留了 keepassDatabaseId。
- 结果就是从 KeePass 分组筛选进入新建验证器时，目标组信息在进入编辑页前就已经丢失。

本轮已修复：

- TotpViewModel.saveTotpItem 增加 keepassGroupPath 参数，并在未绑定密码时参与目标组解析。
- AddEditTotpScreen 增加 initialKeePassGroupPath 和 keepassGroupPath 状态，并在 remembered storage target 中持久化。
- SimpleMainScreen 的 TOTP 默认目标结构补充 keepassGroupPath。
- MainActivity 的 TOTP 路由默认目标结构补充 keepassGroupPath。
- 新建与编辑 TOTP 的 onSave 链路现在会把 keepassGroupPath 一直传到 TotpViewModel。

这意味着 TOTP 现在和笔记一样，从 KeePass 分组视图发起“新建”时，不会在 UI 层先把目标组静默丢掉，然后再回退到数据库级别写入。

### 3.11 本轮继续补齐：银行卡/证件新建入口继承当前 KeePass 分组目标

这一轮继续把同类问题扩展到卡片钱包。

之前的问题是：

- AddEditBankCardScreen 和 AddEditDocumentScreen 只记 keepassDatabaseId，不记 keepassGroupPath。
- remembered storage target 在银行卡/证件场景下也只会恢复数据库，不会恢复目标组路径。
- CardWalletScreen 虽然已经能把当前 KeePass 分组筛选编码为 CARD_WALLET 分类筛选状态，但这个 groupPath 没有进入新建编辑页。
- 结果就是从 KeePass 分组筛选进入新建银行卡/证件时，写入会退化成“只知道数据库，不知道目标组”。

本轮已修复：

- AddEditBankCardScreen 增加 keepassGroupPath 状态，并在编辑已有项时恢复 item.keepassGroupPath。
- AddEditDocumentScreen 增加 keepassGroupPath 状态，并在编辑已有项时恢复 item.keepassGroupPath。
- 两个编辑页在新建模式下，都会优先从 CARD_WALLET 当前分类筛选中读取 KeePass groupPath；如果没有当前筛选，再回退到 remembered storage target。
- 新建银行卡/证件时，onSave 链路现在会把 keepassGroupPath 传给 BankCardViewModel.addCard / DocumentViewModel.addDocument。
- 银行卡/证件的 remembered storage target 现在也会持久化 keepassGroupPath，并在切换到 Bitwarden 或切换 KeePass 数据库时清空过期 groupPath。

这意味着卡片钱包现在和笔记、TOTP 一样，从 KeePass 分组视图发起“新建”时，不会在进入 ViewModel 前先把目标组上下文丢掉。

### 3.12 本轮继续补齐：回收站恢复链显式回写 KeePass groupUuid

这一轮开始收口 Trash 恢复链。

之前的问题是：

- TrashViewModel 在恢复 KeePass 条目时，只把服务层解析出来的 restored groupPath 写回本地对象。
- 但本地对象上的 keepassGroupUuid 会沿用删除前的旧值。
- 如果恢复目标已经因为 previousParentGroup、分组改名、分组删除后的回退而发生变化，那么“只更新 path、不更新 UUID”会把本地条目留在不一致状态。

本轮已修复：

- KeePassKdbxService 新增 KeePassRestoreTarget，恢复目标现在同时携带 groupPath 和 groupUuid。
- restore 解析优先继续走 UUID-first：优先 groupUuid，其次匹配到的 entry.previousParentGroup，最后才回退到 path。
- KeePassWorkspaceRepository 和 KeePassCompatibilityBridge 已把新的 restore target 接口透传出来。
- TrashViewModel 的单条恢复和批量恢复都改为使用 KeePassRestoreTarget，并在本地恢复时同步回写 keepassGroupPath 与 keepassGroupUuid。
- 当恢复结果最终无法定位到有效组时，回收站恢复链会显式把 keepassGroupUuid 清空，而不是保留陈旧 UUID。

这意味着回收站恢复现在不再只是“路径恢复”，而是会把 KeePass 目标组身份一起纠正回本地对象，继续向 DX 风格的 native identity 模型收口。

### 3.13 本轮继续补齐：密码删除链开始从混合 ViewModel 拆到 KeePass 专属执行器

这一轮开始处理 KeePass 删除体感延迟问题，并把执行逻辑从混合 ViewModel 中拆出来。

之前的问题是：

- PasswordViewModel.deletePasswordEntry 同时负责 UI 决策、Bitwarden tombstone、KeePass 删除策略和本地删除状态更新。
- KeePass 删除部分还是内联在 PasswordViewModel 里，无法单独优化，也不利于后续把 KeePass 逻辑进一步独立成专属执行层。
- 当某个 KeePass 数据库没有 recycle bin 时，每次删除都会先失败一次，再 fallback 到直删，连续删除时会重复付出相同的失败成本。

本轮已修复：

- 新增 KeePassPasswordDeleteExecutor，开始把 KeePass 密码删除策略从 PasswordViewModel 中抽离。
- PasswordViewModel.deletePasswordEntry 现在不再内联 KeePass 删除细节，而是调用专属执行器。
- 执行器内部增加了 recycle bin unavailable 的进程内缓存；同一个 KeePass 数据库一旦确认没有 recycle bin，后续删除会直接走直删，不再重复先失败一次。
- 这一步只影响 KeePass 密码删除链，不影响 Monica 本地库和 Bitwarden 的删除语义，UI 层也继续复用原有流程。

这意味着 KeePass 删除现在开始具备独立执行层的形态，后续要继续做 local-first 或把 BankCard/Document/Note/TOTP 的删除也抽出来时，已经有了第一块可复用的专属基础设施。

### 3.14 本轮继续补齐：SecureItem 删除链也改为 KeePass 专属执行器

这一轮继续把 KeePass 删除逻辑从混合 ViewModel 中抽离，范围从密码扩展到 SecureItem。

之前的问题是：

- NoteViewModel、TotpViewModel、BankCardViewModel、DocumentViewModel 都各自内联了一份 KeePass 删除逻辑。
- 这四条链都重复包含“先尝试 move to recycle bin，失败再 fallback 直删”的相同策略代码。
- 即使之前密码删除已经开始独立，SecureItem 这四类仍然会继续把 KeePass 细节散落在各自 ViewModel 中。

本轮已修复：

- 新增 KeePassSecureItemDeleteExecutor，作为 Note/TOTP/BankCard/Document 共用的 KeePass 删除执行层。
- NoteViewModel.deleteNote / deleteNotes 不再内联 KeePass 删除细节，改为调用专属执行器。
- TotpViewModel.deleteTotpItem 改为调用专属执行器。
- BankCardViewModel.deleteCard 和 DocumentViewModel.deleteDocument 也都改为调用专属执行器。
- 执行器内部同样带有 recycle bin unavailable 的进程内缓存，避免同一 KeePass 数据库对 SecureItem 删除重复走无效的失败再回退路径。

这意味着 KeePass 删除执行层已经不再只覆盖密码，SecureItem 这四条主要删除链也开始统一走专属执行器。到这里，“UI 共用、KeePass 删除逻辑独立”已经有了更完整的骨架。

### 3.15 本轮继续补齐：密码 KeePass 软删除改为 local-first

这一轮开始直接处理删除体感延迟，而不再只是拆执行器。

之前的问题是：

- 即使密码删除已经改为 KeePass 专属执行器，PasswordViewModel 在 trashEnabled 场景下仍然会先等待 KeePass 删除完成，再把本地条目标记为已删除。
- 这意味着用户点击删除后，UI 仍要等完整的 KDBX 删除链跑完，连续删除时体感依旧接近秒级。

本轮已修复：

- PasswordViewModel 的密码软删除现在改为 local-first：先本地移入回收站，再后台同步 KeePass 删除。
- 这一步只应用于 KeePass 密码的 soft delete 场景，不改变 Bitwarden tombstone 和永久删除的现有语义。
- 后台 KeePass 删除成功时只记录同步完成；失败时会把本地条目从回收站状态回滚，避免把 UI 维持在错误的“已删”状态。

这意味着密码删除现在已经开始从“同步等待远端完成”转向“本地先响应、远端随后收敛”的执行模型，体感延迟会比之前明显下降，也更接近 KeePassDX 对原生操作的交互预期。

### 3.16 本轮继续补齐：SecureItem 软删除也改为 local-first

这一轮把同样的执行策略扩展到 Note、TOTP、BankCard、Document。

之前的问题是：

- 虽然 SecureItem 删除链已经改为 KeePass 专属执行器，但 soft delete 仍然会先等待 KeePass 删除完成，再把本地项目移入回收站。
- 这意味着 SecureItem 在 KeePass 场景下的体感延迟，仍然保留了同步等待远端 KDBX 操作的特征。

本轮已修复：

- NoteViewModel.deleteNote / deleteNotes 的 KeePass soft delete 改为 local-first。
- TotpViewModel.deleteTotpItem 的 KeePass soft delete 改为 local-first。
- BankCardViewModel.deleteCard 和 DocumentViewModel.deleteDocument 的 KeePass soft delete 也改为 local-first。
- 这些改动只作用于 `softDelete && 非 Bitwarden` 场景；Bitwarden tombstone 和永久删除仍然沿用原有保守语义。
- 每条链在本地先移入回收站后，都会后台同步 KeePass 删除；如果后台失败，会把本地条目从回收站状态回滚，避免形成假成功状态。

这意味着到目前为止，密码、Note、TOTP、BankCard、Document 这五条主要 KeePass 软删除链，都已经从“同步等待 KeePass 完成”转向“本地先响应、远端随后收敛”的执行模型。

### 3.17 本轮继续补齐：主要 KeePass 更新链开始收束到专属执行器

这一轮继续从删除链扩展到更新链。

之前的问题是：

- PasswordViewModel、NoteViewModel、TotpViewModel、BankCardViewModel、DocumentViewModel 都各自内联了一份 KeePass 更新同步逻辑。
- 这些逻辑本质上都在做相同的事：本地先更新，再根据 oldKeepassId/newKeepassId 决定是否删除旧库条目、更新新库条目。
- 即使删除链已经开始独立，更新链仍然把 KeePass 原生变更细节散落在各个 ViewModel 中。

本轮已修复：

- 新增 KeePassPasswordUpdateExecutor，收束密码更新时的 KeePass 同步逻辑。
- 新增 KeePassSecureItemUpdateExecutor，收束 SecureItem 更新时的 KeePass 同步逻辑。
- PasswordViewModel.updatePasswordEntryInternal 现在通过 KeePassPasswordUpdateExecutor 同步 KeePass 更新。
- NoteViewModel.updateNote、TotpViewModel.saveTotpItem 的更新分支、BankCardViewModel.updateCard、DocumentViewModel.updateDocument 都已切到 KeePassSecureItemUpdateExecutor。
- TotpViewModel.moveToKeePassRoot / moveToKeePassGroup 也已改为走同一个 SecureItem 更新执行器。

这意味着 KeePass 执行层现在不再只覆盖删除，主要更新链也开始从混合 ViewModel 中收束出来，继续向“UI 共用、KeePass 执行独立”的方向推进。

### 3.18 本轮继续补齐：主要 KeePass 创建链开始收束到专属执行器

这一轮继续把相同的收口思路扩展到创建链。

之前的问题是：

- PasswordViewModel、NoteViewModel、TotpViewModel、BankCardViewModel、DocumentViewModel 都各自内联了一份 KeePass 创建后写回逻辑。
- Password 创建失败时已经会回滚本地行，但 Note、TOTP、银行卡、证件创建失败时仍可能把“本地已创建、KeePass 未创建”的半成功状态留在库里。
- Note、BankCard、Document 的创建分支还没有统一复用现有的 KeePass 身份解析规则，新建时的 groupUuid 保持也不一致。

本轮已修复：

- 新增 KeePassPasswordCreateExecutor，收束密码创建后的 KeePass 写入与失败回滚逻辑。
- 新增 KeePassSecureItemCreateExecutor，收束 SecureItem 创建后的 KeePass 写入与失败回滚逻辑。
- PasswordViewModel.createPasswordEntryInternal 现在通过 KeePassPasswordCreateExecutor 完成本地插入、KeePass 写入和失败回滚。
- NoteViewModel.addNote、TotpViewModel.saveTotpItem 的创建分支、BankCardViewModel.addCard、DocumentViewModel.addDocument 都已切到 KeePassSecureItemCreateExecutor。
- Note、BankCard、Document 创建时现在也统一走 resolveKeePassMutationIdentity，和更新链一样按同一套 KeePass 身份规则生成 entryUuid，并在需要时保留 groupPath/groupUuid 语义。
- SecureItem 主创建链在 KeePass 写入失败时，现在会回滚刚刚插入的本地记录，避免留下本地与 KDBX 分叉的脏状态。

这意味着到目前为止，KeePass 的主要创建、更新、删除三类写操作都已经开始从混合 ViewModel 中抽离，执行层独立化进入下一阶段。

### 3.19 本轮继续补齐：主要存储迁移链开始统一到 KeePass 更新执行器

这一轮继续处理“不是编辑也不是新建，而是迁移存储位置”的剩余缺口。

之前的问题是：

- NoteViewModel.moveNoteToStorage 只更新本地记录，不会把迁移结果同步回 KeePass。
- BankCardViewModel.moveCardToStorage 和 DocumentViewModel.moveDocumentToStorage 虽然会同步 KeePass，但仍然各自内联 delete old / update new 的细节。
- TotpViewModel.moveToKeePassDatabase、moveToKeePassGroup、moveToBitwardenFolder 还没有统一走 KeePass 身份解析，迁移时 entryUuid/groupUuid 与旧库清理语义并不一致。

本轮已修复：

- NoteViewModel.moveNoteToStorage 现在会在本地更新后调用 KeePassSecureItemUpdateExecutor，同步迁移结果到 KeePass。
- BankCardViewModel.moveCardToStorage 和 DocumentViewModel.moveDocumentToStorage 已移除内联 KeePass 同步细节，改为统一走 KeePassSecureItemUpdateExecutor。
- TotpViewModel.moveToKeePassDatabase 与 moveToKeePassGroup 现在会先走 resolveKeePassMutationIdentity，再统一通过 KeePassSecureItemUpdateExecutor 处理旧库删除与新库更新。
- TotpViewModel.moveToBitwardenFolder 现在会在离开 KeePass 时显式清空 keepassDatabaseId、keepassGroupPath、keepassEntryUuid、keepassGroupUuid，并通过 KeePassSecureItemUpdateExecutor 删除旧的 KeePass 条目。

这意味着 KeePass 不再只有“创建、编辑、删除”三条主链开始独立，连“存储位置迁移”这类最容易出现残留节点和 UUID 失真的链路，也开始收敛到统一执行模型。

### 3.20 本轮继续补齐：回收站恢复链改为 local-first，并补齐 KeePass 同步日志

这一轮直接针对回收站恢复体感慢的问题处理执行模型。

之前的问题是：

- TrashViewModel.restoreItem 和 restoreCategory 会先等待 KeePass 恢复完成，再把本地条目从回收站状态改回正常状态。
- 这意味着恢复操作的 UI 体感完全受 KDBX 写入耗时支配，即使本地恢复本身只是一条简单的数据库更新。
- 删除链虽然已经改为 local-first，但恢复链仍然保留着“远端优先、UI 后响应”的旧模型。

结合日志也可以确认另一件事：

- 删除并不只是 UI 快。PasswordViewModel 在本地移入回收站后，随后会打印 `KeePass trash delete synced`，说明后台 KDBX 写入确实完成了，只是完成时间晚于 UI 响应。

本轮已修复：

- TrashViewModel.restoreItem 改为 local-first：先恢复本地，再后台补写 KeePass。
- TrashViewModel.restoreCategory 也改为 local-first：先批量恢复本地，再后台批量补写 KeePass。
- 对 KeePass 项目，后台恢复失败时现在会把对应本地条目回滚回回收站状态，避免出现“UI 恢复了但 KDBX 没恢复”的假成功状态。
- 单条和批量恢复都新增了 `KeePass restore synced` / 失败回滚日志，便于后续直接从 logcat 判断恢复是否真正落到 KDBX。

这意味着回收站恢复现在和删除一样，开始转向“本地先响应、KeePass 随后收敛”的执行模型，体感会明显比之前更接近删除链。

### 3.21 本轮继续补齐：增强 KDBX 强制落盘，提升跨客户端可见性

这一轮继续处理“Monica 写完后，另一个打开同一数据库的 KeePass 客户端是否能更快看到变化”的问题。

已确认的事实是：

- Monica 当前的 KeePass 写链本身已经是立即执行的，不存在额外的延迟提交队列。
- 但另一个客户端能否“马上显示”，除了 Monica 是否真的写到文件，还取决于对方客户端是否会立刻检测文件变化并重载内存中的数据库。

本轮已增强：

- KeePassKdbxService.writeInternal 现在在临时文件写入和直接回退写入两条路径上都显式 flush + fsync。
- KeePassKdbxService.writeExternal 现在改为通过文件描述符写入，并显式 flush + fsync，而不只是简单写完关闭流。

这意味着从 Monica 这一侧，数据库变更现在会更快、更明确地落到真实文件本身，能最大化其他 KeePass 客户端检测到文件变化的机会。

当前剩余限制：

- 如果另一个客户端已经把数据库完整加载到内存里，但没有立即重载或刷新，Monica 无法跨进程强制它立刻更新当前列表。
- 也就是说，Monica 现在可以保证“尽快真实落盘”，但不能单方面保证“另一个客户端当前界面无感自动立刻显示新条目”。

## 4. 当前所处阶段

当前路线可以概括为一句话：

先完成访问收口，再逐步替换内核，把 Monica 的 KeePass 管理从兼容式同步模型推进到 KeePass 原生身份模型。

现在已经不是纯分析阶段，也不是只修删除补丁的阶段，而是已经进入“原生身份改造第一批代码落地”的阶段。

当前阶段完成度判断：

- KeePass 访问收口：已完成大半，核心 ViewModel 已处理。
- 原生 entry UUID 落库：已完成。
- 原生 group UUID 落库：已完成字段接入，后续仍需扩大使用范围。
- 服务层 UUID-first 匹配：已完成第一批核心改造。
- 次级编辑器、辅助流转路径：仍未全部覆盖。

## 5. 已验证结果

最近一轮改造后，已做过 Kotlin 编译验证。

成功命令：

```powershell
& ".\Monica for Android\gradlew.bat" -p ".\Monica for Android" :app:compileDebugKotlin --stacktrace --console=plain
```

验证结果：

- 编译通过。
- 期间出现过 KeePassKdbxService 新参数 groupUuid 漏传的编译错误，已修复。
- 期间发现过 PasswordDatabase 迁移注册重复，已清理。

结论：当前这批 KeePass 原生身份改造代码处于可编译状态。

## 6. 当前还没做完的部分

虽然关键方向已经立住，但还没有到“完美 KeePass 管理器”的程度。当前明确未完成的主要点有：

- 仍有部分次级创建/编辑入口没有完全接入 UUID 传播。
- 分组操作虽然已开始携带 group UUID，但还没有全面做到 group UUID 驱动。
- keepassGroupPath 仍然在一些流程里承担了过多定位意义，后续要继续降级为展示或兼容信息。
- restore、move、editor、passkey 等边缘路径还需要继续补齐。

其中目前仍需继续排查的重点是：

- TOTP 的编辑与存储迁移链是否存在和笔记/银行卡/证件相同的 UUID 丢失窗口。
- TrashViewModel 的恢复回写是否已经完整利用现有 keepassEntryUuid / keepassGroupUuid，而不只是路径回填。
- 次级 editor state 是否还存在“UI 层只记住 databaseId，提交时让业务层被动猜测 group/uuid”的残留点。

其中第一项已完成主链修复，当前排查重点进一步收缩为：

- TrashViewModel 的恢复路径和批量恢复路径。
- editor state 与辅助 UI 流转层的 KeePass 元数据保真度。
- Passkey 是否需要进入同级别的 KDBX 原生身份模型。

其中 editor state 方向已经补完一块关键缺口：笔记草稿/记忆目标现已保留 keepassGroupPath。
接下来更值得排查的是：

- TrashViewModel 的恢复路径与批量恢复路径是否还存在 UUID 已有但未被充分利用的场景。
- 其他编辑页是否也有类似“UI 只保存 databaseId，不保存 KeePass 目标组”的问题。
- Passkey 是否仍然应该维持目标元数据级别，还是进入完整原生节点身份链。

其中“其他编辑页”这一项目前已经确认并处理了两条主链：

- Note 新建/编辑入口
- TOTP 新建/编辑入口

接下来优先级更高的候选是：

- 卡片钱包相关的新建入口是否也存在相同的 groupPath 丢失问题。
- TrashViewModel 的恢复链是否还需要进一步从“路径恢复”推进到更显式利用已有 UUID 状态。

补充说明：

- Passkey 当前虽然已经有 keepassDatabaseId 和 keepassGroupPath 等目标字段，但目前更偏向“目标归属元数据”，还没有形成和 Password/Note/SecureItem 同级别的 KeePass 原生节点写入与 UUID 回写链路。
- 因此 Passkey 这部分后续会单独评估，避免在没有完整 KDBX 写入链的情况下先盲目扩 schema。

目前我已经确认过，后续优先排查的文件包括：

- NoteEditorViewModel.kt
- PasskeyCreateActivity.kt
- 以及其他仍然只带 keepassDatabaseId、未完整携带 entry/group UUID 的辅助路径

## 7. 接下来准备继续做什么

下一阶段的工作重点会按这个顺序推进：

1. 继续把剩余的 KeePass 创建、编辑、导入、恢复入口补齐 UUID 传播。
2. 让更多 group 操作从路径驱动改成 group UUID 驱动。
3. 继续压缩 MonicaLocalId 和标题字段匹配在主流程中的存在感，让它们只承担历史兼容职责。
4. 在稳定后，再回头看回收站恢复、跨组移动、边缘编辑器等复杂场景是否已经达到接近 KeePassDX 的行为质量。

## 8. 你可以如何使用这份文档

后面我会持续按这份文档更新，不再让进度只散落在对话里。

建议你看这几个部分就能快速判断进展：

- 看“已完成工作”：知道已经改了哪些层。
- 看“当前所处阶段”：知道我现在是在修补，还是在做主线重构。
- 看“已验证结果”：知道当前代码是不是至少可编译。
- 看“接下来准备继续做什么”：知道下一步不会跑偏。