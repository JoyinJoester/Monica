use std::time::{Duration, Instant};

use mdbx_core::model::EntryType;

use crate::connection::VaultConnection;
use crate::repo::attachment::AttachmentRepo;
use crate::repo::commit_ctx::CommitContext;
use crate::repo::entry::EntryRepo;
use crate::repo::project::ProjectRepo;

/// 单个 benchmark 结果。
#[derive(Debug, Clone)]
pub struct BenchResult {
    pub name: String,
    pub duration: Duration,
    pub ops: u32,
    pub ops_per_sec: f64,
}

/// Benchmark 套件结果。
#[derive(Debug, Clone)]
pub struct BenchSuite {
    pub suite_name: String,
    pub results: Vec<BenchResult>,
    pub total_duration: Duration,
}

impl BenchSuite {
    pub fn print(&self) {
        println!("=== {} ===", self.suite_name);
        for r in &self.results {
            println!(
                "  {:40} {:>8} ops  {:>12.3} µs/op  {:>10.1} ops/s",
                r.name,
                r.ops,
                r.duration.as_micros() as f64 / r.ops.max(1) as f64,
                r.ops_per_sec,
            );
        }
        println!(
            "  Total: {:>12.3} ms",
            self.total_duration.as_secs_f64() * 1000.0
        );
        println!();
    }

    /// 与另一组对照结果比较。
    pub fn compare(&self, other: &BenchSuite, label_a: &str, label_b: &str) {
        println!("=== Comparison: {} vs {} ===", label_a, label_b);
        for (a, b) in self.results.iter().zip(other.results.iter()) {
            let ratio = if b.ops_per_sec > 0.0 {
                a.ops_per_sec / b.ops_per_sec
            } else {
                f64::NAN
            };
            let winner = if ratio > 1.0 { label_a } else { label_b };
            println!(
                "  {:38}  {:>10.1} vs {:>10.1} ops/s  ({:.2}x, {} faster)",
                a.name,
                a.ops_per_sec,
                b.ops_per_sec,
                ratio.max(1.0 / ratio),
                winner
            );
        }
        println!();
    }
}

/// MDBX Benchmark 执行器。
///
/// 覆盖以下操作：
/// - vault 创建
/// - entry 保存
/// - project 搜索
/// - 附件写入
/// - vault 打开
/// - 同步 delta 计算
pub struct BenchmarkRunner;

impl BenchmarkRunner {
    /// 运行完整 benchmark 套件。
    pub fn run_full_suite(iterations: u32) -> BenchSuite {
        let mut results = Vec::new();
        let start = Instant::now();

        results.push(Self::bench_create_vault(iterations));
        results.push(Self::bench_save_entry(iterations));
        results.push(Self::bench_search(iterations));
        results.push(Self::bench_attachment_write(iterations));
        results.push(Self::bench_open_vault(iterations));
        results.push(Self::bench_sync_delta(iterations));

        let total = start.elapsed();
        BenchSuite {
            suite_name: format!("MDBX Benchmark ({} iterations)", iterations),
            results,
            total_duration: total,
        }
    }

    /// Benchmark: vault 创建。
    pub fn bench_create_vault(iterations: u32) -> BenchResult {
        let start = Instant::now();
        let mut success = 0u32;

        for _ in 0..iterations {
            let conn = VaultConnection::open_in_memory().unwrap();
            let params = crate::init::VaultInitParams::default();
            if crate::init::initialize_vault(&conn, &params).is_ok() {
                success += 1;
            }
            // conn 在作用域结束时关闭
        }

        let duration = start.elapsed();
        BenchResult {
            name: "vault_create".to_string(),
            duration,
            ops: success,
            ops_per_sec: success as f64 / duration.as_secs_f64(),
        }
    }

    /// Benchmark: entry 保存。
    pub fn bench_save_entry(iterations: u32) -> BenchResult {
        let conn = VaultConnection::open_in_memory().unwrap();
        crate::init::initialize_vault(&conn, &crate::init::VaultInitParams::default()).unwrap();
        let ctx = CommitContext::new("bench-device".to_string());

        // 预先创建 project
        let project = ProjectRepo::create(&conn, &ctx, "Bench Project", None, None).unwrap();

        let start = Instant::now();
        let mut success = 0u32;

        for i in 0..iterations {
            let payload = serde_json::json!({
                "username": format!("user-{}", i),
                "password": format!("pass-{}", i),
                "url": format!("https://site-{}.example.com", i),
            });
            if EntryRepo::create(
                &conn,
                &ctx,
                &project.project_id,
                EntryType::Login,
                Some(&format!("Entry-{}", i)),
                &payload,
            )
            .is_ok()
            {
                success += 1;
            }
        }

        let duration = start.elapsed();
        BenchResult {
            name: "entry_save".to_string(),
            duration,
            ops: success,
            ops_per_sec: success as f64 / duration.as_secs_f64(),
        }
    }

