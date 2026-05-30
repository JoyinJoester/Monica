/// 稳定标识符类型别名。
pub type VaultId = String;
pub type ProjectId = String;
pub type EntryId = String;
pub type AttachmentId = String;
pub type CommitId = String;
pub type DeviceId = String;
pub type BranchId = String;
pub type KeyEpochId = String;
pub type SnapshotId = String;
pub type TombstoneId = String;

/// hex(SHA-256) 形式的 hash。
pub type ContentHash = String;
pub type ChunkHash = String;

/// 向量时钟的序列化形式。
pub type ObjectClock = String;

/// 加密后的二进制负载。在 SQLite 中对应 BLOB 列。
pub type CipherText = Vec<u8>;
