use mdbx_core::model::EntryType;

use crate::connection::VaultConnection;
use crate::error::{StorageError, StorageResult};
use crate::import::kdbx_model::{ImportResult, KdbxAttachment, KdbxEntry};
use crate::repo::attachment::AttachmentRepo;
use crate::repo::commit_ctx::CommitContext;
use crate::repo::entry::EntryRepo;
use crate::repo::project::ProjectRepo;

/// KDBX 条目导入器。
///
/// 将 KDBX 逻辑条目映射为 MDBX project 模型：
/// - 每个 KDBX entry → 一个 Project
/// - 主凭据（username/password/URL/TOTP）→ 一个 Login Entry
/// - 备注 → 一个 Note Entry（非空时）
/// - 自定义字段 → 合并入 Login Entry payload 的 `custom_fields` 字段
/// - 附件 → Attachment 对象（embedded-inline 模式）
/// - 组路径 → project 的 group_id（`/` 分隔）
pub struct KdbxImporter;

impl KdbxImporter {
    /// 将一组 KDBX 条目导入到 MDBX vault。
    ///
    /// 每个条目在一个事务内完成（project + entry + attachment），
    /// 单个条目失败不影响其他条目。
    pub fn import_entries(
        conn: &VaultConnection,
        ctx: &CommitContext,
        entries: &[KdbxEntry],
    ) -> ImportResult {
        let mut result = ImportResult {
            projects_created: 0,
            entries_created: 0,
            attachments_created: 0,
            entries_skipped: 0,
            warnings: Vec::new(),
        };

        for entry in entries {
            match Self::import_one(conn, ctx, entry) {
                Ok((p_count, e_count, a_count, warnings)) => {
                    result.projects_created += p_count;
                    result.entries_created += e_count;
                    result.attachments_created += a_count;
                    result.warnings.extend(warnings);
                }
                Err(e) => {
                    result.entries_skipped += 1;
                    result
                        .warnings
                        .push(format!("skipped entry '{}': {}", entry.title, e));
                }
            }
        }

        result
    }

    /// 导入单个 KDBX 条目。
    ///
    /// 返回 (projects, entries, attachments, warnings)。
    fn import_one(
        conn: &VaultConnection,
        ctx: &CommitContext,
        kdbx_entry: &KdbxEntry,
    ) -> StorageResult<(u32, u32, u32, Vec<String>)> {
        let mut warnings: Vec<String> = Vec::new();
        let mut project_count: u32 = 0;
        let mut entry_count: u32 = 0;
        let mut attachment_count: u32 = 0;

        // 标题为空的条目跳过
        if kdbx_entry.title.trim().is_empty() {
            return Err(StorageError::ConstraintViolation(
                "KDBX entry has empty title".to_string(),
            ));
        }

        // 1. 创建 project
        let group_id = if kdbx_entry.group_path.is_empty() {
            None
        } else {
            Some(kdbx_entry.group_path.join("/"))
        };

        let project = ProjectRepo::create(
            conn,
            ctx,
            &kdbx_entry.title,
            group_id.as_deref(),
            None, // icon_ref — KDBX icon_id 可后续映射
        )
        .map_err(|e| {
            StorageError::ConstraintViolation(format!(
                "failed to create project for '{}': {}",
                kdbx_entry.title, e
            ))
        })?;
        project_count += 1;

        // 2. 创建 Login entry（主凭据载荷）
        let login_payload = Self::build_login_payload(kdbx_entry);
        match EntryRepo::create(
            conn,
            ctx,
            &project.project_id,
            EntryType::Login,
            Some(&kdbx_entry.title),
            &login_payload,
        ) {
            Ok(_) => entry_count += 1,
            Err(e) => warnings.push(format!(
                "failed to create login entry for '{}': {}",
                kdbx_entry.title, e
            )),
        }

        // 3. 如果 notes 非空，创建 Note entry
        if !kdbx_entry.notes.trim().is_empty() {
            let note_payload = serde_json::json!({"text": kdbx_entry.notes.trim()});
            match EntryRepo::create(
                conn,
                ctx,
                &project.project_id,
                EntryType::Note,
                Some("Notes"),
                &note_payload,
            ) {
                Ok(_) => entry_count += 1,
                Err(e) => warnings.push(format!(
                    "failed to create note entry for '{}': {}",
                    kdbx_entry.title, e
                )),
            }
        }

        // 4. 导入附件
        for att in &kdbx_entry.attachments {
            match Self::import_attachment(conn, ctx, &project.project_id, att) {
                Ok(_) => attachment_count += 1,
                Err(e) => warnings.push(format!(
                    "failed to import attachment '{}' for '{}': {}",
                    att.name, kdbx_entry.title, e
                )),
            }
        }

        Ok((project_count, entry_count, attachment_count, warnings))
    }

