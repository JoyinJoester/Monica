use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::io::{Read, Write};

use crate::error::{SyncError, SyncResult};
use crate::message::SerializedCommit;

/// 文件格式魔数：`MDBXSYNC`
const BUNDLE_MAGIC: &[u8; 8] = b"MDBXSYNC";
/// 当前格式版本
const BUNDLE_VERSION: u32 = 1;

/// 离线同步包。
///
/// 包含一组 commit 及其关联的对象数据，用于通过文件（USB、邮件等）进行离线同步。
///
/// 文件格式：
/// ```text
/// ┌──────────────────────────────────────┐
/// │ magic:    [u8; 8]  = b"MDBXSYNC"   │
/// │ version:  u32 (LE)  = 1             │
/// │ reserved: [u8; 20]  (zero)          │
/// │ payload:  <bincode>                 │
/// │ hash:     [u8; 32]  SHA-256(body)   │
/// └──────────────────────────────────────┘
/// ```
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncBundle {
    /// 导出时的 UTC 时间戳 (RFC 3339)
    pub exported_at: String,
    /// 导出设备 ID
    pub source_device_id: String,
    /// 源 vault ID
    pub vault_id: String,
    /// 包含的 commits 列表
    pub commits: Vec<SerializedCommit>,
}

// ---------------------------------------------------------------------------
// 构建
// ---------------------------------------------------------------------------

/// 从 vault 导出一组 commit 为 SyncBundle。
pub fn build_bundle(
    vault_id: &str,
    source_device_id: &str,
    commits: Vec<SerializedCommit>,
) -> SyncBundle {
    SyncBundle {
        exported_at: chrono::Utc::now().to_rfc3339(),
        source_device_id: source_device_id.to_string(),
        vault_id: vault_id.to_string(),
        commits,
    }
}

// ---------------------------------------------------------------------------
// 文件写入
// ---------------------------------------------------------------------------

/// 将 SyncBundle 写入文件。
pub fn write_bundle(bundle: &SyncBundle, writer: &mut impl Write) -> SyncResult<()> {
    let payload = bincode::serialize(bundle)
        .map_err(|e| SyncError::IoError(std::io::Error::new(std::io::ErrorKind::InvalidData, e)))?;

    // 计算 hash
    let hash = {
        let mut h = Sha256::new();
        h.update(&payload);
        h.finalize()
    };

    // 写入 header
    writer.write_all(BUNDLE_MAGIC)?;
    writer.write_all(&BUNDLE_VERSION.to_le_bytes())?;
    writer.write_all(&[0u8; 20])?; // reserved
    writer.write_all(&payload)?;
    writer.write_all(&hash)?;

    Ok(())
}

/// 导出 SyncBundle 到字节数组（用于测试和传输）。
pub fn bundle_to_bytes(bundle: &SyncBundle) -> SyncResult<Vec<u8>> {
    let mut buf = Vec::new();
    write_bundle(bundle, &mut buf)?;
    Ok(buf)
}

// ---------------------------------------------------------------------------
// 文件读取
// ---------------------------------------------------------------------------

/// 从 reader 读取并验证 SyncBundle。
pub fn read_bundle(reader: &mut impl Read) -> SyncResult<SyncBundle> {
    // 读取 magic
    let mut magic = [0u8; 8];
    reader.read_exact(&mut magic)?;
    if &magic != BUNDLE_MAGIC {
        return Err(SyncError::BundleFormat("invalid magic bytes".to_string()));
    }

    // 读取 version
    let mut version_buf = [0u8; 4];
    reader.read_exact(&mut version_buf)?;
    let version = u32::from_le_bytes(version_buf);
    if version != BUNDLE_VERSION {
        return Err(SyncError::BundleFormat(format!(
            "unsupported version: {}, expected {}",
            version, BUNDLE_VERSION
        )));
    }

    // 跳过 reserved
    let mut reserved = [0u8; 20];
    reader.read_exact(&mut reserved)?;

    // 读取 body
    let mut payload = Vec::new();
    reader.read_to_end(&mut payload)?;

    if payload.len() < 32 {
        return Err(SyncError::BundleFormat(
            "payload too short (missing hash)".to_string(),
        ));
    }

    // 分离 hash（最后 32 字节）
    let hash_offset = payload.len() - 32;
    let stored_hash = &payload[hash_offset..];
    let payload_data = &payload[..hash_offset];

    // 验证 hash
    let computed = {
        let mut h = Sha256::new();
        h.update(payload_data);
        h.finalize()
    };
    if stored_hash != computed.as_slice() {
        return Err(SyncError::BundleIntegrity(
            "SHA-256 hash mismatch".to_string(),
        ));
    }

    // 反序列化
    let bundle: SyncBundle = bincode::deserialize(payload_data)
        .map_err(|e| SyncError::IoError(std::io::Error::new(std::io::ErrorKind::InvalidData, e)))?;

    Ok(bundle)
}

