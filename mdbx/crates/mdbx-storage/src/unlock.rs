use mdbx_crypto::aead;
use mdbx_crypto::kdf::{self, Argon2Params};
use mdbx_crypto::keyring::Keyring;
use uuid::Uuid;

use mdbx_core::model::{KdfParams, UnlockMethod, UnlockMethodType, VaultSession};
use mdbx_core::tiga::TigaMode;

use crate::connection::VaultConnection;
use crate::error::{StorageError, StorageResult};

/// AEAD 包装 vault 密钥时使用的 AAD。
const VAULT_KEY_WRAP_AAD: &[u8] = b"mdbx-vault-key-wrap";

/// 保管库解锁服务。
///
/// 支持三种用户可见的解锁方式：PIN、密码、安全密钥。
///
/// 密钥层级：
/// ```text
/// 用户凭据 ──[Argon2id]──► unlock_key ──[AEAD 解包]──► vault_key
///                                                          │
///                                ┌─────────────────────────┤
///                                ▼               ▼         ▼
///                           记录子密钥      附件子密钥   元数据子密钥
/// ```
///
/// - **setup**: 生成随机 vault_key → 用派生密钥 AEAD 包裹 → 存储 wrapped_vault_key_ct
/// - **unlock**: 派生密钥 → AEAD 解包 vault_key → 构建 Keyring → 附加到连接
/// - **change**: 验证旧凭据 → 解包 vault_key → 用新凭据重新包裹
pub struct UnlockService;

impl UnlockService {
    // -----------------------------------------------------------------------
    // SETUP — 配置解锁方式
    // -----------------------------------------------------------------------

    /// 配置 PIN 解锁方式。
    ///
    /// PIN 至少需要 4 位数字。PIN 派生的密钥用于包装 vault 密钥材料。
    pub fn setup_pin(conn: &mut VaultConnection, pin: &str) -> StorageResult<UnlockMethod> {
        Self::validate_pin(pin)?;

        let normalized = pin.trim();
        let mut kdf_params = KdfParams::for_pin();
        kdf_params.salt = kdf::generate_salt(16).map_err(|e| {
            StorageError::Crypto(mdbx_crypto::error::CryptoError::RngError(e.to_string()))
        })?;
        let unlock_key = Self::derive_key(normalized.as_bytes(), &kdf_params)?;

        let vault_key = Self::get_or_generate_vault_key(conn);
        let wrapped = Self::wrap_vault_key(&unlock_key, &vault_key)?;

        let vault_ctx = Self::read_vault_context(conn)?;
        let keyring = Keyring::from_vault_key(&vault_key, &vault_ctx)?;
        conn.attach_keyring(keyring);

        Self::store_method(conn, UnlockMethodType::Pin, &kdf_params, &wrapped)
    }

    /// 配置密码解锁方式（默认 Multi 模式）。
    ///
    /// 密码在进入 KDF 前进行 Unicode NFC 规范化，确保跨平台一致性。
    pub fn setup_password(
        conn: &mut VaultConnection,
        password: &str,
    ) -> StorageResult<UnlockMethod> {
        Self::setup_password_with_mode(conn, password, TigaMode::Multi)
    }

    /// 配置密码解锁方式，指定 Tiga 安全等级。
    ///
    /// Power → 最高防护 (256 MiB, 10 iterations)
    /// Multi → 平衡默认  (64 MiB, 3 iterations)
    /// Sky   → 快速轻便  (8 MiB, 1 iterations)
    pub fn setup_password_with_mode(
        conn: &mut VaultConnection,
        password: &str,
        mode: TigaMode,
    ) -> StorageResult<UnlockMethod> {
        Self::validate_password(password)?;

        let normalized = Self::normalize_unicode(password);
        let mut kdf_params = KdfParams::for_password_with_mode(mode);
        kdf_params.salt = kdf::generate_salt(16).map_err(|e| {
            StorageError::Crypto(mdbx_crypto::error::CryptoError::RngError(e.to_string()))
        })?;
        let unlock_key = Self::derive_key(normalized.as_bytes(), &kdf_params)?;

        let vault_key = Self::get_or_generate_vault_key(conn);
        let wrapped = Self::wrap_vault_key(&unlock_key, &vault_key)?;

        let vault_ctx = Self::read_vault_context(conn)?;
        let keyring = Keyring::from_vault_key(&vault_key, &vault_ctx)?;
        conn.attach_keyring(keyring);

        Self::store_method(conn, UnlockMethodType::Password, &kdf_params, &wrapped)
    }

    /// 配置安全密钥解锁方式。
    pub fn setup_security_key(
        conn: &mut VaultConnection,
        key_data: &[u8],
    ) -> StorageResult<UnlockMethod> {
        if key_data.is_empty() {
            return Err(StorageError::Validation(
                "security key data must not be empty".to_string(),
            ));
        }

        let mut kdf_params = KdfParams::for_security_key();
        kdf_params.salt = kdf::generate_salt(16).map_err(|e| {
            StorageError::Crypto(mdbx_crypto::error::CryptoError::RngError(e.to_string()))
        })?;
        let unlock_key = Self::derive_key(key_data, &kdf_params)?;

        let vault_key = Self::get_or_generate_vault_key(conn);
        let wrapped = Self::wrap_vault_key(&unlock_key, &vault_key)?;

        let vault_ctx = Self::read_vault_context(conn)?;
        let keyring = Keyring::from_vault_key(&vault_key, &vault_ctx)?;
        conn.attach_keyring(keyring);

        Self::store_method(conn, UnlockMethodType::SecurityKey, &kdf_params, &wrapped)
    }

    // -----------------------------------------------------------------------
    // UNLOCK — 解锁
    // -----------------------------------------------------------------------

