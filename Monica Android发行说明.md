# Monica for Android 1.0.321

> 未发布 / Unreleased

## 中文

- 支持同步 Bitwarden 团队／组织保险库中有权读取的条目及附件元数据，兼容组织密钥与独立条目密钥；编辑回传保留组织、集合和密钥归属，只读或解密失败时保留本地更改并提示。#168

- 修复数据库筛选菜单编辑模式下 KeePass、MDBX、Bitwarden 文件夹无法打开操作弹窗的问题，统一重命名与删除确认，并在成功后刷新文件夹列表。

- 修复附近设备同步“仅接收”模式在等待连接时准备失败的问题，保持仅接收限制，并在现有超时范围内重试手动同步的临时连接失败。

## English

- Added sync for readable Bitwarden team/organization items and attachment metadata, including organization and per-item keys. Updates retain organization, collections and key ownership; read-only or failed operations preserve pending local changes.
- Fixed folder actions in database filter edit mode for KeePass, MDBX and Bitwarden, with shared rename/delete dialogs, deletion confirmation and folder-list refresh after successful changes.
- Fixed receive-only nearby sync failing before connecting when the receiving device acts as the listener; receive-only limits remain enforced. Manual sync retries transient connection setup failures within its existing timeout.
