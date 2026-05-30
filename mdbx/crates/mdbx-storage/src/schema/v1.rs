use rusqlite::Connection;

use crate::error::{StorageError, StorageResult};

/// 创建全部 v1 表与索引。
pub fn create_all_tables(conn: &Connection) -> StorageResult<()> {
    conn.execute_batch(&format!(
        "{}\n{}\n{}\n{}\n{}\n{}\n{}\n{}\n{}\n{}\n{}\n{}\n{}\n{}\n{}\n{}\n{}\n{}",
        VAULT_META_DDL,
        PROJECTS_DDL,
        ENTRIES_DDL,
        ATTACHMENTS_DDL,
        ATTACHMENT_CHUNKS_DDL,
        COMMITS_DDL,
        COMMIT_PARENTS_DDL,
        DEVICE_HEADS_DDL,
        BRANCHES_DDL,
        OBJECT_VERSIONS_DDL,
        TOMBSTONES_DDL,
        SNAPSHOTS_DDL,
        KEY_EPOCHS_DDL,
        CONFLICTS_DDL,
        UNLOCK_METHODS_DDL,
        PROJECT_TAGS_DDL,
        PROJECT_TITLES_FTS_DDL,
        INDEX_DDL,
    ))
    .map_err(|e| StorageError::SchemaCreation(e.to_string()))
}

// ---------------------------------------------------------------------------
// 13 张表 DDL
// ---------------------------------------------------------------------------

pub const VAULT_META_DDL: &str = "\
CREATE TABLE IF NOT EXISTS vault_meta (
    vault_id            TEXT PRIMARY KEY NOT NULL,
    format_version      TEXT NOT NULL DEFAULT 'MDBX-1',
    created_at          TEXT NOT NULL,
    updated_at          TEXT NOT NULL,
    default_tiga_mode   TEXT NOT NULL DEFAULT 'multi',
    active_key_epoch_id TEXT NOT NULL,
    compat_flags        TEXT NOT NULL DEFAULT '',
    critical_extensions TEXT NOT NULL DEFAULT ''
);";

pub const PROJECTS_DDL: &str = "\
CREATE TABLE IF NOT EXISTS projects (
    project_id           TEXT PRIMARY KEY NOT NULL,
    title_ct             BLOB NOT NULL,
    summary_ct           BLOB,
    group_id             TEXT,
    icon_ref             TEXT,
    favorite             INTEGER NOT NULL DEFAULT 0,
    archived             INTEGER NOT NULL DEFAULT 0,
    deleted              INTEGER NOT NULL DEFAULT 0,
    tiga_mode_override   TEXT,
    object_clock         TEXT NOT NULL,
    head_commit_id       TEXT NOT NULL,
    attachment_count     INTEGER NOT NULL DEFAULT 0,
    created_at           TEXT NOT NULL,
    updated_at           TEXT NOT NULL,
    created_by_device_id TEXT NOT NULL,
    updated_by_device_id TEXT NOT NULL
);";

pub const ENTRIES_DDL: &str = "\
CREATE TABLE IF NOT EXISTS entries (
    entry_id               TEXT PRIMARY KEY NOT NULL,
    project_id             TEXT NOT NULL,
    entry_type             TEXT NOT NULL,
    title_ct               BLOB,
    payload_ct             BLOB NOT NULL,
    payload_schema_version INTEGER NOT NULL DEFAULT 1,
    tiga_mode_override     TEXT,
    object_clock           TEXT NOT NULL,
    head_commit_id         TEXT NOT NULL,
    deleted                INTEGER NOT NULL DEFAULT 0,
    created_at             TEXT NOT NULL,
    updated_at             TEXT NOT NULL,
    created_by_device_id   TEXT NOT NULL,
    updated_by_device_id   TEXT NOT NULL,
    FOREIGN KEY (project_id) REFERENCES projects(project_id)
);";

