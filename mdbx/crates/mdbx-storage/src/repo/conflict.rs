use rusqlite::params;
use rusqlite::OptionalExtension;
use uuid::Uuid;

use mdbx_core::model::{Conflict, ConflictObjectType, ConflictResolution};

use crate::connection::VaultConnection;
use crate::error::{StorageError, StorageResult};
use crate::repo::commit_ctx::CommitContext;
use crate::repo::entry::EntryRepo;
use crate::repo::object_version::ObjectVersionRepo;
use crate::sync_state::EntryRow;

/// 冲突记录的持久化仓库。
///
/// 冲突由 ConflictDetector 检测后写入此表，
/// 后续供 UI 层查询并交由用户手动解决。
pub struct ConflictRepo;

impl ConflictRepo {
    /// 记录一个新的冲突。
    pub fn create(
        conn: &VaultConnection,
        ctx: &CommitContext,
        object_type: ConflictObjectType,
        object_id: &str,
        base_commit_id: &str,
        local_commit_id: &str,
        incoming_commit_id: &str,
        conflicting_fields: &[String],
    ) -> StorageResult<Conflict> {
        let now = chrono::Utc::now().to_rfc3339();
        let conflict_id = Uuid::new_v4().to_string();
        let fields_json = serde_json::to_string(conflicting_fields)
            .map_err(|e| StorageError::SchemaCreation(e.to_string()))?;

        conn.inner().execute(
            "INSERT INTO conflicts (conflict_id, object_type, object_id,
             base_commit_id, local_commit_id, incoming_commit_id,
             conflicting_fields, resolution, created_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, 'unresolved', ?8)",
            params![
                conflict_id,
                object_type.to_string(),
                object_id,
                base_commit_id,
                local_commit_id,
                incoming_commit_id,
                fields_json,
                now,
            ],
        )?;

        // 也创建一个 commit 记录此冲突（无 parent，冲突是新事件）
        ctx.create_commit(conn, "change", "multi", &[conflict_id.clone()], &[])?;

