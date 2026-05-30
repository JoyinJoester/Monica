use mdbx_core::model::EntryType;

#[cfg(test)]
use mdbx_core::model::Project;
use rusqlite::params;

use crate::connection::VaultConnection;
use crate::error::{StorageError, StorageResult};

/// 搜索结果条目。
#[derive(Debug, Clone)]
pub struct SearchResult {
    pub project_id: String,
    pub title: String,
    pub summary: String,
    pub entry_types: Vec<String>,
    pub tags: Vec<String>,
    pub updated_at: String,
    /// FTS 相关性得分（仅标题匹配时有值）
    pub relevance_score: Option<f64>,
}

/// 搜索服务。
///
/// 支持：
/// - 项目标题全文搜索（FTS5）
/// - 标签搜索
/// - Entry 类型筛选
/// - 更新时间范围筛选
/// - 组合查询
pub struct SearchService;

impl SearchService {
    // -----------------------------------------------------------------------
    // INDEX — 索引维护
    // -----------------------------------------------------------------------

    /// 为项目标题建立 FTS 索引。
    ///
    /// 应在项目创建或标题更新后调用。
    pub fn index_project_title(
        conn: &VaultConnection,
        project_id: &str,
        title: &str,
    ) -> StorageResult<()> {
        // 先删除旧索引（如果存在）
        conn.inner()
            .execute(
                "DELETE FROM project_titles_fts WHERE project_id = ?1",
                params![project_id],
            )
            .map_err(StorageError::Database)?;

        // 插入新索引
        conn.inner()
            .execute(
                "INSERT INTO project_titles_fts (project_id, title) VALUES (?1, ?2)",
                params![project_id, title],
            )
            .map_err(StorageError::Database)?;

        Ok(())
    }

    /// 移除项目的 FTS 索引（项目删除时调用）。
    pub fn remove_project_index(conn: &VaultConnection, project_id: &str) -> StorageResult<()> {
        conn.inner()
            .execute(
                "DELETE FROM project_titles_fts WHERE project_id = ?1",
                params![project_id],
            )
            .map_err(StorageError::Database)?;

        // 同时清理标签
        conn.inner()
            .execute(
                "DELETE FROM project_tags WHERE project_id = ?1",
                params![project_id],
            )
            .map_err(StorageError::Database)?;

        Ok(())
    }

    /// 批量重建所有项目的 FTS 索引。
    pub fn rebuild_fts_index(conn: &VaultConnection) -> StorageResult<u32> {
        // 清空 FTS 索引
        conn.inner()
            .execute("DELETE FROM project_titles_fts", [])
            .map_err(StorageError::Database)?;

        // 重新索引所有未删除的项目
        let mut stmt = conn
            .inner()
            .prepare("SELECT project_id, title_ct FROM projects WHERE deleted = 0")
            .map_err(StorageError::Database)?;

        let rows: Vec<(String, Vec<u8>)> = stmt
            .query_map([], |row| Ok((row.get(0)?, row.get(1)?)))
            .map_err(StorageError::Database)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(StorageError::Database)?;

        let mut count: u32 = 0;
        for (project_id, title_ct) in &rows {
            let title = String::from_utf8_lossy(title_ct);
            Self::index_project_title(conn, project_id, &title)?;
            count += 1;
        }

        Ok(count)
    }

    // -----------------------------------------------------------------------
    // TAGS — 标签管理
    // -----------------------------------------------------------------------

    /// 为项目添加标签。
    pub fn add_tag(conn: &VaultConnection, project_id: &str, tag: &str) -> StorageResult<()> {
        let trimmed = tag.trim().to_lowercase();
        if trimmed.is_empty() {
            return Err(StorageError::Validation(
                "tag must not be empty".to_string(),
            ));
        }

        conn.inner()
            .execute(
                "INSERT OR IGNORE INTO project_tags (project_id, tag) VALUES (?1, ?2)",
                params![project_id, trimmed],
            )
            .map_err(StorageError::Database)?;

        Ok(())
    }

