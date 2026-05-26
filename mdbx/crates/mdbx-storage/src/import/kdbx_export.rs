use mdbx_core::model::EntryType;

use crate::connection::VaultConnection;
use crate::error::StorageResult;
use crate::import::kdbx_model::{ExportResult, KdbxAttachment, KdbxEntry};
use crate::repo::attachment::AttachmentRepo;
use crate::repo::entry::EntryRepo;
use crate::repo::project::ProjectRepo;

/// KDBX 条目导出器。
///
/// 将 MDBX project 模型逆向映射为 KDBX 兼容结构：
/// - 每个 Project → 一个 KDBX entry
/// - Login Entry 的 payload → username/password/URL/TOTP
/// - Note Entry 的 payload → notes
/// - Attachment → KDBX attachment
/// - group_id → group_path（`/` 拆分）
///
/// 这是导入器的逆操作，用于 KDBX 格式导出或互操作桥接。
pub struct KdbxExporter;

impl KdbxExporter {
    /// 从 MDBX vault 导出所有未删除的 project 为 KDBX 条目列表。
    pub fn export_all(conn: &VaultConnection) -> ExportResult {
        let mut result = ExportResult {
            entries_exported: 0,
            attachments_exported: 0,
            projects_skipped: 0,
            warnings: Vec::new(),
        };

        let projects = match ProjectRepo::list_all(conn) {
            Ok(ps) => ps,
            Err(e) => {
                result
                    .warnings
                    .push(format!("failed to list projects: {}", e));
                return result;
            }
        };

        for project in &projects {
            match Self::export_one(conn, project) {
                Ok((entry, att_count, warnings)) => {
                    result.entries_exported += 1;
                    result.attachments_exported += att_count;
                    result.warnings.extend(warnings);
                    // 在完整实现中，entry 会被写入 KDBX 文件
                    let _ = entry;
                }
                Err(e) => {
                    result.projects_skipped += 1;
                    result.warnings.push(format!(
                        "skipped project '{}': {}",
                        String::from_utf8_lossy(&project.title_ct),
                        e
                    ));
                }
            }
        }

        result
    }

    /// 导出单个 project 为 KDBX 条目。
    ///
    /// 返回 (KdbxEntry, attachment_count, warnings)。
    pub fn export_one(
        conn: &VaultConnection,
        project: &mdbx_core::model::Project,
    ) -> StorageResult<(KdbxEntry, u32, Vec<String>)> {
        let mut warnings: Vec<String> = Vec::new();
        let mut attachment_count: u32 = 0;

        let title = String::from_utf8_lossy(&project.title_ct).to_string();

        // 读取 project 下的所有未删除 entry
        let entries = EntryRepo::list_by_project(conn, &project.project_id).unwrap_or_default();

        // 提取 Login entry 中的凭据
        let login_entry = entries.iter().find(|e| e.entry_type == EntryType::Login);
        let (username, password, url, totp_seed, custom_fields) = match login_entry {
            Some(entry) => Self::extract_login_payload(entry, &mut warnings),
            None => {
                warnings.push(format!(
                    "project '{}' has no Login entry, using empty credentials",
                    title
                ));
                (String::new(), String::new(), String::new(), None, vec![])
            }
        };

        // 提取 Note entry 的文本
        let note_entry = entries.iter().find(|e| e.entry_type == EntryType::Note);
        let notes = match note_entry {
            Some(entry) => Self::extract_note_text(entry, &mut warnings),
            None => String::new(),
        };

        // 读取附件
        let attachments: Vec<KdbxAttachment> =
            match AttachmentRepo::list_by_project(conn, &project.project_id) {
                Ok(atts) => {
                    let mut kdbx_atts: Vec<KdbxAttachment> = Vec::new();
                    for att in &atts {
                        match AttachmentRepo::read_content(conn, &att.attachment_id) {
                            Ok(data) => {
                                kdbx_atts.push(KdbxAttachment {
                                    name: String::from_utf8_lossy(&att.file_name_ct).to_string(),
                                    data,
                                });
                                attachment_count += 1;
                            }
                            Err(e) => {
                                warnings.push(format!(
                                    "failed to read attachment '{}': {}",
                                    String::from_utf8_lossy(&att.file_name_ct),
                                    e
                                ));
                            }
                        }
                    }
                    kdbx_atts
                }
                Err(e) => {
                    warnings.push(format!("failed to list attachments: {}", e));
                    vec![]
                }
            };

        // group_id → group_path
        let group_path: Vec<String> = project
            .group_id
            .as_deref()
            .map(|g| g.split('/').map(|s| s.to_string()).collect())
            .unwrap_or_default();

        let kdbx_entry = KdbxEntry {
            uuid: project.project_id.clone(),
            title,
            username,
            password,
            url,
            notes,
            totp_seed,
            custom_fields,
            attachments,
            group_path,
            icon_id: None, // icon_ref 可后续映射
            created_at: project.created_at.clone(),
            updated_at: project.updated_at.clone(),
        };

        Ok((kdbx_entry, attachment_count, warnings))
    }