    /// 使用 PIN 解锁 vault。
    pub fn unlock_with_pin(conn: &mut VaultConnection, pin: &str) -> StorageResult<VaultSession> {
        let method = Self::find_method_by_type(conn, UnlockMethodType::Pin)?.ok_or_else(|| {
            StorageError::Validation("no PIN unlock method configured".to_string())
        })?;

        let normalized = pin.trim();
        let kdf_params = KdfParams::from_json_bytes(&method.kdf_params_ct)
            .map_err(|e| StorageError::SchemaCreation(format!("invalid KDF params: {}", e)))?;
        let unlock_key = Self::derive_key(normalized.as_bytes(), &kdf_params)?;

        let vault_key = Self::unwrap_vault_key(&unlock_key, &method.wrapped_vault_key_ct)?;

        let vault_ctx = Self::read_vault_context(conn)?;
        let keyring = Keyring::from_vault_key(&vault_key, &vault_ctx)?;
        conn.attach_keyring(keyring);

        Self::create_session(UnlockMethodType::Pin)
    }

    /// 使用密码解锁 vault。
    pub fn unlock_with_password(
        conn: &mut VaultConnection,
        password: &str,
    ) -> StorageResult<VaultSession> {
        let method =
            Self::find_method_by_type(conn, UnlockMethodType::Password)?.ok_or_else(|| {
                StorageError::Validation("no password unlock method configured".to_string())
            })?;

        let normalized = Self::normalize_unicode(password);
        let kdf_params = KdfParams::from_json_bytes(&method.kdf_params_ct)
            .map_err(|e| StorageError::SchemaCreation(format!("invalid KDF params: {}", e)))?;
        let unlock_key = Self::derive_key(normalized.as_bytes(), &kdf_params)?;

        let vault_key = Self::unwrap_vault_key(&unlock_key, &method.wrapped_vault_key_ct)?;

        let vault_ctx = Self::read_vault_context(conn)?;
        let keyring = Keyring::from_vault_key(&vault_key, &vault_ctx)?;
        conn.attach_keyring(keyring);

        Self::create_session(UnlockMethodType::Password)
    }

    /// 使用安全密钥解锁 vault。
    pub fn unlock_with_security_key(
        conn: &mut VaultConnection,
        key_data: &[u8],
    ) -> StorageResult<VaultSession> {
        let method =
            Self::find_method_by_type(conn, UnlockMethodType::SecurityKey)?.ok_or_else(|| {
                StorageError::Validation("no security key unlock method configured".to_string())
            })?;

        let kdf_params = KdfParams::from_json_bytes(&method.kdf_params_ct)
            .map_err(|e| StorageError::SchemaCreation(format!("invalid KDF params: {}", e)))?;
        let unlock_key = Self::derive_key(key_data, &kdf_params)?;

        let vault_key = Self::unwrap_vault_key(&unlock_key, &method.wrapped_vault_key_ct)?;

        let vault_ctx = Self::read_vault_context(conn)?;
        let keyring = Keyring::from_vault_key(&vault_key, &vault_ctx)?;
        conn.attach_keyring(keyring);

        Self::create_session(UnlockMethodType::SecurityKey)
    }

    // -----------------------------------------------------------------------
    // CHANGE — 修改凭据
    // -----------------------------------------------------------------------

    /// 修改 PIN。
    ///
    /// 用旧 PIN 解包 vault_key，再用新 PIN 重新包裹。
    pub fn change_pin(
        conn: &mut VaultConnection,
        old_pin: &str,
        new_pin: &str,
    ) -> StorageResult<()> {
        // 用旧凭据解包 vault_key
        let method = Self::find_method_by_type(conn, UnlockMethodType::Pin)?
            .ok_or_else(|| StorageError::Validation("no PIN configured".to_string()))?;

        let old_normalized = old_pin.trim();
        let old_kdf_params = KdfParams::from_json_bytes(&method.kdf_params_ct)
            .map_err(|e| StorageError::SchemaCreation(format!("invalid KDF params: {}", e)))?;
        let old_unlock_key = Self::derive_key(old_normalized.as_bytes(), &old_kdf_params)?;
        let vault_key = Self::unwrap_vault_key(&old_unlock_key, &method.wrapped_vault_key_ct)?;

        // 用新凭据重新包裹
        Self::validate_pin(new_pin)?;
        let new_normalized = new_pin.trim();
        let mut new_kdf_params = KdfParams::for_pin();
        new_kdf_params.salt = kdf::generate_salt(16).map_err(|e| {
            StorageError::Crypto(mdbx_crypto::error::CryptoError::RngError(e.to_string()))
        })?;
        let new_unlock_key = Self::derive_key(new_normalized.as_bytes(), &new_kdf_params)?;
        let new_wrapped = Self::wrap_vault_key(&new_unlock_key, &vault_key)?;

        // 更新密钥环（vault_key 不变，但派生密钥变了）
        let vault_ctx = Self::read_vault_context(conn)?;
        let keyring = Keyring::from_vault_key(&vault_key, &vault_ctx)?;
        conn.attach_keyring(keyring);

        Self::update_method_key(conn, UnlockMethodType::Pin, &new_kdf_params, &new_wrapped)
    }

