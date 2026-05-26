use argon2::{Argon2, Params, Version};

use crate::error::{CryptoError, CryptoResult};

/// Argon2id 参数配置。
///
/// 根据 Tiga 安全模式提供三档预设。
#[derive(Debug, Clone)]
pub struct Argon2Params {
    pub memory_kib: u32,
    pub iterations: u32,
    pub parallelism: u32,
    pub output_len: usize,
}

impl Argon2Params {
    /// Power 模式 — 最高防护。
    /// 高内存 + 高迭代，最大限度抵御离线暴力破解。
    pub fn power() -> Self {
        Self {
            memory_kib: 262144, // 256 MiB
            iterations: 10,
            parallelism: 4,
            output_len: 32,
        }
    }

    /// Multi 模式 — 平衡默认。
    /// 强度较高，同时保证日常使用体验。
    pub fn multi() -> Self {
        Self {
            memory_kib: 65536, // 64 MiB
            iterations: 3,
            parallelism: 2,
            output_len: 32,
        }
    }

    /// Sky 模式 — 快速轻便。
    /// 较低但仍然合格的参数，适用于低风险环境。
    pub fn sky() -> Self {
        Self {
            memory_kib: 8192, // 8 MiB
            iterations: 1,
            parallelism: 1,
            output_len: 32,
        }
    }
}

/// Argon2id KDF。
///
/// 从用户凭据（密码/PIN/安全密钥材料）派生密钥。
///
/// # Unicode 规范化
///
/// 调用者在传入密码前应完成 NFC 规范化，确保跨平台一致性。
pub fn derive_key(credential: &[u8], salt: &[u8], params: &Argon2Params) -> CryptoResult<Vec<u8>> {
    let argon2_params = Params::new(
        params.memory_kib,
        params.iterations,
        params.parallelism,
        Some(params.output_len),
    )
    .map_err(|e| CryptoError::KeyDerivation(format!("invalid Argon2 params: {}", e)))?;

    let argon2 = Argon2::new(argon2::Algorithm::Argon2id, Version::V0x13, argon2_params);

    let mut output = vec![0u8; params.output_len];
    argon2
        .hash_password_into(credential, salt, &mut output)
        .map_err(|e| CryptoError::KeyDerivation(format!("Argon2id hashing failed: {}", e)))?;

    Ok(output)
}

/// 生成随机 salt。
pub fn generate_salt(len: usize) -> CryptoResult<Vec<u8>> {
    use rand::RngCore;
    let mut salt = vec![0u8; len];
    rand::rngs::OsRng.fill_bytes(&mut salt);
    Ok(salt)
}

// ---------------------------------------------------------------------------
// 测试
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_derive_key_deterministic() {
        let salt = b"test-salt-16bytes";
        let cred = b"my-password";
        let params = Argon2Params::sky();

        let k1 = derive_key(cred, salt, &params).unwrap();
        let k2 = derive_key(cred, salt, &params).unwrap();
        assert_eq!(k1, k2);
    }

    #[test]
    fn test_derive_key_different_credentials() {
        let salt = b"test-salt-16bytes";
        let params = Argon2Params::sky();

        let k1 = derive_key(b"password-a", salt, &params).unwrap();
        let k2 = derive_key(b"password-b", salt, &params).unwrap();
        assert_ne!(k1, k2);
    }

    #[test]
    fn test_derive_key_different_salts() {
        let cred = b"my-password";
        let params = Argon2Params::sky();

        let k1 = derive_key(cred, b"salt-aaaaaaaaaaaa", &params).unwrap();
        let k2 = derive_key(cred, b"salt-bbbbbbbbbbbb", &params).unwrap();
        assert_ne!(k1, k2);
    }

    #[test]
    fn test_derive_key_output_length() {
        let salt = b"test-salt-16bytes";
        let params = Argon2Params::multi();
        let key = derive_key(b"password", salt, &params).unwrap();
        assert_eq!(key.len(), 32);
    }

    #[test]
    fn test_generate_salt() {
        let salt1 = generate_salt(16).unwrap();
        let salt2 = generate_salt(16).unwrap();
        assert_eq!(salt1.len(), 16);
        assert_eq!(salt2.len(), 16);
        assert_ne!(salt1, salt2);
    }

    #[test]
    fn test_power_params_stronger_than_sky() {
        let pw = Argon2Params::power();
        let sky = Argon2Params::sky();
        assert!(pw.memory_kib > sky.memory_kib);
        assert!(pw.iterations > sky.iterations);
    }

    #[test]
    fn test_all_modes_produce_valid_output() {
        let salt = b"test-salt-16bytes";
        let cred = b"test-password";

        for mode in &[
            Argon2Params::power(),
            Argon2Params::multi(),
            Argon2Params::sky(),
        ] {
            let key = derive_key(cred, salt, mode).unwrap();
            assert_eq!(key.len(), 32);
        }
    }
}
