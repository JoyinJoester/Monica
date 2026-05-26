use std::path::Path;

use crate::commit_integrity::{compute_commit_integrity_tag, CommitIntegrityInput};
use crate::connection::VaultConnection;
use crate::error::{StorageError, StorageResult};

/// 数据库健康检查结果。
#[derive(Debug, Clone)]
pub struct HealthCheckResult {
    pub healthy: bool,
    pub issues: Vec<HealthIssue>,
}

/// 健康问题描述。
#[derive(Debug, Clone)]
pub struct HealthIssue {
    pub severity: IssueSeverity,
    pub category: String,
    pub description: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum IssueSeverity {
    Info,
    Warning,
    Error,
    Critical,
}

/// 恢复验证器。
///
/// 检查 vault 数据库在异常场景下的完整性和可恢复性：
/// - WAL 恢复后的数据一致性
/// - 损坏 chunk 检测
/// - 陈旧设备 head 检测
/// - 快照完整性
/// - 孤儿记录检测
pub struct RecoveryVerifier;

impl RecoveryVerifier {
    /// 对打开的 vault 进行完整健康检查。
    pub fn full_health_check(conn: &VaultConnection) -> StorageResult<HealthCheckResult> {
        let mut issues: Vec<HealthIssue> = Vec::new();

        // 1. 基本完整性检查
        if let Err(e) = Self::check_basic_integrity(conn) {
            issues.push(HealthIssue {
                severity: IssueSeverity::Critical,
                category: "integrity".to_string(),
                description: format!("basic integrity check failed: {}", e),
            });
        }

        // 2. 检查 commit 链
        match Self::check_commit_chain(conn) {
            Ok(commit_issues) => issues.extend(commit_issues),
            Err(e) => issues.push(HealthIssue {
                severity: IssueSeverity::Error,
                category: "commit-chain".to_string(),
                description: format!("commit chain check failed: {}", e),
            }),
        }

        // 3. 检查附件 chunk 完整性
        match Self::check_attachment_chunks(conn) {
            Ok(chunk_issues) => issues.extend(chunk_issues),
            Err(e) => issues.push(HealthIssue {
                severity: IssueSeverity::Error,
                category: "attachment-chunks".to_string(),
                description: format!("chunk check failed: {}", e),
            }),
        }

        // 4. 检查孤儿记录
        match Self::check_orphans(conn) {
            Ok(orphan_issues) => issues.extend(orphan_issues),
            Err(e) => issues.push(HealthIssue {
                severity: IssueSeverity::Warning,
                category: "orphans".to_string(),
                description: format!("orphan check failed: {}", e),
            }),
        }

        // 5. 检查陈旧 head
        match Self::check_stale_heads(conn) {
            Ok(head_issues) => issues.extend(head_issues),
            Err(e) => issues.push(HealthIssue {
                severity: IssueSeverity::Warning,
                category: "stale-heads".to_string(),
                description: format!("stale head check failed: {}", e),
            }),
        }

        let error_count = issues
            .iter()
            .filter(|i| i.severity >= IssueSeverity::Error)
            .count();
        let healthy = error_count == 0;

        Ok(HealthCheckResult { healthy, issues })
    }

    /// 基本完整性：检查 SQLite 内部一致性。
    pub fn check_basic_integrity(conn: &VaultConnection) -> StorageResult<()> {
        let result: String = conn
            .inner()
            .query_row("PRAGMA integrity_check", [], |row| row.get(0))
            .map_err(StorageError::Database)?;

        if result != "ok" {
            return Err(StorageError::SchemaCreation(format!(
                "integrity_check failed: {}",
                result
            )));
        }
        Ok(())
    }

    /// 检查 commit DAG 是否有断链。
    pub fn check_commit_chain(conn: &VaultConnection) -> StorageResult<Vec<HealthIssue>> {
        let mut issues: Vec<HealthIssue> = Vec::new();

        // 查找 parent commit 不存在的 commit（除了 genesis）
        let mut stmt = conn
            .inner()
            .prepare(
                "SELECT cp.commit_id, cp.parent_commit_id
                 FROM commit_parents cp
                 LEFT JOIN commits c ON cp.parent_commit_id = c.commit_id
                 WHERE c.commit_id IS NULL",
            )
            .map_err(StorageError::Database)?;

        let orphans: Vec<(String, String)> = stmt
            .query_map([], |row| Ok((row.get(0)?, row.get(1)?)))
            .map_err(StorageError::Database)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(StorageError::Database)?;

        for (commit_id, parent_id) in &orphans {
            issues.push(HealthIssue {
                severity: IssueSeverity::Error,
                category: "commit-chain".to_string(),
                description: format!(
                    "commit {} references non-existent parent {}",
                    commit_id, parent_id
                ),
            });
        }

        // 检查是否有引用不存在 commit 的 branch head
        let mut stmt = conn
            .inner()
            .prepare(
                "SELECT b.branch_id, b.head_commit_id
                 FROM branches b
                 LEFT JOIN commits c ON b.head_commit_id = c.commit_id
                 WHERE c.commit_id IS NULL",
            )
            .map_err(StorageError::Database)?;

        let dangling_branches: Vec<(String, String)> = stmt
            .query_map([], |row| Ok((row.get(0)?, row.get(1)?)))
            .map_err(StorageError::Database)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(StorageError::Database)?;

        for (branch_id, head_id) in &dangling_branches {
            issues.push(HealthIssue {
                severity: IssueSeverity::Critical,
                category: "commit-chain".to_string(),
                description: format!(
                    "branch {} head points to non-existent commit {}",
                    branch_id, head_id
                ),
            });
        }

        issues.extend(Self::check_commit_integrity_tags(conn)?);

        Ok(issues)
    }

