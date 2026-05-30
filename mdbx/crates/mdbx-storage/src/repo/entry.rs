use rusqlite::params;
use rusqlite::types::Type;
use rusqlite::OptionalExtension;
use uuid::Uuid;

use mdbx_core::model::{Entry, EntryType};

use crate::connection::VaultConnection;
use crate::crypto_layer::{decrypt_field, encrypt_field};
use crate::error::{StorageError, StorageResult};
use crate::repo::commit_ctx::CommitContext;
use crate::repo::object_version::ObjectVersionRepo;

/// Entry 的持久化仓库。
///
/// 每个 entry 必须归属于一个 project。
/// `_ct` 字段在写入时加密、读取时解密。
pub struct EntryRepo;

impl EntryRepo {
    // -----------------------------------------------------------------------
    // CREATE
    // -----------------------------------------------------------------------

    pub fn create(
        conn: &VaultConnection,
        ctx: &CommitContext,
        project_id: &str,
        entry_type: EntryType,
        title: Option<&str>,
        payload: &serde_json::Value,
    ) -> StorageResult<Entry> {
        let now = chrono::Utc::now().to_rfc3339();
        let entry_id = Uuid::new_v4().to_string();

        // 验证 project 存在且未删除
        let (exists, deleted): (bool, bool) = conn
            .inner()
            .query_row(
                "SELECT 1, deleted FROM projects WHERE project_id = ?1",
                params![project_id],
                |row| Ok((true, row.get::<_, i32>(1)? != 0)),
            )
            .optional()
            .map_err(StorageError::Database)?
            .unwrap_or((false, false));

        if !exists {
            return Err(StorageError::NotFound(format!(
                "project {} not found",
                project_id
            )));
        }
        if deleted {
            return Err(StorageError::ConstraintViolation(format!(
                "project {} is deleted",
                project_id
            )));
        }

        let commit_id = ctx.create_commit(conn, "change", "entry", &[entry_id.clone()], &[])?;

        let object_clock = format!(r#"{{"counter":1}}"#);
        let payload_blob =
            serde_json::to_vec(payload).map_err(|e| StorageError::SchemaCreation(e.to_string()))?;
        let payload_ct = Self::encrypt_record(conn, &entry_id, "payload", &payload_blob)?;
        let title_ct = title
            .map(|t| Self::encrypt_metadata(conn, &entry_id, "title", t.as_bytes()))
            .transpose()?;

        conn.inner().execute(
            "INSERT INTO entries (entry_id, project_id, entry_type, title_ct,
             payload_ct, payload_schema_version, tiga_mode_override, object_clock,
             head_commit_id, deleted, created_at, updated_at,
             created_by_device_id, updated_by_device_id)
             VALUES (?1, ?2, ?3, ?4, ?5, 1, NULL, ?6, ?7, 0, ?8, ?8, ?9, ?9)",
            params![
                entry_id,
                project_id,
                entry_type.to_string(),
                title_ct,
                payload_ct,
                object_clock,
                commit_id,
                now,
                ctx.device_id,
            ],
        )?;

        ObjectVersionRepo::record_entry_current(conn, &commit_id, &entry_id)?;

        EntryRepo::get_by_id(conn, &entry_id)?.ok_or_else(|| StorageError::NotFound(entry_id))
    }

    // -----------------------------------------------------------------------
    // READ
    // -----------------------------------------------------------------------