    /// 修改密码（保持原有 Tiga 安全等级）。
    ///
    /// 用旧密码解包 vault_key，再用新密码重新包裹。
    pub fn change_password(
        conn: &mut VaultConnection,
        old_password: &str,
        new_password: &str,
    ) -> StorageResult<()> {
        let method = Self::find_method_by_type(conn, UnlockMethodType::Password)?
            .ok_or_else(|| StorageError::Validation("no password configured".to_string()))?;

        let old_normalized = Self::normalize_unicode(old_password);
        let old_kdf_params = KdfParams::from_json_bytes(&method.kdf_params_ct)
            .map_err(|e| StorageError::SchemaCreation(format!("invalid KDF params: {}", e)))?;
        let old_unlock_key = Self::derive_key(old_normalized.as_bytes(), &old_kdf_params)?;
        let vault_key = Self::unwrap_vault_key(&old_unlock_key, &method.wrapped_vault_key_ct)?;

        let mode = old_kdf_params.infer_tiga_mode();
        Self::validate_password(new_password)?;
        let new_normalized = Self::normalize_unicode(new_password);
        let mut new_kdf_params = KdfParams::for_password_with_mode(mode);
        new_kdf_params.salt = kdf::generate_salt(16).map_err(|e| {
            StorageError::Crypto(mdbx_crypto::error::CryptoError::RngError(e.to_string()))
        })?;
        let new_unlock_key = Self::derive_key(new_normalized.as_bytes(), &new_kdf_params)?;
        let new_wrapped = Self::wrap_vault_key(&new_unlock_key, &vault_key)?;

        let vault_ctx = Self::read_vault_context(conn)?;
        let keyring = Keyring::from_vault_key(&vault_key, &vault_ctx)?;
        conn.attach_keyring(keyring);

        Self::update_method_key(
            conn,
            UnlockMethodType::Password,
            &new_kdf_params,
            &new_wrapped,
        )
    }

    // -----------------------------------------------------------------------
    // LIST — 查询
    // -----------------------------------------------------------------------

    /// 列出所有已配置的解锁方式。
    pub fn list_methods(conn: &VaultConnection) -> StorageResult<Vec<UnlockMethod>> {
        let mut stmt = conn
            .inner()
            .prepare(
                "SELECT method_id, method_type, kdf_profile_id, kdf_params_ct,
                        wrapped_vault_key_ct, created_at, updated_at
                 FROM unlock_methods
                 ORDER BY created_at",
            )
            .map_err(StorageError::Database)?;

        let methods = stmt
            .query_map([], |row| {
                Ok(UnlockMethod {
                    method_id: row.get(0)?,
                    method_type: {
                        let s: String = row.get(1)?;
                        UnlockMethodType::parse(&s).unwrap()
                    },
                    kdf_profile_id: row.get(2)?,
                    kdf_params_ct: row.get(3)?,
                    wrapped_vault_key_ct: row.get(4)?,
                    created_at: row.get(5)?,
                    updated_at: row.get(6)?,
                })
            })
            .map_err(StorageError::Database)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(StorageError::Database)?;

        Ok(methods)
    }

    /// 检查是否已配置指定类型的解锁方式。
    pub fn has_method_of_type(
        conn: &VaultConnection,
        method_type: UnlockMethodType,
    ) -> StorageResult<bool> {
        Self::find_method_by_type(conn, method_type).map(|m| m.is_some())
    }

    /// 删除指定类型的解锁方式。
    ///
    /// 至少需要保留一种解锁方式。
    pub fn remove_method(conn: &VaultConnection, method_id: &str) -> StorageResult<()> {
        let methods = Self::list_methods(conn)?;
        if methods.len() <= 1 {
            return Err(StorageError::Validation(
                "cannot remove the last unlock method".to_string(),
            ));
        }

        let affected = conn
            .inner()
            .execute(
                "DELETE FROM unlock_methods WHERE method_id = ?1",
                rusqlite::params![method_id],
            )
            .map_err(StorageError::Database)?;

        if affected == 0 {
            return Err(StorageError::NotFound(method_id.to_string()));
        }
        Ok(())
    }

    // -----------------------------------------------------------------------
    // PRIVATE HELPERS — 密钥操作
    // -----------------------------------------------------------------------

    /// 获取已有的 vault_key，若无则生成新的。
    ///
    /// 首次设置解锁方式时生成新的随机 vault_key。
    /// 后续设置的解锁方式复用同一个 vault_key，
    /// 确保无论用哪种方式解锁都能解密同一批数据。
    fn get_or_generate_vault_key(conn: &VaultConnection) -> Vec<u8> {
        match conn.keyring() {
            Some(kr) => kr.vault_key.clone(),
            None => aead::generate_key().unwrap_or_else(|_| vec![0u8; 32]),
        }
    }

    /// 用 unlock_key 包裹 vault_key。
    fn wrap_vault_key(unlock_key: &[u8], vault_key: &[u8]) -> StorageResult<Vec<u8>> {
        aead::encrypt(unlock_key, vault_key, VAULT_KEY_WRAP_AAD).map_err(StorageError::Crypto)
    }

    /// 用 unlock_key 解包得到 vault_key。
    fn unwrap_vault_key(unlock_key: &[u8], wrapped: &[u8]) -> StorageResult<Vec<u8>> {
        aead::decrypt(unlock_key, wrapped, VAULT_KEY_WRAP_AAD).map_err(|e| match e {
            mdbx_crypto::error::CryptoError::AuthenticationFailed => {
                StorageError::Validation("incorrect credential".to_string())
            }
            other => StorageError::Crypto(other),
        })
    }

    /// 从 vault_meta 读取 vault_id 作为 Keyring 的派生上下文。
    fn read_vault_context(conn: &VaultConnection) -> StorageResult<Vec<u8>> {
        let vault_id: String = conn
            .inner()
            .query_row("SELECT vault_id FROM vault_meta LIMIT 1", [], |row| {
                row.get(0)
            })
            .map_err(|e| StorageError::Database(e))?;
        Ok(vault_id.into_bytes())
    }

    /// 从凭据和 KDF 参数派生密钥（使用 Argon2id）。
    fn derive_key(credential: &[u8], kdf_params: &KdfParams) -> StorageResult<Vec<u8>> {
        let argon2_params = Argon2Params {
            memory_kib: kdf_params.mem_limit_kib,
            iterations: kdf_params.ops_limit,
            parallelism: kdf_params.parallelism,
            output_len: kdf_params.output_len as usize,
        };
        kdf::derive_key(credential, &kdf_params.salt, &argon2_params)
            .map_err(|e| StorageError::Crypto(e))
    }