    /// 为项目批量设置标签（替换现有标签）。
    pub fn set_tags(
        conn: &VaultConnection,
        project_id: &str,
        tags: &[String],
    ) -> StorageResult<()> {
        // 在事务中替换
        conn.inner()
            .execute("BEGIN IMMEDIATE", [])
            .map_err(StorageError::Database)?;

        let result = (|| -> StorageResult<()> {
            conn.inner()
                .execute(
                    "DELETE FROM project_tags WHERE project_id = ?1",
                    params![project_id],
                )
                .map_err(StorageError::Database)?;

            for tag in tags {
                Self::add_tag(conn, project_id, tag)?;
            }
            Ok(())
        })();

        match result {
            Ok(()) => {
                conn.inner()
                    .execute("COMMIT", [])
                    .map_err(StorageError::Database)?;
                Ok(())
            }
            Err(e) => {
                let _ = conn.inner().execute("ROLLBACK", []);
                Err(e)
            }
        }
    }

    /// 移除项目的指定标签。
    pub fn remove_tag(conn: &VaultConnection, project_id: &str, tag: &str) -> StorageResult<()> {
        let affected = conn
            .inner()
            .execute(
                "DELETE FROM project_tags WHERE project_id = ?1 AND tag = ?2",
                params![project_id, tag.trim().to_lowercase()],
            )
            .map_err(StorageError::Database)?;

        if affected == 0 {
            return Err(StorageError::NotFound(format!(
                "tag '{}' not found on project '{}'",
                tag, project_id
            )));
        }
        Ok(())
    }

    /// 列出项目的所有标签。
    pub fn list_tags(conn: &VaultConnection, project_id: &str) -> StorageResult<Vec<String>> {
        let mut stmt = conn
            .inner()
            .prepare("SELECT tag FROM project_tags WHERE project_id = ?1 ORDER BY tag")
            .map_err(StorageError::Database)?;

        let tags: Vec<String> = stmt
            .query_map(params![project_id], |row| row.get(0))
            .map_err(StorageError::Database)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(StorageError::Database)?;

        Ok(tags)
    }

    /// 列出所有已使用的标签。
    pub fn list_all_tags(conn: &VaultConnection) -> StorageResult<Vec<(String, u32)>> {
        let mut stmt = conn
            .inner()
            .prepare(
                "SELECT tag, COUNT(*) as cnt FROM project_tags GROUP BY tag ORDER BY cnt DESC, tag",
            )
            .map_err(StorageError::Database)?;

        let tags: Vec<(String, u32)> = stmt
            .query_map([], |row| Ok((row.get(0)?, row.get(1)?)))
            .map_err(StorageError::Database)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(StorageError::Database)?;

        Ok(tags)
    }

    // -----------------------------------------------------------------------
    // SEARCH — 查询
    // -----------------------------------------------------------------------

    /// 全文搜索项目标题。
    ///
    /// 使用 FTS5 进行全文检索，按相关性排序。如果 FTS5 不可用则回退到 LIKE。
    pub fn search_by_title(
        conn: &VaultConnection,
        query: &str,
    ) -> StorageResult<Vec<SearchResult>> {
        if query.trim().is_empty() {
            return Ok(Vec::new());
        }

        // 尝试 FTS5，失败则回退到 LIKE
        let rows = match Self::search_title_fts(conn, query) {
            Ok(results) if !results.is_empty() => results,
            _ => Self::search_title_like(conn, query)?,
        };

        Self::enrich_results(conn, &rows)
    }

    /// FTS5 标题搜索。
    fn search_title_fts(conn: &VaultConnection, query: &str) -> StorageResult<Vec<SearchResult>> {
        // 将用户查询转换为 FTS5 查询语法
        let fts_query = Self::build_fts_query(query);

        let mut stmt = conn
            .inner()
            .prepare(
                "SELECT p.project_id, p.title_ct, p.summary_ct, p.updated_at,
                        fts.rank
                 FROM project_titles_fts fts
                 JOIN projects p ON p.project_id = fts.project_id
                 WHERE p.deleted = 0
                   AND project_titles_fts MATCH ?1
                 ORDER BY fts.rank
                 LIMIT 50",
            )
            .map_err(StorageError::Database)?;

        let rows: Vec<SearchResult> = stmt
            .query_map(params![fts_query], |row| {
                let project_id: String = row.get(0)?;
                let title_ct: Vec<u8> = row.get(1)?;
                let summary_ct: Option<Vec<u8>> = row.get(2)?;
                let updated_at: String = row.get(3)?;
                let rank: f64 = row.get(4)?;

                Ok(SearchResult {
                    project_id,
                    title: String::from_utf8_lossy(&title_ct).to_string(),
                    summary: summary_ct
                        .map(|b| String::from_utf8_lossy(&b).to_string())
                        .unwrap_or_default(),
                    entry_types: Vec::new(),
                    tags: Vec::new(),
                    updated_at,
                    relevance_score: Some(-rank), // FTS5 rank 越低越好
                })
            })
            .map_err(StorageError::Database)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(StorageError::Database)?;

        Ok(rows)
    }

