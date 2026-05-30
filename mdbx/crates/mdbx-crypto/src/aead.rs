use chacha20poly1305::{
    aead::{Aead, Payload},
    KeyInit, XChaCha20Poly1305, XNonce,
};
use rand::RngCore;

use crate::error::{CryptoError, CryptoResult};

/// 使用 XChaCha20-Poly1305 加密。
///
/// # Arguments
/// - `key`: 32 字节密钥
/// - `plaintext`: 明文数据
/// - `associated_data`: 关联数据（不加密但参与认证）
///
/// # Returns
/// `(nonce || ciphertext)` — nonce 前置的密文（24 字节 nonce + 密文 + 16 字节 tag）
pub fn encrypt(key: &[u8], plaintext: &[u8], associated_data: &[u8]) -> CryptoResult<Vec<u8>> {
    if key.len() != 32 {
        return Err(CryptoError::InvalidParameter(
            "key must be 32 bytes for XChaCha20-Poly1305".to_string(),
        ));
    }

    let cipher = XChaCha20Poly1305::new_from_slice(key)
        .map_err(|e| CryptoError::Encryption(format!("invalid key: {}", e)))?;

    let mut nonce_bytes = [0u8; 24];
    rand::rngs::OsRng.fill_bytes(&mut nonce_bytes);
    let nonce = XNonce::from_slice(&nonce_bytes);

    let ciphertext = cipher
        .encrypt(
            nonce,
            Payload {
                msg: plaintext,
                aad: associated_data,
            },
        )
        .map_err(|e| CryptoError::Encryption(format!("encrypt failed: {}", e)))?;

    // nonce || ciphertext
    let mut output = Vec::with_capacity(24 + ciphertext.len());
    output.extend_from_slice(&nonce_bytes);
    output.extend_from_slice(&ciphertext);
    Ok(output)
}

/// 使用 XChaCha20-Poly1305 解密。
pub fn decrypt(key: &[u8], ciphertext: &[u8], associated_data: &[u8]) -> CryptoResult<Vec<u8>> {
    if key.len() != 32 {
        return Err(CryptoError::InvalidParameter(
            "key must be 32 bytes for XChaCha20-Poly1305".to_string(),
        ));
    }

    if ciphertext.len() < 40 {
        return Err(CryptoError::Decryption(
            "ciphertext too short (need at least 40 bytes: 24 nonce + 16 tag)".to_string(),
        ));
    }

    let cipher = XChaCha20Poly1305::new_from_slice(key)
        .map_err(|e| CryptoError::Decryption(format!("invalid key: {}", e)))?;

    let nonce = XNonce::from_slice(&ciphertext[..24]);

    let plaintext = cipher
        .decrypt(
            nonce,
            Payload {
                msg: &ciphertext[24..],
                aad: associated_data,
            },
        )
        .map_err(|_| CryptoError::AuthenticationFailed)?;

    Ok(plaintext)
}

/// 生成随机 32 字节密钥。
pub fn generate_key() -> CryptoResult<Vec<u8>> {
    let mut key = vec![0u8; 32];
    rand::rngs::OsRng.fill_bytes(&mut key);
    Ok(key)
}

// ---------------------------------------------------------------------------
// 测试
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_encrypt_decrypt_roundtrip() {
        let key = generate_key().unwrap();
        let plaintext = b"Hello, MDBX! This is secret data.";
        let aad = b"project-id-12345";

        let ct = encrypt(&key, plaintext, aad).unwrap();
        let pt = decrypt(&key, &ct, aad).unwrap();
        assert_eq!(pt, plaintext);
    }

    #[test]
    fn test_decrypt_with_wrong_key_fails() {
        let key1 = generate_key().unwrap();
        let key2 = generate_key().unwrap();
        let plaintext = b"secret message";
        let aad = b"context";

        let ct = encrypt(&key1, plaintext, aad).unwrap();
        let result = decrypt(&key2, &ct, aad);
        assert!(matches!(result, Err(CryptoError::AuthenticationFailed)));
    }

    #[test]
    fn test_decrypt_with_wrong_aad_fails() {
        let key = generate_key().unwrap();
        let plaintext = b"secret message";

        let ct = encrypt(&key, plaintext, b"correct-aad").unwrap();
        let result = decrypt(&key, &ct, b"wrong-aad");
        assert!(matches!(result, Err(CryptoError::AuthenticationFailed)));
    }

    #[test]
    fn test_decrypt_with_tampered_data_fails() {
        let key = generate_key().unwrap();
        let plaintext = b"secret message";
        let aad = b"context";

        let mut ct = encrypt(&key, plaintext, aad).unwrap();
        // 篡改密文（跳过 nonce 部分）
        if ct.len() > 30 {
            ct[30] ^= 0x01;
        }
        let result = decrypt(&key, &ct, aad);
        assert!(matches!(result, Err(CryptoError::AuthenticationFailed)));
    }

    #[test]
    fn test_encrypt_produces_different_ciphertexts() {
        let key = generate_key().unwrap();
        let plaintext = b"same plaintext";
        let aad = b"same context";

        let ct1 = encrypt(&key, plaintext, aad).unwrap();
        let ct2 = encrypt(&key, plaintext, aad).unwrap();
        // 不同 nonce，密文不同
        assert_ne!(ct1, ct2);
    }

    #[test]
    fn test_invalid_key_length() {
        let result = encrypt(b"short-key", b"data", b"");
        assert!(result.is_err());
    }

    #[test]
    fn test_ciphertext_too_short() {
        let key = generate_key().unwrap();
        let result = decrypt(&key, b"too-short", b"");
        assert!(result.is_err());
    }

    #[test]
    fn test_empty_plaintext() {
        let key = generate_key().unwrap();
        let ct = encrypt(&key, b"", b"context").unwrap();
        let pt = decrypt(&key, &ct, b"context").unwrap();
        assert!(pt.is_empty());
    }

    #[test]
    fn test_large_plaintext() {
        let key = generate_key().unwrap();
        let plaintext = vec![0xAB; 1024 * 1024]; // 1 MiB
        let aad = b"large-data-test";

        let ct = encrypt(&key, &plaintext, aad).unwrap();
        let pt = decrypt(&key, &ct, aad).unwrap();
        assert_eq!(pt, plaintext);
    }
}