    pub fn get_by_id(conn: &VaultConnection, entry_id: &str) -> StorageResult<Option<Entry>> {
        conn.inner()
            .query_row(
                "SELECT entry_id, project_id, entry_type, title_ct, payload_ct,
                        payload_schema_version, tiga_mode_override, object_clock,
                        head_commit_id, deleted, created_at, updated_at,
                        created_by_device_id, updated_by_device_id
                 FROM entries WHERE entry_id = ?1",
                params![entry_id],
                |row| {
                    let eid: String = row.get(0)?;
                    let raw_title: Option<Vec<u8>> = row.get(3)?;
                    let raw_payload: Vec<u8> = row.get(4)?;
                    Ok(Entry {
                        entry_id: eid.clone(),
                        project_id: row.get(1)?,
                        entry_type: {
                            let s: String = row.get(2)?;
                            s.parse().unwrap_or(EntryType::Login)
                        },
                        title_ct: raw_title
                            .map(|t| {
                                Self::decrypt_metadata(conn, &eid, "title", &t).map_err(|e| {
                                    rusqlite::Error::FromSqlConversionFailure(
                                        3,
                                        Type::Blob,
                                        Box::new(e),
                                    )
                                })
                            })
                            .transpose()?,
                        payload_ct: Self::decrypt_record(conn, &eid, "payload", &raw_payload)
                            .map_err(|e| {
                                rusqlite::Error::FromSqlConversionFailure(
                                    4,
                                    Type::Blob,
                                    Box::new(e),
                                )
                            })?,
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
                },
            )
            .optional()
            .map_err(StorageError::Database)
    }

    pub fn list_by_project(conn: &VaultConnection, project_id: &str) -> StorageResult<Vec<Entry>> {
        EntryRepo::list_where(
            conn,
            "deleted = 0 AND project_id = ?1",
            rusqlite::params![project_id],
        )
    }

    pub fn list_by_type(
        conn: &VaultConnection,
        entry_type: EntryType,
    ) -> StorageResult<Vec<Entry>> {
        let type_str = entry_type.to_string();
        EntryRepo::list_where(
            conn,
            "deleted = 0 AND entry_type = ?1",
            rusqlite::params![type_str],
        )
    }

    pub fn list_deleted(conn: &VaultConnection) -> StorageResult<Vec<Entry>> {
        EntryRepo::list_where(conn, "deleted = 1", [])
    }

    fn list_where(
        conn: &VaultConnection,
        where_clause: &str,
        params: impl rusqlite::Params,
    ) -> StorageResult<Vec<Entry>> {
        let sql = format!(
            "SELECT entry_id, project_id, entry_type, title_ct, payload_ct,
                    payload_schema_version, tiga_mode_override, object_clock,
                    head_commit_id, deleted, created_at, updated_at,
                    created_by_device_id, updated_by_device_id
             FROM entries WHERE {} ORDER BY updated_at DESC",
            where_clause
        );

        let mut stmt = conn.inner().prepare(&sql)?;
        let rows = stmt.query_map(params, |row| {
            let eid: String = row.get(0)?;
            let raw_title: Option<Vec<u8>> = row.get(3)?;
            let raw_payload: Vec<u8> = row.get(4)?;
            Ok(Entry {
                entry_id: eid.clone(),
                project_id: row.get(1)?,
                entry_type: {
                    let s: String = row.get(2)?;
                    s.parse().unwrap_or(EntryType::Login)
                },
                title_ct: raw_title
                    .map(|t| {
                        Self::decrypt_metadata(conn, &eid, "title", &t).map_err(|e| {
                            rusqlite::Error::FromSqlConversionFailure(3, Type::Blob, Box::new(e))
                        })
                    })
                    .transpose()?,
                payload_ct: Self::decrypt_record(conn, &eid, "payload", &raw_payload).map_err(
                    |e| rusqlite::Error::FromSqlConversionFailure(4, Type::Blob, Box::new(e)),
                )?,
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

    // -----------------------------------------------------------------------
    // UPDATE
    // -----------------------------------------------------------------------

    pub fn update(
        conn: &VaultConnection,
        ctx: &CommitContext,
        entry: &Entry,
    ) -> StorageResult<Entry> {
        let now = chrono::Utc::now().to_rfc3339();

        let commit_id =
            ctx.commit_object_change(conn, "entries", &entry.entry_id, "change", "entry")?;

        let object_clock = bump_clock(&entry.object_clock);

        let title_ct = entry
            .title_ct
            .as_ref()
            .map(|t| Self::encrypt_metadata(conn, &entry.entry_id, "title", t))
            .transpose()?;
        let payload_ct = Self::encrypt_record(conn, &entry.entry_id, "payload", &entry.payload_ct)?;

        conn.inner().execute(
            "UPDATE entries SET
                title_ct = ?2, payload_ct = ?3, payload_schema_version = ?4,
                entry_type = ?5, tiga_mode_override = ?6, object_clock = ?7,
                head_commit_id = ?8, deleted = ?9,
                updated_at = ?10, updated_by_device_id = ?11
             WHERE entry_id = ?1",
            params![
                entry.entry_id,
                title_ct,
                payload_ct,
                entry.payload_schema_version as i32,
                entry.entry_type.to_string(),
                entry.tiga_mode_override.as_ref().map(|m| m.to_string()),
                object_clock,
                commit_id,
                entry.deleted as i32,
                now,
                ctx.device_id,
            ],
        )?;

        ObjectVersionRepo::record_entry_current(conn, &commit_id, &entry.entry_id)?;

        EntryRepo::get_by_id(conn, &entry.entry_id)?
            .ok_or_else(|| StorageError::NotFound(entry.entry_id.clone()))
    }

    // -----------------------------------------------------------------------
    // MOVE / COPY
    // -----------------------------------------------------------------------

    pub fn move_to_project(
        conn: &VaultConnection,
        ctx: &CommitContext,
        entry_id: &str,
        target_project_id: &str,
    ) -> StorageResult<Entry> {
        let entry = EntryRepo::get_by_id(conn, entry_id)?
            .ok_or_else(|| StorageError::NotFound(entry_id.to_string()))?;

        if entry.deleted {
            return Err(StorageError::ConstraintViolation(
                "entry is deleted".to_string(),
            ));
        }
        ensure_active_project(conn, target_project_id)?;

        let now = chrono::Utc::now().to_rfc3339();
        let commit_id = ctx.commit_object_change(conn, "entries", entry_id, "move", "entry")?;
        let object_clock = bump_clock(&entry.object_clock);

        conn.inner().execute(
            "UPDATE entries SET project_id = ?2, object_clock = ?3,
             head_commit_id = ?4, updated_at = ?5, updated_by_device_id = ?6
             WHERE entry_id = ?1",
            params![
                entry_id,
                target_project_id,
                object_clock,
                commit_id,
                now,
                ctx.device_id,
            ],
        )?;

        ObjectVersionRepo::record_entry_current(conn, &commit_id, entry_id)?;

        EntryRepo::get_by_id(conn, entry_id)?
            .ok_or_else(|| StorageError::NotFound(entry_id.to_string()))
    }

    pub fn copy_to_project(
        conn: &VaultConnection,
        ctx: &CommitContext,
        entry_id: &str,
        target_project_id: &str,
    ) -> StorageResult<Entry> {
        let source = EntryRepo::get_by_id(conn, entry_id)?
            .ok_or_else(|| StorageError::NotFound(entry_id.to_string()))?;

        if source.deleted {
            return Err(StorageError::ConstraintViolation(
                "entry is deleted".to_string(),
            ));
        }
        ensure_active_project(conn, target_project_id)?;

        let now = chrono::Utc::now().to_rfc3339();
        let new_entry_id = Uuid::new_v4().to_string();
        let commit_id = ctx.create_commit(
            conn,
            "copy",
            "entry",
            &[new_entry_id.clone()],
            std::slice::from_ref(&source.head_commit_id),
        )?;
        let title_ct = source
            .title_ct
            .as_ref()
            .map(|t| Self::encrypt_metadata(conn, &new_entry_id, "title", t))
            .transpose()?;
        let payload_ct = Self::encrypt_record(conn, &new_entry_id, "payload", &source.payload_ct)?;

        conn.inner().execute(
            "INSERT INTO entries (entry_id, project_id, entry_type, title_ct,
             payload_ct, payload_schema_version, tiga_mode_override, object_clock,
             head_commit_id, deleted, created_at, updated_at,
             created_by_device_id, updated_by_device_id)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, 0, ?10, ?10, ?11, ?11)",
            params![
                new_entry_id,
                target_project_id,
                source.entry_type.to_string(),
                title_ct,
                payload_ct,
                source.payload_schema_version as i32,
                source.tiga_mode_override.as_ref().map(|m| m.to_string()),
                r#"{"counter":1}"#,
                commit_id,
                now,
                ctx.device_id,
            ],
        )?;

        ObjectVersionRepo::record_entry_current(conn, &commit_id, &new_entry_id)?;

        EntryRepo::get_by_id(conn, &new_entry_id)?
            .ok_or_else(|| StorageError::NotFound(new_entry_id))
    }

    // -----------------------------------------------------------------------
    // SOFT DELETE
    // -----------------------------------------------------------------------

    pub fn soft_delete(
        conn: &VaultConnection,
        ctx: &CommitContext,
        entry_id: &str,
    ) -> StorageResult<()> {
        let entry = EntryRepo::get_by_id(conn, entry_id)?
            .ok_or_else(|| StorageError::NotFound(entry_id.to_string()))?;

        if entry.deleted {
            return Err(StorageError::ConstraintViolation(
                "entry is already deleted".to_string(),
            ));
        }

        let now = chrono::Utc::now().to_rfc3339();

        ctx.create_tombstone(conn, "entry", entry_id)?;

        let commit_id = ctx.commit_object_change(conn, "entries", entry_id, "change", "entry")?;

        let object_clock = bump_clock(&entry.object_clock);

        conn.inner().execute(
            "UPDATE entries SET deleted = 1, object_clock = ?2,
             head_commit_id = ?3, updated_at = ?4, updated_by_device_id = ?5
             WHERE entry_id = ?1",
            params![entry_id, object_clock, commit_id, now, ctx.device_id],
        )?;

        ObjectVersionRepo::record_entry_current(conn, &commit_id, entry_id)?;

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
        encrypt_field(conn.keyring(), &subkey, plaintext, "entry", id, field)
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
        decrypt_field(conn.keyring(), &subkey, ciphertext, "entry", id, field)
            .map_err(StorageError::Crypto)
    }

    fn encrypt_record(
        conn: &VaultConnection,
        id: &str,
        field: &str,
        plaintext: &[u8],
    ) -> StorageResult<Vec<u8>> {
        let subkey = conn
            .keyring()
            .map(|kr| kr.record_subkey.clone())
            .unwrap_or_default();
        encrypt_field(conn.keyring(), &subkey, plaintext, "entry", id, field)
            .map_err(StorageError::Crypto)
    }

    fn decrypt_record(
        conn: &VaultConnection,
        id: &str,
        field: &str,
        ciphertext: &[u8],
    ) -> StorageResult<Vec<u8>> {
        let subkey = conn
            .keyring()
            .map(|kr| kr.record_subkey.clone())
            .unwrap_or_default();
        decrypt_field(conn.keyring(), &subkey, ciphertext, "entry", id, field)
            .map_err(StorageError::Crypto)
    }

    pub(crate) fn encrypt_payload_blob(
        conn: &VaultConnection,
        entry_id: &str,
        plaintext: &[u8],
    ) -> StorageResult<Vec<u8>> {
        Self::encrypt_record(conn, entry_id, "payload", plaintext)
    }

    pub(crate) fn decrypt_payload_blob(
        conn: &VaultConnection,
        entry_id: &str,
        ciphertext: &[u8],
    ) -> StorageResult<Vec<u8>> {
        Self::decrypt_record(conn, entry_id, "payload", ciphertext)
    }
}

fn bump_clock(clock: &str) -> String {
    let counter: u64 = serde_json::from_str::<serde_json::Value>(clock)
        .ok()
        .and_then(|v| v.get("counter")?.as_u64())
        .unwrap_or(0);
    format!(r#"{{"counter":{}}}"#, counter + 1)
}

fn ensure_active_project(conn: &VaultConnection, project_id: &str) -> StorageResult<()> {
    let deleted: Option<bool> = conn
        .inner()
        .query_row(
            "SELECT deleted FROM projects WHERE project_id = ?1",
            params![project_id],
            |row| Ok(row.get::<_, i32>(0)? != 0),
        )
        .optional()
        .map_err(StorageError::Database)?;

    match deleted {
        Some(false) => Ok(()),
        Some(true) => Err(StorageError::ConstraintViolation(format!(
            "project {} is deleted",
            project_id
        ))),
        None => Err(StorageError::NotFound(format!(
            "project {} not found",
            project_id
        ))),
    }
}

// ---------------------------------------------------------------------------
// 测试
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use crate::init::{initialize_vault, VaultInitParams};
    use crate::repo::project::ProjectRepo;

    fn setup() -> (VaultConnection, CommitContext, String) {
        let conn = VaultConnection::open_in_memory().unwrap();
        let params = VaultInitParams::default();
        initialize_vault(&conn, &params).unwrap();
        let ctx = CommitContext::new("test-device".to_string());
        let project = ProjectRepo::create(&conn, &ctx, "Parent Project", None, None).unwrap();
        (conn, ctx, project.project_id)
    }

    fn login_payload() -> serde_json::Value {
        serde_json::json!({
            "username": "alice@example.com",
            "password": "s3cret!",
            "url": "https://example.com/login",
            "totp_seed": null
        })
    }

    // -----------------------------------------------------------------------
    // CREATE
    // -----------------------------------------------------------------------

    #[test]
    fn test_create_entry() {
        let (conn, ctx, project_id) = setup();
        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            EntryType::Login,
            Some("My Login"),
            &login_payload(),
        )
        .unwrap();

        assert!(!entry.entry_id.is_empty());
        assert_eq!(entry.project_id, project_id);
        assert_eq!(entry.entry_type, EntryType::Login);
        assert_eq!(entry.title_ct, Some(b"My Login".to_vec()));
        assert!(!entry.payload_ct.is_empty());
        assert!(!entry.head_commit_id.is_empty());
        assert!(!entry.deleted);
    }

    #[test]
    fn test_create_entry_nonexistent_project() {
        let (conn, ctx, _project_id) = setup();
        let result = EntryRepo::create(
            &conn,
            &ctx,
            "nonexistent",
            EntryType::Note,
            None,
            &serde_json::json!({"text": "hello"}),
        );
        assert!(result.is_err());
    }

    #[test]
    fn test_create_entry_on_deleted_project() {
        let (conn, ctx, project_id) = setup();
        ProjectRepo::soft_delete(&conn, &ctx, &project_id).unwrap();

        let result = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            EntryType::Note,
            None,
            &serde_json::json!({"text": "should fail"}),
        );
        assert!(result.is_err());
    }

    // -----------------------------------------------------------------------
    // READ
    // -----------------------------------------------------------------------

    #[test]
    fn test_get_by_id() {
        let (conn, ctx, project_id) = setup();
        let created = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            EntryType::Login,
            Some("Test"),
            &login_payload(),
        )
        .unwrap();

        let found = EntryRepo::get_by_id(&conn, &created.entry_id)
            .unwrap()
            .unwrap();

        assert_eq!(found.entry_id, created.entry_id);
        assert_eq!(found.payload_ct, created.payload_ct);
    }

    #[test]
    fn test_get_nonexistent() {
        let (conn, _ctx, _project_id) = setup();
        assert!(EntryRepo::get_by_id(&conn, "nonexistent")
            .unwrap()
            .is_none());
    }

    #[test]
    fn test_list_by_project() {
        let (conn, ctx, project_id) = setup();
        EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            EntryType::Login,
            Some("A"),
            &serde_json::json!({"user": "a"}),
        )
        .unwrap();
        EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            EntryType::Note,
            Some("B"),
            &serde_json::json!({"text": "b"}),
        )
        .unwrap();

        let entries = EntryRepo::list_by_project(&conn, &project_id).unwrap();
        assert_eq!(entries.len(), 2);
    }

