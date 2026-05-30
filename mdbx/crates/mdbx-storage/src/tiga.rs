use rusqlite::params;
use rusqlite::OptionalExtension;

use mdbx_core::model::Entry;
use mdbx_core::model::Project;
use mdbx_core::tiga::TigaMode;

use crate::connection::VaultConnection;
use crate::error::{StorageError, StorageResult};
use crate::repo::commit_ctx::CommitContext;
use crate::repo::entry::EntryRepo;
use crate::repo::project::ProjectRepo;

/// Tiga 模式三级参数服务。
///
/// 优先级: entry override > project override > global default。
/// 更窄范围的覆盖优先于更宽范围的覆盖。
pub struct TigaService;

impl TigaService {
    // -----------------------------------------------------------------------
    // GLOBAL
    // -----------------------------------------------------------------------

    /// 读取全局默认 Tiga 模式。
    pub fn get_global_default(conn: &VaultConnection) -> StorageResult<TigaMode> {
        let s: String = conn
            .inner()
            .query_row("SELECT default_tiga_mode FROM vault_meta", [], |row| {
                row.get(0)
            })
            .map_err(StorageError::Database)?;

        s.parse()
            .map_err(|e| StorageError::SchemaCreation(format!("invalid global tiga_mode: {}", e)))
    }

    /// 更新全局默认 Tiga 模式。
    pub fn set_global_default(
        conn: &VaultConnection,
        ctx: &CommitContext,
        mode: TigaMode,
    ) -> StorageResult<()> {
        let now = chrono::Utc::now().to_rfc3339();
        ctx.create_commit(
            conn,
            "change",
            "vault-meta",
            &["vault-meta:tiga-default".to_string()],
            &current_device_head(conn, ctx)?
                .into_iter()
                .collect::<Vec<_>>(),
        )?;
        conn.inner()
            .execute(
                "UPDATE vault_meta SET default_tiga_mode = ?1, updated_at = ?2",
                params![mode.to_string(), now],
            )
            .map_err(StorageError::Database)?;
        Ok(())
    }

    // -----------------------------------------------------------------------
    // PROJECT
    // -----------------------------------------------------------------------

    /// 读取 project 级 Tiga 覆盖（如果设置）。
    pub fn get_project_override(
        conn: &VaultConnection,
        project_id: &str,
    ) -> StorageResult<Option<TigaMode>> {
        let project = ProjectRepo::get_by_id(conn, project_id)?
            .ok_or_else(|| StorageError::NotFound(project_id.to_string()))?;
        Ok(project.tiga_mode_override)
    }

    /// 设置 project 级 Tiga 覆盖。
    pub fn set_project_override(
        conn: &VaultConnection,
        ctx: &CommitContext,
        project_id: &str,
        mode: Option<TigaMode>,
    ) -> StorageResult<()> {
        let project = ProjectRepo::get_by_id(conn, project_id)?
            .ok_or_else(|| StorageError::NotFound(project_id.to_string()))?;
        let mode_str = mode.map(|m| m.to_string());
        let commit_id =
            ctx.commit_object_change(conn, "projects", project_id, "change", "project")?;
        let now = chrono::Utc::now().to_rfc3339();
        let object_clock = bump_clock(&project.object_clock);
        conn.inner().execute(
            "UPDATE projects SET tiga_mode_override = ?1, object_clock = ?2,
             head_commit_id = ?3, updated_at = ?4, updated_by_device_id = ?5
             WHERE project_id = ?6",
            params![
                mode_str,
                object_clock,
                commit_id,
                now,
                ctx.device_id,
                project_id
            ],
        )?;
        Ok(())
    }

    /// 清除 project 级覆盖（回退到全局默认）。
    pub fn clear_project_override(
        conn: &VaultConnection,
        ctx: &CommitContext,
        project_id: &str,
    ) -> StorageResult<()> {
        Self::set_project_override(conn, ctx, project_id, None)
    }

