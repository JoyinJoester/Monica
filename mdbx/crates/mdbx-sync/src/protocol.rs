use std::collections::HashSet;

use crate::error::{SyncError, SyncResult};
use crate::message::*;

/// 同步会话状态机。
///
/// 管理双向同步的各个阶段：
///
/// ```text
/// Idle ──► HelloWait ──► Negotiate ──► Exchange ──► Done
///              │              │            │
///              └──────────────┴────────────┘
///                         │
///                       Error
/// ```
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum SyncPhase {
    Idle,
    HelloWait,
    Negotiate,
    Exchange,
    Done,
}

/// 同步协商器。
///
/// 基于双方的 head 信息和已知 commit 集合，
/// 计算出需要发送和接收的 commit 列表。
pub struct SyncNegotiator {
    /// 本地设备 ID。
    device_id: String,

    /// 本方已知的所有 commit ID。
    known_commit_ids: HashSet<String>,

    /// 对方已知的 commit ID（从 Hello 获得）。
    remote_known_commit_ids: HashSet<String>,

    /// 本地分支 head。
    local_heads: Vec<BranchHead>,

    /// 对方分支 head。
    remote_heads: Vec<BranchHead>,
}

impl SyncNegotiator {
    /// 创建本地协商器。
    pub fn new(
        device_id: &str,
        local_heads: Vec<BranchHead>,
        local_commit_ids: Vec<String>,
    ) -> Self {
        Self {
            device_id: device_id.to_string(),
            known_commit_ids: local_commit_ids.into_iter().collect(),
            remote_known_commit_ids: HashSet::new(),
            local_heads,
            remote_heads: Vec::new(),
        }
    }

    /// 处理对方的 Hello 消息并生成己方的 Hello 响应。
    pub fn on_hello(&mut self, hello: &HelloRequest) -> SyncResult<HelloResponse> {
        if hello.protocol_version != PROTOCOL_VERSION {
            return Err(SyncError::VersionMismatch {
                local: PROTOCOL_VERSION,
                remote: hello.protocol_version,
            });
        }

        self.remote_heads = hello.heads.clone();
        self.remote_known_commit_ids = hello.known_commit_ids.iter().cloned().collect();

        Ok(HelloResponse::new(
            &self.device_id,
            self.local_heads.clone(),
            self.known_commit_ids.iter().cloned().collect(),
        ))
    }

    /// 处理对方的 Hello 响应。
    pub fn on_hello_ack(&mut self, response: &HelloResponse) -> SyncResult<()> {
        if response.protocol_version != PROTOCOL_VERSION {
            return Err(SyncError::VersionMismatch {
                local: PROTOCOL_VERSION,
                remote: response.protocol_version,
            });
        }

        self.remote_heads = response.heads.clone();
        self.remote_known_commit_ids = response.known_commit_ids.iter().cloned().collect();
        Ok(())
    }

    /// 计算我方需要从对方拉取的 commit ID。
    ///
    /// 即：对方有而我们没有的 commit。
    pub fn compute_want(&self, remote_commit_ids: &[String]) -> Vec<String> {
        remote_commit_ids
            .iter()
            .filter(|id| !self.known_commit_ids.contains(*id))
            .cloned()
            .collect()
    }

    /// 计算我方需要推送给对方的 commit ID。
    ///
    /// 即：我们有而对方没有的 commit。
    pub fn compute_push(&self, our_extra_ids: &[String]) -> Vec<String> {
        our_extra_ids
            .iter()
            .filter(|id| !self.remote_known_commit_ids.contains(*id))
            .cloned()
            .collect()
    }

    /// 检查双方 head 是否已经一致（无需同步）。
    pub fn is_already_synced(&self) -> bool {
        if self.local_heads.len() != self.remote_heads.len() {
            return false;
        }

        for local in &self.local_heads {
            let remote_match = self.remote_heads.iter().any(|r| {
                r.branch_name == local.branch_name && r.head_commit_id == local.head_commit_id
            });
            if !remote_match {
                return false;
            }
        }
        true
    }

    /// 找出对方领先于我们的分支。
    pub fn ahead_branches(&self) -> Vec<&BranchHead> {
        self.remote_heads
            .iter()
            .filter(|remote| {
                !self.local_heads.iter().any(|local| {
                    local.branch_name == remote.branch_name
                        && local.head_commit_id == remote.head_commit_id
                })
            })
            .collect()
    }

    // -- accessors --

    pub fn device_id(&self) -> &str {
        &self.device_id
    }

    pub fn local_heads(&self) -> &[BranchHead] {
        &self.local_heads
    }

    pub fn remote_heads(&self) -> &[BranchHead] {
        &self.remote_heads
    }

    pub fn known_commit_ids(&self) -> &HashSet<String> {
        &self.known_commit_ids
    }

    pub fn remote_known_commit_ids(&self) -> &HashSet<String> {
        &self.remote_known_commit_ids
    }
}

/// Commit 包构建器。
///
/// 将 commit 列表打包成适合传输的批次。
pub struct BatchBuilder {
    max_per_batch: usize,
}

impl BatchBuilder {
    pub fn new(max_per_batch: usize) -> Self {
        Self { max_per_batch }
    }

    /// 将 commit 列表拆分为多个批次。
    pub fn build_batches(&self, commits: Vec<SerializedCommit>) -> Vec<CommitBatch> {
        let total = commits.len();
        let batch_count = (total + self.max_per_batch - 1) / self.max_per_batch;

        commits
            .chunks(self.max_per_batch)
            .enumerate()
            .map(|(i, chunk)| {
                CommitBatch::new(chunk.to_vec(), i as u32, i == batch_count.saturating_sub(1))
            })
            .collect()
    }
}