    #[test]
    fn test_list_by_type() {
        let (conn, ctx, project_id) = setup();
        EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            EntryType::Login,
            Some("L1"),
            &login_payload(),
        )
        .unwrap();
        EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            EntryType::Note,
            Some("N1"),
            &serde_json::json!({"text": "note"}),
        )
        .unwrap();
        EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            EntryType::Login,
            Some("L2"),
            &login_payload(),
        )
        .unwrap();

        let logins = EntryRepo::list_by_type(&conn, EntryType::Login).unwrap();
        assert_eq!(logins.len(), 2);
        let notes = EntryRepo::list_by_type(&conn, EntryType::Note).unwrap();
        assert_eq!(notes.len(), 1);
    }

    #[test]
    fn test_list_excludes_deleted() {
        let (conn, ctx, project_id) = setup();
        let e = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            EntryType::Note,
            Some("Del"),
            &serde_json::json!({"text": "x"}),
        )
        .unwrap();
        EntryRepo::soft_delete(&conn, &ctx, &e.entry_id).unwrap();

        let active = EntryRepo::list_by_project(&conn, &project_id).unwrap();
        assert!(active.is_empty());

        let deleted = EntryRepo::list_deleted(&conn).unwrap();
        assert_eq!(deleted.len(), 1);
    }

    // -----------------------------------------------------------------------
    // UPDATE
    // -----------------------------------------------------------------------

    #[test]
    fn test_update_entry() {
        let (conn, ctx, project_id) = setup();
        let mut entry = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            EntryType::Login,
            Some("Original"),
            &login_payload(),
        )
        .unwrap();

        entry.title_ct = Some(b"Updated Title".to_vec());
        let new_payload = serde_json::json!({"username": "bob", "password": "newpass"});
        entry.payload_ct = serde_json::to_vec(&new_payload).unwrap();

        let updated = EntryRepo::update(&conn, &ctx, &entry).unwrap();

        assert_eq!(updated.title_ct, Some(b"Updated Title".to_vec()));
        assert_ne!(updated.head_commit_id, entry.head_commit_id);
    }

    // -----------------------------------------------------------------------
    // MOVE / COPY
    // -----------------------------------------------------------------------

    #[test]
    fn test_move_to_project_generates_commit_with_parent() {
        let (conn, ctx, project_id) = setup();
        let target = ProjectRepo::create(&conn, &ctx, "Target Project", None, None).unwrap();
        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            EntryType::Login,
            Some("Movable"),
            &login_payload(),
        )
        .unwrap();

        let moved =
            EntryRepo::move_to_project(&conn, &ctx, &entry.entry_id, &target.project_id).unwrap();

        assert_eq!(moved.entry_id, entry.entry_id);
        assert_eq!(moved.project_id, target.project_id);
        assert_eq!(moved.title_ct, entry.title_ct);
        assert_eq!(moved.payload_ct, entry.payload_ct);
        assert_ne!(moved.head_commit_id, entry.head_commit_id);

        let (commit_kind, parent): (String, String) = conn
            .inner()
            .query_row(
                "SELECT c.commit_kind, cp.parent_commit_id
                 FROM commits c
                 JOIN commit_parents cp ON cp.commit_id = c.commit_id
                 WHERE c.commit_id = ?1",
                params![moved.head_commit_id],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .unwrap();
        assert_eq!(commit_kind, "move");
        assert_eq!(parent, entry.head_commit_id);

        assert!(EntryRepo::list_by_project(&conn, &project_id)
            .unwrap()
            .is_empty());
        assert_eq!(
            EntryRepo::list_by_project(&conn, &target.project_id)
                .unwrap()
                .len(),
            1
        );
    }

    #[test]
    fn test_copy_to_project_generates_new_entry_with_source_parent() {
        let (conn, ctx, project_id) = setup();
        let target = ProjectRepo::create(&conn, &ctx, "Copy Target", None, None).unwrap();
        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            EntryType::Login,
            Some("Copyable"),
            &login_payload(),
        )
        .unwrap();

        let copied =
            EntryRepo::copy_to_project(&conn, &ctx, &entry.entry_id, &target.project_id).unwrap();

        assert_ne!(copied.entry_id, entry.entry_id);
        assert_eq!(copied.project_id, target.project_id);
        assert_eq!(copied.entry_type, entry.entry_type);
        assert_eq!(copied.title_ct, entry.title_ct);
        assert_eq!(copied.payload_ct, entry.payload_ct);
        assert!(!copied.deleted);

        let (commit_kind, parent): (String, String) = conn
            .inner()
            .query_row(
                "SELECT c.commit_kind, cp.parent_commit_id
                 FROM commits c
                 JOIN commit_parents cp ON cp.commit_id = c.commit_id
                 WHERE c.commit_id = ?1",
                params![copied.head_commit_id],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .unwrap();
        assert_eq!(commit_kind, "copy");
        assert_eq!(parent, entry.head_commit_id);

        assert_eq!(
            EntryRepo::list_by_project(&conn, &project_id)
                .unwrap()
                .len(),
            1
        );
        assert_eq!(
            EntryRepo::list_by_project(&conn, &target.project_id)
                .unwrap()
                .len(),
            1
        );
    }

    #[test]
    fn test_move_and_copy_reject_deleted_entry() {
        let (conn, ctx, project_id) = setup();
        let target = ProjectRepo::create(&conn, &ctx, "Target", None, None).unwrap();
        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            EntryType::Note,
            Some("Deleted"),
            &serde_json::json!({"text": "gone"}),
        )
        .unwrap();
        EntryRepo::soft_delete(&conn, &ctx, &entry.entry_id).unwrap();

        assert!(
            EntryRepo::move_to_project(&conn, &ctx, &entry.entry_id, &target.project_id).is_err()
        );
        assert!(
            EntryRepo::copy_to_project(&conn, &ctx, &entry.entry_id, &target.project_id).is_err()
        );
    }

    // -----------------------------------------------------------------------
    // SOFT DELETE
    // -----------------------------------------------------------------------

    #[test]
    fn test_soft_delete() {
        let (conn, ctx, project_id) = setup();
        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            EntryType::Note,
            Some("Gone"),
            &serde_json::json!({"text": "bye"}),
        )
        .unwrap();

        EntryRepo::soft_delete(&conn, &ctx, &entry.entry_id).unwrap();

        let deleted = EntryRepo::get_by_id(&conn, &entry.entry_id)
            .unwrap()
            .unwrap();
        assert!(deleted.deleted);

        // tombstone 已生成
        let count: i32 = conn.inner().query_row(
            "SELECT COUNT(*) FROM tombstones WHERE target_object_type = 'entry' AND target_object_id = ?1",
            params![entry.entry_id],
            |row| row.get(0),
        ).unwrap();
        assert_eq!(count, 1);
    }

    #[test]
    fn test_double_delete_rejected() {
        let (conn, ctx, project_id) = setup();
        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            EntryType::Note,
            Some("Once"),
            &serde_json::json!({"text": "hi"}),
        )
        .unwrap();

        EntryRepo::soft_delete(&conn, &ctx, &entry.entry_id).unwrap();
        assert!(EntryRepo::soft_delete(&conn, &ctx, &entry.entry_id).is_err());
    }

    // -----------------------------------------------------------------------
    // COMMIT INTEGRITY
    // -----------------------------------------------------------------------

    #[test]
    fn test_create_generates_commit() {
        let (conn, ctx, project_id) = setup();
        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            EntryType::Login,
            None,
            &login_payload(),
        )
        .unwrap();

        let commit_count: i32 = conn
            .inner()
            .query_row(
                "SELECT COUNT(*) FROM commits WHERE commit_id = ?1",
                params![entry.head_commit_id],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(commit_count, 1);
    }

    #[test]
    fn test_update_commit_has_parent() {
        let (conn, ctx, project_id) = setup();
        let mut entry = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            EntryType::Login,
            None,
            &login_payload(),
        )
        .unwrap();
        let first_commit = entry.head_commit_id.clone();

        entry.title_ct = Some(b"v2".to_vec());
        let updated = EntryRepo::update(&conn, &ctx, &entry).unwrap();

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
    fn test_entry_types() {
        let (conn, ctx, project_id) = setup();

        for etype in [
            EntryType::Login,
            EntryType::Note,
            EntryType::Card,
            EntryType::Identity,
            EntryType::Totp,
            EntryType::Passkey,
            EntryType::SshKey,
            EntryType::ApiToken,
            EntryType::DocumentRef,
        ] {
            let e = EntryRepo::create(
                &conn,
                &ctx,
                &project_id,
                etype.clone(),
                None,
                &serde_json::json!({"data": "test"}),
            )
            .unwrap();
            assert_eq!(e.entry_type, etype);
        }

        // 9 entries total
        assert_eq!(
            EntryRepo::list_by_project(&conn, &project_id)
                .unwrap()
                .len(),
            9
        );
    }

    // -----------------------------------------------------------------------
    // ENCRYPTION INTEGRATION
    // -----------------------------------------------------------------------

    #[test]
    fn test_entry_encrypted_with_keyring() {
        use crate::init::{initialize_vault, VaultInitParams};
        use crate::repo::commit_ctx::CommitContext;
        use mdbx_crypto::keyring::Keyring;

        let mut conn = VaultConnection::open_in_memory().unwrap();
        let params = VaultInitParams::default();
        initialize_vault(&conn, &params).unwrap();

        // attach a real keyring
        let vault_ctx = b"test-vault-context";
        let vault_key = mdbx_crypto::aead::generate_key().unwrap();
        let keyring = Keyring::from_vault_key(&vault_key, vault_ctx).unwrap();
        conn.attach_keyring(keyring);

        let ctx = CommitContext::new("test-device".to_string());
        let project = ProjectRepo::create(&conn, &ctx, "Parent", None, None).unwrap();

        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project.project_id,
            EntryType::Login,
            Some("My Login"),
            &serde_json::json!({"username": "alice", "password": "secret"}),
        )
        .unwrap();

        // 通过 API 读回应该是明文
        let found = EntryRepo::get_by_id(&conn, &entry.entry_id)
            .unwrap()
            .unwrap();
        assert_eq!(found.title_ct, Some(b"My Login".to_vec()));

        // 但数据库中的原始字节应该是密文（不同于明文）
        let raw_title: Vec<u8> = conn
            .inner()
            .query_row(
                "SELECT title_ct FROM entries WHERE entry_id = ?1",
                params![entry.entry_id],
                |row| row.get(0),
            )
            .unwrap();
        assert_ne!(raw_title, b"My Login");
        assert!(!raw_title.is_empty());

        let raw_payload: Vec<u8> = conn
            .inner()
            .query_row(
                "SELECT payload_ct FROM entries WHERE entry_id = ?1",
                params![entry.entry_id],
                |row| row.get(0),
            )
            .unwrap();
        let expected_plain =
            serde_json::to_vec(&serde_json::json!({"username": "alice", "password": "secret"}))
                .unwrap();
        assert_ne!(raw_payload, expected_plain);

        let changed_object_ids_ct: Vec<u8> = conn
            .inner()
            .query_row(
                "SELECT changed_object_ids_ct FROM commits WHERE commit_id = ?1",
                params![entry.head_commit_id],
                |row| row.get(0),
            )
            .unwrap();
        let changed_plain = serde_json::to_vec(&vec![entry.entry_id.clone()]).unwrap();
        assert_ne!(changed_object_ids_ct, changed_plain);

        let integrity_tag: Vec<u8> = conn
            .inner()
            .query_row(
                "SELECT integrity_tag FROM commits WHERE commit_id = ?1",
                params![entry.head_commit_id],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(integrity_tag.len(), 32);
        assert_ne!(integrity_tag, vec![0]);
    }

    #[test]
    fn test_encrypted_entry_tamper_is_rejected() {
        use mdbx_crypto::keyring::Keyring;

        let mut conn = VaultConnection::open_in_memory().unwrap();
        let params = VaultInitParams::default();
        initialize_vault(&conn, &params).unwrap();
        let vault_key = mdbx_crypto::aead::generate_key().unwrap();
        let keyring = Keyring::from_vault_key(&vault_key, b"entry-tamper-test").unwrap();
        conn.attach_keyring(keyring);

        let ctx = CommitContext::new("test-device".to_string());
        let project = ProjectRepo::create(&conn, &ctx, "Parent", None, None).unwrap();
        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project.project_id,
            EntryType::Login,
            Some("Tamper Me"),
            &login_payload(),
        )
        .unwrap();

        conn.inner()
            .execute(
                "UPDATE entries SET payload_ct = ?1 WHERE entry_id = ?2",
                params![b"not-valid-ciphertext".as_slice(), entry.entry_id],
            )
            .unwrap();

        assert!(EntryRepo::get_by_id(&conn, &entry.entry_id).is_err());
    }
}
