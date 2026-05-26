use serde::{Deserialize, Serialize};

use crate::tiga::TigaMode;
use crate::types::*;

/// Entry 是 project 内部的具体类型化记录。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Entry {
    pub entry_id: EntryId,
    /// 父级 project（FK 约束）
    pub project_id: ProjectId,
    /// entry 类型
    pub entry_type: EntryType,
    /// 加密的标题（可选）
    pub title_ct: Option<CipherText>,
    /// 加密的秘密载荷 — 所有敏感字段都在这里
    pub payload_ct: CipherText,
    /// 载荷 schema 版本，用于兼容性
    pub payload_schema_version: u32,
    /// entry 级 Tiga 覆盖
    pub tiga_mode_override: Option<TigaMode>,
    /// 向量时钟
    pub object_clock: ObjectClock,
    /// 当前 head commit
    pub head_commit_id: CommitId,
    /// 软删除标记
    pub deleted: bool,
    /// 创建时间 (ISO 8601)
    pub created_at: String,
    /// 更新时间 (ISO 8601)
    pub updated_at: String,
    /// 创建设备 ID
    pub created_by_device_id: DeviceId,
    /// 更新设备 ID
    pub updated_by_device_id: DeviceId,
}

/// Entry 的类型枚举。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum EntryType {
    /// 登录凭据
    Login,
    /// 安全笔记
    Note,
    /// 卡片（信用卡、会员卡等）
    Card,
    /// 身份记录
    Identity,
    /// TOTP 记录
    Totp,
    /// Passkey 记录
    Passkey,
    /// SSH 密钥
    SshKey,
    /// API Token
    ApiToken,
    /// 文档引用
    DocumentRef,
}

impl std::fmt::Display for EntryType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            EntryType::Login => write!(f, "login"),
            EntryType::Note => write!(f, "note"),
            EntryType::Card => write!(f, "card"),
            EntryType::Identity => write!(f, "identity"),
            EntryType::Totp => write!(f, "totp"),
            EntryType::Passkey => write!(f, "passkey"),
            EntryType::SshKey => write!(f, "ssh-key"),
            EntryType::ApiToken => write!(f, "api-token"),
            EntryType::DocumentRef => write!(f, "document-ref"),
        }
    }
}

impl std::str::FromStr for EntryType {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s {
            "login" => Ok(EntryType::Login),
            "note" => Ok(EntryType::Note),
            "card" => Ok(EntryType::Card),
            "identity" => Ok(EntryType::Identity),
            "totp" => Ok(EntryType::Totp),
            "passkey" => Ok(EntryType::Passkey),
            "ssh-key" => Ok(EntryType::SshKey),
            "api-token" => Ok(EntryType::ApiToken),
            "document-ref" => Ok(EntryType::DocumentRef),
            _ => Err(format!("unknown EntryType: {}", s)),
        }
    }
}