    /// 从 Login entry 的 payload 中提取凭据字段。
    fn extract_login_payload(
        entry: &mdbx_core::model::Entry,
        warnings: &mut Vec<String>,
    ) -> (
        String,
        String,
        String,
        Option<String>,
        Vec<(String, String)>,
    ) {
        let payload: serde_json::Value = match serde_json::from_slice(&entry.payload_ct) {
            Ok(v) => v,
            Err(e) => {
                warnings.push(format!("failed to parse login payload: {}", e));
                return (String::new(), String::new(), String::new(), None, vec![]);
            }
        };

        let username = payload
            .get("username")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string();
        let password = payload
            .get("password")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string();
        let url = payload
            .get("url")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string();
        let totp_seed = payload
            .get("totp_seed")
            .and_then(|v| v.as_str())
            .map(|s| s.to_string());

        let custom_fields: Vec<(String, String)> = payload
            .get("custom_fields")
            .and_then(|v| v.as_object())
            .map(|obj| {
                obj.iter()
                    .map(|(k, v)| (k.clone(), v.as_str().unwrap_or("").to_string()))
                    .collect()
            })
            .unwrap_or_default();

        (username, password, url, totp_seed, custom_fields)
    }

    /// 从 Note entry 的 payload 中提取文本内容。
    fn extract_note_text(entry: &mdbx_core::model::Entry, warnings: &mut Vec<String>) -> String {
        let payload: serde_json::Value = match serde_json::from_slice(&entry.payload_ct) {
            Ok(v) => v,
            Err(e) => {
                warnings.push(format!("failed to parse note payload: {}", e));
                return String::new();
            }
        };

        payload
            .get("text")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string()
    }
}

