use serde::{Deserialize, Serialize};

/// 从 KDBX 文件解析出的逻辑条目。
///
/// 这是 KDBX 二进制解析器与 MDBX 导入器之间的中间表示。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct KdbxEntry {
    /// KDBX entry UUID
    pub uuid: String,
    /// 条目标题
    pub title: String,
    /// 用户名
    pub username: String,
    /// 密码
    pub password: String,
    /// URL
    pub url: String,
    /// 备注/笔记
    pub notes: String,
    /// TOTP 种子（可选）
    pub totp_seed: Option<String>,
    /// 自定义字段 (key, value)
    pub custom_fields: Vec<(String, String)>,
    /// 附件列表
    pub attachments: Vec<KdbxAttachment>,
    /// 所属组路径（如 ["Work", "Email"]）
    pub group_path: Vec<String>,
    /// KDBX 图标 ID
    pub icon_id: Option<u32>,
    /// 创建时间 (ISO 8601)
    pub created_at: String,
    /// 修改时间 (ISO 8601)
    pub updated_at: String,
}

/// 从 KDBX 文件解析出的附件。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct KdbxAttachment {
    /// 附件文件名
    pub name: String,
    /// 附件二进制数据
    pub data: Vec<u8>,
}

/// 导入结果摘要。
#[derive(Debug, Clone)]
pub struct ImportResult {
    /// 创建的 project 数量
    pub projects_created: u32,
    /// 创建的 entry 数量
    pub entries_created: u32,
    /// 创建的 attachment 数量
    pub attachments_created: u32,
    /// 跳过的条目数（标题为空等）
    pub entries_skipped: u32,
    /// 导入过程中的警告信息
    pub warnings: Vec<String>,
}

/// 导出结果摘要。
#[derive(Debug, Clone)]
pub struct ExportResult {
    /// 导出的 KDBX 条目数
    pub entries_exported: u32,
    /// 导出的附件总数
    pub attachments_exported: u32,
    /// 跳过的 project 数（无 Login entry 等）
    pub projects_skipped: u32,
    /// 导出过程中的警告信息
    pub warnings: Vec<String>,
}