    /// LIKE 回退搜索。
    fn search_title_like(conn: &VaultConnection, query: &str) -> StorageResult<Vec<SearchResult>> {
        let pattern = format!("%{}%", query.trim().replace('%', "\\%").replace('_', "\\_"));

        let mut stmt = conn
            .inner()
            .prepare(
                "SELECT project_id, title_ct, summary_ct, updated_at
                 FROM projects
                 WHERE deleted = 0
                   AND CAST(title_ct AS TEXT) LIKE ?1 ESCAPE '\\'
                 ORDER BY updated_at DESC
                 LIMIT 50",
            )
            .map_err(StorageError::Database)?;

        let rows: Vec<SearchResult> = stmt
            .query_map(params![pattern], |row| {
                let project_id: String = row.get(0)?;
                let title_ct: Vec<u8> = row.get(1)?;
                let summary_ct: Option<Vec<u8>> = row.get(2)?;
                let updated_at: String = row.get(3)?;

                Ok(SearchResult {
                    project_id,
                    title: String::from_utf8_lossy(&title_ct).to_string(),
                    summary: summary_ct
                        .map(|b| String::from_utf8_lossy(&b).to_string())
                        .unwrap_or_default(),
                    entry_types: Vec::new(),
                    tags: Vec::new(),
                    updated_at,
                    relevance_score: None,
                })
            })
            .map_err(StorageError::Database)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(StorageError::Database)?;

        Ok(rows)
    }

    /// 按标签搜索项目。
    pub fn search_by_tag(conn: &VaultConnection, tag: &str) -> StorageResult<Vec<SearchResult>> {
        let mut stmt = conn
            .inner()
            .prepare(
                "SELECT p.project_id, p.title_ct, p.summary_ct, p.updated_at
                 FROM projects p
                 JOIN project_tags pt ON pt.project_id = p.project_id
                 WHERE p.deleted = 0 AND pt.tag = ?1
                 ORDER BY p.updated_at DESC
                 LIMIT 50",
            )
            .map_err(StorageError::Database)?;

        let rows: Vec<SearchResult> = stmt
            .query_map(params![tag.trim().to_lowercase()], |row| {
                let project_id: String = row.get(0)?;
                let title_ct: Vec<u8> = row.get(1)?;
                let summary_ct: Option<Vec<u8>> = row.get(2)?;
                let updated_at: String = row.get(3)?;

                Ok(SearchResult {
                    project_id,
                    title: String::from_utf8_lossy(&title_ct).to_string(),
                    summary: summary_ct
                        .map(|b| String::from_utf8_lossy(&b).to_string())
                        .unwrap_or_default(),
                    entry_types: Vec::new(),
                    tags: Vec::new(),
                    updated_at,
                    relevance_score: None,
                })
            })
            .map_err(StorageError::Database)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(StorageError::Database)?;

        Self::enrich_results(conn, &rows)
    }

    /// 按 entry 类型搜索。
    pub fn search_by_entry_type(
        conn: &VaultConnection,
        entry_type: EntryType,
    ) -> StorageResult<Vec<SearchResult>> {
        let mut stmt = conn
            .inner()
            .prepare(
                "SELECT DISTINCT p.project_id, p.title_ct, p.summary_ct, p.updated_at
                 FROM projects p
                 JOIN entries e ON e.project_id = p.project_id
                 WHERE p.deleted = 0
                   AND e.deleted = 0
                   AND e.entry_type = ?1
                 ORDER BY p.updated_at DESC
                 LIMIT 50",
            )
            .map_err(StorageError::Database)?;

        let rows: Vec<SearchResult> = stmt
            .query_map(params![entry_type.to_string()], |row| {
                let project_id: String = row.get(0)?;
                let title_ct: Vec<u8> = row.get(1)?;
                let summary_ct: Option<Vec<u8>> = row.get(2)?;
                let updated_at: String = row.get(3)?;

                Ok(SearchResult {
                    project_id,
                    title: String::from_utf8_lossy(&title_ct).to_string(),
                    summary: summary_ct
                        .map(|b| String::from_utf8_lossy(&b).to_string())
                        .unwrap_or_default(),
                    entry_types: Vec::new(),
                    tags: Vec::new(),
                    updated_at,
                    relevance_score: None,
                })
            })
            .map_err(StorageError::Database)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(StorageError::Database)?;

        Self::enrich_results(conn, &rows)
    }

