# Monica for Android 1.0.319

> 未发布 / Unreleased

## 中文

- 新增“附近设备同步”：在同一可互通 Wi-Fi／局域网中扫码或使用链接配对，加密同步本地或受支持的 MDBX 数据库，包含密码、独立验证器、Steam 本地账户、通行密钥、笔记、卡片、文件夹、标签和附件。同步库默认“本地”；Bitwarden／Vaultwarden 与 KeePass 外部库继续使用各自的同步方式。附近同步无需云中转。
- 支持双向、仅发送和仅接收，可选择自动或手动同步。自动同步开关随时可切换，关闭后当前传输正常收尾；配对后也可从主要页面右上角菜单手动同步或查看进度，两端均关闭自动同步时需分别点击。
- 自动同步仅在两端 Monica 均处于前台且已解锁时进行，退到后台后暂停；未发现对方时不反复探测，数据未变化时重新打开不会强制重传。系统仍有少量设备发现报文，服务发现受限时可在两端手动同步。
- 同步前说明通行密钥的跨设备限制，可勾选“不再提醒”，并在诊断中查看限制及恢复提醒。签名计数非零的 Passkey 会跳过；不可导出的系统／硬件密钥仅同步凭据信息，可导出密钥在接收端重新加密保存。跨端使用需双方安装兼容版本。
- 提供同步进度、连接诊断和失败处理建议；校验及保存完成后才确认成功，离线冲突保留副本。

- 移除喵喵语、芝士雪豹语和雪豹彩蛋图标，原始语言与图标资源归档至“彩蛋语言备份”；旧语言设置自动恢复简体中文，保留升级后的正常桌面入口。

- 修复 MDBX 格式检查遇到损坏数据库时的错误分类，并禁止 Android 默认损坏处理器删除原文件，保留后续恢复所需的数据。

- Steam 筛选复用密码与通行密钥页面的数据库／文件夹菜单，支持本地分类、MDBX 文件夹、KeePass 分组及 Bitwarden／Vaultwarden 文件夹；记住所选文件夹，并可与搜索组合。切换同库文件夹不重复加载或同步账户，共用菜单支持从 MDBX 子文件夹逐级返回。

- Passkey 和 Steam 支持选择数据库内的文件夹进行移动或复制，也可在当前数据库中整理归属；修复 Passkey 目标选择器未加载 MDBX 文件夹的问题。
- Passkey 复制保留源记录；操作前检查绑定、引用、私钥及目标限制，遇到无法处理的条目可取消或跳过后继续，并显示逐项结果。要求凭据 ID 唯一的 KeePass／MDBX 不支持同库复制，可改选其他数据库。
- Steam 复制生成独立条目，同库移动保留原记录；新增本地分类归属并迁移旧数据，修复 MDBX 后续编辑或刷新会话时回到根目录的问题。跨库移动在目标写入成功后才清理源记录。

- 修复通行密钥本地数据库页面反复出现同步状态：按当前数据库和文件夹范围触发刷新，跳过无目标、无法读取或已更新的 KeePass 索引；本地页面不再显示其他 Bitwarden／Vaultwarden 账户的同步进度。
- 缓存通行密钥的“密钥不可用”“跨端受限”等标记，返回列表或打开详情时立即复用检查结果；密钥删除、恢复、兼容性参数或解锁会话变化后重新检查，减少重复加载与标记闪烁。

- 修复通行密钥列表进入详情后返回会重置为“全部”的问题：保留所选数据库及文件夹，重新打开后恢复上次选择，避免页面重建时用默认值覆盖筛选设置。

- 修复旧 Passkey 在刷新或合并时可能改变已使用的备份资格标志：保留既有凭据的注册属性，冲突时保留原数据；新导入仍遵循来源标志。
- 修复修改 Bitwarden／Vaultwarden Passkey 时可能覆盖同一登录项中的其他密钥、密码、网址和自定义字段；更新前读取完整条目，使用版本校验并回读确认，失败或冲突时保留本地数据；保留用户主动移动文件夹和移回根目录的操作。
- 通行密钥列表在格式旁标记“仅本地”“跨端受限”或“密钥不可用”，详情解释限制原因；支持小屏及大字体换行。

