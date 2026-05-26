use serde::{Deserialize, Serialize};

use crate::tiga::TigaMode;
use crate::types::*;

/// Project 是 MDBX 的核心主容器。
/// 所有密码类内容必须归属于某个 project。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Project {
    pub project_id: ProjectId,
    /// 加密的标题
    pub title_ct: CipherText,
    /// 加密的摘要（可选）
    pub summary_ct: Option<CipherText>,
    /// 分组归属
    pub group_id: Option<String>,
    /// 图标引用
    pub icon_ref: Option<String>,
    /// 收藏状态
    pub favorite: bool,
    /// 归档状态
    pub archived: bool,
    /// 软删除标记
    pub deleted: bool,
    /// project 级 Tiga 覆盖
    pub tiga_mode_override: Option<TigaMode>,
    /// 向量时钟
    pub object_clock: ObjectClock,
    /// 当前 head commit
    pub head_commit_id: CommitId,
    /// 附件计数（用于 UI 快速渲染）
    pub attachment_count: u32,
    /// 创建时间 (ISO 8601)
    pub created_at: String,
    /// 更新时间 (ISO 8601)
    pub updated_at: String,
    /// 创建设备 ID
    pub created_by_device_id: DeviceId,
    /// 更新设备 ID
    pub updated_by_device_id: DeviceId,
}