    /// 按更新时间范围搜索。
    ///
    /// `from` 和 `to` 为 ISO 8601 格式的时间字符串。
    pub fn search_by_date_range(
        conn: &VaultConnection,
        from: &str,
        to: &str,
    ) -> StorageResult<Vec<SearchResult>> {
        let mut stmt = conn
            .inner()
            .prepare(
                "SELECT project_id, title_ct, summary_ct, updated_at
                 FROM projects
                 WHERE deleted = 0
                   AND updated_at >= ?1
                   AND updated_at <= ?2
                 ORDER BY updated_at DESC
                 LIMIT 100",
            )
            .map_err(StorageError::Database)?;

        let rows: Vec<SearchResult> = stmt
            .query_map(params![from, to], |row| {
                let project_id: String = row.get(0)?;
                let title_ct: Vec<u8> = row.get(1)?;
                let summary_ct: Option<Vec<u8>> = row.get(2)?;
                let updated_at: String = row.get(3)?;

                Ok(SearchResult {
                    project_id,
                    title: String::from_utf8_lossy(&title_ct).to_string(),
                    summary: summary_ct
                        .map(|b| String::from_utf8_lossy(&b).to_string())
                        .unwrap_or_default(),
                    entry_types: Vec::new(),
                    tags: Vec::new(),
                    updated_at,
                    relevance_score: None,
                })
            })
            .map_err(StorageError::Database)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(StorageError::Database)?;

        Self::enrich_results(conn, &rows)
    }

    /// 组合搜索：标题 + 标签 + entry 类型 + 时间范围。
    ///
    /// 所有参数都可选。至少需要一个非空参数。
    pub fn search(
        conn: &VaultConnection,
        title_query: Option<&str>,
        tag: Option<&str>,
        entry_type: Option<EntryType>,
        date_from: Option<&str>,
        date_to: Option<&str>,
    ) -> StorageResult<Vec<SearchResult>> {
        // 构建动态查询
        let mut conditions: Vec<String> = Vec::new();
        let mut param_values: Vec<Box<dyn rusqlite::types::ToSql>> = Vec::new();

        // 基础条件
        conditions.push("p.deleted = 0".to_string());

        // FTS 标题搜索
        let use_fts = title_query.map(|q| !q.trim().is_empty()).unwrap_or(false);
        if use_fts {
            let fts_query = Self::build_fts_query(title_query.unwrap());
            conditions.push(format!(
                "p.project_id IN (SELECT project_id FROM project_titles_fts WHERE project_titles_fts MATCH ?{})",
                param_values.len() + 1
            ));
            param_values.push(Box::new(fts_query));
        }

        // 标签筛选
        if let Some(t) = tag {
            if !t.trim().is_empty() {
                conditions.push(format!(
                    "p.project_id IN (SELECT project_id FROM project_tags WHERE tag = ?{})",
                    param_values.len() + 1
                ));
                param_values.push(Box::new(t.trim().to_lowercase()));
            }
        }

        // Entry 类型筛选
        if let Some(et) = entry_type {
            conditions.push(format!(
                "p.project_id IN (SELECT project_id FROM entries WHERE deleted = 0 AND entry_type = ?{})",
                param_values.len() + 1
            ));
            param_values.push(Box::new(et.to_string()));
        }

        // 时间范围
        if let Some(from) = date_from {
            conditions.push(format!("p.updated_at >= ?{}", param_values.len() + 1));
            param_values.push(Box::new(from.to_string()));
        }
        if let Some(to) = date_to {
            conditions.push(format!("p.updated_at <= ?{}", param_values.len() + 1));
            param_values.push(Box::new(to.to_string()));
        }

        let where_clause = conditions.join(" AND ");
        let sql = format!(
            "SELECT p.project_id, p.title_ct, p.summary_ct, p.updated_at
             FROM projects p
             WHERE {}
             ORDER BY p.updated_at DESC
             LIMIT 50",
            where_clause
        );

        let mut stmt = conn.inner().prepare(&sql).map_err(StorageError::Database)?;

        // 转换参数为引用
        let param_refs: Vec<&dyn rusqlite::types::ToSql> =
            param_values.iter().map(|p| p.as_ref()).collect();

        let rows: Vec<SearchResult> = stmt
            .query_map(param_refs.as_slice(), |row| {
                let project_id: String = row.get(0)?;
                let title_ct: Vec<u8> = row.get(1)?;
                let summary_ct: Option<Vec<u8>> = row.get(2)?;
                let updated_at: String = row.get(3)?;

                Ok(SearchResult {
                    project_id,
                    title: String::from_utf8_lossy(&title_ct).to_string(),
                    summary: summary_ct
                        .map(|b| String::from_utf8_lossy(&b).to_string())
                        .unwrap_or_default(),
                    entry_types: Vec::new(),
                    tags: Vec::new(),
                    updated_at,
                    relevance_score: None,
                })
            })
            .map_err(StorageError::Database)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(StorageError::Database)?;

        // 填充每个结果的 entry_types 和 tags
        let enriched = Self::enrich_results(conn, &rows)?;

        Ok(enriched)
    }