        Ok(Conflict {
            conflict_id,
            object_type,
            object_id: object_id.to_string(),
            base_commit_id: base_commit_id.to_string(),
            local_commit_id: local_commit_id.to_string(),
            incoming_commit_id: incoming_commit_id.to_string(),
            conflicting_fields: conflicting_fields.to_vec(),
            resolution: ConflictResolution::Unresolved,
            created_at: now,
            resolved_at: None,
        })
    }

    /// 按 ID 查询冲突。
    pub fn get_by_id(conn: &VaultConnection, conflict_id: &str) -> StorageResult<Option<Conflict>> {
        conn.inner()
            .query_row(
                "SELECT conflict_id, object_type, object_id,
                        base_commit_id, local_commit_id, incoming_commit_id,
                        conflicting_fields, resolution, created_at, resolved_at
                 FROM conflicts WHERE conflict_id = ?1",
                params![conflict_id],
                |row| {
                    Ok(Conflict {
                        conflict_id: row.get(0)?,
                        object_type: {
                            let s: String = row.get(1)?;
                            s.parse().unwrap_or(ConflictObjectType::Entry)
                        },
                        object_id: row.get(2)?,
                        base_commit_id: row.get(3)?,
                        local_commit_id: row.get(4)?,
                        incoming_commit_id: row.get(5)?,
                        conflicting_fields: {
                            let s: String = row.get(6)?;
                            serde_json::from_str(&s).unwrap_or_default()
                        },
                        resolution: {
                            let s: String = row.get(7)?;
                            s.parse().unwrap_or(ConflictResolution::Unresolved)
                        },
                        created_at: row.get(8)?,
                        resolved_at: row.get(9)?,
                    })
                },
            )
            .optional()
            .map_err(StorageError::Database)
    }

    /// 列出所有未解决的冲突。
    pub fn list_unresolved(conn: &VaultConnection) -> StorageResult<Vec<Conflict>> {
        ConflictRepo::list_where(conn, "resolution = 'unresolved'", [])
    }

    /// 列出指定对象的所有冲突。
    pub fn list_by_object(
        conn: &VaultConnection,
        object_type: ConflictObjectType,
        object_id: &str,
    ) -> StorageResult<Vec<Conflict>> {
        ConflictRepo::list_where(
            conn,
            "object_type = ?1 AND object_id = ?2",
            params![object_type.to_string(), object_id],
        )
    }

    /// 解决冲突：更新 resolution 和 resolved_at。
    pub fn resolve(
        conn: &VaultConnection,
        conflict_id: &str,
        resolution: ConflictResolution,
    ) -> StorageResult<()> {
        let now = chrono::Utc::now().to_rfc3339();
        let affected = conn.inner().execute(
            "UPDATE conflicts SET resolution = ?2, resolved_at = ?3
             WHERE conflict_id = ?1",
            params![conflict_id, resolution.to_string(), now],
        )?;

        if affected == 0 {
            return Err(StorageError::NotFound(conflict_id.to_string()));
        }
        Ok(())
    }

    /// Resolve an entry conflict and write the chosen result back into history.
    ///
    /// This is the storage-core operation Android should call eventually:
    /// resolving a conflict is itself a tracked mutation, not just a metadata
    /// flag update on the conflict row.
    pub fn resolve_entry(
        conn: &VaultConnection,
        ctx: &CommitContext,
        conflict_id: &str,
        resolution: ConflictResolution,
    ) -> StorageResult<Conflict> {
        let conflict = Self::get_by_id(conn, conflict_id)?
            .ok_or_else(|| StorageError::NotFound(conflict_id.to_string()))?;

        if conflict.object_type != ConflictObjectType::Entry {
            return Err(StorageError::ConstraintViolation(
                "only entry conflicts can be resolved through resolve_entry".to_string(),
            ));
        }
        if conflict.resolution != ConflictResolution::Unresolved {
            return Err(StorageError::ConstraintViolation(format!(
                "conflict {} is already resolved",
                conflict_id
            )));
        }

        match resolution {
            ConflictResolution::LocalWins => {
                Self::write_local_wins_resolution(conn, ctx, &conflict)?;
            }
            ConflictResolution::IncomingWins => {
                Self::write_incoming_wins_resolution(conn, ctx, &conflict)?;
            }
            ConflictResolution::Custom => {
                return Err(StorageError::ConstraintViolation(
                    "custom conflict resolution requires an explicit merged payload".to_string(),
                ));
            }
            ConflictResolution::Unresolved => {
                return Err(StorageError::ConstraintViolation(
                    "cannot resolve a conflict as unresolved".to_string(),
                ));
            }
        }

        Self::resolve(conn, conflict_id, resolution)?;
        Self::get_by_id(conn, conflict_id)?
            .ok_or_else(|| StorageError::NotFound(conflict_id.to_string()))
    }

    /// Resolve an entry conflict with a caller-provided merged JSON payload.
    ///
    /// The current local entry keeps its structural fields (project, type,
    /// title, Tiga override, deleted state), while `merged_payload` replaces
    /// the encrypted record payload under a new merge commit.
    pub fn resolve_entry_custom_payload(
        conn: &VaultConnection,
        ctx: &CommitContext,
        conflict_id: &str,
        merged_payload: &serde_json::Value,
    ) -> StorageResult<Conflict> {
        let conflict = Self::get_by_id(conn, conflict_id)?
            .ok_or_else(|| StorageError::NotFound(conflict_id.to_string()))?;

        if conflict.object_type != ConflictObjectType::Entry {
            return Err(StorageError::ConstraintViolation(
                "only entry conflicts can be resolved through resolve_entry_custom_payload"
                    .to_string(),
            ));
        }
        if conflict.resolution != ConflictResolution::Unresolved {
            return Err(StorageError::ConstraintViolation(format!(
                "conflict {} is already resolved",
                conflict_id
            )));
        }

        Self::write_custom_payload_resolution(conn, ctx, &conflict, merged_payload)?;
        Self::resolve(conn, conflict_id, ConflictResolution::Custom)?;
        Self::get_by_id(conn, conflict_id)?
            .ok_or_else(|| StorageError::NotFound(conflict_id.to_string()))
    }

    /// 检查指定对象是否存在未解决的冲突。
    pub fn has_unresolved_conflict(
        conn: &VaultConnection,
        object_type: ConflictObjectType,
        object_id: &str,
    ) -> StorageResult<bool> {
        let count: i32 = conn
            .inner()
            .query_row(
                "SELECT COUNT(*) FROM conflicts
                 WHERE object_type = ?1 AND object_id = ?2 AND resolution = 'unresolved'",
                params![object_type.to_string(), object_id],
                |row| row.get(0),
            )
            .map_err(StorageError::Database)?;
        Ok(count > 0)
    }

    fn list_where(
        conn: &VaultConnection,
        where_clause: &str,
        params: impl rusqlite::Params,
    ) -> StorageResult<Vec<Conflict>> {
        let sql = format!(
            "SELECT conflict_id, object_type, object_id,
                    base_commit_id, local_commit_id, incoming_commit_id,
                    conflicting_fields, resolution, created_at, resolved_at
             FROM conflicts WHERE {} ORDER BY created_at DESC",
            where_clause
        );

        let mut stmt = conn.inner().prepare(&sql)?;
        let rows = stmt.query_map(params, |row| {
            Ok(Conflict {
                conflict_id: row.get(0)?,
                object_type: {
                    let s: String = row.get(1)?;
                    s.parse().unwrap_or(ConflictObjectType::Entry)
                },
                object_id: row.get(2)?,
                base_commit_id: row.get(3)?,
                local_commit_id: row.get(4)?,
                incoming_commit_id: row.get(5)?,
                conflicting_fields: {
                    let s: String = row.get(6)?;
                    serde_json::from_str(&s).unwrap_or_default()
                },
                resolution: {
                    let s: String = row.get(7)?;
                    s.parse().unwrap_or(ConflictResolution::Unresolved)
                },
                created_at: row.get(8)?,
                resolved_at: row.get(9)?,
            })
        })?;

        let mut conflicts = Vec::new();
        for row in rows {
            conflicts.push(row?);
        }
        Ok(conflicts)
    }

    fn write_local_wins_resolution(
        conn: &VaultConnection,
        ctx: &CommitContext,
        conflict: &Conflict,
    ) -> StorageResult<()> {
        let current = ObjectVersionRepo::current_entry_row(conn, &conflict.object_id)?;
        let commit_id =
            Self::create_resolution_commit(conn, ctx, conflict, &current.head_commit_id)?;
        let now = chrono::Utc::now().to_rfc3339();
        conn.inner().execute(
            "UPDATE entries SET object_clock = ?2, head_commit_id = ?3,
             updated_at = ?4, updated_by_device_id = ?5
             WHERE entry_id = ?1",
            params![
                conflict.object_id,
                bump_object_clock(&current.object_clock),
                commit_id,
                now,
                ctx.device_id,
            ],
        )?;
        ObjectVersionRepo::record_entry_current(conn, &commit_id, &conflict.object_id)?;
        Ok(())
    }

    fn write_incoming_wins_resolution(
        conn: &VaultConnection,
        ctx: &CommitContext,
        conflict: &Conflict,
    ) -> StorageResult<()> {
        let current = ObjectVersionRepo::current_entry_row(conn, &conflict.object_id)?;
        let incoming =
            ObjectVersionRepo::get_entry(conn, &conflict.object_id, &conflict.incoming_commit_id)?
                .ok_or_else(|| {
                    StorageError::NotFound(format!(
                        "incoming entry snapshot {}@{}",
                        conflict.object_id, conflict.incoming_commit_id
                    ))
                })?;
        let commit_id =
            Self::create_resolution_commit(conn, ctx, conflict, &current.head_commit_id)?;
        Self::apply_entry_row_for_resolution(
            conn,
            ctx,
            &incoming,
            &commit_id,
            &bump_object_clock(&current.object_clock),
        )?;
        ObjectVersionRepo::record_entry_current(conn, &commit_id, &conflict.object_id)?;
        Ok(())
    }

    fn write_custom_payload_resolution(
        conn: &VaultConnection,
        ctx: &CommitContext,
        conflict: &Conflict,
        merged_payload: &serde_json::Value,
    ) -> StorageResult<()> {
        let current = ObjectVersionRepo::current_entry_row(conn, &conflict.object_id)?;
        if current.deleted {
            return Err(StorageError::ConstraintViolation(
                "custom payload resolution cannot revive a deleted entry".to_string(),
            ));
        }
        let commit_id =
            Self::create_resolution_commit(conn, ctx, conflict, &current.head_commit_id)?;
        let payload_plain = serde_json::to_vec(merged_payload)
            .map_err(|e| StorageError::SchemaCreation(e.to_string()))?;
        let payload_ct =
            EntryRepo::encrypt_payload_blob(conn, &conflict.object_id, &payload_plain)?;
        let now = chrono::Utc::now().to_rfc3339();
        conn.inner().execute(
            "UPDATE entries SET payload_ct = ?2, object_clock = ?3,
             head_commit_id = ?4, deleted = 0, updated_at = ?5,
             updated_by_device_id = ?6
             WHERE entry_id = ?1",
            params![
                conflict.object_id,
                payload_ct,
                bump_object_clock(&current.object_clock),
                commit_id,
                now,
                ctx.device_id,
            ],
        )?;
        ObjectVersionRepo::record_entry_current(conn, &commit_id, &conflict.object_id)?;
        Ok(())
    }

    fn create_resolution_commit(
        conn: &VaultConnection,
        ctx: &CommitContext,
        conflict: &Conflict,
        current_head_id: &str,
    ) -> StorageResult<String> {
        let mut parents = vec![current_head_id.to_string()];
        if conflict.incoming_commit_id != current_head_id {
            parents.push(conflict.incoming_commit_id.clone());
        }
        ctx.create_commit(
            conn,
            "merge",
            "entry",
            std::slice::from_ref(&conflict.object_id),
            &parents,
        )
    }

    fn apply_entry_row_for_resolution(
        conn: &VaultConnection,
        ctx: &CommitContext,
        row: &EntryRow,
        commit_id: &str,
        object_clock: &str,
    ) -> StorageResult<()> {
        let now = chrono::Utc::now().to_rfc3339();
        conn.inner().execute(
            "UPDATE entries SET project_id = ?2, entry_type = ?3, title_ct = ?4,
             payload_ct = ?5, payload_schema_version = ?6, tiga_mode_override = ?7,
             object_clock = ?8, head_commit_id = ?9, deleted = ?10,
             updated_at = ?11, updated_by_device_id = ?12
             WHERE entry_id = ?1",
            params![
                row.entry_id,
                row.project_id,
                row.entry_type,
                row.title_ct,
                row.payload_ct,
                row.payload_schema_version as i64,
                row.tiga_mode_override,
                object_clock,
                commit_id,
                row.deleted as i32,
                now,
                ctx.device_id,
            ],
        )?;
        Ok(())
    }
}