/// 从字节数组读取 SyncBundle。
pub fn bundle_from_bytes(data: &[u8]) -> SyncResult<SyncBundle> {
    let mut cursor = std::io::Cursor::new(data);
    read_bundle(&mut cursor)
}

// ---------------------------------------------------------------------------
// 测试
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use crate::message::ObjectPayload;
    use mdbx_core::model::Commit;

    fn sample_bundle() -> SyncBundle {
        SyncBundle {
            exported_at: "2026-05-21T00:00:00Z".to_string(),
            source_device_id: "test-device".to_string(),
            vault_id: "test-vault".to_string(),
            commits: vec![SerializedCommit {
                commit: Commit {
                    commit_id: "commit-1".to_string(),
                    commit_kind: mdbx_core::model::CommitKind::Change,
                    change_scope: mdbx_core::model::ChangeScope::Entry,
                    device_id: "test-device".to_string(),
                    local_seq: 1,
                    changed_object_ids_ct: vec![],
                    vector_clock: r#"{"counter":1}"#.to_string(),
                    message_ct: None,
                    created_at: "2026-05-21T00:00:00Z".to_string(),
                    integrity_tag: vec![],
                },
                parent_ids: vec!["genesis".to_string()],
                tombstones: vec![],
                object_payloads: vec![ObjectPayload {
                    object_type: "entry".to_string(),
                    object_id: "entry-1".to_string(),
                    ciphertext: b"encrypted-bytes".to_vec(),
                    associated_data: b"entry:entry-1:payload".to_vec(),
                }],
            }],
        }
    }

    #[test]
    fn test_bundle_roundtrip() {
        let bundle = sample_bundle();
        let bytes = bundle_to_bytes(&bundle).unwrap();
        assert!(bytes.len() > 44);

        let restored = bundle_from_bytes(&bytes).unwrap();
        assert_eq!(restored.vault_id, bundle.vault_id);
        assert_eq!(restored.source_device_id, bundle.source_device_id);
        assert_eq!(restored.commits.len(), 1);
        assert_eq!(restored.commits[0].commit.commit_id, "commit-1");
    }

    #[test]
    fn test_magic_validation() {
        let mut bytes = bundle_to_bytes(&sample_bundle()).unwrap();
        bytes[0] = b'X';
        let result = bundle_from_bytes(&bytes);
        assert!(result.is_err());
    }

    #[test]
    fn test_version_validation() {
        let mut bytes = bundle_to_bytes(&sample_bundle()).unwrap();
        bytes[8] = 99;
        let result = bundle_from_bytes(&bytes);
        assert!(result.is_err());
    }

    #[test]
    fn test_tamper_detection() {
        let mut bytes = bundle_to_bytes(&sample_bundle()).unwrap();
        let tamper_idx = 40;
        bytes[tamper_idx] ^= 0xFF;
        let result = bundle_from_bytes(&bytes);
        assert!(result.is_err());
    }

    #[test]
    fn test_empty_commits() {
        let bundle = SyncBundle {
            exported_at: "2026-05-21T00:00:00Z".to_string(),
            source_device_id: "device".to_string(),
            vault_id: "vault".to_string(),
            commits: vec![],
        };

        let bytes = bundle_to_bytes(&bundle).unwrap();
        let restored = bundle_from_bytes(&bytes).unwrap();
        assert!(restored.commits.is_empty());
    }

    #[test]
    fn test_multiple_commits() {
        let commits: Vec<SerializedCommit> = (0..100)
            .map(|i| SerializedCommit {
                commit: Commit {
                    commit_id: format!("commit-{}", i),
                    commit_kind: mdbx_core::model::CommitKind::Change,
                    change_scope: mdbx_core::model::ChangeScope::Entry,
                    device_id: "device".to_string(),
                    local_seq: i,
                    changed_object_ids_ct: vec![],
                    vector_clock: r#"{"counter":1}"#.to_string(),
                    message_ct: None,
                    created_at: "2026-05-21T00:00:00Z".to_string(),
                    integrity_tag: vec![],
                },
                parent_ids: vec![],
                tombstones: vec![],
                object_payloads: vec![],
            })
            .collect();

        let bundle = SyncBundle {
            exported_at: "2026-05-21T00:00:00Z".to_string(),
            source_device_id: "device".to_string(),
            vault_id: "vault".to_string(),
            commits,
        };

        let bytes = bundle_to_bytes(&bundle).unwrap();
        let restored = bundle_from_bytes(&bytes).unwrap();
        assert_eq!(restored.commits.len(), 100);
    }
}