impl Default for BatchBuilder {
    fn default() -> Self {
        Self {
            max_per_batch: MAX_COMMITS_PER_BATCH,
        }
    }
}

// ---------------------------------------------------------------------------
// 测试
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    fn make_head(branch: &str, commit: &str) -> BranchHead {
        BranchHead {
            branch_name: branch.to_string(),
            head_commit_id: commit.to_string(),
        }
    }

    #[test]
    fn test_already_synced() {
        let heads = vec![make_head("main", "abc123")];
        let mut negotiator = SyncNegotiator::new("device-a", heads.clone(), vec!["abc123".into()]);

        let hello = HelloRequest::new("device-b", heads.clone(), vec!["abc123".into()]);
        negotiator.on_hello(&hello).unwrap();

        assert!(negotiator.is_already_synced());
    }

    #[test]
    fn test_not_synced_different_head() {
        let local_heads = vec![make_head("main", "abc123")];
        let remote_heads = vec![make_head("main", "def456")];

        let mut negotiator = SyncNegotiator::new("device-a", local_heads, vec!["abc123".into()]);

        let hello = HelloRequest::new(
            "device-b",
            remote_heads,
            vec!["abc123", "def456"]
                .iter()
                .map(|s| s.to_string())
                .collect(),
        );
        negotiator.on_hello(&hello).unwrap();

        assert!(!negotiator.is_already_synced());
        let ahead = negotiator.ahead_branches();
        assert_eq!(ahead.len(), 1);
        assert_eq!(ahead[0].head_commit_id, "def456");
    }

    #[test]
    fn test_version_mismatch() {
        let heads = vec![make_head("main", "abc")];
        let mut negotiator = SyncNegotiator::new("device-a", heads, vec!["abc".into()]);

        let mut hello = HelloRequest::new("device-b", vec![], vec![]);
        hello.protocol_version = 999;

        let result = negotiator.on_hello(&hello);
        assert!(matches!(result, Err(SyncError::VersionMismatch { .. })));
    }

    #[test]
    fn test_compute_want() {
        let negotiator = SyncNegotiator::new(
            "device-a",
            vec![],
            vec!["a", "b", "c"].iter().map(|s| s.to_string()).collect(),
        );

        let want = negotiator.compute_want(&["a".into(), "d".into(), "e".into()]);
        assert_eq!(want, vec!["d", "e"]);
    }

    #[test]
    fn test_compute_push() {
        let mut negotiator = SyncNegotiator::new(
            "device-a",
            vec![],
            vec!["a", "b", "c"].iter().map(|s| s.to_string()).collect(),
        );

        let hello = HelloRequest::new("device-b", vec![], vec!["a".to_string(), "d".to_string()]);
        negotiator.on_hello(&hello).unwrap();

        let push = negotiator.compute_push(&["a".to_string(), "b".to_string(), "c".to_string()]);
        assert_eq!(push, vec!["b", "c"]);
    }

    #[test]
    fn test_batch_builder_single_batch() {
        let builder = BatchBuilder::new(256);
        let commits = vec![SerializedCommit {
            commit: mdbx_core::model::Commit {
                commit_id: "c1".into(),
                device_id: "d1".into(),
                local_seq: 1,
                commit_kind: mdbx_core::model::CommitKind::Change,
                change_scope: mdbx_core::model::ChangeScope::Entry,
                changed_object_ids_ct: vec![],
                vector_clock: "{}".into(),
                message_ct: None,
                created_at: "2024-01-01T00:00:00Z".into(),
                integrity_tag: vec![],
            },
            parent_ids: vec![],
            tombstones: vec![],
            object_payloads: vec![],
        }];

        let batches = builder.build_batches(commits);
        assert_eq!(batches.len(), 1);
        assert!(batches[0].is_last);
        assert_eq!(batches[0].batch_index, 0);
    }

    #[test]
    fn test_batch_builder_multiple() {
        let builder = BatchBuilder::new(2);
        let mut commits = Vec::new();
        for i in 0..5 {
            commits.push(SerializedCommit {
                commit: mdbx_core::model::Commit {
                    commit_id: format!("c{}", i),
                    device_id: "d1".into(),
                    local_seq: i,
                    commit_kind: mdbx_core::model::CommitKind::Change,
                    change_scope: mdbx_core::model::ChangeScope::Entry,
                    changed_object_ids_ct: vec![],
                    vector_clock: "{}".into(),
                    message_ct: None,
                    created_at: "2024-01-01T00:00:00Z".into(),
                    integrity_tag: vec![],
                },
                parent_ids: vec![],
                tombstones: vec![],
                object_payloads: vec![],
            });
        }

        let batches = builder.build_batches(commits);
        assert_eq!(batches.len(), 3);
        assert_eq!(batches[0].batch_index, 0);
        assert!(!batches[0].is_last);
        assert_eq!(batches[1].batch_index, 1);
        assert!(!batches[1].is_last);
        assert_eq!(batches[2].batch_index, 2);
        assert!(batches[2].is_last);
    }

    #[test]
    fn test_syncmessage_roundtrip() {
        let msg = SyncMessage::Hello(HelloRequest::new(
            "device-1",
            vec![make_head("main", "abc123")],
            vec!["abc123".into()],
        ));

        let bytes = msg.to_bytes().unwrap();
        let restored = SyncMessage::from_bytes(&bytes).unwrap();

        match restored {
            SyncMessage::Hello(h) => {
                assert_eq!(h.device_id, "device-1");
                assert_eq!(h.heads.len(), 1);
            }
            _ => panic!("expected Hello"),
        }
    }
}
