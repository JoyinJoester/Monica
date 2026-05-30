use rusqlite::params;
use rusqlite::OptionalExtension;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use uuid::Uuid;

use mdbx_core::model::attachment::StorageMode;
use mdbx_core::model::{Attachment, Entry, EntryType, Project, Snapshot};

use crate::connection::VaultConnection;
use crate::crypto_layer::{decrypt_field, encrypt_field};
use crate::error::{StorageError, StorageResult};
use crate::repo::commit_ctx::CommitContext;

/// Snapshot 内部负载（MVP 阶段为未加密 JSON）。
#[derive(Debug, Clone, Serialize, Deserialize)]
struct SnapshotPayload {
    vault_id: String,
    format_version: String,
    snapshot_created_at: String,
    projects: Vec<Project>,
    entries: Vec<Entry>,
    attachments: Vec<Attachment>,
}

/// Snapshot 持久化仓库。
///
/// 负责创建和恢复检查点，捕获 projects / entries / attachments 元数据。
pub struct SnapshotRepo;

impl SnapshotRepo {
    // -----------------------------------------------------------------------
    // CREATE
    // -----------------------------------------------------------------------

    /// 创建 snapshot：捕获当前所有未删除对象的元数据。
    pub fn create_snapshot(conn: &VaultConnection, ctx: &CommitContext) -> StorageResult<Snapshot> {
        let now = chrono::Utc::now().to_rfc3339();
        let snapshot_id = Uuid::new_v4().to_string();

        let vault_id: String = conn
            .inner()
            .query_row("SELECT vault_id FROM vault_meta", [], |row| row.get(0))
            .map_err(StorageError::Database)?;

        let projects = read_all_active_projects(conn)?;
        let entries = read_all_active_entries(conn)?;
        let attachments = read_all_active_attachments(conn)?;

        let payload = SnapshotPayload {
            vault_id,
            format_version: "MDBX-1".to_string(),
            snapshot_created_at: now.clone(),
            projects,
            entries,
            attachments,
        };

        let snapshot_json = serde_json::to_vec(&payload)
            .map_err(|e| StorageError::SchemaCreation(e.to_string()))?;
        let snapshot_ct = Self::encrypt_payload(conn, &snapshot_id, &snapshot_json)?;
        let snapshot_hash = compute_sha256_hex(&snapshot_ct);

        // 创建 snapshot commit（kind="snapshot", scope="multi"）
        let commit_id =
            ctx.create_commit(conn, "snapshot", "multi", &[snapshot_id.clone()], &[])?;

        conn.inner().execute(
            "INSERT INTO snapshots (snapshot_id, base_commit_id, snapshot_ct,
             snapshot_hash, created_at, created_by_device_id)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
            params![
                snapshot_id,
                commit_id,
                snapshot_ct,
                snapshot_hash,
                now,
                ctx.device_id,
            ],
        )?;

