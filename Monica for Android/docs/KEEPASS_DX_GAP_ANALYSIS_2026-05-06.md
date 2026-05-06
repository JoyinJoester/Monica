# Monica Android KeePass 链路与 KeePassDX 差距分析

日期：2026-05-06

## 1. 背景与结论

用户反馈的两个问题是：

- 管理 KeePass 数据库时“不跟手”。
- 管理多个 `kdbx` 时数据加载很慢。

从当前代码看，这两个现象不是单个页面卡顿点，而是 Monica KeePass 链路的结构性结果：Monica 仍然把 KeePass 当成 Monica 密码体系的一组外部数据源来管理，而 KeePassDX 是 KDBX-first 的独立客户端模型。

最关键的差距是：

1. Monica 数据库管理页会在数据库列表出现后主动验证所有库，验证会解密打开 KDBX；多个库时会排队。
2. Monica 底层为了稳定性使用全局单线程 decode 和全局 mutation mutex，多库场景下所有库共享瓶颈。
3. Monica 的工作区加载把密码、SecureItem、分组拆成多次读取/遍历，缺少“一次打开，一次构建快照”的统一模型。
4. Monica 仍保留大量 Room 投影和兼容桥逻辑，KeePass 条目的真实身份、父子关系、回收站、历史等没有完全成为业务主模型。
5. KeePassDX 用绑定服务持有单个活动数据库，并用最近文件历史展示轻量元信息；Monica 则倾向于同时管理和检查多个数据库。

## 2. Monica 当前 KeePass 链路

### 2.1 数据库管理页

`LocalKeePassScreen` 同时订阅所有数据库列表、内部库、外部库、远端库和验证状态。列表数据一变化，就触发所有数据库的验证：

- `LocalKeePassScreen.kt:65-70`：页面订阅 `allDatabases`、`internalDatabases`、`externalDatabases`、`remoteDatabases`、`verificationStates`。
- `LocalKeePassScreen.kt:121-122`：`LaunchedEffect(allDatabases.map { it.id })` 调用 `ensureVerificationForDatabases`。
- `LocalKeePassViewModel.kt:178-184`：对每个未知库触发 `verifyDatabaseCredentials`。
- `LocalKeePassViewModel.kt:189-204`：验证内部调用 `workspaceRepository.verifyDatabase`，并统计解密耗时。

问题在于：验证不是读取 Room 元信息，而是真正解密 KDBX。多个数据库同时进入页面时，首屏会把 N 个库都加入后台验证队列。用户此时只是想浏览数据库列表，却被迫等待解密任务消耗 CPU、IO 和 KDF 时间。

### 2.2 KDBX 服务与缓存

`LocalKeePassViewModel` 自己持有一个 `KeePassKdbxService`，再包装成 `KeePassWorkspaceRepository` 和 `KeePassCompatibilityBridge`：

- `LocalKeePassViewModel.kt:109-111`：构造 `KeePassKdbxService`、`KeePassWorkspaceRepository`、`KeePassCompatibilityBridge`。
- `KeePassKdbxService.kt:286`：服务内部有 `loadedDatabaseCache`。
- `KeePassKdbxService.kt:3105-3168`：缓存按 databaseId 保存，内部文件通过签名校验，未知来源缓存 TTL 为 60 秒。

这个缓存能避免同一个 service 实例内的重复 decode，但它不是全局活动数据库服务。业务 ViewModel、页面、兼容桥路径中仍可能创建不同的 `KeePassKdbxService` 实例。跨实例只靠静态失效集合通知，不等于共享打开后的数据库对象。

### 2.3 全局串行瓶颈

为了规避 kotpass decode 的稳定性问题，Monica 做了强串行：

- `KeePassKdbxService.kt:176-188`：全局 `globalDecodeMutex`。
- `KeePassKdbxService.kt:181`：固定单线程 `decodeDispatcher`。
- `KeePassKdbxService.kt:2736`：decode 在单线程 dispatcher 内执行。
- `KeePassKdbxService.kt:183`、`2684-2691`：所有数据库写入走全局 `globalMutationMutex`。

这些设计能降低崩溃和并发写冲突风险，但副作用很明确：多个 kdbx 的验证、打开和写入会互相排队。用户管理多库时，越多库越明显。

### 2.4 工作区加载

`KeePassWorkspaceRepository.loadWorkspace` 当前按三段依次读取：

- `KeePassWorkspaceRepository.kt:35-47`：依次调用 `readPasswordEntries`、`readSecureItems`、`listGroups`。
- `KeePassKdbxService.kt:595-613`：`readPasswordEntries` 会加载数据库、收集 entry contexts、构建字段引用上下文、映射数据。
- `KeePassKdbxService.kt:617-636`：`listGroups` 再遍历分组。
- `KeePassKdbxService.kt:837`：`readSecureItems` 是另一条读取链。

即使第一次读取后缓存变热，逻辑层仍然是多次扫描和多次模型转换。对于大库或包含多类型 Monica 自定义字段的库，加载成本容易放大。

### 2.5 远端同步耦合在写路径