    // -----------------------------------------------------------------------
    // PRIVATE HELPERS — 存储
    // -----------------------------------------------------------------------

    /// 存储一种解锁方式。
    fn store_method(
        conn: &VaultConnection,
        method_type: UnlockMethodType,
        kdf_params: &KdfParams,
        wrapped_vault_key_ct: &[u8],
    ) -> StorageResult<UnlockMethod> {
        let method_id = Uuid::new_v4().to_string();
        let now = chrono::Utc::now().to_rfc3339();

        conn.inner()
            .execute(
                "INSERT INTO unlock_methods (method_id, method_type, kdf_profile_id,
                 kdf_params_ct, wrapped_vault_key_ct, created_at, updated_at)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?6)",
                rusqlite::params![
                    method_id,
                    method_type.to_string(),
                    "mdbx-default-v1",
                    kdf_params.to_json_bytes(),
                    wrapped_vault_key_ct,
                    now,
                ],
            )
            .map_err(StorageError::Database)?;

        Ok(UnlockMethod {
            method_id,
            method_type,
            kdf_profile_id: "mdbx-default-v1".to_string(),
            kdf_params_ct: kdf_params.to_json_bytes(),
            wrapped_vault_key_ct: wrapped_vault_key_ct.to_vec(),
            created_at: now.clone(),
            updated_at: now,
        })
    }

    /// 更新已有方法的密钥。
    fn update_method_key(
        conn: &VaultConnection,
        method_type: UnlockMethodType,
        kdf_params: &KdfParams,
        wrapped_vault_key_ct: &[u8],
    ) -> StorageResult<()> {
        let now = chrono::Utc::now().to_rfc3339();
        let affected = conn
            .inner()
            .execute(
                "UPDATE unlock_methods
                 SET kdf_params_ct = ?1, wrapped_vault_key_ct = ?2, updated_at = ?3
                 WHERE method_type = ?4",
                rusqlite::params![
                    kdf_params.to_json_bytes(),
                    wrapped_vault_key_ct,
                    now,
                    method_type.to_string(),
                ],
            )
            .map_err(StorageError::Database)?;

        if affected == 0 {
            return Err(StorageError::Validation(format!(
                "no {:?} unlock method configured",
                method_type
            )));
        }
        Ok(())
    }

    /// 按类型查找已配置的解锁方式。
    fn find_method_by_type(
        conn: &VaultConnection,
        method_type: UnlockMethodType,
    ) -> StorageResult<Option<UnlockMethod>> {
        let result = conn.inner().query_row(
            "SELECT method_id, method_type, kdf_profile_id, kdf_params_ct,
                        wrapped_vault_key_ct, created_at, updated_at
                 FROM unlock_methods
                 WHERE method_type = ?1",
            rusqlite::params![method_type.to_string()],
            |row| {
                Ok(UnlockMethod {
                    method_id: row.get(0)?,
                    method_type: {
                        let s: String = row.get(1)?;
                        UnlockMethodType::parse(&s).unwrap()
                    },
                    kdf_profile_id: row.get(2)?,
                    kdf_params_ct: row.get(3)?,
                    wrapped_vault_key_ct: row.get(4)?,
                    created_at: row.get(5)?,
                    updated_at: row.get(6)?,
                })
            },
        );

        match result {
            Ok(method) => Ok(Some(method)),
            Err(rusqlite::Error::QueryReturnedNoRows) => Ok(None),
            Err(e) => Err(StorageError::Database(e)),
        }
    }

    // -----------------------------------------------------------------------
    // PRIVATE HELPERS — 工具
    // -----------------------------------------------------------------------

    /// 对字符串进行 Unicode NFC 规范化。
    fn normalize_unicode(s: &str) -> String {
        use unicode_normalization::UnicodeNormalization;
        s.trim().nfc().collect()
    }

    /// 创建解锁会话。
    fn create_session(method: UnlockMethodType) -> StorageResult<VaultSession> {
        Ok(VaultSession {
            session_id: Uuid::new_v4().to_string(),
            unlock_method: method,
            created_at: chrono::Utc::now().to_rfc3339(),
        })
    }

    // -----------------------------------------------------------------------
    // VALIDATION
    // -----------------------------------------------------------------------

    fn validate_pin(pin: &str) -> StorageResult<()> {
        let trimmed = pin.trim();
        if trimmed.len() < 4 {
            return Err(StorageError::Validation(
                "PIN must be at least 4 digits".to_string(),
            ));
        }
        if !trimmed.chars().all(|c| c.is_ascii_digit()) {
            return Err(StorageError::Validation(
                "PIN must contain only digits".to_string(),
            ));
        }
        Ok(())
    }

    fn validate_password(password: &str) -> StorageResult<()> {
        let trimmed = password.trim();
        if trimmed.is_empty() {
            return Err(StorageError::Validation(
                "password must not be empty".to_string(),
            ));
        }
        Ok(())
    }
}

