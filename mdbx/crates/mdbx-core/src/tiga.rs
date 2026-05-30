/// MDBX Tiga 三安全模式。
///
/// 优先级从低到高: Global < Project < Entry。
/// 更窄范围的覆盖优先于更宽范围的覆盖。
#[derive(
    Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, serde::Serialize, serde::Deserialize,
)]
#[serde(rename_all = "kebab-case")]
pub enum TigaMode {
    /// 更快更轻便 — 低风险环境
    Sky,
    /// 平衡默认 — 安全与可用性之间
    Multi,
    /// 最高防护 — 增强暴力破解阻力
    Power,
}

impl TigaMode {
    /// 根据三级层次解析有效的 Tiga 模式。
    ///
    /// 优先级: entry override > project override > global default
    pub fn resolve(
        global_default: TigaMode,
        project_override: Option<TigaMode>,
        entry_override: Option<TigaMode>,
    ) -> TigaMode {
        entry_override
            .or(project_override)
            .unwrap_or(global_default)
    }
}

impl std::fmt::Display for TigaMode {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            TigaMode::Sky => write!(f, "sky"),
            TigaMode::Multi => write!(f, "multi"),
            TigaMode::Power => write!(f, "power"),
        }
    }
}

impl std::str::FromStr for TigaMode {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s {
            "sky" => Ok(TigaMode::Sky),
            "multi" => Ok(TigaMode::Multi),
            "power" => Ok(TigaMode::Power),
            _ => Err(format!("unknown TigaMode: {}", s)),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_resolve_global_default() {
        assert_eq!(
            TigaMode::resolve(TigaMode::Multi, None, None),
            TigaMode::Multi
        );
    }

    #[test]
    fn test_resolve_project_override() {
        assert_eq!(
            TigaMode::resolve(TigaMode::Sky, Some(TigaMode::Power), None),
            TigaMode::Power
        );
    }

    #[test]
    fn test_resolve_entry_override_wins() {
        assert_eq!(
            TigaMode::resolve(TigaMode::Sky, Some(TigaMode::Multi), Some(TigaMode::Power)),
            TigaMode::Power
        );
    }

    #[test]
    fn test_display_and_parse_roundtrip() {
        for mode in [TigaMode::Sky, TigaMode::Multi, TigaMode::Power] {
            let s = mode.to_string();
            let parsed: TigaMode = s.parse().unwrap();
            assert_eq!(mode, parsed);
        }
    }
}