`writeDatabase` 在写入本地后，如果是远端来源，会继续同步远端：

- `KeePassKdbxService.kt:2916-2947`：写入、远端同步、缓存更新在同一方法内完成。
- `KeePassKdbxService.kt:2936`：调用 `syncRemoteWorkingCopy`。
- `KeePassKdbxService.kt:2959`：进入具体 WebDAV / OneDrive / Google Drive 写入与冲突处理。

这意味着某些编辑动作的“完成感”会被网络、远端版本检查、冲突合并拖慢。KeePassDX 更偏向显式保存、重载、合并和数据库变更提示。

## 3. KeePassDX 参考模型

KeePassDX 的链路更像独立 KeePass 客户端：

### 3.1 最近文件列表是轻量元信息

- `FileDatabaseHistoryAction.kt:34`：最近数据库历史独立管理。
- `FileDatabaseHistoryAction.kt:69-105`：列表读取历史记录、存在性、最后修改时间、大小等元信息，不批量解密每个 KDBX。
- `FileDatabaseHistoryAction.kt:124-171`：打开成功后才更新最近文件与 key file 位置。

这能保证多库列表展示很轻，不会因为用户有很多 KDBX 就触发很多 KDF/解密任务。

### 3.2 单个活动数据库由服务持有

- `ContextualDatabase.kt:10`、`34`：`ContextualDatabase` 是单例式数据库对象。
- `DatabaseTaskNotificationService.kt:95`：服务持有当前 `mDatabase`。
- `DatabaseTaskNotificationService.kt:315`：服务取 `ContextualDatabase.getInstance()`。
- `DatabaseTaskProvider.kt:94-117`：Activity 通过 Provider 从服务取回当前数据库。

这个模型下，UI 和后台任务围绕同一个活动 KDBX 工作，减少重复打开和重复转换。

### 3.3 加载、保存、节点操作都有任务边界

- `DatabaseTaskProvider.kt:267`：打开库通过 `startDatabaseLoad` 进入服务任务。
- `LoadDatabaseRunnable.kt:35-68`：加载任务先清空旧数据库，再读取 URI、解密、加载数据，失败后清理。
- `SaveDatabaseRunnable.kt:32-61`：保存任务写临时文件，避免错误导致文件损坏。
- `ActionNodeDatabaseRunnable.kt:27-49`：节点操作先修改内存树，再进入保存任务。
- `DatabaseTaskNotificationService.kt:626-641`：任务在 `Dispatchers.IO` 执行，完成后回主线程通知。
- `DatabaseTaskNotificationService.kt:664-668`：加载过程中有“获取数据库 key / 解密数据库”阶段进度。

这套任务边界让用户知道当前在做什么，也避免 Compose 页面或 ViewModel 自己堆很多不可见的解密任务。

### 3.4 KDBX 原生节点是主模型

KeePassDX 的 `Database` 持有 root group、entry/group parent、node id、索引、回收站、模板、历史等原生概念：

- `Database.kt:72-110`：数据库对象持有 KDB/KDBX 实例、只读状态、加载时间、binary cache。
- `Database.kt:398-417`：root group 是数据库核心对象。
- `Database.kt:892-907`：搜索直接基于数据库对象创建虚拟分组。
- `Database.kt:1063-1122`：entry/group 增删直接操作 KDBX 节点和父组关系。

Monica 已经开始补 `keepassEntryUuid`、`keepassGroupUuid`，但整体仍被 PasswordEntry / SecureItem 等 Room 模型牵引。

## 4. Monica 主要不足

### P0：数据库列表页不应批量解密验证所有库

当前 `LocalKeePassScreen` 打开后会触发所有库验证。多个库时，这会直接造成“进入管理页不跟手”。

建议：

- 数据库列表只展示 Room 元信息：名称、位置、来源、上次验证状态、上次 entry count、上次打开时间、文件 size / mtime。
- 验证改为按需：用户点击库、下拉刷新、显式“检查凭据”时再解密。
- 对远端库默认只展示同步状态，不自动解密远端工作副本。
- 若需要健康检查，用低优先级队列，并设置并发上限和可取消任务。

### P0：decode / mutation 锁粒度过粗

全局 decode 单线程和全局 mutation mutex 让不同数据库互相阻塞。稳定性可以理解，但多库用户会明显感知慢。

建议：

- 保留 decode 稳定性保护，但建立 per-database 操作队列。
- 写入 mutex 改成 databaseId 维度，只有跨库移动/合并才拿多库锁。
- 给验证任务设置去重：同一 databaseId 已在验证时，新请求复用同一 Deferred / Flow。
- 为前台点击打开的数据库设置更高优先级，后台验证不能挡住用户操作。

### P0：缺少 KeePass 活动数据库会话模型

Monica 的缓存存在于 `KeePassKdbxService` 实例内，而 KeePassDX 是服务级单活动数据库。Monica 多入口、多 ViewModel 的兼容桥会让缓存收益被稀释。

建议：

