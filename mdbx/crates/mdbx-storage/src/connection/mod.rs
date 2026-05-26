use rusqlite::Connection;
use std::path::Path;

use mdbx_crypto::keyring::Keyring;

use crate::error::{StorageError, StorageResult};
use crate::schema;

/// 打开的 vault 数据库连接。
///
/// 在打开时自动设置必要的 PRAGMA：
/// - WAL 模式（增量写入友好）
/// - foreign_keys 强制
/// - secure_delete 启用
/// - busy_timeout 5 秒
///
/// 解锁后可附加 Keyring 以启用字段级加密。
pub struct VaultConnection {
    pub(crate) conn: Connection,
    pub(crate) keyring: Option<Keyring>,
}

impl VaultConnection {
    /// 打开已有的 `.mdbx` 文件。
    pub fn open(path: &Path) -> StorageResult<Self> {
        let conn = Connection::open(path)?;
        Self::apply_pragmas(&conn)?;
        Ok(Self {
            conn,
            keyring: None,
        })
    }

    /// 创建新的 `.mdbx` 文件。
    pub fn create(path: &Path) -> StorageResult<Self> {
        let conn = Connection::open(path)?;
        Self::apply_pragmas(&conn)?;
        schema::create_all_tables(&conn)?;
        Ok(Self {
            conn,
            keyring: None,
        })
    }

    /// 打开内存数据库（用于测试）。
    pub fn open_in_memory() -> StorageResult<Self> {
        let conn = Connection::open_in_memory()?;
        Self::apply_pragmas(&conn)?;
        schema::create_all_tables(&conn)?;
        Ok(Self {
            conn,
            keyring: None,
        })
    }

    fn apply_pragmas(conn: &Connection) -> StorageResult<()> {
        conn.execute_batch(
            "PRAGMA journal_mode=WAL;
             PRAGMA foreign_keys=ON;
             PRAGMA secure_delete=ON;
             PRAGMA busy_timeout=5000;",
        )
        .map_err(|e| StorageError::Database(e))
    }

    /// 获取内部 rusqlite 连接的引用。
    pub fn inner(&self) -> &Connection {
        &self.conn
    }

    /// 附加密钥环，启用字段级加密。
    ///
    /// 在解锁成功后调用。此后所有 `_ct` 字段在写入时加密、读取时解密。
    pub fn attach_keyring(&mut self, keyring: Keyring) {
        self.keyring = Some(keyring);
    }

    /// 获取密钥环的引用（存在时）。
    pub fn keyring(&self) -> Option<&Keyring> {
        self.keyring.as_ref()
    }

    /// 当前连接是否已启用加密。
    pub fn is_encrypted(&self) -> bool {
        self.keyring.is_some()
    }
}