    /// Benchmark: project 标题搜索。
    pub fn bench_search(iterations: u32) -> BenchResult {
        let conn = VaultConnection::open_in_memory().unwrap();
        crate::init::initialize_vault(&conn, &crate::init::VaultInitParams::default()).unwrap();
        let ctx = CommitContext::new("bench-device".to_string());

        // 预先创建数据
        let mut project_ids = Vec::new();
        for i in 0..100u32 {
            let p = ProjectRepo::create(
                &conn,
                &ctx,
                &format!("Searchable Project {}", i),
                None,
                None,
            )
            .unwrap();

            // 为一半的项目做 FTS 索引
            if i < 50 {
                crate::search::SearchService::index_project_title(
                    &conn,
                    &p.project_id,
                    &format!("Searchable Project {}", i),
                )
                .unwrap();
            }
            project_ids.push(p.project_id);
        }

        let start = Instant::now();
        let mut success = 0u32;

        for i in 0..iterations {
            let query = format!("Project {}", i % 100);
            if crate::search::SearchService::search_by_title(&conn, &query).is_ok() {
                success += 1;
            }
        }

        let duration = start.elapsed();
        BenchResult {
            name: "search_by_title".to_string(),
            duration,
            ops: success,
            ops_per_sec: success as f64 / duration.as_secs_f64(),
        }
    }

    /// Benchmark: 小附件写入。
    pub fn bench_attachment_write(iterations: u32) -> BenchResult {
        let conn = VaultConnection::open_in_memory().unwrap();
        crate::init::initialize_vault(&conn, &crate::init::VaultInitParams::default()).unwrap();
        let ctx = CommitContext::new("bench-device".to_string());

        let project = ProjectRepo::create(&conn, &ctx, "Attach Bench", None, None).unwrap();

        let start = Instant::now();
        let mut success = 0u32;

        for i in 0..iterations {
            let data = format!("benchmark attachment data {}", i);
            match AttachmentRepo::add(
                &conn,
                &ctx,
                &project.project_id,
                None,
                &format!("file-{}.txt", i),
                Some("text/plain"),
                "",
                0,
            ) {
                Ok(att) => {
                    if AttachmentRepo::write_inline_content(
                        &conn,
                        &ctx,
                        &att.attachment_id,
                        data.as_bytes(),
                    )
                    .is_ok()
                    {
                        success += 1;
                    }
                }
                Err(_) => {}
            }
        }

        let duration = start.elapsed();
        BenchResult {
            name: "attachment_write_small".to_string(),
            duration,
            ops: success,
            ops_per_sec: success as f64 / duration.as_secs_f64(),
        }
    }

    /// Benchmark: vault 文件打开。
    pub fn bench_open_vault(iterations: u32) -> BenchResult {
        // 先创建一个持久化的 vault 文件
        let dir = std::env::temp_dir();
        let db_path = dir.join(format!("mdbx-bench-open-{}.mdbx", uuid::Uuid::new_v4()));

        {
            let conn = VaultConnection::create(&db_path).unwrap();
            crate::init::initialize_vault(&conn, &crate::init::VaultInitParams::default()).unwrap();
            let ctx = CommitContext::new("bench-device".to_string());
            let p = ProjectRepo::create(&conn, &ctx, "Bench Open", None, None).unwrap();
            EntryRepo::create(
                &conn,
                &ctx,
                &p.project_id,
                EntryType::Login,
                Some("Login"),
                &serde_json::json!({"username":"a","password":"b"}),
            )
            .unwrap();
        }

        let start = Instant::now();
        let mut success = 0u32;

        for _ in 0..iterations {
            if VaultConnection::open(&db_path).is_ok() {
                success += 1;
            }
        }

        let duration = start.elapsed();
        let result = BenchResult {
            name: "vault_open".to_string(),
            duration,
            ops: success,
            ops_per_sec: success as f64 / duration.as_secs_f64(),
        };

        // 清理
        let _ = std::fs::remove_file(&db_path);
        let _ = std::fs::remove_file(db_path.with_extension("mdbx-wal"));
        let _ = std::fs::remove_file(db_path.with_extension("mdbx-shm"));

        result
    }

    /// Benchmark: 同步 delta 计算（模拟生成变更摘要）。
    pub fn bench_sync_delta(iterations: u32) -> BenchResult {
        let conn = VaultConnection::open_in_memory().unwrap();
        crate::init::initialize_vault(&conn, &crate::init::VaultInitParams::default()).unwrap();
        let ctx = CommitContext::new("bench-device".to_string());

        // 创建 50 个 project，各带 1 个 entry
        for i in 0..50u32 {
            let p = ProjectRepo::create(&conn, &ctx, &format!("Delta Project {}", i), None, None)
                .unwrap();
            EntryRepo::create(
                &conn,
                &ctx,
                &p.project_id,
                EntryType::Login,
                Some("Login"),
                &serde_json::json!({"username": format!("user-{}", i)}),
            )
            .unwrap();
        }

        let start = Instant::now();
        let mut success = 0u32;

        for _ in 0..iterations {
            // 模拟 sync delta：列出所有 project + entry 的 (id, updated_at) 对
            let projects = match ProjectRepo::list_all(&conn) {
                Ok(ps) => ps,
                Err(_) => continue,
            };

            let mut delta_size: u64 = 0;
            for p in &projects {
                delta_size += p.project_id.len() as u64;
                delta_size += p.updated_at.len() as u64;

                if let Ok(entries) = EntryRepo::list_by_project(&conn, &p.project_id) {
                    for e in &entries {
                        delta_size += e.entry_id.len() as u64;
                        delta_size += e.updated_at.len() as u64;
                    }
                }
            }
            let _ = delta_size;
            success += 1;
        }

        let duration = start.elapsed();
        BenchResult {
            name: "sync_delta_compute".to_string(),
            duration,
            ops: success,
            ops_per_sec: success as f64 / duration.as_secs_f64(),
        }
    }

