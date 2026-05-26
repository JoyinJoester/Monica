use rusqlite::params;
use rusqlite::types::Type;
use rusqlite::OptionalExtension;
use uuid::Uuid;

use mdbx_core::model::Project;

use crate::connection::VaultConnection;
use crate::crypto_layer::{decrypt_field, encrypt_field};
use crate::error::{StorageError, StorageResult};
use crate::repo::commit_ctx::CommitContext;

/// Project 的持久化仓库。
///
/// `_ct` 字段在写入时加密、读取时解密。
/// 若连接未附加 Keyring，则透传原始字节（用于测试和明文模式）。
pub struct ProjectRepo;

impl ProjectRepo {
    // -----------------------------------------------------------------------
    // CREATE
    // -----------------------------------------------------------------------

    pub fn create(
        conn: &VaultConnection,
        ctx: &CommitContext,
        title: &str,
        group_id: Option<&str>,
        icon_ref: Option<&str>,
    ) -> StorageResult<Project> {
        let now = chrono::Utc::now().to_rfc3339();
        let project_id = Uuid::new_v4().to_string();

        let commit_id = ctx.create_commit(conn, "change", "project", &[project_id.clone()], &[])?;

        let object_clock = format!(r#"{{"counter":1}}"#);

        let title_ct = Self::encrypt_metadata(conn, &project_id, "title", title.as_bytes())?;

        conn.inner().execute(
            "INSERT INTO projects (project_id, title_ct, summary_ct, group_id, icon_ref,
             favorite, archived, deleted, tiga_mode_override, object_clock,
             head_commit_id, attachment_count, created_at, updated_at,
             created_by_device_id, updated_by_device_id)
             VALUES (?1, ?2, NULL, ?3, ?4, 0, 0, 0, NULL, ?5, ?6, 0, ?7, ?7, ?8, ?8)",
            params![
                project_id,
                title_ct,
                group_id,
                icon_ref,
                object_clock,
                commit_id,
                now,
                ctx.device_id,
            ],
        )?;

        ProjectRepo::get_by_id(conn, &project_id)?
            .ok_or_else(|| StorageError::NotFound(project_id.clone()))
    }

    // -----------------------------------------------------------------------
    // READ
    // -----------------------------------------------------------------------

