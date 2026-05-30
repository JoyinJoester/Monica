use serde_json::Value;

/// 三路合并冲突检测器。
///
/// 基于 base / local / incoming 三路模型判断并发修改是否冲突：
/// - 只有一方修改的字段 → 安全，可自动合并
/// - 双方都修改且结果不同的字段 → 冲突，不可自动合并
pub struct ConflictDetector;

impl ConflictDetector {
    /// 对 entry payload 做字段级三路合并分析。
    ///
    /// 返回冲突字段的 JSON 路径列表。空列表表示可安全自动合并。
    ///
    /// # 合并规则
    ///
    /// 对 payload 中的每个顶层字段：
    /// - 两方最终值相同 → 无冲突
    /// - 只有 local 修改 → 无冲突，采用 local
    /// - 只有 incoming 修改 → 无冲突，采用 incoming
    /// - 双方都修改且值不同 → **冲突**
    /// - base 中不存在（新增字段），双方值不同 → 冲突
    /// - 一方缺失，另一方有值且与 base 不同 → 无冲突
    pub fn detect_entry_conflict(
        base_payload: &Value,
        local_payload: &Value,
        incoming_payload: &Value,
    ) -> Vec<String> {
        let base_obj = base_payload.as_object();
        let local_obj = local_payload.as_object();
        let incoming_obj = incoming_payload.as_object();

        let base = match base_obj {
            Some(obj) => obj,
            None => {
                // payload 不是 JSON 对象则做整体比较
                if local_payload != incoming_payload
                    && local_payload != base_payload
                    && incoming_payload != base_payload
                {
                    return vec!["<payload>".to_string()];
                }
                return vec![];
            }
        };

        let local = local_obj.map_or(serde_json::Map::new(), |_| {
            local_payload.as_object().unwrap().clone()
        });
        let incoming = incoming_obj.map_or(serde_json::Map::new(), |_| {
            incoming_payload.as_object().unwrap().clone()
        });

        let mut conflicting: Vec<String> = Vec::new();

        // 收集所有键
        let mut all_keys: Vec<String> = Vec::new();
        for key in base.keys() {
            if !all_keys.contains(key) {
                all_keys.push(key.clone());
            }
        }
        for key in local.keys() {
            if !all_keys.contains(key) {
                all_keys.push(key.clone());
            }
        }
        for key in incoming.keys() {
            if !all_keys.contains(key) {
                all_keys.push(key.clone());
            }
        }

        for key in &all_keys {
            let base_val = base.get(key);
            let local_val = local.get(key);
            let incoming_val = incoming.get(key);

            match (base_val, local_val, incoming_val) {
                // 两者都无此字段，或值相同
                (_, l, i) if l == i => {
                    // 双方一致，安全
                }

                // 只在 local 侧有变化
                (Some(b), Some(l), Some(i)) if b != i && l != b && i == b => {
                    // local 修改了，incoming 没改 → 安全，采用 local
                }

                // 只在 incoming 侧有变化
                (Some(b), Some(l), Some(i)) if b != i && l == b && i != b => {
                    // incoming 修改了，local 没改 → 安全，采用 incoming
                }

                // base 为 null，两方各自赋了不同值
                (None, Some(l), Some(i)) if l != i => {
                    conflicting.push(key.clone());
                }

                // base 有值，local 改了，incoming 也改了且结果不同
                (Some(b), Some(l), Some(i)) if l != b && i != b && l != i => {
                    conflicting.push(key.clone());
                }

                // local 删除了字段，incoming 修改了它
                (Some(_b), None, Some(i)) if i != base_val.unwrap() => {
                    conflicting.push(key.clone());
                }

                // incoming 删除了字段，local 修改了它
                (Some(_b), Some(l), None) if l != base_val.unwrap() => {
                    conflicting.push(key.clone());
                }

                // 双方都删除了 → 一致
                (Some(_b), None, None) => {}

                // 其余情况（一方新增，另一方无变化等）→ 安全
                _ => {}
            }
        }

        conflicting
    }

