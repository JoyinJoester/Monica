use serde::{Deserialize, Serialize};

use crate::types::*;

/// 每次本地变更或合并操作产生一条 commit 记录。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Commit {
    pub commit_id: CommitId,
    /// 产生此 commit 的设备
    pub device_id: DeviceId,
    /// 设备本地单调递增序列号
    pub local_seq: u64,
    /// commit 类型
    pub commit_kind: CommitKind,
    /// 变更范围
    pub change_scope: ChangeScope,
    /// 加密的变更对象 ID 列表
    pub changed_object_ids_ct: CipherText,
    /// 向量时钟
    pub vector_clock: String,
    /// 加密的 commit message
    pub message_ct: Option<CipherText>,
    /// 创建时间 (ISO 8601)
    pub created_at: String,
    /// 完整性标签
    pub integrity_tag: CipherText,
}

/// Commit 的类型。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum CommitKind {
    /// 普通本地修改
    Change,
    /// 合并操作
    Merge,
    /// 快照创建
    Snapshot,
    /// 密钥轮换
    KeyRotation,
}

impl std::fmt::Display for CommitKind {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            CommitKind::Change => write!(f, "change"),
            CommitKind::Merge => write!(f, "merge"),
            CommitKind::Snapshot => write!(f, "snapshot"),
            CommitKind::KeyRotation => write!(f, "key-rotation"),
        }
    }
}

/// 变更范围，用于快速过滤。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum ChangeScope {
    Project,
    Entry,
    Attachment,
    VaultMeta,
    KeyEpoch,
    /// 跨越多个对象类型
    Multi,
}

impl std::fmt::Display for ChangeScope {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ChangeScope::Project => write!(f, "project"),
            ChangeScope::Entry => write!(f, "entry"),
            ChangeScope::Attachment => write!(f, "attachment"),
            ChangeScope::VaultMeta => write!(f, "vault-meta"),
            ChangeScope::KeyEpoch => write!(f, "key-epoch"),
            ChangeScope::Multi => write!(f, "multi"),
        }
    }
}

/// Commit DAG 的 parent 关系。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommitParent {
    pub commit_id: CommitId,
    pub parent_commit_id: CommitId,
}

/// 设备 head 记录。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeviceHead {
    pub device_id: DeviceId,
    pub head_commit_id: CommitId,
    pub last_seen_at: String,
    pub revoked: bool,
}

/// 逻辑分支。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Branch {
    pub branch_id: BranchId,
    pub branch_name: String,
    pub head_commit_id: CommitId,
    pub created_at: String,
    pub updated_at: String,
}

/// Tombstone — 删除标记，防止并发同步时误复活。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Tombstone {
    pub tombstone_id: TombstoneId,
    pub target_object_type: TombstoneTargetType,
    pub target_object_id: String,
    pub delete_clock: ObjectClock,
    pub deleted_by_device_id: DeviceId,
    pub deleted_at: String,
    /// 可安全物理清理的时间
    pub purge_eligible_at: Option<String>,
}

/// Tombstone 目标对象类型。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum TombstoneTargetType {
    Project,
    Entry,
    Attachment,
    Branch,
}

impl std::fmt::Display for TombstoneTargetType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            TombstoneTargetType::Project => write!(f, "project"),
            TombstoneTargetType::Entry => write!(f, "entry"),
            TombstoneTargetType::Attachment => write!(f, "attachment"),
            TombstoneTargetType::Branch => write!(f, "branch"),
        }
    }
}

impl std::str::FromStr for TombstoneTargetType {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s {
            "project" => Ok(TombstoneTargetType::Project),
            "entry" => Ok(TombstoneTargetType::Entry),
            "attachment" => Ok(TombstoneTargetType::Attachment),
            "branch" => Ok(TombstoneTargetType::Branch),
            _ => Err(format!("unknown TombstoneTargetType: {}", s)),
        }
    }
}