pub const ATTACHMENTS_DDL: &str = "\
CREATE TABLE IF NOT EXISTS attachments (
    attachment_id        TEXT PRIMARY KEY NOT NULL,
    project_id           TEXT NOT NULL,
    entry_id             TEXT,
    file_name_ct         BLOB NOT NULL,
    media_type_ct        BLOB,
    storage_mode         TEXT NOT NULL,
    content_hash         TEXT NOT NULL,
    original_size        INTEGER NOT NULL,
    stored_size          INTEGER NOT NULL,
    chunk_count          INTEGER NOT NULL DEFAULT 0,
    head_commit_id       TEXT NOT NULL,
    deleted              INTEGER NOT NULL DEFAULT 0,
    created_at           TEXT NOT NULL,
    updated_at           TEXT NOT NULL,
    created_by_device_id TEXT NOT NULL,
    updated_by_device_id TEXT NOT NULL,
    FOREIGN KEY (project_id) REFERENCES projects(project_id),
    FOREIGN KEY (entry_id) REFERENCES entries(entry_id)
);";

pub const ATTACHMENT_CHUNKS_DDL: &str = "\
CREATE TABLE IF NOT EXISTS attachment_chunks (
    attachment_id    TEXT NOT NULL,
    chunk_index      INTEGER NOT NULL,
    chunk_hash       TEXT NOT NULL,
    chunk_ct         BLOB,
    external_uri_ct  BLOB,
    stored_size      INTEGER NOT NULL,
    created_at       TEXT NOT NULL,
    PRIMARY KEY (attachment_id, chunk_index),
    FOREIGN KEY (attachment_id) REFERENCES attachments(attachment_id),
    CHECK (chunk_ct IS NOT NULL OR external_uri_ct IS NOT NULL)
);";

pub const COMMITS_DDL: &str = "\
CREATE TABLE IF NOT EXISTS commits (
    commit_id             TEXT PRIMARY KEY NOT NULL,
    device_id             TEXT NOT NULL,
    local_seq             INTEGER NOT NULL,
    commit_kind           TEXT NOT NULL,
    change_scope          TEXT NOT NULL,
    changed_object_ids_ct BLOB NOT NULL,
    vector_clock          TEXT NOT NULL,
    message_ct            BLOB,
    created_at            TEXT NOT NULL,
    integrity_tag         BLOB NOT NULL
);";

pub const COMMIT_PARENTS_DDL: &str = "\
CREATE TABLE IF NOT EXISTS commit_parents (
    commit_id        TEXT NOT NULL,
    parent_commit_id TEXT NOT NULL,
    PRIMARY KEY (commit_id, parent_commit_id),
    FOREIGN KEY (commit_id) REFERENCES commits(commit_id),
    FOREIGN KEY (parent_commit_id) REFERENCES commits(commit_id)
);";

pub const DEVICE_HEADS_DDL: &str = "\
CREATE TABLE IF NOT EXISTS device_heads (
    device_id      TEXT PRIMARY KEY NOT NULL,
    head_commit_id TEXT NOT NULL,
    last_seen_at   TEXT NOT NULL,
    revoked        INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (head_commit_id) REFERENCES commits(commit_id)
);";

pub const BRANCHES_DDL: &str = "\
CREATE TABLE IF NOT EXISTS branches (
    branch_id      TEXT PRIMARY KEY NOT NULL,
    branch_name    TEXT NOT NULL,
    head_commit_id TEXT NOT NULL,
    created_at     TEXT NOT NULL,
    updated_at     TEXT NOT NULL,
    FOREIGN KEY (head_commit_id) REFERENCES commits(commit_id)
);";

pub const OBJECT_VERSIONS_DDL: &str = "\
CREATE TABLE IF NOT EXISTS object_versions (
    object_type TEXT NOT NULL,
    object_id   TEXT NOT NULL,
    commit_id   TEXT NOT NULL,
    snapshot_ct BLOB NOT NULL,
    created_at  TEXT NOT NULL,
    PRIMARY KEY (object_type, object_id, commit_id),
    FOREIGN KEY (commit_id) REFERENCES commits(commit_id)
);";

pub const TOMBSTONES_DDL: &str = "\
CREATE TABLE IF NOT EXISTS tombstones (
    tombstone_id         TEXT PRIMARY KEY NOT NULL,
    target_object_type   TEXT NOT NULL,
    target_object_id     TEXT NOT NULL,
    delete_clock         TEXT NOT NULL,
    deleted_by_device_id TEXT NOT NULL,
    deleted_at           TEXT NOT NULL,
    purge_eligible_at    TEXT
);";