        Ok(Snapshot {
            snapshot_id,
            base_commit_id: commit_id,
            snapshot_ct,
            snapshot_hash,
            created_at: now,
            created_by_device_id: ctx.device_id.clone(),
        })
    }

    // -----------------------------------------------------------------------
    // RESTORE
    // -----------------------------------------------------------------------

    /// 从 snapshot 恢复 projects / entries / attachments 元数据。
    ///
    /// 每个对象使用 INSERT OR REPLACE，保持原始 ID 不变。
    /// 恢复完成后创建一个 "snapshot" 类型的 commit。
    pub fn restore_snapshot(
        conn: &VaultConnection,
        ctx: &CommitContext,
        snapshot_id: &str,
    ) -> StorageResult<()> {
        let snap = SnapshotRepo::get_by_id(conn, snapshot_id)?
            .ok_or_else(|| StorageError::NotFound(snapshot_id.to_string()))?;

        // 校验 hash
        let computed = compute_sha256_hex(&snap.snapshot_ct);
        if computed != snap.snapshot_hash {
            return Err(StorageError::ConstraintViolation(format!(
                "snapshot hash mismatch: expected {}, got {}",
                snap.snapshot_hash, computed
            )));
        }

        let snapshot_json = Self::decrypt_payload(conn, snapshot_id, &snap.snapshot_ct)?;
        let payload: SnapshotPayload = serde_json::from_slice(&snapshot_json)
            .map_err(|e| StorageError::SchemaCreation(e.to_string()))?;

        // 按依赖顺序恢复：projects → entries → attachments
        for p in &payload.projects {
            upsert_project(conn, p)?;
        }

        for e in &payload.entries {
            upsert_entry(conn, e)?;
        }

        for a in &payload.attachments {
            upsert_attachment(conn, a)?;
        }

        // 创建恢复 commit
        ctx.create_commit(
            conn,
            "snapshot",
            "multi",
            &[snapshot_id.to_string()],
            &[snap.base_commit_id],
        )?;

        Ok(())
    }

    // -----------------------------------------------------------------------
    // READ
    // -----------------------------------------------------------------------

    pub fn get_by_id(conn: &VaultConnection, snapshot_id: &str) -> StorageResult<Option<Snapshot>> {
        conn.inner()
            .query_row(
                "SELECT snapshot_id, base_commit_id, snapshot_ct, snapshot_hash,
                        created_at, created_by_device_id
                 FROM snapshots WHERE snapshot_id = ?1",
                params![snapshot_id],
                |row| {
                    Ok(Snapshot {
                        snapshot_id: row.get(0)?,
                        base_commit_id: row.get(1)?,
                        snapshot_ct: row.get(2)?,
                        snapshot_hash: row.get(3)?,
                        created_at: row.get(4)?,
                        created_by_device_id: row.get(5)?,
                    })
                },
            )
            .optional()
            .map_err(StorageError::Database)
    }

    pub fn list_all(conn: &VaultConnection) -> StorageResult<Vec<Snapshot>> {
        let mut stmt = conn.inner().prepare(
            "SELECT snapshot_id, base_commit_id, snapshot_ct, snapshot_hash,
                    created_at, created_by_device_id
             FROM snapshots ORDER BY created_at DESC",
        )?;

        let rows = stmt.query_map([], |row| {
            Ok(Snapshot {
                snapshot_id: row.get(0)?,
                base_commit_id: row.get(1)?,
                snapshot_ct: row.get(2)?,
                snapshot_hash: row.get(3)?,
                created_at: row.get(4)?,
                created_by_device_id: row.get(5)?,
            })
        })?;

        let mut snapshots = Vec::new();
        for row in rows {
            snapshots.push(row?);
        }
        Ok(snapshots)
    }

    /// 校验 snapshot 内部 hash 一致性。
    pub fn verify_integrity(conn: &VaultConnection, snapshot_id: &str) -> StorageResult<bool> {
        let snap = match SnapshotRepo::get_by_id(conn, snapshot_id)? {
            Some(s) => s,
            None => return Ok(false),
        };
        Ok(compute_sha256_hex(&snap.snapshot_ct) == snap.snapshot_hash)
    }

    // -----------------------------------------------------------------------
    // ENCRYPTION HELPERS
    // -----------------------------------------------------------------------

    fn encrypt_payload(
        conn: &VaultConnection,
        id: &str,
        plaintext: &[u8],
    ) -> StorageResult<Vec<u8>> {
        let subkey = conn
            .keyring()
            .map(|kr| kr.metadata_subkey.clone())
            .unwrap_or_default();
        encrypt_field(
            conn.keyring(),
            &subkey,
            plaintext,
            "snapshot",
            id,
            "payload",
        )
        .map_err(StorageError::Crypto)
    }

    fn decrypt_payload(
        conn: &VaultConnection,
        id: &str,
        ciphertext: &[u8],
    ) -> StorageResult<Vec<u8>> {
        let subkey = conn
            .keyring()
            .map(|kr| kr.metadata_subkey.clone())
            .unwrap_or_default();
        decrypt_field(
            conn.keyring(),
            &subkey,
            ciphertext,
            "snapshot",
            id,
            "payload",
        )
        .map_err(StorageError::Crypto)
    }
}