    /// 构建 Login entry 的 payload JSON。
    fn build_login_payload(kdbx_entry: &KdbxEntry) -> serde_json::Value {
        let mut payload = serde_json::json!({
            "username": kdbx_entry.username,
            "password": kdbx_entry.password,
            "url": kdbx_entry.url,
        });

        if let Some(ref totp) = kdbx_entry.totp_seed {
            if !totp.is_empty() {
                payload["totp_seed"] = serde_json::Value::String(totp.clone());
            }
        }

        if !kdbx_entry.custom_fields.is_empty() {
            let custom: serde_json::Map<String, serde_json::Value> = kdbx_entry
                .custom_fields
                .iter()
                .map(|(k, v)| (k.clone(), serde_json::Value::String(v.clone())))
                .collect();
            payload["custom_fields"] = serde_json::Value::Object(custom);
        }

        payload
    }

    /// 导入单个 KDBX 附件到 MDBX。
    fn import_attachment(
        conn: &VaultConnection,
        ctx: &CommitContext,
        project_id: &str,
        att: &KdbxAttachment,
    ) -> StorageResult<()> {
        // 创建附件元数据（content_hash 和 size 由 write_inline_content 更新）
        let mdbx_att = AttachmentRepo::add(
            conn,
            ctx,
            project_id,
            None, // entry 级附件暂不绑定
            &att.name,
            None, // media_type 可从扩展名推断，MVP 暂空
            "",   // 占位 hash
            att.data.len() as u64,
        )?;

        // 写入内联内容
        AttachmentRepo::write_inline_content(conn, ctx, &mdbx_att.attachment_id, &att.data)?;

        Ok(())
    }
}

