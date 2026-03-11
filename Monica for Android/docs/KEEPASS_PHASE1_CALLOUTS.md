# KeePass 第一阶段调用点收口清单

## 1. 目的

本清单用于执行 KeePass 第一阶段收口，只处理 KeePass 管理域，不改变 Bitwarden 与 Monica 本地数据库的行为语义。

第一阶段目标不是重写所有业务，而是先把 KeePass 的直接访问点集中起来，停止继续扩散：

- 新入口统一走 KeePassWorkspaceRepository / KeePassCompatibilityBridge。
- 旧入口不再直接 new KeePassKdbxService。
- 业务 ViewModel 中的 KeePass 调用先收口到兼容桥，再进入后续阶段替换节点身份模型。

## 2. 当前状态

已完成的第一步：

- 新增 KeePassWorkspaceRepository。
- 新增 KeePassCompatibilityBridge。
- 新增 KeePassWorkspaceViewModel。
- LocalKeePassViewModel 已开始通过仓库访问部分 KeePass 能力。

未完成的核心问题：

- 多个业务 ViewModel 仍然直接持有 KeePassKdbxService。
- 多个 Compose 页面仍然自己创建 KeePassKdbxService 只为拉取分组。
- TrashViewModel 仍然直接负责 KeePass 永久删除与恢复路径推断。

## 3. P0 必须先迁出的调用点

这些文件中的 KeePass 直连调用，应该优先迁移到 KeePassCompatibilityBridge 或专用仓库入口。

### 3.1 PasswordViewModel

文件：

- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/PasswordViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/PasswordViewModel.kt#L105)
- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/PasswordViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/PasswordViewModel.kt#L1322)
- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/PasswordViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/PasswordViewModel.kt#L1385)

现状：

- 直接创建 KeePassKdbxService。
- 直接处理密码更新时的跨库删旧写新。
- 直接处理删除、回收站、兜底直接删除。

第一阶段动作：

- 把 service 构造替换成 KeePassCompatibilityBridge 注入。
- 保留现有密码页和本地/Bitwarden 语义不变。
- 把 updatePasswordEntryInternal、deletePasswordEntry 里的 KeePass 调用改成桥接调用。

### 3.2 NoteViewModel

文件：

- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/NoteViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/NoteViewModel.kt#L43)
- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/NoteViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/NoteViewModel.kt#L202)
- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/NoteViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/NoteViewModel.kt#L284)
- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/NoteViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/NoteViewModel.kt#L377)
- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/NoteViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/NoteViewModel.kt#L453)

现状：

- 新建/更新笔记时直接写 KeePass。
- 更换数据库时直接删旧库条目并写新库条目。
- 删除与批量删除时直接决定 moveSecureItemsToRecycleBin 或 deleteSecureItems。

第一阶段动作：

- 把所有 keepassService 调用统一改成兼容桥方法。
- 暂不改变笔记业务流和 Bitwarden 队列逻辑。

### 3.3 TotpViewModel

文件：

- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/TotpViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/TotpViewModel.kt#L81)
- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/TotpViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/TotpViewModel.kt#L578)
- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/TotpViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/TotpViewModel.kt#L616)
- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/TotpViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/TotpViewModel.kt#L727)
- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/TotpViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/TotpViewModel.kt#L882)
- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/TotpViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/TotpViewModel.kt#L909)

现状：

- TOTP 创建、更新、删除、与密码绑定变化时都可能直接触发 KeePass 写入。
- 同时夹杂了密码库绑定逻辑与 Bitwarden 删除队列逻辑。

第一阶段动作：

- 只抽离 KeePass 访问，不改绑定密码和 Bitwarden 的既有语义。
- 所有 KeePass 写入先过兼容桥，后续第二阶段再替换成节点模型。

### 3.4 BankCardViewModel

文件：

- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/BankCardViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/BankCardViewModel.kt#L37)
- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/BankCardViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/BankCardViewModel.kt#L163)
- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/BankCardViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/BankCardViewModel.kt#L239)
- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/BankCardViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/BankCardViewModel.kt#L291)
- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/BankCardViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/BankCardViewModel.kt#L330)

现状：

- 卡片新增、更新、移动存储、删除都直接访问 KeePass。

第一阶段动作：

- 替换 service 持有方式。
- 保持卡片本地库与 Bitwarden 状态流不变，只收口 KeePass 调用。

### 3.5 DocumentViewModel

文件：

- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/DocumentViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/DocumentViewModel.kt#L36)
- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/DocumentViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/DocumentViewModel.kt#L145)
- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/DocumentViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/DocumentViewModel.kt#L217)
- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/DocumentViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/DocumentViewModel.kt#L269)
- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/DocumentViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/DocumentViewModel.kt#L308)

现状：

- 证件新增、更新、移动存储、删除都直接访问 KeePass。

第一阶段动作：

- 与 BankCardViewModel 同步收口，避免两套安全项逻辑继续分叉。

### 3.6 TrashViewModel

文件：

- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/TrashViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/TrashViewModel.kt#L54)

现状：

- 直接持有 KeePassKdbxService。
- 负责回收站永久删除与恢复时的 KeePass 联动。

第一阶段动作：

- 优先把 TrashViewModel 中的 KeePass 删除/恢复调用改成兼容桥或工作区仓库入口。
- 不改回收站天数、自动清空与 Bitwarden tombstone 行为。

## 4. P1 应一并处理的 UI 直连点

这些地方虽然不是业务 ViewModel，但它们在 Compose 层直接 new KeePassKdbxService 拉分组，属于继续扩散服务层依赖，也应在第一阶段一起收口。

### 4.1 CardWalletScreen

文件：

- [Monica for Android/app/src/main/java/takagi/ru/monica/ui/screens/CardWalletScreen.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/ui/screens/CardWalletScreen.kt#L149)

现状：

- 页面自己创建 KeePassKdbxService。
- 页面自己维护 keepassGroupFlows 并调用 listGroups。

第一阶段动作：

- 改为通过 LocalKeePassViewModel 或 KeePassWorkspaceViewModel 暴露分组流。

### 4.2 NoteListScreen

文件：

- [Monica for Android/app/src/main/java/takagi/ru/monica/ui/screens/NoteListScreen.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/ui/screens/NoteListScreen.kt#L157)

现状：

- 页面自己创建 KeePassKdbxService 拉分组。

第一阶段动作：

- 替换为 ViewModel 级别分组查询接口。

### 4.3 TotpListContent

文件：

- [Monica for Android/app/src/main/java/takagi/ru/monica/ui/totp/TotpListContent.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/ui/totp/TotpListContent.kt#L223)

现状：

- 组合函数自己 new KeePassKdbxService 仅用于分组读取。

第一阶段动作：

- 改为从 ViewModel 获取分组流，停止 UI 层直持 service。

### 4.4 PasskeyListScreen

文件：

- [Monica for Android/app/src/main/java/takagi/ru/monica/ui/screens/PasskeyListScreen.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/ui/screens/PasskeyListScreen.kt#L138)

现状：

- 页面自己创建 KeePassKdbxService 拉分组。

第一阶段动作：

- 与其他 UI 页面统一改成通过 ViewModel 获取 KeePass 分组。

## 5. P2 暂不迁但必须冻结的点

### 5.1 LocalKeePassViewModel

文件：

- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/LocalKeePassViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/LocalKeePassViewModel.kt#L81)
- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/LocalKeePassViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/LocalKeePassViewModel.kt#L506)
- [Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/LocalKeePassViewModel.kt](Monica%20for%20Android/app/src/main/java/takagi/ru/monica/viewmodel/LocalKeePassViewModel.kt#L525)

现状：

- 该类已经开始接入仓库，但仍保留底层 service 与 process cache invalidation。

第一阶段动作：

- 不继续新增新的业务型 KeePass 逻辑到这个类。
- 暂时允许它继续承担数据库管理职责。
- 后续第二阶段再决定是否进一步下沉 invalidateProcessCache 到 storage engine。

## 6. 推荐执行顺序

建议按下面顺序落地，以减少回归面：

1. PasswordViewModel
2. NoteViewModel
3. TotpViewModel
4. BankCardViewModel + DocumentViewModel
5. TrashViewModel
6. CardWalletScreen、NoteListScreen、TotpListContent、PasskeyListScreen

原因：

- Password/Note/Totp 是最常用链路，先收口收益最大。
- BankCard/Document 模式相同，可以成对迁移。
- TrashViewModel 依赖恢复与永久删除逻辑，放在业务侧收口之后更稳。
- UI 直连点最后统一替换成 ViewModel 读取接口，改动最小。

## 7. 第一阶段完成标准

达到以下条件即可认为第一阶段完成：

- 业务 ViewModel 中不再直接 new KeePassKdbxService。
- Compose 页面中不再直接 new KeePassKdbxService。
- KeePass 的分组读取、基础写入与数据库检查都通过仓库或兼容桥进入。
- Bitwarden 与 Monica 本地库的既有行为保持不变。