- 修复仅绑定 Bitwarden／Vaultwarden 时密码库首页和搜索可能看不到已同步条目：未保存数据库范围时默认显示“全部”，旧版省略范围的设置同步采用该默认值；明确保存的本地库或具体数据库选择继续保留，锁定库仍不参与展示与搜索。

## English

- Added Nearby device sync: pair by QR code or link on the same reachable Wi-Fi/LAN to securely sync Local or supported MDBX vaults, including passwords, standalone authenticators, local Steam accounts, Passkeys, notes, cards, folders, tags and attachments. The default sync database is Local; external Bitwarden/Vaultwarden and KeePass vaults keep their own sync mechanisms. Nearby sync needs no cloud relay.
- Choose bidirectional, send-only or receive-only transfers, with automatic or manual sync. The automatic toggle works at any time; switching it off lets the current transfer finish. After pairing, main-page menus offer manual sync and progress access. When automatic sync is off on both devices, start a manual round on each.
- Automatic sync runs while both Monica apps are visible and unlocked, pausing in the background. Unavailable peers are not repeatedly probed, and reopening unchanged data does not force a resend. Small system discovery messages remain; use manual sync on both devices if service discovery is restricted.
- Passkey review explains cross-device restrictions, offers a persistent “Don’t show again” choice and lets you inspect limits or restore reminders in diagnostics. Passkeys with nonzero signature counters are skipped. Non-exportable system/hardware keys sync credential metadata only; exportable keys are encrypted again on receipt. Both devices need compatible app versions.
- View sync progress, connection diagnostics and failure guidance. Success is confirmed after validation and saving; offline conflicts retain copies.

- Removed the Nya and Snow Leopard Easter egg languages and Snow Leopard icon. Original resources are archived in “彩蛋语言备份”; existing selections migrate to Simplified Chinese while preserving a working launcher entry.

- Fixed MDBX format checks misclassifying corrupt databases and prevented Android's default corruption handler from deleting the original file, preserving it for recovery.

- Steam now shares the database/folder filter menu used by passwords and Passkeys, supporting local categories, MDBX folders, KeePass groups and Bitwarden/Vaultwarden folders. Folder selections persist and work with search. Switching folders within a database does not reload or sync accounts; the shared menu also supports navigating back through MDBX parent folders.

- Passkey and Steam entries can now be moved or copied into folders, including organization within the current database. Fixed MDBX folders not loading in the Passkey destination picker.
- Passkey copies preserve the source. A preflight check identifies bound entries, references, unavailable keys and destination restrictions, with options to cancel or skip blocked entries and continue, followed by per-entry results. KeePass/MDBX databases requiring unique credential IDs cannot hold a second copy in the same database; choose another database instead.
- Steam copies create independent entries, while moves within a database retain the original record. Added local category assignments with migration of existing data, and fixed MDBX edits or session refreshes moving entries back to root. Cross-database moves clean up sources only after destination writes succeed.

- Fixed repeated sync activity on local Passkey pages. Refreshes follow the selected database and folder, skipping KeePass indexing with no target, inaccessible databases or an unchanged index. Local pages no longer display another Bitwarden/Vaultwarden account's sync progress.
- Cached Passkey compatibility labels, including Key unavailable and Transfer limited, so returning to the list or opening details reuses known results immediately. Key deletion or restoration, compatibility changes and lock-session changes trigger a new check, reducing repeated loading and badge flicker.

- Fixed the Passkey list resetting to All after returning from details. The selected database and folder now remain active and are restored on reopening, without page recreation overwriting saved filters with defaults.

- Fixed refresh and merge of legacy Passkeys changing previously used backup eligibility: preserve existing credential properties and retain local data on conflicts; new imports retain source flags.
- Fixed Bitwarden/Vaultwarden Passkey updates overwriting other credentials, passwords, URLs and custom fields in the same login. Updates now read the full item, check its revision and verify the saved content; failures and conflicts retain local data, while explicit folder moves and moves back to root remain supported.
- Added Local only, Transfer limited and Key unavailable labels beside Passkey formats, with explanations in details and wrapping for narrow screens and large text.

- Fixed synced entries being absent from the vault overview and search for Bitwarden/Vaultwarden users: use All when no database scope is saved, including older settings that omitted the scope. Explicitly saved local or individual database scopes are preserved, and locked vaults remain excluded from display and search.