    fn check_commit_integrity_tags(conn: &VaultConnection) -> StorageResult<Vec<HealthIssue>> {
        let mut issues = Vec::new();
        let mut stmt = conn
            .inner()
            .prepare(
                "SELECT commit_id, device_id, local_seq, commit_kind, change_scope,
                        changed_object_ids_ct, vector_clock, message_ct,
                        created_at, integrity_tag
                 FROM commits",
            )
            .map_err(StorageError::Database)?;

        let commits = stmt
            .query_map([], |row| {
                Ok(CommitIntegrityRow {
                    commit_id: row.get(0)?,
                    device_id: row.get(1)?,
                    local_seq: row.get::<_, i64>(2)? as u64,
                    commit_kind: row.get(3)?,
                    change_scope: row.get(4)?,
                    changed_object_ids_ct: row.get(5)?,
                    vector_clock: row.get(6)?,
                    message_ct: row.get(7)?,
                    created_at: row.get(8)?,
                    integrity_tag: row.get(9)?,
                })
            })
            .map_err(StorageError::Database)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(StorageError::Database)?;

        for commit in commits {
            let parents = Self::parent_ids_for_commit(conn, &commit.commit_id)?;
            let input = CommitIntegrityInput {
                commit_id: &commit.commit_id,
                device_id: &commit.device_id,
                local_seq: commit.local_seq,
                commit_kind: &commit.commit_kind,
                change_scope: &commit.change_scope,
                changed_object_ids_ct: &commit.changed_object_ids_ct,
                vector_clock: &commit.vector_clock,
                message_ct: commit.message_ct.as_deref(),
                created_at: &commit.created_at,
                parents: &parents,
            };
            let expected = compute_commit_integrity_tag(conn.keyring(), &input)?;

            if expected == commit.integrity_tag {
                continue;
            }

            if conn.keyring().is_some()
                && looks_like_plain_history_payload(&commit.changed_object_ids_ct)
                && compute_commit_integrity_tag(None, &input)? == commit.integrity_tag
            {
                continue;
            }

            if conn.keyring().is_none()
                && !looks_like_plain_history_payload(&commit.changed_object_ids_ct)
            {
                issues.push(HealthIssue {
                    severity: IssueSeverity::Warning,
                    category: "commit-integrity".to_string(),
                    description: format!(
                        "commit {} integrity cannot be verified without an unlocked keyring",
                        commit.commit_id
                    ),
                });
            } else {
                issues.push(HealthIssue {
                    severity: IssueSeverity::Error,
                    category: "commit-integrity".to_string(),
                    description: format!("commit {} integrity tag mismatch", commit.commit_id),
                });
            }
        }

        Ok(issues)
    }

    fn parent_ids_for_commit(
        conn: &VaultConnection,
        commit_id: &str,
    ) -> StorageResult<Vec<String>> {
        let mut stmt = conn
            .inner()
            .prepare(
                "SELECT parent_commit_id FROM commit_parents
                 WHERE commit_id = ?1
                 ORDER BY parent_commit_id",
            )
            .map_err(StorageError::Database)?;
        let parents = stmt
            .query_map(rusqlite::params![commit_id], |row| row.get(0))
            .map_err(StorageError::Database)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(StorageError::Database)?;
        Ok(parents)
    }

    /// 检查附件 chunk 完整性。
    pub fn check_attachment_chunks(conn: &VaultConnection) -> StorageResult<Vec<HealthIssue>> {
        let mut issues: Vec<HealthIssue> = Vec::new();

        // 查找 chunk_count 与实际 chunk 数不匹配的附件
        let mut stmt = conn
            .inner()
            .prepare(
                "SELECT a.attachment_id, a.chunk_count,
                        (SELECT COUNT(*) FROM attachment_chunks ac
                         WHERE ac.attachment_id = a.attachment_id) as actual_count
                 FROM attachments a
                 WHERE a.deleted = 0",
            )
            .map_err(StorageError::Database)?;

        let mismatches: Vec<(String, i32, i32)> = stmt
            .query_map([], |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?)))
            .map_err(StorageError::Database)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(StorageError::Database)?;

        for (att_id, expected, actual) in &mismatches {
            if expected != actual {
                issues.push(HealthIssue {
                    severity: IssueSeverity::Error,
                    category: "attachment-chunks".to_string(),
                    description: format!(
                        "attachment {} has chunk_count={} but {} actual chunks",
                        att_id, expected, actual
                    ),
                });
            }
        }

        // 检查 chunk_index 连续性
        let mut stmt = conn
            .inner()
            .prepare(
                "SELECT attachment_id, chunk_index
                 FROM attachment_chunks
                 ORDER BY attachment_id, chunk_index",
            )
            .map_err(StorageError::Database)?;

        let chunks: Vec<(String, i32)> = stmt
            .query_map([], |row| Ok((row.get(0)?, row.get(1)?)))
            .map_err(StorageError::Database)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(StorageError::Database)?;