    pub fn get_by_id(conn: &VaultConnection, project_id: &str) -> StorageResult<Option<Project>> {
        conn.inner()
            .query_row(
                "SELECT project_id, title_ct, summary_ct, group_id, icon_ref,
                        favorite, archived, deleted, tiga_mode_override, object_clock,
                        head_commit_id, attachment_count, created_at, updated_at,
                        created_by_device_id, updated_by_device_id
                 FROM projects WHERE project_id = ?1",
                params![project_id],
                |row| {
                    let raw_title: Vec<u8> = row.get(1)?;
                    let raw_summary: Option<Vec<u8>> = row.get(2)?;
                    Ok(Project {
                        project_id: row.get(0)?,
                        title_ct: Self::decrypt_metadata(conn, project_id, "title", &raw_title)
                            .map_err(|e| {
                                rusqlite::Error::FromSqlConversionFailure(
                                    1,
                                    Type::Blob,
                                    Box::new(e),
                                )
                            })?,
                        summary_ct: raw_summary
                            .map(|s| {
                                Self::decrypt_metadata(conn, project_id, "summary", &s).map_err(
                                    |e| {
                                        rusqlite::Error::FromSqlConversionFailure(
                                            2,
                                            Type::Blob,
                                            Box::new(e),
                                        )
                                    },
                                )
                            })
                            .transpose()?,
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
                },
            )
            .optional()
            .map_err(StorageError::Database)
    }

    pub fn list_all(conn: &VaultConnection) -> StorageResult<Vec<Project>> {
        ProjectRepo::list_where(conn, "deleted = 0")
    }

    pub fn list_by_group(conn: &VaultConnection, group_id: &str) -> StorageResult<Vec<Project>> {
        ProjectRepo::list_where(
            conn,
            &format!(
                "deleted = 0 AND group_id = '{}'",
                group_id.replace('\'', "''")
            ),
        )
    }

    pub fn list_deleted(conn: &VaultConnection) -> StorageResult<Vec<Project>> {
        ProjectRepo::list_where(conn, "deleted = 1")
    }

    fn list_where(conn: &VaultConnection, where_clause: &str) -> StorageResult<Vec<Project>> {
        let sql = format!(
            "SELECT project_id, title_ct, summary_ct, group_id, icon_ref,
                    favorite, archived, deleted, tiga_mode_override, object_clock,
                    head_commit_id, attachment_count, created_at, updated_at,
                    created_by_device_id, updated_by_device_id
             FROM projects WHERE {} ORDER BY updated_at DESC",
            where_clause
        );

        let mut stmt = conn.inner().prepare(&sql)?;
        let rows = stmt.query_map([], |row| {
            let pid: String = row.get(0)?;
            let raw_title: Vec<u8> = row.get(1)?;
            let raw_summary: Option<Vec<u8>> = row.get(2)?;
            Ok(Project {
                project_id: pid.clone(),
                title_ct: ProjectRepo::decrypt_metadata(conn, &pid, "title", &raw_title).map_err(
                    |e| rusqlite::Error::FromSqlConversionFailure(1, Type::Blob, Box::new(e)),
                )?,
                summary_ct: raw_summary
                    .map(|s| {
                        ProjectRepo::decrypt_metadata(conn, &pid, "summary", &s).map_err(|e| {
                            rusqlite::Error::FromSqlConversionFailure(2, Type::Blob, Box::new(e))
                        })
                    })
                    .transpose()?,
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

    // -----------------------------------------------------------------------
    // UPDATE
    // -----------------------------------------------------------------------

    pub fn update(
        conn: &VaultConnection,
        ctx: &CommitContext,
        project: &Project,
    ) -> StorageResult<Project> {
        let now = chrono::Utc::now().to_rfc3339();

        let commit_id =
            ctx.commit_object_change(conn, "projects", &project.project_id, "change", "project")?;

        let object_clock = bump_clock(&project.object_clock);

        let title_ct =
            Self::encrypt_metadata(conn, &project.project_id, "title", &project.title_ct)?;
        let summary_ct = project
            .summary_ct
            .as_ref()
            .map(|s| Self::encrypt_metadata(conn, &project.project_id, "summary", s))
            .transpose()?;

        conn.inner().execute(
            "UPDATE projects SET
                title_ct = ?2, summary_ct = ?3, group_id = ?4, icon_ref = ?5,
                favorite = ?6, archived = ?7, deleted = ?8,
                tiga_mode_override = ?9, object_clock = ?10,
                head_commit_id = ?11, attachment_count = ?12,
                updated_at = ?13, updated_by_device_id = ?14
             WHERE project_id = ?1",
            params![
                project.project_id,
                title_ct,
                summary_ct,
                project.group_id,
                project.icon_ref,
                project.favorite as i32,
                project.archived as i32,
                project.deleted as i32,
                project.tiga_mode_override.as_ref().map(|m| m.to_string()),
                object_clock,
                commit_id,
                project.attachment_count as i32,
                now,
                ctx.device_id,
            ],
        )?;

        ProjectRepo::get_by_id(conn, &project.project_id)?
            .ok_or_else(|| StorageError::NotFound(project.project_id.clone()))
    }

    // -----------------------------------------------------------------------
    // SOFT DELETE
    // -----------------------------------------------------------------------

    /// 软删除：标记 deleted=1，生成 tombstone 和 commit。
    ///
    /// 不会物理删除下游 entry 或 attachment 数据。
    pub fn soft_delete(
        conn: &VaultConnection,
        ctx: &CommitContext,
        project_id: &str,
    ) -> StorageResult<()> {
        // 验证存在
        let project = ProjectRepo::get_by_id(conn, project_id)?
            .ok_or_else(|| StorageError::NotFound(project_id.to_string()))?;

        if project.deleted {
            return Err(StorageError::ConstraintViolation(
                "project is already deleted".to_string(),
            ));
        }

        let now = chrono::Utc::now().to_rfc3339();

        // tombstone
        ctx.create_tombstone(conn, "project", project_id)?;

        // commit
        let commit_id =
            ctx.commit_object_change(conn, "projects", project_id, "change", "project")?;

        let object_clock = bump_clock(&project.object_clock);

        conn.inner().execute(
            "UPDATE projects SET deleted = 1, object_clock = ?2,
             head_commit_id = ?3, updated_at = ?4, updated_by_device_id = ?5
             WHERE project_id = ?1",
            params![project_id, object_clock, commit_id, now, ctx.device_id],
        )?;

        Ok(())
    }

    // -----------------------------------------------------------------------
    // ENCRYPTION HELPERS
    // -----------------------------------------------------------------------

    fn encrypt_metadata(
        conn: &VaultConnection,
        id: &str,
        field: &str,
        plaintext: &[u8],
    ) -> StorageResult<Vec<u8>> {
        let subkey = conn
            .keyring()
            .map(|kr| kr.metadata_subkey.clone())
            .unwrap_or_default();
        encrypt_field(conn.keyring(), &subkey, plaintext, "project", id, field)
            .map_err(StorageError::Crypto)
    }

    fn decrypt_metadata(
        conn: &VaultConnection,
        id: &str,
        field: &str,
        ciphertext: &[u8],
    ) -> StorageResult<Vec<u8>> {
        let subkey = conn
            .keyring()
            .map(|kr| kr.metadata_subkey.clone())
            .unwrap_or_default();
        decrypt_field(conn.keyring(), &subkey, ciphertext, "project", id, field)
            .map_err(StorageError::Crypto)
    }
}

/// 简单时钟递增：counter + 1。
fn bump_clock(clock: &str) -> String {
    let counter: u64 = serde_json::from_str::<serde_json::Value>(clock)
        .ok()
        .and_then(|v| v.get("counter")?.as_u64())
        .unwrap_or(0);
    format!(r#"{{"counter":{}}}"#, counter + 1)
}

// ---------------------------------------------------------------------------
// 测试
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use crate::init::{initialize_vault, VaultInitParams};

    fn setup() -> (VaultConnection, CommitContext) {
        let conn = VaultConnection::open_in_memory().unwrap();
        let params = VaultInitParams::default();
        initialize_vault(&conn, &params).unwrap();
        let ctx = CommitContext::new("test-device".to_string());
        (conn, ctx)
    }

    #[test]
    fn test_create_project() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "My Project", None, None).unwrap();

        assert_eq!(project.title_ct, b"My Project");
        assert!(!project.project_id.is_empty());
        assert!(!project.head_commit_id.is_empty());
        assert!(!project.favorite);
        assert!(!project.deleted);

        // 验证 commit 已生成
        let commit_count: i32 = conn
            .inner()
            .query_row(
                "SELECT COUNT(*) FROM commits WHERE commit_id = ?1",
                params![project.head_commit_id],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(commit_count, 1);

        let integrity_tag: Vec<u8> = conn
            .inner()
            .query_row(
                "SELECT integrity_tag FROM commits WHERE commit_id = ?1",
                params![project.head_commit_id],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(integrity_tag.len(), 32);
        assert_ne!(integrity_tag, vec![0]);
    }

    #[test]
    fn test_get_by_id() {
        let (conn, ctx) = setup();
        let created = ProjectRepo::create(&conn, &ctx, "Find Me", None, None).unwrap();
        let found = ProjectRepo::get_by_id(&conn, &created.project_id)
            .unwrap()
            .unwrap();

        assert_eq!(found.project_id, created.project_id);
        assert_eq!(found.title_ct, b"Find Me");
    }

    #[test]
    fn test_get_nonexistent() {
        let (conn, _ctx) = setup();
        let result = ProjectRepo::get_by_id(&conn, "nonexistent").unwrap();
        assert!(result.is_none());
    }

    #[test]
    fn test_list_all() {
        let (conn, ctx) = setup();
        ProjectRepo::create(&conn, &ctx, "Project A", None, None).unwrap();
        ProjectRepo::create(&conn, &ctx, "Project B", None, None).unwrap();

        let all = ProjectRepo::list_all(&conn).unwrap();
        assert_eq!(all.len(), 2);
    }

    #[test]
    fn test_list_excludes_deleted() {
        let (conn, ctx) = setup();
        let p = ProjectRepo::create(&conn, &ctx, "To Delete", None, None).unwrap();
        ProjectRepo::soft_delete(&conn, &ctx, &p.project_id).unwrap();

        let all = ProjectRepo::list_all(&conn).unwrap();
        assert!(all.is_empty());

        let deleted = ProjectRepo::list_deleted(&conn).unwrap();
        assert_eq!(deleted.len(), 1);
    }

    #[test]
    fn test_update_project() {
        let (conn, ctx) = setup();
        let mut project = ProjectRepo::create(&conn, &ctx, "Original", None, None).unwrap();

        project.title_ct = b"Updated".to_vec();
        project.favorite = true;
        let updated = ProjectRepo::update(&conn, &ctx, &project).unwrap();

        assert_eq!(updated.title_ct, b"Updated");
        assert!(updated.favorite);

        // head_commit_id 应该变化
        assert_ne!(updated.head_commit_id, project.head_commit_id);
    }

    #[test]
    fn test_soft_delete() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "To Delete", None, None).unwrap();

        ProjectRepo::soft_delete(&conn, &ctx, &project.project_id).unwrap();

        let deleted = ProjectRepo::get_by_id(&conn, &project.project_id)
            .unwrap()
            .unwrap();
        assert!(deleted.deleted);

        // tombstone 已生成
        let tombstone_count: i32 = conn
            .inner()
            .query_row(
                "SELECT COUNT(*) FROM tombstones WHERE target_object_type = 'project' AND target_object_id = ?1",
                params![project.project_id],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(tombstone_count, 1);
    }

    #[test]
    fn test_double_delete_rejected() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "Delete Once", None, None).unwrap();

        ProjectRepo::soft_delete(&conn, &ctx, &project.project_id).unwrap();
        let result = ProjectRepo::soft_delete(&conn, &ctx, &project.project_id);
        assert!(result.is_err());
    }

    #[test]
    fn test_update_generates_commit_with_parent() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "Chain Test", None, None).unwrap();
        let first_commit = project.head_commit_id.clone();

        let mut project = project;
        project.title_ct = b"Chain Test v2".to_vec();
        let updated = ProjectRepo::update(&conn, &ctx, &project).unwrap();

        // 新的 commit 应该指向旧的 commit 作为 parent
        let parent: String = conn
            .inner()
            .query_row(
                "SELECT parent_commit_id FROM commit_parents WHERE commit_id = ?1",
                params![updated.head_commit_id],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(parent, first_commit);
    }

    #[test]
    fn test_device_head_updated_after_mutation() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "Head Test", None, None).unwrap();

        let head: String = conn
            .inner()
            .query_row(
                "SELECT head_commit_id FROM device_heads WHERE device_id = ?1",
                params![ctx.device_id],
                |row| row.get(0),
            )
            .unwrap();

        assert_eq!(head, project.head_commit_id);
    }

    #[test]
    fn test_create_with_group() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "Work Stuff", Some("work"), None).unwrap();

