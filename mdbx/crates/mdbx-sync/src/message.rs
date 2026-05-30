use serde::{Deserialize, Serialize};

use mdbx_core::model::Commit;

/// 同步协议版本。
pub const PROTOCOL_VERSION: u32 = 1;

/// 单次批量传输的最大 commit 数。
pub const MAX_COMMITS_PER_BATCH: usize = 256;

// ---------------------------------------------------------------------------
// 顶层消息容器
// ---------------------------------------------------------------------------

/// 同步协议中的一条消息。
///
/// 所有消息都是自描述的，可以跨任意传输通道发送
/// （文件同步、本地网络、中继服务等）。
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "kebab-case")]
pub enum SyncMessage {
    /// 初始握手：宣告设备身份和当前 head 快照。
    Hello(HelloRequest),

    /// 对 Hello 的响应。
    HelloAck(HelloResponse),

    /// 请求对方发送缺失的 commit。
    WantCommits(WantRequest),

    /// 发送一组合并 commit。
    CommitBatch(CommitBatch),

    /// 确认收到 commit 批次。
    BatchAck(BatchAck),

    /// 同步完成。
    Done(SyncDone),

    /// 错误响应。
    Error(SyncErrorMessage),
}

// ---------------------------------------------------------------------------
// Hello
// ---------------------------------------------------------------------------

/// Hello 请求：设备宣告自己的身份和当前已知 head。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HelloRequest {
    /// 发送设备 ID。
    pub device_id: String,

    /// 协议版本号。
    pub protocol_version: u32,

    /// 此设备已知的全部分支 head。
    /// `(branch_name, head_commit_id)`。
    pub heads: Vec<BranchHead>,

    /// 此设备已知的全部 commit ID（用于 skip 已存在的 commit）。
    pub known_commit_ids: Vec<String>,
}

/// 单个分支的 head 信息。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BranchHead {
    pub branch_name: String,
    pub head_commit_id: String,
}

/// Hello 响应：对方宣告其身份和 head。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HelloResponse {
    pub device_id: String,
    pub protocol_version: u32,
    pub heads: Vec<BranchHead>,
    pub known_commit_ids: Vec<String>,
}

// ---------------------------------------------------------------------------
// Want
// ---------------------------------------------------------------------------

/// 请求对方发送特定的 commit。
///
/// 发送方已将自己的 known_commit_ids 发给对方，
/// 现在请求对方发送那些对方有而自己没有的 commit。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WantRequest {
    /// 请求的 commit ID 列表。
    pub want: Vec<String>,

    /// 已拥有的 commit ID 列表（避免重复发送）。
    pub have: Vec<String>,
}

// ---------------------------------------------------------------------------
// Commit 传输
// ---------------------------------------------------------------------------

/// 一批序列化的 commit。
///
/// 发送方将 commit 连同其关联的对象数据打包在此消息中。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommitBatch {
    /// 本批次的 commit 列表。
    pub commits: Vec<SerializedCommit>,

    /// 是否为最后一批。
    pub is_last: bool,

    /// 批次序号（从 0 开始）。
    pub batch_index: u32,
}

/// 单个完整的 commit 及其关联对象。
///
/// 包含 commit 元数据、parent 关系、以及 commit 引用的加密对象负载。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SerializedCommit {
    /// Commit 元数据。
    pub commit: Commit,

    /// 此 commit 的 parent commit ID 列表（DAG 关联）。
    pub parent_ids: Vec<String>,

    /// 此 commit 创建的所有 tombstone。
    pub tombstones: Vec<TombstoneRecord>,

    /// 此 commit 引用的对象负载（加密形式）。
    /// `(object_type, object_id, ciphertext)`。
    pub object_payloads: Vec<ObjectPayload>,
}

/// Commit 关联的 tombstone。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TombstoneRecord {
    pub tombstone_id: String,
    pub target_object_type: String,
    pub target_object_id: String,
    pub delete_clock: String,
    pub deleted_by_device_id: String,
    pub deleted_at: String,
}

/// Commit 引用的加密对象负载。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ObjectPayload {
    /// 对象类型: "project" | "entry" | "attachment"
    pub object_type: String,
    /// 对象 ID。
    pub object_id: String,
    /// 加密后的对象数据。
    pub ciphertext: Vec<u8>,
    /// 关联数据（用于 AEAD 验证）。
    pub associated_data: Vec<u8>,
}

// ---------------------------------------------------------------------------
// Ack / Done / Error
// ---------------------------------------------------------------------------

/// 接收方确认收到一个 commit 批次。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BatchAck {
    /// 确认的批次序号。
    pub batch_index: u32,

    /// 该批次中成功应用的 commit 数。
    pub applied_count: u32,

    /// 该批次引入的冲突数。
    pub conflict_count: u32,
}

/// 同步完成通知。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncDone {
    /// 发送方身份。
    pub device_id: String,

    /// 本次同步交换的 commit 总数。
    pub total_commits: u32,

    /// 同步后的分支 head。
    pub final_heads: Vec<BranchHead>,
}

/// 错误消息。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncErrorMessage {
    /// 错误码。
    pub code: String,

    /// 人类可读的描述。
    pub message: String,
}

// ---------------------------------------------------------------------------
// 辅助方法
// ---------------------------------------------------------------------------

impl SyncMessage {
    /// 序列化为 JSON 字节。
    pub fn to_bytes(&self) -> Result<Vec<u8>, serde_json::Error> {
        serde_json::to_vec(self)
    }

    /// 从 JSON 字节反序列化。
    pub fn from_bytes(data: &[u8]) -> Result<Self, serde_json::Error> {
        serde_json::from_slice(data)
    }
}

impl HelloRequest {
    pub fn new(device_id: &str, heads: Vec<BranchHead>, known_commit_ids: Vec<String>) -> Self {
        Self {
            device_id: device_id.to_string(),
            protocol_version: PROTOCOL_VERSION,
            heads,
            known_commit_ids,
        }
    }
}

impl HelloResponse {
    pub fn new(device_id: &str, heads: Vec<BranchHead>, known_commit_ids: Vec<String>) -> Self {
        Self {
            device_id: device_id.to_string(),
            protocol_version: PROTOCOL_VERSION,
            heads,
            known_commit_ids,
        }
    }
}

impl CommitBatch {
    pub fn new(commits: Vec<SerializedCommit>, batch_index: u32, is_last: bool) -> Self {
        Self {
            commits,
            is_last,
            batch_index,
        }
    }
}
