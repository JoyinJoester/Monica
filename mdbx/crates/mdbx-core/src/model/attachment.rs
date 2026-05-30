use serde::{Deserialize, Serialize};

use crate::types::*;

/// 附件元数据 — MDBX 从 v1 起的一等结构。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Attachment {
    pub attachment_id: AttachmentId,
    /// 归属的 project（必须存在）
    pub project_id: ProjectId,
    /// 可选的归属 entry
    pub entry_id: Option<EntryId>,
    /// 加密的文件名
    pub file_name_ct: CipherText,
    /// 加密的媒体类型
    pub media_type_ct: Option<CipherText>,
    /// 存储模式
    pub storage_mode: StorageMode,
    /// 内容 hash（完整性校验）
    pub content_hash: ContentHash,
    /// 原始文件大小
    pub original_size: u64,
    /// 存储后的大小
    pub stored_size: u64,
    /// 分块数量
    pub chunk_count: u32,
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

/// 附件存储模式。
///
/// 即使 MVP 只支持部分模式，枚举也必须先定义完整。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum StorageMode {
    /// 小型二进制直接内嵌在附件载荷中
    EmbeddedInline,
    /// 附件在数据库内按加密分块存储
    EmbeddedChunked,
    /// 数据库保存元数据，并以内容 hash 绑定外部 blob
    ExternalHashRef,
}

impl std::fmt::Display for StorageMode {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            StorageMode::EmbeddedInline => write!(f, "embedded-inline"),
            StorageMode::EmbeddedChunked => write!(f, "embedded-chunked"),
            StorageMode::ExternalHashRef => write!(f, "external-hash-ref"),
        }
    }
}

impl std::str::FromStr for StorageMode {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s {
            "embedded-inline" => Ok(StorageMode::EmbeddedInline),
            "embedded-chunked" => Ok(StorageMode::EmbeddedChunked),
            "external-hash-ref" => Ok(StorageMode::ExternalHashRef),
            _ => Err(format!("unknown StorageMode: {}", s)),
        }
    }
}

/// 附件分块记录。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AttachmentChunk {
    pub attachment_id: AttachmentId,
    /// 从 0 开始连续递增的 chunk 序号
    pub chunk_index: u32,
    /// chunk 内容 hash
    pub chunk_hash: ChunkHash,
    /// 加密的 chunk 内容（与 external_uri_ct 至少一个存在）
    pub chunk_ct: Option<CipherText>,
    /// 加密的外部 URI（与 chunk_ct 至少一个存在）
    pub external_uri_ct: Option<CipherText>,
    /// 存储大小
    pub stored_size: u64,
    /// 创建时间 (ISO 8601)
    pub created_at: String,
}