// ---------------------------------------------------------------------------
// 测试
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use crate::init::{initialize_vault, VaultInitParams};

    fn setup() -> VaultConnection {
        let conn = VaultConnection::open_in_memory().unwrap();
        let params = VaultInitParams::default();
        initialize_vault(&conn, &params).unwrap();
        conn
    }

    // -----------------------------------------------------------------------
    // PIN
    // -----------------------------------------------------------------------

    #[test]
    fn test_setup_and_unlock_pin() {
        let mut conn = setup();
        UnlockService::setup_pin(&mut conn, "123456").unwrap();

        let session = UnlockService::unlock_with_pin(&mut conn, "123456").unwrap();
        assert_eq!(session.unlock_method, UnlockMethodType::Pin);
    }

    #[test]
    fn test_wrong_pin_rejected() {
        let mut conn = setup();
        UnlockService::setup_pin(&mut conn, "9999").unwrap();

        let result = UnlockService::unlock_with_pin(&mut conn, "0000");
        assert!(result.is_err());
    }

    #[test]
    fn test_pin_too_short_rejected() {
        let mut conn = setup();
        let result = UnlockService::setup_pin(&mut conn, "123");
        assert!(result.is_err());
        let err = result.unwrap_err().to_string();
        assert!(err.contains("at least 4 digits"));
    }

    #[test]
    fn test_pin_non_digit_rejected() {
        let mut conn = setup();
        let result = UnlockService::setup_pin(&mut conn, "12ab");
        assert!(result.is_err());
        let err = result.unwrap_err().to_string();
        assert!(err.contains("only digits"));
    }

    #[test]
    fn test_pin_whitespace_trimmed() {
        let mut conn = setup();
        UnlockService::setup_pin(&mut conn, "  7777  ").unwrap();
        assert!(UnlockService::unlock_with_pin(&mut conn, "7777").is_ok());
    }

    #[test]
    fn test_change_pin() {
        let mut conn = setup();
        UnlockService::setup_pin(&mut conn, "111111").unwrap();

        UnlockService::change_pin(&mut conn, "111111", "222222").unwrap();

        assert!(UnlockService::unlock_with_pin(&mut conn, "111111").is_err());
        assert!(UnlockService::unlock_with_pin(&mut conn, "222222").is_ok());
    }

    #[test]
    fn test_unlock_without_setup_pin() {
        let mut conn = setup();
        let result = UnlockService::unlock_with_pin(&mut conn, "123456");
        assert!(result.is_err());
    }

    // -----------------------------------------------------------------------
    // PASSWORD
    // -----------------------------------------------------------------------

    #[test]
    fn test_setup_and_unlock_password() {
        let mut conn = setup();
        UnlockService::setup_password(&mut conn, "my-secret-password").unwrap();

        let session = UnlockService::unlock_with_password(&mut conn, "my-secret-password").unwrap();
        assert_eq!(session.unlock_method, UnlockMethodType::Password);
    }

    #[test]
    fn test_wrong_password_rejected() {
        let mut conn = setup();
        UnlockService::setup_password(&mut conn, "correct-horse-battery-staple").unwrap();

        let result = UnlockService::unlock_with_password(&mut conn, "wrong-horse-battery-staple");
        assert!(result.is_err());
    }

    #[test]
    fn test_empty_password_rejected() {
        let mut conn = setup();
        let result = UnlockService::setup_password(&mut conn, "");
        assert!(result.is_err());
        let err = result.unwrap_err().to_string();
        assert!(err.contains("must not be empty"));
    }

    #[test]
    fn test_password_whitespace_trimmed() {
        let mut conn = setup();
        UnlockService::setup_password(&mut conn, "  my-password  ").unwrap();
        assert!(UnlockService::unlock_with_password(&mut conn, "my-password").is_ok());
    }

    #[test]
    fn test_change_password() {
        let mut conn = setup();
        UnlockService::setup_password(&mut conn, "old-password").unwrap();

        UnlockService::change_password(&mut conn, "old-password", "new-password").unwrap();

        assert!(UnlockService::unlock_with_password(&mut conn, "old-password").is_err());
        assert!(UnlockService::unlock_with_password(&mut conn, "new-password").is_ok());
    }

    #[test]
    fn test_chinese_password() {
        let mut conn = setup();
        let password = "我的密码是安全的123";
        UnlockService::setup_password(&mut conn, password).unwrap();

        let session = UnlockService::unlock_with_password(&mut conn, password).unwrap();
        assert_eq!(session.unlock_method, UnlockMethodType::Password);
    }

    #[test]
    fn test_chinese_password_rejected_with_different_chars() {
        let mut conn = setup();
        UnlockService::setup_password(&mut conn, "中文密码").unwrap();

        let result = UnlockService::unlock_with_password(&mut conn, "日文密码");
        assert!(result.is_err());
    }

    #[test]
    fn test_unicode_emoji_password() {
        let mut conn = setup();
        let password = "password🔐with🚀emoji";
        UnlockService::setup_password(&mut conn, password).unwrap();

        let session = UnlockService::unlock_with_password(&mut conn, password).unwrap();
        assert_eq!(session.unlock_method, UnlockMethodType::Password);
    }

    #[test]
    fn test_mixed_script_password() {
        let mut conn = setup();
        let password = "パスワード mot de passe 密码 пароль";
        UnlockService::setup_password(&mut conn, password).unwrap();

        assert!(UnlockService::unlock_with_password(&mut conn, password).is_ok());
    }

    #[test]
    fn test_unlock_without_setup_password() {
        let mut conn = setup();
        let result = UnlockService::unlock_with_password(&mut conn, "some-password");
        assert!(result.is_err());
    }

    // -----------------------------------------------------------------------
    // SECURITY KEY
    // -----------------------------------------------------------------------

    #[test]
    fn test_setup_and_unlock_security_key() {
        let mut conn = setup();
        let key_data = b"hardware-key-material-32bytes!!!";
        UnlockService::setup_security_key(&mut conn, key_data).unwrap();

        let session = UnlockService::unlock_with_security_key(&mut conn, key_data).unwrap();
        assert_eq!(session.unlock_method, UnlockMethodType::SecurityKey);
    }

    #[test]
    fn test_wrong_security_key_rejected() {
        let mut conn = setup();
        UnlockService::setup_security_key(&mut conn, b"original-key-data").unwrap();

        let result = UnlockService::unlock_with_security_key(&mut conn, b"wrong-key-data");
        assert!(result.is_err());
    }

    #[test]
    fn test_empty_security_key_rejected() {
        let mut conn = setup();
        let result = UnlockService::setup_security_key(&mut conn, b"");
        assert!(result.is_err());
    }

    #[test]
    fn test_unlock_without_setup_security_key() {
        let mut conn = setup();
        let result = UnlockService::unlock_with_security_key(&mut conn, b"some-key-data");
        assert!(result.is_err());
    }

    // -----------------------------------------------------------------------
    // LIST & REMOVE
    // -----------------------------------------------------------------------

    #[test]
    fn test_list_methods() {
        let mut conn = setup();
        UnlockService::setup_pin(&mut conn, "123456").unwrap();
        UnlockService::setup_password(&mut conn, "password").unwrap();

        let methods = UnlockService::list_methods(&conn).unwrap();
        assert_eq!(methods.len(), 2);

        let has_pin = methods
            .iter()
            .any(|m| m.method_type == UnlockMethodType::Pin);
        let has_password = methods
            .iter()
            .any(|m| m.method_type == UnlockMethodType::Password);
        assert!(has_pin);
        assert!(has_password);
    }

    #[test]
    fn test_has_method_of_type() {
        let mut conn = setup();
        assert!(!UnlockService::has_method_of_type(&conn, UnlockMethodType::Pin).unwrap());

        UnlockService::setup_pin(&mut conn, "123456").unwrap();
        assert!(UnlockService::has_method_of_type(&conn, UnlockMethodType::Pin).unwrap());
    }

    #[test]
    fn test_remove_method() {
        let mut conn = setup();
        UnlockService::setup_pin(&mut conn, "123456").unwrap();
        let pw = UnlockService::setup_password(&mut conn, "password").unwrap();

        let methods = UnlockService::list_methods(&conn).unwrap();
        assert_eq!(methods.len(), 2);

        UnlockService::remove_method(&conn, &pw.method_id).unwrap();

        let methods = UnlockService::list_methods(&conn).unwrap();
        assert_eq!(methods.len(), 1);
        assert_eq!(methods[0].method_type, UnlockMethodType::Pin);
    }

    #[test]
    fn test_cannot_remove_last_method() {
        let mut conn = setup();
        let pin = UnlockService::setup_pin(&mut conn, "123456").unwrap();

        let result = UnlockService::remove_method(&conn, &pin.method_id);
        assert!(result.is_err());
        let err = result.unwrap_err().to_string();
        assert!(err.contains("last unlock method"));
    }

    #[test]
    fn test_remove_nonexistent_method() {
        let conn = setup();
        let result = UnlockService::remove_method(&conn, "nonexistent-id");
        assert!(result.is_err());
    }

    // -----------------------------------------------------------------------
    // UNLOCK WITH UNCONFIGURED METHOD
    // -----------------------------------------------------------------------

    #[test]
    fn test_unlock_with_wrong_method_type() {
        let mut conn = setup();
        UnlockService::setup_pin(&mut conn, "123456").unwrap();

        let result = UnlockService::unlock_with_password(&mut conn, "some-password");
        assert!(result.is_err());
    }

    // -----------------------------------------------------------------------
    // UNLOCK SESSION
    // -----------------------------------------------------------------------

    #[test]
    fn test_session_contains_method_and_timestamp() {
        let mut conn = setup();
        UnlockService::setup_pin(&mut conn, "123456").unwrap();

        let session = UnlockService::unlock_with_pin(&mut conn, "123456").unwrap();
        assert!(!session.session_id.is_empty());
        assert_eq!(session.unlock_method, UnlockMethodType::Pin);
        assert!(!session.created_at.is_empty());
    }

    #[test]
    fn test_multiple_sessions_unique() {
        let mut conn = setup();
        UnlockService::setup_pin(&mut conn, "123456").unwrap();

        let s1 = UnlockService::unlock_with_pin(&mut conn, "123456").unwrap();
        let s2 = UnlockService::unlock_with_pin(&mut conn, "123456").unwrap();
        assert_ne!(s1.session_id, s2.session_id);
    }

    // -----------------------------------------------------------------------
    // KDF PARAMETER ROUNDTRIP
    // -----------------------------------------------------------------------

    #[test]
    fn test_kdf_params_roundtrip() {
        let params = KdfParams::for_password();
        let bytes = params.to_json_bytes();
        let restored = KdfParams::from_json_bytes(&bytes).unwrap();
        assert_eq!(restored.algorithm, params.algorithm);
        assert_eq!(restored.ops_limit, params.ops_limit);
        assert_eq!(restored.mem_limit_kib, params.mem_limit_kib);
        assert_eq!(restored.parallelism, params.parallelism);
        assert_eq!(restored.output_len, params.output_len);
    }

    #[test]
    fn test_kdf_params_per_method_different() {
        let pin_params = KdfParams::for_pin();
        let pw_params = KdfParams::for_password();
        let sk_params = KdfParams::for_security_key();

        assert!(pin_params.ops_limit < pw_params.ops_limit);
        assert!(pin_params.mem_limit_kib < pw_params.mem_limit_kib);
        assert!(sk_params.ops_limit < pw_params.ops_limit);
    }

    // -----------------------------------------------------------------------
    // PIN VALIDATION EDGE CASES
    // -----------------------------------------------------------------------

    #[test]
    fn test_pin_exactly_4_digits_ok() {
        let mut conn = setup();
        assert!(UnlockService::setup_pin(&mut conn, "0000").is_ok());
    }

    #[test]
    fn test_pin_spaces_around_digits() {
        let mut conn = setup();
        UnlockService::setup_pin(&mut conn, "  888888  ").unwrap();
        assert!(UnlockService::unlock_with_pin(&mut conn, "888888").is_ok());
    }

    // -----------------------------------------------------------------------
    // UNICODE NFC NORMALIZATION
    // -----------------------------------------------------------------------

    #[test]
    fn test_nfc_normalization_combining_accent() {
        let nfd = "caf\u{0065}\u{0301}";
        let nfc = "caf\u{00E9}";

        let normalized_nfd = UnlockService::normalize_unicode(nfd);
        let normalized_nfc = UnlockService::normalize_unicode(nfc);

        assert_eq!(normalized_nfd, normalized_nfc);
        assert_eq!(normalized_nfd, nfc);
    }

    #[test]
    fn test_nfc_normalization_korean() {
        let nfd = "\u{1112}\u{1161}\u{11AB}";
        let nfc_expected = "\u{D55C}";

        let normalized = UnlockService::normalize_unicode(nfd);
        assert_eq!(normalized, nfc_expected);
    }

    #[test]
    fn test_nfc_noop_for_already_normalized() {
        let s = "我的密码是安全的123";
        let normalized = UnlockService::normalize_unicode(s);
        assert_eq!(normalized, s);
    }

    #[test]
    fn test_unlock_with_nfc_mismatched_input() {
        let mut conn = setup();
        let nfc_password = "caf\u{00E9}";
        UnlockService::setup_password(&mut conn, nfc_password).unwrap();

        let nfd_input = "caf\u{0065}\u{0301}";
        let session = UnlockService::unlock_with_password(&mut conn, nfd_input).unwrap();
        assert_eq!(session.unlock_method, UnlockMethodType::Password);
    }

    #[test]
    fn test_nfc_with_whitespace() {
        let s = "  \u{00E9}  ";
        let normalized = UnlockService::normalize_unicode(s);
        assert_eq!(normalized, "\u{00E9}");
    }

    // -----------------------------------------------------------------------
    // ENCRYPTION — 密钥包装与 Keyring 正确性
    // -----------------------------------------------------------------------

    #[test]
    fn test_derived_key_not_stored_directly() {
        let mut conn = setup();
        UnlockService::setup_password(&mut conn, "test-password").unwrap();

        let method = UnlockService::find_method_by_type(&conn, UnlockMethodType::Password)
            .unwrap()
            .unwrap();
        let kdf_params = KdfParams::from_json_bytes(&method.kdf_params_ct).unwrap();
        let derived = UnlockService::derive_key(b"test-password", &kdf_params).unwrap();

        // wrapped_vault_key_ct 不能等于派生密钥（它是 AEAD 密文，不是原始密钥字节）
        assert_ne!(method.wrapped_vault_key_ct, derived);
        // 密文至少 nonce(24) + tag(16) + 加密的 vault_key(32) = 72 字节
        assert!(method.wrapped_vault_key_ct.len() >= 72);
    }

    #[test]
    fn test_setup_attaches_keyring() {
        let mut conn = setup();
        assert!(!conn.is_encrypted());

        UnlockService::setup_password(&mut conn, "my-password").unwrap();
        assert!(conn.is_encrypted());
    }

    #[test]
    fn test_unlock_attaches_keyring() {
        let mut conn = setup();
        UnlockService::setup_password(&mut conn, "my-password").unwrap();

        // 读取 vault_id 以创建相同 vault 的第二个连接
        let vault_id: String = conn
            .inner()
            .query_row("SELECT vault_id FROM vault_meta", [], |row| row.get(0))
            .unwrap();

        // 重新创建连接（模拟重新打开 vault）
        let mut conn2 = VaultConnection::open_in_memory().unwrap();
        let params = VaultInitParams {
            vault_id: Some(vault_id),
            ..VaultInitParams::default()
        };
        initialize_vault(&conn2, &params).unwrap();
        // 把 unlock_methods 复制过去（模拟持久化数据）
        let methods = UnlockService::list_methods(&conn).unwrap();
        for m in &methods {
            conn2
                .inner()
                .execute(
                    "INSERT INTO unlock_methods (method_id, method_type, kdf_profile_id,
                 kdf_params_ct, wrapped_vault_key_ct, created_at, updated_at)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
                    rusqlite::params![
                        m.method_id,
                        m.method_type.to_string(),
                        m.kdf_profile_id,
                        m.kdf_params_ct,
                        m.wrapped_vault_key_ct,
                        m.created_at,
                        m.updated_at,
                    ],
                )
                .unwrap();
        }

        assert!(!conn2.is_encrypted());
        UnlockService::unlock_with_password(&mut conn2, "my-password").unwrap();
        assert!(conn2.is_encrypted());
    }

    #[test]
    fn test_unlock_then_setup_second_method_reuses_vault_key() {
        let mut conn = setup();
        // 先设置密码
        UnlockService::setup_password(&mut conn, "password1").unwrap();
        let vault_key_1 = conn.keyring().unwrap().vault_key.clone();

        // 再设置 PIN — 应复用同一个 vault_key
        UnlockService::setup_pin(&mut conn, "123456").unwrap();
        let vault_key_2 = conn.keyring().unwrap().vault_key.clone();

        assert_eq!(vault_key_1, vault_key_2);
    }

    #[test]
    fn test_both_methods_unlock_to_same_keyring() {
        let mut conn = setup();
        UnlockService::setup_password(&mut conn, "password").unwrap();
        UnlockService::setup_pin(&mut conn, "123456").unwrap();

        let vault_id: String = conn
            .inner()
            .query_row("SELECT vault_id FROM vault_meta", [], |row| row.get(0))
            .unwrap();
        let methods = UnlockService::list_methods(&conn).unwrap();

        // 用 PIN 解锁
        let mut conn_a = VaultConnection::open_in_memory().unwrap();
        initialize_vault(
            &conn_a,
            &VaultInitParams {
                vault_id: Some(vault_id.clone()),
                ..VaultInitParams::default()
            },
        )
        .unwrap();
        for m in &methods {
            conn_a
                .inner()
                .execute(
                    "INSERT INTO unlock_methods VALUES (?1,?2,?3,?4,?5,?6,?7)",
                    rusqlite::params![
                        m.method_id,
                        m.method_type.to_string(),
                        m.kdf_profile_id,
                        m.kdf_params_ct,
                        m.wrapped_vault_key_ct,
                        m.created_at,
                        m.updated_at
                    ],
                )
                .unwrap();
        }
        UnlockService::unlock_with_pin(&mut conn_a, "123456").unwrap();
        let subkeys_from_pin = (
            conn_a.keyring().unwrap().record_subkey.clone(),
            conn_a.keyring().unwrap().attachment_subkey.clone(),
        );

        // 用密码解锁 — 子密钥应相同
        let mut conn_b = VaultConnection::open_in_memory().unwrap();
        initialize_vault(
            &conn_b,
            &VaultInitParams {
                vault_id: Some(vault_id),
                ..VaultInitParams::default()
            },
        )
        .unwrap();
        for m in &methods {
            conn_b
                .inner()
                .execute(
                    "INSERT INTO unlock_methods VALUES (?1,?2,?3,?4,?5,?6,?7)",
                    rusqlite::params![
                        m.method_id,
                        m.method_type.to_string(),
                        m.kdf_profile_id,
                        m.kdf_params_ct,
                        m.wrapped_vault_key_ct,
                        m.created_at,
                        m.updated_at
                    ],
                )
                .unwrap();
        }
        UnlockService::unlock_with_password(&mut conn_b, "password").unwrap();
        let subkeys_from_pw = (
            conn_b.keyring().unwrap().record_subkey.clone(),
            conn_b.keyring().unwrap().attachment_subkey.clone(),
        );

        assert_eq!(subkeys_from_pin, subkeys_from_pw);
    }

    /// 完整端到端测试：创建 vault → 设密码 → 写数据 → 关 → 重开 → 解密验证。
    #[test]
    fn test_e2e_full_workflow() {
        use crate::init::{initialize_vault, VaultInitParams};
        use crate::repo::{CommitContext, EntryRepo, ProjectRepo};
        use mdbx_core::model::EntryType;
        use mdbx_core::tiga::TigaMode;

        // ---- Phase 1: 创建 vault + 设置 Power 模式密码 ----
        let mut conn = VaultConnection::open_in_memory().unwrap();
        let vault_id = uuid::Uuid::new_v4().to_string();
        initialize_vault(
            &conn,
            &VaultInitParams {
                vault_id: Some(vault_id.clone()),
                default_tiga_mode: "power".to_string(),
                ..VaultInitParams::default()
            },
        )
        .unwrap();

        UnlockService::setup_password_with_mode(&mut conn, "我的密码123", TigaMode::Power).unwrap();

        // 验证 Tiga 模式写入
        let global_mode = crate::tiga::TigaService::get_global_default(&conn).unwrap();
        assert_eq!(global_mode, TigaMode::Power);

        // 验证 Power 模式的 KDF 参数 (256 MiB)
        let methods = UnlockService::list_methods(&conn).unwrap();
        let kdf = KdfParams::from_json_bytes(&methods[0].kdf_params_ct).unwrap();
        assert_eq!(kdf.mem_limit_kib, 262144);

        // ---- Phase 2: 写入数据 ----
        let ctx = CommitContext::new("device-e2e".to_string());
        let proj = ProjectRepo::create(&conn, &ctx, "我的工作账号", None, None).unwrap();
        let _entry = EntryRepo::create(
            &conn,
            &ctx,
            &proj.project_id,
            EntryType::Login,
            Some("GitHub"),
            &serde_json::json!({"username": "alice@example.com", "password": "s3cret-token"}),
        )
        .unwrap();

        // ---- Phase 3: 验证原始数据库中是密文 ----
        let raw_title: Vec<u8> = conn
            .inner()
            .query_row(
                "SELECT title_ct FROM projects WHERE project_id = ?1",
                rusqlite::params![proj.project_id],
                |row| row.get(0),
            )
            .unwrap();
        let plain_bytes = "我的工作账号".as_bytes().to_vec();
        assert_ne!(raw_title, plain_bytes, "DB 中应存储密文而非明文");

        // ---- Phase 4: 通过 API 读取得到明文 ----
        let projects = ProjectRepo::list_all(&conn).unwrap();
        assert_eq!(projects.len(), 1);
        assert_eq!(projects[0].title_ct, plain_bytes);

        let entries = EntryRepo::list_by_project(&conn, &proj.project_id).unwrap();
        assert_eq!(entries.len(), 1);
        assert_eq!(entries[0].title_ct.as_deref(), Some(&b"GitHub"[..]));

        // ---- Phase 5: 错误密码被拒绝 ----
        let mut conn2 = VaultConnection::open_in_memory().unwrap();
        initialize_vault(
            &conn2,
            &VaultInitParams {
                vault_id: Some(uuid::Uuid::new_v4().to_string()),
                ..VaultInitParams::default()
            },
        )
        .unwrap();
        for m in &methods {
            conn2
                .inner()
                .execute(
                    "INSERT INTO unlock_methods VALUES (?1,?2,?3,?4,?5,?6,?7)",
                    rusqlite::params![
                        m.method_id,
                        m.method_type.to_string(),
                        m.kdf_profile_id,
                        m.kdf_params_ct,
                        m.wrapped_vault_key_ct,
                        m.created_at,
                        m.updated_at
                    ],
                )
                .unwrap();
        }
        // 错误的密码应导致解锁失败
        assert!(UnlockService::unlock_with_password(&mut conn2, "错误密码").is_err());
    }
}
