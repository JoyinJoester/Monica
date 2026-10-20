# Monica for Android 1.0.321

> 未发布 / Unreleased

## 中文

- 支持同步 Bitwarden 团队／组织保险库中有权读取的条目及附件元数据，兼容组织密钥与独立条目密钥；编辑回传保留组织、集合和密钥归属，只读或解密失败时保留本地更改并提示。#168

- 修复数据库筛选菜单编辑模式下 KeePass、MDBX、Bitwarden 文件夹无法打开操作弹窗的问题，统一重命名与删除确认，并在成功后刷新文件夹列表。

- 修复附近设备同步“仅接收”模式在等待连接时准备失败的问题，保持仅接收限制，并在现有超时范围内重试手动同步的临时连接失败。

- 修复部分嵌套网页丢失网址导致绑定账号无法推荐的问题；从键盘和主动填充入口打开选择页时复用现有匹配规则推荐已绑定账号。#167
- 修复将 Android 应用包名直接填写在网址字段时无法推荐绑定账号的问题；兼容裸包名和 `androidapp://` 绑定格式，并保留普通网址匹配。#167

- 修复银行卡有效期输入年份首位后光标跳回、后续数字顺序错乱的问题；同步修复密码条目支付信息中的有效期输入，改善粘贴、选区替换和删除时的光标位置。

## English

- Added sync for readable Bitwarden team/organization items and attachment metadata, including organization and per-item keys. Updates retain organization, collections and key ownership; read-only or failed operations preserve pending local changes.
- Fixed folder actions in database filter edit mode for KeePass, MDBX and Bitwarden, with shared rename/delete dialogs, deletion confirmation and folder-list refresh after successful changes.
- Fixed receive-only nearby sync failing before connecting when the receiving device acts as the listener; receive-only limits remain enforced. Manual sync retries transient connection setup failures within its existing timeout.
- Fixed missing bound-account suggestions when nested web forms lost their origin. Pickers opened from the keyboard or active-fill notification now reuse existing matching rules for saved app and website bindings. #167
- Fixed missing autofill suggestions when an Android package name was entered directly in the website field. Bare package names and `androidapp://` bindings now match while ordinary website matching remains unchanged. #167
- Fixed the expiry-date cursor jumping before the first year digit and reversing subsequent input in bank cards and password payment fields. Pasting, selection replacement and deletion now preserve the correct editing position.