// ---------------------------------------------------------------------------
// 内部辅助函数
// ---------------------------------------------------------------------------

fn read_all_active_projects(conn: &VaultConnection) -> StorageResult<Vec<Project>> {
    let mut stmt = conn.inner().prepare(
        "SELECT project_id, title_ct, summary_ct, group_id, icon_ref,
                favorite, archived, deleted, tiga_mode_override, object_clock,
                head_commit_id, attachment_count, created_at, updated_at,
                created_by_device_id, updated_by_device_id
         FROM projects WHERE deleted = 0 ORDER BY updated_at DESC",
    )?;

    let rows = stmt.query_map([], |row| {
        Ok(Project {
            project_id: row.get(0)?,
            title_ct: row.get::<_, Vec<u8>>(1)?,
            summary_ct: row.get::<_, Option<Vec<u8>>>(2)?,
            group_id: row.get(3)?,
            icon_ref: row.get(4)?,
            favorite: row.get::<_, i32>(5)? != 0,
            archived: row.get::<_, i32>(6)? != 0,
            deleted: row.get::<_, i32>(7)? != 0,
            tiga_mode_override: row
                .get::<_, Option<String>>(8)?
                .and_then(|s| s.parse().ok()),
            object_clock: row.get(9)?,
            head_commit_id: row.get(10)?,
            attachment_count: row.get::<_, i32>(11)? as u32,
            created_at: row.get(12)?,
            updated_at: row.get(13)?,
            created_by_device_id: row.get(14)?,
            updated_by_device_id: row.get(15)?,
        })
    })?;

    let mut projects = Vec::new();
    for row in rows {
        projects.push(row?);
    }
    Ok(projects)
}

fn read_all_active_entries(conn: &VaultConnection) -> StorageResult<Vec<Entry>> {
    let mut stmt = conn.inner().prepare(
        "SELECT entry_id, project_id, entry_type, title_ct, payload_ct,
                payload_schema_version, tiga_mode_override, object_clock,
                head_commit_id, deleted, created_at, updated_at,
                created_by_device_id, updated_by_device_id
         FROM entries WHERE deleted = 0 ORDER BY updated_at DESC",
    )?;

    let rows = stmt.query_map([], |row| {
        Ok(Entry {
            entry_id: row.get(0)?,
            project_id: row.get(1)?,
            entry_type: {
                let s: String = row.get(2)?;
                s.parse().unwrap_or(EntryType::Login)
            },
            title_ct: row.get::<_, Option<Vec<u8>>>(3)?,
            payload_ct: row.get::<_, Vec<u8>>(4)?,
            payload_schema_version: row.get::<_, i32>(5)? as u32,
            tiga_mode_override: row
                .get::<_, Option<String>>(6)?
                .and_then(|s| s.parse().ok()),
            object_clock: row.get(7)?,
            head_commit_id: row.get(8)?,
            deleted: row.get::<_, i32>(9)? != 0,
            created_at: row.get(10)?,
            updated_at: row.get(11)?,
            created_by_device_id: row.get(12)?,
            updated_by_device_id: row.get(13)?,
        })
    })?;

    let mut entries = Vec::new();
    for row in rows {
        entries.push(row?);
    }
    Ok(entries)
}

