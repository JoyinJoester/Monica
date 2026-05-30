use mdbx_crypto::error::CryptoError;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum StorageError {
    #[error("schema creation failed: {0}")]
    SchemaCreation(String),

    #[error("database error: {0}")]
    Database(#[from] rusqlite::Error),

    #[error("not found: {0}")]
    NotFound(String),

    #[error("constraint violation: {0}")]
    ConstraintViolation(String),

    #[error("validation error: {0}")]
    Validation(String),

    #[error("crypto error: {0}")]
    Crypto(#[from] CryptoError),
}

pub type StorageResult<T> = Result<T, StorageError>;