    // -----------------------------------------------------------------------
    // HELPERS
    // -----------------------------------------------------------------------

    /// 为搜索结果填充 entry_types 和 tags。
    fn enrich_results(
        conn: &VaultConnection,
        results: &[SearchResult],
    ) -> StorageResult<Vec<SearchResult>> {
        let mut enriched = Vec::new();
        for r in results {
            let entry_types =
                Self::get_project_entry_types(conn, &r.project_id).unwrap_or_default();
            let tags = Self::list_tags(conn, &r.project_id).unwrap_or_default();
            let mut result = r.clone();
            result.entry_types = entry_types;
            result.tags = tags;
            enriched.push(result);
        }
        Ok(enriched)
    }

    /// 获取项目的所有 entry 类型。
    fn get_project_entry_types(
        conn: &VaultConnection,
        project_id: &str,
    ) -> StorageResult<Vec<String>> {
        let mut stmt = conn
            .inner()
            .prepare(
                "SELECT DISTINCT entry_type FROM entries
                 WHERE project_id = ?1 AND deleted = 0
                 ORDER BY entry_type",
            )
            .map_err(StorageError::Database)?;

        let types: Vec<String> = stmt
            .query_map(params![project_id], |row| row.get(0))
            .map_err(StorageError::Database)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(StorageError::Database)?;

        Ok(types)
    }

    /// 将用户查询转换为 FTS5 查询语法。
    ///
    /// 对每个词添加前缀匹配支持（*通配符）。
    fn build_fts_query(query: &str) -> String {
        let trimmed = query.trim();
        if trimmed.is_empty() {
            return "*".to_string();
        }

        // 如果已经是 FTS5 表达式，直接返回
        if trimmed.contains('"') || trimmed.contains(" AND ") || trimmed.contains(" OR ") {
            return trimmed.to_string();
        }

        // 为每个词添加前缀匹配
        trimmed
            .split_whitespace()
            .map(|word| {
                // 清理特殊字符
                let cleaned: String = word
                    .chars()
                    .filter(|c| c.is_alphanumeric() || c.is_whitespace())
                    .collect();
                if cleaned.is_empty() {
                    String::new()
                } else {
                    format!("\"{}\"*", cleaned.replace('"', ""))
                }
            })
            .filter(|s| !s.is_empty())
            .collect::<Vec<_>>()
            .join(" ")
    }
}