fn read_all_active_attachments(conn: &VaultConnection) -> StorageResult<Vec<Attachment>> {
    let mut stmt = conn.inner().prepare(
        "SELECT attachment_id, project_id, entry_id, file_name_ct,
                media_type_ct, storage_mode, content_hash,
                original_size, stored_size, chunk_count, head_commit_id,
                deleted, created_at, updated_at,
                created_by_device_id, updated_by_device_id
         FROM attachments WHERE deleted = 0 ORDER BY updated_at DESC",
    )?;

    let rows = stmt.query_map([], |row| {
        Ok(Attachment {
            attachment_id: row.get(0)?,
            project_id: row.get(1)?,
            entry_id: row.get(2)?,
            file_name_ct: row.get::<_, Vec<u8>>(3)?,
            media_type_ct: row.get::<_, Option<Vec<u8>>>(4)?,
            storage_mode: {
                let s: String = row.get(5)?;
                s.parse().unwrap_or(StorageMode::EmbeddedInline)
            },
            content_hash: row.get(6)?,
            original_size: row.get::<_, i64>(7)? as u64,
            stored_size: row.get::<_, i64>(8)? as u64,
            chunk_count: row.get::<_, i32>(9)? as u32,
            head_commit_id: row.get(10)?,
            deleted: row.get::<_, i32>(11)? != 0,
            created_at: row.get(12)?,
            updated_at: row.get(13)?,
            created_by_device_id: row.get(14)?,
            updated_by_device_id: row.get(15)?,
        })
    })?;

    let mut attachments = Vec::new();
    for row in rows {
        attachments.push(row?);
    }
    Ok(attachments)
}

fn upsert_project(conn: &VaultConnection, p: &Project) -> StorageResult<()> {
    conn.inner().execute(
        "INSERT OR REPLACE INTO projects (project_id, title_ct, summary_ct, group_id,
         icon_ref, favorite, archived, deleted, tiga_mode_override, object_clock,
         head_commit_id, attachment_count, created_at, updated_at,
         created_by_device_id, updated_by_device_id)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16)",
        params![
            p.project_id,
            p.title_ct,
            p.summary_ct,
            p.group_id,
            p.icon_ref,
            p.favorite as i32,
            p.archived as i32,
            p.deleted as i32,
            p.tiga_mode_override.as_ref().map(|m| m.to_string()),
            p.object_clock,
            p.head_commit_id,
            p.attachment_count as i32,
            p.created_at,
            p.updated_at,
            p.created_by_device_id,
            p.updated_by_device_id,
        ],
    )?;
    Ok(())
}

fn upsert_entry(conn: &VaultConnection, e: &Entry) -> StorageResult<()> {
    conn.inner().execute(
        "INSERT OR REPLACE INTO entries (entry_id, project_id, entry_type, title_ct,
         payload_ct, payload_schema_version, tiga_mode_override, object_clock,
         head_commit_id, deleted, created_at, updated_at,
         created_by_device_id, updated_by_device_id)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)",
        params![
            e.entry_id,
            e.project_id,
            e.entry_type.to_string(),
            e.title_ct,
            e.payload_ct,
            e.payload_schema_version as i32,
            e.tiga_mode_override.as_ref().map(|m| m.to_string()),
            e.object_clock,
            e.head_commit_id,
            e.deleted as i32,
            e.created_at,
            e.updated_at,
            e.created_by_device_id,
            e.updated_by_device_id,
        ],
    )?;
    Ok(())
}

fn upsert_attachment(conn: &VaultConnection, a: &Attachment) -> StorageResult<()> {
    conn.inner().execute(
        "INSERT OR REPLACE INTO attachments (attachment_id, project_id, entry_id,
         file_name_ct, media_type_ct, storage_mode, content_hash,
         original_size, stored_size, chunk_count, head_commit_id,
         deleted, created_at, updated_at, created_by_device_id, updated_by_device_id)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16)",
        params![
            a.attachment_id,
            a.project_id,
            a.entry_id,
            a.file_name_ct,
            a.media_type_ct,
            a.storage_mode.to_string(),
            a.content_hash,
            a.original_size as i64,
            a.stored_size as i64,
            a.chunk_count as i32,
            a.head_commit_id,
            a.deleted as i32,
            a.created_at,
            a.updated_at,
            a.created_by_device_id,
            a.updated_by_device_id,
        ],
    )?;
    Ok(())
}

fn compute_sha256_hex(data: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(data);
    format!("{:x}", hasher.finalize())
}

