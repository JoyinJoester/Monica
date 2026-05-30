use rusqlite::params;
use rusqlite::OptionalExtension;
use std::collections::HashSet;

use mdbx_core::model::ConflictObjectType;
use mdbx_sync::{CommitBatch, ObjectPayload, SerializedCommit};

use crate::commit_integrity::{compute_commit_integrity_tag, CommitIntegrityInput};
use crate::conflict::ConflictDetector;
use crate::connection::VaultConnection;
use crate::error::{StorageError, StorageResult};
use crate::repo::{CommitContext, ConflictRepo, EntryRepo, ObjectVersionRepo};
use crate::sync_state::{
    decode_sync_state_payload, AttachmentChunkRow, AttachmentRow, BranchRow, EntryRow, ProjectRow,
    SyncStatePayload,
};

#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct ApplyBatchResult {
    pub applied_commits: u32,
    pub skipped_commits: u32,
    pub conflict_count: u32,
    pub missing_parent_count: u32,
}

pub struct SyncApplyRepo;

impl SyncApplyRepo {
    pub fn apply_batch(
        conn: &VaultConnection,
        ctx: &CommitContext,
        batch: &CommitBatch,
    ) -> StorageResult<ApplyBatchResult> {
        let mut result = ApplyBatchResult::default();

        for serialized in &batch.commits {
            match Self::apply_commit(conn, ctx, serialized)? {
                ApplyOutcome::Applied => result.applied_commits += 1,
                ApplyOutcome::Skipped => result.skipped_commits += 1,
                ApplyOutcome::Conflict => {
                    result.applied_commits += 1;
                    result.conflict_count += 1;
                }
                ApplyOutcome::MissingParent => result.missing_parent_count += 1,
            }
        }

        Ok(result)
    }

    fn apply_commit(
        conn: &VaultConnection,
        ctx: &CommitContext,
        serialized: &SerializedCommit,
    ) -> StorageResult<ApplyOutcome> {
        if Self::commit_exists(conn, &serialized.commit.commit_id)? {
            return Ok(ApplyOutcome::Skipped);
        }

        for parent in &serialized.parent_ids {
            if !Self::commit_exists(conn, parent)? {
                return Ok(ApplyOutcome::MissingParent);
            }
        }

        let local_head = Self::current_branch_head(conn, "main")?;
        let fast_forward = local_head
            .as_deref()
            .map(|head| serialized.parent_ids.iter().any(|parent| parent == head))
            .unwrap_or(true);

        conn.inner().execute_batch("BEGIN IMMEDIATE TRANSACTION;")?;
        let tx_result: StorageResult<ApplyOutcome> = (|| {
            Self::insert_commit(conn, serialized)?;
            if fast_forward {
                let payload_conflicts = Self::apply_fast_forward_payloads(conn, ctx, serialized)?;
                if payload_conflicts == 0 {
                    Self::advance_main_branch(conn, &serialized.commit.commit_id)?;
                }
                Self::sync_device_head(conn, serialized)?;
                Ok(if payload_conflicts == 0 {
                    ApplyOutcome::Applied
                } else {
                    ApplyOutcome::Conflict
                })
            } else {
                let payload_conflicts =
                    Self::apply_divergent_payloads(conn, ctx, serialized, local_head.as_deref())?;
                Self::sync_device_head(conn, serialized)?;
                Ok(if payload_conflicts == 0 {
                    ApplyOutcome::Applied
                } else {
                    ApplyOutcome::Conflict
                })
            }
        })();

        match tx_result {
            Ok(outcome) => {
                conn.inner().execute_batch("COMMIT;")?;
                Ok(outcome)
            }
            Err(err) => {
                let _ = conn.inner().execute_batch("ROLLBACK;");
                Err(err)
            }
        }
    }