/// Vault 级元信息。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VaultMeta {
    pub vault_id: VaultId,
    pub format_version: String,
    pub created_at: String,
    pub updated_at: String,
    pub default_tiga_mode: String,
    pub active_key_epoch_id: KeyEpochId,
    /// 兼容性标志（逗号分隔或 JSON 数组）
    pub compat_flags: String,
    /// 关键扩展标识
    pub critical_extensions: String,
}

/// 密钥轮换时期的记录。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct KeyEpoch {
    pub key_epoch_id: KeyEpochId,
    pub status: KeyEpochStatus,
    pub wrapped_epoch_key_ct: CipherText,
    pub kdf_profile_id: String,
    pub created_at: String,
    pub activated_at: Option<String>,
    pub retired_at: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum KeyEpochStatus {
    Created,
    Active,
    Retired,
}

impl std::fmt::Display for KeyEpochStatus {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            KeyEpochStatus::Created => write!(f, "created"),
            KeyEpochStatus::Active => write!(f, "active"),
            KeyEpochStatus::Retired => write!(f, "retired"),
        }
    }
}

/// Snapshot 恢复检查点。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Snapshot {
    pub snapshot_id: SnapshotId,
    pub base_commit_id: CommitId,
    pub snapshot_ct: CipherText,
    pub snapshot_hash: String,
    pub created_at: String,
    pub created_by_device_id: DeviceId,
}

/// 冲突记录 — 并发修改无法自动合并时产生。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Conflict {
    pub conflict_id: String,
    /// 冲突对象类型
    pub object_type: ConflictObjectType,
    /// 冲突对象 ID
    pub object_id: String,
    /// 共同祖先 commit
    pub base_commit_id: CommitId,
    /// 本地版本 commit
    pub local_commit_id: CommitId,
    /// 传入版本 commit
    pub incoming_commit_id: CommitId,
    /// 冲突字段列表（JSON 路径）
    pub conflicting_fields: Vec<String>,
    /// 解决状态
    pub resolution: ConflictResolution,
    /// 创建时间
    pub created_at: String,
    /// 解决时间
    pub resolved_at: Option<String>,
}

/// 冲突涉及的对象类型。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum ConflictObjectType {
    Project,
    Entry,
    Attachment,
}

impl std::fmt::Display for ConflictObjectType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ConflictObjectType::Project => write!(f, "project"),
            ConflictObjectType::Entry => write!(f, "entry"),
            ConflictObjectType::Attachment => write!(f, "attachment"),
        }
    }
}

impl std::str::FromStr for ConflictObjectType {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s {
            "project" => Ok(ConflictObjectType::Project),
            "entry" => Ok(ConflictObjectType::Entry),
            "attachment" => Ok(ConflictObjectType::Attachment),
            _ => Err(format!("unknown ConflictObjectType: {}", s)),
        }
    }
}

/// 冲突解决状态。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum ConflictResolution {
    /// 尚未解决
    Unresolved,
    /// 采用本地版本
    LocalWins,
    /// 采用传入版本
    IncomingWins,
    /// 用户自定义合并
    Custom,
}

impl ConflictResolution {
    pub fn is_resolved(&self) -> bool {
        !matches!(self, ConflictResolution::Unresolved)
    }
}

impl std::fmt::Display for ConflictResolution {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ConflictResolution::Unresolved => write!(f, "unresolved"),
            ConflictResolution::LocalWins => write!(f, "local-wins"),
            ConflictResolution::IncomingWins => write!(f, "incoming-wins"),
            ConflictResolution::Custom => write!(f, "custom"),
        }
    }
}

impl std::str::FromStr for ConflictResolution {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s {
            "unresolved" => Ok(ConflictResolution::Unresolved),
            "local-wins" => Ok(ConflictResolution::LocalWins),
            "incoming-wins" => Ok(ConflictResolution::IncomingWins),
            "custom" => Ok(ConflictResolution::Custom),
            _ => Err(format!("unknown ConflictResolution: {}", s)),
        }
    }
}