        assert_eq!(project.group_id.as_deref(), Some("work"));

        let by_group = ProjectRepo::list_by_group(&conn, "work").unwrap();
        assert_eq!(by_group.len(), 1);
        assert_eq!(by_group[0].project_id, project.project_id);
    }

    #[test]
    fn test_encrypted_project_tamper_is_rejected() {
        use mdbx_crypto::keyring::Keyring;

        let mut conn = VaultConnection::open_in_memory().unwrap();
        let params = VaultInitParams::default();
        initialize_vault(&conn, &params).unwrap();
        let vault_key = mdbx_crypto::aead::generate_key().unwrap();
        let keyring = Keyring::from_vault_key(&vault_key, b"project-tamper-test").unwrap();
        conn.attach_keyring(keyring);

        let ctx = CommitContext::new("test-device".to_string());
        let project = ProjectRepo::create(&conn, &ctx, "Encrypted Project", None, None).unwrap();

        conn.inner()
            .execute(
                "UPDATE projects SET title_ct = ?1 WHERE project_id = ?2",
                params![b"not-valid-ciphertext".as_slice(), project.project_id],
            )
            .unwrap();

        assert!(ProjectRepo::get_by_id(&conn, &project.project_id).is_err());
    }

    #[test]
    fn test_bump_clock() {
        let clock1 = bump_clock(r#"{"counter":5}"#);
        assert_eq!(clock1, r#"{"counter":6}"#);

        let clock2 = bump_clock(&clock1);
        assert_eq!(clock2, r#"{"counter":7}"#);
    }
}