pub const SNAPSHOTS_DDL: &str = "\
CREATE TABLE IF NOT EXISTS snapshots (
    snapshot_id          TEXT PRIMARY KEY NOT NULL,
    base_commit_id       TEXT NOT NULL,
    snapshot_ct          BLOB NOT NULL,
    snapshot_hash        TEXT NOT NULL,
    created_at           TEXT NOT NULL,
    created_by_device_id TEXT NOT NULL,
    FOREIGN KEY (base_commit_id) REFERENCES commits(commit_id)
);";

pub const KEY_EPOCHS_DDL: &str = "\
CREATE TABLE IF NOT EXISTS key_epochs (
    key_epoch_id         TEXT PRIMARY KEY NOT NULL,
    status               TEXT NOT NULL,
    wrapped_epoch_key_ct BLOB NOT NULL,
    kdf_profile_id       TEXT NOT NULL,
    created_at           TEXT NOT NULL,
    activated_at         TEXT,
    retired_at           TEXT
);";

pub const CONFLICTS_DDL: &str = "\
CREATE TABLE IF NOT EXISTS conflicts (
    conflict_id         TEXT PRIMARY KEY NOT NULL,
    object_type         TEXT NOT NULL,
    object_id           TEXT NOT NULL,
    base_commit_id      TEXT NOT NULL,
    local_commit_id     TEXT NOT NULL,
    incoming_commit_id  TEXT NOT NULL,
    conflicting_fields  TEXT NOT NULL,
    resolution          TEXT NOT NULL DEFAULT 'unresolved',
    created_at          TEXT NOT NULL,
    resolved_at         TEXT
);";

pub const UNLOCK_METHODS_DDL: &str = "\
CREATE TABLE IF NOT EXISTS unlock_methods (
    method_id            TEXT PRIMARY KEY NOT NULL,
    method_type          TEXT NOT NULL CHECK(method_type IN ('pin','password','security_key')),
    kdf_profile_id       TEXT NOT NULL,
    kdf_params_ct        BLOB NOT NULL,
    wrapped_vault_key_ct BLOB NOT NULL,
    created_at           TEXT NOT NULL,
    updated_at           TEXT NOT NULL
);";

pub const PROJECT_TAGS_DDL: &str = "\
CREATE TABLE IF NOT EXISTS project_tags (
    project_id TEXT NOT NULL,
    tag        TEXT NOT NULL COLLATE NOCASE,
    PRIMARY KEY (project_id, tag),
    FOREIGN KEY (project_id) REFERENCES projects(project_id)
);";

pub const PROJECT_TITLES_FTS_DDL: &str = "\
CREATE VIRTUAL TABLE IF NOT EXISTS project_titles_fts USING fts5(
    project_id UNINDEXED,
    title,
    tokenize='unicode61 remove_diacritics 2'
);";

// ---------------------------------------------------------------------------
// 索引
// ---------------------------------------------------------------------------

pub const INDEX_DDL: &str = "\
CREATE INDEX IF NOT EXISTS idx_projects_updated_at
    ON projects(updated_at);
CREATE INDEX IF NOT EXISTS idx_projects_group_id
    ON projects(group_id);
CREATE INDEX IF NOT EXISTS idx_projects_deleted
    ON projects(deleted);
CREATE INDEX IF NOT EXISTS idx_projects_head_commit_id
    ON projects(head_commit_id);
CREATE INDEX IF NOT EXISTS idx_entries_project_id
    ON entries(project_id);
CREATE INDEX IF NOT EXISTS idx_entries_type
    ON entries(entry_type);
CREATE INDEX IF NOT EXISTS idx_entries_updated_at
    ON entries(updated_at);
CREATE INDEX IF NOT EXISTS idx_entries_deleted
    ON entries(deleted);
CREATE INDEX IF NOT EXISTS idx_attachments_project_id
    ON attachments(project_id);
CREATE INDEX IF NOT EXISTS idx_attachments_entry_id
    ON attachments(entry_id);
CREATE INDEX IF NOT EXISTS idx_attachments_content_hash
    ON attachments(content_hash);
