use rusqlite::params;
use rusqlite::OptionalExtension;
use uuid::Uuid;

use crate::commit_integrity::{compute_commit_integrity_tag, CommitIntegrityInput};
use crate::connection::VaultConnection;
use crate::error::{StorageError, StorageResult};

/// 执行 mutation 所需的上下文：设备身份 + commit 生成。
pub struct CommitContext {
    pub device_id: String,
}

impl CommitContext {
    pub fn new(device_id: String) -> Self {
        Self { device_id }
    }

    /// 获取该设备的下一个 local_seq。
    fn next_local_seq(&self, conn: &VaultConnection) -> StorageResult<u64> {
        let max_seq: Option<u64> = conn
            .inner()
            .query_row(
                "SELECT MAX(local_seq) FROM commits WHERE device_id = ?1",
                params![self.device_id],
                |row| row.get(0),
            )
            .optional()
            .map_err(StorageError::Database)?
            .flatten();

        Ok(max_seq.unwrap_or(0) + 1)
    }

    /// 获取指定对象的当前 head_commit_id。
    fn current_head(
        &self,
        conn: &VaultConnection,
        table: &str,
        id_column: &str,
        object_id: &str,
    ) -> StorageResult<Option<String>> {
        let sql = format!(
            "SELECT head_commit_id FROM {} WHERE {} = ?1",
            table, id_column
        );
        conn.inner()
            .query_row(&sql, params![object_id], |row| row.get(0))
            .optional()
            .map_err(StorageError::Database)
            .map(|r| r.flatten())
    }

    fn current_branch_head(
        &self,
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

    /// 创建一个 commit 记录并更新 device head。
    ///
    /// 返回新的 commit_id。
    pub fn create_commit(
        &self,
        conn: &VaultConnection,
        commit_kind: &str,
        change_scope: &str,
        changed_object_ids: &[String],
        parents: &[String],
    ) -> StorageResult<String> {
        let now = chrono::Utc::now().to_rfc3339();
        let local_seq = self.next_local_seq(conn)?;
        let commit_id = Uuid::new_v4().to_string();
        let vector_clock = format!(r#"{{"{}":{}}}"#, self.device_id, local_seq);
        let resolved_parents: Vec<String> = if parents.is_empty() {
            self.current_branch_head(conn, "main")?
                .into_iter()
                .collect()
        } else {
            parents.to_vec()
        };
        let changed_json = serde_json::to_string(changed_object_ids)
            .map_err(|e| StorageError::SchemaCreation(e.to_string()))?;
        let changed_object_ids_ct = Self::encrypt_history(
            conn,
            &commit_id,
            "changed-object-ids",
            changed_json.as_bytes(),
        )?;
        let integrity_tag = compute_commit_integrity_tag(
            conn.keyring(),
            &CommitIntegrityInput {
                commit_id: &commit_id,
                device_id: &self.device_id,
                local_seq,
                commit_kind,
                change_scope,
                changed_object_ids_ct: &changed_object_ids_ct,
                vector_clock: &vector_clock,
                message_ct: None,
                created_at: &now,
                parents: &resolved_parents,
            },
        )?;

        conn.inner().execute(
            "INSERT INTO commits (commit_id, device_id, local_seq, commit_kind,
             change_scope, changed_object_ids_ct, vector_clock, message_ct,
             created_at, integrity_tag)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, NULL, ?8, ?9)",
            params![
                commit_id,
                self.device_id,
                local_seq,
                commit_kind,
                change_scope,
                changed_object_ids_ct,
                vector_clock,
                now,
                integrity_tag,
            ],
        )?;

        // 写入 parent 关系
        for parent_id in &resolved_parents {
            conn.inner().execute(
                "INSERT INTO commit_parents (commit_id, parent_commit_id) VALUES (?1, ?2)",
                params![commit_id, parent_id],
            )?;
        }

        // 更新设备 head
        conn.inner().execute(
            "INSERT INTO device_heads (device_id, head_commit_id, last_seen_at, revoked)
             VALUES (?1, ?2, ?3, 0)
             ON CONFLICT(device_id) DO UPDATE SET
                head_commit_id = excluded.head_commit_id,
                last_seen_at = excluded.last_seen_at",
            params![self.device_id, commit_id, now],
        )?;

        conn.inner().execute(
            "UPDATE branches SET head_commit_id = ?1, updated_at = ?2 WHERE branch_name = 'main'",
            params![commit_id, now],
        )?;

        Ok(commit_id)
    }

    pub(crate) fn encrypt_history(
        conn: &VaultConnection,
        commit_id: &str,
        field: &str,
        plaintext: &[u8],
    ) -> StorageResult<Vec<u8>> {
        let subkey = conn
            .keyring()
            .map(|kr| kr.history_subkey.clone())
            .unwrap_or_default();
        crate::crypto_layer::encrypt_field(
            conn.keyring(),
            &subkey,
            plaintext,
            "commit",
            commit_id,
            field,
        )
        .map_err(StorageError::Crypto)
    }

    /// 写入 tombstone 记录。
    pub fn create_tombstone(
        &self,
        conn: &VaultConnection,
        target_object_type: &str,
        target_object_id: &str,
    ) -> StorageResult<String> {
        let tombstone_id = Uuid::new_v4().to_string();
        let now = chrono::Utc::now().to_rfc3339();
        let delete_clock = format!(r#"{{"tombstone":"{}"}}"#, tombstone_id);

        conn.inner().execute(
            "INSERT INTO tombstones (tombstone_id, target_object_type, target_object_id,
             delete_clock, deleted_by_device_id, deleted_at, purge_eligible_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, NULL)",
            params![
                tombstone_id,
                target_object_type,
                target_object_id,
                delete_clock,
                self.device_id,
                now,
            ],
        )?;

        Ok(tombstone_id)
    }

    /// 便捷方法：为单个对象的变更创建 commit。
    ///
    /// `object_table` 是目标表名（用于查询当前 head），
    /// `object_id` 是变更对象的 ID。
    pub fn commit_object_change(
        &self,
        conn: &VaultConnection,
        object_table: &str,
        object_id: &str,
        commit_kind: &str,
        change_scope: &str,
    ) -> StorageResult<String> {
        let parent_head = self.current_head(
            conn,
            object_table,
            &format!("{}_id", change_scope),
            object_id,
        )?;
        let parents: Vec<String> = parent_head.into_iter().collect();

        self.create_commit(
            conn,
            commit_kind,
            change_scope,
            &[object_id.to_string()],
            &parents,
        )
    }
}
