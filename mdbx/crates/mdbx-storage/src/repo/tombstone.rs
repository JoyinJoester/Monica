use rusqlite::params;
use rusqlite::OptionalExtension;

use mdbx_core::model::Tombstone;
use mdbx_core::model::TombstoneTargetType;

use crate::connection::VaultConnection;
use crate::error::{StorageError, StorageResult};

/// 墓碑查询仓库。
///
/// 墓碑记录由 CommitContext::create_tombstone 写入，
/// 本仓库只负责查询和批量操作。
pub struct TombstoneRepo;

impl TombstoneRepo {
    /// 按类型列出所有墓碑。
    pub fn list_by_type(
        conn: &VaultConnection,
        target_type: TombstoneTargetType,
    ) -> StorageResult<Vec<Tombstone>> {
        TombstoneRepo::list_where(
            conn,
            "target_object_type = ?1",
            params![target_type.to_string()],
        )
    }

    /// 列出所有墓碑。
    pub fn list_all(conn: &VaultConnection) -> StorageResult<Vec<Tombstone>> {
        TombstoneRepo::list_where(conn, "1=1", [])
    }

    /// 根据目标对象 ID 查找墓碑记录。
    pub fn find_by_target(
        conn: &VaultConnection,
        target_object_id: &str,
    ) -> StorageResult<Option<Tombstone>> {
        let mut stmt = conn.inner().prepare(
            "SELECT tombstone_id, target_object_type, target_object_id,
                    delete_clock, deleted_by_device_id, deleted_at, purge_eligible_at
             FROM tombstones WHERE target_object_id = ?1
             ORDER BY deleted_at DESC LIMIT 1",
        )?;

        stmt.query_row(params![target_object_id], |row| {
            Ok(Tombstone {
                tombstone_id: row.get(0)?,
                target_object_type: {
                    let s: String = row.get(1)?;
                    s.parse().unwrap_or(TombstoneTargetType::Project)
                },
                target_object_id: row.get(2)?,
                delete_clock: row.get(3)?,
                deleted_by_device_id: row.get(4)?,
                deleted_at: row.get(5)?,
                purge_eligible_at: row.get(6)?,
            })
        })
        .optional()
        .map_err(StorageError::Database)
    }

    /// 检查目标对象是否已有墓碑记录。
    pub fn is_tombstoned(conn: &VaultConnection, target_object_id: &str) -> StorageResult<bool> {
        let count: i32 = conn
            .inner()
            .query_row(
                "SELECT COUNT(*) FROM tombstones WHERE target_object_id = ?1",
                params![target_object_id],
                |row| row.get(0),
            )
            .map_err(StorageError::Database)?;
        Ok(count > 0)
    }

    /// 分类型统计墓碑数量。
    pub fn count_by_type(
        conn: &VaultConnection,
        target_type: TombstoneTargetType,
    ) -> StorageResult<u32> {
        let count: i32 = conn
            .inner()
            .query_row(
                "SELECT COUNT(*) FROM tombstones WHERE target_object_type = ?1",
                params![target_type.to_string()],
                |row| row.get(0),
            )
            .map_err(StorageError::Database)?;
        Ok(count as u32)
    }

    /// 清除指定墓碑（物理清理）。
    pub fn purge(conn: &VaultConnection, tombstone_id: &str) -> StorageResult<()> {
        let affected = conn.inner().execute(
            "DELETE FROM tombstones WHERE tombstone_id = ?1",
            params![tombstone_id],
        )?;
        if affected == 0 {
            return Err(StorageError::NotFound(tombstone_id.to_string()));
        }
        Ok(())
    }

    fn list_where(
        conn: &VaultConnection,
        where_clause: &str,
        params: impl rusqlite::Params,
    ) -> StorageResult<Vec<Tombstone>> {
        let sql = format!(
            "SELECT tombstone_id, target_object_type, target_object_id,
                    delete_clock, deleted_by_device_id, deleted_at, purge_eligible_at
             FROM tombstones WHERE {} ORDER BY deleted_at DESC",
            where_clause
        );

        let mut stmt = conn.inner().prepare(&sql)?;
        let rows = stmt.query_map(params, |row| {
            Ok(Tombstone {
                tombstone_id: row.get(0)?,
                target_object_type: {
                    let s: String = row.get(1)?;
                    s.parse().unwrap_or(TombstoneTargetType::Project)
                },
                target_object_id: row.get(2)?,
                delete_clock: row.get(3)?,
                deleted_by_device_id: row.get(4)?,
                deleted_at: row.get(5)?,
                purge_eligible_at: row.get(6)?,
            })
        })?;

        let mut tombstones = Vec::new();
        for row in rows {
            tombstones.push(row?);
        }
        Ok(tombstones)
    }
}

