use rusqlite::params;
use rusqlite::OptionalExtension;

use crate::connection::VaultConnection;
use crate::error::{StorageError, StorageResult};
use crate::sync_state::EntryRow;

pub struct ObjectVersionRepo;

impl ObjectVersionRepo {
    pub fn record_entry_current(
        conn: &VaultConnection,
        commit_id: &str,
        entry_id: &str,
    ) -> StorageResult<()> {
        let row = Self::current_entry_row(conn, entry_id)?;
        Self::record_entry_row(conn, commit_id, &row)
    }

    pub fn record_entry_row(
        conn: &VaultConnection,
        commit_id: &str,
        row: &EntryRow,
    ) -> StorageResult<()> {
        let snapshot_ct =
            serde_json::to_vec(row).map_err(|e| StorageError::SchemaCreation(e.to_string()))?;
        let now = chrono::Utc::now().to_rfc3339();

        conn.inner().execute(
            "INSERT OR REPLACE INTO object_versions
                (object_type, object_id, commit_id, snapshot_ct, created_at)
             VALUES ('entry', ?1, ?2, ?3, ?4)",
            params![row.entry_id, commit_id, snapshot_ct, now],
        )?;
        Ok(())
    }

    pub fn get_entry(
        conn: &VaultConnection,
        entry_id: &str,
        commit_id: &str,
    ) -> StorageResult<Option<EntryRow>> {
        let snapshot: Option<Vec<u8>> = conn
            .inner()
            .query_row(
                "SELECT snapshot_ct FROM object_versions
                 WHERE object_type = 'entry' AND object_id = ?1 AND commit_id = ?2",
                params![entry_id, commit_id],
                |row| row.get(0),
            )
            .optional()?;

        snapshot
            .map(|bytes| {
                serde_json::from_slice(&bytes)
                    .map_err(|e| StorageError::SchemaCreation(e.to_string()))
            })
            .transpose()
    }

    pub fn current_entry_row(conn: &VaultConnection, entry_id: &str) -> StorageResult<EntryRow> {
        conn.inner()
            .query_row(
                "SELECT entry_id, project_id, entry_type, title_ct, payload_ct,
                        payload_schema_version, tiga_mode_override, object_clock,
                        head_commit_id, deleted, created_at, updated_at,
                        created_by_device_id, updated_by_device_id
                 FROM entries WHERE entry_id = ?1",
                params![entry_id],
                |row| {
                    Ok(EntryRow {
                        entry_id: row.get(0)?,
                        project_id: row.get(1)?,
                        entry_type: row.get(2)?,
                        title_ct: row.get(3)?,
                        payload_ct: row.get(4)?,
                        payload_schema_version: row.get::<_, i64>(5)? as u32,
                        tiga_mode_override: row.get(6)?,
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
            .optional()?
            .ok_or_else(|| StorageError::NotFound(entry_id.to_string()))
    }
}