    /// 对 project 字段做三路冲突检测。
    ///
    /// 比较非系统字段：title_ct, summary_ct, group_id, icon_ref, favorite, archived。
    /// 系统字段（head_commit_id, object_clock, updated_at 等）不参与比较。
    pub fn detect_project_conflict(
        base_title: &[u8],
        local_title: &[u8],
        incoming_title: &[u8],
        base_summary: Option<&[u8]>,
        local_summary: Option<&[u8]>,
        incoming_summary: Option<&[u8]>,
        base_group_id: Option<&str>,
        local_group_id: Option<&str>,
        incoming_group_id: Option<&str>,
        base_icon_ref: Option<&str>,
        local_icon_ref: Option<&str>,
        incoming_icon_ref: Option<&str>,
        base_favorite: bool,
        local_favorite: bool,
        incoming_favorite: bool,
        base_archived: bool,
        local_archived: bool,
        incoming_archived: bool,
    ) -> Vec<String> {
        let mut conflicting: Vec<String> = Vec::new();

        // title_ct
        if is_field_conflicting(base_title, local_title, incoming_title) {
            conflicting.push("title_ct".to_string());
        }

        // summary_ct
        if is_opt_field_conflicting(base_summary, local_summary, incoming_summary) {
            conflicting.push("summary_ct".to_string());
        }

        // group_id
        if is_opt_field_conflicting(
            base_group_id.map(|s| s.as_bytes()),
            local_group_id.map(|s| s.as_bytes()),
            incoming_group_id.map(|s| s.as_bytes()),
        ) {
            conflicting.push("group_id".to_string());
        }

        // icon_ref
        if is_opt_field_conflicting(
            base_icon_ref.map(|s| s.as_bytes()),
            local_icon_ref.map(|s| s.as_bytes()),
            incoming_icon_ref.map(|s| s.as_bytes()),
        ) {
            conflicting.push("icon_ref".to_string());
        }

        // favorite
        if is_field_conflicting(&base_favorite, &local_favorite, &incoming_favorite) {
            conflicting.push("favorite".to_string());
        }

        // archived
        if is_field_conflicting(&base_archived, &local_archived, &incoming_archived) {
            conflicting.push("archived".to_string());
        }

        conflicting
    }

    /// 便捷方法：判断是否应阻止自动合并。
    pub fn is_safe_to_auto_merge(conflicting_fields: &[String]) -> bool {
        conflicting_fields.is_empty()
    }

    /// 构建自动合并后的结果 payload。
    ///
    /// 前提：`detect_entry_conflict` 已返回空（无冲突）。
    /// 合并策略：对每字段取 local 和 incoming 中相对 base 有变化的一方；
    /// 双方都有相同变化时取任意一方。
    pub fn build_merged_payload(
        base_payload: &Value,
        local_payload: &Value,
        incoming_payload: &Value,
    ) -> Value {
        let base_obj = match base_payload.as_object() {
            Some(o) => o,
            None => {
                // 非对象：取不同于 base 的一方
                if local_payload != base_payload {
                    return local_payload.clone();
                }
                return incoming_payload.clone();
            }
        };

        let local = local_payload.as_object();
        let incoming = incoming_payload.as_object();

        let mut merged = base_obj.clone();

        let local_map = local.cloned().unwrap_or_default();
        let incoming_map = incoming.cloned().unwrap_or_default();

        // 收集所有键
        let mut all_keys: Vec<String> = Vec::new();
        for key in merged.keys() {
            if !all_keys.contains(key) {
                all_keys.push(key.clone());
            }
        }
        for key in local_map.keys() {
            if !all_keys.contains(key) {
                all_keys.push(key.clone());
            }
        }
        for key in incoming_map.keys() {
            if !all_keys.contains(key) {
                all_keys.push(key.clone());
            }
        }

        for key in &all_keys {
            let base_val = merged.get(key);
            let local_val = local_map.get(key);
            let incoming_val = incoming_map.get(key);

            if local_val == incoming_val {
                // 双方一致 → 采纳任一方（包括双方都删除的情况）
                match local_val {
                    Some(v) => {
                        merged.insert(key.clone(), v.clone());
                    }
                    None => {
                        merged.remove(key);
                    }
                }
            } else if local_val != base_val && incoming_val == base_val {
                // 只有 local 变了 → 采纳 local
                match local_val {
                    Some(v) => {
                        merged.insert(key.clone(), v.clone());
                    }
                    None => {
                        merged.remove(key);
                    }
                }
            } else if local_val == base_val && incoming_val != base_val {
                // 只有 incoming 变了 → 采纳 incoming
                match incoming_val {
                    Some(v) => {
                        merged.insert(key.clone(), v.clone());
                    }
                    None => {
                        merged.remove(key);
                    }
                }
            }
            // else: 双方都变了且不同 → 冲突（调用方应在合并前先检测）
        }

        Value::Object(merged)
    }
}