// ---------------------------------------------------------------------------
// 测试
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use crate::init::{initialize_vault, VaultInitParams};
    use crate::repo::attachment::AttachmentRepo;
    use crate::repo::commit_ctx::CommitContext;
    use crate::repo::entry::EntryRepo;
    use crate::repo::project::ProjectRepo;

    fn setup() -> (VaultConnection, CommitContext, String) {
        let conn = VaultConnection::open_in_memory().unwrap();
        let params = VaultInitParams::default();
        initialize_vault(&conn, &params).unwrap();
        let ctx = CommitContext::new("test-device".to_string());
        let project = ProjectRepo::create(&conn, &ctx, "Tombstone Project", None, None).unwrap();
        (conn, ctx, project.project_id)
    }

    #[test]
    fn test_tombstone_written_on_project_delete() {
        let (conn, ctx, project_id) = setup();
        ProjectRepo::soft_delete(&conn, &ctx, &project_id).unwrap();

        let ts = TombstoneRepo::find_by_target(&conn, &project_id)
            .unwrap()
            .unwrap();
        assert_eq!(ts.target_object_type, TombstoneTargetType::Project);
        assert_eq!(ts.target_object_id, project_id);
        assert_eq!(ts.deleted_by_device_id, "test-device");
        assert!(!ts.tombstone_id.is_empty());
    }

    #[test]
    fn test_tombstone_written_on_entry_delete() {
        let (conn, ctx, project_id) = setup();
        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            mdbx_core::model::EntryType::Note,
            Some("Test Note"),
            &serde_json::json!({"text":"hi"}),
        )
        .unwrap();
        EntryRepo::soft_delete(&conn, &ctx, &entry.entry_id).unwrap();

        let ts = TombstoneRepo::find_by_target(&conn, &entry.entry_id)
            .unwrap()
            .unwrap();
        assert_eq!(ts.target_object_type, TombstoneTargetType::Entry);
        assert_eq!(ts.target_object_id, entry.entry_id);
    }

    #[test]
    fn test_tombstone_written_on_attachment_delete() {
        let (conn, ctx, project_id) = setup();
        let att = AttachmentRepo::add(
            &conn,
            &ctx,
            &project_id,
            None,
            "file.txt",
            None,
            "hash",
            100,
        )
        .unwrap();
        AttachmentRepo::soft_delete(&conn, &ctx, &att.attachment_id).unwrap();

        let ts = TombstoneRepo::find_by_target(&conn, &att.attachment_id)
            .unwrap()
            .unwrap();
        assert_eq!(ts.target_object_type, TombstoneTargetType::Attachment);
    }

    #[test]
    fn test_list_by_type() {
        let (conn, ctx, project_id) = setup();

        // 创建并删除 2 个 project
        let p1 = ProjectRepo::create(&conn, &ctx, "P1", None, None).unwrap();
        let p2 = ProjectRepo::create(&conn, &ctx, "P2", None, None).unwrap();
        ProjectRepo::soft_delete(&conn, &ctx, &p1.project_id).unwrap();
        ProjectRepo::soft_delete(&conn, &ctx, &p2.project_id).unwrap();

        // 再删一个 entry（不同类型）
        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            mdbx_core::model::EntryType::Note,
            None,
            &serde_json::json!({"text":"x"}),
        )
        .unwrap();
        EntryRepo::soft_delete(&conn, &ctx, &entry.entry_id).unwrap();

        let project_tombstones =
            TombstoneRepo::list_by_type(&conn, TombstoneTargetType::Project).unwrap();
        let entry_tombstones =
            TombstoneRepo::list_by_type(&conn, TombstoneTargetType::Entry).unwrap();

        assert_eq!(project_tombstones.len(), 2);
        assert_eq!(entry_tombstones.len(), 1);
    }

    #[test]
    fn test_list_all() {
        let (conn, ctx, project_id) = setup();
        ProjectRepo::soft_delete(&conn, &ctx, &project_id).unwrap();

        let all = TombstoneRepo::list_all(&conn).unwrap();
        assert_eq!(all.len(), 1);
    }

    #[test]
    fn test_is_tombstoned() {
        let (conn, ctx, project_id) = setup();

        assert!(!TombstoneRepo::is_tombstoned(&conn, &project_id).unwrap());
        ProjectRepo::soft_delete(&conn, &ctx, &project_id).unwrap();
        assert!(TombstoneRepo::is_tombstoned(&conn, &project_id).unwrap());
    }

    #[test]
    fn test_count_by_type() {
        let (conn, ctx, project_id) = setup();

        assert_eq!(
            TombstoneRepo::count_by_type(&conn, TombstoneTargetType::Project).unwrap(),
            0
        );

        ProjectRepo::soft_delete(&conn, &ctx, &project_id).unwrap();
        assert_eq!(
            TombstoneRepo::count_by_type(&conn, TombstoneTargetType::Project).unwrap(),
            1
        );
    }

    #[test]
    fn test_purge() {
        let (conn, ctx, project_id) = setup();
        ProjectRepo::soft_delete(&conn, &ctx, &project_id).unwrap();

        let ts = TombstoneRepo::find_by_target(&conn, &project_id)
            .unwrap()
            .unwrap();
        TombstoneRepo::purge(&conn, &ts.tombstone_id).unwrap();

        assert!(TombstoneRepo::find_by_target(&conn, &project_id)
            .unwrap()
            .is_none());
    }

    #[test]
    fn test_purge_nonexistent() {
        let (conn, _ctx, _project_id) = setup();
        assert!(TombstoneRepo::purge(&conn, "nonexistent").is_err());
    }

    #[test]
    fn test_find_nonexistent_returns_none() {
        let (conn, _ctx, _project_id) = setup();
        let result = TombstoneRepo::find_by_target(&conn, "nonexistent").unwrap();
        assert!(result.is_none());
    }

    #[test]
    fn test_create_on_tombstoned_project_blocked() {
        let (conn, ctx, _project_id) = setup();
        let p = ProjectRepo::create(&conn, &ctx, "ToDelete", None, None).unwrap();
        ProjectRepo::soft_delete(&conn, &ctx, &p.project_id).unwrap();

        // 尝试在已删除 project 下创建 entry 应被阻止
        let result = EntryRepo::create(
            &conn,
            &ctx,
            &p.project_id,
            mdbx_core::model::EntryType::Note,
            None,
            &serde_json::json!({"text":"no"}),
        );
        assert!(result.is_err());
    }
}