    fn insert_commit(conn: &VaultConnection, serialized: &SerializedCommit) -> StorageResult<()> {
        let commit = &serialized.commit;
        let commit_integrity_input = CommitIntegrityInput {
            commit_id: &commit.commit_id,
            device_id: &commit.device_id,
            local_seq: commit.local_seq,
            commit_kind: &commit.commit_kind.to_string(),
            change_scope: &commit.change_scope.to_string(),
            changed_object_ids_ct: &commit.changed_object_ids_ct,
            vector_clock: &commit.vector_clock,
            message_ct: commit.message_ct.as_deref(),
            created_at: &commit.created_at,
            parents: &serialized.parent_ids,
        };
        let expected = compute_commit_integrity_tag(conn.keyring(), &commit_integrity_input)?;
        if expected != commit.integrity_tag {
            return Err(StorageError::Validation(format!(
                "incoming commit {} integrity mismatch",
                commit.commit_id
            )));
        }

        conn.inner().execute(
            "INSERT INTO commits (commit_id, device_id, local_seq, commit_kind, change_scope,
             changed_object_ids_ct, vector_clock, message_ct, created_at, integrity_tag)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)",
            params![
                commit.commit_id,
                commit.device_id,
                commit.local_seq as i64,
                commit.commit_kind.to_string(),
                commit.change_scope.to_string(),
                &commit.changed_object_ids_ct,
                commit.vector_clock,
                commit.message_ct.as_deref(),
                commit.created_at,
                &commit.integrity_tag,
            ],
        )?;

        for parent_id in &serialized.parent_ids {
            conn.inner().execute(
                "INSERT OR IGNORE INTO commit_parents (commit_id, parent_commit_id) VALUES (?1, ?2)",
                params![commit.commit_id, parent_id],
            )?;
        }

        for tombstone in &serialized.tombstones {
            conn.inner().execute(
                "INSERT OR REPLACE INTO tombstones (tombstone_id, target_object_type, target_object_id,
                 delete_clock, deleted_by_device_id, deleted_at, purge_eligible_at)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, NULL)",
                params![
                    tombstone.tombstone_id,
                    tombstone.target_object_type,
                    tombstone.target_object_id,
                    tombstone.delete_clock,
                    tombstone.deleted_by_device_id,
                    tombstone.deleted_at,
                ],
            )?;
        }

        Ok(())
    }

    fn apply_fast_forward_payloads(
        conn: &VaultConnection,
        ctx: &CommitContext,
        serialized: &SerializedCommit,
    ) -> StorageResult<u32> {
        let mut conflicts = 0;
        for payload in &serialized.object_payloads {
            if let Some(state) = decode_sync_state_payload(payload)? {
                conflicts +=
                    Self::apply_sync_state(conn, ctx, &serialized.commit.commit_id, &state)?;
            }
        }
        Ok(conflicts)
    }

    fn apply_divergent_payloads(
        conn: &VaultConnection,
        ctx: &CommitContext,
        serialized: &SerializedCommit,
        local_head: Option<&str>,
    ) -> StorageResult<u32> {
        let mut conflicts = 0;
        for payload in &serialized.object_payloads {
            if let Some(state) = decode_sync_state_payload(payload)? {
                conflicts +=
                    Self::apply_sync_state(conn, ctx, &serialized.commit.commit_id, &state)?;
            } else {
                conflicts +=
                    Self::record_payload_conflict(conn, ctx, serialized, payload, local_head)?;
            }
        }
        Ok(conflicts)
    }

    fn apply_sync_state(
        conn: &VaultConnection,
        ctx: &CommitContext,
        incoming_commit_id: &str,
        state: &SyncStatePayload,
    ) -> StorageResult<u32> {
        let mut conflicts = 0;
        conflicts += Self::apply_projects(conn, ctx, incoming_commit_id, &state.projects)?;
        conflicts += Self::apply_entries(conn, ctx, incoming_commit_id, &state.entries)?;
        let replace_attachment_chunks =
            Self::apply_attachments(conn, ctx, incoming_commit_id, &state.attachments)?;
        conflicts += replace_attachment_chunks.conflict_count;
        Self::apply_attachment_chunks(
            conn,
            &state.attachment_chunks,
            &replace_attachment_chunks.ids,
        )?;
        Self::apply_branches(conn, &state.branches)?;
        Ok(conflicts)
    }

    fn apply_projects(
        conn: &VaultConnection,
        ctx: &CommitContext,
        _incoming_commit_id: &str,
        projects: &[ProjectRow],
    ) -> StorageResult<u32> {
        let mut conflicts = 0;
        for row in projects {
            match Self::object_apply_decision(
                conn,
                "projects",
                "project_id",
                &row.project_id,
                &row.head_commit_id,
            )? {
                ObjectDecision::Insert => {
                    conn.inner().execute(
                        "INSERT INTO projects (project_id, title_ct, summary_ct, group_id, icon_ref,
                         favorite, archived, deleted, tiga_mode_override, object_clock,
                         head_commit_id, attachment_count, created_at, updated_at,
                         created_by_device_id, updated_by_device_id)
                         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16)",
                        params![
                            row.project_id,
                            row.title_ct,
                            row.summary_ct,
                            row.group_id,
                            row.icon_ref,
                            row.favorite as i32,
                            row.archived as i32,
                            row.deleted as i32,
                            row.tiga_mode_override,
                            row.object_clock,
                            row.head_commit_id,
                            row.attachment_count as i64,
                            row.created_at,
                            row.updated_at,
                            row.created_by_device_id,
                            row.updated_by_device_id,
                        ],
                    )?;
                }
                ObjectDecision::FastForward => {
                    conn.inner().execute(
                        "UPDATE projects SET title_ct = ?2, summary_ct = ?3, group_id = ?4,
                         icon_ref = ?5, favorite = ?6, archived = ?7, deleted = ?8,
                         tiga_mode_override = ?9, object_clock = ?10, head_commit_id = ?11,
                         attachment_count = ?12, created_at = ?13, updated_at = ?14,
                         created_by_device_id = ?15, updated_by_device_id = ?16
                         WHERE project_id = ?1",
                        params![
                            row.project_id,
                            row.title_ct,
                            row.summary_ct,
                            row.group_id,
                            row.icon_ref,
                            row.favorite as i32,
                            row.archived as i32,
                            row.deleted as i32,
                            row.tiga_mode_override,
                            row.object_clock,
                            row.head_commit_id,
                            row.attachment_count as i64,
                            row.created_at,
                            row.updated_at,
                            row.created_by_device_id,
                            row.updated_by_device_id,
                        ],
                    )?;
                }
                ObjectDecision::Conflict { local_head } => {
                    conflicts += Self::record_object_conflict(
                        conn,
                        ctx,
                        ConflictObjectType::Project,
                        &row.project_id,
                        &local_head,
                        &row.head_commit_id,
                    )?;
                }
                ObjectDecision::Skip => {}
            }
        }
        Ok(conflicts)
    }

    fn apply_entries(
        conn: &VaultConnection,
        ctx: &CommitContext,
        _incoming_commit_id: &str,
        entries: &[EntryRow],
    ) -> StorageResult<u32> {
        let mut conflicts = 0;
        for row in entries {
            if Self::commit_exists(conn, &row.head_commit_id)? {
                ObjectVersionRepo::record_entry_row(conn, &row.head_commit_id, row)?;
            }
            match Self::object_apply_decision(
                conn,
                "entries",
                "entry_id",
                &row.entry_id,
                &row.head_commit_id,
            )? {
                ObjectDecision::Insert => {
                    conn.inner().execute(
                        "INSERT INTO entries (entry_id, project_id, entry_type, title_ct,
                         payload_ct, payload_schema_version, tiga_mode_override, object_clock,
                         head_commit_id, deleted, created_at, updated_at,
                         created_by_device_id, updated_by_device_id)
                         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)",
                        params![
                            row.entry_id,
                            row.project_id,
                            row.entry_type,
                            row.title_ct,
                            row.payload_ct,
                            row.payload_schema_version as i64,
                            row.tiga_mode_override,
                            row.object_clock,
                            row.head_commit_id,
                            row.deleted as i32,
                            row.created_at,
                            row.updated_at,
                            row.created_by_device_id,
                            row.updated_by_device_id,
                        ],
                    )?;
                    ObjectVersionRepo::record_entry_row(conn, &row.head_commit_id, row)?;
                }
                ObjectDecision::FastForward => {
                    conn.inner().execute(
                        "UPDATE entries SET project_id = ?2, entry_type = ?3, title_ct = ?4,
                         payload_ct = ?5, payload_schema_version = ?6, tiga_mode_override = ?7,
                         object_clock = ?8, head_commit_id = ?9, deleted = ?10,
                         created_at = ?11, updated_at = ?12,
                         created_by_device_id = ?13, updated_by_device_id = ?14
                         WHERE entry_id = ?1",
                        params![
                            row.entry_id,
                            row.project_id,
                            row.entry_type,
                            row.title_ct,
                            row.payload_ct,
                            row.payload_schema_version as i64,
                            row.tiga_mode_override,
                            row.object_clock,
                            row.head_commit_id,
                            row.deleted as i32,
                            row.created_at,
                            row.updated_at,
                            row.created_by_device_id,
                            row.updated_by_device_id,
                        ],
                    )?;
                    ObjectVersionRepo::record_entry_row(conn, &row.head_commit_id, row)?;
                }
                ObjectDecision::Conflict { local_head } => {
                    conflicts += Self::merge_or_record_entry_conflict(conn, ctx, row, &local_head)?;
                }
                ObjectDecision::Skip => {}
            }
        }
        Ok(conflicts)
    }

    fn apply_attachments(
        conn: &VaultConnection,
        ctx: &CommitContext,
        _incoming_commit_id: &str,
        attachments: &[AttachmentRow],
    ) -> StorageResult<AttachmentApplyResult> {
        let mut result = AttachmentApplyResult::default();
        for row in attachments {
            match Self::object_apply_decision(
                conn,
                "attachments",
                "attachment_id",
                &row.attachment_id,
                &row.head_commit_id,
            )? {
                ObjectDecision::Insert => {
                    conn.inner().execute(
                        "INSERT INTO attachments (attachment_id, project_id, entry_id,
                         file_name_ct, media_type_ct, storage_mode, content_hash,
                         original_size, stored_size, chunk_count, head_commit_id,
                         deleted, created_at, updated_at, created_by_device_id, updated_by_device_id)
                         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16)",
                        params![
                            row.attachment_id,
                            row.project_id,
                            row.entry_id,
                            row.file_name_ct,
                            row.media_type_ct,
                            row.storage_mode,
                            row.content_hash,
                            row.original_size as i64,
                            row.stored_size as i64,
                            row.chunk_count as i64,
                            row.head_commit_id,
                            row.deleted as i32,
                            row.created_at,
                            row.updated_at,
                            row.created_by_device_id,
                            row.updated_by_device_id,
                        ],
                    )?;
                    result.ids.insert(row.attachment_id.clone());
                }
                ObjectDecision::FastForward => {
                    conn.inner().execute(
                        "UPDATE attachments SET project_id = ?2, entry_id = ?3,
                         file_name_ct = ?4, media_type_ct = ?5, storage_mode = ?6,
                         content_hash = ?7, original_size = ?8, stored_size = ?9,
                         chunk_count = ?10, head_commit_id = ?11, deleted = ?12,
                         created_at = ?13, updated_at = ?14,
                         created_by_device_id = ?15, updated_by_device_id = ?16
                         WHERE attachment_id = ?1",
                        params![
                            row.attachment_id,
                            row.project_id,
                            row.entry_id,
                            row.file_name_ct,
                            row.media_type_ct,
                            row.storage_mode,
                            row.content_hash,
                            row.original_size as i64,
                            row.stored_size as i64,
                            row.chunk_count as i64,
                            row.head_commit_id,
                            row.deleted as i32,
                            row.created_at,
                            row.updated_at,
                            row.created_by_device_id,
                            row.updated_by_device_id,
                        ],
                    )?;
                    conn.inner().execute(
                        "DELETE FROM attachment_chunks WHERE attachment_id = ?1",
                        params![row.attachment_id],
                    )?;
                    result.ids.insert(row.attachment_id.clone());
                }
                ObjectDecision::Conflict { local_head } => {
                    result.conflict_count += Self::record_object_conflict(
                        conn,
                        ctx,
                        ConflictObjectType::Attachment,
                        &row.attachment_id,
                        &local_head,
                        &row.head_commit_id,
                    )?;
                }
                ObjectDecision::Skip => {}
            }
        }
        Ok(result)
    }

    fn apply_attachment_chunks(
        conn: &VaultConnection,
        chunks: &[AttachmentChunkRow],
        replace_attachment_ids: &HashSet<String>,
    ) -> StorageResult<()> {
        for row in chunks {
            if !replace_attachment_ids.contains(&row.attachment_id) {
                continue;
            }
            conn.inner().execute(
                "INSERT OR REPLACE INTO attachment_chunks (attachment_id, chunk_index,
                 chunk_hash, chunk_ct, external_uri_ct, stored_size, created_at)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
                params![
                    row.attachment_id,
                    row.chunk_index as i64,
                    row.chunk_hash,
                    row.chunk_ct,
                    row.external_uri_ct,
                    row.stored_size as i64,
                    row.created_at,
                ],
            )?;
        }
        Ok(())
    }

    fn apply_branches(conn: &VaultConnection, branches: &[BranchRow]) -> StorageResult<()> {
        for row in branches {
            if !Self::commit_exists(conn, &row.head_commit_id)? {
                continue;
            }
            let local_head: Option<String> = conn
                .inner()
                .query_row(
                    "SELECT head_commit_id FROM branches WHERE branch_id = ?1",
                    params![row.branch_id],
                    |row| row.get(0),
                )
                .optional()?;

            let should_upsert = match local_head {
                None => true,
                Some(local_head) if local_head == row.head_commit_id => false,
                Some(local_head) => {
                    Self::is_ancestor_commit(conn, &local_head, &row.head_commit_id)?
                }
            };
            if should_upsert {
                conn.inner().execute(
                    "INSERT INTO branches (branch_id, branch_name, head_commit_id, created_at, updated_at)
                     VALUES (?1, ?2, ?3, ?4, ?5)
                     ON CONFLICT(branch_id) DO UPDATE SET
                        branch_name = excluded.branch_name,
                        head_commit_id = excluded.head_commit_id,
                        updated_at = excluded.updated_at",
                    params![
                        row.branch_id,
                        row.branch_name,
                        row.head_commit_id,
                        row.created_at,
                        row.updated_at,
                    ],
                )?;
            }
        }
        Ok(())
    }

    fn object_apply_decision(
        conn: &VaultConnection,
        table: &str,
        id_column: &str,
        object_id: &str,
        incoming_head: &str,
    ) -> StorageResult<ObjectDecision> {
        let sql = format!(
            "SELECT head_commit_id FROM {} WHERE {} = ?1",
            table, id_column
        );
        let local_head: Option<String> = conn
            .inner()
            .query_row(&sql, params![object_id], |row| row.get(0))
            .optional()?;

        let Some(local_head) = local_head else {
            return Ok(ObjectDecision::Insert);
        };
        if local_head == incoming_head {
            return Ok(ObjectDecision::Skip);
        }
        if Self::is_ancestor_commit(conn, &local_head, incoming_head)? {
            return Ok(ObjectDecision::FastForward);
        }
        if Self::is_ancestor_commit(conn, incoming_head, &local_head)? {
            return Ok(ObjectDecision::Skip);
        }
        Ok(ObjectDecision::Conflict { local_head })
    }

    fn is_ancestor_commit(
        conn: &VaultConnection,
        ancestor: &str,
        descendant: &str,
    ) -> StorageResult<bool> {
        if ancestor == descendant {
            return Ok(true);
        }
        let mut stack = vec![descendant.to_string()];
        let mut seen = HashSet::new();
        while let Some(commit_id) = stack.pop() {
            if !seen.insert(commit_id.clone()) {
                continue;
            }
            let parents = Self::parent_ids_for_commit(conn, &commit_id)?;
            for parent in parents {
                if parent == ancestor {
                    return Ok(true);
                }
                stack.push(parent);
            }
        }
        Ok(false)
    }

    fn parent_ids_for_commit(
        conn: &VaultConnection,
        commit_id: &str,
    ) -> StorageResult<Vec<String>> {
        let mut stmt = conn.inner().prepare(
            "SELECT parent_commit_id FROM commit_parents
             WHERE commit_id = ?1
             ORDER BY parent_commit_id",
        )?;
        let rows = stmt.query_map(params![commit_id], |row| row.get(0))?;
        let mut parents = Vec::new();
        for row in rows {
            parents.push(row?);
        }
        Ok(parents)
    }

    fn record_object_conflict(
        conn: &VaultConnection,
        ctx: &CommitContext,
        object_type: ConflictObjectType,
        object_id: &str,
        local_commit_id: &str,
        incoming_commit_id: &str,
    ) -> StorageResult<u32> {
        if ConflictRepo::has_unresolved_conflict(conn, object_type.clone(), object_id)? {
            return Ok(0);
        }
        let base_commit_id =
            Self::nearest_known_common_parent(conn, local_commit_id, incoming_commit_id)?
                .unwrap_or_else(|| "unknown".to_string());
        ConflictRepo::create(
            conn,
            ctx,
            object_type,
            object_id,
            &base_commit_id,
            local_commit_id,
            incoming_commit_id,
            &[String::from("<object>")],
        )?;
        Ok(1)
    }

    fn merge_or_record_entry_conflict(
        conn: &VaultConnection,
        ctx: &CommitContext,
        incoming: &EntryRow,
        local_commit_id: &str,
    ) -> StorageResult<u32> {
        let incoming_commit_id = &incoming.head_commit_id;
        let Some(base_commit_id) =
            Self::nearest_known_common_parent(conn, local_commit_id, incoming_commit_id)?
        else {
            return Self::record_entry_field_conflict(
                conn,
                ctx,
                &incoming.entry_id,
                "unknown",
                local_commit_id,
                incoming_commit_id,
                &[String::from("<base>")],
            );
        };

        let Some(base_row) =
            ObjectVersionRepo::get_entry(conn, &incoming.entry_id, &base_commit_id)?
        else {
            return Self::record_entry_field_conflict(
                conn,
                ctx,
                &incoming.entry_id,
                &base_commit_id,
                local_commit_id,
                incoming_commit_id,
                &[String::from("<base>")],
            );
        };

        let local_row =
            match ObjectVersionRepo::get_entry(conn, &incoming.entry_id, local_commit_id)? {
                Some(row) => row,
                None => ObjectVersionRepo::current_entry_row(conn, &incoming.entry_id)?,
            };

        let mut structural_conflicts = Vec::new();
        if local_row.deleted != incoming.deleted {
            structural_conflicts.push("deleted".to_string());
        }
        if merge_value(
            &base_row.project_id,
            &local_row.project_id,
            &incoming.project_id,
        )
        .is_none()
        {
            structural_conflicts.push("project_id".to_string());
        }
        if merge_value(
            &base_row.entry_type,
            &local_row.entry_type,
            &incoming.entry_type,
        )
        .is_none()
        {
            structural_conflicts.push("entry_type".to_string());
        }
        if merge_value(&base_row.title_ct, &local_row.title_ct, &incoming.title_ct).is_none() {
            structural_conflicts.push("title_ct".to_string());
        }
        if merge_value(
            &base_row.tiga_mode_override,
            &local_row.tiga_mode_override,
            &incoming.tiga_mode_override,
        )
        .is_none()
        {
            structural_conflicts.push("tiga_mode_override".to_string());
        }
        if !structural_conflicts.is_empty() {
            return Self::record_entry_field_conflict(
                conn,
                ctx,
                &incoming.entry_id,
                &base_commit_id,
                local_commit_id,
                incoming_commit_id,
                &structural_conflicts,
            );
        }

        let base_payload =
            Self::entry_payload_json(conn, &base_row.entry_id, &base_row.payload_ct)?;
        let local_payload =
            Self::entry_payload_json(conn, &local_row.entry_id, &local_row.payload_ct)?;
        let incoming_payload =
            Self::entry_payload_json(conn, &incoming.entry_id, &incoming.payload_ct)?;
        let payload_conflicts = ConflictDetector::detect_entry_conflict(
            &base_payload,
            &local_payload,
            &incoming_payload,
        );

        if !ConflictDetector::is_safe_to_auto_merge(&payload_conflicts) {
            return Self::record_entry_field_conflict(
                conn,
                ctx,
                &incoming.entry_id,
                &base_commit_id,
                local_commit_id,
                incoming_commit_id,
                &payload_conflicts,
            );
        }

        let merged_payload = ConflictDetector::build_merged_payload(
            &base_payload,
            &local_payload,
            &incoming_payload,
        );
        Self::apply_merged_entry(
            conn,
            ctx,
            &base_row,
            &local_row,
            incoming,
            local_commit_id,
            incoming_commit_id,
            &merged_payload,
        )?;
        Ok(0)
    }

    fn apply_merged_entry(
        conn: &VaultConnection,
        ctx: &CommitContext,
        base: &EntryRow,
        local: &EntryRow,
        incoming: &EntryRow,
        local_commit_id: &str,
        incoming_commit_id: &str,
        merged_payload: &serde_json::Value,
    ) -> StorageResult<()> {
        let mut parents = vec![local_commit_id.to_string()];
        if incoming_commit_id != local_commit_id {
            parents.push(incoming_commit_id.to_string());
        }
        let commit_id = ctx.create_commit(
            conn,
            "merge",
            "entry",
            &[incoming.entry_id.clone()],
            &parents,
        )?;
        let payload_plain = serde_json::to_vec(merged_payload)
            .map_err(|e| StorageError::SchemaCreation(e.to_string()))?;
        let payload_ct = EntryRepo::encrypt_payload_blob(conn, &incoming.entry_id, &payload_plain)?;
        let now = chrono::Utc::now().to_rfc3339();
        let project_id = merge_value(&base.project_id, &local.project_id, &incoming.project_id)
            .unwrap_or_else(|| local.project_id.clone());
        let entry_type = merge_value(&base.entry_type, &local.entry_type, &incoming.entry_type)
            .unwrap_or_else(|| local.entry_type.clone());
        let title_ct = merge_value(&base.title_ct, &local.title_ct, &incoming.title_ct)
            .unwrap_or_else(|| local.title_ct.clone());
        let tiga_mode_override = merge_value(
            &base.tiga_mode_override,
            &local.tiga_mode_override,
            &incoming.tiga_mode_override,
        )
        .unwrap_or_else(|| local.tiga_mode_override.clone());

        conn.inner().execute(
            "UPDATE entries SET project_id = ?2, entry_type = ?3, title_ct = ?4,
             payload_ct = ?5, payload_schema_version = ?6, tiga_mode_override = ?7,
             object_clock = ?8, head_commit_id = ?9, deleted = 0,
             updated_at = ?10, updated_by_device_id = ?11
             WHERE entry_id = ?1",
            params![
                incoming.entry_id,
                project_id,
                entry_type,
                title_ct,
                payload_ct,
                std::cmp::max(
                    local.payload_schema_version,
                    incoming.payload_schema_version
                ) as i64,
                tiga_mode_override,
                bump_object_clock(&local.object_clock),
                commit_id,
                now,
                ctx.device_id,
            ],
        )?;
        ObjectVersionRepo::record_entry_current(conn, &commit_id, &incoming.entry_id)?;
        Ok(())
    }

    fn entry_payload_json(
        conn: &VaultConnection,
        entry_id: &str,
        payload_ct: &[u8],
    ) -> StorageResult<serde_json::Value> {
        let plaintext = EntryRepo::decrypt_payload_blob(conn, entry_id, payload_ct)?;
        serde_json::from_slice(&plaintext).map_err(|e| {
            StorageError::Validation(format!(
                "entry {} payload is not valid JSON: {}",
                entry_id, e
            ))
        })
    }

    fn record_entry_field_conflict(
        conn: &VaultConnection,
        ctx: &CommitContext,
        entry_id: &str,
        base_commit_id: &str,
        local_commit_id: &str,
        incoming_commit_id: &str,
        fields: &[String],
    ) -> StorageResult<u32> {
        if ConflictRepo::has_unresolved_conflict(conn, ConflictObjectType::Entry, entry_id)? {
            return Ok(0);
        }
        ConflictRepo::create(
            conn,
            ctx,
            ConflictObjectType::Entry,
            entry_id,
            base_commit_id,
            local_commit_id,
            incoming_commit_id,
            fields,
        )?;
        Ok(1)
    }

    fn nearest_known_common_parent(
        conn: &VaultConnection,
        left: &str,
        right: &str,
    ) -> StorageResult<Option<String>> {
        let left_ancestors = Self::ancestor_set(conn, left)?;
        let mut stack = vec![right.to_string()];
        let mut seen = HashSet::new();
        while let Some(commit_id) = stack.pop() {
            if !seen.insert(commit_id.clone()) {
                continue;
            }
            if left_ancestors.contains(&commit_id) {
                return Ok(Some(commit_id));
            }
            stack.extend(Self::parent_ids_for_commit(conn, &commit_id)?);
        }
        Ok(None)
    }

    fn ancestor_set(conn: &VaultConnection, head: &str) -> StorageResult<HashSet<String>> {
        let mut result = HashSet::new();
        let mut stack = vec![head.to_string()];
        while let Some(commit_id) = stack.pop() {
            if !result.insert(commit_id.clone()) {
                continue;
            }
            stack.extend(Self::parent_ids_for_commit(conn, &commit_id)?);
        }
        Ok(result)
    }

    fn commit_exists(conn: &VaultConnection, commit_id: &str) -> StorageResult<bool> {
        let count: i64 = conn.inner().query_row(
            "SELECT COUNT(*) FROM commits WHERE commit_id = ?1",
            params![commit_id],
            |row| row.get(0),
        )?;
        Ok(count > 0)
    }

    fn record_payload_conflict(
        conn: &VaultConnection,
        ctx: &CommitContext,
        serialized: &SerializedCommit,
        payload: &ObjectPayload,
        local_head: Option<&str>,
    ) -> StorageResult<u32> {
        let local_head = local_head.ok_or_else(|| {
            StorageError::Validation("missing local branch head for conflict detection".into())
        })?;

        let object_type = conflict_object_type(&payload.object_type);
        let Some(object_type) = object_type else {
            return Ok(0);
        };
        let local_exists = match payload.object_type.as_str() {
            "project" => exists(conn, "projects", "project_id", &payload.object_id)?,
            "entry" => exists(conn, "entries", "entry_id", &payload.object_id)?,
            "attachment" => exists(conn, "attachments", "attachment_id", &payload.object_id)?,
            _ => false,
        };
        if !local_exists || local_head == serialized.commit.commit_id {
            return Ok(0);
        }
        if ConflictRepo::has_unresolved_conflict(conn, object_type.clone(), &payload.object_id)? {
            return Ok(0);
        }

        let base_commit_id =
            Self::nearest_known_common_parent(conn, local_head, &serialized.commit.commit_id)?
                .unwrap_or_else(|| local_head.to_string());
        ConflictRepo::create(
            conn,
            ctx,
            object_type,
            &payload.object_id,
            &base_commit_id,
            local_head,
            &serialized.commit.commit_id,
            &[String::from("payload_ct")],
        )?;
        Ok(1)
    }

    fn sync_device_head(
        conn: &VaultConnection,
        serialized: &SerializedCommit,
    ) -> StorageResult<()> {
        conn.inner().execute(
            "INSERT INTO device_heads (device_id, head_commit_id, last_seen_at, revoked)
             VALUES (?1, ?2, ?3, 0)
             ON CONFLICT(device_id) DO UPDATE SET
                head_commit_id = excluded.head_commit_id,
                last_seen_at = excluded.last_seen_at,
                revoked = 0",
            params![
                serialized.commit.device_id,
                serialized.commit.commit_id,
                serialized.commit.created_at
            ],
        )?;
        Ok(())
    }

    fn current_branch_head(
        conn: &VaultConnection,
        branch_name: &str,
    ) -> StorageResult<Option<String>> {
        conn.inner()
            .query_row(
                "SELECT head_commit_id FROM branches WHERE branch_name = ?1 ORDER BY updated_at DESC LIMIT 1",
                params![branch_name],
                |row| row.get(0),
            )
            .optional()
            .map_err(StorageError::Database)
    }

    fn advance_main_branch(conn: &VaultConnection, commit_id: &str) -> StorageResult<()> {
        let now = chrono::Utc::now().to_rfc3339();
        conn.inner().execute(
            "UPDATE branches SET head_commit_id = ?1, updated_at = ?2 WHERE branch_name = 'main'",
            params![commit_id, now],
        )?;
        Ok(())
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum ApplyOutcome {
    Applied,
    Skipped,
    Conflict,
    MissingParent,
}

fn conflict_object_type(value: &str) -> Option<ConflictObjectType> {
    match value {
        "project" => Some(ConflictObjectType::Project),
        "entry" => Some(ConflictObjectType::Entry),
        "attachment" => Some(ConflictObjectType::Attachment),
        _ => None,
    }
}

#[derive(Debug, Clone, Default)]
struct AttachmentApplyResult {
    ids: HashSet<String>,
    conflict_count: u32,
}

#[derive(Debug, Clone, PartialEq, Eq)]
enum ObjectDecision {
    Insert,
    FastForward,
    Conflict { local_head: String },
    Skip,
}

fn exists(
    conn: &VaultConnection,
    table: &str,
    id_column: &str,
    object_id: &str,
) -> StorageResult<bool> {
    let sql = format!("SELECT COUNT(*) FROM {} WHERE {} = ?1", table, id_column);
    let count: i64 = conn
        .inner()
        .query_row(&sql, params![object_id], |row| row.get(0))?;
    Ok(count > 0)
}

fn merge_value<T: Clone + PartialEq>(base: &T, local: &T, incoming: &T) -> Option<T> {
    if local == incoming {
        Some(local.clone())
    } else if local != base && incoming == base {
        Some(local.clone())
    } else if local == base && incoming != base {
        Some(incoming.clone())
    } else {
        None
    }
}

fn bump_object_clock(clock: &str) -> String {
    let counter: u64 = serde_json::from_str::<serde_json::Value>(clock)
        .ok()
        .and_then(|v| v.get("counter")?.as_u64())
        .unwrap_or(0);
    format!(r#"{{"counter":{}}}"#, counter + 1)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::commit_integrity::compute_commit_integrity_tag;
    use crate::commit_integrity::CommitIntegrityInput;
    use crate::init::{initialize_vault, VaultInitParams};
    use crate::repo::{AttachmentRepo, EntryRepo, ObjectVersionRepo, ProjectRepo};
    use crate::sync_state::collect_sync_state_payload;
    use mdbx_core::model::{ChangeScope, Commit, CommitKind, ConflictResolution, EntryType};
    use mdbx_sync::{CommitBatch, ObjectPayload, SerializedCommit, TombstoneRecord};
    use std::path::PathBuf;
    use uuid::Uuid;

    fn setup() -> (VaultConnection, CommitContext) {
        let conn = VaultConnection::open_in_memory().unwrap();
        initialize_vault(&conn, &VaultInitParams::default()).unwrap();
        let ctx = CommitContext::new("device-a".to_string());
        (conn, ctx)
    }

    fn make_commit(
        commit_id: &str,
        device_id: &str,
        local_seq: u64,
        parents: Vec<String>,
        changed: Vec<String>,
        object_id: &str,
        payload_object_type: &str,
    ) -> SerializedCommit {
        let commit = Commit {
            commit_id: commit_id.to_string(),
            device_id: device_id.to_string(),
            local_seq,
            commit_kind: CommitKind::Change,
            change_scope: ChangeScope::Project,
            changed_object_ids_ct: serde_json::to_vec(&changed).unwrap(),
            vector_clock: format!(r#"{{"{}":{}}}"#, device_id, local_seq),
            message_ct: None,
            created_at: "2026-05-22T00:00:00Z".to_string(),
            integrity_tag: vec![],
        };
        let tag = compute_commit_integrity_tag(
            None,
            &CommitIntegrityInput {
                commit_id: &commit.commit_id,
                device_id: &commit.device_id,
                local_seq: commit.local_seq,
                commit_kind: &commit.commit_kind.to_string(),
                change_scope: &commit.change_scope.to_string(),
                changed_object_ids_ct: &commit.changed_object_ids_ct,
                vector_clock: &commit.vector_clock,
                message_ct: None,
                created_at: &commit.created_at,
                parents: &parents,
            },
        )
        .unwrap();
        SerializedCommit {
            commit: Commit {
                integrity_tag: tag,
                ..commit
            },
            parent_ids: parents,
            tombstones: vec![TombstoneRecord {
                tombstone_id: format!("t-{}", commit_id),
                target_object_type: payload_object_type.to_string(),
                target_object_id: object_id.to_string(),
                delete_clock: "{}".to_string(),
                deleted_by_device_id: device_id.to_string(),
                deleted_at: "2026-05-22T00:00:00Z".to_string(),
            }],
            object_payloads: vec![ObjectPayload {
                object_type: payload_object_type.to_string(),
                object_id: object_id.to_string(),
                ciphertext: vec![1, 2, 3],
                associated_data: vec![],
            }],
        }
    }

    fn temp_vault_path(label: &str) -> PathBuf {
        std::env::temp_dir().join(format!("mdbx-sync-{}-{}.mdbx", label, Uuid::new_v4()))
    }

    fn checkpoint(conn: &VaultConnection) {
        conn.inner()
            .execute_batch("PRAGMA wal_checkpoint(TRUNCATE);")
            .unwrap();
    }

    fn serialized_commits_from(conn: &VaultConnection) -> Vec<SerializedCommit> {
        let mut stmt = conn
            .inner()
            .prepare(
                "SELECT commit_id, device_id, local_seq, commit_kind, change_scope,
                        changed_object_ids_ct, vector_clock, message_ct, created_at, integrity_tag
                 FROM commits
                 ORDER BY created_at ASC, device_id ASC, local_seq ASC",
            )
            .unwrap();

        let rows = stmt
            .query_map([], |row| {
                let commit_id: String = row.get(0)?;
                Ok(SerializedCommit {
                    parent_ids: SyncApplyRepo::parent_ids_for_commit(conn, &commit_id).unwrap(),
                    tombstones: vec![],
                    object_payloads: vec![],
                    commit: Commit {
                        commit_id,
                        device_id: row.get(1)?,
                        local_seq: row.get::<_, i64>(2)? as u64,
                        commit_kind: parse_commit_kind_for_test(&row.get::<_, String>(3)?),
                        change_scope: parse_change_scope_for_test(&row.get::<_, String>(4)?),
                        changed_object_ids_ct: row.get(5)?,
                        vector_clock: row.get(6)?,
                        message_ct: row.get(7)?,
                        created_at: row.get(8)?,
                        integrity_tag: row.get(9)?,
                    },
                })
            })
            .unwrap();

        rows.map(|row| row.unwrap()).collect()
    }

    fn parse_commit_kind_for_test(value: &str) -> CommitKind {
        match value {
            "merge" => CommitKind::Merge,
            "snapshot" => CommitKind::Snapshot,
            "key-rotation" => CommitKind::KeyRotation,
            _ => CommitKind::Change,
        }
    }

    fn parse_change_scope_for_test(value: &str) -> ChangeScope {
        match value {
            "project" => ChangeScope::Project,
            "entry" => ChangeScope::Entry,
            "attachment" => ChangeScope::Attachment,
            "vault-meta" => ChangeScope::VaultMeta,
            "key-epoch" => ChangeScope::KeyEpoch,
            _ => ChangeScope::Multi,
        }
    }

    fn update_entry_payload(
        conn: &VaultConnection,
        ctx: &CommitContext,
        entry_id: &str,
        payload: serde_json::Value,
    ) -> mdbx_core::model::Entry {
        let mut entry = EntryRepo::get_by_id(conn, entry_id).unwrap().unwrap();
        entry.payload_ct = serde_json::to_vec(&payload).unwrap();
        EntryRepo::update(conn, ctx, &entry).unwrap()
    }

    fn attach_state_payload_to_commit(
        conn: &VaultConnection,
        commits: &mut [SerializedCommit],
        commit_id: &str,
    ) {
        let payload = collect_sync_state_payload(conn).unwrap();
        commits
            .iter_mut()
            .find(|commit| commit.commit.commit_id == commit_id)
            .unwrap()
            .object_payloads
            .push(payload);
    }

    fn attach_tombstones_to_commit(
        conn: &VaultConnection,
        commits: &mut [SerializedCommit],
        commit_id: &str,
    ) {
        let mut stmt = conn
            .inner()
            .prepare(
                "SELECT tombstone_id, target_object_type, target_object_id,
                        delete_clock, deleted_by_device_id, deleted_at
                 FROM tombstones",
            )
            .unwrap();
        let tombstones = stmt
            .query_map([], |row| {
                Ok(TombstoneRecord {
                    tombstone_id: row.get(0)?,
                    target_object_type: row.get(1)?,
                    target_object_id: row.get(2)?,
                    delete_clock: row.get(3)?,
                    deleted_by_device_id: row.get(4)?,
                    deleted_at: row.get(5)?,
                })
            })
            .unwrap()
            .map(|row| row.unwrap())
            .collect::<Vec<_>>();

        commits
            .iter_mut()
            .find(|commit| commit.commit.commit_id == commit_id)
            .unwrap()
            .tombstones
            .extend(tombstones);
    }

    fn entry_payload_json(conn: &VaultConnection, entry_id: &str) -> serde_json::Value {
        let entry = EntryRepo::get_by_id(conn, entry_id).unwrap().unwrap();
        serde_json::from_slice(&entry.payload_ct).unwrap()
    }

    fn create_divergent_password_conflict(label: &str) -> (PathBuf, PathBuf, String) {
        let source_path = temp_vault_path(&format!("{}-source", label));
        let target_path = temp_vault_path(&format!("{}-target", label));
        let entry_id;

        {
            let source = VaultConnection::create(&source_path).unwrap();
            initialize_vault(
                &source,
                &VaultInitParams {
                    vault_id: Some(format!("{}-vault", label)),
                    device_id: "device-a".to_string(),
                    ..Default::default()
                },
            )
            .unwrap();
            let source_ctx = CommitContext::new("device-a".to_string());
            let project = ProjectRepo::create(&source, &source_ctx, "P", None, None).unwrap();
            let entry = EntryRepo::create(
                &source,
                &source_ctx,
                &project.project_id,
                EntryType::Login,
                Some("Login"),
                &serde_json::json!({
                    "username": "alice",
                    "password": "old",
                    "url": "https://old.example"
                }),
            )
            .unwrap();
            entry_id = entry.entry_id.clone();
            checkpoint(&source);
        }
        std::fs::copy(&source_path, &target_path).unwrap();

        let source = VaultConnection::open(&source_path).unwrap();
        let target = VaultConnection::open(&target_path).unwrap();
        let source_ctx = CommitContext::new("device-a".to_string());
        let target_ctx = CommitContext::new("device-b".to_string());

        let incoming = update_entry_payload(
            &source,
            &source_ctx,
            &entry_id,
            serde_json::json!({
                "username": "alice",
                "password": "remote-secret",
                "url": "https://old.example"
            }),
        );
        let _local = update_entry_payload(
            &target,
            &target_ctx,
            &entry_id,
            serde_json::json!({
                "username": "alice",
                "password": "local-secret",
                "url": "https://old.example"
            }),
        );

        let mut commits = serialized_commits_from(&source);
        attach_state_payload_to_commit(&source, &mut commits, &incoming.head_commit_id);
        let result =
            SyncApplyRepo::apply_batch(&target, &target_ctx, &CommitBatch::new(commits, 0, true))
                .unwrap();
        assert_eq!(result.conflict_count, 1);

        drop(source);
        drop(target);
        (source_path, target_path, entry_id)
    }

    fn create_delete_modify_conflict(
        label: &str,
        remote_deletes: bool,
    ) -> (PathBuf, PathBuf, String) {
        let source_path = temp_vault_path(&format!("{}-source", label));
        let target_path = temp_vault_path(&format!("{}-target", label));
        let entry_id;

        {
            let source = VaultConnection::create(&source_path).unwrap();
            initialize_vault(
                &source,
                &VaultInitParams {
                    vault_id: Some(format!("{}-vault", label)),
                    device_id: "device-a".to_string(),
                    ..Default::default()
                },
            )
            .unwrap();
            let source_ctx = CommitContext::new("device-a".to_string());
            let project = ProjectRepo::create(&source, &source_ctx, "P", None, None).unwrap();
            let entry = EntryRepo::create(
                &source,
                &source_ctx,
                &project.project_id,
                EntryType::Login,
                Some("Login"),
                &serde_json::json!({
                    "username": "alice",
                    "password": "old"
                }),
            )
            .unwrap();
            entry_id = entry.entry_id.clone();
            checkpoint(&source);
        }
        std::fs::copy(&source_path, &target_path).unwrap();

        let source = VaultConnection::open(&source_path).unwrap();
        let target = VaultConnection::open(&target_path).unwrap();
        let source_ctx = CommitContext::new("device-a".to_string());
        let target_ctx = CommitContext::new("device-b".to_string());

        let incoming_head = if remote_deletes {
            EntryRepo::soft_delete(&source, &source_ctx, &entry_id).unwrap();
            EntryRepo::get_by_id(&source, &entry_id)
                .unwrap()
                .unwrap()
                .head_commit_id
        } else {
            update_entry_payload(
                &source,
                &source_ctx,
                &entry_id,
                serde_json::json!({
                    "username": "alice",
                    "password": "remote-change"
                }),
            )
            .head_commit_id
        };

        if remote_deletes {
            update_entry_payload(
                &target,
                &target_ctx,
                &entry_id,
                serde_json::json!({
                    "username": "alice",
                    "password": "local-change"
                }),
            );
        } else {
            EntryRepo::soft_delete(&target, &target_ctx, &entry_id).unwrap();
        }

        let mut commits = serialized_commits_from(&source);
        attach_state_payload_to_commit(&source, &mut commits, &incoming_head);
        attach_tombstones_to_commit(&source, &mut commits, &incoming_head);
        let result =
            SyncApplyRepo::apply_batch(&target, &target_ctx, &CommitBatch::new(commits, 0, true))
                .unwrap();
        assert_eq!(result.conflict_count, 1);

        drop(source);
        drop(target);
        (source_path, target_path, entry_id)
    }

    #[test]
    fn apply_fast_forward_commit() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "P", None, None).unwrap();
        let main_head: String = conn
            .inner()
            .query_row(
                "SELECT head_commit_id FROM branches WHERE branch_name = 'main'",
                [],
                |row| row.get(0),
            )
            .unwrap();
        let incoming = make_commit(
            "remote-1",
            "device-b",
            1,
            vec![main_head],
            vec![project.project_id.clone()],
            &project.project_id,
            "project",
        );
        let batch = CommitBatch::new(vec![incoming], 0, true);
        let result = SyncApplyRepo::apply_batch(&conn, &ctx, &batch).unwrap();
        assert_eq!(result.applied_commits, 1);
        assert_eq!(result.conflict_count, 0);
    }

    #[test]
    fn apply_divergent_commit_creates_conflict() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "P", None, None).unwrap();
        let updated = ProjectRepo::update(
            &conn,
            &ctx,
            &ProjectRepo::get_by_id(&conn, &project.project_id)
                .unwrap()
                .unwrap(),
        )
        .unwrap();
        let incoming = make_commit(
            "remote-2",
            "device-b",
            1,
            vec![project.head_commit_id.clone()],
            vec![project.project_id.clone()],
            &updated.project_id,
            "project",
        );
        let batch = CommitBatch::new(vec![incoming], 0, true);
        let result = SyncApplyRepo::apply_batch(&conn, &ctx, &batch).unwrap();
        assert_eq!(result.conflict_count, 1);
        assert!(ConflictRepo::list_unresolved(&conn).unwrap().len() >= 1);
    }

    #[test]
    fn apply_fast_forward_state_payload_materializes_objects() {
        let source_path = temp_vault_path("source");
        let target_path = temp_vault_path("target");

        {
            let source = VaultConnection::create(&source_path).unwrap();
            initialize_vault(
                &source,
                &VaultInitParams {
                    vault_id: Some("sync-state-test-vault".to_string()),
                    device_id: "device-a".to_string(),
                    ..Default::default()
                },
            )
            .unwrap();
            checkpoint(&source);
        }
        std::fs::copy(&source_path, &target_path).unwrap();

        let source = VaultConnection::open(&source_path).unwrap();
        let target = VaultConnection::open(&target_path).unwrap();
        let source_ctx = CommitContext::new("device-a".to_string());
        let target_ctx = CommitContext::new("device-b".to_string());

        let project =
            ProjectRepo::create(&source, &source_ctx, "Synced Project", Some("work"), None)
                .unwrap();
        let entry = EntryRepo::create(
            &source,
            &source_ctx,
            &project.project_id,
            EntryType::Login,
            Some("Synced Login"),
            &serde_json::json!({
                "username": "alice",
                "password": "synced-secret",
                "url": "https://sync.example"
            }),
        )
        .unwrap();
        let attachment = AttachmentRepo::add(
            &source,
            &source_ctx,
            &project.project_id,
            Some(&entry.entry_id),
            "proof.txt",
            Some("text/plain"),
            "",
            0,
        )
        .unwrap();
        AttachmentRepo::write_inline_content(
            &source,
            &source_ctx,
            &attachment.attachment_id,
            b"hello from source",
        )
        .unwrap();

        let mut commits = serialized_commits_from(&source);
        commits
            .last_mut()
            .unwrap()
            .object_payloads
            .push(collect_sync_state_payload(&source).unwrap());

        let batch = CommitBatch::new(commits, 0, true);
        let result = SyncApplyRepo::apply_batch(&target, &target_ctx, &batch).unwrap();

        assert!(result.applied_commits >= 4);
        assert_eq!(result.conflict_count, 0);

        let synced_project = ProjectRepo::get_by_id(&target, &project.project_id)
            .unwrap()
            .unwrap();
        assert_eq!(synced_project.title_ct, b"Synced Project");

        let synced_entry = EntryRepo::get_by_id(&target, &entry.entry_id)
            .unwrap()
            .unwrap();
        assert_eq!(synced_entry.project_id, project.project_id);
        assert_eq!(synced_entry.title_ct, Some(b"Synced Login".to_vec()));

        let synced_attachment = AttachmentRepo::get_by_id(&target, &attachment.attachment_id)
            .unwrap()
            .unwrap();
        assert_eq!(
            synced_attachment.entry_id.as_deref(),
            Some(entry.entry_id.as_str())
        );
        assert_eq!(
            AttachmentRepo::read_content(&target, &attachment.attachment_id).unwrap(),
            b"hello from source"
        );

        drop(source);
        drop(target);
        let _ = std::fs::remove_file(source_path);
        let _ = std::fs::remove_file(target_path);
    }

    #[test]
    fn divergent_entry_different_payload_fields_auto_merge() {
        let source_path = temp_vault_path("field-merge-source");
        let target_path = temp_vault_path("field-merge-target");
        let entry_id;

        {
            let source = VaultConnection::create(&source_path).unwrap();
            initialize_vault(
                &source,
                &VaultInitParams {
                    vault_id: Some("field-merge-vault".to_string()),
                    device_id: "device-a".to_string(),
                    ..Default::default()
                },
            )
            .unwrap();
            let source_ctx = CommitContext::new("device-a".to_string());
            let project = ProjectRepo::create(&source, &source_ctx, "P", None, None).unwrap();
            let entry = EntryRepo::create(
                &source,
                &source_ctx,
                &project.project_id,
                EntryType::Login,
                Some("Login"),
                &serde_json::json!({
                    "username": "alice",
                    "password": "old",
                    "url": "https://old.example"
                }),
            )
            .unwrap();
            entry_id = entry.entry_id.clone();
            checkpoint(&source);
        }
        std::fs::copy(&source_path, &target_path).unwrap();

        let source = VaultConnection::open(&source_path).unwrap();
        let target = VaultConnection::open(&target_path).unwrap();
        let source_ctx = CommitContext::new("device-a".to_string());
        let target_ctx = CommitContext::new("device-b".to_string());

        let incoming = update_entry_payload(
            &source,
            &source_ctx,
            &entry_id,
            serde_json::json!({
                "username": "alice",
                "password": "old",
                "url": "https://remote.example"
            }),
        );
        let _local = update_entry_payload(
            &target,
            &target_ctx,
            &entry_id,
            serde_json::json!({
                "username": "alice-local",
                "password": "old",
                "url": "https://old.example"
            }),
        );

        let mut commits = serialized_commits_from(&source);
        attach_state_payload_to_commit(&source, &mut commits, &incoming.head_commit_id);
        let result =
            SyncApplyRepo::apply_batch(&target, &target_ctx, &CommitBatch::new(commits, 0, true))
                .unwrap();

        assert_eq!(result.conflict_count, 0);
        assert!(ConflictRepo::list_unresolved(&target).unwrap().is_empty());
        let merged = entry_payload_json(&target, &entry_id);
        assert_eq!(merged["username"], "alice-local");
        assert_eq!(merged["password"], "old");
        assert_eq!(merged["url"], "https://remote.example");

        let merged_entry = EntryRepo::get_by_id(&target, &entry_id).unwrap().unwrap();
        let parent_count: i64 = target
            .inner()
            .query_row(
                "SELECT COUNT(*) FROM commit_parents WHERE commit_id = ?1",
                params![merged_entry.head_commit_id],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(parent_count, 2);
        let version_count: i64 = target
            .inner()
            .query_row(
                "SELECT COUNT(*) FROM object_versions
                 WHERE object_type = 'entry' AND object_id = ?1 AND commit_id = ?2",
                params![entry_id, merged_entry.head_commit_id],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(version_count, 1);

        drop(source);
        drop(target);
        let _ = std::fs::remove_file(source_path);
        let _ = std::fs::remove_file(target_path);
    }

    #[test]
    fn divergent_entry_same_payload_field_creates_conflict() {
        let source_path = temp_vault_path("field-conflict-source");
        let target_path = temp_vault_path("field-conflict-target");
        let entry_id;

        {
            let source = VaultConnection::create(&source_path).unwrap();
            initialize_vault(
                &source,
                &VaultInitParams {
                    vault_id: Some("field-conflict-vault".to_string()),
                    device_id: "device-a".to_string(),
                    ..Default::default()
                },
            )
            .unwrap();
            let source_ctx = CommitContext::new("device-a".to_string());
            let project = ProjectRepo::create(&source, &source_ctx, "P", None, None).unwrap();
            let entry = EntryRepo::create(
                &source,
                &source_ctx,
                &project.project_id,
                EntryType::Login,
                Some("Login"),
                &serde_json::json!({
                    "username": "alice",
                    "password": "old",
                    "url": "https://old.example"
                }),
            )
            .unwrap();
            entry_id = entry.entry_id.clone();
            checkpoint(&source);
        }
        std::fs::copy(&source_path, &target_path).unwrap();

        let source = VaultConnection::open(&source_path).unwrap();
        let target = VaultConnection::open(&target_path).unwrap();
        let source_ctx = CommitContext::new("device-a".to_string());
        let target_ctx = CommitContext::new("device-b".to_string());

        let incoming = update_entry_payload(
            &source,
            &source_ctx,
            &entry_id,
            serde_json::json!({
                "username": "alice",
                "password": "remote-secret",
                "url": "https://old.example"
            }),
        );
        let _local = update_entry_payload(
            &target,
            &target_ctx,
            &entry_id,
            serde_json::json!({
                "username": "alice",
                "password": "local-secret",
                "url": "https://old.example"
            }),
        );

        let mut commits = serialized_commits_from(&source);
        attach_state_payload_to_commit(&source, &mut commits, &incoming.head_commit_id);
        let result =
            SyncApplyRepo::apply_batch(&target, &target_ctx, &CommitBatch::new(commits, 0, true))
                .unwrap();

        assert_eq!(result.conflict_count, 1);
        let conflicts = ConflictRepo::list_unresolved(&target).unwrap();
        assert_eq!(conflicts.len(), 1);
        assert_eq!(conflicts[0].object_id, entry_id);
        assert_eq!(conflicts[0].conflicting_fields, vec!["password"]);

        let local_after = entry_payload_json(&target, &entry_id);
        assert_eq!(local_after["password"], "local-secret");

        drop(source);
        drop(target);
        let _ = std::fs::remove_file(source_path);
        let _ = std::fs::remove_file(target_path);
    }

    #[test]
    fn entry_conflict_resolve_local_wins_writes_resolution_commit() {
        let (source_path, target_path, entry_id) =
            create_divergent_password_conflict("resolve-local-wins");
        let target = VaultConnection::open(&target_path).unwrap();
        let target_ctx = CommitContext::new("device-b".to_string());
        let conflict = ConflictRepo::list_unresolved(&target).unwrap().remove(0);
        let before = EntryRepo::get_by_id(&target, &entry_id).unwrap().unwrap();

        let resolved = ConflictRepo::resolve_entry(
            &target,
            &target_ctx,
            &conflict.conflict_id,
            ConflictResolution::LocalWins,
        )
        .unwrap();

        assert_eq!(resolved.resolution, ConflictResolution::LocalWins);
        assert!(resolved.resolved_at.is_some());
        assert!(ConflictRepo::list_unresolved(&target).unwrap().is_empty());

        let after = EntryRepo::get_by_id(&target, &entry_id).unwrap().unwrap();
        assert_ne!(after.head_commit_id, before.head_commit_id);
        assert_eq!(
            entry_payload_json(&target, &entry_id)["password"],
            "local-secret"
        );

        let parents = SyncApplyRepo::parent_ids_for_commit(&target, &after.head_commit_id).unwrap();
        assert!(parents.contains(&before.head_commit_id));
        assert!(parents.contains(&conflict.incoming_commit_id));

        drop(target);
        let _ = std::fs::remove_file(source_path);
        let _ = std::fs::remove_file(target_path);
    }

    #[test]
    fn entry_conflict_resolve_incoming_wins_applies_incoming_snapshot() {
        let (source_path, target_path, entry_id) =
            create_divergent_password_conflict("resolve-incoming-wins");
        let target = VaultConnection::open(&target_path).unwrap();
        let target_ctx = CommitContext::new("device-b".to_string());
        let conflict = ConflictRepo::list_unresolved(&target).unwrap().remove(0);
        let before = EntryRepo::get_by_id(&target, &entry_id).unwrap().unwrap();

        let resolved = ConflictRepo::resolve_entry(
            &target,
            &target_ctx,
            &conflict.conflict_id,
            ConflictResolution::IncomingWins,
        )
        .unwrap();

        assert_eq!(resolved.resolution, ConflictResolution::IncomingWins);
        assert!(resolved.resolved_at.is_some());
        assert!(ConflictRepo::list_unresolved(&target).unwrap().is_empty());

        let after = EntryRepo::get_by_id(&target, &entry_id).unwrap().unwrap();
        assert_ne!(after.head_commit_id, before.head_commit_id);
        assert_eq!(
            entry_payload_json(&target, &entry_id)["password"],
            "remote-secret"
        );
        assert!(
            ObjectVersionRepo::get_entry(&target, &entry_id, &after.head_commit_id)
                .unwrap()
                .is_some()
        );

        let parents = SyncApplyRepo::parent_ids_for_commit(&target, &after.head_commit_id).unwrap();
        assert!(parents.contains(&before.head_commit_id));
        assert!(parents.contains(&conflict.incoming_commit_id));

        drop(target);
        let _ = std::fs::remove_file(source_path);
        let _ = std::fs::remove_file(target_path);
    }

    #[test]
    fn entry_conflict_resolve_custom_payload_writes_merge_commit() {
        let (source_path, target_path, entry_id) =
            create_divergent_password_conflict("resolve-custom-payload");
        let target = VaultConnection::open(&target_path).unwrap();
        let target_ctx = CommitContext::new("device-b".to_string());
        let conflict = ConflictRepo::list_unresolved(&target).unwrap().remove(0);
        let before = EntryRepo::get_by_id(&target, &entry_id).unwrap().unwrap();

        let resolved = ConflictRepo::resolve_entry_custom_payload(
            &target,
            &target_ctx,
            &conflict.conflict_id,
            &serde_json::json!({
                "username": "alice",
                "password": "merged-secret",
                "url": "https://old.example"
            }),
        )
        .unwrap();

        assert_eq!(resolved.resolution, ConflictResolution::Custom);
        assert!(resolved.resolved_at.is_some());
        assert!(ConflictRepo::list_unresolved(&target).unwrap().is_empty());

        let after = EntryRepo::get_by_id(&target, &entry_id).unwrap().unwrap();
        assert_ne!(after.head_commit_id, before.head_commit_id);
        assert_eq!(
            entry_payload_json(&target, &entry_id)["password"],
            "merged-secret"
        );
        assert!(
            ObjectVersionRepo::get_entry(&target, &entry_id, &after.head_commit_id)
                .unwrap()
                .is_some()
        );

        let parents = SyncApplyRepo::parent_ids_for_commit(&target, &after.head_commit_id).unwrap();
        assert!(parents.contains(&before.head_commit_id));
        assert!(parents.contains(&conflict.incoming_commit_id));

        drop(target);
        let _ = std::fs::remove_file(source_path);
        let _ = std::fs::remove_file(target_path);
    }

    #[test]
    fn remote_delete_local_modify_creates_conflict_without_losing_tombstone() {
        let (source_path, target_path, entry_id) =
            create_delete_modify_conflict("remote-delete-local-modify", true);
        let target = VaultConnection::open(&target_path).unwrap();

        let conflicts = ConflictRepo::list_unresolved(&target).unwrap();
        assert_eq!(conflicts.len(), 1);
        assert_eq!(conflicts[0].object_id, entry_id);
        assert_eq!(conflicts[0].conflicting_fields, vec!["deleted"]);

        let entry = EntryRepo::get_by_id(&target, &entry_id).unwrap().unwrap();
        assert!(!entry.deleted);
        assert_eq!(
            entry_payload_json(&target, &entry_id)["password"],
            "local-change"
        );

        let tombstone_count: i64 = target
            .inner()
            .query_row(
                "SELECT COUNT(*) FROM tombstones
                 WHERE target_object_type = 'entry' AND target_object_id = ?1",
                params![entry_id],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(tombstone_count, 1);

        drop(target);
        let _ = std::fs::remove_file(source_path);
        let _ = std::fs::remove_file(target_path);
    }

    #[test]
    fn local_delete_remote_modify_creates_conflict_without_reviving_entry() {
        let (source_path, target_path, entry_id) =
            create_delete_modify_conflict("local-delete-remote-modify", false);
        let target = VaultConnection::open(&target_path).unwrap();

        let conflicts = ConflictRepo::list_unresolved(&target).unwrap();
        assert_eq!(conflicts.len(), 1);
        assert_eq!(conflicts[0].object_id, entry_id);
        assert_eq!(conflicts[0].conflicting_fields, vec!["deleted"]);

        let entry = EntryRepo::get_by_id(&target, &entry_id).unwrap().unwrap();
        assert!(entry.deleted);

        let tombstone_count: i64 = target
            .inner()
            .query_row(
                "SELECT COUNT(*) FROM tombstones
                 WHERE target_object_type = 'entry' AND target_object_id = ?1",
                params![entry_id],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(tombstone_count, 1);

        drop(target);
        let _ = std::fs::remove_file(source_path);
        let _ = std::fs::remove_file(target_path);
    }
}