    // -----------------------------------------------------------------------
    // ENTRY
    // -----------------------------------------------------------------------

    /// 读取 entry 级 Tiga 覆盖（如果设置）。
    pub fn get_entry_override(
        conn: &VaultConnection,
        entry_id: &str,
    ) -> StorageResult<Option<TigaMode>> {
        let entry = EntryRepo::get_by_id(conn, entry_id)?
            .ok_or_else(|| StorageError::NotFound(entry_id.to_string()))?;
        Ok(entry.tiga_mode_override)
    }

    /// 设置 entry 级 Tiga 覆盖。
    pub fn set_entry_override(
        conn: &VaultConnection,
        ctx: &CommitContext,
        entry_id: &str,
        mode: Option<TigaMode>,
    ) -> StorageResult<()> {
        let entry = EntryRepo::get_by_id(conn, entry_id)?
            .ok_or_else(|| StorageError::NotFound(entry_id.to_string()))?;
        let mode_str = mode.map(|m| m.to_string());
        let commit_id = ctx.commit_object_change(conn, "entries", entry_id, "change", "entry")?;
        let now = chrono::Utc::now().to_rfc3339();
        let object_clock = bump_clock(&entry.object_clock);
        conn.inner().execute(
            "UPDATE entries SET tiga_mode_override = ?1, object_clock = ?2,
             head_commit_id = ?3, updated_at = ?4, updated_by_device_id = ?5
             WHERE entry_id = ?6",
            params![
                mode_str,
                object_clock,
                commit_id,
                now,
                ctx.device_id,
                entry_id
            ],
        )?;
        Ok(())
    }

    /// 清除 entry 级覆盖（回退到 project 或全局默认）。
    pub fn clear_entry_override(
        conn: &VaultConnection,
        ctx: &CommitContext,
        entry_id: &str,
    ) -> StorageResult<()> {
        Self::set_entry_override(conn, ctx, entry_id, None)
    }

    // -----------------------------------------------------------------------
    // RESOLVE — 核心优先级逻辑
    // -----------------------------------------------------------------------

    /// 解析 entry 的生效 Tiga 模式。
    ///
    /// 优先级: entry.tiga_mode_override > project.tiga_mode_override > global
    pub fn resolve_for_entry(conn: &VaultConnection, entry_id: &str) -> StorageResult<TigaMode> {
        let entry = EntryRepo::get_by_id(conn, entry_id)?
            .ok_or_else(|| StorageError::NotFound(entry_id.to_string()))?;
        Self::resolve_for_entry_obj(conn, &entry)
    }

    /// 给定已加载的 Entry 对象，解析生效模式。
    pub fn resolve_for_entry_obj(conn: &VaultConnection, entry: &Entry) -> StorageResult<TigaMode> {
        let project_override = Self::get_project_override(conn, &entry.project_id)?;
        let global = Self::get_global_default(conn)?;
        Ok(TigaMode::resolve(
            global,
            project_override,
            entry.tiga_mode_override,
        ))
    }

    /// 解析 project 的生效 Tiga 模式（不含 entry 覆盖）。
    ///
    /// 优先级: project.tiga_mode_override > global
    pub fn resolve_for_project(
        conn: &VaultConnection,
        project_id: &str,
    ) -> StorageResult<TigaMode> {
        let project = ProjectRepo::get_by_id(conn, project_id)?
            .ok_or_else(|| StorageError::NotFound(project_id.to_string()))?;
        Self::resolve_for_project_obj(conn, &project)
    }

    /// 给定已加载的 Project 对象，解析生效模式。
    pub fn resolve_for_project_obj(
        conn: &VaultConnection,
        project: &Project,
    ) -> StorageResult<TigaMode> {
        let global = Self::get_global_default(conn)?;
        Ok(TigaMode::resolve(global, project.tiga_mode_override, None))
    }
}

