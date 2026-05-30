use mdbx_sync::ObjectPayload;
use serde::{Deserialize, Serialize};

use crate::connection::VaultConnection;
use crate::error::{StorageError, StorageResult};

pub const SYNC_STATE_OBJECT_TYPE: &str = "mdbx-storage/state-v1";
pub const LEGACY_CLI_SYNC_STATE_OBJECT_TYPE: &str = "mdbx-cli/state-v1";
pub const SYNC_STATE_OBJECT_ID: &str = "state";
const SYNC_STATE_FORMAT: &str = "mdbx-storage-sync-state-v1";
const LEGACY_CLI_SYNC_STATE_FORMAT: &str = "mdbx-cli-sync-state-v1";

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct SyncStatePayload {
    pub format: String,
    pub projects: Vec<ProjectRow>,
    pub entries: Vec<EntryRow>,
    pub attachments: Vec<AttachmentRow>,
    pub attachment_chunks: Vec<AttachmentChunkRow>,
    pub branches: Vec<BranchRow>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct ProjectRow {
    pub project_id: String,
    pub title_ct: Vec<u8>,
    pub summary_ct: Option<Vec<u8>>,
    pub group_id: Option<String>,
    pub icon_ref: Option<String>,
    pub favorite: bool,
    pub archived: bool,
    pub deleted: bool,
    pub tiga_mode_override: Option<String>,
    pub object_clock: String,
    pub head_commit_id: String,
    pub attachment_count: u32,
    pub created_at: String,
    pub updated_at: String,
    pub created_by_device_id: String,
    pub updated_by_device_id: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct EntryRow {
    pub entry_id: String,
    pub project_id: String,
    pub entry_type: String,
    pub title_ct: Option<Vec<u8>>,
    pub payload_ct: Vec<u8>,
    pub payload_schema_version: u32,
    pub tiga_mode_override: Option<String>,
    pub object_clock: String,
    pub head_commit_id: String,
    pub deleted: bool,
    pub created_at: String,
    pub updated_at: String,
    pub created_by_device_id: String,
    pub updated_by_device_id: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct AttachmentRow {
    pub attachment_id: String,
    pub project_id: String,
    pub entry_id: Option<String>,
    pub file_name_ct: Vec<u8>,
    pub media_type_ct: Option<Vec<u8>>,
    pub storage_mode: String,
    pub content_hash: String,
    pub original_size: u64,
    pub stored_size: u64,
    pub chunk_count: u32,
    pub head_commit_id: String,
    pub deleted: bool,
    pub created_at: String,
    pub updated_at: String,
    pub created_by_device_id: String,
    pub updated_by_device_id: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct AttachmentChunkRow {
    pub attachment_id: String,
    pub chunk_index: u32,
    pub chunk_hash: String,
    pub chunk_ct: Option<Vec<u8>>,
    pub external_uri_ct: Option<Vec<u8>>,
    pub stored_size: u64,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct BranchRow {
    pub branch_id: String,
    pub branch_name: String,
    pub head_commit_id: String,
    pub created_at: String,
    pub updated_at: String,
}

pub fn collect_sync_state(conn: &VaultConnection) -> StorageResult<SyncStatePayload> {
    Ok(SyncStatePayload {
        format: SYNC_STATE_FORMAT.to_string(),
        projects: load_project_rows(conn)?,
        entries: load_entry_rows(conn)?,
        attachments: load_attachment_rows(conn)?,
        attachment_chunks: load_attachment_chunk_rows(conn)?,
        branches: load_branch_rows(conn)?,
    })
}

pub fn collect_sync_state_payload(conn: &VaultConnection) -> StorageResult<ObjectPayload> {
    let state = collect_sync_state(conn)?;
    let ciphertext =
        serde_json::to_vec(&state).map_err(|e| StorageError::SchemaCreation(e.to_string()))?;
    Ok(ObjectPayload {
        object_type: SYNC_STATE_OBJECT_TYPE.to_string(),
        object_id: SYNC_STATE_OBJECT_ID.to_string(),
        ciphertext,
        associated_data: SYNC_STATE_OBJECT_TYPE.as_bytes().to_vec(),
    })
}

pub fn decode_sync_state_payload(
    payload: &ObjectPayload,
) -> StorageResult<Option<SyncStatePayload>> {
    if payload.object_id != SYNC_STATE_OBJECT_ID {
        return Ok(None);
    }
    if payload.object_type != SYNC_STATE_OBJECT_TYPE
        && payload.object_type != LEGACY_CLI_SYNC_STATE_OBJECT_TYPE
    {
        return Ok(None);
    }

    let state: SyncStatePayload = serde_json::from_slice(&payload.ciphertext)
        .map_err(|e| StorageError::SchemaCreation(e.to_string()))?;
    if state.format != SYNC_STATE_FORMAT && state.format != LEGACY_CLI_SYNC_STATE_FORMAT {
        return Err(StorageError::Validation(format!(
            "unsupported sync state format: {}",
            state.format
        )));
    }
    Ok(Some(state))
}

fn load_project_rows(conn: &VaultConnection) -> StorageResult<Vec<ProjectRow>> {
    let mut stmt = conn.inner().prepare(
        "SELECT project_id, title_ct, summary_ct, group_id, icon_ref,
                favorite, archived, deleted, tiga_mode_override, object_clock,
                head_commit_id, attachment_count, created_at, updated_at,
                created_by_device_id, updated_by_device_id
         FROM projects
         ORDER BY updated_at ASC, project_id ASC",
    )?;
    let rows = stmt.query_map([], |row| {
        Ok(ProjectRow {
            project_id: row.get(0)?,
            title_ct: row.get(1)?,
            summary_ct: row.get(2)?,
            group_id: row.get(3)?,
            icon_ref: row.get(4)?,
            favorite: row.get::<_, i32>(5)? != 0,
            archived: row.get::<_, i32>(6)? != 0,
            deleted: row.get::<_, i32>(7)? != 0,
            tiga_mode_override: row.get(8)?,
            object_clock: row.get(9)?,
            head_commit_id: row.get(10)?,
            attachment_count: row.get::<_, i64>(11)? as u32,
            created_at: row.get(12)?,
            updated_at: row.get(13)?,
            created_by_device_id: row.get(14)?,
            updated_by_device_id: row.get(15)?,
        })
    })?;

    let mut out = Vec::new();
    for row in rows {
        out.push(row?);
    }
    Ok(out)
}

fn load_entry_rows(conn: &VaultConnection) -> StorageResult<Vec<EntryRow>> {
    let mut stmt = conn.inner().prepare(
        "SELECT entry_id, project_id, entry_type, title_ct, payload_ct,
                payload_schema_version, tiga_mode_override, object_clock,
                head_commit_id, deleted, created_at, updated_at,
                created_by_device_id, updated_by_device_id
         FROM entries
         ORDER BY updated_at ASC, entry_id ASC",
    )?;
    let rows = stmt.query_map([], |row| {
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
    })?;

    let mut out = Vec::new();
    for row in rows {
        out.push(row?);
    }
    Ok(out)
}

fn load_attachment_rows(conn: &VaultConnection) -> StorageResult<Vec<AttachmentRow>> {
    let mut stmt = conn.inner().prepare(
        "SELECT attachment_id, project_id, entry_id, file_name_ct,
                media_type_ct, storage_mode, content_hash,
                original_size, stored_size, chunk_count, head_commit_id,
                deleted, created_at, updated_at,
                created_by_device_id, updated_by_device_id
         FROM attachments
         ORDER BY updated_at ASC, attachment_id ASC",
    )?;
    let rows = stmt.query_map([], |row| {
        Ok(AttachmentRow {
            attachment_id: row.get(0)?,
            project_id: row.get(1)?,
            entry_id: row.get(2)?,
            file_name_ct: row.get(3)?,
            media_type_ct: row.get(4)?,
            storage_mode: row.get(5)?,
            content_hash: row.get(6)?,
            original_size: row.get::<_, i64>(7)? as u64,
            stored_size: row.get::<_, i64>(8)? as u64,
            chunk_count: row.get::<_, i64>(9)? as u32,
            head_commit_id: row.get(10)?,
            deleted: row.get::<_, i32>(11)? != 0,
            created_at: row.get(12)?,
            updated_at: row.get(13)?,
            created_by_device_id: row.get(14)?,
            updated_by_device_id: row.get(15)?,
        })
    })?;

    let mut out = Vec::new();
    for row in rows {
        out.push(row?);
    }
    Ok(out)
}

fn load_attachment_chunk_rows(conn: &VaultConnection) -> StorageResult<Vec<AttachmentChunkRow>> {
    let mut stmt = conn.inner().prepare(
        "SELECT attachment_id, chunk_index, chunk_hash, chunk_ct,
                external_uri_ct, stored_size, created_at
         FROM attachment_chunks
         ORDER BY attachment_id ASC, chunk_index ASC",
    )?;
    let rows = stmt.query_map([], |row| {
        Ok(AttachmentChunkRow {
            attachment_id: row.get(0)?,
            chunk_index: row.get::<_, i64>(1)? as u32,
            chunk_hash: row.get(2)?,
            chunk_ct: row.get(3)?,
            external_uri_ct: row.get(4)?,
            stored_size: row.get::<_, i64>(5)? as u64,
            created_at: row.get(6)?,
        })
    })?;

    let mut out = Vec::new();
    for row in rows {
        out.push(row?);
    }
    Ok(out)
}

fn load_branch_rows(conn: &VaultConnection) -> StorageResult<Vec<BranchRow>> {
    let mut stmt = conn.inner().prepare(
        "SELECT branch_id, branch_name, head_commit_id, created_at, updated_at
         FROM branches
         ORDER BY branch_name ASC, branch_id ASC",
    )?;
    let rows = stmt.query_map([], |row| {
        Ok(BranchRow {
            branch_id: row.get(0)?,
            branch_name: row.get(1)?,
            head_commit_id: row.get(2)?,
            created_at: row.get(3)?,
            updated_at: row.get(4)?,
        })
    })?;

    let mut out = Vec::new();
    for row in rows {
        out.push(row?);
    }
    Ok(out)
}