// ---------------------------------------------------------------------------
// 测试
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use crate::init::{initialize_vault, VaultInitParams};
    use crate::repo::attachment::AttachmentRepo;
    use crate::repo::entry::EntryRepo;
    use crate::repo::project::ProjectRepo;

    fn setup() -> (VaultConnection, CommitContext) {
        let conn = VaultConnection::open_in_memory().unwrap();
        let params = VaultInitParams::default();
        initialize_vault(&conn, &params).unwrap();
        let ctx = CommitContext::new("test-device".to_string());
        (conn, ctx)
    }

    fn login_payload() -> serde_json::Value {
        serde_json::json!({"username": "alice", "password": "s3cret"})
    }

    // -----------------------------------------------------------------------
    // CREATE SNAPSHOT
    // -----------------------------------------------------------------------

    #[test]
    fn test_create_empty_snapshot() {
        let (conn, ctx) = setup();
        let snap = SnapshotRepo::create_snapshot(&conn, &ctx).unwrap();

        assert!(!snap.snapshot_id.is_empty());
        assert!(!snap.base_commit_id.is_empty());
        assert!(!snap.snapshot_ct.is_empty());
        assert_eq!(snap.snapshot_hash.len(), 64);
        assert_eq!(snap.created_by_device_id, "test-device");

        // 验证 payload 可反序列化
        let payload: SnapshotPayload = serde_json::from_slice(&snap.snapshot_ct).unwrap();
        assert_eq!(payload.format_version, "MDBX-1");
        assert!(payload.projects.is_empty());
        assert!(payload.entries.is_empty());
        assert!(payload.attachments.is_empty());
    }

    #[test]
    fn test_snapshot_captures_projects() {
        let (conn, ctx) = setup();
        ProjectRepo::create(&conn, &ctx, "Alpha", None, None).unwrap();
        ProjectRepo::create(&conn, &ctx, "Beta", None, None).unwrap();

        let snap = SnapshotRepo::create_snapshot(&conn, &ctx).unwrap();
        let payload: SnapshotPayload = serde_json::from_slice(&snap.snapshot_ct).unwrap();

        assert_eq!(payload.projects.len(), 2);
        let titles: Vec<&str> = payload
            .projects
            .iter()
            .map(|p| std::str::from_utf8(&p.title_ct).unwrap())
            .collect();
        assert!(titles.contains(&"Alpha"));
        assert!(titles.contains(&"Beta"));
    }

    #[test]
    fn test_snapshot_excludes_deleted() {
        let (conn, ctx) = setup();
        let p1 = ProjectRepo::create(&conn, &ctx, "Keep", None, None).unwrap();
        let p2 = ProjectRepo::create(&conn, &ctx, "Delete", None, None).unwrap();
        ProjectRepo::soft_delete(&conn, &ctx, &p2.project_id).unwrap();

        let snap = SnapshotRepo::create_snapshot(&conn, &ctx).unwrap();
        let payload: SnapshotPayload = serde_json::from_slice(&snap.snapshot_ct).unwrap();

        assert_eq!(payload.projects.len(), 1);
        assert_eq!(payload.projects[0].project_id, p1.project_id);
    }

    #[test]
    fn test_snapshot_captures_entries() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "P", None, None).unwrap();
        EntryRepo::create(
            &conn,
            &ctx,
            &project.project_id,
            EntryType::Login,
            Some("E1"),
            &login_payload(),
        )
        .unwrap();
        EntryRepo::create(
            &conn,
            &ctx,
            &project.project_id,
            EntryType::Note,
            Some("E2"),
            &serde_json::json!({"text":"hi"}),
        )
        .unwrap();

        let snap = SnapshotRepo::create_snapshot(&conn, &ctx).unwrap();
        let payload: SnapshotPayload = serde_json::from_slice(&snap.snapshot_ct).unwrap();

        assert_eq!(payload.entries.len(), 2);
        for e in &payload.entries {
            assert_eq!(e.project_id, project.project_id);
        }
    }

    #[test]
    fn test_snapshot_captures_attachments() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "P", None, None).unwrap();
        AttachmentRepo::add(
            &conn,
            &ctx,
            &project.project_id,
            None,
            "a.txt",
            None,
            "h1",
            100,
        )
        .unwrap();
        AttachmentRepo::add(
            &conn,
            &ctx,
            &project.project_id,
            None,
            "b.txt",
            None,
            "h2",
            200,
        )
        .unwrap();

        let snap = SnapshotRepo::create_snapshot(&conn, &ctx).unwrap();
        let payload: SnapshotPayload = serde_json::from_slice(&snap.snapshot_ct).unwrap();

        assert_eq!(payload.attachments.len(), 2);
    }

    #[test]
    fn test_snapshot_commit_created() {
        let (conn, ctx) = setup();
        let snap = SnapshotRepo::create_snapshot(&conn, &ctx).unwrap();

        let (commit_kind, change_scope): (String, String) = conn
            .inner()
            .query_row(
                "SELECT commit_kind, change_scope FROM commits WHERE commit_id = ?1",
                params![snap.base_commit_id],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .unwrap();
        assert_eq!(commit_kind, "snapshot");
        assert_eq!(change_scope, "multi");
    }

    // -----------------------------------------------------------------------
    // RESTORE SNAPSHOT
    // -----------------------------------------------------------------------

    #[test]
    fn test_restore_rebuilds_projects() {
        let (conn, ctx) = setup();

        // 创建一些数据并拍快照
        ProjectRepo::create(&conn, &ctx, "Original", None, None).unwrap();
        let snap = SnapshotRepo::create_snapshot(&conn, &ctx).unwrap();

        // 清空 projects（模拟数据丢失）
        conn.inner().execute("DELETE FROM entries", []).unwrap();
        conn.inner().execute("DELETE FROM attachments", []).unwrap();
        conn.inner().execute("DELETE FROM projects", []).unwrap();

        // 恢复
        SnapshotRepo::restore_snapshot(&conn, &ctx, &snap.snapshot_id).unwrap();

        let restored = ProjectRepo::list_all(&conn).unwrap();
        assert_eq!(restored.len(), 1);
        assert_eq!(restored[0].title_ct, b"Original");
    }

    #[test]
    fn test_restore_rebuilds_entries() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "P", None, None).unwrap();
        EntryRepo::create(
            &conn,
            &ctx,
            &project.project_id,
            EntryType::Login,
            Some("MyLogin"),
            &login_payload(),
        )
        .unwrap();

        let snap = SnapshotRepo::create_snapshot(&conn, &ctx).unwrap();

        // 清空
        conn.inner().execute("DELETE FROM entries", []).unwrap();
        conn.inner().execute("DELETE FROM attachments", []).unwrap();
        conn.inner().execute("DELETE FROM projects", []).unwrap();

        // 恢复
        SnapshotRepo::restore_snapshot(&conn, &ctx, &snap.snapshot_id).unwrap();

        let entries = read_all_active_entries(&conn).unwrap();
        assert_eq!(entries.len(), 1);
        assert_eq!(entries[0].entry_type, EntryType::Login);
        assert_eq!(entries[0].title_ct, Some(b"MyLogin".to_vec()));
    }

    #[test]
    fn test_restore_rebuilds_attachments() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "P", None, None).unwrap();
        AttachmentRepo::add(
            &conn,
            &ctx,
            &project.project_id,
            None,
            "photo.png",
            Some("image/png"),
            "abc123",
            512,
        )
        .unwrap();

        let snap = SnapshotRepo::create_snapshot(&conn, &ctx).unwrap();

        // 清空
        conn.inner().execute("DELETE FROM attachments", []).unwrap();
        conn.inner().execute("DELETE FROM entries", []).unwrap();
        conn.inner().execute("DELETE FROM projects", []).unwrap();

        // 恢复
        SnapshotRepo::restore_snapshot(&conn, &ctx, &snap.snapshot_id).unwrap();

        let attachments = read_all_active_attachments(&conn).unwrap();
        assert_eq!(attachments.len(), 1);
        assert_eq!(attachments[0].file_name_ct, b"photo.png");
        assert_eq!(attachments[0].media_type_ct, Some(b"image/png".to_vec()));
        assert_eq!(attachments[0].content_hash, "abc123");
        assert_eq!(attachments[0].original_size, 512);
    }

    #[test]
    fn test_restore_creates_commit() {
        let (conn, ctx) = setup();
        ProjectRepo::create(&conn, &ctx, "P", None, None).unwrap();
        let snap = SnapshotRepo::create_snapshot(&conn, &ctx).unwrap();

        // 清空并恢复
        conn.inner().execute("DELETE FROM entries", []).unwrap();
        conn.inner().execute("DELETE FROM attachments", []).unwrap();
        conn.inner().execute("DELETE FROM projects", []).unwrap();
        SnapshotRepo::restore_snapshot(&conn, &ctx, &snap.snapshot_id).unwrap();

        // 恢复后应有新的 snapshot commit
        let count: i32 = conn
            .inner()
            .query_row(
                "SELECT COUNT(*) FROM commits WHERE commit_kind = 'snapshot'",
                [],
                |row| row.get(0),
            )
            .unwrap();
        assert!(
            count >= 2,
            "expected at least 2 snapshot commits, got {}",
            count
        );
    }

    #[test]
    fn test_restore_hash_mismatch_rejected() {
        let (conn, ctx) = setup();
        let mut snap = SnapshotRepo::create_snapshot(&conn, &ctx).unwrap();

        // 篡改 snapshot_ct 但不改 hash
        snap.snapshot_ct = b"corrupted".to_vec();
        conn.inner()
            .execute(
                "UPDATE snapshots SET snapshot_ct = ?1 WHERE snapshot_id = ?2",
                params![snap.snapshot_ct, snap.snapshot_id],
            )
            .unwrap();

        let result = SnapshotRepo::restore_snapshot(&conn, &ctx, &snap.snapshot_id);
        assert!(result.is_err());
    }

    #[test]
    fn test_restore_nonexistent() {
        let (conn, ctx) = setup();
        let result = SnapshotRepo::restore_snapshot(&conn, &ctx, "nonexistent");
        assert!(result.is_err());
    }

    // -----------------------------------------------------------------------
    // READ
    // -----------------------------------------------------------------------

    #[test]
    fn test_get_by_id() {
        let (conn, ctx) = setup();
        let created = SnapshotRepo::create_snapshot(&conn, &ctx).unwrap();

        let found = SnapshotRepo::get_by_id(&conn, &created.snapshot_id)
            .unwrap()
            .unwrap();
        assert_eq!(found.snapshot_id, created.snapshot_id);
        assert_eq!(found.snapshot_hash, created.snapshot_hash);
    }

    #[test]
    fn test_get_nonexistent() {
        let (conn, _ctx) = setup();
        let result = SnapshotRepo::get_by_id(&conn, "nonexistent").unwrap();
        assert!(result.is_none());
    }

    #[test]
    fn test_list_all() {
        let (conn, ctx) = setup();
        SnapshotRepo::create_snapshot(&conn, &ctx).unwrap();
        SnapshotRepo::create_snapshot(&conn, &ctx).unwrap();

        let all = SnapshotRepo::list_all(&conn).unwrap();
        assert_eq!(all.len(), 2);
        // 按时间降序排列
        assert!(all[0].created_at >= all[1].created_at);
    }

    // -----------------------------------------------------------------------
    // VERIFY INTEGRITY
    // -----------------------------------------------------------------------

    #[test]
    fn test_verify_integrity_passes() {
        let (conn, ctx) = setup();
        let snap = SnapshotRepo::create_snapshot(&conn, &ctx).unwrap();
        assert!(SnapshotRepo::verify_integrity(&conn, &snap.snapshot_id).unwrap());
    }

    #[test]
    fn test_verify_integrity_fails_on_tamper() {
        let (conn, ctx) = setup();
        let snap = SnapshotRepo::create_snapshot(&conn, &ctx).unwrap();

        conn.inner()
            .execute(
                "UPDATE snapshots SET snapshot_ct = ?1 WHERE snapshot_id = ?2",
                params![b"tampered payload", snap.snapshot_id],
            )
            .unwrap();

        assert!(!SnapshotRepo::verify_integrity(&conn, &snap.snapshot_id).unwrap());
    }

    #[test]
    fn test_verify_integrity_nonexistent() {
        let (conn, _ctx) = setup();
        assert!(!SnapshotRepo::verify_integrity(&conn, "nonexistent").unwrap());
    }

    // -----------------------------------------------------------------------
    // ROUNDTRIP
    // -----------------------------------------------------------------------

    #[test]
    fn test_full_roundtrip() {
        let (conn, ctx) = setup();

        // 创建完整数据集
        let p1 =
            ProjectRepo::create(&conn, &ctx, "Work", Some("group-1"), Some("icon-work")).unwrap();
        let p2 = ProjectRepo::create(&conn, &ctx, "Personal", None, None).unwrap();

        let e1 = EntryRepo::create(
            &conn,
            &ctx,
            &p1.project_id,
            EntryType::Login,
            Some("GitHub"),
            &serde_json::json!({"username": "gh", "password": "pass1"}),
        )
        .unwrap();
        let _e2 = EntryRepo::create(
            &conn,
            &ctx,
            &p2.project_id,
            EntryType::Note,
            Some("Ideas"),
            &serde_json::json!({"text": "build something"}),
        )
        .unwrap();

        let a1 = AttachmentRepo::add(
            &conn,
            &ctx,
            &p1.project_id,
            Some(&e1.entry_id),
            "screenshot.png",
            Some("image/png"),
            "hash1",
            1024,
        )
        .unwrap();
        let _a2 = AttachmentRepo::add(
            &conn,
            &ctx,
            &p2.project_id,
            None,
            "notes.txt",
            Some("text/plain"),
            "hash2",
            2048,
        )
        .unwrap();

        // 拍快照
        let snap = SnapshotRepo::create_snapshot(&conn, &ctx).unwrap();

        // 清空
        conn.inner()
            .execute("DELETE FROM attachment_chunks", [])
            .unwrap();
        conn.inner().execute("DELETE FROM attachments", []).unwrap();
        conn.inner().execute("DELETE FROM entries", []).unwrap();
        conn.inner().execute("DELETE FROM projects", []).unwrap();

        // 恢复
        SnapshotRepo::restore_snapshot(&conn, &ctx, &snap.snapshot_id).unwrap();

        // 验证完整恢复
        let projects = ProjectRepo::list_all(&conn).unwrap();
        assert_eq!(projects.len(), 2);

        let entries = read_all_active_entries(&conn).unwrap();
        assert_eq!(entries.len(), 2);

        let attachments = read_all_active_attachments(&conn).unwrap();
        assert_eq!(attachments.len(), 2);

        // 验证字段完整性
        let p1_restored = projects
            .iter()
            .find(|p| p.project_id == p1.project_id)
            .unwrap();
        assert_eq!(p1_restored.title_ct, b"Work");
        assert_eq!(p1_restored.group_id.as_deref(), Some("group-1"));
        assert_eq!(p1_restored.icon_ref.as_deref(), Some("icon-work"));

        let e1_restored = entries.iter().find(|e| e.entry_id == e1.entry_id).unwrap();
        assert_eq!(e1_restored.project_id, p1.project_id);
        assert_eq!(e1_restored.entry_type, EntryType::Login);
        assert_eq!(e1_restored.title_ct, Some(b"GitHub".to_vec()));

        let a1_restored = attachments
            .iter()
            .find(|a| a.attachment_id == a1.attachment_id)
            .unwrap();
        assert_eq!(a1_restored.entry_id, Some(e1.entry_id));
        assert_eq!(a1_restored.storage_mode, StorageMode::EmbeddedInline);
    }
}