fn bump_object_clock(clock: &str) -> String {
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
    use crate::repo::entry::EntryRepo;
    use crate::repo::project::ProjectRepo;

    fn setup() -> (VaultConnection, CommitContext) {
        let conn = VaultConnection::open_in_memory().unwrap();
        let params = VaultInitParams::default();
        initialize_vault(&conn, &params).unwrap();
        let ctx = CommitContext::new("test-device".to_string());
        (conn, ctx)
    }

    #[test]
    fn test_create_conflict() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "P", None, None).unwrap();
        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project.project_id,
            mdbx_core::model::EntryType::Login,
            Some("E"),
            &serde_json::json!({"user":"a"}),
        )
        .unwrap();

        let conflict = ConflictRepo::create(
            &conn,
            &ctx,
            ConflictObjectType::Entry,
            &entry.entry_id,
            "base-commit-1",
            "local-commit-1",
            "incoming-commit-1",
            &["pass".to_string(), "user".to_string()],
        )
        .unwrap();

        assert!(!conflict.conflict_id.is_empty());
        assert_eq!(conflict.object_type, ConflictObjectType::Entry);
        assert_eq!(conflict.object_id, entry.entry_id);
        assert_eq!(conflict.conflicting_fields.len(), 2);
        assert_eq!(conflict.resolution, ConflictResolution::Unresolved);
        assert!(conflict.resolved_at.is_none());
    }

    #[test]
    fn test_get_by_id() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "P", None, None).unwrap();

        let created = ConflictRepo::create(
            &conn,
            &ctx,
            ConflictObjectType::Project,
            &project.project_id,
            "base",
            "local",
            "incoming",
            &["title_ct".to_string()],
        )
        .unwrap();

        let found = ConflictRepo::get_by_id(&conn, &created.conflict_id)
            .unwrap()
            .unwrap();
        assert_eq!(found.conflict_id, created.conflict_id);
        assert_eq!(found.conflicting_fields, vec!["title_ct"]);
    }

    #[test]
    fn test_get_nonexistent() {
        let (conn, _ctx) = setup();
        assert!(ConflictRepo::get_by_id(&conn, "nonexistent")
            .unwrap()
            .is_none());
    }

    #[test]
    fn test_list_unresolved() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "P", None, None).unwrap();

        ConflictRepo::create(
            &conn,
            &ctx,
            ConflictObjectType::Project,
            &project.project_id,
            "b1",
            "l1",
            "i1",
            &["title_ct".to_string()],
        )
        .unwrap();

        ConflictRepo::create(
            &conn,
            &ctx,
            ConflictObjectType::Project,
            &project.project_id,
            "b2",
            "l2",
            "i2",
            &["icon_ref".to_string()],
        )
        .unwrap();

        let unresolved = ConflictRepo::list_unresolved(&conn).unwrap();
        assert_eq!(unresolved.len(), 2);

        // resolve one
        ConflictRepo::resolve(
            &conn,
            &unresolved[1].conflict_id,
            ConflictResolution::LocalWins,
        )
        .unwrap();

        let still_unresolved = ConflictRepo::list_unresolved(&conn).unwrap();
        assert_eq!(still_unresolved.len(), 1);
    }

    #[test]
    fn test_list_by_object() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "P", None, None).unwrap();
        let e1 = EntryRepo::create(
            &conn,
            &ctx,
            &project.project_id,
            mdbx_core::model::EntryType::Login,
            Some("E1"),
            &serde_json::json!({"a":1}),
        )
        .unwrap();
        let e2 = EntryRepo::create(
            &conn,
            &ctx,
            &project.project_id,
            mdbx_core::model::EntryType::Note,
            Some("E2"),
            &serde_json::json!({"b":2}),
        )
        .unwrap();

        ConflictRepo::create(
            &conn,
            &ctx,
            ConflictObjectType::Entry,
            &e1.entry_id,
            "b1",
            "l1",
            "i1",
            &["x".to_string()],
        )
        .unwrap();
        ConflictRepo::create(
            &conn,
            &ctx,
            ConflictObjectType::Entry,
            &e2.entry_id,
            "b2",
            "l2",
            "i2",
            &["y".to_string()],
        )
        .unwrap();
        ConflictRepo::create(
            &conn,
            &ctx,
            ConflictObjectType::Entry,
            &e2.entry_id,
            "b3",
            "l3",
            "i3",
            &["z".to_string()],
        )
        .unwrap();

        assert_eq!(
            ConflictRepo::list_by_object(&conn, ConflictObjectType::Entry, &e1.entry_id)
                .unwrap()
                .len(),
            1
        );
        assert_eq!(
            ConflictRepo::list_by_object(&conn, ConflictObjectType::Entry, &e2.entry_id)
                .unwrap()
                .len(),
            2
        );
    }

    #[test]
    fn test_resolve() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "P", None, None).unwrap();

        let conflict = ConflictRepo::create(
            &conn,
            &ctx,
            ConflictObjectType::Project,
            &project.project_id,
            "base",
            "local",
            "incoming",
            &["title_ct".to_string()],
        )
        .unwrap();

        ConflictRepo::resolve(&conn, &conflict.conflict_id, ConflictResolution::LocalWins).unwrap();

        let resolved = ConflictRepo::get_by_id(&conn, &conflict.conflict_id)
            .unwrap()
            .unwrap();
        assert_eq!(resolved.resolution, ConflictResolution::LocalWins);
        assert!(resolved.resolved_at.is_some());
    }

    #[test]
    fn test_resolve_nonexistent() {
        let (conn, _ctx) = setup();
        assert!(ConflictRepo::resolve(&conn, "nonexistent", ConflictResolution::Custom).is_err());
    }

    #[test]
    fn test_has_unresolved_conflict() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "P", None, None).unwrap();
        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project.project_id,
            mdbx_core::model::EntryType::Login,
            Some("E"),
            &serde_json::json!({"c":3}),
        )
        .unwrap();

        assert!(!ConflictRepo::has_unresolved_conflict(
            &conn,
            ConflictObjectType::Entry,
            &entry.entry_id
        )
        .unwrap());

        ConflictRepo::create(
            &conn,
            &ctx,
            ConflictObjectType::Entry,
            &entry.entry_id,
            "b",
            "l",
            "i",
            &["d".to_string()],
        )
        .unwrap();

        assert!(ConflictRepo::has_unresolved_conflict(
            &conn,
            ConflictObjectType::Entry,
            &entry.entry_id
        )
        .unwrap());
    }

    #[test]
    fn test_conflict_resolution_enum() {
        assert!(!ConflictResolution::Unresolved.is_resolved());
        assert!(ConflictResolution::LocalWins.is_resolved());
        assert!(ConflictResolution::IncomingWins.is_resolved());
        assert!(ConflictResolution::Custom.is_resolved());

        // Display + FromStr roundtrip
        for (res, s) in [
            (ConflictResolution::Unresolved, "unresolved"),
            (ConflictResolution::LocalWins, "local-wins"),
            (ConflictResolution::IncomingWins, "incoming-wins"),
            (ConflictResolution::Custom, "custom"),
        ] {
            assert_eq!(res.to_string(), s);
            assert_eq!(s.parse::<ConflictResolution>().unwrap(), res);
        }
    }

    #[test]
    fn test_conflict_object_type_roundtrip() {
        assert_eq!(ConflictObjectType::Project.to_string(), "project");
        assert_eq!(
            "entry".parse::<ConflictObjectType>().unwrap(),
            ConflictObjectType::Entry
        );
    }
}