- 引入 `KeePassSessionManager` 或 `KeePassVaultSession`：按 databaseId 管理打开态、凭据态、数据库快照、dirty 状态、最近访问时间。
- 页面、业务 ViewModel、批量操作都通过 session 获取活动库，不直接 new service。
- session 暴露 `StateFlow<OpenState>`、`StateFlow<Progress>`、`Flow<Snapshot>`。
- 支持锁定/关闭库，释放敏感内存和 binary cache。

### P1：工作区加载需要“一次打开，一次快照”

现在 `loadWorkspace` 先读密码，再读 SecureItem，再读分组。它们共享缓存，但仍有多次遍历与模型转换。

建议：

- 在 `KeePassKdbxService` 增加 `loadSnapshot(databaseId, options)`，一次加载后同时产出 groups、password entries、secure items、passkeys、索引摘要。
- 构建 entry/group UUID 索引，后续增删改查优先走索引。
- 支持分页/懒加载：列表首屏只需要 group tree 和当前 group 的条目摘要，详情页再解字段引用、附件、历史。
- 字段引用解析和复杂 custom data 解析延迟到详情页或后台渐进更新。

### P1：Room 投影与 KDBX 原生模型仍然互相牵扯

Monica 当前数据类里已有 KeePass UUID 字段，但主业务仍以 `PasswordEntry`、`SecureItem`、`PasskeyEntry` 等 Monica 模型为中心。`KeePassCompatibilityBridge` 的方法名里也保留 `Legacy`，说明这仍是过渡层。

风险：

- KDBX 分组改名、移动、回收站恢复后，Room 投影可能滞后。
- 同一条目可能同时有本地行 ID、KeePass UUID、group path、group UUID，多套身份不一致。
- 移动/删除/恢复容易退回 path 或本地 ID 推断。

建议：

- KeePass 工作区 UI 使用 `KeePassNodeRef(databaseId, entryUuid/groupUuid)` 做主身份。
- `keepassGroupPath` 降级为展示字段，操作不要依赖 path。
- Room 投影只服务聚合列表和搜索缓存，不能作为 KDBX 写入的主真相。
- 所有 KeePass 写入返回新的 node ref / group ref，并统一更新投影。

### P1：写路径把本地保存和远端同步绑得太紧

远端库写入时，`writeDatabase` 会同步远端并处理冲突。网络慢时，用户会觉得“保存卡住”。

建议：

- 区分“本地工作副本已保存”和“远端同步完成”两个状态。
- 编辑后先快速落工作副本，远端同步进后台任务队列。
- UI 显示 pending sync / conflict / synced。
- 冲突合并与覆盖操作显式化，避免普通编辑动作承担全部远端成本。

### P2：进度与取消能力不足

KeePassDX 的加载任务能报告 key 派生、解密等阶段。Monica 目前更多是 `OperationState.Loading("...")` 级别，用户不知道卡在哪里。

建议：

- 为打开、验证、保存、同步定义阶段：读取文件、派生 key、解密、构建索引、写入、远端同步。
- 所有长任务暴露 progress Flow，并允许取消后台验证。
- 数据库列表中显示“正在验证第 N 个 / 可取消”，避免误以为页面卡死。

### P2：KeePass 客户端深度能力仍不足

相较 KeePassDX，Monica 还需要继续补齐：

- KDBX 原生历史版本、模板组、custom icon / attachment 的完整管理。
- 数据库变更检测后的重载、保存覆盖、合并三分支选择。
- 只读模式、权限撤销、外部文件变更提示。
- 大附件和图标 binary cache 的内存/磁盘策略。
- 数据库级设置：KDF、压缩、默认用户名、回收站、历史限制等。

## 5. 推荐改造顺序

### 第一阶段：先解决“不跟手”

1. 移除列表页自动批量验证。
2. 数据库卡片改用上次验证缓存和轻量文件元信息。
3. 验证任务改按需触发，并支持去重、取消、后台低优先级。
4. 将 `globalMutationMutex` 拆为 per-database mutex。

### 第二阶段：建立 KeePass 会话层

1. 新增 `KeePassSessionManager`。
2. 把 `LocalKeePassViewModel`、`KeePassWorkspaceViewModel`、业务兼容桥统一接入 session。
3. session 维护打开态、dirty、progress、snapshot cache。
4. 工作区加载改为一次快照构建。

### 第三阶段：KDBX-first 工作区

1. 工作区列表、分组树、详情页直接使用 KeePass node ref。
2. Room 投影变成缓存和跨模块聚合用途。
3. 删除/移动/恢复/重命名全部 UUID-first。
4. 远端同步异步化，冲突处理产品化。

## 6. 判断依据摘要

Monica 的慢点主要来自“列表页主动打开所有库”和“全局串行化 KDBX decode/write”。KeePassDX 的优势不是某个算法更快，而是产品链路更轻：最近文件列表不解密，打开一个库才进入有进度的任务服务，服务持有活动数据库，节点操作围绕内存中的 KDBX 树执行。

因此，Monica 优先不应该先做 UI 微调，而应该先把“什么时候解密、谁持有打开态、多个库如何排队、远端同步是否阻塞保存”这四件事重新收口。
