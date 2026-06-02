# MDBX SQLite 初版 Schema 规范

版本：`MDBX-1-DRAFT`

本文定义 MDBX 在 `SQLite + 自定义加密层` 路线下的第一版逻辑 schema。
这是实现指导文档，但其中与 `project`、`attachment`、历史链路相关的关键结构应视为强约束。

## 1. 目标

这版 schema 必须满足：

- 密码按 `project` 组织
- `entry` 归属于 `project`
- 附件是一等结构
- 可以记录 commit 历史
- 可以记录 tombstone
- 可以做快照和恢复
- 可以支持后续 KDBX 导入导出

## 2. 设计原则

### 2.1 强制表

以下表从 v1 起必须存在：

- `vault_meta`
- `projects`
- `entries`
- `attachments`
- `attachment_chunks`
- `commits`
- `commit_parents`
- `device_heads`
- `branches`
- `object_versions`
- `tombstones`
- `snapshots`
- `key_epochs`
- `conflicts`
- `unlock_methods`
- `project_tags`

### 2.2 可后续补强表

以下表可在 MVP 后增强：

- `audit_events`
- `entry_custom_fields`

全文搜索索引可以在解锁会话中使用临时表或内存结构，但不得作为持久 schema 保存解密后的 project 标题或其他秘密文本。

## 3. 表结构总览

## 3.1 vault_meta

用途：

- 存储 vault 级公开或半公开元信息
- 不存储明文秘密

推荐字段：

- `vault_id TEXT PRIMARY KEY`
- `format_version TEXT NOT NULL`
- `created_at TEXT NOT NULL`
- `updated_at TEXT NOT NULL`
- `default_tiga_mode TEXT NOT NULL`
- `active_key_epoch_id TEXT NOT NULL`
- `compat_flags TEXT NOT NULL`
- `critical_extensions TEXT NOT NULL`

## 3.2 projects

用途：

- `project` 是主容器
- 所有密码类内容必须可归属于某个 project

推荐字段：

- `project_id TEXT PRIMARY KEY`
- `title_ct BLOB NOT NULL`
- `summary_ct BLOB NULL`
- `group_id TEXT NULL`
- `icon_ref TEXT NULL`
- `favorite INTEGER NOT NULL DEFAULT 0`
- `archived INTEGER NOT NULL DEFAULT 0`
- `deleted INTEGER NOT NULL DEFAULT 0`
- `tiga_mode_override TEXT NULL`
- `object_clock TEXT NOT NULL`
- `head_commit_id TEXT NOT NULL`
- `attachment_count INTEGER NOT NULL DEFAULT 0`
- `created_at TEXT NOT NULL`
- `updated_at TEXT NOT NULL`
- `created_by_device_id TEXT NOT NULL`
- `updated_by_device_id TEXT NOT NULL`

索引建议：

- `idx_projects_updated_at`
- `idx_projects_group_id`
- `idx_projects_deleted`
- `idx_projects_head_commit_id`

## 3.3 entries

用途：

- 存储 project 内部的登录项、笔记、卡片、身份、TOTP、Passkey 等

推荐字段：

- `entry_id TEXT PRIMARY KEY`
- `project_id TEXT NOT NULL`
- `entry_type TEXT NOT NULL`
- `title_ct BLOB NULL`
- `payload_ct BLOB NOT NULL`
- `payload_schema_version INTEGER NOT NULL`
- `tiga_mode_override TEXT NULL`
- `object_clock TEXT NOT NULL`
- `head_commit_id TEXT NOT NULL`
- `deleted INTEGER NOT NULL DEFAULT 0`
- `created_at TEXT NOT NULL`
- `updated_at TEXT NOT NULL`
- `created_by_device_id TEXT NOT NULL`
- `updated_by_device_id TEXT NOT NULL`

约束：

- `project_id` 必须引用 `projects.project_id`
- 所有秘密型 entry 数据必须放入 `payload_ct`
- 不允许把敏感字段拆成明文列

索引建议：

- `idx_entries_project_id`
- `idx_entries_type`
- `idx_entries_updated_at`
- `idx_entries_deleted`

## 3.4 attachments

用途：

- 存储附件元数据
- 支持 project 级附件和 entry 级附件

推荐字段：

- `attachment_id TEXT PRIMARY KEY`
- `project_id TEXT NOT NULL`
- `entry_id TEXT NULL`
- `file_name_ct BLOB NOT NULL`
- `media_type_ct BLOB NULL`
- `storage_mode TEXT NOT NULL`
- `content_hash TEXT NOT NULL`
- `original_size INTEGER NOT NULL`
- `stored_size INTEGER NOT NULL`
- `chunk_count INTEGER NOT NULL DEFAULT 0`
- `head_commit_id TEXT NOT NULL`
- `deleted INTEGER NOT NULL DEFAULT 0`
- `created_at TEXT NOT NULL`
- `updated_at TEXT NOT NULL`
- `created_by_device_id TEXT NOT NULL`
- `updated_by_device_id TEXT NOT NULL`

