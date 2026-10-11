# Monica for Android 1.0.318

> 未发布 / Unreleased

## 中文

- 修复 MDBX 格式检查遇到损坏数据库时的错误分类，并禁止 Android 默认损坏处理器删除原文件，保留后续恢复所需的数据。
- 附近设备同步发送前复用通行密钥迁移弹窗，逐项说明仅本地和跨平台受限的原因，可取消同步或了解后继续。检查覆盖配对后的首次自动同步及后续发送；同一应用进程内已确认的限制不重复提醒，新增受限条目或新设备会重新确认，仅接收模式无需确认本机发送限制。
- 新增“附近设备同步”：同一可互通 Wi-Fi／局域网中的设备扫码配对后，可自动或手动同步 Monica 本地保险库及附件，支持密码、独立验证器、通行密钥、笔记、卡片、文件夹、标签和关联信息。包括验证器和 MDBX 中的 Steam 类型内容；Steam 页面独立的本地账户库暂不参与，Bitwarden／Vaultwarden 与 KeePass 外部库继续使用各自的同步方式。
- 附近设备同步默认双向，也可设为本机仅发送或仅接收；传输遵守双方方向设置，方向冲突或旧客户端不支持单向时明确提示。配对与传输经过加密，锁定或编辑期间暂停接收保存，校验及保存完成后才记录成功时间，离线冲突保留副本。
- 附近设备同步保留通行密钥的原始密钥、算法与 WebAuthn 备份标志；可导出私钥在接收端重新加密保存，不可导出的系统／硬件密钥仅同步凭据信息。其他数据库中的副本不会被误判为本地冲突，远端删除保留副本仍引用的受保护私钥。跨端使用需双方安装兼容版本。

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

- Fixed MDBX format checks misclassifying corrupt databases and prevented Android's default corruption handler from deleting the original file, preserving it for recovery.
- Nearby sync reuses the Passkey transfer dialog before sending to explain local-only and cross-platform restrictions per entry, with options to cancel sync or continue after review. This also covers the first automatic sync after pairing. Confirmed restrictions are remembered for the current app process; new restricted entries or devices require review again. Receive-only mode does not require review of local sending restrictions.
- Added Nearby device sync: pair devices by QR code on the same reachable Wi-Fi/LAN to sync Monica's local vault and attachments automatically or manually, including passwords, standalone authenticators, Passkeys, notes, cards, folders, tags and relationships. Steam entries in authenticators and MDBX are included; the Steam page's separate local account database is not yet included. External Bitwarden/Vaultwarden and KeePass vaults continue to use their own sync mechanisms.
- Nearby sync defaults to bidirectional, with send-only and receive-only options. Transfers respect both devices' direction settings and report conflicting directions or unsupported older clients. Pairing and transfers are encrypted; incoming saves pause while locked or editing, success is recorded after validation and saving, and offline conflicts retain copies.
- Nearby sync preserves original Passkey keys, algorithms and WebAuthn backup flags. Exportable private keys are encrypted again on the receiving device; non-exportable system/hardware keys sync credential metadata only. Copies in other vaults do not cause false local conflicts, and remote deletion preserves protected private keys still referenced by copies. Both devices need compatible app versions.

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
