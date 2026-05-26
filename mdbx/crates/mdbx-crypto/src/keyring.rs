use hkdf::Hkdf;
use sha2::Sha256;

use crate::error::{CryptoError, CryptoResult};
use crate::kdf;

/// 密钥层级中的各层密钥。
///
/// 密钥派生链：
/// ```text
/// 用户凭据 ──[Argon2id]──► 解锁密钥 ──[HKDF]──► vault 密钥
///                                                      │
///                             ┌────────────────────────┤
///                             ▼               ▼        ▼
///                        记录子密钥      附件子密钥   元数据子密钥
///                        历史子密钥      完整性子密钥
/// ```
pub struct Keyring {
    /// 主解锁密钥（从用户凭据派生）
    pub unlock_key: Vec<u8>,
    /// vault 加密密钥
    pub vault_key: Vec<u8>,
    /// 记录加密子密钥
    pub record_subkey: Vec<u8>,
    /// 附件加密子密钥
    pub attachment_subkey: Vec<u8>,
    /// 元数据加密子密钥
    pub metadata_subkey: Vec<u8>,
    /// commit/history 加密子密钥
    pub history_subkey: Vec<u8>,
    /// 完整性认证子密钥
    pub integrity_subkey: Vec<u8>,
}

impl Keyring {
    /// 从用户凭据派生完整密钥环。
    ///
    /// # Arguments
    /// - `credential`: 用户凭据（密码/PIN/安全密钥材料）
    /// - `salt`: Argon2id salt
    /// - `params`: Argon2id 参数
    /// - `vault_context`: vault 标识上下文（用于 HKDF info）
    pub fn derive(
        credential: &[u8],
        salt: &[u8],
        params: &kdf::Argon2Params,
        vault_context: &[u8],
    ) -> CryptoResult<Self> {
        // 1. Argon2id: credential → unlock_key
        let unlock_key = kdf::derive_key(credential, salt, params)?;

        // 2. HKDF-SHA-256: unlock_key → vault_key
        let vault_key = Self::hkdf_expand(&unlock_key, vault_context, b"vault-key")?;

        // 3. HKDF: vault_key → sub-keys
        let record_subkey = Self::hkdf_expand(&vault_key, vault_context, b"record")?;
        let attachment_subkey = Self::hkdf_expand(&vault_key, vault_context, b"attachment")?;
        let metadata_subkey = Self::hkdf_expand(&vault_key, vault_context, b"metadata")?;
        let history_subkey = Self::hkdf_expand(&vault_key, vault_context, b"history")?;
        let integrity_subkey = Self::hkdf_expand(&vault_key, vault_context, b"integrity")?;

        Ok(Self {
            unlock_key,
            vault_key,
            record_subkey,
            attachment_subkey,
            metadata_subkey,
            history_subkey,
            integrity_subkey,
        })
    }

    /// 从已知的 vault_key 重建子密钥（用于已解锁状态）。
    pub fn from_vault_key(vault_key: &[u8], vault_context: &[u8]) -> CryptoResult<Self> {
        let record_subkey = Self::hkdf_expand(vault_key, vault_context, b"record")?;
        let attachment_subkey = Self::hkdf_expand(vault_key, vault_context, b"attachment")?;
        let metadata_subkey = Self::hkdf_expand(vault_key, vault_context, b"metadata")?;
        let history_subkey = Self::hkdf_expand(vault_key, vault_context, b"history")?;
        let integrity_subkey = Self::hkdf_expand(vault_key, vault_context, b"integrity")?;

        Ok(Self {
            unlock_key: Vec::new(), // 未保存
            vault_key: vault_key.to_vec(),
            record_subkey,
            attachment_subkey,
            metadata_subkey,
            history_subkey,
            integrity_subkey,
        })
    }

    /// HKDF-SHA-256 派生。
    fn hkdf_expand(ikm: &[u8], salt: &[u8], info: &[u8]) -> CryptoResult<Vec<u8>> {
        let hk = Hkdf::<Sha256>::new(Some(salt), ikm);
        let mut okm = vec![0u8; 32];
        hk.expand(info, &mut okm)
            .map_err(|e| CryptoError::KeyDerivation(format!("HKDF expand failed: {}", e)))?;
        Ok(okm)
    }
}

// ---------------------------------------------------------------------------
// 测试
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_full_keyring_derivation() {
        let salt = kdf::generate_salt(16).unwrap();
        let params = kdf::Argon2Params::sky();
        let vault_ctx = b"mdbx-vault-001";

        let keyring = Keyring::derive(b"my-password", &salt, &params, vault_ctx).unwrap();

        assert_eq!(keyring.unlock_key.len(), 32);
        assert_eq!(keyring.vault_key.len(), 32);
        assert_eq!(keyring.record_subkey.len(), 32);
        assert_eq!(keyring.attachment_subkey.len(), 32);
        assert_eq!(keyring.metadata_subkey.len(), 32);
        assert_eq!(keyring.history_subkey.len(), 32);
        assert_eq!(keyring.integrity_subkey.len(), 32);
    }

    #[test]
    fn test_keyring_deterministic() {
        let salt = b"test-salt-16bytes";
        let params = kdf::Argon2Params::sky();
        let vault_ctx = b"mdbx-vault-001";

        let k1 = Keyring::derive(b"password", salt, &params, vault_ctx).unwrap();
        let k2 = Keyring::derive(b"password", salt, &params, vault_ctx).unwrap();

        assert_eq!(k1.vault_key, k2.vault_key);
        assert_eq!(k1.record_subkey, k2.record_subkey);
    }

    #[test]
    fn test_different_vault_contexts() {
        let salt = b"test-salt-16bytes";
        let params = kdf::Argon2Params::sky();

        let k1 = Keyring::derive(b"password", salt, &params, b"vault-a").unwrap();
        let k2 = Keyring::derive(b"password", salt, &params, b"vault-b").unwrap();

        assert_ne!(k1.vault_key, k2.vault_key);
        assert_ne!(k1.record_subkey, k2.record_subkey);
    }

    #[test]
    fn test_subkeys_are_different() {
        let salt = b"test-salt-16bytes";
        let params = kdf::Argon2Params::sky();
        let kr = Keyring::derive(b"password", salt, &params, b"vault").unwrap();

        assert_ne!(kr.record_subkey, kr.attachment_subkey);
        assert_ne!(kr.record_subkey, kr.metadata_subkey);
        assert_ne!(kr.record_subkey, kr.history_subkey);
        assert_ne!(kr.record_subkey, kr.integrity_subkey);
        assert_ne!(kr.attachment_subkey, kr.metadata_subkey);
    }

    #[test]
    fn test_from_vault_key() {
        let vault_key = crate::aead::generate_key().unwrap();
        let kr = Keyring::from_vault_key(&vault_key, b"vault").unwrap();

        assert_eq!(kr.vault_key, vault_key);
        assert_eq!(kr.record_subkey.len(), 32);
        assert_eq!(kr.history_subkey.len(), 32);
        assert_eq!(kr.integrity_subkey.len(), 32);
        assert!(kr.unlock_key.is_empty()); // from_vault_key 不解锁
    }
}
