use serde::{Deserialize, Serialize};

use crate::tiga::TigaMode;

/// 用户可见的解锁方式。
///
/// 这是 UI 层面的概念，与底层加密密钥模型分离。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum UnlockMethodType {
    #[serde(rename = "pin")]
    Pin,
    #[serde(rename = "password")]
    Password,
    #[serde(rename = "security_key")]
    SecurityKey,
}

impl UnlockMethodType {
    pub fn to_string(&self) -> String {
        match self {
            Self::Pin => "pin".to_string(),
            Self::Password => "password".to_string(),
            Self::SecurityKey => "security_key".to_string(),
        }
    }

    pub fn parse(s: &str) -> Result<Self, String> {
        match s {
            "pin" => Ok(Self::Pin),
            "password" => Ok(Self::Password),
            "security_key" => Ok(Self::SecurityKey),
            other => Err(format!("unknown unlock method type: {}", other)),
        }
    }
}

/// 已配置的解锁方式记录。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UnlockMethod {
    pub method_id: String,
    pub method_type: UnlockMethodType,
    pub kdf_profile_id: String,
    pub kdf_params_ct: Vec<u8>,
    pub wrapped_vault_key_ct: Vec<u8>,
    pub created_at: String,
    pub updated_at: String,
}

/// 成功解锁后返回的会话。
#[derive(Debug, Clone)]
pub struct VaultSession {
    pub session_id: String,
    pub unlock_method: UnlockMethodType,
    pub created_at: String,
}

/// KDF 参数配置。
///
/// 根据不同模式设置不同的 Argon2id 参数。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct KdfParams {
    pub algorithm: String,
    pub ops_limit: u32,
    pub mem_limit_kib: u32,
    pub parallelism: u32,
    pub output_len: u32,
    pub salt: Vec<u8>,
}

impl KdfParams {
    /// 为 PIN 解锁推荐的低成本 KDF（PIN 本身强度低，KDF 不是主要防线）。
    pub fn for_pin() -> Self {
        Self {
            algorithm: "argon2id".to_string(),
            ops_limit: 1,
            mem_limit_kib: 8192,
            parallelism: 1,
            output_len: 32,
            salt: Vec::new(),
        }
    }

    /// 为密码解锁推荐的平衡 KDF（Multi 模式，默认）。
    pub fn for_password() -> Self {
        Self::for_password_with_mode(TigaMode::Multi)
    }

    /// 根据 Tiga 模式选择密码解锁的 Argon2id 参数。
    ///
    /// Power → 最高防护 (256 MiB, 10 iterations, 4 parallelism)
    /// Multi → 平衡默认  (64 MiB, 3 iterations, 2 parallelism)
    /// Sky   → 快速轻便  (8 MiB, 1 iterations, 1 parallelism)
    pub fn for_password_with_mode(mode: TigaMode) -> Self {
        let (ops_limit, mem_limit_kib, parallelism) = match mode {
            TigaMode::Power => (10, 262144, 4),
            TigaMode::Multi => (3, 65536, 2),
            TigaMode::Sky => (1, 8192, 1),
        };
        Self {
            algorithm: "argon2id".to_string(),
            ops_limit,
            mem_limit_kib,
            parallelism,
            output_len: 32,
            salt: Vec::new(),
        }
    }

    /// 为安全密钥解锁推荐的 KDF（硬件密钥本身强，KDF 可以较轻）。
    pub fn for_security_key() -> Self {
        Self {
            algorithm: "argon2id".to_string(),
            ops_limit: 1,
            mem_limit_kib: 16384,
            parallelism: 1,
            output_len: 32,
            salt: Vec::new(),
        }
    }

    pub fn to_json_bytes(&self) -> Vec<u8> {
        serde_json::to_vec(self).unwrap_or_default()
    }

    /// 从当前参数推断 Tiga 模式。
    /// 根据 `mem_limit_kib` 匹配最接近的预设。
    pub fn infer_tiga_mode(&self) -> TigaMode {
        if self.mem_limit_kib >= 200_000 {
            TigaMode::Power
        } else if self.mem_limit_kib >= 50_000 {
            TigaMode::Multi
        } else {
            TigaMode::Sky
        }
    }

    pub fn from_json_bytes(data: &[u8]) -> Result<Self, String> {
        serde_json::from_slice(data).map_err(|e| format!("failed to parse KdfParams: {}", e))
    }
}