// ---------------------------------------------------------------------------
// 测试
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use crate::import::kdbx_model::{KdbxAttachment, KdbxEntry};
    use crate::init::{initialize_vault, VaultInitParams};
    use crate::repo::attachment::AttachmentRepo;
    use crate::repo::commit_ctx::CommitContext;
    use crate::repo::entry::EntryRepo;
    use crate::repo::project::ProjectRepo;

    fn setup() -> (VaultConnection, CommitContext) {
        let conn = VaultConnection::open_in_memory().unwrap();
        let params = VaultInitParams::default();
        initialize_vault(&conn, &params).unwrap();
        let ctx = CommitContext::new("test-device".to_string());
        (conn, ctx)
    }

    /// 辅助：创建一个测试用 project + login entry
    fn create_test_project(
        conn: &VaultConnection,
        ctx: &CommitContext,
        title: &str,
        username: &str,
        password: &str,
    ) -> mdbx_core::model::Project {
        let project = ProjectRepo::create(conn, ctx, title, None, None).unwrap();
        let payload = serde_json::json!({
            "username": username,
            "password": password,
            "url": format!("https://{}.example.com", title.to_lowercase()),
        });
        EntryRepo::create(
            conn,
            ctx,
            &project.project_id,
            EntryType::Login,
            Some(title),
            &payload,
        )
        .unwrap();
        ProjectRepo::get_by_id(conn, &project.project_id)
            .unwrap()
            .unwrap()
    }

    // -----------------------------------------------------------------------
    // BASIC EXPORT
    // -----------------------------------------------------------------------

    #[test]
    fn test_export_single_project() {
        let (conn, ctx) = setup();
        create_test_project(&conn, &ctx, "GitHub", "alice", "s3cret");

        let result = KdbxExporter::export_all(&conn);
        assert_eq!(result.entries_exported, 1);
        assert_eq!(result.projects_skipped, 0);
        assert!(result.warnings.is_empty());
    }

    #[test]
    fn test_export_fields_roundtrip() {
        let (conn, ctx) = setup();
        let project = create_test_project(&conn, &ctx, "Example", "bob", "pass123");

        let (entry, _att_count, warnings) = KdbxExporter::export_one(&conn, &project).unwrap();

        assert!(warnings.is_empty());
        assert_eq!(entry.title, "Example");
        assert_eq!(entry.username, "bob");
        assert_eq!(entry.password, "pass123");
        assert!(entry.url.contains("example.example.com"));
    }

    #[test]
    fn test_export_multiple_projects() {
        let (conn, ctx) = setup();
        create_test_project(&conn, &ctx, "Site A", "a", "p1");
        create_test_project(&conn, &ctx, "Site B", "b", "p2");
        create_test_project(&conn, &ctx, "Site C", "c", "p3");

        let result = KdbxExporter::export_all(&conn);
        assert_eq!(result.entries_exported, 3);
    }

    // -----------------------------------------------------------------------
    // TOTP
    // -----------------------------------------------------------------------

    #[test]
    fn test_export_totp_seed() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "2FA Site", None, None).unwrap();
        let payload = serde_json::json!({
            "username": "alice",
            "password": "s3cret",
            "url": "https://2fa.example.com",
            "totp_seed": "JBSWY3DPEHPK3PXP"
        });
        EntryRepo::create(
            &conn,
            &ctx,
            &project.project_id,
            EntryType::Login,
            Some("2FA Site"),
            &payload,
        )
        .unwrap();

        let (entry, _, warnings) = KdbxExporter::export_one(&conn, &project).unwrap();
        assert!(warnings.is_empty());
        assert_eq!(entry.totp_seed, Some("JBSWY3DPEHPK3PXP".to_string()));
    }

    // -----------------------------------------------------------------------
    // NOTES
    // -----------------------------------------------------------------------

    #[test]
    fn test_export_notes() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "Server", None, None).unwrap();
        // Login entry
        EntryRepo::create(
            &conn,
            &ctx,
            &project.project_id,
            EntryType::Login,
            Some("Server"),
            &serde_json::json!({"username":"root","password":"pass","url":""}),
        )
        .unwrap();
        // Note entry
        EntryRepo::create(
            &conn,
            &ctx,
            &project.project_id,
            EntryType::Note,
            Some("Notes"),
            &serde_json::json!({"text": "Production server - do not restart"}),
        )
        .unwrap();

        let (entry, _, warnings) = KdbxExporter::export_one(&conn, &project).unwrap();
        assert!(warnings.is_empty());
        assert_eq!(entry.notes, "Production server - do not restart");
    }

    // -----------------------------------------------------------------------
    // CUSTOM FIELDS
    // -----------------------------------------------------------------------

    #[test]
    fn test_export_custom_fields() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "Bank", None, None).unwrap();
        let mut payload = serde_json::json!({
            "username": "alice",
            "password": "s3cret",
            "url": "https://bank.example.com"
        });
        payload["custom_fields"] = serde_json::json!({
            "Account Number": "12345678",
            "PIN": "9999"
        });
        EntryRepo::create(
            &conn,
            &ctx,
            &project.project_id,
            EntryType::Login,
            Some("Bank"),
            &payload,
        )
        .unwrap();

        let (entry, _, warnings) = KdbxExporter::export_one(&conn, &project).unwrap();
        assert!(warnings.is_empty());
        assert_eq!(entry.custom_fields.len(), 2);
        assert!(entry
            .custom_fields
            .contains(&("Account Number".to_string(), "12345678".to_string())));
        assert!(entry
            .custom_fields
            .contains(&("PIN".to_string(), "9999".to_string())));
    }

    // -----------------------------------------------------------------------
    // GROUP PATH
    // -----------------------------------------------------------------------

    #[test]
    fn test_export_group_path() {
        let (conn, ctx) = setup();
        let project =
            ProjectRepo::create(&conn, &ctx, "Work Mail", Some("Work/Email"), None).unwrap();
        EntryRepo::create(
            &conn,
            &ctx,
            &project.project_id,
            EntryType::Login,
            Some("Work Mail"),
            &serde_json::json!({"username":"alice","password":"s3cret","url":""}),
        )
        .unwrap();

        let (entry, _, warnings) = KdbxExporter::export_one(&conn, &project).unwrap();
        assert!(warnings.is_empty());
        assert_eq!(entry.group_path, vec!["Work", "Email"]);
    }

    // -----------------------------------------------------------------------
    // ATTACHMENTS
    // -----------------------------------------------------------------------

    #[test]
    fn test_export_attachment() {
        let (conn, ctx) = setup();
        let project = ProjectRepo::create(&conn, &ctx, "Docs", None, None).unwrap();
        EntryRepo::create(
            &conn,
            &ctx,
            &project.project_id,
            EntryType::Login,
            Some("Docs"),
            &serde_json::json!({"username":"alice","password":"s3cret","url":""}),
        )
        .unwrap();

        // 添加附件
        let att = AttachmentRepo::add(
            &conn,
            &ctx,
            &project.project_id,
            None,
            "readme.txt",
            Some("text/plain"),
            "",
            0,
        )
        .unwrap();
        AttachmentRepo::write_inline_content(&conn, &ctx, &att.attachment_id, b"Hello, export!")
            .unwrap();

        let (entry, att_count, warnings) = KdbxExporter::export_one(&conn, &project).unwrap();
        assert!(warnings.is_empty());
        assert_eq!(att_count, 1);
        assert_eq!(entry.attachments.len(), 1);
        assert_eq!(entry.attachments[0].name, "readme.txt");
        assert_eq!(entry.attachments[0].data, b"Hello, export!");
    }

    // -----------------------------------------------------------------------
    // NO LOGIN ENTRY
    // -----------------------------------------------------------------------

    #[test]
    fn test_export_project_without_login() {
        let (conn, ctx) = setup();
        // 创建 project 但不创建 Login entry
        let project = ProjectRepo::create(&conn, &ctx, "NoLogin", None, None).unwrap();

        let (entry, _att_count, warnings) = KdbxExporter::export_one(&conn, &project).unwrap();
        assert_eq!(warnings.len(), 1);
        assert!(warnings[0].contains("no Login entry"));
        assert_eq!(entry.title, "NoLogin");
        assert!(entry.username.is_empty());
        assert!(entry.password.is_empty());
    }

    // -----------------------------------------------------------------------
    // IMPORT → EXPORT ROUNDTRIP
    // -----------------------------------------------------------------------

    #[test]
    fn test_import_export_roundtrip() {
        let (conn, ctx) = setup();

        // 先导入
        let original = KdbxEntry {
            uuid: uuid::Uuid::new_v4().to_string(),
            title: "Roundtrip Test".to_string(),
            username: "rt_user".to_string(),
            password: "rt_pass".to_string(),
            url: "https://rt.example.com".to_string(),
            notes: "This is a note".to_string(),
            totp_seed: Some("TOTPSEED123".to_string()),
            custom_fields: vec![("Key1".to_string(), "Val1".to_string())],
            attachments: vec![KdbxAttachment {
                name: "file.txt".to_string(),
                data: b"roundtrip data".to_vec(),
            }],
            group_path: vec!["Test".to_string(), "Sub".to_string()],
            icon_id: None,
            created_at: "2024-01-01T00:00:00Z".to_string(),
            updated_at: "2024-01-01T00:00:00Z".to_string(),
        };

        use crate::import::KdbxImporter;
        let import_result = KdbxImporter::import_entries(&conn, &ctx, &[original.clone()]);
        assert_eq!(import_result.projects_created, 1);
        assert_eq!(import_result.entries_created, 2); // Login + Note
        assert_eq!(import_result.attachments_created, 1);

        // 再导出
        let result = KdbxExporter::export_all(&conn);
        assert_eq!(result.entries_exported, 1);

        // 重建单个 project 并验证
        let projects = ProjectRepo::list_all(&conn).unwrap();
        let (exported, _att_count, warnings) =
            KdbxExporter::export_one(&conn, &projects[0]).unwrap();
        assert!(warnings.is_empty());

        // 核心字段应一致
        assert_eq!(exported.title, original.title);
        assert_eq!(exported.username, original.username);
        assert_eq!(exported.password, original.password);
        assert_eq!(exported.url, original.url);
        assert_eq!(exported.notes, original.notes);
        assert_eq!(exported.totp_seed, original.totp_seed);
        assert_eq!(exported.custom_fields, original.custom_fields);
        assert_eq!(exported.group_path, original.group_path);
        assert_eq!(exported.attachments.len(), 1);
        assert_eq!(exported.attachments[0].name, "file.txt");
        assert_eq!(exported.attachments[0].data, b"roundtrip data");
    }

    #[test]
    fn test_export_all_with_empty_vault() {
        let (conn, _ctx) = setup();
        let result = KdbxExporter::export_all(&conn);
        assert_eq!(result.entries_exported, 0);
        assert_eq!(result.projects_skipped, 0);
    }
}