fn current_device_head(
    conn: &VaultConnection,
    ctx: &CommitContext,
) -> StorageResult<Option<String>> {
    conn.inner()
        .query_row(
            "SELECT head_commit_id FROM device_heads WHERE device_id = ?1",
            params![ctx.device_id],
            |row| row.get(0),
        )
        .optional()
        .map_err(StorageError::Database)
        .map(|r| r.flatten())
}

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
    use crate::repo::commit_ctx::CommitContext;
    use crate::repo::entry::EntryRepo;
    use crate::repo::project::ProjectRepo;

    fn setup() -> (VaultConnection, CommitContext, String) {
        let conn = VaultConnection::open_in_memory().unwrap();
        let params = VaultInitParams {
            default_tiga_mode: "multi".to_string(),
            ..Default::default()
        };
        initialize_vault(&conn, &params).unwrap();
        let ctx = CommitContext::new("test-device".to_string());
        let project = ProjectRepo::create(&conn, &ctx, "Test Project", None, None).unwrap();
        (conn, ctx, project.project_id)
    }

    fn login_payload() -> serde_json::Value {
        serde_json::json!({"username": "alice", "password": "s3cret"})
    }

    fn device_head(conn: &VaultConnection, ctx: &CommitContext) -> String {
        conn.inner()
            .query_row(
                "SELECT head_commit_id FROM device_heads WHERE device_id = ?1",
                params![ctx.device_id],
                |row| row.get(0),
            )
            .unwrap()
    }

    fn commit_parent(conn: &VaultConnection, commit_id: &str) -> String {
        conn.inner()
            .query_row(
                "SELECT parent_commit_id FROM commit_parents WHERE commit_id = ?1",
                params![commit_id],
                |row| row.get(0),
            )
            .unwrap()
    }

    // -----------------------------------------------------------------------
    // GLOBAL
    // -----------------------------------------------------------------------

    #[test]
    fn test_get_global_default() {
        let (conn, _ctx, _project_id) = setup();
        let mode = TigaService::get_global_default(&conn).unwrap();
        assert_eq!(mode, TigaMode::Multi);
    }

    #[test]
    fn test_set_global_default() {
        let (conn, ctx, _project_id) = setup();
        let first_head = device_head(&conn, &ctx);

        TigaService::set_global_default(&conn, &ctx, TigaMode::Power).unwrap();
        let mode = TigaService::get_global_default(&conn).unwrap();
        assert_eq!(mode, TigaMode::Power);
        let power_head = device_head(&conn, &ctx);
        assert_ne!(power_head, first_head);
        assert_eq!(commit_parent(&conn, &power_head), first_head);

        TigaService::set_global_default(&conn, &ctx, TigaMode::Sky).unwrap();
        let mode = TigaService::get_global_default(&conn).unwrap();
        assert_eq!(mode, TigaMode::Sky);
        let sky_head = device_head(&conn, &ctx);
        assert_ne!(sky_head, power_head);
        assert_eq!(commit_parent(&conn, &sky_head), power_head);
    }

    // -----------------------------------------------------------------------
    // PROJECT OVERRIDE
    // -----------------------------------------------------------------------

    #[test]
    fn test_project_override_default_none() {
        let (conn, _ctx, project_id) = setup();
        let ov = TigaService::get_project_override(&conn, &project_id).unwrap();
        assert!(ov.is_none());
    }

    #[test]
    fn test_set_and_get_project_override() {
        let (conn, ctx, project_id) = setup();
        let before = ProjectRepo::get_by_id(&conn, &project_id).unwrap().unwrap();

        TigaService::set_project_override(&conn, &ctx, &project_id, Some(TigaMode::Power)).unwrap();
        let ov = TigaService::get_project_override(&conn, &project_id).unwrap();
        assert_eq!(ov, Some(TigaMode::Power));
        let after = ProjectRepo::get_by_id(&conn, &project_id).unwrap().unwrap();
        assert_ne!(after.head_commit_id, before.head_commit_id);
        assert_eq!(
            commit_parent(&conn, &after.head_commit_id),
            before.head_commit_id
        );
    }

    #[test]
    fn test_clear_project_override() {
        let (conn, ctx, project_id) = setup();

        TigaService::set_project_override(&conn, &ctx, &project_id, Some(TigaMode::Sky)).unwrap();
        TigaService::clear_project_override(&conn, &ctx, &project_id).unwrap();

        let ov = TigaService::get_project_override(&conn, &project_id).unwrap();
        assert!(ov.is_none());
    }

    // -----------------------------------------------------------------------
    // ENTRY OVERRIDE
    // -----------------------------------------------------------------------

    #[test]
    fn test_entry_override_default_none() {
        let (conn, ctx, project_id) = setup();
        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            mdbx_core::model::EntryType::Login,
            Some("E1"),
            &login_payload(),
        )
        .unwrap();

        let ov = TigaService::get_entry_override(&conn, &entry.entry_id).unwrap();
        assert!(ov.is_none());
    }

    #[test]
    fn test_set_and_get_entry_override() {
        let (conn, ctx, project_id) = setup();
        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            mdbx_core::model::EntryType::Login,
            Some("E1"),
            &login_payload(),
        )
        .unwrap();
        let before_head = entry.head_commit_id.clone();

        TigaService::set_entry_override(&conn, &ctx, &entry.entry_id, Some(TigaMode::Power))
            .unwrap();
        let ov = TigaService::get_entry_override(&conn, &entry.entry_id).unwrap();
        assert_eq!(ov, Some(TigaMode::Power));
        let after = EntryRepo::get_by_id(&conn, &entry.entry_id)
            .unwrap()
            .unwrap();
        assert_ne!(after.head_commit_id, before_head);
        assert_eq!(commit_parent(&conn, &after.head_commit_id), before_head);
    }

    #[test]
    fn test_clear_entry_override() {
        let (conn, ctx, project_id) = setup();
        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            mdbx_core::model::EntryType::Login,
            Some("E1"),
            &login_payload(),
        )
        .unwrap();

        TigaService::set_entry_override(&conn, &ctx, &entry.entry_id, Some(TigaMode::Sky)).unwrap();
        TigaService::clear_entry_override(&conn, &ctx, &entry.entry_id).unwrap();

        let ov = TigaService::get_entry_override(&conn, &entry.entry_id).unwrap();
        assert!(ov.is_none());
    }

    // -----------------------------------------------------------------------
    // RESOLVE — 优先级
    // -----------------------------------------------------------------------

    #[test]
    fn test_resolve_global_only() {
        let (conn, _ctx, project_id) = setup();

        // 全局 = Multi, 无 project/entry override
        let effective = TigaService::resolve_for_project(&conn, &project_id).unwrap();
        assert_eq!(effective, TigaMode::Multi);
    }

    #[test]
    fn test_resolve_project_overrides_global() {
        let (conn, ctx, project_id) = setup();

        TigaService::set_project_override(&conn, &ctx, &project_id, Some(TigaMode::Power)).unwrap();

        let effective = TigaService::resolve_for_project(&conn, &project_id).unwrap();
        assert_eq!(effective, TigaMode::Power);
    }

    #[test]
    fn test_resolve_entry_wins_over_project_and_global() {
        let (conn, ctx, project_id) = setup();

        // 全局 = Multi
        TigaService::set_project_override(&conn, &ctx, &project_id, Some(TigaMode::Power)).unwrap();

        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            mdbx_core::model::EntryType::Login,
            Some("E1"),
            &login_payload(),
        )
        .unwrap();
        TigaService::set_entry_override(&conn, &ctx, &entry.entry_id, Some(TigaMode::Sky)).unwrap();

        // entry(Sky) > project(Power) > global(Multi) → Sky
        let effective = TigaService::resolve_for_entry(&conn, &entry.entry_id).unwrap();
        assert_eq!(effective, TigaMode::Sky);
    }

    #[test]
    fn test_resolve_entry_falls_back_to_project() {
        let (conn, ctx, project_id) = setup();

        TigaService::set_project_override(&conn, &ctx, &project_id, Some(TigaMode::Power)).unwrap();

        // entry 没有 override，应回退到 project
        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            mdbx_core::model::EntryType::Login,
            Some("E2"),
            &login_payload(),
        )
        .unwrap();

        let effective = TigaService::resolve_for_entry(&conn, &entry.entry_id).unwrap();
        assert_eq!(effective, TigaMode::Power);
    }

    #[test]
    fn test_resolve_project_falls_back_to_global() {
        let (conn, _ctx, project_id) = setup();

        // project 没有 override，回退到全局 Multi
        let effective = TigaService::resolve_for_project(&conn, &project_id).unwrap();
        assert_eq!(effective, TigaMode::Multi);
    }

    #[test]
    fn test_resolve_full_chain() {
        let (conn, ctx, project_id) = setup();

        // 创建两个 project，各自有 entry
        let p2 = ProjectRepo::create(&conn, &ctx, "Project 2", None, None).unwrap();

        TigaService::set_project_override(&conn, &ctx, &project_id, Some(TigaMode::Power)).unwrap();
        // p2 无 override，回退到全局 Multi

        let e1 = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            mdbx_core::model::EntryType::Login,
            Some("E1"),
            &login_payload(),
        )
        .unwrap();
        // e1 无 override → project = Power
        TigaService::set_entry_override(&conn, &ctx, &e1.entry_id, Some(TigaMode::Sky)).unwrap();
        // e1 override = Sky → wins

        let e2 = EntryRepo::create(
            &conn,
            &ctx,
            &p2.project_id,
            mdbx_core::model::EntryType::Note,
            Some("E2"),
            &serde_json::json!({"text":"hi"}),
        )
        .unwrap();
        // e2 无 override, p2 无 override → global Multi

        assert_eq!(
            TigaService::resolve_for_entry(&conn, &e1.entry_id).unwrap(),
            TigaMode::Sky
        );
        assert_eq!(
            TigaService::resolve_for_entry(&conn, &e2.entry_id).unwrap(),
            TigaMode::Multi
        );
    }

    // -----------------------------------------------------------------------
    // EDGE CASES
    // -----------------------------------------------------------------------

    #[test]
    fn test_resolve_nonexistent_entry() {
        let (conn, _ctx, _project_id) = setup();
        assert!(TigaService::resolve_for_entry(&conn, "nonexistent").is_err());
    }

    #[test]
    fn test_resolve_nonexistent_project() {
        let (conn, _ctx, _project_id) = setup();
        assert!(TigaService::resolve_for_project(&conn, "nonexistent").is_err());
    }

    #[test]
    fn test_vault_init_preserves_global_default() {
        let conn = VaultConnection::open_in_memory().unwrap();
        let params = VaultInitParams {
            default_tiga_mode: "power".to_string(),
            ..Default::default()
        };
        initialize_vault(&conn, &params).unwrap();

        let mode = TigaService::get_global_default(&conn).unwrap();
        assert_eq!(mode, TigaMode::Power);
    }

    #[test]
    fn test_null_override_cleared_in_db() {
        let (conn, ctx, project_id) = setup();

        TigaService::set_project_override(&conn, &ctx, &project_id, Some(TigaMode::Sky)).unwrap();
        TigaService::set_project_override(&conn, &ctx, &project_id, None).unwrap();

        // 验证 DB 中确实是 NULL
        let val: Option<String> = conn
            .inner()
            .query_row(
                "SELECT tiga_mode_override FROM projects WHERE project_id = ?1",
                params![project_id],
                |row| row.get(0),
            )
            .unwrap();
        assert!(val.is_none());
    }
}