// ---------------------------------------------------------------------------
// 测试
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use crate::import::kdbx_model::KdbxAttachment;
    use crate::init::{initialize_vault, VaultInitParams};

    fn setup() -> (VaultConnection, CommitContext) {
        let conn = VaultConnection::open_in_memory().unwrap();
        let params = VaultInitParams::default();
        initialize_vault(&conn, &params).unwrap();
        let ctx = CommitContext::new("test-device".to_string());
        (conn, ctx)
    }

    fn make_entry(title: &str, user: &str, pass: &str) -> KdbxEntry {
        KdbxEntry {
            uuid: uuid::Uuid::new_v4().to_string(),
            title: title.to_string(),
            username: user.to_string(),
            password: pass.to_string(),
            url: String::new(),
            notes: String::new(),
            totp_seed: None,
            custom_fields: vec![],
            attachments: vec![],
            group_path: vec![],
            icon_id: None,
            created_at: "2024-01-01T00:00:00Z".to_string(),
            updated_at: "2024-01-01T00:00:00Z".to_string(),
        }
    }

    // -----------------------------------------------------------------------
    // BASIC IMPORT
    // -----------------------------------------------------------------------

    #[test]
    fn test_import_single_entry() {
        let (conn, ctx) = setup();
        let entries = vec![make_entry("GitHub", "alice", "s3cret")];

        let result = KdbxImporter::import_entries(&conn, &ctx, &entries);

        assert_eq!(result.projects_created, 1);
        assert_eq!(result.entries_created, 1); // Login entry
        assert_eq!(result.attachments_created, 0);
        assert_eq!(result.entries_skipped, 0);
        assert!(result.warnings.is_empty());

        // 验证 project 存在
        let projects = ProjectRepo::list_all(&conn).unwrap();
        assert_eq!(projects.len(), 1);
        assert_eq!(projects[0].title_ct, b"GitHub");

        // 验证 entry 存在
        let entries_in_db = EntryRepo::list_by_project(&conn, &projects[0].project_id).unwrap();
        assert_eq!(entries_in_db.len(), 1);
        assert_eq!(entries_in_db[0].entry_type, EntryType::Login);
    }

    #[test]
    fn test_import_entry_with_notes() {
        let (conn, ctx) = setup();
        let mut entry = make_entry("Server", "root", "pass123");
        entry.notes = "Production server - handle with care".to_string();

        let result = KdbxImporter::import_entries(&conn, &ctx, &[entry]);
        assert_eq!(result.entries_created, 2); // Login + Note
        assert_eq!(result.projects_created, 1);
    }

    #[test]
    fn test_import_entry_with_totp() {
        let (conn, ctx) = setup();
        let mut entry = make_entry("2FA Site", "bob", "pass456");
        entry.totp_seed = Some("JBSWY3DPEHPK3PXP".to_string());

        let result = KdbxImporter::import_entries(&conn, &ctx, &[entry]);
        assert_eq!(result.projects_created, 1);

        // 验证 payload 中包含 totp_seed
        let projects = ProjectRepo::list_all(&conn).unwrap();
        let entries = EntryRepo::list_by_project(&conn, &projects[0].project_id).unwrap();
        let login = &entries[0];
        let payload: serde_json::Value = serde_json::from_slice(&login.payload_ct).unwrap();
        assert_eq!(payload["totp_seed"], "JBSWY3DPEHPK3PXP");
    }

    #[test]
    fn test_import_multiple_entries() {
        let (conn, ctx) = setup();
        let entries = vec![
            make_entry("Site A", "a", "p1"),
            make_entry("Site B", "b", "p2"),
            make_entry("Site C", "c", "p3"),
        ];

        let result = KdbxImporter::import_entries(&conn, &ctx, &entries);

        assert_eq!(result.projects_created, 3);
        assert_eq!(result.entries_created, 3);
        assert_eq!(result.entries_skipped, 0);
    }

    // -----------------------------------------------------------------------
    // EMPTY TITLE
    // -----------------------------------------------------------------------

    #[test]
    fn test_empty_title_skipped() {
        let (conn, ctx) = setup();
        let entries = vec![make_entry("", "user", "pass")];

        let result = KdbxImporter::import_entries(&conn, &ctx, &entries);

        assert_eq!(result.projects_created, 0);
        assert_eq!(result.entries_skipped, 1);
        assert_eq!(result.warnings.len(), 1);
    }

    #[test]
    fn test_whitespace_only_title_skipped() {
        let (conn, ctx) = setup();
        let entries = vec![make_entry("   ", "user", "pass")];

        let result = KdbxImporter::import_entries(&conn, &ctx, &entries);
        assert_eq!(result.entries_skipped, 1);
    }

    // -----------------------------------------------------------------------
    // GROUP PATH
    // -----------------------------------------------------------------------

    #[test]
    fn test_group_path_becomes_group_id() {
        let (conn, ctx) = setup();
        let mut entry = make_entry("Work Mail", "alice", "s3cret");
        entry.group_path = vec!["Work".to_string(), "Email".to_string()];

        let result = KdbxImporter::import_entries(&conn, &ctx, &[entry]);
        assert_eq!(result.projects_created, 1);

        let projects = ProjectRepo::list_all(&conn).unwrap();
        assert_eq!(projects[0].group_id.as_deref(), Some("Work/Email"));
    }

    // -----------------------------------------------------------------------
    // CUSTOM FIELDS
    // -----------------------------------------------------------------------

    #[test]
    fn test_custom_fields_in_payload() {
        let (conn, ctx) = setup();
        let mut entry = make_entry("Bank", "alice", "s3cret");
        entry.custom_fields = vec![
            ("Account Number".to_string(), "12345678".to_string()),
            ("PIN".to_string(), "9999".to_string()),
        ];

        let result = KdbxImporter::import_entries(&conn, &ctx, &[entry]);
        assert_eq!(result.projects_created, 1);

        let projects = ProjectRepo::list_all(&conn).unwrap();
        let entries = EntryRepo::list_by_project(&conn, &projects[0].project_id).unwrap();
        let payload: serde_json::Value = serde_json::from_slice(&entries[0].payload_ct).unwrap();

        let custom = payload["custom_fields"].as_object().unwrap();
        assert_eq!(custom["Account Number"], "12345678");
        assert_eq!(custom["PIN"], "9999");
    }

    // -----------------------------------------------------------------------
    // ATTACHMENTS
    // -----------------------------------------------------------------------

    #[test]
    fn test_import_entry_with_attachment() {
        let (conn, ctx) = setup();
        let mut entry = make_entry("Docs", "alice", "s3cret");
        entry.attachments = vec![KdbxAttachment {
            name: "readme.txt".to_string(),
            data: b"Hello, MDBX import!".to_vec(),
        }];

        let result = KdbxImporter::import_entries(&conn, &ctx, &[entry]);

        assert_eq!(result.projects_created, 1);
        assert_eq!(result.attachments_created, 1);

        // 验证附件存在
        let projects = ProjectRepo::list_all(&conn).unwrap();
        let attachments = AttachmentRepo::list_by_project(&conn, &projects[0].project_id).unwrap();
        assert_eq!(attachments.len(), 1);
        assert_eq!(attachments[0].file_name_ct, b"readme.txt");

        // 验证附件内容可读
        let data = AttachmentRepo::read_content(&conn, &attachments[0].attachment_id).unwrap();
        assert_eq!(data, b"Hello, MDBX import!");
    }

    #[test]
    fn test_import_entry_with_multiple_attachments() {
        let (conn, ctx) = setup();
        let mut entry = make_entry("MultiAtt", "alice", "s3cret");
        entry.attachments = vec![
            KdbxAttachment {
                name: "a.txt".to_string(),
                data: vec![1, 2, 3],
            },
            KdbxAttachment {
                name: "b.txt".to_string(),
                data: vec![4, 5, 6],
            },
        ];

        let result = KdbxImporter::import_entries(&conn, &ctx, &[entry]);
        assert_eq!(result.attachments_created, 2);
    }

    // -----------------------------------------------------------------------
    // NOTES EDGE CASES
    // -----------------------------------------------------------------------

    #[test]
    fn test_empty_notes_no_note_entry() {
        let (conn, ctx) = setup();
        let entry = make_entry("NoNotes", "alice", "s3cret");
        // notes is empty by default

        let result = KdbxImporter::import_entries(&conn, &ctx, &[entry]);
        assert_eq!(result.entries_created, 1); // only Login
    }

    #[test]
    fn test_whitespace_only_notes_no_note_entry() {
        let (conn, ctx) = setup();
        let mut entry = make_entry("Spaces", "alice", "s3cret");
        entry.notes = "   \n  \t  ".to_string();

        let result = KdbxImporter::import_entries(&conn, &ctx, &[entry]);
        assert_eq!(result.entries_created, 1); // only Login
    }

    // -----------------------------------------------------------------------
    // PARTIAL FAILURE RESILIENCE
    // -----------------------------------------------------------------------

    #[test]
    fn test_mixed_valid_and_invalid() {
        let (conn, ctx) = setup();
        let entries = vec![
            make_entry("Valid", "a", "p1"),
            make_entry("", "b", "p2"), // bad — empty title
            make_entry("Also Valid", "c", "p3"),
        ];

        let result = KdbxImporter::import_entries(&conn, &ctx, &entries);

        assert_eq!(result.projects_created, 2);
        assert_eq!(result.entries_skipped, 1);
        assert_eq!(result.warnings.len(), 1);
        assert!(result.warnings[0].contains("empty title"));
    }
}