CREATE INDEX IF NOT EXISTS idx_attachments_deleted
    ON attachments(deleted);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_commits_device_seq
    ON commits(device_id, local_seq);
CREATE INDEX IF NOT EXISTS idx_commits_created_at
    ON commits(created_at);
CREATE INDEX IF NOT EXISTS idx_commits_device_id
    ON commits(device_id);
CREATE INDEX IF NOT EXISTS idx_object_versions_object
    ON object_versions(object_type, object_id);
CREATE INDEX IF NOT EXISTS idx_object_versions_commit
    ON object_versions(commit_id);
CREATE INDEX IF NOT EXISTS idx_tombstones_target
    ON tombstones(target_object_type, target_object_id);
CREATE INDEX IF NOT EXISTS idx_tombstones_deleted_at
    ON tombstones(deleted_at);
CREATE INDEX IF NOT EXISTS idx_project_tags_tag
    ON project_tags(tag);";

// ---------------------------------------------------------------------------
// 测试
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use rusqlite::Connection;

    #[test]
    fn test_all_tables_created() {
        let conn = Connection::open_in_memory().unwrap();
        conn.execute_batch("PRAGMA foreign_keys = ON;").unwrap();
        create_all_tables(&conn).unwrap();

        let tables: Vec<String> = conn
            .prepare("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")
            .unwrap()
            .query_map([], |row| row.get(0))
            .unwrap()
            .filter_map(|r| r.ok())
            .collect();

        let required = [
            "vault_meta",
            "projects",
            "entries",
            "attachments",
            "attachment_chunks",
            "commits",
            "commit_parents",
            "device_heads",
            "branches",
            "object_versions",
            "tombstones",
            "snapshots",
            "key_epochs",
            "conflicts",
            "unlock_methods",
            "project_tags",
            "project_titles_fts",
        ];
        for table in required {
            assert!(
                tables.contains(&table.to_string()),
                "missing table: {}",
                table
            );
        }
    }

    #[test]
    fn test_entries_fk_to_projects() {
        let conn = Connection::open_in_memory().unwrap();
        conn.execute_batch("PRAGMA foreign_keys = ON;").unwrap();
        create_all_tables(&conn).unwrap();

        // 没有 project 时插入 entry 应该失败
        let result = conn.execute(
            "INSERT INTO entries (entry_id, project_id, entry_type, payload_ct, object_clock, head_commit_id, created_at, updated_at, created_by_device_id, updated_by_device_id)
             VALUES ('e1', 'nonexistent', 'login', X'00', '{}', 'c1', '2024-01-01T00:00:00Z', '2024-01-01T00:00:00Z', 'd1', 'd1')",
            [],
        );
        assert!(
            result.is_err(),
            "FK constraint should reject nonexistent project_id"
        );
    }

    #[test]
    fn test_attachments_fk_to_projects() {
        let conn = Connection::open_in_memory().unwrap();
        conn.execute_batch("PRAGMA foreign_keys = ON;").unwrap();
        create_all_tables(&conn).unwrap();

        // 没有 project 时插入 attachment 应该失败
        let result = conn.execute(
            "INSERT INTO attachments (attachment_id, project_id, file_name_ct, storage_mode, content_hash, original_size, stored_size, head_commit_id, created_at, updated_at, created_by_device_id, updated_by_device_id)
             VALUES ('a1', 'nonexistent', X'00', 'embedded-inline', 'hash', 100, 100, 'c1', '2024-01-01T00:00:00Z', '2024-01-01T00:00:00Z', 'd1', 'd1')",
            [],
        );
        assert!(
            result.is_err(),
            "FK constraint should reject nonexistent project_id"
        );
    }

    #[test]
    fn test_attachment_chunks_unique_pk() {
        let conn = Connection::open_in_memory().unwrap();
        conn.execute_batch("PRAGMA foreign_keys = ON;").unwrap();
        create_all_tables(&conn).unwrap();

        // 需要先有 project 和 attachment
        conn.execute(
            "INSERT INTO projects (project_id, title_ct, object_clock, head_commit_id, created_at, updated_at, created_by_device_id, updated_by_device_id)
             VALUES ('p1', X'00', '{}', 'c1', '2024-01-01T00:00:00Z', '2024-01-01T00:00:00Z', 'd1', 'd1')",
            [],
        ).unwrap();
        conn.execute(
            "INSERT INTO attachments (attachment_id, project_id, file_name_ct, storage_mode, content_hash, original_size, stored_size, head_commit_id, created_at, updated_at, created_by_device_id, updated_by_device_id)
             VALUES ('a1', 'p1', X'00', 'embedded-chunked', 'hash', 100, 100, 'c1', '2024-01-01T00:00:00Z', '2024-01-01T00:00:00Z', 'd1', 'd1')",
            [],
        ).unwrap();

        // 相同 attachment_id + chunk_index 应该唯一
        conn.execute(
            "INSERT INTO attachment_chunks (attachment_id, chunk_index, chunk_hash, chunk_ct, stored_size, created_at)
             VALUES ('a1', 0, 'h0', X'00', 50, '2024-01-01T00:00:00Z')",
            [],
        ).unwrap();
        let result = conn.execute(
            "INSERT INTO attachment_chunks (attachment_id, chunk_index, chunk_hash, chunk_ct, stored_size, created_at)
             VALUES ('a1', 0, 'h0', X'00', 50, '2024-01-01T00:00:00Z')",
            [],
        );
        assert!(
            result.is_err(),
            "duplicate (attachment_id, chunk_index) must be rejected"
        );
    }

    #[test]
    fn test_commit_parents_dag() {
        let conn = Connection::open_in_memory().unwrap();
        conn.execute_batch("PRAGMA foreign_keys = ON;").unwrap();
        create_all_tables(&conn).unwrap();

        // 创建两个 commit
        for cid in ["c1", "c2"] {
            conn.execute(
                "INSERT INTO commits (commit_id, device_id, local_seq, commit_kind, change_scope, changed_object_ids_ct, vector_clock, created_at, integrity_tag)
                 VALUES (?1, 'd1', ?2, 'change', 'project', X'00', '{}', '2024-01-01T00:00:00Z', X'00')",
                rusqlite::params![cid, cid.chars().last().unwrap() as u32],
            ).unwrap();
        }

        // commit_parents 构建 DAG
        conn.execute(
            "INSERT INTO commit_parents (commit_id, parent_commit_id) VALUES ('c2', 'c1')",
            [],
        )
        .unwrap();

        let count: i32 = conn
            .query_row(
                "SELECT COUNT(*) FROM commit_parents WHERE commit_id = 'c2' AND parent_commit_id = 'c1'",
                [],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(count, 1);
    }

    #[test]
    fn test_chunk_check_constraint() {
        let conn = Connection::open_in_memory().unwrap();
        conn.execute_batch("PRAGMA foreign_keys = ON;").unwrap();
        create_all_tables(&conn).unwrap();

        // 需要先有 project 和 attachment
        conn.execute(
            "INSERT INTO projects (project_id, title_ct, object_clock, head_commit_id, created_at, updated_at, created_by_device_id, updated_by_device_id)
             VALUES ('p1', X'00', '{}', 'c1', '2024-01-01T00:00:00Z', '2024-01-01T00:00:00Z', 'd1', 'd1')",
            [],
        ).unwrap();
        conn.execute(
            "INSERT INTO attachments (attachment_id, project_id, file_name_ct, storage_mode, content_hash, original_size, stored_size, head_commit_id, created_at, updated_at, created_by_device_id, updated_by_device_id)
             VALUES ('a1', 'p1', X'00', 'embedded-chunked', 'hash', 100, 100, 'c1', '2024-01-01T00:00:00Z', '2024-01-01T00:00:00Z', 'd1', 'd1')",
            [],
        ).unwrap();

        // chunk_ct 和 external_uri_ct 都为空应该违反 CHECK 约束
        let result = conn.execute(
            "INSERT INTO attachment_chunks (attachment_id, chunk_index, chunk_hash, stored_size, created_at)
             VALUES ('a1', 0, 'h0', 50, '2024-01-01T00:00:00Z')",
            [],
        );
        assert!(
            result.is_err(),
            "CHECK constraint should reject when both chunk_ct and external_uri_ct are NULL"
        );
    }
}
