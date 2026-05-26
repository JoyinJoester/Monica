use rusqlite::params;
use rusqlite::types::Type;
use rusqlite::OptionalExtension;
use sha2::{Digest, Sha256};
use uuid::Uuid;

use mdbx_core::model::attachment::StorageMode;
use mdbx_core::model::Attachment;

use crate::connection::VaultConnection;
use crate::crypto_layer::{decrypt_field, encrypt_field};
use crate::error::{StorageError, StorageResult};
use crate::repo::commit_ctx::CommitContext;

/// 附件元数据的持久化仓库。
///
/// 附件属于一等结构，支持 project 级和 entry 级归属。
/// 改名只改元数据，不改变 content_hash。
pub struct AttachmentRepo;

impl AttachmentRepo {
    // -----------------------------------------------------------------------
    // CREATE
    // -----------------------------------------------------------------------

    pub fn add(
        conn: &VaultConnection,
        ctx: &CommitContext,
        project_id: &str,
        entry_id: Option<&str>,
        file_name: &str,
        media_type: Option<&str>,
        content_hash: &str,
        original_size: u64,
    ) -> StorageResult<Attachment> {
        let now = chrono::Utc::now().to_rfc3339();
        let attachment_id = Uuid::new_v4().to_string();

        // 验证 project 存在且未删除
        let (p_exists, p_deleted): (bool, bool) = conn
            .inner()
            .query_row(
                "SELECT 1, deleted FROM projects WHERE project_id = ?1",
                params![project_id],
                |row| Ok((true, row.get::<_, i32>(1)? != 0)),
            )
            .optional()
            .map_err(StorageError::Database)?
            .unwrap_or((false, false));

        if !p_exists {
            return Err(StorageError::NotFound(format!(
                "project {} not found",
                project_id
            )));
        }
        if p_deleted {
            return Err(StorageError::ConstraintViolation(format!(
                "project {} is deleted",
                project_id
            )));
        }

        // 验证 entry（如果指定）存在且未删除
        if let Some(eid) = entry_id {
            let (e_exists, e_deleted, e_project): (bool, bool, String) = conn
                .inner()
                .query_row(
                    "SELECT 1, deleted, project_id FROM entries WHERE entry_id = ?1",
                    params![eid],
                    |row| Ok((true, row.get::<_, i32>(1)? != 0, row.get(2)?)),
                )
                .optional()
                .map_err(StorageError::Database)?
                .unwrap_or((false, false, String::new()));

            if !e_exists {
                return Err(StorageError::NotFound(format!("entry {} not found", eid)));
            }
            if e_deleted {
                return Err(StorageError::ConstraintViolation(format!(
                    "entry {} is deleted",
                    eid
                )));
            }
            if e_project != project_id {
                return Err(StorageError::ConstraintViolation(format!(
                    "entry {} does not belong to project {}",
                    eid, project_id
                )));
            }
        }

        let commit_id =
            ctx.create_commit(conn, "change", "attachment", &[attachment_id.clone()], &[])?;

        let storage_mode = "embedded-inline";

        let file_name_ct = Self::encrypt_attachment_field(
            conn,
            &attachment_id,
            "file_name",
            file_name.as_bytes(),
        )?;
        let media_type_ct = media_type
            .map(|m| {
                Self::encrypt_attachment_field(conn, &attachment_id, "media_type", m.as_bytes())
            })
            .transpose()?;

        conn.inner().execute(
            "INSERT INTO attachments (attachment_id, project_id, entry_id,
             file_name_ct, media_type_ct, storage_mode, content_hash,
             original_size, stored_size, chunk_count, head_commit_id,
             deleted, created_at, updated_at, created_by_device_id, updated_by_device_id)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?8, 0, ?9, 0, ?10, ?10, ?11, ?11)",
            params![
                attachment_id,
                project_id,
                entry_id,
                file_name_ct,
                media_type_ct,
                storage_mode,
                content_hash,
                original_size,
                commit_id,
                now,
                ctx.device_id,
            ],
        )?;

        AttachmentRepo::get_by_id(conn, &attachment_id)?
            .ok_or_else(|| StorageError::NotFound(attachment_id))
    }

    // -----------------------------------------------------------------------
    // READ
    // -----------------------------------------------------------------------

    pub fn get_by_id(
        conn: &VaultConnection,
        attachment_id: &str,
    ) -> StorageResult<Option<Attachment>> {
        conn.inner()
            .query_row(
                "SELECT attachment_id, project_id, entry_id, file_name_ct,
                        media_type_ct, storage_mode, content_hash,
                        original_size, stored_size, chunk_count, head_commit_id,
                        deleted, created_at, updated_at,
                        created_by_device_id, updated_by_device_id
                 FROM attachments WHERE attachment_id = ?1",
                params![attachment_id],
                |row| {
                    let aid: String = row.get(0)?;
                    let raw_file_name: Vec<u8> = row.get(3)?;
                    let raw_media_type: Option<Vec<u8>> = row.get(4)?;
                    Ok(Attachment {
                        attachment_id: aid.clone(),
                        project_id: row.get(1)?,
                        entry_id: row.get(2)?,
                        file_name_ct: Self::decrypt_attachment_field(
                            conn,
                            &aid,
                            "file_name",
                            &raw_file_name,
                        )
                        .map_err(|e| {
                            rusqlite::Error::FromSqlConversionFailure(3, Type::Blob, Box::new(e))
                        })?,
                        media_type_ct: raw_media_type
                            .map(|m| {
                                Self::decrypt_attachment_field(conn, &aid, "media_type", &m)
                                    .map_err(|e| {
                                        rusqlite::Error::FromSqlConversionFailure(
                                            4,
                                            Type::Blob,
                                            Box::new(e),
                                        )
                                    })
                            })
                            .transpose()?,
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
                },
            )
            .optional()
            .map_err(StorageError::Database)
    }

    pub fn list_by_project(
        conn: &VaultConnection,
        project_id: &str,
    ) -> StorageResult<Vec<Attachment>> {
        AttachmentRepo::list_where(conn, "deleted = 0 AND project_id = ?1", params![project_id])
    }

    pub fn list_by_entry(conn: &VaultConnection, entry_id: &str) -> StorageResult<Vec<Attachment>> {
        AttachmentRepo::list_where(conn, "deleted = 0 AND entry_id = ?1", params![entry_id])
    }

    pub fn list_deleted(conn: &VaultConnection) -> StorageResult<Vec<Attachment>> {
        AttachmentRepo::list_where(conn, "deleted = 1", [])
    }

    fn list_where(
        conn: &VaultConnection,
        where_clause: &str,
        params: impl rusqlite::Params,
    ) -> StorageResult<Vec<Attachment>> {
        let sql = format!(
            "SELECT attachment_id, project_id, entry_id, file_name_ct,
                    media_type_ct, storage_mode, content_hash,
                    original_size, stored_size, chunk_count, head_commit_id,
                    deleted, created_at, updated_at,
                    created_by_device_id, updated_by_device_id
             FROM attachments WHERE {} ORDER BY updated_at DESC",
            where_clause
        );

        let mut stmt = conn.inner().prepare(&sql)?;
        let rows = stmt.query_map(params, |row| {
            let aid: String = row.get(0)?;
            let raw_file_name: Vec<u8> = row.get(3)?;
            let raw_media_type: Option<Vec<u8>> = row.get(4)?;
            Ok(Attachment {
                attachment_id: aid.clone(),
                project_id: row.get(1)?,
                entry_id: row.get(2)?,
                file_name_ct: Self::decrypt_attachment_field(
                    conn,
                    &aid,
                    "file_name",
                    &raw_file_name,
                )
                .map_err(|e| {
                    rusqlite::Error::FromSqlConversionFailure(3, Type::Blob, Box::new(e))
                })?,
                media_type_ct: raw_media_type
                    .map(|m| {
                        Self::decrypt_attachment_field(conn, &aid, "media_type", &m).map_err(|e| {
                            rusqlite::Error::FromSqlConversionFailure(4, Type::Blob, Box::new(e))
                        })
                    })
                    .transpose()?,
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

    // -----------------------------------------------------------------------
    // RENAME — 只改元数据，不触碰内容
    // -----------------------------------------------------------------------

    pub fn rename(
        conn: &VaultConnection,
        ctx: &CommitContext,
        attachment_id: &str,
        new_file_name: &str,
        new_media_type: Option<&str>,
    ) -> StorageResult<Attachment> {
        let att = AttachmentRepo::get_by_id(conn, attachment_id)?
            .ok_or_else(|| StorageError::NotFound(attachment_id.to_string()))?;

        if att.deleted {
            return Err(StorageError::ConstraintViolation(
                "attachment is deleted".to_string(),
            ));
        }

        let now = chrono::Utc::now().to_rfc3339();

        let commit_id =
            ctx.commit_object_change(conn, "attachments", attachment_id, "change", "attachment")?;

        // content_hash 不变！
        let file_name_ct = Self::encrypt_attachment_field(
            conn,
            attachment_id,
            "file_name",
            new_file_name.as_bytes(),
        )?;
        let media_type_ct = new_media_type
            .map(|m| {
                Self::encrypt_attachment_field(conn, attachment_id, "media_type", m.as_bytes())
            })
            .transpose()?;

        conn.inner().execute(
            "UPDATE attachments SET
                file_name_ct = ?2, media_type_ct = ?3,
                head_commit_id = ?4,
                updated_at = ?5, updated_by_device_id = ?6
             WHERE attachment_id = ?1",
            params![
                attachment_id,
                file_name_ct,
                media_type_ct,
                commit_id,
                now,
                ctx.device_id,
            ],
        )?;

        AttachmentRepo::get_by_id(conn, attachment_id)?
            .ok_or_else(|| StorageError::NotFound(attachment_id.to_string()))
    }

    // -----------------------------------------------------------------------
    // SOFT DELETE
    // -----------------------------------------------------------------------

    pub fn soft_delete(
        conn: &VaultConnection,
        ctx: &CommitContext,
        attachment_id: &str,
    ) -> StorageResult<()> {
        let att = AttachmentRepo::get_by_id(conn, attachment_id)?
            .ok_or_else(|| StorageError::NotFound(attachment_id.to_string()))?;

        if att.deleted {
            return Err(StorageError::ConstraintViolation(
                "attachment is already deleted".to_string(),
            ));
        }

        let now = chrono::Utc::now().to_rfc3339();

        ctx.create_tombstone(conn, "attachment", attachment_id)?;

        let commit_id =
            ctx.commit_object_change(conn, "attachments", attachment_id, "change", "attachment")?;

        conn.inner().execute(
            "UPDATE attachments SET deleted = 1,
             head_commit_id = ?2, updated_at = ?3, updated_by_device_id = ?4
             WHERE attachment_id = ?1",
            params![attachment_id, commit_id, now, ctx.device_id],
        )?;

        Ok(())
    }

    // -----------------------------------------------------------------------
    // 更新 attachment_count（供 ProjectRepo 内部使用）
    // -----------------------------------------------------------------------

    pub fn count_for_project(conn: &VaultConnection, project_id: &str) -> StorageResult<u32> {
        let count: i32 = conn
            .inner()
            .query_row(
                "SELECT COUNT(*) FROM attachments WHERE project_id = ?1 AND deleted = 0",
                params![project_id],
                |row| row.get(0),
            )
            .map_err(StorageError::Database)?;
        Ok(count as u32)
    }

    // -----------------------------------------------------------------------
    // INLINE CONTENT — 小附件内嵌模式
    // -----------------------------------------------------------------------

    /// 将二进制内容写入 attachment_chunks（chunk_index=0）。
    ///
    /// 自动计算 content_hash（SHA-256），更新 attachments 表的 stored_size 和
    /// content_hash。如果已有 chunk 数据则覆盖。
    pub fn write_inline_content(
        conn: &VaultConnection,
        ctx: &CommitContext,
        attachment_id: &str,
        data: &[u8],
    ) -> StorageResult<String> {
        let att = AttachmentRepo::get_by_id(conn, attachment_id)?
            .ok_or_else(|| StorageError::NotFound(attachment_id.to_string()))?;

        if att.deleted {
            return Err(StorageError::ConstraintViolation(
                "attachment is deleted".to_string(),
            ));
        }

        let content_hash = compute_sha256_hex(data);
        let now = chrono::Utc::now().to_rfc3339();

        let chunk_ct = Self::encrypt_attachment_field(conn, attachment_id, "chunk", data)?;

        let commit_id =
            ctx.commit_object_change(conn, "attachments", attachment_id, "change", "attachment")?;

        // upsert chunk
        conn.inner().execute(
            "INSERT INTO attachment_chunks (attachment_id, chunk_index, chunk_hash,
             chunk_ct, stored_size, created_at)
             VALUES (?1, 0, ?2, ?3, ?4, ?5)
             ON CONFLICT(attachment_id, chunk_index) DO UPDATE SET
                chunk_hash = excluded.chunk_hash,
                chunk_ct = excluded.chunk_ct,
                stored_size = excluded.stored_size",
            params![
                attachment_id,
                content_hash,
                chunk_ct,
                data.len() as i64,
                now,
            ],
        )?;

        // 更新 attachments 元数据
        conn.inner().execute(
            "UPDATE attachments SET
                content_hash = ?2, stored_size = ?3, chunk_count = 1,
                storage_mode = 'embedded-inline',
                head_commit_id = ?4, updated_at = ?5, updated_by_device_id = ?6
             WHERE attachment_id = ?1",
            params![
                attachment_id,
                content_hash,
                data.len() as i64,
                commit_id,
                now,
                ctx.device_id,
            ],
        )?;

        Ok(content_hash)
    }

    /// 读取附件内容并验证完整性。
    ///
    /// 从 attachment_chunks 读取 chunk_index=0 的内容，计算 hash 并与
    /// attachments.content_hash 对比。不匹配则返回错误。
    pub fn read_content(conn: &VaultConnection, attachment_id: &str) -> StorageResult<Vec<u8>> {
        let att = AttachmentRepo::get_by_id(conn, attachment_id)?
            .ok_or_else(|| StorageError::NotFound(attachment_id.to_string()))?;

        if att.deleted {
            return Err(StorageError::ConstraintViolation(
                "attachment is deleted".to_string(),
            ));
        }

        if att.chunk_count == 0 {
            return Ok(Vec::new());
        }

        let data = read_all_chunks(conn, attachment_id, att.chunk_count)?;

        // 整体完整性校验
        let computed = compute_sha256_hex(&data);
        if computed != att.content_hash {
            return Err(StorageError::ConstraintViolation(format!(
                "content hash mismatch: expected {}, got {}",
                att.content_hash, computed
            )));
        }

        Ok(data)
    }

    /// 校验附件完整性，不返回内容。
    pub fn verify_integrity(conn: &VaultConnection, attachment_id: &str) -> StorageResult<bool> {
        let att = AttachmentRepo::get_by_id(conn, attachment_id)?
            .ok_or_else(|| StorageError::NotFound(attachment_id.to_string()))?;

        if att.chunk_count == 0 {
            return Ok(true);
        }

        let data = match read_all_chunks(conn, attachment_id, att.chunk_count) {
            Ok(d) => d,
            Err(_) => return Ok(false),
        };

        Ok(compute_sha256_hex(&data) == att.content_hash)
    }

    // -----------------------------------------------------------------------
    // CHUNKED CONTENT — 大附件分块模式
    // -----------------------------------------------------------------------

    /// 将大文件按 chunk_size 分块写入 attachment_chunks。
    ///
    /// chunk_index 从 0 开始连续递增，每个 chunk 有独立的 chunk_hash。
    /// 整体 content_hash 是所有数据拼接后的 SHA-256。
    /// storage_mode 设为 `embedded-chunked`。
    pub fn write_chunked_content(
        conn: &VaultConnection,
        ctx: &CommitContext,
        attachment_id: &str,
        data: &[u8],
        chunk_size: usize,
    ) -> StorageResult<String> {
        let att = AttachmentRepo::get_by_id(conn, attachment_id)?
            .ok_or_else(|| StorageError::NotFound(attachment_id.to_string()))?;

        if att.deleted {
            return Err(StorageError::ConstraintViolation(
                "attachment is deleted".to_string(),
            ));
        }

        assert!(chunk_size > 0, "chunk_size must be > 0");

        let content_hash = compute_sha256_hex(data);
        let now = chrono::Utc::now().to_rfc3339();
        let chunks: Vec<&[u8]> = data.chunks(chunk_size).collect();
        let chunk_count = chunks.len() as u32;

        let commit_id =
            ctx.commit_object_change(conn, "attachments", attachment_id, "change", "attachment")?;

        // 清除旧 chunk 数据
        conn.inner().execute(
            "DELETE FROM attachment_chunks WHERE attachment_id = ?1",
            params![attachment_id],
        )?;

        // 逐 chunk 写入
        for (i, chunk) in chunks.iter().enumerate() {
            let chunk_hash = compute_sha256_hex(chunk);
            let chunk_ct = Self::encrypt_attachment_field(conn, attachment_id, "chunk", chunk)?;
            conn.inner().execute(
                "INSERT INTO attachment_chunks (attachment_id, chunk_index, chunk_hash,
                 chunk_ct, stored_size, created_at)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
                params![
                    attachment_id,
                    i as i64,
                    chunk_hash,
                    chunk_ct,
                    chunk.len() as i64,
                    now,
                ],
            )?;
        }

        // 更新 attachments 元数据
        conn.inner().execute(
            "UPDATE attachments SET
                content_hash = ?2, stored_size = ?3, chunk_count = ?4,
                storage_mode = 'embedded-chunked',
                head_commit_id = ?5, updated_at = ?6, updated_by_device_id = ?7
             WHERE attachment_id = ?1",
            params![
                attachment_id,
                content_hash,
                data.len() as i64,
                chunk_count,
                commit_id,
                now,
                ctx.device_id,
            ],
        )?;

        Ok(content_hash)
    }

    /// 校验每个 chunk 的独立 hash。
    pub fn verify_chunks_integrity(
        conn: &VaultConnection,
        attachment_id: &str,
    ) -> StorageResult<bool> {
        let att = AttachmentRepo::get_by_id(conn, attachment_id)?
            .ok_or_else(|| StorageError::NotFound(attachment_id.to_string()))?;

        if att.chunk_count == 0 {
            return Ok(true);
        }

        let mut stmt = conn.inner().prepare(
            "SELECT chunk_index, chunk_hash, chunk_ct
             FROM attachment_chunks
             WHERE attachment_id = ?1
             ORDER BY chunk_index",
        )?;

        let rows = stmt.query_map(params![attachment_id], |row| {
            Ok((
                row.get::<_, i64>(0)?,
                row.get::<_, String>(1)?,
                row.get::<_, Vec<u8>>(2)?,
            ))
        })?;

        for row in rows {
            let (_index, expected_hash, chunk_data): (i64, String, Vec<u8>) = row?;
            let plaintext =
                match Self::decrypt_attachment_field(conn, attachment_id, "chunk", &chunk_data) {
                    Ok(plaintext) => plaintext,
                    Err(_) => return Ok(false),
                };
            let computed = compute_sha256_hex(&plaintext);
            if computed != expected_hash {
                return Ok(false);
            }
        }

        Ok(true)
    }

    // -----------------------------------------------------------------------
    // ENCRYPTION HELPERS
    // -----------------------------------------------------------------------

    fn encrypt_attachment_field(
        conn: &VaultConnection,
        id: &str,
        field: &str,
        plaintext: &[u8],
    ) -> StorageResult<Vec<u8>> {
        let subkey = conn
            .keyring()
            .map(|kr| kr.attachment_subkey.clone())
            .unwrap_or_default();
        encrypt_field(conn.keyring(), &subkey, plaintext, "attachment", id, field)
            .map_err(StorageError::Crypto)
    }

    fn decrypt_attachment_field(
        conn: &VaultConnection,
        id: &str,
        field: &str,
        ciphertext: &[u8],
    ) -> StorageResult<Vec<u8>> {
        let subkey = conn
            .keyring()
            .map(|kr| kr.attachment_subkey.clone())
            .unwrap_or_default();
        decrypt_field(conn.keyring(), &subkey, ciphertext, "attachment", id, field)
            .map_err(StorageError::Crypto)
    }
}

/// 按 chunk_index 顺序读取所有 chunk 并拼接，解密每个 chunk。
fn read_all_chunks(
    conn: &VaultConnection,
    attachment_id: &str,
    chunk_count: u32,
) -> StorageResult<Vec<u8>> {
    let mut stmt = conn.inner().prepare(
        "SELECT chunk_ct FROM attachment_chunks
         WHERE attachment_id = ?1
         ORDER BY chunk_index",
    )?;

    let rows = stmt.query_map(params![attachment_id], |row| row.get::<_, Vec<u8>>(0))?;

    let mut data = Vec::new();
    for row in rows {
        let encrypted = row?;
        let plaintext =
            AttachmentRepo::decrypt_attachment_field(conn, attachment_id, "chunk", &encrypted)?;
        data.extend_from_slice(&plaintext);
    }

    // 验证 chunk 数量与声明的匹配
    if data.is_empty() && chunk_count > 0 {
        return Err(StorageError::ConstraintViolation(
            "attachment has chunk_count > 0 but no chunk data found".to_string(),
        ));
    }

    Ok(data)
}

/// 计算 SHA-256 并返回 hex 字符串。
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
    use crate::repo::entry::EntryRepo;
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
        serde_json::json!({"username": "alice", "password": "secret"})
    }

    // -----------------------------------------------------------------------
    // CREATE
    // -----------------------------------------------------------------------

    #[test]
    fn test_add_attachment_to_project() {
        let (conn, ctx, project_id) = setup();
        let att = AttachmentRepo::add(
            &conn,
            &ctx,
            &project_id,
            None,
            "screenshot.png",
            Some("image/png"),
            "abc123hash",
            1024,
        )
        .unwrap();

        assert!(!att.attachment_id.is_empty());
        assert_eq!(att.project_id, project_id);
        assert_eq!(att.entry_id, None);
        assert_eq!(att.file_name_ct, b"screenshot.png");
        assert_eq!(att.media_type_ct, Some(b"image/png".to_vec()));
        assert_eq!(att.content_hash, "abc123hash");
        assert_eq!(att.original_size, 1024);
        assert_eq!(att.stored_size, 1024);
        assert_eq!(att.storage_mode, StorageMode::EmbeddedInline);
        assert!(!att.deleted);
        assert!(!att.head_commit_id.is_empty());
    }

    #[test]
    fn test_add_attachment_to_entry() {
        let (conn, ctx, project_id) = setup();
        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            mdbx_core::model::EntryType::Login,
            Some("My Login"),
            &login_payload(),
        )
        .unwrap();

        let att = AttachmentRepo::add(
            &conn,
            &ctx,
            &project_id,
            Some(&entry.entry_id),
            "avatar.jpg",
            Some("image/jpeg"),
            "hash999",
            2048,
        )
        .unwrap();

        assert_eq!(att.project_id, project_id);
        assert_eq!(att.entry_id, Some(entry.entry_id));
    }

    #[test]
    fn test_add_to_nonexistent_project() {
        let (conn, ctx, _project_id) = setup();
        let result = AttachmentRepo::add(
            &conn,
            &ctx,
            "nonexistent",
            None,
            "file.txt",
            None,
            "hash",
            100,
        );
        assert!(result.is_err());
    }

    #[test]
    fn test_add_to_deleted_project() {
        let (conn, ctx, project_id) = setup();
        ProjectRepo::soft_delete(&conn, &ctx, &project_id).unwrap();
        let result = AttachmentRepo::add(
            &conn,
            &ctx,
            &project_id,
            None,
            "file.txt",
            None,
            "hash",
            100,
        );
        assert!(result.is_err());
    }

    #[test]
    fn test_add_to_nonexistent_entry() {
        let (conn, ctx, project_id) = setup();
        let result = AttachmentRepo::add(
            &conn,
            &ctx,
            &project_id,
            Some("nonexistent"),
            "file.txt",
            None,
            "hash",
            100,
        );
        assert!(result.is_err());
    }

    #[test]
    fn test_add_entry_attachment_wrong_project() {
        let (conn, ctx, _project_id) = setup();
        let project2 = ProjectRepo::create(&conn, &ctx, "Project 2", None, None).unwrap();
        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project2.project_id,
            mdbx_core::model::EntryType::Note,
            None,
            &serde_json::json!({"text":"hi"}),
        )
        .unwrap();

        // 尝试把 entry 的附件挂到错误的 project
        // 先创建另一个 project
        let project3 = ProjectRepo::create(&conn, &ctx, "Project 3", None, None).unwrap();
        let result = AttachmentRepo::add(
            &conn,
            &ctx,
            &project3.project_id,
            Some(&entry.entry_id),
            "file.txt",
            None,
            "hash",
            100,
        );
        assert!(result.is_err());
    }

    // -----------------------------------------------------------------------
    // READ
    // -----------------------------------------------------------------------

    #[test]
    fn test_get_by_id() {
        let (conn, ctx, project_id) = setup();
        let created = AttachmentRepo::add(
            &conn,
            &ctx,
            &project_id,
            None,
            "doc.pdf",
            Some("application/pdf"),
            "hash123",
            4096,
        )
        .unwrap();

        let found = AttachmentRepo::get_by_id(&conn, &created.attachment_id)
            .unwrap()
            .unwrap();
        assert_eq!(found.attachment_id, created.attachment_id);
        assert_eq!(found.content_hash, "hash123");
    }

    #[test]
    fn test_list_by_project() {
        let (conn, ctx, project_id) = setup();
        AttachmentRepo::add(&conn, &ctx, &project_id, None, "a.txt", None, "h1", 10).unwrap();
        AttachmentRepo::add(&conn, &ctx, &project_id, None, "b.txt", None, "h2", 20).unwrap();

        let all = AttachmentRepo::list_by_project(&conn, &project_id).unwrap();
        assert_eq!(all.len(), 2);
    }

    #[test]
    fn test_list_by_entry() {
        let (conn, ctx, project_id) = setup();
        let e1 = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            mdbx_core::model::EntryType::Login,
            Some("E1"),
            &login_payload(),
        )
        .unwrap();
        let e2 = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            mdbx_core::model::EntryType::Note,
            Some("E2"),
            &serde_json::json!({"text":"hi"}),
        )
        .unwrap();

        AttachmentRepo::add(
            &conn,
            &ctx,
            &project_id,
            Some(&e1.entry_id),
            "f1.txt",
            None,
            "h1",
            10,
        )
        .unwrap();
        AttachmentRepo::add(
            &conn,
            &ctx,
            &project_id,
            Some(&e2.entry_id),
            "f2.txt",
            None,
            "h2",
            10,
        )
        .unwrap();
        AttachmentRepo::add(
            &conn,
            &ctx,
            &project_id,
            Some(&e1.entry_id),
            "f3.txt",
            None,
            "h3",
            10,
        )
        .unwrap();

        assert_eq!(
            AttachmentRepo::list_by_entry(&conn, &e1.entry_id)
                .unwrap()
                .len(),
            2
        );
        assert_eq!(
            AttachmentRepo::list_by_entry(&conn, &e2.entry_id)
                .unwrap()
                .len(),
            1
        );
    }

    // -----------------------------------------------------------------------
    // RENAME
    // -----------------------------------------------------------------------

    #[test]
    fn test_rename_preserves_content_hash() {
        let (conn, ctx, project_id) = setup();
        let att = AttachmentRepo::add(
            &conn,
            &ctx,
            &project_id,
            None,
            "old_name.txt",
            Some("text/plain"),
            "original-hash",
            500,
        )
        .unwrap();

        let renamed = AttachmentRepo::rename(
            &conn,
            &ctx,
            &att.attachment_id,
            "new_name.txt",
            Some("text/plain"),
        )
        .unwrap();

        assert_eq!(renamed.file_name_ct, b"new_name.txt");
        // content_hash 不变
        assert_eq!(renamed.content_hash, "original-hash");
        // head_commit_id 变化
        assert_ne!(renamed.head_commit_id, att.head_commit_id);
    }

    #[test]
    fn test_rename_generates_commit() {
        let (conn, ctx, project_id) = setup();
        let att = AttachmentRepo::add(&conn, &ctx, &project_id, None, "old.txt", None, "hash", 100)
            .unwrap();
        let old_commit = att.head_commit_id.clone();

        let renamed =
            AttachmentRepo::rename(&conn, &ctx, &att.attachment_id, "new.txt", None).unwrap();

        let parent: String = conn
            .inner()
            .query_row(
                "SELECT parent_commit_id FROM commit_parents WHERE commit_id = ?1",
                params![renamed.head_commit_id],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(parent, old_commit);
    }

    // -----------------------------------------------------------------------
    // SOFT DELETE
    // -----------------------------------------------------------------------

    #[test]
    fn test_soft_delete() {
        let (conn, ctx, project_id) = setup();
        let att = AttachmentRepo::add(
            &conn,
            &ctx,
            &project_id,
            None,
            "delete_me.txt",
            None,
            "hash",
            10,
        )
        .unwrap();

        AttachmentRepo::soft_delete(&conn, &ctx, &att.attachment_id).unwrap();

        let deleted = AttachmentRepo::get_by_id(&conn, &att.attachment_id)
            .unwrap()
            .unwrap();
        assert!(deleted.deleted);

        // 被删除的不在活跃列表中
        assert!(AttachmentRepo::list_by_project(&conn, &project_id)
            .unwrap()
            .is_empty());
        assert_eq!(AttachmentRepo::list_deleted(&conn).unwrap().len(), 1);
    }

    #[test]
    fn test_double_delete_rejected() {
        let (conn, ctx, project_id) = setup();
        let att = AttachmentRepo::add(&conn, &ctx, &project_id, None, "once.txt", None, "hash", 10)
            .unwrap();

        AttachmentRepo::soft_delete(&conn, &ctx, &att.attachment_id).unwrap();
        assert!(AttachmentRepo::soft_delete(&conn, &ctx, &att.attachment_id).is_err());
    }

    #[test]
    fn test_rename_deleted_rejected() {
        let (conn, ctx, project_id) = setup();
        let att = AttachmentRepo::add(&conn, &ctx, &project_id, None, "gone.txt", None, "hash", 10)
            .unwrap();

        AttachmentRepo::soft_delete(&conn, &ctx, &att.attachment_id).unwrap();
        assert!(AttachmentRepo::rename(&conn, &ctx, &att.attachment_id, "nope.txt", None).is_err());
    }

    // -----------------------------------------------------------------------
    // COUNT
    // -----------------------------------------------------------------------

    #[test]
    fn test_count_for_project() {
        let (conn, ctx, project_id) = setup();
        assert_eq!(
            AttachmentRepo::count_for_project(&conn, &project_id).unwrap(),
            0
        );

        AttachmentRepo::add(&conn, &ctx, &project_id, None, "a.txt", None, "h1", 10).unwrap();
        AttachmentRepo::add(&conn, &ctx, &project_id, None, "b.txt", None, "h2", 20).unwrap();
        assert_eq!(
            AttachmentRepo::count_for_project(&conn, &project_id).unwrap(),
            2
        );

        // 删除一个
        let att =
            AttachmentRepo::add(&conn, &ctx, &project_id, None, "c.txt", None, "h3", 30).unwrap();
        AttachmentRepo::soft_delete(&conn, &ctx, &att.attachment_id).unwrap();
        assert_eq!(
            AttachmentRepo::count_for_project(&conn, &project_id).unwrap(),
            2
        );
    }

    // -----------------------------------------------------------------------
    // INLINE CONTENT
    // -----------------------------------------------------------------------

    #[test]
    fn test_write_and_read_inline_content() {
        let (conn, ctx, project_id) = setup();
        let att = AttachmentRepo::add(
            &conn,
            &ctx,
            &project_id,
            None,
            "data.bin",
            Some("application/octet-stream"),
            "",
            0, // hash 和 size 由 write_inline_content 更新
        )
        .unwrap();

        let content = b"Hello, MDBX inline attachment!";
        let hash =
            AttachmentRepo::write_inline_content(&conn, &ctx, &att.attachment_id, content).unwrap();

        // hash 应该是 SHA-256 hex
        assert_eq!(hash.len(), 64);

        // 读回内容
        let data = AttachmentRepo::read_content(&conn, &att.attachment_id).unwrap();
        assert_eq!(data, content);

        // metadata 已更新
        let refreshed = AttachmentRepo::get_by_id(&conn, &att.attachment_id)
            .unwrap()
            .unwrap();
        assert_eq!(refreshed.content_hash, hash);
        assert_eq!(refreshed.stored_size, content.len() as u64);
        assert_eq!(refreshed.chunk_count, 1);
        assert_eq!(refreshed.storage_mode, StorageMode::EmbeddedInline);
    }

    #[test]
    fn test_write_inline_updates_content_hash() {
        let (conn, ctx, project_id) = setup();
        let att =
            AttachmentRepo::add(&conn, &ctx, &project_id, None, "file.bin", None, "", 0).unwrap();

        let content_v1 = b"version 1";
        let hash_v1 =
            AttachmentRepo::write_inline_content(&conn, &ctx, &att.attachment_id, content_v1)
                .unwrap();

        let content_v2 = b"version 2 - different content";
        let hash_v2 =
            AttachmentRepo::write_inline_content(&conn, &ctx, &att.attachment_id, content_v2)
                .unwrap();

        assert_ne!(hash_v1, hash_v2);
        assert_eq!(
            AttachmentRepo::read_content(&conn, &att.attachment_id).unwrap(),
            content_v2
        );
    }

    #[test]
    fn test_integrity_verification_passes() {
        let (conn, ctx, project_id) = setup();
        let att =
            AttachmentRepo::add(&conn, &ctx, &project_id, None, "ok.bin", None, "", 0).unwrap();
        AttachmentRepo::write_inline_content(&conn, &ctx, &att.attachment_id, b"clean data")
            .unwrap();

        assert!(AttachmentRepo::verify_integrity(&conn, &att.attachment_id).unwrap());
    }

    #[test]
    fn test_integrity_verification_fails_on_tamper() {
        let (conn, ctx, project_id) = setup();
        let att =
            AttachmentRepo::add(&conn, &ctx, &project_id, None, "tamper.bin", None, "", 0).unwrap();
        AttachmentRepo::write_inline_content(&conn, &ctx, &att.attachment_id, b"original data")
            .unwrap();

        // 直接在数据库层面篡改 chunk 内容
        conn.inner()
            .execute(
                "UPDATE attachment_chunks SET chunk_ct = ?1
             WHERE attachment_id = ?2 AND chunk_index = 0",
                params![b"tampered!!", att.attachment_id],
            )
            .unwrap();

        // 验证应失败
        assert!(!AttachmentRepo::verify_integrity(&conn, &att.attachment_id).unwrap());

        // 读取应报错
        assert!(AttachmentRepo::read_content(&conn, &att.attachment_id).is_err());
    }

    #[test]
    fn test_hash_mismatch_on_content_hash_tamper() {
        let (conn, ctx, project_id) = setup();
        let att = AttachmentRepo::add(
            &conn,
            &ctx,
            &project_id,
            None,
            "hash-tamper.bin",
            None,
            "",
            0,
        )
        .unwrap();
        AttachmentRepo::write_inline_content(&conn, &ctx, &att.attachment_id, b"real data")
            .unwrap();

        // 篡改 attachments 表中的 content_hash
        conn.inner().execute(
            "UPDATE attachments SET content_hash = '0000000000000000000000000000000000000000000000000000000000000000'
             WHERE attachment_id = ?1",
            params![att.attachment_id],
        ).unwrap();

        assert!(!AttachmentRepo::verify_integrity(&conn, &att.attachment_id).unwrap());
        assert!(AttachmentRepo::read_content(&conn, &att.attachment_id).is_err());
    }

    #[test]
    fn test_read_empty_attachment() {
        let (conn, ctx, project_id) = setup();
        let att = AttachmentRepo::add(
            &conn,
            &ctx,
            &project_id,
            None,
            "empty.bin",
            None,
            "unused",
            0,
        )
        .unwrap();

        // 没有写入内容的附件返回空
        let data = AttachmentRepo::read_content(&conn, &att.attachment_id).unwrap();
        assert!(data.is_empty());
    }

    #[test]
    fn test_write_to_deleted_attachment_rejected() {
        let (conn, ctx, project_id) = setup();
        let att =
            AttachmentRepo::add(&conn, &ctx, &project_id, None, "del.bin", None, "", 0).unwrap();
        AttachmentRepo::soft_delete(&conn, &ctx, &att.attachment_id).unwrap();

        assert!(
            AttachmentRepo::write_inline_content(&conn, &ctx, &att.attachment_id, b"data",)
                .is_err()
        );
    }

    #[test]
    fn test_compute_sha256_hex() {
        let hash = compute_sha256_hex(b"hello");
        // SHA-256("hello") = 2cf24dba...
        assert_eq!(
            hash,
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        );
    }

    // -----------------------------------------------------------------------
    // CHUNKED CONTENT
    // -----------------------------------------------------------------------

    #[test]
    fn test_write_and_read_chunked_content() {
        let (conn, ctx, project_id) = setup();
        let att = AttachmentRepo::add(
            &conn,
            &ctx,
            &project_id,
            None,
            "bigfile.bin",
            Some("application/octet-stream"),
            "",
            0,
        )
        .unwrap();

        // 写入 1KB 分块（每块 256 字节）
        let data: Vec<u8> = (0..1024).map(|i| (i % 256) as u8).collect();
        let hash =
            AttachmentRepo::write_chunked_content(&conn, &ctx, &att.attachment_id, &data, 256)
                .unwrap();

        assert_eq!(hash.len(), 64);

        let read = AttachmentRepo::read_content(&conn, &att.attachment_id).unwrap();
        assert_eq!(read, data);

        let refreshed = AttachmentRepo::get_by_id(&conn, &att.attachment_id)
            .unwrap()
            .unwrap();
        assert_eq!(refreshed.content_hash, hash);
        assert_eq!(refreshed.stored_size, 1024);
        assert_eq!(refreshed.chunk_count, 4);
        assert_eq!(refreshed.storage_mode, StorageMode::EmbeddedChunked);
    }

    #[test]
    fn test_chunks_have_sequential_indices() {
        let (conn, ctx, project_id) = setup();
        let att =
            AttachmentRepo::add(&conn, &ctx, &project_id, None, "seq.bin", None, "", 0).unwrap();

        let data = b"0123456789".repeat(10); // 100 bytes
        AttachmentRepo::write_chunked_content(&conn, &ctx, &att.attachment_id, &data, 30).unwrap();

        // 验证 chunk_index 0,1,2,3 连续
        let mut stmt = conn
            .inner()
            .prepare(
                "SELECT chunk_index FROM attachment_chunks
             WHERE attachment_id = ?1 ORDER BY chunk_index",
            )
            .unwrap();
        let indices: Vec<i64> = stmt
            .query_map(params![att.attachment_id], |row| row.get(0))
            .unwrap()
            .map(|r| r.unwrap())
            .collect();

        assert_eq!(indices, vec![0, 1, 2, 3]);
    }

    #[test]
    fn test_per_chunk_hash_verification() {
        let (conn, ctx, project_id) = setup();
        let att = AttachmentRepo::add(&conn, &ctx, &project_id, None, "perchunk.bin", None, "", 0)
            .unwrap();

        let data = b"AAAA".repeat(25); // 100 bytes
        AttachmentRepo::write_chunked_content(&conn, &ctx, &att.attachment_id, &data, 25).unwrap();

        assert!(AttachmentRepo::verify_chunks_integrity(&conn, &att.attachment_id).unwrap());
    }

    #[test]
    fn test_chunk_integrity_detects_corruption() {
        let (conn, ctx, project_id) = setup();
        let att = AttachmentRepo::add(&conn, &ctx, &project_id, None, "corrupt.bin", None, "", 0)
            .unwrap();

        let data = b"clean data for chunking test".repeat(10);
        AttachmentRepo::write_chunked_content(&conn, &ctx, &att.attachment_id, &data, 50).unwrap();

        // 篡改第二个 chunk
        conn.inner()
            .execute(
                "UPDATE attachment_chunks SET chunk_ct = ?1
             WHERE attachment_id = ?2 AND chunk_index = 1",
                params![b"TAMPERED_DATA_HERE!!!!!!!!!!!!!!!!!!!!", att.attachment_id],
            )
            .unwrap();

        assert!(!AttachmentRepo::verify_chunks_integrity(&conn, &att.attachment_id).unwrap());
        assert!(AttachmentRepo::read_content(&conn, &att.attachment_id).is_err());
    }

    #[test]
    fn test_metadata_update_does_not_rewrite_chunks() {
        let (conn, ctx, project_id) = setup();
        let att = AttachmentRepo::add(&conn, &ctx, &project_id, None, "oldname.bin", None, "", 0)
            .unwrap();

        let data = b"chunked content for rename test".repeat(20);
        AttachmentRepo::write_chunked_content(&conn, &ctx, &att.attachment_id, &data, 100).unwrap();

        let fresh = AttachmentRepo::get_by_id(&conn, &att.attachment_id)
            .unwrap()
            .unwrap();
        let original_chunk_count = fresh.chunk_count;
        let original_stored_size = fresh.stored_size;

        // 改名不应影响 chunk 数据
        let renamed =
            AttachmentRepo::rename(&conn, &ctx, &att.attachment_id, "newname.bin", None).unwrap();

        assert_eq!(renamed.file_name_ct, b"newname.bin");
        assert_eq!(renamed.chunk_count, original_chunk_count);
        assert_eq!(renamed.stored_size, original_stored_size);
        assert_eq!(renamed.storage_mode, StorageMode::EmbeddedChunked);
    }

    #[test]
    fn test_read_content_concatenates_chunks_correctly() {
        let (conn, ctx, project_id) = setup();
        let att =
            AttachmentRepo::add(&conn, &ctx, &project_id, None, "concat.bin", None, "", 0).unwrap();

        // 创建内容：每 chunk 不同内容，确保拼接顺序正确
        let chunk0 = vec![0u8; 50];
        let chunk1 = vec![1u8; 50];
        let chunk2 = vec![2u8; 50];
        let all_data: Vec<u8> = [&chunk0[..], &chunk1[..], &chunk2[..]].concat();

        AttachmentRepo::write_chunked_content(&conn, &ctx, &att.attachment_id, &all_data, 50)
            .unwrap();

        let read = AttachmentRepo::read_content(&conn, &att.attachment_id).unwrap();
        assert_eq!(read, all_data);
        assert_eq!(&read[0..50], &chunk0[..]);
        assert_eq!(&read[50..100], &chunk1[..]);
        assert_eq!(&read[100..150], &chunk2[..]);
    }

    #[test]
    fn test_chunked_storage_mode_set() {
        let (conn, ctx, project_id) = setup();
        let att =
            AttachmentRepo::add(&conn, &ctx, &project_id, None, "mode.bin", None, "", 0).unwrap();

        let data = b"test data for mode verification".repeat(100);
        AttachmentRepo::write_chunked_content(&conn, &ctx, &att.attachment_id, &data, 256).unwrap();

        let refreshed = AttachmentRepo::get_by_id(&conn, &att.attachment_id)
            .unwrap()
            .unwrap();
        assert_eq!(refreshed.storage_mode, StorageMode::EmbeddedChunked);
        assert_eq!(
            refreshed.chunk_count,
            (data.len() as f64 / 256.0).ceil() as u32
        );
    }

    // -----------------------------------------------------------------------
    // ENCRYPTION INTEGRATION
    // -----------------------------------------------------------------------

    #[test]
    fn test_attachment_encrypted_with_keyring() {
        use crate::init::{initialize_vault, VaultInitParams};
        use crate::repo::commit_ctx::CommitContext;
        use mdbx_crypto::keyring::Keyring;

        let mut conn = VaultConnection::open_in_memory().unwrap();
        let params = VaultInitParams::default();
        initialize_vault(&conn, &params).unwrap();

        let vault_ctx = b"test-vault-ctx";
        let vault_key = mdbx_crypto::aead::generate_key().unwrap();
        let keyring = Keyring::from_vault_key(&vault_key, vault_ctx).unwrap();
        conn.attach_keyring(keyring);

        let ctx = CommitContext::new("test-device".to_string());
        let project = ProjectRepo::create(&conn, &ctx, "P", None, None).unwrap();

        let att = AttachmentRepo::add(
            &conn,
            &ctx,
            &project.project_id,
            None,
            "secret-doc.pdf",
            Some("application/pdf"),
            "abc123",
            4096,
        )
        .unwrap();

        // 通过 API 读回是明文
        let found = AttachmentRepo::get_by_id(&conn, &att.attachment_id)
            .unwrap()
            .unwrap();
        assert_eq!(found.file_name_ct, b"secret-doc.pdf");
        assert_eq!(found.media_type_ct, Some(b"application/pdf".to_vec()));

        // 数据库中存的是密文
        let raw_name: Vec<u8> = conn
            .inner()
            .query_row(
                "SELECT file_name_ct FROM attachments WHERE attachment_id = ?1",
                params![att.attachment_id],
                |row| row.get(0),
            )
            .unwrap();
        assert_ne!(raw_name, b"secret-doc.pdf");

        // inline content 也应加密
        AttachmentRepo::write_inline_content(&conn, &ctx, &att.attachment_id, b"top secret data")
            .unwrap();
        let raw_chunk: Vec<u8> = conn.inner().query_row(
            "SELECT chunk_ct FROM attachment_chunks WHERE attachment_id = ?1 AND chunk_index = 0",
            params![att.attachment_id],
            |row| row.get(0),
        ).unwrap();
        assert_ne!(raw_chunk, b"top secret data");

        // 读回明文
        let plaintext = AttachmentRepo::read_content(&conn, &att.attachment_id).unwrap();
        assert_eq!(plaintext, b"top secret data");
    }

    #[test]
    fn test_encrypted_attachment_tamper_is_rejected() {
        use crate::init::{initialize_vault, VaultInitParams};
        use crate::repo::commit_ctx::CommitContext;
        use mdbx_crypto::keyring::Keyring;

        let mut conn = VaultConnection::open_in_memory().unwrap();
        let params = VaultInitParams::default();
        initialize_vault(&conn, &params).unwrap();

        let vault_key = mdbx_crypto::aead::generate_key().unwrap();
        let keyring = Keyring::from_vault_key(&vault_key, b"attachment-tamper-test").unwrap();
        conn.attach_keyring(keyring);

        let ctx = CommitContext::new("test-device".to_string());
        let project = ProjectRepo::create(&conn, &ctx, "P", None, None).unwrap();
        let att = AttachmentRepo::add(
            &conn,
            &ctx,
            &project.project_id,
            None,
            "secret.bin",
            None,
            "",
            0,
        )
        .unwrap();
        AttachmentRepo::write_inline_content(&conn, &ctx, &att.attachment_id, b"secret").unwrap();

        conn.inner()
            .execute(
                "UPDATE attachment_chunks SET chunk_ct = ?1 WHERE attachment_id = ?2 AND chunk_index = 0",
                params![b"not-valid-ciphertext".as_slice(), att.attachment_id],
            )
            .unwrap();

        assert!(AttachmentRepo::read_content(&conn, &att.attachment_id).is_err());
        assert!(!AttachmentRepo::verify_chunks_integrity(&conn, &att.attachment_id).unwrap());
    }
}