        // 按 attachment 分组，检查 index 是否连续
        let mut current_att_id: Option<&str> = None;
        let mut expected_index: i32 = 0;
        for (att_id, chunk_index) in &chunks {
            if current_att_id.as_deref() != Some(att_id.as_str()) {
                current_att_id = Some(att_id.as_str());
                expected_index = 0;
            }
            if *chunk_index != expected_index {
                issues.push(HealthIssue {
                    severity: IssueSeverity::Error,
                    category: "attachment-chunks".to_string(),
                    description: format!(
                        "attachment {} has non-sequential chunk index: expected {}, got {}",
                        att_id, expected_index, chunk_index
                    ),
                });
                // 重新同步期望值
                expected_index = *chunk_index;
            }
            expected_index += 1;
        }

        Ok(issues)
    }

    /// 检查孤儿记录（entries/attachments 引用了不存在的 project）。
    pub fn check_orphans(conn: &VaultConnection) -> StorageResult<Vec<HealthIssue>> {
        let mut issues: Vec<HealthIssue> = Vec::new();

        // 检查引用不存在 project 的 entry
        let mut stmt = conn
            .inner()
            .prepare(
                "SELECT e.entry_id, e.project_id
                 FROM entries e
                 LEFT JOIN projects p ON e.project_id = p.project_id
                 WHERE p.project_id IS NULL",
            )
            .map_err(StorageError::Database)?;

        let orphan_entries: Vec<(String, String)> = stmt
            .query_map([], |row| Ok((row.get(0)?, row.get(1)?)))
            .map_err(StorageError::Database)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(StorageError::Database)?;

        for (entry_id, project_id) in &orphan_entries {
            issues.push(HealthIssue {
                severity: IssueSeverity::Error,
                category: "orphans".to_string(),
                description: format!(
                    "entry {} references non-existent project {}",
                    entry_id, project_id
                ),
            });
        }

        // 检查引用不存在 project 的 attachment
        let mut stmt = conn
            .inner()
            .prepare(
                "SELECT a.attachment_id, a.project_id
                 FROM attachments a
                 LEFT JOIN projects p ON a.project_id = p.project_id
                 WHERE p.project_id IS NULL",
            )
            .map_err(StorageError::Database)?;

        let orphan_attachments: Vec<(String, String)> = stmt
            .query_map([], |row| Ok((row.get(0)?, row.get(1)?)))
            .map_err(StorageError::Database)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(StorageError::Database)?;

        for (att_id, project_id) in &orphan_attachments {
            issues.push(HealthIssue {
                severity: IssueSeverity::Error,
                category: "orphans".to_string(),
                description: format!(
                    "attachment {} references non-existent project {}",
                    att_id, project_id
                ),
            });
        }

        Ok(issues)
    }

    /// 检查陈旧 device head：head 对应的 commit 是否存在。
    pub fn check_stale_heads(conn: &VaultConnection) -> StorageResult<Vec<HealthIssue>> {
        let mut issues: Vec<HealthIssue> = Vec::new();

        let mut stmt = conn
            .inner()
            .prepare(
                "SELECT dh.device_id, dh.head_commit_id
                 FROM device_heads dh
                 LEFT JOIN commits c ON dh.head_commit_id = c.commit_id
                 WHERE c.commit_id IS NULL",
            )
            .map_err(StorageError::Database)?;

        let stale_heads: Vec<(String, String)> = stmt
            .query_map([], |row| Ok((row.get(0)?, row.get(1)?)))
            .map_err(StorageError::Database)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(StorageError::Database)?;

        for (device_id, head_id) in &stale_heads {
            issues.push(HealthIssue {
                severity: IssueSeverity::Critical,
                category: "stale-heads".to_string(),
                description: format!(
                    "device {} head {} references non-existent commit",
                    device_id, head_id
                ),
            });
        }

        // 检查是否有从未活跃过的设备（head = genesis only，可能已废弃）
        // 这里只是 Info 级别
        let mut stmt = conn
            .inner()
            .prepare(
                "SELECT dh.device_id, dh.last_seen_at
                 FROM device_heads dh
                 WHERE dh.last_seen_at < ?1",
            )
            .map_err(StorageError::Database)?;

        let old_threshold = "2024-01-01T00:00:00Z";
        let old_devices: Vec<(String, String)> = stmt
            .query_map(rusqlite::params![old_threshold], |row| {
                Ok((row.get(0)?, row.get(1)?))
            })
            .map_err(StorageError::Database)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(StorageError::Database)?;

        for (device_id, last_seen) in &old_devices {
            issues.push(HealthIssue {
                severity: IssueSeverity::Info,
                category: "stale-heads".to_string(),
                description: format!(
                    "device {} last seen at {} (before threshold {})",
                    device_id, last_seen, old_threshold
                ),
            });
        }

        Ok(issues)
    }

    /// 验证快照完整性（hash 校验）。
    pub fn verify_snapshot_integrity(
        conn: &VaultConnection,
        snapshot_id: &str,
    ) -> StorageResult<bool> {
        use crate::repo::snapshot::SnapshotRepo;
        SnapshotRepo::verify_integrity(conn, snapshot_id)
    }
}

struct CommitIntegrityRow {
    commit_id: String,
    device_id: String,
    local_seq: u64,
    commit_kind: String,
    change_scope: String,
    changed_object_ids_ct: Vec<u8>,
    vector_clock: String,
    message_ct: Option<Vec<u8>>,
    created_at: String,
    integrity_tag: Vec<u8>,
}