约束：

- `project_id` 必须存在
- `entry_id` 可空，但如果存在必须引用 `entries.entry_id`
- 附件改名不得改变 `content_hash`
- 附件元数据与附件内容必须解耦

索引建议：

- `idx_attachments_project_id`
- `idx_attachments_entry_id`
- `idx_attachments_content_hash`
- `idx_attachments_deleted`

## 3.5 attachment_chunks

用途：

- 存储大型附件的分块内容或块引用

推荐字段：

- `attachment_id TEXT NOT NULL`
- `chunk_index INTEGER NOT NULL`
- `chunk_hash TEXT NOT NULL`
- `chunk_ct BLOB NULL`
- `external_uri_ct BLOB NULL`
- `stored_size INTEGER NOT NULL`
- `created_at TEXT NOT NULL`
- `PRIMARY KEY (attachment_id, chunk_index)`

约束：

- `chunk_ct` 与 `external_uri_ct` 至少一个存在
- `chunk_index` 必须从 `0` 开始连续递增

## 3.6 commits

用途：

- 记录每次本地变更或合并操作

推荐字段：

- `commit_id TEXT PRIMARY KEY`
- `device_id TEXT NOT NULL`
- `local_seq INTEGER NOT NULL`
- `commit_kind TEXT NOT NULL`
- `change_scope TEXT NOT NULL`
- `changed_object_ids_ct BLOB NOT NULL`
- `vector_clock TEXT NOT NULL`
- `message_ct BLOB NULL`
- `created_at TEXT NOT NULL`
- `integrity_tag BLOB NOT NULL`

约束：

- `(device_id, local_seq)` 必须唯一
- `local_seq` 必须单调递增

索引建议：

- `uniq_commits_device_seq`
- `idx_commits_created_at`
- `idx_commits_device_id`

## 3.7 commit_parents

用途：

- 记录 commit DAG 关系

推荐字段：

- `commit_id TEXT NOT NULL`
- `parent_commit_id TEXT NOT NULL`
- `PRIMARY KEY (commit_id, parent_commit_id)`

## 3.8 device_heads

用途：

- 记录每台设备当前可见的 head

推荐字段：

- `device_id TEXT PRIMARY KEY`
- `head_commit_id TEXT NOT NULL`
- `last_seen_at TEXT NOT NULL`
- `revoked INTEGER NOT NULL DEFAULT 0`

## 3.9 branches

用途：

- 逻辑分支引用

推荐字段：

- `branch_id TEXT PRIMARY KEY`
- `branch_name TEXT NOT NULL`
- `head_commit_id TEXT NOT NULL`
- `created_at TEXT NOT NULL`
- `updated_at TEXT NOT NULL`

## 3.10 object_versions

用途：

- 保存对象在指定 commit 的行级快照
- 支持 base/local/incoming 三方合并与单条目回滚

推荐字段：

- `object_type TEXT NOT NULL`
- `object_id TEXT NOT NULL`
- `commit_id TEXT NOT NULL`
- `snapshot_ct BLOB NOT NULL`
- `created_at TEXT NOT NULL`
- `PRIMARY KEY (object_type, object_id, commit_id)`

约束：

- `commit_id` 应引用 `commits.commit_id`
- `snapshot_ct` 存储序列化后的对象行快照；敏感字段仍应保持密文或由加密层保护
- v1 至少必须覆盖 `entry`

索引建议：

- `idx_object_versions_object`
- `idx_object_versions_commit`

## 3.11 tombstones

用途：

- 存储删除标记，防止并发同步时误复活

推荐字段：

- `tombstone_id TEXT PRIMARY KEY`
- `target_object_type TEXT NOT NULL`
- `target_object_id TEXT NOT NULL`
- `delete_clock TEXT NOT NULL`
- `deleted_by_device_id TEXT NOT NULL`
- `deleted_at TEXT NOT NULL`
- `purge_eligible_at TEXT NULL`

索引建议：

- `idx_tombstones_target`
- `idx_tombstones_deleted_at`

## 3.12 snapshots

用途：

- 记录恢复检查点

推荐字段：

- `snapshot_id TEXT PRIMARY KEY`
- `base_commit_id TEXT NOT NULL`
- `snapshot_ct BLOB NOT NULL`
- `snapshot_hash TEXT NOT NULL`
- `created_at TEXT NOT NULL`
- `created_by_device_id TEXT NOT NULL`