// ---------------------------------------------------------------------------
// 内部辅助
// ---------------------------------------------------------------------------

/// 三路比较一个字段是否冲突（实现了 PartialEq 的类型）。
fn is_field_conflicting<T: PartialEq>(base: T, local: T, incoming: T) -> bool {
    if local == incoming {
        return false; // 双方一致
    }
    if local != base && incoming == base {
        return false; // 只有 local 修改
    }
    if local == base && incoming != base {
        return false; // 只有 incoming 修改
    }
    // 双方都修改且结果不同
    true
}

/// 三路比较 Optional 字段。
fn is_opt_field_conflicting<T: PartialEq>(
    base: Option<T>,
    local: Option<T>,
    incoming: Option<T>,
) -> bool {
    if local == incoming {
        return false;
    }
    if local != base && incoming == base {
        return false;
    }
    if local == base && incoming != base {
        return false;
    }
    true
}

// ---------------------------------------------------------------------------
// 测试
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    fn payload(json: &str) -> Value {
        serde_json::from_str(json).unwrap()
    }

    // -----------------------------------------------------------------------
    // ENTRY PAYLOAD CONFLICT DETECTION
    // -----------------------------------------------------------------------

    #[test]
    fn test_safe_when_only_local_changed() {
        let base = payload(r#"{"user":"alice","pass":"old"}"#);
        let local = payload(r#"{"user":"alice","pass":"new_local"}"#);
        let incoming = payload(r#"{"user":"alice","pass":"old"}"#);

        let conflicts = ConflictDetector::detect_entry_conflict(&base, &local, &incoming);
        assert!(conflicts.is_empty());
    }

    #[test]
    fn test_safe_when_only_incoming_changed() {
        let base = payload(r#"{"user":"alice","pass":"old"}"#);
        let local = payload(r#"{"user":"alice","pass":"old"}"#);
        let incoming = payload(r#"{"user":"alice","pass":"new_incoming"}"#);

        let conflicts = ConflictDetector::detect_entry_conflict(&base, &local, &incoming);
        assert!(conflicts.is_empty());
    }

    #[test]
    fn test_safe_when_different_fields_changed() {
        let base = payload(r#"{"user":"alice","pass":"old","url":"http://a.com"}"#);
        let local = payload(r#"{"user":"alice","pass":"new_local","url":"http://a.com"}"#);
        let incoming = payload(r#"{"user":"alice","pass":"old","url":"http://b.com"}"#);

        // local 改了 pass, incoming 改了 url → 不同字段，安全
        let conflicts = ConflictDetector::detect_entry_conflict(&base, &local, &incoming);
        assert!(conflicts.is_empty());
    }

    #[test]
    fn test_conflict_when_same_field_changed_differently() {
        let base = payload(r#"{"user":"alice","pass":"old"}"#);
        let local = payload(r#"{"user":"alice","pass":"local_version"}"#);
        let incoming = payload(r#"{"user":"alice","pass":"incoming_version"}"#);

        let conflicts = ConflictDetector::detect_entry_conflict(&base, &local, &incoming);
        assert_eq!(conflicts, vec!["pass"]);
    }

    #[test]
    fn test_conflict_when_both_add_same_key_differently() {
        let base = payload(r#"{"user":"alice"}"#);
        let local = payload(r#"{"user":"alice","totp":"seed1"}"#);
        let incoming = payload(r#"{"user":"alice","totp":"seed2"}"#);

        let conflicts = ConflictDetector::detect_entry_conflict(&base, &local, &incoming);
        assert_eq!(conflicts, vec!["totp"]);
    }

    #[test]
    fn test_safe_when_both_add_same_key_identically() {
        let base = payload(r#"{"user":"alice"}"#);
        let local = payload(r#"{"user":"alice","notes":"hello"}"#);
        let incoming = payload(r#"{"user":"alice","notes":"hello"}"#);

        let conflicts = ConflictDetector::detect_entry_conflict(&base, &local, &incoming);
        assert!(conflicts.is_empty());
    }

    #[test]
    fn test_conflict_when_local_deletes_incoming_modifies() {
        let base = payload(r#"{"user":"alice","notes":"old"}"#);
        let local = payload(r#"{"user":"alice"}"#);
        let incoming = payload(r#"{"user":"alice","notes":"new"}"#);

        let conflicts = ConflictDetector::detect_entry_conflict(&base, &local, &incoming);
        assert_eq!(conflicts, vec!["notes"]);
    }

    #[test]
    fn test_conflict_when_incoming_deletes_local_modifies() {
        let base = payload(r#"{"user":"alice","notes":"old"}"#);
        let local = payload(r#"{"user":"alice","notes":"new"}"#);
        let incoming = payload(r#"{"user":"alice"}"#);

        let conflicts = ConflictDetector::detect_entry_conflict(&base, &local, &incoming);
        assert_eq!(conflicts, vec!["notes"]);
    }

    #[test]
    fn test_safe_when_both_delete_same_key() {
        let base = payload(r#"{"user":"alice","notes":"old"}"#);
        let local = payload(r#"{"user":"alice"}"#);
        let incoming = payload(r#"{"user":"alice"}"#);

        let conflicts = ConflictDetector::detect_entry_conflict(&base, &local, &incoming);
        assert!(conflicts.is_empty());
    }

    #[test]
    fn test_safe_when_both_set_same_value() {
        let base = payload(r#"{"user":"alice","pass":"old"}"#);
        let local = payload(r#"{"user":"alice","pass":"same_new"}"#);
        let incoming = payload(r#"{"user":"alice","pass":"same_new"}"#);

        let conflicts = ConflictDetector::detect_entry_conflict(&base, &local, &incoming);
        assert!(conflicts.is_empty());
    }

    #[test]
    fn test_multiple_conflicts() {
        let base = payload(r#"{"user":"alice","pass":"old","url":"http://a.com"}"#);
        let local = payload(r#"{"user":"alice","pass":"new1","url":"http://b.com"}"#);
        let incoming = payload(r#"{"user":"alice","pass":"new2","url":"http://c.com"}"#);

        let conflicts = ConflictDetector::detect_entry_conflict(&base, &local, &incoming);
        assert_eq!(conflicts.len(), 2);
        assert!(conflicts.contains(&"pass".to_string()));
        assert!(conflicts.contains(&"url".to_string()));
    }

    #[test]
    fn test_non_object_payload() {
        let base = Value::String("old".to_string());
        let local = Value::String("local".to_string());
        let incoming = Value::String("old".to_string());

        let conflicts = ConflictDetector::detect_entry_conflict(&base, &local, &incoming);
        assert!(conflicts.is_empty());

        let incoming2 = Value::String("incoming".to_string());
        let conflicts2 = ConflictDetector::detect_entry_conflict(&base, &local, &incoming2);
        assert!(!conflicts2.is_empty());
    }

    #[test]
    fn test_empty_payloads() {
        let base = payload(r#"{}"#);
        let local = payload(r#"{}"#);
        let incoming = payload(r#"{}"#);

        let conflicts = ConflictDetector::detect_entry_conflict(&base, &local, &incoming);
        assert!(conflicts.is_empty());
    }

    #[test]
    fn test_safe_when_no_changes() {
        let base = payload(r#"{"user":"alice","pass":"secret"}"#);
        let conflicts = ConflictDetector::detect_entry_conflict(&base, &base, &base);
        assert!(conflicts.is_empty());
    }

    // -----------------------------------------------------------------------
    // PROJECT CONFLICT DETECTION
    // -----------------------------------------------------------------------

    #[test]
    fn test_project_safe_different_fields() {
        let conflicts = ConflictDetector::detect_project_conflict(
            b"Old Title",
            b"New Local Title",
            b"Old Title", // title: local changed
            None,
            None,
            None, // summary
            None,
            None,
            Some("work"), // group: incoming added
            None,
            None,
            None, // icon
            false,
            false,
            false, // favorite
            false,
            false,
            false, // archived
        );
        assert!(conflicts.is_empty());
    }

    #[test]
    fn test_project_conflict_same_field() {
        let conflicts = ConflictDetector::detect_project_conflict(
            b"Old Title",
            b"Local Title",
            b"Incoming Title", // title: both changed
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            false,
            false,
            false,
            false,
            false,
            false,
        );
        assert_eq!(conflicts, vec!["title_ct"]);
    }

    #[test]
    fn test_project_safe_both_agree() {
        let conflicts = ConflictDetector::detect_project_conflict(
            b"Old",
            b"Same New",
            b"Same New", // title: both changed identically
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            false,
            false,
            false,
            false,
            false,
            false,
        );
        assert!(conflicts.is_empty());
    }

    #[test]
    fn test_project_conflict_favorite() {
        let conflicts = ConflictDetector::detect_project_conflict(
            b"", b"", b"", None, None, None, None, None, None, None, None, None, false, true,
            true, // both favorited → same, safe
            false, false, false,
        );
        assert!(conflicts.is_empty());

        let conflicts2 = ConflictDetector::detect_project_conflict(
            b"", b"", b"", None, None, None, None, None, None, None, None, None, false, true,
            false, // local favorited, incoming not → conflict with base
            false, false, false,
        );
        // local changes from false to true, incoming stays at false (base) → safe
        assert!(conflicts2.is_empty());
    }

    // -----------------------------------------------------------------------
    // MERGE BUILDING
    // -----------------------------------------------------------------------

    #[test]
    fn test_build_merged_payload_local_change() {
        let base = payload(r#"{"user":"alice","pass":"old"}"#);
        let local = payload(r#"{"user":"alice","pass":"new_local"}"#);
        let incoming = payload(r#"{"user":"alice","pass":"old"}"#);

        let merged = ConflictDetector::build_merged_payload(&base, &local, &incoming);
        assert_eq!(merged, local);
    }

    #[test]
    fn test_build_merged_payload_incoming_change() {
        let base = payload(r#"{"user":"alice","pass":"old"}"#);
        let local = payload(r#"{"user":"alice","pass":"old"}"#);
        let incoming = payload(r#"{"user":"alice","pass":"new_incoming"}"#);

        let merged = ConflictDetector::build_merged_payload(&base, &local, &incoming);
        assert_eq!(merged, incoming);
    }

    #[test]
    fn test_build_merged_payload_different_fields() {
        let base = payload(r#"{"user":"alice","pass":"old","url":"http://a.com"}"#);
        let local = payload(r#"{"user":"alice","pass":"new_local","url":"http://a.com"}"#);
        let incoming = payload(r#"{"user":"alice","pass":"old","url":"http://b.com"}"#);

        let merged = ConflictDetector::build_merged_payload(&base, &local, &incoming);
        let expected = payload(r#"{"user":"alice","pass":"new_local","url":"http://b.com"}"#);
        assert_eq!(merged, expected);
    }

    #[test]
    fn test_build_merged_payload_new_fields() {
        let base = payload(r#"{"user":"alice"}"#);
        let local = payload(r#"{"user":"alice","notes":"local_note"}"#);
        let incoming = payload(r#"{"user":"alice","totp":"incoming_seed"}"#);

        let merged = ConflictDetector::build_merged_payload(&base, &local, &incoming);
        let expected = payload(r#"{"user":"alice","notes":"local_note","totp":"incoming_seed"}"#);
        assert_eq!(merged, expected);
    }

    #[test]
    fn test_build_merged_payload_delete() {
        let base = payload(r#"{"user":"alice","notes":"old"}"#);
        let local = payload(r#"{"user":"alice"}"#); // local deleted notes
        let incoming = payload(r#"{"user":"alice","notes":"old"}"#); // incoming kept it

        let merged = ConflictDetector::build_merged_payload(&base, &local, &incoming);
        let expected = payload(r#"{"user":"alice"}"#);
        assert_eq!(merged, expected);
    }

    // -----------------------------------------------------------------------
    // SAFE TO AUTO MERGE
    // -----------------------------------------------------------------------

    #[test]
    fn test_is_safe_to_auto_merge() {
        assert!(ConflictDetector::is_safe_to_auto_merge(&[]));
        assert!(!ConflictDetector::is_safe_to_auto_merge(&[
            "pass".to_string()
        ]));
    }
}