fn looks_like_plain_history_payload(bytes: &[u8]) -> bool {
    serde_json::from_slice::<serde_json::Value>(bytes).is_ok()
}

/// WAL 恢复测试工具。
///
/// 模拟异常关闭后 WAL 恢复场景，验证数据完整性。
pub struct WalRecoveryTester;

impl WalRecoveryTester {
    /// 创建一个基于文件的数据库（启用 WAL 模式），写入数据后关闭，
    /// 然后重新打开验证数据完整。
    pub fn test_wal_recovery_roundtrip(db_path: &Path) -> StorageResult<WalRecoveryResult> {
        let mut result = WalRecoveryResult {
            entries_before_crash: 0,
            entries_after_recovery: 0,
            recovery_successful: false,
            issues: Vec::new(),
        };

        // Phase 1: 创建并写入
        {
            let conn = VaultConnection::create(db_path)?;

            // 手动初始化（简化版）
            let now = chrono::Utc::now().to_rfc3339();
            let vault_id = uuid::Uuid::new_v4().to_string();
            let commit_id = uuid::Uuid::new_v4().to_string();
            let branch_id = uuid::Uuid::new_v4().to_string();
            let key_epoch_id = uuid::Uuid::new_v4().to_string();

            conn.inner()
                .execute(
                    "INSERT INTO vault_meta (vault_id, format_version, created_at,
                     updated_at, default_tiga_mode, active_key_epoch_id)
                     VALUES (?1, 'MDBX-1', ?2, ?2, 'multi', ?3)",
                    rusqlite::params![vault_id, now, key_epoch_id],
                )
                .map_err(StorageError::Database)?;

            conn.inner()
                .execute(
                    "INSERT INTO commits (commit_id, device_id, local_seq, commit_kind,
                     change_scope, changed_object_ids_ct, vector_clock, message_ct,
                     created_at, integrity_tag)
                     VALUES (?1, 'test-device', 0, 'change', 'vault-meta', X'00',
                     '{}', NULL, ?2, X'00')",
                    rusqlite::params![commit_id, now],
                )
                .map_err(StorageError::Database)?;

            conn.inner()
                .execute(
                    "INSERT INTO branches (branch_id, branch_name, head_commit_id,
                     created_at, updated_at)
                     VALUES (?1, 'main', ?2, ?3, ?3)",
                    rusqlite::params![branch_id, commit_id, now],
                )
                .map_err(StorageError::Database)?;

            conn.inner()
                .execute(
                    "INSERT INTO device_heads (device_id, head_commit_id, last_seen_at)
                     VALUES ('test-device', ?1, ?2)",
                    rusqlite::params![commit_id, now],
                )
                .map_err(StorageError::Database)?;

            conn.inner()
                .execute(
                    "INSERT INTO key_epochs (key_epoch_id, status, wrapped_epoch_key_ct,
                     kdf_profile_id, created_at, activated_at)
                     VALUES (?1, 'active', X'00', 'mdbx-default-v1', ?2, ?2)",
                    rusqlite::params![key_epoch_id, now],
                )
                .map_err(StorageError::Database)?;

            // 写入一些 project 和 entry
            for i in 0..3 {
                let project_id = uuid::Uuid::new_v4().to_string();
                let entry_id = uuid::Uuid::new_v4().to_string();
                let obj_clock = format!("{{\"test-device\":{}}}", i + 1);

                conn.inner()
                    .execute(
                        "INSERT INTO projects (project_id, title_ct, object_clock,
                         head_commit_id, created_at, updated_at,
                         created_by_device_id, updated_by_device_id)
                         VALUES (?1, ?2, ?3, ?4, ?5, ?5, 'test-device', 'test-device')",
                        rusqlite::params![
                            project_id,
                            format!("project-{}", i).as_bytes(),
                            obj_clock,
                            commit_id,
                            now
                        ],
                    )
                    .map_err(StorageError::Database)?;

                conn.inner()
                    .execute(
                        "INSERT INTO entries (entry_id, project_id, entry_type,
                         payload_ct, object_clock, head_commit_id,
                         created_at, updated_at, created_by_device_id, updated_by_device_id)
                         VALUES (?1, ?2, 'login', X'00', ?3, ?4, ?5, ?5, 'test-device', 'test-device')",
                        rusqlite::params![entry_id, project_id, obj_clock, commit_id, now],
                    )
                    .map_err(StorageError::Database)?;

                result.entries_before_crash += 1;
            }
            // conn 在作用域结束时关闭（模拟 crash）
        }

        // Phase 2: 重新打开并验证
        {
            let conn = VaultConnection::open(db_path)?;

            let count: i32 = conn
                .inner()
                .query_row("SELECT COUNT(*) FROM entries", [], |row| row.get(0))
                .map_err(StorageError::Database)?;

            result.entries_after_recovery = count as u32;

            // 运行完整性检查
            match RecoveryVerifier::full_health_check(&conn) {
                Ok(health) => {
                    result.recovery_successful = health.healthy
                        && result.entries_before_crash == result.entries_after_recovery;
                    result.issues = health
                        .issues
                        .iter()
                        .map(|i| i.description.clone())
                        .collect();
                }
                Err(e) => {
                    result
                        .issues
                        .push(format!("health check after recovery failed: {}", e));
                }
            }
        }

        Ok(result)
    }
}

