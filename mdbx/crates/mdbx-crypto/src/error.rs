use thiserror::Error;

#[derive(Debug, Error)]
pub enum CryptoError {
    #[error("key derivation failed: {0}")]
    KeyDerivation(String),

    #[error("encryption failed: {0}")]
    Encryption(String),

    #[error("decryption failed: {0}")]
    Decryption(String),

    #[error("authentication failed: data has been tampered with or key is wrong")]
    AuthenticationFailed,

    #[error("invalid parameter: {0}")]
    InvalidParameter(String),

    #[error("random number generation failed: {0}")]
    RngError(String),
}

pub type CryptoResult<T> = Result<T, CryptoError>;