## 3.13 key_epochs

用途：

- 管理密钥轮换

推荐字段：

- `key_epoch_id TEXT PRIMARY KEY`
- `status TEXT NOT NULL`
- `wrapped_epoch_key_ct BLOB NOT NULL`
- `kdf_profile_id TEXT NOT NULL`
- `created_at TEXT NOT NULL`
- `activated_at TEXT NULL`
- `retired_at TEXT NULL`

## 3.14 conflicts

用途：

- 记录自动合并不安全的并发修改
- 支持后续用户选择 local、incoming 或 custom 结果

推荐字段：

- `conflict_id TEXT PRIMARY KEY`
- `object_type TEXT NOT NULL`
- `object_id TEXT NOT NULL`
- `base_commit_id TEXT NOT NULL`
- `local_commit_id TEXT NOT NULL`
- `incoming_commit_id TEXT NOT NULL`
- `conflicting_fields TEXT NOT NULL`
- `resolution TEXT NOT NULL DEFAULT 'unresolved'`
- `created_at TEXT NOT NULL`
- `resolved_at TEXT NULL`

## 3.15 unlock_methods

用途：

- 记录用户可见解锁方式如何包装 vault key
- 支持 Tiga 对便携性、安全密钥和组合解锁的策略约束

推荐字段：

- `method_id TEXT PRIMARY KEY`
- `method_type TEXT NOT NULL`
- `kdf_profile_id TEXT NOT NULL`
- `kdf_params_ct BLOB NOT NULL`
- `wrapped_vault_key_ct BLOB NOT NULL`
- `created_at TEXT NOT NULL`
- `updated_at TEXT NOT NULL`

约束：

- `method_type` 必须至少支持 `pin`、`password`、`security_key`、`password_security_key`
- `password_security_key` 表示密码 + 安全密钥组合解锁路径，用于满足更严格的 Power 策略
- 安全密钥材料、challenge response、派生 key material 或可重放等价材料不得写入日志、缓存或未认证同步元数据

## 3.16 project_tags

用途：

- 记录非秘密标签索引或已确认可持久化的标签关系

推荐字段：

- `project_id TEXT NOT NULL`
- `tag TEXT NOT NULL COLLATE NOCASE`
- `PRIMARY KEY (project_id, tag)`

约束：

- 如果 tag 可能包含秘密语义，应改为密文载荷或仅在解锁会话中临时索引

## 4. 建议关系图

逻辑关系如下：

```text
vault
  -> projects
      -> entries
      -> attachments
          -> attachment_chunks
  -> commits
      -> commit_parents
      -> object_versions
  -> device_heads
  -> branches
  -> tombstones
  -> snapshots
  -> key_epochs
  -> conflicts
  -> unlock_methods
  -> project_tags
```

## 5. 最小 SQL 原型约束

低端模型在写 SQL 草案时，至少要体现：

- `projects` 存在
- `entries.project_id` 存在
- `attachments.project_id` 存在
- `attachments.entry_id` 可空
- `commits` 与 `commit_parents` 支持 DAG
- `object_versions` 支持 entry 的 commit 快照
- `tombstones` 支持延迟清理
- `unlock_methods` 支持 `pin`、`password`、`security_key`、`password_security_key`
- 全文搜索持久 schema 不保存解密标题

## 6. 禁止事项

以下 schema 设计不合格：

- 没有 `projects` 表
- `entries` 不归属于 project
- 附件只是某个 JSON 大字段中的匿名数组
- 大附件和普通元数据强耦合，导致小修改也重写大块内容
- 没有 `commits` 或没有 `tombstones`
- 持久 FTS 表保存解密后的 project title 或 secret-bearing text
- `unlock_methods` 不支持组合的 `password_security_key`，却宣称完整 Power 策略

## 7. MVP 实现顺序建议

建议低端模型按这个顺序落地 schema：

1. `vault_meta`
2. `projects`
3. `entries`
4. `attachments`
5. `attachment_chunks`
6. `commits`
7. `commit_parents`
8. `device_heads`
9. `branches`
10. `object_versions`
11. `tombstones`
12. `snapshots`
13. `key_epochs`
14. `conflicts`
15. `unlock_methods`
16. `project_tags`

## 8. 验收标准

一个合格的 v1 schema 至少应满足：

- 能表达 `project -> entry` 主关系
- 能表达 `project -> attachment` 与 `entry -> attachment`
- 能在不重写附件内容的情况下修改项目元数据
- 能表达 commit DAG
- 能表达 entry 的 commit 快照，用于字段级三方合并
- 能表达 tombstone
- 能表达多种 unlock method，包括 `password_security_key`
- 能确认全文搜索不会把解密标题持久化
- 能支持后续 KDBX 导入映射