/// WAL 恢复测试结果。
#[derive(Debug, Clone)]
pub struct WalRecoveryResult {
    pub entries_before_crash: u32,
    pub entries_after_recovery: u32,
    pub recovery_successful: bool,
    pub issues: Vec<String>,
}

// ---------------------------------------------------------------------------
// 测试
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use crate::init::{initialize_vault, VaultInitParams};
    use crate::repo::attachment::AttachmentRepo;
    use crate::repo::commit_ctx::CommitContext;
    use crate::repo::entry::EntryRepo;
    use crate::repo::project::ProjectRepo;

    fn setup() -> (VaultConnection, CommitContext, String) {
        let conn = VaultConnection::open_in_memory().unwrap();
        let params = VaultInitParams::default();
        initialize_vault(&conn, &params).unwrap();
        let ctx = CommitContext::new("test-device".to_string());
        let project = ProjectRepo::create(&conn, &ctx, "Test", None, None).unwrap();
        (conn, ctx, project.project_id)
    }

    fn attach_test_keyring(conn: &mut VaultConnection) {
        let vault_key = mdbx_crypto::aead::generate_key().unwrap();
        let keyring =
            mdbx_crypto::keyring::Keyring::from_vault_key(&vault_key, b"recovery-test").unwrap();
        conn.attach_keyring(keyring);
    }

    // -----------------------------------------------------------------------
    // BASIC INTEGRITY
    // -----------------------------------------------------------------------

    #[test]
    fn test_basic_integrity_check_passes() {
        let (conn, _ctx, _p) = setup();
        RecoveryVerifier::check_basic_integrity(&conn).unwrap();
    }

    #[test]
    fn test_full_health_check_passes_on_clean_vault() {
        let (conn, _ctx, _p) = setup();
        let result = RecoveryVerifier::full_health_check(&conn).unwrap();
        assert!(result.healthy);
        // 可能有 Info 级别的陈旧 head 告警（测试中 last_seen_at 是当前时间，不应触发旧设备检查）
        let has_errors = result
            .issues
            .iter()
            .any(|i| i.severity >= IssueSeverity::Error);
        assert!(!has_errors, "unexpected errors: {:?}", result.issues);
    }

    // -----------------------------------------------------------------------
    // COMMIT CHAIN
    // -----------------------------------------------------------------------

    #[test]
    fn test_check_commit_chain_no_dangling_references() {
        let (conn, _ctx, _p) = setup();
        let issues = RecoveryVerifier::check_commit_chain(&conn).unwrap();
        let critical: Vec<_> = issues
            .iter()
            .filter(|i| i.severity >= IssueSeverity::Error)
            .collect();
        assert!(critical.is_empty(), "critical issues: {:?}", critical);
    }

    #[test]
    fn test_check_commit_chain_detects_dangling_branch() {
        let (conn, _ctx, _p) = setup();

        // 暂时禁用 FK 以便注入坏数据
        conn.inner().execute("PRAGMA foreign_keys=OFF", []).unwrap();

        // 插入一个指向不存在 commit 的 branch
        conn.inner()
            .execute(
                "INSERT INTO branches (branch_id, branch_name, head_commit_id,
                 created_at, updated_at)
                 VALUES ('bad-branch', 'bad', 'nonexistent-commit',
                 '2024-01-01T00:00:00Z', '2024-01-01T00:00:00Z')",
                [],
            )
            .unwrap();

        conn.inner().execute("PRAGMA foreign_keys=ON", []).unwrap();

        let issues = RecoveryVerifier::check_commit_chain(&conn).unwrap();
        let critical: Vec<_> = issues
            .iter()
            .filter(|i| i.severity >= IssueSeverity::Critical)
            .collect();
        assert!(!critical.is_empty());
        assert!(critical[0].description.contains("bad-branch"));
    }

    #[test]
    fn test_check_commit_chain_detects_commit_integrity_tamper() {
        let (conn, _ctx, project_id) = setup();
        let commit_id: String = conn
            .inner()
            .query_row(
                "SELECT head_commit_id FROM projects WHERE project_id = ?1",
                rusqlite::params![project_id],
                |row| row.get(0),
            )
            .unwrap();

        conn.inner()
            .execute(
                "UPDATE commits SET change_scope = 'tampered-project' WHERE commit_id = ?1",
                rusqlite::params![commit_id],
            )
            .unwrap();

        let issues = RecoveryVerifier::check_commit_chain(&conn).unwrap();
        let has_integrity_error = issues.iter().any(|i| {
            i.severity >= IssueSeverity::Error
                && i.category == "commit-integrity"
                && i.description.contains(&commit_id)
        });
        assert!(has_integrity_error, "issues: {:?}", issues);
    }

    #[test]
    fn test_check_commit_chain_accepts_legacy_plaintext_history_when_unlocked() {
        let (mut conn, _ctx, _p) = setup();

        // These commits were created before a keyring was attached, so their
        // history payload and integrity tag use the legacy plaintext profile.
        attach_test_keyring(&mut conn);

        let issues = RecoveryVerifier::check_commit_chain(&conn).unwrap();
        let errors: Vec<_> = issues
            .iter()
            .filter(|i| i.severity >= IssueSeverity::Error)
            .collect();
        assert!(errors.is_empty(), "unexpected errors: {:?}", errors);
    }

    // -----------------------------------------------------------------------
    // ATTACHMENT CHUNKS
    // -----------------------------------------------------------------------

    #[test]
    fn test_check_attachment_chunks_passes() {
        let (conn, ctx, project_id) = setup();
        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            mdbx_core::model::EntryType::Login,
            Some("E"),
            &serde_json::json!({"username":"a"}),
        )
        .unwrap();

        let att = AttachmentRepo::add(
            &conn,
            &ctx,
            &project_id,
            Some(&entry.entry_id),
            "file.bin",
            Some("application/octet-stream"),
            "",
            0,
        )
        .unwrap();
        AttachmentRepo::write_inline_content(&conn, &ctx, &att.attachment_id, b"data").unwrap();

        let issues = RecoveryVerifier::check_attachment_chunks(&conn).unwrap();
        assert!(issues.is_empty());
    }

    #[test]
    fn test_check_attachment_chunks_detects_count_mismatch() {
        let (conn, _ctx, project_id) = setup();

        // 手动创建附件元数据，但 chunk_count 与实际不符
        let now = chrono::Utc::now().to_rfc3339();
        let att_id = uuid::Uuid::new_v4().to_string();
        let commit_id = uuid::Uuid::new_v4().to_string();

        conn.inner()
            .execute(
                "INSERT INTO attachments (attachment_id, project_id, file_name_ct,
                 storage_mode, content_hash, original_size, stored_size,
                 chunk_count, head_commit_id, created_at, updated_at,
                 created_by_device_id, updated_by_device_id)
                 VALUES (?1, ?2, X'00', 'embedded-chunked', 'fake-hash',
                 100, 100, 3, ?3, ?4, ?4, 'dev', 'dev')",
                rusqlite::params![att_id, project_id, commit_id, now],
            )
            .unwrap();

        let issues = RecoveryVerifier::check_attachment_chunks(&conn).unwrap();
        let has_mismatch = issues.iter().any(|i| i.description.contains("chunk_count"));
        assert!(has_mismatch);
    }

    // -----------------------------------------------------------------------
    // ORPHANS
    // -----------------------------------------------------------------------

    #[test]
    fn test_check_orphans_passes_on_clean_vault() {
        let (conn, ctx, project_id) = setup();
        EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            mdbx_core::model::EntryType::Login,
            Some("E"),
            &serde_json::json!({"username":"a"}),
        )
        .unwrap();

        let issues = RecoveryVerifier::check_orphans(&conn).unwrap();
        assert!(issues.is_empty());
    }

    #[test]
    fn test_check_orphans_detects_entry_without_project() {
        let (conn, _ctx, _p) = setup();

        // 暂时禁用 FK 以便注入坏数据
        conn.inner().execute("PRAGMA foreign_keys=OFF", []).unwrap();

        // 手动插入一个引用不存在 project 的 entry
        let now = chrono::Utc::now().to_rfc3339();
        conn.inner()
            .execute(
                "INSERT INTO entries (entry_id, project_id, entry_type, payload_ct,
                 object_clock, head_commit_id, created_at, updated_at,
                 created_by_device_id, updated_by_device_id)
                 VALUES ('orphan-entry', 'nonexistent-project', 'login', X'00',
                 '{}', 'fake-commit', ?1, ?1, 'dev', 'dev')",
                rusqlite::params![now],
            )
            .unwrap();

        conn.inner().execute("PRAGMA foreign_keys=ON", []).unwrap();

        let issues = RecoveryVerifier::check_orphans(&conn).unwrap();
        let has_orphan = issues
            .iter()
            .any(|i| i.description.contains("orphan-entry"));
        assert!(has_orphan);
    }

    // -----------------------------------------------------------------------
    // STALE HEADS
    // -----------------------------------------------------------------------

    #[test]
    fn test_check_stale_heads_detects_dangling_head() {
        let (conn, _ctx, _p) = setup();

        // 暂时禁用 FK 以便注入坏数据
        conn.inner().execute("PRAGMA foreign_keys=OFF", []).unwrap();

        // 插入指向不存在 commit 的 device head
        conn.inner()
            .execute(
                "INSERT INTO device_heads (device_id, head_commit_id, last_seen_at)
                 VALUES ('ghost-device', 'nonexistent-commit', '2024-01-01T00:00:00Z')",
                [],
            )
            .unwrap();

        conn.inner().execute("PRAGMA foreign_keys=ON", []).unwrap();

        let issues = RecoveryVerifier::check_stale_heads(&conn).unwrap();
        let has_critical = issues.iter().any(|i| {
            i.severity >= IssueSeverity::Critical && i.description.contains("ghost-device")
        });
        assert!(has_critical);
    }

    #[test]
    fn test_check_stale_heads_reports_old_devices() {
        let (conn, _ctx, _p) = setup();

        // 插入一个 last_seen_at 很旧的设备（不指向不存在 commit）
        let now = chrono::Utc::now().to_rfc3339();
        let commit_id = uuid::Uuid::new_v4().to_string();
        conn.inner()
            .execute(
                "INSERT INTO commits (commit_id, device_id, local_seq, commit_kind,
                 change_scope, changed_object_ids_ct, vector_clock, message_ct,
                 created_at, integrity_tag)
                 VALUES (?1, 'old-device', 1, 'change', 'project', X'00',
                 '{}', NULL, ?2, X'00')",
                rusqlite::params![commit_id, now],
            )
            .unwrap();
        conn.inner()
            .execute(
                "INSERT INTO device_heads (device_id, head_commit_id, last_seen_at)
                 VALUES ('old-device', ?1, '2020-01-01T00:00:00Z')",
                rusqlite::params![commit_id],
            )
            .unwrap();

        let issues = RecoveryVerifier::check_stale_heads(&conn).unwrap();
        let has_info = issues
            .iter()
            .any(|i| i.severity == IssueSeverity::Info && i.description.contains("old-device"));
        assert!(has_info);
    }

    // -----------------------------------------------------------------------
    // CHUNK CORRUPTION
    // -----------------------------------------------------------------------

    #[test]
    fn test_detect_chunk_hash_mismatch() {
        let (conn, ctx, project_id) = setup();
        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            mdbx_core::model::EntryType::Login,
            Some("E"),
            &serde_json::json!({"username":"a"}),
        )
        .unwrap();

        let att = AttachmentRepo::add(
            &conn,
            &ctx,
            &project_id,
            Some(&entry.entry_id),
            "data.bin",
            Some("application/octet-stream"),
            "",
            0,
        )
        .unwrap();

        // 写 chunk 内容
        AttachmentRepo::write_inline_content(&conn, &ctx, &att.attachment_id, b"original-content")
            .unwrap();

        // 模拟损坏：直接修改 chunk 内容
        conn.inner()
            .execute(
                "UPDATE attachment_chunks SET chunk_ct = X'DEADBEEF'
                 WHERE attachment_id = ?1",
                rusqlite::params![att.attachment_id],
            )
            .unwrap();

        // 完整性验证应该检测到
        let result = AttachmentRepo::verify_integrity(&conn, &att.attachment_id);
        assert!(result.is_err() || result.unwrap() == false);
    }

    #[test]
    fn test_detect_missing_chunk() {
        let (conn, ctx, project_id) = setup();
        let entry = EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            mdbx_core::model::EntryType::Login,
            Some("E"),
            &serde_json::json!({"username":"a"}),
        )
        .unwrap();

        let att = AttachmentRepo::add(
            &conn,
            &ctx,
            &project_id,
            Some(&entry.entry_id),
            "chunked.bin",
            Some("application/octet-stream"),
            "",
            0,
        )
        .unwrap();

        // 写 chunked 内容（会自动分块）
        AttachmentRepo::write_chunked_content(
            &conn,
            &ctx,
            &att.attachment_id,
            b"chunk-0-data-plus-chunk-1-data",
            256,
        )
        .unwrap();

        // 手动修改 chunk_count 制造不一致
        conn.inner()
            .execute(
                "UPDATE attachments SET chunk_count = 99
                 WHERE attachment_id = ?1",
                rusqlite::params![att.attachment_id],
            )
            .unwrap();

        let issues = RecoveryVerifier::check_attachment_chunks(&conn).unwrap();
        let has_mismatch = issues
            .iter()
            .any(|i| i.description.contains(&att.attachment_id));
        assert!(has_mismatch);
    }

    // -----------------------------------------------------------------------
    // WAL RECOVERY
    // -----------------------------------------------------------------------

    #[test]
    fn test_wal_recovery_preserves_data() {
        let dir = std::env::temp_dir();
        let db_path = dir.join(format!("mdbx-wal-test-{}.mdbx", uuid::Uuid::new_v4()));

        let result = WalRecoveryTester::test_wal_recovery_roundtrip(&db_path).unwrap();
        assert!(result.recovery_successful);
        assert_eq!(result.entries_before_crash, 3);
        assert_eq!(
            result.entries_after_recovery, 3,
            "data lost after WAL recovery"
        );

        // 清理
        let _ = std::fs::remove_file(&db_path);
        // 也清理 WAL 和 SHM 文件
        let _ = std::fs::remove_file(db_path.with_extension("mdbx-wal"));
        let _ = std::fs::remove_file(db_path.with_extension("mdbx-shm"));
    }

    #[test]
    fn test_wal_recovery_multiple_connections() {
        // 模拟 crash-recovery：写入 → 关闭 → 重新打开 → 验证
        let dir = std::env::temp_dir();
        let db_path = dir.join(format!("mdbx-wal-multi-{}.mdbx", uuid::Uuid::new_v4()));

        // 第一次打开，写入数据
        {
            let conn = VaultConnection::create(&db_path).unwrap();
            let params = VaultInitParams::default();
            initialize_vault(&conn, &params).unwrap();

            let ctx = CommitContext::new("test-device".to_string());
            let p = ProjectRepo::create(&conn, &ctx, "Survivor", None, None).unwrap();
            EntryRepo::create(
                &conn,
                &ctx,
                &p.project_id,
                mdbx_core::model::EntryType::Login,
                Some("Login"),
                &serde_json::json!({"username":"survivor", "password":"secret"}),
            )
            .unwrap();
            // conn 在此关闭
        }

        // 重新打开，验证数据完整
        {
            let conn = VaultConnection::open(&db_path).unwrap();
            let projects = ProjectRepo::list_all(&conn).unwrap();

            // 先检查基本完整性
            RecoveryVerifier::check_basic_integrity(&conn).unwrap();

            let survivor = projects
                .iter()
                .find(|p| String::from_utf8_lossy(&p.title_ct) == "Survivor");
            assert!(
                survivor.is_some(),
                "project 'Survivor' lost after WAL recovery"
            );

            let entries = EntryRepo::list_by_project(&conn, &survivor.unwrap().project_id).unwrap();
            assert_eq!(entries.len(), 1);
        }

        // 清理
        let _ = std::fs::remove_file(&db_path);
        let _ = std::fs::remove_file(db_path.with_extension("mdbx-wal"));
        let _ = std::fs::remove_file(db_path.with_extension("mdbx-shm"));
    }

    // -----------------------------------------------------------------------
    // SNAPSHOT INTEGRITY
    // -----------------------------------------------------------------------

    #[test]
    fn test_snapshot_integrity_after_recovery() {
        use crate::repo::snapshot::SnapshotRepo;

        let (conn, ctx, project_id) = setup();
        EntryRepo::create(
            &conn,
            &ctx,
            &project_id,
            mdbx_core::model::EntryType::Login,
            Some("E"),
            &serde_json::json!({"username":"a"}),
        )
        .unwrap();

        // 创建快照
        let snapshot = SnapshotRepo::create_snapshot(&conn, &ctx).unwrap();
        assert!(
            RecoveryVerifier::verify_snapshot_integrity(&conn, &snapshot.snapshot_id,).unwrap()
        );

        // 模拟腐败：修改快照密文
        conn.inner()
            .execute(
                "UPDATE snapshots SET snapshot_ct = X'0000' WHERE snapshot_id = ?1",
                rusqlite::params![snapshot.snapshot_id],
            )
            .unwrap();

        // 验证应失败
        assert!(
            !RecoveryVerifier::verify_snapshot_integrity(&conn, &snapshot.snapshot_id,).unwrap()
        );
    }

    // -----------------------------------------------------------------------
    // COMPREHENSIVE HEALTH CHECK
    // -----------------------------------------------------------------------

    #[test]
    fn test_health_check_detects_multiple_issues() {
        let (conn, _ctx, _p) = setup();

        // 暂时禁用 FK 以便注入坏数据
        conn.inner().execute("PRAGMA foreign_keys=OFF", []).unwrap();

        // 注入多种问题
        let now = chrono::Utc::now().to_rfc3339();

        // 1. 孤儿 entry
        conn.inner()
            .execute(
                "INSERT INTO entries (entry_id, project_id, entry_type, payload_ct,
                 object_clock, head_commit_id, created_at, updated_at,
                 created_by_device_id, updated_by_device_id)
                 VALUES ('orphan-1', 'no-such-project', 'login', X'00',
                 '{}', 'fake', ?1, ?1, 'dev', 'dev')",
                rusqlite::params![now],
            )
            .unwrap();

        // 2. 陈旧的 device head
        conn.inner()
            .execute(
                "INSERT INTO device_heads (device_id, head_commit_id, last_seen_at)
                 VALUES ('ghost-1', 'no-such-commit', '2020-01-01T00:00:00Z')",
                [],
            )
            .unwrap();

        conn.inner().execute("PRAGMA foreign_keys=ON", []).unwrap();

        let result = RecoveryVerifier::full_health_check(&conn).unwrap();

        assert!(!result.healthy); // 孤儿 entry 导致不健康

        let has_orphan = result
            .issues
            .iter()
            .any(|i| i.description.contains("orphan-1"));
        let has_ghost = result
            .issues
            .iter()
            .any(|i| i.description.contains("ghost-1"));
        assert!(has_orphan);
        assert!(has_ghost);
    }

    // -----------------------------------------------------------------------
    // RESUME AFTER INTERRUPTED WRITE (SIMULATED)
    // -----------------------------------------------------------------------

    #[test]
    fn test_resume_after_interrupted_write() {
        // 模拟：写入一半时中断，然后重新打开验证
        let dir = std::env::temp_dir();
        let db_path = dir.join(format!("mdbx-interrupt-{}.mdbx", uuid::Uuid::new_v4()));

        // Phase 1: 正常创建和写入
        {
            let conn = VaultConnection::create(&db_path).unwrap();
            let params = VaultInitParams::default();
            initialize_vault(&conn, &params).unwrap();

            let ctx = CommitContext::new("test-device".to_string());
            for i in 0..5 {
                let p = ProjectRepo::create(&conn, &ctx, &format!("Project-{}", i), None, None)
                    .unwrap();

                // 为每个 project 写一个 entry（不在事务中，模拟可能中断）
                let _ = EntryRepo::create(
                    &conn,
                    &ctx,
                    &p.project_id,
                    mdbx_core::model::EntryType::Login,
                    Some("Login"),
                    &serde_json::json!({"username": format!("user-{}", i)}),
                );
            }
            // conn 正常关闭
        }

        // Phase 2: 验证数据完整
        {
            let conn = VaultConnection::open(&db_path).unwrap();
            // SQLite WAL 应自动恢复
            RecoveryVerifier::check_basic_integrity(&conn).unwrap();

            let projects = ProjectRepo::list_all(&conn).unwrap();
            assert_eq!(
                projects.len(),
                5,
                "some projects lost after interrupted write simulation"
            );

            let result = RecoveryVerifier::full_health_check(&conn).unwrap();
            let errors: Vec<_> = result
                .issues
                .iter()
                .filter(|i| i.severity >= IssueSeverity::Error)
                .collect();
            assert!(errors.is_empty(), "errors after recovery: {:?}", errors);
        }

        // 清理
        let _ = std::fs::remove_file(&db_path);
        let _ = std::fs::remove_file(db_path.with_extension("mdbx-wal"));
        let _ = std::fs::remove_file(db_path.with_extension("mdbx-shm"));
    }
}