// ---------------------------------------------------------------------------
// 测试
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use crate::init::{initialize_vault, VaultInitParams};
    use crate::repo::commit_ctx::CommitContext;
    use crate::repo::entry::EntryRepo;
    use crate::repo::project::ProjectRepo;

    fn setup() -> (VaultConnection, CommitContext) {
        let conn = VaultConnection::open_in_memory().unwrap();
        let params = VaultInitParams::default();
        initialize_vault(&conn, &params).unwrap();
        let ctx = CommitContext::new("test-device".to_string());
        (conn, ctx)
    }

    fn create_project_with_entry(
        conn: &VaultConnection,
        ctx: &CommitContext,
        title: &str,
        entry_type: EntryType,
        username: &str,
        password: &str,
    ) -> Project {
        let project = ProjectRepo::create(conn, ctx, title, None, None).unwrap();
        let payload = serde_json::json!({
            "username": username,
            "password": password,
            "url": format!("https://{}.example.com", title.to_lowercase()),
        });
        EntryRepo::create(
            conn,
            ctx,
            &project.project_id,
            entry_type,
            Some(title),
            &payload,
        )
        .unwrap();
        project
    }

    // -----------------------------------------------------------------------
    // TITLE SEARCH
    // -----------------------------------------------------------------------

    #[test]
    fn test_index_and_search_title() {
        let (conn, ctx) = setup();
        let p = create_project_with_entry(
            &conn,
            &ctx,
            "My GitHub Account",
            EntryType::Login,
            "alice",
            "s3cret",
        );
        SearchService::index_project_title(&conn, &p.project_id, "My GitHub Account").unwrap();

        let results = SearchService::search_by_title(&conn, "GitHub").unwrap();
        assert!(!results.is_empty());
        assert!(results.iter().any(|r| r.project_id == p.project_id));
    }

    #[test]
    fn test_search_title_no_match() {
        let (conn, ctx) = setup();
        create_project_with_entry(&conn, &ctx, "Bank Account", EntryType::Login, "bob", "pass");
        // 没有索引，FTS 查不到 → 回退 LIKE 可能查到
        // 无索引 + 精确查询
        let results = SearchService::search_by_title(&conn, "NonexistentXYZ").unwrap();
        assert!(results.is_empty());
    }

    #[test]
    fn test_search_empty_query() {
        let (conn, _ctx) = setup();
        let results = SearchService::search_by_title(&conn, "").unwrap();
        assert!(results.is_empty());
    }

    #[test]
    fn test_search_partial_match() {
        let (conn, ctx) = setup();
        let p = create_project_with_entry(
            &conn,
            &ctx,
            "Production Database Server",
            EntryType::Login,
            "admin",
            "root",
        );
        SearchService::index_project_title(&conn, &p.project_id, "Production Database Server")
            .unwrap();

        // 部分词匹配
        let results = SearchService::search_by_title(&conn, "Database").unwrap();
        assert!(!results.is_empty());
    }

    #[test]
    fn test_rebuild_fts_index() {
        let (conn, ctx) = setup();
        create_project_with_entry(&conn, &ctx, "Alpha Site", EntryType::Login, "a", "1");
        create_project_with_entry(&conn, &ctx, "Beta Site", EntryType::Login, "b", "2");
        create_project_with_entry(&conn, &ctx, "Gamma Site", EntryType::Login, "c", "3");

        let count = SearchService::rebuild_fts_index(&conn).unwrap();
        assert_eq!(count, 3);

        let results = SearchService::search_by_title(&conn, "Beta").unwrap();
        assert!(!results.is_empty());
    }

    // -----------------------------------------------------------------------
    // TAG SEARCH
    // -----------------------------------------------------------------------

    #[test]
    fn test_add_and_search_tag() {
        let (conn, ctx) = setup();
        let p = create_project_with_entry(
            &conn,
            &ctx,
            "Work Email",
            EntryType::Login,
            "alice",
            "s3cret",
        );

        SearchService::add_tag(&conn, &p.project_id, "work").unwrap();
        SearchService::add_tag(&conn, &p.project_id, "email").unwrap();

        let results = SearchService::search_by_tag(&conn, "work").unwrap();
        assert!(!results.is_empty());
        assert_eq!(results[0].project_id, p.project_id);
    }

    #[test]
    fn test_list_tags() {
        let (conn, ctx) = setup();
        let p = create_project_with_entry(&conn, &ctx, "Site", EntryType::Login, "user", "pass");

        SearchService::add_tag(&conn, &p.project_id, "important").unwrap();
        SearchService::add_tag(&conn, &p.project_id, "finance").unwrap();

        let tags = SearchService::list_tags(&conn, &p.project_id).unwrap();
        assert_eq!(tags.len(), 2);
        assert!(tags.contains(&"finance".to_string()));
        assert!(tags.contains(&"important".to_string()));
    }

    #[test]
    fn test_set_tags_replaces_existing() {
        let (conn, ctx) = setup();
        let p = create_project_with_entry(&conn, &ctx, "Site", EntryType::Login, "user", "pass");

        SearchService::add_tag(&conn, &p.project_id, "old-tag").unwrap();
        SearchService::set_tags(
            &conn,
            &p.project_id,
            &["new-a".to_string(), "new-b".to_string()],
        )
        .unwrap();

        let tags = SearchService::list_tags(&conn, &p.project_id).unwrap();
        assert_eq!(tags.len(), 2);
        assert!(tags.contains(&"new-a".to_string()));
        assert!(!tags.contains(&"old-tag".to_string()));
    }

    #[test]
    fn test_remove_tag() {
        let (conn, ctx) = setup();
        let p = create_project_with_entry(&conn, &ctx, "Site", EntryType::Login, "user", "pass");

        SearchService::add_tag(&conn, &p.project_id, "temp").unwrap();
        SearchService::remove_tag(&conn, &p.project_id, "temp").unwrap();

        let tags = SearchService::list_tags(&conn, &p.project_id).unwrap();
        assert!(tags.is_empty());
    }

    #[test]
    fn test_remove_nonexistent_tag() {
        let (conn, ctx) = setup();
        let p = create_project_with_entry(&conn, &ctx, "Site", EntryType::Login, "user", "pass");
        let result = SearchService::remove_tag(&conn, &p.project_id, "nonexistent");
        assert!(result.is_err());
    }

    #[test]
    fn test_empty_tag_rejected() {
        let (conn, ctx) = setup();
        let p = create_project_with_entry(&conn, &ctx, "Site", EntryType::Login, "user", "pass");
        let result = SearchService::add_tag(&conn, &p.project_id, "");
        assert!(result.is_err());
    }

    #[test]
    fn test_tag_case_insensitive() {
        let (conn, ctx) = setup();
        let p = create_project_with_entry(&conn, &ctx, "Site", EntryType::Login, "user", "pass");

        SearchService::add_tag(&conn, &p.project_id, "IMPORTANT").unwrap();

        // 用小写也能搜到
        let results = SearchService::search_by_tag(&conn, "important").unwrap();
        assert!(!results.is_empty());
    }

    #[test]
    fn test_list_all_tags() {
        let (conn, ctx) = setup();
        let p1 = create_project_with_entry(&conn, &ctx, "Site A", EntryType::Login, "a", "1");
        let p2 = create_project_with_entry(&conn, &ctx, "Site B", EntryType::Login, "b", "2");

        SearchService::add_tag(&conn, &p1.project_id, "shared").unwrap();
        SearchService::add_tag(&conn, &p2.project_id, "shared").unwrap();
        SearchService::add_tag(&conn, &p1.project_id, "unique").unwrap();

        let all_tags = SearchService::list_all_tags(&conn).unwrap();
        assert_eq!(all_tags.len(), 2);
        // "shared" 出现次数多，排第一
        assert_eq!(all_tags[0].0, "shared");
        assert_eq!(all_tags[0].1, 2);
        assert_eq!(all_tags[1].0, "unique");
        assert_eq!(all_tags[1].1, 1);
    }

    // -----------------------------------------------------------------------
    // ENTRY TYPE SEARCH
    // -----------------------------------------------------------------------

    #[test]
    fn test_search_by_entry_type_login() {
        let (conn, ctx) = setup();
        create_project_with_entry(&conn, &ctx, "Login Site", EntryType::Login, "alice", "pass");
        create_project_with_entry(&conn, &ctx, "Note Site", EntryType::Note, "bob", "");

        let results = SearchService::search_by_entry_type(&conn, EntryType::Login).unwrap();
        assert!(!results.is_empty());
        assert!(results.iter().any(|r| r.title == "Login Site"));
    }

    #[test]
    fn test_search_by_entry_type_note() {
        let (conn, ctx) = setup();
        create_project_with_entry(&conn, &ctx, "A Login", EntryType::Login, "a", "1");
        create_project_with_entry(&conn, &ctx, "A Note", EntryType::Note, "b", "");

        let results = SearchService::search_by_entry_type(&conn, EntryType::Note).unwrap();
        assert!(results.iter().any(|r| r.title == "A Note"));
    }

    // -----------------------------------------------------------------------
    // DATE RANGE SEARCH
    // -----------------------------------------------------------------------

    #[test]
    fn test_search_by_date_range() {
        let (conn, ctx) = setup();
        create_project_with_entry(&conn, &ctx, "Old Site", EntryType::Login, "a", "1");

        // 使用宽范围，应能找到
        let results = SearchService::search_by_date_range(
            &conn,
            "2020-01-01T00:00:00Z",
            "2099-12-31T23:59:59Z",
        )
        .unwrap();
        assert!(!results.is_empty());
    }

    #[test]
    fn test_search_by_date_range_no_match() {
        let (conn, ctx) = setup();
        create_project_with_entry(&conn, &ctx, "Site", EntryType::Login, "a", "1");

        let results = SearchService::search_by_date_range(
            &conn,
            "2000-01-01T00:00:00Z",
            "2000-01-02T00:00:00Z",
        )
        .unwrap();
        assert!(results.is_empty());
    }

    // -----------------------------------------------------------------------
    // COMBINED SEARCH
    // -----------------------------------------------------------------------

    #[test]
    fn test_combined_search_title_and_tag() {
        let (conn, ctx) = setup();
        let p = create_project_with_entry(
            &conn,
            &ctx,
            "Work Gmail",
            EntryType::Login,
            "alice",
            "s3cret",
        );
        SearchService::index_project_title(&conn, &p.project_id, "Work Gmail").unwrap();
        SearchService::add_tag(&conn, &p.project_id, "email").unwrap();

        let results =
            SearchService::search(&conn, Some("Gmail"), Some("email"), None, None, None).unwrap();
        assert!(!results.is_empty());
        assert_eq!(results[0].project_id, p.project_id);
    }

    #[test]
    fn test_combined_search_all_filters() {
        let (conn, ctx) = setup();
        let p = create_project_with_entry(
            &conn,
            &ctx,
            "Bank Login",
            EntryType::Login,
            "alice",
            "s3cret",
        );
        SearchService::index_project_title(&conn, &p.project_id, "Bank Login").unwrap();
        SearchService::add_tag(&conn, &p.project_id, "finance").unwrap();

        let results = SearchService::search(
            &conn,
            Some("Bank"),
            Some("finance"),
            Some(EntryType::Login),
            Some("2020-01-01T00:00:00Z"),
            Some("2099-12-31T23:59:59Z"),
        )
        .unwrap();
        assert!(!results.is_empty());
        assert_eq!(results[0].project_id, p.project_id);
    }

    #[test]
    fn test_combined_search_no_results() {
        let (conn, ctx) = setup();
        let p = create_project_with_entry(&conn, &ctx, "Site", EntryType::Login, "alice", "s3cret");
        SearchService::add_tag(&conn, &p.project_id, "work").unwrap();

        let results =
            SearchService::search(&conn, None, Some("nonexistent-tag"), None, None, None).unwrap();
        assert!(results.is_empty());
    }

    // -----------------------------------------------------------------------
    // REMOVE PROJECT INDEX
    // -----------------------------------------------------------------------

    #[test]
    fn test_remove_project_index_cleans_tags() {
        let (conn, ctx) = setup();
        let p = create_project_with_entry(&conn, &ctx, "To Delete", EntryType::Login, "a", "1");
        SearchService::add_tag(&conn, &p.project_id, "temp").unwrap();
        SearchService::index_project_title(&conn, &p.project_id, "To Delete").unwrap();

        SearchService::remove_project_index(&conn, &p.project_id).unwrap();

        let tags = SearchService::list_tags(&conn, &p.project_id).unwrap();
        assert!(tags.is_empty());
    }

    // -----------------------------------------------------------------------
    // FTS QUERY BUILDING
    // -----------------------------------------------------------------------

    #[test]
    fn test_fts_query_simple() {
        let query = SearchService::build_fts_query("hello");
        assert!(query.contains("hello"));
    }

    #[test]
    fn test_fts_query_multiple_words() {
        let query = SearchService::build_fts_query("hello world");
        assert!(query.contains("hello"));
        assert!(query.contains("world"));
    }

    #[test]
    fn test_fts_query_empty() {
        let query = SearchService::build_fts_query("");
        assert_eq!(query, "*");
    }

    // -----------------------------------------------------------------------
    // ENRICHED RESULTS
    // -----------------------------------------------------------------------

    #[test]
    fn test_search_result_includes_entry_types_and_tags() {
        let (conn, ctx) = setup();
        let p = create_project_with_entry(
            &conn,
            &ctx,
            "Full Project",
            EntryType::Login,
            "alice",
            "s3cret",
        );
        // 添加 Note entry
        EntryRepo::create(
            &conn,
            &ctx,
            &p.project_id,
            EntryType::Note,
            Some("Notes"),
            &serde_json::json!({"text": "some note"}),
        )
        .unwrap();

        SearchService::add_tag(&conn, &p.project_id, "test").unwrap();
        SearchService::index_project_title(&conn, &p.project_id, "Full Project").unwrap();

        let results = SearchService::search_by_title(&conn, "Full").unwrap();
        assert!(!results.is_empty());
        let result = &results[0];
        assert!(result.entry_types.contains(&"login".to_string()));
        assert!(result.entry_types.contains(&"note".to_string()));
        assert!(result.tags.contains(&"test".to_string()));
    }
}