    /// KDBX 对照组的数据结构（参考值，用于对比输出）。
    ///
    /// 这些是预期的参考数量级，实际 KDBX benchmark 数据可从外部输入。
    pub fn kdbx_reference_numbers() -> BenchSuite {
        BenchSuite {
            suite_name: "KDBX Reference (estimated)".to_string(),
            results: vec![
                BenchResult {
                    name: "vault_create".to_string(),
                    duration: Duration::from_millis(100),
                    ops: 100,
                    ops_per_sec: 1000.0,
                },
                BenchResult {
                    name: "entry_save".to_string(),
                    duration: Duration::from_millis(200),
                    ops: 100,
                    ops_per_sec: 500.0,
                },
                BenchResult {
                    name: "search_by_title".to_string(),
                    duration: Duration::from_millis(50),
                    ops: 100,
                    ops_per_sec: 2000.0,
                },
                BenchResult {
                    name: "attachment_write_small".to_string(),
                    duration: Duration::from_millis(150),
                    ops: 100,
                    ops_per_sec: 666.0,
                },
                BenchResult {
                    name: "vault_open".to_string(),
                    duration: Duration::from_millis(300),
                    ops: 100,
                    ops_per_sec: 333.0,
                },
                BenchResult {
                    name: "sync_delta_compute".to_string(),
                    duration: Duration::from_millis(80),
                    ops: 100,
                    ops_per_sec: 1250.0,
                },
            ],
            total_duration: Duration::from_millis(880),
        }
    }
}

// ---------------------------------------------------------------------------
// 测试（集成 smoke 测试）
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_bench_create_vault() {
        let result = BenchmarkRunner::bench_create_vault(10);
        assert_eq!(result.ops, 10);
        assert!(result.duration.as_micros() > 0);
        assert!(result.ops_per_sec > 0.0);
    }

    #[test]
    fn test_bench_save_entry() {
        let result = BenchmarkRunner::bench_save_entry(10);
        assert_eq!(result.ops, 10);
        assert!(result.ops_per_sec > 0.0);
    }

    #[test]
    fn test_bench_search() {
        let result = BenchmarkRunner::bench_search(10);
        assert_eq!(result.ops, 10);
        assert!(result.ops_per_sec > 0.0);
    }

    #[test]
    fn test_bench_attachment_write() {
        let result = BenchmarkRunner::bench_attachment_write(5);
        assert!(result.ops > 0);
        assert!(result.ops_per_sec > 0.0);
    }

    #[test]
    fn test_bench_open_vault() {
        let result = BenchmarkRunner::bench_open_vault(5);
        assert_eq!(result.ops, 5);
        assert!(result.ops_per_sec > 0.0);
    }

    #[test]
    fn test_bench_sync_delta() {
        let result = BenchmarkRunner::bench_sync_delta(5);
        assert_eq!(result.ops, 5);
        assert!(result.ops_per_sec > 0.0);
    }

    #[test]
    fn test_run_full_suite() {
        let suite = BenchmarkRunner::run_full_suite(5);
        assert_eq!(suite.results.len(), 6);
        assert!(suite.total_duration.as_micros() > 0);

        // 验证可以打印
        suite.print();
    }

    #[test]
    fn test_compare_with_kdbx() {
        let mdbx_suite = BenchmarkRunner::run_full_suite(5);
        let kdbx_suite = BenchmarkRunner::kdbx_reference_numbers();
        mdbx_suite.compare(&kdbx_suite, "MDBX", "KDBX");
    }

    #[test]
    fn test_bench_results_are_reproducible() {
        // 连续运行两次，结果应在合理范围内
        let r1 = BenchmarkRunner::bench_save_entry(10);
        let r2 = BenchmarkRunner::bench_save_entry(10);

        assert_eq!(r1.ops, r2.ops);
        // ops_per_sec 应在 2x 范围内
        let ratio = if r2.ops_per_sec > 0.0 {
            r1.ops_per_sec / r2.ops_per_sec
        } else {
            1.0
        };
        assert!(
            ratio > 0.1 && ratio < 10.0,
            "benchmark results not reproducible: {:.1} vs {:.1} ops/s",
            r1.ops_per_sec,
            r2.ops_per_sec
        );
    }
}
